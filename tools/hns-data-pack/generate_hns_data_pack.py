#!/usr/bin/env python3
"""
tools/hns-data-pack/generate_hns_data_pack.py

Extracts authoritative Pokemon species and moves data from the pinned
Pokemon Heart & Soul 2.0.5 upstream target and generates the Kotlin
HeartAndSoul205DataPack.

Provenance:
  Repository: PokemonHnS-Development/pokehns-expansion
  Tag: Release-v2.0.5
  Commit: 1f42b74dff0e9fe942419845d040663dd829a973
  Release ROM SHA-256: edf76ecf2a1c23a65c62ab63b1c0e775965978c81baeed20e249e96b3417679b
"""

import argparse
import os
import re
import struct
import sys

DEFAULT_UPSTREAM_DIR = "/home/dq/Projects/upstream-hns/pokehns-expansion"
DEFAULT_ROM_PATH = "/home/dq/Downloads/Pokémon Heart and Soul (v2.0.5).gba"
ALT_ROM_PATH = "/home/dq/Projects/upstream-hns/pokehns-expansion/pokehns.gba"
DEFAULT_OUT_FILE = os.path.join(
    os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))),
    "app/src/main/java/com/dualdex/pokemon/hns/HeartAndSoul205DataPack.kt"
)

TYPE_MAP = {
    1: "PokemonType.NORMAL",
    2: "PokemonType.FIGHTING",
    3: "PokemonType.FLYING",
    4: "PokemonType.POISON",
    5: "PokemonType.GROUND",
    6: "PokemonType.ROCK",
    7: "PokemonType.BUG",
    8: "PokemonType.GHOST",
    9: "PokemonType.STEEL",
    10: "PokemonType.NORMAL",   # MYSTERY / ???
    11: "PokemonType.FIRE",
    12: "PokemonType.WATER",
    13: "PokemonType.GRASS",
    14: "PokemonType.ELECTRIC",
    15: "PokemonType.PSYCHIC",
    16: "PokemonType.ICE",
    17: "PokemonType.DRAGON",
    18: "PokemonType.DARK",
    19: "PokemonType.FAIRY",
    20: "PokemonType.NORMAL",   # STELLAR
}

CATEGORY_MAP = {
    0: "MoveCategory.PHYSICAL",
    1: "MoveCategory.SPECIAL",
    2: "MoveCategory.STATUS",
}

SPECIAL_NAMES = {
    "HO-OH": "Ho-Oh",
    "PORYGON-Z": "Porygon-Z",
    "TYPE: NULL": "Type: Null",
    "JANGMO-O": "Jangmo-o",
    "HAKAMO-O": "Hakamo-o",
    "KOMMO-O": "Kommo-o",
    "FARFETCH'D": "Farfetch'd",
    "SIRFETCH'D": "Sirfetch'd",
    "MR. MIME": "Mr. Mime",
    "MIME JR.": "Mime Jr.",
    "FLABEBE": "Flabébé",
}


def load_charmap(charmap_path):
    charmap = {0xFF: ""}
    if not os.path.exists(charmap_path):
        return charmap

    with open(charmap_path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("@") or line.startswith("//"):
                continue
            if "=" in line:
                left, right = line.split("=", 1)
                left = left.strip()
                tokens = right.strip().split()
                if not tokens:
                    continue
                try:
                    val = int(tokens[0], 16)
                    # Prefer standard ASCII symbols
                    if left == r"'\''":
                        charmap[val] = "'"
                    elif left == "'-'":
                        charmap[val] = "-"
                    elif left == "' '":
                        charmap[val] = " "
                    elif left.startswith("'") and left.endswith("'") and len(left) >= 2:
                        parsed = left[1:-1]
                        if val not in charmap:
                            charmap[val] = parsed
                        elif parsed in ["-", "'", ".", " ", "♂", "♀"]:
                            charmap[val] = parsed
                except Exception:
                    pass

    # Explicit canonical overrides for GBA text decoding
    charmap[0x00] = " "
    charmap[0xAE] = "-"
    charmap[0xB4] = "'"
    charmap[0xB5] = "♂"
    charmap[0xB6] = "♀"
    charmap[0xAD] = "."
    charmap[0xFF] = ""
    return charmap


def decode_gba(byte_data, charmap):
    chars = []
    for b in byte_data:
        if b == 0xFF:
            break
        chars.append(charmap.get(b, "?"))
    return "".join(chars).strip()


def format_title_case(name):
    if not name:
        return ""
    if name in SPECIAL_NAMES:
        return SPECIAL_NAMES[name]

    tokens = re.split(r"([ \-\'\:\.\u2019])", name)
    out = []
    for token in tokens:
        if token in [" ", "-", "'", ":", ".", "\u2019"]:
            out.append(token)
        elif token.upper() in ["A", "G", "H", "P", "F", "M", "Z", "X", "Y"]:
            out.append(token.upper())
        elif token.upper() == "II":
            out.append("II")
        elif token.upper() == "NULL":
            out.append("Null")
        else:
            out.append(token.capitalize())
    return "".join(out)


def extract_data(rom_path, charmap):
    with open(rom_path, "rb") as f:
        rom = f.read()

    # Species extraction
    # Bulbasaur stats signature: 45, 49, 49, 45, 65, 65, 13, 4
    bulba_sig = bytes([45, 49, 49, 45, 65, 65, 13, 4])
    bulba_offset = rom.find(bulba_sig)
    if bulba_offset == -1:
        raise ValueError(f"Could not locate gSpeciesInfo in ROM {rom_path}")
    species_base = bulba_offset - 268

    species_list = []
    for idx in range(1, 1573):
        entry_offset = species_base + idx * 268
        chunk = rom[entry_offset : entry_offset + 268]
        hp, atk, def_, spe, spa, spd, t1, t2 = chunk[0:8]
        raw_name = decode_gba(chunk[44 : 44 + 13], charmap)
        if hp > 0 and raw_name and not raw_name.startswith("?"):
            name = format_title_case(raw_name)
            type1_str = TYPE_MAP.get(t1, "PokemonType.NORMAL")
            # If t2 is identical to t1 or is NONE/MYSTERY, type2 is null
            type2_str = TYPE_MAP.get(t2) if (t2 != t1 and t2 != 0 and t2 != 10) else "null"
            species_list.append({
                "id": idx,
                "name": name,
                "type1": type1_str,
                "type2": type2_str,
                "hp": hp,
                "atk": atk,
                "def": def_,
                "spa": spa,
                "spd": spd,
                "spe": spe,
            })

    # Move extraction
    # Pointer to POUND string in ROM: 0xCA, 0xC9, 0xCF, 0xC8, 0xBE, 0xFF
    pound_str = bytes([0xCA, 0xC9, 0xCF, 0xC8, 0xBE, 0xFF])
    pound_str_offset = rom.find(pound_str)
    if pound_str_offset == -1:
        raise ValueError("Could not locate POUND move string in ROM")
    pound_ptr = struct.pack("<I", 0x08000000 + pound_str_offset)
    pound_move_offset = rom.find(pound_ptr)
    if pound_move_offset == -1:
        raise ValueError("Could not locate gMovesInfo in ROM")
    moves_base = pound_move_offset - 68

    moves_list = []
    for idx in range(1, 1100):
        entry_offset = moves_base + idx * 68
        name_ptr = struct.unpack_from("<I", rom, entry_offset)[0]
        if name_ptr < 0x08000000 or name_ptr >= 0x09000000:
            break
        n_off = name_ptr - 0x08000000
        raw_name = decode_gba(rom[n_off : n_off + 25], charmap)
        if not raw_name or raw_name == "-":
            continue

        w1 = struct.unpack_from("<H", rom, entry_offset + 10)[0]
        t = w1 & 0x1F
        cat = (w1 >> 5) & 0x03
        pwr = (w1 >> 7) & 0x1FF
        w2 = struct.unpack_from("<H", rom, entry_offset + 12)[0]
        acc = w2 & 0x7F
        pp = struct.unpack_from("B", rom, entry_offset + 14)[0]

        moves_list.append({
            "id": idx,
            "name": format_title_case(raw_name),
            "type": TYPE_MAP.get(t, "PokemonType.NORMAL"),
            "category": CATEGORY_MAP.get(cat, "MoveCategory.PHYSICAL"),
            "power": pwr,
            "accuracy": acc,
            "pp": pp,
        })

    return species_list, moves_list


def generate_kotlin_code(species_list, moves_list):
    lines = []
    lines.append("package com.dualdex.pokemon.hns")
    lines.append("")
    lines.append("import com.dualdex.pokemon.GameDataPack")
    lines.append("import com.dualdex.pokemon.MoveCategory")
    lines.append("import com.dualdex.pokemon.MoveInfo")
    lines.append("import com.dualdex.pokemon.PokemonType")
    lines.append("import com.dualdex.pokemon.SpeciesInfo")
    lines.append("import com.dualdex.pokemon.TypeChart")
    lines.append("")
    lines.append("/**")
    lines.append(" * AUTO-GENERATED FILE - DO NOT EDIT MANUALLY.")
    lines.append(" * Generated by tools/hns-data-pack/generate_hns_data_pack.py")
    lines.append(" *")
    lines.append(" * Provenance:")
    lines.append(" *   Repository: PokemonHnS-Development/pokehns-expansion")
    lines.append(" *   Tag: Release-v2.0.5")
    lines.append(" *   Commit: 1f42b74dff0e9fe942419845d040663dd829a973")
    lines.append(" *   Authoritative Species Count: %d" % len(species_list))
    lines.append(" *   Authoritative Moves Count: %d" % len(moves_list))
    lines.append(" */")
    lines.append("object HeartAndSoul205DataPack : GameDataPack {")
    lines.append("    override val id: String = \"hns_2_0_5\"")
    lines.append("    override val generation: Int = 8")
    lines.append("    override val hasFairyType: Boolean = true")
    lines.append("    override val hasPhysicalSpecialSplit: Boolean = true")
    lines.append("")
    lines.append("    private val speciesMap = mutableMapOf<Int, SpeciesInfo>()")
    lines.append("    private val moveMap = mutableMapOf<Int, MoveInfo>()")
    lines.append("")
    lines.append("    override fun getSpecies(id: Int): SpeciesInfo? = speciesMap[id]")
    lines.append("    override fun getMove(id: Int): MoveInfo? = moveMap[id]")
    lines.append("")
    lines.append("    override fun getEffectiveness(attackType: PokemonType, defType: PokemonType): Double {")
    lines.append("        return TypeChart.getEffectiveness(attackType, defType, steelResistsGhostDark = false).toDouble()")
    lines.append("    }")
    lines.append("")
    lines.append("    override fun isSpeciesAuthoritative(id: Int): Boolean = speciesMap.containsKey(id)")
    lines.append("    override fun isMoveAuthoritative(id: Int): Boolean = moveMap.containsKey(id)")
    lines.append("")
    lines.append("    private fun registerSpecies(id: Int, name: String, t1: PokemonType, t2: PokemonType?,")
    lines.append("                                hp: Int, atk: Int, def: Int, spa: Int, spd: Int, spe: Int) {")
    lines.append("        speciesMap[id] = SpeciesInfo(id, name, t1, t2, hp, atk, def, spa, spd, spe)")
    lines.append("    }")
    lines.append("")
    lines.append("    private fun registerMove(id: Int, name: String, type: PokemonType, category: MoveCategory, power: Int, acc: Int, pp: Int) {")
    lines.append("        moveMap[id] = MoveInfo(id, name, type, category, power, acc, pp)")
    lines.append("    }")
    lines.append("")

    # Chunk registration calls
    chunk_size = 180
    species_chunks = [species_list[i : i + chunk_size] for i in range(0, len(species_list), chunk_size)]
    move_chunks = [moves_list[i : i + chunk_size] for i in range(0, len(moves_list), chunk_size)]

    lines.append("    init {")
    for c_idx in range(len(species_chunks)):
        lines.append(f"        registerSpeciesChunk{c_idx + 1}()")
    for c_idx in range(len(move_chunks)):
        lines.append(f"        registerMoveChunk{c_idx + 1}()")
    lines.append("    }")
    lines.append("")

    # Emit species chunks
    for c_idx, chunk in enumerate(species_chunks):
        lines.append(f"    private fun registerSpeciesChunk{c_idx + 1}() {{")
        for s in chunk:
            escaped_name = s["name"].replace('"', '\\"')
            lines.append(
                f"        registerSpecies({s['id']}, \"{escaped_name}\", {s['type1']}, {s['type2']}, "
                f"{s['hp']}, {s['atk']}, {s['def']}, {s['spa']}, {s['spd']}, {s['spe']})"
            )
        lines.append("    }")
        lines.append("")

    # Emit move chunks
    for c_idx, chunk in enumerate(move_chunks):
        lines.append(f"    private fun registerMoveChunk{c_idx + 1}() {{")
        for m in chunk:
            escaped_name = m["name"].replace('"', '\\"')
            lines.append(
                f"        registerMove({m['id']}, \"{escaped_name}\", {m['type']}, {m['category']}, "
                f"{m['power']}, {m['accuracy']}, {m['pp']})"
            )
        lines.append("    }")
        lines.append("")

    lines.append("}")
    lines.append("")
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description="Heart & Soul 2.0.5 Data Pack Generator")
    parser.add_argument("--upstream-dir", default=DEFAULT_UPSTREAM_DIR, help="Path to pokehns-expansion")
    parser.add_argument("--rom-path", default=None, help="Path to H&S 2.0.5 GBA ROM")
    parser.add_argument("--out-file", default=DEFAULT_OUT_FILE, help="Path to output Kotlin file")
    parser.add_argument("--verify", action="store_true", help="Verify existing file matches without writing")
    args = parser.parse_args()

    rom_path = args.rom_path
    if not rom_path or not os.path.exists(rom_path):
        if os.path.exists(DEFAULT_ROM_PATH):
            rom_path = DEFAULT_ROM_PATH
        elif os.path.exists(ALT_ROM_PATH):
            rom_path = ALT_ROM_PATH
        else:
            sys.exit(f"Error: Could not find H&S ROM at {DEFAULT_ROM_PATH} or {ALT_ROM_PATH}")

    charmap_path = os.path.join(args.upstream_dir, "charmap.txt")
    charmap = load_charmap(charmap_path)

    print(f"Extracting data from: {rom_path}")
    species_list, moves_list = extract_data(rom_path, charmap)
    print(f"Extracted {len(species_list)} species and {len(moves_list)} moves.")

    # Validate essential invariant: Gen 5 starter collision
    sp_by_id = {s["id"]: s for s in species_list}
    assert sp_by_id[500]["name"] == "Emboar", f"Expected Emboar at 500, got {sp_by_id[500]['name']}"
    assert sp_by_id[501]["name"] == "Oshawott", f"Expected Oshawott at 501, got {sp_by_id[501]['name']}"
    assert sp_by_id[502]["name"] == "Dewott", f"Expected Dewott at 502, got {sp_by_id[502]['name']}"

    code = generate_kotlin_code(species_list, moves_list)

    if args.verify:
        if not os.path.exists(args.out_file):
            sys.exit(f"Verification failed: output file {args.out_file} does not exist.")
        with open(args.out_file, "r", encoding="utf-8") as f:
            existing = f.read()
        if existing == code:
            print("Verification PASSED: generated code is identical.")
            sys.exit(0)
        else:
            sys.exit("Verification FAILED: generated code differs from existing file.")

    os.makedirs(os.path.dirname(args.out_file), exist_ok=True)
    with open(args.out_file, "w", encoding="utf-8") as f:
        f.write(code)
    print(f"Successfully generated: {args.out_file}")


if __name__ == "__main__":
    main()
