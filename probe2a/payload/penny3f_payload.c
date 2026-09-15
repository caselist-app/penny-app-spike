/*
 * Rungs 3f and 3h, in ONE payload. The fifth .so in this APK; the four before
 * it are untouched and stay reproducible.
 *
 * WHY THEY SHARE A BUILD. There is no C compiler on the Mac. Every payload
 * rebuild costs a manual round trip — Matt opens the Terminal app on the
 * phone by hand, a port is forwarded, this file is compiled inside the Debian
 * guest and copied back. 3e-iii proved the way to pay that cost once: make
 * the payload a COMMAND SERVER, not a single-shot. The host connects once and
 * sends a sequence of commands down the same socket; the process keeps its
 * state between them. One build answered nine experiments there. This one has
 * to answer two whole rungs plus a loose end.
 *
 * WHAT IS BEING ASKED.
 *
 *   3f  IS THE GUEST CPU REAL? 3e-i was handed 8 vCPUs and 3e-ii and 3e-iii
 *       moved gigabytes through the guest — and not one payload in this repo
 *       has ever asked a processor to CALCULATE anything. Every one of them is
 *       single-threaded and does nothing but move bytes. "Can a model run in
 *       1792MB" splits into *is there room*, now answered exhaustively, and
 *       *is the processor any good*, never once asked.
 *
 *   3h  CAN A GIGABYTE BE PUSHED IN? The encrypted store is keyed to the VM,
 *       so the host cannot write it and the guest must. The only inbound
 *       channel is vsock, and its record is 32,000 bytes in a single shot
 *       (3c). A model has to get in somehow.
 *
 *   The loose end: 3e-iv verified the stored file's SIZE, not its CONTENT. A
 *   store handing back 1610612736 bytes of zeroes would have logged
 *   identically. CMD_VERIFY closes that.
 *
 * ── THE CONTROL, AND IT IS THE REASON THIS FILE IS SHAPED THE WAY IT IS ────
 *
 * A millisecond figure from inside a VM means nothing on its own. 3f needs a
 * comparison, and the comparison specified is the SAME C compiled by the SAME
 * gcc and run in the phone's own Debian guest — same silicon, same day, a
 * known-good Linux.
 *
 * "Same C" is usually a promise. Here it is literal: this ONE file builds
 * both binaries.
 *
 *     -DPENNY_CONTROL   a static Debian executable with a _start entry
 *     (default)         a microdroid payload .so with AVmPayload_main
 *
 * Nothing differs between them but the environment. Same source, same
 * compiler, same -O1, same flags, and — because this file has no C library in
 * EITHER build — the same raw aarch64 syscalls. The syscall ABI belongs to the
 * kernel and is identical above glibc, above bionic, and above nothing at all,
 * which is the same reason 2d's payload talks to the kernel directly.
 *
 * It is NOT a bare-metal control and must never be written up as one. Debian
 * here is itself a guest in a VM the Terminal app owns. It is a sanity number:
 * it would catch a microdroid guest running at a tenth of expected speed, and
 * that is all it is for.
 *
 * ── WHAT THE SCALING CURVE CAN AND CANNOT SHOW ─────────────────────────────
 *
 * This phone is a Pixel 6a: Google Tensor, 2x Cortex-X1, 2x Cortex-A76,
 * 4x Cortex-A55. Eight cores, THREE different kinds. So:
 *
 *   - Perfect 8x is not on the table and its absence is not a failure. Four of
 *     the eight cores are little ones at roughly a third of the throughput.
 *     Something in the region of 4-5x aggregate is what working hardware looks
 *     like here.
 *   - A SINGLE-thread figure is a lottery: the host scheduler may put it on an
 *     X1 or on an A55, and nothing in the guest can pin it. Run it more than
 *     once before believing it.
 *   - Therefore every thread reports its OWN start and end, not just the
 *     aggregate. A spread of per-thread times IS the heterogeneity, visible
 *     rather than inferred. A curve that flattens at 2 threads with all
 *     timings equal would mean something quite different — that
 *     CPU_TOPOLOGY_MATCH_HOST is a number in a config file rather than eight
 *     usable cores.
 *
 * ── THREADS WITH NO C LIBRARY ──────────────────────────────────────────────
 *
 * The hard part, as expected. There is no pthreads here and there is no libc
 * to hold one. A thread is made by hand:
 *
 *   1. mmap a stack. Never unmapped — a child sets its done flag and then
 *      calls exit(2), and freeing its stack in that window is a race worth
 *      nobody's time. Eight stacks of 1MB is 8MB of a 2GB guest.
 *   2. clone() through a written-out assembly trampoline, because the child
 *      comes back from the syscall on a brand new stack with no return
 *      address on it. A C function cannot survive that; it would return into
 *      nothing. The trampoline branches the child straight into its work and
 *      then into exit(), and never lets it reach a `ret`.
 *   3. Join by polling a shared done flag, not by futex. CLONE_CHILD_CLEARTID
 *      plus FUTEX_WAIT is the proper way and needs twice the code to be
 *      slightly faster at something that happens eight times per command.
 *
 * aarch64 puts clone's arguments in the CLONE_BACKWARDS order — flags, stack,
 * parent_tid, TLS, child_tid — which is NOT the x86-64 order. Getting that
 * wrong hands the kernel a garbage TLS pointer.
 *
 * No CLONE_SETTLS: the children inherit the parent's tpidr_el0 and never touch
 * thread-local storage, because there is none in a binary with no libc and
 * -fno-stack-protector. If that ever stops being true this is where it breaks.
 *
 * If microdroid's payload sandbox refuses clone outright, that is a real
 * finding and the errno is reported rather than swallowed.
 *
 * ── THE CHECKSUM IS NOT 3c's, DELIBERATELY ─────────────────────────────────
 *
 * 3c hashed 32,000 bytes with byte-at-a-time FNV-1a. That is a serial multiply
 * chain: roughly four cycles a byte, which over 1.5GB is several seconds on
 * BOTH ends and would be charged straight to 3h's throughput figure. So this
 * file hashes 64-bit WORDS — same FNV-1a construction, eight times fewer
 * rounds, still order-sensitive — and the result stays 64-bit. (This comment
 * said "folds to 32 bits at the end" until 15 Sept. It does not: every value
 * it has ever reported is 16 hex digits, e.g. ck64 0x757b795dd5138044.)
 *
 * It therefore does NOT produce 3c's or 3d's numbers and must not be compared
 * with them. It only ever has to agree with itself, at the two ends of a wire.
 *
 * ── STREAMING, AND THE FAILURE THAT LOOKS LIKE NOTHING ─────────────────────
 *
 * The interesting way 3h fails is the guest buffering the whole transfer in
 * RAM and hitting 3e-ii's live-lock: kswapd needing to allocate in order to
 * free, the VM hanging for a minute and a half, no exception, no `has died`
 * line, no reply. So this receiver holds ONE chunk at a time in .bss and
 * writes it straight through to the file. Nothing accumulates. If memory grows
 * anyway it is the page cache, which is reclaimable, and the MemFree line
 * logged every 64MB is what tells the two apart.
 *
 * ── WIRE FORMAT ────────────────────────────────────────────────────────────
 *
 * 3e-iii's framing with one more argument, because CMD_THREADS needs both an
 * iteration count and a thread count:
 *
 *      host -> guest   4 bytes BE length L      (L == 0 means "goodbye")
 *                      4 bytes BE command
 *                      4 bytes BE arg
 *                      4 bytes BE arg2
 *                      L-12 bytes of path, no terminator
 *
 *      guest -> host   4 bytes BE length (== 44), then eleven BE 32-bit
 *                      fields:
 *                       0 status
 *                       1 memFreeBeforeKB   the guest's own, not the host's
 *                       2 memFreeAfterKB
 *                       3 resultMB
 *                       4 ms
 *                       5 extra     errno on failure, else command-specific
 *                       6 ckHi      checksum >> 32
 *                       7 ckLo
 *                       8 sizeHi    bytes >> 32
 *                       9 sizeLo
 *                      10 aux       command-specific
 *
 * CMD_STREAM nests inside its own frame: after the command, the host sends
 *      4 bytes BE chunk length, then that many bytes, repeating, until a
 *      chunk length of 0. Then the guest replies as above.
 */

#ifndef PENNY_CONTROL
extern void AVmPayload_notifyPayloadReady(void);
#endif

/* ----------------------------------------------------------- the syscalls */

static long sys4(long nr, long a0, long a1, long a2, long a3) {
    register long x8 __asm__("x8") = nr;
    register long x0 __asm__("x0") = a0;
    register long x1 __asm__("x1") = a1;
    register long x2 __asm__("x2") = a2;
    register long x3 __asm__("x3") = a3;
    __asm__ volatile("svc #0"
                     : "+r"(x0)
                     : "r"(x8), "r"(x1), "r"(x2), "r"(x3)
                     : "memory", "cc");
    return x0;
}

/* mmap needs six argument slots; sys4 cannot reach x4/x5. */
static long sys6(long nr, long a0, long a1, long a2, long a3, long a4, long a5) {
    register long x8 __asm__("x8") = nr;
    register long x0 __asm__("x0") = a0;
    register long x1 __asm__("x1") = a1;
    register long x2 __asm__("x2") = a2;
    register long x3 __asm__("x3") = a3;
    register long x4 __asm__("x4") = a4;
    register long x5 __asm__("x5") = a5;
    __asm__ volatile("svc #0"
                     : "+r"(x0)
                     : "r"(x8), "r"(x1), "r"(x2), "r"(x3), "r"(x4), "r"(x5)
                     : "memory", "cc");
    return x0;
}

/* asm-generic/unistd.h — the arm64 table. */
#define SYS_openat        56
#define SYS_close         57
#define SYS_read          63
#define SYS_write         64
#define SYS_fsync         82
#define SYS_fstat         80
#define SYS_exit          93
#define SYS_exit_group    94
#define SYS_nanosleep    101
#define SYS_clock_gettime 113
#define SYS_socket       198
#define SYS_bind         200
#define SYS_listen       201
#define SYS_clone        220
#define SYS_mmap         222
#define SYS_accept4      242

#define AF_VSOCK        40
#define SOCK_STREAM     1
#define PORT            5555

#define PROT_READ       1
#define PROT_WRITE      2
#define MAP_PRIVATE     0x02
#define MAP_ANONYMOUS   0x20
#define AT_FDCWD        -100

#define O_RDONLY        0
#define O_WRONLY        1
#define O_CREAT         0100
#define O_TRUNC         01000

/* A thread, as the kernel understands one. See the header: aarch64 wants
 * these in CLONE_BACKWARDS order and there is deliberately no CLONE_SETTLS. */
#define CLONE_VM        0x00000100
#define CLONE_FS        0x00000200
#define CLONE_FILES     0x00000400
#define CLONE_SIGHAND   0x00000800
#define CLONE_THREAD    0x00010000
#define CLONE_SYSVSEM   0x00040000
#define THREAD_FLAGS (CLONE_VM | CLONE_FS | CLONE_FILES | CLONE_SIGHAND \
                      | CLONE_THREAD | CLONE_SYSVSEM)

#define MAX_THREADS     16
#define STACK_SIZE      (1UL * 1024UL * 1024UL)

/* One chunk in flight, and only one. See the streaming note in the header —
 * this buffer is the reason the guest cannot quietly buffer a gigabyte. */
#define WBUF            (1UL * 1024UL * 1024UL)
static unsigned char g_wbuf[WBUF];

/* ------------------------------------------------- memcpy/memset, by hand */

/*
 * gcc is entitled to turn a bounded copy or clear loop into a call to memcpy
 * or memset EVEN under -ffreestanding — the C standard requires them to exist
 * in a freestanding implementation, so the compiler assumes them. This binary
 * has no C library in either build, so such a call is an undefined symbol, and
 * in a .so that surfaces at dlopen inside the guest where nothing useful is
 * logged.
 *
 * The attribute is not decoration: without it gcc recognises the loop INSIDE
 * memcpy as a memcpy and rewrites it as a call to itself.
 */
__attribute__((used, optimize("no-tree-loop-distribute-patterns")))
void *memcpy(void *d, const void *s, unsigned long n) {
    unsigned char *a = d;
    const unsigned char *b = s;
    unsigned long i;
    for (i = 0; i < n; i++) {
        a[i] = b[i];
    }
    return d;
}

__attribute__((used, optimize("no-tree-loop-distribute-patterns")))
void *memset(void *d, int c, unsigned long n) {
    unsigned char *a = d;
    unsigned long i;
    for (i = 0; i < n; i++) {
        a[i] = (unsigned char) c;
    }
    return d;
}

/* ----------------------------------------------------------- console lines */

/* Each write() to fd 1 becomes its own console line — see the trap in
 * CLAUDE.md — so a line is assembled in a buffer and written once. */
static char g_line[512];

static void say(const char *s) {
    long n = 0;
    while (s[n]) {
        n++;
    }
    sys4(SYS_write, 1, (long) s, n, 0);
}

static int putstr(int at, const char *s) {
    while (*s && at < (int) sizeof g_line - 1) {
        g_line[at++] = *s++;
    }
    return at;
}

static int putu(int at, unsigned long v) {
    char d[24];
    int i = 23;
    d[i--] = 0;
    if (v == 0) {
        d[i--] = '0';
    }
    while (v > 0) {
        d[i--] = (char) ('0' + (v % 10));
        v /= 10;
    }
    return putstr(at, &d[i + 1]);
}

static int puthex(int at, unsigned long v) {
    const char *h = "0123456789abcdef";
    int i;
    for (i = 60; i >= 0; i -= 4) {
        if (at < (int) sizeof g_line - 1) {
            g_line[at++] = h[(v >> i) & 0xf];
        }
    }
    return at;
}

static void flushline(int at) {
    if (at < (int) sizeof g_line) {
        g_line[at++] = '\n';
    }
    sys4(SYS_write, 1, (long) g_line, at, 0);
}

static unsigned long now_ms(void) {
    long ts[2] = { 0, 0 };
    sys4(SYS_clock_gettime, 1 /* CLOCK_MONOTONIC */, (long) ts, 0, 0);
    return (unsigned long) ts[0] * 1000UL + (unsigned long) ts[1] / 1000000UL;
}

static void sleep_ms(unsigned long ms) {
    long ts[2];
    ts[0] = (long) (ms / 1000UL);
    ts[1] = (long) ((ms % 1000UL) * 1000000UL);
    sys4(SYS_nanosleep, (long) ts, 0, 0, 0);
}

/* ------------------------------------------------------------- /proc files */

static unsigned long meminfo_kb(const char *key) {
    char buf[2048];
    long fd, n, i, j;

    fd = sys4(SYS_openat, AT_FDCWD, (long) "/proc/meminfo", O_RDONLY, 0);
    if (fd < 0) {
        return 0;
    }
    n = sys4(SYS_read, fd, (long) buf, sizeof buf - 1, 0);
    sys4(SYS_close, fd, 0, 0, 0);
    if (n <= 0) {
        return 0;
    }
    buf[n] = 0;

    for (i = 0; i < n; i++) {
        if (i != 0 && buf[i - 1] != '\n') {
            continue;
        }
        for (j = 0; key[j]; j++) {
            if (buf[i + j] != key[j]) {
                break;
            }
        }
        if (key[j]) {
            continue;
        }
        i += j;
        while (i < n && (buf[i] == ' ' || buf[i] == '\t')) {
            i++;
        }
        {
            unsigned long v = 0;
            while (i < n && buf[i] >= '0' && buf[i] <= '9') {
                v = v * 10UL + (unsigned long) (buf[i] - '0');
                i++;
            }
            return v;
        }
    }
    return 0;
}

static void dump_file(const char *path, const char *tag) {
    char buf[8192];
    long fd, n, i, start;
    int at;

    fd = sys4(SYS_openat, AT_FDCWD, (long) path, O_RDONLY, 0);
    if (fd < 0) {
        at = putstr(0, "PENNY3F: ");
        at = putstr(at, path);
        at = putstr(at, " UNREADABLE errno ");
        at = putu(at, (unsigned long) -fd);
        flushline(at);
        return;
    }
    for (;;) {
        n = sys4(SYS_read, fd, (long) buf, sizeof buf - 1, 0);
        if (n <= 0) {
            break;
        }
        buf[n] = 0;
        start = 0;
        for (i = 0; i < n; i++) {
            if (buf[i] != '\n') {
                continue;
            }
            buf[i] = 0;
            at = putstr(0, "PENNY3F: ");
            at = putstr(at, tag);
            at = putstr(at, " ");
            at = putstr(at, &buf[start]);
            flushline(at);
            start = i + 1;
        }
    }
    sys4(SYS_close, fd, 0, 0, 0);
}

/* st_size sits at offset 48 in the arm64 (asm-generic) struct stat. */
static long file_size(long fd) {
    unsigned char st[144];
    long r = sys4(SYS_fstat, fd, (long) st, 0, 0);
    if (r < 0) {
        return r;
    }
    return *(long *) (st + 48);
}

/* ----------------------------------------------------------- the checksum */

/*
 * FNV-1a over 64-bit little-endian WORDS, not bytes. See the header: this is
 * NOT 3c's hash and its numbers must never be compared with 3c's or 3d's. It
 * exists so that hashing 1.5GB costs a fraction of a second at each end rather
 * than several seconds charged to 3h's throughput figure.
 */
#define FNV_BASIS 14695981039346656037UL
#define FNV_PRIME 1099511628211UL

static unsigned long ck_update(unsigned long h, const unsigned char *p,
                               unsigned long n) {
    unsigned long i = 0;
    while (i + 8UL <= n) {
        unsigned long w = (unsigned long) p[i]
                        | ((unsigned long) p[i + 1] << 8)
                        | ((unsigned long) p[i + 2] << 16)
                        | ((unsigned long) p[i + 3] << 24)
                        | ((unsigned long) p[i + 4] << 32)
                        | ((unsigned long) p[i + 5] << 40)
                        | ((unsigned long) p[i + 6] << 48)
                        | ((unsigned long) p[i + 7] << 56);
        h ^= w;
        h *= FNV_PRIME;
        i += 8UL;
    }
    while (i < n) {
        h ^= (unsigned long) p[i];
        h *= FNV_PRIME;
        i++;
    }
    return h;
}

/* -------------------------------------------------------- the CPU kernels */

/*
 * THESE TWO FUNCTIONS ARE THE ENTIRE POINT OF RUNG 3f. Everything else in
 * this file exists to run them and report a number.
 *
 * Both are dependency chains on purpose. A wide, independent loop would
 * measure how many execution ports the core has; a chain measures issue
 * latency, which is far harder for a hypervisor or an emulator to fake and is
 * the thing that would collapse if the guest's "CPU" were not a real one. It
 * also makes the two environments comparable without caring how the compiler
 * unrolled anything, because there is nothing to unroll into.
 *
 * `volatile` sinks below stop -O1 deleting the whole loop, which it is
 * entitled to do: neither function has any other observable effect.
 */

static unsigned long int_kernel(unsigned long iters) {
    unsigned long a = 0x9E3779B97F4A7C15UL;
    unsigned long b = 0x123456789ABCDEF0UL;
    unsigned long i;
    for (i = 0; i < iters; i++) {
        a += b;
        a ^= a >> 29;
        a *= 0xBF58476D1CE4E5B9UL;
        a ^= a >> 32;
        b = b * 6364136223846793005UL + 1442695040888963407UL;
    }
    return a ^ b;
}

/* Returns the bit pattern, never the value: converting a double to an integer
 * for printing is the one operation here that could pull in a libgcc helper,
 * and there is no libgcc in either build. A union costs nothing and removes
 * the question. */
static unsigned long fp_kernel(unsigned long iters) {
    union { double d; unsigned long u; } cast;
    double a = 1.0, b = 0.5, c = 0.25, d = 0.125;
    unsigned long i;
    for (i = 0; i < iters; i++) {
        a = a * 0.9999999 + 0.0000001;
        b = b * 1.0000001 - 0.0000001 * a;
        c = (c + a * b) * 0.9999998;
        d = d * 0.5 + c * 0.5;
    }
    cast.d = a + b + c + d;
    return cast.u;
}

static volatile unsigned long g_sink;

/* ------------------------------------------------------------- the threads */

/*
 * The clone trampoline. It cannot be C: the child returns from the syscall
 * with x0 == 0 and its stack pointer on a page that has never held a return
 * address, so a C function would `ret` into nothing. This branches the child
 * straight into its work and then into exit(), and never lets it reach a ret.
 *
 * On entry:  x0 = fn, x1 = arg, x2 = child stack top, x3 = flags
 * Returns:   the child's tid, or -errno, to the PARENT only.
 *
 * x9/x10 hold fn and arg across the svc. The aarch64 Linux syscall ABI
 * preserves everything but x0, which is what makes that safe — it is the same
 * trick glibc's own aarch64 clone uses.
 *
 * clone's argument order here is aarch64's: flags, stack, parent_tid, TLS,
 * child_tid. That is CLONE_BACKWARDS and it is NOT the x86-64 order.
 */
__asm__(
".text\n"
".globl penny_clone_thread\n"
".hidden penny_clone_thread\n"
".type penny_clone_thread, %function\n"
"penny_clone_thread:\n"
"    mov  x9,  x0\n"          /* fn  */
"    mov  x10, x1\n"          /* arg */
"    mov  x0,  x3\n"          /* flags      */
"    mov  x1,  x2\n"          /* child sp   */
"    mov  x2,  xzr\n"         /* parent_tid */
"    mov  x3,  xzr\n"         /* tls        */
"    mov  x4,  xzr\n"         /* child_tid  */
"    mov  x8,  #220\n"        /* __NR_clone */
"    svc  #0\n"
"    cbz  x0, 1f\n"
"    ret\n"                   /* parent: tid, or -errno */
"1:  mov  x0, x10\n"          /* child: fn(arg) */
"    blr  x9\n"
"    mov  x0, xzr\n"
"    mov  x8, #93\n"          /* __NR_exit — this thread only, not the group */
"    svc  #0\n"
"    brk  #0\n"               /* unreachable; traps rather than runs on */
".size penny_clone_thread, .-penny_clone_thread\n"
);

extern long penny_clone_thread(void (*fn)(void *), void *arg,
                               void *stack_top, long flags);

struct tjob {
    volatile unsigned long done;
    unsigned long iters;
    unsigned long kind;        /* 0 int, 1 fp */
    unsigned long result;
    unsigned long t0;
    unsigned long t1;
    unsigned long idx;
};

static struct tjob g_jobs[MAX_THREADS];

static void thread_entry(void *p) {
    struct tjob *j = (struct tjob *) p;
    j->t0 = now_ms();
    j->result = j->kind == 0 ? int_kernel(j->iters) : fp_kernel(j->iters);
    j->t1 = now_ms();
    /* aarch64 is weakly ordered, so the done flag could otherwise become
     * visible to the parent before the timings it is announcing. `volatile`
     * constrains the compiler and not the processor; the barrier is what makes
     * t0/t1/result safe to read once done is seen. */
    __asm__ volatile("dmb ish" ::: "memory");
    j->done = 1;
}

/* -------------------------------------------------------------- commands */

#define CMD_INFO        1
#define CMD_CPU_INT     2
#define CMD_CPU_FP      3
#define CMD_THREADS_INT 4
#define CMD_THREADS_FP  5
#define CMD_STREAM      6
#define CMD_VERIFY      7

#define ST_OK        0
#define ST_REFUSED   1
#define ST_MISMATCH  2
#define ST_OPEN      3
#define ST_WRITE     4
#define ST_BADCMD    5
#define ST_SHORT     6

struct reply {
    unsigned int status;
    unsigned int result_mb;
    unsigned int ms;
    unsigned int extra;
    unsigned long ck;
    unsigned long size;
    unsigned int aux;
};

static void reply_clear(struct reply *r) {
    r->status = ST_BADCMD;
    r->result_mb = 0;
    r->ms = 0;
    r->extra = 0;
    r->ck = 0;
    r->size = 0;
    r->aux = 0;
}

/*
 * Single-threaded bench. Reported straight, with no averaging and no warm-up
 * pass: a warm-up would hide exactly the thing a first measurement of a guest
 * CPU should show. Run it twice from the host instead, which is a line in the
 * plan rather than a decision baked into the binary.
 */
static void do_cpu(unsigned long miters, unsigned long kind, struct reply *r) {
    unsigned long iters = miters * 1000000UL;
    unsigned long t0, t1;
    int at;

    t0 = now_ms();
    g_sink = kind == 0 ? int_kernel(iters) : fp_kernel(iters);
    t1 = now_ms();

    r->status = ST_OK;
    r->ms = (unsigned int) (t1 - t0);
    r->aux = (unsigned int) miters;
    r->ck = g_sink;

    at = putstr(0, "PENNY3F: ");
    at = putstr(at, kind == 0 ? "INT" : "FP");
    at = putstr(at, " 1 thread ");
    at = putu(at, miters);
    at = putstr(at, "M iters in ");
    at = putu(at, t1 - t0);
    at = putstr(at, " ms, sink 0x");
    at = puthex(at, g_sink);
    flushline(at);
}

/*
 * The scaling half. Every thread runs the SAME iteration count, so ideal
 * hardware returns the SAME wall time whatever the thread count — the curve is
 * read as work-per-millisecond, not as a speed-up of a fixed job. That keeps
 * each thread's slice identical and makes the per-thread spread meaningful.
 *
 * Per-thread start and end are logged individually. On a 2+2+4 big.LITTLE part
 * that spread IS the answer to "are these eight real cores": eight threads with
 * eight near-identical times would be the surprise, not eight with a wide one.
 */
static void do_threads(unsigned long miters, unsigned long nthreads,
                       unsigned long kind, struct reply *r) {
    unsigned long iters = miters * 1000000UL;
    unsigned long t0, t1, i, started = 0;
    unsigned long slowest = 0, fastest = 0;
    int at;

    if (nthreads < 1) {
        nthreads = 1;
    }
    if (nthreads > MAX_THREADS) {
        nthreads = MAX_THREADS;
    }

    for (i = 0; i < nthreads; i++) {
        g_jobs[i].done = 0;
        g_jobs[i].iters = iters;
        g_jobs[i].kind = kind;
        g_jobs[i].result = 0;
        g_jobs[i].t0 = 0;
        g_jobs[i].t1 = 0;
        g_jobs[i].idx = i;
    }

    t0 = now_ms();
    for (i = 0; i < nthreads; i++) {
        long stack, tid;

        stack = sys6(SYS_mmap, 0, (long) STACK_SIZE, PROT_READ | PROT_WRITE,
                     MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
        if (stack < 0 && stack > -4096) {
            at = putstr(0, "PENNY3F: thread stack mmap REFUSED errno ");
            at = putu(at, (unsigned long) -stack);
            flushline(at);
            r->status = ST_REFUSED;
            r->extra = (unsigned int) -stack;
            break;
        }

        tid = penny_clone_thread(thread_entry, &g_jobs[i],
                                 (void *) (stack + (long) STACK_SIZE),
                                 THREAD_FLAGS);
        if (tid < 0 && tid > -4096) {
            /* A microdroid payload refusing clone would be a finding in its
             * own right, so the errno goes back rather than being swallowed. */
            at = putstr(0, "PENNY3F: clone REFUSED for thread ");
            at = putu(at, i);
            at = putstr(at, " errno ");
            at = putu(at, (unsigned long) -tid);
            flushline(at);
            r->status = ST_REFUSED;
            r->extra = (unsigned int) -tid;
            break;
        }
        started++;
    }

    /* Join by polling. Any thread that was started must be waited for even if
     * a later clone failed, or its stack would be reused under it. */
    for (;;) {
        unsigned long fin = 0;
        for (i = 0; i < started; i++) {
            fin += g_jobs[i].done;
        }
        if (fin >= started) {
            __asm__ volatile("dmb ish" ::: "memory");
            break;
        }
        sleep_ms(1);
    }
    t1 = now_ms();

    for (i = 0; i < started; i++) {
        unsigned long ms = g_jobs[i].t1 - g_jobs[i].t0;
        if (i == 0 || ms > slowest) {
            slowest = ms;
        }
        if (i == 0 || ms < fastest) {
            fastest = ms;
        }
        g_sink += g_jobs[i].result;

        at = putstr(0, "PENNY3F: thread ");
        at = putu(at, i);
        at = putstr(at, "/");
        at = putu(at, started);
        at = putstr(at, " ");
        at = putstr(at, kind == 0 ? "INT" : "FP");
        at = putstr(at, " ");
        at = putu(at, miters);
        at = putstr(at, "M iters in ");
        at = putu(at, ms);
        at = putstr(at, " ms");
        flushline(at);
    }

    if (r->status != ST_REFUSED) {
        r->status = ST_OK;
    }
    r->ms = (unsigned int) (t1 - t0);
    r->extra = r->status == ST_REFUSED ? r->extra : (unsigned int) started;
    r->aux = (unsigned int) miters;
    r->ck = g_sink;
    r->size = slowest;             /* reused: the slowest thread, in ms */
    r->result_mb = (unsigned int) fastest;  /* reused: the fastest, in ms */

    at = putstr(0, "PENNY3F: ");
    at = putu(at, started);
    at = putstr(at, " threads ");
    at = putstr(at, kind == 0 ? "INT" : "FP");
    at = putstr(at, " ");
    at = putu(at, miters);
    at = putstr(at, "M iters each, wall ");
    at = putu(at, t1 - t0);
    at = putstr(at, " ms, fastest ");
    at = putu(at, fastest);
    at = putstr(at, " slowest ");
    at = putu(at, slowest);
    at = putstr(at, " ms");
    flushline(at);
}

/* ---------------------------------------------------------------- sockets */

static long readn(long fd, unsigned char *p, unsigned long n) {
    unsigned long got = 0;
    while (got < n) {
        long r = sys4(SYS_read, fd, (long) (p + got), (long) (n - got), 0);
        if (r <= 0) {
            return r == 0 ? (long) got : r;
        }
        got += (unsigned long) r;
    }
    return (long) got;
}

static long writen(long fd, const unsigned char *p, unsigned long n) {
    unsigned long put = 0;
    while (put < n) {
        long r = sys4(SYS_write, fd, (long) (p + put), (long) (n - put), 0);
        if (r <= 0) {
            return r;
        }
        put += (unsigned long) r;
    }
    return (long) put;
}

static void put_be32(unsigned char *p, unsigned int v) {
    p[0] = (unsigned char) (v >> 24);
    p[1] = (unsigned char) (v >> 16);
    p[2] = (unsigned char) (v >> 8);
    p[3] = (unsigned char) v;
}

static unsigned int get_be32(const unsigned char *p) {
    return ((unsigned int) p[0] << 24) | ((unsigned int) p[1] << 16)
         | ((unsigned int) p[2] << 8)  | (unsigned int) p[3];
}

/*
 * RUNG 3h. Receive a stream and write it straight to a file.
 *
 * One chunk in flight, ever. The bytes land in g_wbuf, are hashed, are written
 * to the fd, and the buffer is reused. Nothing is accumulated, which is the
 * design decision the whole rung rests on: the failure worth fearing is the
 * guest holding the transfer in RAM and live-locking in zram with no error and
 * no reply, and this shape makes that impossible to do by accident.
 *
 * MemFree is logged every 64MB. Page cache growth is expected and is fine —
 * clean file-backed pages are reclaimable. MemFree falling and staying down
 * while the file grows would be the bad shape.
 */
static void do_stream(long cfd, const char *path, struct reply *r) {
    unsigned long h = FNV_BASIS, total = 0, t0, t1, next_log = 64UL << 20;
    unsigned char lhdr[4];
    long fd, wr;
    int at;

    fd = sys4(SYS_openat, AT_FDCWD, (long) path,
              O_WRONLY | O_CREAT | O_TRUNC, 0600);
    if (fd < 0) {
        /* The channel still has a whole stream queued on it. There is no way
         * to decline politely mid-frame, so the connection is left to the
         * caller to tear down and the host reads this status. */
        r->status = ST_OPEN;
        r->extra = (unsigned int) -fd;
        at = putstr(0, "PENNY3F: stream open '");
        at = putstr(at, path);
        at = putstr(at, "' FAILED errno ");
        at = putu(at, (unsigned long) -fd);
        flushline(at);
        return;
    }

    at = putstr(0, "PENNY3F: streaming into ");
    at = putstr(at, path);
    flushline(at);

    t0 = now_ms();
    for (;;) {
        unsigned int clen;

        if (readn(cfd, lhdr, 4) != 4) {
            r->status = ST_SHORT;
            say("PENNY3F: stream ended without a terminator\n");
            break;
        }
        clen = get_be32(lhdr);
        if (clen == 0) {
            r->status = ST_OK;
            break;
        }
        if (clen > WBUF) {
            r->status = ST_SHORT;
            say("PENNY3F: stream chunk larger than the receive buffer\n");
            break;
        }
        if (readn(cfd, g_wbuf, clen) != (long) clen) {
            r->status = ST_SHORT;
            say("PENNY3F: short read inside a stream chunk\n");
            break;
        }

        h = ck_update(h, g_wbuf, clen);

        wr = writen(fd, g_wbuf, clen);
        if (wr != (long) clen) {
            r->status = ST_WRITE;
            r->extra = (unsigned int) (wr < 0 ? -wr : 0);
            say("PENNY3F: write to the store FAILED\n");
            break;
        }
        total += clen;

        if (total >= next_log) {
            next_log += 64UL << 20;
            at = putstr(0, "PENNY3F: received ");
            at = putu(at, total >> 20);
            at = putstr(at, " MB, guest MemFree ");
            at = putu(at, meminfo_kb("MemFree:"));
            at = putstr(at, " kB Cached ");
            at = putu(at, meminfo_kb("Cached:"));
            at = putstr(at, " kB");
            flushline(at);
        }
    }

    /* fsync so that "arrived" means committed to the store rather than merely
     * queued in the guest's page cache. It is inside the timing on purpose:
     * a throughput figure that excludes the write-back is not a figure for
     * getting a model onto a disk. */
    sys4(SYS_fsync, fd, 0, 0, 0);
    t1 = now_ms();
    sys4(SYS_close, fd, 0, 0, 0);

    r->ms = (unsigned int) (t1 - t0);
    r->ck = h;
    r->size = total;
    r->result_mb = (unsigned int) (total >> 20);

    at = putstr(0, "PENNY3F: stream DONE ");
    at = putu(at, total);
    at = putstr(at, " bytes, ck64 0x");
    at = puthex(at, h);
    at = putstr(at, ", ");
    at = putu(at, t1 - t0);
    at = putstr(at, " ms");
    flushline(at);
}

/*
 * The loose end from 3e-iv. It proved a file of exactly 1610612736 bytes
 * survives a reboot and that every page faults in — but it never looked at
 * what was IN them, so a store handing back a gigabyte and a half of zeroes
 * would have logged identically. This reads the bytes and hashes them.
 */
static void do_verify(const char *path, struct reply *r) {
    unsigned long h = FNV_BASIS, total = 0, t0, t1;
    long fd, sz, n;
    int at;

    fd = sys4(SYS_openat, AT_FDCWD, (long) path, O_RDONLY, 0);
    if (fd < 0) {
        r->status = ST_OPEN;
        r->extra = (unsigned int) -fd;
        at = putstr(0, "PENNY3F: verify open '");
        at = putstr(at, path);
        at = putstr(at, "' FAILED errno ");
        at = putu(at, (unsigned long) -fd);
        flushline(at);
        return;
    }
    sz = file_size(fd);

    t0 = now_ms();
    for (;;) {
        n = sys4(SYS_read, fd, (long) g_wbuf, (long) WBUF, 0);
        if (n < 0) {
            r->status = ST_WRITE;
            r->extra = (unsigned int) -n;
            break;
        }
        if (n == 0) {
            r->status = ST_OK;
            break;
        }
        h = ck_update(h, g_wbuf, (unsigned long) n);
        total += (unsigned long) n;
    }
    t1 = now_ms();
    sys4(SYS_close, fd, 0, 0, 0);

    r->ms = (unsigned int) (t1 - t0);
    r->ck = h;
    r->size = total;
    r->result_mb = (unsigned int) (total >> 20);
    r->aux = (unsigned int) (sz > 0 && (unsigned long) sz != total ? 1 : 0);

    at = putstr(0, "PENNY3F: verify ");
    at = putstr(at, path);
    at = putstr(at, " read ");
    at = putu(at, total);
    at = putstr(at, " bytes (stat said ");
    at = putu(at, (unsigned long) (sz < 0 ? 0 : sz));
    at = putstr(at, "), ck64 0x");
    at = puthex(at, h);
    at = putstr(at, ", ");
    at = putu(at, t1 - t0);
    at = putstr(at, " ms");
    flushline(at);
}

/* ------------------------------------------------------- the command loop */

static unsigned char g_req[1024];
static char g_path[768];

static void run_command(long cfd, unsigned int cmd, unsigned int arg,
                        unsigned int arg2, const char *path, struct reply *r) {
    reply_clear(r);
    switch (cmd) {
    case CMD_INFO:
        dump_file("/proc/cpuinfo", "cpu");
        dump_file("/proc/mounts", "mount");
        dump_file("/proc/meminfo", "mem");
        r->status = ST_OK;
        break;
    case CMD_CPU_INT:
        do_cpu(arg, 0, r);
        break;
    case CMD_CPU_FP:
        do_cpu(arg, 1, r);
        break;
    case CMD_THREADS_INT:
        do_threads(arg, arg2, 0, r);
        break;
    case CMD_THREADS_FP:
        do_threads(arg, arg2, 1, r);
        break;
    case CMD_STREAM:
        do_stream(cfd, path, r);
        break;
    case CMD_VERIFY:
        do_verify(path, r);
        break;
    default:
        say("PENNY3F: unknown command\n");
        break;
    }
}

#ifndef PENNY_CONTROL

int AVmPayload_main(void) {
    long fd, cfd;
    unsigned char sa[16];
    unsigned char hdr[48];
    int i;

    say("PENNY3F: payload up inside the guest\n");

    fd = sys4(SYS_socket, AF_VSOCK, SOCK_STREAM, 0, 0);
    if (fd < 0) {
        say("PENNY3F: socket(AF_VSOCK) FAILED\n");
        return 10;
    }
    for (i = 0; i < 16; i++) {
        sa[i] = 0;
    }
    sa[0] = (unsigned char) (AF_VSOCK & 0xff);
    sa[1] = (unsigned char) ((AF_VSOCK >> 8) & 0xff);
    sa[4] = (unsigned char) (PORT & 0xff);
    sa[5] = (unsigned char) ((PORT >> 8) & 0xff);
    sa[6] = (unsigned char) ((PORT >> 16) & 0xff);
    sa[7] = (unsigned char) ((PORT >> 24) & 0xff);
    sa[8] = sa[9] = sa[10] = sa[11] = 0xff;      /* VMADDR_CID_ANY */

    if (sys4(SYS_bind, fd, (long) sa, 16, 0) < 0) {
        say("PENNY3F: bind FAILED\n");
        return 11;
    }
    if (sys4(SYS_listen, fd, 4, 0, 0) < 0) {
        say("PENNY3F: listen FAILED\n");
        return 12;
    }

    /* After listen(), never before — 3c's ordering, for 3c's reason: the host
     * must not be able to connect before there is something to accept it. */
    AVmPayload_notifyPayloadReady();
    say("PENNY3F: listening on vsock 5555, waiting for commands\n");

    cfd = sys4(SYS_accept4, fd, 0, 0, 0);
    if (cfd < 0) {
        say("PENNY3F: accept FAILED\n");
        return 13;
    }
    say("PENNY3F: host connected\n");

    for (;;) {
        unsigned int len, cmd, arg, arg2;
        unsigned long before, after;
        struct reply r;
        int at;

        if (readn(cfd, hdr, 4) != 4) {
            say("PENNY3F: channel closed without a goodbye\n");
            break;
        }
        len = get_be32(hdr);
        if (len == 0) {
            say("PENNY3F: host said goodbye\n");
            break;
        }
        if (len < 12 || len > sizeof g_req) {
            say("PENNY3F: malformed request length\n");
            break;
        }
        if (readn(cfd, g_req, len) != (long) len) {
            say("PENNY3F: short read on the request body\n");
            break;
        }
        cmd  = get_be32(g_req);
        arg  = get_be32(g_req + 4);
        arg2 = get_be32(g_req + 8);

        {
            unsigned int plen = len - 12;
            if (plen > sizeof g_path - 1) {
                plen = sizeof g_path - 1;
            }
            for (i = 0; i < (int) plen; i++) {
                g_path[i] = (char) g_req[12 + i];
            }
            g_path[plen] = 0;
        }

        at = putstr(0, "PENNY3F: CMD ");
        at = putu(at, cmd);
        at = putstr(at, " arg ");
        at = putu(at, arg);
        at = putstr(at, " arg2 ");
        at = putu(at, arg2);
        at = putstr(at, " path '");
        at = putstr(at, g_path);
        at = putstr(at, "'");
        flushline(at);

        before = meminfo_kb("MemFree:");
        run_command(cfd, cmd, arg, arg2, g_path, &r);
        after = meminfo_kb("MemFree:");

        at = putstr(0, "PENNY3F: DONE cmd ");
        at = putu(at, cmd);
        at = putstr(at, " status ");
        at = putu(at, r.status);
        at = putstr(at, " ms ");
        at = putu(at, r.ms);
        at = putstr(at, ", guest MemFree ");
        at = putu(at, before);
        at = putstr(at, " -> ");
        at = putu(at, after);
        at = putstr(at, " kB");
        flushline(at);

        put_be32(hdr, 44);
        put_be32(hdr + 4,  r.status);
        put_be32(hdr + 8,  (unsigned int) before);
        put_be32(hdr + 12, (unsigned int) after);
        put_be32(hdr + 16, r.result_mb);
        put_be32(hdr + 20, r.ms);
        put_be32(hdr + 24, r.extra);
        put_be32(hdr + 28, (unsigned int) (r.ck >> 32));
        put_be32(hdr + 32, (unsigned int) r.ck);
        put_be32(hdr + 36, (unsigned int) (r.size >> 32));
        put_be32(hdr + 40, (unsigned int) r.size);
        put_be32(hdr + 44, r.aux);
        if (writen(cfd, hdr, 48) != 48) {
            say("PENNY3F: short write on the reply\n");
            break;
        }
    }

    sys4(SYS_close, cfd, 0, 0, 0);
    say("PENNY3F: exiting 46\n");

    /* A beat before returning, so the last console lines are flushed through
     * the guest's console pipe before the VM tears itself down. 2d's lesson. */
    sleep_ms(1000);
    return 46;
}

#else   /* PENNY_CONTROL — the Debian half of rung 3f's comparison */

/*
 * THE CONTROL BINARY. Same file, same compiler, same flags, same syscalls,
 * same kernels — only the operating system underneath is different. That is
 * the strongest form this comparison can take without a second toolchain.
 *
 * It is static and has no C library, so the entry point is _start and the
 * kernel hands it a raw stack with argc at the top. A C function cannot read
 * that directly — its prologue moves sp before the body runs — so _start is
 * three instructions of assembly that pass the original sp along.
 *
 * Usage, inside the Debian guest:
 *      ./penny3f_control int 200
 *      ./penny3f_control fp 200
 *      ./penny3f_control threads 200 8
 */

__asm__(
".text\n"
".globl _start\n"
".type _start, %function\n"
"_start:\n"
"    mov  x0, sp\n"
"    b    penny_control_main\n"
".size _start, .-_start\n"
);

static unsigned long atou(const char *s) {
    unsigned long v = 0;
    if (!s) {
        return 0;
    }
    while (*s >= '0' && *s <= '9') {
        v = v * 10UL + (unsigned long) (*s - '0');
        s++;
    }
    return v;
}

void penny_control_main(unsigned long *sp);

void penny_control_main(unsigned long *sp) {
    long argc = (long) sp[0];
    char **argv = (char **) (sp + 1);
    const char *what = argc > 1 ? argv[1] : "int";
    unsigned long miters = argc > 2 ? atou(argv[2]) : 200UL;
    unsigned long nthr = argc > 3 ? atou(argv[3]) : 1UL;
    struct reply r;

    if (miters == 0) {
        miters = 200UL;
    }

    say("PENNY3F-CONTROL: same source, same gcc, same flags, Debian guest\n");

    reply_clear(&r);
    if (what[0] == 'f') {
        do_cpu(miters, 1, &r);
    } else if (what[0] == 't') {
        do_threads(miters, nthr ? nthr : 1UL, 0, &r);
    } else if (what[0] == 'T') {
        do_threads(miters, nthr ? nthr : 1UL, 1, &r);
    } else {
        do_cpu(miters, 0, &r);
    }

    sys4(SYS_exit_group, 0, 0, 0, 0);
}

#endif
