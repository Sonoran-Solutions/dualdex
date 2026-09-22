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

import hns_route

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
