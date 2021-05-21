#!/bin/sh
set -e

DEST_DIR="$1"
[ -z "$DEST_DIR" ] && DEST_DIR="dist"

mkdir -p "$DEST_DIR"

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

# ./gradlew clean fermata:bundleRelease -PABI='arm64-v8a,armeabi-v7a'
# mv ./fermata/build/outputs/bundle/autoRelease/fermata-*.aab "$DEST_DIR"
# mv ./fermata/build/outputs/bundle/mobileRelease/fermata-*.aab "$DEST_DIR"

./gradlew -p control assembleRelease
mv ./control/build/outputs/apk/release/fermata-auto-control-*-release.apk "$DEST_DIR"

./gradlew clean fermata:bundleRelease -PABI=arm64-v8a
bundletool_universal ./fermata/build/outputs/bundle/autoRelease/fermata-*-release.aab -universal-arm64 -release
mv ./fermata/build/outputs/bundle/autoRelease/fermata-*.apk "$DEST_DIR"

./gradlew clean fermata:bundleRelease -PABI=armeabi-v7a
bundletool_universal ./fermata/build/outputs/bundle/autoRelease/fermata-*-release.aab -universal-arm -release
mv ./fermata/build/outputs/bundle/autoRelease/fermata-*.apk "$DEST_DIR"
