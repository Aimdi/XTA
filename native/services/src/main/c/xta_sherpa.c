/*
 * The JNI side of SherpaJni (services/speech/SherpaJni.kt): sherpa-onnx's C
 * API, opened at run time from the folder the voice engine is in, as XTA's
 * sherpa_voice_synthesizer.dart opens it: onnxruntime first, then
 * libsherpa-onnx-c-api.so, whose dependency is then already loaded. Nothing
 * here links against the engine, so the app starts and runs without it.
 *
 * Android builds this with ndk-build (Android.mk, from :app); the desktop with
 * cc (tool/voice_engine.py desktop). Strings arrive as NUL-terminated UTF-8.
 *
 * On glibc the engine goes into a link-map namespace of its own (dlmopen),
 * with its own libstdc++: a C++ runtime that another library of the app
 * carries statically shares the locale facets' ids process-wide (they are
 * unique symbols), and the engine's calls through std::locale then reached
 * the wrong facet and crashed. The engine's memory stays its own: what it
 * allocates goes back through its own calls.
 */
#define _GNU_SOURCE /* dlmopen and dlinfo, on glibc */
#include <dlfcn.h>
#include <jni.h>
#include <stdint.h>
#include <string.h>

#if defined(__GLIBC__) && defined(__x86_64__)
/* The desktop shim runs wherever the engine does (glibc 2.16 on): glibc 2.34
 * moved these calls into libc under a new version, which an older one lacks. */
__asm__(".symver dlopen,dlopen@GLIBC_2.2.5");
__asm__(".symver dlsym,dlsym@GLIBC_2.2.5");
__asm__(".symver dlerror,dlerror@GLIBC_2.2.5");
__asm__(".symver dlmopen,dlmopen@GLIBC_2.3.4");
__asm__(".symver dlinfo,dlinfo@GLIBC_2.3.3");
#endif

/* sherpa-onnx 1.13.8's c-api.h, as that release's sherpa_onnx package lays it
 * out (lib/src/sherpa_onnx_bindings.dart): the same fields in the same order. */
typedef struct {
  const char *model, *lexicon, *tokens, *data_dir;
  float noise_scale, noise_scale_w, length_scale;
  const char *dict_dir;
} VitsConfig;

typedef struct {
  const char *acoustic_model, *vocoder, *lexicon, *tokens, *data_dir;
  float noise_scale, length_scale;
  const char *dict_dir;
} MatchaConfig;

typedef struct {
  const char *model, *voices, *tokens, *data_dir;
  float length_scale;
  const char *dict_dir, *lexicon, *lang;
} KokoroConfig;

typedef struct {
  const char *model, *voices, *tokens, *data_dir;
  float length_scale;
} KittenConfig;

typedef struct {
  const char *tokens, *encoder, *decoder, *vocoder, *data_dir, *lexicon;
  float feat_scale, t_shift, target_rms, guidance_scale;
} ZipVoiceConfig;

typedef struct {
  const char *lm_flow, *lm_main, *encoder, *decoder, *text_conditioner, *vocab_json, *token_scores_json;
  int32_t voice_embedding_cache_capacity;
} PocketConfig;

typedef struct {
  const char *duration_predictor, *text_encoder, *vector_estimator, *vocoder, *tts_json, *unicode_indexer, *voice_style;
} SupertonicConfig;

typedef struct {
  VitsConfig vits;
  int32_t num_threads;
  int32_t debug;
  const char *provider;
  MatchaConfig matcha;
  KokoroConfig kokoro;
  KittenConfig kitten;
  ZipVoiceConfig zipvoice;
  PocketConfig pocket;
  SupertonicConfig supertonic;
} ModelConfig;

typedef struct {
  ModelConfig model;
  const char *rule_fsts;
  int32_t max_num_sentences;
  const char *rule_fars;
  float silence_scale;
} TtsConfig;

typedef struct {
  const float *samples;
  int32_t n;
  int32_t sample_rate;
} GeneratedAudio;

static struct {
  const void *(*create)(const TtsConfig *);
  void (*destroy)(const void *);
  const GeneratedAudio *(*generate)(const void *, const char *, int32_t, float);
  void (*free_audio)(const GeneratedAudio *);
  int32_t (*write_wave)(const float *, int32_t, int32_t, const char *);
} api;

/* The model kinds SherpaJni names, as VoiceModelKind orders them. */
enum { KIND_VITS = 0, KIND_KOKORO = 1 };

static jbyteArray bytes_of(JNIEnv *env, const char *text) {
  const char *message = text ? text : "unknown error";
  jsize length = (jsize)strlen(message);
  jbyteArray out = (*env)->NewByteArray(env, length);
  if (out) (*env)->SetByteArrayRegion(env, out, 0, length, (const jbyte *)message);
  return out;
}

static void *find(void *library, const char *name, const char **missing) {
  void *symbol = dlsym(library, name);
  if (!symbol && !*missing) *missing = name;
  return symbol;
}

#if defined(__GLIBC__)
static Lmid_t engine_namespace = LM_ID_NEWLM;

/* [path] in the engine's namespace, made by its first library; the app's own
 * where glibc cannot make one (no room left for another libc's TLS). */
static void *open_library(const char *path) {
  void *library = dlmopen(engine_namespace, path, RTLD_LAZY | RTLD_LOCAL);
  if (library && engine_namespace == LM_ID_NEWLM) {
    Lmid_t made;
    if (dlinfo(library, RTLD_DI_LMID, &made) == 0) engine_namespace = made;
  }
  return library ? library : dlopen(path, RTLD_LAZY);
}
#else
static void *open_library(const char *path) { return dlopen(path, RTLD_LAZY); }
#endif

/* Opens onnxruntime, then sherpa-onnx's C API, and finds the calls a voice
 * makes; null when it worked, else what went wrong. */
JNIEXPORT jbyteArray JNICALL Java_com_aimdi_xtanative_services_speech_SherpaJni_nativeOpen(
    JNIEnv *env, jclass type, jbyteArray onnxruntime, jbyteArray sherpa) {
  (void)type;
  jbyte *onnx_path = (*env)->GetByteArrayElements(env, onnxruntime, NULL);
  jbyte *sherpa_path = (*env)->GetByteArrayElements(env, sherpa, NULL);
  jbyteArray error = NULL;
  if (!onnx_path || !sherpa_path) {
    error = bytes_of(env, "out of memory");
  } else if (!open_library((const char *)onnx_path)) {
    error = bytes_of(env, dlerror());
  } else {
    void *library = open_library((const char *)sherpa_path);
    if (!library) {
      error = bytes_of(env, dlerror());
    } else {
      const char *missing = NULL;
      api.create = (const void *(*)(const TtsConfig *))find(library, "SherpaOnnxCreateOfflineTts", &missing);
      api.destroy = (void (*)(const void *))find(library, "SherpaOnnxDestroyOfflineTts", &missing);
      api.generate = (const GeneratedAudio *(*)(const void *, const char *, int32_t, float))find(library, "SherpaOnnxOfflineTtsGenerate", &missing);
      api.free_audio = (void (*)(const GeneratedAudio *))find(library, "SherpaOnnxDestroyOfflineTtsGeneratedAudio", &missing);
      api.write_wave = (int32_t (*)(const float *, int32_t, int32_t, const char *))find(library, "SherpaOnnxWriteWave", &missing);
      if (missing) error = bytes_of(env, missing);
    }
  }
  if (onnx_path) (*env)->ReleaseByteArrayElements(env, onnxruntime, onnx_path, JNI_ABORT);
  if (sherpa_path) (*env)->ReleaseByteArrayElements(env, sherpa, sherpa_path, JNI_ABORT);
  return error;
}

/* The values XTA's Dart binding gives every field it does not set itself. */
static void defaults(TtsConfig *config, int32_t threads) {
  memset(config, 0, sizeof *config);
  VitsConfig *vits = &config->model.vits;
  vits->model = vits->lexicon = vits->tokens = vits->data_dir = "";
  vits->noise_scale = 0.667f;
  vits->noise_scale_w = 0.8f;
  vits->length_scale = 1.0f;
  MatchaConfig *matcha = &config->model.matcha;
  matcha->acoustic_model = matcha->vocoder = matcha->lexicon = matcha->tokens = matcha->data_dir = "";
  matcha->noise_scale = 0.667f;
  matcha->length_scale = 1.0f;
  KokoroConfig *kokoro = &config->model.kokoro;
  kokoro->model = kokoro->voices = kokoro->tokens = kokoro->data_dir = kokoro->lexicon = kokoro->lang = "";
  kokoro->length_scale = 1.0f;
  KittenConfig *kitten = &config->model.kitten;
  kitten->model = kitten->voices = kitten->tokens = kitten->data_dir = "";
  kitten->length_scale = 1.0f;
  ZipVoiceConfig *zipvoice = &config->model.zipvoice;
  zipvoice->tokens = zipvoice->encoder = zipvoice->decoder = zipvoice->vocoder = zipvoice->data_dir = zipvoice->lexicon = "";
  zipvoice->feat_scale = 0.1f;
  zipvoice->t_shift = 0.5f;
  zipvoice->target_rms = 0.1f;
  zipvoice->guidance_scale = 1.0f;
  PocketConfig *pocket = &config->model.pocket;
  pocket->lm_flow = pocket->lm_main = pocket->encoder = pocket->decoder = "";
  pocket->text_conditioner = pocket->vocab_json = pocket->token_scores_json = "";
  pocket->voice_embedding_cache_capacity = 50;
  SupertonicConfig *supertonic = &config->model.supertonic;
  supertonic->duration_predictor = supertonic->text_encoder = supertonic->vector_estimator = "";
  supertonic->vocoder = supertonic->tts_json = supertonic->unicode_indexer = supertonic->voice_style = "";
  config->model.num_threads = threads;
  config->model.debug = 0;
  config->model.provider = "cpu";
  config->rule_fsts = config->rule_fars = "";
  config->max_num_sentences = 1;
  config->silence_scale = 0.2f;
}

/* Loads a voice; 0 when it cannot. */
JNIEXPORT jlong JNICALL Java_com_aimdi_xtanative_services_speech_SherpaJni_nativeCreate(
    JNIEnv *env, jclass type, jint kind, jbyteArray model, jbyteArray tokens, jbyteArray data_dir, jbyteArray voices, jint threads) {
  (void)type;
  if (!api.create) return 0;
  jbyte *m = (*env)->GetByteArrayElements(env, model, NULL);
  jbyte *t = (*env)->GetByteArrayElements(env, tokens, NULL);
  jbyte *d = (*env)->GetByteArrayElements(env, data_dir, NULL);
  jbyte *v = (*env)->GetByteArrayElements(env, voices, NULL);
  const void *tts = NULL;
  if (m && t && d && v) {
    TtsConfig config;
    defaults(&config, threads);
    if (kind == KIND_KOKORO) {
      config.model.kokoro.model = (const char *)m;
      config.model.kokoro.voices = (const char *)v;
      config.model.kokoro.tokens = (const char *)t;
      config.model.kokoro.data_dir = (const char *)d;
    } else {
      config.model.vits.model = (const char *)m;
      config.model.vits.tokens = (const char *)t;
      config.model.vits.data_dir = (const char *)d;
    }
    tts = api.create(&config);
  }
  if (m) (*env)->ReleaseByteArrayElements(env, model, m, JNI_ABORT);
  if (t) (*env)->ReleaseByteArrayElements(env, tokens, t, JNI_ABORT);
  if (d) (*env)->ReleaseByteArrayElements(env, data_dir, d, JNI_ABORT);
  if (v) (*env)->ReleaseByteArrayElements(env, voices, v, JNI_ABORT);
  return (jlong)(intptr_t)tts;
}

/* Speaks text into a WAV file; the samples written, 0 when none were. */
JNIEXPORT jint JNICALL Java_com_aimdi_xtanative_services_speech_SherpaJni_nativeSpeak(
    JNIEnv *env, jclass type, jlong tts, jbyteArray text, jint speaker, jfloat speed, jbyteArray out) {
  (void)type;
  if (!api.generate || !tts) return 0;
  jbyte *words = (*env)->GetByteArrayElements(env, text, NULL);
  jbyte *path = (*env)->GetByteArrayElements(env, out, NULL);
  jint written = 0;
  if (words && path) {
    const GeneratedAudio *audio = api.generate((const void *)(intptr_t)tts, (const char *)words, speaker, speed);
    if (audio) {
      if (audio->n > 0 && api.write_wave(audio->samples, audio->n, audio->sample_rate, (const char *)path) == 1) written = audio->n;
      api.free_audio(audio);
    }
  }
  if (words) (*env)->ReleaseByteArrayElements(env, text, words, JNI_ABORT);
  if (path) (*env)->ReleaseByteArrayElements(env, out, path, JNI_ABORT);
  return written;
}

JNIEXPORT void JNICALL Java_com_aimdi_xtanative_services_speech_SherpaJni_nativeDestroy(JNIEnv *env, jclass type, jlong tts) {
  (void)env;
  (void)type;
  if (api.destroy && tts) api.destroy((const void *)(intptr_t)tts);
}
