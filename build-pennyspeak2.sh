#!/bin/sh
# Build pennyspeak2 for the phone (brief Y, 22 Sept). A COPY of
# build-pennyspeak.sh (960a9d75...): same checks, same flags, same link line,
# same library order. Only the source file, the object name, the binary names
# and the output directory differ -- `diff -u build-pennyspeak.sh
# build-pennyspeak2.sh` shows exactly that. build/pennyspeak/ is not touched.
#
# build-pennyspeak.sh's header, unchanged:
# Build pennyspeak for the phone. No cmake, no configure step, no network, and
# NOTHING is written inside ~/Documents/sherpa-onnx or ~/Documents/sherpa-onnx-deps:
# both are read only. Objects and the binary go to this repo's build/pennyspeak/.
#
# Two translation units:
#   pennyspeak.cpp                                  (this repo)
#   ~/Documents/sherpa-onnx/sherpa-onnx/c-api/c-api.cc   (the clone at a5b4a94, UNMODIFIED)
# The C API library was never built here: build-android-arm64-v8a/CMakeCache.txt:685
# reads SHERPA_ONNX_ENABLE_C_API:BOOL=OFF. c-api/CMakeLists.txt:2-3 makes it one
# source file linked against sherpa-onnx-core, which is what is done below.
#
# WHERE EVERY FLAG COMES FROM -- copied, not written from memory:
#   compile rule (compiler, --target, --sysroot, then DEFINES INCLUDES FLAGS):
#     ~/Documents/sherpa-onnx/build-android-arm64-v8a/sherpa-onnx/csrc/CMakeFiles/sherpa-onnx-core.dir/build.make:2988
#     ~/Documents/sherpa-onnx/build-android-arm64-v8a/sherpa-onnx/csrc/CMakeFiles/sherpa-onnx-offline-tts.dir/build.make:76
#     (-MD -MT -MF, which only write dependency files, are left out)
#   c-api.cc     <- ~/Documents/sherpa-onnx/build-android-arm64-v8a/sherpa-onnx/csrc/CMakeFiles/sherpa-onnx-core.dir/flags.make
#                   (library code, compiled as the core's objects were: -fPIC)
#   pennyspeak.cpp <- ~/Documents/sherpa-onnx/build-android-arm64-v8a/sherpa-onnx/csrc/CMakeFiles/sherpa-onnx-offline-tts.dir/flags.make
#                   (the executable's own object, compiled as the CLI's was: -fPIE)
#   The two flags.make files are identical except -fPIC / -fPIE.
#   link line    <- ~/Documents/sherpa-onnx/build-android-arm64-v8a/sherpa-onnx/csrc/CMakeFiles/sherpa-onnx-offline-tts.dir/link.txt
#                   same flags, same libraries in the same order, same rpaths;
#                   only the object list and -o differ, and ../../lib is spelled out.
#   strip        llvm-strip with no options, as build-pennyload.sh.
set -eux

NDK=/opt/homebrew/share/android-commandlinetools/ndk/30.0.16248370
TC=$NDK/toolchains/llvm/prebuilt/darwin-x86_64
SO=$HOME/Documents/sherpa-onnx
BD=$SO/build-android-arm64-v8a
ORT=$HOME/Documents/sherpa-onnx-deps/onnxruntime-android-1.28.2
ORTSO=$ORT/jni/arm64-v8a//libonnxruntime.so
HERE=$(cd "$(dirname "$0")" && pwd)
OUT=$HERE/build/pennyspeak2

# ---------------- INPUTS MUST BE THE ONES STEP A READ ----------------
check() {  # check <what> <got> <want>
    if [ "$2" = "$3" ]; then
        echo "CHECK OK   $1 = $2"
    else
        echo "CHECK FAIL $1 = $2, want $3"
        exit 1
    fi
}
check "clone HEAD"               "$(git -C "$SO" rev-parse HEAD)" a5b4a944c5186a68bcdc0ac3011e4c541781ac84
check "sha256 c-api.h"           "$(shasum -a 256 "$SO/sherpa-onnx/c-api/c-api.h" | cut -d' ' -f1)"  8475682608354bb3eb958453de5a4d1d6e3ef08ea875d73435c2003d41977328
check "sha256 c-api.cc"          "$(shasum -a 256 "$SO/sherpa-onnx/c-api/c-api.cc" | cut -d' ' -f1)" 6ec0fb568a43b72cbaaa410d558b55999ff7a4aa490e4225989de2c8d3ebf00c
check "sha256 libonnxruntime.so" "$(shasum -a 256 "$ORTSO" | cut -d' ' -f1)"                         33847ad43bffe204699fd4a27f7f3603452a8cdaf2f9a44983a0bc31ffcf2da1

mkdir -p "$OUT"

# ---------------- flags.make, verbatim ----------------
CXX_DEFINES="-DLIBESPEAK_NG_EXPORT=1 -DSHERPA_ONNX_ENABLE_DIRECTML=0 -DSHERPA_ONNX_ENABLE_SPEAKER_DIARIZATION=1 -DSHERPA_ONNX_ENABLE_TTS=1 -Dkiss_fft_scalar=float"

# -I/include is in both flags.make files as recorded; kept verbatim.
CXX_INCLUDES="-I$BD/_deps/kaldi_decoder-src -I$BD/_deps/json-src/include -I$BD/_deps/hclust_cpp-src -I$SO -I$BD/_deps/kaldi_native_fbank-src -I$BD/_deps/kissfft-src -I$BD/_deps/kaldifst-src -I$BD/_deps/openfst-src/src/include -I$BD/_deps/eigen-src -I$BD/_deps/simple-sentencepiece-src -I$BD/_deps/piper_phonemize-src/src -I$BD/_deps/espeak_ng-src/include -I/include -I$BD/_deps/piper_phonemize-src/src/include -I$BD/_deps/espeak_ng-src/src/include -I$BD/_deps/espeak_ng-src/src/ucd-tools/src/include -isystem $ORT/headers"

CXX_FLAGS_CORE="-g -DANDROID -fdata-sections -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -D_FORTIFY_SOURCE=2 -Wformat -Werror=format-security   -O3 -DNDEBUG  -fPIC -fvisibility=hidden -fvisibility-inlines-hidden"
CXX_FLAGS_EXE="-g -DANDROID -fdata-sections -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -D_FORTIFY_SOURCE=2 -Wformat -Werror=format-security   -O3 -DNDEBUG  -fPIE -fvisibility=hidden -fvisibility-inlines-hidden"

# ---------------- COMPILE ----------------
"$TC/bin/clang++" --target=aarch64-none-linux-android24 --sysroot="$TC/sysroot" \
    $CXX_DEFINES $CXX_INCLUDES $CXX_FLAGS_CORE \
    -o "$OUT/c-api.cc.o" -c "$SO/sherpa-onnx/c-api/c-api.cc"

"$TC/bin/clang++" --target=aarch64-none-linux-android24 --sysroot="$TC/sysroot" \
    $CXX_DEFINES $CXX_INCLUDES $CXX_FLAGS_EXE \
    -o "$OUT/pennyspeak2.cpp.o" -c "$HERE/pennyspeak2.cpp"

# ---------------- LINK (link.txt, library order unchanged) ----------------
"$TC/bin/clang++" --target=aarch64-none-linux-android24 --sysroot="$TC/sysroot" \
    -g -DANDROID -fdata-sections -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -D_FORTIFY_SOURCE=2 -Wformat -Werror=format-security   -O3 -DNDEBUG  -static-libstdc++ -Wl,--build-id=sha1 -Wl,--no-rosegment -Wl,--no-undefined-version -Wl,--fatal-warnings -Wl,--no-undefined -Qunused-arguments   -Wl,--gc-sections \
    "$OUT/pennyspeak2.cpp.o" "$OUT/c-api.cc.o" -o "$OUT/pennyspeak2" \
    "$BD/lib/libsherpa-onnx-core.a" '-Wl,-rpath,$ORIGIN/../lib' '-Wl,-rpath,$ORIGIN/../../../sherpa_onnx/lib' -landroid -llog \
    "$BD/lib/libkaldi-native-fbank-core.a" "$BD/lib/libkissfft-float.a" "$BD/lib/libkaldi-decoder-core.a" \
    "$BD/lib/libsherpa-onnx-kaldifst-core.a" "$BD/lib/libssentencepiece_core.a" "$ORTSO" -lm \
    "$BD/lib/libsherpa-onnx-fstfar.a" "$BD/lib/libsherpa-onnx-fst.a" -ldl \
    "$BD/lib/libpiper_phonemize.a" "$BD/lib/libespeak-ng.a" -lm "$BD/lib/libucd.a" -latomic -lm

"$TC/bin/llvm-strip" -o "$OUT/pennyspeak2-stripped" "$OUT/pennyspeak2"
echo "built: $OUT/pennyspeak2 and $OUT/pennyspeak2-stripped"
