"""Deterministic scenario matrix for the H&S 2.0.5 differential damage oracle (issue #90).

Every scenario is a single measured hit described by pinned H&S symbols and explicit battle
operands. The matrix is pure data: the same code always yields the same scenario list, in the same
order, with the same IDs. Nothing here computes damage.

The type tables below are *planning hints* used only to pick attackers, defenders and moves that
exercise a given mechanic (and to declare which hits are type immunities, taken from the pinned
``src/data/types_info.h``). They are never used as expected values: the oracle records the battle
types, move type/power/category and every roll as the pinned engine reports them, and fails when a
declared expectation (for example ``expect: immune``) does not hold.

Surfaces describe each scenario's actual production contract, not a blanket format restriction.
The authoritative ordinary-hit Doubles subset is now production-modelled; historical scenario
surfaces and vectors remain unchanged unless their individual contract warrants reclassification.

Surfaces:

* ``modelled``    -- mechanics the DualDex H&S calculator claims to reproduce exactly in production
                     (ordinary damage, type chart, STAB, crit, stat stages, burn, Rain/Sun, Singles
                     screens, badge boosts, pinch abilities, physical Hustle, statused-physical Guts,
                     Wise Glasses, Fairy toggle, option style, Normalize, the four -ate rewrites and
                     their explicit ateBoost behavior, sound-gated Liquid Voice, Adaptability STAB,
                     and the first low-state final ability modifiers);
* ``engine-only`` -- arithmetic the calculator *engine* contains but production refuses or strips
                     (historical/direct-JS or deliberately unsupported Doubles scenarios,
                     Thick Fat, Guts contexts outside the admitted physical/status path,
                     Huge/Pure Power, type-boost items).
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
    "Garchomp": ("Dragon", "Ground"), "Cherrim": ("Grass",), "Cherrim-Sunshine": ("Grass",),
    "Pikachu": ("Electric",), "Pikachu-Rock-Star": ("Electric",), "Pichu": ("Electric",),
    "Cubone": ("Ground",), "Marowak": ("Ground",), "Marowak-Alola": ("Fire", "Ghost"),
    "Clamperl": ("Water",), "Ditto": ("Normal",), "Latias": ("Dragon", "Psychic"),
    "Latios": ("Dragon", "Psychic"), "Dialga": ("Steel", "Dragon"),
    "Dialga-Origin": ("Steel", "Dragon"), "Palkia": ("Water", "Dragon"),
    "Palkia-Origin": ("Water", "Dragon"), "Giratina": ("Ghost", "Dragon"),
    "Giratina-Origin": ("Ghost", "Dragon"), "Ogerpon-Teal": ("Grass",),
    "Ogerpon-Wellspring": ("Grass", "Water"), "Ogerpon-Hearthflame": ("Grass", "Fire"),
    "Ogerpon-Cornerstone": ("Grass", "Rock"), "Kyogre-Primal": ("Water",),
    "Groudon-Primal": ("Ground",),
}

# label -> (planning type, planning per-move category, planning power)
MOVES = {
    "U-Turn": ("Bug", "physical", 70),
    "Volt Switch": ("Electric", "special", 70),
    "Flip Turn": ("Water", "physical", 60),
    "Rollout": ("Rock", "physical", 30),
    "Ice Ball": ("Ice", "physical", 30),
    "Brine": ("Water", "special", 65),
    "Gyro Ball": ("Steel", "physical", 1),
    "Electro Ball": ("Electric", "special", 1),
    "Smelling Salts": ("Normal", "physical", 70), "Wake-Up Slap": ("Fighting", "physical", 70),
    "Venoshock": ("Poison", "special", 65), "Hex": ("Ghost", "special", 65),
    "Barb Barrage": ("Poison", "physical", 60), "Infernal Parade": ("Ghost", "special", 60),
    "Self-Destruct": ("Normal", "physical", 200), "Explosion": ("Normal", "physical", 250),
    "Petal Blizzard": ("Grass", "physical", 90),
    "Tackle": ("Normal", "physical", 40), "Scratch": ("Normal", "physical", 40),
    "Headbutt": ("Normal", "physical", 70), "Strength": ("Normal", "physical", 80),
    "Body Slam": ("Normal", "physical", 85), "Mega Kick": ("Normal", "physical", 120),
    "Swift": ("Normal", "special", 60), "Quick Attack": ("Normal", "physical", 40), "Tri Attack": ("Normal", "special", 80),
    "Hyper Voice": ("Normal", "special", 90),
    "Relic Song": ("Normal", "special", 75),
    "Karate Chop": ("Fighting", "physical", 50), "Sky Uppercut": ("Fighting", "physical", 85),
    "Aura Sphere": ("Fighting", "special", 80), "Focus Blast": ("Fighting", "special", 120),
    "Drill Peck": ("Flying", "physical", 80), "Wing Attack": ("Flying", "physical", 60),
    "Air Slash": ("Flying", "special", 75), "Gust": ("Flying", "special", 40),
    "Poison Jab": ("Poison", "physical", 80), "Sludge Bomb": ("Poison", "special", 90),
    "Sludge": ("Poison", "special", 65),
    "Bone Club": ("Ground", "physical", 65), "Earthquake": ("Ground", "physical", 100), "Bulldoze": ("Ground", "physical", 60),
    "Earth Power": ("Ground", "special", 90), "Mud-Slap": ("Ground", "special", 20),
    "Rock Slide": ("Rock", "physical", 75), "Rock Throw": ("Rock", "physical", 50),
    "Power Gem": ("Rock", "special", 80),
    "X-Scissor": ("Bug", "physical", 80), "Megahorn": ("Bug", "physical", 120),
    "Bug Buzz": ("Bug", "special", 90), "Signal Beam": ("Bug", "special", 75),
    "Shadow Claw": ("Ghost", "physical", 70), "Shadow Punch": ("Ghost", "physical", 60),
    "Shadow Ball": ("Ghost", "special", 80), "Moongeist Beam": ("Ghost", "special", 100),
    "Iron Head": ("Steel", "physical", 80), "Meteor Mash": ("Steel", "physical", 90),
    "Metal Claw": ("Steel", "physical", 50),
    "Flash Cannon": ("Steel", "special", 80), "Mirror Shot": ("Steel", "special", 65),
    "Sunsteel Strike": ("Steel", "physical", 100),
    "Fire Punch": ("Fire", "physical", 75), "Fire Fang": ("Fire", "physical", 65),
    "Flamethrower": ("Fire", "special", 90), "Ember": ("Fire", "special", 40),
    "Fire Blast": ("Fire", "special", 110), "Scald": ("Water", "special", 80), "Pay Day": ("Normal", "physical", 40),
    "Heat Wave": ("Fire", "special", 95),
    "Waterfall": ("Water", "physical", 80), "Crabhammer": ("Water", "physical", 100),
    "Whirlpool": ("Water", "special", 35), "Surf": ("Water", "special", 90), "Water Gun": ("Water", "special", 40),
    "Hydro Pump": ("Water", "special", 110), "Bubble Beam": ("Water", "special", 65),
    "Leaf Blade": ("Grass", "physical", 90), "Razor Leaf": ("Grass", "physical", 55),
    "Vine Whip": ("Grass", "physical", 45),
    "Seed Bomb": ("Grass", "physical", 80), "Bullet Seed": ("Grass", "physical", 25), "Energy Ball": ("Grass", "special", 90),
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
    "Brine": ("Water", "special", 65),
    "Gyro Ball": ("Steel", "physical", 1),
    "Electro Ball": ("Electric", "special", 1),
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
    ("ITEM_BLACK_GLASSES", "Black Glasses", "Dark"), ("ITEM_FAIRY_FEATHER", "Fairy Feather", "Fairy"),
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


def battler(label: str, *, level: int = 50, maxhp: int | None = None, hp: int | None = None,
            atk: int = 100, dfn: int = 100, spa: int = 100, spd: int = 100, spe: int = 80,
            ability: tuple[str, str] = NEUTRAL_ABILITY, item: tuple[str, str] | None = None,
            status: str = "none", stages: dict | None = None, role: str) -> dict:
    if label not in SPECIES:
        raise KeyError(f"species {label!r} is not in the reviewed catalogue")
    if role == "defender":
        maxhp = DEFENDER_HP if maxhp is None else maxhp
        default_stages = {"defense": 0, "spDefense": 0}
    else:
        maxhp = 200 if maxhp is None else maxhp
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
             terrain: str = "none", gravity: bool = False,
             fairy: bool = True, style: str = "perMoveSplit", badges: tuple[int, ...] = (),
             side: str = "player", doubles: str | None = None, expect: str = "damage",
             surface: str = "modelled", state_setup: dict | None = None) -> dict:
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
        "field": {"weather": weather, "reflect": reflect, "lightScreen": light_screen,
                  "terrain": terrain, "gravity": gravity},
        "doubles": {"defenderPartner": doubles} if doubles else None,
        "stateSetup": state_setup,
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


def _group_c_immunities() -> list[dict]:
    """Pinned ability, move-flag, priority, and item immunity gates with negative controls."""
    out = []
    at = lambda **kw: attacker("Machamp", atk=145, spa=145, **kw)  # noqa: E731
    df = lambda **kw: defender("Snorlax", dfn=107, spd=107, **kw)  # noqa: E731

    absorbers = (
        ("Volt Absorb", "Electric", "Thunderbolt"),
        ("Motor Drive", "Electric", "Thunderbolt"),
        ("Lightning Rod", "Electric", "Thunderbolt"),
        ("Water Absorb", "Water", "Surf"),
        ("Storm Drain", "Water", "Surf"),
        ("Dry Skin", "Water", "Surf"),
        ("Sap Sipper", "Grass", "Leaf Blade"),
        ("Earth Eater", "Ground", "Earthquake"),
        ("Well-Baked Body", "Fire", "Flamethrower"),
        ("Flash Fire", "Fire", "Flamethrower"),
    )
    for ability, move_type, move in absorbers:
        out.append(scenario(
            f"group-c-absorb-{slug(ability)}", ["group-c-immunity", "ability-immunity", f"ability:{slug(ability)}"],
            at(), df(ability=(symbol("ABILITY", ability), ability)), move, expect="immune"))

    out.append(scenario("group-c-dry-skin-fire-boost", ["group-c-immunity", "ability-boost", "ability:dry-skin"],
                        at(), df(ability=("ABILITY_DRY_SKIN", "Dry Skin")), "Flamethrower"))
    wise_glasses = ("ITEM_WISE_GLASSES", "Wise Glasses")
    out.append(scenario(
        "group-c-dry-skin-wise-glasses-control",
        ["group-c-immunity", "ability-boost", "ability:dry-skin", "negative-control", "item:wise-glasses"],
        attacker("Machamp", atk=145, spa=40),
        defender("Snorlax", dfn=107, spd=41, ability=("ABILITY_DRY_SKIN", "Dry Skin")), "Flamethrower"))
    out.append(scenario(
        "group-c-dry-skin-wise-glasses",
        ["group-c-immunity", "ability-boost", "ability:dry-skin", "item:wise-glasses", "modifier-stacking"],
        attacker("Machamp", atk=145, spa=40, item=wise_glasses),
        defender("Snorlax", dfn=107, spd=41, ability=("ABILITY_DRY_SKIN", "Dry Skin")), "Flamethrower"))

    for ability, move, flag in (
        ("Soundproof", "Hyper Voice", "sound"),
        ("Bulletproof", "Bullet Seed", "ballistic"),
        ("Wind Rider", "Gust", "wind"),
    ):
        out.append(scenario(
            f"group-c-flag-{slug(ability)}", ["group-c-immunity", "move-flag-immunity", f"ability:{slug(ability)}"],
            at(), df(ability=(symbol("ABILITY", ability), ability)), move, expect="immune"))
        out.append(scenario(
            f"group-c-flag-control-{slug(ability)}", ["group-c-immunity", "negative-control", f"flag-control:{flag}"],
            at(), df(ability=(symbol("ABILITY", ability), ability)), "Thunderbolt"))

    for ability in ("Queenly Majesty", "Dazzling", "Armor Tail"):
        out.append(scenario(
            f"group-c-priority-{slug(ability)}", ["group-c-immunity", "priority-immunity", f"ability:{slug(ability)}"],
            at(), df(ability=(symbol("ABILITY", ability), ability)), "Quick Attack", expect="immune"))
        out.append(scenario(
            f"group-c-priority-control-{slug(ability)}", ["group-c-immunity", "negative-control", "priority-control"],
            at(), df(ability=(symbol("ABILITY", ability), ability)), "Swift"))

    out.append(scenario("group-c-wonder-guard-neutral", ["group-c-immunity", "ability-immunity", "ability:wonder-guard"],
                        at(), df(ability=("ABILITY_WONDER_GUARD", "Wonder Guard")), "Tackle", expect="immune"))
    ability_shield = ("ITEM_ABILITY_SHIELD", "Ability Shield")
    out.append(scenario("group-c-wonder-guard-sunsteel-strike", ["group-c-immunity", "ability:wonder-guard",
                        "move-ability-bypass"], at(), df(ability=("ABILITY_WONDER_GUARD", "Wonder Guard")),
                        "Sunsteel Strike"))
    out.append(scenario("group-c-wonder-guard-sunsteel-strike-ability-shield", ["group-c-immunity",
                        "ability:wonder-guard", "item:ability-shield", "move-ability-bypass"], at(),
                        df(ability=("ABILITY_WONDER_GUARD", "Wonder Guard"), item=ability_shield),
                        "Sunsteel Strike", expect="immune"))
    out.append(scenario("group-c-wonder-guard-ability-shield-control", ["group-c-immunity",
                        "ability:wonder-guard", "item:ability-shield", "negative-control"], at(),
                        df(ability=("ABILITY_WONDER_GUARD", "Wonder Guard"), item=ability_shield),
                        "Tackle", expect="immune"))
    out.append(scenario("group-c-wonder-guard-resisted", ["group-c-immunity", "ability-immunity", "ability:wonder-guard"],
                        at(), defender("Sudowoodo", dfn=107, spd=107,
                                       ability=("ABILITY_WONDER_GUARD", "Wonder Guard")), "Tackle", expect="immune"))
    out.append(scenario("group-c-wonder-guard-chart-immunity", ["group-c-immunity", "type-immunity", "ability:wonder-guard"],
                        at(), defender("Sableye", dfn=107, spd=107,
                                       ability=("ABILITY_WONDER_GUARD", "Wonder Guard")), "Tackle", expect="immune"))
    out.append(scenario("group-c-wonder-guard-super-effective", ["group-c-immunity", "negative-control", "ability:wonder-guard"],
                        at(), defender("Charizard", dfn=107, spd=107,
                                       ability=("ABILITY_WONDER_GUARD", "Wonder Guard")), "Rock Slide"))
    out.append(scenario("group-c-levitate", ["group-c-immunity", "ability-immunity", "ability:levitate"],
                        at(), df(ability=("ABILITY_LEVITATE", "Levitate")), "Earthquake", expect="immune"))
    out.append(scenario("group-c-levitate-control", ["group-c-immunity", "negative-control", "ability:levitate"],
                        at(), df(ability=("ABILITY_LEVITATE", "Levitate")), "Thunderbolt"))

    out.append(scenario("group-c-air-balloon", ["group-c-immunity", "item-immunity", "item:air-balloon"],
                        at(), df(item=("ITEM_AIR_BALLOON", "Air Balloon")), "Earthquake", expect="immune"))
    out.append(scenario("group-c-iron-ball-grounding", ["group-c-immunity", "negative-control", "item:iron-ball"],
                        at(), defender("Pidgey", dfn=107, spd=107, item=("ITEM_IRON_BALL", "Iron Ball")), "Earthquake"))
    out.append(scenario("group-c-iron-ball-control", ["group-c-immunity", "negative-control", "item:iron-ball"],
                        at(), df(item=("ITEM_IRON_BALL", "Iron Ball")), "Tackle"))
    out.append(scenario("group-c-float-stone-no-op", ["group-c-immunity", "negative-control", "item:float-stone"],
                        at(), df(item=("ITEM_FLOAT_STONE", "Float Stone")), "Tackle"))
    out.append(scenario("group-c-ring-target", ["group-c-immunity", "item-type-rewrite", "item:ring-target"],
                        at(), defender("Clefable", dfn=107, spd=107, item=("ITEM_RING_TARGET", "Ring Target")),
                        "Dragon Claw"))
    out.append(scenario("group-c-ring-target-control", ["group-c-immunity", "negative-control", "item:ring-target"],
                        at(), defender("Clefable", dfn=107, spd=107), "Dragon Claw", expect="immune"))
    out.append(scenario("group-c-ring-target-dual", ["group-c-immunity", "item-type-rewrite", "item:ring-target"],
                        at(), defender("Sableye", dfn=107, spd=107, item=("ITEM_RING_TARGET", "Ring Target")),
                        "Karate Chop"))
    out.append(scenario("group-c-ring-target-dual-control", ["group-c-immunity", "negative-control", "item:ring-target"],
                        at(), defender("Sableye", dfn=107, spd=107), "Karate Chop", expect="immune"))
    out.append(scenario("group-c-ring-target-does-not-clear-ability", ["group-c-immunity", "negative-control", "item:ring-target"],
                        at(), df(item=("ITEM_RING_TARGET", "Ring Target"),
                                 ability=("ABILITY_VOLT_ABSORB", "Volt Absorb")), "Thunderbolt", expect="immune"))
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


def _attack_stat_abilities() -> list[dict]:
    """Group D's first Attack-stat batch: source-backed Hustle and Guts controls."""
    out = []
    hustle = ("ABILITY_HUSTLE", "Hustle")
    guts = ("ABILITY_GUTS", "Guts")
    out.append(scenario(
        "group-d-hustle-physical-badge-a255", ["ability:hustle", "attack-stat", "modifier-stacking"],
        attacker("Machamp", atk=255, ability=hustle), defender("Snorlax", dfn=109), "Strength", badges=(1,)))
    out.append(scenario(
        "group-d-hustle-special-control", ["ability:hustle", "attack-stat", "negative-control"],
        attacker("Machamp", spa=151, ability=hustle), defender("Snorlax", spd=109), "Psychic"))
    out.append(scenario(
        "group-d-hustle-type-based-ghost-special", ["ability:hustle", "attack-stat", "option-style", "negative-control"],
        attacker("Machamp", spa=151, ability=hustle), defender("Machamp", spd=109), "Shadow Ball",
        style="typeBased"))
    out.append(scenario(
        "group-d-hustle-type-based-dark-physical", ["ability:hustle", "attack-stat", "option-style"],
        attacker("Machamp", atk=151, ability=hustle), defender("Snorlax", dfn=109), "Crunch",
        style="typeBased"))
    out.append(scenario(
        "group-d-hustle-defender-control", ["ability:hustle", "attack-stat", "negative-control"],
        attacker("Machamp", atk=151), defender("Snorlax", dfn=109, ability=hustle), "Strength"))

    out.append(scenario(
        "group-d-guts-burn-physical-badge-crit-a255", ["ability:guts", "attack-stat", "badge", "crit", "modifier-stacking"],
        attacker("Machamp", atk=255, maxhp=300, status="burn", ability=guts),
        defender("Snorlax", dfn=109), "Strength", badges=(1,), crit=True))
    out.append(scenario(
        "group-d-guts-poison-physical", ["ability:guts", "attack-stat", "status:poison"],
        attacker("Machamp", atk=151, maxhp=300, status="poison", ability=guts),
        defender("Snorlax", dfn=109), "Karate Chop"))
    out.append(scenario(
        "group-d-guts-physical-no-status-control", ["ability:guts", "attack-stat", "negative-control"],
        attacker("Machamp", atk=151, maxhp=300, status="none", ability=guts),
        defender("Snorlax", dfn=109), "Strength"))
    out.append(scenario(
        "group-d-guts-special-status-control", ["ability:guts", "attack-stat", "negative-control"],
        attacker("Machamp", spa=151, maxhp=300, status="burn", ability=guts),
        defender("Snorlax", spd=109), "Psychic", surface="engine-only"))
    out.append(scenario(
        "group-d-guts-type-based-ghost-special-status", ["ability:guts", "attack-stat", "option-style", "status:burn", "negative-control"],
        attacker("Machamp", spa=151, maxhp=300, status="burn", ability=guts),
        defender("Machamp", spd=109), "Shadow Ball", style="typeBased"))
    out.append(scenario(
        "group-d-guts-type-based-dark-physical-status", ["ability:guts", "attack-stat", "option-style", "status:burn"],
        attacker("Machamp", atk=151, maxhp=300, status="burn", ability=guts),
        defender("Snorlax", dfn=109), "Crunch", style="typeBased"))
    out.append(scenario(
        "group-d-guts-defender-control", ["ability:guts", "attack-stat", "negative-control"],
        attacker("Machamp", atk=151), defender("Snorlax", dfn=109, ability=guts), "Strength"))
    return out


def _low_state_stat_abilities() -> list[dict]:
    """Group D low-state Attack/Defense stat-stage abilities."""
    out = []
    solar = ("ABILITY_SOLAR_POWER", "Solar Power")
    defeatist = ("ABILITY_DEFEATIST", "Defeatist")
    fur_coat = ("ABILITY_FUR_COAT", "Fur Coat")
    mold_breaker = ("ABILITY_MOLD_BREAKER", "Mold Breaker")
    ability_shield = ("ITEM_ABILITY_SHIELD", "Ability Shield")
    umbrella = ("ITEM_UTILITY_UMBRELLA", "Utility Umbrella")
    drought = ("ABILITY_DROUGHT", "Drought")

    out.extend((
        scenario("group-d-solar-power-special-sun-rounding", ["ability:solar-power", "attack-stat", "weather:sun", "rounding"],
                 attacker("Alakazam", spa=153, ability=solar), defender("Snorlax", spd=107, ability=drought), "Psychic", weather="sun"),
        scenario("group-d-solar-power-special-clear-control", ["ability:solar-power", "attack-stat", "negative-control"],
                 attacker("Alakazam", spa=153, ability=solar), defender("Snorlax", spd=107), "Psychic"),
        scenario("group-d-solar-power-special-rain-control", ["ability:solar-power", "attack-stat", "negative-control", "weather:rain"],
                 attacker("Alakazam", spa=153, ability=solar), defender("Snorlax", spd=107), "Psychic", weather="rain"),
        scenario("group-d-solar-power-physical-sun-control", ["ability:solar-power", "attack-stat", "negative-control", "weather:sun"],
                 attacker("Machamp", atk=153, ability=solar), defender("Snorlax", dfn=107, ability=drought), "Strength", weather="sun"),
        scenario("group-d-solar-power-type-based-final-special", ["ability:solar-power", "attack-stat", "option-style", "category-shift", "weather:sun"],
                 attacker("Alakazam", spa=153, ability=solar), defender("Skarmory", spd=107, ability=drought), "Shadow Claw", style="typeBased", weather="sun"),
        scenario("group-d-solar-power-special-sun-crit-negative-stage", ["ability:solar-power", "attack-stat", "weather:sun", "crit", "stages"],
                 attacker("Alakazam", spa=153, ability=solar, stages={"spAttack": -2}), defender("Snorlax", spd=107, ability=drought), "Psychic", weather="sun", crit=True),
        scenario("group-d-solar-power-utility-umbrella-control", ["ability:solar-power", "attack-stat", "item:utility-umbrella", "negative-control", "weather:sun"],
                 attacker("Alakazam", spa=153, ability=solar, item=umbrella), defender("Snorlax", spd=107), "Psychic", weather="sun", surface="engine-only"),
        scenario("group-d-solar-power-cloud-nine-weather-suppressed", ["ability:solar-power", "ability:cloud-nine", "weather:sun", "negative-control"],
                 attacker("Alakazam", spa=153, ability=solar), defender("Snorlax", spd=107, ability=("ABILITY_CLOUD_NINE", "Cloud Nine")), "Psychic", weather="sun", surface="engine-only"),
    ))

    out.extend((
        scenario("group-d-defeatist-physical-even-half", ["ability:defeatist", "attack-stat", "hp:half", "rounding"],
                 attacker("Machamp", atk=153, maxhp=20, hp=10, ability=defeatist), defender("Snorlax", dfn=107), "Strength"),
        scenario("group-d-defeatist-special-odd-half", ["ability:defeatist", "attack-stat", "hp:half", "rounding"],
                 attacker("Alakazam", spa=153, maxhp=15, hp=7, ability=defeatist), defender("Snorlax", spd=107), "Psychic"),
        scenario("group-d-defeatist-odd-above-half-control", ["ability:defeatist", "attack-stat", "negative-control"],
                 attacker("Alakazam", spa=153, maxhp=15, hp=8, ability=defeatist), defender("Snorlax", spd=107), "Psychic"),
        scenario("group-d-defeatist-physical-crit-negative-stage", ["ability:defeatist", "attack-stat", "crit", "stages"],
                 attacker("Machamp", atk=153, maxhp=20, hp=10, ability=defeatist, stages={"attack": -2}),
                 defender("Snorlax", dfn=107, stages={"defense": 2}), "Strength", crit=True),
        scenario("group-d-defeatist-special-stage-composition", ["ability:defeatist", "attack-stat", "stages", "modifier-stacking"],
                 attacker("Alakazam", spa=153, maxhp=20, hp=9, ability=defeatist, stages={"spAttack": 1}),
                 defender("Snorlax", spd=107), "Psychic"),
        scenario("group-d-defeatist-one-hp", ["ability:defeatist", "attack-stat", "hp:low"],
                 attacker("Machamp", atk=153, maxhp=20, hp=1, ability=defeatist), defender("Snorlax", dfn=107), "Strength"),
        scenario("group-d-defeatist-full-hp-control", ["ability:defeatist", "attack-stat", "negative-control", "hp:full"],
                 attacker("Machamp", atk=153, maxhp=20, hp=20, ability=defeatist), defender("Snorlax", dfn=107), "Strength"),
    ))

    out.extend((
        scenario("group-d-fur-coat-physical-defense-stage", ["ability:fur-coat", "defense-stat", "rounding"],
                 attacker("Machamp", atk=153), defender("Snorlax", dfn=107, ability=fur_coat), "Strength"),
        scenario("group-d-fur-coat-special-spdef-control", ["ability:fur-coat", "defense-stat", "negative-control"],
                 attacker("Alakazam", spa=153), defender("Snorlax", spd=107, ability=fur_coat), "Psychic"),
        scenario("group-d-fur-coat-type-based-physical", ["ability:fur-coat", "defense-stat", "option-style", "category-shift"],
                 attacker("Machamp", atk=153), defender("Snorlax", dfn=107, ability=fur_coat), "Rock Slide", style="typeBased"),
        scenario("group-d-fur-coat-physical-crit-positive-stage", ["ability:fur-coat", "defense-stat", "crit", "stages"],
                 attacker("Machamp", atk=153), defender("Snorlax", dfn=107, ability=fur_coat, stages={"defense": 2}), "Strength", crit=True),
        scenario("group-d-fur-coat-defense-stage-composition", ["ability:fur-coat", "defense-stat", "stages", "modifier-stacking"],
                 attacker("Machamp", atk=153), defender("Snorlax", dfn=107, ability=fur_coat, stages={"defense": -1}), "Strength"),
        scenario("group-d-fur-coat-ability-shield", ["ability:fur-coat", "defense-stat", "ability-shield"],
                 attacker("Machamp", atk=153, ability=mold_breaker), defender("Snorlax", dfn=107, ability=fur_coat, item=ability_shield), "Strength"),
        scenario("group-d-fur-coat-mold-breaker-engine-control", ["ability:fur-coat", "ability:mold-breaker", "mold-breaker", "defense-stat"],
                 attacker("Machamp", atk=153, ability=mold_breaker), defender("Snorlax", dfn=107, ability=fur_coat), "Strength", surface="engine-only"),
        scenario("group-d-fur-coat-literal-bypass", ["ability:fur-coat", "move-ability-bypass", "defense-stat"],
                 attacker("Machamp", atk=153), defender("Snorlax", dfn=107, ability=fur_coat), "Sunsteel Strike"),
    ))
    return out


def _field_backed_stat_abilities() -> list[dict]:
    """Request-local Grassy Terrain / Electric Terrain stat-stage ability slice."""
    grass_pelt = ("ABILITY_GRASS_PELT", "Grass Pelt")
    hadron = ("ABILITY_HADRON_ENGINE", "Hadron Engine")
    neutral = ("ABILITY_INSOMNIA", "Insomnia")
    out = [
        scenario("field-grass-pelt-grassy-physical", ["ability:grass-pelt", "field:grassy", "defense-stat"],
                 attacker("Machamp", atk=151, ability=neutral),
                 defender("Dragonite", dfn=109, ability=grass_pelt), "Strength", terrain="grassy"),
        scenario("field-grass-pelt-no-terrain", ["ability:grass-pelt", "field:grassy", "negative-control"],
                 attacker("Machamp", atk=151, ability=neutral),
                 defender("Dragonite", dfn=109, ability=grass_pelt), "Strength"),
        scenario("field-grass-pelt-special-control", ["ability:grass-pelt", "field:grassy", "negative-control"],
                 attacker("Alakazam", spa=151, ability=neutral),
                 defender("Dragonite", spd=109, ability=grass_pelt), "Psychic", terrain="grassy"),
        scenario("field-grass-pelt-attacker-control", ["ability:grass-pelt", "field:grassy", "negative-control"],
                 attacker("Machamp", atk=151, ability=grass_pelt),
                 defender("Dragonite", dfn=109, ability=neutral), "Strength", terrain="grassy"),
        scenario("field-grass-pelt-defense-stage-composition",
                 ["ability:grass-pelt", "field:grassy", "defense-stat", "modifier-stacking", "stages"],
                 attacker("Machamp", atk=151, ability=neutral),
                 defender("Dragonite", dfn=109, ability=grass_pelt, stages={"defense": 1}),
                 "Strength", terrain="grassy"),
        scenario("field-hadron-electric-special", ["ability:hadron-engine", "field:electric", "attack-stat"],
                 attacker("Alakazam", spa=153, ability=hadron),
                 defender("Snorlax", spd=109, ability=neutral), "Psychic", terrain="electric"),
        scenario("field-hadron-terrain-replaced", ["ability:hadron-engine", "field:electric", "negative-control"],
                 attacker("Alakazam", spa=153, ability=hadron),
                 defender("Dragonite", spd=109, ability=neutral), "Psychic", terrain="grassy"),
        scenario("field-hadron-physical-control", ["ability:hadron-engine", "field:electric", "negative-control"],
                 attacker("Machamp", atk=153, ability=hadron),
                 defender("Snorlax", dfn=109, ability=neutral), "Strength", terrain="electric"),
        scenario("field-hadron-defender-control", ["ability:hadron-engine", "field:electric", "negative-control"],
                 attacker("Alakazam", spa=153, ability=neutral),
                 defender("Snorlax", spd=109, ability=hadron), "Psychic", terrain="electric"),
        scenario("field-hadron-special-badge-composition",
                 ["ability:hadron-engine", "attack-stat", "badge", "field:electric", "modifier-stacking"],
                 attacker("Alakazam", spa=153, ability=hadron),
                 defender("Snorlax", spd=109, ability=neutral), "Psychic", terrain="electric", badges=(1,)),
    ]
    return out


def _terrain_move_modifiers() -> list[dict]:
    """Ordinary move type factors, grounding controls, and modifier compositions."""
    neutral = ("ABILITY_INSOMNIA", "Insomnia")
    levitate = ("ABILITY_LEVITATE", "Levitate")
    balloon = ("ITEM_AIR_BALLOON", "Air Balloon")
    iron_ball = ("ITEM_IRON_BALL", "Iron Ball")
    technician = ("ABILITY_TECHNICIAN", "Technician")
    return [
        scenario("terrain-grassy-grass-attacker", ["terrain:grassy", "move-modifier", "attacker-grounded"],
                 attacker("Tangela", atk=153), defender("Dragonite", dfn=109), "Vine Whip", terrain="grassy"),
        scenario("terrain-grassy-no-terrain-control", ["terrain:grassy", "move-modifier", "negative-control"],
                 attacker("Tangela", atk=153), defender("Dragonite", dfn=109), "Vine Whip"),
        scenario("terrain-grassy-grass-pelt-composition", ["terrain:grassy", "ability:grass-pelt", "modifier-stacking"],
                 attacker("Tangela", atk=153), defender("Dragonite", dfn=109, ability=("ABILITY_GRASS_PELT", "Grass Pelt")),
                 "Vine Whip", terrain="grassy"),
        scenario("terrain-electric-electric-attacker", ["terrain:electric", "move-modifier", "attacker-grounded"],
                 attacker("Electabuzz", spa=153), defender("Snorlax", spd=109), "Thunder Shock", terrain="electric"),
        scenario("terrain-electric-no-terrain-control", ["terrain:electric", "move-modifier", "negative-control"],
                 attacker("Electabuzz", spa=153), defender("Snorlax", spd=109), "Thunder Shock"),
        scenario("terrain-electric-levitate-control", ["terrain:electric", "grounding", "negative-control"],
                 attacker("Electabuzz", spa=153, ability=levitate), defender("Snorlax", spd=109), "Thunder Shock", terrain="electric"),
        scenario("terrain-electric-hadron-composition", ["terrain:electric", "ability:hadron-engine", "modifier-stacking"],
                 attacker("Alakazam", spa=153, ability=("ABILITY_HADRON_ENGINE", "Hadron Engine")),
                 defender("Snorlax", spd=109), "Thunder Shock", terrain="electric"),
        scenario("terrain-misty-dragon-defender", ["terrain:misty", "move-modifier", "defender-grounded"],
                 attacker("Dragonite", spa=153), defender("Snorlax", spd=109), "Dragon Breath", terrain="misty"),
        scenario("terrain-misty-no-terrain-control", ["terrain:misty", "move-modifier", "negative-control"],
                 attacker("Dragonite", spa=153), defender("Snorlax", spd=109), "Dragon Breath"),
        scenario("terrain-misty-flying-defender-control", ["terrain:misty", "grounding", "negative-control"],
                 attacker("Dragonite", spa=153), defender("Dragonite", spd=109), "Dragon Breath", terrain="misty"),
        scenario("terrain-psychic-psychic-attacker", ["terrain:psychic", "move-modifier", "attacker-grounded"],
                 attacker("Alakazam", spa=153), defender("Snorlax", spd=109), "Confusion", terrain="psychic"),
        scenario("terrain-psychic-no-terrain-control", ["terrain:psychic", "move-modifier", "negative-control"],
                 attacker("Alakazam", spa=153), defender("Snorlax", spd=109), "Confusion"),
        scenario("terrain-psychic-air-balloon-control", ["terrain:psychic", "grounding", "negative-control"],
                 attacker("Alakazam", spa=153, item=balloon), defender("Snorlax", spd=109), "Confusion", terrain="psychic"),
        scenario("terrain-grassy-iron-ball-overrides-flying", ["terrain:grassy", "grounding", "iron-ball"],
                 attacker("Charizard", atk=153, item=iron_ball), defender("Dragonite", dfn=109), "Vine Whip", terrain="grassy"),
        scenario("terrain-electric-gravity-overrides-levitate", ["terrain:electric", "grounding", "gravity"],
                 attacker("Electabuzz", spa=153, ability=levitate), defender("Snorlax", spd=109), "Thunder Shock",
                 terrain="electric", gravity=True),
        scenario("terrain-psychic-gravity-overrides-balloon", ["terrain:psychic", "grounding", "gravity"],
                 attacker("Alakazam", spa=153, item=balloon), defender("Snorlax", spd=109), "Confusion",
                 terrain="psychic", gravity=True),
        scenario("terrain-electric-technician-composition", ["terrain:electric", "ability:technician", "modifier-stacking"],
                 attacker("Electabuzz", spa=153, ability=technician), defender("Snorlax", spd=109), "Thunder Shock", terrain="electric"),
    ]


def _mixed_rounding_attack_stat_abilities() -> list[dict]:
    """Group D final-type Attack-stat branches with pinned half-up composition (issue #91)."""
    transistor = ("ABILITY_TRANSISTOR", "Transistor")
    dragons_maw = ("ABILITY_DRAGONS_MAW", "Dragon's Maw")
    rocky_payload = ("ABILITY_ROCKY_PAYLOAD", "Rocky Payload")
    orichalcum = ("ABILITY_ORICHALCUM_PULSE", "Orichalcum Pulse")
    umbrella = ("ITEM_UTILITY_UMBRELLA", "Utility Umbrella")
    cloud_nine = ("ABILITY_CLOUD_NINE", "Cloud Nine")
    sun_drought = ("ABILITY_DROUGHT", "Drought")
    badge = (1,)
    return [
        scenario("group-d-transistor-electric-physical", ["ability:transistor", "attack-stat", "effective-type"],
                 attacker("Electabuzz", atk=153, ability=transistor), defender("Snorlax", dfn=107), "Spark"),
        scenario("group-d-transistor-electric-special", ["ability:transistor", "attack-stat", "effective-type", "special"],
                 attacker("Electabuzz", spa=153, ability=transistor), defender("Snorlax", spd=107), "Thunderbolt"),
        scenario("group-d-transistor-non-electric-control", ["ability:transistor", "attack-stat", "negative-control"],
                 attacker("Electabuzz", atk=153, ability=transistor), defender("Snorlax", dfn=107), "Fire Punch"),
        scenario("group-d-transistor-badge-composition", ["ability:transistor", "attack-stat", "badge", "modifier-stacking", "rounding"],
                 attacker("Electabuzz", atk=153, ability=transistor), defender("Snorlax", dfn=107), "Spark", badges=badge),
        scenario("group-d-transistor-crit-negative-stage", ["ability:transistor", "attack-stat", "crit", "stages"],
                 attacker("Electabuzz", atk=153, ability=transistor, stages={"attack": -2}),
                 defender("Snorlax", dfn=107), "Spark", crit=True),
        scenario("group-d-dragons-maw-dragon-physical", ["ability:dragons-maw", "attack-stat", "effective-type"],
                 attacker("Dragonite", atk=153, ability=dragons_maw), defender("Snorlax", dfn=107), "Dragon Claw"),
        scenario("group-d-dragons-maw-dragon-special", ["ability:dragons-maw", "attack-stat", "effective-type", "special"],
                 attacker("Dragonite", spa=153, ability=dragons_maw), defender("Snorlax", spd=107), "Dragon Pulse"),
        scenario("group-d-dragons-maw-non-dragon-control", ["ability:dragons-maw", "attack-stat", "negative-control"],
                 attacker("Dragonite", atk=153, ability=dragons_maw), defender("Snorlax", dfn=107), "Strength"),
        scenario("group-d-dragons-maw-stage-badge", ["ability:dragons-maw", "attack-stat", "badge", "stages", "modifier-stacking"],
                 attacker("Dragonite", atk=153, ability=dragons_maw, stages={"attack": 1}),
                 defender("Snorlax", dfn=107), "Dragon Claw", badges=badge),
        scenario("group-d-rocky-payload-rock-physical", ["ability:rocky-payload", "attack-stat", "effective-type"],
                 attacker("Golem", atk=153, ability=rocky_payload), defender("Snorlax", dfn=107), "Rock Slide"),
        scenario("group-d-rocky-payload-non-rock-control", ["ability:rocky-payload", "attack-stat", "negative-control"],
                 attacker("Golem", atk=153, ability=rocky_payload), defender("Snorlax", dfn=107), "Strength"),
        scenario("group-d-rocky-payload-stage", ["ability:rocky-payload", "attack-stat", "stages", "modifier-stacking"],
                 attacker("Golem", atk=153, ability=rocky_payload, stages={"attack": -1}),
                 defender("Snorlax", dfn=107), "Rock Slide"),
        scenario("group-d-orichalcum-pulse-physical-sun", ["ability:orichalcum-pulse", "attack-stat", "weather:sun"],
                 attacker("Machamp", atk=153, ability=orichalcum), defender("Snorlax", dfn=107, ability=sun_drought),
                 "Strength", weather="sun"),
        scenario("group-d-orichalcum-pulse-physical-sun-badge", ["ability:orichalcum-pulse", "attack-stat", "weather:sun", "badge", "modifier-stacking"],
                 attacker("Machamp", atk=153, ability=orichalcum), defender("Snorlax", dfn=107, ability=sun_drought),
                 "Strength", weather="sun", badges=badge),
        scenario("group-d-orichalcum-pulse-no-sun-control", ["ability:orichalcum-pulse", "attack-stat", "weather:rain", "negative-control"],
                 attacker("Machamp", atk=153, ability=orichalcum), defender("Snorlax", dfn=107),
                 "Strength", weather="rain"),
        scenario("group-d-orichalcum-pulse-special-sun-control", ["ability:orichalcum-pulse", "attack-stat", "weather:sun", "negative-control"],
                 attacker("Alakazam", spa=153, ability=orichalcum), defender("Snorlax", spd=107), "Swift", weather="sun"),
        scenario("group-d-orichalcum-pulse-utility-umbrella", ["ability:orichalcum-pulse", "attack-stat", "item:utility-umbrella"],
                 attacker("Machamp", atk=153, ability=orichalcum, item=umbrella), defender("Snorlax", dfn=107),
                 "Strength", weather="sun"),
        scenario("group-d-orichalcum-pulse-cloud-nine-raw-sun", ["ability:orichalcum-pulse", "ability:cloud-nine", "weather:sun", "raw-weather"],
                 attacker("Machamp", atk=153, ability=orichalcum), defender("Snorlax", dfn=107, ability=cloud_nine),
                 "Strength", weather="sun", surface="engine-only"),
    ]


def _base_power_abilities() -> list[dict]:
    """Group D source-ordered base-power ability modifiers (issue #91)."""
    out = []
    technician = ("ABILITY_TECHNICIAN", "Technician")
    iron_fist = ("ABILITY_IRON_FIST", "Iron Fist")
    strong_jaw = ("ABILITY_STRONG_JAW", "Strong Jaw")
    mega_launcher = ("ABILITY_MEGA_LAUNCHER", "Mega Launcher")
    sharpness = ("ABILITY_SHARPNESS", "Sharpness")
    water_bubble = ("ABILITY_WATER_BUBBLE", "Water Bubble")
    steelworker = ("ABILITY_STEELWORKER", "Steelworker")
    toxic_boost = ("ABILITY_TOXIC_BOOST", "Toxic Boost")
    flare_boost = ("ABILITY_FLARE_BOOST", "Flare Boost")
    punk_rock = ("ABILITY_PUNK_ROCK", "Punk Rock")
    steely_spirit = ("ABILITY_STEELY_SPIRIT", "Steely Spirit")
    normalize = ("ABILITY_NORMALIZE", "Normalize")
    liquid_voice = ("ABILITY_LIQUID_VOICE", "Liquid Voice")

    for move in ("Ember", "Swift", "Sludge"):
        out.append(scenario(
            f"group-d-technician-{slug(move)}", ["ability:technician", "base-power"],
            attacker("Porygon", spa=151, ability=technician), defender("Snorlax", spd=109), move))
    out.append(scenario(
        "group-d-technician-wise-glasses-dry-skin", ["ability:technician", "base-power", "modifier-stacking"],
        attacker("Porygon", spa=151, ability=technician, item=("ITEM_WISE_GLASSES", "Wise Glasses")),
        defender("Snorlax", spd=109, ability=("ABILITY_DRY_SKIN", "Dry Skin")), "Ember"))
    out.append(scenario(
        "group-d-technician-defender-control", ["ability:technician", "base-power", "negative-control"],
        attacker("Porygon", spa=151), defender("Snorlax", spd=109, ability=technician), "Ember"))

    for ability, name, move, flag in (
        (iron_fist, "iron-fist", "Fire Punch", "punching"),
        (strong_jaw, "strong-jaw", "Bite", "biting"),
        (mega_launcher, "mega-launcher", "Aura Sphere", "pulse"),
        (sharpness, "sharpness", "Leaf Blade", "slicing"),
    ):
        out.append(scenario(
            f"group-d-{name}-{slug(move)}", [f"ability:{name}", "base-power", f"move-flag:{flag}"],
            attacker("Machamp", atk=151, spa=151, ability=ability), defender("Snorlax", dfn=109, spd=109), move))
        out.append(scenario(
            f"group-d-{name}-nonmatching-control", [f"ability:{name}", "base-power", "negative-control"],
            attacker("Machamp", atk=151, spa=151, ability=ability), defender("Snorlax", dfn=109, spd=109), "Tackle"))
        out.append(scenario(
            f"group-d-{name}-defender-control", [f"ability:{name}", "base-power", "negative-control"],
            attacker("Machamp", atk=151), defender("Snorlax", dfn=109, ability=ability), move))

    out.extend((
        scenario("group-d-water-bubble-attacker-waterfall", ["ability:water-bubble", "base-power"],
                 attacker("Blastoise", atk=151, spa=151, ability=water_bubble), defender("Snorlax", dfn=109, spd=109), "Waterfall"),
        scenario("group-d-water-bubble-attacker-nonwater-control", ["ability:water-bubble", "base-power", "negative-control"],
                 attacker("Blastoise", atk=151, ability=water_bubble), defender("Snorlax", dfn=109), "Fire Punch"),
        scenario("group-d-water-bubble-defender-fire-deferred", ["ability:water-bubble", "base-power", "target-ability-slot"],
                 attacker("Machamp", atk=151), defender("Blastoise", dfn=109, ability=water_bubble), "Fire Punch"),
        scenario("group-d-water-bubble-defender-fire-blast", ["ability:water-bubble", "base-power", "special"],
                 attacker("Machamp", spa=151), defender("Snorlax", spd=109, ability=water_bubble), "Fire Blast"),
        scenario("group-d-water-bubble-defender-nonfire-control", ["ability:water-bubble", "base-power", "negative-control"],
                 attacker("Machamp", atk=151), defender("Snorlax", dfn=109, ability=water_bubble), "Tackle"),
        scenario("group-d-water-bubble-defender-iron-fist-composition",
                 ["ability:water-bubble", "ability:iron-fist", "base-power", "modifier-stacking"],
                 attacker("Machamp", atk=151, ability=iron_fist),
                 defender("Snorlax", dfn=109, ability=water_bubble), "Fire Punch"),
        scenario("group-d-water-bubble-defender-wise-glasses-rounding",
                 ["ability:water-bubble", "base-power", "item:wise-glasses", "modifier-stacking", "rounding"],
                 attacker("Porygon", spa=151, item=("ITEM_WISE_GLASSES", "Wise Glasses")),
                 defender("Snorlax", spd=109, ability=water_bubble), "Fire Blast"),
        scenario("group-d-water-bubble-defender-ability-shield-mold-breaker",
                 ["ability:water-bubble", "base-power", "mold-breaker", "ability-shield"],
                 attacker("Machamp", atk=151, ability=("ABILITY_MOLD_BREAKER", "Mold Breaker")),
                 defender("Snorlax", dfn=109, ability=water_bubble,
                          item=("ITEM_ABILITY_SHIELD", "Ability Shield")), "Fire Punch"),
        scenario("group-d-water-bubble-defender-mold-breaker-unshielded",
                 ["ability:water-bubble", "base-power", "mold-breaker", "engine-only"],
                 attacker("Machamp", atk=151, ability=("ABILITY_MOLD_BREAKER", "Mold Breaker")),
                 defender("Snorlax", dfn=109, ability=water_bubble), "Fire Punch", surface="engine-only"),
        scenario("group-d-heatproof-defender-fire-punch",
                 ["ability:heatproof", "base-power", "physical"],
                 attacker("Machamp", atk=151), defender("Snorlax", dfn=109,
                     ability=("ABILITY_HEATPROOF", "Heatproof")), "Fire Punch"),
        scenario("group-d-heatproof-defender-fire-blast",
                 ["ability:heatproof", "base-power", "special"],
                 attacker("Machamp", spa=151), defender("Snorlax", spd=109,
                     ability=("ABILITY_HEATPROOF", "Heatproof")), "Fire Blast"),
        scenario("group-d-heatproof-defender-nonfire-control",
                 ["ability:heatproof", "base-power", "negative-control"],
                 attacker("Machamp", atk=151), defender("Snorlax", dfn=109,
                     ability=("ABILITY_HEATPROOF", "Heatproof")), "Tackle"),
        scenario("group-d-heatproof-attacker-fire-control",
                 ["ability:heatproof", "base-power", "attacker-control"],
                 attacker("Machamp", atk=151, ability=("ABILITY_HEATPROOF", "Heatproof")),
                 defender("Snorlax", dfn=109), "Fire Punch"),
        scenario("group-d-heatproof-defender-ability-shield-mold-breaker",
                 ["ability:heatproof", "base-power", "mold-breaker", "ability-shield"],
                 attacker("Machamp", atk=151, ability=("ABILITY_MOLD_BREAKER", "Mold Breaker")),
                 defender("Snorlax", dfn=109, ability=("ABILITY_HEATPROOF", "Heatproof"),
                          item=("ITEM_ABILITY_SHIELD", "Ability Shield")), "Fire Punch"),
        scenario("group-d-heatproof-defender-mold-breaker-unshielded",
                 ["ability:heatproof", "base-power", "mold-breaker", "engine-only"],
                 attacker("Machamp", atk=151, ability=("ABILITY_MOLD_BREAKER", "Mold Breaker")),
                 defender("Snorlax", dfn=109, ability=("ABILITY_HEATPROOF", "Heatproof")),
                 "Fire Punch", surface="engine-only"),
        scenario("group-d-dry-skin-defender-fire-control",
                 ["ability:dry-skin", "base-power", "target-ability-slot", "regression-control"],
                 attacker("Machamp", atk=151), defender("Snorlax", dfn=109,
                     ability=("ABILITY_DRY_SKIN", "Dry Skin")), "Fire Punch"),
        scenario("group-d-steelworker-iron-head", ["ability:steelworker", "base-power"],
                 attacker("Machamp", atk=151, ability=steelworker), defender("Snorlax", dfn=109), "Iron Head"),
        scenario("group-d-steelworker-nonsteel-control", ["ability:steelworker", "base-power", "negative-control"],
                 attacker("Machamp", atk=151, ability=steelworker), defender("Snorlax", dfn=109), "Tackle"),
        scenario("group-d-steelworker-defender-control", ["ability:steelworker", "base-power", "negative-control"],
                 attacker("Machamp", atk=151), defender("Snorlax", dfn=109, ability=steelworker), "Iron Head"),
        scenario("group-d-punk-rock-attacker-hyper-voice", ["ability:punk-rock", "base-power", "sound"],
                 attacker("Machamp", spa=151, ability=punk_rock), defender("Snorlax", spd=109), "Hyper Voice"),
        scenario("group-d-punk-rock-attacker-nonsound-control",
                 ["ability:punk-rock", "base-power", "sound", "negative-control"],
                 attacker("Machamp", atk=151, ability=punk_rock), defender("Snorlax", dfn=109), "Tackle"),
        scenario("group-d-punk-rock-attacker-wise-glasses-relic-song",
                 ["ability:punk-rock", "base-power", "sound", "modifier-stacking", "item:wise-glasses", "rounding"],
                 attacker("Machamp", spa=151, ability=punk_rock, item=("ITEM_WISE_GLASSES", "Wise Glasses")),
                 defender("Snorlax", spd=109), "Relic Song"),
        scenario("group-d-punk-rock-defender-hyper-voice",
                 ["ability:punk-rock", "final-damage", "sound"],
                 attacker("Machamp", spa=151), defender("Snorlax", spd=109, ability=punk_rock), "Hyper Voice"),
        scenario("group-d-punk-rock-defender-nonsound-control",
                 ["ability:punk-rock", "final-damage", "sound", "negative-control"],
                 attacker("Machamp", atk=151), defender("Snorlax", dfn=109, ability=punk_rock), "Tackle"),
        scenario("group-d-punk-rock-defender-light-screen",
                 ["ability:punk-rock", "final-damage", "sound", "screen", "modifier-stacking"],
                 attacker("Porygon", spa=151), defender("Snorlax", spd=109, ability=punk_rock), "Hyper Voice",
                 light_screen=True),
        scenario("group-d-punk-rock-defender-low-damage-rounding",
                 ["ability:punk-rock", "final-damage", "sound", "rounding", "minimum-damage"],
                 attacker("Porygon", level=1, spa=1),
                 defender("Snorlax", spd=60000, maxhp=60000, ability=punk_rock), "Relic Song"),
        scenario("group-d-punk-rock-soundproof-immunity",
                 ["ability:punk-rock", "group-c-immunity", "sound", "ability:soundproof"],
                 attacker("Machamp", spa=151, ability=punk_rock),
                 defender("Snorlax", spd=109, ability=("ABILITY_SOUNDPROOF", "Soundproof")), "Hyper Voice",
                 expect="immune"),
        scenario("group-d-liquid-voice-punk-rock-defender",
                 ["ability:liquid-voice", "ability:punk-rock", "final-damage", "sound", "move-type-rewrite"],
                 attacker("Porygon", spa=151, ability=liquid_voice),
                 defender("Snorlax", spd=109, ability=punk_rock), "Hyper Voice"),
        scenario("group-d-liquid-voice-soundproof-immunity",
                 ["ability:liquid-voice", "group-c-immunity", "sound", "move-type-rewrite", "ability:soundproof"],
                 attacker("Porygon", spa=151, ability=liquid_voice),
                 defender("Snorlax", spd=109, ability=("ABILITY_SOUNDPROOF", "Soundproof")), "Hyper Voice",
                 expect="immune"),
        scenario("group-d-steely-spirit-iron-head",
                 ["ability:steely-spirit", "base-power", "effective-type"],
                 attacker("Machamp", atk=151, ability=steely_spirit), defender("Snorlax", dfn=109), "Iron Head"),
        scenario("group-d-steely-spirit-nonsteel-control",
                 ["ability:steely-spirit", "base-power", "effective-type", "negative-control"],
                 attacker("Machamp", atk=151, ability=steely_spirit), defender("Snorlax", dfn=109), "Tackle"),
        scenario("group-d-steely-spirit-defender-control",
                 ["ability:steely-spirit", "base-power", "effective-type", "negative-control", "role:defender"],
                 attacker("Machamp", atk=151),
                 defender("Snorlax", dfn=109, ability=steely_spirit), "Iron Head"),
        scenario("group-d-normalize-steel-source-final-normal",
                 ["ability:normalize", "move-type-rewrite", "effective-type", "ate-boost", "cross-product"],
                 attacker("Machamp", atk=151, ability=normalize), defender("Snorlax", dfn=109), "Iron Head"),
    ))

    for status in ("poison", "toxic", "none"):
        out.append(scenario(
            f"group-d-toxic-boost-physical-{status}", ["ability:toxic-boost", "base-power", f"status:{status}"],
            attacker("Machamp", atk=151, maxhp=300, status=status, ability=toxic_boost),
            defender("Snorlax", dfn=109), "Strength"))
    out.extend((
        scenario("group-d-toxic-boost-special-poison-control", ["ability:toxic-boost", "base-power", "negative-control", "status:poison"],
                 attacker("Machamp", spa=151, maxhp=300, status="poison", ability=toxic_boost),
                 defender("Snorlax", spd=109), "Psychic"),
        scenario("group-d-toxic-boost-defender-control", ["ability:toxic-boost", "base-power", "negative-control"],
                 attacker("Machamp", atk=151), defender("Snorlax", dfn=109, ability=toxic_boost), "Strength"),
    ))
    out.extend((
        scenario("group-d-flare-boost-special-burn", ["ability:flare-boost", "base-power", "status:burn"],
                 attacker("Machamp", spa=151, maxhp=300, status="burn", ability=flare_boost),
                 defender("Snorlax", spd=109), "Psychic"),
        scenario("group-d-flare-boost-special-no-burn-control", ["ability:flare-boost", "base-power", "negative-control"],
                 attacker("Machamp", spa=151, maxhp=300, ability=flare_boost),
                 defender("Snorlax", spd=109), "Psychic"),
        scenario("group-d-flare-boost-physical-burn-control", ["ability:flare-boost", "base-power", "negative-control", "status:burn"],
                 attacker("Machamp", atk=151, maxhp=300, status="burn", ability=flare_boost),
                 defender("Snorlax", dfn=109), "Strength"),
        scenario("group-d-flare-boost-defender-control", ["ability:flare-boost", "base-power", "negative-control"],
                 attacker("Machamp", spa=151), defender("Snorlax", spd=109, ability=flare_boost), "Psychic"),
    ))

    # Issue #91: the move-type rewrite and the source-set ateBoost bit form one indivisible
    # mechanic. The pinned oracle records effective type/category/ateBoost after the selected hit;
    # the production adapter feeds those observations into the same QuickJS request fields.
    normalize = ("ABILITY_NORMALIZE", "Normalize")
    refrigerate = ("ABILITY_REFRIGERATE", "Refrigerate")
    pixilate = ("ABILITY_PIXILATE", "Pixilate")
    aerilate = ("ABILITY_AERILATE", "Aerilate")
    galvanize = ("ABILITY_GALVANIZE", "Galvanize")
    liquid_voice = ("ABILITY_LIQUID_VOICE", "Liquid Voice")
    for ability, label, target, defender_label in (
        (refrigerate, "refrigerate", "Ice", "Dragonite"),
        (pixilate, "pixilate", "Fairy", "Dragonite"),
        (aerilate, "aerilate", "Flying", "Heracross"),
        (galvanize, "galvanize", "Electric", "Vaporeon"),
    ):
        out.append(scenario(
            f"group-d-{label}-tackle-positive", [f"ability:{label}", "move-type-rewrite", "ate-boost", "stab", "type-chart"],
            attacker("Porygon", atk=151, spa=151, ability=ability), defender(defender_label, dfn=109, spd=109), "Tackle"))
        out.append(scenario(
            f"group-d-{label}-fire-punch-control", [f"ability:{label}", "move-type-rewrite", "negative-control"],
            attacker("Porygon", atk=151, spa=151, ability=ability), defender("Snorlax", dfn=109, spd=109), "Fire Punch"))

    out.extend((
        scenario("group-d-normalize-tackle-same-type-ate-boost",
                 ["ability:normalize", "move-type-rewrite", "ate-boost", "same-type-target"],
                 attacker("Porygon", atk=151, ability=normalize), defender("Snorlax", dfn=109), "Tackle"),
        scenario("group-d-normalize-fire-punch-dry-skin-control",
                 ["ability:normalize", "move-type-rewrite", "type-sensitive-defender-ability"],
                 attacker("Machamp", atk=151, ability=normalize),
                 defender("Snorlax", dfn=109, ability=("ABILITY_DRY_SKIN", "Dry Skin")), "Fire Punch"),
        scenario("group-d-normalize-hyper-voice-wise-glasses",
                 ["ability:normalize", "move-type-rewrite", "ate-boost", "modifier-stacking", "item:wise-glasses"],
                 attacker("Porygon", spa=151, ability=normalize,
                          item=("ITEM_WISE_GLASSES", "Wise Glasses")),
                 defender("Snorlax", spd=109), "Hyper Voice"),
        scenario("group-d-pixilate-type-based-category",
                 ["ability:pixilate", "move-type-rewrite", "ate-boost", "option-style", "category-shift"],
                 attacker("Porygon", atk=151, spa=151, ability=pixilate), defender("Snorlax", dfn=109, spd=109),
                 "Tackle", style="typeBased"),
        scenario("group-d-pixilate-fairy-toggle-off",
                 ["ability:pixilate", "move-type-rewrite", "ate-boost", "fairy", "fairy:off"],
                 attacker("Porygon", atk=151, ability=pixilate), defender("Snorlax", dfn=109), "Tackle", fairy=False),
        scenario("group-d-pixilate-fairy-wind-fairy-on",
                 ["ability:pixilate", "move-type-rewrite", "fairy", "fairy:on", "negative-control"],
                 attacker("Porygon", spa=151, ability=pixilate), defender("Snorlax", spd=109), "Fairy Wind"),
        scenario("group-d-pixilate-fairy-wind-fairy-off",
                 ["ability:pixilate", "move-type-rewrite", "ate-boost", "fairy", "fairy:off"],
                 attacker("Porygon", spa=151, ability=pixilate), defender("Snorlax", spd=109), "Fairy Wind",
                 fairy=False),
        scenario("group-d-pixilate-gains-fairy-stab",
                 ["ability:pixilate", "move-type-rewrite", "ate-boost", "stab"],
                 attacker("Gardevoir", atk=151, ability=pixilate), defender("Snorlax", dfn=109), "Tackle"),
        scenario("group-d-pixilate-loses-normal-stab",
                 ["ability:pixilate", "move-type-rewrite", "ate-boost", "stab", "negative-control"],
                 attacker("Porygon", atk=151, ability=pixilate), defender("Snorlax", dfn=109), "Tackle"),
        scenario("group-d-pixilate-super-effective-wonder-guard",
                 ["ability:pixilate", "move-type-rewrite", "ate-boost", "group-c-immunity", "ability:wonder-guard"],
                 attacker("Porygon", atk=151, ability=pixilate),
                 defender("Dragonite", dfn=109, ability=("ABILITY_WONDER_GUARD", "Wonder Guard")),
                 "Tackle"),
        scenario("group-d-normal-tackle-wonder-guard-immunity-control",
                 ["move-type-rewrite", "negative-control", "group-c-immunity", "ability:wonder-guard"],
                 attacker("Porygon", atk=151),
                 defender("Dragonite", dfn=109, ability=("ABILITY_WONDER_GUARD", "Wonder Guard")),
                 "Tackle", expect="immune"),
        scenario("group-d-refrigerate-removes-normal-immunity",
                 ["ability:refrigerate", "move-type-rewrite", "ate-boost", "type-immunity"],
                 attacker("Porygon", atk=151, ability=refrigerate), defender("Banette", dfn=109), "Tackle"),
        scenario("group-d-normal-tackle-ghost-immunity-control",
                 ["type-immunity", "negative-control"],
                 attacker("Porygon", atk=151), defender("Banette", dfn=109), "Tackle", expect="immune"),
        scenario("group-d-galvanize-ground-immunity",
                 ["ability:galvanize", "move-type-rewrite", "ate-boost", "type-immunity"],
                 attacker("Porygon", atk=151, ability=galvanize), defender("Dugtrio", dfn=109), "Tackle",
                 expect="immune"),
        scenario("group-d-galvanize-volt-absorb-immunity",
                 ["ability:galvanize", "move-type-rewrite", "ate-boost", "group-c-immunity", "ability:volt-absorb"],
                 attacker("Porygon", atk=151, ability=galvanize),
                 defender("Snorlax", dfn=109, ability=("ABILITY_VOLT_ABSORB", "Volt Absorb")), "Tackle",
                 expect="immune"),
        scenario("group-d-liquid-voice-hyper-voice-positive",
                 ["ability:liquid-voice", "move-type-rewrite", "sound", "group-c-immunity"],
                 attacker("Porygon", spa=151, ability=liquid_voice), defender("Snorlax", spd=109), "Hyper Voice"),
        scenario("group-d-liquid-voice-water-absorb-immunity",
                 ["ability:liquid-voice", "move-type-rewrite", "sound", "group-c-immunity", "ability:water-absorb"],
                 attacker("Porygon", spa=151, ability=liquid_voice),
                 defender("Snorlax", spd=109, ability=("ABILITY_WATER_ABSORB", "Water Absorb")), "Hyper Voice",
                 expect="immune"),
        scenario("group-d-liquid-voice-nonsound-control",
                 ["ability:liquid-voice", "move-type-rewrite", "negative-control"],
                 attacker("Porygon", atk=151, ability=liquid_voice), defender("Snorlax", dfn=109), "Tackle"),
        scenario("group-d-liquid-voice-defender-control",
                 ["ability:liquid-voice", "move-type-rewrite", "negative-control", "role:defender"],
                 attacker("Porygon", atk=151),
                 defender("Snorlax", dfn=109, ability=liquid_voice), "Tackle"),
        scenario("group-d-ate-rounding-wise-glasses",
                 ["ability:normalize", "ate-boost", "modifier-stacking", "item:wise-glasses", "rounding"],
                 attacker("Porygon", spa=151, ability=normalize,
                          item=("ITEM_WISE_GLASSES", "Wise Glasses")),
                 defender("Snorlax", spd=109), "Hyper Voice"),
    ))
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
    return out


def _final_modifiers_and_stab() -> list[dict]:
    """Low-state final slots and Adaptability's pinned STAB-stage branch."""
    out = []
    tinted = ("ABILITY_TINTED_LENS", "Tinted Lens")
    neuro = ("ABILITY_NEUROFORCE", "Neuroforce")
    sniper = ("ABILITY_SNIPER", "Sniper")
    filter_ability = ("ABILITY_FILTER", "Filter")
    solid_rock = ("ABILITY_SOLID_ROCK", "Solid Rock")
    prism = ("ABILITY_PRISM_ARMOR", "Prism Armor")
    multiscale = ("ABILITY_MULTISCALE", "Multiscale")
    shadow = ("ABILITY_SHADOW_SHIELD", "Shadow Shield")
    ice_scales = ("ABILITY_ICE_SCALES", "Ice Scales")
    adaptability = ("ABILITY_ADAPTABILITY", "Adaptability")
    mold_breaker = ("ABILITY_MOLD_BREAKER", "Mold Breaker")

    out.append(scenario("final-tinted-lens-resisted-half", ["ability:tinted-lens", "final-modifier"],
                        attacker("Magmar", atk=140, ability=tinted), defender("Vaporeon", dfn=110), "Fire Punch"))
    out.append(scenario("final-tinted-lens-resisted-quarter", ["ability:tinted-lens", "final-modifier"],
                        attacker("Chikorita", atk=140, ability=tinted), defender("Dragonite", dfn=110), "Leaf Blade"))
    out.append(scenario("final-tinted-lens-neutral-control", ["ability:tinted-lens", "final-modifier", "negative-control"],
                        attacker("Magmar", atk=140, ability=tinted), defender("Snorlax", dfn=110), "Fire Punch"))
    out.append(scenario("final-tinted-lens-super-effective-control", ["ability:tinted-lens", "final-modifier", "negative-control"],
                        attacker("Magmar", atk=140, ability=tinted), defender("Tangela", dfn=110), "Fire Punch"))
    out.append(scenario("final-tinted-lens-defender-control", ["ability:tinted-lens", "final-modifier", "negative-control", "role:defender"],
                        attacker("Machamp", atk=140), defender("Vaporeon", dfn=110, ability=tinted), "Thunderbolt"))
    out.append(scenario("final-tinted-lens-immunity-control", ["ability:tinted-lens", "final-modifier", "immune", "negative-control"],
                        attacker("Dragonite", atk=140, ability=tinted), defender("Clefable", dfn=110), "Dragon Claw",
                        expect="immune"))

    out.append(scenario("final-neuroforce-super-effective", ["ability:neuroforce", "final-modifier"],
                        attacker("Machamp", atk=145, ability=neuro), defender("Snorlax", dfn=110), "Karate Chop"))
    out.append(scenario("final-neuroforce-neutral-control", ["ability:neuroforce", "final-modifier", "negative-control"],
                        attacker("Machamp", atk=145, ability=neuro), defender("Snorlax", dfn=110), "Tackle"))
    out.append(scenario("final-neuroforce-defender-control", ["ability:neuroforce", "final-modifier", "negative-control", "role:defender"],
                        attacker("Machamp", atk=145), defender("Snorlax", dfn=110, ability=neuro), "Karate Chop"))
    out.append(scenario("final-sniper-critical", ["ability:sniper", "crit", "final-modifier"],
                        attacker("Machamp", atk=145, ability=sniper), defender("Snorlax", dfn=110), "Karate Chop", crit=True))
    out.append(scenario("final-sniper-noncritical-control", ["ability:sniper", "final-modifier", "negative-control"],
                        attacker("Machamp", atk=145, ability=sniper), defender("Snorlax", dfn=110), "Karate Chop"))
    out.append(scenario("final-sniper-crit-filter-cross-product", ["ability:sniper", "ability:filter", "crit", "modifier-stacking"],
                        attacker("Machamp", atk=145, ability=sniper),
                        defender("Snorlax", dfn=110, ability=filter_ability), "Karate Chop", crit=True))

    for ab, name in ((filter_ability, "filter"), (solid_rock, "solid-rock"), (prism, "prism-armor")):
        out.append(scenario(f"final-{name}-super-effective", [f"ability:{name}", "final-modifier"],
                            attacker("Magmar", atk=145), defender("Tangela", dfn=110, ability=ab), "Fire Punch"))
        out.append(scenario(f"final-{name}-neutral-control", [f"ability:{name}", "final-modifier", "negative-control"],
                            attacker("Machamp", atk=145), defender("Snorlax", dfn=110, ability=ab), "Tackle"))
        out.append(scenario(f"final-{name}-resisted-control", [f"ability:{name}", "final-modifier", "negative-control"],
                            attacker("Magmar", atk=145), defender("Vaporeon", dfn=110, ability=ab), "Fire Punch"))
        out.append(scenario(f"final-{name}-attacker-control", [f"ability:{name}", "final-modifier", "negative-control", "role:attacker"],
                            attacker("Machamp", atk=145, ability=ab), defender("Snorlax", dfn=110), "Karate Chop"))
    out.append(scenario("final-filter-four-times", ["ability:filter", "final-modifier", "type-effectiveness:4"],
                        attacker("Lapras", atk=145), defender("Dragonite", dfn=110, ability=filter_ability), "Ice Punch"))

    # The pinned ability table marks Filter/Multiscale breakable but Prism Armor/Shadow Shield
    # unbreakable. The first two are engine-only because the production boundary deliberately
    # refuses unshielded Mold Breaker suppression; the non-breakable and Shield paths are admitted.
    out.append(scenario("final-mold-breaker-filter-suppressed", ["ability:mold-breaker", "ability:filter", "mold-breaker"],
                        attacker("Machamp", atk=145, ability=mold_breaker),
                        defender("Snorlax", dfn=110, ability=filter_ability), "Karate Chop", surface="engine-only"))
    out.append(scenario("final-mold-breaker-solid-rock-suppressed", ["ability:mold-breaker", "ability:solid-rock", "mold-breaker"],
                        attacker("Machamp", atk=145, ability=mold_breaker),
                        defender("Snorlax", dfn=110, ability=solid_rock), "Karate Chop", surface="engine-only"))
    out.append(scenario("final-mold-breaker-filter-ability-shield-preserved", ["ability:mold-breaker", "ability:filter", "ability-shield"],
                        attacker("Machamp", atk=145, ability=mold_breaker),
                        defender("Snorlax", dfn=110, ability=filter_ability,
                                 item=("ITEM_ABILITY_SHIELD", "Ability Shield")), "Karate Chop"))
    out.append(scenario("final-mold-breaker-multiscale-suppressed", ["ability:mold-breaker", "ability:multiscale", "mold-breaker"],
                        attacker("Machamp", atk=145, ability=mold_breaker),
                        defender("Snorlax", dfn=110, ability=multiscale), "Tackle", surface="engine-only"))
    out.append(scenario("final-mold-breaker-ice-scales-suppressed", ["ability:mold-breaker", "ability:ice-scales", "mold-breaker"],
                        attacker("Alakazam", spa=145, ability=mold_breaker),
                        defender("Snorlax", spd=110, ability=ice_scales), "Swift", surface="engine-only"))
    out.append(scenario("final-mold-breaker-prism-armor-preserved", ["ability:mold-breaker", "ability:prism-armor", "mold-breaker"],
                        attacker("Machamp", atk=145, ability=mold_breaker),
                        defender("Snorlax", dfn=110, ability=prism), "Karate Chop"))
    out.append(scenario("final-mold-breaker-shadow-shield-preserved", ["ability:mold-breaker", "ability:shadow-shield", "mold-breaker"],
                        attacker("Machamp", atk=145, ability=mold_breaker),
                        defender("Snorlax", dfn=110, ability=shadow), "Tackle"))

    # Literal move-level bypasses set moldBreakerActive too, so only pinned breakable target
    # abilities are suppressed. Prism Armor and full-HP Shadow Shield are controls for the two
    # unbreakable final modifiers; Filter proves the breakable defender control.
    out.append(scenario("final-move-bypass-prism-armor-preserved",
                        ["ability:prism-armor", "final-modifier", "move-ability-bypass"],
                        attacker("Machamp", atk=145),
                        defender("Sudowoodo", dfn=110, ability=prism), "Sunsteel Strike"))
    out.append(scenario("final-move-bypass-shadow-shield-preserved",
                        ["ability:shadow-shield", "final-modifier", "move-ability-bypass", "full-hp"],
                        attacker("Machamp", atk=145),
                        defender("Alakazam", spd=110, hp=60000, maxhp=60000, ability=shadow), "Moongeist Beam"))
    out.append(scenario("final-move-bypass-filter-suppressed",
                        ["ability:filter", "final-modifier", "move-ability-bypass"],
                        attacker("Machamp", atk=145),
                        defender("Sudowoodo", dfn=110, ability=filter_ability), "Sunsteel Strike"))

    # Speed swaps prove the source's attacker/defender ability slots remain in distinct orders.
    for order, atk_spe, def_spe in (("attacker-first", 80, 40), ("defender-first", 30, 90)):
        out.append(scenario(f"final-neuroforce-filter-{order}", ["ability:neuroforce", "ability:filter", "final-modifier", "speed-order"],
                            attacker("Machamp", atk=145, spe=atk_spe, ability=neuro),
                            defender("Snorlax", dfn=110, spe=def_spe, ability=filter_ability), "Karate Chop"))
    # The UQ4.12 product (1.25 x 0.75 = 0.9375) is applied once with half-down integer
    # multiplication. At this low base damage, applying the two factors separately changes a
    # 2-damage pre-final roll to 1; the pinned accumulated factor keeps it at 2.
    out.append(scenario("final-neuroforce-filter-half-down-rounding", ["ability:neuroforce", "ability:filter", "final-modifier", "rounding", "low-damage"],
                        attacker("Alakazam", level=1, atk=1, spe=80, ability=neuro),
                        defender("Snorlax", dfn=65535, spe=40, ability=filter_ability), "Karate Chop"))

    for ab, name in ((multiscale, "multiscale"), (shadow, "shadow-shield")):
        out.append(scenario(f"final-{name}-full-hp", [f"ability:{name}", "final-modifier", "full-hp"],
                            attacker("Machamp", atk=145), defender("Snorlax", dfn=110, ability=ab), "Tackle"))
        out.append(scenario(f"final-{name}-one-below-max", [f"ability:{name}", "final-modifier", "negative-control"],
                            attacker("Machamp", atk=145),
                            defender("Snorlax", dfn=110, maxhp=60000, hp=59999, ability=ab), "Tackle"))
        out.append(scenario(f"final-{name}-low-damage-floor", [f"ability:{name}", "final-modifier", "low-damage", "damage-floor"],
                            attacker("Alakazam", level=1, atk=1, spa=1),
                            defender("Snorlax", dfn=5000, spd=5000, ability=ab), "Tackle"))

    out.append(scenario("final-ice-scales-special", ["ability:ice-scales", "final-modifier", "special"],
                        attacker("Alakazam", spa=145), defender("Snorlax", spd=110, ability=ice_scales), "Swift"))
    out.append(scenario("final-ice-scales-low-damage-floor", ["ability:ice-scales", "final-modifier", "low-damage", "damage-floor"],
                        attacker("Alakazam", level=1, spa=1),
                        defender("Snorlax", spd=5000, ability=ice_scales), "Swift"))
    out.append(scenario("final-ice-scales-physical-control", ["ability:ice-scales", "final-modifier", "negative-control", "physical"],
                        attacker("Machamp", atk=145), defender("Snorlax", dfn=110, ability=ice_scales), "Tackle"))
    out.append(scenario("final-ice-scales-type-based-pixilate", ["ability:ice-scales", "ability:pixilate", "final-modifier", "type-rewrite", "type-based"],
                        attacker("Gardevoir", spa=145, ability=("ABILITY_PIXILATE", "Pixilate")),
                        defender("Snorlax", spd=110, ability=ice_scales), "Tackle", style="typeBased"))
    out.append(scenario("final-ice-scales-attacker-control", ["ability:ice-scales", "final-modifier", "negative-control", "role:attacker"],
                        attacker("Alakazam", spa=145, ability=ice_scales), defender("Snorlax", spd=110), "Swift"))

    out.append(scenario("final-adaptability-stab", ["ability:adaptability", "stab", "final-modifier"],
                        attacker("Hariyama", atk=145, ability=adaptability), defender("Snorlax", dfn=110), "Karate Chop"))
    out.append(scenario("final-adaptability-no-stab-control", ["ability:adaptability", "negative-control", "stab"],
                        attacker("Hariyama", atk=145, ability=adaptability), defender("Snorlax", dfn=110), "Tackle"))
    out.append(scenario("final-adaptability-defender-control", ["ability:adaptability", "negative-control", "role:defender"],
                        attacker("Hariyama", atk=145), defender("Snorlax", dfn=110, ability=adaptability), "Karate Chop"))
    out.append(scenario("final-adaptability-fairy-on-no-dark-stab", ["ability:adaptability", "fairy", "negative-control", "stab"],
                        attacker("Houndoom", spa=145, ability=adaptability), defender("Snorlax", spd=110), "Moonblast", fairy=True))
    out.append(scenario("final-adaptability-fairy-off-dark-stab", ["ability:adaptability", "fairy", "stab", "type-rewrite"],
                        attacker("Houndoom", spa=145, ability=adaptability), defender("Snorlax", spd=110), "Moonblast", fairy=False))

    # Dynamic-type ability cross-products prove that the engine's STAB check reads its final
    # effective type and observed attacker typing. These are separate cases because the holder can
    # have only one attacker ability; Adaptability cannot coexist with a type-rewriting ability.
    rewrites = (
        ("normalize", "ABILITY_NORMALIZE", "Normalize", "Kecleon", "Karate Chop"),
        ("pixilate", "ABILITY_PIXILATE", "Pixilate", "Gardevoir", "Tackle"),
        ("refrigerate", "ABILITY_REFRIGERATE", "Refrigerate", "Glaceon", "Tackle"),
        ("aerilate", "ABILITY_AERILATE", "Aerilate", "Corvisquire", "Tackle"),
        ("galvanize", "ABILITY_GALVANIZE", "Galvanize", "Electabuzz", "Tackle"),
    )
    for slug_name, ab_id, ab_name, mon, move in rewrites:
        out.append(scenario(f"final-stab-rewrite-{slug_name}-matching-live-type",
                            [f"ability:{slug_name}", "stab", "type-rewrite"],
                            attacker(mon, atk=145, spa=145, ability=(ab_id, ab_name)),
                            defender("Snorlax", dfn=110, spd=110), move))
    out.append(scenario("final-stab-rewrite-normalize-removes-fighting-stab",
                        ["ability:normalize", "negative-control", "stab", "type-rewrite"],
                        attacker("Hariyama", atk=145, ability=("ABILITY_NORMALIZE", "Normalize")),
                        defender("Snorlax", dfn=110), "Karate Chop"))
    out.append(scenario("final-stab-rewrite-pixilate-no-matching-fairy-type",
                        ["ability:pixilate", "negative-control", "stab", "type-rewrite"],
                        attacker("Porygon", spa=145, ability=("ABILITY_PIXILATE", "Pixilate")),
                        defender("Snorlax", spd=110), "Tackle"))
    liquid_voice = ("ABILITY_LIQUID_VOICE", "Liquid Voice")
    for mon, tag in (("Blastoise", "matching"), ("Porygon", "nonmatching")):
        out.append(scenario(f"final-stab-rewrite-liquid-voice-{tag}-live-type",
                            ["ability:liquid-voice", "stab", "type-rewrite", "sound"],
                            attacker(mon, spa=145, ability=liquid_voice), defender("Snorlax", spd=110), "Hyper Voice"))
    return out


def _engine_items() -> list[dict]:
    out = []
    for item_sym, item_label, item_type in TYPE_BOOST_ITEMS:
        phys, spec = TYPE_MOVES[item_type]
        dfn_label = "Machamp" if item_type == "Ghost" else "Snorlax"
        for move in (phys, spec):
            out.append(scenario(f"engine-item-{slug(item_label)}-{slug(move)}", ["item:type-boost"],
                                attacker("Kecleon", atk=129, spa=129, item=(item_sym, item_label)),
                                defender(dfn_label, dfn=103, spd=103), move, surface="modelled"))
    out.append(scenario("engine-item-charcoal-off-type", ["item:type-boost"],
                        attacker("Kecleon", atk=129, item=("ITEM_CHARCOAL", "Charcoal")),
                        defender("Snorlax", dfn=103), "Waterfall", surface="modelled"))
    out.append(scenario("engine-item-charcoal-defender", ["item:type-boost"],
                        attacker("Kecleon", atk=129), defender("Snorlax", dfn=103, item=("ITEM_CHARCOAL", "Charcoal")),
                        "Fire Punch", surface="modelled"))
    out.append(scenario("engine-item-flame-plate-fire-punch", ["item:plate", "item:type-boost"],
                        attacker("Kecleon", atk=129, item=("ITEM_FLAME_PLATE", "Flame Plate")),
                        defender("Snorlax", dfn=103), "Fire Punch", surface="modelled"))
    out.append(scenario("engine-item-flame-plate-off-type", ["item:plate", "item:type-boost", "negative-control"],
                        attacker("Kecleon", atk=129, item=("ITEM_FLAME_PLATE", "Flame Plate")),
                        defender("Snorlax", dfn=103), "Waterfall", surface="modelled"))
    return out


def _group_d_held_items() -> list[dict]:
    """Direct held-item operands added for #92; all measurements use the pinned damage runner."""
    out = []
    def item(symbol_name, label):
        return (symbol("ITEM", label), label)
    def ability(name):
        return (symbol("ABILITY", name), name)
    def add(name, atk, dfn, move, tags=None, **kwargs):
        out.append(scenario("group-d-item-" + name,
                            ["group-d-held-item", "item:" + name.split("-")[0]] + (tags or []),
                            atk, dfn, move, **kwargs))

    # Gems use the source generated parameter and the pre-damage current item. The engine may
    # consume a matching Gem during the hit; the runtime record intentionally preserves its
    # pre-hit identity while the ordinary battler line records the post-hit ITEM_NONE.
    fire_gem = item("ITEM_FIRE_GEM", "Fire Gem")
    add("fire-gem-matching-consumed-after-hit", attacker("Kecleon", atk=145, item=fire_gem),
        defender("Snorlax", dfn=109), "Fire Punch")
    add("fire-gem-wrong-type", attacker("Kecleon", atk=145, item=fire_gem),
        defender("Snorlax", dfn=109), "Waterfall", tags=["negative-control"])
    add("fire-gem-technician-composition", attacker("Magmar", spa=145, ability=ability("Technician"),
        item=fire_gem), defender("Snorlax", spd=109), "Ember", tags=["modifier-stacking"])
    add("fire-gem-magic-room-suppressed", attacker("Kecleon", atk=145, item=fire_gem),
        defender("Snorlax", dfn=109), "Fire Punch", tags=["suppression"],
        state_setup={"magicRoom": True})
    add("no-current-gem", attacker("Kecleon", atk=145), defender("Snorlax", dfn=109),
        "Fire Punch", tags=["negative-control"])
    add("charcoal-magic-room-suppressed", attacker("Kecleon", atk=145, item=item("ITEM_CHARCOAL", "Charcoal")),
        defender("Snorlax", dfn=109), "Fire Punch", tags=["suppression"],
        state_setup={"magicRoom": True})
    add("charcoal-embargo-suppressed", attacker("Kecleon", atk=145, item=item("ITEM_CHARCOAL", "Charcoal")),
        defender("Snorlax", dfn=109), "Fire Punch", tags=["suppression"],
        state_setup={"attacker": {"embargo": 1}})
    add("charcoal-klutz-suppressed", attacker("Kecleon", atk=145, ability=ability("Klutz"),
        item=item("ITEM_CHARCOAL", "Charcoal")), defender("Snorlax", dfn=109), "Fire Punch",
        tags=["suppression"])

    # Category and type conditions for base-power items.
    add("muscle-band-physical", attacker("Machamp", atk=151, item=item("ITEM_MUSCLE_BAND", "Muscle Band")),
        defender("Snorlax", dfn=109), "Strength")
    add("muscle-band-special-control", attacker("Machamp", spa=151, item=item("ITEM_MUSCLE_BAND", "Muscle Band")),
        defender("Snorlax", spd=109), "Psychic", tags=["negative-control"])
    add("muscle-band-technician-composition", attacker("Scizor", atk=151,
        ability=ability("Technician"), item=item("ITEM_MUSCLE_BAND", "Muscle Band")),
        defender("Snorlax", dfn=109), "Metal Claw", tags=["modifier-stacking"])
    add("choice-band-physical", attacker("Machamp", atk=151, item=item("ITEM_CHOICE_BAND", "Choice Band")),
        defender("Snorlax", dfn=109), "Strength")
    add("choice-band-special-control", attacker("Machamp", spa=151, item=item("ITEM_CHOICE_BAND", "Choice Band")),
        defender("Snorlax", spd=109), "Psychic", tags=["negative-control"])
    add("choice-band-stage-composition", attacker("Machamp", atk=151, stages={"attack": 1},
        item=item("ITEM_CHOICE_BAND", "Choice Band")), defender("Snorlax", dfn=109), "Strength",
        tags=["modifier-stacking"], state_setup={"attackerStatStages": {"attack": 1}})
    add("choice-specs-special", attacker("Porygon", spa=151, item=item("ITEM_CHOICE_SPECS", "Choice Specs")),
        defender("Snorlax", spd=109), "Psychic")
    add("choice-specs-stage-composition", attacker("Porygon", spa=151, stages={"spAttack": 1},
        item=item("ITEM_CHOICE_SPECS", "Choice Specs")), defender("Snorlax", spd=109), "Psychic",
        tags=["modifier-stacking"], state_setup={"attackerStatStages": {"spAttack": 1}})
    add("choice-specs-physical-control", attacker("Machamp", atk=151, item=item("ITEM_CHOICE_SPECS", "Choice Specs")),
        defender("Snorlax", dfn=109), "Strength", tags=["negative-control"])

    # Source species predicates, including alternate forms whose base species differs from the
    # active form used by the source predicate.
    thick = item("ITEM_THICK_CLUB", "Thick Club")
    add("thick-club-cubone-physical", attacker("Cubone", atk=151, item=thick), defender("Snorlax", dfn=109), "Bone Club")
    add("thick-club-alolan-marowak", attacker("Marowak-Alola", atk=151, item=thick), defender("Snorlax", dfn=109), "Fire Punch")
    add("thick-club-wrong-species", attacker("Machamp", atk=151, item=thick), defender("Snorlax", dfn=109), "Strength",
        tags=["negative-control"])
    add("thick-club-special-control", attacker("Cubone", spa=151, item=thick), defender("Snorlax", spd=109), "Psychic",
        tags=["negative-control"])
    light = item("ITEM_LIGHT_BALL", "Light Ball")
    add("light-ball-pikachu-physical", attacker("Pikachu", atk=151, item=light), defender("Snorlax", dfn=109), "Strength")
    add("light-ball-pikachu-special", attacker("Pikachu", spa=151, item=light), defender("Snorlax", spd=109), "Psychic")
    add("light-ball-pikachu-form", attacker("Pikachu-Rock-Star", atk=151, item=light),
        defender("Snorlax", dfn=109), "Strength", tags=["form"])
    add("light-ball-wrong-species", attacker("Pichu", atk=151, item=light), defender("Snorlax", dfn=109), "Strength",
        tags=["negative-control"])
    tooth = item("ITEM_DEEP_SEA_TOOTH", "Deep Sea Tooth")
    add("deep-sea-tooth-clamperl-special", attacker("Clamperl", spa=151, item=tooth), defender("Snorlax", spd=109), "Surf")
    add("deep-sea-tooth-physical-control", attacker("Clamperl", atk=151, item=tooth), defender("Snorlax", dfn=109), "Waterfall",
        tags=["negative-control"])
    add("deep-sea-tooth-wrong-species", attacker("Porygon", spa=151, item=tooth), defender("Snorlax", spd=109), "Psychic",
        tags=["negative-control"])

    # Signature Orbs and Soul Dew are type- and species-gated at CalcMoveBasePowerAfterModifiers.
    for family, species, form, signature, move, wrong_species in (
        ("lustrous-orb", "Palkia", "Palkia-Origin", "Lustrous Orb", "Surf", "Dialga"),
        ("adamant-orb", "Dialga", "Dialga-Origin", "Adamant Orb", "Iron Head", "Palkia"),
        ("griseous-orb", "Giratina", "Giratina-Origin", "Griseous Orb", "Dragon Pulse", "Palkia"),
    ):
        orb = item("ITEM_" + signature.upper().replace(" ", "_"), signature)
        add(f"{family}-matching", attacker(species, atk=151, spa=151, item=orb), defender("Snorlax", dfn=109), move)
        add(f"{family}-alternate-form", attacker(form, atk=151, spa=151, item=orb), defender("Snorlax", dfn=109), move,
            tags=["form"])
        add(f"{family}-wrong-species", attacker(wrong_species, atk=151, spa=151, item=orb),
            defender("Snorlax", dfn=109), move, tags=["negative-control"])
        add(f"{family}-wrong-type", attacker(species, atk=151, spa=151, item=orb),
            defender("Snorlax", dfn=109), "Fire Punch", tags=["negative-control"])
    soul = item("ITEM_SOUL_DEW", "Soul Dew")
    add("soul-dew-latias-psychic", attacker("Latias", spa=151, item=soul), defender("Snorlax", spd=109), "Psychic")
    add("soul-dew-latios-dragon", attacker("Latios", spa=151, item=soul), defender("Snorlax", spd=109), "Dragon Pulse")
    add("soul-dew-wrong-species", attacker("Palkia", spa=151, item=soul), defender("Snorlax", spd=109), "Psychic",
        tags=["negative-control"])
    add("soul-dew-wrong-type", attacker("Latias", spa=151, item=soul), defender("Snorlax", spd=109), "Fire Blast",
        tags=["negative-control"])

    # Punching Glove changes base power and makes punching moves avoid contact effects.
    glove = item("ITEM_PUNCHING_GLOVE", "Punching Glove")
    add("punching-glove-punching", attacker("Machamp", atk=151, item=glove), defender("Snorlax", dfn=109), "Fire Punch")
    add("punching-glove-non-punching", attacker("Machamp", atk=151, item=glove), defender("Snorlax", dfn=109), "Fire Blast",
        tags=["negative-control"])
    add("punching-glove-contact-fluffy", attacker("Machamp", atk=151, item=glove),
        defender("Snorlax", dfn=109, ability=ability("Fluffy")), "Fire Punch", tags=["modifier-stacking"])
    add("punching-glove-long-reach-composition", attacker("Machamp", atk=151,
        ability=ability("Long Reach"), item=glove),
        defender("Snorlax", dfn=109, ability=ability("Fluffy")), "Fire Punch", tags=["modifier-stacking"])

    # All three Ogerpon masks share the source base-species predicate.
    for mask, species in (("Cornerstone Mask", "Ogerpon-Cornerstone"),
                          ("Wellspring Mask", "Ogerpon-Wellspring"),
                          ("Hearthflame Mask", "Ogerpon-Hearthflame")):
        mask_item = item("ITEM_" + mask.upper().replace(" ", "_"), mask)
        add("ogrepon-" + slug(mask) + "-qualified", attacker(species, atk=151, item=mask_item),
            defender("Snorlax", dfn=109), "Leaf Blade")
    add("ogrepon-mask-wrong-species", attacker("Machamp", atk=151, item=item("ITEM_CORNERSTONE_MASK", "Cornerstone Mask")),
        defender("Snorlax", dfn=109), "Leaf Blade", tags=["negative-control"])
    add("ogrepon-mask-magic-room", attacker("Ogerpon-Teal", atk=151,
        item=item("ITEM_CORNERSTONE_MASK", "Cornerstone Mask")), defender("Snorlax", dfn=109), "Leaf Blade",
        tags=["suppression"], state_setup={"magicRoom": True})

    # Defender defense items use CalcDefenseStat's exact selected stat, including Wonder Room and
    # source live transform state for Metal Powder and Eviolite.
    scale = item("ITEM_DEEP_SEA_SCALE", "Deep Sea Scale")
    add("deep-sea-scale-clamperl-special", attacker("Porygon", spa=151), defender("Clamperl", spd=109, item=scale), "Psychic")
    add("deep-sea-scale-physical-control", attacker("Machamp", atk=151), defender("Clamperl", dfn=109, item=scale), "Strength",
        tags=["negative-control"])
    add("deep-sea-scale-wrong-species", attacker("Porygon", spa=151), defender("Snorlax", spd=109, item=scale), "Psychic",
        tags=["negative-control"])
    powder = item("ITEM_METAL_POWDER", "Metal Powder")
    add("metal-powder-ditto-physical", attacker("Machamp", atk=151), defender("Ditto", dfn=109, item=powder), "Strength")
    add("metal-powder-special-control", attacker("Porygon", spa=151), defender("Ditto", spd=109, item=powder), "Psychic",
        tags=["negative-control"])
    add("metal-powder-transformed-ditto", attacker("Machamp", atk=151), defender("Ditto", dfn=109, item=powder), "Strength",
        tags=["form", "negative-control"], state_setup={"defender": {"transformed": 1}})
    eviolite = item("ITEM_EVIOLITE", "Eviolite")
    add("eviolite-evolvable", attacker("Machamp", atk=151), defender("Cubone", dfn=109, item=eviolite), "Strength")
    add("eviolite-non-evolvable", attacker("Machamp", atk=151), defender("Snorlax", dfn=109, item=eviolite), "Strength",
        tags=["negative-control"])
    add("eviolite-transformed-evolvable", attacker("Machamp", atk=151), defender("Snorlax", dfn=109, item=eviolite), "Strength",
        tags=["form"], state_setup={"defender": {"transformed": 1},
                                   "defenderTransformedMonSpecies": "SPECIES_CUBONE"})
    add("eviolite-transformed-non-evolvable", attacker("Machamp", atk=151), defender("Cubone", dfn=109, item=eviolite), "Strength",
        tags=["form", "negative-control"], state_setup={"defender": {"transformed": 1},
                                                        "defenderTransformedMonSpecies": "SPECIES_SNORLAX"})
    add("eviolite-wonder-room", attacker("Porygon", spa=151), defender("Cubone", dfn=109, spd=109, item=eviolite), "Psychic",
        tags=["field:wonder-room"], state_setup={"wonderRoom": True})
    vest = item("ITEM_ASSAULT_VEST", "Assault Vest")
    # The Assault Vest holder still needs a selectable move in the link-battle runner. Snorlax's
    # Tackle is a neutral action against these Ghost attackers, so the selected hit's HP inputs
    # remain unchanged while its defender-side item is live.
    add("assault-vest-special", attacker("Gengar", spa=151), defender("Snorlax", spd=109, item=vest), "Psychic")
    add("assault-vest-physical-control", attacker("Gengar", atk=151), defender("Snorlax", dfn=109, item=vest), "Fire Punch",
        tags=["negative-control"])
    add("assault-vest-wonder-room-physical", attacker("Gengar", atk=151), defender("Snorlax", dfn=109, spd=109, item=vest),
        "Fire Punch", tags=["field:wonder-room"], state_setup={"wonderRoom": True})

    # Final item slots, threshold controls, live Metronome counter, and rounding-sensitive
    # composition with attacker/defender abilities across the pinned raw-Speed order.
    life = item("ITEM_LIFE_ORB", "Life Orb")
    belt = item("ITEM_EXPERT_BELT", "Expert Belt")
    metronome = item("ITEM_METRONOME", "Metronome")
    neuro = ability("Neuroforce")
    filter_ability = ability("Filter")
    add("life-orb-floored", attacker("Machamp", atk=151, item=life), defender("Snorlax", dfn=109), "Strength")
    add("expert-belt-neutral", attacker("Machamp", atk=151, item=belt), defender("Snorlax", dfn=109), "Strength",
        tags=["negative-control"])
    add("expert-belt-super-effective", attacker("Magmar", spa=151, item=belt), defender("Tangela", spd=109), "Fire Blast")
    add("expert-belt-four-times", attacker("Magmar", spa=151, item=belt), defender("Scizor", spd=109), "Fire Blast")
    for turns in (0, 1, 3, 5, 8):
        add("metronome-counter-" + str(turns), attacker("Machamp", atk=151, item=metronome),
            defender("Snorlax", dfn=109), "Strength", tags=["runtime:metronome-counter"],
            state_setup={"attacker": {"metronomeItemCounter": turns}})
    add("metronome-magic-room-suppressed", attacker("Machamp", atk=151, item=metronome),
        defender("Snorlax", dfn=109), "Strength", tags=["suppression"],
        state_setup={"magicRoom": True, "attacker": {"metronomeItemCounter": 3}})
    for label, item_desc, state in (("life-orb", life, {}), ("expert-belt", belt, {}),
                                    ("metronome", metronome, {"attacker": {"metronomeItemCounter": 3}})):
        for speed_label, atk_speed in (("faster", 120), ("slower", 40)):
            add(f"final-order-{label}-{speed_label}", attacker("Magmar", spa=151, spe=atk_speed,
                ability=neuro, item=item_desc), defender("Scizor", spd=109, spe=80, ability=filter_ability),
                "Fire Blast", tags=["final-modifier-order", "modifier-stacking"], crit=False,
                **({"state_setup": state} if state else {}))
    # A matching Shuca Berry is the defender slot and composes with Life Orb plus Neuroforce.
    shuca = item("ITEM_SHUCA_BERRY", "Shuca Berry")
    for speed_label, atk_speed in (("faster", 120), ("slower", 40)):
        add("final-order-resist-berry-" + speed_label,
            attacker("Jolteon", spa=151, spe=atk_speed, ability=neuro, item=life),
            defender("Golem", spd=109, spe=80, ability=filter_ability, item=shuca), "Earth Power",
            tags=["final-modifier-order", "modifier-stacking"])

    # Resist berries: current item, type/effectiveness, Ripen, Unnerve, and Normal's exception.
    occa = item("ITEM_OCCA_BERRY", "Occa Berry")
    add("resist-berry-matching-super-effective", attacker("Magmar", spa=151),
        defender("Tangela", spd=109, item=occa), "Fire Blast")
    add("resist-berry-wrong-type", attacker("Magmar", spa=151),
        defender("Tangela", spd=109, item=item("ITEM_PASSHO_BERRY", "Passho Berry")), "Fire Blast",
        tags=["negative-control"])
    add("resist-berry-normal-neutral", attacker("Machamp", atk=151),
        defender("Snorlax", dfn=109, item=item("ITEM_CHILAN_BERRY", "Chilan Berry")), "Strength")
    add("resist-berry-ripen", attacker("Magmar", spa=151),
        defender("Tangela", spd=109, ability=ability("Ripen"), item=occa), "Fire Blast")
    add("resist-berry-unnerve", attacker("Magmar", spa=151, ability=ability("Unnerve")),
        defender("Tangela", spd=109, item=occa), "Fire Blast", tags=["suppression"])
    add("resist-berry-as-one-ice-rider", attacker("Magmar", spa=151, ability=ability("As One Ice Rider")),
        defender("Tangela", spd=109, item=occa), "Fire Blast", tags=["suppression"])
    add("resist-berry-as-one-shadow-rider", attacker("Magmar", spa=151, ability=ability("As One Shadow Rider")),
        defender("Tangela", spd=109, item=occa), "Fire Blast", tags=["suppression"])
    add("resist-berry-no-current-item", attacker("Magmar", spa=151), defender("Tangela", spd=109), "Fire Blast",
        tags=["negative-control"])
    add("resist-berry-klutz-suppressed", attacker("Magmar", spa=151),
        defender("Tangela", spd=109, ability=ability("Klutz"), item=occa), "Fire Blast", tags=["suppression"])

    # Utility Umbrella normalizes only the documented Sun/Rain paths; suppression restores them.
    umbrella = item("ITEM_UTILITY_UMBRELLA", "Utility Umbrella")
    add("umbrella-defender-rain-fire", attacker("Magmar", spa=151),
        defender("Snorlax", spd=109, item=umbrella), "Fire Blast", weather="rain")
    add("umbrella-defender-sun-water", attacker("Blastoise", spa=151),
        defender("Snorlax", spd=109, item=umbrella), "Surf", weather="sun")
    add("umbrella-embargo-rain-fire", attacker("Magmar", spa=151),
        defender("Snorlax", spd=109, item=umbrella), "Fire Blast", weather="rain",
        tags=["suppression"], state_setup={"defender": {"embargo": 1}})
    add("umbrella-magic-room-rain-fire", attacker("Magmar", spa=151),
        defender("Snorlax", spd=109, item=umbrella), "Fire Blast", weather="rain",
        tags=["suppression"], state_setup={"magicRoom": True})
    orichalcum = ability("Orichalcum Pulse")
    add("umbrella-orichalcum-pulse-sun", attacker("Machamp", atk=151, ability=orichalcum, item=umbrella),
        defender("Snorlax", dfn=109), "Strength", weather="sun")
    add("umbrella-orichalcum-pulse-embargo-sun", attacker("Machamp", atk=151, ability=orichalcum, item=umbrella),
        defender("Snorlax", dfn=109), "Strength", weather="sun", tags=["suppression"],
        state_setup={"attacker": {"embargo": 1}})

    # Booster Energy is only a switch-in trigger; already-authoritative Paradox state carries
    # the damage modifier. An extant item must not add a second multiplier.
    booster = item("ITEM_BOOSTER_ENERGY", "Booster Energy")
    proto = ability("Protosynthesis")
    quark = ability("Quark Drive")
    add("booster-energy-held-proto-sun", attacker("Machamp", atk=151, ability=proto, item=booster),
        defender("Snorlax", dfn=109, ability=ability("Drought")), "Strength", weather="sun",
        state_setup={"attacker": {"transformed": 0, "boosterEnergyActivated": 0, "paradoxBoostedStat": 1}})
    add("booster-energy-active-quark-terrain", attacker("Machamp", atk=151, ability=quark),
        defender("Snorlax", dfn=109), "Strength", terrain="electric",
        state_setup={"attacker": {"transformed": 0, "boosterEnergyActivated": 1, "paradoxBoostedStat": 1}})
    add("booster-energy-wrong-ability", attacker("Machamp", atk=151, item=booster),
        defender("Snorlax", dfn=109), "Strength", tags=["negative-control"])
    add("booster-energy-transformed-paradox", attacker("Machamp", atk=151, ability=proto),
        defender("Snorlax", dfn=109), "Strength",
        state_setup={"attacker": {"transformed": 1, "boosterEnergyActivated": 0, "paradoxBoostedStat": 0}})

    # Primal Orb does not multiply damage; the source-settled live form/ability is the operand.
    add("primal-blue-orb-kyogre-primal", attacker("Kyogre-Primal", spa=151,
        ability=ability("Primordial Sea"), item=item("ITEM_BLUE_ORB", "Blue Orb")),
        defender("Snorlax", spd=109), "Surf", weather="rain")
    add("primal-red-orb-groudon-primal", attacker("Groudon-Primal", atk=151,
        ability=ability("Desolate Land"), item=item("ITEM_RED_ORB", "Red Orb")),
        defender("Snorlax", dfn=109), "Strength", weather="sun")
    return out


def _authoritative_doubles() -> list[dict]:
    def ability(name): return (symbol("ABILITY", name), name)
    out = []
    def add(name, move="Strength", partner="present", setup=None, atk_ability=None, **kwargs):
        out.append(scenario("ordinary-doubles-" + name, ["ordinary-doubles"],
            attacker("Machamp", atk=151, spa=151, ability=atk_ability or NEUTRAL_ABILITY),
            defender("Snorlax", dfn=109, spd=109), move,
            doubles=partner, state_setup={"doubles": setup or {"helpingHand": 0}}, **kwargs))
    for move in ("Rock Slide", "Heat Wave", "Strength", "Petal Blizzard"):
        for partner in ("present", "fainted"):
            add(slug(move) + "-" + partner, move, partner)
    for move, screen in (("Strength", "reflect"), ("Psychic", "light-screen")):
        for partner in ("present", "fainted"):
            add(screen + "-" + partner, move, partner, reflect=screen == "reflect", light_screen=screen == "light-screen")
    for count in (1, 2, 7): add("helping-hand-" + str(count), setup={"helpingHand": count})
    for name, move in (("Battery", "Psychic"), ("Power Spot", "Strength"), ("Steely Spirit", "Iron Head")):
        for suppressed in (0, 1):
            add(slug(name) + "-gastro-" + str(suppressed), move, setup={"attackerPartnerAbility": ability(name)[0], "attackerPartnerGastroAcid": suppressed})
    add("battery-physical-control", setup={"attackerPartnerAbility": "ABILITY_BATTERY"})
    for name in ("Plus", "Minus"):
        for partner in ("Plus", "Minus"):
            add(slug(name) + "-" + slug(partner), "Psychic", atk_ability=ability(name), setup={"attackerPartnerAbility": ability(partner)[0]})
    for suppressed in (0, 1):
        add("friend-guard-gastro-" + str(suppressed), setup={"defenderPartnerAbility": "ABILITY_FRIEND_GUARD", "defenderPartnerGastroAcid": suppressed})
    for category in ("attack", "defense"):
        add("flower-gift-" + category, "Strength" if category == "attack" else "Psychic", weather="sun",
            setup={category.replace("attack", "attacker").replace("defense", "defender") + "PartnerAbility": "ABILITY_FLOWER_GIFT",
                   category.replace("attack", "attacker").replace("defense", "defender") + "PartnerSpecies": "SPECIES_CHERRIM_SUNSHINE"})
    for aura, move in (("Dark Aura", "Bite"), ("Fairy Aura", "Dazzling Gleam")):
        add(slug(aura), move, setup={"attackerPartnerAbility": ability(aura)[0]})
        add(slug(aura) + "-break", move, setup={"attackerPartnerAbility": ability(aura)[0], "defenderPartnerAbility": "ABILITY_AURA_BREAK"})
    for bit, move in ((1,"Psychic"),(2,"Strength"),(4,"Strength"),(8,"Psychic")):
        add("ruin-" + str(bit), move, setup={"attackerPartnerRuinFlags": bit})
    for name in ("Cloud Nine", "Air Lock"):
        for weather in ("sun", "rain"):
            for suppressed in (0, 1):
                add(slug(name) + "-" + weather + "-gastro-" + str(suppressed), "Flamethrower", weather=weather,
                    setup={"attackerPartnerAbility": ability(name)[0], "attackerPartnerGastroAcid": suppressed})
    add("plus-suppressed-partner", "Psychic", atk_ability=ability("Plus"),
        setup={"attackerPartnerAbility": "ABILITY_MINUS", "attackerPartnerGastroAcid": 1})
    add("friend-guard-absent", partner="fainted", setup={"defenderPartnerAbility": "ABILITY_FRIEND_GUARD"})
    add("composition", "Rock Slide", reflect=True, setup={"helpingHand": 2, "attackerPartnerAbility": "ABILITY_POWER_SPOT", "defenderPartnerAbility": "ABILITY_FRIEND_GUARD"})
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


def _remaining_group_d() -> list[dict]:
    """Low-state Group D ability predicates that need no additional runtime readers."""
    out = []
    marvel = ("ABILITY_MARVEL_SCALE", "Marvel Scale")
    flower = ("ABILITY_FLOWER_GIFT", "Flower Gift")
    tough = ("ABILITY_TOUGH_CLAWS", "Tough Claws")
    sheer = ("ABILITY_SHEER_FORCE", "Sheer Force")
    fluffy = ("ABILITY_FLUFFY", "Fluffy")
    out.extend((
        scenario("group-d-marvel-scale-physical-burn", ["ability:marvel-scale", "defense-stage", "status:paralysis"],
                 attacker("Machamp", atk=151), defender("Snorlax", dfn=109, status="paralysis", ability=marvel), "Strength"),
        scenario("group-d-marvel-scale-physical-no-status", ["ability:marvel-scale", "defense-stage", "negative-control"],
                 attacker("Machamp", atk=151), defender("Snorlax", dfn=109, ability=marvel), "Strength"),
        scenario("group-d-marvel-scale-special-status", ["ability:marvel-scale", "defense-stage", "negative-control", "status:paralysis"],
                 attacker("Porygon", spa=151), defender("Snorlax", spd=109, status="paralysis", ability=marvel), "Psychic"),
        scenario("group-d-marvel-scale-status-stage-composition", ["ability:marvel-scale", "defense-stage", "modifier-stacking", "status:paralysis"],
                 attacker("Machamp", atk=151), defender("Snorlax", dfn=109, status="paralysis", stages={"defense": 1}, ability=marvel), "Strength"),
        scenario("group-d-marvel-scale-mold-breaker", ["ability:marvel-scale", "mold-breaker", "suppression", "engine-only", "status:paralysis"],
                 attacker("Machamp", atk=151, ability=("ABILITY_MOLD_BREAKER", "Mold Breaker")),
                 defender("Snorlax", dfn=109, status="paralysis", ability=marvel), "Strength", surface="engine-only"),
        scenario("group-d-marvel-scale-ability-shield", ["ability:marvel-scale", "mold-breaker", "ability-shield", "status:paralysis"],
                 attacker("Machamp", atk=151, ability=("ABILITY_MOLD_BREAKER", "Mold Breaker")),
                 defender("Snorlax", dfn=109, status="paralysis", ability=marvel,
                          item=("ITEM_ABILITY_SHIELD", "Ability Shield")), "Strength"),
    ))
    umbrella = ("ITEM_UTILITY_UMBRELLA", "Utility Umbrella")
    cloud_nine = ("ABILITY_CLOUD_NINE", "Cloud Nine")
    out.extend((
        scenario("group-d-flower-gift-attacker-sun-physical", ["ability:flower-gift", "form", "weather:sun", "attack-stage"],
                 attacker("Cherrim-Sunshine", atk=151, ability=flower), defender("Snorlax", dfn=109), "Strength", weather="sun"),
        scenario("group-d-flower-gift-attacker-no-sun", ["ability:flower-gift", "form", "negative-control"],
                 attacker("Cherrim-Sunshine", atk=151, ability=flower), defender("Snorlax", dfn=109), "Strength",
                 state_setup={"attackerSpeciesForm": "SPECIES_CHERRIM_SUNSHINE"}),
        scenario("group-d-flower-gift-wrong-form", ["ability:flower-gift", "form", "weather:sun", "negative-control"],
                 attacker("Cherrim", atk=151, ability=flower), defender("Snorlax", dfn=109), "Strength", weather="sun",
                 state_setup={"attackerSpeciesForm": "SPECIES_CHERRIM"}),
        scenario("group-d-flower-gift-attacker-special", ["ability:flower-gift", "form", "negative-control", "weather:sun"],
                 attacker("Cherrim-Sunshine", spa=151, ability=flower), defender("Snorlax", spd=109), "Psychic", weather="sun"),
        scenario("group-d-flower-gift-defender-sun-special", ["ability:flower-gift", "form", "weather:sun", "defense-stage"],
                 attacker("Porygon", spa=151), defender("Cherrim-Sunshine", spd=109, ability=flower), "Psychic", weather="sun"),
        scenario("group-d-flower-gift-defender-physical", ["ability:flower-gift", "form", "weather:sun", "negative-control"],
                 attacker("Machamp", atk=151), defender("Cherrim-Sunshine", dfn=109, ability=flower), "Strength", weather="sun"),
        scenario("group-d-flower-gift-attacker-cloud-nine", ["ability:flower-gift", "form", "weather:sun", "weather-suppression", "negative-control"],
                 attacker("Cherrim-Sunshine", atk=151, ability=flower), defender("Snorlax", dfn=109, ability=cloud_nine), "Strength", weather="sun",
                 state_setup={"attackerSpeciesForm": "SPECIES_CHERRIM_SUNSHINE"}),
        scenario("group-d-flower-gift-attacker-umbrella", ["ability:flower-gift", "form", "weather:sun", "item:utility-umbrella", "negative-control"],
                 attacker("Cherrim-Sunshine", atk=151, ability=flower, item=umbrella), defender("Snorlax", dfn=109), "Strength", weather="sun",
                 state_setup={"attackerSpeciesForm": "SPECIES_CHERRIM_SUNSHINE"}),
        scenario("group-d-flower-gift-defender-holder-umbrella", ["ability:flower-gift", "form", "weather:sun", "item:utility-umbrella", "negative-control"],
                 attacker("Porygon", spa=151), defender("Cherrim-Sunshine", spd=109, ability=flower, item=umbrella), "Psychic", weather="sun",
                 state_setup={"defenderSpeciesForm": "SPECIES_CHERRIM_SUNSHINE"}),
        scenario("group-d-flower-gift-defender-cloud-nine", ["ability:flower-gift", "form", "weather:sun", "weather-suppression", "negative-control"],
                 attacker("Porygon", spa=151, ability=cloud_nine), defender("Cherrim-Sunshine", spd=109, ability=flower), "Psychic", weather="sun",
                 state_setup={"defenderSpeciesForm": "SPECIES_CHERRIM_SUNSHINE"}),
    ))
    protective_pads = ("ITEM_PROTECTIVE_PADS", "Protective Pads")
    long_reach = ("ABILITY_LONG_REACH", "Long Reach")
    charcoal = ("ITEM_CHARCOAL", "Charcoal")
    out.extend((
        scenario("group-d-tough-claws-fire-punch-contact", ["ability:tough-claws", "contact", "base-power", "item:charcoal", "modifier-stacking"],
                 attacker("Machamp", atk=151, ability=tough, item=charcoal), defender("Snorlax", dfn=109), "Fire Punch"),
        scenario("group-d-tough-claws-flamethrower-noncontact", ["ability:tough-claws", "contact", "negative-control"],
                 attacker("Machamp", spa=151, ability=tough), defender("Snorlax", spd=109), "Flamethrower"),
        scenario("group-d-tough-claws-protective-pads-still-contact", ["ability:tough-claws", "contact", "item:protective-pads", "engine-only"],
                 attacker("Machamp", atk=151, ability=tough, item=protective_pads), defender("Snorlax", dfn=109), "Fire Punch", surface="engine-only"),
    ))
    wise = ("ITEM_WISE_GLASSES", "Wise Glasses")
    out.extend((
        scenario("group-d-sheer-force-scald-helper-positive", ["ability:sheer-force", "base-power", "move-additional-effect", "modifier-stacking"],
                 attacker("Porygon", spa=151, ability=sheer, item=wise), defender("Snorlax", spd=109), "Scald"),
        scenario("group-d-sheer-force-pay-day-helper-negative", ["ability:sheer-force", "base-power", "move-additional-effect", "negative-control"],
                 attacker("Porygon", atk=151, ability=sheer), defender("Snorlax", dfn=109), "Pay Day"),
        scenario("group-d-sheer-force-fire-blast-positive", ["ability:sheer-force", "base-power", "move-additional-effect"],
                 attacker("Porygon", spa=151, ability=sheer), defender("Snorlax", spd=109), "Fire Blast"),
        scenario("group-d-sheer-force-defender-control", ["ability:sheer-force", "base-power", "negative-control"],
                 attacker("Porygon", spa=151), defender("Snorlax", spd=109, ability=sheer), "Scald"),
    ))
    mold = ("ABILITY_MOLD_BREAKER", "Mold Breaker")
    shield = ("ITEM_ABILITY_SHIELD", "Ability Shield")
    out.extend((
        scenario("group-d-fluffy-fire-noncontact-double", ["ability:fluffy", "contact", "final-modifier"],
                 attacker("Machamp", spa=151), defender("Snorlax", dfn=109, ability=fluffy), "Fire Blast"),
        scenario("group-d-fluffy-fire-contact-neutral", ["ability:fluffy", "contact", "final-modifier", "negative-control"],
                 attacker("Machamp", atk=151), defender("Snorlax", dfn=109, ability=fluffy), "Fire Punch"),
        scenario("group-d-fluffy-nonfire-contact-half", ["ability:fluffy", "contact", "final-modifier"],
                 attacker("Machamp", atk=151), defender("Snorlax", dfn=109, ability=fluffy), "Tackle"),
        scenario("group-d-fluffy-nonfire-noncontact-neutral", ["ability:fluffy", "contact", "final-modifier", "negative-control"],
                 attacker("Porygon", spa=151), defender("Snorlax", spd=109, ability=fluffy), "Psychic"),
        scenario("group-d-fluffy-long-reach-suppresses-contact", ["ability:fluffy", "contact", "ability:long-reach", "final-modifier"],
                 attacker("Machamp", atk=151, ability=long_reach), defender("Snorlax", dfn=109, ability=fluffy), "Fire Punch"),
        scenario("group-d-fluffy-protective-pads-do-not-suppress-contact", ["ability:fluffy", "contact", "item:protective-pads", "negative-control", "engine-only"],
                 attacker("Machamp", atk=151, item=protective_pads), defender("Snorlax", dfn=109, ability=fluffy), "Tackle", surface="engine-only"),
        scenario("group-d-fluffy-mold-breaker", ["ability:fluffy", "contact", "mold-breaker", "suppression", "engine-only"],
                 attacker("Machamp", atk=151, ability=mold), defender("Snorlax", dfn=109, ability=fluffy), "Tackle", surface="engine-only"),
        scenario("group-d-fluffy-ability-shield", ["ability:fluffy", "contact", "mold-breaker", "ability-shield"],
                 attacker("Machamp", atk=151, ability=mold), defender("Snorlax", dfn=109, ability=fluffy, item=shield), "Tackle"),
    ))
    reckless = ("ABILITY_RECKLESS", "Reckless")
    sand_force = ("ABILITY_SAND_FORCE", "Sand Force")
    battery = ("ABILITY_BATTERY", "Battery")
    power_spot = ("ABILITY_POWER_SPOT", "Power Spot")
    out.extend((
        scenario("group-d-reckless-ordinary-hit-clear", ["ability:reckless", "ordinary-move", "negative-control"],
                 attacker("Machamp", atk=151, ability=reckless), defender("Snorlax", dfn=109), "Strength"),
        scenario("group-d-sand-force-sandstorm-ground-engine-only", ["ability:sand-force", "weather:sandstorm", "base-power", "engine-only"],
                 attacker("Machamp", atk=151, ability=sand_force), defender("Swampert", dfn=109), "Earthquake", weather="sandstorm", surface="engine-only"),
        scenario("group-d-sand-force-sun-clear", ["ability:sand-force", "weather:sun", "negative-control"],
                 attacker("Machamp", atk=151, ability=sand_force), defender("Snorlax", dfn=109), "Earthquake", weather="sun"),
        scenario("group-d-sand-force-sandstorm-normal-clear", ["ability:sand-force", "weather:sandstorm", "negative-control", "engine-only"],
                 attacker("Machamp", atk=151, ability=sand_force), defender("Swampert", dfn=109), "Strength", weather="sandstorm", surface="engine-only"),
        scenario("group-d-sand-force-cloud-nine-no-boost", ["ability:sand-force", "weather:sandstorm", "weather-suppression", "negative-control", "engine-only"],
                 attacker("Machamp", atk=151, ability=sand_force), defender("Swampert", dfn=109, ability=cloud_nine), "Earthquake",
                 weather="sandstorm", surface="engine-only"),
        scenario("group-d-battery-singles-self-clear", ["ability:battery", "singles", "negative-control"],
                 attacker("Porygon", spa=151, ability=battery), defender("Snorlax", spd=109), "Psychic"),
        scenario("group-d-power-spot-singles-self-clear", ["ability:power-spot", "singles", "negative-control"],
                 attacker("Machamp", atk=151, ability=power_spot), defender("Snorlax", dfn=109), "Strength"),
        scenario("group-d-battery-defender-singles-clear", ["ability:battery", "singles", "negative-control"],
                 attacker("Porygon", spa=151), defender("Snorlax", spd=109, ability=battery), "Psychic"),
        scenario("group-d-power-spot-defender-singles-clear", ["ability:power-spot", "singles", "negative-control"],
                 attacker("Machamp", atk=151), defender("Snorlax", dfn=109, ability=power_spot), "Strength"),
    ))
    return out


def _state_backed_group_d() -> list[dict]:
    """One-hit predicate isolation; setup writes operands at the test runner's pre-damage hook."""
    out = []
    def ability(name):
        return (symbol("ABILITY", name), name)
    def add(name, ability_name, *, move="Strength", role="attacker", a=None, d=None,
            astate=None, dstate=None, setup=None, **kwargs):
        a = a or attacker("Machamp", atk=151, spa=151)
        d = d or defender("Snorlax", dfn=109, spd=109)
        (a if role == "attacker" else d).update(ability=ability(ability_name)[0], abilityLabel=ability_name)
        state = {"capture": True, **(setup or {})}
        if astate is not None: state["attacker"] = astate
        if dstate is not None: state["defender"] = dstate
        out.append(scenario("state-d-" + name, ["state-backed-group-d", "ability:" + slug(ability_name)],
                            a, d, move, state_setup=state, **kwargs))
    for timer, move, name in ((5, "Strength", "active"), (0, "Strength", "zero"), (5, "Psychic", "special")):
        add("slow-start-" + name, "Slow Start", move=move, astate={"slowStartTimer": timer})
    add("slow-start-stage-composition", "Slow Start", astate={"slowStartTimer": 3},
        a=attacker("Machamp", atk=151, stages={"attack": 1}))
    for boosted, move, name in ((1, "Flamethrower", "active"), (0, "Flamethrower", "inactive"),
                                 (1, "Strength", "nonfire"), (1, "Fire Punch", "physical")):
        add("flash-fire-" + name, "Flash Fire", move=move, astate={"flashFireBoosted": boosted})
    for name, a_species, d_species, apid, dpid in (
        ("same", "Machamp", "Snorlax", 255, 255), ("opposite", "Machamp", "Snorlax", 255, 0),
        ("attacker-genderless", "Porygon", "Snorlax", 255, 255),
        ("defender-genderless", "Machamp", "Porygon", 255, 255)):
        add("rivalry-" + name, "Rivalry", a=attacker(a_species, atk=151), d=defender(d_species, dfn=109),
            astate={"personality": apid}, dstate={"personality": dpid})
    add("rivalry-current-form-genderless", "Rivalry", a=attacker("Porygon", atk=151),
        astate={"personality": 255, "transformed": 1}, dstate={"personality": 255})
    for raw in range(4):
        add("stakeout-raw-" + str(raw), "Stakeout", dstate={"isFirstTurn": raw})
    add("stakeout-stage-composition", "Stakeout", dstate={"isFirstTurn": 2},
        a=attacker("Machamp", atk=151, stages={"attack": 1}))
    for count in range(6):
        add("supreme-overlord-counter-" + str(count), "Supreme Overlord", astate={"supremeOverlordCounter": count})
    add("supreme-overlord-rounding-composition", "Supreme Overlord", astate={"supremeOverlordCounter": 3},
        move="Flamethrower", a=attacker("Machamp", spa=151),
        d=defender("Snorlax", spd=109, ability=ability("Heatproof")))
    for name, move, selected, active in (("physical", "Strength", 0, 0), ("special", "Psychic", 0, 0),
                                       ("pending", "Strength", 1, 0), ("active", "Strength", 0, 4)):
        add("gorilla-tactics-" + name, "Gorilla Tactics", move=move,
            astate={"dynamaxSelected": selected, "activeGimmick": active},
            surface="engine-only" if selected or active else "modelled")
    for name in ("Protosynthesis", "Quark Drive"):
        activation = {"weather": "sun"} if name == "Protosynthesis" else {"terrain": "electric"}
        for role, stat, move in (("attacker", "atk", "Strength"), ("attacker", "spa", "Psychic"),
                                ("attacker", "dfn", "Strength"), ("attacker", "spe", "Strength"),
                                ("defender", "dfn", "Strength"), ("defender", "spd", "Psychic")):
            a = attacker("Machamp", **({stat: 201} if role == "attacker" else {"atk": 151, "spa": 151}))
            d = defender("Snorlax", **({stat: 201} if role == "defender" else {"dfn": 109, "spd": 109}))
            state = {"transformed": 0, "boosterEnergyActivated": 0, "paradoxBoostedStat": 0}
            add(slug(name) + "-" + role + "-highest-" + stat, name, move=move, role=role, a=a, d=d,
                astate=state if role == "attacker" else None, dstate=state if role == "defender" else None,
                **activation)
        for suffix, state, weather in (
            ("booster-consumed", {"transformed": 0, "boosterEnergyActivated": 1, "paradoxBoostedStat": 1}, {}),
            ("transformed", {"transformed": 1, "boosterEnergyActivated": 1, "paradoxBoostedStat": 1}, activation),
            ("stored-other", {"transformed": 0, "boosterEnergyActivated": 1, "paradoxBoostedStat": 2}, {}),
            ("inactive", {"transformed": 0, "boosterEnergyActivated": 0, "paradoxBoostedStat": 1}, {}),
            ("recompute-tie", {"transformed": 0, "boosterEnergyActivated": 1, "paradoxBoostedStat": 0}, {})):
            add(slug(name) + "-" + suffix, name, astate=state, **weather)
        add(slug(name) + "-wonder-room", name, role="defender", d=defender("Snorlax", dfn=109, spd=201),
            dstate={"transformed": 0, "boosterEnergyActivated": 1, "paradoxBoostedStat": 0},
            setup={"wonderRoom": True}, surface="engine-only")
    add("protosynthesis-umbrella-global-sun", "Protosynthesis", weather="sun",
        a=attacker("Machamp", atk=151, item=("ITEM_UTILITY_UMBRELLA", "Utility Umbrella")),
        astate={"transformed": 0, "boosterEnergyActivated": 0, "paradoxBoostedStat": 1})
    add("protosynthesis-cloud-nine", "Protosynthesis", weather="sun",
        d=defender("Snorlax", dfn=109, ability=ability("Cloud Nine")),
        astate={"transformed": 0, "boosterEnergyActivated": 0, "paradoxBoostedStat": 1})
    add("analytic-last", "Analytic", a=attacker("Machamp", atk=151, spe=20))
    add("analytic-not-last", "Analytic", a=attacker("Machamp", atk=151, spe=80))
    add("analytic-later-nonmove", "Analytic", setup={"laterAction": 13})
    for name, move in (("Dark Aura", "Dark Pulse"), ("Fairy Aura", "Moonblast")):
        add(slug(name) + "-matching", name, move=move)
        add(slug(name) + "-wrong-type", name)
        add(slug(name) + "-defender", name, role="defender", move=move)
        add(slug(name) + "-aura-break", name, move=move, d=defender("Snorlax", spd=109, ability=ability("Aura Break")))
    add("aura-break-alone", "Aura Break", move="Dark Pulse")
    for name, flag, role, move in (("Vessel of Ruin", "vesselOfRuin", "defender", "Psychic"),
                                   ("Tablets of Ruin", "tabletsOfRuin", "defender", "Strength"),
                                   ("Sword of Ruin", "swordOfRuin", "attacker", "Strength"),
                                   ("Beads of Ruin", "beadsOfRuin", "attacker", "Psychic")):
        for suffix, which, value, chosen in (("opponent", role, 1, move),
                ("self", "attacker" if role == "defender" else "defender", 1, move),
                ("inactive", role, 0, move), ("wrong-stat", role, 1, "Strength" if move == "Psychic" else "Psychic")):
            add(slug(name) + "-" + suffix, name, role=which, move=chosen,
                astate={flag: value} if which == "attacker" else None,
                dstate={flag: value} if which == "defender" else None)
        add(slug(name) + "-gastro-acid", name, role=role, move=move,
            astate={flag: 1, "gastroAcid": 1} if role == "attacker" else None,
            dstate={flag: 1, "gastroAcid": 1} if role == "defender" else None, surface="engine-only")
    for shield in (False, True):
        add("sword-of-ruin-gas" + ("-shield" if shield else ""), "Sword of Ruin",
            a=attacker("Machamp", atk=151, item=("ITEM_ABILITY_SHIELD", "Ability Shield") if shield else None),
            d=defender("Snorlax", dfn=109, ability=ability("Neutralizing Gas")),
            astate={"swordOfRuin": 1}, dstate={"neutralizingGas": 1}, surface="engine-only")
    for name, flag in (("Sword of Ruin", "swordOfRuin"), ("Beads of Ruin", "beadsOfRuin")):
        add(slug(name) + "-wonder-room", name, astate={flag: 1}, setup={"wonderRoom": True}, surface="engine-only")
    return out


def _group_e_charge() -> list[dict]:
    out = []
    for name in ("Wind Power", "Electromorphosis", "None"):
        for timer, move in ((0, "Thunder Shock"), (1, "Thunder Shock"), (2, "Thunder Punch"),
                            (2, "Thunder Shock"), (1, "Strength")):
            a = attacker("Machamp", atk=151, spa=151)
            if name != "None":
                a.update(ability=symbol("ABILITY", name), abilityLabel=name)
            out.append(scenario("group-e-charge-" + slug(name) + "-" + str(timer) + "-" + slug(move),
                ["group-e-charge", "shared-charge-timer"], a, defender("Snorlax", dfn=109, spd=109), move,
                state_setup={"capture": True, "attacker": {"chargeTimer": timer}}))
    return out


# Pinned moves_info.h planning labels; actual power/type/category are engine-observed.
RECOIL_MOVES = {
    "Take Down": ("Normal", "physical", 90), "Double-Edge": ("Normal", "physical", 120),
    "Submission": ("Fighting", "physical", 80), "Volt Tackle": ("Electric", "physical", 120),
    "Flare Blitz": ("Fire", "physical", 120), "Brave Bird": ("Flying", "physical", 120),
    "Wood Hammer": ("Grass", "physical", 120), "Head Smash": ("Rock", "physical", 150),
    "Wild Charge": ("Electric", "physical", 90), "Head Charge": ("Normal", "physical", 120),
    "Light of Ruin": ("Fairy", "special", 140), "Wave Crash": ("Water", "physical", 120),
}
MOVES.update(RECOIL_MOVES)


def _fixed_single_hit_recoil():
    out = []
    def case(name, move="Take Down", ability="Insomnia", item=None, **kw):
        a = attacker("Machamp", atk=151, spa=151, maxhp=60000,
                     ability=(symbol("ABILITY", ability), ability),
                     item=(symbol("ITEM", item), item) if item else None)
        out.append(scenario("recoil-"+name, ["move-coverage-slice-1", "fixed-single-hit-recoil"],
                            a, defender("Snorlax", dfn=109, spd=109), move, **kw))
    for move in RECOIL_MOVES:
        for side in ("player", "opponent"):
            case(slug(move)+"-"+side, move, side=side)
    for n in (109, 110, 111):
        a = attacker("Machamp", atk=n, maxhp=60000, ability=("ABILITY_RECKLESS", "Reckless"))
        out.append(scenario("recoil-reckless-round-"+str(n), ["move-coverage-slice-1"],
                            a, defender("Snorlax", dfn=107), "Take Down"))
    for ability in ("Rock Head", "Magic Guard", "Reckless", "Technician", "Tough Claws", "Long Reach"):
        case(slug(ability), ability=ability, surface="engine-only" if ability == "Long Reach" else "modelled")
    for move in ("Flare Blitz", "Volt Tackle", "Brave Bird"):
        case("sheer-force-"+slug(move),move,ability="Sheer Force")
    case("fluffy-contact", ability="Tough Claws")
    out[-1]["defender"].update(ability="ABILITY_FLUFFY", abilityLabel="Fluffy")
    case("reckless-crit",ability="Reckless",crit=True)
    case("reckless-physical-stage",ability="Reckless")
    out[-1]["attacker"]["stages"]["attack"]=1
    out[-1]["defender"]["stages"]["defense"]=-1
    case("light-of-ruin-type-based", "Light of Ruin", style="typeBased")
    case("normalize", "Wave Crash", ability="Normalize")
    case("pixilate", ability="Pixilate")
    case("reckless-life-orb-rain", "Wave Crash", ability="Reckless", item="Life Orb", weather="rain")
    case("magic-guard-life-orb",item="Life Orb",ability="Magic Guard")
    case("rock-head-life-orb",item="Life Orb",ability="Rock Head")
    case("sheer-force-life-orb", "Flare Blitz",ability="Sheer Force",item="Life Orb")
    case("reckless-suppressed", ability="Reckless", surface="engine-only", state_setup={"capture":True,"gastroAcidBeforeHit":"attacker","attacker":{"gastroAcid":1}})
    case("life-orb-suppressed",item="Life Orb",state_setup={"capture":True,"attacker":{"embargo":1}})
    case("burn", ability="Guts")
    out[-1]["attacker"]["status"]="burn"
    case("light-screen", "Light of Ruin", light_screen=True)
    case("reflect",reflect=True)
    return out


MOVES.update({"Absorb": ("Grass", "special", 20), "Mega Drain": ("Grass", "special", 40),
    "Leech Life": ("Bug", "physical", 80), "Giga Drain": ("Grass", "special", 75),
    "Drain Punch": ("Fighting", "physical", 75), "Horn Leech": ("Grass", "physical", 75),
    "Draining Kiss": ("Fairy", "special", 50)})

def _fixed_single_hit_drain():
    out = []
    def case(name, move="Drain Punch", ability="Insomnia", item=None, **kw):
        a = attacker("Machamp", atk=151, spa=151, maxhp=60000, hp=30000,
                     ability=(symbol("ABILITY", ability), ability),
                     item=(symbol("ITEM", item), item) if item else None)
        out.append(scenario("drain-"+name, ["move-coverage-slice-2", "fixed-single-hit-drain"],
                            a, defender("Snorlax", dfn=109, spd=109), move, **kw))
    for move in ("Absorb", "Mega Drain", "Leech Life", "Giga Drain", "Drain Punch", "Horn Leech", "Draining Kiss"):
        for side in ("player", "opponent"):
            case(slug(move)+"-"+side, move, side=side)
    for ability in ("Iron Fist", "Tough Claws", "Triage", "Long Reach"):
        case(slug(ability), ability=ability, surface="engine-only" if ability == "Long Reach" else "modelled")
    case("technician", "Absorb", ability="Technician")
    case("fluffy-contact", ability="Tough Claws")
    out[-1]["defender"].update(ability="ABILITY_FLUFFY", abilityLabel="Fluffy")
    case("fluffy-noncontact", "Giga Drain")
    out[-1]["defender"].update(ability="ABILITY_FLUFFY", abilityLabel="Fluffy")
    case("normalize", ability="Normalize")
    case("crit", crit=True)
    case("stages")
    out[-1]["attacker"]["stages"]["attack"] = 1
    out[-1]["defender"]["stages"]["defense"] = -1
    case("reflect", reflect=True)
    case("light-screen", "Giga Drain", light_screen=True)
    case("sun", "Giga Drain", weather="sun")
    case("life-orb", item="Life Orb")
    case("big-root", item="Big Root")
    case("big-root-kiss", "Draining Kiss", item="Big Root")
    case("liquid-ooze")
    out[-1]["defender"].update(ability="ABILITY_LIQUID_OOZE", abilityLabel="Liquid Ooze")
    case("big-root-liquid-ooze", item="Big Root")
    out[-1]["defender"].update(ability="ABILITY_LIQUID_OOZE", abilityLabel="Liquid Ooze")
    case("minimum", "Absorb")
    out[-1]["attacker"]["level"] = 1
    out[-1]["attacker"]["stats"].update(attack=1,spAttack=1)
    out[-1]["defender"]["stats"].update(defense=10000,spDefense=10000)
    case("big-root-suppressed", item="Big Root", state_setup={"capture":True,"attacker":{"embargo":1}})
    case("defender-triage")
    out[-1]["defender"].update(ability="ABILITY_TRIAGE", abilityLabel="Triage")
    case("liquid-ooze-suppressed", surface="engine-only", state_setup={"capture":True,"gastroAcidBeforeHit":"defender","defender":{"gastroAcid":1}})
    out[-1]["defender"].update(ability="ABILITY_LIQUID_OOZE", abilityLabel="Liquid Ooze")
    return out


def _fixed_single_hit_earthquake():
    out = []
    def case(name, move="Earthquake", ability="Insomnia", item=None, underground=False, **kw):
        a = attacker("Machamp", atk=151, spa=151, spe=40,
                     ability=(symbol("ABILITY", ability), ability),
                     item=(symbol("ITEM", item), item) if item else None)
        d = defender("Snorlax", dfn=109, spd=109, spe=100)
        out.append(scenario("earthquake-"+name, ["move-coverage-slice-3"], a, d, move,
                            state_setup={"capture":True, "underground":True} if underground else None, **kw))
    for move in ("Earthquake", "Bulldoze"):
        for side in ("player", "opponent"):
            case(slug(move)+"-"+side, move, side=side)
        case(slug(move)+"-grassy", move, terrain="grassy")
        case(slug(move)+"-sheer-force", move, ability="Sheer Force")
        case(slug(move)+"-flying-grassy", move, ability="Normalize", terrain="grassy")
        out[-1]["defender"].update(species="SPECIES_PIDGEOT",speciesLabel="Pidgeot")
    case("grassy-technician", ability="Technician", terrain="grassy")
    case("grassy-sheer-force", "Bulldoze", ability="Sheer Force", terrain="grassy")
    case("underground", underground=True)
    case("underground-opponent", underground=True, side="opponent")
    case("underground-grassy", underground=True, terrain="grassy")
    case("underground-reflect", underground=True, reflect=True)
    case("underground-life-orb", underground=True, item="Life Orb")
    case("underground-crit", underground=True, crit=True)
    case("underground-stages", underground=True)
    out[-1]["attacker"]["stages"]["attack"] = 1
    out[-1]["defender"]["stages"]["defense"] = -1
    case("underground-type", underground=True)
    out[-1]["defender"].update(species="SPECIES_ARCANINE",speciesLabel="Arcanine")
    case("underground-rounding", underground=True, item="Life Orb", reflect=True)
    out[-1]["attacker"]["stats"]["attack"] = 153
    out[-1]["defender"].update(ability="ABILITY_FILTER",abilityLabel="Filter",species="SPECIES_ARCANINE",speciesLabel="Arcanine")
    for ability in ("Levitate", "Earth Eater"):
        case(slug(ability), expect="immune")
        out[-1]["defender"].update(ability=symbol("ABILITY",ability),abilityLabel=ability)
    case("flying", expect="immune")
    out[-1]["defender"].update(species="SPECIES_PIDGEOT",speciesLabel="Pidgeot")
    case("air-balloon", expect="immune")
    out[-1]["defender"].update(item="ITEM_AIR_BALLOON",itemLabel="Air Balloon")
    case("iron-ball-flying")
    out[-1]["defender"].update(species="SPECIES_PIDGEOT",speciesLabel="Pidgeot",item="ITEM_IRON_BALL",itemLabel="Iron Ball")
    case("gravity-neutral", gravity=True, surface="engine-only")
    return out


def _fixed_single_hit_explosion():
    out = []
    def case(name, move="Explosion", ability="Insomnia", item=None, **kw):
        a = attacker("Machamp", atk=151, spa=151, spe=100, hp=200, maxhp=200,
                     ability=(symbol("ABILITY", ability), ability),
                     item=(symbol("ITEM", item), item) if item else None)
        out.append(scenario("explosion-"+name, ["move-coverage-slice-4"], a,
            defender("Snorlax", dfn=109, spd=109, spe=40), move, **kw))
    for move in ("Self-Destruct", "Explosion"):
        for side in ("player", "opponent"):
            case(slug(move)+"-"+side, move, side=side)
    case("high-defense")
    out[-1]["defender"]["stats"]["defense"] = 503
    case("defense-stage")
    out[-1]["defender"]["stages"]["defense"] = 2
    case("reflect", reflect=True)
    case("wonder-room", surface="engine-only", state_setup={"capture":True,"wonderRoom":True})
    case("fur-coat")
    out[-1]["defender"].update(ability="ABILITY_FUR_COAT",abilityLabel="Fur Coat")
    for ability in ("Defeatist", "Parental Bond", "Normalize", "Pixilate", "Aerilate", "Refrigerate", "Galvanize"):
        case(slug(ability), ability=ability)
    case("life-orb", item="Life Orb")
    case("crit", crit=True)
    case("attack-stages")
    out[-1]["attacker"]["stages"]["attack"] = 2
    case("rounding", item="Life Orb", reflect=True)
    out[-1]["attacker"]["stats"]["attack"] = 153
    case("ghost", expect="immune")
    out[-1]["defender"].update(species="SPECIES_BANETTE",speciesLabel="Banette")
    case("pixilate-ghost", ability="Pixilate")
    out[-1]["defender"].update(species="SPECIES_BANETTE",speciesLabel="Banette")
    case("mold-breaker-damp", ability="Mold Breaker")
    out[-1]["defender"].update(ability="ABILITY_DAMP",abilityLabel="Damp")
    case("gastro-damp", surface="engine-only", state_setup={"capture":True,"gastroAcidBeforeHit":"defender","defender":{"gastroAcid":1}})
    out[-1]["defender"].update(ability="ABILITY_DAMP",abilityLabel="Damp")
    return out


def _fixed_single_hit_status_double():
    out = []
    def case(name, move, raw=0, ability="Insomnia", defender_ability="Run Away", item=None, **kw):
        a = attacker("Machamp", atk=151, spa=151, spe=40, hp=200, maxhp=200,
            ability=(symbol("ABILITY", ability), ability), item=(symbol("ITEM", item), item) if item else None)
        d = defender("Blastoise", dfn=109, spd=109, spe=100,
            ability=(symbol("ABILITY", defender_ability), defender_ability))
        out.append(scenario("status-double-"+slug(move)+"-"+name, ["move-coverage-slice-6"], a, d, move,
            state_setup={"capture":True, "statusDoubleStatus1":raw}, **kw))
    moves = (("Smelling Salts",64),("Wake-Up Slap",3),("Venoshock",8),("Hex",16),("Barb Barrage",8),("Infernal Parade",16))
    for move, matching in moves:
        for side in ("player", "opponent"):
            case("neutral-"+side,move,side=side)
            case("matching-"+side,move,matching,side=side)
        for label, word in (("sleep",3),("poison",8),("toxic",0x380),("burn",16),("freeze",32),("paralysis",64),("frostbite",4096)):
            case(label,move,word)
        case("comatose",move,defender_ability="Comatose")
        for ability in ("Technician","Sheer Force"):
            case(slug(ability)+"-neutral",move,ability=ability)
            case(slug(ability)+"-matching",move,matching,ability=ability)
        case("screen",move,matching,reflect=True,light_screen=True)
        case("crit",move,matching,crit=True)
        case("life-orb",move,matching,item="Life Orb")
        case("normalize",move,matching,ability="Normalize")
        case("rounding",move,matching,item="Life Orb",light_screen=True)
        out[-1]["attacker"]["stages"].update(attack=1,spAttack=1)
        out[-1]["defender"]["stages"].update(defense=1,spDefense=1)
    return out


def _fixed_single_hit_underwater():
    out = []
    def case(name, move="Surf", ability="Insomnia", item=None, underwater=False, defender_ability="Insomnia", **kw):
        a = attacker("Machamp", atk=151, spa=151, spe=40, hp=200, maxhp=200,
                     ability=(symbol("ABILITY", ability), ability),
                     item=(symbol("ITEM", item), item) if item else None)
        d = defender("Snorlax", dfn=109, spd=109, spe=100,
                     ability=(symbol("ABILITY", defender_ability), defender_ability))
        out.append(scenario("underwater-"+name, ["move-coverage-slice-5"], a, d, move,
            state_setup={"capture":True, "underwater":True} if underwater else None, **kw))
    for move in ("Surf", "Whirlpool"):
        for side in ("player", "opponent"):
            case(slug(move)+"-"+side, move, side=side)
            case(slug(move)+"-dive-"+side, move, underwater=True, side=side)
        case(slug(move)+"-sheer-force", move, ability="Sheer Force")
        for name, kwargs in (("light-screen", {"light_screen":True}), ("rain", {"weather":"rain"}),
                ("sun", {"weather":"sun"}), ("life-orb", {"item":"Life Orb"}), ("crit", {"crit":True}),
                ("water-bubble", {"ability":"Water Bubble"}), ("mystic-water", {"item":"Mystic Water"}),
                ("rain-umbrella", {"weather":"rain", "item":"Utility Umbrella"})):
            case(slug(move)+"-dive-"+name, move, underwater=True, **kwargs)
        case(slug(move)+"-dive-stages", move, underwater=True)
        out[-1]["attacker"]["stages"]["spAttack"] = 1
        out[-1]["defender"]["stages"]["spDefense"] = -1
        case(slug(move)+"-dive-crit-stages", move, underwater=True, crit=True)
        out[-1]["attacker"]["stages"]["spAttack"] = -1
        out[-1]["defender"]["stages"]["spDefense"] = 1
        case(slug(move)+"-dive-rounding", move, underwater=True, item="Life Orb", light_screen=True, defender_ability="Filter")
        out[-1]["attacker"]["stats"]["spAttack"] = 153
        out[-1]["defender"].update(species="SPECIES_ARCANINE", speciesLabel="Arcanine")
        for ability in ("Water Absorb", "Dry Skin", "Storm Drain"):
            for dive in (False, True):
                case(slug(move)+"-"+slug(ability)+("-dive" if dive else ""), move,
                     underwater=dive, defender_ability=ability, expect="immune")
        case(slug(move)+"-mold-breaker-dive", move, underwater=True, ability="Mold Breaker", defender_ability="Water Absorb")
        case(slug(move)+"-shield-dive", move, underwater=True, ability="Mold Breaker", defender_ability="Water Absorb", expect="immune")
        out[-1]["defender"].update(item="ITEM_ABILITY_SHIELD",itemLabel="Ability Shield")
    for item in ("Binding Band", "Grip Claw"):
        case("whirlpool-"+slug(item), "Whirlpool", item=item)
        case("whirlpool-"+slug(item)+"-dive", "Whirlpool", item=item, underwater=True)
    case("whirlpool-magic-guard", "Whirlpool", defender_ability="Magic Guard")
    case("whirlpool-magic-guard-dive", "Whirlpool", defender_ability="Magic Guard", underwater=True)
    case("surf-dive-grassy", underwater=True, terrain="grassy")
    case("surf-gastro-water-absorb", defender_ability="Water Absorb", surface="engine-only")
    out[-1]["stateSetup"]={"capture":True,"gastroAcidBeforeHit":"defender","defender":{"gastroAcid":1}}
    return out


def _fixed_single_hit_brine():
    out = []
    def case(name, hp=30000, maxhp=60000, ability="Insomnia", defender_ability="Insomnia", item=None, **kw):
        a = attacker("Machamp", atk=151, spa=151, spe=40, hp=200, maxhp=200,
            ability=(symbol("ABILITY", ability), ability), item=(symbol("ITEM", item), item) if item else None)
        d = defender("Blastoise", dfn=109, spd=109, spe=100,
            ability=(symbol("ABILITY", defender_ability), defender_ability))
        d["stats"].update(hp=hp, maxHp=maxhp)
        out.append(scenario("brine-"+name, ["move-coverage-slice-7"], a, d, "Brine", **kw))
    for side in ("player", "opponent"):
        for name,hp,maxhp in (("full",100,100),("above",51,100),("half",50,100),("below",49,100),
                ("odd-above",51,101),("odd-half",50,101)):
            case(name+"-"+side,hp,maxhp,side=side)
    for threshold,hp in (("full",60000),("half",30000)):
        for name,kw in (("rain",dict(weather="rain")),("sun",dict(weather="sun")),
            ("rain-umbrella",dict(weather="rain",item="Utility Umbrella")),
            ("water-bubble",dict(ability="Water Bubble")),("mystic-water",dict(item="Mystic Water")),
            ("splash-plate",dict(item="Splash Plate")),("screen",dict(light_screen=True)),
            ("life-orb",dict(item="Life Orb")),("crit",dict(crit=True)),
            ("normalize",dict(ability="Normalize")),("technician",dict(ability="Technician")),
            ("sheer-force",dict(ability="Sheer Force"))):
            case(threshold+"-"+name,hp,**kw)
        for ability in ("Multiscale","Shadow Shield"):
            case(threshold+"-"+slug(ability),hp,defender_ability=ability)
        case(threshold+"-stages",hp)
        out[-1]["attacker"]["stages"]["spAttack"]=1
        out[-1]["defender"]["stages"]["spDefense"]=-1
    for ability in ("Water Absorb","Dry Skin","Storm Drain"):
        case(slug(ability),defender_ability=ability,expect="immune")
    case("mold-breaker",ability="Mold Breaker",defender_ability="Water Absorb",surface="engine-only")
    case("ability-shield",ability="Mold Breaker",defender_ability="Water Absorb",expect="immune")
    out[-1]["defender"].update(item="ITEM_ABILITY_SHIELD",itemLabel="Ability Shield")
    case("gastro-acid",defender_ability="Water Absorb",surface="engine-only")
    out[-1]["stateSetup"]={"capture":True,"gastroAcidBeforeHit":"defender","defender":{"gastroAcid":1}}
    for ability in ("Torrent","Adaptability"):
        case(slug(ability),ability=ability)
        out[-1]["attacker"].update(species="SPECIES_BLASTOISE",speciesLabel="Blastoise")
        if ability=="Torrent": out[-1]["attacker"]["stats"]["hp"]=66
    case("rounding",ability="Normalize",item="Life Orb",light_screen=True)
    out[-1]["attacker"]["stats"]["spAttack"]=153
    out[-1]["defender"]["stages"]["spDefense"]=1
    return out



def _gyro_ball():
    out = []
    def case(name, a_speed=100, d_speed=100, ability="Insomnia", defender_ability="Insomnia", item=None, defender_item=None, a_species="Machamp", setup=None, surface="modelled", expect="damage", **kw):
        a = attacker(a_species, atk=151, spa=151, spe=a_speed, ability=(symbol("ABILITY",ability),ability), item=(symbol("ITEM",item),item) if item else None)
        d = defender("Blastoise", dfn=109, spd=109, spe=d_speed, ability=(symbol("ABILITY",defender_ability),defender_ability), item=(symbol("ITEM",defender_item),defender_item) if defender_item else None)
        out.append(scenario("gyro-ball-"+name, ["move-coverage-slice-8"], a, d, "Gyro Ball", surface=surface, expect=expect, **kw))
        out[-1]["stateSetup"] = setup or {"capture":True}
    for name,a,d in (("equal",100,100),("slow",40,100),("fast",100,40),("fractional",103,237),
            ("cap",100,596),("above-cap",40,500),("bp60",100,236),("bp61",100,240)):
        case(name,a,d)
    for power,speed in ((60,236),(61,240)):
        case("technician-"+str(power),100,speed,ability="Technician")
    for role in ("attacker","defender"):
        for stage in (-1,1):
            case(role+"-stage-"+("minus" if stage < 0 else "plus"),setup={"gyroSpeed":{role:{"stage":stage}}})
        case(role+"-zero",1 if role=="attacker" else 100,1 if role=="defender" else 100,
             setup={"gyroSpeed":{role:{"stage":-6}}})
    for ability,weather in (("Swift Swim","rain"),("Chlorophyll","sun")):
        case(slug(ability),ability=ability,weather=weather)
        case(slug(ability)+"-umbrella",ability=ability,weather=weather,item="Utility Umbrella")
        for suppressor in ("Cloud Nine","Air Lock"):
            case(slug(ability)+"-"+slug(suppressor),ability=ability,weather=weather,defender_ability=suppressor)
    for active in (0,1):
        case("slow-start-"+str(active),ability="Slow Start",setup={"attacker":{"slowStartTimer":active}})
    case("surge-surfer",ability="Surge Surfer",terrain="electric")
    for ability,kw in (("Protosynthesis",{"weather":"sun"}),("Quark Drive",{"terrain":"electric"})):
        case(slug(ability),ability=ability,a_speed=300,setup={"attacker":{"paradoxBoostedStat":3}},**kw)
    for role in ("attacker","defender"):
        for item in ("Iron Ball","Choice Scarf","Macho Brace","Power Anklet"):
            case(role+"-"+slug(item),item=item if role=="attacker" else None,defender_item=item if role=="defender" else None)
        for name,mask in (("tailwind",0x10),("swamp",0x400),("tailwind-swamp",0x410)):
            case(role+"-"+name,setup={"gyroSpeed":{role:{"sideStatuses":mask}}})
    case("quick-powder-ditto",item="Quick Powder",a_species="Ditto")
    case("tailwind-before-swamp-rounding",a_speed=103,
         setup={"gyroSpeed":{"attacker":{"sideStatuses":0x410}}})
    case("quick-powder-non-ditto",item="Quick Powder")
    case("quick-powder-transformed",item="Quick Powder",a_species="Ditto",setup={"attacker":{"transformed":1}})
    case("trick-room",setup={"gyroSpeed":{"trickRoom":True}})
    for item in ("Quick Claw","Lagging Tail","Full Incense"):
        case(slug(item),item=item)
    for ability in ("Stall","Quick Draw"):
        case(slug(ability),ability=ability)
    for ability in ("Tough Claws","Steelworker","Steely Spirit","Long Reach"):
        case(slug(ability),ability=ability,surface="engine-only" if ability=="Long Reach" else "modelled")
    case("fluffy",defender_ability="Fluffy")
    case("bulletproof",defender_ability="Bulletproof",expect="immune")
    case("mold-breaker",ability="Mold Breaker",defender_ability="Bulletproof",surface="engine-only")
    case("ability-shield",ability="Mold Breaker",defender_ability="Bulletproof",defender_item="Ability Shield",expect="immune")
    case("reflect",reflect=True)
    for item in ("Metal Coat","Life Orb"):
        case(slug(item),item=item)
    case("crit",crit=True)
    case("stages",setup={"attackerStatStages":{"attack":1},"defenderStatStages":{"defense":-1}})
    out[-1]["attacker"]["stages"]["attack"] = 1
    out[-1]["defender"]["stages"]["defense"] = -1
    case("unburden-active",ability="Unburden",surface="engine-only",setup={"gyroSpeed":{"attacker":{"unburdenActive":True}}})
    case("paralysis",surface="engine-only")
    out[-1]["stateSetup"] = {"gyroSpeed":{"attacker":{"status1":64}}}
    case("quick-feet-paralysis",ability="Quick Feet",surface="engine-only")
    out[-1]["stateSetup"] = {"gyroSpeed":{"attacker":{"status1":64}}}
    case("quick-claw-proc",item="Quick Claw",setup={"gyroSpeed":{"quickClawProc":True}})
    case("quick-claw-no-proc",item="Quick Claw",setup={"gyroSpeed":{"quickClawProc":False}})
    case("choice-scarf-magic-room",item="Choice Scarf",setup={"magicRoom":True})
    case("choice-scarf-embargo",item="Choice Scarf",setup={"attacker":{"embargo":1}})
    case("choice-scarf-klutz",item="Choice Scarf",ability="Klutz",surface="engine-only")
    for side in ("player","opponent"):
        case("badge-"+side,badges=(3,),side=side)
    return out

def _electro_ball():
    out = []
    def case(name, a_speed=100, d_speed=100, ability="Insomnia", defender_ability="Insomnia", item=None, defender_item=None, a_species="Machamp", setup=None, surface="modelled", expect="damage", **kw):
        a = attacker(a_species, atk=151, spa=151, spe=a_speed, ability=(symbol("ABILITY",ability),ability), item=(symbol("ITEM",item),item) if item else None)
        d = defender("Blastoise", dfn=109, spd=109, spe=d_speed, ability=(symbol("ABILITY",defender_ability),defender_ability), item=(symbol("ITEM",defender_item),defender_item) if defender_item else None)
        out.append(scenario("electro-ball-"+name, ["move-coverage-slice-9"], a, d, "Electro Ball", surface=surface, expect=expect, **kw))
        out[-1]["stateSetup"] = setup or {"capture":True}
    for a in (99,100,199,200,299,300,399,400,500):
        case("ratio-"+str(a),a)
    case("attacker-zero",1,setup={"gyroSpeed":{"attacker":{"stage":-6}}})
    for bp,a in ((40,99),(60,100),(80,200)):
        case("technician-"+str(bp),a,ability="Technician")
    for role in ("attacker","defender"):
        for item in ("Choice Scarf","Iron Ball"):
            case(role+"-"+slug(item),item=item if role=="attacker" else None,defender_item=item if role=="defender" else None)
        case(role+"-tailwind",setup={"gyroSpeed":{role:{"sideStatuses":0x10}}})
    case("power-anklet",item="Power Anklet")
    case("quick-powder-ditto",item="Quick Powder",a_species="Ditto")
    for ability,weather in (("Swift Swim","rain"),("Chlorophyll","sun")):
        case(slug(ability),ability=ability,weather=weather)
        case(slug(ability)+"-umbrella",ability=ability,weather=weather,item="Utility Umbrella")
        case(slug(ability)+"-cloud-nine",ability=ability,weather=weather,defender_ability="Cloud Nine")
    case("slow-start",ability="Slow Start",setup={"attacker":{"slowStartTimer":1}})
    case("surge-surfer-grounded",ability="Surge Surfer",terrain="electric")
    case("surge-surfer-ungrounded",ability="Surge Surfer",terrain="electric",item="Air Balloon")
    case("terrain-control",terrain="electric")
    case("quark-drive-speed",ability="Quark Drive",terrain="electric",a_speed=300,setup={"attacker":{"paradoxBoostedStat":3}})
    case("hadron-engine",ability="Hadron Engine",terrain="electric")
    case("protosynthesis-speed",ability="Protosynthesis",weather="sun",a_speed=300,setup={"attacker":{"paradoxBoostedStat":3}})
    case("charge",setup={"attacker":{"chargeTimer":1}})
    case("charge-terrain-technician",ability="Technician",terrain="electric",setup={"attacker":{"chargeTimer":1}})
    case("light-screen",light_screen=True)
    for item in ("Magnet","Life Orb"):
        case(slug(item),item=item)
    case("crit",crit=True)
    case("stages",setup={"attackerStatStages":{"spAttack":1},"defenderStatStages":{"spDefense":-1}})
    out[-1]["attacker"]["stages"]["spAttack"] = 1
    out[-1]["defender"]["stages"]["spDefense"] = -1
    for ability in ("Bulletproof","Volt Absorb","Motor Drive","Lightning Rod"):
        case(slug(ability),a_speed=500,defender_ability=ability,expect="immune")
    case("mold-breaker",ability="Mold Breaker",defender_ability="Volt Absorb",surface="engine-only")
    case("ability-shield",ability="Mold Breaker",defender_ability="Volt Absorb",defender_item="Ability Shield",expect="immune")
    case("sheer-force",ability="Sheer Force")
    case("normalize",ability="Normalize")
    case("trick-room",setup={"gyroSpeed":{"trickRoom":True}})
    case("quick-claw",item="Quick Claw")
    case("unburden",ability="Unburden",surface="engine-only",setup={"gyroSpeed":{"attacker":{"unburdenActive":True}}})
    case("paralysis",surface="engine-only",setup={"gyroSpeed":{"attacker":{"status1":64}}})
    return out


def _rollout():
    out = []
    def case(move, name, timer=0, curl=0, ability="Insomnia", defender_ability="Insomnia", item=None, defender_item=None, species="Machamp", surface="modelled", expect="damage", setup=None, **kw):
        a = attacker(species, atk=151, spa=151, ability=(symbol("ABILITY",ability),ability), item=(symbol("ITEM",item),item) if item else None)
        d = defender("Blastoise", dfn=109, spd=109, ability=(symbol("ABILITY",defender_ability),defender_ability), item=(symbol("ITEM",defender_item),defender_item) if defender_item else None)
        out.append(scenario("rollout-"+slug(move)+"-"+name, ["move-coverage-slice-10"], a, d, move, surface=surface, expect=expect, **kw))
        out[-1]["stateSetup"] = {"rollout":{"timer":timer,"defenseCurl":curl,"electrify":0}, **(setup or {})}
    for move in ("Rollout","Ice Ball"):
        for curl in (0,1):
            for timer in range(5):
                case(move, f"counter-{timer}-curl-{curl}",timer,curl)
                if timer < 3:
                    case(move, f"technician-{timer}-curl-{curl}",timer,curl,ability="Technician")
        for ability in ("Tough Claws","Long Reach","Sheer Force","Normalize"):
            case(move,slug(ability),timer=2,ability=ability)
        for ability in ("Fluffy","Fur Coat","Multiscale","Solid Rock","Bulletproof"):
            case(move,slug(ability),timer=1,defender_ability=ability,expect="immune" if ability=="Bulletproof" and move=="Ice Ball" else "damage")
        case(move,"adaptability-stab",timer=2,ability="Adaptability",species="Glaceon" if move=="Ice Ball" else "Tyranitar")
        case(move,"stab",timer=2,species="Glaceon" if move=="Ice Ball" else "Tyranitar")
        case(move,"type-item",timer=1,item="Never-Melt Ice" if move=="Ice Ball" else "Hard Stone")
        case(move,"life-orb",timer=3,curl=1,item="Life Orb")
        case(move,"reflect",timer=1,reflect=True)
        case(move,"critical-reflect",timer=1,reflect=True,crit=True)
        case(move,"rounding",timer=1,curl=1,ability="Tough Claws",item="Life Orb",reflect=True,
             setup={"attackerStatStages":{"attack":1},"defenderStatStages":{"defense":1}})
        out[-1]["attacker"]["stats"]["attack"]=153
        out[-1]["attacker"]["stages"]["attack"]=1
        out[-1]["defender"]["stages"]["defense"]=1
        case(move,"normalize-charge",timer=1,ability="Normalize",terrain="electric",setup={"attacker":{"chargeTimer":1}})
        case(move,"electrify-charge-terrain",timer=1,ability="Technician",terrain="electric",surface="engine-only",setup={"attacker":{"chargeTimer":1}})
        out[-1]["stateSetup"]["rollout"]["electrify"]=1
        out[-1]["attacker"]["stats"]["speed"]=40
        out[-1]["defender"]["stats"]["speed"]=100
        case(move,"mold-breaker",timer=1,ability="Mold Breaker",defender_ability="Bulletproof" if move=="Ice Ball" else "Fluffy",surface="engine-only")
        case(move,"ability-shield",timer=1,ability="Mold Breaker",defender_ability="Bulletproof",defender_item="Ability Shield",expect="immune" if move=="Ice Ball" else "damage")
    return out


def _hit_escape():
    out = []
    def case(move, name, ability="Insomnia", defender_ability="Insomnia", item=None, species="Machamp", setup=None, expect="damage", **kw):
        a = attacker(species, atk=151, spa=151, ability=(symbol("ABILITY",ability),ability), item=(symbol("ITEM",item),item) if item else None)
        d = defender("Snorlax", dfn=109, spd=109, ability=(symbol("ABILITY",defender_ability),defender_ability))
        out.append(scenario("hit-escape-"+slug(move)+"-"+name, ["move-coverage-slice-11"], a, d, move, expect=expect, **kw))
        out[-1]["stateSetup"] = setup or {"capture":True}
    for move in ("U-Turn","Volt Switch","Flip Turn"):
        case(move,"neutral")
        case(move,"opponent",side="opponent")
        for ability in ("Technician","Tough Claws","Long Reach","Sheer Force","Normalize"):
            case(move,slug(ability),ability=ability)
        case(move,"fluffy",defender_ability="Fluffy")
        case(move,"long-reach-fluffy",ability="Long Reach",defender_ability="Fluffy")
        case(move,"stab",species={"U-Turn":"Heracross","Volt Switch":"Pikachu","Flip Turn":"Blastoise"}[move])
        case(move,"adaptability",ability="Adaptability",species={"U-Turn":"Heracross","Volt Switch":"Pikachu","Flip Turn":"Blastoise"}[move])
        case(move,"screen",**({"light_screen":True} if move=="Volt Switch" else {"reflect":True}))
        case(move,"crit-screen",crit=True,**({"light_screen":True} if move=="Volt Switch" else {"reflect":True}))
        case(move,"life-orb",item="Life Orb")
        case(move,"type-item",item={"U-Turn":"Silver Powder","Volt Switch":"Magnet","Flip Turn":"Mystic Water"}[move])
        stat,defstat=("spAttack","spDefense") if move=="Volt Switch" else ("attack","defense")
        case(move,"rounding",ability="Tough Claws",item="Life Orb",setup={"attackerStatStages":{stat:1},"defenderStatStages":{defstat:1}},**({"light_screen":True} if move=="Volt Switch" else {"reflect":True}))
        out[-1]["attacker"]["stats"][stat]=153
        out[-1]["attacker"]["stages"][stat]=1
        out[-1]["defender"]["stages"][defstat]=1
    case("Volt Switch","charge",setup={"attacker":{"chargeTimer":1}})
    case("Volt Switch","electric-terrain",terrain="electric")
    case("Volt Switch","charge-terrain",terrain="electric",setup={"attacker":{"chargeTimer":1}})
    for ability in ("Volt Absorb","Motor Drive","Lightning Rod"):
        case("Volt Switch",slug(ability),defender_ability=ability,expect="immune")
    case("Flip Turn","rain",weather="rain")
    case("Flip Turn","sun",weather="sun")
    case("Flip Turn","water-absorb",defender_ability="Water Absorb",expect="immune")
    return out


def build_scenarios() -> list[dict]:
    """The complete, deterministic scenario list (sorted by ID)."""
    groups = (_xref, _chart_mono, _chart_dual, _arithmetic, _min_damage, _crit, _stages, _burn,
              _weather, _screens, _pinch, _group_c_immunities, _wise_glasses, _badges, _attack_stat_abilities,
              _base_power_abilities, _low_state_stat_abilities, _rules, _final_modifiers_and_stab, _engine_abilities,
              _engine_items, _doubles, _authoritative_doubles, _mixed_rounding_attack_stat_abilities, _field_backed_stat_abilities,
              _terrain_move_modifiers, _remaining_group_d, _state_backed_group_d, _group_d_held_items, _group_e_charge, _fixed_single_hit_recoil, _fixed_single_hit_drain, _fixed_single_hit_earthquake, _fixed_single_hit_explosion, _fixed_single_hit_underwater, _fixed_single_hit_status_double, _fixed_single_hit_brine, _gyro_ball, _electro_ball, _rollout, _hit_escape)
    scenarios = [s for group in groups for s in group()]
    return sorted(scenarios, key=lambda s: s["id"])
