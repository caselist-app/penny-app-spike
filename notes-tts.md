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
