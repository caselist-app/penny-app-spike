# notes.md

Append-only. Newest entries at the bottom. Never reorganise, compact or
rewrite an earlier entry — a wrong guess that got corrected is useful
history. Every entry is headed with a date and a rung number.

---

## 2026-09-14 — repo created. Carried-over state from penny-box, and what rung 2 has to answer.

`~/Documents/penny-app-spike` initialised as a new git repo next to
`~/Documents/penny-box`. penny-box is closed at commit d5a8371.

**Why a second repo.** penny-box asked whether the hardware and the OS
can run a Linux VM at all. It can, and that is settled. This repo asks a
different kind of question: not "does the machinery work" but "who is
allowed to drive it". The answer to that is a permission question, not a
capability question, and it decides whether Penny can ship as an app or
has to ship as an operating system.

**What carried over, verified on this device, from penny-box rung 1:**

- `adb shell /apex/com.android.virt/bin/vm run-microdroid` boots a VM as
  the unprivileged `shell` user on locked GrapheneOS with verified boot.
  No `pm grant` was needed and none was attempted.
- `vm list` showed that VM at `requesterUid: 2000` (Android's shell user)
  alongside the Terminal app's Debian VM at `requesterUid: 10179`. Two
  distinct owners, enumerated together in one call.
- Both VMs ran side by side. Boot to payload-ready was 1.14 seconds.
- The Debian guest did not notice the second VM. Across the launch minute
  its uptime advanced 60.15s against a 60s wall clock, used memory FELL
  3M, load went 0.08 to 0.12 and back. 55 samples, no gaps, 54 minutes.
- The expected memory-pressure kill on a 6GB phone did not happen at all.

Conclusion carried forward: **VM ownership is not the Terminal app's
private property.** The machinery is reachable from outside it.

**What rung 1 did not prove, and why rung 2 exists.** The payload that
booted was `EmptyPayloadApp`, which ships inside the `com.android.virt`
APEX and is platform-signed. Running it exercises
`MANAGE_VIRTUAL_MACHINE`, whose protection level carries `preinstalled`
— an escape hatch our app cannot use.
`USE_CUSTOM_VIRTUAL_MACHINE`, which has no such escape and which our own
guest image requires, has never been touched on this device.

**Not an attestation result.** The microdroid run logged `Using sample
DICE values` because it was a debuggable VM. No attestation claim rests
on anything proven so far.

**Device state at the time of writing** (unchanged from penny-box):
Pixel 6a (bluejay), 6GB Micron DRAM, 128GB Micron UFS. GrapheneOS
2026091001, Android 17, build ID CP2A.260705.006, patch 2026-09-01.
Bootloader `bluejay-17.0-15199431`, LOCKED, `verifiedbootstate=yellow`,
OEM unlocking deliberately left ENABLED. Debian 13.6 trixie in the guest,
kernel 6.12.92-android16-6-...-4k. Claude Code 2.1.270 installed and
authenticated in the guest, ~317MiB resident. VM slider at max: 3.9GB,
reporting 3.6Gi inside. 8 cores, 104G disk.

The q9 endurance sampler is dead — the device was rebooted. Its log
survives at `/home/droid/q9-soak.log` inside the Debian guest, 55 samples
ending 10:29:20Z.

**The API question, settled before this repo existed, recorded here so it
is not researched a third time.** `android.system.virtualmachine` is
`@SystemApi` and is not in the public SDK. In
`packages/modules/Virtualization/libs/framework-virtualization/api/`,
`current.txt` — the public surface — is empty, one line reading
`// Signature format: 2.0`, and all seven classes sit in
`system-current.txt`. `VirtualMachineManager` is annotated `@SystemApi`
at class level and on every public method. The module README states the
APIs "require the restricted android.permission.MANAGE_VIRTUAL_MACHINE
permission, so they are not available to third party apps" — and, in the
same breath, that "it can also be granted to other apps via `adb shell pm
grant` for development purposes". That second sentence is what keeps rung
2 alive.

Protection levels, read off this device in penny-box:

    MANAGE_VIRTUAL_MACHINE      signature|development|preinstalled
    USE_CUSTOM_VIRTUAL_MACHINE  signature|development
    DEBUG_VIRTUAL_MACHINE       signature

`development` is the operative word: both of the first two can be granted
over adb for prototyping, without building an OS.

For contrast, the Terminal app — the uid 10179 VM owner — is built
`platform_apis: true`, `privileged: true`, inside the `com.android.virt`
APEX. A privileged, platform-signed system app. Our app will be none of
those things.

**Consequence for tooling.** An app using these APIs cannot be compiled
in Android Studio against the stock `android.jar`, because the classes
are absent from it. Two routes exist that do not need a full AOSP
checkout: swap in an AOSP-built `android.jar` that carries the system
surface, or use reflection against the stock SDK. That is Matt's choice
and has not been made.

**No CLI fallback.** AOSP's `docs/custom_vm.md` describes the `vm run`
custom-VM route as requiring root over adb. There is no root on a locked
build. If the app route fails, there is no second route on this device.

**Rung 2 is three results, not one.** 2a: can a sideloaded app touch the
API at all — object, `SecurityException`, or `NoClassDefFoundError`.
Whether Android's runtime non-SDK restrictions permit a sideloaded,
non-platform-signed APK to reach `@SystemApi` members after `pm grant` is
genuinely unknown; the expectation is that the permission check is the
real gate, but no source states it plainly and at least one cautions that
the restrictions still apply to non-system apps. 2b: does it own a VM
running Google's stock microdroid payload — proven by a `requesterUid` in
`vm list` that is neither 2000 nor 10179. 2c: does it own a VM running
our own guest image, with `USE_CUSTOM_VIRTUAL_MACHINE` granted.
`USE_CUSTOM_VIRTUAL_MACHINE` carries no `@RequiresPermission` annotation
anywhere in the API surface; VirtualizationService checks it at
VM-creation time when the config is a custom one — so 2b and 2c differ by
which config object is built, not by which method is called. The gap
between 2b and 2c is the entire product question.

Nothing has been installed, built or run for this repo yet. No device
action has been taken.

---

## 2026-09-14 — rung 2 pre-flight: the Mac's build toolchain. Nothing is installed. Three things are missing.

Checked before writing any app code, on the Mac
(`mattstevenson@Matts-MacBook-Pro-2`). Three commands, no installs.

**JDK: ABSENT.**

    /usr/libexec/java_home -V
    The operation couldn't be completed. Unable to locate a Java Runtime.

`java_home` is macOS's own JDK registry. It reports no JVM of any version,
which means no Oracle JDK, no Temurin, no Homebrew `openjdk`, and no
Android Studio bundled JBR — Studio registers its own runtime here when
present.

**Android Studio: ABSENT. Android SDK: ABSENT.**

    ls -d /Applications/Android*.app ~/Library/Android/sdk

returned nothing. `~/Library/Android/sdk` is the default SDK root on
macOS and does not exist, so there are no build-tools, no platform jars
and no `sdkmanager`.

**What does exist: Homebrew's platform-tools, and only that.**

    which adb
    /opt/homebrew/bin/adb

That is the `android-platform-tools` Homebrew cask — `adb` and `fastboot`
as standalone binaries. It is the whole reason every penny-box device
command worked. It contains no compiler, no SDK platform, no
`aapt2`/`d8`, and no `android.jar` of any kind. It can install and talk to
an APK; it cannot build one.

**Therefore, to build any APK at all, three things are missing, in this
dependency order:** a JDK; an Android SDK (a platform jar to compile
against, build-tools to package and sign with); and a build driver
(Gradle, or Android Studio which bundles both Gradle and a JDK).

This is a genuinely clean machine as far as Android development goes —
which is the right starting point for this spike, because whatever we
install is a deliberate choice rather than something inherited.

Not yet decided, and deliberately not decided here: whether to install
full Android Studio or command-line tooling only, and whether the app
compiles against an AOSP-built `android.jar` or reaches the `@SystemApi`
surface by reflection against the stock SDK. Both are Matt's calls.
