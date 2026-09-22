// pennyspeak2.cpp -- pennyspeak.cpp (6bce6d7a..., brief X) plus a COUNTING
// progress callback, for brief Y (stage 3a step 4), penny-app-spike, 22 Sept 2026.
//
// WHAT IS NEW IN pennyspeak2 -- `diff -u pennyspeak.cpp pennyspeak2.cpp` shows
// all of it; everything else is pennyspeak.cpp unchanged:
//   - on_progress() is passed to SherpaOnnxOfflineTtsGenerateWithConfig in
//     place of NULL, NULL. Kokoro calls it once after each sentence's Process()
//     (offline-tts-kokoro-impl.h:308-326, batch size fixed at 1, :284), AFTER
//     that sentence's samples are already appended to the line (:316-318), so
//     it cannot change the audio. The block at :335-347 never runs with batch
//     size 1, so callbacks per line = sentences per line.
//   - on_progress() stores the CLOCK_MONOTONIC time, n and p of each call in a
//     fixed 64-slot array, and sums n. It prints nothing and reads no file.
//     THE ONLY CODE ADDED INSIDE THE TIMED CALL is on_progress() itself: one
//     clock_gettime(CLOCK_MONOTONIC) and a few stores per sentence, plus the C
//     API's wrapper lambda around it (c-api.cc:1925-1929).
//   - After the call returns, one "event=chunk" record per stored callback,
//     and five new keys in the event=line record (before text=, which stays
//     last because it is free text): n_callbacks, cb_overflow, cb_samples_sum,
//     cb_sum_eq_n, first_audio_ms. The done record gains cb_overflow_lines.
//   - build-pennyspeak2.sh builds it into build/pennyspeak2/; nothing of
//     pennyspeak's is rebuilt or overwritten.
//
// ---- pennyspeak.cpp's header, unchanged below, except where it said no
// ---- callback is passed (marked "pennyspeak2:") ----
//
// pennyspeak.cpp -- a RESIDENT Kokoro for brief X (stage 3a step 3),
// penny-app-spike, 21 Sept 2026.
//
// WHY THIS EXISTS. sherpa-onnx-offline-tts takes ONE text per process
// (sherpa-onnx/csrc/sherpa-onnx-offline-tts.cc:159-165 at a5b4a94), so every
// line of briefs V and W paid a fresh model load, and every "Elapsed" was the
// first inference of a new process. This program creates the TTS engine ONCE
// and speaks the requested lines in sequence in the same process.
//
// It includes sherpa-onnx's C API header and nothing else from sherpa-onnx --
// no internal csrc/ header. The C API library was NOT built on 18 Sept
// (build-android-arm64-v8a/CMakeCache.txt:685, SHERPA_ONNX_ENABLE_C_API=OFF),
// so build-pennyspeak.sh compiles the clone's own c-api/c-api.cc, UNMODIFIED,
// beside this file and links both against the libraries the CLI linked.
//
// THE SAME WORK AS THE CLI. SherpaOnnxOfflineTtsGenerateWithConfig calls the
// same sherpa_onnx::OfflineTts::Generate the CLI calls (c-api.cc:1713; CLI
// :225), and SherpaOnnxWriteWave is sherpa_onnx::WriteWave with the same
// arguments (c-api.cc:2026-2029; CLI :249). The CLI's progress callback does
// not change the audio (offline-tts-kokoro-impl.h: it runs after the samples
// are appended, and only its return value is read), so none is passed here.
// pennyspeak2: one IS passed now -- on_progress(), below -- for exactly that
// reason: it cannot change the audio, provided it always returns 1.
// c-api.cc:53 turns a zero or NULL field into a default; NOTHING here relies on
// that -- after memset, every value the CLI run has is set explicitly below,
// each with the CLI flag or CLI default it stands for.
//
// THE MARKS. clock_gettime(CLOCK_MONOTONIC), and NOTHING between each pair --
// no printf, no /proc read, no WAV write, no destroy:
//
//   load_ms   t0; SherpaOnnxCreateOfflineTts();              t1
//   gen_ms    t0; SherpaOnnxOfflineTtsGenerateWithConfig();  t1
//
// pennyspeak2: on_progress() runs INSIDE the gen_ms pair, once per sentence
// (see the top of this file). Its own clock reads are the chunk times.
//
// CLOCK_BOOTTIME -- the clock /proc/uptime reads -- is stamped OUTSIDE each
// pair (before t0, after t1) as up_before / up_after, so a clock-ceiling
// minimum the wrapper records at uptime X can be placed inside a line.
// wav_ms is timed separately around SherpaOnnxWriteWave and is never part of
// gen_ms.
//
// THE TIMING WINDOW IS NOT THE CLI'S. gen_ms includes the C API copying the
// samples into a new buffer (c-api.cc:1719-1722); the CLI's "Elapsed" does
// not, but it does include the CLI's printf callback (CLI :17-21) and is
// truncated to whole milliseconds (CLI :236-239). Every resident-vs-fresh
// comparison carries this caveat.
//
// MEMORY. VmRSS, VmHWM, RssAnon and RssFile from /proc/self/status, and
// MemAvailable from /proc/meminfo, read at start, after load, after each line
// -- AFTER that line's WAV write and its
// SherpaOnnxDestroyOfflineTtsGeneratedAudio -- and after the final
// SherpaOnnxDestroyOfflineTts. -1 means the read failed.
//
// PINNING IS NOT DONE HERE. The wrapper runs this under taskset, as
// pennytts.sh runs the CLI.
//
// usage:  pennyspeak <modeldir> <modelfile> <threads> <outdir> <tag> [lines] [idle_ms]
//           lines    "all" (default) = 0,1,...,17 in order, or a comma list of
//                    0-17, e.g. "0,4" or "4,0". No repeats (a repeat would
//                    overwrite its own WAV).
//           idle_ms  sleep between lines, default 0; not after the last line.
//         WAVs are written to <outdir>/<tag>_NN.wav, NN = the line number.
// e.g.    pennyspeak /data/local/tmp/tts/penny-kokoro-fp32 model.fp32.onnx 2 \
//             /data/local/tmp/tts/out 7a_tts_xsmoke_fp32 0,4
//
// OUTPUT. One "PENNYSPEAK event=..." key=value line per event on stdout,
// flushed as it is written: start, config, load, line (one per line spoken),
// destroy, done.
//
// EXIT CODES. 0 all lines spoken and written; 2 bad arguments; 3 the engine
// could not be created; 4 at least one generate returned NULL; 5 at least one
// WAV write failed (4 takes precedence over 5). A failed line is recorded,
// named in the done record, and the pass carries on with the next line.

#include "sherpa-onnx/c-api/c-api.h"

#include <errno.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <time.h>
#include <unistd.h>

#define N_LINES 18

// BEGIN LINES -- byte for byte the 18 texts in pennytts.sh's case block,
// lines 0-17 in order (from ~/kokoro-models/abtest_sherpa.py). One per source
// line, so both lists can be extracted and diffed.
static const char *const kLines[N_LINES] = {
    "On it.",
    "Done.",
    "Yes?",
    "One second.",
    "Your call is at 3:45 pm on Thursday the 24th of September.",
    "Revenue this month is £12,480.50, up 17.5% on August.",
    "That's 3 of 14 done, and the other 11 are due by Friday.",
    "The API returned a 502, so I retried the webhook and opened a pull request on GitHub.",
    "The OAuth token expired, so I've refreshed it and restarted the MCP server.",
    "CI is green on the staging branch, and the migration ran in under 4 seconds.",
    "Six separate sessions since Saturday suggest something's slipping.",
    "Penny picked the practical path and parked the rest for Thursday.",
    "I read the record this morning, and I'll record the read-through later.",
    "Do you want me to send it now, or wait until you've read it?",
    "Three things. First, the contract needs signing. Second, the invoice is overdue. Third, your 2 o'clock has moved to 4.",
    "I went through everything that came in overnight, and most of it can wait, but there's one email from the investor you spoke to last week that reads like a soft yes, so I'd reply to that before anything else.",
    "That's everything. Nothing else needs you tonight.",
    "Morning Matt. Two things need you today, and one of them can wait until after lunch.",
};
// END LINES

static double mono_ms(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (double) ts.tv_sec * 1000.0 + (double) ts.tv_nsec / 1e6;
}

// Seconds since boot on the clock /proc/uptime reads, without opening a file.
static double boot_s(void) {
    struct timespec ts;
    clock_gettime(CLOCK_BOOTTIME, &ts);
    return (double) ts.tv_sec + (double) ts.tv_nsec / 1e9;
}

static void sleep_ms(long ms) {
    if (ms <= 0) { return; }
    struct timespec ts;
    ts.tv_sec  = (time_t) (ms / 1000);
    ts.tv_nsec = (long) (ms % 1000) * 1000000L;
    while (nanosleep(&ts, &ts) == -1 && errno == EINTR) { }
}

// ---------------- pennyspeak2: THE PROGRESS CALLBACK ----------------
// One record per line, zeroed before the line's generate call. Times are
// ABSOLUTE mono_ms() values; the line's t0 is subtracted only after the call
// returns, so nothing is added between t0 and the call.
#define CB_SLOTS 64

struct cb_record {
    int32_t   count;             // every call, stored or not
    int32_t   overflow;          // 1 once count has exceeded CB_SLOTS
    long long samples_sum;       // n summed over EVERY call, stored or not
    double    t_abs[CB_SLOTS];   // mono_ms() at entry to the call
    int32_t   n[CB_SLOTS];
    float     p[CB_SLOTS];
};

// Called by Kokoro inside SherpaOnnxOfflineTtsGenerateWithConfig, once after
// each sentence (offline-tts-kokoro-impl.h:319-321). `samples` is NOT read:
// the audio is taken from the call's return value as in pennyspeak, and
// Kokoro frees this buffer when we return (:322-324).
//
// IT ALWAYS RETURNS 1, ON EVERY PATH, INCLUDING OVERFLOW. Kokoro's sentence
// loop runs only while the return value is nonzero (:308, :320): a 0 would
// stop generation after this sentence and the call would still return the
// sentences made so far -- a shorter line with a valid-looking WAV and
// status=OK. Nothing here may ever return anything else.
//
// No printf, no /proc read, no allocation: this runs inside the timed call.
// Past CB_SLOTS calls it stores nothing more (never writes beyond the arrays),
// sets overflow, and keeps counting and summing.
static int32_t on_progress(const float * samples, int32_t n, float p, void * arg) {
    (void) samples;
    const double t = mono_ms();
    struct cb_record * r = (struct cb_record *) arg;
    if (r->count < CB_SLOTS) {
        r->t_abs[r->count] = t;
        r->n[r->count]     = n;
        r->p[r->count]     = p;
    } else {
        r->overflow = 1;
    }
    r->count++;
    r->samples_sum += n;
    return 1;
}

struct mem_read {
    long vmrss;
    long vmhwm;
    long rssanon;
    long rssfile;
    long memavail;
};

// Each field stays -1 if its line is not found. The kernel pads these lines
// with a tab and spaces; a space in a scanf format matches any run of both.
static void read_mem(struct mem_read * m) {
    m->vmrss = m->vmhwm = m->rssanon = m->rssfile = m->memavail = -1;
    char line[256];
    long v;
    FILE * f = fopen("/proc/self/status", "r");
    if (f != NULL) {
        while (fgets(line, sizeof(line), f) != NULL) {
            if      (sscanf(line, "VmRSS: %ld",   &v) == 1) { m->vmrss   = v; }
            else if (sscanf(line, "VmHWM: %ld",   &v) == 1) { m->vmhwm   = v; }
            else if (sscanf(line, "RssAnon: %ld", &v) == 1) { m->rssanon = v; }
            else if (sscanf(line, "RssFile: %ld", &v) == 1) { m->rssfile = v; }
        }
        fclose(f);
    }
    f = fopen("/proc/meminfo", "r");
    if (f != NULL) {
        while (fgets(line, sizeof(line), f) != NULL) {
            if (sscanf(line, "MemAvailable: %ld", &v) == 1) { m->memavail = v; break; }
        }
        fclose(f);
    }
}

static void print_mem(const struct mem_read * m) {
    printf(" vmrss_kB=%ld vmhwm_kB=%ld rssanon_kB=%ld rssfile_kB=%ld memavail_kB=%ld",
           m->vmrss, m->vmhwm, m->rssanon, m->rssfile, m->memavail);
}

// Whole string must be a decimal integer in [lo, hi]; no sign games, no suffix.
static int parse_long(const char * s, long lo, long hi, long * out) {
    if (s == NULL || *s == '\0') { return 0; }
    for (const char * p = s; *p; p++) {
        if (*p < '0' || *p > '9') { return 0; }
    }
    errno = 0;
    char * end = NULL;
    const long v = strtol(s, &end, 10);
    if (errno != 0 || end == NULL || *end != '\0' || v < lo || v > hi) { return 0; }
    *out = v;
    return 1;
}

// "all" -> 0..17. Otherwise a comma list of 0-17, no empty items, no repeats.
static int parse_lines(const char * s, int * out, int * n_out) {
    int n = 0;
    if (strcmp(s, "all") == 0) {
        for (int i = 0; i < N_LINES; i++) { out[n++] = i; }
        *n_out = n;
        return 1;
    }
    char buf[256];
    if (strlen(s) >= sizeof(buf)) { return 0; }
    strcpy(buf, s);
    int seen[N_LINES] = {0};
    char * p = buf;
    for (;;) {
        char * comma = strchr(p, ',');
        if (comma != NULL) { *comma = '\0'; }
        long v;
        if (!parse_long(p, 0, N_LINES - 1, &v) || seen[v] || n >= N_LINES) { return 0; }
        seen[v] = 1;
        out[n++] = (int) v;
        if (comma == NULL) { break; }
        p = comma + 1;
    }
    *n_out = n;
    return 1;
}

static void usage(void) {
    fprintf(stderr,
            "usage: pennyspeak2 <modeldir> <modelfile> <threads> <outdir> <tag> [lines] [idle_ms]\n"
            "       threads >= 1; lines \"all\" or a comma list of 0-17 without repeats; idle_ms >= 0\n");
}

// Append ",N" (or "N" first) to a failed-lines list.
static void note_failed(char * list, size_t cap, int line) {
    const size_t used = strlen(list);
    snprintf(list + used, cap - used, "%s%d", used ? "," : "", line);
}

int main(int argc, char ** argv) {
    if (argc < 6 || argc > 8) { usage(); return 2; }

    const char * modeldir  = argv[1];
    const char * modelfile = argv[2];
    const char * outdir    = argv[4];
    const char * tag       = argv[5];
    const char * lines_arg = argc >= 7 ? argv[6] : "all";

    // A 0 would silently become 1 inside c-api.cc (GetNumThreads, :63-75).
    long threads = 0;
    if (!parse_long(argv[3], 1, 64, &threads)) { usage(); return 2; }

    int order[N_LINES];
    int n_order = 0;
    if (!parse_lines(lines_arg, order, &n_order)) { usage(); return 2; }

    long idle_ms = 0;
    if (argc == 8 && !parse_long(argv[7], 0, 600000, &idle_ms)) { usage(); return 2; }

    // The same five paths pennytts.sh passes, all under MODELDIR.
    char model[4096], voices[4096], tokens[4096], data_dir[4096], lexicon[4096];
    if (snprintf(model,    sizeof(model),    "%s/%s", modeldir, modelfile)            >= (int) sizeof(model)    ||
        snprintf(voices,   sizeof(voices),   "%s/voices.bin", modeldir)               >= (int) sizeof(voices)   ||
        snprintf(tokens,   sizeof(tokens),   "%s/tokens.txt", modeldir)               >= (int) sizeof(tokens)   ||
        snprintf(data_dir, sizeof(data_dir), "%s/espeak-ng-data", modeldir)           >= (int) sizeof(data_dir) ||
        snprintf(lexicon,  sizeof(lexicon),  "%s/lexicon-gb-en.txt", modeldir)        >= (int) sizeof(lexicon)) {
        usage(); return 2;
    }

    struct stat st;
    const long long model_bytes = stat(model, &st) == 0 ? (long long) st.st_size : -1;

    struct mem_read mem;
    read_mem(&mem);
    printf("PENNYSPEAK event=start tag=%s pid=%d threads=%ld lines=%s n_lines=%d idle_ms=%ld"
           " model=%s model_bytes=%lld outdir=%s sherpa_version=%s sherpa_git_sha1=%s onnxruntime=%s up_s=%.3f",
           tag, (int) getpid(), threads, lines_arg, n_order, idle_ms,
           model, model_bytes, outdir, SherpaOnnxGetVersionStr(), SherpaOnnxGetGitSha1(),
           SherpaOnnxGetOnnxruntimeVersionStr(), boot_s());
    print_mem(&mem);
    printf("\n");
    fflush(stdout);

    // ---------------- ENGINE CONFIG: every field the CLI run has, set explicitly ----------------
    SherpaOnnxOfflineTtsConfig config;
    memset(&config, 0, sizeof(config));
    config.model.kokoro.model        = model;      // --kokoro-model
    config.model.kokoro.voices       = voices;     // --kokoro-voices
    config.model.kokoro.tokens       = tokens;     // --kokoro-tokens
    config.model.kokoro.data_dir     = data_dir;   // --kokoro-data-dir
    config.model.kokoro.lexicon      = lexicon;    // --kokoro-lexicon
    config.model.kokoro.lang         = "en";       // --kokoro-lang=en
    config.model.kokoro.length_scale = 1.0f;       // CLI default, offline-tts-kokoro-model-config.h:28
    config.model.num_threads         = (int32_t) threads;  // --num-threads
    config.model.debug               = 0;          // CLI default false
    config.model.provider            = "cpu";      // CLI default, offline-tts-model-config.h:32
    config.max_num_sentences         = 1;          // CLI default, offline-tts.h:34
    config.silence_scale             = 0.2f;       // CLI default, offline-tts.h:39
    config.rule_fsts                 = "";         // CLI default empty
    config.rule_fars                 = "";         // CLI default empty

    // ---------------- GENERATION CONFIG ----------------
    SherpaOnnxGenerationConfig gen;
    memset(&gen, 0, sizeof(gen));
    gen.silence_scale         = 0.2f;   // CLI GenerationConfig default, offline-tts.h:68
    gen.speed                 = 1.0f;   // --speed=1.0
    gen.sid                   = 22;     // --sid=22
    gen.reference_audio       = NULL;   // CLI: not a reference-audio model
    gen.reference_audio_len   = 0;
    gen.reference_sample_rate = 0;
    gen.reference_text        = NULL;   // c-api.cc:1697 maps NULL to "", the CLI's empty string
    gen.num_steps             = 5;      // CLI GenerationConfig default, offline-tts.h:76
    gen.extra                 = NULL;   // CLI sets no extra for Kokoro (CLI :188-194)

    printf("PENNYSPEAK event=config provider=cpu num_threads=%ld debug=0 lang=en length_scale=1.0"
           " max_num_sentences=1 config_silence_scale=0.2 rule_fsts=\"\" rule_fars=\"\""
           " sid=22 speed=1.0 gen_silence_scale=0.2 num_steps=5 extra=NULL callback=on_progress cb_slots=64"
           " voices=%s tokens=%s data_dir=%s lexicon=%s\n",
           threads, voices, tokens, data_dir, lexicon);
    fflush(stdout);

    // ---------------- LOAD ----------------
    const double lup0 = boot_s();
    const double lt0  = mono_ms();
    const SherpaOnnxOfflineTts * tts = SherpaOnnxCreateOfflineTts(&config);
    const double lt1  = mono_ms();
    const double lup1 = boot_s();

    read_mem(&mem);
    if (tts == NULL) {
        printf("PENNYSPEAK event=load status=FAIL load_ms=%.3f up_before=%.3f up_after=%.3f",
               lt1 - lt0, lup0, lup1);
        print_mem(&mem);
        printf("\n");
        printf("PENNYSPEAK event=done rc=3 lines_ok=0 failed_gen_lines=none failed_wav_lines=none\n");
        fflush(stdout);
        return 3;
    }
    printf("PENNYSPEAK event=load status=OK load_ms=%.3f sample_rate=%d up_before=%.3f up_after=%.3f",
           lt1 - lt0, (int) SherpaOnnxOfflineTtsSampleRate(tts), lup0, lup1);
    print_mem(&mem);
    printf("\n");
    fflush(stdout);

    // pennyspeak2: for each chunk's audio_ms. Read once, outside every clock pair.
    const int32_t engine_sr = SherpaOnnxOfflineTtsSampleRate(tts);

    // ---------------- LINES ----------------
    char failed_gen[128] = "";
    char failed_wav[128] = "";
    char cb_overflow_lines[128] = "";   // pennyspeak2
    int lines_ok = 0;
    static struct cb_record cb;         // pennyspeak2: zeroed before every line

    for (int k = 0; k < n_order; k++) {
        const int n = order[k];
        char wav[4096];
        if (snprintf(wav, sizeof(wav), "%s/%s_%02d.wav", outdir, tag, n) >= (int) sizeof(wav)) {
            wav[0] = '\0';
        }

        memset(&cb, 0, sizeof(cb));     // pennyspeak2: before up0, outside the clock pair

        const double up0 = boot_s();
        const double t0  = mono_ms();
        const SherpaOnnxGeneratedAudio * audio =
            SherpaOnnxOfflineTtsGenerateWithConfig(tts, kLines[n], &gen, on_progress, &cb);
        const double t1  = mono_ms();
        const double up1 = boot_s();

        // pennyspeak2: time from t0 to the first callback's entry; -1 if none fired.
        const double first_audio_ms = cb.count > 0 ? cb.t_abs[0] - t0 : -1.0;
        if (cb.overflow) { note_failed(cb_overflow_lines, sizeof(cb_overflow_lines), n); }

        if (audio == NULL) {
            note_failed(failed_gen, sizeof(failed_gen), n);
            read_mem(&mem);
            printf("PENNYSPEAK event=line k=%d n=%d status=FAIL_GENERATE gen_ms=%.3f up_before=%.3f up_after=%.3f",
                   k + 1, n, t1 - t0, up0, up1);
            print_mem(&mem);
            printf(" n_callbacks=%d cb_overflow=%d cb_samples_sum=%lld cb_sum_eq_n=na first_audio_ms=%.3f",
                   (int) cb.count, (int) cb.overflow, cb.samples_sum, first_audio_ms);
            printf(" text=\"%s\"\n", kLines[n]);
            fflush(stdout);
        } else {
            const int32_t samples = audio->n;
            const int32_t sr      = audio->sample_rate;
            const double  audio_s = (double) samples / (double) sr;
            const double  gen_ms  = t1 - t0;

            // SherpaOnnxWriteWave returns 1 on success, 0 on failure (c-api.h:2799).
            const double w0 = mono_ms();
            const int32_t wav_ok = wav[0] ? SherpaOnnxWriteWave(audio->samples, samples, sr, wav) : 0;
            const double w1 = mono_ms();

            SherpaOnnxDestroyOfflineTtsGeneratedAudio(audio);
            audio = NULL;

            if (wav_ok == 1) { lines_ok++; } else { note_failed(failed_wav, sizeof(failed_wav), n); }

            read_mem(&mem);
            printf("PENNYSPEAK event=line k=%d n=%d status=%s gen_ms=%.3f audio_ms=%.3f samples=%d sample_rate=%d"
                   " rtf=%.4f up_before=%.3f up_after=%.3f wav_ok=%d wav_ms=%.3f wav=%s",
                   k + 1, n, wav_ok == 1 ? "OK" : "FAIL_WAV", gen_ms, audio_s * 1000.0, (int) samples, (int) sr,
                   gen_ms / (audio_s * 1000.0), up0, up1, (int) wav_ok, w1 - w0, wav[0] ? wav : "PATH_TOO_LONG");
            print_mem(&mem);
            printf(" n_callbacks=%d cb_overflow=%d cb_samples_sum=%lld cb_sum_eq_n=%d first_audio_ms=%.3f",
                   (int) cb.count, (int) cb.overflow, cb.samples_sum,
                   cb.samples_sum == (long long) samples ? 1 : 0, first_audio_ms);
            printf(" text=\"%s\"\n", kLines[n]);
            fflush(stdout);
        }

        // pennyspeak2: one record per STORED callback, after the call, outside
        // every clock pair. t_ms is from the line's t0; gap_ms is from the
        // previous callback (from t0 for the first); audio_ms is that chunk's
        // own duration at the engine's sample rate.
        const int32_t stored = cb.count < CB_SLOTS ? cb.count : CB_SLOTS;
        for (int32_t i = 0; i < stored; i++) {
            const double t_ms   = cb.t_abs[i] - t0;
            const double gap_ms = i == 0 ? t_ms : cb.t_abs[i] - cb.t_abs[i - 1];
            printf("PENNYSPEAK event=chunk k=%d n=%d cb=%d of=%d samples=%d p=%.6f t_ms=%.3f gap_ms=%.3f audio_ms=%.3f\n",
                   k + 1, n, (int) (i + 1), (int) cb.count, (int) cb.n[i], (double) cb.p[i], t_ms, gap_ms,
                   engine_sr > 0 ? (double) cb.n[i] * 1000.0 / (double) engine_sr : -1.0);
        }
        if (cb.overflow) {
            printf("PENNYSPEAK event=chunk_overflow k=%d n=%d stored=%d of=%d\n",
                   k + 1, n, (int) stored, (int) cb.count);
        }
        fflush(stdout);

        if (k + 1 < n_order) { sleep_ms(idle_ms); }
    }

    // ---------------- DESTROY ----------------
    SherpaOnnxDestroyOfflineTts(tts);
    tts = NULL;
    read_mem(&mem);
    printf("PENNYSPEAK event=destroy up_s=%.3f", boot_s());
    print_mem(&mem);
    printf("\n");

    const int rc = failed_gen[0] ? 4 : (failed_wav[0] ? 5 : 0);
    printf("PENNYSPEAK event=done rc=%d lines_ok=%d n_lines=%d failed_gen_lines=%s failed_wav_lines=%s"
           " cb_overflow_lines=%s\n",
           rc, lines_ok, n_order, failed_gen[0] ? failed_gen : "none", failed_wav[0] ? failed_wav : "none",
           cb_overflow_lines[0] ? cb_overflow_lines : "none");
    fflush(stdout);
    return rc;
}
