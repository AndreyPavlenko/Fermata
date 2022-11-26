## Fermata Media Player
[<img alt="Get it on Google Play" height="60" src="https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png">](https://play.google.com/store/apps/details?id=me.aap.fermata)

## About
Fermata Media Player is a free, open source audio, video and TV player with a simple and intuitive interface. It is focused on playing media files organized in folders and playlists.

Supported features:

* Play media files organized in folders
* IPTV addon with support for XMLTV EPG and Catchup
* Remembers the last played track and position for each folder
* Support for favorites and playlists
* Support for CUE and M3U playlists
* Support for bookmarks
* Audio effects: Equalizer, Bass/Volume Boost and Virtualizer
* Configure audio effects for individual tracks and folders
* Configure playback speed for individual tracks and folders
* Customizable titles and subtitles
* Support for Android Auto
* Support for Android TV
* Show favorites and playlists on Android TV home screen
* Pluggable media engines: MediaPlayer, ExoPlayer and VLC
* Video player with support for subtitles and audio streams (VLC Engine only)

## Building APK
* Download and install the latest Android NDK from https://developer.android.com/ndk/downloads/
* Download and install the latest Android SDK or Android Studio from https://developer.android.com/studio/
* Set the environment variable ANDROID_SDK_ROOT pointing to the SDK directory

### Clone the repository
```
git clone --recurse-submodules https://github.com/AndreyPavlenko/Fermata.git
```

### Build ExoPlayer extensions (optional)
```
cd Fermata/depends
./build.sh <path to Android NDK directory>
```

### Build Fermata Auto and Fermata Control
```
cd Fermata
export ANDROID_SDK_ROOT=<path to android SDK>
./gradlew packageAutoReleaseUniversalApk -PABI=arm64-v8a
./gradlew packageAutoReleaseUniversalApk -PABI=armeabi-v7a
./gradlew control:assembleRelease
```

To build the apks with a dirrenent package name, use the APP_ID property:
```
./gradlew packageAutoReleaseUniversalApk -PABI=arm64-v8a -PAPP_ID=my.app.name
```

## Donation
If you like the application, please consider making a donation:

[PayPal](https://www.paypal.com/donate/?hosted_button_id=NP5Q3YDSCJ98N)

[CloudTips](https://pay.cloudtips.ru/p/a03a73da)

[Yandex Money](https://money.yandex.ru/to/410014661137336)
