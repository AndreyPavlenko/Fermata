#!/bin/sh
set -e

APP_ID_SFX='.dear.google.why'
DIR="$(cd "$(dirname "$0")"; pwd -P)"
DEST_DIR="$DIR/dist"
mkdir -p "$DEST_DIR"
export NO_GS=true

build_apk() {
    local sfx='arm64'
    local abi='arm64-v8a'

    if [ "$1" = 'arm' ]; then
        sfx='arm'
        abi='armeabi-v7a'
    fi

    ./gradlew clean fermata:packageAutoReleaseUniversalApk -PABI=$abi -PAPP_ID_SFX=$APP_ID_SFX
    local path=$(ls ./fermata/build/outputs/universal_apk/autoRelease/fermata-*.apk)
    local name=${path##*/}
    mv $path "$DEST_DIR/${name%auto-release-universal.apk}auto-universal-$sfx.apk"
}

./gradlew control:assembleRelease -PAPP_ID_SFX=$APP_ID_SFX
mv ./control/build/outputs/apk/release/fermata-auto-control-*-release.apk "$DEST_DIR"

build_apk 'arm'
build_apk 'arm64'
