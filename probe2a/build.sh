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

echo "java      $(java -version 2>&1 | head -1)"
echo "build     $BT"
echo "platform  $PLATFORM"
echo

rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/stubs"

# 1. Manifest -> a compiled, resource-free APK skeleton.
"$BT/aapt2" link \
    -I "$PLATFORM" \
    --manifest "$HERE/AndroidManifest.xml" \
    --min-sdk-version 34 \
    --target-sdk-version 37 \
    -o "$OUT/base.apk"
echo "1/5 manifest linked"

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
echo "2/5 stubs compiled (compile-only, not shipped)"

# 3. Our own Java -> JVM class files, against android.jar plus the stubs.
javac -source 17 -target 17 -nowarn \
    -classpath "$PLATFORM:$OUT/stubs" \
    -d "$OUT/classes" \
    $(find "$HERE/src" -name '*.java')
echo "3/5 app compiled"

# 4. JVM class files -> Android dex bytecode. Note this dexes $OUT/classes
#    only. If the stubs were packaged, the APK would carry a second, fake
#    copy of a platform class, and which one won would be a coin toss worth
#    losing. The stubs are passed with --lib, exactly as android.jar is:
#    visible to the compiler, absent from the output.
"$BT/d8" --lib "$PLATFORM" --lib "$OUT/stubs" --min-api 34 --output "$OUT" \
    $(find "$OUT/classes" -name '*.class')
echo "4/5 dexed"

# 5. Put the dex inside the APK and sign it. Android refuses unsigned APKs;
#    a throwaway local key is enough for a sideload. This is NOT the
#    platform key and holds no privilege.
(cd "$OUT" && zip -q base.apk classes.dex)

if [ ! -f "$HERE/debug.keystore" ]; then
    keytool -genkeypair -keystore "$HERE/debug.keystore" \
        -storepass android -keypass android -alias probe2a \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=penny-app-spike probe2a"
    echo "    generated a throwaway signing key"
fi

"$BT/apksigner" sign \
    --ks "$HERE/debug.keystore" --ks-pass pass:android --key-pass pass:android \
    --out "$OUT/probe2a.apk" "$OUT/base.apk"
echo "5/5 signed"

echo
echo "built: $OUT/probe2a.apk"

# Proof the stubs did not ship. If this prints anything, stop and fix it.
LEAK=$("$BT/dexdump" -e "$OUT/classes.dex" 2>/dev/null \
    | grep -c "Landroid/system/virtualmachine/" || true)
echo "stub classes leaked into the dex: $LEAK  (must be 0)"
