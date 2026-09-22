- **Build.** llama.cpp at commit `38a5b42d9a3e82e0a586bcd1caed121f36c87a73`,
  configured from nothing; the exact working line is **notes.md 5030-5057**
  (entry at 5023):

      NDK=/opt/homebrew/share/android-commandlinetools/ndk/30.0.16248370
      SDKCM=/opt/homebrew/share/android-commandlinetools/cmake/3.22.1/bin

      "$SDKCM/cmake" \
        -DCMAKE_TOOLCHAIN_FILE=$NDK/build/cmake/android.toolchain.cmake \
        -DANDROID_ABI=arm64-v8a \
        -DANDROID_PLATFORM=android-28 \
        -DCMAKE_BUILD_TYPE=Release \
        -DBUILD_SHARED_LIBS=OFF \
        -DGGML_NATIVE=OFF \
        -DGGML_CPU_ARM_ARCH=armv8.2-a+dotprod+fp16 \
        -DGGML_OPENMP=OFF \
        -DGGML_LLAMAFILE=OFF \
        -DLLAMA_OPENSSL=OFF \
        -DLLAMA_BUILD_EXAMPLES=OFF \
        -DLLAMA_BUILD_SERVER=OFF \
        -DLLAMA_BUILD_TESTS=OFF \
        -G Ninja \
        -DCMAKE_MAKE_PROGRAM="$SDKCM/ninja" \
        -B build-android

      "$SDKCM/cmake" --build build-android --target llama-bench -j 8
