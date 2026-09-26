"""Deterministic scenario matrix for the H&S 2.0.5 differential damage oracle (issue #90).

Every scenario is a single measured hit described by pinned H&S symbols and explicit battle
operands. The matrix is pure data: the same code always yields the same scenario list, in the same
order, with the same IDs. Nothing here computes damage.

The type tables below are *planning hints* used only to pick attackers, defenders and moves that
exercise a given mechanic (and to declare which hits are type immunities, taken from the pinned
``src/data/types_info.h``). They are never used as expected values: the oracle records the battle
types, move type/power/category and every roll as the pinned engine reports them, and fails when a
declared expectation (for example ``expect: immune``) does not hold.

Surfaces:

* ``modelled``    -- mechanics the DualDex H&S calculator claims to reproduce exactly in production
                     (ordinary damage, type chart, STAB, crit, stat stages, burn, Rain/Sun, Singles
                     screens, badge boosts, pinch abilities, Wise Glasses, Fairy toggle, option style);
* ``engine-only`` -- arithmetic the calculator *engine* contains but production refuses or strips
                     (Doubles, Thick Fat, Guts, Huge/Pure Power, Adaptability, type-boost items).
"""

from __future__ import annotations

from oracle_schema import MAX_MEASURABLE_DAMAGE

DEFENDER_HP = 60000
assert DEFENDER_HP > MAX_MEASURABLE_DAMAGE

NEUTRAL_ABILITY = ("ABILITY_INSOMNIA", "Insomnia")

# label -> planning types (Fairy-on typings of the pinned species table).
SPECIES = {
    "Alakazam": ("Psychic",), "Arcanine": ("Fire",), "Banette": ("Ghost",), "Blastoise": ("Water",),
    "Charizard": ("Fire", "Flying"), "Chikorita": ("Grass",), "Clefable": ("Fairy",),
    "Croconaw": ("Water",), "Dragonite": ("Dragon", "Flying"), "Dugtrio": ("Ground",),
    "Electabuzz": ("Electric",), "Espeon": ("Psychic",), "Gardevoir": ("Psychic", "Fairy"),
    "Gengar": ("Ghost", "Poison"), "Glalie": ("Ice",), "Golem": ("Rock", "Ground"),
    "Granbull": ("Fairy",), "Gyarados": ("Water", "Flying"), "Hariyama": ("Fighting",),
    "Heracross": ("Bug", "Fighting"), "Hitmonlee": ("Fighting",), "Houndoom": ("Dark", "Fire"),
    "Jolteon": ("Electric",), "Kecleon": ("Normal",), "Lapras": ("Water", "Ice"),
    "Ludicolo": ("Water", "Grass"), "Machamp": ("Fighting",), "Magmar": ("Fire",),
    "Magneton": ("Electric", "Steel"), "Mawile": ("Steel", "Fairy"), "Muk": ("Poison",),
    "Pidgey": ("Normal", "Flying"), "Pinsir": ("Bug",), "Porygon": ("Normal",),
    "Registeel": ("Steel",), "Sableye": ("Dark", "Ghost"), "Scizor": ("Bug", "Steel"),
    "Shelgon": ("Dragon",), "Skarmory": ("Steel", "Flying"), "Snorlax": ("Normal",),
    "Sudowoodo": ("Rock",), "Swampert": ("Water", "Ground"), "Tangela": ("Grass",),
    "Togekiss": ("Fairy", "Flying"), "Corvisquire": ("Flying",), "Totodile": ("Water",),
    "Tyranitar": ("Rock", "Dark"), "Umbreon": ("Dark",), "Vaporeon": ("Water",),
    "Venusaur": ("Grass", "Poison"), "Azumarill": ("Water", "Fairy"), "Glaceon": ("Ice",),
    "Garchomp": ("Dragon", "Ground"),
}

# label -> (planning type, planning per-move category, planning power)
MOVES = {
    "Tackle": ("Normal", "physical", 40), "Scratch": ("Normal", "physical", 40),
    "Headbutt": ("Normal", "physical", 70), "Strength": ("Normal", "physical", 80),
    "Body Slam": ("Normal", "physical", 85), "Mega Kick": ("Normal", "physical", 120),
    "Swift": ("Normal", "special", 60), "Tri Attack": ("Normal", "special", 80),
    "Hyper Voice": ("Normal", "special", 90),
    "Karate Chop": ("Fighting", "physical", 50), "Sky Uppercut": ("Fighting", "physical", 85),
    "Aura Sphere": ("Fighting", "special", 80), "Focus Blast": ("Fighting", "special", 120),
    "Drill Peck": ("Flying", "physical", 80), "Wing Attack": ("Flying", "physical", 60),
    "Air Slash": ("Flying", "special", 75), "Gust": ("Flying", "special", 40),
    "Poison Jab": ("Poison", "physical", 80), "Sludge Bomb": ("Poison", "special", 90),
    "Sludge": ("Poison", "special", 65),
    "Bone Club": ("Ground", "physical", 65), "Earthquake": ("Ground", "physical", 100),
    "Earth Power": ("Ground", "special", 90), "Mud-Slap": ("Ground", "special", 20),
    "Rock Slide": ("Rock", "physical", 75), "Rock Throw": ("Rock", "physical", 50),
    "Power Gem": ("Rock", "special", 80),
    "X-Scissor": ("Bug", "physical", 80), "Megahorn": ("Bug", "physical", 120),
    "Bug Buzz": ("Bug", "special", 90), "Signal Beam": ("Bug", "special", 75),
    "Shadow Claw": ("Ghost", "physical", 70), "Shadow Punch": ("Ghost", "physical", 60),
    "Shadow Ball": ("Ghost", "special", 80),
    "Iron Head": ("Steel", "physical", 80), "Meteor Mash": ("Steel", "physical", 90),
    "Flash Cannon": ("Steel", "special", 80), "Mirror Shot": ("Steel", "special", 65),
    "Fire Punch": ("Fire", "physical", 75), "Fire Fang": ("Fire", "physical", 65),
    "Flamethrower": ("Fire", "special", 90), "Ember": ("Fire", "special", 40),
    "Heat Wave": ("Fire", "special", 95),
    "Waterfall": ("Water", "physical", 80), "Crabhammer": ("Water", "physical", 100),
    "Surf": ("Water", "special", 90), "Water Gun": ("Water", "special", 40),
    "Hydro Pump": ("Water", "special", 110), "Bubble Beam": ("Water", "special", 65),
    "Leaf Blade": ("Grass", "physical", 90), "Razor Leaf": ("Grass", "physical", 55),
    "Seed Bomb": ("Grass", "physical", 80), "Energy Ball": ("Grass", "special", 90),
    "Magical Leaf": ("Grass", "special", 60),
    "Thunder Punch": ("Electric", "physical", 75), "Spark": ("Electric", "physical", 65),
    "Thunderbolt": ("Electric", "special", 90), "Thunder Shock": ("Electric", "special", 40),
    "Zen Headbutt": ("Psychic", "physical", 80), "Psychic": ("Psychic", "special", 90),
    "Psybeam": ("Psychic", "special", 65), "Confusion": ("Psychic", "special", 50),
    "Ice Punch": ("Ice", "physical", 75), "Icicle Crash": ("Ice", "physical", 85),
    "Ice Fang": ("Ice", "physical", 65), "Ice Beam": ("Ice", "special", 90), "Powder Snow": ("Ice", "special", 40),
    "Dragon Claw": ("Dragon", "physical", 80), "Dragon Pulse": ("Dragon", "special", 85),
    "Dragon Breath": ("Dragon", "special", 60),
    "Crunch": ("Dark", "physical", 80), "Bite": ("Dark", "physical", 60),
    "Dark Pulse": ("Dark", "special", 80),
    "Play Rough": ("Fairy", "physical", 90), "Moonblast": ("Fairy", "special", 95),
    "Dazzling Gleam": ("Fairy", "special", 80), "Fairy Wind": ("Fairy", "special", 40),
}

TYPES = (
    "Normal", "Fighting", "Flying", "Poison", "Ground", "Rock", "Bug", "Ghost", "Steel",
    "Fire", "Water", "Grass", "Electric", "Psychic", "Ice", "Dragon", "Dark", "Fairy",
)

# Pinned src/data/types_info.h X(0.0) cells (attacking type, defending type).
IMMUNITIES = {
    ("Normal", "Ghost"), ("Fighting", "Ghost"), ("Poison", "Steel"), ("Ground", "Flying"),
    ("Ghost", "Normal"), ("Electric", "Ground"), ("Psychic", "Dark"), ("Dragon", "Fairy"),
}

# One physical and one special representative move per type.
TYPE_MOVES = {
    "Normal": ("Strength", "Swift"), "Fighting": ("Karate Chop", "Aura Sphere"),
    "Flying": ("Drill Peck", "Air Slash"), "Poison": ("Poison Jab", "Sludge Bomb"),
    "Ground": ("Bone Club", "Earth Power"), "Rock": ("Rock Slide", "Power Gem"),
    "Bug": ("X-Scissor", "Bug Buzz"), "Ghost": ("Shadow Claw", "Shadow Ball"),
    "Steel": ("Iron Head", "Flash Cannon"), "Fire": ("Fire Punch", "Flamethrower"),
    "Water": ("Waterfall", "Surf"), "Grass": ("Leaf Blade", "Energy Ball"),
    "Electric": ("Thunder Punch", "Thunderbolt"), "Psychic": ("Zen Headbutt", "Psychic"),
    "Ice": ("Ice Punch", "Ice Beam"), "Dragon": ("Dragon Claw", "Dragon Pulse"),
    "Dark": ("Crunch", "Dark Pulse"), "Fairy": ("Play Rough", "Moonblast"),
}

# A mono-typed species per type (defenders for the full mono chart, STAB attackers elsewhere).
MONO = {
    "Normal": "Snorlax", "Fighting": "Hitmonlee", "Flying": "Corvisquire", "Poison": "Muk",
    "Ground": "Dugtrio", "Rock": "Sudowoodo", "Bug": "Pinsir", "Ghost": "Banette",
    "Steel": "Registeel", "Fire": "Arcanine", "Water": "Vaporeon", "Grass": "Tangela",
    "Electric": "Jolteon", "Psychic": "Espeon", "Ice": "Glalie", "Dragon": "Shelgon",
    "Dark": "Umbreon", "Fairy": "Granbull",
}
STAB_ATTACKER = dict(MONO, Normal="Kecleon", Fighting="Hariyama", Fire="Magmar",
                     Water="Blastoise", Electric="Electabuzz", Psychic="Alakazam")

DUAL_DEFENDERS = (
    "Gyarados", "Charizard", "Skarmory", "Gengar", "Tyranitar", "Swampert", "Scizor", "Venusaur",
    "Dragonite", "Lapras", "Magneton", "Gardevoir", "Sableye", "Golem", "Ludicolo", "Heracross",
)

TYPE_BOOST_ITEMS = (
    ("ITEM_SILK_SCARF", "Silk Scarf", "Normal"), ("ITEM_BLACK_BELT", "Black Belt", "Fighting"),
    ("ITEM_SHARP_BEAK", "Sharp Beak", "Flying"), ("ITEM_POISON_BARB", "Poison Barb", "Poison"),
    ("ITEM_SOFT_SAND", "Soft Sand", "Ground"), ("ITEM_HARD_STONE", "Hard Stone", "Rock"),
    ("ITEM_SILVER_POWDER", "Silver Powder", "Bug"), ("ITEM_SPELL_TAG", "Spell Tag", "Ghost"),
    ("ITEM_METAL_COAT", "Metal Coat", "Steel"), ("ITEM_CHARCOAL", "Charcoal", "Fire"),
    ("ITEM_MYSTIC_WATER", "Mystic Water", "Water"), ("ITEM_MIRACLE_SEED", "Miracle Seed", "Grass"),
    ("ITEM_MAGNET", "Magnet", "Electric"), ("ITEM_TWISTED_SPOON", "Twisted Spoon", "Psychic"),
    ("ITEM_NEVER_MELT_ICE", "Never-Melt Ice", "Ice"), ("ITEM_DRAGON_FANG", "Dragon Fang", "Dragon"),
    ("ITEM_BLACK_GLASSES", "Black Glasses", "Dark"),
)

PINCH_ABILITIES = (
    ("ABILITY_OVERGROW", "Overgrow", "Grass", "Venusaur", "Leaf Blade", "Energy Ball"),
    ("ABILITY_BLAZE", "Blaze", "Fire", "Charizard", "Fire Punch", "Flamethrower"),
    ("ABILITY_TORRENT", "Torrent", "Water", "Blastoise", "Waterfall", "Surf"),
    ("ABILITY_SWARM", "Swarm", "Bug", "Scizor", "X-Scissor", "Bug Buzz"),
)


def symbol(prefix: str, label: str) -> str:
    cleaned = "".join(ch if ch.isalnum() else "_" for ch in label.upper().replace("'", ""))
    while "__" in cleaned:
        cleaned = cleaned.replace("__", "_")
    return f"{prefix}_{cleaned.strip('_')}"


def slug(text: str) -> str:
    out = "".join(ch if ch.isalnum() else "-" for ch in text.lower())
    while "--" in out:
        out = out.replace("--", "-")
    return out.strip("-")


def battler(label: str, *, level: int = 50, maxhp: int = 200, hp: int | None = None,
            atk: int = 100, dfn: int = 100, spa: int = 100, spd: int = 100, spe: int = 80,
            ability: tuple[str, str] = NEUTRAL_ABILITY, item: tuple[str, str] | None = None,
            status: str = "none", stages: dict | None = None, role: str) -> dict:
    if label not in SPECIES:
        raise KeyError(f"species {label!r} is not in the reviewed catalogue")
    if role == "defender":
        maxhp = DEFENDER_HP
        hp = DEFENDER_HP
        default_stages = {"defense": 0, "spDefense": 0}
    else:
        default_stages = {"attack": 0, "spAttack": 0}
    merged = dict(default_stages)
    for key, value in (stages or {}).items():
        if key not in merged:
            raise KeyError(f"{role} stage {key!r} is not settable")
        merged[key] = value
    return {
        "species": symbol("SPECIES", label),
        "speciesLabel": label,
        "level": level,
        "stats": {"maxHp": maxhp, "hp": maxhp if hp is None else hp, "attack": atk, "defense": dfn,
                  "spAttack": spa, "spDefense": spd, "speed": spe},
        "ability": ability[0],
        "abilityLabel": ability[1],
        "item": item[0] if item else "ITEM_NONE",
        "itemLabel": item[1] if item else None,
        "status": status,
        "stages": merged,
    }


def attacker(label: str, **kw) -> dict:
    return battler(label, role="attacker", **kw)


def defender(label: str, **kw) -> dict:
    kw.setdefault("spe", 40)
    return battler(label, role="defender", **kw)


def scenario(sid: str, tags: list[str], atk: dict, dfn: dict, move: str, *, crit: bool = False,
             weather: str = "none", reflect: bool = False, light_screen: bool = False,
             fairy: bool = True, style: str = "perMoveSplit", badges: tuple[int, ...] = (),
             side: str = "player", doubles: str | None = None, expect: str = "damage",
             surface: str = "modelled") -> dict:
    if move not in MOVES:
        raise KeyError(f"move {move!r} is not in the reviewed catalogue")
    return {
        "id": sid,
        "tags": sorted(set(tags)),
        "surface": surface,
        "format": "doubles" if doubles else "singles",
        "attackerSide": side,
        "rules": {"fairyTypes": fairy, "optionStyle": style},
        "badges": sorted(set(badges)),
        "attacker": atk,
        "defender": dfn,
        "move": {"symbol": symbol("MOVE", move), "label": move},
        "crit": crit,
        "field": {"weather": weather, "reflect": reflect, "lightScreen": light_screen},
        "doubles": {"defenderPartner": doubles} if doubles else None,
        "expect": expect,
    }


def planned_effect(move_type: str, defender_label: str) -> float:
    """Planning-only immunity check (pinned X(0.0) cells); never an expected value."""
    for t in SPECIES[defender_label]:
        if (move_type, t) in IMMUNITIES:
            return 0.0
    return 1.0


class _Lcg:
    """Tiny fixed-seed generator so sampled grids are reproducible without `random` state."""

    def __init__(self, seed: int) -> None:
        self.state = seed & 0xFFFFFFFF

    def next(self) -> int:
        self.state = (1103515245 * self.state + 12345) & 0x7FFFFFFF
        return self.state

    def pick(self, seq):
        return seq[self.next() % len(seq)]


def _xref() -> list[dict]:
    """Scenarios that reproduce the operands of existing hand-derived / ROM-observed fixtures."""
    out = []
    machamp = lambda **kw: attacker("Machamp", atk=150, spa=75, **kw)  # noqa: E731
    snorlax = lambda **kw: defender("Snorlax", dfn=85, spd=130, **kw)  # noqa: E731
    out.append(scenario("xref-c4a-neutral-base", ["xref"], machamp(), snorlax(), "Rock Slide"))
    out.append(scenario("xref-c4a-neutral-special", ["xref"],
                        attacker("Alakazam", atk=60, spa=155), snorlax(), "Heat Wave"))
    out.append(scenario("xref-c4a-stab", ["xref"], machamp(), snorlax(), "Karate Chop"))
    out.append(scenario("xref-c4a-crit-stab", ["xref"], machamp(), snorlax(), "Karate Chop", crit=True))
    out.append(scenario("xref-c4b-stat-stages", ["xref"], machamp(stages={"attack": 2}),
                        snorlax(stages={"defense": -1}), "Rock Slide"))
    out.append(scenario("xref-c4b-crit-ignores-stages", ["xref"], machamp(stages={"attack": -2}),
                        snorlax(stages={"defense": 2}), "Rock Slide", crit=True))
    out.append(scenario("xref-c4b-badge-attack", ["xref"], machamp(), snorlax(), "Rock Slide", badges=(1,)))
    out.append(scenario("xref-c4b-badge-defense", ["xref"], machamp(), snorlax(), "Rock Slide",
                        badges=(6,), side="opponent"))
    out.append(scenario("xref-c4b-weather-sun", ["xref"], attacker("Charizard", spa=129),
                        snorlax(), "Heat Wave", weather="sun"))
    out.append(scenario("xref-c4b-reflect", ["xref"], machamp(), snorlax(), "Rock Slide", reflect=True))
    out.append(scenario("xref-c4b-raw-stats", ["xref"], attacker("Machamp", atk=200),
                        defender("Snorlax", dfn=100), "Rock Slide"))
    out.append(scenario("xref-c4b-doubles-single-target", ["xref"], machamp(), snorlax(), "Strength",
                        doubles="present", surface="engine-only"))
    out.append(scenario("xref-c4b-doubles-spread-two", ["xref"], machamp(), snorlax(), "Rock Slide",
                        doubles="present", surface="engine-only"))
    out.append(scenario("xref-c4b-doubles-spread-one", ["xref"], machamp(), snorlax(), "Rock Slide",
                        doubles="fainted", surface="engine-only"))
    chiko5 = attacker("Chikorita", level=5, maxhp=20, atk=12, dfn=12, spa=11, spd=11, spe=8)
    pidgey3 = defender("Pidgey", level=3, atk=8, dfn=7, spa=7, spd=7, spe=8)
    out.append(scenario("xref-c4d-golden-a", ["rom-golden", "xref"], chiko5, pidgey3, "Tackle"))
    out.append(scenario("xref-c4d-golden-e-crit", ["rom-golden", "xref"], chiko5, pidgey3, "Tackle", crit=True))
    overgrow = ("ABILITY_OVERGROW", "Overgrow")
    for sid, hp in (("xref-c4e-overgrow-inactive", 11), ("xref-c4e-overgrow-active", 7),
                    ("xref-c4e-overgrow-edge-inactive", 8)):
        out.append(scenario(sid, ["pinch", "xref"],
                            attacker("Chikorita", level=6, maxhp=23, hp=hp, atk=13, dfn=13, spa=12, spd=12,
                                     spe=9, ability=overgrow),
                            pidgey3, "Razor Leaf"))
    out.append(scenario("xref-c4d-golden-b", ["rom-golden", "xref"],
                        attacker("Chikorita", level=6, maxhp=23, hp=11, atk=13, dfn=13, spa=12, spd=12,
                                 spe=9, ability=overgrow),
                        defender("Pidgey", level=3, atk=8, dfn=7, spa=7, spd=7, spe=8), "Razor Leaf"))
    out.append(scenario("xref-c4d-golden-c", ["rom-golden", "xref"],
                        attacker("Totodile", level=5, maxhp=20, atk=12, dfn=13, spa=9, spd=10, spe=9),
                        defender("Pidgey", level=2, atk=7, dfn=6, spa=5, spd=6, spe=7,
                                 stages={"defense": -1}), "Scratch"))
    porygon = lambda item=None: attacker("Porygon", level=10, maxhp=30, atk=15, dfn=17, spa=22, spd=19,  # noqa: E731
                                         spe=12, item=item)
    croconaw = lambda item=None: defender("Croconaw", level=10, atk=21, dfn=20, spa=17, spd=18, spe=16,  # noqa: E731
                                          item=item)
    wise = ("ITEM_WISE_GLASSES", "Wise Glasses")
    out.append(scenario("xref-c4f-wise-glasses-water-gun", ["item:wise-glasses", "xref"],
                        porygon(wise), croconaw(), "Water Gun"))
    out.append(scenario("xref-c4f-wise-glasses-swift", ["item:wise-glasses", "xref"],
                        porygon(wise), croconaw(), "Swift"))
    out.append(scenario("xref-c4f-wise-glasses-psybeam", ["item:wise-glasses", "xref"],
                        porygon(wise), croconaw(), "Psybeam"))
    out.append(scenario("xref-c4f-wise-glasses-tackle", ["item:wise-glasses", "xref"],
                        porygon(wise), croconaw(), "Tackle"))
    out.append(scenario("xref-c4f-defender-wise-glasses", ["item:wise-glasses", "xref"],
                        porygon(), croconaw(wise), "Water Gun"))
    out.append(scenario("xref-c4f-dragon-claw-per-move", ["item:wise-glasses", "xref"],
                        porygon(wise), croconaw(), "Dragon Claw"))
    out.append(scenario("xref-c4f-dragon-claw-type-based", ["item:wise-glasses", "xref"],
                        porygon(wise), croconaw(), "Dragon Claw", style="typeBased"))
    out.append(scenario("xref-c1-ghost-steel", ["type-chart", "xref"],
                        attacker("Gengar", spa=150), defender("Registeel", spd=170), "Shadow Ball"))
    # gap_c1_dark_steel requested Crunch with @smogon's Generation III type-based category (special).
    # Pinned H&S Crunch is physical under the per-move split, and the pinned type-based split makes
    # Dark physical too (src/data/types_info.h), so the fixture's special operands are carried by
    # pinned Dark Pulse (80, Dark, special).
    out.append(scenario("xref-c1-dark-steel", ["type-chart", "xref"],
                        attacker("Houndoom", spa=130), defender("Registeel", spd=170), "Dark Pulse"))
    out.append(scenario("xref-c1-fairy-offense", ["fairy", "xref"],
                        attacker("Clefable", spa=115), defender("Dragonite", spd=120), "Moonblast"))
    out.append(scenario("xref-c1-fairy-immunity", ["fairy", "immune", "xref"],
                        attacker("Dragonite", atk=154), defender("Clefable", dfn=93), "Dragon Claw",
                        expect="immune"))
    # Upstream expansion's own damage_formula.c vector (Bulbapedia's worked example): independent of
    # both DualDex and this oracle's scenario choices.
    out.append(scenario("xref-upstream-bulbapedia-glaceon", ["upstream-test", "xref"],
                        attacker("Glaceon", level=75, atk=123), defender("Garchomp", level=100, dfn=163),
                        "Ice Fang"))
    out.append(scenario("xref-c3-silk-scarf-forwarded", ["item:type-boost", "xref"],
                        attacker("Chikorita", level=6, maxhp=23, hp=11, atk=13, dfn=13, spa=12, spd=12, spe=9,
                                 item=("ITEM_SILK_SCARF", "Silk Scarf")),
                        pidgey3, "Tackle", surface="engine-only"))
    return out


def _chart_mono() -> list[dict]:
    out = []
    for mi, move_type in enumerate(TYPES):
        for di, def_type in enumerate(TYPES):
            category = "physical" if (mi + di) % 2 == 0 else "special"
            move = TYPE_MOVES[move_type][0 if category == "physical" else 1]
            atk_label = "Machamp" if move_type == "Normal" else "Porygon"
            dfn_label = MONO[def_type]
            immune = planned_effect(move_type, dfn_label) == 0.0
            out.append(scenario(
                f"chart-mono-{slug(move_type)}-vs-{slug(def_type)}",
                ["type-chart", "mono", category] + (["immune"] if immune else []),
                attacker(atk_label, atk=120, spa=120),
                defender(dfn_label, dfn=100, spd=100),
                move, expect="immune" if immune else "damage"))
    return out


def _chart_dual() -> list[dict]:
    out = []
    for mi, move_type in enumerate(TYPES):
        for di, dfn_label in enumerate(DUAL_DEFENDERS):
            category = "special" if (mi + di) % 2 == 0 else "physical"
            move = TYPE_MOVES[move_type][0 if category == "physical" else 1]
            immune = planned_effect(move_type, dfn_label) == 0.0
            out.append(scenario(
                f"chart-dual-{slug(move_type)}-vs-{slug(dfn_label)}",
                ["type-chart", "dual", "stab", category] + (["immune"] if immune else []),
                attacker(STAB_ATTACKER[move_type], atk=110, spa=110),
                defender(dfn_label, dfn=95, spd=105),
                move, expect="immune" if immune else "damage"))
    return out


def _arithmetic() -> list[dict]:
    """Base-formula rounding grid: level x BP x Attack x Defense, neutral, no STAB."""
    rng = _Lcg(0x90DA7A)
    levels = (1, 2, 5, 9, 13, 20, 27, 34, 42, 50, 63, 77, 88, 100)
    attacks = (5, 11, 18, 33, 47, 71, 99, 128, 150, 187, 233, 299, 350, 431, 512)
    defenses = (5, 9, 20, 37, 64, 85, 101, 142, 180, 230, 299, 377, 450, 600)
    physical = ("Tackle", "Headbutt", "Strength", "Body Slam", "Mega Kick")
    special = ("Swift", "Tri Attack", "Hyper Voice")
    out, seen = [], set()
    while len(out) < 150:
        level, a, d = rng.pick(levels), rng.pick(attacks), rng.pick(defenses)
        is_special = rng.next() % 3 == 0
        move = rng.pick(special if is_special else physical)
        key = (level, a, d, move)
        if key in seen:
            continue
        seen.add(key)
        atk_kw = {"spa": a, "atk": 50} if is_special else {"atk": a, "spa": 50}
        dfn_kw = {"spd": d, "dfn": 50} if is_special else {"dfn": d, "spd": 50}
        out.append(scenario(
            f"arith-l{level}-{slug(move)}-a{a}-d{d}",
            ["arithmetic", "special" if is_special else "physical"],
            attacker("Machamp", level=level, **atk_kw), defender("Snorlax", level=50, **dfn_kw), move))
    return out


def _min_damage() -> list[dict]:
    """Tiny hits where the pinned floor-to-1 applies after resistances, crits and burn."""
    out = []
    cases = (
        ("resisted", "Porygon", "Sudowoodo", "Tackle", {}),
        ("double-resisted", "Porygon", "Golem", "Tackle", {}),
        ("double-resisted-grass", "Porygon", "Charizard", "Leaf Blade", {}),
        ("neutral", "Machamp", "Snorlax", "Tackle", {}),
        ("crit-resisted", "Porygon", "Sudowoodo", "Tackle", {"crit": True}),
        ("burn-resisted", "Porygon", "Sudowoodo", "Tackle", {"burn": True}),
        ("special-resisted", "Porygon", "Registeel", "Ice Beam", {}),
        ("screen-resisted", "Porygon", "Sudowoodo", "Tackle", {"reflect": True}),
    )
    for name, atk_label, dfn_label, move, extra in cases:
        for level, a, d in ((1, 5, 600), (2, 9, 450), (5, 11, 230), (3, 7, 99)):
            out.append(scenario(
                f"min-{name}-l{level}-a{a}-d{d}", ["arithmetic", "min-damage"],
                attacker(atk_label, level=level, atk=a, spa=a, status="burn" if extra.get("burn") else "none",
                         maxhp=400),
                defender(dfn_label, dfn=d, spd=d), move, crit=extra.get("crit", False),
                reflect=extra.get("reflect", False)))
    return out


def _crit() -> list[dict]:
    out = []
    combos = (
        ("Machamp", "Snorlax", "Karate Chop"), ("Machamp", "Snorlax", "Rock Slide"),
        ("Alakazam", "Machamp", "Psychic"), ("Porygon", "Snorlax", "Swift"),
        ("Magmar", "Tangela", "Flamethrower"), ("Blastoise", "Golem", "Waterfall"),
    )
    for atk_label, dfn_label, move in combos:
        for atk_stage in (-2, 0, 2):
            for def_stage in (-1, 0, 2):
                special = MOVES[move][1] == "special"
                astage = {"spAttack" if special else "attack": atk_stage}
                dstage = {"spDefense" if special else "defense": def_stage}
                out.append(scenario(
                    f"crit-{slug(atk_label)}-{slug(move)}-a{atk_stage:+d}-d{def_stage:+d}".replace("+", "p"),
                    ["crit", "stages"],
                    attacker(atk_label, atk=140, spa=140, stages=astage),
                    defender(dfn_label, dfn=110, spd=110, stages=dstage), move, crit=True))
    return out


def _stages() -> list[dict]:
    out = []
    atk_stages = (-6, -4, -3, -2, -1, 1, 2, 3, 4, 6)
    def_stages = (-6, -2, -1, 0, 1, 2, 3, 6)
    rng = _Lcg(0x57A6E5)
    seen = set()
    for special in (False, True):
        for a in atk_stages:
            for _ in range(3):
                d = rng.pick(def_stages)
                if (special, a, d) in seen:
                    continue
                seen.add((special, a, d))
                move = "Psychic" if special else "Strength"
                astage = {"spAttack" if special else "attack": a}
                dstage = {"spDefense" if special else "defense": d}
                out.append(scenario(
                    f"stages-{'spec' if special else 'phys'}-a{a:+d}-d{d:+d}".replace("+", "p"),
                    ["stages", "special" if special else "physical"],
                    attacker("Machamp" if special else "Alakazam", atk=163, spa=163, stages=astage),
                    defender("Snorlax", dfn=127, spd=127, stages=dstage), move))
    for d in (-6, -3, 1, 4, 6):
        out.append(scenario(f"stages-def-only-d{d:+d}".replace("+", "p"), ["stages", "physical"],
                            attacker("Alakazam", atk=163), defender("Snorlax", dfn=127, stages={"defense": d}),
                            "Strength"))
    return out


def _burn() -> list[dict]:
    out = []
    for move in ("Strength", "Rock Slide", "Karate Chop", "Swift", "Psychic", "Fire Punch"):
        for crit in (False, True):
            out.append(scenario(
                f"burn-{slug(move)}{'-crit' if crit else ''}", ["burn"] + (["crit"] if crit else []),
                attacker("Machamp", atk=150, spa=120, status="burn", maxhp=240),
                defender("Snorlax", dfn=100, spd=100), move, crit=crit))
    out.append(scenario("burn-strength-reflect", ["burn", "screen"],
                        attacker("Machamp", atk=150, status="burn", maxhp=240),
                        defender("Snorlax", dfn=100), "Strength", reflect=True))
    out.append(scenario("burn-karate-chop-stage-p2", ["burn", "stages"],
                        attacker("Machamp", atk=150, status="burn", maxhp=240, stages={"attack": 2}),
                        defender("Snorlax", dfn=100), "Karate Chop"))
    out.append(scenario("burn-rain-waterfall", ["burn", "weather"],
                        attacker("Blastoise", atk=130, status="burn", maxhp=240),
                        defender("Snorlax", dfn=100), "Waterfall", weather="rain"))
    return out


def _weather() -> list[dict]:
    out = []
    moves = ("Fire Punch", "Flamethrower", "Waterfall", "Surf", "Strength", "Thunderbolt")
    for weather in ("rain", "sun"):
        for move in moves:
            for crit in (False, True):
                mtype = MOVES[move][0]
                atk_label = {"Fire": "Magmar", "Water": "Blastoise"}.get(mtype, "Porygon")
                out.append(scenario(
                    f"weather-{weather}-{slug(move)}{'-crit' if crit else ''}",
                    ["weather", f"weather:{weather}"] + (["crit"] if crit else []),
                    attacker(atk_label, atk=133, spa=133), defender("Snorlax", dfn=111, spd=111),
                    move, weather=weather, crit=crit))
        for move, dfn_label in (("Flamethrower", "Tangela"), ("Surf", "Arcanine"), ("Fire Punch", "Vaporeon")):
            out.append(scenario(
                f"weather-{weather}-{slug(move)}-vs-{slug(dfn_label)}", ["weather", f"weather:{weather}", "type-chart"],
                attacker("Porygon", atk=133, spa=133), defender(dfn_label, dfn=111, spd=111),
                move, weather=weather))
    return out


def _screens() -> list[dict]:
    out = []
    for screen in ("reflect", "light-screen"):
        for move in ("Strength", "Rock Slide", "Swift", "Psychic", "Thunderbolt", "Karate Chop"):
            for crit in (False, True):
                out.append(scenario(
                    f"screen-{screen}-{slug(move)}{'-crit' if crit else ''}",
                    ["screen", f"screen:{screen}"] + (["crit"] if crit else []),
                    attacker("Machamp", atk=160, spa=160), defender("Snorlax", dfn=120, spd=120), move,
                    reflect=screen == "reflect", light_screen=screen == "light-screen", crit=crit))
    out.append(scenario("screen-both-strength", ["screen"], attacker("Machamp", atk=160),
                        defender("Snorlax", dfn=120), "Strength", reflect=True, light_screen=True))
    out.append(scenario("screen-both-psychic", ["screen"], attacker("Machamp", spa=160),
                        defender("Snorlax", spd=120), "Psychic", reflect=True, light_screen=True))
    return out


def _pinch() -> list[dict]:
    out = []
    for ab_sym, ab_label, ab_type, species, phys, spec in PINCH_ABILITIES:
        ability = (ab_sym, ab_label)
        for maxhp in (23, 150, 200, 301):
            t = maxhp // 3
            for hp in (t, t + 1):
                for move in (phys, spec):
                    out.append(scenario(
                        f"pinch-{slug(ab_label)}-{slug(move)}-hp{hp}-of-{maxhp}",
                        ["pinch", f"ability:{slug(ab_label)}"],
                        attacker(species, atk=137, spa=137, maxhp=maxhp, hp=hp, ability=ability),
                        defender("Snorlax", dfn=101, spd=101), move))
        for move in ("Strength", "Swift"):
            out.append(scenario(
                f"pinch-{slug(ab_label)}-off-type-{slug(move)}", ["pinch", f"ability:{slug(ab_label)}"],
                attacker(species, atk=137, spa=137, maxhp=200, hp=1, ability=ability),
                defender("Snorlax", dfn=101, spd=101), move))
        out.append(scenario(
            f"pinch-{slug(ab_label)}-hp1-crit", ["pinch", "crit", f"ability:{slug(ab_label)}"],
            attacker(species, atk=137, spa=137, maxhp=200, hp=1, ability=ability),
            defender("Snorlax", dfn=101, spd=101), phys, crit=True))
    return out


def _wise_glasses() -> list[dict]:
    out = []
    wise = ("ITEM_WISE_GLASSES", "Wise Glasses")
    specials = ("Water Gun", "Confusion", "Swift", "Psybeam", "Bubble Beam", "Air Slash", "Tri Attack",
                "Psychic", "Heat Wave", "Hydro Pump", "Focus Blast", "Mud-Slap", "Dragon Pulse")
    for move in specials:
        out.append(scenario(f"wise-glasses-{slug(move)}", ["item:wise-glasses"],
                            attacker("Porygon", spa=121, item=wise), defender("Snorlax", spd=97), move))
    for move in ("Strength", "Crunch", "Dragon Claw", "Iron Head"):
        out.append(scenario(f"wise-glasses-physical-{slug(move)}", ["item:wise-glasses", "physical"],
                            attacker("Porygon", atk=121, item=wise), defender("Snorlax", dfn=97), move))
        out.append(scenario(f"wise-glasses-type-based-{slug(move)}", ["item:wise-glasses", "option-style"],
                            attacker("Porygon", atk=121, spa=121, item=wise), defender("Snorlax", dfn=97, spd=97),
                            move, style="typeBased"))
    for move in ("Psychic", "Strength"):
        out.append(scenario(f"wise-glasses-defender-{slug(move)}", ["item:wise-glasses"],
                            attacker("Porygon", atk=121, spa=121), defender("Snorlax", dfn=97, spd=97, item=wise),
                            move))
    out.append(scenario("wise-glasses-psychic-crit-stage", ["crit", "item:wise-glasses", "stages"],
                        attacker("Alakazam", spa=155, item=wise, stages={"spAttack": 1}),
                        defender("Snorlax", spd=130), "Psychic", crit=True))
    out.append(scenario("wise-glasses-surf-rain", ["item:wise-glasses", "weather"],
                        attacker("Blastoise", spa=140, item=wise), defender("Snorlax", spd=130), "Surf",
                        weather="rain"))
    return out


def _badges() -> list[dict]:
    out = []
    for badges in ((1,), (6,), (7,), (1, 6, 7), (3,)):
        tag = "-".join(str(b) for b in badges)
        for move in ("Strength", "Psychic"):
            out.append(scenario(f"badge-{tag}-player-attacks-{slug(move)}", ["badge"],
                                attacker("Machamp", atk=143, spa=143), defender("Snorlax", dfn=119, spd=119),
                                move, badges=badges))
            out.append(scenario(f"badge-{tag}-opponent-attacks-{slug(move)}", ["badge"],
                                attacker("Machamp", atk=143, spa=143), defender("Snorlax", dfn=119, spd=119),
                                move, badges=badges, side="opponent"))
    overgrow = ("ABILITY_OVERGROW", "Overgrow")
    for a in (13, 37, 101, 144, 199, 255):
        out.append(scenario(f"badge-pinch-overgrow-a{a}", ["badge", "pinch"],
                            attacker("Venusaur", atk=a, maxhp=150, hp=50, ability=overgrow),
                            defender("Snorlax", dfn=90), "Leaf Blade", badges=(1,)))
    out.append(scenario("badge-crit-stage", ["badge", "crit", "stages"],
                        attacker("Machamp", atk=143, stages={"attack": 1}),
                        defender("Snorlax", dfn=119), "Strength", badges=(1,), crit=True))
    # Badge scenarios are wild battles, whose opponent can only act in a single-turn battle, so the
    # stage is raised by the badge-holding player itself.
    out.append(scenario("badge-attack-stage-plus", ["badge", "stages"],
                        attacker("Machamp", atk=143, stages={"attack": 2}), defender("Snorlax", dfn=119),
                        "Strength", badges=(1,)))
    return out


def _rules() -> list[dict]:
    out = []
    fairy_moves = ("Moonblast", "Play Rough", "Dazzling Gleam", "Fairy Wind")
    for fairy in (True, False):
        mode = "on" if fairy else "off"
        for move in fairy_moves:
            for dfn_label in ("Dragonite", "Snorlax", "Umbreon"):
                out.append(scenario(f"fairy-{mode}-{slug(move)}-vs-{slug(dfn_label)}", ["fairy", f"fairy:{mode}"],
                                    attacker("Porygon", atk=125, spa=125), defender(dfn_label, dfn=105, spd=105),
                                    move, fairy=fairy))
        for atk_label, move in (("Clefable", "Moonblast"), ("Gardevoir", "Psychic"), ("Granbull", "Play Rough"),
                                ("Togekiss", "Air Slash"), ("Azumarill", "Waterfall")):
            out.append(scenario(f"fairy-{mode}-stab-{slug(atk_label)}-{slug(move)}", ["fairy", f"fairy:{mode}", "stab"],
                                attacker(atk_label, atk=125, spa=125), defender("Machamp", dfn=105, spd=105),
                                move, fairy=fairy))
        for dfn_label in ("Clefable", "Gardevoir", "Mawile", "Togekiss", "Azumarill"):
            immune = fairy and dfn_label in ("Clefable", "Gardevoir", "Mawile", "Togekiss", "Azumarill")
            out.append(scenario(f"fairy-{mode}-dragon-claw-vs-{slug(dfn_label)}", ["fairy", f"fairy:{mode}"]
                                + (["immune"] if immune else []),
                                attacker("Dragonite", atk=125), defender(dfn_label, dfn=105), "Dragon Claw",
                                fairy=fairy, expect="immune" if immune else "damage"))
    for move in ("Shadow Ball", "Shadow Claw", "Crunch", "Dark Pulse", "Fire Punch", "Karate Chop", "Aura Sphere",
                 "Dragon Claw", "Thunder Punch", "Poison Jab", "Sludge Bomb", "Ice Punch", "Waterfall", "Moonblast",
                 "Play Rough", "Zen Headbutt", "Earth Power", "Power Gem", "Air Slash", "Bug Buzz"):
        out.append(scenario(f"style-type-based-{slug(move)}", ["option-style"],
                            attacker("Porygon", atk=150, spa=90), defender("Machamp", dfn=90, spd=150), move,
                            style="typeBased"))
    out.append(scenario("style-type-based-fairy-off-moonblast", ["fairy:off", "option-style"],
                        attacker("Porygon", atk=150, spa=90), defender("Snorlax", dfn=90, spd=150), "Moonblast",
                        style="typeBased", fairy=False))
    out.append(scenario("style-type-based-fairy-off-play-rough", ["fairy:off", "option-style"],
                        attacker("Porygon", atk=150, spa=90), defender("Snorlax", dfn=90, spd=150), "Play Rough",
                        style="typeBased", fairy=False))
    return out


def _engine_abilities() -> list[dict]:
    out = []
    thick_fat = ("ABILITY_THICK_FAT", "Thick Fat")
    for move in ("Fire Punch", "Flamethrower", "Ice Punch", "Ice Beam", "Strength", "Surf"):
        out.append(scenario(f"engine-thick-fat-{slug(move)}", ["ability:thick-fat"],
                            attacker("Porygon", atk=141, spa=141), defender("Snorlax", dfn=113, spd=113, ability=thick_fat),
                            move, surface="engine-only"))
    out.append(scenario("engine-thick-fat-attacker-side", ["ability:thick-fat"],
                        attacker("Porygon", atk=141, ability=thick_fat), defender("Snorlax", dfn=113), "Fire Punch",
                        surface="engine-only"))
    guts = ("ABILITY_GUTS", "Guts")
    for status in ("burn", "poison", "none"):
        for move in ("Strength", "Psychic", "Karate Chop"):
            out.append(scenario(f"engine-guts-{status}-{slug(move)}", ["ability:guts"],
                                attacker("Machamp", atk=151, spa=151, maxhp=300, status=status, ability=guts),
                                defender("Snorlax", dfn=109, spd=109), move, surface="engine-only"))
    for ab_sym, ab_label in (("ABILITY_HUGE_POWER", "Huge Power"), ("ABILITY_PURE_POWER", "Pure Power")):
        for move in ("Strength", "Psychic", "Karate Chop"):
            for a in (97, 150):
                out.append(scenario(f"engine-{slug(ab_label)}-{slug(move)}-a{a}", [f"ability:{slug(ab_label)}"],
                                    attacker("Machamp", atk=a, spa=a, ability=(ab_sym, ab_label)),
                                    defender("Snorlax", dfn=109, spd=109), move, surface="engine-only"))
    adapt = ("ABILITY_ADAPTABILITY", "Adaptability")
    for move in ("Karate Chop", "Aura Sphere", "Strength", "Swift"):
        for crit in (False, True):
            out.append(scenario(f"engine-adaptability-{slug(move)}{'-crit' if crit else ''}",
                                ["ability:adaptability"] + (["crit"] if crit else []),
                                attacker("Machamp", atk=144, spa=144, ability=adapt),
                                defender("Snorlax", dfn=111, spd=111), move, crit=crit, surface="engine-only"))
    return out


def _engine_items() -> list[dict]:
    out = []
    for item_sym, item_label, item_type in TYPE_BOOST_ITEMS:
        phys, spec = TYPE_MOVES[item_type]
        dfn_label = "Machamp" if item_type == "Ghost" else "Snorlax"
        for move in (phys, spec):
            out.append(scenario(f"engine-item-{slug(item_label)}-{slug(move)}", ["item:type-boost"],
                                attacker("Kecleon", atk=129, spa=129, item=(item_sym, item_label)),
                                defender(dfn_label, dfn=103, spd=103), move, surface="engine-only"))
    out.append(scenario("engine-item-charcoal-off-type", ["item:type-boost"],
                        attacker("Kecleon", atk=129, item=("ITEM_CHARCOAL", "Charcoal")),
                        defender("Snorlax", dfn=103), "Waterfall", surface="engine-only"))
    out.append(scenario("engine-item-charcoal-defender", ["item:type-boost"],
                        attacker("Kecleon", atk=129), defender("Snorlax", dfn=103, item=("ITEM_CHARCOAL", "Charcoal")),
                        "Fire Punch", surface="engine-only"))
    return out


def _doubles() -> list[dict]:
    out = []
    for move in ("Strength", "Psychic", "Rock Slide", "Heat Wave", "Hyper Voice", "Dazzling Gleam", "Razor Leaf"):
        for partner in ("present", "fainted"):
            for crit in (False, True):
                out.append(scenario(
                    f"doubles-{slug(move)}-partner-{partner}{'-crit' if crit else ''}",
                    ["doubles", f"doubles:{partner}"] + (["crit"] if crit else []),
                    attacker("Machamp", atk=145, spa=145), defender("Snorlax", dfn=107, spd=107), move,
                    doubles=partner, crit=crit, surface="engine-only"))
    for move, screen in (("Strength", "reflect"), ("Rock Slide", "reflect"), ("Psychic", "light-screen"),
                         ("Heat Wave", "light-screen")):
        for partner in ("present", "fainted"):
            out.append(scenario(f"doubles-{screen}-{slug(move)}-partner-{partner}", ["doubles", "screen"],
                                attacker("Machamp", atk=145, spa=145), defender("Snorlax", dfn=107, spd=107), move,
                                doubles=partner, reflect=screen == "reflect",
                                light_screen=screen == "light-screen", surface="engine-only"))
    for move in ("Earthquake", "Surf"):
        out.append(scenario(f"doubles-{slug(move)}-all-present", ["doubles"],
                            attacker("Machamp", atk=145, spa=145), defender("Snorlax", dfn=107, spd=107), move,
                            doubles="present", surface="engine-only"))
    out.append(scenario("doubles-rain-heat-wave", ["doubles", "weather"],
                        attacker("Magmar", spa=145), defender("Snorlax", spd=107), "Heat Wave",
                        doubles="present", weather="rain", surface="engine-only"))
    return out


def build_scenarios() -> list[dict]:
    """The complete, deterministic scenario list (sorted by ID)."""
    groups = (_xref, _chart_mono, _chart_dual, _arithmetic, _min_damage, _crit, _stages, _burn,
              _weather, _screens, _pinch, _wise_glasses, _badges, _rules, _engine_abilities,
              _engine_items, _doubles)
    scenarios = [s for group in groups for s in group()]
    return sorted(scenarios, key=lambda s: s["id"])
