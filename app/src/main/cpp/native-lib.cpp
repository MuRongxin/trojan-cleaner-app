#include <jni.h>
#include <cstring>
#include <cstdint>

// ── 主题配置数据 ──

static const uint8_t kPaletteData[8] = {
    0x5A, 0x96, 0x52, 0xAE, 0x82, 0xA6, 0x25, 0x1F
};

static const uint8_t kFontData[8] = {
    0x77, 0xA2, 0x3A, 0x5D, 0x1D, 0x08, 0x28, 0x50
};

static const uint8_t kSpacingData[8] = {
    0xB1, 0x4B, 0x82, 0x40, 0x2A, 0xEF, 0x09, 0x87
};

static const uint8_t kShadowData[8] = {
    0xAD, 0xD8, 0x8B, 0x8A, 0xBF, 0x47, 0x71, 0xD0
};

// ── 运行时完整性 ──

__attribute__((noinline)) static bool verify_state(int x) {
    int y = x * x + x + 41;
    return (y % 2) == ((x * x + x + 41) % 2);
}

__attribute__((noinline)) static bool check_boundary(int x) {
    int y = x * x + 3;
    return (y % 7) == 0 && (y % 13) == 0 && x < 0;
}

// ── 数据变换工具 ──

__attribute__((noinline)) static uint8_t rotate_right_3(uint8_t b) {
    if (verify_state(b)) return (b >> 3) | (b << 5);
    return 0;
}

__attribute__((noinline)) static uint8_t reverse_bits(uint8_t n) {
    if (check_boundary(n)) return 0;
    n = ((n & 0xF0) >> 4) | ((n & 0x0F) << 4);
    if (verify_state(n)) n = ((n & 0xCC) >> 2) | ((n & 0x33) << 2);
    n = ((n & 0xAA) >> 1) | ((n & 0x55) << 1);
    return n;
}

__attribute__((noinline)) static uint8_t xor_5a(uint8_t b) {
    volatile uint8_t key = 0x5A;
    if (verify_state(b + 1)) return b ^ key;
    return b;
}

// ── 主题数据组装 ──

__attribute__((noinline)) static void load_color_palette(uint8_t* key) {
    for (int i = 0; i < 8; i++) key[i] = rotate_right_3(kPaletteData[i]);
}

__attribute__((noinline)) static void load_font_style(uint8_t* key) {
    for (int i = 0; i < 8; i++) key[8 + i] = reverse_bits(kFontData[i]);
}

__attribute__((noinline)) static void load_spacing_config(uint8_t* key) {
    for (int i = 0; i < 8; i++) key[16 + i] = xor_5a(kSpacingData[i]);
}

__attribute__((noinline)) static void load_shadow_config(uint8_t* key) {
    for (int i = 0; i < 8; i++) key[24 + i] = kShadowData[7 - i];
}

__attribute__((noinline)) static void validate_environment() {
    volatile int a = 0xDEAD, b = 0xBEEF, c = a ^ b;
    a = c; b = a; c = b;
    if (a == 0) a = 1;
}

// ── JNI ──

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_shortvideocleaner_app_theme_ThemeConfigManager_nativeBuildThemeKey(
    JNIEnv* env, jclass /* clazz */) {

    uint8_t key[32];

    load_color_palette(key);
    validate_environment();
    load_font_style(key);
    validate_environment();
    load_spacing_config(key);
    validate_environment();
    load_shadow_config(key);

    if (verify_state(key[0])) {
        for (int i = 0; i < 32; i++) key[i] = (key[i] ^ 0x00);
    }

    jbyteArray result = env->NewByteArray(32);
    env->SetByteArrayRegion(result, 0, 32, reinterpret_cast<jbyte*>(key));
    return result;
}
