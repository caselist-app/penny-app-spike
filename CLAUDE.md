# CLAUDE.md

## What this repo is

A feasibility spike, not a product. penny-box answered whether a Pixel
running GrapheneOS can bring up a Linux VM at all, and whether a VM can
be created from outside the Terminal app. It can. That repo is closed.

This repo answers the next thing: **can an ordinary sideloaded app own a
VM?** An app we build, installed by us, not signed by the platform, not
preinstalled in the OS image. And then: can that app bring its VM up on
its own after a reboot, with nobody touching the screen?

Nothing here is intended to survive. If the answer is yes, the real
project starts elsewhere.

## How to read this repo — READ THIS BEFORE OPENING notes.md

`notes.md` is append-only history. This file carries the current state.
Read it, then grep `notes.md` only for the entry you actually need —
every entry is headed with a date and a rung number.

    grep -n "^## " notes.md          # list every entry
    grep -n "rung 2a" notes.md       # jump to one

Later entries correct earlier ones. Where two disagree, the later wins.

`~/Documents/penny-box` is the previous spike and is **closed**. Read its
`CLAUDE.md` for **device state and traps only** — that block is current.
Ignore its "open questions" section entirely; it predates rung 1 and
still frames the work as questions 1-9. Do **not** read its `notes.md`
end to end — it is 2,298 lines. Grep it.

## State of play — 15 September 2026

Pixel 6a (bluejay), refurbished, 6GB Micron DRAM, 128GB Micron UFS.
Bootloader **LOCKED**, verified boot against a custom key. OEM unlocking
deliberately left **ENABLED** so the device can be returned to stock.

    GrapheneOS     2026091001
    Android        17, build ID CP2A.260705.006, patch 2026-09-01
    Bootloader     bluejay-17.0-15199431, locked, verifiedbootstate=yellow
    Debian guest   13.7 trixie, kernel 6.12.92-android16-6-...-4k
                   (13.6 in earlier entries; it updates itself)
    Claude Code    2.1.270 in the guest, native install, ~317MiB resident
    VM resources   3.9GB slider max -> 3.6Gi in guest, 8 cores, 104G disk

**LIVE DEVICE STATE — as left at 15 Sept, ~20:12, after the soak was stopped
and the native memory baseline taken. Check it, do not trust it.** This block
exists because state that only survives in a handover message is state that
gets lost. Verify each line before relying on it; correct this block in the
same commit as whatever changes it.

    Phone                      **REBOOTED 18 Sept at 13:17:19 for BOOT 4 — the
                               brief-S boot.** Matt unlocked by hand; adb
                               answered at uptime 52.95 s, wallclock 13:18:26,
                               so boot start was ~13:17:33. **STILL UP at the
                               last read: uptime 7960.07 s, wallclock 15:30:14
                               on 18 Sept**, with all three clock ceilings back
                               at rated, `MemAvailable` 2,319,936 kB, `SwapFree`
                               747,928 kB and battery 317 dC (31.7 C).
                               **BOTH PROTOCOL READINGS WERE TAKEN ON THIS BOOT**,
                               each in ONE wrapped invocation:
                               **MemAvailable 2,218,088 kB at 303.84 s
                               (13:22:37)** and **2,347,476 kB at 1501.63 s
                               (13:42:35)**. The 5-minute figure is **1.28 GB
                               ABOVE** the only other ~5-minute reading in the
                               repo (940,640 kB at 5.8 min, 15 Sept) — recorded,
                               not explained, and it is the second reading
                               behind the standing warning that the 5-minute
                               mark is not a stable point on this handset.
                               **THIS BOOT IS SPENT**: S0, S1 (59.16 min) and S2
                               (26.03 min) have all run on it, plus the
                               ceiling-recovery reading. **S3 was NOT run** —
                               Matt's call, not taken.
                               Earlier boots, kept because the 25-minute curve
                               is now read on six of them: 18 Sept 10:18:14
                               (boot 3, Q-A/Q-B rows 6-9), 16 Sept 17:16:57 and
                               18:06:24, and 16 Sept 15:20:52 and 14:22:50.
                               MemAvailable at ~25 min across the five earlier
                               boots runs **2,135,144 to 2,284,200 kB**; boot 4's
                               2,347,476 kB is 63,276 kB above that range and is
                               NOT offered as a sixth member of the set, because
                               a row had already run on it.
                               **`policy0` HAS NOW BEEN SAMPLED DURING ROWS** —
                               see the trap below. AC power, screen on, unlocked.
    OUR APP IS DISABLED        `adb shell pm disable-user --user 0
                               com.pennyspike.probe2a` at ~19:57. Nothing of
                               ours starts at boot, nothing restarts, and the
                               reboot above confirmed it. **No data was
                               cleared**, so every store survives. Reverse with
                               `adb shell pm enable com.pennyspike.probe2a`.
                               Do this BEFORE expecting any boot service to run.
                               **WARNING — DO NOT `pm enable` AS THINGS STAND.**
                               The installed APK starts SIX boot services, and
                               two of them each ask for 2048MB on the next
                               boot: `PennySoakService` immediately and
                               `Penny3giService` after ~15-45s (it waits on
                               3e-v's verdict or its own timeout). That is the
                               two-2GB-VM trap below, which on 15 Sept evening
                               took `com.android.launcher3` and our own app at
                               **adj 100**, 50 kills, settling on the third
                               attempt. Under that pressure
                               `Penny3evService.java` ~345-352 DELETES the
                               `penny3ev` store on ANY `run()` failure and
                               recreates it — the recovery path, logging
                               `STORE WAS RESET`. So an enable can cost the
                               verified 64MB store. **Neutralise one of the two
                               2GB services before enabling.** Not fixed on 15
                               Sept — recorded only.
    All VMs                    DOWN. `vm list` returns `Running VMs: []`, no
                               crosvm process, no app process. penny3, penny3d,
                               penny3ev, penny3gi and pennysoak are all
                               stopped — they are boot services and the app is
                               disabled. **SIX services start from the boot
                               broadcast, not five**: VmService, MicFgsService,
                               Penny3dService, Penny3evService, Penny3giService
                               and PennySoakService. Five of them hold a VM;
                               MicFgsService holds none, which is why only five
                               VM names appear here.
    Terminal app / Debian VM   DOWN since 15:37 and it does not restart itself.
                               It holds ~3.6GB while it runs, so close it again
                               before any memory-sensitive measurement.
    penny3ev (rung 3e-v's VM)  STOPPED. **PROBABLY STRANDED by the 16:51
                               reinstall — DO NOT PLAN ON IT.** This line said
                               "believed INTACT" until 15 Sept evening and that
                               was wrong. It was last VERIFIED 16:31:39 under
                               the FIFTH install (/mnt/encryptedstore/
                               penny3ev.bin, 67,108,864 bytes, ck64
                               0x757b795dd5138044, SIZE MATCH and CONTENT
                               MATCH). The SIXTH build was installed at 16:51
                               — sha256 9efe27cb..., confirmed on the phone —
                               so by the getOrCreate stale-config trap below,
                               penny3ev's stored config now points at a
                               /data/app path that reinstall replaced. Its next
                               run() therefore throws, and Penny3evService's
                               recovery path (~345-352) deletes and recreates
                               the store, logging STORE WAS RESET.
                               **WHETHER THAT ALREADY HAPPENED ON THE 19:36
                               BOOT CANNOT BE ANSWERED**: Penny3evService did
                               run then, alongside the soak, but logcat dies on
                               reboot and the buffer's oldest line is now
                               09-15 19:59:34 — the current boot. Checked 15
                               Sept ~21:05; no PENNY3EV or STORE WAS RESET line
                               survives. So the store is either already reset
                               or will reset on its next run, and nothing
                               distinguishes the two from here. PARKED — the
                               ck64 is DERIVABLE from the fixed-seed generator,
                               so if it is ever wanted back it is re-creatable
                               rather than lost, but no result may rest on the
                               existing file.
    pennysoak store            **CONTAMINATED, and the run that used it is
                               VOID.** A force-stop restarted PennySoakService
                               through START_STICKY with a NULL intent on an
                               unlocked phone, so it fell back to the default VM
                               name and created and wrote the store there and
                               then. The boot that followed found it already
                               full and never exercised the create path. Claim
                               nothing from it. If the soak is re-run, give it a
                               fresh VM name.
    penny3gic / penny3gi       Rung 3g-i's 256MB control and 2048MB test. No
                               store, deleted and recreated on every run,
                               nothing to protect. **Runs ONCE PER PROCESS** — a
                               second am start-foreground-service logs `already
                               started by an earlier delivery — nothing to do`.
                               Re-arming it needs a force-stop.
    penny3f store              **GONE.** 3g-ii's runs passed --ei storage 0 with
                               keep=0. The 32MB file ck64 0x5f2e2310fc323145 is
                               unreachable. Nothing depends on it.
    penny3eiii encrypted store **GONE — STRANDED, DELIBERATELY** on 15 Sept.
                               model.bin is unreachable. Do not plan anything
                               that needs it.
    installed APK              Still installed, disabled. **THE HASH AND PATH
                               BELOW WERE WRONG UNTIL 15 Sept EVENING** — they
                               named an earlier install. Read off the phone at
                               ~20:50 on 15 Sept, app disabled, 58 min uptime:
                               /data/app/~~AfIhpIXq9pYEyRdOP2bSQw==/
                               com.pennyspike.probe2a-nou1Eur-h0XqviaxcCE9ww==
                               sha256 9efe27cb0aa802243123be26bcc0ee5ff9da52f6
                               685a20831a699daf699902fe, 156,613 bytes.
                               **That is the build on disk** —
                               `probe2a/build/probe2a.apk`, same hash, same
                               156,613 bytes, written 15 Sept 16:51 after
                               PennySoakService was saved. So the soak service
                               IS in the installed APK, and the old
                               `2e89918f...` / `~~F-GoV1zaM...` values are
                               superseded. Verify with
                               `adb shell sha256sum $(adb shell pm path
                               com.pennyspike.probe2a | sed 's/package://')`.
                               All five pm grants and all four assistant
                               preconditions survived every install. **Whether
                               they survive `pm disable-user` + `pm enable` is
                               UNTESTED — check before trusting them.**
    apps opened by hand        NONE. The reboot cleared 3g-ii's camera, browser,
                               gallery, clock, calculator and files.
    native memory baseline     MemTotal 5,718,280 kB. MemAvailable read FIVE
                               times on ONE boot, no VM, app disabled:
                               **940,640 kB at 5.8 min, 2,119,020 kB at 25.3
                               min, 2,012,348 kB at 60.5 min, 1,929,032 kB at
                               120.6 min, 1,771,920 kB at 973.2 min.** It peaks
                               at ~25 min and declines at every later reading.
                               **THE DECLINE DECELERATES SHARPLY** — ~2.0
                               MB/min over 25->120 min, but only **0.18 MB/min**
                               over 120->973 min, so last night's rate must not
                               be extrapolated. The first four were on an
                               untouched phone; the 973-min reading was not
                               (see Phone above). **THERE IS NO SINGLE IDLE
                               NUMBER. Never quote one without its uptime.**
                               Swap two-thirds spent at idle and now static
                               (SwapFree 1,266,044 of 3,145,724 kB at 973.2 min,
                               +4,224 kB in 14 hours); what moved overnight was
                               Cached, +658,800 kB. `dumpsys` Free RAM
                               3,552,852 kB at 973.2 min, of which only
                               120,360 kB is genuinely free. See the
                               host-memory bullet in open threads.
    /data/local/tmp            **llama-bench IS ON THE PHONE as of 16 Sept
                               12:15** — 4,708,216 B, mode 755, sha256
                               44015c0614b3f1c0f4ee3240fb8f3a37503420ab
                               7285a36a10ad14abaaaeb84e, byte-identical to
                               `~/Documents/llama.cpp/build-android/bin/
                               llama-bench-stripped` on the Mac. It LOADS AND
                               LINKS: `--help` printed usage and exited 0, so
                               the shell user has exec here and the binary
                               resolves against bionic with no LD_LIBRARY_PATH.
                               **SIGILL RULED OUT 16 Sept 12:24, NARROWLY.**
                               A real run returned correct text, so the Q4_K
                               and Q6_K kernels this model uses are clean.
                               **The Q4_0 repack path has NEVER executed on
                               this phone** — different kernels, and it is
                               where llama.cpp's ARM dot-product repacking
                               lives. `--help` alone had proved nothing here.
    models on the phone        **ONE — Qwen3-1.7B-Q4_K_M, and this block said
                               Qwen3.5-2B until 18 Sept 13:11.** Swapped back
                               for brief S (the sustained run), by Claude over
                               adb with Matt's authorisation, in this order at
                               uptime 10279.96-10347.05 on boot 3: hash
                               `q35_state.bin` (`6f461e45…`, MATCHED the
                               recorded value), `rm` Qwen3.5-2B-Q4_K_M.gguf and
                               q35_state.bin, `ls` to confirm both absent,
                               `adb push` the 1.7B (1,107,409,472 B in 32.810 s,
                               32.2 MB/s), `sha256sum` it ON THE PHONE:
                               **b139949c5bd74937ad8ed8c8cf3d9ffb1e99c866c8232
                               04dc42c0d91fa181897**, matching MANIFEST.txt's
                               HF-LFS-verified value and the Mac copy hashed
                               the same day. **Qwen3.5-2B-Q4_K_M.gguf and
                               q35_state.bin are DELETED**, both confirmed
                               absent by `ls`; /data went 9.2G -> 8.0G used,
                               102 G free. All ten models remain
                               manifest-verified in ~/Documents/penny-models.
                               **q35_state.bin is NOT re-creatable without a
                               row** — it was row 7's output. Its hash is in
                               notes.md and in the deleted line below; nothing
                               outstanding depends on it, and the 2B's B3 (the
                               unspent boot 4) would need it regenerated.
                               **Its mtime reads 2026-09-15 20:25 and that is
                               NOT when it was pushed** — `adb push` preserves
                               the SOURCE file's mtime, so an mtime on this
                               phone dates the Mac's copy, never the transfer.
                               Cost a wrong read on 16 Sept 17:00.
                               **THE PUSH WARMED THE PAGE CACHE**, so no cold
                               read is available on boot 3 — which is moot,
                               because boot 3 is about to be spent and S needs
                               a fresh one anyway.
                               One model at a time: delete before pushing the
                               next.
    logcat buffer              **RAISED to 64 MiB, 16 Sept 12:22**, after
                               saving the whole buffer to
                               ~/Documents/logcat-2026-09-16-preraise.txt
                               (6.6 MB, 43,759 lines, oldest 09-15 19:59:34).
                               The resize did NOT clear it. Dies on reboot.
    /data/local/tmp contents   **Read off the phone 18 Sept 13:11, after a
                               `sync`**, at the close of boot 3 and after the
                               model swap and the brief-S instrument install.
                               `Qwen3-1.7B-Q4_K_M.gguf` 1,107,409,472 B
                               `b139949c…` (see above);
                               **`q17_state.bin` 46,685,237 B, sha256
                               707e0ea3c1cc490187616a67ba0097747c8b8c58fcd2dcf
                               38e1870a31a8f6f4d** — REGENERATED by S0 on
                               18 Sept 13:24 and **byte-for-byte the file
                               deleted on 16 Sept**, written that time by
                               `be2cab2c…` and this time by `f52fc604…`. The
                               prefix-save path is deterministic across
                               binaries and boots, so this file is always
                               re-creatable from `penny_system.txt` and never
                               needs protecting;
                               `llama-bench` 4,708,216 B `44015c06…`;
                               `llama-simple` 3,805,208 B `3d6b6afa…`;
                               **`pennyload` 3,836,992 B
                               `f52fc60411b55e5ed9eb34e8307f32b45d6bed6f06de85
                               a5347bc02ec2f4ffe9`** (= the repo's
                               `build/pennyload-stripped`), which **SUPERSEDES
                               `be2cab2c…` / 3,817,808 B** — it adds
                               `--turns`/`--interval-s` and nothing else, and
                               the single-turn path was verified unchanged on
                               this phone by an A/B against the old binary
                               (same 28 lines, same order, same
                               `token_fnv1a64 0x19d53b5da9186ff6`, all 64 token
                               ids identical);
                               **`pennybench.sh` revision 5, 12,790 B,
                               `96163d047d7a91cd3f937cba71c4bce9270e6f84afcb4d
                               700fe7a1513e0889af`**, which **SUPERSEDES
                               revision 4 `c5b9f03a…` / 8,402 B** — it adds a
                               10 s time series to `<tag>.series` and the
                               `oom_score_adj_child pre=/post=` line;
                               `penny_system.txt` 1,911 B (`94969770…`, hashed
                               16 Sept, size re-confirmed 18 Sept);
                               `penny_user.txt` **95 B**, replaced 16 Sept
                               17:05, its current hash not on record;
                               `out/` (every row's .bench/.err/.kills/.report,
                               and from rev 5 also .series) and the pre-existing
                               `microdroid/` from 14 Sept — left alone.
                               `Qwen3.5-2B-Q4_K_M.gguf` and `q35_state.bin` are
                               GONE, as are the temporary `pennyload_s` and
                               `pennybench_s.sh` the smoke tests used — there is
                               exactly one of each binary on the phone.
                               **NO `q17_state.bin` EXISTS**, on the phone or on
                               the Mac (`find ~/Documents` returned nothing,
                               18 Sept 12:28), so brief S's row S0 must
                               regenerate it.
    adb                        ALIVE (phone unlocked). GrapheneOS keeps the port
                               charging-only while locked.
    models on the Mac          ~/Documents/penny-models, 10 GGUF files,
                               19,771,681,856 bytes, EVERY ONE verified against
                               Hugging Face's published LFS sha256. See
                               MANIFEST.txt there, and the 15 Sept evening
                               notes.md entry. NOTHING is on the phone yet.
    adb forward tcp:2222       DEAD (the Debian VM is down). Pointless until it
                               is reopened by hand.
    logcat buffer              DEFAULT — the reboot cleared 3g-ii's 64M setting,
                               and `setprop persist.logd.size` is REFUSED by
                               SELinux on this build.

Verify with: `adb shell /apex/com.android.virt/bin/vm list`,
`adb shell pm path com.pennyspike.probe2a`.

Build toolchain on the Mac, installed 14 Sept, command-line only — no
Android Studio, deliberately (see `notes.md`):

    Temurin JDK    21.0.12.1 arm64, /Library/Java/JavaVirtualMachines/temurin-21.jdk
    SDK root       /opt/homebrew/share/android-commandlinetools
    platform       platforms;android-37.0    (Android 17 is API 37)
    build-tools    build-tools;37.0.0
    platform-tools 37.0.1
    Gradle         9.7.1  — INSTALLED BUT NOT USED, see below
    adb/fastboot   /opt/homebrew/bin, Homebrew android-platform-tools
    NDK            30.0.16248370   INSTALLED 15 Sept evening
    cmake          3.22.1-g37088a8 INSTALLED 15 Sept evening, sdkmanager
    ninja          1.10.2          INSTALLED 15 Sept evening, sdkmanager,
                                   inside the cmake package's own bin/
    ninja (brew)   1.13.2          also present, and it SHADOWS the above

**THE NDK IS NOW INSTALLED — this paragraph used to say it never would be.**
`ndk/30.0.16248370`, with a real
`toolchains/llvm/prebuilt/darwin-x86_64/bin/clang`, fetched 15 Sept evening on
a home LAN for the native `llama-bench` work. The earlier reasoning was sound
and is kept for why the payloads look as they do: rung 2d needed a
cross-compiler, the NDK is a ~975MB download, the Mac was on a phone tether,
and the guest payload was compiled **inside the Debian guest on the phone**
instead — already arm64, no cross-compiler needed, `gcc 14.2.0 (Debian
14.2.0-19)` for 43MB. See the glibc/bionic trap below — that is why
`payload/penny_payload.c` uses no C library at all, and none of that changes.
**What the NDK means for the guest payloads: nothing, for now.** The five
existing payloads are built in the Debian guest with no C library at all, and
that route is proven — leave them exactly as they are. Building a payload with
the NDK instead is **untested, not ruled out**: the NDK targets bionic, which
is what microdroid runs, so there is no glibc/bionic objection to it. It is
moot unless the VM comes back (see Sequencing). The NDK was installed for the
separate question of running a model natively on Android.

**Neither `cmake` nor the SDK's `ninja` is on `PATH`, and a second ninja is.**
`/opt/homebrew/bin/ninja` (1.13.2, Homebrew) is found first; the sdkmanager one
(1.10.2) sits beside cmake at
`/opt/homebrew/share/android-commandlinetools/cmake/3.22.1/bin/` and is not on
`PATH` at all. Name both by full path or the build silently uses Homebrew's.
The brew install was a mistake — the sdkmanager cmake package already ships
ninja — and it is recorded rather than undone.

**The APK is hand-built, not Gradle-built.** `probe2a/build.sh` runs the
five stages directly — `aapt2 link`, `javac` for the stubs, `javac` for
the app, `d8`, `apksigner` — all from `build-tools;37.0.0`, with
`JAVA_HOME` pinned to Temurin 21 inside the script. The stubs compile to
a separate directory and are passed to `d8` with `--lib`, exactly as
`android.jar` is: visible to the compiler, absent from the APK. **Since
rung 3b it is six stages, not five** — `aapt2 compile --dir res` runs ahead
of `aapt2 link -R`, because an app cannot be offered as the device assistant
by code alone and `res/xml/` is the one resource directory this spike has.
**Since rung 2d it is eight**, adding the guest payload and a separate
package-and-align stage: the payload `.so` must be Stored (`zip -0`) and
page-aligned (`zipalign -p`), because microdroid mmaps it out of the APK in
place rather than unpacking it. `PENNY_PAYLOAD_SO=<path>` packages a `.so`
built elsewhere — which is both the control seam AND the route every payload
here has actually used, compiled in the Debian guest on the phone. (An NDK is
now present on the Mac as of 15 Sept evening, but no payload has been built
with it and none needs to be.) **As of rungs 3f/3h there are FIVE payloads in the APK** —
`PennyPayload.so` (2d), `Penny3cPayload.so` (3c), `Penny3eiiPayload.so` (3e-ii),
`Penny3eiiiPayload.so` (3e-iii) and `Penny3fPayload.so` (3f and 3h together),
via `PENNY_PAYLOAD_SO`, `PENNY_PAYLOAD_3C_SO`, `PENNY_PAYLOAD_3EII_SO`,
`PENNY_PAYLOAD_3EIII_SO` and `PENNY_PAYLOAD_3F_SO`.
They are packaged side by side rather than one replacing the other: microdroid
loads only the file `setPayloadBinaryName()` names, extra entries cost nothing,
and every earlier rung stays reproducible from the same build. **Add a payload,
never edit one.** All five must read `Stored` in the final `unzip -lv`.
**`penny3f_payload.c` also builds the Debian-side control binary**, from the
identical source under `-DPENNY_CONTROL` — see rung 3f.
`PENNY_BLOB_MB=<n>` additionally packs an incompressible n-MB file as
`PennyBlob.so`, Stored and page-aligned — built for 3e-iii's APK escape hatch,
never needed because encrypted storage was the better answer, and left in place
because the packaging is proven and costs nothing to keep. The
script ends by grepping the built dex for `Landroid/system/virtualmachine/`
and printing the count, which must be 0 — if a stub ever shipped, the app
would carry a fake copy of a platform class and which one won would be a
coin toss worth losing. Nothing is downloaded and no build system negotiates versions with
anything. That is deliberate: rung 2 exists to read a precise runtime
refusal, and an AGP/Gradle/JDK version mismatch fails in a way that reads
as a permission or code error — the exact signal we are trying to measure.
It also sidesteps the JDK 26 trap below entirely. Rung 4 compiles inside
AOSP anyway, so a Gradle project was never going to survive.

`verifiedbootstate=yellow` is CORRECT here: locked, verifying against a
custom key. `green` would mean Google's key, i.e. stock.

**What rung 1 proved, on this locked device** (penny-box, commit d5a8371):
`adb shell /apex/com.android.virt/bin/vm run-microdroid` boots a VM as the
unprivileged `shell` user, no `pm grant` needed. `vm list` showed it at
`requesterUid: 2000` alongside the Terminal app's Debian VM at
`requesterUid: 10179` — two owners, enumerated together. Boot to
payload-ready 1.14s. The Debian guest did not notice: 55 samples over 54
minutes, no memory-pressure kill. **VM ownership is not the Terminal
app's private property.**

**What rung 1 did NOT prove.** What booted was `EmptyPayloadApp`, shipped
inside the virt APEX and platform-signed. That exercises
`MANAGE_VIRTUAL_MACHINE`, which carries `preinstalled` in its flags.
`USE_CUSTOM_VIRTUAL_MACHINE` — the one with no `preinstalled` escape, the
one our own guest image needs — has never been touched.

Also: the microdroid run logged `Using sample DICE values` because it was
a debuggable VM. **No attestation claim rests on anything proven so far.**

## The API question — ANSWERED. Do not research it again.

`android.system.virtualmachine` is `@SystemApi`. It is **not** in the
public SDK. In `packages/modules/Virtualization/libs/framework-virtualization/api/`,
`current.txt` (the public surface) is empty — one line, `// Signature
format: 2.0` — and all seven classes sit in `system-current.txt`.
`VirtualMachineManager` is annotated `@SystemApi` at class level and on
every public method. The module README: "All of these APIs were all
@SystemApi and require the restricted android.permission.MANAGE_VIRTUAL_MACHINE
permission, so they are not available to third party apps."

What keeps rung 2 alive, same README: "it can also be granted to other
apps via `adb shell pm grant` for development purposes."

Protection levels:

    MANAGE_VIRTUAL_MACHINE      signature|development|preinstalled
    USE_CUSTOM_VIRTUAL_MACHINE  signature|development
    DEBUG_VIRTUAL_MACHINE       signature

`development` means both can be granted over adb for prototyping without
an OS build.

**The real surface, read off the device on 14 Sept (rung 2a), not off a
document.** Six public methods on `VirtualMachineManager`, and these are
the signatures the 2b/2c stub classes must match exactly:

    VirtualMachine create(String, VirtualMachineConfig)
    void           delete(String)
    VirtualMachine get(String)
    int            getCapabilities()
    VirtualMachine getOrCreate(String, VirtualMachineConfig)
    VirtualMachine importFromDescriptor(String, VirtualMachineDescriptor)

All but `getCapabilities()` throw `VirtualMachineException`. You get the
manager with `context.getSystemService(VirtualMachineManager.class)` —
there is **no** `getInstance(Context)`.

For reference, the Terminal app (the uid 10179 VM owner) is built
`platform_apis: true`, `privileged: true`, inside the `com.android.virt`
APEX — a privileged, platform-signed system app. We are not that.

CONSEQUENCE: this cannot be compiled in Android Studio against the stock
`android.jar` — the classes are simply absent from it.

**The stub route is PROVEN as of 2b.** Five hand-written compile-only
stubs in `probe2a/stubs/android/system/virtualmachine/` were enough to
create and boot a VM with no reflection anywhere in the 2b path. The
signatures were not recalled — they were extracted from the device's own
`framework-virtualization.jar` by pulling it and running `dexdump -e` on
the Mac, which is far cheaper than a build-install-run cycle per guess
and is the method to reuse for 2c.

**DECIDED 14 Sept: reflection for 2a, self-written stub classes for
2b/2c.** The gate is enforced at runtime, not at compile time, and is
identical however the compiler was satisfied — so a 2a verdict reached by
reflection binds on every route. Reflection is therefore a cheap
throwaway probe; stubs (tiny signature-matching fakes, compile-only,
never packaged) are what we build on once the API is known reachable.
A third-party AOSP-built `android.jar` was considered and **rejected**:
Google does not distribute one, so it means an unofficial jar off GitHub
inside a project whose entire premise is a verified, attested device.

AOSP's `docs/custom_vm.md` describes its `vm run` custom-VM route as
needing root over adb. There is no root on a locked build, so **the CLI
is not a fallback if the app route fails. The app route is the only route.**

## Open questions, in order

**Rung 2 — can a sideloaded app own a VM?** Three separate results. Each
gets its own `notes.md` entry. Do not collapse them.

- **2a. Can a sideloaded app touch the API at all? ANSWERED YES.**
  `com.pennyspike.probe2a`, hand-built and sideloaded, **uid 10192**, not
  platform-signed and not privileged, obtained a live
  `VirtualMachineManager` and called it. Both `pm grant`s were accepted
  silently on GrapheneOS with the bootloader locked, and the app read
  both back as GRANTED from inside itself.
  **PARTLY CORRECTED BY 2c — read this.** 2a concluded "the non-SDK
  restriction worry is dead; `pm grant` is the only gate." That is true
  of the members 2a and 2b touched and **false in general.** The class
  does load from `BootClassLoader`, and the members on the 2b path are
  gated by permission alone — but the hidden-API blocklist is very much
  alive on this app and blocks other members of the same classes
  outright. See 2c. `pm grant` is the only gate *on the members that are
  not blocklisted*.
- **2b. Does it own a VM with Google's stock payload? ANSWERED YES.**
  `com.pennyspike.probe2a` created and booted a microdroid VM named
  `penny2b`. `vm list` reported `requesterUid: 10192`, `requesterPid`
  matching the app's process, cid 2050 — alongside the Terminal app's
  `debian` at 10179, two owners enumerated together. The payload was
  Google's own `MicrodroidEmptyPayloadJniLib.so`, read out of
  `EmptyPayloadApp.apk` in the `com.android.virt` APEX via
  `setApkPath()`; `onPayloadReady` fired ~1s after `run()`, so the guest
  genuinely booted rather than merely being registered. The VM's state
  lives under the app's OWN data dir
  (`/data/user/0/com.pennyspike.probe2a/vm/penny2b`), not the Terminal
  app's — VM ownership is per-app, and the Terminal app has no handle on
  it. Built with compile-only stubs, no reflection.
- **2c. Does it own a VM running OUR guest? ANSWERED NO — and not for
  the reason we expected.** A sideloaded app cannot reach the custom-VM
  API **at all**. Every member of it is on the hidden-API **blocklist**,
  so the call is refused inside our own process, by the Android runtime,
  before any binder call is made. `USE_CUSTOM_VIRTUAL_MACHINE` is never
  consulted. Measured 14 Sept with both permissions reading
  `granted=true`, so the permission is ruled out as the cause.
  Four members tried, four refused with `NoSuchMethodError`, while a
  known-good control (`setApkPath`) on the same builder in the same run
  succeeded: `VirtualMachineConfig.Builder.setCustomImageConfig`,
  `VirtualMachineCustomImageConfig.Builder.<init>`,
  `...$Partition.<init>`, `...$Disk.RODisk`. ART logged each one:
  `api=blocked ... domain=platform ... from ...base.apk (domain=app,
  TargetSdkVersion=37) using linking: denied`. No VM appeared in
  `vm list`, and there was no crash.
  **There are TWO gates and they are in series: the hidden-API gate in
  our process, then the permission gate in VirtualizationService. The
  first one is shut.** No amount of `pm grant` moves it — `pm grant`
  speaks to the second gate only.
  What this does NOT say: it says nothing about whether
  `USE_CUSTOM_VIRTUAL_MACHINE` would be granted, because nothing ever
  asked. That question is now unreachable from a sideloaded app and only
  rung 4 can ask it.
  The one experiment that could still separate "blocked for everyone"
  from "blocked for us" is flipping `settings put global
  hidden_api_policy 1`, which disables the blocklist device-wide. **Not
  done, deliberately** — it is a device-wide state change on a phone
  that has to go back, and it would prove nothing shippable, since no
  customer device will have it set. Raise it with Matt before ever
  doing it.
  Retained because it stays true and rung 4 will want it: a complete,
  Google-shipped, world-readable kernel and rootfs sit in
  `/apex/com.android.virt/etc/fs/` — `microdroid_kernel` (11MB),
  `microdroid.img` (32MB, system_a), `microdroid_vbmeta.img` (vbmeta_a)
  — and `/apex/com.android.virt/etc/microdroid.json` is the recipe that
  assembles them, mapping one-to-one onto
  `VirtualMachineCustomImageConfig.Builder`.

- **2d. Does it run OUR OWN CODE inside Google's microdroid? ANSWERED YES.**
  15 Sept. `com.pennyspike.probe2a`, uid 10192, sideloaded and throwaway-signed,
  ran a payload we wrote inside a VM it owns. Three independent signals, all
  present: the guest console carried our strings
  (`virtmgr: Console(2052): PENNY2D: our own payload is running inside the
  guest`), `onPayloadReady` fired — which happens only because our C calls
  `AVmPayload_notifyPayloadReady()` — and `onPayloadFinished` returned
  **exitCode=42**, which the stock payload cannot produce. A fourth, unplanned
  and the most convincing because no string of ours is involved: the guest's
  own clock reads 0.667s to 5.667s across our deliberate pause, 5.000s exactly,
  so the guest kernel was serving our `nanosleep`.
  **This does NOT contradict 2c and the two must not be conflated.** 2c wanted
  a custom guest IMAGE (our own kernel and rootfs) and is still NO — every
  member of that API is blocklisted. 2d changes nothing about the machinery:
  same unmodified microdroid, same two SDK-visible methods 2b already used
  (`setApkPath`, `setPayloadBinaryName`), merely pointed at our APK and our
  `.so`. **We cannot bring our own kernel; we do not need to.**
  Answered in two halves on purpose, and the ordering is the reusable lesson:
  a CONTROL run first, packing Google's own
  `MicrodroidEmptyPayloadJniLib.so` into our APK under our payload's name,
  which needed no compiler at all and settled the APK path, the throwaway
  signature, the `idsig` and the Stored/page-aligned packaging while the
  toolchain question was still open. Only then was our own C the single
  remaining variable, and it worked first time.
  Rung 3's `VmService` was deliberately NOT modified, so the proven wake
  result was never put at risk. **Payload-at-boot is therefore untested.**
  2d left the boundary one-way; **rung 3c opened it inwards — see below.**

**Rung 3 — does that app solve the wake problem? ANSWERED YES.** 14 Sept.
Rebooted, touched nothing, and the sideloaded app's VM was booted and
ready **15.1 and 15.9 seconds after power-on with the phone still at the
lock screen** — `userUnlocked=false` both times, disk still encrypted,
nobody in the room. **Measured twice, on two reboots, same APK.**
`vm list` confirmed `requesterUid: 10192` on both. Corroborated without
our own log: `/proc/<pid>/stat` field 22 = 1329 and 1381 jiffies
(CLK_TCK 100), i.e. the process started 13.3s and 13.8s after boot.
**Restart after a kill also YES, also twice**: `am crash` killed the
process, the system recreated the service with a null intent — no app,
no user, no broadcast — and the VM was back up in 1.44s and 1.51s.

The shape that works, and the two things that had to be right:

    BootReceiver   directBootAware, listens for LOCKED_BOOT_COMPLETED
    VmService      directBootAware, foregroundServiceType="specialUse",
                   returns START_STICKY, holds the VirtualMachine handle,
                   uses createDeviceProtectedStorageContext()

1. **`BOOT_COMPLETED` is the wrong broadcast.** On a phone with a PIN it
   does not fire at boot — it fires at FIRST UNLOCK. Measured twice, with
   two deliberately different waits: it arrived 95 minutes and 3 minutes
   after power-on, each time at the exact moment a human typed the PIN.
   It does not mean "booted", it means "somebody unlocked me".
   `LOCKED_BOOT_COMPLETED` is the one that tracks power-on, and only
   `directBootAware` components receive it.
2. **The VM's directory must live in device-encrypted storage.** The
   first attempt failed with `FileSystemException: /data/user/0/<pkg>/vm:
   Required key not available` — the normal app data dir does not exist
   until first unlock. See the Traps entry.

**Why this one matters commercially.** The four permissions the wake path
needs — `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`,
`FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS` — are all ordinary
permissions any Play Store app may declare. Unlike
`MANAGE_VIRTUAL_MACHINE`, none needs a cable. So the wake design proven
here survives into rung 4 unchanged; it is the only part of this spike
that is already shippable.

**Rung 3b — can the thing that wakes at boot also listen? ANSWERED YES.**
15 Sept. Rebooted, touched nothing, and the sideloaded app captured **real
audio 9.5 seconds after power-on with the phone still at the lock screen**,
`userUnlocked=false`, disk still encrypted. Corroborated by the OS's own
audio log, which is the part that matters:

    09:29:03:631 rec start riid:47 uid:10192 session:33 src:MIC
                 not silenced pack:com.pennyspike.probe2a

**"not silenced"** is the platform's word, and it is the exact inverse of
the documented failure mode. The PIN was not typed until 68.8s.

**Two attempt sites, and BOTH worked**, which is the commercially important
half:

    A-assistant   inside PennyVoiceService.onReady(), in a process the OS
                  itself started to bind the assistant.  9.5s, peak 557,
                  rms 198, 91.6% non-zero.
    B-fgs         an ORDINARY foreground service started from the boot
                  broadcast with foregroundServiceType="microphone".
                  12.3s, peak 670, rms 253, 90.6% non-zero.

**This overturns a constraint rung 3 inferred.** Same APK, same service,
same code, same broadcast — refused with a `SecurityException` on the
reboot where the app was not the assistant, accepted and recording on the
reboot where it was. So the exemption is conferred by holding the assistant
role and it reaches beyond the assistant's own process. **The wake service
and the listening service do NOT have to be two different things.**

Rung 3 did not regress: VM up at 13.97s, `userUnlocked=false`, `vm list`
`requesterUid: 10192`, cid 2048. Third reproduction.

**The eviction trap, which cost the first reboot and is in no document.**
On the first attempt the assistant was never bound at all. The role holder
and `settings secure assistant` both survived the reboot, but
`voice_interaction_service` came back EMPTY and `dumpsys voiceinteraction`
said `(No active implementation)`. Cause, read out of
`VoiceInteractionManagerService.initForUserNoTracing`: it reads
`VOICE_RECOGNITION_SERVICE` first, and if that is null the whole
"Current interactor/recognizer okay, done!" early return is skipped,
execution falls through to `findAvailInteractor(userHandle, null)` — which
never auto-selects a third-party app — and reaches
`setCurInteractor(null, userHandle)`. **Penny is wiped at every boot,
silently, unless a recogniser is set.** The fix is one attribute and one
setting:

    res/xml/recognition_service.xml   android:selectableAsDefault="true"
    settings put secure voice_recognition_service \
        com.pennyspike.probe2a/.PennyRecognitionService

Without `selectableAsDefault` the OS logs "Found non selectableAsDefault
recognizer as default. Unsetting the default" and evicts Penny again.

**Had we stopped after the first reboot we would have written rung 3b up as
NO, and it would have been wrong.** The cause was a device-state
precondition, not the hypothesis.

The two device conditions CLAUDE.md flagged as unread are both favourable,
measured before any code:

    ro.config.low_ram            EMPTY. MemTotal 5,718,280 kB. Not a
                                 low-RAM device, so the VoiceInteractionService
                                 branch is live.
    config_showDefaultAssistant  TRUE — read as bool 0x01110001 out of the
                                 device's own framework-res.apk with
                                 `aapt2 dump resources`. Framework default is
                                 FALSE, so this device deliberately shows the
                                 picker.

**REPRODUCED, and the user-tap route TESTED and FAILED. 15 Sept.** Four
reboots on one APK isolate the variable exactly:

    reboot 1  interactor set, recognizer NULL             evicted, no audio
    reboot 2  interactor set, recognizer SET              bound, REAL AUDIO
    reboot 3  interactor set by the SETTINGS UI by hand,
              recognizer NULL                             evicted, no audio
    reboot 4  interactor set by the SETTINGS UI by hand,
              recognizer SET                              bound, REAL AUDIO

Reboot 4: assistant bound at 9.1s, real audio at both sites with
`userUnlocked=false` (peak 1329 and 1201), OS Recording Activity again
`not silenced`, VM up at 14.0s. **The microphone YES is now measured on two
separate reboots; rung 3's VM wake on four.**

**The Settings UI sets the role, `assistant` and the interactor — but NOT
`voice_recognition_service`.** That one setting is the whole difference, it
is `Settings.Secure` (needs `WRITE_SECURE_SETTINGS`, signature|privileged,
or adb), and there is no user-reachable screen for it:
`android.settings.VOICE_INPUT_SETTINGS` just resolves back to the same
assistant picker. **So a user with no cable cannot put Penny in the
assistant slot in a way that survives a power cycle.** The microphone
result stands; the delivery route does not.

And eviction is permanent — `setCurInteractor(null)` writes an **empty
string**, not null, and both restore paths are guarded against `""`. Once
Penny is evicted it never returns on its own at any later boot.

Why the OS will not fill the recogniser in for us is **UNREAD — do not
assert it.** On AOSP main `findAvailRecognizer` cannot return null when any
recognition service exists (it falls back to non-selectable ones) and
`getAvailableServices` applies no privileged filter. This device does not
behave like main: Android 17 logs `no auto selectable voice recognition
services found for user 0` and returns null even with
`android:selectableAsDefault="true"` and the service resolvable. Ruled out
on the way: `BIND_RECOGNITION_SERVICE` does not exist as a platform
permission here and `RecognitionService` does not require one.

Worth testing before treating as universal: **GrapheneOS ships no speech
recogniser at all**, which is why the setting is empty. On a stock phone
Google's occupies it and the early return would preserve a user-chosen
assistant. This may be a GrapheneOS consequence rather than an Android one.
Untested — no stock device here. Rung 4 dissolves it either way: the OS
image sets its own default recogniser and can preinstall Penny as the
assistant.

Also unchanged: the guest VM still does nothing with audio. This measures
Android handing the app a microphone, not voice reaching Penny.

The DSP hotword route is confirmed closed, from the OS itself:
`PccSandboxManagerInternal: Package com.pennyspike.probe2a is not qualified
for hotword detection and can't start a PCC Process`. Do not spend time on
it.

**What rung 2 buys, and what it does not. Do not get this wrong.**
`pm grant` cannot ship. Both permissions are `development` protection
level, which means they can only be granted over adb, by a person with a
cable. There is no mechanism to grant them on a customer's device. So a
sideloaded app that owns a VM is **not a shippable product** — it is
proof that the VM machinery answers to an app rather than only to the OS,
and that proof is the thing that justifies spending weeks on rung 4.
**2c raised the stakes on this.** It is no longer only that the
permissions cannot be granted in the field: the custom-VM API is not
reachable from an app in the `app` domain at any permission level. Only
code the runtime treats as `domain=platform` — the system image and the
APEXes — is exempt from the blocklist. That is precisely what rung 4
builds, so rung 4 is now the **only** route to a custom guest, not
merely the commercial one. There is no sideloaded shortcut left to find,
and time spent looking for one is wasted.
**2d moved the ceiling back up, and it is the line worth saying out loud.**
A sideloaded, unprivileged, non-platform-signed app CAN run its own compiled
code inside a hardware-isolated VM on a locked, verified-boot Pixel. What it
cannot do is supply the guest image, or hold the permission without a cable.
So the remaining gap to a product is entirely about DELIVERY — who signs the
app and how the permission is granted — and no longer about whether the
machinery will carry our code. That is a much better position to raise on, and
it is still rung 4 that closes it.
Rung 4 is the commercial route: the app inside the OS image,
platform-signed, holding the permissions because it is part of the
system. At that point the app compiles inside AOSP against the real
system API, and both reflection and stubs disappear. Prove it cheaply
outside the OS; build it properly inside the OS. **Never mistake a
working `pm grant` prototype for a product.**

**Rung 3c — can anything get INTO the guest, and can audio make the trip?
ANSWERED YES, both halves.** 15 Sept. A sideloaded, unprivileged app captured
one real second of microphone audio and delivered it, intact and verified, into
a VM it owns running code it wrote.

    3c-i   55 bytes of ASCII, host -> guest -> host, identical, 2ms round trip
    3c-ii  32,000 bytes of real PCM, fnv1a 225c918f at BOTH ends,
           echo byte-for-byte, 12ms round trip

Corroborated from the guest's own console (cid 2054 and 2056), a channel the
host process does not write to: `PENNY3C: received 32000 bytes,
fnv1a=225c918f`. Guest exit code 43 both runs.

**The route, and it was forced, not chosen.** Read off the device's own
`framework-virtualization.jar` with `dexdump` before any code was written:

    connectVsock      (J)Landroid/os/ParcelFileDescriptor;   SDK
    getConsoleOutput  ()Ljava/io/InputStream;                SDK
    getConsoleInput   ()Ljava/io/OutputStream;               BLOCKED
    MIN/MAX_VSOCK_PORT = 1024 / 4294967295                   SDK

The console is the obvious way to push bytes at a guest and it is
**outbound-only from an app**. So vsock is not the better route, it is the
only one left. Note `connectVsock` takes a **long, not an int**.

The guest listens, the host connects, with a trivial wire format (4-byte
big-endian length, then bytes; reply is length + FNV-1a + the bytes echoed).
The payload is `payload/penny3c_payload.c` — still **no C library**, four raw
`AF_VSOCK` syscalls, 64KB static buffer in `.bss` because there is no malloc.
`AVmPayload_notifyPayloadReady()` is called **after** `listen()`, never before,
so the host cannot connect before there is a listener.

**`libvm_payload.so`'s own symbols could NOT be read and this is a real gap.**
It lives only inside `microdroid.img`, which is EROFS with that file's blocks
compressed: `grep -ac AVmPayload` over the 32MB image returns 0 while
`microdroid_manager` returns 22, so the image is only partly plaintext. Reading
it needs erofs tooling, i.e. a download. Not bought, because the only byte
channel it is understood to offer is a **binder RPC server** — which needs
libbinder_ndk, libc++ and generated AIDL, i.e. the C++ toolchain this spike
deliberately does not have. If rung 4 wants that route, the symbol list is
still unread.

**What 3c does NOT say.** It ran **unlocked, in the foreground, by hand over
adb**. Rungs 3 and 3b measured the locked, unattended, pre-unlock case; 3c did
not. Audio crossing the boundary AT BOOT with nobody in the room is untested
and is the obvious next thing. It was one second, once — no streaming, no
sustained capture, nothing ran longer than 3 seconds. And **the guest does
nothing with the audio**: it hashes it and echoes it. No recognition, no model,
no processing. "Audio reached the guest" is not "Penny heard you", and the gap
between those is most of the product.

`VmService` was NOT touched. 2d re-ran from the same APK afterwards and still
returns exit code 42, so nothing regressed.

**Rung 3d — does the whole chain run at boot, locked, with nobody in the room?
ANSWERED YES.** 15 Sept. All six conditions defined in advance, met on two
reboots, on one APK. The sideloaded app woke itself, took the microphone at the
lock screen, booted a VM it owns running code it wrote, and delivered one real
second of captured audio into that VM intact — **15.1s and 15.5s after
power-on, `userUnlocked=false`, the PIN not typed for another 134s and 101s.**

    1  userUnlocked=false AT THE EXCHANGE   both boots
    2  audio real on the samples            peak 1506/2723, 90.5%/90.9% non-zero
    3  the guest's OWN console fnv1a        cid 2049: ac4b8dcf / 312decbb, matched
    4  guest exit code 43                   both boots
    5  every timestamp before first unlock  134.5s and 101.3s of margin
    6  reproduced on a second reboot        yes

**The whole voice path now runs end to end without a human — minus the part
that understands what was said.** That last part is the largest unanswered
thing in this repo. See the open threads.

`Penny3dService` is a **COPY** of `VmService`, not an edit: `VmService` is
still byte-for-byte what rung 3 proved and it ran on both these reboots.
Three services now start from the same boot broadcast — `VmService`,
`MicFgsService`, `Penny3dService` — independent, none able to take the others
down. **FIVE as of the 3e-v/3g-i boots**, with `Penny3evService` (rung 3e-v)
and `Penny3giService` (rung 3g-i) added the same way, and all five were granted
the `duration:20000` exemption on that boot. **SIX in the APK now installed** —
`PennySoakService` was added afterwards (commit 543f9f0) and `BootReceiver`
starts it last. The "all five took the exemption" reading is from the boot that
had five; six on one boot is untested. The guest payload was **not rebuilt**: `Penny3cPayload.so` from 3c was
reused unchanged, same wire protocol, no new variable.

**Five things measured here that were not known before.**

- **One foreground service may declare `specialUse|microphone` and start at
  boot.** Rung 3 used specialUse alone, 3b used microphone alone. Combined,
  accepted every time. The wake service, the listening service and the VM
  holder can be the same object.
- **The assistant-role microphone exemption reaches a service that is also
  holding a VM.** 3b showed it reaches beyond the assistant's own process;
  this shows it is not narrowed by what else the service does.
- **Three simultaneous microphone captures in one app at boot all returned
  real audio** — ~10.7s, ~13.8s, ~14.3s. The contention worry was unfounded.
- **The microphone is still there at ~14s**, not only at the ~9.5s 3b measured.
- **Two VMs boot unattended before first unlock**, `penny3` and `penny3d`,
  both `requesterUid: 10192`, 256MB each on a 6GB phone.

Rung 3 has now reproduced on **six** reboots, rung 3b's microphone on **four**.
**Updated 15 Sept by rung 3e-iv: rung 3 is now SEVEN and rung 3d THREE**, both
picked up free on the 3e-iv reboot — audio at the lock screen with
`userUnlocked=false`, fnv1a `4362a8d0` matched, guest exit 43, and **494
seconds of margin before first unlock**, far the largest yet (3d's own runs had
134s and 101s).

**Updated again 15 Sept by rungs 3e-v and 3g-i, which reproduced four rungs free
across their two boots: rung 3 is now EIGHT, rung 3b's microphone SIX (two sites
on the answering boot) and rung 3d FOUR.** On the boot that answered 3e-v: rung
3's VM ready at 14,154ms; `MIC [A-assistant]` at 10,577ms peak 1329 and
`MIC [B-fgs]` at 13,214ms peak 1265, both ~90.9% non-zero; rung 3d's 32,000
bytes across at 15,258ms with exit 43. All with `userUnlocked=false` and 197.6
seconds before the PIN.

**The ordering decision, and it was written down before the run.** The
microphone is available at ~9.5s, the VM not until ~14s, so the audio exists
before there is anywhere to send it. Buffer early, or capture on
`onPayloadReady`? Took the second: `MicFgsService` is already capturing at
~12-13s on the same boot and a second `AudioRecord` then risked contention
that would look exactly like OS suppression; it keeps "no audio" and "audio
but no crossing" separable in the log; and asking later is the harder case.
Record this kind of choice BEFORE the run — a log cannot tell you afterwards.

**What 3d does NOT say.** The guest hashes the audio and echoes it; there is
no recognition, no model, no processing. It was one second, once, into a VM
that lived three seconds — no streaming, no endurance. Delivery is unchanged:
`pm grant` and `voice_recognition_service` both still need a cable. Still
`DEBUG_LEVEL_FULL`, still non-protected, still sample DICE values, so no
attestation claim rests on it. And two reboots is reproducibility, not
reliability — nothing was tested under memory pressure or on battery.

**Rung 3e-i — will microdroid give a sideloaded app a VM big enough for a
model? ANSWERED YES, with a ceiling.** 15 Sept. `com.pennyspike.probe2a`,
uid 10192, created and booted a VM with **2048MB and 8 vCPUs** and the guest
served a vsock round trip from inside it. Measured twice, nothing killed.

    256MB  ONE_CPU     ready +0.7s   nothing killed          (control)
    2048MB MATCH_HOST  ready +4.3s   nothing killed          x2
    3072MB MATCH_HOST  ready +5.1s   launcher, IME + 3 more killed
    4096MB MATCH_HOST  NEVER ready   OUR OWN APP killed
    6144MB MATCH_HOST  NEVER ready   (above physical memory)

**Nothing ever refused.** The builder and `VirtualizationService` accepted
every figure, 6144MB included — more than the phone physically has —
returning `STATUS_RUNNING` in 10-22ms each time. There is no API cap, no
permission check on size, and no sanity check against physical memory.
**The ceiling is Android's low-memory killer and it arrives silently.** At
4096MB it reached our own foreground TOP process; killing the app kills the
handle, which kills the VM, so the probe died before its own watchdog could
fire. **The usable figure on this phone is 2GB** — largest tested that
booted, served and killed nothing — and that is headroom ALONGSIDE rung 3's
and 3d's 256MB VMs, which were up throughout. The Terminal app's Debian VM
was NOT running; it holds 3.6GB when it is.

Dex flags read before any code, the method that has now been right 10 times
out of 10: `setMemoryBytes` (J), `setCpuTopology` (I),
`CPU_TOPOLOGY_MATCH_HOST`, `CPU_TOPOLOGY_ONE_CPU` are all
`hiddenapi 0x0020 (SDK,TEST-API)` — the same flag `DEBUG_LEVEL_FULL` and
`setApkPath` carry. `setMemoryBytes` takes a **long**.

Corroborated twice over, neither written by our app. The hypervisor's own
command line: `"--mem", "2065"` and `"--cpus", "sve=[auto=true]"` with no
`num-cores` at all at MATCH_HOST, against `num-cores=1` and `--mem 273` at
the control. And the guest kernel's own reading, since microdroid sizes zram
to its RAM — three guests alive at once, `Adding 2038096k swap` in penny3e
next to `Adding 242896k` in penny3 and penny3d. vCPU count came from the
host kernel (`ps -AT` shows `crosvm_vcpu0`..`vcpu7`), because microdroid's
console pipe attaches after SMP bringup and the guest's own CPU count is not
in the log at all.

**What it does NOT say.** ~~Being GIVEN 2GB is not being able to USE it: on
the first 2GB run the host surrendered only ~660MB of the 2048MB granted.~~
**ANSWERED AND PARTLY CORRECTED BY 3e-ii — see below.** The memory is real
(1792MB written and read back inside a 2048MB VM, twice), and the ~660MB
reading was an artefact of the low-memory killer freeing memory in the same
moment: the host in fact pays ~1.92GB up front, at VM creation. ~~Nor is it
known where a
model FILE would live — 3d logged `init: Unknown /data fs type: tmpfs` and
every guest here built a zram swap sized to its whole RAM, so a 1.5GB model
file may cost 1.5GB of RAM on top of running it.~~ **ANSWERED NO BY 3e-iii —
a model file costs no permanent RAM; see below.** Nothing ran inside the VM: the payload is 3c's, unchanged, and uses
no second CPU. And this was unlocked, in the foreground, over adb — a 2GB
VM takes ~4s longer to reach ready, which eats into the 20-second
foreground-service exemption, so the boot case is untested.

**Rung 3e-ii — is the memory REAL? ANSWERED YES, and it corrects 3e-i.**
15 Sept. A sideloaded app's guest **wrote, read back and held 1792MB inside a
2048MB VM — measured twice**, at 1.78-2.15 GB/s, with `status=0` and not one
wrong byte in 5.1GB of verified traffic.

    VM      asked    written AND verified   guest ms   outcome
    256MB    64MB     64MB                     271     clean (CONTROL)
    2048MB 1536MB   1536MB                    1410     clean
    2048MB 1792MB   1792MB                    2014     clean
    2048MB 1792MB   1792MB                    1664     clean, REPRODUCED
    2048MB 1920MB   1856MB then wedged          --     kernel live-lock,
                                                       VM down at 94s

A 2048MB config gives the guest `MemTotal: 2038100 kB` (~57MB hypervisor
overhead). Touching 1792MB moved guest MemFree by 1,840,124 kB against
1,835,008 kB asked — **one for one**, which is the proof that zram absorbed
none of it.

**The pseudo-random fill is the decision the whole result rests on, and it
was written down before the run.** microdroid gives every guest a zram swap
device sized to the guest's WHOLE RAM (`Adding 2038096k swap on
/dev/block/zram0` in a 2038MB guest). zram is compressed swap in that same
RAM, so a payload filling pages with zeros would have reached 2GB having
proved nothing at all. `payload/penny3eii_payload.c` fills every byte of
every page with xorshift64* output and reads it all back.

**THE CEILING IS A LIVE-LOCK, NOT AN OOM KILL, and it is silent.** See the
trap entry. Ceiling is between 1856MB and 1872MB; **build on 1792MB, 88% of
the guest's RAM.**

**crosvm takes the memory AT VM CREATION, not lazily** — the correction to
3e-i. On the clean run: host MemFree 2,257,952 kB before the VM existed,
288,756 kB at `onPayloadReady` with the guest not yet having touched a page,
137,088 kB after 1792MB of guest writes. **~1.92GB left the host before the
guest asked for anything**; the guest's own writes then cost a further
~150MB. So there is no hidden gap between granted and paid-for, and nothing
can quietly fail later because the host over-promised.

Booting a 2048MB VM drives the low-memory killer **every time**. The first
run killed thirteen processes, including the Terminal app's Debian VM which
had been opened fifteen minutes earlier to compile this payload. Our own app
was never killed at 2048MB.

Its own component throughout: `Probe3eiiActivity`, VM `penny3eii`, and a
THIRD payload in the same APK (`PENNY_PAYLOAD_3EII_SO`). `VmService`,
`Penny3dService` and `Probe3eActivity` untouched — rung 3d logged exit code
43 mid-run, so nothing regressed.

**What it does NOT say.** Nothing RAN: 1.8GB written and verified is memory,
not compute, and the payload is single-threaded, so it used one of the 8
vCPUs. The ceiling is with an EMPTY guest — 1792MB left 88MB free with only
init and our payload in it, and a runtime eats into that. Residency was two
seconds, not two hours. Unlocked, foreground, over adb. ~~And where a model
FILE would live is **rung 3e-iii, NOT RUN**, now more urgent rather than
less: the guest's `/data` is `tmpfs` and zram already claims a swap device
the size of the whole guest, so a 1.5GB model file may cost 1.5GB of RAM
before anything loads it, against a measured ceiling of 1792MB.~~
**ANSWERED BY 3e-iii AND THE FEAR WAS UNFOUNDED.** The `/data` reading was
right and irrelevant: `setEncryptedStorageBytes` gives the guest a real ext4
disk instead, a 1.5GB file on it costs no permanent RAM, and this same 1792MB
ceiling still holds with that file mapped and resident. See below.

**Rung 3e-iii — where does a model FILE live? ANSWERED YES, and the answer is
better than the question.** 15 Sept. A sideloaded app's guest wrote a **1536MB
file to a real, persistent, encrypted ext4 disk**, and then touched and
verified **1792MB of anonymous memory on top of it** — 3e-ii's exact ceiling,
unchanged. **A model file does not come out of the guest's RAM budget.**

**The first finding was that the question was wrong.** There is no writable
filesystem in a default microdroid guest at all. `/data` IS tmpfs — confirming
3d's inference — and is capped at `size=131072k`, 128MB. But the payload cannot
write there anyway. Six locations probed in one run, every one refused:
`/data`, `/mnt`, `/mnt/androidwritable`, `/dev` all `EACCES`; `/tmp` `ENOENT`;
a relative path `EROFS`, because the working directory is the read-only erofs
root. No SELinux denial was logged for any of them — the payload simply is not
root. `/` and `/mnt/apk` would not even list.

**The thing that was missing was found by reading the dex, as ever.**
`VirtualMachineConfig.Builder.setEncryptedStorageBytes(J)` is
`hiddenapi 0x0020 (SDK,TEST-API)`, the same flag `setApkPath` and
`setMemoryBytes` carry, so an app in the `app` domain may call it. **That
method has now been right 11 times out of 11.** Takes a **long**. One extra
line in the config and the guest gains:

    /dev/block/mapper/crypt  /mnt/encryptedstore  ext4  rw,discard

**It is a real disk, and the proof does not rest on a figure.** A **256MB**
guest wrote a **384MB** file — 1.5x its own total RAM — at ~149 MB/s, with
MemFree oscillating rather than falling as the kernel wrote back and reclaimed.
3e-ii measured this guest's RAM at 1.78-2.15 GB/s.

    VM      storage   step                          result   guest ms
    2048MB  2048MB    write 1536MB to the store      1536MB      7321
    2048MB  2048MB    mmap it, fault every page      1536MB       163
    2048MB  2048MB    touch+verify anonymous         1792MB      1797

All `status=0`. Across the anonymous touch, guest MemFree went 280748 -> 84740
kB: the kernel dropped 1.6GB of page cache to supply it, which is exactly what
clean file-backed pages are for and exactly what anonymous pages cannot do.

**It PERSISTS across VM instances and a cold read runs at 835 MB/s.** Run A
wrote the file and exited; run B was a NEW VM on the SAME store, found the file
at 1610612736 bytes, faulted all 1536MB in from disk in 1841ms with `Cached`
climbing 105460 -> 1547180 kB, then touched and verified 1792MB anonymous.
Second reproduction of the combined ceiling, from cold. `--ei keep 1` skips
`vmm.delete()` and uses `getOrCreate`; without it the store dies with the VM.

**The APK escape hatch is moot and was deliberately NOT run.** It was worth
testing only if writable storage cost RAM. Encrypted storage beats it on every
axis — writable, persistent, encrypted, and it does not add its size to every
install. `build.sh` keeps the unused `PENNY_BLOB_MB` machinery in case rung 4
wants a read-only shipped file.

**One build answered nine experiments.** `penny3eiii_payload.c` is a COMMAND
SERVER, not a single-shot — the host sends a sequence of commands down one
vsock connection and the guest keeps its state between them, so "what is left
AFTER a file exists" is askable at all. The plan is an intent extra
(`--es plan "3,1536,/path;?4,1792,"`, steps `cmd,mb,path` separated by `;`, a
leading `?` marking a probe whose failure does not stop the plan). Only the
Java was rebuilt between experiments, and Java needs no phone. **Do this again
for anything that needs a guest payload.**

Its own component throughout: `Probe3eiiiActivity`, VM `penny3eiii`, a FOURTH
payload in the same APK. `VmService`, `Penny3dService`, `Probe3eActivity` and
`Probe3eiiActivity` untouched — rung 3d logged exit code 43 mid-run and not one
`has died` line appeared all session.

**What it does NOT say.** Nothing RAN — 1.5GB written, read back and mapped is
a file, not a model. **How the model gets INTO the store is untested and is a
real gap**: the store is encrypted and keyed to the VM, so the host cannot
write it and the guest must. 3c proved bytes cross inwards over vsock; pushing
1.5GB that way has never been tried. Persistence was across two VM instances
minutes apart, **not across a reboot**. 835 MB/s is one cold read on an idle
phone. Unlocked, foreground, over adb. Still `DEBUG_LEVEL_FULL`, still
non-protected, still sample DICE values.

**Rung 3e-iv — does the encrypted store survive a REBOOT? ANSWERED YES.
Can it be read BEFORE first unlock? NOT ANSWERED — and unaskable the way it was
written. Carried forward as 3e-v.** 15 Sept.

After a full power cycle, a NEW VM on the SAME encrypted store found
`model.bin` at **exactly 1610612736 bytes**, the figure 3e-iii wrote, and
faulted in all 1536MB from cold in 2570ms (~598 MB/s), `status=0`, guest exit
code 45. Cold on BOTH sides and that is the part that matters: the phone had
rebooted eight minutes earlier so the host cache held nothing (host `Cached`
1120240 -> 3348232 kB across the read), and the VM was new so the guest's was
empty too (guest `Cached` 105392 -> 1547132 kB, climbing in lockstep with the
fault-in). The bytes came off UFS. **A model shipped into that store does not
have to be re-fetched at every boot.** No rebuild and no reinstall — `pm path`
confirmed the same APK either side, so `getOrCreate` found a valid stored
config.

3e-iii's 835 MB/s and this 598 MB/s are two single cold reads, the second on a
phone still settling after boot with ~20 processes being killed around it.
"Several hundred MB/s", not a regression.

**CONTENT WAS NOT VERIFIED and 3e-iii asked for it.** The payload checks SIZE
and that every page faults in; it does not checksum. A store returning
1610612736 bytes of zeroes would have logged identically. Truncation and
absence are definitively ruled out, contents are not. **DONE, and only half a
win — see 3f/3h.** `CMD_VERIFY` is in that payload and was exercised on files
up to 1.5GB, so the method exists. But the reinstall that build needed stranded
`penny3eiii`'s store, so THIS file can never be checksummed. The question is
now asked of a new file instead: `--ei keep 1`, a reboot, `--ei keep 1` again.

**Why the second half could not be asked, and it generalises.** The plan was
the same `am start` at the lock screen. Two obstacles, and the second outranks
the first. `Probe3eiiiActivity` is **not `directBootAware`** (nor is the
`<application>` tag), so the OS would have hidden it pre-unlock. But the
command could never have arrived at all: **GrapheneOS keeps the USB port
charging-only while locked** — polled every 5s for two minutes after `adb
reboot`, `adb get-state` said "no devices" every time and
`system_profiler SPUSBDataType` counted **zero** devices on the bus. The port
woke the instant the PIN was typed. The read finally ran **34 seconds AFTER
first unlock**, which is a different question and is written up as such.

**Nothing on this device can be measured pre-unlock by sending it a command.**
Any pre-unlock question must be asked by a component that starts ITSELF at
boot and writes its answer to logcat, read back after an unlock. That is the
shape rungs 3, 3b and 3d used; it was forced, not convenient.

**Rung 3e-v — is the encrypted store readable BEFORE first unlock? ANSWERED
YES.** 15 Sept. **The store is NOT tied to the user's credential.** A
`directBootAware` service woke itself from `LOCKED_BOOT_COMPLETED`, created a VM
with an encrypted store attached, opened a file an earlier run had put there and
read back all **67,108,864 bytes, ck64 `0x757b795dd5138044`, 14.4 seconds after
power-on with `userUnlocked=false` and the PIN not typed for another 197.6
seconds.**

    sinceBoot  12,059 ms   startForeground(SPECIAL_USE) accepted
    sinceBoot  14,279 ms   onPayloadReady, userUnlocked=false
    sinceBoot  14,431 ms   67,108,864 B  ck64 0x757b795dd5138044
                           expected 67,108,864 / 0x757b795dd5138044
                           SIZE MATCH, CONTENT MATCH
    sinceBoot 211,992 ms   LockSettingsService: unlockUser started

**This was the one that could still have closed the plan down.** Had the key
been credential-tied, no model could be read at boot and rungs 3, 3b, 3c, 3d and
every other unattended result would have applied only to a phone somebody had
already unlocked once. They all keep their meaning.

**The checksum is checked by the machine, not by reading two logs.**
`Probe3fActivity`'s generator is a fixed-seed xorshift64* that advances per
8-byte word and never resets at a chunk boundary, so a file of a given length
has exactly ONE correct ck64. `Penny3evService` runs the identical generator
with a null output stream — producing nothing, computing only — and compares.
The expected value is DERIVED, never copied out of a log. **That also closes
3e-iv's loose end** (content across a reboot, not merely size) and closes it
before first unlock, which is more than the loose end asked for.

**Corroborated from the guest's own console, cid 2049**, which the host process
does not write to: `ext4 filesystem being mounted at /mnt/encryptedstore` at
guest time 1.248s, then `PENNY3F: verify ... read 67108864 bytes (stat said
67108864), ck64 0x757b795dd5138044, 147 ms` at 1.426s.

**THE CONTROL RODE THE SAME SOCKET, AND THAT WAS FORCED.**
`penny3f_payload.c` calls `accept4()` exactly ONCE and then loops until a
zero-length frame, so a second `connectVsock` would never be accepted. The
socket was held open across the 197-second wait and the after-unlock read went
down the same one: `67,108,864 B  0x757b795dd5138044`, identical. Between the
two reads literally nothing changed but the PIN — the strongest form this
control could take, and the payload chose it, not us.

**What it does NOT say. A store was never CREATED before first unlock.** Boot 1
tried and died on the stale-config trap below; the file read here was written on
an unlocked phone during the diagnostic run. So "re-open an existing store
pre-unlock" is YES twice over, and "create a NEW store pre-unlock" — the
first-boot-after-factory-reset case — is untested. It costs one constant and two
reboots. And nothing RAN: 64MB read back and checksummed is a file, not a
model.

**Rung 3f — is the guest CPU real? ANSWERED YES.** 15 Sept. Eight vCPUs
carrying genuine physical core identities; single-core at parity with a
known-good Linux on the same silicon on the same afternoon; 4.2x aggregate
across eight threads, within 1% of what the same binary achieves in Debian.

**The control is the SAME FILE, and that is the reusable part.** One source
builds both binaries — a microdroid payload by default, a static Debian
executable under `-DPENNY_CONTROL`. Same gcc 14.2.0, same `-O1`, same flags,
and no C library in either, so both make the same raw aarch64 syscalls. The
syscall ABI belongs to the kernel and is identical above glibc, above bionic
and above nothing. Nothing differs but the operating system underneath.
**It is NOT a bare-metal control** — Debian is itself a guest in the Terminal
app's VM — and must never be written up as one.

    INT 200M iters, best of 3      microdroid 366 ms   Debian 411 ms
    FP  200M iters, best of 3      microdroid 437 ms   Debian 434 ms

**Both environments returned bit-identical results** — INT `0xae0c3710f848e024`,
FP `0xc316fb657f0b8495`. Same arithmetic to the last bit in two operating
systems. That is not a timing claim; it is proof the same code ran, which no
millisecond figure gives on its own.

    INT, 200M iters PER THREAD     microdroid          Debian
    threads   wall ms / M-iters/s
    1         445 / 449            585 / 342
    2         540 / 740            480 / 833
    4         583 / 1372           558 / 1434
    8         855 / 1871           849 / 1884
    8 again   905 / 1767  838 / 1909

**Eight threads is ~4.2x one thread, and 8x was never on the table** — four of
the eight cores are Cortex-A55s at roughly a third of an X1's throughput. The
per-thread spread IS that heterogeneity: at 8 threads the guest's own console
logged 597..836 ms (INT) and 606..949 ms (FP). Eight near-identical times would
have been the surprise, because it would mean `CPU_TOPOLOGY_MATCH_HOST` was a
number in a config file rather than eight usable cores.

**The guest's `/proc/cpuinfo` IS readable from the payload, and it gives more
than a count.** This is the answer to the trap that says the CPU count is not
in the guest console — true of the console, false of `/proc`.

    guest processors 0..7   parts: d44 d05 d0b d0b d0b d0b d05 d05
    host  processors 0..7   parts: d05 d05 d05 d05 d0b d0b d44 d44

`0xd44` Cortex-X1, `0xd0b` Cortex-A76, `0xd05` Cortex-A55. **The mixes do not
match and that is the finding**: the host is the real 2+2+4 Tensor; the guest
reported 1+4+3. Each vCPU is an unpinned host thread and the guest kernel reads
`MIDR_EL1` once per vCPU at boot, so the identity recorded is wherever that
thread happened to be sitting. **"8 vCPUs" is eight unpinned threads on a
heterogeneous host, not a topology.** Nothing in the guest can pin them.

**THREADS WITH NO C LIBRARY WORK, and microdroid does not refuse `clone`.**
Budgeted as the whole difficulty; worked first time. mmap a stack, `clone()`
through an eighteen-instruction assembly trampoline, join by polling a shared
flag. Four things that each cost a round trip if wrong, all in
`payload/penny3f_payload.c`:
- aarch64 takes clone's arguments in **CLONE_BACKWARDS** order — flags, stack,
  parent_tid, TLS, child_tid. NOT the x86-64 order.
- The trampoline **cannot be C**: the child returns on a brand new stack with
  no return address, so a C function would `ret` into nothing.
- Thread stacks are **allocated and never freed** — a child sets its done flag
  and then calls exit, and unmapping under it is a race worth nobody's time.
- **`dmb ish` before the done flag.** aarch64 is weakly ordered; `volatile`
  constrains the compiler, not the processor.

**What 3f does NOT say. "The CPU is real" is not "a model will run well."** A
dependency-chain benchmark says the processor issues instructions at the rate a
real Cortex does. It says nothing about memory bandwidth under a real working
set, cache behaviour, or NEON/dot-product throughput — both kernels are scalar
and single-issue by design, because the job was to catch a fake CPU, not to
profile a real one. The guest's features line advertises `asimd`, `asimddp` and
`fphp`; nothing here touched them. Single-thread figures are a scheduler
lottery (531/397/366 on the same kernel). All of it on an idle phone.

**Rung 3h — can a GIGABYTE be pushed into the guest? ANSWERED YES.** 15 Sept.
**1,610,612,736 bytes crossed into the guest over vsock and landed on the
encrypted store intact, in 15.4 seconds** — 99 MB/s end to end including
`fsync`, with the channel alone sustaining 268 MB/s. Same 1536MB figure 3e-iii
wrote and 3e-iv read back, arriving by a different route.

    size              guest ms   guest MB/s   INTACT
    32,768 B                 4   --           yes
    67,108,864 B          1479   43           yes
    1,610,612,736 B      15401   99           yes

Verified at THREE independent points each — what the host sent, what the guest
received, and what came back off the disk afterwards (`ck64` 1536MB
`0x1695ce2a1dce440a`). Read-back from the store ran at 2206 MB/s warm.

**THE CONTROL IS THE RESULT WORTH QUOTING: a 256MB guest took the same 1536MB
— six times its own total RAM — intact, at 73 MB/s.** The guest holds exactly
one 1MB chunk in `.bss` and writes straight through, so it cannot buffer by
accident, and its memory proves it did not: MemFree **oscillated** (60780 ->
29148 -> 42032 -> 46540 kB) while Cached stayed near 110MB, the kernel writing
back and reclaiming continuously. That is the shape that says this scales DOWN,
not merely that it worked once at a comfortable size. The 2048MB guest simply
let page cache grow to 1.68GB instead — same result, lazier route.

**The checksum is NOT 3c's** and its numbers must never be compared with 3c's
or 3d's. FNV-1a over 64-bit WORDS, not bytes: byte-at-a-time is a serial
multiply chain costing several seconds over 1.5GB at each end, charged straight
to the throughput figure.

**3e-iv's loose end is HALF closed.** `CMD_VERIFY` reads a file back and
checksums it, and was exercised on files up to 1.5GB — so the method gap is
shut. But **3e-iv's own file is gone**: this build's reinstall stranded
`penny3eiii`'s store, as authorised. Whether a file survives a reboot with its
CONTENT intact rather than merely its size is now cheap to ask and **has not
been asked** — one run with `--ei keep 1` to write and checksum, a reboot, one
more to read and compare.

**What 3h does NOT say.** One transfer, once, on an idle unlocked phone over
adb. No transfer under memory pressure, none interrupted and resumed, and the
1536MB case has one reproduction of the RESULT but not of the figure. **Where
the bytes come FROM is still unanswered** — this pushed bytes the host
manufactured; a real model arrives over a network, and nothing here measures
that, or where it is staged on the host, or what it costs.

**Rung 3g-i — will a 2GB VM start at boot, locked, with nobody in the room?
ANSWERED YES.** 15 Sept, on two reboots, with a 256MB control on each.

    boot   VM      ready sinceBoot   into the attempt   userUnlocked   guest MemTotal
    1      256MB       59,133 ms           1,381 ms        false          239,796 kB
    1      2048MB      65,688 ms           4,746 ms        false        2,038,164 kB
    2      256MB       17,012 ms           1,901 ms        false          239,796 kB
    2      2048MB      23,519 ms           4,639 ms        false        2,038,164 kB

`CMD_INFO` answered **`status=0`, which is the success signal**, and the guest
exited 46 every time. **Exit 46 is an identifier, not a verdict** — it says
which payload path ran and would be logged just the same by a run that answered
nothing. Read `status=0`. Margins
before first unlock: 171.9s and 188.5s. **The guest's own
`MemTotal: 2038164 kB` is the reading that matters** — not our process saying
2048MB was accepted, but the guest kernel saying it was delivered.

**THE RUNG WAS MISFRAMED AND THE CORRECTION IS THE REUSABLE PART.** It was
defined as "can a 2GB VM make the 20-second exemption, given it reaches ready
~4s slower". **That is the wrong reading of the window.**
`Background started FGS: Allowed ... duration:20000` governs `startForeground()`
and nothing after it; every service here calls it as the FIRST statement of
`onStartCommand` and only then hands VM work to another thread. Measured:
`Penny3evService` reached it 33ms in, `Penny3giService` 2ms in, and **all five
foreground services then in the APK took the exemption on the same boot** —
itself untested before. The APK now installed starts **six**; the sixth,
`PennySoakService`, has never taken the exemption on a boot that was not void. A VM that reaches ready four seconds later cannot miss a window it was
never racing. Had the rung been run on its own terms it would have produced a
reassuring non-answer.

**The control is on the same boot, from the same service, and ONLY the memory
figure differs** — same broadcast, same payload, same `MATCH_HOST`. 256MB first
so it is banked before the risky one, and waited out to `onStopped` so its
memory is genuinely back.

**The low-memory killer was gentler than expected, and the shape is a finding.**
Our app was never killed on either boot.

    boot 1   2GB VM started at 57.7s   ZERO kills
    boot 2   2GB VM started at 15.1s   4 kills, all adj 905, all cch CEM

Against 3e-ii's thirteen on an idle unlocked phone. **The variable is WHEN the
2GB VM starts**, and it was accidental: `Penny3giService` waits for 3e-v's
verdict or a 45s timeout, and 3e-v failed on boot 1 so the timeout ran. Starting
a 2GB VM while the boot is still settling costs four cached processes; a minute
later costs none. Every casualty was `cch` — cached and empty.

**This joins the two halves that had never been in the same room:** the
unattended wake and the memory a model needs. A VM big enough to hold a model
wakes on its own at boot, on a locked phone, and 3e-v says it can read a model
out of encrypted storage while it is there.

**What it does NOT say.** Nothing RAN — `CMD_INFO` is not a workload. Both boots
were an idle phone with nothing open and the Debian VM down. Two boots is
reproducibility, not reliability.

**Rung 3g-ii — what happens on a phone somebody is using? ANSWERED: the VM does
NOT kill what is on the screen. It evicts everything behind it, and the
keyboard.** 15 Sept, three runs on one APK, no build and no reboot.

    run  phone state   foreground app   kills   deepest adj   our app   camera
    A    idle          our own probe      37       905        survived    --
    B    in use        our own probe      16       201        survived   KILLED (700)
    C    in use        the CAMERA         14       201        survived   SURVIVED (0)

**Our app was never killed in any run**, and the guest got `MemTotal:
2,038,100 kB` every time. The killer walks strictly from the cheapest tier down.
On an idle phone it eats its fill of cached processes and stops at 905; on a
loaded phone it clears the cached band and then takes
`com.android.inputmethod.latin` at **adj 201** (`prcp IMPB`) — and stops there.
**201 twice is the finding: it does not enter the foreground band (200/100/0).**
The keyboard restarts itself immediately. In run C it died **1.9s AFTER
`onPayloadReady`**, so the pressure does not end when the guest is ready.

**Run B's dead camera is an artefact of the probe, and run C is what proves
it.** `Probe3fActivity` is an ACTIVITY, so starting it takes the screen and
demotes whatever was there to "previous app" (adj 700) — the camera died with
reason `prev LAST`, killed as the app behind, not the app in front. Run C
started the same 2048MB VM from `Penny3giService`, which never touches the
screen, and the camera sat at adj 0 and lived. **Every one of run C's 14 kills
landed AFTER the 2048MB VM was created** (first at +2.8s); its 256MB control
caused none. So the casualties are attributable to the 2GB VM and nothing else.

**A 2GB VM is SLOWER under load**: ready in 7,424ms in run C against 4,262ms for
the same service on the same phone forty minutes earlier, and ~4.3s in 3e-i's
idle figures.

**The margin is ONE TIER and it is not a law.** 3e-i killed our own foreground
TOP app at 4096MB, which took the VM handle with it. 2048MB stopped short twice.
**2GB works on this handset and 4GB does not.**

**What it does NOT say.** Every VM here lived about seven seconds and exited 46
— and 46 is an identifier, not a success signal; `status=0` is the one that
says the command worked —
a 2GB VM held open for hours while somebody uses the phone is untested and is
the actual product shape. Nothing RAN; `CMD_INFO` reads `/proc/meminfo` and
exits. AC power, screen on, Debian VM down, one afternoon. And **a stripped
phone was NOT tested — that is a rung 4 question.** Two things to carry into it:
most of what died is the operating system, not user apps
(`settings`, `permissioncontroller`, `media`, `acore`, `externalstorage`,
`keychain`, `rkpdapp`, `packageinstaller`, the IME — and `rkpdapp` is remote key
provisioning, part of the attestation story that justifies the phone); and fewer
apps is not more headroom but fewer cheap victims — the idle phone had 37
disposable cached processes and never went below 905, the loaded phone had fewer
and went to 201. Which way that nets out is untested and must not be asserted.

**Sequencing — REVISED 15 Sept (evening). Both remaining handset questions are
PARKED, and the work has moved to measuring a model natively on Android.**

- **Can a NEW encrypted store be created before first unlock? PARKED, and the
  condition that reopens it is NARROW.** 3e-v answered the re-open case twice;
  the create case is untested, because boot 1 died on the stale-config trap and
  the file it eventually read had been written unlocked. This is the
  first-boot-after-factory-reset case. **Parked because it only matters for a
  store inside a VM, and running the model inside a VM is CLOSED on today's
  evidence.** Three grounds, all measured in this repo: crosvm takes ~1.92GB
  from the host at VM creation (3e-ii) against a total `MemAvailable` of
  2,012,348 kB idle at 60.5 min uptime (2,119,020 kB at 25.3 min, 940,640 kB
  at 5.8 min); the guest cannot pin cores,
  because each vCPU is an unpinned host
  thread and the guest's MIDR mix does not match the host's (3f); and
  `getCapabilities()` returns 2, so this device cannot make a PROTECTED VM and
  the host can read the guest anyway. **A poor native benchmark does NOT reopen
  it** — the VM pays all three of those costs on top of whatever native costs,
  so it cannot be the better answer to a speed problem. **The only thing that
  reopens it is model isolation becoming a requirement.** If it does, this
  question comes back unchanged and still cheap: one constant (a new VM name in
  `Penny3evService` — `penny3ev2`, a name that has never existed, so
  `getOrCreate` has no prior directory), one build, and two reboots.
- **Endurance. BUILT, RUN ONCE, AND THAT RUN IS VOID.** `PennySoakService`
  exists and is committed (543f9f0) — a `directBootAware` copy of
  `Penny3evService` with its own VM and store, which brings the VM up, connects
  ONCE and sends `CMD_VERIFY` down the SAME socket every 5 minutes, logging
  elapsed time, checksum, host `/proc/meminfo`, guest MemFree and battery. It
  needs no new guest C. **Its only run was on a store contaminated by a
  null-intent sticky restart and nothing is claimed from it.** Matt's answers
  to the design questions stand: AC power, 2048MB, duration still open. A clean
  re-run needs a fresh VM name and a guard that tests the PHONE rather than the
  intent — see the trap. **Still true that nothing in this repo has run longer
  than about twenty seconds, and nothing has been tested on battery.**
- **Can this phone run a small model natively on Android, outside any VM?
  ANSWERED YES, 16 Sept. DONE — see the closing entry at notes.md 7543.**
  Fifteen `-lm none` rows across three models on a Pixel 6a: **Qwen3-1.7B
  Q4_K_M generates 14.1-14.9 t/s pinned to the X1 pair at 1.35-1.42 GiB peak
  RSS; Qwen3.5-2B 10.9-11.2 t/s at 1.69-1.74 GiB; Gemma 4 E2B 11.21 t/s at
  3.18 GiB** (row G2 on a fresh boot; G1's 9.99 ran with swap at 7.8% and is
  the depressed figure). Prompt processing 48-85 t/s. All on AC power, screen on, idle
  phone, each row gated at rated clock on `policy4` and `policy6`. P3 holds
  (pp scales with cores, 2.10x from 1 thread to 4); P2 holds (tg does not —
  four threads are SLOWER than two); P5's kills half FAILS. **Loading a 2B on
  a boot that has not already been cleared by hours of kills costs one to three
  cached processes; Gemma costs 34-39, deepest `oom_score_adj` 905 on BOTH
  runs, in 2.4-5.3 s.**
  The X1 clock ceiling falls to 18-46% of rated INSIDE every row and no row
  ended at rated. **THE FINDING IS "MEMORY BINDS, NOT COMPUTE" IN ROWS OF 1-4
  MINUTES ONLY. THE SUSTAINED CASE IS UNMEASURED AND THE THERMAL CAP MAY BIND
  THERE.** Every row was still descending in clock when it ended, so the
  headline must never be quoted without that qualifier — it is a claim about
  52-247 second rows on an idle phone on AC power, not about a model held
  resident and working.
  **It is an ABSOLUTE feasibility measurement of the handset,
  never a native-versus-VM comparison**, which is closed per the bullet above.
  **DONE 18 Sept — the first two of those four are ANSWERED.** Cold-load time
  and time-to-first-token with a cached prefix are both measured: nine gated
  rows across three boots on two models at **notes.md 8999-10993**, closed by
  the entry at **notes.md 10995**, with an amendment at 11342. Twelve
  predictions, **eleven pass and A3 fails low**. The wake figure that matters:
  a cold process restoring a 407-token prefix has a first token in **4.099 s**
  on Qwen3-1.7B (row `q17_r5_coldcache_cached`), of which **90.1% is the model
  load and 1.4% is the prefix**. **B3 on Qwen3.5-2B is NOT MEASURED** and needs
  the unspent boot 4, which is Matt's call.
  **Not done, and these are the NEXT THREE ITEMS, in order: (1) the `-ub` test
  that separates batch size from micro-batch size; (2) a sustained run — the
  thermal question every row in this repo has deferred; (3) the on-device VOICE
  bake-off.**
  **(2) IS DONE — BRIEF S, 18 Sept, and the sustained question is ANSWERED for
  this handset.** A model held resident with a cached prefix, answering a
  20-token turn repeatedly, on Qwen3-1.7B, boot 4.
  **S1 — one turn a minute for an hour (60 turns, 59.16 min, 7.84% duty): NO
  MEASURABLE DECAY.** Settled `ttft_turn` declined **-0.44%** and settled
  `gen_tps` **-0.20%** — the last quarter was fractionally FASTER than the
  first. TTFT median 354.39 ms, `gen_tps` median 14.49. All three clock
  ceilings read rated in all 355 series samples.
  **S2 — 200 turns back to back (26.03 min, 100% duty): loses 45% and then
  holds.** `gen_tps` 15.04 -> 9.21 in the first 40 turns and 260 s, then
  9.21 -> 8.26 over the remaining 160 turns and 1,291 s. Settled at **54.77% of
  turn 1**, against a 50% fail floor. X1 ceiling at its 984,000 floor (35.12%)
  after 129 s and never recovered in-row.
  **Every fail condition passed; four bands missed** — S-A1/S-A2 low (I
  predicted decay and measured none) and S-A3/D4 low and high respectively,
  both because the prediction extrapolated from C-series rows of 52-247 s to a
  row of 1,561 s and underestimated the throttling.
  **260 state restores across the two rows produced ONE token checksum**,
  `0xcba17a2fcbba49f4`, the same value rows 1/2/3/5 and S0 produced.
  **Nothing was killed above `oom_score_adj 935`** — two cached processes four
  seconds into S1, none at all in S2 — so "survived at adj 200" means the
  killer never came near it, not that the process withstood pressure.
  Commits: predictions and definitions BEFORE any code at `3d0329e` / `6f8e2e4`;
  instrument at `57c0f18`; smoke tests and the bug they found at `ff009f5`;
  device prep at `ca46a3b`; boot and readings at `3e85c63` / `170955d` /
  `013e04a`; S1 at `4940fbd`; S2 at `503da65`.
  **NOT DONE: S3** (does the conversational shape recover after S2, or is the
  boot spent) — Matt's call, not taken. **A growing context is untested**: the
  turn is the same 20 tokens 260 times and the KV cache is restored, never
  extended. The third is Kokoro-82M `bf_isabella` and `kokoro-onnx` int8
  under sherpa-onnx (**Matt's decision, 18 Sept**; recorded here from his
  instruction, with no notes.md entry behind it yet), measured with STT and the
  LLM **resident at the same time** — which is the first thing in this repo
  that asks what this handset does with more than one model in memory at once,
  against a 1.47-1.74 GiB LLM working set and ~2.1-2.3 GB of idle
  `MemAvailable`. Also not done: Q4_0, anything on battery, `policy0` sampled
  DURING a row (it has now been read at idle, 1,803,000 kHz, and was not
  sampled during any of the nine Q-A/Q-B rows either), and **any judgement of
  output quality** — the Q-A/Q-B rows did print text and it is quoted in
  notes.md, but no row's text has been judged.
- **TTS rung 1 — CLOSED on the 6a, 18 Sept (branch `tts-kokoro`, merged 19
  Sept).** Kokoro int8 (`penny-kokoro-int8`, sid 22) under sherpa-onnx
  `sherpa-onnx-offline-tts` `bd7d26e8…`, NDK r30, android-24, a fresh process
  per line. **Every row ran on spent boot 4, model page-cached; nothing here is
  a baseline.** Quoted from the write-up at **notes.md 13753** and the A76 row
  at **notes.md 13870**:
  RTF **1.067-1.570 on all 41 rows, none under 1.0** — X1 pair 2 threads: 4a
  1.324-1.401, 4b 1.067-1.373; unpinned 4 threads (4c) 1.174-1.570. Peak RSS
  283,656-577,488 kB, the top only on line 15 (10.416 s of audio). Derived load,
  cached (wall minus elapsed, includes process start and WAV write)
  1,875-2,139 ms. X1 ceiling fell to 2,048,000 kHz, 73.09% of rated, inside
  4b. Sample counts within 3.625 ms of the Mac's. **P-T5 HELD; P-T2, P-T3,
  P-T4 MISSED; P-T1 NOT JUDGED.** Product assumption: 2 threads pinned to the
  X1 pair (4c's 4 threads unpinned was slower on 18 of 18 lines, but ran after
  4b with its X1 ceiling lower — not controlled for heat).
  A76 pair, 2 threads (row a2): RTF **1.822-2.523, 7 of 18 lines above 2.0 —
  P-T6 MISSED**; neither ceiling moved; 18-line elapsed sum 143,543 ms against
  4b's 85,431 ms (ratio 1.680); derived load 3,137-3,279 ms.
  **NOT measured:** load from flash, resident RSS (rung 2), TTS beside the LLM
  (rung 3), the app / AudioTrack / time-to-first-audio (rung 4), a fresh boot,
  `policy0` during a row, pronunciation, the 7a.
  The twelve entries sit at notes.md 12868-13943, moved verbatim from
  `notes-tts.md` (now removed). **References inside them of the form
  `notes-tts.md:N` mean notes.md line N+12859.** `pennytts.sh` is at the repo
  root. On the 6a, `/data/local/tmp/tts/` still holds the binary,
  `libonnxruntime.so`, `penny-kokoro-int8/`, `pennytts.sh` and `out/`, left as
  they were.

**A rebuild now COSTS something again.** `penny3ev`'s store holds a verified
64MB file and a reinstall strands it — and worse, `Penny3evService`'s recovery
path will then silently delete and recreate it, logging `STORE WAS RESET`. That
line is the only thing standing between a stranded store and a false NO. If a
rebuild is needed and `penny3ev` still matters, give the new work its OWN VM
name.

**The payload budget is spent for this handset, and 3e-v and 3g-i spent none of
it.** Both were answered with `Penny3fPayload.so` unchanged, using `CMD_STREAM`
and `CMD_VERIFY`. If something later does need guest C, add a SIXTH payload in
the command-server shape and make it answer everything outstanding at once — the
Terminal app has to be opened by hand and that is the expensive part, not the
compile.

**Rung 4 — the OS image. DO NOT START IT.** Build GrapheneOS from source,
preinstall the app, sign with our platform key, flash, lock, verify
attestation covers the app. Weeks. Not now.

~~Before any app code: establish whether a JDK is present, whether the
Android SDK or Android Studio is present, and what needs installing.~~
**DONE 14 Sept and superseded** — Temurin 21, the command-line SDK, build-tools
37.0.0 and platform-tools are all installed and listed in the toolchain block
near the top of this file, and the NDK joined them on 15 Sept. Nothing to
establish.

Do not work ahead of the current rung.

## Traps that have already cost time

- **Not every member of these classes is callable, even though the class
  is.** `VirtualMachine.getCid()` and `VirtualMachineConfig.getOs()` both
  threw `NoSuchMethodError` from the app in rung 2b — with signatures
  taken verbatim off the device's own dex, so the methods demonstrably
  exist in `framework-virtualization.jar`. Meanwhile `create`, `run`,
  `setCallback`, `getStatus`, `getName`, `delete` and `getCapabilities`
  all worked. **PROVEN in 2c, and it is now predictable in advance.**
  The dex carries a per-member `hiddenapi` flag and `dexdump -d` prints
  it. Members marked `SDK` are callable by this app; members marked
  `BLOCKED` throw `NoSuchMethodError`. Checked against every member 2b
  touched, it was right 9 times out of 9 with no counterexample, then
  predicted all four 2c refusals correctly before the build was run.
  **Read the flag before writing the code**, from the jar already pulled
  in the scratchpad:

      dexdump -d classes.dex | grep -A3 "name          : 'setApkPath'"

  Look for the `hiddenapi     : 0x....` line. `0x0002` is BLOCKED.
  ART also logs each refusal — `adb logcat -d | grep hiddenapi` prints
  `api=blocked ... using linking: denied`, which is the unambiguous
  signature of this gate rather than a typo or a permission problem.
  Practical rule: a `NoSuchMethodError` on a signature you read off the
  dex is a runtime block, not a typo. Get the value another way — the CID
  came from `vm list` instead, and nothing was lost.
- **A VM can be granted more memory than the phone can survive, and nothing
  refuses it.** `setMemoryBytes` accepts any figure — 6GB on a 5.45GB phone
  included — and `VirtualizationService` returns `STATUS_RUNNING` in
  milliseconds. The refusal, when it comes, is Android's low-memory killer
  eating the phone: at 3GB it took the launcher, the IME and three system
  processes; at 4GB it took our own **foreground TOP** app, which killed the
  VM handle with it. The probe therefore died before its own watchdog could
  log a verdict, so the log shows `run() returned RUNNING` and then silence
  — which reads exactly like a VM that was accepted and hung. Check
  `logcat | grep "has died"` before concluding anything about a hang.
  **Seen three times now, and the shape is consistent: it works from the
  cheapest tier down.** 3e-iv's 2GB VM killed ~20 processes and every one was
  `cch` — cached, empty — with reasons running `cch +95 CEM` down to
  `cch +45 CEM`. Nothing a user would notice, and our app survived. That is
  NOT evidence for rung 3g-ii, which asks what happens when the processes in
  the way are a camera and a browser someone is actually using.
  **3g-ii ANSWERED it, 15 Sept, and the shape holds down to the tier that
  matters.** On a phone with apps open the killer clears the whole cached band
  and then takes the keyboard at **adj 201** — but stops there, twice, and never
  enters 200/100/0. Our app survived all three runs and so did the app on the
  screen. **The margin is one tier**, and 3e-i's 4096MB run shows it is
  crossable. Two method points that cost time: `am force-stop` restarts ALL the
  boot services — FIVE when 3g-ii was run, SIX in the APK now installed — so every run in this app carries a restart storm that must be
  present on both sides before two runs are compared; and `Penny3giService` runs
  once per process, logging `already started by an earlier delivery — nothing to
  do` on a second `am start-foreground-service`, so re-arming it needs a
  force-stop. (**It is SIX boot services in the APK now installed**, not five —
  `PennySoakService` was added after 3g-ii ran.)
- **TWO 2048MB VMs on one boot DOES cross into the foreground band, and it
  took our own app twice.** 3g-ii's comfortable reading — the killer clears the
  cached band, takes the keyboard at adj 201 and stops — holds for ONE 2GB VM.
  On 15 Sept evening a botched boot started `PennySoak`'s 2048MB VM at
  19:36:48 and `Penny3gi`'s at 19:36:55, and 7 seconds later the killer took
  `com.android.launcher3` and `com.pennyspike.probe2a` at **adj 100**, then our
  app again 14 seconds after that — 50 kills, settling on the third attempt.
  Accidental, not a designed run, so it is a warning rather than a result: do
  not start two 2GB VMs on the same boot, and when adding a boot service that
  holds a large VM, check what the other four are already holding.
- **A guard that lives in the intent is not a guard.** A service returning
  `START_STICKY` is recreated by Android with a **null intent**, so every
  `getStringExtra`/`getIntExtra` override falls back to its default. On 15 Sept
  this silently re-created and wrote an encrypted store on an unlocked phone
  under the default VM name, which burned the "can a store be created before
  first unlock" question for that store. Anything that must not happen on an
  unlocked phone has to test the PHONE — `UserManager.isUserUnlocked()` — not
  the intent. And a run that must be reproducible needs a VM name that cannot
  be reached by a default.
- **`am force-stop` does NOT stop a sticky boot service — it restarts it.**
  The documented restart storm is not only "all the services re-run" — **six of
  them in the APK now installed**, five when this was first written; the one
  you were trying to stop comes back too, with a null intent. To actually stop
  the app and keep it stopped: `adb shell pm disable-user --user 0
  com.pennyspike.probe2a`, which clears no data, survives a reboot, and is
  reversed with `pm enable`. Whether the two `pm grant`s and the assistant
  preconditions survive a disable/enable cycle is **UNTESTED**.
- **A microdroid guest that runs out of memory LIVE-LOCKS; it does not OOM
  kill and it does not return an error.** microdroid builds a zram swap
  device sized to the guest's entire RAM, and zram is compressed swap held in
  that same RAM. Push a guest near its ceiling with data that does not
  compress and `kswapd` has to allocate memory in order to free memory:
  `zs_malloc -> alloc_zspage -> __alloc_pages: page allocation failure`. The
  VM then hangs — 94 seconds on 15 Sept — and powers itself down through a
  normal shutdown. On the host there is no exception, no `has died` line and
  no reply on the channel; it presents as a hang. Ceiling measured at
  1856-1872MB in a 2038MB guest. Leave 10%.
- **A guest payload that allocates must fill pages with INCOMPRESSIBLE data
  or it measures nothing.** Same zram device as above: zeros or a repeated
  byte compress away to almost nothing, so a payload can "hold" far more than
  the guest has and the number is meaningless. Fill every byte of every page
  from a PRNG. Check it worked by comparing the guest's MemFree drop against
  the bytes written — 3e-ii's was one for one.
- **Memory for a VM is taken at CREATION, not when the guest touches it.**
  ~1.92GB left the host at `run()` for a 2048MB VM, before the guest had
  written a single page; the guest's subsequent 1792MB of writes cost only
  ~150MB more. So a host memory reading taken after boot has ALREADY paid for
  the whole VM. Rung 3e-i misread this as lazy allocation because the
  low-memory killer was freeing memory in the same instant; sample
  `/proc/meminfo` repeatedly through a run rather than twice, or the two
  movements cancel and the conclusion inverts.
- **A microdroid guest has NO writable filesystem unless you ask for one.**
  This is not "the storage is a RAM disk", it is that there is nothing to write
  to. `/data` is tmpfs capped at 128MB and the payload gets `EACCES` on it
  anyway; `/mnt`, `/mnt/androidwritable` and `/dev` are `EACCES`; `/tmp` does
  not exist; a relative path is `EROFS` because the working directory is the
  read-only erofs root. No SELinux denial is logged for any of it — the payload
  is simply not root, so it reads as a code bug rather than as a platform
  shape. The fix is one line: `setEncryptedStorageBytes(long)`, which mounts a
  real ext4 on dm-crypt at `/mnt/encryptedstore`. It is
  `hiddenapi 0x0020 (SDK,TEST-API)`, so a sideloaded app may call it.
- **`getOrCreate` reuses the VM's STORED config, and the APK path changes on
  every reinstall.** The failure is
  `VirtualMachineException: Failed to open APK ... FileNotFoundException: ENOENT`
  thrown out of `VirtualMachineConfig.toVsConfig`, which reads as a missing VM
  and is not: it is the old config pointing at a `/data/app/~~<hash>/base.apk`
  that the reinstall replaced. Never reinstall between two runs that must share
  an encrypted store. Cost one run on 15 Sept, and then **a whole reboot of
  rung 3e-v on the same day, because the trap was remembered and then talked
  out of.** The reasoning that did the damage: "the stored config was written
  minutes ago by the current install, so it cannot be stale" — which sounds
  decisive and is unfalsifiable from outside the app. It was stale; the VM had
  been created under the FIRST of that day's installs.
  **READ `config.xml`, do not reason about it.** It sits at
  `/data/user_de/0/<pkg>/vm/<name>/config.xml`, `adb shell` cannot read it on a
  user build, and the app itself can — `Penny3evService.dumpVmDir()` lists the
  VM directory and prints anything small and textual, which named the cause in
  one line after two wrong guesses. That helper costs nothing until something
  fails and is worth copying into anything that uses `getOrCreate`.
  Two zero-code tests exonerate the innocent suspects in about a minute, both
  via `Probe3fActivity`: `--ei keep 0` is `delete`+`create` WITH encrypted
  storage, `--ei keep 1` is `getOrCreate` on a VM the current APK made. If both
  pass, the fault is that one VM's stored config.
- **A guest payload must define its own `memcpy` and `memset`.** gcc may turn a
  bounded copy or clear loop into a call to either EVEN under `-ffreestanding`
  — the C standard requires those functions to exist in a freestanding
  implementation, so the compiler assumes them — and under `-nostdlib` that is
  an undefined symbol which surfaces at `dlopen` inside the guest where nothing
  useful is logged. Eight lines each, and they MUST carry
  `__attribute__((optimize("no-tree-loop-distribute-patterns")))` or gcc
  rewrites the loop inside `memcpy` as a call to itself.
- **`-nostdlib` is not optional and is missing from the build command recorded
  in `build.sh`'s comments.** Without it the compile fails with
  `/usr/bin/ld: cannot find crti.o`, because the Debian guest has gcc but no
  libc development files — which is the very reason these payloads use no C
  library. The full working command is:

      gcc -shared -fPIC -O1 -nostdlib -ffreestanding -fno-builtin \
          -fno-stack-protector -Wl,-z,max-page-size=4096 \
          -Wl,--hash-style=sysv -o Out.so in.c -L. -lvm_payload

- **A transfer figure measures the GENERATOR until you prove otherwise.** Rung
  3h's first 1.5GB run reported 26 MB/s. The bytes were intact; the number was
  measuring this repo's own Java, which shifted each 64-bit word out a byte at
  a time and then re-read the whole buffer to hash it. Splitting the host-side
  timer into "generate and hash" against "write to the socket" settled it in
  one run: 32 MB/s against 154 MB/s. Rewritten as one pass — hash the word
  where it is generated, store it with a LITTLE_ENDIAN `ByteBuffer.putLong` —
  and the same 1.5GB went in at 99 MB/s with the **identical checksum**, which
  is the proof the rewrite changed the speed and not the bytes. **When a
  transfer looks slow, time the generator before blaming the channel**, and
  quote the RECEIVER's figure as the headline because it is bounded by
  receiving rather than by manufacturing. Costs a Java-only rebuild, which
  needs no phone.
- **`adb shell` RE-JOINS ITS ARGUMENTS, so quoting inside an unwrapped remote
  command is unsafe — and the failure reads as a bug in the remote tool.**
  Measured 18 Sept, and it cost boot 3's ~5-minute protocol reading outright,
  which cannot be retaken without spending another boot. Sent:

      adb shell cut -d' ' -f1 /proc/uptime

  The Mac's shell strips the quotes, adb joins the remaining words with single
  spaces, and the phone receives `cut -d -f1 /proc/uptime` — so `-d` swallows
  `-f1` as its delimiter and the phone answers
  `cut: Needs -CFfcb (see "cut --help")`, exit 1. Inside a poll loop the
  variable was empty on every pass, the loop spun for twenty minutes and no
  reading was ever taken. **The rule: wrap the WHOLE remote command in ONE
  quoted string**, so adb has a single argument and nothing to re-join:

      adb shell 'echo "uptime_s=$(cut -d\  -f1 /proc/uptime) wallclock=$(date +%H:%M:%S)"'

  or sidestep the quoting entirely — read the file whole and do the arithmetic
  on the Mac:

      u=$(adb shell cat /proc/uptime | tr -d '\r' | awk '{print int($1)}')

  Same family as the `--es` extra and `input text` traps below. **A remote
  command that fails in a way that makes no sense for the tool named is this,
  not the tool.**
- **AN UNCLOSED QUOTE INSIDE AN `adb shell` POLL LOOP READS NOTHING AND SPINS
  SILENTLY.** Cost brief S its swap-recovery trend on 18 Sept. The command was
  `adb shell 'echo "t=$(…) … wall=$(date +%H:%M:%S)'` — **the double quote was
  never closed before the closing single quote**. Every pass returned
  `/system/bin/sh: no closing quote` and the loop ran to completion having
  measured nothing. This is the same family as the argument-rejoining trap
  above and it is worse inside a loop, because a single bad invocation is
  obvious while sixty of them look like a long wait. **Dry-run the exact
  invocation ONCE in the foreground before arming any loop with it**, which is
  what recovered the 25-minute reading after this failure.
- **A 10-SECOND SERIES MISSES SUB-10-SECOND CEILING DIPS, AND WILL TELL YOU THE
  CLOCK NEVER MOVED.** Measured on S1, 18 Sept: all **355 of 355** series
  samples read `policy6` at 2,802,000 — 100.00% at rated — while
  `pennybench.sh`'s own 0.2 s poll caught the same ceiling at **1,826,000 kHz,
  65.17% of rated**, 1,868 s into the row. Both numbers are in the same report.
  **NEVER quote a series "fraction at rated" without the poll-loop `min` beside
  it.** The series is for the shape over time; the `ceil_*_kHz min=` line is for
  whether the ceiling moved at all. On S2, where the dips were long, the two
  agreed — so agreement proves nothing about the sampling rate either.
- **THE FINAL SAMPLE IN A `.series` CAN BE A TEARDOWN SAMPLE. READ `VmRSS`
  BEFORE QUOTING IT AS END-OF-ROW.** S2's last line, 18 Sept, reads
  `MemAvailable 2431828` — its maximum for the whole row — beside
  `VmRSS 156676` against a `VmHWM` of 1,548,952. The process was already
  releasing its 1.5 GiB when that sample was taken, so the memory figure
  describes the teardown, not the row. S1's last line was clean
  (`VmRSS 1548652`) and its 932,128 kB IS the end-of-row figure. **The check is
  one column: if `VmRSS` is far below `VmHWM` on the last line, use the
  second-to-last, or the row minimum, and say which.**
- **A PROCESS LAUNCHED FROM `adb shell` HAS `oom_score_adj` -1000 — see the
  benchmark-protocol bullet.** Repeated here because it qualifies EVERY kill
  list in this repo taken before 18 Sept: those rows could not have been killed
  whatever the pressure, so "our own process was never touched" was not a
  measurement on any of them. From brief S, `pennybench.sh` writes 200 and reads
  it back. **Even then, "survived" is weak evidence unless the killer actually
  came close** — on S1 and S2 it stopped at `oom_score_adj 935` and never
  approached 200.
- **`policy0` (the A55 cluster) THROTTLES UNDER SUSTAINED LOAD, and this was
  unobserved until 18 Sept** because no row had ever sampled it. On S2 —
  200 turns back to back, 26.03 min — it fell to **738,000 kHz of 1,803,000
  rated, 40.93%**, and read below rated in **115 of 157** samples, **on a row
  where `taskset c0` scheduled nothing onto it at all.** That is the same
  package-wide limiter the 16 Sept cooled matrix found on `policy4`, now
  confirmed on the third cluster: **the cap is thermal and package-wide, not
  per-cluster, and pinning away from a cluster does not keep its clock up.**
  On S1, at 7.84% duty, all three held rated in every sample — so it is
  sustained load that does it, not load as such.
- **AN INTERRUPTED SESSION'S TOOL OUTPUT IS NOT GONE — IT IS IN THE
  TRANSCRIPT, AND "the scrollback is gone" MUST BE CHECKED BEFORE IT IS
  WRITTEN DOWN.** Cost a wrong statement inside a COMMITTED notes.md entry on
  18 Sept, which then needed its own amendment commit (`32706a2`). Claude Code
  keeps every session's tool calls and their full output at

      ~/.claude/projects/<project-path-with-slashes-as-dashes>/<session-uuid>.jsonl

  one JSON object per line. The 16 Sept session's `rm`, `adb push` and
  `sha256sum` outputs were all still there verbatim two days after that session
  was interrupted — including the push's `1 file pushed, 0 skipped. 31.4 MB/s
  (1280835840 bytes in 38.886s)`. **Grep it before claiming anything cannot be
  quoted.** The failure is not lost data; it is asserting an absence that was
  never checked, in an entry whose entire purpose is that its figures are
  quotable.
- **A `;` inside an `--es` extra is eaten by the device's shell.** `adb shell
  am start ... --es plan "1;2,200,0,"` fails with `/system/bin/sh: 3,200,0,:
  inaccessible or not found` — the double quotes are stripped by the Mac's
  shell and the phone's shell then splits on the semicolon. Wrap the value in
  single quotes INSIDE the double quotes: `--es plan "'1;2,200,0,'"`. Same
  family as the `input text` trap; cost a minute on 15 Sept.
- **The guest's CPU count and core identities ARE readable — from
  `/proc/cpuinfo` inside the payload.** The trap below says they are not in the
  guest console, and that stays true: microdroid attaches the console pipe
  after SMP bringup. It is a fact about the console, not about the guest.
  Reading `/proc/cpuinfo` from the payload returns eight processors with real
  `MIDR` part numbers. **Do not read the mix as a topology** — each vCPU is an
  unpinned host thread and records whichever physical core it sat on when the
  guest kernel read the register, so the guest's 1 X1 + 4 A76 + 3 A55 does not
  match the host's real 2 + 2 + 4.
- **Threads in a payload need a written-out `clone` trampoline, and aarch64's
  argument order is not x86-64's.** Proven working in rung 3f, and microdroid
  does NOT refuse `clone`. Four things that each cost a round trip if wrong:
  aarch64 is **CLONE_BACKWARDS** (flags, stack, parent_tid, TLS, child_tid);
  the trampoline **cannot be C**, because the child returns on a fresh stack
  with no return address and would `ret` into nothing; thread stacks must be
  **allocated and never freed**, since a child sets its done flag and then
  calls exit; and a **`dmb ish`** must precede that flag, because aarch64 is
  weakly ordered and `volatile` constrains the compiler rather than the
  processor. Working code is in `payload/penny3f_payload.c`.
- **A payload source can build its own host-side control, and should.** Rung
  3f's comparison is the same file compiled twice — a microdroid `.so` by
  default, a static Debian executable under `-DPENNY_CONTROL`. Because these
  payloads have no C library in either build, both make the same raw syscalls
  and the only difference is the operating system. That is far stronger than
  "equivalent C", it costs about fifteen lines of `_start` and argv parsing,
  and it is the shape to reuse for any future guest-versus-host measurement.
  Note what it is NOT: Debian on this phone is itself a guest in the Terminal
  app's VM, so it is a sanity number and never a bare-metal control.
- **`adb logcat -G 64M` before measuring anything with a DEBUG_LEVEL_FULL
  guest.** One guest console is several hundred lines a second and three at
  once evicted a whole run's own log lines from the default ring buffer
  before they could be read. The symptom is a probe that appears to have
  printed nothing.
  **IT CANNOT BE DONE FOR A BOOT-TIME MEASUREMENT, and there is no fix.**
  `-G` dies on reboot, adb cannot reach a locked GrapheneOS phone to set it
  beforehand, and the persistent route is refused:
  `setprop persist.logd.size 64M` returns
  `Failed to set property ... See dmesg for error reason` (SELinux, measured 15
  Sept). So the boot window is always recorded at the default buffer size.
  The mitigation that works: have each boot-time component accumulate its
  findings and re-emit them as ONE compact `SUMMARY` line at a quiet moment —
  after the unlock, say. `Penny3evService` and `Penny3giService` both do it. If
  the flood evicts the detail, the answer still survives.
- **The guest's CPU count is NOT in the guest console.** microdroid attaches
  the console pipe after the kernel's SMP bringup, so `grep -i cpu` over the
  whole console returns zero lines and an absent count proves nothing. Count
  crosvm's vCPU threads on the host instead: `ps -AT | grep crosvm_vcpu`,
  one thread per guest CPU. The guest's MEMORY, by contrast, is readable —
  microdroid sizes zram to its RAM and prints `Adding <N>k swap on
  /dev/block/zram0`.
- **Do not hand `setCallback()` and a watchdog the same single-thread
  executor.** A watchdog sleeping on it blocks the very callback it is
  waiting for, and a perfectly healthy VM is reported as one that never
  became ready. Cost one run on 15 Sept; the control caught it before any
  real figure was measured. **Walked into AGAIN on 15 Sept in
  `Penny3giService`**, whose blocking `runBoth()` sat on the same executor
  `setCallback()` was handed, and a healthy 256MB VM read as never ready for
  three minutes. The rule in practice: **`mExecutor` is for callbacks and for
  nothing else.** Anything that blocks — a watchdog, a wait loop, a command
  chain — gets `new Thread(...)`.
  **A SMOKE TEST IS WHAT CAUGHT IT, and that is the cheap habit worth keeping.**
  A boot-only service can be started by hand with
  `adb shell am start-foreground-service -n <pkg>/.<Service>` while the phone is
  unlocked, which exercises startForeground, the VM bring-up, vsock and the
  whole command path without spending a reboot. Pick one that owns no store to
  contaminate. Four minutes, and it saved a power cycle.
- **A payload built on Debian will not load in microdroid, and the failure is
  at dlopen where nothing useful is logged.** Debian is glibc; microdroid is
  Android, so bionic, and there is no glibc in the guest at all. Built the
  obvious way the `.so` carries `DT_NEEDED` for `libc.so.6` and
  `libgcc_s.so.1` and dies. The fix is to use **no C library at all** — the
  payload makes the two syscalls it needs directly in aarch64 inline assembly,
  because the syscall ABI belongs to the kernel and is the same whichever libc
  sits above it. Four flags, each against a specific refusal:
  `-nostdlib` (or gcc links `libgcc_s.so.1`), `-ffreestanding` (or gcc turns a
  hand-written loop back into a call to `strlen`), `-fno-stack-protector`
  (Debian defaults it ON and it needs `__stack_chk_fail` from glibc), and
  `-Wl,-z,max-page-size=4096` (aarch64 `ld` defaults to 64k segment alignment
  and the APK is aligned to 4k, so a 64k-aligned `.so` cannot be mapped in
  place). **Verify before packaging** — `readelf -d` must show `NEEDED
  libvm_payload.so` and nothing else, `readelf --dyn-syms` exactly one
  undefined symbol, OS/ABI `UNIX - System V`, and `LOAD` align `0x1000`. That
  check is seconds and catches every one of the above.
- **`getConsoleInput()` is BLOCKED; the guest console is outbound-only.**
  The obvious way to push bytes at a guest is the console, and an app cannot.
  `connectVsock(long)` is the only inbound channel that is not blocklisted —
  and the argument is a **long, not an int**. A wrong width presents as
  `NoSuchMethodError`, which on this device is also exactly how a hidden-API
  block presents, so the mistake reads as a platform refusal. Read the
  descriptor, not your memory: `(J)Landroid/os/ParcelFileDescriptor;`.
- **Each `write()` to fd 1 in the guest becomes its OWN console line.** The
  guest kernel frames the console per write, so `say("received "); sayu(n);
  say(" bytes")` arrives as three separately timestamped lines — and a naive
  `grep PENNY3C` drops the number entirely, making it look like the payload
  printed nothing. Build the whole line in a buffer and write it once, or grep
  on the payload's thread id (`T61`) rather than on the message prefix.
- **The guest console's payload thread id CHANGES between boots.** The fix for
  the fragmentation trap above is to grep the thread id rather than the message
  prefix — but it was T61 on one boot, T62 on another and T63 on a third, so a
  remembered id silently returns nothing and looks like a payload that printed
  nothing. Find it first with `grep "PENNY3C: host connected"`, then grep that
  thread. Cost a minute on 15 Sept, twice.
- **A foreground service may declare TWO types at once and still start at
  boot.** `android:foregroundServiceType="specialUse|microphone"` was accepted
  on every attempt in rung 3d. This was an open risk before 3d and is now
  answered: the wake service, the listening service and the VM holder do not
  have to be three separate objects.
- **`libvm_payload.so` cannot be read from the host.** It exists only inside
  `/apex/com.android.virt/etc/fs/microdroid.img`, which is EROFS (magic
  `e2e1f5e0` at offset 1024) with that file's blocks compressed. Much of the
  image IS plaintext — `microdroid_manager` greps 22 hits, `AF_VSOCK` 3 — so an
  empty grep for `AVmPayload` is NOT proof the symbol is absent, only that this
  file is in the compressed part. Do not conclude anything about that library
  from grepping the image.
- **Do not run `apt-get update` in the Debian guest without a reason.** Its
  package lists are cached from 12-14 Sept and re-fetching them is ~150MB of
  indices — more than three times the 43MB the compiler itself cost. The Mac's
  network is a phone tether; index downloads are the expensive part.
  **The tether was OFF on 15 Sept evening only** — the default route was
  verified as the wired home LAN (`en8`, 192.168.68.110) before ~20GB of
  models and the NDK were fetched. It is back on a tether from 16 Sept.
  Verify the route before assuming either way: `route -n get default`.
- **Copy first, kill second.** `sdkmanager` wipes its own
  `.temp/PackageOperation01/` on exit, so killing it and then trying to rescue
  the partial download loses the race. Cost a ~100MB partial NDK on 15 Sept.
  The empty `ndk/30.0.16248370` directory it left behind was a shell, not an
  install — `build.sh` tolerated it and resolved `CLANG` to empty.
  **NO LONGER TRUE, and that is now a trap of its own — see the next entry.**
  The NDK was reinstalled properly later the same evening, so `CLANG` resolves
  to a real compiler and the `else` branch that was dead is now live.
- **`sh build.sh` WITH NO `PENNY_PAYLOAD_*_SO` VARIABLES SET IS NOW DESTRUCTIVE,
  and it was harmless until the NDK arrived.** Read off `probe2a/build.sh` on 15
  Sept evening, not run. Two things happen, neither announced:
  1. The `else` at ~line 180 compiles `payload/penny_payload.c` with
     `"$CLANG" -shared -fPIC -O2` and **none** of the flags the trap below says
     are not optional — no `-nostdlib`, no `-ffreestanding`, no `-fno-builtin`,
     no `-fno-stack-protector`, no `-Wl,-z,max-page-size=4096`, no
     `-Wl,--hash-style=sysv` — and at `-O2` rather than the `-O1` every proven
     payload was built with. Until the NDK existed this branch could not run at
     all, so the missing flags cost nothing. Whether the result would load in
     microdroid is **UNTESTED** and is not the point: it is not the binary any
     rung was answered with.
  2. Every `cp` and every `zip` for the other four payloads sits inside
     `if [ -n "$PENNY_PAYLOAD_3*_SO" ]`, so the APK it builds carries **only
     `PennyPayload.so`**. Rungs 3c, 3d, 3e-ii, 3e-iii, 3e-v, 3f, 3g-i, 3g-ii and
     3h all name a payload that would not be in it, and installing it would
     strand every store as well.
  The rule: **never run `build.sh` bare.** Set all five `PENNY_PAYLOAD_*_SO` to
  the files in `probe2a/build-payloads/`, which is the only copy of them that
  exists. Not fixed on 15 Sept — recorded only.
- `VirtualMachineManager.getInstance(Context)` **does not exist**. Use
  `getSystemService(VirtualMachineManager.class)`. This matters beyond
  the typo: a wrong method name comes back as `NoSuchMethodException`,
  which is also exactly how a hidden-API block presents. Enumerate with
  `getDeclaredMethods()` before calling anything, or you will misread a
  guessing mistake as a platform refusal.
- `adb install -r` does **not** stop a running activity. `am start` then
  reports "intent has been delivered to currently running top-most
  instance" and `onCreate` never re-runs, so the log shows nothing new
  and the probe looks broken. Always
  `adb shell am force-stop com.pennyspike.probe2a` before `am start`.
- Pasting a long command into the Mac terminal can insert a real newline
  at the wrap point, splitting it in two. It cost a bogus `pm grant`
  failure on 14 Sept. Keep commands to one short line; do not chain two
  `adb shell` calls with `;` in a single quoted string.
- `brew install gradle` drags in Homebrew's own `openjdk` and Gradle runs
  on **that**, not on Temurin 21. `gradle --version` reported JDK 26. The
  Android Gradle Plugin does not support it, and the failure reads as a
  code or permission problem rather than a Java-version one.
  `/usr/libexec/java_home` does **not** list Homebrew formula JDKs, so it
  will not warn you. Sidestepped for now by not using Gradle at all; if
  Gradle ever comes back, pin `org.gradle.java.home` to the Temurin path
  in `gradle.properties` — never rely on `JAVA_HOME` in one shell.
- The Terminal app is **hidden** until enabled at Settings > System >
  Developer options > "Linux development environment". Not in the app
  drawer. Absence of an icon proves nothing.
- Losing adb mid-session is far more likely to be the wired connection
  than anything clever on the phone. ~~Run `system_profiler SPUSBDataType`
  on the Mac **before** `adb devices` — empty output means macOS sees
  nothing on the bus at all~~ **— THAT CHECK IS DEAD. MEASURED 16 Sept:
  `system_profiler SPUSBDataType` returns EMPTY OUTPUT AND EXIT 0 on this
  Mac with the phone connected and `adb devices` reporting
  `25301JEGR11115  device`.** It produces nothing whether or not anything
  is plugged in, so it distinguishes nothing and will falsely "confirm" a
  dead bus every time. It cost a wrong diagnosis on 16 Sept, where an
  entry cited it as evidence for a conclusion that happened to be right
  for another reason. **Use `adb devices` and `adb get-state`**, which are
  the question actually being asked. Why the command is empty was not
  diagnosed — macOS 26, a permissions gate and a reporting change are all
  untested candidates. The half that stays true: a phone charging normally
  can still have a dead data path, and this cost an afternoon.
- GrapheneOS sets the USB-C port to "charging-only when locked". It was
  not the cause in penny-box, but it **WAS** the cause on 14 Sept during
  rung 3, and again on 15 Sept during rung 3e-iv: after `adb reboot` the
  device never came back on adb, and `system_profiler SPUSBDataType` showed
  **zero** devices on the bus, until the phone was unlocked by hand. Note
  `aapm_usb_data_protection=0` did NOT predict this — that is a different
  GrapheneOS setting.
  **THE CONSEQUENCE IS A RULE, not an inconvenience: nothing on this device
  can be measured pre-unlock by SENDING it a command.** `adb shell am start`,
  `adb shell am broadcast`, `adb shell dumpsys` — none of them can reach the
  phone at the lock screen, so no probe driven from the Mac can ever answer a
  pre-unlock question. Any such question must be asked by a `directBootAware`
  component that starts ITSELF from `LOCKED_BOOT_COMPLETED` and writes its
  answer to logcat, read back AFTER an unlock. That is the shape rungs 3, 3b
  and 3d used and it was forced, not convenient. logcat survives an unlock and
  only dies on reboot; timestamps (`sinceBoot=`, and the
  `LockSettingsService: unlockUser started` line that marks first unlock) are
  what let you prove what happened before the human touched it. Rung 3e-iv
  lost its second half to this.
- Port forwarding does **not** survive a VM restart. Symptom is
  `Connection closed by 127.0.0.1 port 2222` with every indicator looking
  healthy. Fix is `adb shell am force-stop com.android.virtualization.terminal`,
  reopen the app by hand, then rebuild `adb forward`.
- **An app cannot create a VM before first unlock unless it uses
  device-encrypted storage.** `VirtualMachine.createVmDir` builds the VM
  directory relative to the Context it is handed, and the default Context
  points at `/data/user/0/<pkg>`, which is credential-encrypted and does
  not exist until somebody types the PIN. The failure reads
  `VirtualMachineException: failed to create directory for VM` caused by
  `java.nio.file.FileSystemException: ... Required key not available`,
  which looks like a permissions problem and is not. Fix is one line —
  hand the API `createDeviceProtectedStorageContext()`, which moves the
  VM to `/data/user_de/0/<pkg>`. Cost: that directory is readable without
  the user's PIN, so it is a confidentiality trade-off, not a free win.
- A foreground service started from a boot broadcast is denied
  microphone, camera and location — **UNLESS the app holds the assistant
  role. CORRECTED BY RUNG 3b, 15 Sept; the earlier "treat it as absolute"
  reading is wrong.** Measured both ways on the same APK, same service,
  same broadcast, two reboots apart: without the role,
  `startForeground(MICROPHONE)` throws `SecurityException ... and the app
  must be in the eligible state/exemptions to access the foreground only
  permission`; with the role it is accepted and records real audio at 12.3s
  with `userUnlocked=false`. So the wake service and the listening service
  do NOT have to be two different things. The exemption is real, it comes
  from the assistant role, and it reaches beyond the assistant's own
  process.
- **A third-party assistant is silently evicted at every boot unless a
  recogniser is also set.** The role holder and `settings secure assistant`
  both survive the reboot, so everything looks fine — but
  `voice_interaction_service` comes back EMPTY and `dumpsys voiceinteraction`
  says `(No active implementation)`, with nothing logged to explain it.
  `VoiceInteractionManagerService.initForUserNoTracing` reads
  `VOICE_RECOGNITION_SERVICE` first; if it is null the "Current
  interactor/recognizer okay, done!" early return is skipped and execution
  reaches `setCurInteractor(null, userHandle)`. Two things fix it, and
  neither is documented anywhere: `android:selectableAsDefault="true"` in
  the `<recognition-service>` XML, and pointing
  `settings secure voice_recognition_service` at that service. Cost one
  reboot on 15 Sept and would have produced a false NO for rung 3b.
- **Silence is the expected failure mode for microphone probes, and it does
  not throw.** Android 17 suppresses background audio "silently without
  throwing an exception" — `AudioRecord` initialises, reports
  `RECORDSTATE_RECORDING`, and returns a buffer of zeros. Decide on the
  samples (peak, RMS, proportion non-zero), never on the absence of an
  exception, and always run the unlocked foreground control first or a dead
  microphone is indistinguishable from a suppressed one.
- The boot broadcast's temporary exemption is **20 seconds**
  (`duration:20000` in the `Background started FGS: Allowed` log line).
  `startForeground` must be called inside that window or the service is
  killed. Rung 3 used ~5ms, so there is headroom, but a cold dex2oat on
  the first boot after an update eats into it.
  **IT GOVERNS `startForeground` AND NOTHING AFTER IT — do not budget VM time
  against it.** Rung 3g-i was defined on the assumption that a 2GB VM's extra
  ~4s to `onPayloadReady` ate into this window. It does not: every service here
  calls `startForeground` as the first statement of `onStartCommand` and hands
  VM work to another thread afterwards. Measured 15 Sept, `Penny3evService` 33ms
  in and `Penny3giService` 2ms in, with **all five foreground services then in
  the APK taking the exemption on the same boot** (the installed APK now starts
  **six**, and six on one boot is untested). Running that rung on its own terms would
  have produced a reassuring non-answer.
- The VM does **not** start itself after a device reboot. The Terminal
  app has to be opened by hand. It reaches a prompt in 4-5 seconds.
  **That is a fact about the Terminal app, not about VMs** — rung 3
  measured our own app bringing its VM up in 16 seconds unattended on the
  same boot where the Terminal app's `debian` VM did not appear until a
  human opened it 95 minutes later.
- The VM's whole subnet is rebuilt on every device reboot. Never pin an
  address, the gateway's included.
- VM CIDs are allocated in creation order and swap between runs. Never
  pin one.
- SSH needs `-i ~/.ssh/penny-box -o IdentitiesOnly=yes` or it fails with
  a misleading `Permission denied (publickey)`.
- A non-interactive SSH command does not source `.bashrc`, so `claude`
  must be called as `/home/droid/.local/bin/claude`.
- `adb shell input text` eats `>` `|` and `;` unless wrapped in single
  quotes.
- `adb shell ss -ltn` is refused by SELinux. Use the guest's own journal.
- `git` is ABSENT from the guest image, as are node, npm, pip3 and unzip.
- Pasting into a terminal whose session was killed mid-stream can leak
  literal `[200~` bracketed-paste characters. Retype rather than paste.
- Returning to stock: erase `avb_custom_key` while the bootloader is
  **unlocked**, before flashing. Getting this order wrong is the one way
  to strand the device.

The shell from the Mac, both commands, in this order:

    adb forward tcp:2222 tcp:2222
    ssh -i ~/.ssh/penny-box -o IdentitiesOnly=yes droid@localhost -p 2222

Which prompt is which — say it every time, or Matt will run it in the
wrong place. The Mac is `mattstevenson@Matts-MacBook-Pro-2`. The VM is
`droid@debian`. Claude Code inside the VM is a third place. `adb`,
`fastboot`, `git` and all Android build tooling exist **only on the Mac**.

## Open threads worth not losing

- Question 9 (endurance) has only ever been tested idle, mains powered,
  no workload. 54 minutes clean is a signal, not an answer. It matters
  less now: rung 3 succeeded, and a measured ~1.5-second self-restart
  beats a VM that survives all night and needs a human. **There is still
  no endurance test.** Rung 3 logged 95 minutes of an unattended VM, but
  that was a lost connection, not a designed soak, and must not be cited
  as one — Matt caught that, Claude had written it up as a result. Two
  boots and two simulated crashes is reproducibility, not endurance, and
  `am crash` is not memory pressure: the low-memory killer has never been
  exercised.
- **Device-encrypted storage is now load-bearing and is a confidentiality
  trade-off.** Rung 3 had to move the VM's state to
  `/data/user_de/0/<pkg>` to start before first unlock. That directory is
  readable once the phone is powered on, without the user's PIN. Decide
  deliberately what is allowed to live there before rung 4 designs
  storage. The pitch is confidentiality; this is the first place it was
  traded away for function.
- **This device does NOT support protected VMs. MEASURED.**
  `CAPABILITY_PROTECTED_VM = 1`, `CAPABILITY_NON_PROTECTED_VM = 2`, and
  `getCapabilities()` returns 2. A protected VM is one the host Android
  cannot read; we do not get one. Trust therefore rests on verified boot
  and attestation of the whole OS, not on the guest being opaque to its
  host — which was already the plan, but the alternative is now closed
  off. Unknown whether the cause is the 6a's silicon, GrapheneOS, or
  Android 17; one run of the same probe on the 7a settles it. Does not
  block 2b or 2c.
- **Host-side memory HAS been measured — this bullet used to say it never
  had, and that was wrong from 3e-ii onwards.** 3e-ii sampled host
  `/proc/meminfo` three times through one run (MemFree 2,257,952 ->
  288,756 -> 137,088 kB), which is the measurement that proved crosvm
  takes its memory at VM creation; 3g-i read host memory around a 2048MB
  VM at boot; 3g-ii recorded host kill lists and adj bands across three
  runs. **Measured for the first time with NO VM at all, 15 Sept
  evening**, on a clean boot with the app disabled: `MemAvailable`
  940,640 kB of 5,718,280 kB total, at 5.8 minutes after boot. **THAT
  FIGURE WAS A PHONE STILL SETTLING AND MUST NOT BE USED AS A BUDGET.** A
  second reading on the same untouched boot at 25.3 minutes returned
  `MemAvailable` **2,119,020 kB** — 1.18 GB more. Nothing was freed: Android
  compressed ~1.5 GB of idle anonymous pages into ~300 MB of zram
  (`AnonPages` -1,587,268 kB, `SwapFree` -1,500,160 kB, Zram physical
  +299,392 kB, ~5:1). **A THIRD reading at 60.5 min on the same boot returned
  `MemAvailable` 2,012,348 kB — 106,672 kB BELOW the 25-minute figure**, with
  `SwapFree` +113,408 and `AnonPages` +129,616, i.e. pages faulted back OUT of
  zram. So the curve flattens by ~25 min and then oscillates; **~2.0 GB is the
  idle figure for the 6a** and 2.12 GB was 0.1 GB optimistic. What decompressed
  those pages was observed, not identified. **A FOURTH reading at 120.6 min
  returned 1,929,032 kB**, falling again by 83,316 kB with the same signature
  (`AnonPages` +87,060, `SwapFree` +73,984, zram physical -6,216). Three falls
  in a row is a trend, so "settles then oscillates" is wrong: it peaks at ~25
  min and then declines slowly, ~2.0 MB/min over the 95 minutes measured.
  Whether that continues past 120.6 min is unknown. Two numbers must
  still be quoted together — `MemAvailable` is what the kernel hands over
  without killing anything, while `dumpsys meminfo` reports 3,748,470 kB free
  at 25 min of which 2,177,002 kB is cached app processes Android will kill
  on demand (3,657,804 kB / 2,199,920 kB cached / 987,048 kB truly free at
  60.5 min; 3,527,022 / 2,200,822 / 828,752 kB at 120.6 min — the cached pool
  stays flat near 2.2 GB and it is the genuinely-free part that shrinks).
  **Swap is two-thirds spent at idle** (SwapFree 1,261,820 of
  3,145,724 kB at 120.6 min) and a model's working set is hot anonymous memory that cannot
  be compressed away while in use, so `MemAvailable` alone is not a plan.
  **Peak RSS plus KV cache during generation is the number that decides
  anything, and it is unmeasured.** Any per-run reading MUST record the
  phone's uptime or it cannot be placed against this curve.
  **The 6a figure is a FLOOR, not the product budget** — the 7a that
  replaces it has more memory, so anything that fits here fits on the
  product, and anything that does not fit here must be re-measured there
  before it is called a no. The half of the old bullet that stays true:
  the q9 sampler read the guest, not Android, so "two VMs caused no
  pressure" remains a statement about the guest only.
- Claude Code has never done real work in the guest. `git` is absent. One
  arithmetic prompt at 317MiB says nothing about an agent holding a long
  context and running tools.
- **The join is CLOSED — rung 3d, 15 Sept.** Voice crosses the VM boundary at
  boot, locked, unattended, on two reboots. This bullet used to name it as the
  next real unknown; it is not one any more. What replaced it is the bullet
  below: the guest does nothing with the audio once it arrives.
- **One second, once, is not a stream.** 3c sent 32,000 bytes in a single shot
  into a VM that lived 3 seconds. No streaming, no backpressure, no long-held
  channel, no endurance. Do not let 3c be stretched into "audio streams to the
  guest". **3h moves this only partly**: it held one connection for 1536 chunks
  and 15 seconds and sustained 268 MB/s through it, so bulk transfer and
  backpressure are no longer unknowns — but that was one file pushed as fast as
  possible, not a live stream arriving in real time over minutes, and it
  carried no audio.
- **The memory gate is CLOSED, the storage gate with it, and as of 3h the
  DELIVERY gate too. 3e-i: 2GB and 8 vCPUs are given. 3e-ii: 1792MB of it is
  genuinely writable and held, twice. 3e-iii: a 1.5GB model FILE costs no
  permanent RAM at all** — a real, persistent, encrypted ext4 disk via
  `setEncryptedStorageBytes`, with 1792MB of anonymous memory still reachable
  alongside it. **3h, 15 Sept: 1,610,612,736 bytes go IN over vsock in 15.4
  seconds, 99 MB/s including fsync, intact — and a 256MB guest takes the same
  1.5GB without buffering a byte.** So the whole route for a model is now
  measured end to end: it can be pushed in, it lands on a real disk, it costs
  no permanent RAM, and it survives a reboot. **What replaced this question:
  nothing in this repo has ever asked where the bytes come FROM.** 3h pushed
  bytes the host manufactured. A real model arrives over a network, and neither
  the download, nor where it is staged on the host, nor what either costs has
  been looked at once.
- **THE STORE GATE IS FULLY CLOSED — 3e-iv and now 3e-v.** 3e-iv: the store
  survives a power cycle, 1610612736 bytes, all 1536MB faulted in from cold at
  ~598 MB/s. **3e-v, 15 Sept: it opens BEFORE FIRST UNLOCK and hands back the
  right BYTES** — 67,108,864 of them, ck64 `0x757b795dd5138044`, 14.4s after
  power-on with `userUnlocked=false` and 197.6 seconds of margin, with an
  after-unlock read of the same store down the same socket returning the
  identical checksum as a control. **The key is not tied to the user's
  credential, so a model can be read at boot with nobody in the room**, and
  every unattended result in this repo keeps its meaning.
  3e-iv's SIZE-not-CONTENT gap is closed with it: the checksum is DERIVED from
  a fixed-seed generator rather than copied from a log, so a store handing back
  the right number of zeroes would have failed.
  **What replaced this question: a store has never been CREATED before first
  unlock.** 3e-v's file was written on an unlocked phone; the pre-unlock attempt
  on its first boot died on the stale-config trap. That is the
  first-boot-after-factory-reset case and it is cheap — one constant and two
  reboots.
- **Compute is MEASURED — rung 3f, 15 Sept, and the guest CPU is real.** Eight
  vCPUs carrying genuine physical core identities, single-core at parity with
  the same binary running in Debian on the same silicon (bit-identical results
  from both), 4.2x aggregate across eight threads and within 1% of Debian's
  figure. `CPU_TOPOLOGY_MATCH_HOST` is eight usable cores. **What replaced this
  question: "the CPU is real" is not "a model will run well."** Both kernels
  were scalar dependency chains chosen to catch a fake CPU, not to profile a
  real one. Memory bandwidth under a real working set, cache behaviour, and
  NEON/dot-product throughput — the things a quantised model actually leans on
  — are all still unmeasured, and the guest advertises `asimd`, `asimddp` and
  `fphp` that nothing here has touched.
- **THE WAKE AND THE MEMORY ARE NOW IN THE SAME ROOM — rung 3g-i, 15 Sept.** A
  2048MB VM reached its payload at boot, locked and unattended, on two reboots
  (65.7s and 23.5s after power-on), each with a 256MB control on the same boot
  from the same service, and the guest's own kernel reporting
  `MemTotal: 2038164 kB`. Our app was never killed; the casualties were four
  cached processes on one boot and none on the other. **So a VM big enough to
  hold a model wakes on its own, and 3e-v says it can read a model while it is
  there.** The 20-second exemption was never the constraint — see the trap.
  **3g-ii ANSWERED that, 15 Sept, and it is the last thing this handset could
  answer without a build.** On a phone with the camera, a browser and four more
  apps open by hand, a 2048MB VM killed 16 and 14 processes across two runs —
  the whole cached band and then the keyboard at **adj 201** — and stopped
  there both times, never entering 200/100/0. **Our app survived all three
  runs, and so did the app on the screen** (camera at adj 0, run C, where the
  VM was started from a background service so nothing took the screen). Run B's
  dead camera was the probe's own activity demoting it to "previous app"
  (adj 700, `prev LAST`), not the VM. A 2GB VM is slower under load: 7,424ms to
  ready against ~4.3s idle.
  **What replaced this question: it was seven seconds, three times.** A 2GB VM
  held open for hours while somebody uses the phone is untested, and that is the
  actual product shape — see Endurance in Sequencing. The margin is also one
  tier: 3e-i killed our own foreground app at 4096MB, so **2GB works here and
  4GB does not**. And a stripped phone — "the phone is Penny, delete the other
  apps" — was NOT tested and is a rung 4 question; most of what died is the
  operating system, `rkpdapp` (remote key provisioning) included, and fewer apps
  is fewer cheap victims rather than more headroom.
- **THE LARGEST UNANSWERED THING IN THIS REPO: the guest still does nothing
  WITH the audio.** The payload hashes it and echoes it back — in 3c unlocked,
  in 3d at boot. No recognition, no model, no processing of any kind. "Audio
  reached the guest" is not "Penny heard you" and the gap between those two is
  most of the product.
- **`libvm_payload.so`'s API surface is still unread**, and with it the binder
  RPC route. See the trap entry. It needs erofs tooling or an NDK; raw vsock
  made it unnecessary for 3c but rung 4 may want it.
- **A ten-line payload is not a workload.** 2d's payload has no C library, let
  alone a runtime. Microdroid is a minimal Android, not Debian: nothing here
  says anything about running Claude Code, or a model, or any real process
  inside it. Do not let the 2d result be stretched into that claim.
- **Can a user take the assistant slot by tapping, rather than by cable?**
  The one thing rung 3b could not answer. The slot was taken with
  `cmd role add-role-holder` plus two `settings put secure` calls, and the
  recogniser setting is the one that makes it survive a reboot. If the
  Settings picker does not set that too, Penny is evicted on the user's
  first power cycle and the whole voice story is cable-only.
  `config_showDefaultAssistant` is true so the picker exists. This is a
  phone-tapping test, costs minutes, and gates the shippable route.
- Whether the phone earns its place at all, versus a small Linux box with
  no permission games and no patch pipeline. The attestation story is
  what justifies the phone.
- Who patches the OS, and how fast, if we fork. Needs an answer before
  rung 4.
- This 6a goes back to Back Market and is replaced with a 7a. The revert
  path is verified on paper — build CP2A.260705.006, checksum
  `03992adc723742de3fa12c6a40fb19ecbcd7f388a9eaacf93e27af466a79cb33` —
  but has never been exercised. Do the dry run on a calm evening.
- The q9 sampler is DEAD (device rebooted). Its log survives at
  `/home/droid/q9-soak.log` in the Debian guest, 55 samples ending
  10:29:20Z.

## Findings

All findings go in `notes.md`, appended in date order. Never reorganise,
compact or rewrite earlier entries — a wrong guess that got corrected is
useful history. When something here becomes wrong, correct it here **in
the same step** AND append an entry to `notes.md` saying what changed and
why. penny-box's CLAUDE.md still said "Question 9 is in progress" after
rung 1 was answered; that would have pointed the next session at the
wrong question entirely.

Record versions for everything: GrapheneOS build number, Android version,
Debian image, Claude Code version, SDK and JDK versions, and the Pixel
model.

**Commit the moment a question is answered, before moving on.** Give Matt
the git command as its own step. Rung 1's entire result sat uncommitted
for hours.

## Constraints

- Do not create new markdown files. `notes.md` and this file are all
  there is.
- Do not propose architecture, folder structures, or abstractions.
  Product and architecture thinking belongs in the Penny project, not in
  this repo. Say so if Matt starts blurring the two.
- Do not build anything beyond what answers the current open rung.
- Do not add tooling, linters, CI, or dependencies.
- Matt runs all shell and git commands himself. Give him the command; do
  not assume it has been run. He does not edit files — Claude writes them.
- Matt does not touch the phone until told to. Never assume a device
  action has happened, and do not caution him against acting ahead.
- **One step at a time.** One command or one action, then wait for the
  output. Not a list. Not "step 1, then step 2".
- Matt is not a developer. Assume no prior knowledge — where to click and
  what to expect on screen, not just the command. Keep answers short and
  in plain English; put the evidence in `notes.md`, not in the message.
- Explain where things live in the stack, not just what to type. Matt has
  to be able to explain this architecture to investors himself.
- Stop and ask before any choice that is Matt's rather than Claude's:
  anything that costs money, adds a dependency, or picks between two
  routes with a real trade-off. Give the trade-off in plain English, then
  wait.
- When something breaks, say plainly what has been ruled out and what is
  being tested next. Do not guess twice in a row.
- Push back. That is the job.
- Say plainly when something does not work. A negative finding closes a
  question, which is the point of this repo.

## Reporting rules — how every message to Matt is written

Added 15 Sept (evening) by the review session. Matt cannot see the
terminal, the files, or the phone. Every claim in a message has to be
checkable by someone who can only read what you write.

- **Every commit report shows `git show --stat <hash>` and the header line
  of any notes.md entry it contains.** A bare hash is not a report.
- **"Verified" names what it was verified against.** A local checksum is
  "computed", not "verified". A match against a published hash says whose
  hash. A file whose existence was checked says what was checked (a
  directory existing is not a toolchain existing).
- **Every measurement carries its conditions in the same line**: uptime
  since boot, what was running, locked or unlocked, pinned or unpinned.
  A number without conditions is not quoted.
- **A wall-clock time is quoted ONLY when it was read in the same command as
  the value beside it** — `adb shell 'echo "uptime_s=$(cut -d" " -f1
  /proc/uptime) wallclock=$(date +%H:%M:%S)"'`, one invocation. Never computed
  afterwards from another reading. Cost a four-minute error on 16 Sept.
- **No adjectives on measurements.** "MemAvailable 940,640 kB at 5.8 min"
  — not "the phone is already tight". The reader decides what it means.
- **A first reading is a reading, not a baseline.** Nothing is called a
  budget, a ceiling or a floor until it has been read twice under the same
  conditions with time between.
- **Numbers from earlier sessions or earlier entries are marked as such.**
  "3e-ii measured 1.92GB" — never restated as if seen now.
- **A direct question from Matt is answered first, in one line, before
  anything else in the message.** If the answer is "not done", say so.
- **Every install, download or dependency is listed in the message that
  did it, with how it was installed.** Nothing arrives via a route Matt
  did not agree to (brew when sdkmanager was agreed, for example) without
  it being named as a deviation.
- **Every message that reports a result ends with what it does NOT say**,
  the same way notes.md entries do.

## Decisions — CLOSED, with the only thing that reopens each

Do not reopen these in prose, in a notes entry, or in a "next step". If
you think one should be reopened, say so as a question to Matt and give
the new evidence. Later entries in notes.md may add to this list; they may
not soften it.

- **The model runs natively on Android, not in a VM.** Closed 15 Sept on
  3e-ii (VM costs ~1.92GB up front), 3f (cannot pin cores), and
  getCapabilities()=2 (no protected VM on this device). A poor native
  benchmark does NOT reopen it. Reopens only if model isolation becomes a
  requirement.
- **The pennysoak run is VOID and is never reported.** Reopens never; a
  re-run is a new run with a new VM name.
- **Endurance testing happens on whatever ships, not on a microdroid
  soak.** Reopens only with the decision above.
- **Official ggml-org llama.cpp built with the NDK, no third-party
  prebuilt binaries.** Reopens never for this spike.
- **The 6a is temporary.** No result is written as if it were the product
  device; every ceiling is "on the 6a", and the 7a re-measures anything
  that fails here before it is called a no.

## Benchmark protocol — native llama.cpp, from 16 Sept

- **Build flags: `-DGGML_CPU_ARM_ARCH=armv8.2-a+dotprod+fp16`, NEVER
  `armv8.7a`, and never as a global `-march`. RUN 16 Sept — the flag VALUE
  was right and four other things in this bullet were wrong. Corrected
  here; see the 16 Sept notes.md entry.**
  `armv8.7a` turns on i8mm, which Tensor G1's Cortex-X1/A76/A55 do not
  have, so the compiler emits `smmla`-family instructions and the binary
  dies with SIGILL on the phone — reading as a broken build, not a wrong
  flag. **`docs/android.md` does NOT recommend it** (this bullet said it
  did): at commit 38a5b42d that doc says "Do not add a global `-march`
  flag" and `docs/build.md:669` says `armv8.7a` is "not required". The
  only `armv8.7a` in the tree is a Snapdragon preset.
  **Use `GGML_CPU_ARM_ARCH`, not `CMAKE_C_FLAGS`.** It applies `-march`
  to the `ggml-cpu` target alone (`ggml/src/ggml-cpu/CMakeLists.txt:172`)
  instead of raising the baseline for every source in the project.
  **Passing nothing is the trap, not the safe option.** With
  `GGML_NATIVE=OFF` — mandatory for cross-compilation — and no
  `GGML_CPU_ARM_ARCH`, ggml adds no `-march` at all and the NDK baseline
  is plain `armv8-a`: no dotprod, no fp16 vector arithmetic, and the Q4_0
  repack and quantised dot kernels compiled out. That binary runs and
  understates the handset.
  **`readelf -A` IS NOT THE CHECK — it returns an empty `BuildAttributes`
  block for every aarch64 binary**, so it would pass a build full of
  `smmla`. Disassemble instead: `llvm-objdump -d` must show **0**
  `smmla|ummla|usmmla`, **0** `ptrue|whilelo|smstart|smstop` or `z<n>.`
  operands, and — the positive half, which absence-checking cannot give —
  a nonzero count of `sdot|udot`. This build: 0, 0, 898.
  **`LLAMA_CURL=OFF` is a dead option** (`CMakeLists.txt:195`,
  `llama_option_depr`, no replacement). Use `LLAMA_OPENSSL=OFF`.
  **`llama-cli` cannot be built offline at this commit**: `tools/cli` sits
  inside `if (LLAMA_BUILD_SERVER)` with `tools/ui`, which downloads
  prebuilt web assets from a Hugging Face bucket at build time.
  **`examples/simple` (`llama-simple`) IS buildable offline** — it is added
  unconditionally under `LLAMA_BUILD_EXAMPLES` and links only `llama`, no
  `common`, no curl, no OpenSSL, no server. Build it: a tok/s figure with
  no printed text cannot show the model produces sensible output, and a
  broken kernel still reports a tok/s.
  **`llama-bench` does NOT supply every column this protocol asks for, and
  an earlier version of this bullet said it did.** It prints pp and tg
  tok/s and nothing else — **no peak RSS, no MemAvailable, no uptime, no
  lowmemorykiller kills**, which is four of the protocol's own required
  columns. Those come from a wrapper on the phone
  (`/data/local/tmp/pennybench.sh`): `VmHWM` from `/proc/<pid>/status`
  polled at 5 Hz while the child lives (VmHWM only grows, so the last read
  before exit is the peak), `/proc/meminfo` and `/proc/uptime` read either
  side, and `logcat -d -b all -t <start>` grepped tag-anchored for
  `am_kill|lowmemorykiller| lmkd : |has died`. **Exclude `am_cpu`** — those
  lines name lmkd and are not kills; they were the one false positive found
  when the grep was calibrated on 16 Sept.
  Otherwise as before: NDK toolchain file, `ANDROID_ABI=arm64-v8a`,
  `BUILD_SHARED_LIBS=OFF` (no `.so` to push, no `LD_LIBRARY_PATH` — but
  the binary still links bionic `libc/libm/libdl`, so it is not a static
  ELF), cmake and ninja as the SDK's copies in `cmake/3.22.1/bin/` by full
  path. The working line, the binary's hashes and the instruction counts
  are in the 16 Sept notes.md entry. **It HAS been executed on the phone**
  — `--help` at 12:13 on 16 Sept, exit 0 — which proves it loads and links
  and nothing more; see the SIGILL note in LIVE DEVICE STATE. No quantised
  kernel has run.
  **The wrapper is `pennybench.sh` at the repo root**, and it is the source
  of every measured column except pp/tg tok/s. Push it to
  `/data/local/tmp/` alongside the binary; the copy on the phone must hash
  to the copy in the repo.
- **EVERY MATRIX RUN USES `-lm none`. `-mmp` DOES NOT EXIST AT THIS COMMIT.**
  Added 16 Sept after step 3 of the RSS chase; see the notes.md entry.
  `-mmp 0` is older llama.cpp and returns `error: invalid parameter for
  argument: -mmp`, printing usage and loading nothing — a run that looks
  like it happened and did not. The flag is now
  `-lm, --load-mode <auto|none|mmap|mlock|mmap+mlock|dio>`, and `none` is
  the no-mmap mode: `src/llama-model-loader.cpp:559` sets `use_mmap` true
  only for `MMAP`, `MMAP_MLOCK` and `AUTO`. llama-bench prints the mode as
  an `lm` column of its own, so the table records which was used.
  **Two reasons it is the mode for every run, and neither is about tok/s.**
  It is what a real app would do — a process that has repacked its weights
  has no use for a mapping of the bytes it repacked from. And it is the
  only mode where **peak RSS IS the working set**: under mmap, VmHWM mixes
  ~817 MiB of dead already-repacked originals in with the live pages and
  cannot be used to size anything, whereas at `-lm none` the peak is 99.6%
  anonymous and every byte is memory the phone has to find. Measured on
  Qwen3-1.7B Q4_K_M, `-p 16 -n 16`, c0, 2 threads: 1,410,496 kB at
  `-lm none` against 2,230,268 kB under mmap, with the hot working set
  ~1372 MiB on BOTH paths. The cost is swap traffic — SwapFree fell
  281,668 kB against 122,284 kB on the mmap run of the same length —
  because anonymous pages can only be compressed, never dropped.
- **THE X1 PAIR LOWERS ITS OWN CLOCK CEILING UNDER SUSTAINED LOAD, SO EVERY
  `c0` FIGURE IN THIS REPO MAY BE THROTTLED — the ceiling was read AFTER row 4
  only, never before or during any row.** Measured 16 Sept when the matrix
  stopped at row 4 of 8; see the notes.md entry. The earlier wording here and
  in that entry's heading said "IS a throttled figure"; that overstated it and
  contradicted the entry's own body, which argues row 1 began on a cool chip.
  `policy6/scaling_max_freq` read **1,426,000 kHz against a `cpuinfo_max_freq`
  of 2,802,000** immediately after a 65-second two-thread run — the ceiling
  itself at 50.9% of rated, not the governor picking a low point. It climbed
  back monotonically to 2,802,000 over **109.8 seconds of idle**.
  **"ONLY THE X1 PAIR IS CAPPED" WAS WRONG AND IS REFUTED BY THE COOLED RUN.**
  That claim came from ONE reading taken after a row had ended, i.e. during
  recovery, not during load. With the ceiling sampled THROUGHOUT each row, the
  A76 pair (`policy4`, rated 2,253,000) fell on six of the seven cooled rows —
  floor **910,000 kHz, 40.4% of rated** on C3 and C5 — and it fell on C1 while
  `taskset c0` scheduled nothing onto it at all. **The limiter acts across the
  package, not per cluster.** The X1 pair fell below half rated on **every**
  cooled row without exception, floor **851,000 kHz, 30.4% of rated** (C5), and
  **no cooled row ended at the X1's rated clock**. `policy0` (the A55 cluster)
  has still never been sampled on any row.
  **The consequence for the protocol: rows run back to back measure heat, not
  cores.** Gaps of 15-28 seconds between rows are a fraction of the ~110 s
  recovery, so every row after the first starts at an unrecorded clock and
  falls further during its own runtime. It inverted P6 at 2 threads
  (`pp64` 49.44 beat `pp512` 45.39), split one `tg128` figure 23% from the
  identical row before it (10.35 vs 12.75 on the same mask and thread count),
  and blew the error bars out to 12.7% and 17.8%.
  **DECIDED 16 Sept, by Matt: COOL BETWEEN ROWS.** Every row from the cooled
  re-run onwards is gated on BOTH `policy6/scaling_max_freq` = 2,802,000 AND
  `policy4/scaling_max_freq` = 2,253,000, polled until both read rated and the
  row launched in the same shell invocation so nothing intervenes. A sustained
  run is **NOT** done and is **NEXT, not done** — it is a thermal run, which
  CLAUDE.md names as out of scope for today.
  `pennybench.sh` records this from 16 Sept: `ceil_x1_kHz` and `ceil_a76_kHz`,
  each `before=` / `min=` / `after=`, with the minima tracked inside the
  existing poll loop so the DESCENT is measured rather than inferred.
  **From the second revision it also records `ceil_x1_min_at` /
  `ceil_a76_min_at`** (the uptime each minimum was first seen at, and how many
  seconds into the row that was), **`pswpin`, `pswpout` and `pgmajfault` from
  `/proc/vmstat` either side of the row**, and reports the swap device once so
  the record says what swap actually is on this handset rather than assuming
  zram. **`/proc/swaps` is `Permission denied` to the shell user on this build,
  and so is every attribute under `/sys/block/zram0` (the node itself exists).**
  `dumpsys meminfo` answers it and is readable: read 16 Sept at 66,065 s uptime,
  **`ZRAM: 666,716K physical used for 2,830,100K in swap (3,145,724K total
  swap)`** — so swap on this handset IS zram, compressed in RAM at ~4.25:1, and
  a "SwapFree" figure is not disk. **Wrapper sha256, revision 5, 18 Sept:
  `96163d047d7a91cd3f937cba71c4bce9270e6f84afcb4d700fe7a1513e0889af`, 12,790 B**
  (supersedes rev 4 `c5b9f03a7c5add80196e8ed584c1091cc62390e24fafe3cda91004c85
  12ffc99`; which superseded `0f5cb2b5…` rev 3, which added `PENNYBIN`; which
  superseded `484d75d4…`, `67eefed1…` and `e5a81104…`).
  **Revision 5 adds TWO things, for brief S.** A TIME SERIES to `$OUT.series`,
  one line every 10 s of uptime while the child lives, fourteen columns:
  `uptime_s ceil_x1 ceil_a76 ceil_a55 MemAvailable_kB MemFree_kB SwapFree_kB
  Cached_kB VmRSS_kB VmHWM_kB pswpout pgmajfault batt_temp_dC batt_level` —
  before/after readings bound an hour-long row but say nothing about what
  happened inside it. **`ceil_a55` is `policy0`, which no row in this repo had
  ever sampled.** And `PENNYBENCH oom_score_adj_child pre=/post=`, which writes
  200 to the child and READS BOTH VALUES BACK — see the `oom_score_adj` trap
  above. **Battery temperature is read from
  `/sys/class/power_supply/battery/temp`, NOT `dumpsys battery`**, and that is a
  named deviation from brief S: the series samples ~360 times in an hour and
  `dumpsys` is a binder call into `system_server`, the process most likely to be
  perturbed by the pressure being measured. `dumpsys` is still read once either
  side as the cross-check — and the smoke tests showed it LAGS, reading 276 dC
  at both ends of a row where sysfs went 278 -> 280, because it updates on
  battery-change broadcasts rather than on demand. **It is BATTERY temperature
  in tenths of a degree C, never SoC temperature**; `/sys/class/thermal/` is
  `Permission denied` to the shell user on this build. **Revision 4 adds one thing and
  one only: the whole REPORT section is tee'd to `$OUT.report` as well as to
  stdout.** Until it, every `PENNYBENCH` line — peak RSS, the kill count,
  the clock ceilings, `MemAvailable` either side — existed ONLY in the
  operator's terminal, so a row's own conditions could not be re-read from the
  phone afterwards. From rev 4 **every row's numbers are read from
  `out/<tag>.report` and `out/<tag>.bench`, never from scrollback.**
  **Do not compare a `c0` figure with another `c0` figure unless both carry
  their thermal state.** The four throttled rows stay in the record as what
  they are and are never quoted as the chip's speed.
  What is NOT known: what writes the cap (`/sys/class/thermal/` is
  `Permission denied` to the shell user on this build, so no temperature was
  read), and what the clock was during any row — only after.
  Memory is unaffected: peak RSS is set by `-p` and is indifferent to `-t`
  (1,491,612 vs 1,491,668 kB at 1 and 2 threads, 0.004% apart).
- **A BOOT WHOSE PAGE CACHE HAS COLLAPSED AND WHOSE SWAP IS SPENT IS
  CONTAMINATED, AND NO FURTHER ROW ON IT COUNTS. REBOOT.** Decided 16 Sept by
  Matt, from row C7. During that single row `Cached` fell **1,104,136 kB**
  (1,413,100 -> 308,964) and was still only 427,980 kB 34 s later, while
  `SwapFree` reached **93,416 kB of 3,145,724 — 97.0% spent**. The model file
  is 1,081,454 kB, so the figure that left the cache is the size of the model:
  **most likely the kernel evicted the model mid-row.** C7 returned a `pp512`
  30% BELOW its own throttled counterpart with a *tight* `tg128` error bar
  (±0.27 on 6.00), which most likely reads as memory starvation rather than
  clock — a clock cap is noisy, a starved machine is consistently slow.
  **BOTH OF THOSE WERE THE LEADING HYPOTHESIS, NOT AN ESTABLISHED FACT, AND THE
  C7 RE-RUN ON A FRESH BOOT WAS WHAT TESTED THEM.** The condition was written
  down before that re-run: **if the re-run reproduces ~32.40 / 9.89 the
  hypothesis stands; if it reproduces ~22.68 / 6.00 it does not, and the
  contamination rule below stays as a rule but loses its explanation.**
  **RUN 16 Sept ON THE FRESH BOOT, AND THE HYPOTHESIS STANDS: 31.45 ± 0.70 /
  10.13 ± 0.64 in 164.38 s**, against row 1's 32.40 ± 0.78 / 9.89 ± 0.38 in
  162.19 s — 2.93% apart on `pp512` with overlapping error bars — and 38.7% /
  68.8% faster than C7. The error bar fell from ±5.38 (23.7%) to ±0.70 (2.23%),
  `Cached` ROSE 38,124 kB instead of collapsing, and `pgmajfault` moved 488
  across the whole row. **C7's 22.68 / 6.00 is not this handset's 1-thread
  speed and is never quoted as it.** What the re-run does NOT establish is the
  mechanism: it replaced the whole boot, not one variable. Nothing was killed and
  `MemAvailable` never fell below 2,695,176 kB, so the ordinary stop conditions
  do not catch this: **swap and `Cached` have to be read as gating conditions
  in their own right.** The rule: if `Cached` drops by roughly the model size
  inside a row, or `SwapFree` falls below ~10% of `SwapTotal`, the boot is
  spent — reboot, take the protocol's ~5 min and ~25 min `MemAvailable`
  readings, and re-run. `pennybench.sh` now carries `pswpin` / `pswpout` /
  `pgmajfault` so the next occurrence is measured rather than inferred from
  `Cached` alone.
- **A KILL COUNT IS MEANINGLESS WITHOUT THE ROW'S `MemAvailable` BEFORE IT, AND
  A LONG-RUNNING BOOT FLATTERS IT.** Decided 16 Sept by Matt, from row B2-R1.
  All twelve `-lm none` rows on the 15 Sept boot returned ZERO kills and every
  one of them started with `MemAvailable` between 2,695,176 and 2,848,012 kB —
  headroom that boot had freed by killing its own cheap processes over 17-18
  hours and spending 97% of its swap. B2-R1, the first row on the fresh boot,
  started at **2,128,524 kB on a phone that had killed nothing** and took one
  cached process at `oom_score_adj 985` (`cch +85 CEM`) four seconds in, at
  model load. **The fresh-boot row is the representative condition; the
  zero-kill rows were flattered by prior kills.** So every row reports its kill
  count WITH `MemAvailable` before it, and a zero on a tired boot is never
  quoted as "this model causes no kills".
- **A PROCESS LAUNCHED FROM `adb shell` HAS `oom_score_adj` -1000, SO IT CANNOT
  BE LMK-KILLED — AND EVERY KILL LIST IN THIS REPO WAS TAKEN THAT WAY.** Read on
  18 Sept, boot 3, before brief S was built:
  `adb shell 'sh -c "cat /proc/\$\$/oom_score_adj"'` returns **-1000**, the
  value adbd hands down. Rows 1-9 of the Q-A/Q-B plan all ran through
  `pennybench.sh` from `adb shell`, so their kill lists — 8 lines / 4 processes
  on r1, 8/4 on r5, 6/3 on r6, 16/8 on r7 — **could never have named
  `pennyload`**. The counts stand; the closing entry's "our own process never
  touched" was not a measurement on any of those rows and must not be read as
  one. **The fix is one line and it is TESTED, not assumed**: raising
  `oom_score_adj` is permitted to one's own uid (lowering needs
  `CAP_SYS_RESOURCE`), and SELinux does not refuse it on this build — a
  throwaway `sleep` went -1000 -> 200 with `write_rc=0`, empty stderr and a
  read-back of 200, at uptime 8625.00 / 12:42:20. From brief S, `pennybench.sh`
  writes **200** to the child and prints `pre=` and `post=` side by side so the
  line is a reading rather than an intention. **200 is the perceptible /
  foreground-service band** — the closest imitation of a service started from a
  boot broadcast. It is NOT cached (900+) and NOT the foreground app (0);
  3g-ii's killer stopped at **adj 201** under a 2GB VM, so 200 sits one point
  inside the band it did not enter.
- Prediction written in notes.md BEFORE the first run, and judged against
  in the write-up. The standing one: token generation barely improves
  beyond 2 threads (memory-bandwidth bound); prompt processing scales.
- Models are identified by sha256 from MANIFEST.txt, never by name alone.
- Every run records, in one table row: model file, quant, threads,
  taskset mask (or "unpinned"), -p value, pp tok/s, tg tok/s, peak RSS,
  MemAvailable before and after with uptime, and any lowmemorykiller
  kills during the run.
- Unpinned runs are never quoted as the chip's speed. Pinned figures are
  labelled by which cores (c0 = X1 pair, f0 = X1+A76).
- One model on the phone at a time. Push, run, record, delete, next.
- Nothing VM-hosted and no thermal run. Those are named as not done in the
  write-up. **TIME-TO-FIRST-TOKEN WITH A CACHED PREFIX IS NO LONGER ON THIS
  LIST — IT IS DONE**, 16-18 Sept, together with cold-load time: nine gated
  rows, three boots, two models, at **notes.md 8999-10993**, closed at
  **notes.md 10995** (amendment at 11342). A cached prefix costs 9.58-59.16 ms
  to restore and removes a 6.0-7.7 s system-prompt decode; what it cannot
  remove is the model load. **B3 on Qwen3.5-2B is NOT MEASURED** — boot 4 is
  unspent and is Matt's call.
- Done = a notes.md entry "native llama.cpp feasibility on the 6a" with
  all three models' tables, whether the prediction held, and a plain
  answer to "can this silicon run a 1.7-2B model usefully". **That entry
  exists at notes.md 7543 and that question is DONE.** For Q-A and Q-B the
  equivalent is the closing entry at **notes.md 10995**, which carries the
  twelve-prediction scorecard naming the row each was scored on, the plain
  answer judged against the plain answer predicted before any run, and what
  is not measured. **Both are DONE. The standard is that the closing entry
  exists and says what failed — not that every prediction passed.**

## Do not

- Do not start rung 4.
- Do not compact, summarise or reorganise `notes.md` in either repo.
- Do not write product or architecture thinking into this repo.
- Do not disable OEM unlocking on this device while it still has to go
  back.
- Do not re-lock the bootloader on a partial image.
