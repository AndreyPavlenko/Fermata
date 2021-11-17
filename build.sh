#!/bin/sh
set -e

APP_ID_SFX='.dear.google.please.dont.block'
DIR="$(cd "$(dirname "$0")"; pwd -P)"
DEST_DIR="$DIR/dist"
mkdir -p "$DEST_DIR"
export NO_GS=true

bundletool_universal() {
    local AAB="$1"
    local ADD_SFX="$2"
    local CUT_SFX="$3"
    local AAB_FILE="$(basename "$AAB")"
    local AAB_DIR="$(dirname "$AAB")"
    local BASENAME="${AAB_FILE%${CUT_SFX}.*}"
    local APKS="$AAB_DIR/$BASENAME.apks"

    bundletool build-apks --bundle="$AAB" --output="$APKS" --mode=universal --overwrite
    unzip -o "$APKS" universal.apk -d "$AAB_DIR"
    mv "$AAB_DIR/universal.apk" "$AAB_DIR/$BASENAME${ADD_SFX}.apk"
    rm "$APKS"
}

build_apk() {
    local sfx='arm64'
    local abi='arm64-v8a'

    if [ "$1" = 'arm' ]; then
        sfx='arm'
        abi='armeabi-v7a'
    fi

    ./gradlew clean fermata:bundleAutoRelease -PABI=$abi -PAPP_ID_SFX=$APP_ID_SFX
    bundletool_universal ./fermata/build/outputs/bundle/autoRelease/fermata-*-release.aab -universal-$sfx -release
    mv ./fermata/build/outputs/bundle/autoRelease/fermata-*.apk "$DEST_DIR"
}

install_apk() {
    local apk="$(ls $DEST_DIR/fermata-*-arm64.apk | sort -n | head)"
    adb push "$apk" /data/local/tmp/fermata.apk
    adb shell pm install -i "com.android.vending" -r /data/local/tmp/fermata.apk
    adb shell rm /data/local/tmp/fermata.apk
}

if [ "$1" = '-i' ] || [ "$1" = '-bi' ]; then
    [ "$1" = '-bi' ] && build_apk 'arm64'
    install_apk
    return 0
fi

./gradlew -p control assembleRelease -PAPP_ID_SFX=$APP_ID_SFX
mv ./control/build/outputs/apk/release/fermata-auto-control-*-release.apk "$DEST_DIR"

build_apk 'arm'
build_apk 'arm64'
