#ifndef DUALDEX_LIBRETRO_HOST_H
#define DUALDEX_LIBRETRO_HOST_H

#include <stdbool.h>
#include <stdint.h>
#include <stddef.h>

#include "gba_memory_map.h"

#ifdef __cplusplus
extern "C" {
#endif

// Controller button bitmasks matching GBA + AYN Thor layout
#define DUALDEX_BTN_B         (1 << 0)
#define DUALDEX_BTN_Y         (1 << 1)
#define DUALDEX_BTN_SELECT    (1 << 2)
#define DUALDEX_BTN_START     (1 << 3)
#define DUALDEX_BTN_UP        (1 << 4)
#define DUALDEX_BTN_DOWN      (1 << 5)
#define DUALDEX_BTN_LEFT      (1 << 6)
#define DUALDEX_BTN_RIGHT     (1 << 7)
#define DUALDEX_BTN_A         (1 << 8)
#define DUALDEX_BTN_X         (1 << 9)
#define DUALDEX_BTN_L         (1 << 10)
#define DUALDEX_BTN_R         (1 << 11)
#define DUALDEX_BTN_L2        (1 << 12)
#define DUALDEX_BTN_R2        (1 << 13)

typedef struct {
    unsigned int width;
    unsigned int height;
    int          pixel_format; // 0=0RGB1555, 1=XRGB8888, 2=RGB565
    const void*  pixels;
    size_t       pitch;
} EmulatorVideoFrame;

typedef struct {
    bool   is_loaded;
    char   rom_title[17];
    double target_fps;
    double sample_rate;
} EmulatorCoreStatus;

/**
 * Initialize the Libretro host and load the core from shared library path (e.g. mgba_libretro.so).
 */
bool libretro_host_init(const char* core_lib_path);

/**
 * Load a ROM file into the emulator core.
 */
bool libretro_host_load_rom(const char* rom_file_path);

/**
 * Unload currently loaded ROM from core.
 */
bool libretro_host_unload_rom(void);

/**
 * Run a single frame of emulation (calls retro_run).
 */
void libretro_host_step_frame(void);

/**
 * Get pointer to the most recent video frame buffer.
 */
bool libretro_host_get_video_frame(EmulatorVideoFrame* out_frame);

/**
 * Retrieve pending 16-bit PCM audio samples from the core audio buffer.
 * @return Number of samples read (samples = frames * 2 for stereo).
 */
size_t libretro_host_get_audio_samples(int16_t* out_buffer, size_t max_samples);

/**
 * Set the current controller button state bitmask.
 */
void libretro_host_set_input_buttons(uint32_t button_mask);

/**
 * Get a direct pointer to the 256 KB EWRAM memory block for zero-copy Pokemon parsing.
 */
uint8_t* libretro_host_get_ewram(size_t* out_size);

/**
 * Maximum number of libretro memory-map regions DualDex retains at once.
 *
 * mGBA publishes 11 descriptors for GBA (IWRAM, EWRAM, SRAM/Flash, 3x ROM mirrors,
 * BIOS, VRAM, palette RAM, OAM, mmapped I/O), so 16 leaves headroom without
 * allowing an unbounded core-controlled allocation.
 */
#define DUALDEX_GBA_MAX_MEMORY_REGIONS DUALDEX_GBA_REGION_CAPACITY

/** Captured metadata for one libretro memory-map region (no caller-owned pointers are kept). */
typedef struct {
    uint32_t start;       // first emulated address covered
    uint32_t len;         // region length in bytes
    uint32_t select;      // address bits that select this region
    uint32_t disconnect;  // address bits masked out before translation
    uint32_t offset;      // descriptor offset added after translation
    uint64_t flags;       // RETRO_MEMDESC_* flags
    bool     present;     // false for an unused slot
} DualDexGbaMemoryRegion;

/**
 * Read @p length bytes from an emulated GBA address through the region descriptors the core
 * published with RETRO_ENVIRONMENT_SET_MEMORY_MAPS.
 *
 * Understands at least 0x02000000.. (EWRAM) and 0x03000000.. (IWRAM). The read is
 * bounds-checked against the exact captured region, so a request that starts outside a mapped
 * region, that runs past the end of one, or that arrives before any map was captured fails
 * closed with false and leaves @p out untouched.
 *
 * @return true only when the whole request was satisfied from a single mapped region.
 */
bool libretro_host_read_gba_address(uint32_t address, void* out, size_t length);

/**
 * Number of memory-map regions currently captured (0 when no ROM is loaded or the core
 * published no map). Exposed for diagnostics and host tests.
 */
size_t libretro_host_get_gba_region_count(void);

/**
 * Copy captured region metadata at @p index. Returns false when @p index is out of range.
 */
bool libretro_host_get_gba_region(size_t index, DualDexGbaMemoryRegion* out_region);

/**
 * Drop every captured region. Called on ROM unload, core reset and cleanup so that stale
 * pointers can never outlive the core state they describe.
 */
void libretro_host_clear_memory_regions(void);

/**
 * Save state to file.
 */
bool libretro_host_save_state(const char* save_state_path);

/**
 * Load state from file.
 */
bool libretro_host_load_state(const char* save_state_path);

/**
 * Load cartridge battery save RAM (SRAM / Flash 128KB) from a .sav file.
 */
bool libretro_host_load_save_ram(const char* save_path);

/**
 * Flush cartridge battery save RAM to a .sav file on disk.
 */
bool libretro_host_flush_save_ram(const char* save_path);

/**
 * Get active core cartridge battery save RAM size in bytes.
 */
size_t libretro_host_get_save_ram_size(void);

/**
 * Get active core serialize state size in bytes.
 */
size_t libretro_host_get_save_state_size(void);

/**
 * Reset emulation core (soft reset).
 */
void libretro_host_reset(void);

/**
 * Get core target FPS (typically 59.7275).
 */
double libretro_host_get_target_fps(void);

/**
 * Get core audio sample rate in Hz (typically 32768 or 44100).
 */
double libretro_host_get_sample_rate(void);

/**
 * Thread-safe copy of the most recent video frame buffer and metadata into dst.
 */
bool libretro_host_copy_video_frame(void* dst, size_t dst_capacity, unsigned int* out_w, unsigned int* out_h, size_t* out_pitch, int* out_fmt);

/**
 * Flush/clear audio ring buffer to eliminate latency buildup.
 */
void libretro_host_clear_audio(void);

/**
 * Set target audio output sample rate for resampler (default: 48000 Hz).
 */
void libretro_host_set_target_audio_sample_rate(uint32_t rate);

/**
 * Get the target audio output sample rate.
 */
uint32_t libretro_host_get_output_sample_rate(void);

/**
 * Clear all active cheats in emulator core.
 */
void libretro_host_cheat_reset(void);

/**
 * Set or add a cheat code in the emulator core.
 * Supported formats: Action Replay (AR v3), GameShark, CodeBreaker.
 */
void libretro_host_cheat_set(unsigned index, bool enabled, const char* code);

/**
 * Unload ROM and core.
 */
void libretro_host_cleanup(void);

#ifdef __cplusplus
}
#endif

#endif // DUALDEX_LIBRETRO_HOST_H
