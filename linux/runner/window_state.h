#ifndef RUNNER_WINDOW_STATE_H_
#define RUNNER_WINDOW_STATE_H_

#include <gtk/gtk.h>

// Opens [window] at the size and maximized state it had when it last
// closed, or at [default_width] x [default_height] the first time, and keeps
// both up to date in the app's data folder as the reader changes them.
void window_state_track(GtkWindow* window,
                        gint default_width,
                        gint default_height);

#endif  // RUNNER_WINDOW_STATE_H_
