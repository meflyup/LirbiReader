LOCAL_PATH := $(call my-dir)
TOP_LOCAL_PATH := $(LOCAL_PATH)

MUPDF_ROOT := $(realpath $(LOCAL_PATH)/../../..)


include $(TOP_LOCAL_PATH)/mobi/Android.mk
include $(TOP_LOCAL_PATH)/hqx/Android.mk
include $(TOP_LOCAL_PATH)/djvu/Android.mk

include $(TOP_LOCAL_PATH)/MuPdf.mk

include $(CLEAR_VARS)

LOCAL_ARM_MODE := $(MY_ARM_MODE)

LOCAL_C_INCLUDES := \
	$(MUPDF_ROOT)/include \
	$(MUPDF_ROOT)/source/fitz \
	$(MUPDF_ROOT)/source/pdf \
	$(TOP_LOCAL_PATH)/djvu \
    $(TOP_LOCAL_PATH)/hqx \
    	
LOCAL_CFLAGS := -DHAVE_ANDROID
LOCAL_MODULE := mypdf

LOCAL_SRC_FILES := \
	ebookdroidjni.c \
	DjvuDroidBridge.cpp \
	cbdroidbridge.c \
	mupdfdroidbridge1.c \
	jni_concurrent.c

LOCAL_STATIC_LIBRARIES := djvu hqx mupdf_core mupdf_thirdparty 
LOCAL_LDLIBS := -lm -llog -ljnigraphics

include $(BUILD_SHARED_LIBRARY)
