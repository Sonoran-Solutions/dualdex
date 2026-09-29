"""Tests for the H&S differential damage oracle infrastructure (issue #90).

These run in ./ci.sh test with no ROM, no upstream checkout, no ARM toolchain and no emulator. They
exercise the schema, the canonical serialisation, the fail-closed parsing of pinned test-runner
output, roll ordering, provenance enforcement, the setup-turn planner, the harness-patch guard and
the committed corpus itself.
"""

from __future__ import annotations

import copy
import io
import json
import subprocess
import sys
import tarfile
import tempfile
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parent))

import generate_hns_damage_oracle as cli  # noqa: E402
import oracle_backend as backend  # noqa: E402
import oracle_matrix as matrix  # noqa: E402
import oracle_schema as schema  # noqa: E402

SCENARIOS = matrix.build_scenarios()


def a_scenario(**changes) -> dict:
    s = copy.deepcopy(next(x for x in SCENARIOS if x["id"] == "xref-c4a-neutral-base"))
    s.update(changes)
    return s


def observed_for(s: dict) -> dict:
    battler = {"speciesId": 68, "types": ["Fighting"],
               "baseStats": {"hp": 90, "attack": 130, "defense": 80, "spAttack": 65, "spDefense": 85, "speed": 55},
               "abilityId": 15, "itemId": 0, "hpAtHit": s["attacker"]["stats"]["hp"],
               "status1": {"none": 0, "poison": 8, "burn": 16, "toxic": 128}[s["attacker"]["status"]],
               "badgeBoosts": {"attack": False, "defense": False, "spAttack": False, "spDefense": False}}
    dfn = copy.deepcopy(battler)
    dfn["hpAtHit"] = s["defender"]["stats"]["hp"]
    dfn["status1"] = {"none": 0, "poison": 8, "burn": 16, "toxic": 128}[s["defender"]["status"]]
    return {"attacker": battler, "defender": dfn,
            "move": {"id": 157, "type": "Rock", "power": 75, "category": "physical", "target": "both",
                     "flags": [], "abilityFlags": [], "priority": 0, "targetClass": 6, "ateBoost": False},
            "targetCount": 1,
            "fieldStatuses": {"none": 0, "grassy": 1 << 6, "electric": 1 << 8}[s["field"]["terrain"]]}


ROLLS = [51, 51, 52, 52, 53, 54, 54, 55, 55, 56, 57, 57, 58, 58, 59, 60]


def good_provenance() -> dict:
    return {
        "hnsUpstream": {"repository": schema.HNS_REPOSITORY, "commit": schema.HNS_PINNED_COMMIT,
                        "tree": schema.HNS_PINNED_TREE},
        "backend": {"kind": schema.ORACLE_BACKEND_KIND, "build": backend.BUILD_COMMAND,
                    "runner": backend.RUNNER_DESCRIPTION, "mgbaRomTestSha256": "a" * 64,
                    "harnessPatches": [{"path": "patches/x.patch", "sha256": "b" * 64}],
                    "prunedUpstreamTests": True},
        "toolchain": {"gcc": "arm-none-eabi-gcc (Arm GNU Toolchain 13.2.rel1) 13.2.1", "gccSha256": "c" * 64,
                      "cc1Sha256": "d" * 64},
        "generator": {"tool": "tools/hns-damage-oracle", "version": schema.ORACLE_TOOL_VERSION,
                      "testSourceSha256": "e" * 64},
    }


def good_corpus() -> dict:
    s = a_scenario()
    return {"schemaVersion": schema.SCHEMA_VERSION, "rollOrder": schema.ROLL_ORDER,
            "provenance": good_provenance(),
            "entries": [{"scenario": s, "observed": observed_for(s), "rolls": list(ROLLS)}]}


class ScenarioSchemaTest(unittest.TestCase):
    def test_matrix_is_valid_large_and_deterministic(self):
        schema.validate_scenarios(SCENARIOS)
        self.assertGreaterEqual(len(SCENARIOS), 1000)
        self.assertEqual(SCENARIOS, matrix.build_scenarios())
        self.assertEqual([s["id"] for s in SCENARIOS], sorted(s["id"] for s in SCENARIOS))
        self.assertEqual({s["surface"] for s in SCENARIOS}, {"modelled", "engine-only"})

    def test_group_c_immunity_cases_include_positive_and_negative_controls(self):
        by_id = {s["id"]: s for s in SCENARIOS}
        expected = {
            "group-c-absorb-volt-absorb", "group-c-absorb-motor-drive", "group-c-absorb-lightning-rod",
            "group-c-absorb-water-absorb", "group-c-absorb-storm-drain", "group-c-absorb-dry-skin",
            "group-c-absorb-sap-sipper", "group-c-absorb-earth-eater", "group-c-absorb-well-baked-body",
            "group-c-absorb-flash-fire", "group-c-flag-soundproof", "group-c-flag-bulletproof",
            "group-c-flag-wind-rider", "group-c-priority-queenly-majesty", "group-c-priority-dazzling",
            "group-c-priority-armor-tail", "group-c-wonder-guard-neutral", "group-c-wonder-guard-resisted",
            "group-c-wonder-guard-chart-immunity", "group-c-levitate",
            "group-c-air-balloon", "group-c-ring-target-does-not-clear-ability",
        }
        self.assertTrue(expected.issubset(by_id))
        self.assertTrue(all(by_id[sid]["expect"] == "immune" for sid in expected))
        self.assertEqual(by_id["group-c-dry-skin-fire-boost"]["expect"], "damage")
        self.assertEqual(by_id["group-c-flag-control-soundproof"]["expect"], "damage")
        self.assertEqual(by_id["group-c-priority-control-dazzling"]["expect"], "damage")
        self.assertEqual(by_id["group-c-wonder-guard-super-effective"]["expect"], "damage")
        self.assertEqual(by_id["group-c-wonder-guard-resisted"]["expect"], "immune")
        self.assertEqual(by_id["group-c-wonder-guard-chart-immunity"]["expect"], "immune")
        self.assertEqual(by_id["group-c-ring-target"]["expect"], "damage")
        self.assertEqual(by_id["group-c-ring-target-control"]["expect"], "immune")

    def test_group_d_attack_stat_matrix_covers_hustle_and_guts_conditions(self):
        by_id = {s["id"]: s for s in SCENARIOS}
        expected_modelled = {
            "group-d-hustle-physical-badge-a255", "group-d-hustle-special-control",
            "group-d-hustle-defender-control", "group-d-guts-burn-physical-badge-crit-a255",
            "group-d-guts-poison-physical", "group-d-guts-physical-no-status-control",
            "group-d-guts-defender-control",
        }
        self.assertTrue(expected_modelled.issubset(by_id))
        self.assertTrue(all(by_id[sid]["surface"] == "modelled" for sid in expected_modelled))
        self.assertEqual(by_id["group-d-guts-special-status-control"]["surface"], "engine-only")
        self.assertEqual(by_id["group-d-guts-burn-physical-badge-crit-a255"]["attacker"]["status"], "burn")
        self.assertEqual(by_id["group-d-guts-poison-physical"]["attacker"]["status"], "poison")
        self.assertEqual(by_id["group-d-guts-physical-no-status-control"]["attacker"]["status"], "none")

    def test_group_d_mixed_rounding_attack_stat_abilities(self):
        by_id = {s["id"]: s for s in SCENARIOS}
        expected_modelled = {
            "group-d-transistor-electric-physical", "group-d-transistor-electric-special",
            "group-d-transistor-non-electric-control", "group-d-transistor-badge-composition",
            "group-d-transistor-crit-negative-stage", "group-d-dragons-maw-dragon-physical",
            "group-d-dragons-maw-dragon-special", "group-d-dragons-maw-non-dragon-control",
            "group-d-dragons-maw-stage-badge", "group-d-rocky-payload-rock-physical",
            "group-d-rocky-payload-non-rock-control", "group-d-rocky-payload-stage",
            "group-d-orichalcum-pulse-physical-sun", "group-d-orichalcum-pulse-physical-sun-badge",
            "group-d-orichalcum-pulse-no-sun-control",
            "group-d-orichalcum-pulse-special-sun-control",
        }
        expected_engine_only = {
            "group-d-orichalcum-pulse-utility-umbrella",
            "group-d-orichalcum-pulse-cloud-nine-raw-sun",
        }
        self.assertTrue(expected_modelled.union(expected_engine_only).issubset(by_id))
        self.assertTrue(all(by_id[sid]["surface"] == "modelled" for sid in expected_modelled))
        self.assertTrue(all(by_id[sid]["surface"] == "engine-only" for sid in expected_engine_only))
        self.assertTrue(by_id["group-d-transistor-badge-composition"]["badges"])
        self.assertTrue(by_id["group-d-transistor-crit-negative-stage"]["crit"])
        self.assertTrue(by_id["group-d-orichalcum-pulse-cloud-nine-raw-sun"]["field"]["weather"] == "sun")

    def test_group_d_base_power_matrix_covers_source_predicates_water_bubble_and_heatproof(self):
        by_id = {s["id"]: s for s in SCENARIOS}
        expected = {
            "group-d-technician-ember", "group-d-technician-swift", "group-d-technician-sludge",
            "group-d-technician-wise-glasses-dry-skin", "group-d-iron-fist-fire-punch",
            "group-d-strong-jaw-bite", "group-d-mega-launcher-aura-sphere",
            "group-d-sharpness-leaf-blade", "group-d-water-bubble-attacker-waterfall",
            "group-d-water-bubble-defender-fire-deferred", "group-d-water-bubble-defender-fire-blast",
            "group-d-water-bubble-defender-nonfire-control",
            "group-d-water-bubble-defender-iron-fist-composition",
            "group-d-water-bubble-defender-wise-glasses-rounding",
            "group-d-water-bubble-defender-ability-shield-mold-breaker",
            "group-d-heatproof-defender-fire-punch", "group-d-heatproof-defender-fire-blast",
            "group-d-heatproof-defender-nonfire-control", "group-d-heatproof-attacker-fire-control",
            "group-d-heatproof-defender-ability-shield-mold-breaker",
            "group-d-dry-skin-defender-fire-control",
            "group-d-steelworker-iron-head", "group-d-toxic-boost-physical-poison",
            "group-d-toxic-boost-physical-toxic", "group-d-toxic-boost-special-poison-control",
            "group-d-flare-boost-special-burn",
        }
        self.assertTrue(expected.issubset(by_id))
        self.assertTrue(all(by_id[sid]["surface"] == "modelled" for sid in expected))
        engine_only = {
            "group-d-water-bubble-defender-mold-breaker-unshielded",
            "group-d-heatproof-defender-mold-breaker-unshielded",
        }
        self.assertTrue(engine_only.issubset(by_id))
        self.assertTrue(all(by_id[sid]["surface"] == "engine-only" for sid in engine_only))
        self.assertEqual(by_id["group-d-technician-ember"]["move"]["label"], "Ember")
        self.assertEqual(by_id["group-d-technician-swift"]["move"]["label"], "Swift")
        self.assertEqual(by_id["group-d-technician-sludge"]["move"]["label"], "Sludge")
        self.assertEqual(by_id["group-d-toxic-boost-physical-toxic"]["attacker"]["status"], "toxic")
        self.assertEqual(by_id["group-d-toxic-boost-special-poison-control"]["move"]["label"], "Psychic")
        self.assertEqual(by_id["group-d-toxic-boost-special-poison-control"]["attacker"]["status"], "poison")
        self.assertEqual(by_id["group-d-water-bubble-defender-wise-glasses-rounding"]["move"]["label"], "Fire Blast")

    def test_group_d_punk_rock_and_steely_spirit_matrix(self):
        by_id = {s["id"]: s for s in SCENARIOS}
        expected = {
            "group-d-punk-rock-attacker-hyper-voice",
            "group-d-punk-rock-attacker-nonsound-control",
            "group-d-punk-rock-attacker-wise-glasses-relic-song",
            "group-d-punk-rock-defender-hyper-voice",
            "group-d-punk-rock-defender-nonsound-control",
            "group-d-punk-rock-defender-light-screen",
            "group-d-punk-rock-defender-low-damage-rounding",
            "group-d-punk-rock-soundproof-immunity",
            "group-d-liquid-voice-punk-rock-defender",
            "group-d-liquid-voice-soundproof-immunity",
            "group-d-steely-spirit-iron-head",
            "group-d-steely-spirit-nonsteel-control",
            "group-d-steely-spirit-defender-control",
            "group-d-normalize-steel-source-final-normal",
        }
        self.assertTrue(expected.issubset(by_id))
        self.assertTrue(all(by_id[sid]["surface"] == "modelled" for sid in expected))
        self.assertEqual(by_id["group-d-punk-rock-attacker-wise-glasses-relic-song"]["move"]["label"],
                         "Relic Song")
        self.assertEqual(by_id["group-d-punk-rock-soundproof-immunity"]["expect"], "immune")
        self.assertEqual(by_id["group-d-liquid-voice-soundproof-immunity"]["expect"], "immune")
        self.assertEqual(by_id["group-d-normalize-steel-source-final-normal"]["attacker"]["ability"],
                         "ABILITY_NORMALIZE")

    def test_group_d_move_type_rewrite_matrix_covers_full_ate_and_liquid_voice_behavior(self):
        by_id = {s["id"]: s for s in SCENARIOS}
        expected = {
            "group-d-refrigerate-tackle-positive", "group-d-refrigerate-fire-punch-control",
            "group-d-pixilate-tackle-positive", "group-d-pixilate-fire-punch-control",
            "group-d-aerilate-tackle-positive", "group-d-aerilate-fire-punch-control",
            "group-d-galvanize-tackle-positive", "group-d-galvanize-fire-punch-control",
            "group-d-normalize-tackle-same-type-ate-boost", "group-d-normalize-fire-punch-dry-skin-control",
            "group-d-normalize-hyper-voice-wise-glasses", "group-d-pixilate-type-based-category",
            "group-d-pixilate-fairy-toggle-off", "group-d-pixilate-fairy-wind-fairy-on",
            "group-d-pixilate-fairy-wind-fairy-off", "group-d-pixilate-gains-fairy-stab",
            "group-d-pixilate-loses-normal-stab", "group-d-pixilate-super-effective-wonder-guard",
            "group-d-normal-tackle-wonder-guard-immunity-control",
            "group-d-refrigerate-removes-normal-immunity", "group-d-normal-tackle-ghost-immunity-control",
            "group-d-galvanize-ground-immunity", "group-d-galvanize-volt-absorb-immunity",
            "group-d-liquid-voice-hyper-voice-positive", "group-d-liquid-voice-water-absorb-immunity",
            "group-d-liquid-voice-nonsound-control", "group-d-liquid-voice-defender-control",
            "group-d-ate-rounding-wise-glasses",
        }
        self.assertTrue(expected.issubset(by_id))
        self.assertTrue(all(by_id[sid]["surface"] == "modelled" for sid in expected))
        self.assertEqual(by_id["group-d-pixilate-type-based-category"]["rules"]["optionStyle"], "typeBased")
        self.assertTrue(by_id["group-d-pixilate-fairy-wind-fairy-on"]["rules"]["fairyTypes"])
        self.assertFalse(by_id["group-d-pixilate-fairy-wind-fairy-off"]["rules"]["fairyTypes"])
        self.assertEqual(by_id["group-d-liquid-voice-hyper-voice-positive"]["move"]["label"], "Hyper Voice")
        self.assertEqual(by_id["group-d-liquid-voice-nonsound-control"]["move"]["label"], "Tackle")
        self.assertEqual(by_id["group-d-galvanize-ground-immunity"]["expect"], "immune")
        self.assertEqual(by_id["group-d-pixilate-super-effective-wonder-guard"]["expect"], "damage")
        self.assertEqual(by_id["group-d-normal-tackle-wonder-guard-immunity-control"]["expect"], "immune")
        self.assertEqual(by_id["group-d-normal-tackle-ghost-immunity-control"]["expect"], "immune")

    def test_field_backed_ability_matrix_has_exact_and_negative_controls(self):
        by_id = {s["id"]: s for s in SCENARIOS}
        expected = {
            "field-grass-pelt-grassy-physical", "field-grass-pelt-no-terrain",
            "field-grass-pelt-special-control", "field-grass-pelt-attacker-control",
            "field-grass-pelt-defense-stage-composition", "field-hadron-electric-special",
            "field-hadron-terrain-replaced", "field-hadron-physical-control",
            "field-hadron-defender-control", "field-hadron-special-badge-composition",
        }
        self.assertTrue(expected <= by_id.keys())
        self.assertTrue(all(by_id[sid]["surface"] == "modelled" for sid in expected))
        self.assertEqual(by_id["field-grass-pelt-grassy-physical"]["field"]["terrain"], "grassy")
        self.assertEqual(by_id["field-hadron-electric-special"]["field"]["terrain"], "electric")
        self.assertEqual(by_id["field-hadron-terrain-replaced"]["field"]["terrain"], "grassy")
        self.assertEqual(by_id["field-hadron-special-badge-composition"]["badges"], [1])

    def test_move_level_ability_bypass_respects_pinned_breakability(self):
        by_id = {s["id"]: s for s in SCENARIOS}
        expected = {
            "final-move-bypass-prism-armor-preserved": "Prism Armor",
            "final-move-bypass-shadow-shield-preserved": "Shadow Shield",
            "final-move-bypass-filter-suppressed": "Filter",
        }
        self.assertTrue(set(expected).issubset(by_id))
        for sid, ability in expected.items():
            with self.subTest(scenario=sid):
                scenario = by_id[sid]
                self.assertEqual(scenario["surface"], "modelled")
                self.assertEqual(scenario["defender"]["abilityLabel"], ability)
                expected_move = "Moongeist Beam" if "shadow" in sid else "Sunsteel Strike"
                self.assertEqual(scenario["move"]["label"], expected_move)
                self.assertIn("move-ability-bypass", scenario["tags"])
        shadow = by_id["final-move-bypass-shadow-shield-preserved"]["defender"]["stats"]
        self.assertEqual(shadow["hp"], shadow["maxHp"])

    def test_duplicate_ids_rejected(self):
        with self.assertRaisesRegex(schema.SchemaError, "duplicate scenario id"):
            schema.validate_scenarios([a_scenario(), a_scenario()])

    def test_unknown_and_missing_fields_rejected(self):
        s = a_scenario()
        s["extra"] = 1
        with self.assertRaisesRegex(schema.SchemaError, "unknown field"):
            schema.validate_scenario(s)
        s = a_scenario()
        del s["crit"]
        with self.assertRaisesRegex(schema.SchemaError, "missing field"):
            schema.validate_scenario(s)

    def test_out_of_domain_operands_rejected(self):
        mutations = [
            ("id", lambda s: s.update(id="Bad ID")),
            ("level", lambda s: s["attacker"].update(level=0)),
            ("level", lambda s: s["attacker"].update(level=101)),
            ("stat", lambda s: s["attacker"]["stats"].update(attack=0)),
            ("stat", lambda s: s["attacker"]["stats"].update(attack=True)),
            ("hp", lambda s: s["attacker"]["stats"].update(hp=999)),
            ("stage", lambda s: s["attacker"]["stages"].update(attack=7)),
            ("stage", lambda s: s["attacker"]["stages"].update(defense=1)),
            ("symbol", lambda s: s["attacker"].update(species="MACHAMP")),
            ("ability", lambda s: s["attacker"].update(ability="ABILITY_NONE")),
            ("item label", lambda s: s["attacker"].update(itemLabel="Charcoal")),
            ("status", lambda s: s["attacker"].update(status="sleep")),
            ("defender status", lambda s: s["defender"].update(status="burn")),
            ("weather", lambda s: s["field"].update(weather="hail")),
            ("style", lambda s: s["rules"].update(optionStyle="gen3")),
            ("badges", lambda s: s.update(badges=[7, 1])),
            ("badges", lambda s: s.update(badges=[9])),
            ("doubles", lambda s: s.update(doubles={"defenderPartner": "present"})),
            ("format", lambda s: s.update(format="doubles")),
            ("tags", lambda s: s.update(tags=["b", "a"])),
            ("expect", lambda s: s.update(expect="zero")),
            ("defender hp", lambda s: s["defender"]["stats"].update(hp=100, maxHp=100)),
        ]
        for name, mutate in mutations:
            s = a_scenario()
            mutate(s)
            with self.subTest(name=name), self.assertRaises(schema.SchemaError):
                schema.validate_scenario(s)


class CorpusSchemaTest(unittest.TestCase):
    def test_good_corpus_validates_and_round_trips(self):
        doc = good_corpus()
        schema.validate_corpus(doc)
        text = schema.canonical_dumps(doc)
        self.assertEqual(text, schema.canonical_dumps(json.loads(text)))
        self.assertEqual(schema.load_corpus_text(text), doc)

    def test_serialisation_independent_of_key_order(self):
        doc = good_corpus()
        shuffled = json.loads(json.dumps(doc, sort_keys=False))
        shuffled["entries"][0] = dict(reversed(list(shuffled["entries"][0].items())))
        self.assertEqual(schema.canonical_dumps(doc), schema.canonical_dumps(shuffled))

    def test_non_canonical_text_rejected(self):
        text = schema.canonical_dumps(good_corpus())
        with self.assertRaisesRegex(schema.SchemaError, "canonical"):
            schema.load_corpus_text(text.replace("\n", "\n ", 1))

    def test_all_sixteen_rolls_required(self):
        for bad in (ROLLS[:15], ROLLS + [60], ROLLS[:15] + [60.5], ROLLS[:15] + ["60"], list(reversed(ROLLS))):
            doc = good_corpus()
            doc["entries"][0]["rolls"] = bad
            with self.subTest(bad=bad), self.assertRaises(schema.SchemaError):
                schema.validate_corpus(doc)

    def test_zero_is_representable_only_for_immunity(self):
        doc = good_corpus()
        doc["entries"][0]["rolls"] = [0] * 16
        with self.assertRaisesRegex(schema.SchemaError, "zero roll"):
            schema.validate_corpus(doc)
        doc["entries"][0]["scenario"]["expect"] = "immune"
        schema.validate_corpus(doc)
        doc["entries"][0]["rolls"] = [0] * 15 + [1]
        with self.assertRaisesRegex(schema.SchemaError, "immune"):
            schema.validate_corpus(doc)

    def test_duplicate_or_unsorted_entries_rejected(self):
        doc = good_corpus()
        doc["entries"].append(copy.deepcopy(doc["entries"][0]))
        with self.assertRaisesRegex(schema.SchemaError, "duplicate"):
            schema.validate_corpus(doc)

    def test_provenance_pinned_commit_enforced(self):
        mutations = [
            lambda p: p["hnsUpstream"].update(commit="0" * 40),
            lambda p: p["hnsUpstream"].update(tree="0" * 40),
            lambda p: p["hnsUpstream"].update(repository="rh-hideout/pokeemerald-expansion"),
            lambda p: p["backend"].update(kind="smogon-calc"),
            lambda p: p["generator"].update(version=99),
            lambda p: p["toolchain"].update(gccSha256="not-a-hash"),
            lambda p: p.pop("toolchain"),
        ]
        for mutate in mutations:
            doc = good_corpus()
            mutate(doc["provenance"])
            with self.assertRaises(schema.SchemaError):
                schema.validate_corpus(doc)

    def test_machine_paths_and_timestamps_rejected(self):
        for poison in ("/home/dq/rom.gba", "/tmp/work", "C:\\\\Users\\\\x", "2026-09-26T08:43:00"):
            doc = good_corpus()
            doc["provenance"]["backend"]["runner"] = poison
            with self.subTest(poison=poison), self.assertRaises(schema.SchemaError):
                schema.canonical_dumps(doc)

    def test_observation_consistency(self):
        doc = good_corpus()
        doc["entries"][0]["observed"]["targetCount"] = 4
        with self.assertRaises(schema.SchemaError):
            schema.validate_corpus(doc)
        doc = good_corpus()
        doc["entries"][0]["observed"]["defender"]["hpAtHit"] = 5
        with self.assertRaises(schema.SchemaError):
            schema.validate_corpus(doc)


def runner_lines(sid: str, rng: int, damage: int, *, delta=None, hp_at_hit=200, types=("Fighting", "Fighting"),
                 def_stage=0, atk_status="none", atk_status1=0, def_status="none", def_status1=0,
                 move_ability_flags=(), field_statuses=0) -> list[str]:
    delta = damage if delta is None else delta
    t = f"{types[0]}|{types[1]}|Mystery"
    return [
        f"DDXO|{sid}|{rng}|A1|68|50|{t}|15|0|{atk_status}|{atk_status1}",
        f"DDXO|{sid}|{rng}|A2|200|200|150|100|75|100|80",
        f"DDXO|{sid}|{rng}|A3|0|0|0|0",
        f"DDXO|{sid}|{rng}|A4|90|130|80|65|85|55",
        f"DDXO|{sid}|{rng}|A5|0|0|0|0",
        f"DDXO|{sid}|{rng}|D1|143|50|Normal|Normal|Mystery|15|0|{def_status}|{def_status1}",
        f"DDXO|{sid}|{rng}|D2|{60000 - delta}|60000|100|85|100|130|40",
        f"DDXO|{sid}|{rng}|D3|0|{def_stage}|0|0",
        f"DDXO|{sid}|{rng}|D4|160|110|65|65|110|30",
        f"DDXO|{sid}|{rng}|D5|0|0|0|0",
        "DDXO|{}|{}|M|157|Rock|75|physical|both|1|157|0|0|0|0|0|0|6|{}|0".format(
            sid, rng, "|".join("1" if flag in move_ability_flags else "0" for flag in
                                ("punchingMove", "bitingMove", "pulseMove", "slicingMove"))),
        f"DDXO|{sid}|{rng}|F|none|0|0|0|1|0|{field_statuses}",
        f"DDXO|{sid}|{rng}|R|{damage}|{delta}|{hp_at_hit}",
    ]


def runner_output(sid: str, *, result="PASS", skip_rng=None, extra=None, damage_for=None,
                  atk_status="none", atk_status1=0, def_status="none", def_status1=0,
                  move_ability_flags=(), field_statuses=0) -> str:
    lines = [f"[0] DDXO {sid}: \x1b[32m{result}\x1b[0m"]
    for rng in range(16):
        if rng == skip_rng:
            continue
        damage = damage_for(rng) if damage_for else 60 - rng // 2
        lines += runner_lines(sid, rng, damage, atk_status=atk_status, atk_status1=atk_status1,
                              def_status=def_status, def_status1=def_status1,
                              move_ability_flags=move_ability_flags, field_statuses=field_statuses)
    lines += extra or []
    return "\n".join(lines) + "\n"


class RunnerOutputTest(unittest.TestCase):
    sid = "xref-c4a-neutral-base"

    def test_well_formed_output_assembles_in_roll_order(self):
        records = backend.parse_runner_output(runner_output(self.sid), [self.sid])
        entry = backend.assemble_entry(a_scenario(), records[self.sid])
        # rng value v is the (100 - v)% factor, so rolls[k] = rng 15 - k.
        self.assertEqual(entry["rolls"][15], 60)
        self.assertEqual(entry["rolls"][0], 60 - 15 // 2)
        self.assertEqual(entry["rolls"], sorted(entry["rolls"]))
        self.assertEqual(entry["observed"]["move"], {"id": 157, "type": "Rock", "power": 75,
                                                     "category": "physical", "target": "both", "flags": [],
                                                     "abilityFlags": [], "priority": 0, "targetClass": 6,
                                                     "ateBoost": False})
        self.assertEqual(entry["observed"]["defender"]["types"], ["Normal"])
        self.assertEqual(entry["observed"]["attacker"]["status1"], 0)

    def test_generated_runner_records_effective_move_type_and_ate_boost(self):
        self.assertIn("DdxoType(GetBattleMoveType(move))", backend.C_PRELUDE)
        self.assertIn("gBattleStruct->battlerState[battlerAtk].ateBoost", backend.C_PRELUDE)

    def test_raw_status1_is_parsed_and_retained(self):
        records = backend.parse_runner_output(
            runner_output(self.sid, atk_status="burn", atk_status1=16), [self.sid])
        self.assertEqual(records[self.sid][0]["A1"][8], "16")
        scenario = a_scenario()
        scenario["attacker"]["status"] = "burn"
        entry = backend.assemble_entry(scenario, records[self.sid])
        self.assertEqual(entry["observed"]["attacker"]["status1"], 16)

    def test_source_move_ability_flags_are_parsed_as_separate_metadata(self):
        records = backend.parse_runner_output(
            runner_output(self.sid, move_ability_flags={"punchingMove", "slicingMove"}), [self.sid])
        entry = backend.assemble_entry(a_scenario(), records[self.sid])
        self.assertEqual(entry["observed"]["move"]["abilityFlags"], ["punchingMove", "slicingMove"])

    def test_failed_or_missing_test_result_is_fatal(self):
        for result in ("FAIL", "ASSUMPTIONS_FAILED", "TO_DO"):
            with self.subTest(result=result), self.assertRaisesRegex(backend.OracleError, "expected PASS"):
                backend.parse_runner_output(runner_output(self.sid, result=result), [self.sid])
        text = runner_output(self.sid).split("\n", 1)[1]
        with self.assertRaisesRegex(backend.OracleError, "no result"):
            backend.parse_runner_output(text, [self.sid])

    def test_nonzero_hydra_exit_rejects_otherwise_parseable_output(self):
        with tempfile.TemporaryDirectory() as tmp:
            work = Path(tmp)
            (work / "pokehns-test.elf").write_bytes(b"test elf")
            process = mock.Mock(returncode=7, stdout=f"[0] DDXO {self.sid}: PASS\nrunner failed\n")
            with mock.patch.object(backend, "_run"), mock.patch.object(backend.subprocess, "run", return_value=process):
                with self.assertRaisesRegex(backend.OracleError, "hydra runner exited with status 7") as caught:
                    backend.build_and_run(work, work / "toolchain", {}, 1)
            self.assertIn("runner failed", str(caught.exception))

    def test_missing_roll_is_fatal(self):
        with self.assertRaisesRegex(backend.OracleError, "missing oracle output"):
            backend.parse_runner_output(runner_output(self.sid, skip_rng=7), [self.sid])

    def test_malformed_duplicate_and_unknown_lines_are_fatal(self):
        cases = [
            ([f"DDXO|{self.sid}|3|R|1|1|200"], "duplicate"),
            ([f"DDXO|{self.sid}|3|R|1|1"], "fields"),
            ([f"DDXO|{self.sid}|x|R|1|1|200"], "malformed integer"),
            ([f"DDXO|{self.sid}|16|R|1|1|200"], "out of range"),
            (["DDXO|someone-else|0|R|1|1|200"], "unknown scenario"),
            ([f"DDXO|{self.sid}|0|Q|1"], "unknown oracle line kind"),
        ]
        for extra, message in cases:
            with self.subTest(message=message), self.assertRaisesRegex(backend.OracleError, message):
                backend.parse_runner_output(runner_output(self.sid, extra=extra), [self.sid])

    def test_state_leak_between_rolls_is_fatal(self):
        text = runner_output(self.sid)
        text = text.replace(f"DDXO|{self.sid}|9|A4|90|130", f"DDXO|{self.sid}|9|A4|91|130")
        records = backend.parse_runner_output(text, [self.sid])
        with self.assertRaisesRegex(backend.OracleError, "differ between rolls"):
            backend.assemble_entry(a_scenario(), records[self.sid])

    def test_failures_are_never_turned_into_zero(self):
        records = backend.parse_runner_output(runner_output(self.sid, damage_for=lambda r: 0), [self.sid])
        with self.assertRaisesRegex(backend.OracleError, "measured 0"):
            backend.assemble_entry(a_scenario(), records[self.sid])

    def test_hp_bar_and_hp_delta_must_agree(self):
        text = runner_output(self.sid).replace(f"DDXO|{self.sid}|4|R|58|58|", f"DDXO|{self.sid}|4|R|58|57|")
        records = backend.parse_runner_output(text, [self.sid])
        with self.assertRaisesRegex(backend.OracleError, "HP delta"):
            backend.assemble_entry(a_scenario(), records[self.sid])

    def test_battle_state_must_match_the_scenario(self):
        s = a_scenario()
        s["defender"]["stages"]["defense"] = -1
        records = backend.parse_runner_output(runner_output(self.sid), [self.sid])
        with self.assertRaisesRegex(backend.OracleError, "defense stage"):
            backend.assemble_entry(s, records[self.sid])


class SetupPlannerTest(unittest.TestCase):
    DELTAS = {"MOVE_SWORDS_DANCE": 2, "MOVE_MEDITATE": 1, "MOVE_FEATHER_DANCE": -2, "MOVE_GROWL": -1,
              "MOVE_NASTY_PLOT": 2, "MOVE_EERIE_IMPULSE": -2, "MOVE_CONFIDE": -1, "MOVE_IRON_DEFENSE": 2,
              "MOVE_HARDEN": 1, "MOVE_SCREECH": -2, "MOVE_LEER": -1, "MOVE_AMNESIA": 2, "MOVE_FAKE_TEARS": -2}

    def test_every_stage_value_is_reached_and_clamped_correctly(self):
        for role, stat in (("attacker", "attack"), ("attacker", "spAttack"), ("defender", "defense"),
                           ("defender", "spDefense")):
            for value in range(-6, 7):
                stage = 0
                for _actor, move in backend._stage_actions(role, stat, value):
                    delta = self.DELTAS.get(move, 1 if move == "MOVE_CALM_MIND" else None)
                    stage = max(-6, min(6, stage + delta))
                with self.subTest(role=role, stat=stat, value=value):
                    self.assertEqual(stage, value)

    def test_weather_and_screens_land_on_the_last_setup_turn(self):
        s = a_scenario()
        s["attacker"]["stages"]["attack"] = 4
        s["field"].update(weather="rain", reflect=True, lightScreen=True)
        atk, dfn, ko = backend.plan_setup(s)
        self.assertEqual(atk[-1], "MOVE_RAIN_DANCE")
        self.assertEqual(dfn[-2:], ["MOVE_REFLECT", "MOVE_LIGHT_SCREEN"])
        self.assertEqual(len(atk), len(dfn))
        self.assertFalse(ko)

    def test_terrain_is_established_by_a_pinned_move_on_the_final_setup_turn(self):
        for terrain, move in (("grassy", "MOVE_GRASSY_TERRAIN"), ("electric", "MOVE_ELECTRIC_TERRAIN")):
            s = a_scenario()
            s["field"]["terrain"] = terrain
            atk, dfn, _ = backend.plan_setup(s)
            self.assertEqual(atk[-1], move)
            self.assertEqual(len(atk), len(dfn))

    def test_solar_power_setup_residual_is_captured_as_hp_at_hit(self):
        scenario = next(s for s in SCENARIOS if s["id"] == "group-d-solar-power-special-sun-crit-negative-stage")
        atk_actions, def_actions, _ = backend.plan_setup(scenario)
        setup_turns = max(len(atk_actions), len(def_actions))
        self.assertEqual(setup_turns, 1)
        self.assertEqual(backend._solar_power_setup_ticks(scenario, setup_turns), 1)

        source = backend.render_scenario(scenario)
        self.assertEqual(source.count("HP_BAR(player, captureHP: &results[i].hpAtHit);"), 1)
        self.assertIn("results[i].hpAtHit", source)

        corpus = json.loads(cli.CORPUS_PATH.read_text())
        entry = next(e for e in corpus["entries"] if e["scenario"]["id"] == scenario["id"])
        self.assertEqual(entry["observed"]["attacker"]["hpAtHit"], 175)

    def test_generated_tests_capture_all_rolls_and_force_crit(self):
        files = backend.render_sources(SCENARIOS)
        self.assertEqual(files, backend.render_sources(matrix.build_scenarios()))
        text = "".join(files.values())
        self.assertEqual(text.count('_BATTLE_TEST("DDXO '), len(SCENARIOS))
        self.assertEqual(text.count("PARAMETRIZE { }"), 16 * len(SCENARIOS))
        self.assertEqual(text.count("WITH_RNG(RNG_DAMAGE_MODIFIER, i)"), len(SCENARIOS))
        self.assertEqual(text.count("secondaryEffect: FALSE"), len(SCENARIOS))
        self.assertNotIn("RNGSeed", text)


class HarnessGuardTest(unittest.TestCase):
    def test_patch_touching_battle_code_is_refused(self):
        with tempfile.TemporaryDirectory() as tmp:
            patch = Path(tmp) / "bad.patch"
            patch.write_text("--- a/src/battle_util.c\n+++ b/src/battle_util.c\n@@ -1 +1 @@\n-a\n+b\n")
            with self.assertRaisesRegex(backend.OracleError, "only touch the upstream test harness"):
                backend._apply_patch(Path(tmp), patch)

    def test_committed_harness_patch_is_test_only(self):
        for name in backend.HARNESS_PATCHES:
            text = (backend.PATCH_DIR / name).read_text()
            targets = [line[6:] for line in text.splitlines() if line.startswith("+++ b/")]
            self.assertEqual(targets, ["test/test_runner.c"])

    def test_export_replaces_a_modified_cached_source_tree(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            upstream = root / "upstream"
            upstream.mkdir()
            work = root / "cache"
            source = work / "src/example.c"
            source.parent.mkdir(parents=True)
            source.write_text("modified cached source\n")
            marker = work / ".dualdex-oracle-export"
            marker.write_text("matching marker\n")

            archive = io.BytesIO()
            with tarfile.open(fileobj=archive, mode="w") as tar:
                content = b"pinned archive source\n"
                info = tarfile.TarInfo("src/example.c")
                info.size = len(content)
                tar.addfile(info, io.BytesIO(content))
            result = mock.Mock(returncode=0, stdout=archive.getvalue())
            with mock.patch.object(backend.subprocess, "run", return_value=result), \
                    mock.patch.object(backend, "_apply_patch"):
                backend.export_worktree(upstream, work)
            self.assertEqual(source.read_text(), "pinned archive source\n")
            self.assertFalse(marker.exists())

    def test_backend_provenance_has_no_machine_paths(self):
        with tempfile.TemporaryDirectory() as tmp:
            mgba = Path(tmp) / "tools/mgba/mgba-rom-test"
            mgba.parent.mkdir(parents=True)
            mgba.write_bytes(b"binary")
            prov = backend.backend_provenance(Path(tmp))
            schema.check_no_machine_artifacts(json.dumps(prov))
            self.assertNotIn(tmp, json.dumps(prov))


class HandDerivationTest(unittest.TestCase):
    def test_transcribed_fixture_arithmetic(self):
        # gap_c4a_parity_neutral_base: rolls 51..60.
        self.assertEqual(cli.hand_rolls(level=50, bp=75, atk=150, dfn=85), ROLLS)
        # gap_c1_ghost_steel: 42..49.
        rolls = cli.hand_rolls(level=50, bp=80, atk=150, dfn=170, stab=True)
        self.assertEqual((rolls[0], rolls[15]), (42, 49))


class CommittedCorpusTest(unittest.TestCase):
    """The normal CI path: committed artifacts only, no subprocess, no upstream, no ROM."""

    def test_check_uses_no_external_process_or_upstream(self):
        def refuse(*_a, **_k):
            raise AssertionError("the ROM-free check must not run external processes")
        with mock.patch.object(subprocess, "run", refuse), mock.patch.object(subprocess, "Popen", refuse), \
                mock.patch.dict("os.environ", {"HNS_UPSTREAM_DIR": "/nonexistent"}):
            cli.cmd_check(None)

    def test_committed_corpus_covers_the_matrix(self):
        doc = schema.load_corpus_text(cli.CORPUS_PATH.read_text())
        self.assertEqual([e["scenario"] for e in doc["entries"]], SCENARIOS)
        self.assertGreaterEqual(len(doc["entries"]), 1000)
        for entry in doc["entries"]:
            scenario = entry["scenario"]
            for role in ("attacker", "defender"):
                raw_status = entry["observed"][role]["status1"]
                status = scenario[role]["status"]
                if status == "toxic":
                    self.assertEqual(raw_status & 0x80, 0x80, f"{scenario['id']} {role} toxic bit")
                    self.assertEqual(raw_status & ~(0x80 | 0x0f00), 0,
                                     f"{scenario['id']} {role} unrelated status bits")
                else:
                    expected = {"none": 0, "poison": 8, "burn": 16}[status]
                    self.assertEqual(raw_status, expected, f"{scenario['id']} {role} raw status1")

    def test_group_d_oracle_pins_rewrite_type_category_and_ate_boost(self):
        doc = schema.load_corpus_text(cli.CORPUS_PATH.read_text())
        by_id = {entry["scenario"]["id"]: entry for entry in doc["entries"]}

        expected = {
            "group-d-refrigerate-tackle-positive": ("Ice", "physical", True),
            "group-d-refrigerate-fire-punch-control": ("Fire", "physical", False),
            "group-d-pixilate-tackle-positive": ("Fairy", "physical", True),
            "group-d-aerilate-tackle-positive": ("Flying", "physical", True),
            "group-d-galvanize-tackle-positive": ("Electric", "physical", True),
            "group-d-normalize-tackle-same-type-ate-boost": ("Normal", "physical", True),
            "group-d-normalize-hyper-voice-wise-glasses": ("Normal", "special", True),
            "group-d-pixilate-type-based-category": ("Fairy", "special", True),
            "group-d-pixilate-fairy-wind-fairy-on": ("Fairy", "special", False),
            "group-d-pixilate-fairy-wind-fairy-off": ("Fairy", "special", True),
            "group-d-liquid-voice-hyper-voice-positive": ("Water", "special", False),
            "group-d-liquid-voice-nonsound-control": ("Normal", "physical", False),
        }
        for sid, (move_type, category, ate_boost) in expected.items():
            with self.subTest(scenario=sid):
                entry = by_id[sid]
                observed = entry["observed"]["move"]
                self.assertEqual((observed["type"], observed["category"], observed["ateBoost"]),
                                 (move_type, category, ate_boost))
                self.assertEqual(len(entry["rolls"]), schema.ROLL_COUNT)

    def test_known_divergences_pin_the_current_calculator_vectors(self):
        doc = schema.load_corpus_text(cli.CORPUS_PATH.read_text())
        by_id = {entry["scenario"]["id"]: entry for entry in doc["entries"]}
        divergences = cli.load_divergences(by_id)
        self.assertEqual(
            {record["scenario"] for record in divergences},
            {
                "doubles-dazzling-gleam-partner-present",
                "doubles-dazzling-gleam-partner-present-crit",
            },
        )
        # The two pre-existing #100 records are deliberately unchanged by this #91 slice.
        prior_100_vectors = {
            "doubles-dazzling-gleam-partner-present": [41, 42, 42, 43, 43, 44, 44, 45,
                                                        45, 46, 46, 47, 47, 48, 48, 49],
            "doubles-dazzling-gleam-partner-present-crit": [83, 84, 85, 86, 87, 88, 89, 90,
                                                             91, 92, 93, 94, 95, 96, 97, 98],
        }
        for record in divergences:
            if record["scenario"] in prior_100_vectors:
                self.assertEqual(record["calculatorRolls"], prior_100_vectors[record["scenario"]])
        for record in divergences:
            with self.subTest(scenario=record["scenario"]):
                self.assertEqual(len(record["calculatorRolls"]), schema.ROLL_COUNT)
                self.assertEqual(record["calculatorRolls"], sorted(record["calculatorRolls"]))
                self.assertNotEqual(record["calculatorRolls"], by_id[record["scenario"]]["rolls"])

    def test_divergence_register_rejects_missing_or_nondivergent_vectors(self):
        scenario = "xref-c4a-neutral-base"
        corpus_rolls = list(ROLLS)
        entry = {"scenario": {"id": scenario}, "rolls": corpus_rolls}
        header = {"schemaVersion": 2, "divergences": []}
        record = {"scenario": scenario, "issue": "https://github.com/Sonoran-Solutions/dualdex/issues/97",
                  "summary": "test divergence", "calculatorRolls": list(ROLLS)}
        with self.assertRaises(SystemExit):
            cli.validate_divergences({**header, "divergences": [{k: v for k, v in record.items() if k != "calculatorRolls"}]},
                                     {scenario: entry})
        with self.assertRaises(SystemExit):
            cli.validate_divergences({**header, "divergences": [record]}, {scenario: entry})

    def test_crossref_detects_an_altered_oracle_roll(self):
        doc = schema.load_corpus_text(cli.CORPUS_PATH.read_text())
        by_id = {e["scenario"]["id"]: e for e in doc["entries"]}
        tampered = copy.deepcopy(by_id)
        tampered["xref-c4a-neutral-base"]["rolls"][7] += 1
        with self.assertRaises(SystemExit):
            cli.check_crossref(tampered)
        self.assertGreater(cli.check_crossref(by_id), 20)


if __name__ == "__main__":
    unittest.main()
