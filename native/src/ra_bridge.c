#include "ra_bridge.h"

#include "rc_api_runtime.h"
#include "rc_consoles.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

typedef struct PendingRequest {
    RaServerRequest request;
    rc_client_server_callback_t callback;
    void* callback_data;
    struct PendingRequest* next;
    char award_hash[33];
    bool submission;
} PendingRequest;

typedef struct UnlockNode {
    RaUnlockEvent event;
    struct UnlockNode* next;
} UnlockNode;

struct RaBridge {
    rc_client_t* client;
    const rc_memory_regions_t* memory;
    DualDexGbaReadFn read;
    void* read_userdata;
    PendingRequest* requests;
    UnlockNode* unlocks;
    UnlockNode* last_unlock;
    RaLoginStatus login;
    RaGameStatus game;
    bool processing;
    bool reset_requested;
    bool closing;
    bool offline;
    bool server_error;
};

/* The host has one core and holds its lock across every client call, including creation. */
static uint64_t next_request_id;

static const char cancelled[] = "Request cancelled";
static const rc_api_server_response_t cancelled_response = {
    cancelled, sizeof(cancelled) - 1, RC_API_SERVER_RESPONSE_CLIENT_ERROR
};

uint32_t ra_bridge_read_memory(const RaBridge* bridge, uint32_t address, uint8_t* out, uint32_t length) {
    if (!bridge || !bridge->read || !bridge->memory || !out || !length) return 0;

    for (uint32_t i = 0; i < bridge->memory->num_regions; i++) {
        const rc_memory_region_t* region = &bridge->memory->region[i];
        if (address < region->start_address || address > region->end_address) continue;
        if (length - 1 > region->end_address - address) return 0;
        const uint32_t absolute = region->real_address + (address - region->start_address);
        return bridge->read(bridge->read_userdata, absolute, out, length) ? length : 0;
    }
    return 0;
}

static uint32_t RC_CCONV read_memory(uint32_t address, uint8_t* out, uint32_t length, rc_client_t* client) {
    return ra_bridge_read_memory(rc_client_get_userdata(client), address, out, length);
}

static char* copy_string(const char* text) {
    if (!text) return NULL;
    size_t length = strlen(text) + 1;
    char* copy = malloc(length);
    if (copy) memcpy(copy, text, length);
    return copy;
}

static void clear_request_strings(PendingRequest* pending) {
    /* The form may contain a password or token. Wipe our copy when it leaves the queue. */
    if (pending->request.post_data) {
        volatile char* p = (char*)pending->request.post_data;
        while (*p) *p++ = 0;
    }
    free((void*)pending->request.post_data);
    free((void*)pending->request.url);
    free((void*)pending->request.content_type);
    pending->request.post_data = NULL;
    pending->request.url = NULL;
    pending->request.content_type = NULL;
}

static void RC_CCONV server_call(const rc_api_request_t* request, rc_client_server_callback_t callback,
                                void* callback_data, rc_client_t* client) {
    RaBridge* bridge = rc_client_get_userdata(client);
    PendingRequest* pending = bridge->closing ? NULL : calloc(1, sizeof(*pending));
    if (pending) {
        pending->request.url = copy_string(request->url);
        pending->request.post_data = copy_string(request->post_data);
        pending->request.content_type = copy_string(request->content_type);
        if (pending->request.url && (!request->post_data || pending->request.post_data) &&
            (!request->content_type || pending->request.content_type)) {
            const char* form = pending->request.post_data;
            if (form && strncmp(form, "r=awardachievement&", 19) == 0) {
                pending->submission = true;
                const char* hash = strstr(form, "&m=");
                if (hash && strcspn(hash + 3, "&") == 32) memcpy(pending->award_hash, hash + 3, 32);
            } else if (form && strncmp(form, "r=submitlbentry&", 16) == 0) {
                pending->submission = true;
            }
            pending->request.id = ++next_request_id;
            pending->callback = callback;
            pending->callback_data = callback_data;
            PendingRequest** end = &bridge->requests;
            while (*end) end = &(*end)->next;
            *end = pending;
            return;
        }
        clear_request_strings(pending);
        free(pending);
    }
    callback(&cancelled_response, callback_data);
}

static void RC_CCONV on_event(const rc_client_event_t* event, rc_client_t* client) {
    RaBridge* bridge = rc_client_get_userdata(client);
    if (bridge->closing) return;

    switch (event->type) {
        case RC_CLIENT_EVENT_ACHIEVEMENT_TRIGGERED: {
            const rc_client_achievement_t* achievement = event->achievement;
            UnlockNode* node = calloc(1, sizeof(*node));
            if (!node) return;
            node->event.id = achievement->id;
            node->event.points = achievement->points;
            snprintf(node->event.title, sizeof(node->event.title), "%s", achievement->title);
            if (bridge->last_unlock) bridge->last_unlock->next = node;
            else bridge->unlocks = node;
            bridge->last_unlock = node;
            break;
        }
        case RC_CLIENT_EVENT_RESET: bridge->reset_requested = true; break;
        case RC_CLIENT_EVENT_DISCONNECTED: bridge->offline = true; break;
        case RC_CLIENT_EVENT_RECONNECTED: bridge->offline = false; break;
        case RC_CLIENT_EVENT_SERVER_ERROR: bridge->server_error = true; break;
    }
}

RaBridge* ra_bridge_create(DualDexGbaReadFn read, void* userdata) {
    RaBridge* bridge = calloc(1, sizeof(*bridge));
    if (!bridge) return NULL;
    bridge->client = rc_client_create(read_memory, server_call);
    if (!bridge->client) {
        free(bridge);
        return NULL;
    }
    bridge->read = read;
    bridge->read_userdata = userdata;
    bridge->memory = rc_console_memory_regions(RC_CONSOLE_GAMEBOY_ADVANCE);
    rc_client_set_userdata(bridge->client, bridge);
    rc_client_set_event_handler(bridge->client, on_event);
    rc_client_set_hardcore_enabled(bridge->client, 0);
    return bridge;
}

static rc_clock_t RC_CCONV drain_clock(const rc_client_t* client) {
    (void)client;
    return UINT64_MAX;
}

void ra_bridge_destroy(RaBridge* bridge) {
    if (!bridge) return;
    bridge->closing = true;
    ra_bridge_unload_game(bridge);
    /* Drain in-flight and scheduled work while the client still owns its callback data. */
    while (bridge->requests) {
        PendingRequest* pending = bridge->requests;
        bridge->requests = pending->next;
        clear_request_strings(pending);
        pending->callback(&cancelled_response, pending->callback_data);
        free(pending);
    }
    rc_client_set_get_time_millisecs_function(bridge->client, drain_clock);
    rc_client_idle(bridge->client);
    rc_client_destroy(bridge->client);
    free(bridge);
}

void ra_bridge_set_hardcore(RaBridge* bridge, bool enabled) {
    if (!bridge) return;
    rc_client_set_hardcore_enabled(bridge->client, enabled);
    if (bridge->game == RA_GAME_ACTIVE)
        bridge->processing = rc_client_is_processing_required(bridge->client);
}

bool ra_bridge_is_hardcore(const RaBridge* bridge) {
    return bridge && rc_client_get_hardcore_enabled(bridge->client);
}

bool ra_bridge_is_active(const RaBridge* bridge) {
    return bridge && bridge->game == RA_GAME_ACTIVE;
}

static void RC_CCONV on_login(int result, const char* error, rc_client_t* client, void* userdata) {
    (void)error;
    (void)client;
    RaBridge* bridge = userdata;
    if (result == RC_OK) bridge->login = RA_LOGIN_READY;
    else if (result == RC_INVALID_CREDENTIALS || result == RC_EXPIRED_TOKEN || result == RC_ACCESS_DENIED)
        bridge->login = RA_LOGIN_REJECTED;
    else bridge->login = RA_LOGIN_FAILED;
}

void ra_bridge_begin_login(RaBridge* bridge, const char* username, const char* secret, bool use_token) {
    if (!bridge || bridge->login == RA_LOGIN_PENDING || bridge->login == RA_LOGIN_READY) return;
    bridge->login = RA_LOGIN_PENDING;
    if (use_token) rc_client_begin_login_with_token(bridge->client, username, secret, on_login, bridge);
    else rc_client_begin_login_with_password(bridge->client, username, secret, on_login, bridge);
}

RaLoginStatus ra_bridge_login_status(const RaBridge* bridge) {
    return bridge ? bridge->login : RA_LOGIN_SIGNED_OUT;
}

const rc_client_user_t* ra_bridge_user(const RaBridge* bridge) {
    return bridge ? rc_client_get_user_info(bridge->client) : NULL;
}

static void RC_CCONV on_game_loaded(int result, const char* error, rc_client_t* client, void* userdata) {
    (void)error;
    RaBridge* bridge = userdata;
    if (result == RC_ABORTED) return;
    const rc_client_game_t* game = rc_client_get_game_info(client);
    if (result == RC_NO_GAME_LOADED || result == RC_NOT_FOUND ||
        (result == RC_OK && (!game || !game->id || game->console_id != RC_CONSOLE_GAMEBOY_ADVANCE ||
            (!rc_client_has_achievements(client) && !rc_client_has_leaderboards(client))))) {
        bridge->game = RA_GAME_NO_SET;
    } else if (result == RC_OK) {
        bridge->game = RA_GAME_ACTIVE;
        bridge->processing = rc_client_is_processing_required(client);
    } else {
        bridge->game = RA_GAME_FAILED;
    }
}

void ra_bridge_load_game(RaBridge* bridge, const char* md5) {
    if (!bridge || bridge->login != RA_LOGIN_READY || !md5 ||
        strlen(md5) != 32 || strspn(md5, "0123456789abcdef") != 32) return;
    ra_bridge_unload_game(bridge);
    bridge->game = RA_GAME_LOADING;
    rc_client_begin_load_game(bridge->client, md5, on_game_loaded, bridge);
    if (bridge->game == RA_GAME_NO_SET) rc_client_unload_game(bridge->client);
}

RaGameStatus ra_bridge_game_status(const RaBridge* bridge) {
    return bridge ? bridge->game : RA_GAME_NONE;
}

const char* ra_bridge_game_title(const RaBridge* bridge) {
    if (!ra_bridge_is_active(bridge)) return NULL;
    return rc_client_get_game_info(bridge->client)->title;
}

void ra_bridge_unload_game(RaBridge* bridge) {
    if (!bridge) return;
    rc_client_unload_game(bridge->client);
    bridge->game = RA_GAME_NONE;
    bridge->processing = false;
    bridge->reset_requested = false;
    RaUnlockEvent discarded;
    while (ra_bridge_pop_unlock(bridge, &discarded)) {}
}

void ra_bridge_reset(RaBridge* bridge) {
    if (!bridge) return;
    bridge->reset_requested = false;
    rc_client_reset(bridge->client);
}

bool ra_bridge_take_reset_request(RaBridge* bridge) {
    if (!bridge || !bridge->reset_requested) return false;
    bridge->reset_requested = false;
    return true;
}

void ra_bridge_do_frame(RaBridge* bridge) {
    if (bridge && bridge->processing) rc_client_do_frame(bridge->client);
}

void ra_bridge_idle(RaBridge* bridge) {
    if (bridge && (ra_bridge_is_active(bridge) || bridge->offline)) rc_client_idle(bridge->client);
}

bool ra_bridge_is_offline(const RaBridge* bridge) { return bridge && bridge->offline; }

bool ra_bridge_take_server_error(RaBridge* bridge) {
    if (!bridge || !bridge->server_error) return false;
    bridge->server_error = false;
    return true;
}

bool ra_bridge_next_request(const RaBridge* bridge, RaServerRequest* request) {
    if (!bridge) return false;
    for (const PendingRequest* pending = bridge->requests; pending; pending = pending->next) {
        if (!pending->request.url) continue;
        *request = pending->request;
        return true;
    }
    return false;
}

void ra_bridge_mark_dispatched(RaBridge* bridge, uint64_t id) {
    for (PendingRequest* pending = bridge->requests; pending; pending = pending->next) {
        if (pending->request.id != id) continue;
        clear_request_strings(pending);
        break;
    }
}

void ra_bridge_complete_request(RaBridge* bridge, uint64_t id, int status, const char* body, size_t length) {
    if (!bridge) return;
    PendingRequest** link = &bridge->requests;
    while (*link && (*link)->request.id != id) link = &(*link)->next;
    PendingRequest* pending = *link;
    if (!pending) return; /* response from a signed-out session */
    *link = pending->next;

    const RaGameStatus previous_game_status = bridge->game;
    rc_api_server_response_t response = {body, length, status};
    const rc_client_game_t* game = ra_bridge_is_active(bridge) ? rc_client_get_game_info(bridge->client) : NULL;
    if (pending->award_hash[0] && (!game || strcmp(pending->award_hash, game->hash) != 0)) {
        /* v12.5.0 assumes client->game still owns the award when it computes mastery.
         * The real award has already reached RA. Suppress local mastery for a stale game. */
        rc_api_award_achievement_response_t award;
        if (rc_api_process_award_achievement_server_response(&award, &response) == RC_OK &&
            award.response.succeeded) {
            bool prior_error = bridge->server_error;
            pending->callback(&cancelled_response, pending->callback_data);
            bridge->server_error = prior_error;
        } else {
            pending->callback(&response, pending->callback_data);
        }
        rc_api_destroy_award_achievement_response(&award);
    } else {
        pending->callback(&response, pending->callback_data);
    }
    if (previous_game_status == RA_GAME_LOADING && bridge->game == RA_GAME_NO_SET)
        rc_client_unload_game(bridge->client);
    if (pending->submission && !bridge->processing) rc_client_idle(bridge->client);
    clear_request_strings(pending);
    free(pending);
}

rc_client_achievement_list_t* ra_bridge_achievements(RaBridge* bridge) {
    return ra_bridge_is_active(bridge) ? rc_client_create_achievement_list(
        bridge->client, RC_CLIENT_ACHIEVEMENT_CATEGORY_CORE, RC_CLIENT_ACHIEVEMENT_LIST_GROUPING_LOCK_STATE) : NULL;
}

rc_client_leaderboard_list_t* ra_bridge_leaderboards(RaBridge* bridge) {
    return ra_bridge_is_active(bridge) ? rc_client_create_leaderboard_list(
        bridge->client, RC_CLIENT_LEADERBOARD_LIST_GROUPING_TRACKING) : NULL;
}

size_t ra_bridge_rich_presence(RaBridge* bridge, char* out, size_t length) {
    if (!out || !length) return 0;
    out[0] = '\0';
    return ra_bridge_is_active(bridge) && rc_client_has_rich_presence(bridge->client)
        ? rc_client_get_rich_presence_message(bridge->client, out, length) : 0;
}

bool ra_bridge_pop_unlock(RaBridge* bridge, RaUnlockEvent* out) {
    if (!bridge || !bridge->unlocks || !out) return false;
    UnlockNode* node = bridge->unlocks;
    *out = node->event;
    bridge->unlocks = node->next;
    if (!bridge->unlocks) bridge->last_unlock = NULL;
    free(node);
    return true;
}
