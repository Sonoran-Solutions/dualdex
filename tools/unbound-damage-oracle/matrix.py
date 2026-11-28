"""Numeric synthetic operands; never calculator-derived expected damage."""

FIELDS = (
    "level", "power", "move_type", "category", "attack", "sp_attack", "defense", "sp_defense",
    "attacker_type_1", "attacker_type_2", "defender_type_1", "defender_type_2",
    "attack_stage", "sp_attack_stage", "defense_stage", "sp_defense_stage",
    "attacker_status", "weather", "screens", "critical", "attacker_ability", "defender_ability",
    "attacker_hold_effect", "defender_hold_effect",
)
TYPES = (*range(9), *range(10, 18), 23)
ATTACKER_ABILITIES = (0, 37, 55, 62, 82, 85, 88, 99)
DEFENDER_ABILITIES = (0, 47, 97, 148, 248)
ATTACKER_ITEMS = (0, 74, 92, 112, 119)
DEFENDER_ITEMS = (0, 70)


def cases():
    base = dict(zip(FIELDS, (
        50, 80, 0, 0, 123, 137, 101, 119, 0, 0, 0, 0,
        6, 6, 6, 6, 0, 0, 0, 0, 0, 0, 0, 0,
    )))
    result = []

    def add(**changes):
        operands = base | changes
        if operands not in result:
            result.append(operands)

    # Category crossovers, weather, category-specific screens, burn and crit compositions.
    for category in (0, 1):
        for move_type in (0, 10, 11, 23):
            for weather in (0, 1, 32):
                add(category=category, move_type=move_type, weather=weather)
        for screens in (1, 2, 3):
            for critical in (0, 1):
                for status in (0, 16):
                    add(category=category, screens=screens, critical=critical, attacker_status=status)
        # Every stage endpoint, with both ignored and retained critical stages.
        for stage in range(13):
            for critical in (0, 1):
                stage_fields = ("attack_stage", "defense_stage") if category == 0 else ("sp_attack_stage", "sp_defense_stage")
                add(category=category, critical=critical, **{stage_fields[0]: stage})
                add(category=category, critical=critical, **{stage_fields[1]: stage})

    # The full 18x18 mono chart, alternating categories; independent dual-type order controls.
    for index, attack_type in enumerate(TYPES):
        for defense_type in TYPES:
            add(move_type=attack_type, category=index % 2,
                attacker_type_1=attack_type, attacker_type_2=attack_type,
                defender_type_1=defense_type, defender_type_2=defense_type)
    for move_type, first, second in ((23, 1, 16), (10, 12, 8), (11, 10, 4),
                                    (0, 5, 8), (7, 8, 17), (16, 23, 8), (23, 10, 3)):
        for a, b in ((first, second), (second, first)):
            add(move_type=move_type, defender_type_1=a, defender_type_2=b, power=79)

    # STAB from either slot, duplicate slots (one boost), and absent STAB.
    for category in (0, 1):
        for first, second in ((0, 23), (23, 0), (23, 23), (0, 0)):
            add(category=category, move_type=23, attacker_type_1=first, attacker_type_2=second)

    for ability in ATTACKER_ABILITIES[1:]:
        for category in (0, 1):
            for critical in (0, 1):
                add(attacker_ability=ability, category=category, critical=critical, attacker_status=16,
                    screens=3, power=59, defender_type_1=5, defender_type_2=5)
        if ability == 82:  # Technician threshold.
            for power in (59, 60, 61):
                add(attacker_ability=ability, power=power)
    for ability in DEFENDER_ABILITIES[1:]:
        for category in (0, 1):
            for move_type in (0, 10, 15):
                add(defender_ability=ability, category=category, move_type=move_type)
    for effect in ATTACKER_ITEMS[1:]:
        for category in (0, 1):
            for power in (59, 79, 81):
                add(attacker_hold_effect=effect, category=category, power=power,
                    move_type=10, defender_type_1=12, defender_type_2=8)
    for category in (0, 1):
        add(defender_hold_effect=70, category=category)
        add(category=category, attacker_ability=82, attacker_hold_effect=74, defender_hold_effect=70,
            move_type=11, weather=1, screens=3, critical=1, attacker_status=16,
            attack_stage=3, sp_attack_stage=9, defense_stage=9, sp_defense_stage=3)
    # Rounding / minimum-damage controls. Small enough to avoid engine u32 overflow.
    for level, power, attack, defense in ((1, 1, 1, 999), (5, 15, 17, 31),
                                          (37, 59, 101, 137), (100, 150, 333, 277)):
        for category in (0, 1):
            add(level=level, power=power, attack=attack, sp_attack=attack,
                defense=defense, sp_defense=defense, category=category,
                screens=3, attacker_status=16, defender_type_1=5, defender_type_2=8)
    return [{"id": i + 1, "input": operands} for i, operands in enumerate(result)]
