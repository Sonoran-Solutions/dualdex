/*
 * json_lite.h - tiny, test-only JSON reader for the native host test suites.
 *
 * The QuickJS calculator contract returns JSON, so asserting on typed values
 * (numbers, strings, booleans, arrays) instead of on substrings requires a
 * parser. This header is deliberately minimal and dependency-free: it lives in
 * native/tests/ (never on the Android include path), is compiled only into host
 * test binaries, and supports exactly the subset the calculator contract
 * produces.
 *
 * Strictness (the suite's fail-closed guarantee depends on it):
 *   - malformed input is reported as NULL instead of being partially accepted;
 *   - numbers must follow the JSON grammar - no leading zero, no leading '+',
 *     at least one digit after '.', at least one digit in the exponent - and
 *     must be finite (so `1e999` is rejected rather than becoming infinity and
 *     reaching a floating-to-integer conversion);
 *   - the escape \u0000 is rejected, and raw control bytes are rejected, so a
 *     decoded string or object key can never alias a different value through a
 *     NUL-terminated C comparison. This parser does not preserve string
 *     lengths; embedded NULs are simply outside its supported subset.
 * Anything else in the subset (nested objects/arrays, escapes, surrogate
 * pairs, negative and fractional numbers) behaves as expected.
 *
 * Usage:
 *     jl_value* doc = jl_parse(response);
 *     const jl_value* hp = jl_get(doc, "defenderMaxHP");
 *     if (jl_is_num(hp)) use(jl_num(hp));
 *     jl_free(doc);
 */

#ifndef DUALDEX_TEST_JSON_LITE_H
#define DUALDEX_TEST_JSON_LITE_H

#include <math.h>
#include <stdlib.h>
#include <string.h>

typedef enum {
    JL_NULL,
    JL_BOOL,
    JL_NUM,
    JL_STR,
    JL_ARR,
    JL_OBJ
} jl_type;

typedef struct jl_value {
    jl_type type;
    int boolean;                 /* JL_BOOL */
    double number;               /* JL_NUM */
    char* string;                /* JL_STR */
    struct jl_value** items;     /* JL_ARR */
    int item_count;
    char** keys;                 /* JL_OBJ */
    struct jl_value** values;
    int pair_count;
} jl_value;

#define JL_MAX_DEPTH 64

typedef struct {
    const char* text;
    size_t pos;
    size_t len;
    int depth;
} jl_parser;

static jl_value* jl__parse_value(jl_parser* p);

static jl_value* jl__new(jl_type type) {
    jl_value* v = (jl_value*)calloc(1, sizeof(jl_value));
    if (v) v->type = type;
    return v;
}

static void jl_free(jl_value* v) {
    if (!v) return;
    free(v->string);
    for (int i = 0; i < v->item_count; i++) jl_free(v->items[i]);
    free(v->items);
    for (int i = 0; i < v->pair_count; i++) {
        free(v->keys[i]);
        jl_free(v->values[i]);
    }
    free(v->keys);
    free(v->values);
    free(v);
}

static void jl__skip_ws(jl_parser* p) {
    while (p->pos < p->len) {
        char c = p->text[p->pos];
        if (c == ' ' || c == '\t' || c == '\n' || c == '\r') p->pos++;
        else break;
    }
}

static int jl__peek(jl_parser* p) {
    jl__skip_ws(p);
    return p->pos < p->len ? (unsigned char)p->text[p->pos] : -1;
}

static void jl__encode_utf8(unsigned int cp, char* out, int* n) {
    if (cp < 0x80) {
        out[(*n)++] = (char)cp;
    } else if (cp < 0x800) {
        out[(*n)++] = (char)(0xC0 | (cp >> 6));
        out[(*n)++] = (char)(0x80 | (cp & 0x3F));
    } else if (cp < 0x10000) {
        out[(*n)++] = (char)(0xE0 | (cp >> 12));
        out[(*n)++] = (char)(0x80 | ((cp >> 6) & 0x3F));
        out[(*n)++] = (char)(0x80 | (cp & 0x3F));
    } else {
        out[(*n)++] = (char)(0xF0 | (cp >> 18));
        out[(*n)++] = (char)(0x80 | ((cp >> 12) & 0x3F));
        out[(*n)++] = (char)(0x80 | ((cp >> 6) & 0x3F));
        out[(*n)++] = (char)(0x80 | (cp & 0x3F));
    }
}

static int jl__hex4(jl_parser* p, unsigned int* out) {
    unsigned int value = 0;
    for (int i = 0; i < 4; i++) {
        if (p->pos >= p->len) return 0;
        char c = p->text[p->pos++];
        value <<= 4;
        if (c >= '0' && c <= '9') value |= (unsigned int)(c - '0');
        else if (c >= 'a' && c <= 'f') value |= (unsigned int)(c - 'a' + 10);
        else if (c >= 'A' && c <= 'F') value |= (unsigned int)(c - 'A' + 10);
        else return 0;
    }
    *out = value;
    return 1;
}

/* Parses a JSON string literal starting at the opening quote. */
static char* jl__parse_string(jl_parser* p) {
    if (p->pos >= p->len || p->text[p->pos] != '"') return NULL;
    p->pos++;
    size_t cap = 32, n = 0;
    char* out = (char*)malloc(cap);
    if (!out) return NULL;
    while (p->pos < p->len) {
        unsigned char c = (unsigned char)p->text[p->pos++];
        if (c == '"') {
            out[n] = '\0';
            return out;
        }
        if (c < 0x20) { /* raw control character is invalid in JSON */
            free(out);
            return NULL;
        }
        char buf[4];
        int extra = 0;
        if (c == '\\') {
            if (p->pos >= p->len) { free(out); return NULL; }
            char esc = p->text[p->pos++];
            switch (esc) {
                case '"': buf[0] = '"'; extra = 1; break;
                case '\\': buf[0] = '\\'; extra = 1; break;
                case '/': buf[0] = '/'; extra = 1; break;
                case 'b': buf[0] = '\b'; extra = 1; break;
                case 'f': buf[0] = '\f'; extra = 1; break;
                case 'n': buf[0] = '\n'; extra = 1; break;
                case 'r': buf[0] = '\r'; extra = 1; break;
                case 't': buf[0] = '\t'; extra = 1; break;
                case 'u': {
                    unsigned int cp = 0;
                    if (!jl__hex4(p, &cp)) { free(out); return NULL; }
                    if (cp >= 0xD800 && cp <= 0xDBFF && p->pos + 1 < p->len &&
                        p->text[p->pos] == '\\' && p->text[p->pos + 1] == 'u') {
                        p->pos += 2;
                        unsigned int low = 0;
                        if (!jl__hex4(p, &low)) { free(out); return NULL; }
                        if (low >= 0xDC00 && low <= 0xDFFF) {
                            cp = 0x10000 + ((cp - 0xD800) << 10) + (low - 0xDC00);
                        }
                    }
                    /* An embedded NUL would make this C string indistinguishable
                     * from the same prefix without it (both for values and for
                     * object keys), so it is rejected rather than aliased. */
                    if (cp == 0) { free(out); return NULL; }
                    jl__encode_utf8(cp, buf, &extra);
                    break;
                }
                default:
                    free(out);
                    return NULL;
            }
        } else {
            buf[0] = (char)c;
            extra = 1;
        }
        if (n + (size_t)extra + 1 > cap) {
            cap = (cap + (size_t)extra) * 2;
            char* grown = (char*)realloc(out, cap);
            if (!grown) { free(out); return NULL; }
            out = grown;
        }
        memcpy(out + n, buf, (size_t)extra);
        n += (size_t)extra;
    }
    free(out);
    return NULL; /* unterminated */
}

/* Parses a JSON number. Enforces the JSON grammar:
 *   number = [ '-' ] int [ frac ] [ exp ]
 *   int    = '0' | [1-9] *DIGIT      (no leading zero, no leading '+')
 *   frac   = '.' 1*DIGIT
 *   exp    = ('e' | 'E') [ '+' | '-' ] 1*DIGIT
 * and rejects values that are not finite (for example 1e999) so a bogus
 * response cannot reach a floating-to-integer conversion. */
static jl_value* jl__parse_number(jl_parser* p) {
    size_t start = p->pos;
    if (p->pos < p->len && p->text[p->pos] == '-') p->pos++;

    if (p->pos >= p->len) return NULL;
    char c = p->text[p->pos];
    if (c == '0') {
        p->pos++; /* JSON forbids further digits here (e.g. 051) */
    } else if (c >= '1' && c <= '9') {
        while (p->pos < p->len && p->text[p->pos] >= '0' && p->text[p->pos] <= '9') p->pos++;
    } else {
        return NULL; /* '.' , '+' or '-' with no integer part */
    }

    if (p->pos < p->len && p->text[p->pos] == '.') {
        p->pos++;
        int frac_digits = 0;
        while (p->pos < p->len && p->text[p->pos] >= '0' && p->text[p->pos] <= '9') { p->pos++; frac_digits++; }
        if (frac_digits == 0) return NULL; /* "51." is not a JSON number */
    }

    if (p->pos < p->len && (p->text[p->pos] == 'e' || p->text[p->pos] == 'E')) {
        p->pos++;
        if (p->pos < p->len && (p->text[p->pos] == '-' || p->text[p->pos] == '+')) p->pos++;
        int exp_digits = 0;
        while (p->pos < p->len && p->text[p->pos] >= '0' && p->text[p->pos] <= '9') { p->pos++; exp_digits++; }
        if (exp_digits == 0) return NULL;
    }

    char buf[64];
    size_t len = p->pos - start;
    if (len >= sizeof(buf)) return NULL;
    memcpy(buf, p->text + start, len);
    buf[len] = '\0';
    char* end = NULL;
    double value = strtod(buf, &end);
    if (!end || *end != '\0') return NULL;
    if (!isfinite(value)) return NULL; /* e.g. 1e999 */
    jl_value* v = jl__new(JL_NUM);
    if (v) v->number = value;
    return v;
}

static jl_value* jl__parse_array(jl_parser* p) {
    p->pos++; /* '[' */
    jl_value* arr = jl__new(JL_ARR);
    if (!arr) return NULL;
    if (jl__peek(p) == ']') { p->pos++; return arr; }
    for (;;) {
        jl_value* item = jl__parse_value(p);
        if (!item) { jl_free(arr); return NULL; }
        jl_value** grown = (jl_value**)realloc(arr->items, sizeof(jl_value*) * (size_t)(arr->item_count + 1));
        if (!grown) { jl_free(item); jl_free(arr); return NULL; }
        arr->items = grown;
        arr->items[arr->item_count++] = item;
        int c = jl__peek(p);
        if (c == ',') { p->pos++; continue; }
        if (c == ']') { p->pos++; return arr; }
        jl_free(arr);
        return NULL;
    }
}

static jl_value* jl__parse_object(jl_parser* p) {
    p->pos++; /* '{' */
    jl_value* obj = jl__new(JL_OBJ);
    if (!obj) return NULL;
    if (jl__peek(p) == '}') { p->pos++; return obj; }
    for (;;) {
        if (jl__peek(p) != '"') { jl_free(obj); return NULL; }
        char* key = jl__parse_string(p);
        if (!key) { jl_free(obj); return NULL; }
        if (jl__peek(p) != ':') { free(key); jl_free(obj); return NULL; }
        p->pos++;
        jl_value* value = jl__parse_value(p);
        if (!value) { free(key); jl_free(obj); return NULL; }
        char** keys = (char**)realloc(obj->keys, sizeof(char*) * (size_t)(obj->pair_count + 1));
        jl_value** values = keys
            ? (jl_value**)realloc(obj->values, sizeof(jl_value*) * (size_t)(obj->pair_count + 1))
            : NULL;
        if (!keys || !values) {
            free(key);
            jl_free(value);
            if (keys) obj->keys = keys;
            jl_free(obj);
            return NULL;
        }
        obj->keys = keys;
        obj->values = values;
        obj->keys[obj->pair_count] = key;
        obj->values[obj->pair_count] = value;
        obj->pair_count++;
        int c = jl__peek(p);
        if (c == ',') { p->pos++; continue; }
        if (c == '}') { p->pos++; return obj; }
        jl_free(obj);
        return NULL;
    }
}

static int jl__match_literal(jl_parser* p, const char* literal) {
    size_t len = strlen(literal);
    if (p->pos + len > p->len) return 0;
    if (memcmp(p->text + p->pos, literal, len) != 0) return 0;
    p->pos += len;
    return 1;
}

static jl_value* jl__parse_value(jl_parser* p) {
    if (p->depth >= JL_MAX_DEPTH) return NULL;
    p->depth++;
    jl_value* result = NULL;
    int c = jl__peek(p);
    switch (c) {
        case '{': result = jl__parse_object(p); break;
        case '[': result = jl__parse_array(p); break;
        case '"': {
            char* s = jl__parse_string(p);
            if (s) {
                result = jl__new(JL_STR);
                if (result) result->string = s;
                else free(s);
            }
            break;
        }
        case 't':
            if (jl__match_literal(p, "true")) {
                result = jl__new(JL_BOOL);
                if (result) result->boolean = 1;
            }
            break;
        case 'f':
            if (jl__match_literal(p, "false")) {
                result = jl__new(JL_BOOL);
                if (result) result->boolean = 0;
            }
            break;
        case 'n':
            if (jl__match_literal(p, "null")) result = jl__new(JL_NULL);
            break;
        default:
            if (c == '-' || (c >= '0' && c <= '9')) result = jl__parse_number(p);
            break;
    }
    p->depth--;
    return result;
}

/* Returns a parsed document, or NULL when the text is not valid JSON. */
static jl_value* jl_parse(const char* text) {
    if (!text) return NULL;
    jl_parser p;
    p.text = text;
    p.pos = 0;
    p.len = strlen(text);
    p.depth = 0;
    jl_value* doc = jl__parse_value(&p);
    if (!doc) return NULL;
    if (jl__peek(&p) != -1) { /* trailing garbage */
        jl_free(doc);
        return NULL;
    }
    return doc;
}

/* Object member lookup; NULL when absent or when value is not an object. */
static const jl_value* jl_get(const jl_value* obj, const char* key) {
    if (!obj || obj->type != JL_OBJ || !key) return NULL;
    for (int i = 0; i < obj->pair_count; i++) {
        if (strcmp(obj->keys[i], key) == 0) return obj->values[i];
    }
    return NULL;
}

static int jl_is_num(const jl_value* v) { return v && v->type == JL_NUM; }
static int jl_is_str(const jl_value* v) { return v && v->type == JL_STR; }
static int jl_is_bool(const jl_value* v) { return v && v->type == JL_BOOL; }
static int jl_is_arr(const jl_value* v) { return v && v->type == JL_ARR; }

static double jl_num(const jl_value* v) { return (v && v->type == JL_NUM) ? v->number : 0.0; }
static const char* jl_str(const jl_value* v) { return (v && v->type == JL_STR) ? v->string : NULL; }
static int jl_bool(const jl_value* v) { return (v && v->type == JL_BOOL) ? v->boolean : 0; }
static int jl_len(const jl_value* v) { return (v && v->type == JL_ARR) ? v->item_count : -1; }
static const jl_value* jl_at(const jl_value* v, int index) {
    if (!v || v->type != JL_ARR || index < 0 || index >= v->item_count) return NULL;
    return v->items[index];
}

#endif /* DUALDEX_TEST_JSON_LITE_H */
