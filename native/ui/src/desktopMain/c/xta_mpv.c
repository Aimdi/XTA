/*
 * The JNI side of MpvJni (ui/media/Mpv.kt): libmpv's client API and its
 * software render API, opened at run time from the system's mpv library, as
 * XTA's media_kit opens it on Linux. Nothing here links against libmpv, so
 * the app starts and runs without it, handing videos to the system's player.
 *
 * The desktop build compiles this with cc (desktop/build.gradle.kts). Strings
 * arrive as NUL-terminated UTF-8.
 */
#include <dlfcn.h>
#include <jni.h>
#include <locale.h>
#include <math.h>
#include <pthread.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#if defined(__GLIBC__) && defined(__x86_64__)
/* The shim runs wherever the app's Java runtime does: glibc 2.34 moved these
 * calls into libc under a new version, which an older one lacks. */
__asm__(".symver dlopen,dlopen@GLIBC_2.2.5");
__asm__(".symver dlsym,dlsym@GLIBC_2.2.5");
__asm__(".symver dlerror,dlerror@GLIBC_2.2.5");
#endif

/* mpv's client.h and render.h, as far as this shim uses them: the layouts and
 * values are its stable ABI (client API 1.109 and later, mpv 0.33 on). */
typedef struct mpv_handle mpv_handle;
typedef struct mpv_render_context mpv_render_context;

enum { FORMAT_NONE = 0, FORMAT_STRING = 1, FORMAT_FLAG = 3, FORMAT_INT64 = 4, FORMAT_DOUBLE = 5, FORMAT_NODE_MAP = 8 };
enum { EVENT_START_FILE = 6, EVENT_END_FILE = 7, EVENT_PROPERTY_CHANGE = 22 };
enum { PARAM_INVALID = 0, PARAM_API_TYPE = 1, PARAM_BLOCK_FOR_TARGET_TIME = 12, PARAM_SW_SIZE = 17, PARAM_SW_FORMAT = 18, PARAM_SW_STRIDE = 19, PARAM_SW_POINTER = 20 };

typedef struct node node;
typedef struct node_list {
  int num;
  node *values;
  char **keys;
} node_list;
struct node {
  union {
    char *string;
    int flag;
    int64_t int64;
    double double_;
    node_list *list;
    void *ba;
  } u;
  int format;
};

typedef struct {
  int event_id;
  int error;
  uint64_t reply_userdata;
  void *data;
} event;

typedef struct {
  const char *name;
  int format;
  void *data;
} event_property;

typedef struct {
  int64_t playlist_entry_id;
} event_start_file;

typedef struct {
  int reason;
  int error;
  int64_t playlist_entry_id;
} event_end_file;

typedef struct {
  int type;
  void *data;
} render_param;

static struct {
  unsigned long (*client_api_version)(void);
  mpv_handle *(*create)(void);
  int (*initialize)(mpv_handle *);
  void (*terminate_destroy)(mpv_handle *);
  int (*set_option_string)(mpv_handle *, const char *, const char *);
  int (*command_ret)(mpv_handle *, const char **, node *);
  void (*free_node_contents)(node *);
  int (*set_property)(mpv_handle *, const char *, int, void *);
  int (*set_property_string)(mpv_handle *, const char *, const char *);
  char *(*get_property_string)(mpv_handle *, const char *);
  void (*free)(void *);
  int (*observe_property)(mpv_handle *, uint64_t, const char *, int);
  event *(*wait_event)(mpv_handle *, double);
  void (*wakeup)(mpv_handle *);
  const char *(*error_string)(int);
  int (*render_context_create)(mpv_render_context **, mpv_handle *, render_param *);
  void (*render_context_set_update_callback)(mpv_render_context *, void (*)(void *), void *);
  uint64_t (*render_context_update)(mpv_render_context *);
  int (*render_context_render)(mpv_render_context *, render_param *);
  void (*render_context_free)(mpv_render_context *);
} api;

static jbyteArray bytes_of(JNIEnv *env, const char *text) {
  if (!text) return NULL;
  jsize length = (jsize)strlen(text);
  jbyteArray out = (*env)->NewByteArray(env, length);
  if (out) (*env)->SetByteArrayRegion(env, out, 0, length, (const jbyte *)text);
  return out;
}

static void *find(void *library, const char *name, const char **missing) {
  void *symbol = dlsym(library, name);
  if (!symbol && !*missing) *missing = name;
  return symbol;
}

/* Opens [library] (a name the loader looks up, or a path) and finds the calls
 * the app makes; null when it worked, else what went wrong. */
JNIEXPORT jbyteArray JNICALL Java_com_aimdi_xtanative_ui_media_MpvJni_nativeOpen(JNIEnv *env, jclass type, jbyteArray name) {
  (void)type;
  jbyte *path = (*env)->GetByteArrayElements(env, name, NULL);
  if (!path) return bytes_of(env, "out of memory");
  void *library = dlopen((const char *)path, RTLD_NOW | RTLD_LOCAL);
  (*env)->ReleaseByteArrayElements(env, name, path, JNI_ABORT);
  if (!library) return bytes_of(env, dlerror());
  const char *missing = NULL;
#define FIND(field, symbol) *(void **)(&api.field) = find(library, symbol, &missing)
  FIND(client_api_version, "mpv_client_api_version");
  FIND(create, "mpv_create");
  FIND(initialize, "mpv_initialize");
  FIND(terminate_destroy, "mpv_terminate_destroy");
  FIND(set_option_string, "mpv_set_option_string");
  FIND(command_ret, "mpv_command_ret");
  FIND(free_node_contents, "mpv_free_node_contents");
  FIND(set_property, "mpv_set_property");
  FIND(set_property_string, "mpv_set_property_string");
  FIND(get_property_string, "mpv_get_property_string");
  FIND(free, "mpv_free");
  FIND(observe_property, "mpv_observe_property");
  FIND(wait_event, "mpv_wait_event");
  FIND(wakeup, "mpv_wakeup");
  FIND(error_string, "mpv_error_string");
  FIND(render_context_create, "mpv_render_context_create");
  FIND(render_context_set_update_callback, "mpv_render_context_set_update_callback");
  FIND(render_context_update, "mpv_render_context_update");
  FIND(render_context_render, "mpv_render_context_render");
  FIND(render_context_free, "mpv_render_context_free");
#undef FIND
  if (missing) {
    memset(&api, 0, sizeof api);
    return bytes_of(env, missing);
  }
  return NULL;
}

JNIEXPORT jlong JNICALL Java_com_aimdi_xtanative_ui_media_MpvJni_nativeApiVersion(JNIEnv *env, jclass type) {
  (void)env;
  (void)type;
  return api.client_api_version ? (jlong)api.client_api_version() : 0;
}

/* The strings of [array], each NUL-terminated, in [out] (count + 1 slots, the
 * last NULL); false when one could not be read. */
static int strings_of(JNIEnv *env, jobjectArray array, jbyteArray *arrays, const char **out, jsize count) {
  for (jsize i = 0; i < count; i++) {
    arrays[i] = (jbyteArray)(*env)->GetObjectArrayElement(env, array, i);
    out[i] = arrays[i] ? (const char *)(*env)->GetByteArrayElements(env, arrays[i], NULL) : NULL;
    if (!out[i]) return 0;
  }
  out[count] = NULL;
  return 1;
}

static void release_strings(JNIEnv *env, jbyteArray *arrays, const char **strings, jsize count) {
  for (jsize i = 0; i < count; i++) {
    if (arrays[i] && strings[i]) (*env)->ReleaseByteArrayElements(env, arrays[i], (jbyte *)strings[i], JNI_ABORT);
    if (arrays[i]) (*env)->DeleteLocalRef(env, arrays[i]);
  }
}

/* A player, not started yet ([nativeSetOption], then [nativeInitialize]); 0
 * when mpv made none. mpv reads numbers in the C locale only, and refuses to
 * make one in another. */
JNIEXPORT jlong JNICALL Java_com_aimdi_xtanative_ui_media_MpvJni_nativeCreate(JNIEnv *env, jclass type) {
  (void)env;
  (void)type;
  if (!api.create) return 0;
  const char *numeric = setlocale(LC_NUMERIC, NULL);
  if (!numeric || strcmp(numeric, "C") != 0) setlocale(LC_NUMERIC, "C");
  return (jlong)(intptr_t)api.create();
}

JNIEXPORT jint JNICALL Java_com_aimdi_xtanative_ui_media_MpvJni_nativeSetOption(JNIEnv *env, jclass type, jlong handle, jbyteArray name, jbyteArray value) {
  (void)type;
  jbyte *option = (*env)->GetByteArrayElements(env, name, NULL);
  jbyte *text = (*env)->GetByteArrayElements(env, value, NULL);
  int result = option && text ? api.set_option_string((mpv_handle *)(intptr_t)handle, (const char *)option, (const char *)text) : -1;
  if (option) (*env)->ReleaseByteArrayElements(env, name, option, JNI_ABORT);
  if (text) (*env)->ReleaseByteArrayElements(env, value, text, JNI_ABORT);
  return result;
}

JNIEXPORT jint JNICALL Java_com_aimdi_xtanative_ui_media_MpvJni_nativeInitialize(JNIEnv *env, jclass type, jlong handle) {
  (void)env;
  (void)type;
  return api.initialize((mpv_handle *)(intptr_t)handle);
}

JNIEXPORT jint JNICALL Java_com_aimdi_xtanative_ui_media_MpvJni_nativeObserve(JNIEnv *env, jclass type, jlong handle, jlong id, jbyteArray name, jint format) {
  (void)type;
  jbyte *property = (*env)->GetByteArrayElements(env, name, NULL);
  if (!property) return -1;
  int result = api.observe_property((mpv_handle *)(intptr_t)handle, (uint64_t)id, (const char *)property, format);
  (*env)->ReleaseByteArrayElements(env, name, property, JNI_ABORT);
  return result;
}

/* Runs a command and waits for it: the playlist entry it made (loadfile), else
 * 0; a negative mpv error when it failed. */
JNIEXPORT jlong JNICALL Java_com_aimdi_xtanative_ui_media_MpvJni_nativeCommand(JNIEnv *env, jclass type, jlong handle, jobjectArray args) {
  (void)type;
  jsize count = (*env)->GetArrayLength(env, args);
  jbyteArray *arrays = calloc((size_t)count + 1, sizeof *arrays);
  const char **strings = calloc((size_t)count + 1, sizeof *strings);
  jlong result = -1;
  if (arrays && strings && strings_of(env, args, arrays, strings, count)) {
    node out;
    memset(&out, 0, sizeof out);
    int error = api.command_ret((mpv_handle *)(intptr_t)handle, strings, &out);
    result = error < 0 ? error : 0;
    if (error >= 0 && out.format == FORMAT_NODE_MAP && out.u.list) {
      for (int i = 0; i < out.u.list->num; i++) {
        if (strcmp(out.u.list->keys[i], "playlist_entry_id") == 0 && out.u.list->values[i].format == FORMAT_INT64) result = out.u.list->values[i].u.int64;
      }
    }
    if (error >= 0) api.free_node_contents(&out);
  }
  if (arrays && strings) release_strings(env, arrays, strings, count);
  free(arrays);
  free(strings);
  return result;
}

JNIEXPORT jint JNICALL Java_com_aimdi_xtanative_ui_media_MpvJni_nativeSetString(JNIEnv *env, jclass type, jlong handle, jbyteArray name, jbyteArray value) {
  (void)type;
  jbyte *property = (*env)->GetByteArrayElements(env, name, NULL);
  jbyte *text = (*env)->GetByteArrayElements(env, value, NULL);
  int result = property && text ? api.set_property_string((mpv_handle *)(intptr_t)handle, (const char *)property, (const char *)text) : -1;
  if (property) (*env)->ReleaseByteArrayElements(env, name, property, JNI_ABORT);
  if (text) (*env)->ReleaseByteArrayElements(env, value, text, JNI_ABORT);
  return result;
}

JNIEXPORT jint JNICALL Java_com_aimdi_xtanative_ui_media_MpvJni_nativeSetDouble(JNIEnv *env, jclass type, jlong handle, jbyteArray name, jdouble value) {
  (void)type;
  jbyte *property = (*env)->GetByteArrayElements(env, name, NULL);
  if (!property) return -1;
  double number = value;
  int result = api.set_property((mpv_handle *)(intptr_t)handle, (const char *)property, FORMAT_DOUBLE, &number);
  (*env)->ReleaseByteArrayElements(env, name, property, JNI_ABORT);
  return result;
}

JNIEXPORT jint JNICALL Java_com_aimdi_xtanative_ui_media_MpvJni_nativeSetFlag(JNIEnv *env, jclass type, jlong handle, jbyteArray name, jboolean value) {
  (void)type;
  jbyte *property = (*env)->GetByteArrayElements(env, name, NULL);
  if (!property) return -1;
  int flag = value ? 1 : 0;
  int result = api.set_property((mpv_handle *)(intptr_t)handle, (const char *)property, FORMAT_FLAG, &flag);
  (*env)->ReleaseByteArrayElements(env, name, property, JNI_ABORT);
  return result;
}

JNIEXPORT jbyteArray JNICALL Java_com_aimdi_xtanative_ui_media_MpvJni_nativeGetString(JNIEnv *env, jclass type, jlong handle, jbyteArray name) {
  (void)type;
  jbyte *property = (*env)->GetByteArrayElements(env, name, NULL);
  if (!property) return NULL;
  char *value = api.get_property_string((mpv_handle *)(intptr_t)handle, (const char *)property);
  (*env)->ReleaseByteArrayElements(env, name, property, JNI_ABORT);
  jbyteArray out = bytes_of(env, value);
  if (value) api.free(value);
  return out;
}

/* The next event, waiting at most [timeout] seconds (negative: until one comes
 * or the player is woken), its id returned and its details in [out]: the error,
 * the request it answers, a property's new value (NaN while it has none), and a
 * file's end reason, its error and its playlist entry. */
JNIEXPORT jint JNICALL Java_com_aimdi_xtanative_ui_media_MpvJni_nativeWaitEvent(JNIEnv *env, jclass type, jlong handle, jdouble timeout, jdoubleArray out) {
  (void)type;
  event *e = api.wait_event((mpv_handle *)(intptr_t)handle, timeout);
  double fields[6] = {e->error, (double)e->reply_userdata, NAN, 0, 0, 0};
  if (e->event_id == EVENT_PROPERTY_CHANGE && e->data) {
    const event_property *property = e->data;
    if (property->format == FORMAT_DOUBLE) fields[2] = *(const double *)property->data;
    else if (property->format == FORMAT_FLAG) fields[2] = *(const int *)property->data;
    else if (property->format == FORMAT_INT64) fields[2] = (double)*(const int64_t *)property->data;
  } else if (e->event_id == EVENT_START_FILE && e->data) {
    fields[5] = (double)((const event_start_file *)e->data)->playlist_entry_id;
  } else if (e->event_id == EVENT_END_FILE && e->data) {
    const event_end_file *end = e->data;
    fields[3] = end->reason;
    fields[4] = end->error;
    fields[5] = (double)end->playlist_entry_id;
  }
  (*env)->SetDoubleArrayRegion(env, out, 0, 6, fields);
  return e->event_id;
}

JNIEXPORT void JNICALL Java_com_aimdi_xtanative_ui_media_MpvJni_nativeWakeup(JNIEnv *env, jclass type, jlong handle) {
  (void)env;
  (void)type;
  api.wakeup((mpv_handle *)(intptr_t)handle);
}

JNIEXPORT void JNICALL Java_com_aimdi_xtanative_ui_media_MpvJni_nativeDestroy(JNIEnv *env, jclass type, jlong handle) {
  (void)env;
  (void)type;
  api.terminate_destroy((mpv_handle *)(intptr_t)handle);
}

JNIEXPORT jbyteArray JNICALL Java_com_aimdi_xtanative_ui_media_MpvJni_nativeErrorString(JNIEnv *env, jclass type, jint error) {
  (void)type;
  return bytes_of(env, api.error_string ? api.error_string(error) : "unknown error");
}

/* A render context and what wakes its thread: mpv's update callback, which may
 * call nothing of mpv's, only sets [pending] and signals. */
typedef struct {
  mpv_render_context *context;
  pthread_mutex_t lock;
  pthread_cond_t wake;
  int pending;
} render;

static void on_update(void *data) {
  render *r = data;
  pthread_mutex_lock(&r->lock);
  r->pending = 1;
  pthread_cond_signal(&r->wake);
  pthread_mutex_unlock(&r->lock);
}

/* Draws [handle]'s video into memory from now on; 0 when it cannot. */
JNIEXPORT jlong JNICALL Java_com_aimdi_xtanative_ui_media_MpvJni_nativeRenderCreate(JNIEnv *env, jclass type, jlong handle) {
  (void)env;
  (void)type;
  render *r = calloc(1, sizeof *r);
  if (!r) return 0;
  pthread_mutex_init(&r->lock, NULL);
  pthread_cond_init(&r->wake, NULL);
  render_param params[] = {{PARAM_API_TYPE, "sw"}, {PARAM_INVALID, NULL}};
  if (api.render_context_create(&r->context, (mpv_handle *)(intptr_t)handle, params) < 0) {
    pthread_cond_destroy(&r->wake);
    pthread_mutex_destroy(&r->lock);
    free(r);
    return 0;
  }
  api.render_context_set_update_callback(r->context, on_update, r);
  return (jlong)(intptr_t)r;
}

/* Waits at most [timeout_ms] for mpv to ask for a redraw, or for
 * [nativeRenderWake]; whether either came. The wait is only a bound, so the
 * wall clock (the condition's own) is good enough for it. */
JNIEXPORT jboolean JNICALL Java_com_aimdi_xtanative_ui_media_MpvJni_nativeRenderWait(JNIEnv *env, jclass type, jlong pointer, jint timeout_ms) {
  (void)env;
  (void)type;
  render *r = (render *)(intptr_t)pointer;
  struct timespec until;
  clock_gettime(CLOCK_REALTIME, &until);
  until.tv_sec += timeout_ms / 1000;
  until.tv_nsec += (long)(timeout_ms % 1000) * 1000000L;
  if (until.tv_nsec >= 1000000000L) {
    until.tv_sec += 1;
    until.tv_nsec -= 1000000000L;
  }
  pthread_mutex_lock(&r->lock);
  while (!r->pending) {
    if (pthread_cond_timedwait(&r->wake, &r->lock, &until) != 0) break;
  }
  int woken = r->pending;
  r->pending = 0;
  pthread_mutex_unlock(&r->lock);
  return woken ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL Java_com_aimdi_xtanative_ui_media_MpvJni_nativeRenderWake(JNIEnv *env, jclass type, jlong pointer) {
  (void)env;
  (void)type;
  on_update((render *)(intptr_t)pointer);
}

JNIEXPORT jlong JNICALL Java_com_aimdi_xtanative_ui_media_MpvJni_nativeRenderUpdate(JNIEnv *env, jclass type, jlong pointer) {
  (void)env;
  (void)type;
  return (jlong)api.render_context_update(((render *)(intptr_t)pointer)->context);
}

/* The frame due now, drawn at [width] x [height] into [pixels] (RGB, a byte
 * each and one unused, [stride] bytes a row), without waiting for its time. */
JNIEXPORT jint JNICALL Java_com_aimdi_xtanative_ui_media_MpvJni_nativeRender(JNIEnv *env, jclass type, jlong pointer, jint width, jint height, jint stride, jlong pixels) {
  (void)env;
  (void)type;
  int size[2] = {width, height};
  size_t row = (size_t)stride;
  int block = 0;
  render_param params[] = {
      {PARAM_SW_SIZE, size},
      {PARAM_SW_FORMAT, "rgb0"},
      {PARAM_SW_STRIDE, &row},
      {PARAM_SW_POINTER, (void *)(intptr_t)pixels},
      {PARAM_BLOCK_FOR_TARGET_TIME, &block},
      {PARAM_INVALID, NULL},
  };
  return api.render_context_render(((render *)(intptr_t)pointer)->context, params);
}

JNIEXPORT void JNICALL Java_com_aimdi_xtanative_ui_media_MpvJni_nativeRenderFree(JNIEnv *env, jclass type, jlong pointer) {
  (void)env;
  (void)type;
  render *r = (render *)(intptr_t)pointer;
  api.render_context_set_update_callback(r->context, NULL, NULL);
  api.render_context_free(r->context);
  pthread_cond_destroy(&r->wake);
  pthread_mutex_destroy(&r->lock);
  free(r);
}
