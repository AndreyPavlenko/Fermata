#!/bin/sh
set -e

DIR="$(cd "$(dirname "$0")"; pwd -P)"
DEST_DIR="$DIR/dist"
mkdir -p "$DEST_DIR"
export NO_GS=true
TASK='apk'

while [ "$1" != "" ]; do
    case "$1" in
        -c)
            CLEAN='clean'
            ;;
        -a)
            ARM=true
            ;;
        -b)
            TASK='aab'
            ;;
        *)
            echo "Unknown argument: $1"
            exit 1
            ;;
    esac
    shift
done

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
cd "$DIR"

build() {
  local ext="$TASK"
  local app_flavor=${APP_ID_SFX:-$(grep -oP "${TASK}Flavor=\K.+" "$DIR/local.properties"  2>/dev/null || true)}
  local app_sfx=${APP_ID_SFX:-$(grep -oP "${TASK}IdSfx=\K.+" "$DIR/local.properties"  2>/dev/null || true)}
  [ -z "$app_sfx" ] || local app_sfx="-PAPP_ID_SFX=$app_sfx"
  if [ $TASK = 'apk' ]; then
    local task="package${app_flavor}AutoReleaseUniversalApk"
    local abi="-PABI=$1"
    [ "$1" = 'arm64-v8a' ] && local sfx='-arm64' || local sfx='-arm'
  else
    local task="bundle${app_flavor}AutoRelease"
  fi

  ./gradlew $CLEAN fermata:$task $abi $app_sfx
  for path in $(ls fermata/build/outputs/*/*/fermata*.$ext); do
    local version=${path##*fermata-}
    version=${version%%-*}
    local dst="$DEST_DIR/fermata-auto-${version}${sfx}.$ext"
    mv "$path" "$dst"
    echo "Built $dst"
  done
}

[ $ARM ] && [ "$TASK" = 'apk' ] && build 'armeabi-v7a' || true
build 'arm64-v8a'
