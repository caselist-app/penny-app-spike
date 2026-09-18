# notes-tts.md — branch tts-kokoro

Session notes for the Kokoro TTS brief (penny-kokoro-int8 on the 6a). Append
only. At merge these entries are appended to notes.md in order and this file is
removed. notes.md is never edited from this branch.

Every entry belongs to branch **tts-kokoro** unless it says otherwise.

## 2026-09-18 — TTS RUNG 1: PREDICTIONS, written before the clone, the build or any run

Branch tts-kokoro, worktree ~/Documents/penny-app-spike-tts, created from main
at 86b716a. Nothing has been built and nothing has run on the phone.

The artefact under test, as it sits on the Mac (computed on the Mac, not
verified against any published hash, because none exists for this repackaging):

    ~/kokoro-models/penny-kokoro-int8/model.int8.onnx
      92,363,779 bytes
      sha256 a089794d1293b91e82f3f2b8bed5417d04ac64447d6ada5f21045cde0799bf99
    folder: 145M by du (disk blocks, 120+ espeak-ng-data files), brief says ~130 MB
    voices.bin 28,200,960 · tokens.txt 687 · lexicon-gb-en.txt 6,366,635
    speaker id 22 (bf_isabella), lang "en", 24 kHz

The 18 lines are the LINES list of ~/kokoro-models/abtest_sherpa.py, which
diffs identical to abtest_penny.py's. The Mac reference clips for step 4d are
~/kokoro-models/abtest-penny/NN-int8.wav (level-matched, which changes
amplitude and not sample count).

Predictions, copied from the brief unchanged. Each is judged in the rung 1
write-up, and changed before then only with a stated reason:

- **P-T1. Derived load** (wall − the binary's Elapsed: process start + model
  load + WAV write) **0.8–2.0 s** from flash.
- **P-T2. Generate RTF on the X1 pair at 2 threads, pinned: 0.3–0.8.** Short
  lines worse than long ones (fixed per-call overhead).
- **P-T3. Peak RSS of the process: 250–450 MB** (VmHWM).
- **P-T4. No clock descent visible within one 18-line pass** (policy6 and
  policy4 min equal to before across the pass).

One addition of my own, stated so it can fail:

- **P-T5. The phone's WAVs match the Mac clips' sample counts within 5 ms
  (120 samples at 24 kHz) on all three pulled lines.** Same model bytes, same
  lexicon, same espeak-ng-data; the only differences are sherpa-onnx version
  (Mac 1.13.8, phone = the commit built) and CPU arithmetic. If P-T5 fails, the
  front end (tokenisation / phonemisation) differs between the two builds, and
  that is a finding, not noise.

What these predictions do not cover: the app, resident vs reload (rung 2), the
LLM running beside TTS (rung 3), thermals beyond one pass, and pronunciation.

## 2026-09-18 — TTS RUNG 1, MAC SIDE: sherpa-onnx cloned, onnxruntime fetched and hash-matched, pennytts.sh written. NOT built: the build fetches nine more archives the brief did not list

Branch tts-kokoro. Mac only; the phone has not been touched from this branch.

**Clone.** `git clone --depth 1 https://github.com/k2-fsa/sherpa-onnx` into
~/Documents/sherpa-onnx. HEAD **a5b4a944c5186a68bcdc0ac3011e4c541781ac84**
(17 Sept 2026, "Add conversion scripts for 7M Kokoro distills"), the commit the
brief was checked against. 51M on disk including 7.2M of .git.

**onnxruntime.** No wget on this Mac, so fetched by curl into
~/Documents/sherpa-onnx-deps/ and unzipped there (the brief's fallback route):

    onnxruntime-android-1.28.2.zip   33,765,715 bytes
      sha256 01518867f78241138b6aa25925802e843a4fa9085af8d303d49e35bbb52aff4d
      MATCHES the hash sherpa-onnx pins at a5b4a94,
      cmake/onnxruntime-android-aarch64.cmake:18
    jni/arm64-v8a/libonnxruntime.so  ELF 64-bit LSB shared object, ARM aarch64
      sha256 33847ad43bffe204699fd4a27f7f3603452a8cdaf2f9a44983a0bc31ffcf2da1 (computed)

The build will use it via SHERPA_ONNX_ONNXRUNTIME_ROOT (read at
build-android-arm64-v8a.sh:38 and :97-99 when BUILD_SHARED_LIBS=ON, which is the
script's default at :15-16), so the script's own wget never runs.

**Toolchain.** NDK /opt/homebrew/share/android-commandlinetools/ndk/30.0.16248370
(the llama.cpp one, per build-pennyload.sh:11). cmake is not on PATH; the SDK's
cmake 3.22.1 is, at .../cmake/3.22.1/bin, and sherpa-onnx requires >= 3.15
(CMakeLists.txt:5). ninja at /opt/homebrew/bin/ninja.

**What the brief's download list missed.** build-android-arm64-v8a.sh configures
with cmake, and at configure time CMakeLists.txt:544-578 FetchContent-downloads
these from GitHub/GitLab. Each is sha256-pinned in sherpa-onnx's cmake/*.cmake, so
cmake refuses a file that does not match; GitHub archive URLs report no
Content-Length, so their sizes are not known before download:

    kaldi-native-fbank v1.22.3        (always)
    kaldi-decoder v0.3.0              (always)
    eigen 5.0.1 (gitlab.com)          (via kaldi-decoder)
    openfst v1.8.5-2026-07-09         (via kaldi-decoder)
    simple-sentencepiece v0.7         (always)
    nlohmann json v3.12.0             (always)
    espeak-ng @ed530aa (csukuangfj)   (TTS on)
    piper-phonemize @f3ff95a          (TTS on)
    hclust-cpp 2026-02-25             (speaker diarization on, the script's default)

Build is held until Matt approves these.

**Mac reference sample counts for step 4d** (abtest-penny/NN-int8.wav, written
by ~/kokoro-models/.venv's sherpa-onnx **1.13.8** Python wheel — NOT the a5b4a94
source the phone will run; a version difference is one explanation to rule out
if P-T5 fails):

    00 "On it."                 19,812 samples  0.826 s  24 kHz PCM_16
    05 "Revenue ... £12,480.50" 165,742 samples 6.906 s
    15 the long line            250,017 samples 10.417 s

**CLI defaults checked at a5b4a94, so the phone matches the Mac run:**
max_num_sentences 1 (offline-tts.h:34 — the Mac script set 1), speed 1.0
(passed explicitly anyway), kokoro length_scale 1.0.

**pennytts.sh**, at the worktree root beside pennybench.sh (which it copies in
shape). One line per invocation: `pennytts.sh <tag> <mask|none> <threads>
<0-17|text>`. The 18 lines are embedded by number so "£" and apostrophes never
cross adb quoting. Wall time is taken in the launching subshell immediately
either side of the binary, not by the 0.2 s poll loop (which would add up to
~0.3 s — the size of the thing being derived). COOL=1 (default) gates launch on
both clock ceilings reading rated; COOL=0 for lines 2-18 of a pass. Audio length
is read from the WAV header after checking the "data" tag at byte 36
(wave-writer.cc writes a canonical 44-byte int16 header). Outputs to
/data/local/tmp/tts/out/, separate from pennybench's /data/local/tmp/out. No
dumpsys per line (it would heat the package between lines of a pass).
Checked on the Mac: `sh -n` and `bash -n` pass; the ms parsing of wall and
Elapsed tested against sample values. **Not run under mksh or toybox**: the Mac
has neither, so `date +'%s %N'`, `od -tu4 -j` and fractional `sleep` on the
phone are unconfirmed until the first row. The script marks wall as `invalid`
rather than computing it from a non-numeric %N.

**Known limitation, stated before it bites:** a short line ("On it.") may run
only ~1 s, i.e. ~3 poll samples. VmHWM is monotonic, so the last sample is a
floor on the peak, not the peak, if the peak lands in the final <0.2 s. The
first row reports `rss_samples`; if it is under ~5, the peak for short lines is
quoted as "at least".

**A risk to check at the first run, not a finding:** the llama.cpp build hit
SIGILL from i8mm instructions this Tensor G1 does not have. The onnxruntime .so
here is a prebuilt that selects kernels at runtime (MLAS), so the same failure
is not expected — but it has not run on this CPU yet.

What this entry does not say: that sherpa-onnx builds with this NDK and cmake,
that the binary runs on the 6a, or anything measured on the phone.

## 2026-09-18 — TTS RUNG 1, BUILD NOT RUN: with ENABLE_BINARY=ON the script fetches ELEVEN archives, not nine — websocketpp and asio are also pulled, and neither is approved

Branch tts-kokoro, after merging main (5d1c4ac) as **8f519a1**. Mac only. The
phone has not been touched from this branch. Nothing was configured, compiled
or downloaded; `~/Documents/sherpa-onnx/build-android-arm64-v8a/` does not exist
and the clone's `git status` is clean.

**Record correction to the first entry's artefact list.**
~/kokoro-models/penny-kokoro-int8 also holds **lexicon-us-en.txt, 5,956,885 B**,
which that list left out. It will be pushed with the folder in step 2; the
runs use lexicon-gb-en.txt only.

**Why the build is held.** The command Matt approved was

    ANDROID_NDK=/opt/homebrew/share/android-commandlinetools/ndk/30.0.16248370 \
    SHERPA_ONNX_ONNXRUNTIME_ROOT=/Users/mattstevenson/Documents/sherpa-onnx-deps/onnxruntime-android-1.28.2 \
    SHERPA_ONNX_ENABLE_BINARY=ON ./build-android-arm64-v8a.sh

with nine archives approved and "anything beyond nine is a STOP". Reading the
cmake path at a5b4a94 before running it:

    CMakeLists.txt:55     option(SHERPA_ONNX_ENABLE_WEBSOCKET ... ON)
    CMakeLists.txt:562-565  if(SHERPA_ONNX_ENABLE_WEBSOCKET)
                              include(websocketpp)
                              include(asio)

build-android-arm64-v8a.sh:172-193 does not pass SHERPA_ONNX_ENABLE_WEBSOCKET
and reads no environment variable for it, so the option stays ON and configure
fetches two more:

    websocketpp @b9aeec6e  cmake/websocketpp.cmake:5,7
      SHA256=1385135ede8191a7fbef9ec8099e3c5a673d48df0c143958216cd1690567f583
    asio 1-24-0            cmake/asio.cmake:4,6
      SHA256=cbcaaba0f66722787b1a7c33afe1befb3a012b5af3ad7da7ff0f6b8c9b7a8a5b

Neither exists in ~/Downloads or /tmp (the local fallbacks those cmake files
check first). **This applies to the script's default build too, not only to
ENABLE_BINARY=ON** — the include is unconditional on the binary flag — so the
first entry's list of nine was short by two from the start. The websocket
executables themselves (sherpa-onnx/csrc/CMakeLists.txt:762) are only built
with ENABLE_BINARY=ON, and sherpa-onnx-offline-tts (:526) does not link them.

What is NOT fetched on this path, checked in the cmake source: onnxruntime
(cmake/onnxruntime.cmake:170-184 takes libonnxruntime.so from
SHERPA_ONNXRUNTIME_LIB_DIR, which the script sets at :98 from
SHERPA_ONNX_ONNXRUNTIME_ROOT; SHERPA_ONNX_USE_PRE_INSTALLED_ONNXRUNTIME_IF_AVAILABLE
defaults ON at CMakeLists.txt:74); cargs (only via c-api-examples, and C_API is
OFF at :160-162); pybind11, googletest, portaudio (PYTHON, TESTS, PORTAUDIO all
OFF). **Not checked**: whether any of the nine approved archives fetches
something of its own at configure time — that is only visible in the configure
log, and will be read there.

**A second thing the approved command would hit first.** `cmake` is not on PATH
(`which -a cmake`: not found), and the script calls it bare at :172 under
`set -ex`, so the command as written stops at "cmake: command not found" before
any download. The build needs
`PATH=/opt/homebrew/share/android-commandlinetools/cmake/3.22.1/bin:$PATH`
(cmake 3.22.1-g37088a8; sherpa-onnx requires >= 3.15). The script builds with
`make -j4` (Unix Makefiles), not ninja, so CLAUDE.md's ninja-shadowing trap
does not apply to it.

**Standing instruction for the first row, from Matt, recorded before it runs.**
Before the first row on the phone, read all THREE ceilings — policy0, policy4,
policy6 `scaling_max_freq` — in ONE wrapped `adb shell` invocation with uptime.
pennytts.sh gates on policy6 and policy4 only, and the 18 Sept trap says
policy0 throttles unseen. pennytts.sh is NOT changed for this.

Held for Matt: approve websocketpp + asio, or build with
SHERPA_ONNX_ENABLE_WEBSOCKET=OFF (which the script cannot pass without an edit
to it or a hand-run of its cmake line).

What this entry does not say: that sherpa-onnx builds with this NDK and cmake,
what the nine approved archives weigh or whether they match their pins, that
nothing further is fetched beyond the eleven, or anything about the phone.

## 2026-09-18 — TTS RUNG 1, BUILD STOPPED IN CONFIGURE: two UNAPPROVED archives were fetched (kissfft, kaldifst — pulled by approved archives' own cmake) before the stop fired. No binary exists.

Branch tts-kokoro. Mac only; the phone has not been touched from this branch.

**The edit to the clone (Matt's option 2).** sherpa-onnx clone = a5b4a94 + this one uncommitted line:

    diff --git a/build-android-arm64-v8a.sh b/build-android-arm64-v8a.sh
    index 8e05154..5b592a7 100755
    --- a/build-android-arm64-v8a.sh
    +++ b/build-android-arm64-v8a.sh
    @@ -183,6 +183,7 @@ cmake -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK/build/cmake/android.toolchain.cmake"
         -DSHERPA_ONNX_ENABLE_TESTS=OFF \
         -DSHERPA_ONNX_ENABLE_CHECK=OFF \
         -DSHERPA_ONNX_ENABLE_PORTAUDIO=OFF \
    +    -DSHERPA_ONNX_ENABLE_WEBSOCKET=OFF \
         -DSHERPA_ONNX_ENABLE_JNI=$SHERPA_ONNX_ENABLE_JNI \
         -DSHERPA_ONNX_LINK_LIBSTDCPP_STATICALLY=OFF \
         -DSHERPA_ONNX_ENABLE_C_API=$SHERPA_ONNX_ENABLE_C_API \

`bash -n` passes. Configure log line 71 reads `-- SHERPA_ONNX_ENABLE_WEBSOCKET OFF`,
and websocketpp and asio were not fetched.

**Command, run from ~/Documents/sherpa-onnx, stdout+stderr to a log:**

    PATH=/opt/homebrew/share/android-commandlinetools/cmake/3.22.1/bin:$PATH \
    ANDROID_NDK=/opt/homebrew/share/android-commandlinetools/ndk/30.0.16248370 \
    SHERPA_ONNX_ONNXRUNTIME_ROOT=/Users/mattstevenson/Documents/sherpa-onnx-deps/onnxruntime-android-1.28.2 \
    SHERPA_ONNX_ENABLE_BINARY=ON ./build-android-arm64-v8a.sh

cmake 3.22.1-g37088a8. Started 17:17:35 BST (read in the launching command). It
never reached `make`: no install/ directory exists. No build wall time is
quoted, because the build did not complete.

**What went wrong, in order.** The nine approved archives were named from
sherpa-onnx's own cmake/*.cmake. Two of them fetch dependencies of their own at
configure time, which the earlier entries did not look for (their cmake files
only exist once the archive is unpacked):

    kaldi-native-fbank v1.22.3 -> its CMakeLists.txt:136 include(kissfft)
      kissfft @febd4cae  pinned _deps/kaldi_native_fbank-src/cmake/kissfft.cmake:9,11
    kaldi-decoder v0.3.0 -> its CMakeLists.txt:56 include(kaldifst)
      kaldifst v1.8.0    pinned _deps/kaldi_decoder-src/cmake/kaldifst.cmake:4-5

Configure log lines 141 and 152 name them: `Downloading kissfft from
https://github.com/mborgerding/kissfft/archive/febd4cae….zip` and `Downloading
kaldifst from https://github.com/k2-fsa/kaldifst/archive/refs/tags/v1.8.0.tar.gz`.

**The stop was mine and it was wrong.** The watcher I ran beside the build
counted `_deps/*-subbuild` directories and killed at more than nine. It should
have matched names against the approved list. So it let kissfft (the 3rd fetch)
and kaldifst (the 4th) through, and fired only at the 10th subbuild,
piper-phonemize, at ~17:24. Its `pkill` stopped the script but not the cmake
children; the ps listing showed cmake still downloading piper-phonemize, and I
killed those four PIDs by hand. `pgrep` then returned none running (17:24:17). **Two
archives outside the approval are on disk, and hclust-cpp (approved) was never
reached.**

**Every archive on disk.** Bytes are from `stat -f %z` on the file in
`_deps/<name>-subbuild/<name>-populate-prefix/src/`. The sha256 was COMPUTED by
me and compared with the pin line named. **No cmake log line shows the hash
check**: FetchContent runs quiet by default and prints download output only on
failure. The only evidence from cmake itself is that each completed archive has
its `-populate-download` stamp, which ExternalProject writes only after its
hash check passes.

    archive                 approved  bytes        sha256 vs pin   pin line
    kaldi-native-fbank      yes           71,144   MATCH           cmake/kaldi-native-fbank.cmake:6
    kaldi-decoder           yes           51,199   MATCH           cmake/kaldi-decoder.cmake:5
    eigen 5.0.1             yes        2,967,272   MATCH           cmake/eigen.cmake:5
    openfst 1.8.5-2026-07-09 yes       1,501,685   MATCH           cmake/openfst.cmake:7
    simple-sentencepiece    yes          355,335   MATCH           cmake/simple-sentencepiece.cmake:6
    nlohmann json 3.12.0    yes        9,678,593   MATCH           cmake/json.cmake:6
    espeak-ng @ed530aa      yes       18,011,501   MATCH           cmake/espeak-ng-for-piper.cmake:5
    piper-phonemize @f3ff95a yes       1,916,928   PARTIAL         cmake/piper-phonemize.cmake:5
                                                   (killed mid-transfer; the size grew
                                                   1,032,192 -> 1,916,928 between two
                                                   reads; its sha256 is of a truncated
                                                   file and means nothing)
    hclust-cpp              yes       not fetched  --              cmake/hclust-cpp.cmake
    kissfft @febd4cae       NO            74,252   MATCH           _deps/kaldi_native_fbank-src/cmake/kissfft.cmake:11
    kaldifst v1.8.0         NO           172,147   MATCH           _deps/kaldi_decoder-src/cmake/kaldifst.cmake:5

    complete approved archives on disk: 7, 32,636,729 B
    unapproved on disk: 2, 246,399 B

**Left as it is, for Matt:** `~/Documents/sherpa-onnx/build-android-arm64-v8a/`
with all of the above, including the partial piper-phonemize zip. Nothing was
deleted. The clone's `git status` is ` M build-android-arm64-v8a.sh` only.

**What the full fetch list now looks like, as far as it has been read.**
The nine, plus kissfft and kaldifst, is eleven. **Unread**: whether
simple-sentencepiece, json, espeak-ng, piper-phonemize (which never finished
unpacking) or hclust-cpp fetch anything of their own. The configure log up to
the kill shows none from the first seven. piper-phonemize and hclust-cpp are the
two still unread.

What this entry does not say: that sherpa-onnx builds with this NDK, anything
about a binary, its libraries or its hashes, that the fetch list is complete at
eleven, or anything about the phone.

## 2026-09-18 — TTS RUNG 1, BUILD FAILED AT LINK: configure completed with all ELEVEN fetches pinned and matching, then every executable failed to link — libonnxruntime.so 1.28.2 needs API-24 libc symbols and the script targets android-21. No binary exists.

Branch tts-kokoro. Mac only; the phone has not been touched from this branch.

**Rule change from Matt, applying from this run:** any archive cmake fetches
during configure is approved if it is sha256-pinned in the cmake file that
requests it. kissfft and kaldifst are approved on that basis. The stop
conditions are only (a) a fetch with no pin, or (b) a hash-check failure. No
watcher and no pkill.

**Deleted before the rerun**, from
~/Documents/sherpa-onnx/build-android-arm64-v8a/_deps/, and nothing else:
`piper_phonemize-subbuild/` (41 entries, 2,260 KB, including the partial
`f3ff95afc03640bc1399e113e83361192a2fafb4.zip` of 1,916,928 B),
`piper_phonemize-src/` (empty) and `piper_phonemize-build/` (empty).

**Run 2.** It used the same command as the previous entry, with the clone's
one-line WEBSOCKET=OFF edit still in place (clone = a5b4a94 + that line). The
wall time was read in the launching command:
START 17:27:42 BST, END 17:31:05 BST, **rc=2, wall_s=203**.
Configure completed ("Configuring done", "Generating done", "Build files have
been written", log lines 223-225). `make -j4` stopped at [ 87%].

**Every archive fetched across both runs.** Run 2 re-used run 1's archives, and
their mtimes are unchanged (17:17:40-17:23:53). Only piper-phonemize (17:29:56)
and hclust-cpp (17:29:58) were fetched in run 2. Bytes are from `stat -f %z`.
Each sha256 was COMPUTED on the Mac and compared with the pin line named. No
cmake log line prints the hash check (FetchContent is quiet on success). Each
archive has its `-populate-download` stamp, which ExternalProject writes only
after the check passes.

    archive                    orig nine  bytes        sha256 vs pin  pin file:line
    kaldi-native-fbank v1.22.3 yes            71,144   MATCH          cmake/kaldi-native-fbank.cmake:6
    kaldi-decoder v0.3.0       yes            51,199   MATCH          cmake/kaldi-decoder.cmake:5
    eigen 5.0.1                yes         2,967,272   MATCH          cmake/eigen.cmake:5
    openfst 1.8.5-2026-07-09   yes         1,501,685   MATCH          cmake/openfst.cmake:7
    simple-sentencepiece v0.7  yes           355,335   MATCH          cmake/simple-sentencepiece.cmake:6
    nlohmann json v3.12.0      yes         9,678,593   MATCH          cmake/json.cmake:6
    espeak-ng @ed530aa         yes        18,011,501   MATCH          cmake/espeak-ng-for-piper.cmake:5
    piper-phonemize @f3ff95a   yes         9,805,962   MATCH          cmake/piper-phonemize.cmake:5
    hclust-cpp 2026-02-25      yes            22,523   MATCH          cmake/hclust-cpp.cmake:7
    kissfft @febd4cae          no             74,252   MATCH          _deps/kaldi_native_fbank-src/cmake/kissfft.cmake:11
    kaldifst v1.8.0            no            172,147   MATCH          _deps/kaldi_decoder-src/cmake/kaldifst.cmake:5

    original nine: 42,465,214 B   the two added: 246,399 B   all eleven: 42,711,613 B
    (plus the discarded partial piper-phonemize zip from run 1, 1,916,928 B)

**Nothing unpinned was fetched.** The whole build tree has exactly eleven
`*-populate-download` stamps and no other `-prefix` or `-stamp` directory.
Two fetch routes exist in the unpacked sources and did NOT run:
piper-phonemize's own `ExternalProject_Add` (its CMakeLists.txt:46-70) sits
under `if(NOT DEFINED ESPEAK_NG_DIR)`, and ESPEAK_NG_DIR is set. espeak-ng's
sonic `FetchContent_Declare` (its cmake/deps.cmake:18-19) sits under
`USE_LIBSONIC`, which is OFF.

**The failure.** Four executables failed to link, and make stopped before
reaching any others:
sherpa-onnx-offline-audio-tagging, sherpa-onnx-offline,
sherpa-onnx-keyword-spotter and sherpa-onnx. `build/bin/` is empty.
**sherpa-onnx-offline-tts was never linked.** It links the same
libonnxruntime.so, so the same failure is expected, but it was not observed.
Each failure is the same five undefined symbols, all referenced by
`onnxruntime-android-1.28.2/jni/arm64-v8a/libonnxruntime.so` and rejected
by `--no-allow-shlib-undefined`:

    __register_atfork@LIBC  stderr@LIBC  __gnu_strerror_r@LIBC
    __write_chk@LIBC_N  __fwrite_chk@LIBC_N

The script defaults to `SHERPA_ONNX_ANDROID_PLATFORM=android-21`
(build-android-arm64-v8a.sh:164-166; CMakeCache ANDROID_PLATFORM=android-21).
Checked with the NDK's own llvm-readelf --dyn-syms against its libc.so stubs
(sysroot/usr/lib/aarch64-linux-android/<api>/libc.so):

    API 21: none of the five exported
    API 23: the three LIBC ones, not the two LIBC_N ones
    API 24: all five

libonnxruntime.so's version-needs list is LIBC and LIBC_N, and LIBC_N is API
24 (Nougat). So this prebuilt needs API >= 24 to link against. The NDK r30
range is 21-37 (meta/platforms.json). The 6a runs Android 17, API 37.

**Likely fix, not applied (it changes the command):** add
`SHERPA_ONNX_ANDROID_PLATFORM=android-24` to the command. The script already
reads it at :164, so the clone needs no edit. The build dir's cache holds
android-21 and the toolchain detection from the first configure. The cleanest
rerun deletes `CMakeCache.txt` and `CMakeFiles/` at the build-dir top level
only; `_deps/` and its eleven verified archives stay. Held for Matt.

What this entry does not say: that android-24 links; anything about
sherpa-onnx-offline-tts, `file` on it, its needed libs, or its sha256;
anything about install/lib/libonnxruntime.so (the script copies it only after
make succeeds); or anything about the phone.

## 2026-09-18 — TTS RUNG 1, BUILT at android-24: sherpa-onnx-offline-tts is aarch64, links only libonnxruntime.so plus Android system libs, and libonnxruntime.so is byte-identical to the hash-matched prebuilt. BUT it is a MIXED build: 318 objects were compiled for android-21 in run 2 and reused.

Branch tts-kokoro. Mac only; the phone has not been touched from this branch.

**Correction to the header at notes-tts.md:317.** It says "every executable
failed to link". Four failed: sherpa-onnx-offline-audio-tagging,
sherpa-onnx-offline, sherpa-onnx-keyword-spotter and sherpa-onnx. make stopped
there, and the rest were not attempted. That entry's body was already right.

**android-24 is a required build parameter for this onnxruntime prebuilt**
(onnxruntime-android-1.28.2, libonnxruntime.so sha256 33847ad4…), because it
needs LIBC_N symbols. The 7a build carries SHERPA_ONNX_ANDROID_PLATFORM=android-24
or higher.

**Deleted before the rerun, at the top level of
~/Documents/sherpa-onnx/build-android-arm64-v8a/ only:** `CMakeCache.txt`
(37,948 B, held ANDROID_PLATFORM=android-21) and `CMakeFiles/` (162 entries).
`_deps/` and every per-subdirectory CMakeFiles/ under the build tree were left.

**Run 3, command (from ~/Documents/sherpa-onnx; clone = a5b4a94 + the one
uncommitted WEBSOCKET=OFF line):**

    PATH=/opt/homebrew/share/android-commandlinetools/cmake/3.22.1/bin:$PATH \
    ANDROID_NDK=/opt/homebrew/share/android-commandlinetools/ndk/30.0.16248370 \
    SHERPA_ONNX_ONNXRUNTIME_ROOT=/Users/mattstevenson/Documents/sherpa-onnx-deps/onnxruntime-android-1.28.2 \
    SHERPA_ONNX_ENABLE_BINARY=ON SHERPA_ONNX_ANDROID_PLATFORM=android-24 ./build-android-arm64-v8a.sh

    START 2026-09-18 17:34:38 BST   END 17:34:58 BST   rc=0   wall_s=20
    (read in the launching command)

Configure: log line 47 passes `-DANDROID_PLATFORM=android-24`, and
CMakeCache.txt:21 reads `ANDROID_PLATFORM:UNINITIALIZED=android-24`.

**Nothing downloaded again.** The log prints the eleven "Downloading …" status
lines on every configure, but every archive under _deps/ has the same size and
mtime as before the run (17:17:40-17:29:58). There is no new archive and no new
subbuild.

**THE CAVEAT: the 20 s wall is because this was NOT a clean android-24 build.**
Run 2 compiled 318 objects (log count of "Building C/CXX object") for
android-21 before it failed at link. Run 3 compiled only 44: the ones run 2
never reached, which were 5 in sherpa-onnx-core, one main.cc per executable
(including sherpa-onnx-offline-tts.cc.o, 17:34:50), 23 in sherpa-onnx-jni and
1 in ssentencepiece_core. It then linked 30 targets. The regenerated rules name
android24 (sherpa-onnx-core.dir/build.make: 690 occurrences), but CMake's
Makefile generator does not rebuild an object when only its rule's target
triple changes. So most of libsherpa-onnx-core, and every _deps library,
inside sherpa-onnx-offline-tts was compiled at __ANDROID_API__ 21. The binary's
.note.android.ident records API 0x18 = 24, NDK r30 16248370 (from the crt
objects at link). The mix is expected to run on API 37 (API-21 code is
forward-compatible), but it is not the build that was asked for, and it is
not reproducible from the command above alone.

**The artefacts, as built:**

    install/bin/sherpa-onnx-offline-tts   2,426,304 B
      file: ELF 64-bit LSB pie executable, ARM aarch64, dynamically linked,
            interpreter /system/bin/linker64, BuildID[sha1]=2266ef9f1bfe5cae7e31b160728b743f9ee672f1, stripped
      sha256 cd23a509cacad4fa17a41e85d8ebc60a3c84fe34ae9716b629b7a15bb4e85a15 (computed)
      RUNPATH $ORIGIN/../lib:$ORIGIN/../../../sherpa_onnx/lib
      needed (llvm-readelf --needed-libs, NDK r30):
        libonnxruntime.so   install/lib/
        libandroid.so libc.so libdl.so liblog.so libm.so   Android system libs (not yet checked on the phone)
    install/lib/libonnxruntime.so        22,249,560 B
      sha256 33847ad43bffe204699fd4a27f7f3603452a8cdaf2f9a44983a0bc31ffcf2da1
      = the unzipped prebuilt's jni/arm64-v8a/libonnxruntime.so (notes-tts.md:69),
        whose zip matched sherpa-onnx's pin at cmake/onnxruntime-android-aarch64.cmake:18
      needed: libc.so libdl.so liblog.so libm.so (system)
    install/lib/libsherpa-onnx-jni.so    4,892,296 B (not needed by the CLI binary)

sherpa-onnx-core is linked statically into the binary; install/lib holds no
libsherpa-onnx-core.so, and the binary does not NEED one. libc++ is the NDK's
static one (the script's default), so there is no libc++_shared.so to push.

**Recommended before anything is pushed, held for Matt:** a clean android-24
rebuild. Delete everything in build-android-arm64-v8a/ except `_deps/*-subbuild`
(which holds the eleven verified archives and their download stamps), rerun
the same command, and take the new sha256. With the same FetchContent stamps
it should not download again, and a full compile was ~3 min in run 2.

What this entry does not say: that the binary runs on the 6a; that the mixed
build behaves differently from a clean one (untested either way); that the five
system libs resolve on the phone; or anything measured.

## 2026-09-18 — TTS RUNG 1, CLEAN android-24 BUILD AND PUSH: 356 objects compiled from scratch, nothing downloaded, new binary bd7d26e8…; every pushed file hashes identically on the phone; --help exits 0 on the 6a. No row run.

Branch tts-kokoro. Boot 4 (up since 13:17:34, a spent boot: S0/S1/S2 and
eight model loads ran on it).

**Deleted, with Matt's approval, from ~/Documents/sherpa-onnx/build-android-arm64-v8a/.**
Top level: `bin`, `cmake_install.cmake`, `CMakeCache.txt`, `CMakeFiles`,
`CPackConfig.cmake`, `CPackSourceConfig.cmake`, `install`,
`install_manifest.txt`, `lib`, `Makefile`, `sherpa-onnx`, `sherpa-onnx.pc`.
In _deps: all eleven `*-build` directories. **Kept: `_deps/*-subbuild` (11) AND
`_deps/*-src` (11).** The approval named only -subbuild. Before deleting,
I found that each subbuild's `<name>-populate-download` stamp also covers
extraction (kissfft-subbuild build.make:96-101: download, verify and extract
all run in one step, whose stamp depends only on urlinfo.txt and the mkdir
stamp). With the stamps present, cmake would not re-extract, and configure
would have met eleven empty source dirs. Matt chose to keep -src. It holds
extracted sources only; `find . -name '*.o'` outside the subbuilds returned 0
before the run.

**Run 4** (same command as run 3, SHERPA_ONNX_ANDROID_PLATFORM=android-24;
clone = a5b4a94 + the one WEBSOCKET=OFF line):

    START 2026-09-18 17:40:48 BST   END 17:42:08 BST   rc=0   wall_s=80
    "Building C/CXX object": 356   (run 2: 318, run 3: 44)   "Linking": 30
    log line 47 -DANDROID_PLATFORM=android-24; CMakeCache.txt:21 android-24
    no "linux-android21" in any build.make; "linux-android24" 999 times

**Nothing downloaded.** All eleven archives keep their sizes and mtimes
(17:17:40-17:29:58, the same eleven values as in the previous entry). There are
still exactly eleven subbuilds.

**The artefact:**

    install/bin/sherpa-onnx-offline-tts   2,432,496 B
      ELF 64-bit LSB pie executable, ARM aarch64, dynamically linked,
      interpreter /system/bin/linker64, BuildID[sha1]=a0028df3343558e8e5ea599edbe74a411906214a, stripped
      sha256 bd7d26e8f1cca82da2596fce2fe1957b2a2ed139f772a7655ec5983cb83c4f2d
      DIFFERS from the mixed build's cd23a509… (2,426,304 B), as expected
      needed: libonnxruntime.so (install/lib) + libandroid libc libdl liblog libm (system)
    install/lib/libonnxruntime.so        22,249,560 B
      sha256 33847ad43bffe204699fd4a27f7f3603452a8cdaf2f9a44983a0bc31ffcf2da1 (unchanged)

**Before the push, one wrapped invocation:** uptime_s=15908.91,
wallclock 2026-09-18 17:42:42 BST, MemAvailable 2,115,568 kB, SwapFree
802,404 kB, scaling_max_freq/cpuinfo_max_freq policy0 1803000/1803000, policy4
2253000/2253000, policy6 2802000/2802000. `/data/local/tmp/tts` did not exist.
AC power, screen on, unlocked; nothing of ours running.

**Push** (adb push; `mkdir /data/local/tmp/tts` first; chmod 755 on the binary
and pennytts.sh; nothing outside /data/local/tmp/tts touched):

    sherpa-onnx-offline-tts   2,432,496 B in 0.005 s
    libonnxruntime.so        22,249,560 B in 0.219 s
    penny-kokoro-int8/       360 files, 150,880,597 B in 3.093 s (46.5 MB/s)
    pennytts.sh                  12,363 B

**sha256, phone (sha256sum) against the Mac (shasum -a 256), all MATCH:**

    sherpa-onnx-offline-tts  bd7d26e8f1cca82da2596fce2fe1957b2a2ed139f772a7655ec5983cb83c4f2d
    libonnxruntime.so        33847ad43bffe204699fd4a27f7f3603452a8cdaf2f9a44983a0bc31ffcf2da1
    model.int8.onnx          a089794d1293b91e82f3f2b8bed5417d04ac64447d6ada5f21045cde0799bf99
    voices.bin               1c5a5b983d3d50d8586d437a51f3faa2da7919ce76a013c081e65671a3447c29
    lexicon-gb-en.txt        c4cbb37316f62210dff52718a7afcaae24f50c032cc75ab47ae67b831d1049e7
    lexicon-us-en.txt        7daaab53a181be9885b853a8582bf1838186317e5dadacbcef9c426d6fa0da14
    tokens.txt               6ebb6bb288f20f3ae8d004d3c2ca27697da27c037d75e81a60e2a6a663f95425
    pennytts.sh              79ec84160a73d3aafb11e808a73b9ad733fc49f0b50b2973e06a7f698dc604b2
                             (= the copy committed at 25b6ed3)

espeak-ng-data: 355 files, 37 dirs on the phone; 355 files, 37 dirs on the
Mac (17,991,651 B). Files were counted, not hashed individually.

**After the push, one wrapped invocation:** uptime_s=15932.39, 17:43:06,
MemAvailable 2,097,528 kB, SwapFree 802,660 kB.

**Smoke (--help only, no model, no taskset), run from /data/local/tmp/tts with
LD_LIBRARY_PATH=/data/local/tmp/tts:** exit **rc=0** at uptime 15942.84,
17:43:16. The output (156 lines, 12,646 B) was written to
/data/local/tmp/tts/help.txt, the one file created beyond the push. First two
lines, verbatim:

    /Users/mattstevenson/Documents/sherpa-onnx/sherpa-onnx/csrc/parse-options.cc:PrintUsage:415
    (empty)

Line 3 is "Offline/Non-streaming text-to-speech with sherpa-onnx". The usage
lists every flag pennytts.sh passes: --kokoro-model --kokoro-voices
--kokoro-tokens --kokoro-lang --kokoro-lexicon --kokoro-data-dir --num-threads
--sid --speed --output-filename. It also lists --kokoro-dict-dir and
--kokoro-length-scale, which pennytts.sh does not pass.

This proves the binary and libonnxruntime.so load, link and run on this CPU
against bionic at API 37. It proves nothing about the model path: no ONNX
session was created and no onnxruntime kernel ran, so the SIGILL risk named in
the second entry is still untested.

What this entry does not say: that the model loads or generates audio on the
6a; that pennytts.sh runs under the phone's shell (its `date +%N` guard is
still unexercised); anything measured; or that any of it is a baseline. Boot
4 is spent.

## 2026-09-18 — TTS RUNG 1, ROW 4a: "On it." ×5, X1 pair, 2 threads, cool-gated — all rc=0; wall 3,021-3,082 ms, elapsed 1,098-1,162 ms, RTF 1.324-1.401, peak RSS 286,280-287,112 kB; WAV 19,899 samples vs the Mac's 19,812. Boot 4, spent; nothing here is a baseline.

Branch tts-kokoro. **Boot 4** (up since 13:17:34). It had already carried S0, S1
(59.16 min) and S2 (26.03 min) and eight model loads, so every figure below is
from a spent boot. **No figure here is a baseline**, and each is "on the 6a".

**Correction: the line at notes-tts.md:540 ("AC power, screen on, unlocked;
nothing of ours running") was not read when it was written.** Read at 17:46:11,
in one wrapped invocation (uptime 16117.09 -> 16117.52):

    battery/status   Charging     capacity 100    battery/temp 278 (dC, battery not SoC)
    online           main-charger 1, usb 1, tcpm-source-psy-i2c-max77759tcpc 1, dc 0
    dumpsys power    mWakefulness=Awake, mWakefulnessChanging=false
                     "Display Power:" printed only an object reference
                     (com.android.server.power.PowerManagerService$1@ec9ada9), not a state
    pgrep -l         pennyload: nothing; llama: nothing
    pm list packages -d   package:com.pennyspike.probe2a  (disabled)
    ceilings         policy0 1803000, policy4 2253000, policy6 2802000

So: USB/mains charging at 100%, the device awake, none of our processes, the
app disabled. "Unlocked" was not read directly; adb answered, and CLAUDE.md
says the port is charging-only while locked.

**A void first attempt, recorded.** At 17:46:22 (uptime 16128.52) the first
ack-1 invocation redirected the script's stderr to
/data/local/tmp/tts/out/ack-1.sherr before out/ existed (the script creates it).
The phone's shell refused the redirect ("can't create … No such file or
directory", rc=1) and **pennytts.sh never ran**. That was confirmed afterwards:
no out/, no sherpa process, and 0.11 s between the hand reads either side.
My error. The row below is the second invocation, with stderr to
/data/local/tmp/tts/ack-1.sherr, which ended up empty (0 B) on all five rows.

**Command, per row, one adb shell invocation each, hand-read ceilings either side:**

    COOL=1 sh /data/local/tmp/tts/pennytts.sh ack-N c0 2 0

c0 = cpus 6-7 (X1 pair). 2 threads, sid 22, lang en, lexicon-gb-en.txt,
speed 1.0. Binary bd7d26e8…, libonnxruntime.so 33847ad4…

**ack-1's .report, verbatim** (from /data/local/tmp/tts/out/ack-1.report):

    PENNYTTS tag=ack-1 rc=0 mask=c0 threads=2 cool_gate=1
    PENNYTTS text=On it.
    PENNYTTS bin=/data/local/tmp/tts/sherpa-onnx-offline-tts
    PENNYTTS cool_wait_s     uptime 16146.57 -> 16146.65
    PENNYTTS uptime_s        before=16146.69 after=16150.43
    PENNYTTS wall_ms         3021   (exec to exit, taken in the launching subshell)
    PENNYTTS elapsed_ms      1098   (the binary's own Elapsed: generate only)
    PENNYTTS derived_load_ms 1923   (wall - elapsed: process start + load + WAV write + 2 date forks)
    PENNYTTS Number of threads: 2
    PENNYTTS Audio duration: 0.829 s
    PENNYTTS Real-time factor (RTF): 1.098/0.829 = 1.324
    PENNYTTS wav             sr=24000 ch=1 bytes=39798 samples=19899 ms=829
    PENNYTTS memavail_kB     before=2073044 after=2044564
    PENNYTTS memfree_kB      before=766112 after=735300
    PENNYTTS swapfree_kB     before=804196 after=804196
    PENNYTTS cached_kB       before=1475944 after=1476864
    PENNYTTS pswpin          before=238174 after=238174
    PENNYTTS pswpout         before=852970 after=852970
    PENNYTTS pgmajfault      before=253999 after=254012
    PENNYTTS ceil_x1_kHz     before=2802000 min=2704000 after=2802000   (policy6, cpus 6-7, rated 2802000)
    PENNYTTS ceil_x1_min_at  uptime=16149.37
    PENNYTTS ceil_a76_kHz    before=2253000 min=2253000 after=2253000   (policy4, cpus 4-5, rated 2253000)
    PENNYTTS ceil_a76_min_at uptime=16146.69
    PENNYTTS peak_rss_kB     286736   (VmHWM, monotonic)
    PENNYTTS max_vmrss_kB    283232
    PENNYTTS max_rssanon_kB  232232   (anonymous -- NOT reclaimable)
    PENNYTTS max_rssfile_kB  50680   (file-backed -- reclaimable)
    PENNYTTS rss_samples     9   (sleep 0.2 s between samples; a short run may get very few)
    PENNYTTS lmk_kill_lines  0
    --- binary stderr (tail) ---
    /Users/mattstevenson/Documents/sherpa-onnx/sherpa-onnx/csrc/parse-options.cc:Read:374 /data/local/tmp/tts/sherpa-onnx-offline-tts --num-threads=2 --kokoro-model=/data/local/tmp/tts/penny-kokoro-int8/model.int8.onnx --kokoro-voices=/data/local/tmp/tts/penny-kokoro-int8/voices.bin --kokoro-tokens=/data/local/tmp/tts/penny-kokoro-int8/tokens.txt --kokoro-data-dir=/data/local/tmp/tts/penny-kokoro-int8/espeak-ng-data --kokoro-lexicon=/data/local/tmp/tts/penny-kokoro-int8/lexicon-gb-en.txt --kokoro-lang=en --sid=22 --speed=1.0 --output-filename=/data/local/tmp/tts/out/ack-1.wav 'On it.' 

    Number of threads: 2
    Elapsed seconds: 1.098 s
    Audio duration: 0.829 s
    Real-time factor (RTF): 1.098/0.829 = 1.324
    The text is: On it.. Speaker ID: 22
    Saved to /data/local/tmp/tts/out/ack-1.wav successfully!
    --- kill lines ---

ack-1.wall raw: `1789750001 050402574 1789750004 071966214 0`. That is five
numeric fields, so toybox `date +'%s %N'` returns real nanoseconds on this
build and the %N guard did not fire. ack-1.err (797 B) is exactly the binary
stderr shown above: the parse-options echo of the command, then "Number of
threads", "Elapsed seconds", "Audio duration", "Real-time factor", "The text
is", "Saved to".

**The five rows.** All figures are from out/ack-N.report, .wall and .err. Derived
load = **wall minus elapsed, which includes process start and WAV write** (and
the model load, and two `date` forks).

    row    wall_ms  elapsed_ms  derived_load_ms  RTF    peak_rss_kB  rss_samples  x1 min     a76 min    memavail_kB before -> after
    ack-1  3021     1098        1923             1.324  286,736      9            2,704,000  2,253,000  2,073,044 -> 2,044,564
    ack-2  3082     1121        1961             1.352  286,996      8            2,630,000  2,253,000  2,075,484 -> 2,046,420
    ack-3  3037     1157        1880             1.395  286,628      8            2,630,000  2,253,000  2,043,172 -> 2,048,156
    ack-4  3037     1162        1875             1.401  286,280      8            2,401,000  2,253,000  2,047,384 -> 2,050,980
    ack-5  3063     1151        1912             1.388  287,112      9            2,630,000  2,253,000  2,041,976 -> 2,034,592

    wall .raw   ack-2 1789750024 574685707 1789750027 656700194 0
                ack-3 1789750029 281759521 1789750032 319538901 0
                ack-4 1789750033 950758058 1789750036 988496870 0
                ack-5 1789750038 587688440 1789750041 651081996 0
    audio 0.829 s on every row; wav sr=24000 ch=1 bytes=39798 samples=19899 on every row
    max_rssanon_kB 232,232-235,628; max_rssfile_kB 50,680-51,848
    swapfree unchanged within each row (804,196 on ack-1, 804,452 on 2-5)
    pgmajfault +13 on ack-1, 0 on ack-2/3/4, +8 on ack-5
    lmk_kill_lines 0 on all five; .kills files 0 lines

The x1 min column is policy6, rated 2,802,000: ack-4's 2,401,000 is 85.7% of
rated, and the others are 93.9-96.5%. policy4 never moved.

**Hand-read ceilings (policy0/4/6 scaling_max_freq), each in the same adb
invocation as the row:**

    ack-1 before 16146.43 17:46:40  1803000/2253000/2802000   after 16151.31 17:46:45  same
    ack-2 before 16169.95 17:47:03  1803000/2253000/2802000   after 16174.48 17:47:08  same
    ack-3 before 16174.62 17:47:08  same                      after 16179.10 17:47:13  same
    ack-4 before 16179.33 17:47:13  same                      after 16183.74 17:47:17  same
    ack-5 before 16184.00 17:47:18  same                      after 16188.76 17:47:22  same

**policy0 is read by hand only, and only before and after.** pennytts.sh does
not poll it, so a dip in policy0 during a row would not be seen.

**Cooling between rows was the gate only.** COOL=1 waits until policy6 and
policy4 read rated, and they already did at every start: cool_wait was
0.04-0.10 s on every row. Gaps from one row's `after` to the next row's gate
start: ack-1 -> ack-2 19.68 s (spent reading ack-1's files), then 1.11 s,
1.20 s and 1.16 s. So ack-3 to ack-5 ran nearly back to back, with the X1
ceiling already recovered to rated between rows.

**Cold vs warm.** Only ack-1 is "cold" in the process sense. The model file was
written by adb push at 17:42, so it was likely already in page cache even for
ack-1 (Cached rose 920 kB on ack-1, pgmajfault +13). **No row here measures a
load from flash.**

**The WAV.** ack-1.wav was pulled to the Mac at
~/kokoro-models/phone-6a-rung1/ack-1.wav (39,842 B). Its sha256
4d7c10b9844e86a345fbd45a158eca46a9d02b59fdf87449c0c942b12f6caca4 matches the
phone's copy. Header (python `wave`): 24,000 Hz, mono, 16-bit, **19,899
frames = 829.12 ms**, "data" tag at 36. The Mac clip abtest-penny/00-int8.wav
reads 19,812 frames = 825.50 ms. **Difference: 87 samples = 3.625 ms**, inside
P-T5's 120-sample / 5 ms limit for this line. All five phone WAVs have the
same sha256 (4d7c10b9…), so generation is deterministic across the five
processes. The phone's WAV is not byte-identical to the Mac's (different
lengths).

What this entry does not say: anything about lines other than "On it."; a
load time from flash (the file was likely cached); what policy0 did during a
row; whether the 87-sample difference comes from the front end or from the
1.13.8-vs-a5b4a94 version gap; anything about thermals beyond ~42 s of
intermittent load; the predictions' verdicts (those come in the rung 1
write-up); or anything on a fresh boot. Nothing here is a baseline.

## 2026-09-18 — TTS RUNG 1, ROWS 4b AND 4c: the 18 lines on the X1 pair at 2 threads and unpinned at 4 threads, back to back — 36 of 36 rc=0, no kills, no stderr warnings; X1 ceiling fell to 73.09% of rated across 4b and to one 17.84% reading in 4c; the three pulled WAVs are within 87 samples (3.625 ms) of the Mac's. Boot 4, spent; nothing here is a baseline.

Branch tts-kokoro. Boot 4 (up since 13:17:34; S0/S1/S2, eight model loads
and row 4a had run on it). AC/USB charging at 100% and awake, as read at
17:46:11 (previous entry). **Nothing here is a baseline.** Every figure is "on
the 6a". **The model is page-cached** (pushed at 17:42, and loaded 5 times in
4a), so no derived load below includes a read from flash. Derived load is
always **wall minus elapsed, which includes process start and WAV write** (and
the model load, and two `date` forks).

**Commands.** Each pass ran as ONE adb shell invocation: a hand read of
policy0/4/6 scaling_max_freq with uptime, then
`COOL=1 sh /data/local/tmp/tts/pennytts.sh <p>-00 <mask> <threads> 0`, then
lines 1-17 with COOL=0 back to back, then the hand read again. 4b: tags
p2-00…p2-17, mask c0, 2 threads. 4c: tags p4-00…p4-17, mask none, 4 threads.
Every figure below was parsed by script from out/<tag>.report and .err
(pulled to the Mac, 246 files), not from scrollback. RTF is the binary's own
line.

    ROW 4b: mask c0 (X1 pair), 2 threads
    line  elapsed_ms  audio_ms  RTF    wall_ms  derived_load_ms  peak_rss_kB  rss_n  x1_min_kHz  a76_min_kHz  memavail_kB before -> after  kills
    00         1132       829  1.365     3104             1972      287,392      9   2,802,000    2,253,000  2,076,496 -> 2,040,228      0
    01          959       758  1.266     2930             1971      283,656      8   2,802,000    2,253,000  2,045,320 -> 2,037,368      0
    02         1032       814  1.268     2989             1957      284,532      8   2,704,000    2,253,000  2,033,484 -> 2,045,612      0
    03         1502      1159  1.296     3405             1903      290,724      9   2,401,000    2,253,000  2,042,100 -> 2,034,260      0
    04         4881      4545  1.074     6775             1894      362,492     18   2,188,000    2,253,000  2,054,932 -> 2,048,848      0
    05         7370      6907  1.067     9344             1974      439,460     24   2,252,000    2,253,000  2,043,824 -> 2,057,596      0
    06         4229      3593  1.177     6179             1950      350,340     16   2,252,000    2,253,000  2,036,880 -> 2,016,736      0
    07         6254      5815  1.075     8218             1964      437,848     22   2,401,000    2,253,000  2,030,784 -> 2,056,848      0
    08         5285      4704  1.124     7244             1959      402,264     19   2,252,000    2,253,000  2,061,548 -> 2,040,968      0
    09         5167      4520  1.143     7105             1938      367,124     19   2,048,000    2,253,000  2,036,796 -> 2,038,416      0
    10         4006      3450  1.161     5972             1966      345,576     16   2,188,000    2,253,000  2,036,636 -> 2,049,496      0
    11         3887      3395  1.145     5853             1966      344,000     15   2,188,000    2,253,000  2,047,168 -> 2,042,276      0
    12         4221      3513  1.202     6152             1931      347,428     16   2,188,000    2,253,000  2,063,148 -> 2,057,688      0
    13         3554      3065  1.159     5509             1955      339,508     14   2,401,000    2,253,000  2,047,152 -> 2,029,380      0
    14         9913      7450  1.331    11887             1974      328,580     30   2,188,000    2,253,000  2,051,352 -> 2,058,944      0
    15        11730     10416  1.126    13667             1937      577,228     34   2,048,000    2,253,000  2,046,872 -> 2,052,752      0
    16         4269      3109  1.373     6236             1967      323,132     16   2,188,000    2,253,000  2,055,460 -> 2,030,352      0
    17         6040      4707  1.283     7977             1937      350,988     21   2,188,000    2,253,000  2,045,712 -> 2,040,848      0
    pass: first line start uptime 16414.46, last line end 16560.31, span 145.85 s
    MemAvailable low-water across the pass (before/after readings only): 2,016,736 kB (line 06)
    X1 min across the pass: 2,048,000 kHz = 73.09% of rated, lines/uptime [(9, '16481.31'), (15, '16536.06')]; A76 min 2,253,000
    peak RSS max 577,228 kB (line 15); rc all 0: True; kills total 0

    ROW 4c: unpinned, 4 threads
    line  elapsed_ms  audio_ms  RTF    wall_ms  derived_load_ms  peak_rss_kB  rss_n  x1_min_kHz  a76_min_kHz  memavail_kB before -> after  kills
    00         1302       829  1.570     3185             1883      286,408      8   2,048,000    2,253,000  2,065,612 -> 2,032,884      0
    01         1087       758  1.435     2972             1885      283,764      8   2,401,000    2,253,000  2,025,920 -> 2,046,756      0
    02         1135       814  1.395     3087             1952      284,216      8   2,252,000    2,253,000  2,042,468 -> 2,038,480      0
    03         1670      1159  1.441     3619             1949      291,168     10   2,401,000    2,253,000  2,057,672 -> 2,044,820      0
    04         5376      4545  1.183     7317             1941      363,116     19     500,000    2,253,000  2,043,748 -> 2,038,888      0
    05         8111      6907  1.174    10080             1969      441,496     27   1,826,000    2,253,000  2,060,812 -> 2,025,672      0
    06         4605      3593  1.282     6567             1962      350,708     17   2,048,000    2,253,000  2,045,044 -> 2,014,792      0
    07         6979      5815  1.200     8906             1927      438,232     23   1,745,000    2,253,000  2,067,788 -> 2,034,392      0
    08         6360      4704  1.352     8464             2104      402,688     22   1,745,000    2,253,000  2,024,948 -> 2,051,684      0
    09         6065      4520  1.342     8176             2111      367,452     21   1,745,000    2,253,000  2,056,952 -> 2,038,200      0
    10         4545      3450  1.317     6611             2066      345,828     17   1,582,000    2,253,000  2,047,472 -> 2,041,476      0
    11         4534      3395  1.335     6619             2085      344,620     17   1,582,000    2,253,000  2,043,592 -> 2,048,604      0
    12         4883      3513  1.390     6918             2035      348,748     18   1,582,000    2,253,000  2,040,508 -> 2,031,208      0
    13         4193      3065  1.368     6217             2024      339,204     16   1,582,000    2,253,000  2,051,952 -> 2,034,224      0
    14        11226      7450  1.507    13251             2025      328,404     32   1,426,000    2,253,000  2,053,728 -> 2,062,316      0
    15        12659     10416  1.215    14711             2052      577,488     35   1,582,000    2,253,000  2,059,616 -> 2,031,312      0
    16         4860      3109  1.563     6925             2065      324,080     18   1,582,000    2,253,000  2,035,616 -> 2,024,972      0
    17         7178      4707  1.525     9317             2139      350,616     23   1,582,000    2,253,000  2,037,492 -> 2,036,160      0
    pass: first line start uptime 16590.48, last line end 16749.2, span 158.72 s
    MemAvailable low-water across the pass (before/after readings only): 2,014,792 kB (line 06)
    X1 min across the pass: 500,000 kHz = 17.84% of rated, lines/uptime [(4, '16616.33')]; A76 min 2,253,000
    peak RSS max 577,488 kB (line 15); rc all 0: True; kills total 0

**Row 4b, conditions.**
- Hand read before line 0: uptime 16414.17, 17:51:08, policy0 1,803,000 /
  policy4 2,253,000 / policy6 2,802,000 (all rated).
- Hand read after line 17: uptime 16561.22, 17:53:35, 1,803,000 / 2,253,000 /
  2,802,000 (all rated).
- The X1 ceiling first moved on line 02 (2,704,000). Its minimum was
  2,048,000 (73.09%) on lines 09 (uptime 16481.31) and 15 (16536.06), then
  2,188,000 on 16-17. The A76 ceiling never moved.
- policy0 was read by hand only, before and after, and read rated both times.
- All 18 rc=0; every .wall has five numeric fields; 0 kill lines; every
  script-stderr file (tts/p2-NN.sherr) is 0 B.

**Gate between the passes, by hand**, polling all three ceilings until rated, in
one invocation: start uptime 16581.12 (17:53:55), end 16581.20 (17:53:55),
**1 poll, 0.08 s**. All three were already rated, 19.9 s after 4b's hand read.

**Row 4c, conditions.**
- Hand read before line 0: uptime 16590.20, 17:54:04, 1,803,000 / 2,253,000 /
  2,802,000 (all rated).
- Hand read after line 17: uptime 16749.96, 17:56:43, policy0 1,803,000,
  policy4 2,253,000, **policy6 2,252,000 (80.37% of rated)**.
- The X1 ceiling moved on line 00 already (2,048,000). **Line 04 read
  500,000 kHz = 17.84% of rated at uptime 16616.33**, one 0.2 s poll sample
  near that line's end (the line ran 16609.08-16616.90); how long it held is
  not known. The pass then sat at 1,582,000 (56.46%) from line 10 to line 17,
  with 1,426,000 (50.89%) on line 14. The A76 ceiling never moved.
- **Unpinned means the scheduler chose the cores; which cores the four threads
  ran on was not recorded.** The X1 ceiling applies to cpus 6-7 whether or not
  any thread ran there.
- All 18 rc=0; every .wall has five numeric fields; 0 kill lines; every
  tts/p4-NN.sherr is 0 B.

**Stated as fact, not judged.** Elapsed on 4c exceeds 4b on all 18 lines.
Sample counts are identical between the passes on all 18 lines, and p2-00/05/15
are byte-identical to p4-00/05/15.

**The three WAVs**, pulled to ~/kokoro-models/phone-6a-rung1/ (sha256 by
shasum on the Mac and sha256sum on the phone, each MATCH):

    p2-00.wav   4d7c10b9844e86a345fbd45a158eca46a9d02b59fdf87449c0c942b12f6caca4
    p2-05.wav   5d6cf04333b87efdbf06b834c42070c458d2a9ca40f124c6b6b81da0375258d5
    p2-15.wav   c24121e107ca63f3c349b485450723ca85a430c41af8ee8a948f9cc7315b2244

    header (python wave; all 24 kHz mono 16-bit, "data" at 36)
    line  phone samples  Mac samples  difference
    00       19,899         19,812    +87 = +3.625 ms
    05      165,773        165,742    +31 = +1.292 ms
    15      249,994        250,017    -23 = -0.958 ms

p2-00.wav is byte-identical to ack-1.wav (4d7c10b9…).

**Stderr and text.** No line in any of the 36 .err files falls outside the
expected set (command echo, Number of threads, Elapsed, Audio duration, RTF,
"The text is", Saved to), so there were no warnings. "The text is:" shows every line
arrived intact, including "£12,480.50" and the apostrophes. The binary appends
its own "." in that log line. Lines named for a later pronunciation check,
**not listened to**:
- 04 "3:45 pm", "24th"
- 05 "£12,480.50", "17.5%"
- 07 "API", "502", "GitHub"
- 08 "OAuth", "MCP"
- 09 "CI"
- 12 read/read and record/record (heteronyms)
- 14 "2 o'clock"
- 11 speaks the word "Penny"

Line 14 is four sentences. Its RTF (1.331 on 4b, 1.507 on 4c) is above the
other long lines, and its peak RSS (328,580 kB) is below line 04's shorter
single sentence. That fits per-sentence generation (max_num_sentences 1), but
it is not shown here.

What this entry does not say: the prediction verdicts (the write-up);
anything from flash; what policy0 did during either pass; which cores 4c
used; how long 4c's 500,000 kHz reading lasted; MemAvailable between the
before/after reads of any line (the low-water figures are over those reads
only); anything about pronunciation (not listened to); anything on a fresh
boot. Nothing here is a baseline.

## 2026-09-18 — TTS RUNG 1, WRITE-UP: it runs on the 6a, and it is slower than predicted — RTF 1.067-1.570 on every one of 41 rows, none under 1.0. P-T5 HELD; P-T2, P-T3 and P-T4 MISSED; P-T1 NOT JUDGED. Product assumption: 2 threads pinned to the X1 pair.

Branch tts-kokoro. All rung 1 rows ran on **boot 4**, which had already carried
S0, S1, S2 and eight model loads. Every figure is "on the 6a". Binary
sherpa-onnx-offline-tts bd7d26e8… (sherpa-onnx a5b4a94 + the WEBSOCKET=OFF line,
android-24, NDK r30), libonnxruntime.so 33847ad4…, model.int8.onnx a089794d…,
sid 22, lang en, lexicon-gb-en.txt.

**(1) The tables, as committed; not re-pasted.**
- Row 4a, "On it." ×5, c0, 2 threads, cool-gated: notes-tts.md:685-690.
- Row 4b, the 18 lines, c0, 2 threads, back to back: notes-tts.md:766-785.
- Row 4c, the 18 lines, unpinned, 4 threads, back to back: notes-tts.md:791-810.

**(2) The predictions (notes-tts.md:32-47), each judged against the figure named.**

- **P-T1, derived load 0.8-2.0 s from flash: NOT JUDGED.** The model was
  page-cached on every row (pushed at 17:42, before the first row), so no load
  from flash was measured.
- **P-T2, generate RTF 0.3-0.8 on the X1 pair at 2 threads: MISSED.**
  4a 1.324-1.401; 4b 1.067 (line 05) to 1.373 (line 16); 4c 1.174 (line 05)
  to 1.570 (line 00). **Lines under 1.0: 0 of 5, 0 of 18, 0 of 18.** Lines
  inside 0.3-0.8: none.
  (The sub-claim "short lines worse": 4b lines 00-03 read 1.266-1.365, against
  1.067-1.143 on the single-sentence lines of 4.5 s or more. Line 16, at 3.1 s
  of audio, read 1.373, and line 14, multi-sentence, read 1.331.)
- **P-T3, peak RSS 250-450 MB: MISSED.** The range across all 41 rows was
  283,656 kB (p2-01, 277.0 MiB) to 577,488 kB (p4-15, 564.0 MiB). kB here is
  /proc's KiB, and "MB" is read as MiB (450 MiB = 460,800 kB), as the repo
  does elsewhere. On that reading, the band is left **only by line 15, at
  10.416 s of audio** (577,228 kB on 4b and 577,488 on 4c). The largest
  in-band row is line 05, at 6.907 s (441,496 kB). If "MB" is read as 10^6
  bytes, line 05 (439,460 / 441,496 kB = 450.0 / 452.1 MB) also leaves it.
  MISSED either way.
- **P-T4, no clock descent within one 18-line pass: MISSED.** The 4b X1
  ceiling (policy6) fell to **2,048,000 kHz (73.09% of 2,802,000) at uptime
  16481.31** (line 09), and again on line 15 (16536.06). It first moved on
  line 02. policy4 held at 2,253,000 throughout. (4c: X1 to 500,000 in one
  sample at 16616.33, then 1,582,000 from line 10.)
- **P-T5, sample counts within 5 ms of the Mac: HELD.** Line 00 +3.625 ms
  (+87), line 05 +1.292 ms (+31), line 15 -0.958 ms (-23).

**(3) The product assumption: 2 threads pinned to the X1 pair (c0).** Elapsed
on 4c (unpinned, 4 threads) exceeded 4b on **18 of 18 lines**, so 4 threads
unpinned is not "clearly faster". **Caveat, stated:** 4c ran after 4b on the
same package, and its X1 ceiling sat lower (1,582,000 from line 10, against
2,048,000-2,188,000 on 4b), so the comparison is not controlled for heat.
It does not show that 4 threads is slower on a cool chip.

**(4) Derived load with the model cached is 1,875-2,139 ms across 41 rows**
(wall minus elapsed, which includes process start and WAV write). That bears
on rung 2's reload-on-wake threshold (cold start ≤ 1.5 s), and does not
answer it: rung 2 is resident vs reload, and none of these rows is a load
from flash.

**(5) For Matt to listen to** (pronunciation is out of scope for this brief;
unchanged from the 4b/4c entry). The WAVs on the Mac are
~/kokoro-models/phone-6a-rung1/p2-00.wav, p2-05.wav, p2-15.wav and ack-1.wav.
- 04 "3:45 pm", "24th"
- 05 "£12,480.50", "17.5%"
- 07 "API", "502", "GitHub"
- 08 "OAuth", "MCP"
- 09 "CI"
- 12 read/read and record/record (heteronyms)
- 14 "2 o'clock"
- 11 speaks the word "Penny"

**The phone as left**, one wrapped invocation: uptime_s=17039.79,
2026-09-18 18:01:33 BST, MemAvailable 2,078,096 kB, SwapFree 811,612 kB,
policy0 1,803,000, policy4 2,253,000, policy6 2,802,000 (all rated), battery
temp 301 dC. /data/local/tmp/tts/ holds 47 entries: the binary,
libonnxruntime.so, penny-kokoro-int8/, pennytts.sh, help.txt, out/ (246 files)
and 41 *.sherr files (all 0 B). It is left exactly as it is.
**Nothing outside /data/local/tmp/tts/ was created, changed or deleted from this
branch.** The /data/local/tmp listing is the one read at 17:06, plus tts/.

**(6) What rung 1 does NOT say.**
- Nothing about load from flash: the model was page-cached on every row.
- Nothing about resident RSS: every row is a fresh process (rung 2).
- Nothing about TTS beside the LLM (rung 3).
- Nothing about the app, AudioTrack or time-to-first-audio (rung 4).
- Nothing about a fresh boot: all of it ran on spent boot 4.
- Nothing about policy0 during a row: it was read by hand before and after
  each pass only, and read rated every time.
- Nothing about which cores 4c's threads used.
- Nothing about pronunciation: not listened to.
- Nothing about the 7a.
