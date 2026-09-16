#!/bin/sh
# Build pennyload for the phone. No cmake, no ninja, no download, and the
# llama.cpp tree is NOT modified -- it stays at commit 38a5b42d9.
#
# The compile and link lines below are copied from what ninja recorded for
# llama-simple in llama.cpp/build-android (CMakeFiles/rules.ninja:495 and
# build.ninja:3609-3633), so this binary is built with the same compiler, the
# same flags and the same object files as the llama-bench already on the phone.
set -e

NDK=/opt/homebrew/share/android-commandlinetools/ndk/30.0.16248370
TC=$NDK/toolchains/llvm/prebuilt/darwin-x86_64
LC=$HOME/Documents/llama.cpp
B=$LC/build-android
OUT=$(dirname "$0")/build

mkdir -p "$OUT"

"$TC/bin/clang++" \
  --target=aarch64-none-linux-android28 \
  --sysroot="$TC/sysroot" \
  -DGGML_USE_CPU \
  -I"$LC/include" -I"$LC/ggml/include" \
  -g -DANDROID -fdata-sections -ffunction-sections -funwind-tables \
  -fstack-protector-strong -no-canonical-prefixes -D_FORTIFY_SOURCE=2 \
  -Wformat -Werror=format-security -O3 -DNDEBUG -fPIE \
  -Wall -Wextra -Wpedantic -std=c++17 \
  -static-libstdc++ \
  -Wl,--build-id=sha1 -Wl,--no-rosegment -Wl,--no-undefined-version \
  -Wl,--fatal-warnings -Wl,--no-undefined -Qunused-arguments -Wl,--gc-sections \
  -o "$OUT/pennyload" \
  "$(dirname "$0")/pennyload.cpp" \
  "$B/src/libllama.a" -pthread \
  "$B/ggml/src/libggml.a" "$B/ggml/src/libggml-cpu.a" "$B/ggml/src/libggml-base.a" \
  -pthread -lm -ldl -latomic -lm

"$TC/bin/llvm-strip" -o "$OUT/pennyload-stripped" "$OUT/pennyload"
echo "built: $OUT/pennyload"
