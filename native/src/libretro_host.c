#include "libretro_host.h"
#include "libretro.h"
#include "pokemon_reader.h"
#include <dlfcn.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <pthread.h>
#include <unistd.h>

// Core function pointers
static void (*p_retro_init)(void);
static void (*p_retro_deinit)(void);
static unsigned (*p_retro_api_version)(void);
static void (*p_retro_set_environment)(retro_environment_t);
static void (*p_retro_set_video_refresh)(retro_video_refresh_t);
static void (*p_retro_set_audio_sample)(retro_audio_sample_t);
static void (*p_retro_set_audio_sample_batch)(retro_audio_sample_batch_t);
static void (*p_retro_set_input_poll)(retro_input_poll_t);
static void (*p_retro_set_input_state)(retro_input_state_t);
static void (*p_retro_set_controller_port_device)(unsigned, unsigned);
static void (*p_retro_reset)(void);
static void (*p_retro_run)(void);
static size_t (*p_retro_serialize_size)(void);
static bool (*p_retro_serialize)(void*, size_t);
static bool (*p_retro_unserialize)(const void*, size_t);
static bool (*p_retro_load_game)(const struct retro_game_info*);
static void (*p_retro_unload_game)(void);
static void (*p_retro_get_system_info)(struct retro_system_info*);
static void (*p_retro_get_system_av_info)(struct retro_system_av_info*);
static void* (*p_retro_get_memory_data)(unsigned);
static size_t (*p_retro_get_memory_size)(unsigned);
static void (*p_retro_cheat_reset)(void);
static void (*p_retro_cheat_set)(unsigned, bool, const char*);

static void* g_core_handle = NULL;
static bool g_is_game_loaded = false;
static uint32_t g_current_buttons = 0;
static enum retro_pixel_format g_pixel_format = RETRO_PIXEL_FORMAT_RGB565;
static double g_target_fps = 59.7275;
static double g_audio_sample_rate = 32768.0;

// Framebuffer storage (GBA max native resolution 240x160)
#define MAX_FB_WIDTH 512
#define MAX_FB_HEIGHT 512
static uint8_t g_fb_storage[MAX_FB_WIDTH * MAX_FB_HEIGHT * 4];
static EmulatorVideoFrame g_current_frame = {0};
static pthread_mutex_t g_video_mutex = PTHREAD_MUTEX_INITIALIZER;

// Environment callback implementation
static bool core_environment_cb(unsigned cmd, void *data) {
    switch (cmd) {
        case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT: {
            const enum retro_pixel_format *fmt = (const enum retro_pixel_format *)data;
            if (!fmt) return false;
            if (*fmt == RETRO_PIXEL_FORMAT_RGB565 ||
                *fmt == RETRO_PIXEL_FORMAT_XRGB8888 ||
                *fmt == RETRO_PIXEL_FORMAT_0RGB1555) {
                g_pixel_format = *fmt;
                return true;
            }
            return false;
        }
        case RETRO_ENVIRONMENT_GET_CAN_DUPE: {
            bool *b = (bool *)data;
            if (b) *b = true;
            return true;
        }
        case RETRO_ENVIRONMENT_GET_VARIABLE:
        case RETRO_ENVIRONMENT_SET_VARIABLES:
        case RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE:
            return false;
        default:
            return false;
    }
}

// Video refresh callback implementation
static void core_video_refresh_cb(const void *data, unsigned width, unsigned height, size_t pitch) {
    if (!data || width == 0 || height == 0) return;

    pthread_mutex_lock(&g_video_mutex);

    size_t bpp = (g_pixel_format == RETRO_PIXEL_FORMAT_XRGB8888) ? 4 : 2;
    size_t copy_size = width * height * bpp;
    if (copy_size > sizeof(g_fb_storage)) {
        copy_size = sizeof(g_fb_storage);
    }

    if (pitch == width * bpp) {
        memcpy(g_fb_storage, data, copy_size);
    } else {
        // Handle stride / pitch difference
        const uint8_t *src_row = (const uint8_t *)data;
        uint8_t *dst_row = g_fb_storage;
        size_t row_bytes = width * bpp;
        for (unsigned y = 0; y < height; y++) {
            if ((size_t)(dst_row - g_fb_storage) + row_bytes > sizeof(g_fb_storage)) break;
            memcpy(dst_row, src_row, row_bytes);
            src_row += pitch;
            dst_row += row_bytes;
        }
    }

    g_current_frame.width = width;
    g_current_frame.height = height;
    g_current_frame.pixel_format = (int)g_pixel_format;
    g_current_frame.pitch = width * bpp;
    g_current_frame.pixels = g_fb_storage;

    pthread_mutex_unlock(&g_video_mutex);
}

// Target audio rate requested by frontend/AudioTrack (default 48000 Hz)
#define DEFAULT_TARGET_AUDIO_RATE 48000
static uint32_t g_target_audio_rate = DEFAULT_TARGET_AUDIO_RATE;

// Native audio ring buffer (stores resampled stereo PCM)
// 16384 samples = 8192 stereo frames = ~170 ms capacity
#define AUDIO_RING_BUFFER_SIZE (16384)
static int16_t g_audio_ring_buffer[AUDIO_RING_BUFFER_SIZE];
static size_t g_audio_write_pos = 0;
static size_t g_audio_read_pos = 0;
static pthread_mutex_t g_audio_mutex = PTHREAD_MUTEX_INITIALIZER;

// Resampler persistent state for continuous phase across batches
static double g_resample_phase = 0.0;
static int16_t g_last_left = 0;
static int16_t g_last_right = 0;
static bool g_has_last_samples = false;

void libretro_host_clear_audio(void) {
    pthread_mutex_lock(&g_audio_mutex);
    g_audio_write_pos = 0;
    g_audio_read_pos = 0;
    g_resample_phase = 0.0;
    g_has_last_samples = false;
    pthread_mutex_unlock(&g_audio_mutex);
}

void libretro_host_set_target_audio_sample_rate(uint32_t rate) {
    if (rate >= 8000 && rate <= 96000) {
        pthread_mutex_lock(&g_audio_mutex);
        g_target_audio_rate = rate;
        g_resample_phase = 0.0;
        g_has_last_samples = false;
        pthread_mutex_unlock(&g_audio_mutex);
    }
}

uint32_t libretro_host_get_output_sample_rate(void) {
    return g_target_audio_rate;
}

static inline void push_audio_sample(int16_t left, int16_t right) {
    g_audio_ring_buffer[g_audio_write_pos] = left;
    g_audio_write_pos = (g_audio_write_pos + 1) % AUDIO_RING_BUFFER_SIZE;
    g_audio_ring_buffer[g_audio_write_pos] = right;
    g_audio_write_pos = (g_audio_write_pos + 1) % AUDIO_RING_BUFFER_SIZE;
}

static size_t core_audio_sample_batch_cb(const int16_t *data, size_t frames) {
    if (!data || frames == 0) return 0;

    pthread_mutex_lock(&g_audio_mutex);

    double in_rate = g_audio_sample_rate > 8000.0 ? g_audio_sample_rate : 65536.0;
    double out_rate = (double)g_target_audio_rate;

    if (in_rate == out_rate) {
        // Direct passthrough when input and output rates match
        size_t count = frames * 2;
        for (size_t i = 0; i < count; i++) {
            g_audio_ring_buffer[g_audio_write_pos] = data[i];
            g_audio_write_pos = (g_audio_write_pos + 1) % AUDIO_RING_BUFFER_SIZE;
        }
    } else {
        // High-precision linear interpolation resampler with continuous phase
        double ratio = in_rate / out_rate;
        while (g_resample_phase < (double)frames) {
            size_t idx0 = (size_t)g_resample_phase;
            double frac = g_resample_phase - (double)idx0;

            int16_t l0, r0, l1, r1;
            if (idx0 == 0 && g_has_last_samples && frac < 0.0001) {
                l0 = g_last_left;
                r0 = g_last_right;
            } else {
                l0 = data[idx0 * 2];
                r0 = data[idx0 * 2 + 1];
            }

            if (idx0 + 1 < frames) {
                l1 = data[(idx0 + 1) * 2];
                r1 = data[(idx0 + 1) * 2 + 1];
            } else {
                l1 = l0;
                r1 = r0;
            }

            int32_t out_l = (int32_t)(l0 + (l1 - l0) * frac);
            int32_t out_r = (int32_t)(r0 + (r1 - r0) * frac);
            if (out_l > 32767) out_l = 32767; else if (out_l < -32768) out_l = -32768;
            if (out_r > 32767) out_r = 32767; else if (out_r < -32768) out_r = -32768;

            push_audio_sample((int16_t)out_l, (int16_t)out_r);
            g_resample_phase += ratio;
        }

        g_resample_phase -= (double)frames;
        g_last_left = data[(frames - 1) * 2];
        g_last_right = data[(frames - 1) * 2 + 1];
        g_has_last_samples = true;
    }

    // Latency limiter: Cap backlog to 4096 samples (2048 stereo frames = ~42.6ms at 48kHz).
    // One emulation frame produces ~1607 samples at 48kHz, so 4096 is ~2.5 frames.
    // This allows normal frame jitter without ever dropping samples, while bounding lag.
    size_t available = (g_audio_write_pos >= g_audio_read_pos)
        ? (g_audio_write_pos - g_audio_read_pos)
        : (AUDIO_RING_BUFFER_SIZE - g_audio_read_pos + g_audio_write_pos);
    if (available > 4096) {
        g_audio_read_pos = (g_audio_write_pos + AUDIO_RING_BUFFER_SIZE - 2048) % AUDIO_RING_BUFFER_SIZE;
    }

    pthread_mutex_unlock(&g_audio_mutex);
    return frames;
}

static void core_audio_sample_cb(int16_t left, int16_t right) {
    int16_t buf[2] = {left, right};
    core_audio_sample_batch_cb(buf, 1);
}

size_t libretro_host_get_audio_samples(int16_t* out_buffer, size_t max_samples) {
    if (!out_buffer || max_samples == 0) return 0;

    pthread_mutex_lock(&g_audio_mutex);
    size_t available = (g_audio_write_pos >= g_audio_read_pos)
        ? (g_audio_write_pos - g_audio_read_pos)
        : (AUDIO_RING_BUFFER_SIZE - g_audio_read_pos + g_audio_write_pos);

    size_t to_read = (available < max_samples) ? available : max_samples;
    for (size_t i = 0; i < to_read; i++) {
        out_buffer[i] = g_audio_ring_buffer[g_audio_read_pos];
        g_audio_read_pos = (g_audio_read_pos + 1) % AUDIO_RING_BUFFER_SIZE;
    }
    pthread_mutex_unlock(&g_audio_mutex);
    return to_read;
}

// Input poll and state callbacks
static void core_input_poll_cb(void) {
    // No-op, polled externally
}

static int16_t core_input_state_cb(unsigned port, unsigned device, unsigned index, unsigned id) {
    (void)index;
    if (port != 0 || device != RETRO_DEVICE_JOYPAD) return 0;

    switch (id) {
        case RETRO_DEVICE_ID_JOYPAD_B:      return (g_current_buttons & DUALDEX_BTN_B) ? 1 : 0;
        case RETRO_DEVICE_ID_JOYPAD_Y:      return (g_current_buttons & DUALDEX_BTN_Y) ? 1 : 0;
        case RETRO_DEVICE_ID_JOYPAD_SELECT: return (g_current_buttons & DUALDEX_BTN_SELECT) ? 1 : 0;
        case RETRO_DEVICE_ID_JOYPAD_START:  return (g_current_buttons & DUALDEX_BTN_START) ? 1 : 0;
        case RETRO_DEVICE_ID_JOYPAD_UP:     return (g_current_buttons & DUALDEX_BTN_UP) ? 1 : 0;
        case RETRO_DEVICE_ID_JOYPAD_DOWN:   return (g_current_buttons & DUALDEX_BTN_DOWN) ? 1 : 0;
        case RETRO_DEVICE_ID_JOYPAD_LEFT:   return (g_current_buttons & DUALDEX_BTN_LEFT) ? 1 : 0;
        case RETRO_DEVICE_ID_JOYPAD_RIGHT:  return (g_current_buttons & DUALDEX_BTN_RIGHT) ? 1 : 0;
        case RETRO_DEVICE_ID_JOYPAD_A:      return (g_current_buttons & DUALDEX_BTN_A) ? 1 : 0;
        case RETRO_DEVICE_ID_JOYPAD_X:      return (g_current_buttons & DUALDEX_BTN_X) ? 1 : 0;
        case RETRO_DEVICE_ID_JOYPAD_L:      return (g_current_buttons & DUALDEX_BTN_L) ? 1 : 0;
        case RETRO_DEVICE_ID_JOYPAD_R:      return (g_current_buttons & DUALDEX_BTN_R) ? 1 : 0;
        case RETRO_DEVICE_ID_JOYPAD_L2:     return (g_current_buttons & DUALDEX_BTN_L2) ? 1 : 0;
        case RETRO_DEVICE_ID_JOYPAD_R2:     return (g_current_buttons & DUALDEX_BTN_R2) ? 1 : 0;
        default: return 0;
    }
}

#define RESOLVE_SYM(name) do { \
    p_##name = dlsym(g_core_handle, #name); \
    if (!p_##name) { \
        fprintf(stderr, "Failed to resolve Libretro symbol: %s\n", #name); \
        dlclose(g_core_handle); \
        g_core_handle = NULL; \
        return false; \
    } \
} while(0)

bool libretro_host_init(const char* core_lib_path) {
    if (!core_lib_path) return false;
    if (g_core_handle) return true;

    g_core_handle = dlopen(core_lib_path, RTLD_LAZY | RTLD_LOCAL);
    if (!g_core_handle) {
        fprintf(stderr, "Failed to dlopen core library: %s (error: %s)\n", core_lib_path, dlerror());
        return false;
    }

    RESOLVE_SYM(retro_init);
    RESOLVE_SYM(retro_deinit);
    RESOLVE_SYM(retro_api_version);
    RESOLVE_SYM(retro_set_environment);
    RESOLVE_SYM(retro_set_video_refresh);
    RESOLVE_SYM(retro_set_audio_sample);
    RESOLVE_SYM(retro_set_audio_sample_batch);
    RESOLVE_SYM(retro_set_input_poll);
    RESOLVE_SYM(retro_set_input_state);
    RESOLVE_SYM(retro_set_controller_port_device);
    RESOLVE_SYM(retro_reset);
    RESOLVE_SYM(retro_run);
    RESOLVE_SYM(retro_serialize_size);
    RESOLVE_SYM(retro_serialize);
    RESOLVE_SYM(retro_unserialize);
    RESOLVE_SYM(retro_load_game);
    RESOLVE_SYM(retro_unload_game);
    RESOLVE_SYM(retro_get_system_info);
    RESOLVE_SYM(retro_get_system_av_info);
    RESOLVE_SYM(retro_get_memory_data);
    RESOLVE_SYM(retro_get_memory_size);

    p_retro_cheat_reset = (void (*)(void))dlsym(g_core_handle, "retro_cheat_reset");
    p_retro_cheat_set = (void (*)(unsigned, bool, const char*))dlsym(g_core_handle, "retro_cheat_set");

    // Initialize core
    p_retro_set_environment(core_environment_cb);
    p_retro_set_video_refresh(core_video_refresh_cb);
    p_retro_set_audio_sample(core_audio_sample_cb);
    p_retro_set_audio_sample_batch(core_audio_sample_batch_cb);
    p_retro_set_input_poll(core_input_poll_cb);
    p_retro_set_input_state(core_input_state_cb);

    p_retro_init();
    return true;
}

bool libretro_host_unload_rom(void) {
    if (!g_core_handle) return false;
    libretro_host_clear_audio();
    if (g_is_game_loaded && p_retro_unload_game) {
        p_retro_unload_game();
        g_is_game_loaded = false;
    }
    pokemon_reader_reset();
    return true;
}

bool libretro_host_load_rom(const char* rom_file_path) {
    if (!g_core_handle || !rom_file_path) return false;

    // Unload any previously loaded ROM before loading a new one
    if (g_is_game_loaded) {
        libretro_host_unload_rom();
    }

    FILE* f = fopen(rom_file_path, "rb");
    if (!f) {
        fprintf(stderr, "Failed to open ROM file: %s\n", rom_file_path);
        return false;
    }

    fseek(f, 0, SEEK_END);
    size_t size = ftell(f);
    fseek(f, 0, SEEK_SET);

    void* data = malloc(size);
    if (!data) {
        fclose(f);
        return false;
    }
    size_t rd = fread(data, 1, size, f);
    fclose(f);

    if (rd != size) {
        free(data);
        return false;
    }

    struct retro_game_info game_info;
    memset(&game_info, 0, sizeof(game_info));
    game_info.path = rom_file_path;
    game_info.data = data;
    game_info.size = size;

    bool ok = p_retro_load_game(&game_info);
    free(data);

    if (ok) {
        g_is_game_loaded = true;
        p_retro_set_controller_port_device(0, RETRO_DEVICE_JOYPAD);
        if (p_retro_get_system_av_info) {
            struct retro_system_av_info av_info;
            memset(&av_info, 0, sizeof(av_info));
            p_retro_get_system_av_info(&av_info);
            if (av_info.timing.fps > 10.0 && av_info.timing.fps < 200.0) {
                g_target_fps = av_info.timing.fps;
            }
            if (av_info.timing.sample_rate >= 8000.0 && av_info.timing.sample_rate <= 96000.0) {
                g_audio_sample_rate = av_info.timing.sample_rate;
            }
        }
        libretro_host_clear_audio();
        pokemon_reader_reset();
    }
    return ok;
}

void libretro_host_step_frame(void) {
    if (g_core_handle && g_is_game_loaded && p_retro_run) {
        p_retro_run();
    }
}

bool libretro_host_get_video_frame(EmulatorVideoFrame* out_frame) {
    if (!out_frame) return false;

    pthread_mutex_lock(&g_video_mutex);
    *out_frame = g_current_frame;
    pthread_mutex_unlock(&g_video_mutex);

    return (out_frame->pixels != NULL);
}

bool libretro_host_copy_video_frame(void* dst, size_t dst_capacity, unsigned int* out_w, unsigned int* out_h, size_t* out_pitch, int* out_fmt) {
    if (!dst || dst_capacity == 0) return false;

    pthread_mutex_lock(&g_video_mutex);
    if (!g_current_frame.pixels || g_current_frame.width == 0 || g_current_frame.height == 0) {
        pthread_mutex_unlock(&g_video_mutex);
        return false;
    }

    size_t copy_size = g_current_frame.pitch * g_current_frame.height;
    if (copy_size > dst_capacity) copy_size = dst_capacity;
    memcpy(dst, g_current_frame.pixels, copy_size);

    if (out_w) *out_w = g_current_frame.width;
    if (out_h) *out_h = g_current_frame.height;
    if (out_pitch) *out_pitch = g_current_frame.pitch;
    if (out_fmt) *out_fmt = g_current_frame.pixel_format;

    pthread_mutex_unlock(&g_video_mutex);
    return true;
}

void libretro_host_set_input_buttons(uint32_t button_mask) {
    g_current_buttons = button_mask;
}

uint8_t* libretro_host_get_ewram(size_t* out_size) {
    if (!g_core_handle || !g_is_game_loaded || !p_retro_get_memory_data) {
        if (out_size) *out_size = 0;
        return NULL;
    }

    void* ptr = p_retro_get_memory_data(RETRO_MEMORY_SYSTEM_RAM);
    size_t sz = p_retro_get_memory_size(RETRO_MEMORY_SYSTEM_RAM);

    // CRITICAL FIX: mGBA's libretro.c has a bug in retro_get_memory_size(RETRO_MEMORY_SYSTEM_RAM)
    // where it returns GB_SIZE_WORKING_RAM (32768) instead of GBA_SIZE_EWRAM (262144).
    // In GBA mode, mGBA's wram pointer always points to the full 256 KB (0x40000 = 262144 bytes) EWRAM block.
    if (ptr && sz < 0x40000) {
        sz = 0x40000;
    }

    if (out_size) *out_size = sz;
    return (uint8_t*)ptr;
}

size_t libretro_host_get_save_ram_size(void) {
    if (!g_core_handle || !g_is_game_loaded || !p_retro_get_memory_size) {
        return 0;
    }
    return p_retro_get_memory_size(RETRO_MEMORY_SAVE_RAM);
}

size_t libretro_host_get_save_state_size(void) {
    if (!g_core_handle || !g_is_game_loaded || !p_retro_serialize_size) {
        return 0;
    }
    return p_retro_serialize_size();
}

bool libretro_host_save_state(const char* save_state_path) {
    if (!g_core_handle || !g_is_game_loaded || !save_state_path || !p_retro_serialize_size || !p_retro_serialize) {
        return false;
    }

    size_t sz = p_retro_serialize_size();
    if (sz == 0) return false;

    void* buf = malloc(sz);
    if (!buf) return false;

    bool ok = p_retro_serialize(buf, sz);
    if (ok) {
        FILE* f = fopen(save_state_path, "wb");
        if (f) {
            size_t written = fwrite(buf, 1, sz, f);
            fflush(f);
            fsync(fileno(f));
            fclose(f);
            if (written != sz) ok = false;
        } else {
            ok = false;
        }
    }
    free(buf);
    return ok;
}

bool libretro_host_load_state(const char* save_state_path) {
    if (!g_core_handle || !g_is_game_loaded || !save_state_path || !p_retro_unserialize) {
        return false;
    }

    FILE* f = fopen(save_state_path, "rb");
    if (!f) return false;

    fseek(f, 0, SEEK_END);
    long file_size = ftell(f);
    fseek(f, 0, SEEK_SET);
    if (file_size <= 0) {
        fclose(f);
        return false;
    }

    size_t sz = (size_t)file_size;
    size_t expected_sz = p_retro_serialize_size ? p_retro_serialize_size() : 0;
    if (expected_sz > 0 && sz != expected_sz) {
        fprintf(stderr, "Save state size mismatch: file has %zu bytes, core expects %zu bytes\n", sz, expected_sz);
        fclose(f);
        return false;
    }

    void* buf = malloc(sz);
    if (!buf) {
        fclose(f);
        return false;
    }
    size_t rd = fread(buf, 1, sz, f);
    fclose(f);

    bool ok = false;
    if (rd == sz) {
        ok = p_retro_unserialize(buf, sz);
    }
    free(buf);
    return ok;
}

bool libretro_host_load_save_ram(const char* save_path) {
    if (!g_core_handle || !g_is_game_loaded || !p_retro_get_memory_data || !p_retro_get_memory_size || !save_path) {
        return false;
    }
    void* ram = p_retro_get_memory_data(RETRO_MEMORY_SAVE_RAM);
    size_t ram_size = p_retro_get_memory_size(RETRO_MEMORY_SAVE_RAM);
    if (!ram || ram_size == 0) return false;

    FILE* f = fopen(save_path, "rb");
    if (!f) return false;

    fseek(f, 0, SEEK_END);
    long file_size = ftell(f);
    fseek(f, 0, SEEK_SET);
    if (file_size <= 0 || (size_t)file_size != ram_size) {
        fprintf(stderr, "Save RAM size mismatch: file has %ld bytes, core expects %zu bytes\n", file_size, ram_size);
        fclose(f);
        return false;
    }

    memset(ram, 0xFF, ram_size);
    size_t bytes_read = fread(ram, 1, ram_size, f);
    fclose(f);

    return (bytes_read == ram_size);
}

bool libretro_host_flush_save_ram(const char* save_path) {
    if (!g_core_handle || !g_is_game_loaded || !p_retro_get_memory_data || !p_retro_get_memory_size || !save_path) {
        return false;
    }
    void* ram = p_retro_get_memory_data(RETRO_MEMORY_SAVE_RAM);
    size_t ram_size = p_retro_get_memory_size(RETRO_MEMORY_SAVE_RAM);
    if (!ram || ram_size == 0) return false;

    FILE* f = fopen(save_path, "wb");
    if (!f) return false;

    size_t written = fwrite(ram, 1, ram_size, f);
    fflush(f);
    fsync(fileno(f));
    fclose(f);
    return (written == ram_size);
}

void libretro_host_reset(void) {
    if (g_core_handle && g_is_game_loaded && p_retro_reset) {
        p_retro_reset();
        libretro_host_clear_audio();
        pokemon_reader_reset();
    }
}

double libretro_host_get_target_fps(void) {
    return g_target_fps;
}

double libretro_host_get_sample_rate(void) {
    return g_audio_sample_rate;
}

void libretro_host_cleanup(void) {
    libretro_host_unload_rom();
    if (g_core_handle && p_retro_deinit) {
        p_retro_deinit();
    }
    if (g_core_handle) {
        dlclose(g_core_handle);
        g_core_handle = NULL;
    }
    // Reset all function pointers to prevent dangling pointer access
    p_retro_init = NULL;
    p_retro_deinit = NULL;
    p_retro_api_version = NULL;
    p_retro_set_environment = NULL;
    p_retro_set_video_refresh = NULL;
    p_retro_set_audio_sample = NULL;
    p_retro_set_audio_sample_batch = NULL;
    p_retro_set_input_poll = NULL;
    p_retro_set_input_state = NULL;
    p_retro_set_controller_port_device = NULL;
    p_retro_reset = NULL;
    p_retro_run = NULL;
    p_retro_serialize_size = NULL;
    p_retro_serialize = NULL;
    p_retro_unserialize = NULL;
    p_retro_load_game = NULL;
    p_retro_unload_game = NULL;
    p_retro_get_system_info = NULL;
    p_retro_get_system_av_info = NULL;
    p_retro_get_memory_data = NULL;
    p_retro_get_memory_size = NULL;
    p_retro_cheat_reset = NULL;
    p_retro_cheat_set = NULL;
}

void libretro_host_cheat_reset(void) {
    if (p_retro_cheat_reset) {
        p_retro_cheat_reset();
    }
}

void libretro_host_cheat_set(unsigned index, bool enabled, const char* code) {
    if (p_retro_cheat_set && code) {
        p_retro_cheat_set(index, enabled, code);
    }
}
