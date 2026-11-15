#!/usr/bin/env bash
# Retry ephemeral Android SDK archive failures on standard GitHub runners.
# Pinned NDK/CMake versions must match Astra's native llama.cpp build.
# Runs only on the build machine; no Android app permissions or endpoints.
set -euo pipefail

: "${ANDROID_HOME:?ANDROID_HOME must point to the Android SDK}"
sdkmanager="${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager"
[[ -x "$sdkmanager" ]] || { echo "Android sdkmanager missing" >&2; exit 1; }

yes | "$sdkmanager" --licenses >/dev/null || true

sdk_ready() {
    [[ -s "$ANDROID_HOME/platforms/android-36/android.jar" ]] &&
    [[ -s "$ANDROID_HOME/ndk/29.0.13113456/source.properties" ]] &&
    [[ -x "$ANDROID_HOME/ndk/29.0.13113456/toolchains/llvm/prebuilt/linux-x86_64/bin/clang" ]] &&
    [[ -x "$ANDROID_HOME/cmake/3.31.6/bin/cmake" ]]
}

for attempt in 1 2 3; do
    echo "Android SDK pinned setup attempt $attempt/3"
    if "$sdkmanager" --install \
        "platforms;android-36" "ndk;29.0.13113456" "cmake;3.31.6"; then
        if sdk_ready; then
            echo "Android SDK platform, NDK and CMake verified."
            exit 0
        fi
        echo "sdkmanager returned success but files were incomplete." >&2
    fi

    if (( attempt == 3 )); then
        echo "Pinned Android SDK installation failed after three attempts." >&2
        exit 1
    fi
    # A failed interrupted unpack can poison the next sdkmanager attempt.
    # Only remove SDK setup's own temporary PackageOperation directories and
    # invalid version-specific installations. Never wipe a valid NDK or all SDK.
    temp_dir="$ANDROID_HOME/.temp"
    if [[ -d "$temp_dir" ]]; then
        find "$temp_dir" -mindepth 1 -maxdepth 1 -type d \
            -name 'PackageOperation*' -exec rm -rf -- {} +
    fi
    if [[ -d "$ANDROID_HOME/ndk/29.0.13113456" &&
          ! -s "$ANDROID_HOME/ndk/29.0.13113456/source.properties" ]]; then
        rm -rf -- "$ANDROID_HOME/ndk/29.0.13113456"
    fi
    if [[ -d "$ANDROID_HOME/cmake/3.31.6" &&
          ! -x "$ANDROID_HOME/cmake/3.31.6/bin/cmake" ]]; then
        rm -rf -- "$ANDROID_HOME/cmake/3.31.6"
    fi
    echo "Retrying Android SDK setup after a short delay..." >&2
    sleep "$(( attempt * 3 ))"
done
