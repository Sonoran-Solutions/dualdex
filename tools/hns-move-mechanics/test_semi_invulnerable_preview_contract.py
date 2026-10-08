#!/usr/bin/env python3
"""Source descriptor and phase-timing mutations for Slice 15."""
import shutil
import re
import argparse
import sys
import tempfile
import unittest
from pathlib import Path

import generate_hns_move_effects as gen

UPSTREAM = None


class SemiInvulnerablePreviewContractTest(unittest.TestCase):
    def test_exact_five_move_inventory_and_full_descriptor_mutations(self):
        moves_path = UPSTREAM / "src/data/moves_info.h"
        original = moves_path.read_text()
        ids = gen.parse_move_enum((UPSTREAM / "include/constants/moves.h").read_text())
        config = (UPSTREAM / "include/config/battle.h").read_text()
        contact_symbols, _unknown_contact, sheer_symbols, _unknown_sheer = gen.parse_contact_and_sheer_force(
            original, updated_move_data_latest=True)
        sheer = {ids[symbol]: value for symbol, value in sheer_symbols.items() if symbol in ids}
        contact = {ids[symbol]: value for symbol, value in contact_symbols.items() if symbol in ids}
        parsed = gen.parse_semi_invulnerable_preview_metadata(original, ids, config, contact, sheer, {}, {})
        self.assertEqual(set(parsed), {19, 91, 291, 340, 566})
        self.assertEqual({i: m["power"] for i, m in parsed.items()}, {19: 90, 91: 80, 291: 80, 340: 85, 566: 90})
        self.assertTrue(all(m["makesContact"] for m in parsed.values()))
        self.assertEqual({i for i, m in parsed.items() if m["sheerForceAffected"]}, {340})
        self.assertEqual({i for i, m in parsed.items() if m["gravityBanned"]}, {19, 340})
        self.assertTrue(parsed[566]["ignoresProtect"])
        self.assertFalse(parsed[566]["minimizeDoubleDamage"])
        self.assertEqual(parsed[340]["additionalEffects"][0]["chance"], 30)
        mutations = (
            ("FLY", ".power = B_UPDATED_MOVE_DATA >= GEN_4 ? 90 : 70", ".power = 70"),
            ("FLY", ".gravityBanned = TRUE", ".gravityBanned = FALSE"),
            ("DIG", ".status = STATE_UNDERGROUND", ".status = STATE_NONE"),
            ("PHANTOM_FORCE", ".ignoresProtect = TRUE", ".ignoresProtect = FALSE"),
            ("BOUNCE", ".moveEffect = MOVE_EFFECT_PARALYSIS", ".moveEffect = MOVE_EFFECT_BURN"),
            ("BOUNCE", ".chance = 30", ".chance = 20"),
        )
        for move, old, new in mutations:
            with self.subTest(move=move, old=old):
                start = original.index(f"[MOVE_{move}] =")
                end = original.index("\n    },", start)
                block = original[start:end]
                self.assertIn(old, block)
                mutated = original[:start] + block.replace(old, new, 1) + original[end:]
                with self.assertRaisesRegex(ValueError, "Changed pinned semi-invulnerable MoveInfo"):
                    gen.parse_semi_invulnerable_preview_metadata(mutated, ids, config, contact, sheer, {}, {})

    def test_preparation_release_damage_and_cleanup_mutations_fail_closed(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            for relative in ("src/battle_move_resolution.c", "src/battle_script_commands.c", "src/battle_util.c",
                             "include/move.h", "data/battle_scripts_1.s"):
                target = root / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(UPSTREAM / relative, target)
            gen.verify_semi_invulnerable_phase_contract(root)
            mutations = (
                ("src/battle_move_resolution.c", "CancelerCharging", "gLockedMoves[ctx->battlerAtk] = ctx->move;", "gLockedMoves[ctx->battlerAtk] = MOVE_NONE;"),
                ("src/battle_move_resolution.c", "CancelerCharging", "GetMoveTwoTurnAttackStatus(ctx->move)", "STATE_NONE"),
                ("src/battle_move_resolution.c", "CancelerCharging", "semiInvulnerable = STATE_NONE;", "semiInvulnerable = STATE_ON_AIR;"),
                ("src/battle_script_commands.c", "Cmd_setsemiinvulnerablebit", "semiInvulnerable = STATE_NONE;", "semiInvulnerable = STATE_ON_AIR;"),
                ("src/battle_util.c", "IsGravityPreventingMove", "return IsMoveGravityBanned(move);", "return FALSE;"),
                ("src/battle_util.c", "MoveIsAffectedBySheerForce", "additionalEffect->chance > 0", "additionalEffect->chance > 5"),
                ("src/battle_util.c", "CalcMoveBasePowerAfterModifiers", "if (MoveIsAffectedBySheerForce(move))", "if (FALSE)"),
                ("src/battle_move_resolution.c", "MoveEndSheerForce", "MOVEEND_ITEMS_EFFECTS_ALL", "MOVEEND_NEXT"),
                ("src/battle_move_resolution.c", "MoveEndLifeOrbShellBell", "GetBattlerHoldEffect(gBattlerAttacker)", "HOLD_EFFECT_NONE"),
                ("data/battle_scripts_1.s", "BattleScript_PowerHerbActivation", "removeitem BS_ATTACKER", "nop"),
                ("include/move.h", "MoveIgnoresProtect", "return gMovesInfo[SanitizeMoveId(moveId)].ignoresProtect;", "return FALSE;"),
                ("include/move.h", "IsMoveGravityBanned", "return gMovesInfo[SanitizeMoveId(moveId)].gravityBanned;", "return FALSE;"),
                ("data/battle_scripts_1.s", "BattleScript_TwoTurnMovesSecondTurnRet", "clearsemiinvulnerablebit @ only for moves", "nop @ only for moves"),
                ("data/battle_scripts_1.s", "BattleScript_HitFromAccCheck", "\taccuracycheck", "\tnop"),
                ("data/battle_scripts_1.s", "BattleScript_HitFromDamageCalc", "\tdamagecalc", "\tnop"),
                ("data/battle_scripts_1.s", "BattleScript_Hit_RetFromAtkAnimation", "\tdatahpupdate BS_TARGET, MOVE_DAMAGE_HP_UPDATE", "\tnop"),
            )
            for relative, region, old, new in mutations:
                with self.subTest(relative=relative, region=region, old=old):
                    target = root / relative
                    original = target.read_text()
                    if relative.endswith((".c", ".h")):
                        extracted = gen._extract_c_function(original, region)
                    else:
                        match = re.search(r"^" + region + r"::?$", original, re.M)
                        start = match.start()
                        following = re.search(r"^BattleScript_\w+::?$", original[match.end():], re.M)
                        following = match.end() + following.start()
                        extracted = original[start:following]
                    self.assertIn(old, extracted)
                    target.write_text(original.replace(extracted, extracted.replace(old, new, 1), 1))
                    with self.assertRaisesRegex(ValueError, "Changed semi-invulnerable phase/damage timing"):
                        gen.verify_semi_invulnerable_phase_contract(root)
                    target.write_text(original)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--upstream-dir", type=Path, required=True)
    UPSTREAM = parser.parse_args().upstream_dir
    unittest.main(argv=[sys.argv[0]])
