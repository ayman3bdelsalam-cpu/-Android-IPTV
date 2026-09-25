# Ayman Smart IPTV — Android v1.1.0

Performance/player update for the Android app.

## Main changes
- Auto landscape + immersive fullscreen + keep-screen-awake
- FIT / FILL / ZOOM with saved preference
- Audio/subtitle selection
- Picture-in-Picture
- HLS -> TS automatic fallback for Live TV
- Faster Live TV buffering and 1s/2s/4s reconnect backoff
- Network reconnection recovery
- In-memory API caching and 250ms search debounce
- Lower-memory poster decoding
- Reduced resume-position writes
- Android Keystore encryption for Xtream credentials
- LG webOS Ayman branding carried over to Android

## Build
The included GitHub Actions workflow builds a debug APK automatically. You can also open the project in Android Studio and build normally with JDK 17 / Android SDK 34.
