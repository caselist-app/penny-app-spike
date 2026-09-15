/*
 * Rung 3e-ii: is the memory REAL?
 *
 * WHAT 3e-i LEFT OPEN. 3e-i proved microdroid will hand a sideloaded app a
 * VM configured for 2048MB and 8 vCPUs, and that nothing in the platform
 * refuses. It did NOT prove the guest can use that memory. On the first 2GB
 * run the host surrendered only ~660MB of the 2048MB granted, because Linux
 * hands out address space eagerly and pages lazily: nothing has ever asked
 * this guest for the other 1.4GB. Until something does, "the VM has 2GB" is
 * a number in a config file, not a fact about memory. This file asks.
 *
 * THE MEASUREMENT. mmap 16MB at a time, fill every byte of it, read every
 * byte back and check it, and keep it mapped. Report how far it got.
 *
 *   - mmap() succeeding proves NOTHING. Linux overcommits by default; a
 *     successful mmap of 2GB on a phone with 300MB free is normal and means
 *     only that the address space was reserved. The page does not exist
 *     until it is written to. So every page is written.
 *   - Reading it back is not paranoia. The two ways this can fail quietly
 *     are a page that was never really supplied and a page that was swapped
 *     out and came back wrong. Both present as correct data on the write
 *     pass and wrong data on the read pass.
 *   - The mapping is deliberately NOT unmapped between chunks. The question
 *     is how much the guest can hold AT ONCE, so it has to be cumulative.
 *
 * THE ZRAM TRAP, AND WHY THE PATTERN IS PSEUDO-RANDOM. This is the one
 * decision in this file that would invalidate the result if it were got
 * wrong, and it is recorded here because a log cannot tell you afterwards.
 * microdroid builds a zram swap device sized to the guest's whole RAM —
 * 3e-i read `Adding 2038096k swap on /dev/block/zram0` out of the 2GB
 * guest's own console. zram is COMPRESSED swap held in the same RAM. So if
 * this payload filled its pages with zeros, or with a repeating byte, the
 * guest kernel could push 1.5GB of it into a few megabytes of zram and the
 * payload would sail to 2GB having proved nothing at all. Filling every
 * page with the output of a PRNG removes that escape: incompressible bytes
 * occupy the same space in zram as they do in RAM, so the ceiling this
 * finds is a real one. It also makes the read-back check meaningful, since
 * a wrong page is then overwhelmingly unlikely to match by accident.
 *
 * WRITING THE WHOLE PAGE MATTERS, not one word of it. A page touched in one
 * place is a page whose other 4088 bytes are still zero, and zram compresses
 * that to almost nothing. Every byte gets written.
 *
 * HOW IT REPORTS. Progress goes to the guest console every 64MB, because the
 * expected failure mode is not an error return — it is the guest's own
 * out-of-memory killer terminating this process, or the whole VM dying. In
 * that case the last console line IS the answer, and the vsock reply never
 * arrives. The reply, when it does arrive, carries the same number, so the
 * two sources are independent. Same trick as the 3c hash.
 *
 * The wire format is 3c's, unchanged, because the channel is proven and
 * changing it would add a variable for nothing:
 *
 *      host -> guest    4 bytes BE length (== 4), then 4 bytes BE target MB
 *      guest -> host    4 bytes BE length (== 12), then
 *                       4 bytes BE megabytes written AND verified
 *                       4 bytes BE status  (0 ok, 1 mmap refused,
 *                                           2 read-back mismatch)
 *                       4 bytes BE milliseconds spent touching
 */

extern void AVmPayload_notifyPayloadReady(void);

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

/* mmap needs six argument slots; sys4 above cannot reach x4/x5. */
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
#define SYS_nanosleep    101
#define SYS_clock_gettime 113
#define SYS_socket       198
#define SYS_bind         200
#define SYS_listen       201
#define SYS_accept4      242
#define SYS_mmap         222

#define AF_VSOCK        40
#define SOCK_STREAM     1
#define PORT            5555

#define PROT_READ       1
#define PROT_WRITE      2
#define MAP_PRIVATE     0x02
#define MAP_ANONYMOUS   0x20
#define AT_FDCWD        -100

/* 16MB per mmap. Small enough that the number reported is precise to within
 * one chunk, large enough that 2GB is 128 syscalls rather than 500,000. */
#define CHUNK           (16UL * 1024UL * 1024UL)

static void say(const char *s) {
    long n = 0;
    while (s[n]) {
        n++;
    }
    sys4(SYS_write, 1, (long) s, n, 0);
}

static void sayu(unsigned long v) {
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
    say(&d[i + 1]);
}

/* Each write() to fd 1 becomes its own console line — see the trap in
 * CLAUDE.md — so progress lines are assembled in a buffer and written once. */
static char g_line[160];

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

static void flushline(int at) {
    if (at < (int) sizeof g_line) {
        g_line[at++] = '\n';
    }
    sys4(SYS_write, 1, (long) g_line, at, 0);
}

/*
 * xorshift64*. Six lines, no tables, and its output does not compress — which
 * is the entire point. The seed is derived from the chunk index so the verify
 * pass can regenerate exactly the same stream without storing it anywhere.
 */
static unsigned long g_rng;

static void rng_seed(unsigned long s) {
    g_rng = s * 6364136223846793005UL + 1442695040888963407UL;
    if (g_rng == 0) {
        g_rng = 88172645463325252UL;
    }
}

static unsigned long rng_next(void) {
    g_rng ^= g_rng >> 12;
    g_rng ^= g_rng << 25;
    g_rng ^= g_rng >> 27;
    return g_rng * 2685821657736338717UL;
}

static unsigned long now_ms(void) {
    long ts[2] = { 0, 0 };
    sys4(SYS_clock_gettime, 1 /* CLOCK_MONOTONIC */, (long) ts, 0, 0);
    return (unsigned long) ts[0] * 1000UL + (unsigned long) ts[1] / 1000000UL;
}

/*
 * The guest's own view of its memory, straight out of its kernel. This is the
 * independent reading: 3e-i inferred the guest's RAM from the size of the zram
 * device it built, which is a good proxy and still only a proxy. MemTotal is
 * the kernel saying it outright.
 */
static void dump_meminfo(const char *when) {
    char buf[512];
    long fd = sys4(SYS_openat, AT_FDCWD, (long) "/proc/meminfo", 0 /* O_RDONLY */, 0);
    long n;
    long i, start;
    int lines = 0, at;

    if (fd < 0) {
        say("PENNY3EII: /proc/meminfo unreadable\n");
        return;
    }
    n = sys4(SYS_read, fd, (long) buf, sizeof buf - 1, 0);
    sys4(SYS_close, fd, 0, 0, 0);
    if (n <= 0) {
        return;
    }
    buf[n] = 0;

    /* The first three lines are MemTotal, MemFree, MemAvailable. Each goes out
     * as its own console line, prefixed, so it can be grepped. */
    start = 0;
    for (i = 0; i < n && lines < 3; i++) {
        if (buf[i] != '\n') {
            continue;
        }
        buf[i] = 0;
        at = putstr(0, "PENNY3EII: guest ");
        at = putstr(at, when);
        at = putstr(at, " ");
        at = putstr(at, &buf[start]);
        flushline(at);
        lines++;
        start = i + 1;
    }
}

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

int AVmPayload_main(void) {
    long fd, r, cfd;
    unsigned char sa[16];
    unsigned char hdr[16];
    unsigned int len, target_mb;
    unsigned long done_mb = 0, chunks, c, t0, t1;
    unsigned int status = 0;
    int i, at;

    say("PENNY3EII: payload up inside the guest\n");
    dump_meminfo("before");

    fd = sys4(SYS_socket, AF_VSOCK, SOCK_STREAM, 0, 0);
    if (fd < 0) {
        say("PENNY3EII: socket(AF_VSOCK) FAILED\n");
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
        say("PENNY3EII: bind FAILED\n");
        return 11;
    }
    if (sys4(SYS_listen, fd, 4, 0, 0) < 0) {
        say("PENNY3EII: listen FAILED\n");
        return 12;
    }

    /* After listen(), never before — 3c's ordering, for 3c's reason. */
    AVmPayload_notifyPayloadReady();
    say("PENNY3EII: listening on vsock 5555, waiting for a target\n");

    cfd = sys4(SYS_accept4, fd, 0, 0, 0);
    if (cfd < 0) {
        say("PENNY3EII: accept FAILED\n");
        return 13;
    }
    if (readn(cfd, hdr, 4) != 4) {
        say("PENNY3EII: short read on the length header\n");
        return 14;
    }
    len = get_be32(hdr);
    if (len != 4) {
        say("PENNY3EII: expected a 4-byte target\n");
        return 15;
    }
    if (readn(cfd, hdr, 4) != 4) {
        say("PENNY3EII: short read on the target\n");
        return 16;
    }
    target_mb = get_be32(hdr);

    at = putstr(0, "PENNY3EII: asked to touch ");
    at = putu(at, target_mb);
    at = putstr(at, " MB, 16MB at a time, pseudo-random fill");
    flushline(at);

    chunks = (unsigned long) target_mb / 16UL;
    t0 = now_ms();

    for (c = 0; c < chunks; c++) {
        unsigned long *p;
        unsigned long words = CHUNK / 8UL;
        unsigned long w;

        r = sys6(SYS_mmap, 0, (long) CHUNK, PROT_READ | PROT_WRITE,
                 MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
        if (r < 0 && r > -4096) {
            at = putstr(0, "PENNY3EII: mmap REFUSED at ");
            at = putu(at, done_mb);
            at = putstr(at, " MB, errno ");
            at = putu(at, (unsigned long) -r);
            flushline(at);
            status = 1;
            break;
        }
        p = (unsigned long *) r;

        /* Write pass. Every byte of every page, incompressible. */
        rng_seed(c + 1);
        for (w = 0; w < words; w++) {
            p[w] = rng_next();
        }

        /* Read-back pass, same stream regenerated. A page that was never
         * really supplied, or that came back wrong through zram, fails here
         * rather than silently passing. */
        rng_seed(c + 1);
        for (w = 0; w < words; w++) {
            if (p[w] != rng_next()) {
                at = putstr(0, "PENNY3EII: READ-BACK MISMATCH at ");
                at = putu(at, done_mb);
                at = putstr(at, " MB, word ");
                at = putu(at, w);
                flushline(at);
                status = 2;
                break;
            }
        }
        if (status == 2) {
            break;
        }

        done_mb += 16;
        if ((done_mb % 64) == 0) {
            at = putstr(0, "PENNY3EII: held and verified ");
            at = putu(at, done_mb);
            at = putstr(at, " MB");
            flushline(at);
        }
    }

    t1 = now_ms();

    at = putstr(0, "PENNY3EII: FINAL held and verified ");
    at = putu(at, done_mb);
    at = putstr(at, " MB of ");
    at = putu(at, target_mb);
    at = putstr(at, " asked, status ");
    at = putu(at, status);
    at = putstr(at, ", ");
    at = putu(at, t1 - t0);
    at = putstr(at, " ms");
    flushline(at);

    dump_meminfo("after");

    put_be32(hdr, 12);
    put_be32(hdr + 4, (unsigned int) done_mb);
    put_be32(hdr + 8, status);
    put_be32(hdr + 12, (unsigned int) (t1 - t0));
    writen(cfd, hdr, 16);
    sys4(SYS_close, cfd, 0, 0, 0);

    {
        long ts[2] = { 1, 0 };
        sys4(SYS_nanosleep, (long) ts, 0, 0, 0);
    }
    return 44;
}
