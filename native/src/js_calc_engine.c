#include "js_calc_engine.h"
#include "quickjs.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

// Logging boundary. Android builds keep real logcat output; host builds (the
// canonical `./ci.sh test` calculator suite) have no liblog, so the same
// message goes to stderr instead of the engine being untestable off-device.
// Same convention as native/src/pokemon_reader.c.
#ifdef __ANDROID__
#include <android/log.h>
#define LOG_CALC(...) __android_log_print(ANDROID_LOG_ERROR, "DualDex_JNI", __VA_ARGS__)
#else
#define LOG_CALC(...) do { fprintf(stderr, "DualDex_JNI: " __VA_ARGS__); fputc('\n', stderr); } while (0)
#endif

static JSRuntime* g_rt = NULL;
static JSContext* g_ctx = NULL;

bool js_calc_init(const char* bundle_js_code) {
    if (!bundle_js_code) return false;

    if (g_ctx != NULL) {
        // Already initialized
        return true;
    }

    g_rt = JS_NewRuntime();
    if (!g_rt) return false;

    // Set reasonable memory limit for mobile/handheld (e.g., 32 MB)
    JS_SetMemoryLimit(g_rt, 32 * 1024 * 1024);

    g_ctx = JS_NewContext(g_rt);
    if (!g_ctx) {
        JS_FreeRuntime(g_rt);
        g_rt = NULL;
        return false;
    }

    // Evaluate calc bundle
    JSValue val = JS_Eval(g_ctx, bundle_js_code, strlen(bundle_js_code), "<calc_bundle.js>", JS_EVAL_TYPE_GLOBAL);
    if (JS_IsException(val)) {
        JSValue exc = JS_GetException(g_ctx);
        const char* exc_str = JS_ToCString(g_ctx, exc);
        fprintf(stderr, "QuickJS calc_bundle error: %s\n", exc_str ? exc_str : "unknown");
        JS_FreeCString(g_ctx, exc_str);
        JS_FreeValue(g_ctx, exc);
        JS_FreeValue(g_ctx, val);
        js_calc_cleanup();
        return false;
    }

    JS_FreeValue(g_ctx, val);
    return true;
}

char* js_calc_calculate(const char* input_json_str) {
    if (!g_ctx || !input_json_str) return NULL;

    JSValue global = JS_GetGlobalObject(g_ctx);
    JSValue calc_obj = JS_GetPropertyStr(g_ctx, global, "DualDexCalc");
    if (JS_IsUndefined(calc_obj) || JS_IsNull(calc_obj)) {
        JS_FreeValue(g_ctx, global);
        return NULL;
    }

    JSValue calc_func = JS_GetPropertyStr(g_ctx, calc_obj, "calculateDamage");
    if (!JS_IsFunction(g_ctx, calc_func)) {
        JS_FreeValue(g_ctx, calc_func);
        JS_FreeValue(g_ctx, calc_obj);
        JS_FreeValue(g_ctx, global);
        return NULL;
    }

    JSValue arg = JS_NewString(g_ctx, input_json_str);
    JSValue res = JS_Call(g_ctx, calc_func, calc_obj, 1, &arg);

    char* out_str = NULL;
    if (!JS_IsException(res)) {
        const char* c_str = JS_ToCString(g_ctx, res);
        if (c_str) {
            out_str = strdup(c_str);
            JS_FreeCString(g_ctx, c_str);
        }
    } else {
        JSValue exc = JS_GetException(g_ctx);
        const char* exc_str = JS_ToCString(g_ctx, exc);
        if (exc_str) {
            // Log the error but don't return raw string as it's not JSON
            LOG_CALC("QuickJS calc error: %s", exc_str);
            JS_FreeCString(g_ctx, exc_str);
        }
        JS_FreeValue(g_ctx, exc);
        // Return NULL to signal error to caller
    }

    JS_FreeValue(g_ctx, res);
    JS_FreeValue(g_ctx, arg);
    JS_FreeValue(g_ctx, calc_func);
    JS_FreeValue(g_ctx, calc_obj);
    JS_FreeValue(g_ctx, global);

    return out_str;
}

void js_calc_cleanup(void) {
    if (g_ctx) {
        JS_FreeContext(g_ctx);
        g_ctx = NULL;
    }
    if (g_rt) {
        JS_FreeRuntime(g_rt);
        g_rt = NULL;
    }
}
