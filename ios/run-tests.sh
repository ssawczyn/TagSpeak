#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

export CLANG_MODULE_CACHE_PATH="${CLANG_MODULE_CACHE_PATH:-/tmp/tagspeak-clang-module-cache}"

if xcode-select -p 2>/dev/null | grep -q "Xcode.*\.app"; then
  echo "Xcode detected — running 'swift test'..."
  swift test
else
  echo "Command Line Tools detected — compiling and running TagSpeakCore unit tests via swiftc..."
  swiftc Sources/TagSpeakCore/*.swift Tests/TagSpeakCoreTests/*.swift -o /tmp/tagspeak-ios-tests
  /tmp/tagspeak-ios-tests
fi
