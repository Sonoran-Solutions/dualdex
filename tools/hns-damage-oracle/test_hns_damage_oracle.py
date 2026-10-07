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
    runtime = {key: 0 for key in schema.RUNTIME_DOMAINS}
    item_record = {"holdEffect": "HOLD_EFFECT_NONE", "holdEffectParam": 0, "itemType": None}
    battler = {"speciesId": 68, "types": ["Fighting"],
               "baseStats": {"hp": 90, "attack": 130, "defense": 80, "spAttack": 65, "spDefense": 85, "speed": 55},
               "abilityId": 15, "itemId": 0, "hpAtHit": s["attacker"]["stats"]["hp"],
               "status1": {"none": 0, "poison": 8, "burn": 16, "toxic": 128, "paralysis": 64}[s["attacker"]["status"]],
               "badgeBoosts": {"attack": False, "defense": False, "spAttack": False, "spDefense": False},
               "terrainAffected": s["field"]["terrain"] != "none", "runtime": runtime,
               "itemRecord": item_record}
    dfn = copy.deepcopy(battler)
    dfn["hpAtHit"] = s["defender"]["stats"]["hp"]
    dfn["status1"] = {"none": 0, "poison": 8, "burn": 16, "toxic": 128, "paralysis": 64}[s["defender"]["status"]]
    return {"attacker": battler, "defender": dfn,
            "move": {"id": 157, "type": "Rock", "power": 75, "category": "physical", "target": "both",
                     "flags": [], "abilityFlags": [], "effect": "EFFECT_HIT", "ordinary": True,
                     "makesContact": False, "punchingMove": False, "sheerForceAffected": None,
                     "priority": 0, "targetClass": 6, "ateBoost": False},
            "targetCount": 1,
            "fieldStatuses": ({"none": 0, "grassy": 1 << 6, "electric": 1 << 8,
                               "misty": 1 << 7, "psychic": 1 << 9}[s["field"]["terrain"]]
                              | ((1 << 5) if s["field"]["gravity"] else 0)
                              | (4 if (s.get("stateSetup") or {}).get("wonderRoom") else 0)
                              | (1 if (s.get("stateSetup") or {}).get("magicRoom") else 0))}


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
    def test_rollout_observed_identity_chain_and_recharge_fail_closed(self):
        scenario = next(s for s in SCENARIOS if s["id"] == "rollout-rollout-counter-1-curl-0")
        observed = observed_for(scenario)
        observed["move"].update(id=205, power=30, effect="EFFECT_ROLLOUT", ordinary=False)
        observed["rollout"] = dict(attacker=0, moveId=205, effectId=91, timer=1,
            defenseCurl=0, multipleTurns=1, lockedMove=205, rechargeTimer=0, electrified=0, basePower=60, effectiveType="Rock")
        schema.validate_observed(observed, scenario, "rollout")
        for key, value in (("attacker",1),("timer",5),("defenseCurl",2),("multipleTurns",0),
                ("lockedMove",301),("rechargeTimer",1),("electrified",2),("effectiveType","Electric"),("basePower",30),("moveId",301),("effectId",0)):
            changed = copy.deepcopy(observed)
            changed["rollout"][key] = value
            with self.subTest(key=key), self.assertRaises(schema.SchemaError):
                schema.validate_observed(changed, scenario, "rollout")
        del observed["rollout"]["timer"]
        with self.assertRaises(schema.SchemaError):
            schema.validate_observed(observed, scenario, "rollout")

    def test_explosion_requires_actual_zero_hp_at_damage(self):
        scenario = next(s for s in SCENARIOS if s["id"] == "explosion-defeatist")
        observed = observed_for(scenario)
        with self.assertRaises(schema.SchemaError):
            schema.validate_observed(observed, scenario, "explosion")
        observed["explosionUserHpAtDamage"] = 1
        with self.assertRaises(schema.SchemaError):
            schema.validate_observed(observed, scenario, "explosion")
        observed["explosionUserHpAtDamage"] = 0
        schema.validate_observed(observed, scenario, "explosion")
        ordinary = a_scenario()
        unexpected = observed_for(ordinary)
        unexpected["explosionUserHpAtDamage"] = 0
        with self.assertRaises(schema.SchemaError):
            schema.validate_observed(unexpected, ordinary, "ordinary")

    def test_observed_field_status_includes_magic_room_and_wonder_room(self):
        for scenario_id, expected_bits in (
            ("group-d-item-charcoal-magic-room-suppressed", 1),
            ("group-d-item-eviolite-wonder-room", 4),
        ):
            scenario = next(s for s in SCENARIOS if s["id"] == scenario_id)
            observed = observed_for(scenario)
            self.assertEqual(observed["fieldStatuses"], expected_bits)
            schema.validate_observed(observed, scenario, f"observed[{scenario_id}]")
            observed["fieldStatuses"] = 0
            with self.assertRaises(schema.SchemaError):
                schema.validate_observed(observed, scenario, f"observed[{scenario_id}]")

    def test_item_secondary_ids_follow_pinned_type_enum_slots(self):
        self.assertEqual(schema.TYPE_SECONDARY_IDS["Normal"], 1)
        self.assertEqual(schema.TYPE_SECONDARY_IDS["Fighting"], 2)
        self.assertEqual(schema.TYPE_SECONDARY_IDS["Steel"], 9)
        self.assertEqual(schema.TYPE_SECONDARY_IDS["Fire"], 11)  # TYPE_MYSTERY reserves ID 10.
        self.assertEqual(schema.TYPE_SECONDARY_IDS["Fairy"], 19)
        self.assertEqual(schema.expected_item_secondary_id(
            {"holdEffect": "HOLD_EFFECT_GEMS", "itemType": "Fighting"}), 2)
        self.assertEqual(schema.expected_item_secondary_id(
            {"holdEffect": "HOLD_EFFECT_RESIST_BERRY", "itemType": "Normal"}), 0)

    def test_state_backed_group_d_covers_every_identity_and_rejects_invalid_operands(self):
        selected = [s for s in SCENARIOS if s["id"].startswith("state-d-")]
        abilities = {s[role]["ability"] for s in selected for role in ("attacker", "defender")}
        expected = {"FLASH_FIRE", "RIVALRY", "SLOW_START", "ANALYTIC", "STAKEOUT", "GORILLA_TACTICS",
                    "PROTOSYNTHESIS", "QUARK_DRIVE", "SUPREME_OVERLORD", "DARK_AURA", "FAIRY_AURA",
                    "AURA_BREAK", "VESSEL_OF_RUIN", "SWORD_OF_RUIN", "TABLETS_OF_RUIN", "BEADS_OF_RUIN"}
        self.assertTrue({"ABILITY_" + name for name in expected}.issubset(abilities))
        self.assertTrue(all(s["stateSetup"] for s in selected))
        for field, invalid in (("slowStartTimer", 8), ("paradoxBoostedStat", 7),
                               ("supremeOverlordCounter", 6), ("isFirstTurn", 4),
                               ("dynamaxSelected", 2), ("selectedGimmick", 6), ("flashFireBoosted", -1)):
            s = copy.deepcopy(selected[0])
            s["stateSetup"] = {"attacker": {field: invalid}}
            with self.subTest(field=field), self.assertRaises(schema.SchemaError):
                schema.validate_scenarios([s])

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
        expected_modelled.add("group-d-orichalcum-pulse-utility-umbrella")
        expected_engine_only = {"group-d-orichalcum-pulse-cloud-nine-raw-sun"}
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
                 move_ability_flags=(), field_statuses=0, terrain_affected=True) -> list[str]:
    delta = damage if delta is None else delta
    t = f"{types[0]}|{types[1]}|Mystery"
    def runtime(role: str, species_id: int, personality: int) -> str:
        values = [personality, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                  0, 0, 0, 0, 0, species_id, 0, 0, 0, 0, 0]
        return f"DDXO|{sid}|{rng}|{role}G|" + "|".join(map(str, values))
    return [
        f"DDXO|{sid}|{rng}|A1|68|50|{t}|15|0|{atk_status}|{atk_status1}",
        f"DDXO|{sid}|{rng}|A2|200|200|150|100|75|100|80",
        f"DDXO|{sid}|{rng}|A3|0|0|0|0",
        f"DDXO|{sid}|{rng}|A4|90|130|80|65|85|55",
        f"DDXO|{sid}|{rng}|A5|0|0|0|0",
        f"DDXO|{sid}|{rng}|A6|{1 if terrain_affected else 0}",
        runtime("A", 68, 68),
        f"DDXO|{sid}|{rng}|D1|143|50|Normal|Normal|Mystery|15|0|{def_status}|{def_status1}",
        f"DDXO|{sid}|{rng}|D2|{60000 - delta}|60000|100|85|100|130|40",
        f"DDXO|{sid}|{rng}|D3|0|{def_stage}|0|0",
        f"DDXO|{sid}|{rng}|D4|160|110|65|65|110|30",
        f"DDXO|{sid}|{rng}|D5|0|0|0|0",
        f"DDXO|{sid}|{rng}|D6|{1 if terrain_affected else 0}",
        runtime("D", 143, 143),
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
                                                     "abilityFlags": [], "effect": "EFFECT_HIT", "ordinary": True,
                                                     "makesContact": False, "punchingMove": False,
                                                     "sheerForceAffected": None,
                                                     "priority": 0, "targetClass": 6,
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
            ([f"DDXO|{self.sid}|0|Z|1"], "unknown oracle line kind"),
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
    def test_quick_claw_weighted_rng_boolean_matches_scenario(self):
        # RandomPercentage weights {100-t, t}: index 0 is FALSE, index 1 is TRUE.
        for suffix, mode in (("proc", 2), ("no-proc", 1)):
            scenario = next(s for s in SCENARIOS if s['id'] == 'gyro-ball-quick-claw-' + suffix)
            self.assertIn(f'gDdxoQuickClawMode = {mode};', backend.render_scenario(scenario))

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

    def test_partner_weather_suppression_precedes_cached_weather(self):
        for name in ("cloud-nine", "air-lock"):
            scenario = next(s for s in SCENARIOS if s["id"] == f"ordinary-doubles-{name}-rain-gastro-1")
            atk, _, _ = backend.plan_setup(scenario)
            self.assertEqual(atk, ["MOVE_GASTRO_ACID", "MOVE_RAIN_DANCE"])
            source = backend.render_scenario(scenario)
            self.assertIn("MOVE(playerLeft, MOVE_GASTRO_ACID, target: playerRight)", source)

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

    def test_assault_vest_opponent_uses_legal_move_on_every_turn(self):
        scenarios = [s for s in SCENARIOS if "assault-vest" in s["id"]]
        self.assertEqual(len(scenarios), 3)
        for scenario in scenarios:
            with self.subTest(scenario=scenario["id"]):
                source = backend.render_scenario(scenario)
                turns = [line for line in source.splitlines() if "TURN {" in line]
                self.assertEqual(len(turns), 1 + int(bool((scenario.get("stateSetup") or {}).get("wonderRoom"))))
                self.assertTrue(all("MOVE(opponent, MOVE_TACKLE);" in turn for turn in turns))

    def test_immunity_cases_assert_live_identity_when_pre_damage_hook_is_skipped(self):
        scenario = next(s for s in SCENARIOS if s["id"] == "group-c-absorb-water-absorb")
        source = backend.render_scenario(scenario)
        self.assertIn("EXPECT_EQ(gBattleMons[B_POSITION_PLAYER_LEFT].species, SPECIES_MACHAMP);", source)
        self.assertIn("EXPECT_EQ(gBattleMons[B_POSITION_PLAYER_LEFT].item, ITEM_NONE);", source)
        self.assertNotIn("EXPECT_EQ(sDdxoItemAtHit[", source)

    def test_life_orb_recoil_is_validated_after_the_hit_from_active_live_item(self):
        scenario = next(s for s in SCENARIOS if s["id"] == "group-d-item-life-orb-floored")
        attacker = {"maxHp": 200}
        active_life_orb = {"itemIdAtHit": 479, "holdEffectActive": 1}
        self.assertEqual(backend._life_orb_post_hit_recoil(scenario, attacker, active_life_orb), 20)
        self.assertEqual(backend._life_orb_post_hit_recoil(
            scenario, attacker, {"itemIdAtHit": 0, "holdEffectActive": 0}), 0)
        self.assertEqual(backend._life_orb_post_hit_recoil(
            scenario, attacker, {"itemIdAtHit": 479, "holdEffectActive": 0}), 0)
        magic_guard = copy.deepcopy(scenario)
        magic_guard["attacker"]["ability"] = "ABILITY_MAGIC_GUARD"
        self.assertEqual(backend._life_orb_post_hit_recoil(magic_guard, attacker, active_life_orb), 0)

    def test_generated_tests_capture_all_rolls_and_force_crit(self):
        files = backend.render_sources(SCENARIOS)
        self.assertEqual(files, backend.render_sources(matrix.build_scenarios()))
        text = "".join(files.values())
        self.assertEqual(text.count('_BATTLE_TEST("DDXO '), len(SCENARIOS) + len(backend.EXECUTION_NAMES))
        self.assertEqual(text.count("PARAMETRIZE { }"), 16 * len(SCENARIOS))
        self.assertEqual(text.count("WITH_RNG(RNG_DAMAGE_MODIFIER, i)"), len(SCENARIOS))
        self.assertEqual(sum(source.count("secondaryEffect: FALSE") for name,source in files.items() if not name.endswith("/execution.c")), len(SCENARIOS))
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
            expected = "src/battle_move_resolution.c" if name.startswith(("0008", "0012")) else "src/battle_util.c" if name.startswith(("0004", "0006", "0007")) else "test/test_runner.c" if name.startswith("0001") else "test/test_runner_battle.c"
            if name.startswith(("0004", "0006", "0007")):
                added = "\n".join(line[1:] for line in text.splitlines() if line.startswith("+") and not line.startswith("+++"))
                self.assertIn("#if TESTING", added)
                self.assertIn("return CalcMoveBasePower(&ctx);", added)
                self.assertNotIn("gBattleMons[", added)
            if name.startswith("0009"):
                added = "\n".join(line[1:] for line in text.splitlines() if line.startswith("+") and not line.startswith("+++"))
                self.assertEqual(added.count("#if TESTING"), 4)
                self.assertNotRegex(added, r"(?<![=!<>])=(?!=)")
                self.assertNotIn("CalculateMoveDamage(", added)
                self.assertNotIn("BattleScriptCall(", added)
            if name.startswith("0012"):
                added = "\n".join(line[1:] for line in text.splitlines() if line.startswith("+") and not line.startswith("+++"))
                self.assertIn("#if TESTING", added)
                self.assertIn("gDdxrScaleShotComplete()", added)
                self.assertNotRegex(added, r"(?<![=!<>])=(?!=)")
            self.assertEqual(targets, ["src/battle_move_resolution.c", "src/battle_script_commands.c"] if name.startswith("0009") else [expected])

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

    def test_slice13_bullet_seed_migration_is_explicit_and_one_to_one(self):
        migrations = {"group-c-flag-bulletproof": "bullet-seed-bulletproof"}
        self.assertEqual(1, len(migrations))

        corpus = schema.load_corpus_text(cli.CORPUS_PATH.read_text())
        entries = [entry for entry in corpus["entries"]
                   if entry["scenario"]["id"] in migrations]
        self.assertEqual(1, len(entries))
        entry = entries[0]
        scenario = entry["scenario"]
        self.assertEqual("modelled", scenario["surface"])
        self.assertEqual("immune", scenario["expect"])
        self.assertEqual({"label": "Bullet Seed", "symbol": "MOVE_BULLET_SEED"}, scenario["move"])
        self.assertEqual("ABILITY_BULLETPROOF", scenario["defender"]["ability"])
        self.assertEqual([0] * schema.ROLL_COUNT, entry["rolls"])
        observed_move = entry["observed"]["move"]
        self.assertEqual((331, "Grass", 25, "physical", "EFFECT_HIT"),
                         (observed_move["id"], observed_move["type"], observed_move["power"],
                          observed_move["category"], observed_move["effect"]))
        self.assertEqual(["ballisticMove"], observed_move["flags"])

        evidence = json.loads(Path(__file__).with_name("variable-multihit-evidence.json").read_text())
        replacements = [case for case in evidence["cases"]
                        if case["scenario"]["id"] == migrations[scenario["id"]]]
        self.assertEqual(1, len(replacements))
        replacement = replacements[0]
        replacement_scenario = replacement["scenario"]
        self.assertTrue(replacement["pass"])
        self.assertEqual(("BULLET_SEED", "BULLETPROOF", True),
                         (replacement_scenario["move"], replacement_scenario["defenderAbility"],
                          replacement_scenario["sourceImmune"]))
        kinds = [event["kind"] for event in replacement["events"]]
        self.assertEqual(["STOP", "BETWEEN", "FINAL"], kinds)
        self.assertEqual([[0, 1]], [event["values"] for event in replacement["events"]
                                   if event["kind"] == "STOP"])
        final = next(event["values"] for event in replacement["events"] if event["kind"] == "FINAL")
        self.assertEqual([0, 0, 0], final[:3])

    def test_state_backed_operands_are_observed_at_hit_with_all_sixteen_rolls(self):
        doc = schema.load_corpus_text(cli.CORPUS_PATH.read_text())
        for entry in doc["entries"]:
            scenario = entry["scenario"]
            self.assertEqual(len(entry["rolls"]), 16)
            for role in ("attacker", "defender"):
                runtime = entry["observed"][role]["runtime"]
                self.assertEqual(set(runtime), set(schema.RUNTIME_DOMAINS))
                self.assertEqual(runtime["itemIdAtHit"], next(
                    int(row["id"]) for row in backend.ITEM_RECORDS.values()
                    if row["symbol"] == scenario[role]["item"]
                ), f"{scenario['id']} {role}.itemIdAtHit")
                self.assertEqual(entry["observed"][role]["itemRecord"]["holdEffectParam"],
                                 runtime["holdEffectParam"], f"{scenario['id']} {role}.holdEffectParam")
                for key, value in (scenario.get("stateSetup") or {}).get(role, {}).items():
                    observed = runtime[key] if key != "dynamaxSelected" else runtime["selectedGimmick"] == 4
                    expected = value if key != "dynamaxSelected" else bool(value)
                    self.assertEqual(observed, expected, f"{scenario['id']} {role}.{key}")
                self.assertIn(runtime["gender"], (0, 254, 255))

    def test_group_d_direct_held_item_families_have_source_observations(self):
        doc = schema.load_corpus_text(cli.CORPUS_PATH.read_text())
        by_id = {entry["scenario"]["id"]: entry for entry in doc["entries"]}
        expected = {
            "group-d-item-muscle-band-physical", "group-d-item-fire-gem-matching-consumed-after-hit",
            "group-d-item-fire-gem-technician-composition", "group-d-item-fire-gem-magic-room-suppressed",
            "group-d-item-punching-glove-contact-fluffy", "group-d-item-lustrous-orb-alternate-form",
            "group-d-item-soul-dew-latias-psychic", "group-d-item-choice-specs-special",
            "group-d-item-choice-specs-stage-composition",
            "group-d-item-thick-club-alolan-marowak", "group-d-item-light-ball-pikachu-form",
            "group-d-item-deep-sea-tooth-clamperl-special", "group-d-item-deep-sea-scale-clamperl-special",
            "group-d-item-metal-powder-transformed-ditto", "group-d-item-eviolite-transformed-evolvable",
            "group-d-item-assault-vest-wonder-room-physical", "group-d-item-life-orb-floored",
            "group-d-item-expert-belt-four-times", "group-d-item-metronome-counter-8",
            "group-d-item-resist-berry-ripen", "group-d-item-resist-berry-as-one-ice-rider",
            "group-d-item-resist-berry-as-one-shadow-rider",
            "group-d-item-umbrella-defender-rain-fire", "group-d-item-booster-energy-held-proto-sun",
            "group-d-item-primal-blue-orb-kyogre-primal",
            "group-d-item-metronome-magic-room-suppressed",
        }
        self.assertTrue(expected.issubset(by_id))
        for scenario_id in expected:
            with self.subTest(scenario=scenario_id):
                entry = by_id[scenario_id]
                self.assertEqual(entry["scenario"]["surface"], "modelled")
                self.assertEqual(len(entry["rolls"]), schema.ROLL_COUNT)
                for role in ("attacker", "defender"):
                    self.assertIsNotNone(entry["observed"][role]["runtime"])
        booster = by_id["group-d-item-booster-energy-held-proto-sun"]["observed"]["attacker"]
        booster_id = next(item_id for item_id, record in backend.ITEM_RECORDS.items()
                          if record["symbol"] == "ITEM_BOOSTER_ENERGY")
        self.assertEqual(booster["itemId"], booster_id)
        self.assertEqual(booster["runtime"]["boosterEnergyActivated"], 0)
        self.assertEqual(booster["runtime"]["paradoxBoostedStat"], 1)
        gem = by_id["group-d-item-fire-gem-matching-consumed-after-hit"]
        self.assertEqual(gem["observed"]["attacker"]["runtime"]["itemIdAtHit"], 340)
        self.assertEqual(gem["observed"]["attacker"]["itemId"], 0)
        transformed = by_id["group-d-item-eviolite-transformed-evolvable"]
        self.assertEqual(transformed["observed"]["defender"]["runtime"]["transformedMonSpecies"], 104)

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
                if role == "defender" and "move-coverage-slice-6" in scenario["tags"]:
                    self.assertEqual(raw_status,scenario["stateSetup"]["statusDoubleStatus1"])
                elif (scenario.get("stateSetup") or {}).get("gyroSpeed", {}).get(role, {}).get("status1") is not None:
                    self.assertEqual(raw_status, scenario["stateSetup"]["gyroSpeed"][role]["status1"])
                elif status == "toxic":
                    self.assertEqual(raw_status & 0x80, 0x80, f"{scenario['id']} {role} toxic bit")
                    self.assertEqual(raw_status & ~(0x80 | 0x0f00), 0,
                                     f"{scenario['id']} {role} unrelated status bits")
                else:
                    expected = {"none": 0, "poison": 8, "burn": 16, "paralysis": 64}[status]
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

    def test_low_state_group_d_corpus_records_source_predicates_and_controls(self):
        doc = schema.load_corpus_text(cli.CORPUS_PATH.read_text())
        by_id = {entry["scenario"]["id"]: entry for entry in doc["entries"]}
        expected_ids = {
            "group-d-marvel-scale-physical-burn", "group-d-marvel-scale-physical-no-status",
            "group-d-marvel-scale-special-status", "group-d-marvel-scale-status-stage-composition",
            "group-d-marvel-scale-mold-breaker", "group-d-marvel-scale-ability-shield",
            "group-d-flower-gift-attacker-sun-physical", "group-d-flower-gift-attacker-no-sun",
            "group-d-flower-gift-wrong-form", "group-d-flower-gift-attacker-special",
            "group-d-flower-gift-defender-sun-special", "group-d-flower-gift-defender-physical",
            "group-d-flower-gift-attacker-umbrella", "group-d-flower-gift-defender-holder-umbrella",
            "group-d-flower-gift-defender-cloud-nine", "group-d-tough-claws-fire-punch-contact",
            "group-d-tough-claws-flamethrower-noncontact",
            "group-d-tough-claws-protective-pads-still-contact", "group-d-sheer-force-scald-helper-positive",
            "group-d-sheer-force-pay-day-helper-negative", "group-d-sheer-force-fire-blast-positive",
            "group-d-sheer-force-defender-control", "group-d-fluffy-fire-noncontact-double",
            "group-d-fluffy-fire-contact-neutral", "group-d-fluffy-nonfire-contact-half",
            "group-d-fluffy-nonfire-noncontact-neutral", "group-d-fluffy-long-reach-suppresses-contact",
            "group-d-fluffy-protective-pads-do-not-suppress-contact", "group-d-fluffy-mold-breaker",
            "group-d-fluffy-ability-shield", "group-d-reckless-ordinary-hit-clear",
            "group-d-sand-force-sandstorm-ground-engine-only", "group-d-sand-force-sandstorm-normal-clear",
            "group-d-sand-force-cloud-nine-no-boost", "group-d-sand-force-sun-clear", "group-d-battery-singles-self-clear",
            "group-d-battery-defender-singles-clear", "group-d-power-spot-singles-self-clear",
            "group-d-power-spot-defender-singles-clear",
        }
        self.assertTrue(expected_ids.issubset(by_id))
        for sid in expected_ids:
            entry = by_id[sid]
            self.assertEqual(len(entry["rolls"]), schema.ROLL_COUNT, sid)
        self.assertTrue(by_id["group-d-tough-claws-fire-punch-contact"]["observed"]["move"]["makesContact"])
        self.assertFalse(by_id["group-d-tough-claws-flamethrower-noncontact"]["observed"]["move"]["makesContact"])
        self.assertTrue(by_id["group-d-sheer-force-scald-helper-positive"]["observed"]["move"]["sheerForceAffected"])
        self.assertFalse(by_id["group-d-sheer-force-pay-day-helper-negative"]["observed"]["move"]["sheerForceAffected"])
        # Each Flower Gift control changes only the named predicate: form, weather, category,
        # holder item, or a live weather suppressor.
        flower = {sid: by_id[sid]["scenario"] for sid in expected_ids if "flower-gift" in sid}
        self.assertEqual(flower["group-d-flower-gift-attacker-no-sun"]["attacker"]["speciesLabel"], "Cherrim-Sunshine")
        self.assertEqual(flower["group-d-flower-gift-wrong-form"]["attacker"]["speciesLabel"], "Cherrim")
        self.assertEqual(flower["group-d-flower-gift-attacker-umbrella"]["attacker"]["speciesLabel"], "Cherrim-Sunshine")
        self.assertEqual(flower["group-d-flower-gift-defender-holder-umbrella"]["defender"]["speciesLabel"], "Cherrim-Sunshine")
        self.assertEqual(flower["group-d-flower-gift-defender-cloud-nine"]["defender"]["speciesLabel"], "Cherrim-Sunshine")
        self.assertEqual(flower["group-d-flower-gift-defender-cloud-nine"]["attacker"]["abilityLabel"], "Cloud Nine")
        for sid in ("group-d-tough-claws-protective-pads-still-contact",
                    "group-d-fluffy-long-reach-suppresses-contact",
                    "group-d-fluffy-protective-pads-do-not-suppress-contact"):
            self.assertTrue(by_id[sid]["observed"]["move"]["makesContact"], sid)
        for sid in ("group-d-tough-claws-protective-pads-still-contact",
                    "group-d-fluffy-protective-pads-do-not-suppress-contact",
                    "group-d-fluffy-mold-breaker",
                    "group-d-sand-force-sandstorm-ground-engine-only",
                    "group-d-sand-force-sandstorm-normal-clear",
                    "group-d-sand-force-cloud-nine-no-boost"):
            self.assertEqual(by_id[sid]["scenario"]["surface"], "engine-only", sid)

    def test_pinned_terrain_applicability_controls_and_gravity_overrides(self):
        doc = schema.load_corpus_text(cli.CORPUS_PATH.read_text())
        by_id = {entry["scenario"]["id"]: entry["observed"] for entry in doc["entries"]}
        expected = {
            "terrain-grassy-grass-attacker": (True, False),
            "terrain-grassy-iron-ball-overrides-flying": (True, False),
            "terrain-grassy-no-terrain-control": (False, False),
            "terrain-electric-levitate-control": (False, True),
            "terrain-electric-gravity-overrides-levitate": (True, True),
            "terrain-misty-flying-defender-control": (False, False),
            "terrain-psychic-air-balloon-control": (False, True),
            "terrain-psychic-gravity-overrides-balloon": (True, True),
        }
        for sid, pair in expected.items():
            with self.subTest(scenario=sid):
                self.assertEqual(
                    (by_id[sid]["attacker"]["terrainAffected"], by_id[sid]["defender"]["terrainAffected"]),
                    pair,
                )

    def test_resolved_post_gen_three_spread_vectors_are_not_registered(self):
        doc = schema.load_corpus_text(cli.CORPUS_PATH.read_text())
        by_id = {entry["scenario"]["id"]: entry for entry in doc["entries"]}
        self.assertEqual(cli.load_divergences(by_id), [])
        # Historical engine observations remain unchanged; shipped-bundle comparison is
        # enforced by the native oracle suite for these two resolved #100 scenarios.
        self.assertEqual(by_id["doubles-dazzling-gleam-partner-present"]["rolls"],
            [20,20,20,21,21,21,21,22,22,22,22,23,23,23,23,24])
        self.assertEqual(by_id["doubles-dazzling-gleam-partner-present-crit"]["rolls"],
            [40,41,41,42,42,43,43,44,44,45,45,46,46,47,47,48])

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


class RecoilSuppressionSetupTest(unittest.TestCase):
    def test_suppression_precedes_cached_damage_context(self):
        s = next(s for s in SCENARIOS if s["id"] == "recoil-reckless-suppressed")
        schema.validate_scenario(s)
        a, d, _ = backend.plan_setup(s)
        self.assertIn("MOVE_GASTRO_ACID", d)
        text = "\n".join(backend.render_sources([s]).values())
        self.assertIn("MOVE_GASTRO_ACID", text)
        self.assertNotIn(".volatiles.gastroAcid = 1", text)
        bad = copy.deepcopy(s)
        del bad["stateSetup"]["attacker"]["gastroAcid"]
        with self.assertRaises(schema.SchemaError):
            schema.validate_scenario(bad)


class EarthquakeHitStateTest(unittest.TestCase):
    def test_real_hit_state_is_required_and_cannot_be_defaulted(self):
        corpus = schema.load_corpus_text(cli.CORPUS_PATH.read_text())
        entries = {e["scenario"]["id"]:e for e in corpus["entries"]}
        for sid in ("earthquake-earthquake-player", "earthquake-underground-grassy"):
            entry = entries[sid]
            self.assertEqual(int(bool((entry["scenario"]["stateSetup"] or {}).get("underground"))),
                             entry["observed"]["defenderSemiInvulnerableState"])
            for raw in (None, -1, 7, 1 - entry["observed"]["defenderSemiInvulnerableState"]):
                bad = copy.deepcopy(entry["observed"])
                bad["defenderSemiInvulnerableState"] = raw
                with self.assertRaises(schema.SchemaError):
                    schema.validate_observed(bad,entry["scenario"],sid)
            bad = copy.deepcopy(entry["observed"])
            del bad["defenderSemiInvulnerableState"]
            with self.assertRaises(schema.SchemaError):
                schema.validate_observed(bad,entry["scenario"],sid)


class UnderwaterHitStateTest(unittest.TestCase):
    def test_real_dive_hit_state_cannot_be_defaulted_or_forged(self):
        corpus = schema.load_corpus_text(cli.CORPUS_PATH.read_text())
        entries = {e["scenario"]["id"]:e for e in corpus["entries"]}
        for sid in ("underwater-surf-player", "underwater-surf-dive-player", "underwater-whirlpool-dive-player"):
            entry = entries[sid]
            expected = 2 if (entry["scenario"]["stateSetup"] or {}).get("underwater") else 0
            self.assertEqual(expected,entry["observed"]["defenderSemiInvulnerableState"])
            for raw in (None, -1, 7, 1):
                bad=copy.deepcopy(entry["observed"])
                bad["defenderSemiInvulnerableState"]=raw
                with self.assertRaises(schema.SchemaError):
                    schema.validate_observed(bad,entry["scenario"],sid)
            source=backend.render_scenario(entry["scenario"])
            self.assertNotIn(".volatiles.semiInvulnerable =",source)
            if expected: self.assertIn("MOVE(opponent, MOVE_DIVE)",source)
            if "whirlpool" in sid:
                self.assertIn("captureDamage: &results[i].damage",source)
                self.assertIn("captureDamage: &results[i].residual",source)

    def test_unexpected_wrap_observation_is_rejected(self):
        scenario=a_scenario()
        sid=scenario["id"]
        records=backend.parse_runner_output(runner_output(sid),[sid])
        records[sid][0]["W"]=["7500","1","3","250"]
        with self.assertRaisesRegex(backend.OracleError,"unexpected wrap observation"):
            backend.assemble_entry(scenario,records[sid])





class BrineHpEvidenceTest(unittest.TestCase):
    def test_hit_time_pair_cannot_be_replaced_by_setup_or_healed_hp(self):
        doc = schema.load_corpus_text((backend.TOOL_DIR / "corpus.json").read_text())
        entry = next(e for e in doc["entries"] if e["scenario"]["id"] == "brine-odd-half-player")
        for field,value in (("hpAtHit",51),("maxHpAtHit",100)):
            fake=copy.deepcopy(entry)
            fake["observed"]["defender"][field]=value
            with self.assertRaises(schema.SchemaError):
                schema.validate_observed(fake["observed"],fake["scenario"],"Brine HP evidence")
        fake=copy.deepcopy(entry)
        del fake["observed"]["defender"]["maxHpAtHit"]
        with self.assertRaises(schema.SchemaError):
            schema.validate_observed(fake["observed"],fake["scenario"],"missing maxHP")
    def test_immunity_observation_precedes_recovery_and_never_writes_hp(self):
        source=backend.render_scenario(next(s for s in SCENARIOS if s["id"]=="brine-water-absorb"))
        self.assertIn("gDdxoBeforeAbilityPopup = DdxoSetup_brine_water_absorb;",source)
        self.assertIn('DdxoBattler("brine-water-absorb", sDdxoRoll, "D", B_POSITION_OPPONENT_LEFT);',source)
        self.assertNotRegex(source,r"gBattleMons\[[^]]+\]\.(?:hp|maxHP)\s*=")

    def test_post_turn_fallback_cannot_impersonate_the_hp_callback(self):
        scenario=next(s for s in SCENARIOS if s["id"]=="brine-water-absorb")
        for slot in ({"DG": [], "D2": []}, {"DG": []}):
            with self.assertRaisesRegex(backend.OracleError,"hit-boundary defender HP capture"):
                backend.assemble_entry(scenario,{0:slot})


class ElectroBallEvidenceTest(unittest.TestCase):
    def test_source_totals_determine_power_and_zero_divisor_fails_before_division(self):
        corpus = schema.load_corpus_text(cli.CORPUS_PATH.read_text())
        entry = copy.deepcopy(next(e for e in corpus['entries'] if e['scenario']['id']=='electro-ball-ratio-100'))
        entry['observed']['effectiveSpeeds']['basePower'] = 80
        with self.assertRaisesRegex(schema.SchemaError, 'dynamic Speed power'):
            schema.validate_entry(entry)
        entry['observed']['effectiveSpeeds']['defender']['total'] = 0
        with self.assertRaisesRegex(schema.SchemaError, 'unsafe Electro Ball divisor'):
            schema.validate_entry(entry)
        scenario = next(s for s in SCENARIOS if s['id']=='electro-ball-attacker-zero')
        source = backend.render_sources([scenario])
        joined = '\n'.join(source.values())
        guard = joined.index('Unsafe Electro Ball divisor')
        accessor = joined.index('DdxoElectroBallBasePower((enum BattlerId)', guard)
        self.assertLess(guard, accessor)


if __name__ == "__main__":
    unittest.main()
