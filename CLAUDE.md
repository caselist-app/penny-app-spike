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

Build toolchain on the Mac, installed 14 Sept, command-line only — no
Android Studio, deliberately (see `notes.md`):

    Temurin JDK    21.0.12.1 arm64, /Library/Java/JavaVirtualMachines/temurin-21.jdk
    SDK root       /opt/homebrew/share/android-commandlinetools
    platform       platforms;android-37.0    (Android 17 is API 37)
    build-tools    build-tools;37.0.0
    platform-tools 37.0.1
    Gradle         9.7.1  — INSTALLED BUT NOT USED, see below
    adb/fastboot   /opt/homebrew/bin, Homebrew android-platform-tools

There is **NO NDK and no C compiler on the Mac**, deliberately. Rung 2d needed
one and the NDK is a 974,984,488-byte download over a phone tether; Homebrew's
`lld` pulls in `llvm` and is larger still. The guest payload is compiled
instead **inside the Debian guest on the phone**, which is already arm64 and
needs no cross-compiler: `gcc 14.2.0 (Debian 14.2.0-19)`, installed 15 Sept
with `--no-install-recommends` for 43MB. See the glibc/bionic trap below —
that is why `payload/penny_payload.c` uses no C library at all.

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
built elsewhere — which is both the control seam AND, since there is no NDK
here, the normal route. **Since rung 3c there are TWO payloads in the APK**,
`PennyPayload.so` (2d) and `Penny3cPayload.so` (3c, via
`PENNY_PAYLOAD_3C_SO=<path>`), packaged side by side rather than one replacing
the other: microdroid loads only the file `setPayloadBinaryName()` names, two
entries cost nothing, and it keeps the 2d result reproducible from the same
build. Both must read `Stored` in the final `unzip -lv`. The
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
down. The guest payload was **not rebuilt**: `Penny3cPayload.so` from 3c was
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
moment: the host in fact pays ~1.92GB up front, at VM creation. Nor is it
known where a
model FILE would live — 3d logged `init: Unknown /data fs type: tmpfs` and
every guest here built a zram swap sized to its whole RAM, so a 1.5GB model
file may cost 1.5GB of RAM on top of running it. That is **rung 3e-iii, NOT
RUN.** Nothing ran inside the VM: the payload is 3c's, unchanged, and uses
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
seconds, not two hours. Unlocked, foreground, over adb. And where a model
FILE would live is **rung 3e-iii, NOT RUN**, now more urgent rather than
less: the guest's `/data` is `tmpfs` and zram already claims a swap device
the size of the whole guest, so a 1.5GB model file may cost 1.5GB of RAM
before anything loads it, against a measured ceiling of 1792MB.

**Rung 3e-iii — where does a model FILE live? DEFINED, NOT RUN.** The last
rung in this group and the one that can still close the plan down. 3e-ii
proved 1792MB of a 2048MB VM is genuinely writable. If a 1.5GB model FILE
must also sit in RAM before anything loads it, then the file and the running
model both come out of that same 1792MB and it does not fit.

**Why it is suspected.** Rung 3d's shutdown log reads `init: Unknown /data fs
type: tmpfs`. tmpfs is a RAM disk. And every guest measured in 3e-i and 3e-ii
built a zram swap device sized to its whole RAM, which is more RAM spoken
for. Nothing here has ever written a file inside a guest, so this is
inference, not measurement.

**What to measure, in this order.**

1. **What is actually mounted.** The payload reads `/proc/mounts` and
   `/proc/meminfo` and prints them. This alone may answer it: if every
   writable mount is `tmpfs` there is no disk in there at all. Cheap, and
   nothing else is worth doing before it is known.
2. **What a file costs.** Write an N-MB file into each writable location and
   watch the guest's own `MemFree` across the write. If MemFree falls by N,
   the file is RAM. If it does not, there is real storage. Use the 3e-ii
   accounting method — it caught zram doing nothing and it will catch this.
3. **The combined ceiling, which is the number that matters.** With an N-MB
   file written, re-run 3e-ii's touch loop and find how much is still
   reachable. `file + touchable` versus 1792MB is the whole question.
4. **THE LIKELY ESCAPE HATCH, and test it even if 1-3 look bad.** The APK is
   already mounted read-only INSIDE the guest — that is how the payload `.so`
   is loaded at all, via `setApkPath()` plus `zip -0` plus `zipalign -p 4`.
   So a model file packaged into the APK the same way could be `mmap`ed by
   the guest out of a read-only mount rather than copied into a RAM disk.
   That costs reclaimable page cache instead of anonymous memory, and the
   packaging machinery is proven since 2d. Test: put a large Stored,
   page-aligned file in the APK, `mmap` it in the guest, read every page, and
   watch MemFree. **If this works the rung is answered even if `/data` is
   tmpfs, and it is the route rung 4 should design around.**

**Control first, as five rungs running have done.** A 256MB guest writing a
64MB file, with the accounting checked against a known figure, before any
number that matters is measured.

**DONE MEANS:** a yes/no on whether the guest's writable storage is RAM, a
number for what a 1.5GB file costs, and a yes/no on whether the APK route
avoids that cost. Then stop and report.

Needs the guest payload rebuilt, so it costs a manual round trip to open the
Terminal app on the phone for the Debian guest's gcc. Give it its OWN
component and its own VM name; do not touch `VmService`, `Penny3dService`,
`Probe3eActivity` or `Probe3eiiActivity`.

**Rung 4 — the OS image. DO NOT START IT.** Build GrapheneOS from source,
preinstall the app, sign with our platform key, flash, lock, verify
attestation covers the app. Weeks. Not now.

Before any app code: establish whether a JDK is present, whether the
Android SDK or Android Studio is present, and what needs installing.

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
- **`adb logcat -G 64M` before measuring anything with a DEBUG_LEVEL_FULL
  guest.** One guest console is several hundred lines a second and three at
  once evicted a whole run's own log lines from the default ring buffer
  before they could be read. The symptom is a probe that appears to have
  printed nothing.
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
  real figure was measured.
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
- **Copy first, kill second.** `sdkmanager` wipes its own
  `.temp/PackageOperation01/` on exit, so killing it and then trying to rescue
  the partial download loses the race. Cost a ~100MB partial NDK on 15 Sept.
  The empty `ndk/30.0.16248370` directory it left behind is a shell, not an
  install — `build.sh` tolerates it and resolves `CLANG` to empty.
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
  than anything clever on the phone. Run `system_profiler SPUSBDataType`
  on the Mac **before** `adb devices` — empty output means macOS sees
  nothing on the bus at all, and no adb question can help. A phone
  charging normally can still have a dead data path. This cost an
  afternoon.
- GrapheneOS sets the USB-C port to "charging-only when locked". It was
  not the cause in penny-box, but it **WAS** the cause on 14 Sept during
  rung 3: after `adb reboot` the device never came back on adb, and
  `system_profiler SPUSBDataType` showed nothing on the bus at all, until
  the phone was unlocked by hand. Note `aapm_usb_data_protection=0` did
  NOT predict this — that is a different GrapheneOS setting. Plan for it:
  anything measured across a reboot must be readable from `logcat` AFTER
  an unlock, because logcat survives an unlock and only dies on reboot.
  Timestamps (`sinceBoot=`) are what let you prove what happened before
  the human touched it.
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
- Host-side memory was NEVER measured. The sampler read the guest, not
  Android. "Two VMs caused no pressure" is a statement about the guest
  only.
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
  guest".
- **The memory gate is OPEN and now measured on both sides. 3e-i: 2GB and 8
  vCPUs are given. 3e-ii: 1792MB of it is genuinely writable, readable back
  and held, twice.** What is left is **`3e-iii`, DEFINED AND NOT RUN** — is
  microdroid's writable storage a RAM disk, so that a 1.5GB model FILE costs
  1.5GB of RAM before anything loads it? 3e-ii made that more urgent, not
  less: the ceiling is 1792MB and the guest's `/data` is `tmpfs`. It needs
  the guest payload rebuilt, i.e. a round trip into the phone's Debian guest
  for gcc.
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

## Do not

- Do not start rung 4.
- Do not compact, summarise or reorganise `notes.md` in either repo.
- Do not write product or architecture thinking into this repo.
- Do not disable OEM unlocking on this device while it still has to go
  back.
- Do not re-lock the bootloader on a partial image.
