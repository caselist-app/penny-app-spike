#!/bin/sh
# Build the probe APK without Gradle.
#
# An APK is a zip containing a compiled manifest, a dex file and a signature.
# Each stage below produces one of those, using only tools that came with
# build-tools 37.0.0. Nothing is downloaded and no build system negotiates
# versions with anything, which matters here: rung 2 exists to read a
# precise runtime refusal, and a build-tool version mismatch fails in a way
# that looks like the same thing.
#
# Run it from the Mac:  sh ~/Documents/penny-app-spike/probe2a/build.sh

set -e

SDK=/opt/homebrew/share/android-commandlinetools
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"

HERE=$(cd "$(dirname "$0")" && pwd)
OUT="$HERE/build"

# Resolve rather than hard-code: Android 17 ships minor platform revisions
# (37.0, 37.1, ...) and the directory name follows them.
BT=$(ls -d "$SDK"/build-tools/37* | sort -V | tail -1)
PLATFORM_DIR=$(ls -d "$SDK"/platforms/android-37* | sort -V | tail -1)
PLATFORM="$PLATFORM_DIR/android.jar"

# Rung 2d added a C compiler to this build. Nothing else in the spike needs
# one: the payload that runs inside the guest is a native .so, and microdroid
# dlopens it directly, so there is no Java route to it.
# There is NO NDK on this Mac and there does not need to be — see the
# PENNY_PAYLOAD_SO block below. These two lines resolve one if it is ever
# installed, and quietly come back empty if not.
NDK=$(ls -d "$SDK"/ndk/* 2>/dev/null | sort -V | tail -1 || true)
CLANG=$(ls "$NDK"/toolchains/llvm/prebuilt/*/bin/aarch64-linux-android34-clang 2>/dev/null | head -1 || true)

echo "java      $(java -version 2>&1 | head -1)"
echo "build     $BT"
echo "platform  $PLATFORM"
echo "ndk       ${NDK:-(none — payload must be supplied via PENNY_PAYLOAD_SO)}"
echo

rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/stubs" "$OUT/payloadstub" "$OUT/apkroot/lib/arm64-v8a"

# 1. Resources -> compiled, then manifest -> an APK skeleton.
#
#    Rung 3b is the first thing in this spike that needs a resource. An app
#    cannot be offered as the device assistant by code alone: the OS reads an
#    XML file named from the manifest to find out which session and recognition
#    services the assistant provides, and rejects the app silently if it cannot
#    resolve it. So the deliberately resource-free build gains exactly one
#    directory, res/xml, and nothing more.
#
#    aapt2 is a two-stage tool. compile turns each source resource into a
#    binary .flat file; link assembles those plus the manifest into the APK and
#    builds the resource table the runtime looks names up in.
"$BT/aapt2" compile --dir "$HERE/res" -o "$OUT/res.zip"
echo "1/8 resources compiled"

"$BT/aapt2" link \
    -I "$PLATFORM" \
    --manifest "$HERE/AndroidManifest.xml" \
    -R "$OUT/res.zip" \
    --auto-add-overlay \
    --min-sdk-version 34 \
    --target-sdk-version 37 \
    -o "$OUT/base.apk"
echo "2/8 manifest linked"

# 2. The @SystemApi stubs -> class files that exist ONLY to satisfy javac.
#    android.system.virtualmachine is absent from the public android.jar, so
#    without these the app could not name those classes at compile time (2a
#    used reflection instead). These are compiled to a SEPARATE directory and
#    are deliberately never dexed — see stage 4. On the device the real
#    classes load from BootClassLoader, which rung 2a measured directly.
javac -source 17 -target 17 -nowarn \
    -classpath "$PLATFORM" \
    -d "$OUT/stubs" \
    $(find "$HERE/stubs" -name '*.java')
echo "3/8 stubs compiled (compile-only, not shipped)"

# 3. Our own Java -> JVM class files, against android.jar plus the stubs.
javac -source 17 -target 17 -nowarn \
    -classpath "$PLATFORM:$OUT/stubs" \
    -d "$OUT/classes" \
    $(find "$HERE/src" -name '*.java')
echo "4/8 app compiled"

# 4. JVM class files -> Android dex bytecode. Note this dexes $OUT/classes
#    only. If the stubs were packaged, the APK would carry a second, fake
#    copy of a platform class, and which one won would be a coin toss worth
#    losing. The stubs are passed with --lib, exactly as android.jar is:
#    visible to the compiler, absent from the output.
"$BT/d8" --lib "$PLATFORM" --lib "$OUT/stubs" --min-api 34 --output "$OUT" \
    $(find "$OUT/classes" -name '*.class')
echo "5/8 dexed"

# 5. The guest payload -> a native arm64 .so.
#
#    Two objects, and the first one never ships. The real
#    AVmPayload_notifyPayloadReady lives in microdroid's own libvm_payload.so
#    inside the guest; the linker on this Mac has never heard of it. So a stub
#    of the same soname is built purely to be linked against, which makes the
#    linker emit DT_NEEDED for libvm_payload.so. In the guest that entry
#    resolves to the real library. It is the same compile-only trick as the
#    Java stubs in stage 2, for the same reason, and it fails the same way if
#    the stub were ever packaged: the guest would load a do-nothing version of
#    the call and onPayloadReady would simply never fire.
if [ -z "$PENNY_PAYLOAD_SO" ]; then
    "$CLANG" -shared -fPIC -o "$OUT/payloadstub/libvm_payload.so" \
        "$HERE/payload/vm_payload_stub.c"
fi

#    PENNY_PAYLOAD_SO takes a .so built somewhere else and packages it
#    unchanged. It exists for two jobs, and the build cannot tell them apart —
#    the caller has to know which one this run is:
#
#      CONTROL. Point it at Google's stock MicrodroidEmptyPayloadJniLib.so and
#      the APK is built identically but carries a payload already known to
#      work, so a failure can only be the packaging, the APK path or the
#      signature, never our C. That run passed on 15 Sept.
#
#      THE REAL BUILD, because there is no NDK here. 15 Sept: the NDK is a
#      975MB download over a phone tether, so penny_payload.c is compiled
#      instead by the Debian guest ON the Pixel, which is already arm64 and so
#      needs no cross-compiler. That is why the source uses raw syscalls and no
#      libc — Debian has glibc, microdroid has bionic, and a payload linked to
#      either would not load in the other. See payload/penny_payload.c.
#
#    The build command used in the guest, for the record:
#
#      gcc -shared -fPIC -O1 -ffreestanding -fno-stack-protector \
#          -Wl,-z,max-page-size=4096 -Wl,--hash-style=sysv \
#          -o PennyPayload.so penny_payload.c -L. -lvm_payload
#    PENNY_PAYLOAD_3C_SO is rung 3c's payload and is packaged ALONGSIDE 2d's,
#    not instead of it. Two .so files in one APK costs nothing — microdroid
#    loads only the one setPayloadBinaryName() asks for — and it means a single
#    build still reproduces 2d exactly while answering 3c. Keeping a proven
#    result runnable is the same reason rung 3's VmService is never edited.
#    PENNY_PAYLOAD_3EII_SO is rung 3e-ii's, added the same way and for the same
#    reason: it is the first payload here that ALLOCATES, so it could not reuse
#    3c's, but 3c's and 2d's both stay in the APK and stay runnable.
#    PENNY_PAYLOAD_3EIII_SO is rung 3e-iii's, a command server rather than a
#    single-shot: there is no compiler on this Mac, so a rebuild costs a manual
#    trip into the phone's Debian guest, and one build therefore has to answer
#    the whole rung.
if [ -n "$PENNY_PAYLOAD_SO" ]; then
    cp "$PENNY_PAYLOAD_SO" "$OUT/apkroot/lib/arm64-v8a/PennyPayload.so"
    echo "6/8 guest payload COPIED FROM $PENNY_PAYLOAD_SO"
    if [ -n "$PENNY_PAYLOAD_3C_SO" ]; then
        cp "$PENNY_PAYLOAD_3C_SO" "$OUT/apkroot/lib/arm64-v8a/Penny3cPayload.so"
        echo "    rung 3c payload COPIED FROM $PENNY_PAYLOAD_3C_SO"
    fi
    if [ -n "$PENNY_PAYLOAD_3EII_SO" ]; then
        cp "$PENNY_PAYLOAD_3EII_SO" "$OUT/apkroot/lib/arm64-v8a/Penny3eiiPayload.so"
        echo "    rung 3e-ii payload COPIED FROM $PENNY_PAYLOAD_3EII_SO"
    fi
    if [ -n "$PENNY_PAYLOAD_3EIII_SO" ]; then
        cp "$PENNY_PAYLOAD_3EIII_SO" "$OUT/apkroot/lib/arm64-v8a/Penny3eiiiPayload.so"
        echo "    rung 3e-iii payload COPIED FROM $PENNY_PAYLOAD_3EIII_SO"
    fi
else
    "$CLANG" -shared -fPIC -O2 -o "$OUT/apkroot/lib/arm64-v8a/PennyPayload.so" \
        "$HERE/payload/penny_payload.c" \
        -L"$OUT/payloadstub" -lvm_payload
    echo "6/8 guest payload compiled"
fi

# 6. Put the dex and the payload inside the APK, then align.
#
#    The payload is added with `zip -0` — STORED, not deflated — and the APK is
#    then page-aligned with `zipalign -p`. Neither is optional and neither is an
#    optimisation. Microdroid does not install or unpack this APK: it mounts it
#    read-only in the guest and mmaps lib/arm64-v8a/PennyPayload.so out of it in
#    place. A compressed or unaligned entry cannot be mapped, and the failure
#    appears as the guest dying rather than as anything wrong at build or
#    install time. classes.dex is added normally, compressed, because Android
#    reads that the ordinary way.
# 5b. Rung 3e-iii's blob: a large file packaged into the APK the SAME WAY the
#     payload is, so it can be mmaped read-only out of the guest's own APK
#     mount instead of being copied into a RAM disk. That is the escape hatch
#     the rung exists to test, and it is only a fair test if the packaging is
#     identical — Stored and page-aligned. It is named .so for exactly that
#     reason: `zipalign -p` page-aligns uncompressed .so entries and nothing
#     else. Nothing ever dlopens it; the guest opens it by path.
#
#     OPTIONAL, and off by default, because it makes the APK as large as the
#     blob and every install pays for it. Steps 1-3 of the rung do not need it.
#     Set PENNY_BLOB_MB=512 to include one. It is cached in build-payloads/
#     (gitignored) so a rebuild does not regenerate it — /dev/urandom is slow
#     and the bytes must be INCOMPRESSIBLE for the same zram reason the guest
#     payload fills its pages from a PRNG.
if [ -n "$PENNY_BLOB_MB" ]; then
    BLOB="$HERE/build-payloads/PennyBlob-${PENNY_BLOB_MB}.so"
    if [ ! -f "$BLOB" ]; then
        echo "    generating a ${PENNY_BLOB_MB}MB incompressible blob (once, cached)"
        mkdir -p "$HERE/build-payloads"
        dd if=/dev/urandom of="$BLOB" bs=1048576 count="$PENNY_BLOB_MB" 2>/dev/null
    fi
    cp "$BLOB" "$OUT/apkroot/lib/arm64-v8a/PennyBlob.so"
    echo "    rung 3e-iii blob ${PENNY_BLOB_MB}MB INCLUDED"
fi

(cd "$OUT" && zip -q base.apk classes.dex)
(cd "$OUT/apkroot" && zip -q -0 -X "$OUT/base.apk" lib/arm64-v8a/PennyPayload.so)
if [ -n "$PENNY_PAYLOAD_3C_SO" ]; then
    (cd "$OUT/apkroot" && zip -q -0 -X "$OUT/base.apk" lib/arm64-v8a/Penny3cPayload.so)
fi
if [ -n "$PENNY_PAYLOAD_3EII_SO" ]; then
    (cd "$OUT/apkroot" && zip -q -0 -X "$OUT/base.apk" lib/arm64-v8a/Penny3eiiPayload.so)
fi
if [ -n "$PENNY_PAYLOAD_3EIII_SO" ]; then
    (cd "$OUT/apkroot" && zip -q -0 -X "$OUT/base.apk" lib/arm64-v8a/Penny3eiiiPayload.so)
fi
if [ -n "$PENNY_BLOB_MB" ]; then
    (cd "$OUT/apkroot" && zip -q -0 -X "$OUT/base.apk" lib/arm64-v8a/PennyBlob.so)
fi
"$BT/zipalign" -p -f 4 "$OUT/base.apk" "$OUT/aligned.apk"
echo "7/8 packaged and page-aligned"

# 7. Sign. Android refuses unsigned APKs; a throwaway local key is enough for
#    a sideload. This is NOT the platform key and holds no privilege. Signing
#    is last: zipalign rewrites offsets, so aligning after signing would
#    invalidate the signature.

if [ ! -f "$HERE/debug.keystore" ]; then
    keytool -genkeypair -keystore "$HERE/debug.keystore" \
        -storepass android -keypass android -alias probe2a \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=penny-app-spike probe2a"
    echo "    generated a throwaway signing key"
fi

"$BT/apksigner" sign \
    --ks "$HERE/debug.keystore" --ks-pass pass:android --key-pass pass:android \
    --out "$OUT/probe2a.apk" "$OUT/aligned.apk"
echo "8/8 signed"

echo
echo "built: $OUT/probe2a.apk"

# Proof the stubs did not ship. If either of these is wrong, stop and fix it;
# both failures are silent at install time and only surface as a confusing
# result much later.
LEAK=$("$BT/dexdump" -e "$OUT/classes.dex" 2>/dev/null \
    | grep -c "Landroid/system/virtualmachine/" || true)
echo "stub classes leaked into the dex: $LEAK  (must be 0)"

SOLEAK=$(unzip -l "$OUT/probe2a.apk" | grep -c "libvm_payload.so" || true)
echo "stub libvm_payload.so leaked into the apk: $SOLEAK  (must be 0)"

# The payload must be Stored, not Defl:N. zipfuse in the guest cannot read a
# deflated entry, and the symptom is the VM failing rather than this build.
echo "payload entries in the apk (both must say Stored):"
unzip -lv "$OUT/probe2a.apk" | grep "Payload.so"
