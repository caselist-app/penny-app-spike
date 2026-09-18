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
