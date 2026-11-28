/* Authored adapter; no engine implementation is distributed here. */
#include <stdio.h>
#include <stdlib.h>
#include <assert.h>
#include "src/defines.h"
#include "src/defines_battle.h"
#include "include/new/move_tables.h"

/* ponytail: synthetic move/effect operands prove source arithmetic only.
 * Upgrade to verified shipping table records after exact-ROM correspondence. */
static struct BattleMove host_moves[2];
#define gBattleMoves host_moves
#undef gTerrainType
static u8 host_terrain;
#define gTerrainType host_terrain
#undef gBitTable
static const u32 host_bits[4] = {1,2,4,8};
#define gBitTable host_bits
#include "src/damage_calc.c"

#define STATE(name) __typeof__(name) name
STATE(gBankAttacker);
STATE(gBankTarget);
STATE(gBattleMons);
STATE(gBattleMoveDamage);
STATE(gBattleMovePower);
STATE(gBattleScripting);
STATE(gBattleTypeFlags);
STATE(gBattleWeather);
STATE(gBattlersCount);
STATE(gBattlescriptCurrInstr);
STATE(gChosenMovesByBanks);
STATE(gCritMultiplier);
STATE(gCurrentMove);
STATE(gDisableStructs);
STATE(gDynamicBasePower);
STATE(gEnemyParty);
STATE(gLastHitByType);
STATE(gLastLandedMoves);
STATE(gLastPrintedMoves);
STATE(gLastUsedAbility);
STATE(gMain);
STATE(gMoveResultFlags);
STATE(gMultiHitCounter);
STATE(gPlayerParty);
STATE(gProtectStructs);
STATE(gSideStatuses);
STATE(gSideTimers);
STATE(gStatuses3);
STATE(gAbsentBattlerFlags);
u8 gBattleCommunication[8];
static struct NewBattleStruct new_battle;
struct NewBattleStruct *gNewBS = &new_battle;
static struct BattleStruct battle;
struct BattleStruct *gBattleStruct = &battle;
static struct BattleResources resources;
struct BattleResources *gBattleResources = &resources;
const struct SpecialMoveFlags gSpecialMoveFlags[2] = {0};
const struct NaturalGiftStruct gNaturalGiftTable[1] = {0};
const struct FlingStruct gFlingTable[1] = {0};
struct AlternateSize gAlternateSpeciesSizeTable[1] = {{.species = SPECIES_TABLES_TERMIN}};
const u8 gDynamaxMovePowers[1] = {0};
/* Standard stage ratios, independent of any ROM table. */
const u8 gStatStageRatios[13][2] = {
    {2,8},{2,7},{2,6},{2,5},{2,4},{2,3},{2,2},
    {3,2},{4,2},{5,2},{6,2},{7,2},{8,2}
};
static u8 effects[2], qualities[2];
static u16 random_operand;
u32 udivsi(u32 a, u32 b) { assert(b); return a / b; }
u32 MathMax(u32 a, u32 b) { return a > b ? a : b; }
u32 MathMin(u32 a, u32 b) { return a < b ? a : b; }
u16 Random(void) { return random_operand; }
u8 GetBattlerSide(u8 bank) { assert(bank < 2); return bank; }
u8 GetBaseMoveTarget(u16 move, u8 bank) { (void)bank; return gBattleMoves[move].target; }
u8 CalcMoveSplit(u16 move, u8 a, u8 d) { (void)a; (void)d; return gBattleMoves[move].split; }
u8 GetBankItemEffect(u8 bank) { assert(bank < 2); return effects[bank]; }
u8 ItemId_GetHoldEffect(u16 item) { assert(item == 1 || item == 2); return effects[item-1]; }
u8 ItemId_GetHoldEffectParam(u16 item) { assert(item == 1 || item == 2); return qualities[item-1]; }
u32 SpeedCalc(u8 bank) { return gBattleMons[bank].speed; }
bool8 IsOfType(u8 bank, u8 type) {
    return gBattleMons[bank].type1 == type || gBattleMons[bank].type2 == type;
}
bool8 CheckGrounding(u8 bank) { return !IsOfType(bank, TYPE_FLYING); }
bool8 WeatherHasEffect(void) { return TRUE; } /* No suppression abilities admitted. */
bool8 IsTargetAbilityIgnored(u8 defender, u8 attacker, u16 move) {
    (void)defender; (void)attacker; (void)move; return FALSE;
}
bool8 IsMoldBreakerAbility(u8 ability) { (void)ability; return FALSE; }
bool8 IsInverseBattle(void) { return FALSE; }
bool8 IsWonderRoomActive(void) { return FALSE; }
bool8 IsScaleMonsBattle(void) { return FALSE; }
bool8 IsZMove(u16 move) { (void)move; return FALSE; }
bool8 IsAnyMaxMove(u16 move) { (void)move; return FALSE; }
bool8 IsDynamaxed(u8 bank) { (void)bank; return FALSE; }
bool8 IsFloatingWithMagnetism(u8 bank) { (void)bank; return FALSE; }
bool8 IsAffectedByShadowShieldBattle(u8 bank) { (void)bank; return FALSE; }
bool8 IsIonDelugeActive(void) { return FALSE; }
bool8 IsWaterSportActive(void) { return FALSE; }
bool8 IsMudSportActive(void) { return FALSE; }
bool8 BypassesScreens(u8 ability) { (void)ability; return FALSE; }
bool8 ItemEffectIgnoresSunAndRain(u8 effect) { (void)effect; return FALSE; }
/* Aura queries are false because none of the admitted battlers has an aura ability. */
u8 AbilityBattleEffects(u8 case_id, u8 bank, u8 ability, u8 special, u16 move) {
    (void)case_id; (void)bank; (void)ability; (void)special; (void)move; return 0;
}

/* Link-only references in out-of-scope engine branches must abort if reached. */
__attribute__((noreturn)) static void unexpected(void) {
    fputs("out-of-scope engine helper reached\n", stderr); exit(3);
}
#include "unsupported.h"

static void reset(void) {
    memset(gBattleMons, 0, sizeof(gBattleMons));
    memset(&new_battle, 0, sizeof(new_battle));
    memset(&battle, 0, sizeof(battle));
    memset(gProtectStructs, 0, sizeof(gProtectStructs));
    memset(gStatuses3, 0, sizeof(gStatuses3));
    memset(gSideStatuses, 0, sizeof(gSideStatuses));
    memset(gDisableStructs, 0, sizeof(gDisableStructs));
    gBattleTypeFlags = 0;
    gBankAttacker = 0; gBankTarget = 1; gBattlersCount = 2;
    gCurrentMove = MOVE_POUND;
    gDynamicBasePower = 0; gMultiHitCounter = 0; gMoveResultFlags = 0;
    static const u8 script[8] = {0};
    gBattlescriptCurrInstr = script;
    for (int b = 0; b < 2; ++b) {
        gBattleMons[b].species = 1;
        gBattleMons[b].hp = gBattleMons[b].maxHP = 300;
        gBattleMons[b].speed = 100;
        gBattleMons[b].type3 = TYPE_BLANK;
        gBattleMons[b].item = b+1;
        memset(gBattleMons[b].statStages, 6, sizeof(gBattleMons[b].statStages));
    }
}

static int member(unsigned value, const unsigned *values, size_t count) {
    for (size_t i = 0; i < count; ++i) if (value == values[i]) return 1;
    return 0;
}
#define MEMBER(value, ...) member(value, (const unsigned[]){__VA_ARGS__}, sizeof((const unsigned[]){__VA_ARGS__})/sizeof(unsigned))
static int valid_type(unsigned value) { return (value < 18 && value != 9) || value == TYPE_FAIRY; }
static int valid(const unsigned *v) {
    if (v[0] < 1 || v[0] > 100 || v[1] < 1 || v[1] > 150 || !valid_type(v[2]) || v[3] > 1) return 0;
    for (int i=4; i<8; ++i) if (v[i] < 1 || v[i] > 999) return 0;
    for (int i=8; i<12; ++i) if (!valid_type(v[i])) return 0;
    for (int i=12; i<16; ++i) if (v[i] > 12) return 0;
    return MEMBER(v[16], 0, STATUS1_BURN)
        && MEMBER(v[17], 0, WEATHER_RAIN_TEMPORARY, WEATHER_SUN_TEMPORARY)
        && v[18] <= (SIDE_STATUS_REFLECT | SIDE_STATUS_LIGHTSCREEN) && v[19] <= 1
        && MEMBER(v[20], ABILITY_NONE, ABILITY_HUGEPOWER, ABILITY_HUSTLE, ABILITY_GUTS,
                  ABILITY_TECHNICIAN, ABILITY_SNIPER, ABILITY_ADAPTABILITY, ABILITY_TINTEDLENS)
        && MEMBER(v[21], ABILITY_NONE, ABILITY_THICKFAT, ABILITY_HEATPROOF, ABILITY_FURCOAT, ABILITY_ICESCALES)
        && MEMBER(v[22], 0, ITEM_EFFECT_LIFE_ORB, ITEM_EFFECT_EXPERT_BELT, ITEM_EFFECT_MUSCLE_BAND, ITEM_EFFECT_WISE_GLASSES)
        && MEMBER(v[23], 0, ITEM_EFFECT_ASSAULT_VEST);
}

int main(int argc, char **argv) {
    if (argc == 2 && strcmp(argv[1], "--describe") == 0) {
        printf("{\"critical_multiplier\":%u,\"fairy_type\":%u,\"burn_status\":%u,\"rain_weather\":%u,\"sun_weather\":%u,\"reflect\":%u,\"light_screen\":%u}\n",
               CRIT_MULTIPLIER, TYPE_FAIRY, STATUS1_BURN, WEATHER_RAIN_TEMPORARY,
               WEATHER_SUN_TEMPORARY, SIDE_STATUS_REFLECT, SIDE_STATUS_LIGHTSCREEN);
        return 0;
    }
    if (argc != 1) return 2;
    unsigned v[24];
    while (1) {
        int first = scanf("%u", &v[0]);
        if (first == EOF) break;
        if (first != 1) return 2;
        for (int i = 1; i < 24; ++i) if (scanf("%u", &v[i]) != 1) return 2;
        if (!valid(v)) { fputs("out-of-scope operands\n", stderr); return 2; }
        reset();
        gBattleMons[0].level = v[0];
        host_moves[1] = (struct BattleMove){.power=v[1], .type=v[2], .split=v[3], .effect=EFFECT_HIT, .target=MOVE_TARGET_SELECTED};
        gBattleMons[0].attack=v[4]; gBattleMons[0].spAttack=v[5];
        gBattleMons[1].defense=v[6]; gBattleMons[1].spDefense=v[7];
        gBattleMons[0].type1=v[8]; gBattleMons[0].type2=v[9];
        gBattleMons[1].type1=v[10]; gBattleMons[1].type2=v[11];
        STAT_STAGE(0, STAT_STAGE_ATK)=v[12]; STAT_STAGE(0, STAT_STAGE_SPATK)=v[13];
        STAT_STAGE(1, STAT_STAGE_DEF)=v[14]; STAT_STAGE(1, STAT_STAGE_SPDEF)=v[15];
        gBattleMons[0].status1=v[16]; gBattleWeather=v[17];
        gSideStatuses[1]=v[18];
        gNewBS->criticalMultiplier[1]=v[19] ? CRIT_MULTIPLIER : BASE_CRIT_MULTIPLIER;
        gBattleMons[0].ability=v[20]; gBattleMons[1].ability=v[21];
        effects[0]=v[22]; effects[1]=v[23];
        qualities[0]=qualities[1]=0;
        atk05_damagecalc();
        atk06_typecalc();
        /* Type immunity is represented by the engine's no-effect flag, not HP damage. */
        s32 before_random = (gMoveResultFlags & MOVE_RESULT_NO_EFFECT) ? 0 : gBattleMoveDamage;
        for (int r=85; r<=100; ++r) {
            gBattleMoveDamage=before_random; random_operand=100-r;
            ApplyRandomDmgMultiplier();
            printf("%s%d", r==85 ? "" : " ", gBattleMoveDamage);
        }
        puts("");
    }
    return ferror(stdin) || ferror(stdout) ? 2 : 0;
}
