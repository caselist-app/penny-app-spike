/*
 * Rung 3e-iii: WHERE WOULD A MODEL FILE LIVE?
 *
 * THE QUESTION, AND WHY IT CAN STILL CLOSE THE PLAN DOWN. 3e-ii proved a
 * guest can write, read back and hold 1792MB inside a 2048MB VM. That is the
 * ceiling for everything the guest does. If a 1.5GB model FILE must also sit
 * in RAM before anything loads it, then the file and the running model come
 * out of the same 1792MB and it does not fit. Nothing in this repo has ever
 * written a file inside a guest, so the suspicion is inference, not
 * measurement: rung 3d's shutdown log reads `init: Unknown /data fs type:
 * tmpfs`, and tmpfs is a RAM disk.
 *
 * WHY THIS PAYLOAD IS A COMMAND SERVER AND NOT A SINGLE-SHOT. There is no C
 * compiler on the Mac. Every rebuild of this file costs a manual round trip —
 * open the Terminal app on the phone by hand, forward a port, ssh into the
 * Debian guest, compile, copy back. So one build has to answer the whole
 * rung. The host connects once and sends a sequence of commands down the same
 * socket; the process keeps its state between them, which is the only way to
 * measure the thing that actually matters: what is left AFTER a file exists.
 * 3e-ii's single-shot shape could not have asked that without three builds.
 *
 * THE COMMANDS.
 *
 *   1 MOUNTS     print /proc/mounts and /proc/meminfo. If every writable
 *                mount is tmpfs there is no disk in here at all, and that
 *                alone answers most of the rung. Cheap, so it runs first.
 *   2 LISTDIR    print a directory. Guessing where the APK is mounted would
 *                cost a rebuild per guess; reading it costs thirty lines.
 *   3 FILEWRITE  create <path> and write N MB to it, reporting the guest's
 *                own MemFree either side. If MemFree falls by N, the file IS
 *                RAM. This is 3e-ii's accounting method and it is the whole
 *                measurement.
 *   4 TOUCH      3e-ii's anonymous mmap-and-fill loop, unchanged. Run AFTER
 *                a file exists and the answer is `file + touchable` versus
 *                1792MB, which is the number the rung exists to produce.
 *   5 MMAPREAD   open <path> read-only, mmap the whole thing, read every
 *                page. THE ESCAPE HATCH — see below.
 *
 * THE ESCAPE HATCH, AND WHY IT IS WORTH TESTING EVEN IF 3 AND 4 LOOK BAD.
 * The APK is already mounted read-only inside the guest; that is how this
 * very file gets loaded, via setApkPath() plus `zip -0` plus `zipalign -p 4`.
 * A model packaged into the APK the same way could be mmaped out of a
 * read-only mount instead of copied into a RAM disk. Clean file-backed pages
 * are RECLAIMABLE — the kernel can drop them under pressure — where anonymous
 * pages are not, and 3e-ii showed what happens when the guest has nothing
 * left to reclaim: it live-locks in zram and dies silently. So the difference
 * between these two routes is not a few percent, it is whether the failure
 * mode exists at all. Command 5 then command 4, in that order, is the test.
 *
 * INCOMPRESSIBLE DATA, STILL. Same reason as 3e-ii: microdroid gives every
 * guest a zram swap device sized to its whole RAM. A tmpfs file full of zeros
 * can be swapped into a few megabytes of zram and the payload would report
 * having written 1.5GB having proved nothing. Every byte written here comes
 * from a PRNG.
 *
 * WIRE FORMAT. 3c's framing, extended — the channel is proven and only the
 * message body changes:
 *
 *      host -> guest   4 bytes BE length L   (L == 0 means "goodbye")
 *                      4 bytes BE command
 *                      4 bytes BE argument, in MB
 *                      L-8 bytes of path, no terminator
 *
 *      guest -> host   4 bytes BE length (== 24), then six BE 32-bit fields:
 *                      status   0 ok, 1 mmap refused, 2 read-back mismatch,
 *                               3 open failed, 4 write failed, 5 bad command
 *                      memFreeBeforeKB   the guest's own, not the host's
 *                      memFreeAfterKB
 *                      resultMB
 *                      ms
 *                      extra    errno on a failure, else command-specific
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
#define SYS_getdents64    61
#define SYS_openat        56
#define SYS_close         57
#define SYS_read          63
#define SYS_write         64
#define SYS_fsync         82
#define SYS_fstat         80
#define SYS_nanosleep    101
#define SYS_clock_gettime 113
#define SYS_socket       198
#define SYS_bind         200
#define SYS_listen       201
#define SYS_accept4      242
#define SYS_munmap       215
#define SYS_mmap         222

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
#define O_DIRECTORY     0200000

/* 16MB per anonymous mmap — 3e-ii's figure. Precise to within one chunk,
 * and 2GB is 128 syscalls rather than half a million. */
#define CHUNK           (16UL * 1024UL * 1024UL)

/* 1MB per write() when creating a file. Large enough that a 1.5GB file is
 * 1536 syscalls, small enough to sit in .bss without argument. */
#define WBUF            (1UL * 1024UL * 1024UL)

static unsigned char g_wbuf[WBUF];

/*
 * gcc is entitled to turn a bounded copy or clear loop into a call to memcpy
 * or memset EVEN under -ffreestanding — the C standard requires those four
 * functions to exist in a freestanding implementation, so the compiler assumes
 * them. This binary has no C library at all (see the glibc/bionic trap in
 * CLAUDE.md), so such a call would be an undefined symbol, and in a .so that
 * surfaces at dlopen inside the guest where nothing useful is logged. Defining
 * them here costs eight lines and removes the failure mode.
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

static void say(const char *s) {
    long n = 0;
    while (s[n]) {
        n++;
    }
    sys4(SYS_write, 1, (long) s, n, 0);
}

/* Each write() to fd 1 becomes its own console line — see the trap in
 * CLAUDE.md — so a line is assembled in a buffer and written once. */
static char g_line[512];

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
 * xorshift64*. Its output does not compress, which is the entire point — see
 * the zram note in the header. Seeded from the chunk index so a verify pass
 * can regenerate the same stream without storing it.
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

/* ------------------------------------------------------------- /proc files */

/*
 * MemFree, in kB, straight from the guest kernel. Every measurement in this
 * rung is a difference between two of these, so it has to be a number and not
 * a printed line. Returns 0 if it cannot be read, which is distinguishable
 * from a real reading because a live guest never has MemFree 0.
 */
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

/* Print a whole /proc file to the console, one line per write so the guest
 * kernel's own framing works with us rather than against us. */
static void dump_file(const char *path, const char *tag) {
    char buf[8192];
    long fd, n, i, start;
    int at;

    fd = sys4(SYS_openat, AT_FDCWD, (long) path, O_RDONLY, 0);
    if (fd < 0) {
        at = putstr(0, "PENNY3EIII: ");
        at = putstr(at, path);
        at = putstr(at, " UNREADABLE errno ");
        at = putu(at, (unsigned long) -fd);
        flushline(at);
        return;
    }
    /* /proc files are generated on read and can exceed one buffer; loop. */
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
            at = putstr(0, "PENNY3EIII: ");
            at = putstr(at, tag);
            at = putstr(at, " ");
            at = putstr(at, &buf[start]);
            flushline(at);
            start = i + 1;
        }
    }
    sys4(SYS_close, fd, 0, 0, 0);
}

/*
 * List a directory. Worth its thirty lines: the APK's mount point inside the
 * guest is not documented anywhere this spike can reach, and guessing it would
 * cost a rebuild — which costs a trip into the phone's Debian guest — per
 * guess.
 *
 * struct linux_dirent64: u64 d_ino, s64 d_off, u16 d_reclen, u8 d_type,
 * char d_name[]. d_reclen at offset 16, d_type at 18, name at 19.
 */
static long list_dir(const char *path) {
    char buf[4096];
    long fd, n, off;
    int at, count = 0;

    fd = sys4(SYS_openat, AT_FDCWD, (long) path, O_RDONLY | O_DIRECTORY, 0);
    if (fd < 0) {
        at = putstr(0, "PENNY3EIII: opendir ");
        at = putstr(at, path);
        at = putstr(at, " FAILED errno ");
        at = putu(at, (unsigned long) -fd);
        flushline(at);
        return fd;
    }
    for (;;) {
        n = sys4(SYS_getdents64, fd, (long) buf, sizeof buf, 0);
        if (n <= 0) {
            break;
        }
        for (off = 0; off < n; ) {
            unsigned short reclen = *(unsigned short *) (buf + off + 16);
            unsigned char type = *(unsigned char *) (buf + off + 18);
            const char *name = buf + off + 19;

            at = putstr(0, "PENNY3EIII: dir ");
            at = putstr(at, path);
            at = putstr(at, " type");
            at = putu(at, type);           /* 4 = DIR, 8 = REG, 10 = LNK */
            at = putstr(at, " ");
            at = putstr(at, name);
            flushline(at);
            count++;
            off += reclen;
        }
    }
    sys4(SYS_close, fd, 0, 0, 0);
    return count;
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

/* ------------------------------------------------------------ the commands */

#define CMD_MOUNTS    1
#define CMD_LISTDIR   2
#define CMD_FILEWRITE 3
#define CMD_TOUCH     4
#define CMD_MMAPREAD  5

/* Anonymous memory taken by CMD_TOUCH is deliberately never unmapped: the
 * question is what can be held AT ONCE, alongside whatever a file already
 * cost. Accumulated across commands for the same reason. */
static unsigned long g_touched_mb = 0;

/* Prevents the compiler deleting the mmap read loop, which has no other
 * observable effect and which -O1 would otherwise be entitled to remove. */
static volatile unsigned long g_sink;

/*
 * Write arg_mb megabytes of incompressible bytes to path.
 *
 * The number that matters is not the return value, it is the caller's MemFree
 * either side. If MemFree falls by arg_mb, the file is RAM and a model cannot
 * live here. fsync is called so that "written" means committed rather than
 * merely queued — on tmpfs it is a no-op, and that no-op is itself a signal.
 */
static unsigned int do_filewrite(const char *path, unsigned long arg_mb,
                                 unsigned long *out_mb, unsigned int *extra) {
    long fd, r;
    unsigned long m, w, words = WBUF / 8UL;
    unsigned long *p = (unsigned long *) g_wbuf;

    *out_mb = 0;
    *extra = 0;

    fd = sys4(SYS_openat, AT_FDCWD, (long) path,
              O_WRONLY | O_CREAT | O_TRUNC, 0600);
    if (fd < 0) {
        *extra = (unsigned int) -fd;
        return 3;
    }

    for (m = 0; m < arg_mb; m++) {
        unsigned long put = 0;

        rng_seed(m + 1);
        for (w = 0; w < words; w++) {
            p[w] = rng_next();
        }
        while (put < WBUF) {
            r = sys4(SYS_write, fd, (long) (g_wbuf + put), (long) (WBUF - put), 0);
            if (r <= 0) {
                *extra = (unsigned int) (r < 0 ? -r : 0);
                sys4(SYS_close, fd, 0, 0, 0);
                return 4;
            }
            put += (unsigned long) r;
        }
        *out_mb = m + 1;

        if (((m + 1) % 64) == 0) {
            int at = putstr(0, "PENNY3EIII: wrote ");
            at = putu(at, m + 1);
            at = putstr(at, " MB to ");
            at = putstr(at, path);
            at = putstr(at, ", guest MemFree ");
            at = putu(at, meminfo_kb("MemFree:"));
            at = putstr(at, " kB");
            flushline(at);
        }
    }

    sys4(SYS_fsync, fd, 0, 0, 0);
    sys4(SYS_close, fd, 0, 0, 0);
    return 0;
}

/* 3e-ii's loop, unchanged in substance. See penny3eii_payload.c for why every
 * byte is written and why the fill is pseudo-random. */
static unsigned int do_touch(unsigned long arg_mb, unsigned long *out_mb,
                             unsigned int *extra) {
    unsigned long chunks = arg_mb / 16UL, c;
    unsigned int status = 0;
    long r;
    int at;

    *extra = 0;
    for (c = 0; c < chunks; c++) {
        unsigned long *p, w, words = CHUNK / 8UL;

        r = sys6(SYS_mmap, 0, (long) CHUNK, PROT_READ | PROT_WRITE,
                 MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
        if (r < 0 && r > -4096) {
            at = putstr(0, "PENNY3EIII: mmap REFUSED after ");
            at = putu(at, g_touched_mb);
            at = putstr(at, " MB, errno ");
            at = putu(at, (unsigned long) -r);
            flushline(at);
            *extra = (unsigned int) -r;
            status = 1;
            break;
        }
        p = (unsigned long *) r;

        rng_seed(c + 1);
        for (w = 0; w < words; w++) {
            p[w] = rng_next();
        }
        rng_seed(c + 1);
        for (w = 0; w < words; w++) {
            if (p[w] != rng_next()) {
                at = putstr(0, "PENNY3EIII: READ-BACK MISMATCH after ");
                at = putu(at, g_touched_mb);
                at = putstr(at, " MB");
                flushline(at);
                status = 2;
                break;
            }
        }
        if (status == 2) {
            break;
        }

        g_touched_mb += 16;
        if ((g_touched_mb % 64) == 0) {
            at = putstr(0, "PENNY3EIII: held and verified ");
            at = putu(at, g_touched_mb);
            at = putstr(at, " MB anonymous, guest MemFree ");
            at = putu(at, meminfo_kb("MemFree:"));
            at = putstr(at, " kB");
            flushline(at);
        }
    }
    *out_mb = g_touched_mb;
    return status;
}

/*
 * THE ESCAPE HATCH. mmap a file read-only and touch every page of it.
 *
 * If the file is on a read-only mount backed by the host — the APK — these
 * pages are clean and file-backed, so the kernel may drop and re-read them
 * under pressure. If it is on tmpfs, "file-backed" is a fiction: the backing
 * store IS memory, the pages cannot be dropped, and this costs exactly as
 * much as an anonymous allocation. The MemFree difference either side tells
 * which, and the Cached figure tells it a second way.
 *
 * The mapping is left in place, and the pages are read on a 4096-byte stride
 * because that is the granularity at which the kernel faults them in.
 */
static unsigned int do_mmapread(const char *path, unsigned long *out_mb,
                                unsigned int *extra) {
    long fd, sz, r, off;
    unsigned char *p;
    int at;

    *out_mb = 0;
    *extra = 0;

    fd = sys4(SYS_openat, AT_FDCWD, (long) path, O_RDONLY, 0);
    if (fd < 0) {
        *extra = (unsigned int) -fd;
        return 3;
    }
    sz = file_size(fd);
    if (sz <= 0) {
        *extra = (unsigned int) (sz < 0 ? -sz : 0);
        sys4(SYS_close, fd, 0, 0, 0);
        return 3;
    }

    at = putstr(0, "PENNY3EIII: ");
    at = putstr(at, path);
    at = putstr(at, " is ");
    at = putu(at, (unsigned long) sz);
    at = putstr(at, " bytes, mmaping it read-only");
    flushline(at);

    r = sys6(SYS_mmap, 0, sz, PROT_READ, MAP_PRIVATE, fd, 0);
    if (r < 0 && r > -4096) {
        *extra = (unsigned int) -r;
        sys4(SYS_close, fd, 0, 0, 0);
        return 1;
    }
    p = (unsigned char *) r;

    /* The fd may be closed; the mapping keeps its own reference. Closing it
     * makes the measurement about the mapping alone. */
    sys4(SYS_close, fd, 0, 0, 0);

    for (off = 0; off < sz; off += 4096) {
        g_sink += p[off];
        if ((off & ((64L << 20) - 1)) == 0 && off != 0) {
            at = putstr(0, "PENNY3EIII: faulted in ");
            at = putu(at, (unsigned long) (off >> 20));
            at = putstr(at, " MB of file, guest MemFree ");
            at = putu(at, meminfo_kb("MemFree:"));
            at = putstr(at, " kB Cached ");
            at = putu(at, meminfo_kb("Cached:"));
            at = putstr(at, " kB");
            flushline(at);
        }
    }

    *out_mb = (unsigned long) (sz >> 20);
    return 0;
}

/* ------------------------------------------------------------------- plumb */

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

static unsigned char g_req[1024];
static char g_path[768];

int AVmPayload_main(void) {
    long fd, cfd;
    unsigned char sa[16];
    unsigned char hdr[28];
    int i;

    say("PENNY3EIII: payload up inside the guest\n");

    fd = sys4(SYS_socket, AF_VSOCK, SOCK_STREAM, 0, 0);
    if (fd < 0) {
        say("PENNY3EIII: socket(AF_VSOCK) FAILED\n");
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
        say("PENNY3EIII: bind FAILED\n");
        return 11;
    }
    if (sys4(SYS_listen, fd, 4, 0, 0) < 0) {
        say("PENNY3EIII: listen FAILED\n");
        return 12;
    }

    /* After listen(), never before — 3c's ordering, for 3c's reason. */
    AVmPayload_notifyPayloadReady();
    say("PENNY3EIII: listening on vsock 5555, waiting for commands\n");

    cfd = sys4(SYS_accept4, fd, 0, 0, 0);
    if (cfd < 0) {
        say("PENNY3EIII: accept FAILED\n");
        return 13;
    }
    say("PENNY3EIII: host connected\n");

    for (;;) {
        unsigned int len, cmd, arg;
        unsigned long before, after, out_mb = 0, t0, t1;
        unsigned int status, extra = 0;
        int at;

        if (readn(cfd, hdr, 4) != 4) {
            say("PENNY3EIII: channel closed without a goodbye\n");
            break;
        }
        len = get_be32(hdr);
        if (len == 0) {
            say("PENNY3EIII: host said goodbye\n");
            break;
        }
        if (len < 8 || len > sizeof g_req) {
            say("PENNY3EIII: malformed request length\n");
            break;
        }
        if (readn(cfd, g_req, len) != (long) len) {
            say("PENNY3EIII: short read on the request body\n");
            break;
        }
        cmd = get_be32(g_req);
        arg = get_be32(g_req + 4);

        {
            unsigned int plen = len - 8;
            if (plen > sizeof g_path - 1) {
                plen = sizeof g_path - 1;
            }
            for (i = 0; i < (int) plen; i++) {
                g_path[i] = (char) g_req[8 + i];
            }
            g_path[plen] = 0;
        }

        at = putstr(0, "PENNY3EIII: CMD ");
        at = putu(at, cmd);
        at = putstr(at, " arg ");
        at = putu(at, arg);
        at = putstr(at, " path '");
        at = putstr(at, g_path);
        at = putstr(at, "'");
        flushline(at);

        before = meminfo_kb("MemFree:");
        t0 = now_ms();
        status = 5;

        switch (cmd) {
        case CMD_MOUNTS:
            dump_file("/proc/mounts", "mount");
            dump_file("/proc/meminfo", "mem");
            status = 0;
            break;
        case CMD_LISTDIR:
            status = list_dir(g_path) < 0 ? 3 : 0;
            break;
        case CMD_FILEWRITE:
            status = do_filewrite(g_path, arg, &out_mb, &extra);
            break;
        case CMD_TOUCH:
            status = do_touch(arg, &out_mb, &extra);
            break;
        case CMD_MMAPREAD:
            status = do_mmapread(g_path, &out_mb, &extra);
            break;
        default:
            say("PENNY3EIII: unknown command\n");
            break;
        }

        t1 = now_ms();
        after = meminfo_kb("MemFree:");

        at = putstr(0, "PENNY3EIII: DONE cmd ");
        at = putu(at, cmd);
        at = putstr(at, " status ");
        at = putu(at, status);
        at = putstr(at, " result ");
        at = putu(at, out_mb);
        at = putstr(at, " MB, guest MemFree ");
        at = putu(at, before);
        at = putstr(at, " -> ");
        at = putu(at, after);
        at = putstr(at, " kB, ");
        at = putu(at, t1 - t0);
        at = putstr(at, " ms");
        flushline(at);

        put_be32(hdr, 24);
        put_be32(hdr + 4, status);
        put_be32(hdr + 8, (unsigned int) before);
        put_be32(hdr + 12, (unsigned int) after);
        put_be32(hdr + 16, (unsigned int) out_mb);
        put_be32(hdr + 20, (unsigned int) (t1 - t0));
        put_be32(hdr + 24, extra);
        if (writen(cfd, hdr, 28) != 28) {
            say("PENNY3EIII: short write on the reply\n");
            break;
        }
    }

    sys4(SYS_close, cfd, 0, 0, 0);
    say("PENNY3EIII: exiting 45\n");

    {
        long ts[2] = { 1, 0 };
        sys4(SYS_nanosleep, (long) ts, 0, 0, 0);
    }
    return 45;
}
