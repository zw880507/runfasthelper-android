# Cloud APK build

This project includes `.github/workflows/build-apk.yml`.

The workflow installs Android SDK 36 + Build Tools 36.0.0, Gradle 8.9, validates sources, builds debug and direct-install release APKs, and uploads them as a GitHub Actions artifact named `paodekuai-live-v0.9.0-apk`.
