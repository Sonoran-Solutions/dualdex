#!/usr/bin/env python3
"""Mutation controls for Slice 13 descriptors and source count selection."""
import argparse
import re
from pathlib import Path

import generate_hns_move_effects as gen


def refuse(call, label):
    try:
        call()
    except ValueError:
        return
    raise AssertionError(f"mutation accepted: {label}")


def inputs(upstream):
    moves = (upstream / "src/data/moves_info.h").read_text()
    ids = gen.parse_move_enum((upstream / "include/constants/moves.h").read_text())
    config = (upstream / "include/config/battle.h").read_text()
    general = (upstream / "include/config/general.h").read_text()
    contacts, unknown_contacts, sheer, unknown_sheer = gen.parse_contact_and_sheer_force(moves)
    _, _, _, _, flags, unknown_flags, _, _ = gen.parse_move_table(moves)
    ability_flags, unknown_ability_flags = gen.parse_ability_move_flags(moves)
    def by_id(source):
        return {ids[symbol]: value for symbol, value in source.items() if symbol in ids}
    return (moves, ids, config, general, by_id(contacts), by_id(unknown_contacts),
            by_id(sheer), by_id(unknown_sheer), by_id(ability_flags),
            by_id(unknown_ability_flags), by_id(flags), by_id(unknown_flags))


def parse(values):
    (moves, ids, config, general, contacts, unknown_contacts, sheer, unknown_sheer,
     ability_flags, unknown_ability_flags, flags, unknown_flags) = values
    return gen.parse_variable_multi_hit_metadata(moves, ids, config, general, contacts,
        unknown_contacts, sheer, unknown_sheer, ability_flags, unknown_ability_flags,
        flags, unknown_flags)


def run(upstream):
    values = inputs(upstream)
    moves, ids, config, general = values[:4]
    metadata = parse(values)
    expected = {move_id for move_id, _ in gen.VARIABLE_MULTI_HIT_MOVE_CONTRACTS.values()}
    assert set(metadata) == expected and len(expected) == 12
    assert not expected.intersection({41, 727, 167, 813, 860, 251})
    assert metadata[4]["punchingMove"] is True and metadata[4]["makesContact"] is True
    assert metadata[331]["family"] == "VARIABLE_MULTI_HIT_PLAIN"
    assert metadata[350]["ballisticMove"] is True

    rejected = 0
    for symbol, (move_id, _digest) in gen.VARIABLE_MULTI_HIT_MOVE_CONTRACTS.items():
        start = moves.index("[" + symbol + "]")
        end = moves.index("\n    },", start)
        body = moves[start:end]
        # Every source-backed execution descriptor change must fail before metadata can drift.
        for field, replacement in (("effect", "EFFECT_MULTI_HIT"), ("power", "power + 1"),
                                   ("type", "TYPE_FIRE"), ("category", "DAMAGE_CATEGORY_SPECIAL"),
                                   ("accuracy", "accuracy - 1"), ("pp", "pp + 1"),
                                   ("target", "TARGET_BOTH"), ("priority", "1"),
                                   ("multiHit", "FALSE"), ("makesContact", "TRUE"),
                                   ("punchingMove", "TRUE"), ("ballisticMove", "TRUE")):
            match = re.search(r"(\." + re.escape(field) + r"\s*=\s*)([^,\n}]+)", body)
            if match is None:
                key = {"makesContact": "makesContact", "punchingMove": "punchingMove",
                       "ballisticMove": "ballisticMove"}.get(field)
                if key is not None:
                    mutated_value = "FALSE" if metadata[move_id][key] else "TRUE"
                    mutated_body = body.replace(".power = ", f".{field} = {mutated_value}, .power = ", 1)
                else:
                    continue
            else:
                if field in ("makesContact", "punchingMove", "ballisticMove"):
                    opposite = "FALSE" if metadata[move_id][field] else "TRUE"
                    replacement = opposite
                mutated_body = body[:match.start(2)] + replacement + body[match.end(2):]
            mutated = moves[:start] + mutated_body + moves[end:]
            changed = (mutated,) + values[1:]
            refuse(lambda changed=changed: parse(changed), f"{symbol}.{field}")
            rejected += 1

    gen._variable_multi_hit_generation_contract(config, general)
    for name in ("B_MULTI_HIT_CHANCE", "B_UPDATED_MOVE_DATA", "B_UPDATED_MOVE_FLAGS"):
        altered = re.sub(r"(#define\s+" + name + r"\s+)GEN_LATEST", r"\1GEN_8", config, count=1)
        refuse(lambda altered=altered: gen._variable_multi_hit_generation_contract(altered, general), name)
        rejected += 1
    older_general = re.sub(r"(#define\s+GEN_LATEST\s+)GEN_9", r"\1GEN_8", general, count=1)
    refuse(lambda: gen._variable_multi_hit_generation_contract(config, older_general), "GEN_LATEST")
    rejected += 1

    for (path, function), digest in gen.VARIABLE_MULTI_HIT_SOURCE_CONTRACTS.items():
        source = (upstream / path).read_text()
        pattern = (r"(?m)^[ \t]*(?:(?:static|inline)[ \t]+)*(?:enum[ \t]+\w+|[A-Za-z_]\w*(?:[ \t]*\*)?)[ \t]+" +
                   re.escape(function) + r"[ \t]*\([^;{}]*\)\s*\{")
        match = re.search(pattern, source)
        assert match, function
        source = source[:match.start()] + source[match.start():].replace("\n", "\n    // harmless-looking source drift\n", 1)
        refuse(lambda source=source, function=function, digest=digest:
            gen._verify_function_hash(source, function, digest), function)
        rejected += 1
    print(f"Variable multi-hit descriptor/count source contract: {rejected} mutations refused")


if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("--upstream-dir", type=Path, required=True)
    run(p.parse_args().upstream_dir)
