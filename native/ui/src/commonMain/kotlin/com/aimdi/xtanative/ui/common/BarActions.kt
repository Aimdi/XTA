package com.aimdi.xtanative.ui.common

/**
 * Whether a screen's one making action (a new antenna, a place, a note)
 * sits in its top bar, as a named icon with a tooltip, rather than in a
 * round button in the corner: on the desktop, where a wide window puts the
 * corner far from what the action is about and the pointer finds the bar.
 * A phone keeps the round button under the thumb.
 */
internal expect val ActionsInTopBar: Boolean
