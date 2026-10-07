#!/usr/bin/env python3
"""Mutation controls for Scale Shot's exact descriptor and post-loop timing proof."""
import argparse
from pathlib import Path
import re

import generate_hns_move_effects as gen


def refuse(fn, label):
    try:
        fn()
    except ValueError:
        return
    raise AssertionError(f"mutation accepted: {label}")


def run(upstream: Path):
    source = (upstream / "src/data/moves_info.h").read_text()
    ids = gen.parse_move_enum((upstream / "include/constants/moves.h").read_text())
    contacts, _, sheer, _ = gen.parse_contact_and_sheer_force(source)
    ability, _ = gen.parse_ability_move_flags(source)
    gen.parse_scale_shot_metadata(source, ids,
        {ids[k]: v for k, v in contacts.items() if k in ids},
        {ids[k]: v for k, v in sheer.items() if k in ids},
        {ids[k]: v for k, v in ability.items() if k in ids})
    start = source.index("[MOVE_SCALE_SHOT]")
    end = source.index("\n    },", start) + 7
    body = source[start:end]
    tests = [
        (r"\.name\s*=\s*COMPOUND_STRING\(\"SCALE SHOT\"\)", ".name = COMPOUND_STRING(\"Altered Shot\")", "name"),
        (r"\.effect\s*=\s*EFFECT_HIT", ".effect = EFFECT_MULTI_HIT", "effect"),
        (r"\.power\s*=\s*25", ".power = 26", "power"),
        (r"\.type\s*=\s*TYPE_DRAGON", ".type = TYPE_FIRE", "type"),
        (r"\.accuracy\s*=\s*90", ".accuracy = 91", "accuracy"),
        (r"\.pp\s*=\s*20", ".pp = 21", "pp"),
        (r"\.target\s*=\s*TARGET_SELECTED", ".target = TARGET_BOTH", "target"),
        (r"\.priority\s*=\s*0", ".priority = 1", "priority"),
        (r"\.category\s*=\s*DAMAGE_CATEGORY_PHYSICAL", ".category = DAMAGE_CATEGORY_SPECIAL", "category"),
        (r"\.multiHit\s*=\s*TRUE", ".multiHit = FALSE", "multiHit"),
        (r"\.additionalEffects\s*=", ".strikeCount = 2,\n        .additionalEffects =", "fixed count"),
        (r"\.additionalEffects\s*=", ".makesContact = TRUE,\n        .additionalEffects =", "contact"),
        (r"\.additionalEffects\s*=", ".punchingMove = TRUE,\n        .additionalEffects =", "punching"),
        (r"\.additionalEffects\s*=", ".ballisticMove = TRUE,\n        .additionalEffects =", "ballistic"),
        (r"\.additionalEffects\s*=", ".sheerForceAffected = TRUE,\n        .additionalEffects =", "Sheer Force"),
        (r"\.moveEffect\s*=\s*MOVE_EFFECT_SCALE_SHOT", ".moveEffect = MOVE_EFFECT_NONE", "additional effect"),
        (r"\.additionalEffects\s*=\s*ADDITIONAL_EFFECTS\(\{\s*\.moveEffect\s*=\s*MOVE_EFFECT_SCALE_SHOT,\s*\}\),", "", "removed additional effect"),
        (r"\.additionalEffects\s*=\s*ADDITIONAL_EFFECTS\(\{\s*\.moveEffect\s*=\s*MOVE_EFFECT_SCALE_SHOT,\s*\}\)", ".additionalEffects = ADDITIONAL_EFFECTS({ .moveEffect = MOVE_EFFECT_SCALE_SHOT, .moveEffect = MOVE_EFFECT_POISON, })", "unrelated effect"),
        (r"\.additionalEffects\s*=", ".preAttackEffect = TRUE,\n        .additionalEffects =", "pre-attack effect"),
    ]
    rejected = 0
    for pattern, replacement, label in tests:
        mutated, count = re.subn(pattern, replacement, body, count=1)
        assert count == 1, label
        doc = source[:start] + mutated + source[end:]
        refuse(lambda doc=doc: gen.parse_scale_shot_metadata(doc, ids,
            {ids[k]: v for k, v in contacts.items() if k in ids},
            {ids[k]: v for k, v in sheer.items() if k in ids},
            {ids[k]: v for k, v in ability.items() if k in ids}), label)
        rejected += 1

    changed_ids=dict(ids); changed_ids["MOVE_SCALE_SHOT"]=728
    refuse(lambda: gen.parse_scale_shot_metadata(source, changed_ids,
        {ids[k]:v for k,v in contacts.items() if k in ids},
        {ids[k]:v for k,v in sheer.items() if k in ids},
        {ids[k]:v for k,v in ability.items() if k in ids}), "move ID")
    rejected += 1

    scripts = upstream / "data/battle_scripts_1.s"
    gen.verify_scale_shot_script_contract(upstream)
    text = scripts.read_text()
    for label, replacement in (
        ("between-hit path", "BattleScript_ScaleShot::\n\tcall BattleScript_MultiHitPrintStrings\n\treturn"),
        ("sequence print order", "BattleScript_ScaleShot::\n\tgoto BattleScript_DefDownSpeedUp\n\tcall BattleScript_MultiHitPrintStrings"),
        ("Defense attempt", "setstatchanger STAT_DEF, -1, TRUE"),
        ("Speed attempt", "setstatchanger STAT_SPEED, 1, TRUE"),
        ("certainty flag", "STAT_CHANGE_ALLOW_PTR, BattleScript_DefDownSpeedUpTrySpeed"),
    ):
        if label in ("between-hit path", "sequence print order"):
            altered = text.replace("BattleScript_ScaleShot::\n\tcall BattleScript_MultiHitPrintStrings\n\tgoto BattleScript_DefDownSpeedUp", replacement, 1)
        else:
            target = text.index("BattleScript_DefDownSpeedUp::")
            needle = "setstatchanger STAT_DEF, 1, TRUE" if label == "Defense attempt" else \
                "setstatchanger STAT_SPEED, 1, FALSE" if label == "Speed attempt" else \
                "STAT_CHANGE_ALLOW_PTR | STAT_CHANGE_CERTAIN, BattleScript_DefDownSpeedUpTrySpeed"
            altered = text[:target] + text[target:].replace(needle, replacement, 1)
        refuse(lambda altered=altered: gen.verify_scale_shot_script_contract_text(altered), label)
        rejected += 1
    print(f"Scale Shot descriptor/script mutations refused: {rejected}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--upstream-dir", type=Path, required=True)
    run(parser.parse_args().upstream_dir)
