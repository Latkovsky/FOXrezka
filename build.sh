#!/usr/bin/env bash
set -euo pipefail

[ -f "$HOME/.cargo/env" ] && . "$HOME/.cargo/env"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export PATH="$ANDROID_HOME/platform-tools:$PATH"

ROOT="$(cd "$(dirname "$0")" && pwd)"
GRADLE_BIN="$ROOT/android/gradlew"
if [ ! -x "$GRADLE_BIN" ]; then
    GRADLE_BIN="$HOME/gradle/gradle-8.9/bin/gradle"
fi

echo "==> Сборка Rust-ядра (host, для генерации биндингов)"
cd "$ROOT/core"
cargo build --release

echo "==> Генерация Kotlin-биндингов"
cargo run --release --bin uniffi-bindgen -- generate \
    --library target/release/librezka_core.so \
    --language kotlin \
    --out-dir "$ROOT/android/app/src/main/java"

echo "==> Кросс-компиляция под Android (arm64-v8a, x86_64)"
cargo ndk -t arm64-v8a -t x86_64 \
    -o "$ROOT/android/app/src/main/jniLibs" \
    build --release

echo "==> Сборка APK"
cd "$ROOT/android"
"$GRADLE_BIN" assembleDebug

APK="$ROOT/android/app/build/outputs/apk/debug/app-debug.apk"
echo "==> APK: $APK"

if adb devices 2>/dev/null | grep -qE "device$"; then
    echo "==> Установка на подключённое устройство"
    adb install -r "$APK"
else
    echo "==> Устройство не подключено. Подключите телефон по USB с включённой отладкой."
fi
