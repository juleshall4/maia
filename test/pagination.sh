#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
android_sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
test_dir="$(mktemp -d)"
trap 'rm -rf "$test_dir"' EXIT

kotlinc \
  android/kotlin/com/vandam/ink/Epub.kt \
  android/kotlin/com/vandam/ink/ReaderPagination.kt \
  test/ReaderPaginationTest.kt \
  -cp "$android_sdk/platforms/android-36/android.jar" \
  -include-runtime -d "$test_dir/pagination.jar"
java -jar "$test_dir/pagination.jar"
