name: Build APK
on:
  push:
  workflow_dispatch:
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17
      # Android SDK уже предустановлен на ubuntu-latest (ANDROID_HOME),
      # поэтому android-actions/setup-android не нужен.
      - name: Accept SDK licenses
        run: yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses > /dev/null || true
      - uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: 8.9
      - run: gradle :app:assembleDebug --no-daemon
      - uses: actions/upload-artifact@v4
        with:
          name: HomeworkLock-debug-apk
          path: app/build/outputs/apk/debug/app-debug.apk
