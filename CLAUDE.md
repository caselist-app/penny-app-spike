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

## State of play — 14 September 2026

Pixel 6a (bluejay), refurbished, 6GB Micron DRAM, 128GB Micron UFS.
Bootloader **LOCKED**, verified boot against a custom key. OEM unlocking
deliberately left **ENABLED** so the device can be returned to stock.

    GrapheneOS     2026091001
    Android        17, build ID CP2A.260705.006, patch 2026-09-01
    Bootloader     bluejay-17.0-15199431, locked, verifiedbootstate=yellow
    Debian guest   13.6 trixie, kernel 6.12.92-android16-6-...-4k
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

**The APK is hand-built, not Gradle-built.** `probe2a/build.sh` runs the
five stages directly — `aapt2 link`, `javac` for the stubs, `javac` for
the app, `d8`, `apksigner` — all from `build-tools;37.0.0`, with
`JAVA_HOME` pinned to Temurin 21 inside the script. The stubs compile to
a separate directory and are passed to `d8` with `--lib`, exactly as
`android.jar` is: visible to the compiler, absent from the APK. The
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
  The non-SDK restriction worry is **dead**: the class loads from
  `BootClassLoader` and `@SystemApi` members are gated by permission, not
  by the hidden-API blocklist. `pm grant` is the only gate.
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
- **2c. Does it own a VM running OUR guest?** Same again, with
  `USE_CUSTOM_VIRTUAL_MACHINE` granted and a custom config.
  `USE_CUSTOM_VIRTUAL_MACHINE` carries no `@RequiresPermission` anywhere
  in the API surface; VirtualizationService checks it at VM-creation time
  when the config is a custom one. So 2b and 2c differ by which config
  object gets built, not by which method gets called.
  **The gap between 2b and 2c is the entire product question.**

**Rung 3 — does that app solve the wake problem?** Only after rung 2.
Foreground service, start on `BOOT_COMPLETED`, restart after a kill.
DONE MEANS: reboot the device, touch nothing, and the VM is up and
reachable. This is the load-bearing rung — Penny ships as a headless
appliance, so nobody will be there to open an app. Needs no OS build.

**What rung 2 buys, and what it does not. Do not get this wrong.**
`pm grant` cannot ship. Both permissions are `development` protection
level, which means they can only be granted over adb, by a person with a
cable. There is no mechanism to grant them on a customer's device. So a
sideloaded app that owns a VM is **not a shippable product** — it is
proof that the VM machinery answers to an app rather than only to the OS,
and that proof is the thing that justifies spending weeks on rung 4.
Rung 4 is the commercial route: the app inside the OS image,
platform-signed, holding the permissions because it is part of the
system. At that point the app compiles inside AOSP against the real
system API, and both reflection and stubs disappear. Prove it cheaply
outside the OS; build it properly inside the OS. **Never mistake a
working `pm grant` prototype for a product.**

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
  all worked. Leading explanation, NOT yet proven: those two are `@hide`
  rather than `@SystemApi`, so the non-SDK blocklist does apply to them —
  which refines rung 2a's finding rather than contradicting it (2a said
  `@SystemApi` members are gated by permission, and that still holds).
  Practical rule: a `NoSuchMethodError` on a signature you read off the
  dex is a runtime block, not a typo. Get the value another way — the CID
  came from `vm list` instead, and nothing was lost.
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
- GrapheneOS sets the USB-C port to "charging-only when locked". It fits
  a lost-adb story perfectly and was **not** the cause last time. Check
  it, don't assume it.
- Port forwarding does **not** survive a VM restart. Symptom is
  `Connection closed by 127.0.0.1 port 2222` with every indicator looking
  healthy. Fix is `adb shell am force-stop com.android.virtualization.terminal`,
  reopen the app by hand, then rebuild `adb forward`.
- The VM does **not** start itself after a device reboot. The Terminal
  app has to be opened by hand. It reaches a prompt in 4-5 seconds.
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
  no workload. 54 minutes clean is a signal, not an answer. It may also
  not matter: if rung 3 succeeds, a VM that dies and self-restarts in ~1
  second beats one that survives all night and needs a human.
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
- Voice has never been through the VM boundary and the microphone was
  denied on this device. Deferred, not solved.
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
