#include "ra_bridge.h"
#include "gba_memory_map.h"

#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#if defined(__GLIBC__)
extern void* __libc_malloc(size_t size);
static int g_fail_malloc_countdown = 0;

void* malloc(size_t size) {
    if (g_fail_malloc_countdown > 0 && --g_fail_malloc_countdown == 0) {
        return NULL;
    }
    return __libc_malloc(size);
}
#endif

static uint8_t iwram[DUALDEX_GBA_IWRAM_SIZE];
static uint8_t ewram[DUALDEX_GBA_EWRAM_SIZE];
static uint8_t sram[0x10000];

static bool read_gba(void* user, uint32_t address, uint8_t* out, size_t count) {
    return gba_memory_map_read(user, address, out, count);
}

static uint64_t request(RaBridge* bridge, const char* expected) {
    RaServerRequest pending;
    assert(ra_bridge_next_request(bridge, &pending));
    assert(strcmp(pending.url, "https://retroachievements.org/dorequest.php") == 0);
    assert(strncmp(pending.post_data, expected, strlen(expected)) == 0);
    ra_bridge_mark_dispatched(bridge, pending.id);
    return pending.id;
}

static void reply(RaBridge* bridge, uint64_t id, const char* body) {
    ra_bridge_complete_request(bridge, id, 200, body, strlen(body));
}

static void check_memory(RaBridge* bridge) {
    iwram[0] = 0x31;
    iwram[0x7fff] = 0x32;
    ewram[0] = 0x21;
    ewram[0x3ffff] = 0x22;
    sram[0] = 0x41;
    sram[0xffff] = 0x42;
    uint8_t out[4] = {0};

    assert(ra_bridge_read_memory(bridge, 0x000000, out, 1) == 1 && out[0] == 0x31);
    assert(ra_bridge_read_memory(bridge, 0x007fff, out, 1) == 1 && out[0] == 0x32);
    assert(ra_bridge_read_memory(bridge, 0x008000, out, 4) == 4 && out[0] == 0x21);
    assert(ra_bridge_read_memory(bridge, 0x047fff, out, 1) == 1 && out[0] == 0x22);
    assert(ra_bridge_read_memory(bridge, 0x048000, out, 1) == 1 && out[0] == 0x41);
    assert(ra_bridge_read_memory(bridge, 0x057fff, out, 1) == 1 && out[0] == 0x42);
    assert(ra_bridge_read_memory(bridge, 0x007fff, out, 2) == 0 && out[0] == 0x42);
    assert(ra_bridge_read_memory(bridge, 0x047fff, out, 2) == 0);
    assert(ra_bridge_read_memory(bridge, 0x057fff, out, 2) == 0);
    assert(ra_bridge_read_memory(bridge, 0x058000, out, 1) == 0);
    assert(ra_bridge_read_memory(bridge, UINT32_MAX, out, 1) == 0);
    assert(ra_bridge_read_memory(bridge, 0, out, UINT32_MAX) == 0);
    assert(ra_bridge_read_memory(bridge, 0, out, 0) == 0);
}

static void check_client(RaBridge* bridge) {
    assert(ra_bridge_login_status(bridge) == RA_LOGIN_SIGNED_OUT);
    assert(!ra_bridge_is_hardcore(bridge));
    ra_bridge_set_hardcore(bridge, true);
    assert(ra_bridge_is_hardcore(bridge));

    ra_bridge_begin_login(bridge, "SyntheticUser", "test-password", false);
    assert(ra_bridge_login_status(bridge) == RA_LOGIN_PENDING);
    reply(bridge, request(bridge, "r=login2&"), "{\"Success\":true,\"User\":\"SyntheticUser\",\"Token\":\"test-token\"}");
    assert(ra_bridge_login_status(bridge) == RA_LOGIN_READY);
    assert(strcmp(ra_bridge_user(bridge)->token, "test-token") == 0);

    ra_bridge_load_game(bridge, "0123456789abcdef0123456789abcdef");
    assert(ra_bridge_game_status(bridge) == RA_GAME_LOADING);
    reply(bridge, request(bridge, "r=achievementsets&"),
        "{\"Success\":true,\"GameId\":7,\"Title\":\"Synthetic GBA\",\"ConsoleId\":5,"
        "\"ImageIconUrl\":\"https://media.retroachievements.org/Images/001.png\","
        "\"RichPresencePatch\":\"Display:\\nPlaying synthetic GBA\","
        "\"Sets\":[{\"AchievementSetId\":7,\"GameId\":7,\"Title\":\"Core\",\"Type\":\"core\","
        "\"ImageIconUrl\":\"https://media.retroachievements.org/Images/001.png\","
        "\"Achievements\":[{\"ID\":42,\"Title\":\"Synthetic Award\",\"Description\":\"Read both RAM banks\","
        "\"Flags\":3,\"Points\":5,\"MemAddr\":\"0xH0000=7_0xH8000=8\",\"Author\":\"Test\","
        "\"BadgeName\":\"1\",\"Created\":1,\"Modified\":1,\"Type\":\"missable\"}],\"Leaderboards\":[]}]}");
    reply(bridge, request(bridge, "r=startsession&"), "{\"Success\":true}");
    assert(ra_bridge_is_active(bridge));
    assert(strcmp(ra_bridge_game_title(bridge), "Synthetic GBA") == 0);
    char presence[64];
    assert(ra_bridge_rich_presence(bridge, presence, sizeof(presence)) > 0);
    assert(strcmp(presence, "Playing synthetic GBA") == 0);

    RaUnlockEvent event;
    ra_bridge_do_frame(bridge);
    assert(!ra_bridge_pop_unlock(bridge, &event));
    iwram[0] = 7;
    ewram[0] = 8;
    ra_bridge_do_frame(bridge);
    assert(ra_bridge_pop_unlock(bridge, &event));
    assert(event.id == 42 && event.points == 5 && strcmp(event.title, "Synthetic Award") == 0);
    assert(!ra_bridge_pop_unlock(bridge, &event));

    rc_client_achievement_list_t* list = ra_bridge_achievements(bridge);
    assert(list && list->num_buckets == 1 && list->buckets[0].num_achievements == 1);
    const rc_client_achievement_t* achievement = list->buckets[0].achievements[0];
    assert(achievement->unlocked & RC_CLIENT_ACHIEVEMENT_UNLOCKED_HARDCORE);
    assert(achievement->type == RC_CLIENT_ACHIEVEMENT_TYPE_MISSABLE);
    rc_client_destroy_achievement_list(list);

    uint64_t submission = request(bridge, "r=awardachievement&");
    ra_bridge_unload_game(bridge);
    assert(!ra_bridge_is_active(bridge));
    ra_bridge_complete_request(bridge, submission, RC_API_SERVER_RESPONSE_RETRYABLE_CLIENT_ERROR,
        "Offline", sizeof("Offline") - 1);
    uint64_t retry = request(bridge, "r=awardachievement&");
    assert(retry != submission);
    /* Successful delivery after unload must not dereference the previous game. */
    reply(bridge, retry, "{\"Success\":true,\"AchievementID\":42,\"AchievementsRemaining\":0}");

    ra_bridge_set_hardcore(bridge, false);
    assert(!ra_bridge_is_hardcore(bridge));
    /* Loading a game in Softcore and then reconciling to Hardcore once active must request a reset. */
    ra_bridge_load_game(bridge, "0123456789abcdef0123456789abcdef");
    assert(ra_bridge_game_status(bridge) == RA_GAME_LOADING);
    reply(bridge, request(bridge, "r=achievementsets&"),
        "{\"Success\":true,\"GameId\":7,\"Title\":\"Synthetic GBA\",\"ConsoleId\":5,"
        "\"ImageIconUrl\":\"https://media.retroachievements.org/Images/001.png\","
        "\"Sets\":[{\"AchievementSetId\":7,\"GameId\":7,\"Title\":\"Core\",\"Type\":\"core\","
        "\"ImageIconUrl\":\"https://media.retroachievements.org/Images/001.png\","
        "\"Achievements\":[{\"ID\":43,\"Title\":\"Reconciled Award\",\"Description\":\"Hardcore after load\","
        "\"Flags\":3,\"Points\":5,\"MemAddr\":\"0xH0000=9\",\"Author\":\"Test\","
        "\"BadgeName\":\"1\",\"Created\":1,\"Modified\":1}],\"Leaderboards\":[]}]}");
    reply(bridge, request(bridge, "r=startsession&"), "{\"Success\":true}");
    assert(ra_bridge_is_active(bridge));
    assert(!ra_bridge_is_hardcore(bridge));
    assert(!ra_bridge_take_reset_request(bridge));
    ra_bridge_set_hardcore(bridge, true);
    assert(ra_bridge_is_hardcore(bridge));
    assert(ra_bridge_take_reset_request(bridge));
    assert(!ra_bridge_take_reset_request(bridge));
    ra_bridge_reset(bridge);
    iwram[0] = 0;
    ra_bridge_do_frame(bridge);
    assert(!ra_bridge_pop_unlock(bridge, &event));
    iwram[0] = 9;
    ra_bridge_do_frame(bridge);
    assert(ra_bridge_pop_unlock(bridge, &event));
    assert(event.id == 43);
    list = ra_bridge_achievements(bridge);
    assert(list && list->num_buckets == 1 && list->buckets[0].num_achievements == 1);
    assert(list->buckets[0].achievements[0]->unlocked & RC_CLIENT_ACHIEVEMENT_UNLOCKED_HARDCORE);
    rc_client_destroy_achievement_list(list);
    reply(bridge, request(bridge, "r=awardachievement&"),
        "{\"Success\":true,\"AchievementID\":43,\"AchievementsRemaining\":0}");
    ra_bridge_unload_game(bridge);

    ra_bridge_set_hardcore(bridge, false);
    assert(!ra_bridge_is_hardcore(bridge));
    ra_bridge_load_game(bridge, "abcdef0123456789abcdef0123456789");
    reply(bridge, request(bridge, "r=achievementsets&"),
        "{\"Success\":true,\"GameId\":0,\"Title\":\"Unknown\",\"ConsoleId\":5,\"ImageIconUrl\":\"https://media.retroachievements.org/Images/001.png\"}");
    assert(ra_bridge_game_status(bridge) == RA_GAME_NO_SET);
    assert(ra_bridge_achievements(bridge) == NULL);
    assert(!ra_bridge_is_active(bridge));
}

static void check_multisubset_bucket_label_oom(RaBridge* bridge) {
    /* Subset titles > 4 KiB exceed any pre-reserved rc_buffer_t slack from loading the 1-item sets,
     * guaranteeing rc_buffer_alloc() must invoke malloc() for each subset bucket label. */
    char core_title[4096];
    char bonus_title[4096];
    memset(core_title, 'C', sizeof(core_title) - 1);
    core_title[sizeof(core_title) - 1] = '\0';
    memcpy(core_title, "Core_", 5);
    memset(bonus_title, 'B', sizeof(bonus_title) - 1);
    bonus_title[sizeof(bonus_title) - 1] = '\0';
    memcpy(bonus_title, "Bonus_", 6);

    char payload[12288];
    int written = snprintf(payload, sizeof(payload),
        "{\"Success\":true,\"GameId\":100,\"Title\":\"MultiSubset GBA\",\"ConsoleId\":5,"
        "\"ImageIconUrl\":\"https://media.retroachievements.org/Images/001.png\","
        "\"Sets\":["
        "{\"AchievementSetId\":100,\"GameId\":100,\"Title\":\"%s\",\"Type\":\"core\","
        "\"ImageIconUrl\":\"https://media.retroachievements.org/Images/001.png\","
        "\"Achievements\":[{\"ID\":101,\"Title\":\"Core Ach\",\"Description\":\"Core desc\","
        "\"Flags\":3,\"Points\":5,\"MemAddr\":\"0xH0000=1\",\"Author\":\"Test\","
        "\"BadgeName\":\"1\",\"Created\":1,\"Modified\":1}],"
        "\"Leaderboards\":[{\"ID\":201,\"Title\":\"Core LB\",\"Description\":\"Core LB desc\","
        "\"Mem\":\"STA:0xH0000=1::CAN:0xH0000=2::SUB:0xH0000=3::VAL:0xH0000\","
        "\"Format\":\"VALUE\",\"LowerIsBetter\":false,\"Hidden\":false}]},"
        "{\"AchievementSetId\":101,\"GameId\":101,\"Title\":\"%s\",\"Type\":\"bonus\","
        "\"ImageIconUrl\":\"https://media.retroachievements.org/Images/001.png\","
        "\"Achievements\":[{\"ID\":102,\"Title\":\"Bonus Ach\",\"Description\":\"Bonus desc\","
        "\"Flags\":3,\"Points\":10,\"MemAddr\":\"0xH0000=2\",\"Author\":\"Test\","
        "\"BadgeName\":\"2\",\"Created\":1,\"Modified\":1}],"
        "\"Leaderboards\":[{\"ID\":202,\"Title\":\"Bonus LB\",\"Description\":\"Bonus LB desc\","
        "\"Mem\":\"STA:0xH0000=1::CAN:0xH0000=2::SUB:0xH0000=3::VAL:0xH0000\","
        "\"Format\":\"VALUE\",\"LowerIsBetter\":false,\"Hidden\":false}]}"
        "]}",
        core_title, bonus_title);
    assert(written > 0 && (size_t)written < sizeof(payload));

    ra_bridge_load_game(bridge, "11111111111111111111111111111111");
    assert(ra_bridge_game_status(bridge) == RA_GAME_LOADING);
    reply(bridge, request(bridge, "r=achievementsets&"), payload);
    reply(bridge, request(bridge, "r=startsession&"), "{\"Success\":true}");
    assert(ra_bridge_is_active(bridge));

#if defined(__GLIBC__)
    /* Fail the main list allocation (#1): must return NULL and unlock cleanly. */
    g_fail_malloc_countdown = 1;
    rc_client_achievement_list_t* oom_ach_list = ra_bridge_achievements(bridge);
    int rem = g_fail_malloc_countdown;
    g_fail_malloc_countdown = 0;
    assert(rem == 0 && oom_ach_list == NULL);

    g_fail_malloc_countdown = 1;
    rc_client_leaderboard_list_t* oom_lb_list = ra_bridge_leaderboards(bridge);
    rem = g_fail_malloc_countdown;
    g_fail_malloc_countdown = 0;
    assert(rem == 0 && oom_lb_list == NULL);

    /* Fail the rc_buffer_alloc bucket label allocation (#2) AFTER list allocation (#1) succeeds. */
    g_fail_malloc_countdown = 2;
    rc_client_achievement_list_t* ach_list = ra_bridge_achievements(bridge);
    rem = g_fail_malloc_countdown;
    g_fail_malloc_countdown = 0;
    assert(rem == 0);
    assert(ach_list != NULL && ach_list->num_buckets == 2);
    assert(ach_list->buckets[0].label != NULL && strcmp(ach_list->buckets[0].label, "Locked") == 0);
    assert(ach_list->buckets[1].label != NULL && strstr(ach_list->buckets[1].label, " - Locked") != NULL);
    rc_client_destroy_achievement_list(ach_list);

    /* Subsequent call with memory available allocates the full subset-qualified label. */
    ach_list = ra_bridge_achievements(bridge);
    assert(ach_list != NULL && ach_list->num_buckets == 2);
    assert(ach_list->buckets[0].label != NULL && strstr(ach_list->buckets[0].label, " - Locked") != NULL);
    assert(ach_list->buckets[1].label != NULL && strstr(ach_list->buckets[1].label, " - Locked") != NULL);
    rc_client_destroy_achievement_list(ach_list);

    /* Same label-allocation OOM check for rc_client_create_leaderboard_list. */
    g_fail_malloc_countdown = 2;
    rc_client_leaderboard_list_t* lb_list = ra_bridge_leaderboards(bridge);
    rem = g_fail_malloc_countdown;
    g_fail_malloc_countdown = 0;
    assert(rem == 0);
    assert(lb_list != NULL && lb_list->num_buckets == 2);
    assert(lb_list->buckets[0].label != NULL && strcmp(lb_list->buckets[0].label, "Inactive") == 0);
    assert(lb_list->buckets[1].label != NULL && strstr(lb_list->buckets[1].label, " - Inactive") != NULL);
    rc_client_destroy_leaderboard_list(lb_list);

    lb_list = ra_bridge_leaderboards(bridge);
    assert(lb_list != NULL && lb_list->num_buckets == 2);
    assert(lb_list->buckets[0].label != NULL && strstr(lb_list->buckets[0].label, " - Inactive") != NULL);
    assert(lb_list->buckets[1].label != NULL && strstr(lb_list->buckets[1].label, " - Inactive") != NULL);
    rc_client_destroy_leaderboard_list(lb_list);
#else
    rc_client_achievement_list_t* ach_list = ra_bridge_achievements(bridge);
    assert(ach_list != NULL && ach_list->num_buckets == 2);
    rc_client_destroy_achievement_list(ach_list);
    rc_client_leaderboard_list_t* lb_list = ra_bridge_leaderboards(bridge);
    assert(lb_list != NULL && lb_list->num_buckets == 2);
    rc_client_destroy_leaderboard_list(lb_list);
#endif

    ra_bridge_unload_game(bridge);
}

int main(void) {
    DualDexGbaRegionTable memory = {0};
    assert(gba_memory_map_add(&memory, iwram, DUALDEX_GBA_IWRAM_BASE, sizeof(iwram), 0, 0, 0, 0));
    assert(gba_memory_map_add(&memory, ewram, DUALDEX_GBA_EWRAM_BASE, sizeof(ewram), 0, 0, 0, 0));
    assert(gba_memory_map_add(&memory, sram, 0x0e000000, sizeof(sram), 0, 0, 0, 0));

    RaBridge* bridge = ra_bridge_create(read_gba, &memory);
    assert(bridge);
    check_memory(bridge);
    check_client(bridge);
    check_multisubset_bucket_label_oom(bridge);
    ra_bridge_destroy(bridge);

    bridge = ra_bridge_create(read_gba, &memory);
    assert(bridge);
    ra_bridge_begin_login(bridge, "SyntheticUser", "test-token", true);
    (void)request(bridge, "r=login2&");
    ra_bridge_destroy(bridge); /* signed out while the request is outstanding */
    ra_bridge_destroy(NULL);
    puts("RetroAchievements bridge checks passed");
    return 0;
}
