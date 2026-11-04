# The JNI shim downloaded voices speak through (xta_sherpa.c, SherpaJni.kt),
# which :app builds with ndk-build for every processor. It links against
# nothing of the voice engine, which it opens where it was downloaded.
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := xta_sherpa
LOCAL_SRC_FILES := xta_sherpa.c
LOCAL_CFLAGS := -O2 -Wall -Wextra
LOCAL_LDLIBS := -ldl
# Phones with 16 KB memory pages load only libraries aligned to them.
LOCAL_LDFLAGS := -Wl,-z,max-page-size=16384
include $(BUILD_SHARED_LIBRARY)
