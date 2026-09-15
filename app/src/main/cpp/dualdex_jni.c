#include <jni.h>
#include <string.h>
#include <stdlib.h>
#include <android/log.h>
#include "pokemon_reader.h"
#include "pokemon_text.h"
#include "libretro_host.h"
#include "js_calc_engine.h"

#define TAG "DualDex_JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static jclass g_parsed_pokemon_cls = NULL;
static jmethodID g_parsed_pokemon_ctor = NULL;

static jclass g_player_location_cls = NULL;
static jmethodID g_player_location_ctor = NULL;

// Trailing "IZZII" carries hiddenNature, natureModified, gigantamaxFactor, shinyState and
// shinyModifier, which were appended to com.dualdex.pokemon.ParsedPokemon for H&S 2.0.5.
#define PARSED_POKEMON_SIG "(ZZJIILjava/lang/String;Ljava/lang/String;IIIILjava/lang/String;ZIZIJIIIIIIIIIIII[I[IIIIIIIIJIZZII)V"
#define PLAYER_LOCATION_SIG "(IIIIIIIIIZZ)V"

static void init_class_cache(JNIEnv* env) {
    if (g_parsed_pokemon_cls == NULL) {
        jclass local_cls = (*env)->FindClass(env, "com/dualdex/pokemon/ParsedPokemon");
        if (!local_cls) {
            LOGE("Failed to find com.dualdex.pokemon.ParsedPokemon");
            (*env)->ExceptionClear(env);
        } else {
            g_parsed_pokemon_cls = (jclass)(*env)->NewGlobalRef(env, local_cls);
            (*env)->DeleteLocalRef(env, local_cls);
        }
    }

    if (g_parsed_pokemon_ctor == NULL && g_parsed_pokemon_cls != NULL) {
        g_parsed_pokemon_ctor = (*env)->GetMethodID(
            env,
            g_parsed_pokemon_cls,
            "<init>",
            PARSED_POKEMON_SIG
        );

        if (!g_parsed_pokemon_ctor) {
            LOGE("Failed to find ParsedPokemon constructor with signature: %s", PARSED_POKEMON_SIG);
            (*env)->ExceptionClear(env);
        } else {
            LOGI("ParsedPokemon constructor resolved successfully");
        }
    }

    if (g_player_location_cls == NULL) {
        jclass local_cls = (*env)->FindClass(env, "com/dualdex/pokemon/PlayerLocation");
        if (!local_cls) {
            LOGE("Failed to find com.dualdex.pokemon.PlayerLocation");
            (*env)->ExceptionClear(env);
        } else {
            g_player_location_cls = (jclass)(*env)->NewGlobalRef(env, local_cls);
            (*env)->DeleteLocalRef(env, local_cls);
        }
    }

    if (g_player_location_ctor == NULL && g_player_location_cls != NULL) {
        g_player_location_ctor = (*env)->GetMethodID(
            env,
            g_player_location_cls,
            "<init>",
            PLAYER_LOCATION_SIG
        );

        if (!g_player_location_ctor) {
            LOGE("Failed to find PlayerLocation constructor with signature: %s", PLAYER_LOCATION_SIG);
            (*env)->ExceptionClear(env);
        } else {
            LOGI("PlayerLocation constructor resolved successfully");
        }
    }
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    (void)reserved;
    JNIEnv* env = NULL;
    if ((*vm)->GetEnv(vm, (void**)&env, JNI_VERSION_1_6) != JNI_OK) {
        LOGE("JNI_OnLoad: GetEnv failed");
        return JNI_ERR;
    }

    init_class_cache(env);
    LOGI("DualDex JNI_OnLoad completed");
    return JNI_VERSION_1_6;
}

static jobject create_parsed_pokemon_object(JNIEnv* env, const ParsedPokemon* p) {
    if (!g_parsed_pokemon_cls || !g_parsed_pokemon_ctor) {
        init_class_cache(env);
        if (!g_parsed_pokemon_cls || !g_parsed_pokemon_ctor) return NULL;
    }

    jstring j_nickname = (*env)->NewStringUTF(env, p->nickname);
    jstring j_otname = (*env)->NewStringUTF(env, p->ot_name);
    jstring j_nature_name = (*env)->NewStringUTF(env, p->nature_name ? p->nature_name : "");

    jintArray j_moves = (*env)->NewIntArray(env, 4);
    if (!j_moves) return NULL;
    jint moves_buf[4];
    for (int i = 0; i < 4; i++) moves_buf[i] = p->moves[i];
    (*env)->SetIntArrayRegion(env, j_moves, 0, 4, moves_buf);

    jintArray j_pp = (*env)->NewIntArray(env, 4);
    if (!j_pp) { (*env)->DeleteLocalRef(env, j_moves); return NULL; }
    jint pp_buf[4];
    for (int i = 0; i < 4; i++) pp_buf[i] = p->pp[i];
    (*env)->SetIntArrayRegion(env, j_pp, 0, 4, pp_buf);

    jobject obj = (*env)->NewObject(
        env,
        g_parsed_pokemon_cls,
        g_parsed_pokemon_ctor,
        (jboolean)p->is_valid,
        (jboolean)p->is_empty,
        (jlong)p->pid,
        (jint)p->tid,
        (jint)p->sid,
        j_nickname,
        j_otname,
        (jint)p->species,
        (jint)p->held_item,
        (jint)p->level,
        (jint)p->nature,
        j_nature_name,
        (jboolean)p->is_shiny,
        (jint)p->ability_slot,
        (jboolean)p->is_egg,
        (jint)p->friendship,
        (jlong)p->experience,
        (jint)p->hp_iv,
        (jint)p->attack_iv,
        (jint)p->defense_iv,
        (jint)p->speed_iv,
        (jint)p->sp_attack_iv,
        (jint)p->sp_defense_iv,
        (jint)p->hp_ev,
        (jint)p->attack_ev,
        (jint)p->defense_ev,
        (jint)p->speed_ev,
        (jint)p->sp_attack_ev,
        (jint)p->sp_defense_ev,
        j_moves,
        j_pp,
        (jint)p->current_hp,
        (jint)p->max_hp,
        (jint)p->attack,
        (jint)p->defense,
        (jint)p->speed,
        (jint)p->sp_attack,
        (jint)p->sp_defense,
        (jlong)p->status_condition,
        (jint)p->hidden_nature,
        (jboolean)p->nature_modified,
        (jboolean)p->gigantamax_factor,
        (jint)p->shiny_state,
        (jint)p->shiny_modifier
    );

    if (!obj) {
        LOGE("create_parsed_pokemon_object: NewObject returned NULL! Species: %d", p->species);
        if ((*env)->ExceptionCheck(env)) {
            (*env)->ExceptionDescribe(env);
            (*env)->ExceptionClear(env);
        }
    }

    (*env)->DeleteLocalRef(env, j_nickname);
    (*env)->DeleteLocalRef(env, j_otname);
    (*env)->DeleteLocalRef(env, j_nature_name);
    (*env)->DeleteLocalRef(env, j_moves);
    (*env)->DeleteLocalRef(env, j_pp);

    return obj;
}

// -------------------------------------------------------------
// Pokemon Memory Reader JNI
// -------------------------------------------------------------

/**
 * Adapter that lets the pure native readers reach an absolute emulated GBA address through the
 * memory-map regions the core published. Every access stays bounds-checked inside
 * libretro_host_read_gba_address, so no raw pointer arithmetic escapes to Kotlin or JNI.
 */
static bool jni_gba_read_cb(void* user, uint32_t gba_address, uint8_t* out, size_t length) {
    (void)user;
    return libretro_host_read_gba_address(gba_address, out, length);
}

JNIEXPORT jint JNICALL
Java_com_dualdex_pokemon_PokemonBridge_detectGame(JNIEnv* env, jobject thiz, jstring rom_title) {
    (void)thiz;
    if (!rom_title) return GAME_UNKNOWN;

    const char* title_str = (*env)->GetStringUTFChars(env, rom_title, NULL);
    GbaGameId game_id = pokemon_detect_game(title_str);
    (*env)->ReleaseStringUTFChars(env, rom_title, title_str);

    return (jint)game_id;
}

JNIEXPORT jobject JNICALL
Java_com_dualdex_pokemon_PokemonBridge_parsePokemon(
    JNIEnv* env,
    jobject thiz,
    jbyteArray raw_bytes,
    jboolean is_party_mon
) {
    (void)thiz;
    if (!raw_bytes) return NULL;

    jsize len = (*env)->GetArrayLength(env, raw_bytes);
    if (len < (is_party_mon ? 100 : 80)) return NULL;

    jbyte* bytes = (*env)->GetByteArrayElements(env, raw_bytes, NULL);
    ParsedPokemon parsed;
    bool ok = pokemon_parse_single((const uint8_t*)bytes, (bool)is_party_mon, &parsed);
    (*env)->ReleaseByteArrayElements(env, raw_bytes, bytes, JNI_ABORT);

    if (!ok && !parsed.is_empty) return NULL;

    return create_parsed_pokemon_object(env, &parsed);
}

JNIEXPORT jobjectArray JNICALL
Java_com_dualdex_pokemon_PokemonBridge_readPlayerParty(
    JNIEnv* env,
    jobject thiz,
    jbyteArray ewram_bytes,
    jint game_id
) {
    (void)thiz;
    init_class_cache(env);
    if (!g_parsed_pokemon_cls) return NULL;

    if (!ewram_bytes) {
        return (*env)->NewObjectArray(env, 0, g_parsed_pokemon_cls, NULL);
    }

    jsize len = (*env)->GetArrayLength(env, ewram_bytes);
    jbyte* bytes = (*env)->GetByteArrayElements(env, ewram_bytes, NULL);

    const GameMemoryConfig* cfg = pokemon_get_game_config((GbaGameId)game_id);
    if (!cfg) {
        (*env)->ReleaseByteArrayElements(env, ewram_bytes, bytes, JNI_ABORT);
        return (*env)->NewObjectArray(env, 0, g_parsed_pokemon_cls, NULL);
    }

    PartySnapshot snapshot;
    uint8_t count = pokemon_read_player_party((const uint8_t*)bytes, (size_t)len, cfg, &snapshot);
    (*env)->ReleaseByteArrayElements(env, ewram_bytes, bytes, JNI_ABORT);

    jobjectArray array = (*env)->NewObjectArray(env, count, g_parsed_pokemon_cls, NULL);
    if (!array) return NULL;
    for (uint8_t i = 0; i < count; i++) {
        jobject p_obj = create_parsed_pokemon_object(env, &snapshot.members[i]);
        if (p_obj) {
            (*env)->SetObjectArrayElement(env, array, i, p_obj);
            (*env)->DeleteLocalRef(env, p_obj);
        }
    }

    return array;
}

JNIEXPORT jobjectArray JNICALL
Java_com_dualdex_pokemon_PokemonBridge_readEnemyParty(
    JNIEnv* env,
    jobject thiz,
    jbyteArray ewram_bytes,
    jint game_id
) {
    (void)thiz;
    init_class_cache(env);
    if (!g_parsed_pokemon_cls) return NULL;

    if (!ewram_bytes) {
        return (*env)->NewObjectArray(env, 0, g_parsed_pokemon_cls, NULL);
    }

    jsize len = (*env)->GetArrayLength(env, ewram_bytes);
    jbyte* bytes = (*env)->GetByteArrayElements(env, ewram_bytes, NULL);

    const GameMemoryConfig* cfg = pokemon_get_game_config((GbaGameId)game_id);
    if (!cfg) {
        (*env)->ReleaseByteArrayElements(env, ewram_bytes, bytes, JNI_ABORT);
        return (*env)->NewObjectArray(env, 0, g_parsed_pokemon_cls, NULL);
    }

    PartySnapshot snapshot;
    uint8_t count = pokemon_read_enemy_party((const uint8_t*)bytes, (size_t)len, cfg, &snapshot);
    (*env)->ReleaseByteArrayElements(env, ewram_bytes, bytes, JNI_ABORT);

    jobjectArray array = (*env)->NewObjectArray(env, count, g_parsed_pokemon_cls, NULL);
    if (!array) return NULL;
    for (uint8_t i = 0; i < count; i++) {
        jobject p_obj = create_parsed_pokemon_object(env, &snapshot.members[i]);
        if (p_obj) {
            (*env)->SetObjectArrayElement(env, array, i, p_obj);
            (*env)->DeleteLocalRef(env, p_obj);
        }
    }

    return array;
}

JNIEXPORT jobject JNICALL
Java_com_dualdex_pokemon_PokemonBridge_readPlayerLocation(
    JNIEnv* env,
    jobject thiz,
    jbyteArray ewram_bytes,
    jint game_id
) {
    (void)thiz;
    init_class_cache(env);
    if (!g_player_location_cls || !g_player_location_ctor) return NULL;
    if (!ewram_bytes) return NULL;

    jsize len = (*env)->GetArrayLength(env, ewram_bytes);
    jbyte* bytes = (*env)->GetByteArrayElements(env, ewram_bytes, NULL);

    const GameMemoryConfig* cfg = pokemon_get_game_config((GbaGameId)game_id);
    if (!cfg) {
        (*env)->ReleaseByteArrayElements(env, ewram_bytes, bytes, JNI_ABORT);
        return NULL;
    }

    PlayerLocationRaw loc;
    // This entry point only receives an EWRAM snapshot and has no IWRAM access, so a layout that
    // resolves SaveBlock1 through the IWRAM gSaveBlock1Ptr symbol (Heart & Soul 2.0.5) fails
    // closed here. LibretroHost.nativeReadPlayerLocation uses the region-checked reader instead.
    bool ok = pokemon_read_player_location((const uint8_t*)bytes, (size_t)len, cfg, &loc);
    (*env)->ReleaseByteArrayElements(env, ewram_bytes, bytes, JNI_ABORT);

    if (!ok || !loc.is_valid) return NULL;

    return (*env)->NewObject(
        env,
        g_player_location_cls,
        g_player_location_ctor,
        (jint)loc.map_group,
        (jint)loc.map_num,
        (jint)loc.warp_id,
        (jint)loc.x,
        (jint)loc.y,
        (jint)loc.local_x,
        (jint)loc.local_y,
        (jint)loc.escape_map_group,
        (jint)loc.escape_map_num,
        (jboolean)loc.is_indoors,
        (jboolean)loc.is_valid
    );
}

// -------------------------------------------------------------
// Libretro Emulator Host JNI
// -------------------------------------------------------------

JNIEXPORT jboolean JNICALL
Java_com_dualdex_emulator_LibretroHost_nativeLoadCore(JNIEnv* env, jobject thiz, jstring core_path) {
    (void)thiz;
    if (!core_path) return JNI_FALSE;

    const char* path_str = (*env)->GetStringUTFChars(env, core_path, NULL);
    bool ok = libretro_host_init(path_str);
    (*env)->ReleaseStringUTFChars(env, core_path, path_str);

    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_dualdex_emulator_LibretroHost_nativeLoadRom(JNIEnv* env, jobject thiz, jstring rom_path) {
    (void)thiz;
    if (!rom_path) return JNI_FALSE;

    const char* path_str = (*env)->GetStringUTFChars(env, rom_path, NULL);
    bool ok = libretro_host_load_rom(path_str);
    (*env)->ReleaseStringUTFChars(env, rom_path, path_str);

    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_dualdex_emulator_LibretroHost_nativeStepFrame(JNIEnv* env, jobject thiz) {
    (void)env;
    (void)thiz;
    libretro_host_step_frame();
}

JNIEXPORT void JNICALL
Java_com_dualdex_emulator_LibretroHost_nativeSetInputButtons(JNIEnv* env, jobject thiz, jint button_mask) {
    (void)env;
    (void)thiz;
    libretro_host_set_input_buttons((uint32_t)button_mask);
}

JNIEXPORT jboolean JNICALL
Java_com_dualdex_emulator_LibretroHost_nativeGetVideoFrame(
    JNIEnv* env,
    jobject thiz,
    jobject direct_buffer,
    jintArray out_metadata
) {
    (void)thiz;
    void* dst = (*env)->GetDirectBufferAddress(env, direct_buffer);
    if (!dst) return JNI_FALSE;

    jlong dst_capacity = (*env)->GetDirectBufferCapacity(env, direct_buffer);
    if (dst_capacity <= 0) return JNI_FALSE;

    unsigned int w = 0, h = 0;
    size_t pitch = 0;
    int fmt = 0;

    bool ok = libretro_host_copy_video_frame(dst, (size_t)dst_capacity, &w, &h, &pitch, &fmt);
    if (!ok) return JNI_FALSE;

    if (out_metadata) {
        jint meta[4] = {
            (jint)w,
            (jint)h,
            (jint)pitch,
            (jint)fmt
        };
        (*env)->SetIntArrayRegion(env, out_metadata, 0, 4, meta);
    }

    return JNI_TRUE;
}

JNIEXPORT jint JNICALL
Java_com_dualdex_emulator_LibretroHost_nativeGetAudioSamples(
    JNIEnv* env,
    jobject thiz,
    jshortArray out_buffer
) {
    (void)thiz;
    if (!out_buffer) return 0;
    jsize len = (*env)->GetArrayLength(env, out_buffer);
    jshort* buf = (*env)->GetShortArrayElements(env, out_buffer, NULL);
    size_t read = libretro_host_get_audio_samples((int16_t*)buf, (size_t)len);
    (*env)->ReleaseShortArrayElements(env, out_buffer, buf, 0);
    return (jint)read;
}

JNIEXPORT void JNICALL
Java_com_dualdex_emulator_LibretroHost_nativeClearAudio(
    JNIEnv* env,
    jobject thiz
) {
    (void)env;
    (void)thiz;
    libretro_host_clear_audio();
}

JNIEXPORT void JNICALL
Java_com_dualdex_emulator_LibretroHost_nativeSetTargetAudioSampleRate(
    JNIEnv* env,
    jobject thiz,
    jint rate
) {
    (void)env;
    (void)thiz;
    libretro_host_set_target_audio_sample_rate((uint32_t)rate);
}

JNIEXPORT jint JNICALL
Java_com_dualdex_emulator_LibretroHost_nativeGetOutputAudioSampleRate(
    JNIEnv* env,
    jobject thiz
) {
    (void)env;
    (void)thiz;
    return (jint)libretro_host_get_output_sample_rate();
}

/**
 * Last observed active-battler results.
 *
 * `known` is tracked next to each slot on purpose: a slot of 0 is a real party slot, so it can
 * never double as "unknown". Callers receive -1 unless the native reader resolved the slot from
 * authoritative battler state in the same read that produced the party.
 */
static int8_t  s_last_active_battler_slot = -1;
static bool    s_last_active_battler_known = false;
static int8_t  s_last_active_enemy_battler_slot = -1;
static bool    s_last_active_enemy_battler_known = false;
static int8_t  s_last_active_enemy_state = ACTIVE_ENEMY_UNKNOWN;
static int8_t  s_last_active_enemy_battler_index = -1;
static uint8_t s_last_opponent_battler_count = 0;
static bool    s_last_active_enemy_fainted = false;

/**
 * Bounds-checked absolute-address reader handed to the production native readers.
 *
 * Heart & Soul 2.0.5 keeps the authoritative battle-lifecycle flag (`gMain.inBattle`) in IWRAM
 * while the battle globals live in EWRAM, so the readers need a region-checked reader rather
 * than only the EWRAM pointer. libretro_host_read_gba_address performs that check against the
 * region table the core published and returns false for anything unmapped.
 */
static bool dualdex_jni_gba_read(void* user, uint32_t gba_address, uint8_t* out, size_t length) {
    (void)user;
    return libretro_host_read_gba_address(gba_address, out, length);
}

JNIEXPORT jobjectArray JNICALL
Java_com_dualdex_emulator_LibretroHost_nativeReadPartyFromCore(JNIEnv* env, jobject thiz, jint game_id) {
    (void)thiz;
    init_class_cache(env);
    if (!g_parsed_pokemon_cls) {
        LOGE("nativeReadPartyFromCore: g_parsed_pokemon_cls is NULL!");
        return NULL;
    }

    size_t ewram_sz = 0;
    uint8_t* ewram = libretro_host_get_ewram(&ewram_sz);
    if (!ewram || ewram_sz == 0) {
        return NULL;
    }

    const GameMemoryConfig* cfg = pokemon_get_game_config((GbaGameId)game_id);
    if (!cfg) {
        // Fail closed: an unknown/unsupported game has no verified memory layout, so no parsing is
        // attempted and no previously observed battler slot may survive.
        s_last_active_battler_slot = -1;
        s_last_active_battler_known = false;
        LOGI("nativeReadPartyFromCore: game_id=%d has no supported layout; party unavailable", game_id);
        return NULL;
    }

    PartySnapshot snapshot;
    uint8_t count = pokemon_read_player_party_gba(
        dualdex_jni_gba_read, NULL, ewram, ewram_sz, cfg, &snapshot);
    s_last_active_battler_known = snapshot.active_battler_known;
    s_last_active_battler_slot = snapshot.active_battler_known ? snapshot.active_battler_slot : -1;

    static int s_party_log_counter = 0;
    if ((++s_party_log_counter % 30) == 1) {
        LOGI("nativeReadPartyFromCore: game_id=%d, ewram_sz=%zu, count=%d, lead_species=%d, lead_nickname='%s', lead_level=%d",
             game_id, ewram_sz, count,
             count > 0 ? snapshot.members[0].species : 0,
             count > 0 ? snapshot.members[0].nickname : "None",
             count > 0 ? snapshot.members[0].level : 0);
    }

    jobjectArray array = (*env)->NewObjectArray(env, count, g_parsed_pokemon_cls, NULL);
    if (!array) return NULL;
    for (uint8_t i = 0; i < count; i++) {
        jobject p_obj = create_parsed_pokemon_object(env, &snapshot.members[i]);
        if (p_obj) {
            (*env)->SetObjectArrayElement(env, array, i, p_obj);
            (*env)->DeleteLocalRef(env, p_obj);
        } else {
            LOGE("nativeReadPartyFromCore: create_parsed_pokemon_object returned NULL for slot %d (species=%d)!",
                 i, snapshot.members[i].species);
        }
    }

    return array;
}

JNIEXPORT jobjectArray JNICALL
Java_com_dualdex_emulator_LibretroHost_nativeReadEnemyPartyFromCore(JNIEnv* env, jobject thiz, jint game_id) {
    (void)thiz;
    init_class_cache(env);
    if (!g_parsed_pokemon_cls) {
        LOGE("nativeReadEnemyPartyFromCore: g_parsed_pokemon_cls is NULL!");
        return NULL;
    }

    size_t ewram_sz = 0;
    uint8_t* ewram = libretro_host_get_ewram(&ewram_sz);
    if (!ewram || ewram_sz == 0) {
        return NULL;
    }

    const GameMemoryConfig* cfg = pokemon_get_game_config((GbaGameId)game_id);
    if (!cfg) {
        s_last_active_enemy_battler_slot = -1;
        s_last_active_enemy_battler_known = false;
        s_last_active_enemy_state = ACTIVE_ENEMY_UNKNOWN;
        s_last_active_enemy_battler_index = -1;
        s_last_opponent_battler_count = 0;
        s_last_active_enemy_fainted = false;
        LOGI("nativeReadEnemyPartyFromCore: game_id=%d has no supported layout; enemy party unavailable", game_id);
        return NULL;
    }

    PartySnapshot snapshot;
    ActiveEnemyInfo enemy_info;
    uint8_t count = pokemon_read_enemy_party_gba(
        dualdex_jni_gba_read, NULL, ewram, ewram_sz, cfg, &snapshot);
    pokemon_resolve_active_enemy(dualdex_jni_gba_read, NULL, ewram, ewram_sz, cfg, &snapshot, &enemy_info);

    s_last_active_enemy_state = (int8_t)enemy_info.state;
    s_last_active_enemy_battler_index = enemy_info.battler_index;
    s_last_opponent_battler_count = enemy_info.opponent_battlers;
    s_last_active_enemy_fainted = enemy_info.fainted;
    s_last_active_enemy_battler_known = (enemy_info.state == ACTIVE_ENEMY_SLOT);
    s_last_active_enemy_battler_slot =
        (enemy_info.state == ACTIVE_ENEMY_SLOT) ? enemy_info.party_slot : -1;

    jobjectArray array = (*env)->NewObjectArray(env, count, g_parsed_pokemon_cls, NULL);
    if (!array) return NULL;
    for (uint8_t i = 0; i < count; i++) {
        jobject p_obj = create_parsed_pokemon_object(env, &snapshot.members[i]);
        if (p_obj) {
            (*env)->SetObjectArrayElement(env, array, i, p_obj);
            (*env)->DeleteLocalRef(env, p_obj);
        }
    }

    return array;
}

JNIEXPORT jint JNICALL
Java_com_dualdex_emulator_LibretroHost_nativeGetActiveBattlerSlot(JNIEnv* env, jobject thiz, jint game_id) {
    (void)env;
    (void)thiz;
    (void)game_id;
    // -1 is the only "unknown" answer. Slot 0 is never used as a stand-in for it.
    return s_last_active_battler_known ? (jint)s_last_active_battler_slot : -1;
}

JNIEXPORT jint JNICALL
Java_com_dualdex_emulator_LibretroHost_nativeGetActiveEnemyBattlerSlot(JNIEnv* env, jobject thiz, jint game_id) {
    (void)env;
    (void)thiz;
    (void)game_id;
    return s_last_active_enemy_battler_known ? (jint)s_last_active_enemy_battler_slot : -1;
}

/**
 * Active-opponent resolution as an explicit tuple rather than an ambiguous integer.
 *
 * Layout: [0] ActiveEnemyState, [1] opponent battler index (-1 unknown),
 *         [2] enemy party slot (-1 unknown/ambiguous/none), [3] active opponent battler count,
 *         [4] fainted flag (1 when the resolved opponent is at 0 HP).
 *
 * Returning the state alongside the slot is what removes the old "is 0 a slot or 'unknown'?"
 * ambiguity: a caller that needs a slot must check element [0] == ACTIVE_ENEMY_SLOT.
 */
JNIEXPORT jintArray JNICALL
Java_com_dualdex_emulator_LibretroHost_nativeResolveActiveEnemy(JNIEnv* env, jobject thiz, jint game_id) {
    (void)thiz;
    size_t ewram_sz = 0;
    uint8_t* ewram = libretro_host_get_ewram(&ewram_sz);

    jint values[5] = {
        (jint)ACTIVE_ENEMY_UNKNOWN, -1, -1, 0, 0
    };

    const GameMemoryConfig* cfg = pokemon_get_game_config((GbaGameId)game_id);
    if (ewram && ewram_sz > 0 && cfg) {
        PartySnapshot snapshot;
        ActiveEnemyInfo info;
        pokemon_resolve_active_enemy(dualdex_jni_gba_read, NULL, ewram, ewram_sz, cfg, &snapshot, &info);

        values[0] = (jint)info.state;
        values[1] = (jint)info.battler_index;
        values[2] = (jint)info.party_slot;
        values[3] = (jint)info.opponent_battlers;
        values[4] = info.fainted ? 1 : 0;

        s_last_active_enemy_state = (int8_t)info.state;
        s_last_active_enemy_battler_index = info.battler_index;
        s_last_opponent_battler_count = info.opponent_battlers;
        s_last_active_enemy_fainted = info.fainted;
        s_last_active_enemy_battler_known = (info.state == ACTIVE_ENEMY_SLOT);
        s_last_active_enemy_battler_slot = (info.state == ACTIVE_ENEMY_SLOT) ? info.party_slot : -1;
    }

    jintArray result = (*env)->NewIntArray(env, 5);
    if (!result) return NULL;
    (*env)->SetIntArrayRegion(env, result, 0, 5, values);
    return result;
}

JNIEXPORT jintArray JNICALL
Java_com_dualdex_emulator_LibretroHost_nativeReadBattleStatStages(JNIEnv* env, jobject thiz, jint game_id, jint battler_index) {
    (void)thiz;
    size_t ewram_sz = 0;
    uint8_t* ewram = libretro_host_get_ewram(&ewram_sz);
    if (!ewram || ewram_sz == 0) return NULL;

    const GameMemoryConfig* cfg = pokemon_get_game_config((GbaGameId)game_id);
    if (!cfg) return NULL;

    int8_t stages[7];
    bool ok = pokemon_read_battle_stat_stages(ewram, ewram_sz, cfg, (uint8_t)battler_index, stages);
    if (!ok) return NULL;

    jintArray result = (*env)->NewIntArray(env, 7);
    if (!result) return NULL;

    jint int_stages[7];
    for (int i = 0; i < 7; i++) {
        int_stages[i] = (jint)stages[i];
    }
    (*env)->SetIntArrayRegion(env, result, 0, 7, int_stages);
    return result;
}

JNIEXPORT jint JNICALL
Java_com_dualdex_emulator_LibretroHost_nativeReadBattleUiState(JNIEnv* env, jobject thiz, jint game_id) {
    (void)env;
    (void)thiz;
    size_t ewram_sz = 0;
    uint8_t* ewram = libretro_host_get_ewram(&ewram_sz);
    if (!ewram || ewram_sz == 0) return 0;

    const GameMemoryConfig* cfg = pokemon_get_game_config((GbaGameId)game_id);
    if (!cfg) return 0;
    return (jint)pokemon_read_battle_ui_state(ewram, ewram_sz, cfg);
}

JNIEXPORT jint JNICALL
Java_com_dualdex_emulator_LibretroHost_nativeReadBattlePresence(JNIEnv* env, jobject thiz, jint game_id) {
    (void)env;
    (void)thiz;
    size_t ewram_sz = 0;
    uint8_t* ewram = libretro_host_get_ewram(&ewram_sz);
    if (!ewram || ewram_sz == 0) return 2;

    const GameMemoryConfig* cfg = pokemon_get_game_config((GbaGameId)game_id);
    if (!cfg) return 2;
    // Authoritative presence: for a layout that declares the lifecycle gate (Heart & Soul 2.0.5
    // gMain.inBattle) this is the ONLY source of truth, so a stale gBattleMons species word can no
    // longer present itself as a running battle. Legacy layouts keep their historical reading.
    return (jint)pokemon_read_battle_presence_gba(
        dualdex_jni_gba_read, NULL, ewram, ewram_sz, cfg);
}

JNIEXPORT jobject JNICALL
Java_com_dualdex_emulator_LibretroHost_nativeReadPlayerLocation(JNIEnv* env, jobject thiz, jint game_id) {
    (void)thiz;
    init_class_cache(env);
    if (!g_player_location_cls || !g_player_location_ctor) {
        LOGE("nativeReadPlayerLocation: g_player_location_cls is NULL!");
        return NULL;
    }

    size_t ewram_sz = 0;
    uint8_t* ewram = libretro_host_get_ewram(&ewram_sz);
    if (!ewram || ewram_sz == 0) {
        return NULL;
    }

    const GameMemoryConfig* cfg = pokemon_get_game_config((GbaGameId)game_id);
    if (!cfg) return NULL; // Fail closed: unknown game has no verified SaveBlock1 layout.

    PlayerLocationRaw loc;
    bool ok = pokemon_read_player_location_gba(
        jni_gba_read_cb, NULL, ewram, ewram_sz, cfg, &loc);
    if (!ok || !loc.is_valid) {
        return NULL;
    }

    return (*env)->NewObject(
        env,
        g_player_location_cls,
        g_player_location_ctor,
        (jint)loc.map_group,
        (jint)loc.map_num,
        (jint)loc.warp_id,
        (jint)loc.x,
        (jint)loc.y,
        (jint)loc.local_x,
        (jint)loc.local_y,
        (jint)loc.escape_map_group,
        (jint)loc.escape_map_num,
        (jboolean)loc.is_indoors,
        (jboolean)loc.is_valid
    );
}

JNIEXPORT jboolean JNICALL
Java_com_dualdex_emulator_LibretroHost_nativeSaveState(JNIEnv* env, jobject thiz, jstring state_path) {
    (void)thiz;
    if (!state_path) return JNI_FALSE;
    const char* path_str = (*env)->GetStringUTFChars(env, state_path, NULL);
    bool ok = libretro_host_save_state(path_str);
    (*env)->ReleaseStringUTFChars(env, state_path, path_str);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_dualdex_emulator_LibretroHost_nativeLoadState(JNIEnv* env, jobject thiz, jstring state_path) {
    (void)thiz;
    if (!state_path) return JNI_FALSE;
    const char* path_str = (*env)->GetStringUTFChars(env, state_path, NULL);
    bool ok = libretro_host_load_state(path_str);
    (*env)->ReleaseStringUTFChars(env, state_path, path_str);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_dualdex_emulator_LibretroHost_nativeLoadSaveRam(JNIEnv* env, jobject thiz, jstring save_path) {
    (void)thiz;
    if (!save_path) return JNI_FALSE;
    const char* path_str = (*env)->GetStringUTFChars(env, save_path, NULL);
    bool ok = libretro_host_load_save_ram(path_str);
    (*env)->ReleaseStringUTFChars(env, save_path, path_str);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_dualdex_emulator_LibretroHost_nativeFlushSaveRam(JNIEnv* env, jobject thiz, jstring save_path) {
    (void)thiz;
    if (!save_path) return JNI_FALSE;
    const char* path_str = (*env)->GetStringUTFChars(env, save_path, NULL);
    bool ok = libretro_host_flush_save_ram(path_str);
    (*env)->ReleaseStringUTFChars(env, save_path, path_str);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_dualdex_emulator_LibretroHost_nativeUnloadRom(JNIEnv* env, jobject thiz) {
    (void)env;
    (void)thiz;
    return libretro_host_unload_rom() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jlong JNICALL
Java_com_dualdex_emulator_LibretroHost_nativeGetSaveRamSize(JNIEnv* env, jobject thiz) {
    (void)env;
    (void)thiz;
    return (jlong)libretro_host_get_save_ram_size();
}

JNIEXPORT jlong JNICALL
Java_com_dualdex_emulator_LibretroHost_nativeGetSaveStateSize(JNIEnv* env, jobject thiz) {
    (void)env;
    (void)thiz;
    return (jlong)libretro_host_get_save_state_size();
}

JNIEXPORT void JNICALL
Java_com_dualdex_emulator_LibretroHost_nativeResetCore(JNIEnv* env, jobject thiz) {
    (void)env;
    (void)thiz;
    libretro_host_reset();
}

JNIEXPORT void JNICALL
Java_com_dualdex_emulator_LibretroHost_nativeCleanup(JNIEnv* env, jobject thiz) {
    (void)env;
    (void)thiz;
    libretro_host_cleanup();
}

JNIEXPORT jdouble JNICALL
Java_com_dualdex_emulator_LibretroHost_nativeGetTargetFps(JNIEnv* env, jobject thiz) {
    (void)env;
    (void)thiz;
    return (jdouble)libretro_host_get_target_fps();
}

JNIEXPORT jdouble JNICALL
Java_com_dualdex_emulator_LibretroHost_nativeGetAudioSampleRate(JNIEnv* env, jobject thiz) {
    (void)env;
    (void)thiz;
    return (jdouble)libretro_host_get_sample_rate();
}

JNIEXPORT void JNICALL
Java_com_dualdex_emulator_LibretroHost_nativeCheatReset(JNIEnv* env, jobject thiz) {
    (void)env;
    (void)thiz;
    libretro_host_cheat_reset();
}

JNIEXPORT void JNICALL
Java_com_dualdex_emulator_LibretroHost_nativeCheatSet(JNIEnv* env, jobject thiz, jint index, jboolean enabled, jstring j_code) {
    (void)thiz;
    if (!j_code) return;
    const char* code_str = (*env)->GetStringUTFChars(env, j_code, NULL);
    if (code_str) {
        libretro_host_cheat_set((unsigned)index, (bool)enabled, code_str);
        (*env)->ReleaseStringUTFChars(env, j_code, code_str);
    }
}

// -------------------------------------------------------------
// QuickJS Damage Calculator JNI
// -------------------------------------------------------------

JNIEXPORT jboolean JNICALL
Java_com_dualdex_calculator_DamageCalculator_nativeInit(JNIEnv* env, jobject thiz, jstring bundle_js) {
    (void)thiz;
    if (!bundle_js) return JNI_FALSE;

    const char* js_str = (*env)->GetStringUTFChars(env, bundle_js, NULL);
    bool ok = js_calc_init(js_str);
    (*env)->ReleaseStringUTFChars(env, bundle_js, js_str);

    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_dualdex_calculator_DamageCalculator_nativeCalculate(JNIEnv* env, jobject thiz, jstring input_json) {
    (void)thiz;
    if (!input_json) return NULL;

    const char* json_str = (*env)->GetStringUTFChars(env, input_json, NULL);
    char* result_str = js_calc_calculate(json_str);
    (*env)->ReleaseStringUTFChars(env, input_json, json_str);

    if (!result_str) return NULL;

    jstring j_out = (*env)->NewStringUTF(env, result_str);
    free(result_str);
    return j_out;
}

JNIEXPORT void JNICALL
Java_com_dualdex_calculator_DamageCalculator_nativeCleanup(JNIEnv* env, jobject thiz) {
    (void)env;
    (void)thiz;
    js_calc_cleanup();
}
