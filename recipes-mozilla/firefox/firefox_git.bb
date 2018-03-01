# Copyright (C) 2009-2015, O.S. Systems Software Ltda. All Rights Reserved
# Released under the MIT license (see packages/COPYING)

DESCRIPTION ?= "Browser made by mozilla"
DEPENDS += "curl startup-notification libevent cairo libnotify \
            virtual/libgl pulseaudio yasm-native icu \
           "
RDEPENDS_${PN}-dev = "dbus"

LICENSE = "MPLv2 | GPLv2+ | LGPLv2.1+"
LIC_FILES_CHKSUM = "file://toolkit/content/license.html;endline=33;md5=f51d0fbc370c551d7371775b4f6544ca"

SRC_URI = "git://github.com/mozilla/gecko-dev.git;branch=master \
           file://mozconfig \
           file://mozilla-firefox.png \
           file://mozilla-firefox.desktop \
           file://prefs/vendor.js \
           file://prefs/autoconfig.js \
           file://prefs/autoconfig.cfg \
           file://fixes/rustc_target_force.patch \
           file://fixes/0001-Always-accept-the-configure-option-with-gl-provider.patch \
           file://fixes/0001-Fix-a-build-error-of-Gecko-Profiler-for-Linux-ARM.patch \
           file://fixes/0001-Add-a-preference-to-force-enable-touch-events-withou.patch \
           file://fixes/fix-get-cpu-feature-definition-conflict.patch \
           file://gn-configs/ \
           "

#FIXME: Set exact source revision
SRCREV = "${AUTOREV}"

PR = "r0"
S = "${WORKDIR}/git"
MOZ_APP_BASE_VERSION = "${@'${PV}'.replace('esr', '')}"

inherit mozilla

DISABLE_STATIC=""
EXTRA_OEMAKE += "installdir=${libdir}/${PN}-${MOZ_APP_BASE_VERSION}"

ARM_INSTRUCTION_SET = "arm"

PACKAGECONFIG ??= "${@bb.utils.contains("DISTRO_FEATURES", "alsa", "alsa", "", d)} \
                   ${@bb.utils.contains("DISTRO_FEATURES", "wayland", "wayland", "", d)} \
"
PACKAGECONFIG[alsa] = "--enable-alsa,--disable-alsa,alsa-lib"
PACKAGECONFIG[wayland] = "--enable-default-toolkit=cairo-gtk3-wayland,"
PACKAGECONFIG[glx] = ",,,"
PACKAGECONFIG[egl] = "--with-gl-provider=EGL,,virtual/egl,"
PACKAGECONFIG[openmax] = ",,,"
PACKAGECONFIG[webgl] = ",,,"
PACKAGECONFIG[canvas-gpu] = ",,,"

SRC_URI += "${@bb.utils.contains('PACKAGECONFIG', 'wayland', \
           ' \
            file://wayland/wayland-hacks.js \
            file://wayland/0001-Permit-to-use-gtk-wayland-3.0-3.18.patch \
            file://wayland/0001-Add-ad-hoc-solution-to-enable-Alt-modifier-on-Waylan.patch \
           ', \
           '', d)}"

# Gecko Embedded's Additional wayland patches to support EGL
#
# TODO: Most of them aren't ported to ESR60 yet
SRC_URI += "${@bb.utils.contains('PACKAGECONFIG', 'wayland egl', \
           ' \
            file://wayland/egl/0001-GLLibraryLoader-Use-given-symbol-lookup-function-fir.patch \
            file://wayland/egl/0001-Enable-sharing-SharedSurface_EGLImage.patch \
            file://wayland/egl/0001-Call-fEGLImageTargetTexture2D-eariler.patch \
            file://wayland/egl/0001-Create-workaround-to-use-BasicCompositor-to-prevent-.patch \
            file://prefs/disable-e10s.js \
           ', \
           '', d)}"

# Add a config file to enable GPU acceleration by default.
SRC_URI += "${@bb.utils.contains_any('PACKAGECONFIG', 'glx egl', \
           'file://prefs/gpu.js', '', d)}"

SRC_URI += "${@bb.utils.contains('PACKAGECONFIG', 'openmax', \
           ' \
            file://openmax/0001-Add-initial-implementation-of-PureOmxPlatformLayer.patch \
            file://openmax/0002-OmxDataDecoder-Fix-a-stall-issue-on-shutting-down.patch \
            file://openmax/openmax.js \
           ', \
           '', d)}"

SRC_URI += "${@bb.utils.contains_any('PACKAGECONFIG', 'webgl', \
           'file://prefs/webgl.js', '', d)}"

SRC_URI += "${@bb.utils.contains_any('PACKAGECONFIG', 'canvas-gpu', \
           'file://prefs/canvas-gpu.js', '', d)}"

python do_check_variables() {
    if bb.utils.contains('PACKAGECONFIG', 'glx egl', True, False, d):
        bb.warn("%s: GLX support will be disabled when EGL is enabled!" % bb.data.getVar('PN', d, 1))
    if bb.utils.contains_any('PACKAGECONFIG', 'glx egl', False, True, d):
        if bb.utils.contains('PACKAGECONFIG', 'webgl', True, False, d):
            bb.warn("%s: WebGL won't be enabled when both glx and egl aren't enabled!" % bb.data.getVar('PN', d, 1))
        if bb.utils.contains('PACKAGECONFIG', 'canvas-gpu', True, False, d):
            bb.warn("%s: Canvas acceleration won't be enabled when both glx and egl aren't enabled!" % bb.data.getVar('PN', d, 1))
}
addtask check_variables before do_configure

do_configure() {
    export SHELL=/bin/bash

    ./mach configure ${CONFIGURE_ARGS}
    cp ${WORKDIR}/gn-configs/*.json ${S}/media/webrtc/gn-configs/
    ./mach build-backend -b GnMozbuildWriter
}

do_compile() {
    export SHELL="/bin/bash"

    ./mach build
}

do_install() {
    export SHELL="/bin/bash"

    INSTALL_SDK=0 DESTDIR="${D}" ./mach install
}

do_install_append() {
    install -d ${D}${datadir}/applications
    install -d ${D}${datadir}/pixmaps

    install -m 0644 ${WORKDIR}/mozilla-firefox.desktop ${D}${datadir}/applications/
    install -m 0644 ${WORKDIR}/mozilla-firefox.png ${D}${datadir}/pixmaps/
    install -m 0644 ${WORKDIR}/prefs/vendor.js ${D}${libdir}/${PN}/defaults/pref/
    install -m 0644 ${WORKDIR}/prefs/autoconfig.js ${D}${libdir}/${PN}/defaults/pref/
    install -m 0644 ${WORKDIR}/prefs/autoconfig.cfg ${D}${libdir}/${PN}/
    if [ -n "${@bb.utils.contains_any('PACKAGECONFIG', 'glx egl', '1', '', d)}" ]; then
        install -m 0644 ${WORKDIR}/prefs/gpu.js ${D}${libdir}/${PN}/defaults/pref/
    fi
    if [ -n "${@bb.utils.contains('PACKAGECONFIG', 'openmax', '1', '', d)}" ]; then
        install -m 0644 ${WORKDIR}/openmax/openmax.js ${D}${libdir}/${PN}/defaults/pref/
    fi
    if [ -n "${@bb.utils.contains('PACKAGECONFIG', 'wayland', '1', '', d)}" ]; then
        install -m 0644 ${WORKDIR}/wayland/wayland-hacks.js ${D}${libdir}/${PN}/defaults/pref/
    fi
    if [ -n "${@bb.utils.contains('PACKAGECONFIG', 'wayland egl', '1', '', d)}" ]; then
        install -m 0644 ${WORKDIR}/prefs/disable-e10s.js ${D}${libdir}/${PN}/defaults/pref/
    fi
    if [ -n "${@bb.utils.contains('PACKAGECONFIG', 'webgl', '1', '', d)}" ]; then
        install -m 0644 ${WORKDIR}/prefs/webgl.js ${D}${libdir}/${PN}/defaults/pref/
    fi
    if [ -n "${@bb.utils.contains('PACKAGECONFIG', 'canvas-gpu', '1', '', d)}" ]; then
        install -m 0644 ${WORKDIR}/prefs/canvas-gpu.js ${D}${libdir}/${PN}/defaults/pref/
    fi

    # Fix ownership of files
    chown root:root -R ${D}${datadir}
    chown root:root -R ${D}${libdir}
}

FILES_${PN} = "${bindir}/${PN} \
               ${datadir}/applications/ \
               ${datadir}/pixmaps/ \
               ${libdir}/${PN}/* \
               ${libdir}/${PN}/.autoreg \
               ${bindir}/defaults"
FILES_${PN}-dev += "${datadir}/idl ${bindir}/${PN}-config ${libdir}/${PN}-devel-*"
FILES_${PN}-staticdev += "${libdir}/${PN}-devel-*/sdk/lib/*.a"
FILES_${PN}-dbg += "${libdir}/${PN}/.debug \
                    ${libdir}/${PN}/*/.debug \
                    ${libdir}/${PN}/*/*/.debug \
                    ${libdir}/${PN}/*/*/*/.debug \
                    ${libdir}/${PN}-devel-*/*/.debug \
                    ${libdir}/${PN}-devel-*/*/*/.debug \
                    ${libdir}/${PN}-devel-*/*/*/*/.debug \
                    ${bindir}/.debug"

# We don't build XUL as system shared lib, so we can mark all libs as private
PRIVATE_LIBS = " \
    libmozjs.so \
    libxpcom.so \
    libnspr4.so \
    libxul.so \
    libmozalloc.so \
    libplc4.so \
    libplds4.so \
    liblgpllibs.so \
    libmozgtk.so \
    libmozwayland.so \
    libmozsqlite3.so \
    libclearkey.so \
"

# mark libraries also provided by nss as private too
PRIVATE_LIBS += " \
    libfreebl3.so \
    libfreeblpriv3.so \
    libnss3.so \
    libnssckbi.so \
    libsmime3.so \
    libnssutil3.so \
    libnssdbm3.so \
    libssl3.so \
    libsoftokn3.so \
"
