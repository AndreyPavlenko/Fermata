#!/bin/bash
set -e

: ${NDK_DIR:="$1"}

if [ ! -d "$NDK_DIR" ];then
    echo "NDK_DIR is not specified"
    exit 1
fi

DEPENDS_DIR="$(cd "$(dirname "$0")" && pwd)"
FFMPEG_DIR="$DEPENDS_DIR/ffmpeg"
EXO_DIR="$DEPENDS_DIR/ExoPlayer"
EXO_EXT_DIR="$EXO_DIR/extensions"

SDK_MIN_VERSION="$(grep 'SDK_MIN_VERSION = ' $DEPENDS_DIR/../build.gradle | cut -d= -f2)"
SDK_TARGET_VERSION="$(grep 'SDK_TARGET_VERSION = ' $DEPENDS_DIR/../build.gradle | cut -d= -f2)"
SDK_COMPILE_VERSION="$(grep 'SDK_COMPILE_VERSION = ' $DEPENDS_DIR/../build.gradle | cut -d= -f2)"
ANDROIDX_MEDIA_VERSION="$(grep 'ANDROIDX_MEDIA_VERSION = ' $DEPENDS_DIR/../build.gradle | cut -d= -f2)"
ANDROIDX_APPCOMPAT_VERSION="$(grep 'ANDROIDX_APPCOMPAT_VERSION = ' $DEPENDS_DIR/../build.gradle | cut -d= -f2)"

: ${HOST_PLATFORM:='linux-x86_64'}
: ${ABI:='armeabi-v7a arm64-v8a x86 x86_64'}

# Clone or update FFmpeg
if [ -d "$FFMPEG_DIR" ]; then
    cd "$FFMPEG_DIR"
    git clean -xfd && git checkout master && git reset --hard && git pull
else
    git clone 'git://source.ffmpeg.org/ffmpeg' "$FFMPEG_DIR"
fi
# Temporary workaround for 'Unknown option "--disable-avresample"'
git checkout n4.4

# Clone or update ExoPlayer
if [ -d "$EXO_DIR" ]; then
    cd "$EXO_DIR"
    git clean -xfd && git reset --hard && git pull
else
    git clone 'https://github.com/google/ExoPlayer.git' "$EXO_DIR"
fi

git checkout release-v2

sed -i "s/minSdkVersion .*/minSdkVersion = $SDK_MIN_VERSION/" "$EXO_DIR/constants.gradle"
sed -i "s/targetSdkVersion .*/targetSdkVersion = $SDK_TARGET_VERSION/" "$EXO_DIR/constants.gradle"
sed -i "s/appTargetSdkVersion .*/appTargetSdkVersion = $SDK_TARGET_VERSION/" "$EXO_DIR/constants.gradle"
sed -i "s/compileSdkVersion .*/compileSdkVersion = $SDK_COMPILE_VERSION/" "$EXO_DIR/constants.gradle"
sed -i "s/androidxMediaVersion .*/androidxMediaVersion = $ANDROIDX_MEDIA_VERSION/" "$EXO_DIR/constants.gradle"
sed -i "s/androidxAppCompatVersion .*/androidxAppCompatVersion = $ANDROIDX_APPCOMPAT_VERSION/" "$EXO_DIR/constants.gradle"
sed -i "s/minSdkVersion .*/minSdkVersion $SDK_MIN_VERSION/" "$EXO_DIR/extensions/gvr/build.gradle"
sed -i "s/minSdkVersion .*/minSdkVersion $SDK_MIN_VERSION/" "$EXO_DIR/extensions/leanback/build.gradle"

# Build ExoPlayer FFmpeg extension
FFMPEG_DECODERS=(vorbis alac mp3 aac ac3 eac3 mlp truehd)

cd "$EXO_EXT_DIR/ffmpeg/src/main/jni"
ln -s "$FFMPEG_DIR" ffmpeg

./build_ffmpeg.sh "$(pwd)/.." "$NDK_DIR" "$HOST_PLATFORM" "${FFMPEG_DECODERS[@]}"

# Build ExoPlayer Flac extension
FLAC_VERSION='1.3.3'

cd "$EXO_EXT_DIR/flac/src/main/jni"
rm -rf flac
curl "https://ftp.osuosl.org/pub/xiph/releases/flac/flac-${FLAC_VERSION}.tar.xz" | tar xJ
mv flac-${FLAC_VERSION} flac
"$NDK_DIR/ndk-build" APP_ABI="$ABI" -j4


# Build ExoPlayer Opus extension
cd "$EXO_EXT_DIR/opus/src/main/jni"

if [ -d libopus ]; then
    cd libopus
    git clean -xfd && git reset --hard && git pull
    cd -
else
#    git clone 'https://git.xiph.org/opus.git' libopus
    git clone 'https://github.com/xiph/opus.git' libopus
fi

./convert_android_asm.sh
"$NDK_DIR/ndk-build" APP_ABI="$ABI" -j4
