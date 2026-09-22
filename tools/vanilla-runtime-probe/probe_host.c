/* Developer-only adapter. No lifecycle/presence reader or product gate is called. */
#include "libretro_host.h"
#include "pokemon_reader.h"

/* Parse the two original party slots through the shipped decrypt/checksum reader.
 * This corroborates the diagnostic BattlePokemon address before any staging.
 * It makes no claim about active-slot/lifecycle resolution in the product. */
bool probe_party(int game, int enemy, unsigned short out[13]) {
    const GameMemoryConfig *config = pokemon_get_game_config((GbaGameId)game);
    uint8_t raw[100];
    ParsedPokemon mon;
    if (!config || !libretro_host_read_gba_address(0x02000000u +
            (enemy ? config->enemy_party_offset : config->player_party_offset), raw, sizeof(raw)) ||
            !pokemon_parse_single(raw, true, &mon)) return false;
    out[0] = mon.species;
    out[1] = mon.attack; out[2] = mon.defense; out[3] = mon.speed;
    out[4] = mon.sp_attack; out[5] = mon.sp_defense;
    for (int i = 0; i < 4; i++) out[6+i] = mon.moves[i];
    out[10] = mon.current_hp; out[11] = mon.level; out[12] = mon.max_hp;
    return true;
}

bool probe_layout(int game, unsigned int out[3]) {
    const GameMemoryConfig *c = pokemon_get_game_config((GbaGameId)game);
    if (!c) return false;
    out[0] = c->battle_mons_size;
    out[1] = c->battle_mons_hp_offset;
    out[2] = c->battle_mons_stat_stages_offset;
    return true;
}
