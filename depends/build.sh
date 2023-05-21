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

: ${ABI:='armeabi-v7a arm64-v8a x86 x86_64'}

if [ -z "$HOST_PLATFORM" ]; then
    case "$OSTYPE" in
      linux*)
        HOST_PLATFORM='linux-x86_64'
        ;;
      darwin*)
        HOST_PLATFORM='darwin-x86_x64'
        ;;
      win*|cygwin|msys)
        HOST_PLATFORM='windows-x86_x64'
        ;;
      *)
        echo 'Failed to detect host platform!'
        echo 'Please, set the HOST_PLATFORM environment variable to one of the following: linux-x86_64, darwin-x86_x64, windows-x86_x64'
        ;;
    esac

    echo "Host platform: $HOST_PLATFORM"
fi

# Clone or update FFmpeg
FFMPEG_VER="release/4.4"
if [ -d "$FFMPEG_DIR" ]; then
    cd "$FFMPEG_DIR"
    git clean -xfd && git reset --hard && git checkout $FFMPEG_VER && git reset --hard && git pull
else
    git clone 'git://source.ffmpeg.org/ffmpeg' "$FFMPEG_DIR"
    cd "$FFMPEG_DIR"
    git checkout $FFMPEG_VER
fi

# Clone or update ExoPlayer
if [ -d "$EXO_DIR" ]; then
    cd "$EXO_DIR"
    git clean -xfd && git reset --hard && git pull origin release-v2
else
    git clone 'https://github.com/google/ExoPlayer.git' "$EXO_DIR"
    cd "$EXO_DIR"
fi
git checkout release-v2

echo > "$EXO_DIR/publish.gradle"
sed -i "s/minSdkVersion .*/minSdkVersion = $SDK_MIN_VERSION/" "$EXO_DIR/constants.gradle"
sed -i "s/targetSdkVersion .*/targetSdkVersion = $SDK_TARGET_VERSION/" "$EXO_DIR/constants.gradle"
sed -i "s/appTargetSdkVersion .*/appTargetSdkVersion = $SDK_TARGET_VERSION/" "$EXO_DIR/constants.gradle"
sed -i "s/compileSdkVersion .*/compileSdkVersion = $SDK_COMPILE_VERSION/" "$EXO_DIR/constants.gradle"
sed -i "s/androidxMediaVersion .*/androidxMediaVersion = $ANDROIDX_MEDIA_VERSION/" "$EXO_DIR/constants.gradle"
sed -i "s/androidxAppCompatVersion .*/androidxAppCompatVersion = $ANDROIDX_APPCOMPAT_VERSION/" "$EXO_DIR/constants.gradle"
sed -i "s/minSdkVersion .*/minSdkVersion $SDK_MIN_VERSION/" "$EXO_DIR/extensions/leanback/build.gradle"

# Move package name to the android.namespace property
for i in $(find "$EXO_DIR/" -name AndroidManifest.xml | grep /src/main/); do
    pkg=$(grep -o ' package="[^ ]*"' "$i")
    pkg="$(eval "$pkg; echo \$package")"
    sed -i 's/ package="[^ ]*"//' "$i"
    echo "android.namespace = '$pkg'" >> "$(dirname $i)/../../build.gradle"
done

# Build ExoPlayer FFmpeg extension
FFMPEG_DECODERS=(vorbis alac mp3 aac ac3 eac3 mlp truehd)

cd "$EXO_EXT_DIR/ffmpeg/src/main/jni"
[ -e ffmpeg ] || ln -s "$FFMPEG_DIR" ffmpeg
sed -ie 's|${TOOLCHAIN_PREFIX}/.*-linux-android.*-nm|${TOOLCHAIN_PREFIX}/llvm-nm|g' build_ffmpeg.sh
sed -ie 's|${TOOLCHAIN_PREFIX}/.*-linux-android.*-ar|${TOOLCHAIN_PREFIX}/llvm-ar|g' build_ffmpeg.sh
sed -ie 's|${TOOLCHAIN_PREFIX}/.*-linux-android.*-ranlib|${TOOLCHAIN_PREFIX}/llvm-ranlib|g' build_ffmpeg.sh
sed -ie 's|${TOOLCHAIN_PREFIX}/.*-linux-android.*-strip|${TOOLCHAIN_PREFIX}/llvm-strip|g' build_ffmpeg.sh
sed -ie 's|android16|android23|g' build_ffmpeg.sh
sed -ie 's|androideabi16|androideabi23|g' build_ffmpeg.sh

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
