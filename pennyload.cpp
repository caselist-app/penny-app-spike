// pennyload.cpp -- the instrument for Q-A (cold-load time) and Q-B (time to
// first token with a cached prefix), penny-app-spike, 16 Sept 2026.
//
// WHY THIS EXISTS. llama-bench excludes load time from its t/s and has no
// state save or load. llama-simple accepts only -m, -n and -ngl: it never sets
// n_threads (so 4, not the pinned 2), derives n_ctx and n_batch from the prompt
// (so not the pinned 1024), never sets load_mode (so mmap, not the protocol's
// -lm none), and its printed "load time" is OVERWRITTEN at
// llama-context.cpp:742 to cover process start through the first eval, so it
// splits into none of the three phases Q-A asks for. Neither binary on the
// phone can meet the conditions this protocol already pins. That, and only
// that, is why a third one exists.
//
// It links llama.h and nothing else -- no `common` -- exactly as
// examples/simple does, against the static libraries already built in
// ~/Documents/llama.cpp/build-android at 16 Sept 12:08. The llama.cpp tree is
// NOT modified and stays at commit 38a5b42d9.
//
// THE MARKS. clock_gettime(CLOCK_MONOTONIC) throughout.
//
//   T0   first statement of main()
//   T1   after ggml_backend_load_all()
//   T2   FIRST progress_callback, progress == 0.0
//          -- llama-model-loader.cpp:1625-1627 fires at the TOP of the
//             per-tensor loop, BEFORE that tensor is read, so the first call
//             marks header/hparams/vocab/alloc done and tensor data starting
//   T3   LAST progress_callback, progress == 1.0  (llama-model-loader.cpp:1785)
//   T4   after llama_model_load_from_file() returns
//   T5   after llama_init_from_model() returns          <- READY TO GENERATE
//   T6   after llama_tokenize()
//   T7   after llama_state_load_file()                  (--load-state only)
//   T9a  after the SYSTEM-PROMPT llama_decode()  (fresh runs)
//          == T7 on cached runs
//   T8   after llama_state_save_file()                  (--save-state only),
//          sits between T9a and T9b
//   T9b  after the USER-TURN llama_decode()
//   T10  after llama_sampler_sample()                   <- FIRST TOKEN EXISTS
//
// THE FOUR DEFINITIONS, fixed before any row runs:
//
//   B4   state save cost        = T8  - T9a
//   TTFT resident, fresh        = T10 - T6
//   TTFT resident, cached       = T10 - T5
//   B3   cold process, cached   = T10 - T0
//
// On a --load-state run the system prompt is NOT tokenised, because T6 falls
// inside the TTFT-resident-cached window and a real cached path would not do
// that work. The restored token count comes from the state file instead.
//
// NO CHAT TEMPLATE IS APPLIED. Both prompt files are tokenised verbatim. The
// tokenize flags used for each are printed in every row.

#include "llama.h"

#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <cstdint>
#include <string>
#include <vector>
#include <ctime>
#include <sys/stat.h>

static double now_ms(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (double) ts.tv_sec * 1000.0 + (double) ts.tv_nsec / 1e6;
}

struct load_marks {
    double first;   // T2
    double last;    // T3
    int    calls;
};

static bool progress_cb(float progress, void * user_data) {
    (void) progress;
    load_marks * m = (load_marks *) user_data;
    const double t = now_ms();
    if (m->calls == 0) {
        m->first = t;
    }
    m->last = t;
    m->calls++;
    return true;
}

static bool read_file(const char * path, std::string & out) {
    FILE * f = fopen(path, "rb");
    if (f == NULL) {
        return false;
    }
    fseek(f, 0, SEEK_END);
    const long n = ftell(f);
    fseek(f, 0, SEEK_SET);
    if (n < 0) { fclose(f); return false; }
    out.resize((size_t) n);
    const size_t rd = n > 0 ? fread(&out[0], 1, (size_t) n, f) : 0;
    fclose(f);
    return rd == (size_t) n;
}

static long file_bytes(const char * path) {
    struct stat st;
    if (stat(path, &st) != 0) {
        return -1;
    }
    return (long) st.st_size;
}

static std::vector<llama_token> tokenize(const llama_vocab * vocab, const std::string & text,
                                         bool add_special, bool parse_special) {
    const int n = -llama_tokenize(vocab, text.c_str(), (int32_t) text.size(),
                                  NULL, 0, add_special, parse_special);
    std::vector<llama_token> out((size_t) (n > 0 ? n : 0));
    if (n > 0) {
        if (llama_tokenize(vocab, text.c_str(), (int32_t) text.size(),
                           out.data(), (int32_t) out.size(), add_special, parse_special) < 0) {
            out.clear();
        }
    }
    return out;
}

// FNV-1a over the 32-bit token ids, so a 64-token sequence can be compared
// between a fresh run and a cached run by one value as well as by the list.
static uint64_t fnv1a_tokens(const std::vector<llama_token> & ids) {
    uint64_t h = 1469598103934665603ULL;
    for (size_t i = 0; i < ids.size(); i++) {
        const uint32_t v = (uint32_t) ids[i];
        for (int b = 0; b < 4; b++) {
            h ^= (uint64_t) ((v >> (8 * b)) & 0xff);
            h *= 1099511628211ULL;
        }
    }
    return h;
}

static void usage(const char * argv0) {
    fprintf(stderr,
        "usage: %s -m <model.gguf> [options]\n"
        "  -t <n>              n_threads and n_threads_batch   (default 2)\n"
        "  -c <n>              n_ctx                           (default 1024)\n"
        "  -b <n>              n_batch                         (default 2048)\n"
        "  -ub <n>             n_ubatch                        (default 512)\n"
        "  -lm <mode>          auto|none|mmap|mlock|mmap+mlock|dio (default none)\n"
        "  --extra-bufts <0|1> use_extra_bufts (weight repack)  (default 1)\n"
        "  -n <n>              TOTAL tokens to generate        (default 1)\n"
        "  --sys-file <path>   system prompt, read verbatim\n"
        "  --user-file <path>  user turn, read verbatim\n"
        "  --save-state <path> write state after the system prompt is decoded\n"
        "  --load-state <path> read state instead of decoding the system prompt\n"
        "  --print             echo generated text after a '--- text ---' line\n"
        "  --tag <s>           label echoed into every output line\n",
        argv0);
}

int main(int argc, char ** argv) {
    const double T0 = now_ms();

    const char * model_path = NULL;
    const char * sys_path   = NULL;
    const char * user_path  = NULL;
    const char * save_path  = NULL;
    const char * load_path  = NULL;
    const char * tag        = "untagged";
    const char * lm_str     = "none";
    int  n_threads    = 2;
    int  n_ctx        = 1024;
    int  n_batch      = 2048;
    int  n_ubatch     = 512;
    int  n_gen        = 1;
    int  extra_bufts  = 1;
    bool do_print     = false;

    for (int i = 1; i < argc; i++) {
        const char * a = argv[i];
        if      (strcmp(a, "-m")  == 0 && i + 1 < argc) { model_path = argv[++i]; }
        else if (strcmp(a, "-t")  == 0 && i + 1 < argc) { n_threads  = atoi(argv[++i]); }
        else if (strcmp(a, "-c")  == 0 && i + 1 < argc) { n_ctx      = atoi(argv[++i]); }
        else if (strcmp(a, "-b")  == 0 && i + 1 < argc) { n_batch    = atoi(argv[++i]); }
        else if (strcmp(a, "-ub") == 0 && i + 1 < argc) { n_ubatch   = atoi(argv[++i]); }
        else if (strcmp(a, "-n")  == 0 && i + 1 < argc) { n_gen      = atoi(argv[++i]); }
        else if (strcmp(a, "-lm") == 0 && i + 1 < argc) { lm_str     = argv[++i]; }
        else if (strcmp(a, "--extra-bufts") == 0 && i + 1 < argc) { extra_bufts = atoi(argv[++i]); }
        else if (strcmp(a, "--sys-file")    == 0 && i + 1 < argc) { sys_path    = argv[++i]; }
        else if (strcmp(a, "--user-file")   == 0 && i + 1 < argc) { user_path   = argv[++i]; }
        else if (strcmp(a, "--save-state")  == 0 && i + 1 < argc) { save_path   = argv[++i]; }
        else if (strcmp(a, "--load-state")  == 0 && i + 1 < argc) { load_path   = argv[++i]; }
        else if (strcmp(a, "--tag")         == 0 && i + 1 < argc) { tag         = argv[++i]; }
        else if (strcmp(a, "--print")       == 0) { do_print = true; }
        else { fprintf(stderr, "pennyload: unknown or incomplete argument '%s'\n", a); usage(argv[0]); return 2; }
    }

    if (model_path == NULL || user_path == NULL) {
        fprintf(stderr, "pennyload: -m and --user-file are required\n");
        usage(argv[0]);
        return 2;
    }
    if (load_path == NULL && sys_path == NULL) {
        fprintf(stderr, "pennyload: --sys-file is required unless --load-state is given\n");
        return 2;
    }
    if (n_gen < 1) { n_gen = 1; }

    // Tokenize flags, fixed here and printed with every row so a later reader
    // does not have to guess which prompt got a BOS.
    const bool SYS_ADD_SPECIAL   = true;
    const bool SYS_PARSE_SPECIAL = true;
    const bool USR_ADD_SPECIAL   = false;
    const bool USR_PARSE_SPECIAL = true;

    std::string sys_text;
    std::string user_text;
    if (sys_path != NULL && load_path == NULL) {
        if (!read_file(sys_path, sys_text)) { fprintf(stderr, "pennyload: cannot read %s\n", sys_path); return 1; }
    }
    if (!read_file(user_path, user_text)) { fprintf(stderr, "pennyload: cannot read %s\n", user_path); return 1; }

    ggml_backend_load_all();
    const double T1 = now_ms();

    load_marks marks;
    marks.first = -1.0;
    marks.last  = -1.0;
    marks.calls = 0;

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers               = 99;   // matches llama-bench's default; CPU-only here
    mparams.load_mode                  = llama_load_mode_from_str(lm_str);
    mparams.use_extra_bufts            = extra_bufts != 0;
    mparams.progress_callback          = progress_cb;
    mparams.progress_callback_user_data = &marks;

    llama_model * model = llama_model_load_from_file(model_path, mparams);
    const double T4 = now_ms();
    if (model == NULL) { fprintf(stderr, "pennyload: failed to load model\n"); return 1; }

    const double T2 = marks.calls > 0 ? marks.first : T1;
    const double T3 = marks.calls > 0 ? marks.last  : T4;

    const llama_vocab * vocab = llama_model_get_vocab(model);

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx           = (uint32_t) n_ctx;
    cparams.n_batch         = (uint32_t) n_batch;
    cparams.n_ubatch        = (uint32_t) n_ubatch;
    cparams.n_threads       = n_threads;
    cparams.n_threads_batch = n_threads;

    llama_context * ctx = llama_init_from_model(model, cparams);
    const double T5 = now_ms();              // <- READY TO GENERATE
    if (ctx == NULL) { fprintf(stderr, "pennyload: failed to create context\n"); return 1; }

    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    llama_sampler * smpl = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(smpl, llama_sampler_init_greedy());

    // --- T6: tokenize. On a cached run the system prompt is deliberately NOT
    //     tokenised: T6 falls inside the TTFT-resident-cached window and a real
    //     cached path would not redo that work.
    std::vector<llama_token> sys_tokens;
    if (load_path == NULL) {
        sys_tokens = tokenize(vocab, sys_text, SYS_ADD_SPECIAL, SYS_PARSE_SPECIAL);
    }
    std::vector<llama_token> user_tokens = tokenize(vocab, user_text, USR_ADD_SPECIAL, USR_PARSE_SPECIAL);
    const double T6 = now_ms();
    if (user_tokens.empty()) { fprintf(stderr, "pennyload: user turn tokenised to nothing\n"); return 1; }

    double T7  = -1.0;
    double T9a = -1.0;
    double T8  = -1.0;
    long   state_bytes       = -1;
    size_t state_tokens_read = 0;

    if (load_path != NULL) {
        state_bytes = file_bytes(load_path);
        std::vector<llama_token> restored((size_t) n_ctx);
        const bool ok = llama_state_load_file(ctx, load_path, restored.data(), restored.size(), &state_tokens_read);
        T7 = now_ms();
        if (!ok) {
            printf("PENNYLOAD tag=%s FAILED state_load=false path=%s\n", tag, load_path);
            fflush(stdout);
            fprintf(stderr, "pennyload: llama_state_load_file returned false\n");
            return 3;
        }
        T9a = T7;   // on a cached run the state load IS the prefix step
    } else {
        if (sys_tokens.empty()) { fprintf(stderr, "pennyload: system prompt tokenised to nothing\n"); return 1; }
        llama_batch sysb = llama_batch_get_one(sys_tokens.data(), (int32_t) sys_tokens.size());
        if (llama_decode(ctx, sysb) != 0) { fprintf(stderr, "pennyload: system-prompt decode failed\n"); return 1; }
        T9a = now_ms();

        if (save_path != NULL) {
            const bool ok = llama_state_save_file(ctx, save_path, sys_tokens.data(), sys_tokens.size());
            T8 = now_ms();
            if (!ok) {
                printf("PENNYLOAD tag=%s FAILED state_save=false path=%s\n", tag, save_path);
                fflush(stdout);
                fprintf(stderr, "pennyload: llama_state_save_file returned false\n");
                return 3;
            }
            state_bytes = file_bytes(save_path);
        }
    }

    // --- T9b: the user turn. Position needs no arithmetic: llama_batch_get_one
    //     leaves batch.pos null (llama-batch.cpp:931-943) and llama-batch.cpp:100
    //     then sets p0 = memory->seq_pos_max(s) + 1 from the restored memory.
    llama_batch usrb = llama_batch_get_one(user_tokens.data(), (int32_t) user_tokens.size());
    if (llama_decode(ctx, usrb) != 0) { fprintf(stderr, "pennyload: user-turn decode failed\n"); return 1; }
    const double T9b = now_ms();

    llama_token first_id = llama_sampler_sample(smpl, ctx, -1);
    const double T10 = now_ms();             // <- FIRST TOKEN EXISTS

    // --- generation. Text is buffered, never written inside a timed window.
    std::vector<llama_token> gen_ids;
    std::string gen_text;
    char piece[256];

    llama_token cur = first_id;
    for (int i = 0; i < n_gen; i++) {
        gen_ids.push_back(cur);
        const int np = llama_token_to_piece(vocab, cur, piece, sizeof(piece), 0, true);
        if (np > 0) { gen_text.append(piece, (size_t) np); }
        if (llama_vocab_is_eog(vocab, cur)) { break; }
        if (i + 1 >= n_gen) { break; }
        llama_batch nb = llama_batch_get_one(&cur, 1);
        if (llama_decode(ctx, nb) != 0) { fprintf(stderr, "pennyload: generation decode failed\n"); break; }
        cur = llama_sampler_sample(smpl, ctx, -1);
    }
    const double T_gen_end = now_ms();

    // ---------------- REPORT ----------------
    const long model_bytes = file_bytes(model_path);

    printf("PENNYLOAD tag=%s run_type=%s rc=0\n", tag,
           load_path != NULL ? "cached" : (save_path != NULL ? "fresh+save" : "fresh"));
    printf("PENNYLOAD model=%s model_bytes=%ld\n", model_path, model_bytes);
    printf("PENNYLOAD threads=%d n_ctx=%d n_batch=%d n_ubatch=%d n_gpu_layers=99\n",
           n_threads, n_ctx, n_batch, n_ubatch);
    printf("PENNYLOAD load_mode=%s extra_bufts=%d sampler=greedy\n",
           llama_load_mode_name(mparams.load_mode), extra_bufts);
    printf("PENNYLOAD chat_template=NONE   (both prompt files tokenised verbatim; no template applied)\n");
    printf("PENNYLOAD sys_file=%s sys_bytes=%ld sys_tokens=%d sys_add_special=%d sys_parse_special=%d\n",
           sys_path != NULL ? sys_path : "(none)",
           sys_path != NULL ? file_bytes(sys_path) : -1,
           load_path != NULL ? -1 : (int) sys_tokens.size(),
           SYS_ADD_SPECIAL ? 1 : 0, SYS_PARSE_SPECIAL ? 1 : 0);
    printf("PENNYLOAD user_file=%s user_bytes=%ld user_tokens=%d user_add_special=%d user_parse_special=%d\n",
           user_path, file_bytes(user_path), (int) user_tokens.size(),
           USR_ADD_SPECIAL ? 1 : 0, USR_PARSE_SPECIAL ? 1 : 0);
    printf("PENNYLOAD progress_calls=%d\n", marks.calls);

    printf("PENNYLOAD t_backend_ms      %.2f   (T1-T0,  ggml_backend_load_all)\n",        T1  - T0);
    printf("PENNYLOAD t_model_open_ms   %.2f   (T2-T1,  header+hparams+vocab+alloc)\n",   T2  - T1);
    printf("PENNYLOAD t_tensor_band_ms  %.2f   (T3-T2,  tensor data read + repack)\n",    T3  - T2);
    printf("PENNYLOAD t_model_tail_ms   %.2f   (T4-T3)\n",                                T4  - T3);
    printf("PENNYLOAD t_model_total_ms  %.2f   (T4-T1,  llama_model_load_from_file)\n",   T4  - T1);
    printf("PENNYLOAD t_ctx_create_ms   %.2f   (T5-T4,  llama_init_from_model)\n",        T5  - T4);
    printf("PENNYLOAD t_ready_ms        %.2f   (T5-T0,  READY TO GENERATE)\n",            T5  - T0);
    printf("PENNYLOAD t_tokenize_ms     %.2f   (T6-T5)\n",                                T6  - T5);
    if (load_path != NULL) {
        printf("PENNYLOAD t_state_load_ms   %.2f   (T7-T6)\n", T7 - T6);
        printf("PENNYLOAD state_file=%s state_bytes=%ld state_tokens_restored=%d\n",
               load_path, state_bytes, (int) state_tokens_read);
    } else {
        printf("PENNYLOAD t_state_load_ms   n/a    (fresh run)\n");
        printf("PENNYLOAD t_sys_decode_ms   %.2f   (T9a-T6, system-prompt llama_decode)\n", T9a - T6);
    }
    if (save_path != NULL) {
        printf("PENNYLOAD t_state_save_ms   %.2f   (T8-T9a) == B4\n", T8 - T9a);
        printf("PENNYLOAD state_file=%s state_bytes=%ld state_tokens_saved=%d\n",
               save_path, state_bytes, (int) sys_tokens.size());
    } else if (load_path == NULL) {
        printf("PENNYLOAD t_state_save_ms   n/a    (no --save-state)\n");
        printf("PENNYLOAD state_file=(none) state_bytes=-1\n");
    }
    printf("PENNYLOAD t_user_decode_ms  %.2f   (T9b-%s, user-turn llama_decode)\n",
           T9b - (T8 > 0 ? T8 : T9a), T8 > 0 ? "T8" : "T9a");
    printf("PENNYLOAD t_sample_ms       %.2f   (T10-T9b, llama_sampler_sample)\n",        T10 - T9b);
    printf("PENNYLOAD ttft_fresh_ms     %.2f   (T10-T6,  TTFT resident FRESH)\n",         T10 - T6);
    printf("PENNYLOAD ttft_cached_ms    %.2f   (T10-T5,  TTFT resident CACHED)\n",        T10 - T5);
    printf("PENNYLOAD ttft_cold_proc_ms %.2f   (T10-T0,  cold process) == B3\n",          T10 - T0);

    const double gen_ms = T_gen_end - T10;
    printf("PENNYLOAD gen_tokens=%d gen_ms=%.2f gen_tps=%.2f\n",
           (int) gen_ids.size(), gen_ms,
           gen_ids.size() > 1 && gen_ms > 0 ? (double) (gen_ids.size() - 1) * 1000.0 / gen_ms : 0.0);
    printf("PENNYLOAD first_token_id=%d\n", (int) first_id);
    printf("PENNYLOAD token_fnv1a64=0x%016llx\n", (unsigned long long) fnv1a_tokens(gen_ids));
    printf("PENNYLOAD token_ids=");
    for (size_t i = 0; i < gen_ids.size(); i++) { printf("%s%d", i ? "," : "", (int) gen_ids[i]); }
    printf("\n");

    if (do_print) {
        printf("--- text ---\n");
        printf("%s\n", gen_text.c_str());
    }
    fflush(stdout);

    llama_sampler_free(smpl);
    llama_free(ctx);
    llama_model_free(model);
    return 0;
}
