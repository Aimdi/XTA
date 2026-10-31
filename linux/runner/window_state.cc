#include "window_state.h"

// Kept beside the app's other data, in ~/.local/share/com.aimdi.xta/.
static const gchar kFileName[] = "window.ini";
static const gchar kGroup[] = "window";

// How long a change rests before it is written, so dragging the window's
// edge writes it once.
static const guint kSaveDelayMs = 500;

// Larger than any screen: a side past it is a damaged file, not a size.
static const gint kMaxSide = 16384;

typedef struct {
  gint width;
  gint height;
  gboolean maximized;
  gboolean fullscreen;
  guint save_source;
} WindowState;

static gchar* window_state_path() {
  return g_build_filename(g_get_user_data_dir(), APPLICATION_ID, kFileName,
                          nullptr);
}

static void window_state_load(WindowState* state) {
  g_autoptr(GKeyFile) file = g_key_file_new();
  g_autofree gchar* path = window_state_path();
  if (!g_key_file_load_from_file(file, path, G_KEY_FILE_NONE, nullptr)) {
    return;
  }
  gint width = g_key_file_get_integer(file, kGroup, "width", nullptr);
  gint height = g_key_file_get_integer(file, kGroup, "height", nullptr);
  if (width > 0 && height > 0 && width <= kMaxSide && height <= kMaxSide) {
    state->width = width;
    state->height = height;
  }
  state->maximized =
      g_key_file_get_boolean(file, kGroup, "maximized", nullptr);
}

static gboolean window_state_save(gpointer data) {
  WindowState* state = static_cast<WindowState*>(data);
  state->save_source = 0;
  g_autoptr(GKeyFile) file = g_key_file_new();
  g_key_file_set_integer(file, kGroup, "width", state->width);
  g_key_file_set_integer(file, kGroup, "height", state->height);
  g_key_file_set_boolean(file, kGroup, "maximized", state->maximized);
  g_autofree gchar* path = window_state_path();
  g_autofree gchar* folder = g_path_get_dirname(path);
  g_mkdir_with_parents(folder, 0700);
  g_key_file_save_to_file(file, path, nullptr);
  return G_SOURCE_REMOVE;
}

static void window_state_save_soon(WindowState* state) {
  if (state->save_source != 0) g_source_remove(state->save_source);
  state->save_source = g_timeout_add(kSaveDelayMs, window_state_save, state);
}

// The size to come back to is the one the window has while it is neither
// maximized nor full screen.
static void on_size_allocate(GtkWidget* widget,
                             GdkRectangle* allocation,
                             gpointer data) {
  WindowState* state = static_cast<WindowState*>(data);
  if (state->maximized || state->fullscreen) return;
  gint width = 0;
  gint height = 0;
  gtk_window_get_size(GTK_WINDOW(widget), &width, &height);
  if (width == state->width && height == state->height) return;
  state->width = width;
  state->height = height;
  window_state_save_soon(state);
}

static gboolean on_window_state_event(GtkWidget* widget,
                                      GdkEventWindowState* event,
                                      gpointer data) {
  WindowState* state = static_cast<WindowState*>(data);
  const gboolean maximized =
      (event->new_window_state & GDK_WINDOW_STATE_MAXIMIZED) != 0;
  state->fullscreen =
      (event->new_window_state & GDK_WINDOW_STATE_FULLSCREEN) != 0;
  if (maximized != state->maximized) {
    state->maximized = maximized;
    window_state_save_soon(state);
  }
  return FALSE;
}

// A change still waiting is written before the window goes.
static void on_destroy(GtkWidget* widget, gpointer data) {
  WindowState* state = static_cast<WindowState*>(data);
  if (state->save_source != 0) {
    g_source_remove(state->save_source);
    window_state_save(state);
  }
  g_free(state);
}

// A size kept on a larger screen is cut down to fit this one.
static void fit_to_screen(GtkWindow* window, WindowState* state) {
  GdkDisplay* display = gtk_widget_get_display(GTK_WIDGET(window));
  GdkMonitor* monitor =
      display != nullptr ? gdk_display_get_primary_monitor(display) : nullptr;
  if (monitor == nullptr) return;
  GdkRectangle area;
  gdk_monitor_get_workarea(monitor, &area);
  if (area.width > 0) state->width = MIN(state->width, area.width);
  if (area.height > 0) state->height = MIN(state->height, area.height);
}

void window_state_track(GtkWindow* window,
                        gint default_width,
                        gint default_height) {
  WindowState* state = g_new0(WindowState, 1);
  state->width = default_width;
  state->height = default_height;
  window_state_load(state);
  fit_to_screen(window, state);
  gtk_window_set_default_size(window, state->width, state->height);
  if (state->maximized) gtk_window_maximize(window);
  g_signal_connect(window, "size-allocate", G_CALLBACK(on_size_allocate),
                   state);
  g_signal_connect(window, "window-state-event",
                   G_CALLBACK(on_window_state_event), state);
  g_signal_connect(window, "destroy", G_CALLBACK(on_destroy), state);
}
