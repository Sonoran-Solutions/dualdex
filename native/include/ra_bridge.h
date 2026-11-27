#ifndef DUALDEX_RA_BRIDGE_H
#define DUALDEX_RA_BRIDGE_H

#include "pokemon_reader.h" /* DualDexGbaReadFn */
#include "rc_client.h"

typedef struct RaBridge RaBridge;

typedef enum {
    RA_LOGIN_SIGNED_OUT, RA_LOGIN_PENDING, RA_LOGIN_READY, RA_LOGIN_REJECTED, RA_LOGIN_FAILED
} RaLoginStatus;

typedef enum {
    RA_GAME_NONE, RA_GAME_LOADING, RA_GAME_ACTIVE, RA_GAME_NO_SET, RA_GAME_FAILED
} RaGameStatus;

typedef struct {
    uint64_t id;
    const char* url;
    const char* post_data;
    const char* content_type;
} RaServerRequest;

typedef struct {
    uint32_t id;
    uint32_t points;
    char title[128];
} RaUnlockEvent;

/* The caller serializes all operations with emulation and ROM lifetime. */
RaBridge* ra_bridge_create(DualDexGbaReadFn read, void* userdata);
void ra_bridge_destroy(RaBridge* bridge);
void ra_bridge_set_hardcore(RaBridge* bridge, bool enabled);
bool ra_bridge_is_hardcore(const RaBridge* bridge);
bool ra_bridge_is_active(const RaBridge* bridge);
void ra_bridge_begin_login(RaBridge* bridge, const char* username, const char* secret, bool use_token);
RaLoginStatus ra_bridge_login_status(const RaBridge* bridge);
const rc_client_user_t* ra_bridge_user(const RaBridge* bridge);
void ra_bridge_load_game(RaBridge* bridge, const char* md5);
RaGameStatus ra_bridge_game_status(const RaBridge* bridge);
const char* ra_bridge_game_title(const RaBridge* bridge);
void ra_bridge_unload_game(RaBridge* bridge);
void ra_bridge_reset(RaBridge* bridge);
bool ra_bridge_take_reset_request(RaBridge* bridge);
void ra_bridge_do_frame(RaBridge* bridge);
void ra_bridge_idle(RaBridge* bridge);
bool ra_bridge_is_offline(const RaBridge* bridge);
bool ra_bridge_take_server_error(RaBridge* bridge);

/* Request strings remain valid until dispatched or completed. IDs are never reused. */
bool ra_bridge_next_request(const RaBridge* bridge, RaServerRequest* request);
void ra_bridge_mark_dispatched(RaBridge* bridge, uint64_t id);
void ra_bridge_complete_request(RaBridge* bridge, uint64_t id, int status, const char* body, size_t length);

rc_client_achievement_list_t* ra_bridge_achievements(RaBridge* bridge);
rc_client_leaderboard_list_t* ra_bridge_leaderboards(RaBridge* bridge);
size_t ra_bridge_rich_presence(RaBridge* bridge, char* out, size_t length);
bool ra_bridge_pop_unlock(RaBridge* bridge, RaUnlockEvent* out);

uint32_t ra_bridge_read_memory(const RaBridge* bridge, uint32_t address, uint8_t* out, uint32_t length);

#endif
