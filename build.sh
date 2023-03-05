#!/bin/sh
set -e

APP_ID_SFX='.dear.google.why'
DIR="$(cd "$(dirname "$0")"; pwd -P)"
DEST_DIR="$DIR/dist"
mkdir -p "$DEST_DIR"
export NO_GS=true

if [ -z "$ANDROID_SDK_ROOT" ]; then
    if [ -f "$DIR/local.properties" ]; then
        ANDROID_SDK_ROOT="$(grep sdk.dir= local.properties | cut -d = -f2)"
    fi
fi

if [ -z "$ANDROID_SDK_ROOT" ]; then
    echo 'ANDROID_SDK_ROOT environment variable is not set'
    exit 1
else
    echo "ANDROID_SDK_ROOT=$ANDROID_SDK_ROOT"
fi

CMAKE_PATH="$(find $ANDROID_SDK_ROOT/cmake/* -maxdepth 1 -type d -name bin | sort -V | tail -1)"
echo "CMAKE_PATH=$CMAKE_PATH"
export PATH=$CMAKE_PATH:$PATH

build_apk() {
    local sfx='arm64'
    local abi='arm64-v8a'

    if [ "$1" = 'arm' ]; then
        sfx='arm'
        abi='armeabi-v7a'
    fi

    ./gradlew clean fermata:packageAutoReleaseUniversalApk -PABI=$abi -PAPP_ID_SFX=$APP_ID_SFX
    local path=$(ls ./fermata/build/outputs/apk_from_bundle/autoRelease/fermata-*.apk)
    local name=${path##*/}
    mv $path "$DEST_DIR/${name%auto-release-universal.apk}auto-universal-$sfx.apk"
}

./gradlew control:assembleRelease -PAPP_ID_SFX=$APP_ID_SFX
mv ./control/build/outputs/apk/release/fermata-auto-control-*-release.apk "$DEST_DIR"

build_apk 'arm'
build_apk 'arm64'
