/*
 * Rung 2d: our own code, running inside the guest VM.
 *
 * Every VM this spike has booted so far ran Google's EmptyPayloadApp payload,
 * read straight out of the com.android.virt APEX. That proved the app owns a
 * VM; it proved nothing about the VM being ours to use, because nothing of
 * ours was ever inside it.
 *
 * This is the smallest possible thing that changes that. Microdroid dlopens
 * the file named by setPayloadBinaryName() out of lib/arm64-v8a/ in the APK
 * named by setApkPath(), and calls AVmPayload_main(). Both of those names were
 * read off the stock payload's own dynamic symbol table rather than recalled:
 *
 *   strings MicrodroidEmptyPayloadJniLib.so
 *     AVmPayload_main
 *     AVmPayload_notifyPayloadReady
 *     libvm_payload.so
 *
 * PROOF THAT THIS RAN, and not the stock payload, is deliberately threefold,
 * so that one missing log channel does not sink the result:
 *
 *   1. the guest console carries the PENNY2D lines below;
 *   2. onPayloadReady fires on the host — which only happens because this
 *      function calls AVmPayload_notifyPayloadReady();
 *   3. onPayloadFinished arrives with exit code 42, a number the stock
 *      payload has no way to produce.
 *
 * WHY THERE IS NO #include AND NO printf. 15 Sept: the NDK is a 975MB
 * download and the only network here is a phone tether, so this is compiled
 * instead by the Debian guest already on the Pixel — which is arm64 already,
 * so no cross-compiler is needed. But Debian's gcc links against glibc, and
 * microdroid is Android: it has bionic, and no glibc at all. A payload
 * carrying DT_NEEDED for libc.so.6 would not load.
 *
 * So this file uses NO C library. It talks to the guest kernel directly with
 * the two syscalls it needs, which is a dozen lines of assembly and costs
 * nothing, because a C library was never what was being tested. The one
 * external call left is AVmPayload_notifyPayloadReady, which microdroid itself
 * supplies. The consequence is that this same source compiles correctly under
 * either toolchain — Debian gcc now, the NDK later — with no #ifdef anywhere.
 *
 * There is no guest image, no kernel, no rootfs — rung 2c closed that off.
 * This runs inside Google's microdroid, unmodified.
 */

/* Declared, not included: the one symbol needed is a void/void call. build.sh
 * links against a stub libvm_payload.so built from vm_payload_stub.c, purely
 * so the linker emits DT_NEEDED for it — the same compile-only trick the Java
 * stubs use. The real one lives in the guest. */
extern void AVmPayload_notifyPayloadReady(void);

/*
 * The aarch64 Linux syscall convention: number in x8, arguments in x0..x5,
 * `svc #0` to trap into the kernel, result back in x0. This is the ABI the
 * guest kernel exposes and it is identical whether the userspace above it is
 * glibc, bionic or nothing at all — which is the entire point of doing it
 * this way.
 */
static long sys3(long nr, long a0, long a1, long a2) {
    register long x8 __asm__("x8") = nr;
    register long x0 __asm__("x0") = a0;
    register long x1 __asm__("x1") = a1;
    register long x2 __asm__("x2") = a2;
    __asm__ volatile("svc #0"
                     : "+r"(x0)
                     : "r"(x8), "r"(x1), "r"(x2)
                     : "memory", "cc");
    return x0;
}

#define SYS_write    64
#define SYS_nanosleep 101

/* Straight to file descriptor 1. printf would have gone to the same place;
 * microdroid wires the payload's stdout to the guest console, which surfaces
 * on the host in logcat under the virtmgr tag as Console(<cid>). */
static void say(const char *s) {
    long n = 0;
    while (s[n]) {
        n++;
    }
    sys3(SYS_write, 1, (long) s, n);
}

int AVmPayload_main(void) {
    say("PENNY2D: our own payload is running inside the guest\n");

    AVmPayload_notifyPayloadReady();

    say("PENNY2D: notified ready, holding for 5s\n");

    /* Returning ends the VM, so hold briefly: it makes the RUNNING state
     * observable from the host with `vm list` while this is still alive.
     * struct timespec is two longs on aarch64; failure here is harmless,
     * it is convenience rather than evidence. */
    {
        long ts[2] = { 5, 0 };
        sys3(SYS_nanosleep, (long) ts, 0, 0);
    }

    say("PENNY2D: exiting 42\n");
    return 42;
}
