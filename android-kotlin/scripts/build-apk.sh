#!/usr/bin/env bash
# Builds the debug and release APKs of the Android app and collects them in dist/.
#
# Usage: scripts/build-apk.sh [--debug] [--release] [--clean] [--test]
#   (no build flag)  build debug and release
#   --debug          only the debug APK
#   --release        only the release APK
#   --clean          run ./gradlew clean first
#   --test           run unit tests (testDebugUnitTest) before building
#
# Environment overrides:
#   MYVU_JDK         JDK home to build with (JDK 21+). Default: first of $JAVA_HOME, /opt/jdk-25, ~/.jdks/*
#   ANDROID_HOME     Android SDK root. Default: $ANDROID_SDK_ROOT, ~/android-sdk, ~/Android/Sdk
#
# Release signing: place keystore.properties in the project root (see BUILD_INSTRUCTIONS.md §4).
# Without it the release APK is unsigned and is named *-unsigned.apk.

set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_DIR"

DO_DEBUG=0
DO_RELEASE=0
DO_CLEAN=0
DO_TEST=0
SELECTED=0
for arg in "$@"; do
    case "$arg" in
        --debug) DO_DEBUG=1; SELECTED=1 ;;
        --release) DO_RELEASE=1; SELECTED=1 ;;
        --clean) DO_CLEAN=1 ;;
        --test) DO_TEST=1 ;;
        -h|--help) sed -n '2,15p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) echo "error: unknown option: $arg" >&2; exit 2 ;;
    esac
done
# No build flag means both builds; --clean and --test alone do not turn them off.
if [ "$SELECTED" -eq 0 ]; then DO_DEBUG=1; DO_RELEASE=1; fi

die() { echo "error: $*" >&2; exit 1; }
log() { echo "==> $*"; }

java_major() {
    # Prints the major version of a JDK home, or nothing if it is not a usable JDK.
    local home="$1"
    [ -x "$home/bin/java" ] || return 0
    "$home/bin/java" -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -1
}

find_jdk() {
    local candidates=()
    [ -n "${MYVU_JDK:-}" ] && candidates+=("$MYVU_JDK")
    [ -n "${JAVA_HOME:-}" ] && candidates+=("$JAVA_HOME")
    candidates+=("/opt/jdk-25")
    for d in "$HOME"/.jdks/*/; do [ -d "$d" ] && candidates+=("${d%/}"); done
    for c in "${candidates[@]}"; do
        local major
        major="$(java_major "$c")"
        if [ -n "$major" ] && [ "$major" -ge 21 ]; then echo "$c"; return 0; fi
    done
    return 1
}

find_sdk() {
    for c in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$HOME/android-sdk" "$HOME/Android/Sdk"; do
        if [ -n "$c" ] && [ -d "$c/platforms" ]; then echo "$c"; return 0; fi
    done
    return 1
}

[ -x ./gradlew ] || die "./gradlew not found in $PROJECT_DIR"

JDK="$(find_jdk)" || die "no JDK 21+ found. Set MYVU_JDK=/path/to/jdk"
SDK="$(find_sdk)" || die "no Android SDK found. Set ANDROID_HOME=/path/to/sdk"
export ANDROID_HOME="$SDK"
log "JDK: $JDK ($(java_major "$JDK"))"
log "Android SDK: $SDK"

VERSION_NAME="$(sed -n 's/.*versionName = "\(.*\)".*/\1/p' app/build.gradle.kts | head -1)"
[ -n "$VERSION_NAME" ] || die "could not read versionName from app/build.gradle.kts"

SIGNED=0
[ -f keystore.properties ] && SIGNED=1
if [ "$DO_RELEASE" -eq 1 ] && [ "$SIGNED" -eq 0 ]; then
    log "warning: keystore.properties not found: release APK will be UNSIGNED"
fi

# Gradle's -D reaches the daemon JVM even when gradle.properties pins another JDK path.
GRADLE_ARGS=("-Dorg.gradle.java.home=$JDK")
TASKS=()
[ "$DO_CLEAN" -eq 1 ] && TASKS+=(clean)
[ "$DO_TEST" -eq 1 ] && TASKS+=(testDebugUnitTest)
[ "$DO_DEBUG" -eq 1 ] && TASKS+=(assembleDebug)
[ "$DO_RELEASE" -eq 1 ] && TASKS+=(assembleRelease)

log "gradle ${TASKS[*]}"
./gradlew "${GRADLE_ARGS[@]}" "${TASKS[@]}"

DIST="$PROJECT_DIR/dist"
mkdir -p "$DIST"
collected=()

collect() {
    local src="$1" dst="$2"
    [ -f "$src" ] || die "expected APK missing: $src"
    cp -f "$src" "$DIST/$dst"
    collected+=("$DIST/$dst")
    log "built: dist/$dst"
}

if [ "$DO_DEBUG" -eq 1 ]; then
    collect "app/build/outputs/apk/debug/app-debug.apk" "myvu-$VERSION_NAME-debug.apk"
fi
if [ "$DO_RELEASE" -eq 1 ]; then
    if [ "$SIGNED" -eq 1 ]; then
        collect "app/build/outputs/apk/release/app-release.apk" "myvu-$VERSION_NAME-release.apk"
    else
        collect "app/build/outputs/apk/release/app-release-unsigned.apk" "myvu-$VERSION_NAME-release-unsigned.apk"
    fi
fi

(cd "$DIST" && sha256sum "${collected[@]##*/}" > SHA256SUMS)
log "checksums: dist/SHA256SUMS"
log "done"
