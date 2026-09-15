/*
 * Rung 3c: the host/guest boundary, crossed inwards for the first time.
 *
 * WHAT CAME BEFORE. 2d put our own compiled code inside Google's microdroid
 * and proved it ran, but everything that crossed the boundary went OUTWARDS
 * and was one-way: three console strings and an exit code. Nothing has ever
 * gone IN. Rung 3b proved the app holds a live microphone 9.5s after power-on
 * with the phone locked — and then the samples died in the host process,
 * because there was no way to hand them to the guest. This file is that way.
 *
 * WHY A RAW SOCKET AND NOT libvm_payload's RPC. The constraint was to read
 * what microdroid's own libvm_payload.so offers before writing syscalls by
 * hand. It was checked, on the device, and the answer is that it cannot be
 * read: libvm_payload.so lives inside microdroid.img, which is EROFS, and the
 * blocks holding that file are compressed — `grep -ac AVmPayload` over the
 * whole 32MB image returns 0 while `microdroid_manager` returns 22, so the
 * image is only partly plaintext and this particular file is not in the
 * readable part. It could not be read without new tooling on a tethered Mac.
 *
 * It would not have changed this file. The only host/guest byte channel that
 * library is understood to offer is a binder RPC server, and binder RPC means
 * libbinder_ndk, libc++ and generated AIDL — a C++ toolchain, which is exactly
 * what this spike does not have and deliberately did not buy. AF_VSOCK is a
 * kernel interface. It costs four syscalls and no library at all, which is the
 * same reason 2d talks to the kernel directly: the syscall ABI belongs to the
 * kernel and is identical whether the userspace above it is glibc, bionic or
 * nothing. See the glibc/bionic trap in CLAUDE.md — that is why there is no
 * #include in this file either.
 *
 * THE SHAPE. The guest listens; the host connects. That direction is forced:
 * the only host-side call that is not on the hidden-API blocklist is
 * VirtualMachine.connectVsock(long), read off the device's own
 * framework-virtualization.jar as hiddenapi 0x0020 (SDK,TEST-API).
 * getConsoleInput() — the obvious alternative — is 0x0022 (BLOCKED,TEST-API),
 * so the console is outbound-only and vsock is not merely the better route,
 * it is the only one left.
 *
 * THE WIRE FORMAT, deliberately trivial, because a protocol bug and a channel
 * failure must not look alike:
 *
 *      host -> guest    4 bytes, big-endian length L
 *                       L bytes of payload           (L == 0 means "done")
 *      guest -> host    4 bytes, big-endian L (as received)
 *                       4 bytes, big-endian FNV-1a hash of those L bytes
 *                       L bytes echoed back verbatim
 *
 * The echo answers 3c-i (can any bytes make the round trip). The hash answers
 * 3c-ii (did the guest see the same bytes the host recorded) without shipping
 * a waveform back for a human to squint at. Both directions are checked by the
 * host, and the guest logs the hash to the console as well, so the comparison
 * survives even if the return leg is the thing that is broken.
 */

extern void AVmPayload_notifyPayloadReady(void);

/*
 * aarch64 Linux syscall convention: number in x8, arguments in x0..x5, `svc #0`
 * to trap, result back in x0. Four argument slots is the most anything below
 * needs (accept4). Same pattern as payload/penny_payload.c, which 2d proved.
 */
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

/* asm-generic/unistd.h — the arm64 table. */
#define SYS_close     57
#define SYS_read      63
#define SYS_write     64
#define SYS_nanosleep 101
#define SYS_socket    198
#define SYS_bind      200
#define SYS_listen    201
#define SYS_accept4   242

/* linux/vm_sockets.h and linux/socket.h */
#define AF_VSOCK        40
#define SOCK_STREAM     1
#define VMADDR_CID_ANY  0xFFFFFFFFu

/*
 * 5555 is inside VirtualMachine.MIN_VSOCK_PORT..MAX_VSOCK_PORT, which were
 * read off the device as 1024 and 4294967295. Below 1024 the host-side call
 * rejects it before any packet moves.
 */
#define PORT 5555

/* .bss. There is no malloc here and there does not need to be: one second of
 * 16kHz 16-bit mono audio is 32,000 bytes, so 64k is twice what 3c-ii sends. */
static unsigned char g_buf[65536];

static void say(const char *s) {
    long n = 0;
    while (s[n]) {
        n++;
    }
    sys4(SYS_write, 1, (long) s, n, 0);
}

/* Unsigned decimal, because there is no printf. Used for byte counts. */
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

/* Eight hex digits, zero padded — the hash is compared against the host's by
 * eye in the log as well as by code, so a stable width matters. */
static void sayx32(unsigned int v) {
    static const char h[] = "0123456789abcdef";
    char d[9];
    int i;
    for (i = 0; i < 8; i++) {
        d[7 - i] = h[(v >> (i * 4)) & 0xf];
    }
    d[8] = 0;
    say(d);
}

/*
 * FNV-1a, 32-bit. Chosen because it is six lines and has no tables: the point
 * is to detect corruption of a byte stream in transit, not to resist an
 * adversary. The host computes the identical function over what it sent.
 */
static unsigned int fnv1a(const unsigned char *p, unsigned long n) {
    unsigned int h = 2166136261u;
    unsigned long i;
    for (i = 0; i < n; i++) {
        h ^= p[i];
        h *= 16777619u;
    }
    return h;
}

/* read() and write() on a socket are both free to return short. Every failure
 * below is reported as its own exit code so that "the channel refused" and
 * "the channel truncated" cannot be confused for one another. */
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
    long fd, r;
    unsigned char sa[16];
    int i;
    int exchanges = 0;

    say("PENNY3C: payload up inside the guest\n");

    fd = sys4(SYS_socket, AF_VSOCK, SOCK_STREAM, 0, 0);
    if (fd < 0) {
        /* If this is where it dies, the guest's SELinux policy does not let a
         * payload open a vsock socket at all, and no amount of host-side work
         * would have helped. That is a real answer, not a bug. */
        say("PENNY3C: socket(AF_VSOCK) FAILED rc=");
        sayu((unsigned long) -fd);
        say(" (negative errno)\n");
        return 10;
    }
    say("PENNY3C: socket ok\n");

    /* struct sockaddr_vm, built by hand: u16 family, u16 reserved, u32 port,
     * u32 cid, u8 flags, u8 zero[3]. Little-endian, aarch64. */
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

    r = sys4(SYS_bind, fd, (long) sa, 16, 0);
    if (r < 0) {
        say("PENNY3C: bind FAILED rc=");
        sayu((unsigned long) -r);
        say("\n");
        return 11;
    }

    r = sys4(SYS_listen, fd, 4, 0, 0);
    if (r < 0) {
        say("PENNY3C: listen FAILED rc=");
        sayu((unsigned long) -r);
        say("\n");
        return 12;
    }
    say("PENNY3C: listening on vsock port 5555\n");

    /*
     * ORDER MATTERS AND IS NOT COSMETIC. The host only calls connectVsock()
     * when onPayloadReady arrives, and onPayloadReady only arrives because of
     * the line below. Notifying after listen() therefore makes it impossible
     * for the host to connect before there is anything to connect to — a race
     * that would have shown up as an intermittent refusal and been read as the
     * channel being closed.
     */
    AVmPayload_notifyPayloadReady();
    say("PENNY3C: notified ready, waiting for the host to connect\n");

    for (;;) {
        unsigned char hdr[8];
        unsigned int len, h;
        long cfd = sys4(SYS_accept4, fd, 0, 0, 0);

        if (cfd < 0) {
            say("PENNY3C: accept FAILED rc=");
            sayu((unsigned long) -cfd);
            say("\n");
            return 13;
        }
        say("PENNY3C: host connected\n");

        if (readn(cfd, hdr, 4) != 4) {
            say("PENNY3C: short read on the length header\n");
            return 14;
        }
        len = get_be32(hdr);

        if (len == 0) {
            say("PENNY3C: host sent length 0 — that is the goodbye\n");
            sys4(SYS_close, cfd, 0, 0, 0);
            break;
        }
        if (len > sizeof g_buf) {
            say("PENNY3C: host asked to send ");
            sayu(len);
            say(" bytes, buffer is 65536\n");
            return 15;
        }

        if (readn(cfd, g_buf, len) != (long) len) {
            say("PENNY3C: short read on the body\n");
            return 16;
        }

        h = fnv1a(g_buf, len);
        say("PENNY3C: received ");
        sayu(len);
        say(" bytes, fnv1a=");
        sayx32(h);
        say("\n");

        put_be32(hdr, len);
        put_be32(hdr + 4, h);
        if (writen(cfd, hdr, 8) != 8) {
            say("PENNY3C: short write on the reply header\n");
            return 17;
        }
        if (writen(cfd, g_buf, len) != (long) len) {
            say("PENNY3C: short write on the echo\n");
            return 18;
        }
        say("PENNY3C: echoed it back\n");

        sys4(SYS_close, cfd, 0, 0, 0);
        if (++exchanges >= 8) {
            break;
        }
    }

    say("PENNY3C: ");
    sayu((unsigned long) exchanges);
    say(" exchange(s) completed, exiting 43\n");

    /* A beat before the VM dies, so the host is certain to have drained the
     * last reply. Convenience, not evidence — the host has already logged its
     * own verdict by now. */
    {
        long ts[2] = { 1, 0 };
        sys4(SYS_nanosleep, (long) ts, 0, 0, 0);
    }
    return 43;
}
