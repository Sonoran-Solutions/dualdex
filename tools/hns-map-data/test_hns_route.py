#!/usr/bin/env python3
"""
Self-contained regression tests for `tools/hns-map-data/hns_route.py` provenance.

The route analyzer is cited as evidence about the pinned Heart & Soul 2.0.5 build, so it must refuse
to analyse anything that is not that build with clean inputs. These tests drive the real
`verify_provenance` function against tiny synthetic git repositories, so they need no upstream
checkout, no ARM toolchain, no ROM and no network -- which is why they belong in the canonical gate
rather than only in `source-check`.

The fail-closed contract they pin:

  * a checkout at another revision is refused, even when it contains a plausible map tree;
  * a modified or deleted file under any path the tool reads is refused;
  * an *untracked* file outside the read set is tolerated, because a CI sparse checkout is full of
    them and they cannot affect the analysis;
  * a directory that is not a git worktree at all is refused rather than analysed.

Run:  cd tools/hns-map-data && python3 -m unittest test_hns_route -v
"""

import os
import shutil
import subprocess
import tempfile
import unittest

import re

import hns_route
from hns_route import BehaviourSource, Source

# The pinned SHA cannot be synthesised offline (that is the point of a commit hash), so the tests
# patch the module constant to a SHA this process CAN create and assert the same contract against
# it: "the tool refuses any revision other than the one it is pinned to". The real constant is
# restored afterwards, and `test_the_real_pinned_constant_is_the_release` pins its value so the
# patch cannot silently weaken what the shipped tool checks.

# A directory tree that satisfies `find_upstream` (map_groups.json exists) and contains one file in
# every path the provenance check treats as a read source.
SYNTHETIC_FILES = (
    "data/maps/map_groups.json",
    "data/layouts/layouts.json",
    "data/tilesets/primary/general/metatile_attributes.bin",
    "data/scripts/new_game.inc",
    "include/constants/metatile_behaviors.h",
    # The engine sources the warp-activation model is derived from, and the boundary check, all have
    # to be present so the dirty-source cases actually exercise the paths this tool reads.
    "include/fieldmap.h",
    "src/fieldmap.c",
    "src/field_control_avatar.c",
    "src/metatile_behavior.c",
    "src/data/tilesets/headers.h",
)


def _git(args, cwd, env=None):
    return subprocess.run(
        ["git"] + args, cwd=cwd, capture_output=True, text=True, check=True, env=env
    )


class ProvenanceTest(unittest.TestCase):
    def setUp(self):
        self.original_pinned = hns_route.PINNED_COMMIT_SHA
        self.addCleanup(setattr, hns_route, "PINNED_COMMIT_SHA", self.original_pinned)
        self.root = tempfile.mkdtemp(prefix="hns-route-provenance-")
        self.addCleanup(shutil.rmtree, self.root, ignore_errors=True)
        self.repo = os.path.join(self.root, "upstream-hns")
        os.makedirs(self.repo)
        for relative in SYNTHETIC_FILES:
            path = os.path.join(self.repo, relative)
            os.makedirs(os.path.dirname(path), exist_ok=True)
            with open(path, "w", encoding="utf-8") as handle:
                handle.write("synthetic\n")
        _git(["init", "-q"], self.repo)
        _git(["config", "user.email", "test@example.invalid"], self.repo)
        _git(["config", "user.name", "Test"], self.repo)
        _git(["config", "commit.gpgsign", "false"], self.repo)
        _git(["add", "-A"], self.repo)

    def commit(self, message="synthetic"):
        """Commit the working tree and return the resulting SHA."""
        env = dict(os.environ)
        env.update({
            "GIT_AUTHOR_DATE": "2020-01-01T00:00:00+0000",
            "GIT_COMMITTER_DATE": "2020-01-01T00:00:00+0000",
        })
        _git(["add", "-A"], self.repo)
        # `--allow-empty` so a second commit can be made without touching the tree: the test needs
        # a different revision of the SAME map data.
        _git(["commit", "-q", "--allow-empty", "-m", message], self.repo, env=env)
        return _git(["rev-parse", "HEAD"], self.repo).stdout.strip()

    def pin_to(self, sha):
        """Make `sha` the revision the tool is pinned to for this test."""
        hns_route.PINNED_COMMIT_SHA = sha

    def setUpPinnedRepo(self):
        """A repo whose HEAD is the pinned revision, with a second commit available."""
        pinned = self.commit("pinned revision")
        self.pin_to(pinned)
        return pinned

    # ------------------------------------------------------------------ location

    def test_find_upstream_requires_a_map_tree(self):
        self.assertEqual(os.path.abspath(self.repo), hns_route.find_upstream(self.repo))
        empty = os.path.join(self.root, "not-an-upstream")
        os.makedirs(empty)
        # Search paths are module-level, so isolate them rather than relying on the host env.
        original = hns_route.UPSTREAM_SEARCH_PATHS
        hns_route.UPSTREAM_SEARCH_PATHS = [empty]
        self.addCleanup(setattr, hns_route, "UPSTREAM_SEARCH_PATHS", original)
        with self.assertRaises(SystemExit) as raised:
            hns_route.find_upstream(None)
        self.assertIn("no pinned H&S upstream checkout found", str(raised.exception))

    # ------------------------------------------------------------------ rejection

    def test_a_wrong_commit_is_refused(self):
        pinned = self.setUpPinnedRepo()
        self.assertEqual(pinned, hns_route.verify_provenance(self.repo))

        # The same plausible map tree at another revision must not be analysed.
        other = self.commit("a different revision")
        self.assertNotEqual(other, pinned)
        with self.assertRaises(SystemExit) as raised:
            hns_route.verify_provenance(self.repo)
        message = str(raised.exception)
        self.assertIn("pinned to", message)
        self.assertIn(pinned, message)
        self.assertIn(other, message)

    def test_a_modified_read_source_is_refused(self):
        self.setUpPinnedRepo()
        # Every path the tool reads must be guarded, because layout dimensions, block data and
        # metatile attributes are all reachability evidence.
        for relative in SYNTHETIC_FILES:
            with self.subTest(modified=relative):
                path = os.path.join(self.repo, relative)
                with open(path, encoding="utf-8") as handle:
                    original = handle.read()
                with open(path, "w", encoding="utf-8") as handle:
                    handle.write("tampered\n")
                try:
                    with self.assertRaises(SystemExit) as raised:
                        hns_route.verify_provenance(self.repo)
                    self.assertIn("uncommitted changes", str(raised.exception))
                finally:
                    with open(path, "w", encoding="utf-8") as handle:
                        handle.write(original)

    def test_a_deleted_read_source_is_refused(self):
        self.setUpPinnedRepo()
        path = os.path.join(self.repo, "data/maps/map_groups.json")
        os.remove(path)
        with self.assertRaises(SystemExit) as raised:
            hns_route.verify_provenance(self.repo)
        self.assertIn("uncommitted changes", str(raised.exception))

    def test_an_untracked_file_outside_the_read_set_is_tolerated(self):
        pinned = self.setUpPinnedRepo()
        # A CI sparse checkout leaves plenty of these; refusing them would make the gate unusable
        # and they cannot affect the analysis.
        with open(os.path.join(self.repo, "README.md"), "w", encoding="utf-8") as handle:
            handle.write("untracked\n")
        self.assertEqual(pinned, hns_route.verify_provenance(self.repo))

    def test_the_engine_sources_the_model_reads_are_inside_the_clean_boundary(self):
        """The derived model reads engine C sources, so those must be protected too.

        `BehaviourSource` reads `src/field_control_avatar.c` and `src/metatile_behavior.c`, and the
        boundary regression reads `include/fieldmap.h` and `src/fieldmap.c`. A dirty copy of any of
        them can change the classification, so each must be inside `REQUIRED_CLEAN_SOURCES`; guarding
        only the map data would let a tampered predicate source through.
        """
        for relative in (
            "src/field_control_avatar.c",
            "src/metatile_behavior.c",
            "src/fieldmap.c",
            "include/fieldmap.h",
        ):
            with self.subTest(source=relative):
                self.assertTrue(
                    os.path.isfile(os.path.join(self.repo, relative)),
                    "the synthetic checkout must contain %s" % relative,
                )
                self.assertTrue(
                    any(relative.startswith(prefix) for prefix in hns_route.REQUIRED_CLEAN_SOURCES),
                    "%s must be guarded by REQUIRED_CLEAN_SOURCES" % relative,
                )

    def test_a_dirty_engine_source_is_refused(self):
        """A modified predicate source must refuse the run, not silently change the model."""
        pinned = self.setUpPinnedRepo()
        for relative in ("src/field_control_avatar.c", "src/metatile_behavior.c", "include/fieldmap.h"):
            with self.subTest(modified=relative):
                path = os.path.join(self.repo, relative)
                os.makedirs(os.path.dirname(path), exist_ok=True)
                with open(path, "w", encoding="utf-8") as handle:
                    handle.write("synthetic\n")
                _git(["add", "-A"], self.repo)
                pinned = self.commit("add %s" % relative)
                self.pin_to(pinned)
                with open(path, "a", encoding="utf-8") as handle:
                    handle.write("tampered\n")
                with self.assertRaises(SystemExit) as raised:
                    hns_route.verify_provenance(self.repo)
                self.assertIn("uncommitted changes", str(raised.exception))
                _git(["checkout", "--", relative], self.repo)

    def test_a_non_git_directory_is_refused(self):
        plain = os.path.join(self.root, "not-a-repo")
        os.makedirs(os.path.join(plain, "data/maps"), exist_ok=True)
        with open(
            os.path.join(plain, "data/maps/map_groups.json"), "w", encoding="utf-8"
        ) as handle:
            handle.write("{}\n")
        with self.assertRaises(SystemExit) as raised:
            hns_route.verify_provenance(plain)
        self.assertIn("could not read the upstream git revision", str(raised.exception))

    def test_accepts_the_pinned_commit_with_clean_sources(self):
        pinned = self.setUpPinnedRepo()
        self.assertEqual(pinned, hns_route.verify_provenance(self.repo))


class BehaviourModelTest(unittest.TestCase):
    """The metatile/activation model must match the pinned engine, not a plausible reading of it.

    This class exists because an earlier revision of this analyzer split primary and secondary
    metatiles at 512. H&S layouts use 640, so every tile in the 512..639 range resolved to the wrong
    tileset and a real `MB_SOUTH_ARROW_WARP` was read as ordinary floor -- which silently inverted a
    cross-region reachability conclusion. These tests read the same constants out of the pinned
    checkout the analyzer reads, so the two cannot drift.

    They need the pinned checkout and are therefore skipped without one; `source-check` always has
    it, and CI's source-validation job runs that gate.
    """

    @classmethod
    def setUpClass(cls):
        # Prefer an explicitly configured checkout, then the tool's own search paths.
        candidates = [os.environ.get("HNS_UPSTREAM_DIR")] + list(hns_route.UPSTREAM_SEARCH_PATHS)
        for candidate in candidates:
            if candidate and os.path.isfile(
                os.path.join(candidate, "include/fieldmap.h")
            ):
                cls.root = candidate
                return
        raise unittest.SkipTest("no pinned upstream checkout available")

    def header(self, relative):
        with open(os.path.join(self.root, relative), encoding="utf-8") as handle:
            return handle.read()

    def function_body(self, source, signature):
        """Text of a C function DEFINITION, by brace matching.

        The signature must include the parameter list: matching a bare name would find the forward
        declaration at the top of the file, whose "body" is a semicolon and the declarations after it.
        """
        start = source.index(signature)
        open_brace = source.index("{", start)
        depth = 0
        for index in range(open_brace, len(source)):
            if source[index] == "{":
                depth += 1
            elif source[index] == "}":
                depth -= 1
                if depth == 0:
                    return source[start:index + 1]
        raise AssertionError("unbalanced braces after %s" % signature)

    def test_primary_boundary_matches_the_pinned_header(self):
        header = self.header("include/fieldmap.h")
        match = re.search(r"^#define NUM_METATILES_IN_PRIMARY (\d+)$", header, re.M)
        self.assertIsNotNone(match, "NUM_METATILES_IN_PRIMARY must be defined in include/fieldmap.h")
        self.assertEqual(
            int(match.group(1)),
            hns_route.NUM_METATILES_IN_PRIMARY,
            "the analyzer must split primary/secondary metatiles where the pinned build does",
        )

    def test_the_boundary_is_the_one_hns_layouts_use(self):
        # `GetNumMetatilesInPrimary` returns NUM_METATILES_IN_PRIMARY for LAYOUT_VERSION_HNS and
        # LAYOUT_VERSION_FRLG, and the Emerald constant only as the default arm.
        source = self.header("src/fieldmap.c")
        body = source[source.index("u32 GetNumMetatilesInPrimary("):]
        body = body[:body.index("\n}")]
        self.assertIn("case LAYOUT_VERSION_HNS:", body)
        self.assertIn("return NUM_METATILES_IN_PRIMARY;", body)
        self.assertEqual(640, hns_route.NUM_METATILES_IN_PRIMARY)

    def test_behaviour_enum_anchors_resolve(self):
        source = BehaviourSource(self.root)
        self.assertEqual(0, source.values.get("MB_NORMAL"))
        self.assertEqual(2, source.values.get("MB_TALL_GRASS"))
        # MB_NORMAL is the zero-fill of every attribute array, so it must be part of no warp path.
        self.assertFalse(source.is_warp_behaviour(source.values["MB_NORMAL"]))
        self.assertFalse(source.is_arrow_behaviour(source.values["MB_NORMAL"]))
        self.assertFalse(source.is_door_behaviour(source.values["MB_NORMAL"]))

    def test_warp_behaviour_sets_are_derived_from_the_pinned_predicates(self):
        """The analyzer's sets must equal what the pinned predicates actually accept.

        This is the test that would have caught the hardcoded-list bug: the first list in this
        analyzer was transcribed from the predicate helper NAMES, and `MetatileBehavior_IsNonAnimDoor`
        also accepts `MB_DEEP_SOUTH_WARP` while `MetatileBehavior_IsUnionRoomWarp` actually tests
        `MB_BRIDGE_OVER_OCEAN`. Deriving the sets from the source removes the transcription step, and
        this asserts the derivation agrees with an independent read of the same predicates.
        """
        behaviours_source = self.header("src/metatile_behavior.c")
        controller = self.header("src/field_control_avatar.c")
        behaviours = BehaviourSource(self.root)

        def accepted(gate_signature):
            found = set()
            body = self.function_body(controller, gate_signature)
            helpers = re.findall(r"MetatileBehavior_Is(\w+)\(", body)
            self.assertTrue(helpers, "%s must call behaviour predicates" % gate_signature)
            for helper in helpers:
                found.update(
                    re.findall(
                        r"==\s*(MB_[A-Z0-9_]+)",
                        self.function_body(
                            behaviours_source, "MetatileBehavior_Is%s(u8 metatileBehavior)" % helper
                        ),
                    )
                )
            return found

        step = accepted("static bool8 IsWarpMetatileBehavior(u16 metatileBehavior)")
        arrow = accepted(
            "static bool8 IsArrowWarpMetatileBehavior(u16 metatileBehavior, "
            "enum Direction direction)"
        )
        door = set(re.findall(
            r"==\s*(MB_[A-Z0-9_]+)",
            self.function_body(behaviours_source, "MetatileBehavior_IsWarpDoor(u8 metatileBehavior)"),
        ))

        self.assertEqual(step, set(behaviours.paths["step"]))
        self.assertEqual(arrow, set(behaviours.paths["arrow"]))
        self.assertEqual(door, set(behaviours.paths["door"]))
        # Spot-check the two entries a name-based transcription gets wrong.
        self.assertIn("MB_DEEP_SOUTH_WARP", behaviours.paths["step"])
        self.assertIn("MB_BRIDGE_OVER_OCEAN", behaviours.paths["step"])
        # And MB_NORMAL must be in none of them, since it is the zero-fill of every attribute array.
        for path in ("step", "arrow", "door"):
            self.assertNotIn("MB_NORMAL", behaviours.paths[path])

    def test_step_on_warps_require_the_warp_predicate_not_just_a_warp_def(self):
        """A warp_def on ordinary floor must not be reported reachable.

        `TryStartWarpEventScript` is the step-on path and it checks `IsWarpMetatileBehavior` before
        `DoWarp`. This asserts the analyzer honours that, using the pinned predicate set rather than
        the analyzer's own view of it.
        """
        source = Source(self.root)
        behaviours = source.behaviours
        # Cinnabar (surfable deep water) and Fuchsia (ordinary floor, unwalkable) both declare a
        # warp_def and neither tile satisfies any warp predicate.
        for name, x, y in (("CinnabarIsland_hns", 41, 1), ("FuchsiaCity_hns", 19, 30)):
            activation = source.warp_activation(name, x, y)
            self.assertIsNotNone(activation)
            self.assertTrue(activation["warp_event_present"], "%s must declare a warp" % name)
            self.assertFalse(activation["reachable"], "%s must not be reachable" % name)
            self.assertNotIn(activation["behaviour"], behaviours.paths["step"])
            self.assertNotIn(activation["behaviour"], behaviours.paths["arrow"])
            self.assertNotIn(activation["behaviour"], behaviours.paths["door"])

    def test_the_real_cross_region_tiles_classify_as_the_engine_requires(self):
        """The four cross-region transitions this build actually has, checked tile by tile.

        If the boundary or the predicate set regresses, at least one of these flips, so a wrong
        model cannot quietly reproduce the same committed inventory.
        """
        source = Source(self.root)
        # (behaviour, path, reachable, trigger tile, presses)
        expected = {
            # An arrow warp is direction-specific: this one takes a south press only.
            ("ReceptionGate_hns", 20, 9): (
                "MB_SOUTH_ARROW_WARP", "arrow", True, (20, 9), ["DOWN"],
            ),
            # An animated door is IMPASSABLE, so the step-on path cannot apply: it is entered by
            # walking north into it from the tile directly south.
            ("Route22_hns", 12, 9): (
                "MB_ANIMATED_DOOR", "door", True, (12, 10), ["UP"],
            ),
            # A non-animated door IS walkable, so the step-on path applies to the tile itself.
            ("MtSilver_1F_WaterfallRoom_hns", 43, 7): (
                "MB_NON_ANIMATED_DOOR", "step", True, (43, 7), ["UP"],
            ),
            ("SnowsweptCavern_hns", 50, 68): (
                "MB_SOUTH_ARROW_WARP", "arrow", True, (50, 68), ["DOWN"],
            ),
            ("CinnabarIsland_hns", 41, 1): ("MB_OCEAN_WATER", None, False, None, []),
            ("FuchsiaCity_hns", 19, 30): ("MB_NORMAL", None, False, None, []),
        }
        for (name, x, y), (behaviour, path, reachable, trigger, presses) in expected.items():
            with self.subTest(map=name, tile=(x, y)):
                activation = source.warp_activation(name, x, y)
                self.assertIsNotNone(activation, "behaviour must resolve")
                self.assertEqual(behaviour, activation["behaviour"])
                self.assertEqual(reachable, activation["reachable"])
                self.assertEqual(path, activation["path"])
                self.assertEqual(presses, list(activation.get("directions") or []))
                if trigger is not None:
                    self.assertEqual(trigger, tuple(activation["trigger_tile"]))
                # A door path must never claim the player stands on the door, and a step path must
                # never be reported for an impassable tile.
                if path == "door":
                    self.assertFalse(activation["tile_walkable"])
                if path == "step":
                    self.assertTrue(activation["tile_walkable"])


class MetaTest(unittest.TestCase):
    """Guards the guard: the checks above are worthless if they are not wired to the real code."""

    def source(self):
        path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "hns_route.py")
        with open(path, encoding="utf-8") as handle:
            return handle.read()

    def test_the_real_pinned_constant_is_the_release(self):
        # The provenance tests patch the constant, so its shipped value is pinned here: a weakened
        # constant would let the tool accept a different revision in production.
        self.assertEqual(
            "1f42b74dff0e9fe942419845d040663dd829a973", hns_route.PINNED_COMMIT_SHA
        )
        self.assertEqual("Release-v2.0.5", hns_route.PINNED_TAG)
        self.assertIn("data/layouts/", hns_route.REQUIRED_CLEAN_SOURCES)
        self.assertIn("data/tilesets/", hns_route.REQUIRED_CLEAN_SOURCES)

    def test_every_command_goes_through_the_verified_source(self):
        source = self.source()
        main = source[source.index("def main():"):]
        # The CLI must build its Source through the verifying helper, never by calling the
        # constructor directly, or a command could bypass provenance.
        self.assertIn("args.func(verified_source(args.upstream_dir), args)", main)
        self.assertNotIn("Source(find_upstream(", main)
        helper = source[source.index("def verified_source("):]
        helper = helper[:helper.index("\n\n\n")]
        self.assertIn("verify_provenance(root)", helper)

    def test_verify_provenance_fails_closed_by_raising(self):
        source = self.source()
        body = source[source.index("def verify_provenance("):]
        body = body[:body.index("\n\ndef ")]
        self.assertIn("raise SystemExit", body)
        # And there must be no path that returns a Source for an unverified tree.
        self.assertNotIn("return Source(", body)


if __name__ == "__main__":
    unittest.main()
