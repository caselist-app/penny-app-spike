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

---

## 2026-09-14 — rung 2 route DECIDED: reflection to probe, stubs to build on. The third-party jar rejected.

Matt's call, taken after the trade-off was put to him in plain English.
**Reflection for 2a. Self-written stub classes for 2b and 2c.**

**The reasoning that decided it, and it is worth not losing.** All three
candidate routes — reflection, stub classes, third-party AOSP
`android.jar` — differ only in how the *compiler* is satisfied. The
compiler refuses to name a class that is absent from the public
`android.jar`, and each route is a different way around that refusal.
None of them changes what happens on the phone. Android enforces the real
gate at call time: does this app hold `MANAGE_VIRTUAL_MACHINE`, and is it
permitted to touch this member? That enforcement is identical in all
three cases.

The consequence is the useful part: **a 2a verdict reached by reflection
binds on every route.** A yes is a real yes for stubs and for the AOSP
jar. A no is a real no for both. Only the shape of the error text
differs — a reflection failure surfaces as `ClassNotFoundException` /
`NoSuchMethodException`, a direct-linkage failure as
`NoClassDefFoundError` / `NoSuchMethodError`. Same gate, different
messenger. This is why we can afford to answer the unknown cheaply
without committing to tooling first.

So: 2a is ~20 lines of reflection — ask Android for the class by its name
as a string, try to obtain a `VirtualMachineManager`, report which of the
three outcomes came back. No extra downloads beyond the standard Google
SDK. Then, once the API is known to be reachable, switch to stubs for 2b
and 2c, where VM config objects get built through long builder chains and
reflection stops being merely ugly and starts being a source of its own
bugs.

**Stub classes, for the record, since they are what we will actually
build on.** We write our own minimal versions of the seven classes —
correct package, correct names, correct method signatures, empty bodies —
and mark them compile-only so they are never packaged into the APK. The
compiler is satisfied; at runtime the phone supplies the real
implementations. This is the established technique for reaching a hidden
Android API from a normal app (Shizuku is built this way). Its one real
hazard is recorded now so it is not mistaken for a result later: **if our
stub signatures do not exactly match the real ones, the app crashes on
the phone in a way that can read as the permission being refused when it
is not.** That is precisely the signal 2a exists to read, which is a
second reason to get 2a's verdict by reflection first, while no stub
exists to muddy it.

**Third-party AOSP-built `android.jar`: REJECTED.** It would be the least
work — the full internal API surface, compiled, published on GitHub by
third parties. Rejected on trust and on maintenance. Google does not
distribute these, so adopting one means an unofficial binary of unknown
provenance sitting in the build of a project whose entire premise is a
locked, verified, attested device; and it would have to be re-sourced for
every Android version. Recorded as considered and declined rather than
overlooked.

Still undecided: full Android Studio versus command-line tooling only.

---

## 2026-09-14 — scope correction: `pm grant` cannot ship. What rung 2 actually buys.

Raised by Matt while the route decision was being committed — that this
is a commercial product and the way we develop has to reflect that.
Correct, and it sharpens what these rungs are for. Recorded here and in
CLAUDE.md in the same step.

**`pm grant` is not a distribution mechanism.** Both permissions rung 2
depends on are `development` protection level:

    MANAGE_VIRTUAL_MACHINE      signature|development|preinstalled
    USE_CUSTOM_VIRTUAL_MACHINE  signature|development

`development` means grantable over adb, by a person holding a cable, on a
device in front of them. There is no path to granting them on a
customer's device — no runtime prompt, no Play Store flow, nothing. A
sideloaded app that owns a VM is therefore **not a product**, and rung 2
succeeding does not produce one.

**What it does produce** is the only thing that matters at this stage: a
demonstration that VirtualizationService will take orders from an
ordinary app process rather than only from platform-signed system
components. That is a question about Android's architecture, not about
our packaging, and it is answerable cheaply. If the answer is no — if the
runtime refuses a non-platform-signed APK even with the permission
granted — then rung 4 is pointless and the whole phone-based approach
needs re-examining before weeks are spent on an OS build. **Rung 2 is a
go/no-go gate on rung 4, not a shipping milestone.**

**The commercial route is rung 4 and only rung 4:** the app built into
the GrapheneOS image, signed with our platform key, holding the
permissions because it is part of the system, with attestation covering
it. At that point the app is compiled inside AOSP against the genuine
system API surface — and reflection and stubs both become unnecessary and
get deleted. Neither technique is intended to survive contact with a
shipping build.

So the development pattern, stated once so it does not have to be
rediscovered: **prove it cheaply outside the OS, then build it properly
inside the OS.** The failure mode to guard against is a working `pm
grant` prototype being mistaken for a product, and rung 4 being deferred
on the strength of it.

---

## 2026-09-14 — toolchain installed on the Mac. Command-line only, no IDE. One trap found on the way out.

Matt's call: **command-line tooling only, no Android Studio.** Reasoning
recorded because it will be questioned later. Every build becomes a
command that can be pasted, which is how this project already works and
means the exact invocation lands in `notes.md` and is reproducible.
Studio is a code editor, and Matt does not write code — Claude writes the
files, Matt runs the commands — so most of its ~2GB is weight we never
touch. `adb logcat` already covers reading errors off the phone. And rung
4 ultimately happens on a headless Linux build machine with no IDE at
all, so the command-line habit is the one worth building.

**Installed, in dependency order, all via Homebrew (already present — it
is where penny-box's `adb` came from):**

    Temurin JDK        21.0.12.1 (arm64, Eclipse Adoptium)
                       /Library/Java/JavaVirtualMachines/temurin-21.jdk
    cmdline-tools      brew cask android-commandlinetools, 156.1MB
                       SDK root /opt/homebrew/share/android-commandlinetools
    platforms;android-37.0      rev 2
    build-tools;37.0.0          37.0.0
    platform-tools              37.0.1
    Gradle             9.7.1 (build time 2026-08-19)

`/usr/libexec/java_home -V` went from "Unable to locate a Java Runtime"
to reporting Temurin 21, which is the check that the JDK registered
properly rather than merely unpacking somewhere.

**Which Android platform, and why it was looked up rather than guessed.**
The device runs Android 17. `sdkmanager --list` shows Android 17 as API
**37**, with stable `platforms;android-37.0` plus quarterly minor
releases `37.1` and `37.2`, beta builds, and a `CANARY` channel. We
compile against `android-37.0` — the stable release matching the device —
with `build-tools;37.0.0` to match. Nothing about the reflection probe
needs a specific platform, but matching the device removes a variable
from a test whose entire output is an error message.

**TRAP, found immediately and not yet worked around:** `brew install
gradle` pulls Homebrew's own `openjdk` as a dependency, and Gradle runs
on it in preference to Temurin. `gradle --version` reports:

    Launcher JVM:  26.0.2.1 (Homebrew 26.0.2.1)
    Daemon JVM:    /opt/homebrew/Cellar/openjdk/26.0.2.1/... (no Daemon JVM specified)

That is **JDK 26**, not the Temurin 21 we installed. The Android Gradle
Plugin does not support a JDK that new, and the resulting failure would
arrive as a compile or plugin error — i.e. it would read as our code or
our permissions being wrong when the real cause is the Java version. Note
also that `/usr/libexec/java_home` does **not** list this JDK 26, because
Homebrew formula JDKs are not registered with macOS's JVM registry; so
the earlier "only Temurin 21 is installed" check was true and still
misleading about what Gradle would actually pick up.

Fix, to be applied when the project is created: pin the build explicitly
with `org.gradle.java.home` in the project's `gradle.properties`, rather
than relying on a `JAVA_HOME` environment variable that lives only in one
terminal session. Pinned in the repo, it survives new shells, reboots and
a different machine.

Nothing has been built and no device action has been taken.

## 2026-09-14 — rung 2a: build route DECIDED against Gradle. The probe APK builds clean on the first attempt.

**The choice.** Two ways to turn one Java class into an installable APK:
a Gradle project (the standard Android build) or the four SDK tools that
make up what Gradle orchestrates. Gradle won on familiarity and on
carrying forward to rung 3. It lost on everything else.

An Android Gradle Plugin build requires Gradle, AGP and the JDK to agree
on versions before a single line of our code is read. We already know
that handshake is broken on this machine — Gradle 9.7.1 is running on
Homebrew's JDK 26, which AGP does not support. A version mismatch there
surfaces as a compile or plugin failure. Rung 2a's entire output is a
failure message: we are trying to distinguish "the runtime refused a
third-party app" from "the class is not visible" from "the permission did
not take". Introducing a fourth failure shape that looks like the first
three, in the one experiment whose whole product is an error string,
would have been a bad trade. Rung 4 compiles inside AOSP with Soong, not
Gradle, so no Gradle work was ever going to survive anyway.

**What replaced it.** `probe2a/build.sh`, four stages, no downloads:

    aapt2 link    AndroidManifest.xml -> a compiled, resource-free APK
    javac         one .java -> JVM class files, against the public android.jar
    d8            JVM class files -> Android dex bytecode
    apksigner     dex into the APK, signed with a throwaway local RSA key

The script pins `JAVA_HOME` to Temurin 21 itself rather than trusting the
shell, and resolves the build-tools and platform directories with a glob
(`build-tools/37*`, `platforms/android-37*`) because Android 17 ships
minor platform revisions and the directory names follow them. It echoes
what it resolved before doing anything, so a bad path shows up as a
printed path rather than as a confusing tool error.

**First run, clean:**

    java      openjdk version "21.0.12.1" 2026-08-18 LTS
    build     /opt/homebrew/share/android-commandlinetools/build-tools/37.0.0
    platform  /opt/homebrew/share/android-commandlinetools/platforms/android-37.0/android.jar
    1/4 manifest linked
    2/4 java compiled
    3/4 dexed
    4/4 signed
    built: .../penny-app-spike/probe2a/build/probe2a.apk

Note the confirmations buried in that: the build ran on **Temurin 21**,
not on Homebrew's JDK 26, so the trap is genuinely sidestepped rather
than merely avoided by luck. The platform directory really is
`android-37.0`, matching the device's Android 17. And `javac` compiled
against the stock public `android.jar` without complaint, which it could
only do because the probe names no AVF class at compile time.

**What the probe is.** `probe2a/src/com/pennyspike/probe2a/MainActivity.java`,
one class, no UI, no VM, no guest. It logs everything under the tag
`PENNY2A` and reports, in order: its own uid and the device fingerprint;
whether each of the two permissions is actually GRANTED (so that a
refusal later cannot be silently blamed on a `pm grant` that did not
take); then four steps —

    STEP1  Class.forName("android.system.virtualmachine.VirtualMachineManager")
    STEP2  enumerate its real declared methods rather than trust documentation
    STEP3  obtain an instance, via getInstance(Context), else getSystemService(Class)
    STEP4  call getCapabilities() — read-only, creates nothing

Failing at STEP1 means the class is not visible to a third-party
classloader and the app route dies immediately. Failing at STEP3 or STEP4
means it is visible but the runtime's non-SDK interface restrictions or a
permission check refused the call — a different and more recoverable
answer. Reaching STEP4 means we hold a live `VirtualMachineManager`.
`describe()` unwraps `InvocationTargetException` so the log carries the
real exception type, which is the thing that separates those cases.

The manifest declares both permissions because `pm grant` will only grant
a permission an app has asked for. Declaring them proves nothing — a
sideloaded APK cannot hold a signature-level permission by naming it.

The APK is signed with a locally generated 2,048-bit RSA key created by
the script on first run. That key is **not** the platform key and confers
no privilege whatsoever; it exists because Android refuses to install an
unsigned APK. `build/` and `debug.keystore` are gitignored — the built
artifact and a private key do not belong in the repo.

Still no device action. The APK exists on the Mac and has not been
installed.

## 2026-09-14 — rung 2a ANSWERED YES. A sideloaded app holds a live VirtualMachineManager and calls it.

Device: Pixel 6a bluejay, GrapheneOS 2026091001, Android 17,
fingerprint `google/bluejay/bluejay:17/CP2A.260705.006/2026091001:user/release-keys`.
App: `com.pennyspike.probe2a`, hand-built APK, signed with a throwaway
local RSA key, sideloaded by `adb install`. **uid 10192.** Not
platform-signed, not preinstalled, not privileged, no `platform_apis`.

The sequence, all on the Mac:

    adb install -r probe2a/build/probe2a.apk
    adb shell pm grant com.pennyspike.probe2a android.permission.MANAGE_VIRTUAL_MACHINE
    adb shell pm grant com.pennyspike.probe2a android.permission.USE_CUSTOM_VIRTUAL_MACHINE
    adb shell am start -n com.pennyspike.probe2a/.MainActivity
    adb logcat -d -s PENNY2A

**Both `pm grant`s were accepted silently, on GrapheneOS, with the
bootloader locked.** The probe then confirmed from inside the app that
both read back GRANTED, which closes the "did the grant actually take"
question rather than inferring it from the absence of an error.

**STEP1 — the class is visible.**

    STEP1 class found: android.system.virtualmachine.VirtualMachineManager
                       loader=java.lang.BootClassLoader@278cbd3

`BootClassLoader`, not the app's own. `framework-virtualization` is a
boot-classpath jar in the `com.android.virt` APEX and is therefore
visible to every process on the device, third-party apps included. No
`<uses-library>` was needed.

**The non-SDK restriction question is ANSWERED, and the answer is that it
does not apply here.** CLAUDE.md listed it as the genuine unknown in 2a:
whether Android's runtime restrictions on non-SDK interfaces would refuse
a sideloaded APK access to `@SystemApi` members even after `pm grant`.
They did not. This matters and the reasoning needs stating, because the
naive reading of the log could go the other way.

Under hidden-API enforcement a *blocked* member is made to look absent:
`getDeclaredMethods()` filters it out and `getMethod()` throws
`NoSuchMethodException`. We saw exactly that shape at STEP3A. But it is
not evidence of blocking here, because the members that *were* listed —
`create`, `delete`, `get`, `getCapabilities`, `getOrCreate`,
`importFromDescriptor` — are themselves `@SystemApi`, and one of them was
not merely listed but successfully **invoked**. If `@SystemApi` members
were on the blocklist for this app, none of them would have appeared at
all. So: `@SystemApi` is gated by permission, not by the non-SDK
blocklist. The gate we are fighting is `pm grant`, and only `pm grant`.

**STEP2 — the real API surface, read off the device rather than off a
document.** Six public methods on `VirtualMachineManager`:

    VirtualMachine create(String, VirtualMachineConfig)         throws VirtualMachineException
    void           delete(String)                               throws VirtualMachineException
    VirtualMachine get(String)                                  throws VirtualMachineException
    int            getCapabilities()
    VirtualMachine getOrCreate(String, VirtualMachineConfig)    throws VirtualMachineException
    VirtualMachine importFromDescriptor(String, VirtualMachineDescriptor)
                                                                throws VirtualMachineException

That is the whole surface. It is also the exact signature list the stub
classes for 2b/2c must match, so it is recorded here verbatim rather than
re-derived later.

**STEP3 — how you actually get one.** `getInstance(Context)` **does not
exist**; the probe tried it first and got `NoSuchMethodException`. The
factory is the ordinary system-service route:

    VirtualMachineManager m = context.getSystemService(VirtualMachineManager.class);
    // returned android.system.virtualmachine.VirtualMachineManager@73eec3a

This is why the probe enumerated the methods before calling anything.
Guessing the factory would have produced a `NoSuchMethodException` that
is indistinguishable, to anyone reading quickly, from the runtime
refusing a third-party app — the precise confusion rung 2a exists to
avoid.

**STEP4 — a live call succeeded.**

    getCapabilities() = 2

An object that answers is worth more than an object that merely exists;
`getCapabilities()` was chosen because it is read-only and creates
nothing, so a pass proves the binder call reached VirtualizationService
and came back without proving anything about VM creation.

**UNVERIFIED, and deliberately not guessed at:** what the value 2 means.
The API defines capability bits (protected VM / non-protected VM) and 2
is almost certainly "non-protected VMs supported, protected VMs not".
If that is right it is a significant hardware fact about this Pixel 6a
and belongs in the open threads, not buried in a sentence. The constants
are static fields on the same class and can be read by reflection in the
same way the methods were, so this will be settled by measurement in the
next entry rather than by recollection.

**What this does and does not establish.** It establishes that the app
route is alive: the machinery answers to an ordinary app holding granted
permissions, not only to the OS. It establishes nothing about owning a
VM — no `create` call has been made, no config object has been built, and
`USE_CUSTOM_VIRTUAL_MACHINE` has been granted but never exercised. And it
remains a prototype, not a product: `pm grant` needs a cable, so this is
the evidence that justifies rung 4, not a shippable thing.

Rungs 2b and 2c are unaffected by the reflection route used here. The
gate is a runtime permission check, identical however the compiler was
satisfied.

## 2026-09-14 — addendum to 2a: the capability bitmask decoded. This device does NOT support protected VMs.

The previous entry left `getCapabilities() = 2` deliberately
un-interpreted. Rather than recall what the constants are, the probe was
extended by eight lines to read the class's own static fields by
reflection — the same mechanism that read the methods — rebuilt,
reinstalled over the top, and re-run. `pm grant` survived the
`adb install -r` because the signing key was unchanged, so the two
permissions did not need re-granting.

    STEP2F field: CAPABILITY_NON_PROTECTED_VM = 2
    STEP2F field: CAPABILITY_PROTECTED_VM     = 1
    STEP4  getCapabilities() = 2

Those are the only two static fields on the class, so the bitmask is
fully accounted for. **2 means bit 1 set and bit 0 clear: non-protected
VMs supported, protected VMs NOT supported** on this Pixel 6a running
GrapheneOS 2026091001. The guess in the previous entry was right, but it
is now a measurement.

**What the distinction actually is.** A protected VM is one the
hypervisor walls off from the host: Android cannot read the guest's
memory, and the guest can make a remote-attestation claim about itself
distinct from the OS. A non-protected VM is isolated from other apps and
from other VMs by the normal mechanisms, but the host OS and anything
with sufficient privilege on it can see inside. Both run under the same
`VirtualizationService` and the same API; the difference is set on the
config object, which means it lands squarely in 2c territory.

**Consequence for the spike, stated plainly.** Anything that assumed the
VM boundary protects the workload from the operating system underneath it
is unavailable on this hardware. What remains is the penny-box position:
trust rests on verified boot and on attestation of the whole OS image,
not on the guest being opaque to its host. That was already the
architecture; this closes off the alternative rather than changing the
plan. Note also that rung 1's microdroid run logged `Using sample DICE
values` — no attestation claim has ever been demonstrated on this device
by any route.

**Open, and worth an answer before the 7a arrives.** Whether the missing
protected-VM bit is the 6a's silicon, the GrapheneOS build, or the
Android 17 build. It is cheap to distinguish: the same probe run on a
different device or a stock build answers it in one reading. Not chased
now — it does not block 2b or 2c, both of which use non-protected
configs.

Also recorded, because it cost a minute: `adb install -r` does not stop a
running activity. `am start` returned "intent has been delivered to
currently running top-most instance" and `onCreate` never re-ran, so the
log showed nothing new. `adb shell am force-stop com.pennyspike.probe2a`
before `am start` is required on every re-run of a probe that does its
work in `onCreate`.

## 2026-09-14 — rung 2b ANSWERED YES. A sideloaded app created, booted and owns a microdroid VM running Google's stock payload.

Device: Pixel 6a bluejay, GrapheneOS 2026091001, Android 17, build
CP2A.260705.006, bootloader LOCKED, `verifiedbootstate=yellow`.
App: `com.pennyspike.probe2a`, **uid 10192**, hand-built APK signed with a
throwaway local RSA key, sideloaded. Not platform-signed, not
preinstalled, not privileged. Both VM permissions granted by
`adb shell pm grant` and confirmed `granted=true` in `dumpsys package`
after the reinstall (the signing key was unchanged, so they survived).
Build toolchain unchanged: Temurin 21.0.12.1, build-tools 37.0.0,
platform android-37.0, no Gradle.

**THE RESULT, from outside the app rather than from its own log:**

    $ adb shell /apex/com.android.virt/bin/vm list
    Running VMs: [
        VirtualMachineDebugInfo {
            name: "penny2b",
            cid: 2050,
            temporaryDirectory: "/data/misc/virtualizationservice/2050",
            requesterUid: 10192,          <-- the sideloaded app
            requesterPid: 5783,
            hostConsoleName: None,
        },
        VirtualMachineDebugInfo {
            name: "debian",
            cid: 2049,
            temporaryDirectory: "/data/misc/virtualizationservice/2049",
            requesterUid: 10179,          <-- the Terminal app
            requesterPid: 3760,
            hostConsoleName: None,
        },
    ]

`requesterUid: 10192`. Not 2000 (the adb `shell` user, which rung 1
already proved and which proves nothing about apps). Not 10179 (the
privileged, platform-signed Terminal app). The app's own uid. Two VMs,
two unrelated owners, enumerated side by side — rung 1's coexistence
result reproduced, but this time with an ordinary app as one of the
owners.

**The process tree, which is the strongest form of the evidence:**

    USER      PID   PPID  NAME
    system    4103  1     virtualizationservice
    u0_a179   4209  3760  virtmgr_zation.terminal
    u0_a179   4230  4209  crosvm
    u0_a179   4241  4209  crosvm_debian
    u0_a192   5783  793   com.pennyspike.probe2a
    u0_a192   5812  5783  virtmgr_nyspike.probe2a
    u0_a192   5829  5812  crosvm_penny2b

`crosvm_penny2b` — the process actually running the guest — executes as
`u0_a192`, our app's uid, under a `virtmgr` spawned as a child of our
app's process. The Terminal app's VM sits in a completely separate tree
under `u0_a179`. Each app gets its own manager and its own crosvm; the
single system-uid `virtualizationservice` brokers, it does not own. This
is what "the app owns the VM" means concretely, and it is worth being
able to say in one sentence: *the hypervisor process for Penny's VM runs
as Penny's app, not as the operating system.*

**"The Terminal app knows nothing about it" — the evidence.** VM state is
stored per-app. The probe's `delete()` of a non-existent VM reported the
exact path it looked in:

    java.nio.file.NoSuchFileException: /data/user/0/com.pennyspike.probe2a/vm/penny2b

That is our app's private data directory, not the Terminal app's, and not
a shared location. Combined with the separate process trees and distinct
`requesterUid`s, the Terminal app has no handle on `penny2b` and no way
to enumerate it. `vm list` sees both only because it is a debug tool
talking to `virtualizationservice` directly, over adb.

**What was booted, and why it counts as Google's payload rather than
ours.** microdroid does not run an APK; it runs one native binary named
inside the config. The config pointed at:

    apk     /apex/com.android.virt/app/EmptyPayloadApp@CP2A.260705.006/EmptyPayloadApp.apk
    payload MicrodroidEmptyPayloadJniLib.so

Both come out of the `com.android.virt` APEX — Google's code, shipped
with the OS, `-rw-r--r--` and therefore readable by an ordinary app. The
APK contains exactly one native library, confirmed by pulling it to the
Mac and listing it. The APEX directory name carries the OS build ID, so
the probe resolves it at runtime by prefix match rather than hard-coding
`CP2A.260705.006`.

Notably, `setApkPath()` pointing at an APK the app does **not** own was
accepted. No permission complaint, no ownership check. That was a genuine
unknown going in.

**It actually booted.** Not merely registered:

    14:40:08.889  STEP5 create() returned VirtualMachine(name:penny2b, ...)
    14:40:08.930  STEP6 run() returned, status=RUNNING
    14:40:09.795  CB onPayloadStarted
    14:40:09.813  CB onPayloadReady — microdroid booted the stock payload

~940ms from `run()` to `onPayloadReady`, consistent with rung 1's 1.14s
for the same payload launched from the adb shell. `onPayloadReady` is
sent by code running *inside* the guest, so it is proof of a booted
kernel and a started payload, not just an accepted request.

**Route: stubs, not reflection, exactly as CLAUDE.md decided.** Five
compile-only stub classes were written under
`probe2a/stubs/android/system/virtualmachine/` —
`VirtualMachineManager`, `VirtualMachineConfig` (with its `Builder`),
`VirtualMachine`, `VirtualMachineCallback`, `VirtualMachineException`.
They declare only the members 2b calls. `build.sh` grew from four stages
to five: the stubs compile to `build/stubs` and are handed to `d8` with
`--lib`, the same way `android.jar` is — visible to the compiler, absent
from the APK. The script now ends by grepping the built dex for
`Landroid/system/virtualmachine/` and printing the count; it printed 0.
That check is not ceremony: a shipped stub would put a fake copy of a
platform class in the APK and the winner would be a coin toss.

**Method: the signatures were extracted, not recalled.** Rather than
guess at `VirtualMachineConfig.Builder` and burn a build-install-run
cycle per wrong guess, the device's own boot-classpath jar was pulled and
disassembled on the Mac:

    adb pull /apex/com.android.virt/javalib/framework-virtualization.jar
    unzip -o framework-virtualization.jar classes.dex
    build-tools/37.0.0/dexdump -e classes.dex

That yields every class, method signature and constant in the real
implementation, offline, in one step. **This is the technique to reuse
for 2c** — it is the reason 2b worked on the first device run. It also
produced the full `Builder` surface, of which 2b used `setApkPath`,
`setPayloadBinaryName`, `setDebugLevel`, `setProtectedVm`,
`setMemoryBytes`, `setCpuTopology`. The one that matters for 2c and was
deliberately NOT touched here is `setPayloadConfigPath` — and next to it
`setCustomImageConfig`, plus a whole `VirtualMachineCustomImageConfig`
builder carrying `setKernelPath`, `setInitrdPath`, `addDisk`,
`addSharedPath`, `useNetwork`. That is the 2c surface, recorded now while
it is in front of us, and not exercised.

**A NEW AND UNEXPECTED FINDING: class-level access does not imply
member-level access.** Two calls failed at runtime:

    NoSuchMethodError: No virtual method getOs()Ljava/lang/String;
      in class Landroid/system/virtualmachine/VirtualMachineConfig;
      (declaration appears in /apex/com.android.virt/javalib/framework-virtualization.jar)
    NoSuchMethodError: No virtual method getCid()I
      in class Landroid/system/virtualmachine/VirtualMachine;

These are not typos and not stale stubs. Both signatures were copied
verbatim out of the `dexdump` of the very jar the error names, so the
methods provably exist in the loaded class. Meanwhile `create`,
`getOrCreate`, `delete`, `run`, `setCallback`, `getStatus`, `getName`,
`getApkPath`, `getPayloadBinaryName`, `isProtectedVm` and
`getCapabilities` all worked from the same app in the same process.

Leading explanation, and it is **NOT yet proven**: `getCid()` and
`getOs()` are `@hide` rather than `@SystemApi`, so the non-SDK interface
blocklist applies to them, while the `@SystemApi` members are gated by
permission only. That refines rung 2a rather than contradicting it — 2a's
claim was about `@SystemApi` members and still stands. The alternatives
not yet ruled out: a different hidden-API list tier, or a deliberate
GrapheneOS restriction. Distinguishing them would mean flipping
`settings put global hidden_api_policy`, which changes device-wide state
and was not done. Not chased, because it cost nothing: the CID was
obtained from `vm list` instead.

**The practical rule, now in CLAUDE.md:** a `NoSuchMethodError` on a
signature you read off the device's own dex is a runtime block, not a
mistake. Get the value another way rather than re-deriving the signature.

**What 2b does NOT establish. Be strict about this.**
- Nothing about running **our** guest. The payload was Google's, from a
  Google-signed APK inside the OS image. Whether a custom config and our
  own image is permitted is rung 2c, untouched, and it is where
  `USE_CUSTOM_VIRTUAL_MACHINE` finally gets exercised — the permission
  has now been held, unused, through two rungs.
- Nothing about surviving anything. The VM is a child of the app process;
  kill the app and it goes. Reboot survival is rung 3.
- Nothing about attestation. `DEBUG_LEVEL_FULL` was used, so as in rung 1
  the guest runs with sample DICE values and attests nothing.
- Nothing about shipping. `pm grant` still needs a cable. 2b makes the
  case for rung 4; it is not a product.
- Protected VMs remain unavailable on this device (measured in the 2a
  addendum). `setProtectedVm(false)` was passed explicitly.

## 2026-09-14 — rung 2c PRE-FLIGHT ONLY. Where the permission is enforced, and what can be borrowed. NOT a 2c answer.

**This entry answers nothing about 2c.** It is reconnaissance done before
committing to a route, recorded because it changes which route is worth
taking and because it cost nothing. 2c gets its own entry when it is
actually run.

**1. The permission check is not in the framework jar, so 2c cannot be
settled offline.** The full dex of
`/apex/com.android.virt/javalib/framework-virtualization.jar` was
disassembled on the Mac (`dexdump -d`) and searched for every reference
to either permission string. There are exactly two, and both are the
field declarations themselves:

    Landroid/system/virtualmachine/VirtualMachine;  ->  MANAGE_VIRTUAL_MACHINE_PERMISSION
    Landroid/system/virtualmachine/VirtualMachine;  ->  USE_CUSTOM_VIRTUAL_MACHINE_PERMISSION

No method in the jar calls `checkPermission` with either. That is the
right design rather than an oversight: this jar is loaded into *our*
process, so a check inside it would be a check we could remove. The real
gate is in `virtualizationservice` (running as the `system` user, pid
4103, observed in 2b), on the far side of a binder call.

**Consequence, and it is the load-bearing sentence here:** the 2c answer
cannot be read off a disassembly the way the 2b method signatures were.
It can only be obtained by building a custom config, calling `create()`,
and reading what comes back. That is fine — this spike is built for
exactly that — but it means no amount of further reading answers it, and
time spent looking is time wasted.

**2. A complete Google-shipped guest can be borrowed, exactly as the 2b
payload was.** `/apex/com.android.virt/etc/fs/`:

    -rw-r--r-- system system 32411648  microdroid.img          <- rootfs, "system_a"
    -rw-r--r-- system system 11321344  microdroid_kernel       <- kernel
    -rw-r--r-- system system    65536  microdroid_vbmeta.img   <- "vbmeta_a"

All world-readable, the same `-rw-r--r--` that made `EmptyPayloadApp.apk`
usable by an ordinary app in 2b.

**3. `/apex/com.android.virt/etc/microdroid.json` is the recipe, and it
maps one-to-one onto the custom-image API.** Read verbatim off the
device:

    {
      "kernel": "/apex/com.android.virt/etc/fs/microdroid_kernel",
      "disks": [
        {
          "partitions": [
            { "label": "vbmeta_a", "path": ".../microdroid_vbmeta.img" },
            { "label": "system_a", "path": ".../microdroid.img" }
          ],
          "writable": false
        }
      ],
      "memory_mib": 256,
      "console_input_device": "hvc0",
      "platform_version": "~1.0"
    }

Note what this is NOT: it is not a microdroid *payload* config (the
`task`/`apexes` shape that `setPayloadConfigPath()` takes). It is a
custom *VM* descriptor — kernel plus raw partitions — which is what the
`vm` CLI consumes. Its fields line up with
`VirtualMachineCustomImageConfig.Builder` from the 2b dex extraction:
`setKernelPath`, `addDisk`, `Disk.RODisk`, `Partition(label, path, ...)`.

**What this means for the route, and the recommendation.** The gate can
be tested with **no new dependency, no NDK, and nothing built**: mirror
that JSON into a `VirtualMachineCustomImageConfig`, hand it to
`setCustomImageConfig()`, and try to create a VM as uid 10192. Every file
involved is Google's and already on the device. Either
VirtualizationService allows a sideloaded app to run a custom VM or it
refuses — and that is the whole question rung 2c exists to ask.

**Explicitly rejected route, and why, so it is not re-proposed:**
compiling our own small payload binary into our own APK and booting it
under Google's microdroid. It sounds like "our guest" and it is not. It
uses `setPayloadBinaryName` with our own APK path, which is the *same*
code path 2b already proved — it would never reach
`USE_CUSTOM_VIRTUAL_MACHINE` at all. It would cost the Android NDK as a
new dependency and answer a question already answered.

**And what the borrow test will NOT prove.** It answers "is the custom-VM
path open to a sideloaded app". It does not answer "can we boot an image
we built". If the gate is open, replacing Google's kernel and rootfs with
our own is a separate and much larger piece of work, and the attestation
story is untouched by either.

## 2026-09-14 — rung 2c ANSWERED NO. A sideloaded app cannot reach the custom-VM API at all. The blocker is the hidden-API blocklist, not the permission.

Separate result from 2b and from the 2c pre-flight entry above. The
pre-flight's recommendation was carried out exactly as written — mirror
`microdroid.json` into a `VirtualMachineCustomImageConfig`, hand it to
`setCustomImageConfig()`, create as uid 10192 — and it never got as far
as creating anything.

**Setup.** `com.pennyspike.probe2a`, uid 10192, the same sideloaded,
locally-signed, unprivileged app that answered 2a and 2b. GrapheneOS
2026091001, Android 17 (API 37), build CP2A.260705.006, Pixel 6a
bluejay, bootloader LOCKED. New activity `Probe2cActivity` in the same
package, so the uid is identical to 2b's. Built by `probe2a/build.sh`,
five stages, no Gradle; `stub classes leaked into the dex: 0`.

**The controls, which are what make this readable.** Both were checked
in the same run, before the probe proper:

    android.permission.USE_CUSTOM_VIRTUAL_MACHINE: granted=true
    android.permission.MANAGE_VIRTUAL_MACHINE: granted=true

    STEP3 control OK — setApkPath (SDK member) is callable

So the permission was held at the moment of the refusal, and the build
was not broken. Neither can be blamed for what follows.

**The result. Four members tried, four refused.** Verbatim:

    STEP4 setCustomImageConfig BLOCKED -> java.lang.NoSuchMethodError: No
    virtual method setCustomImageConfig(Landroid/system/virtualmachine/Virtual
    MachineCustomImageConfig;)Landroid/system/virtualmachine/VirtualMachine
    Config$Builder; in class Landroid/system/virtualmachine/VirtualMachine
    Config$Builder; or its super classes (declaration of 'android.system.
    virtualmachine.VirtualMachineConfig$Builder' appears in
    /apex/com.android.virt/javalib/framework-virtualization.jar)

    STEP5 CustomImageConfig.Builder BLOCKED -> java.lang.NoSuchMethodError:
    No direct method <init>()V in class Landroid/system/virtualmachine/
    VirtualMachineCustomImageConfig$Builder;

    STEP6a Partition BLOCKED -> java.lang.NoSuchMethodError: No direct
    method <init>(Ljava/lang/String;Ljava/lang/String;ZLjava/lang/String;)V
    in class Landroid/system/virtualmachine/VirtualMachineCustomImageConfig
    $Partition;

    STEP6b Disk.RODisk BLOCKED -> java.lang.NoSuchMethodError: No static
    method RODisk(Ljava/lang/String;)Landroid/system/virtualmachine/Virtual
    MachineCustomImageConfig$Disk; in class Landroid/system/virtualmachine/
    VirtualMachineCustomImageConfig$Disk;

Every signature was read verbatim off the device's own
`framework-virtualization.jar` with `dexdump`, and each error names that
same jar as the place the class was found. The methods exist. They are
refused.

**The runtime says why, in its own words.** `adb logcat -d | grep
hiddenapi`, one line per refusal:

    E nyspike.probe2a: hiddenapi: Accessing hidden method Landroid/system/
    virtualmachine/VirtualMachineConfig$Builder;->setCustomImageConfig(...)
    (runtime_flags=0, domain=platform, api=blocked) from /data/app/
    ~~MqmyZ345TWn9q-dM9oAsbg==/com.pennyspike.probe2a-.../base.apk
    (domain=app, TargetSdkVersion=37) using linking: denied

`api=blocked`, `domain=platform` refusing `domain=app`, `denied`. This
is ART's non-SDK interface restriction, enforced inside our own process
at link time. It is not a permission check and it is not a typo.

**Nothing was created.** `vm list` immediately after showed only the
Terminal app's `debian` at requesterUid 10179. No crash:
`logcat -s AndroidRuntime:E DEBUG:E` was empty. (`penny2b` from the 2b
run is absent because the app was force-stopped before launching 2c, as
the traps list requires; it dies with its owning process, which is
itself the 2b ownership finding.)

**What this means. There are two gates and they are in series.**

    1. hidden-API blocklist   — in OUR process, at link time      SHUT
    2. USE_CUSTOM_VIRTUAL_MACHINE — in VirtualizationService, over binder   NEVER REACHED

The pre-flight entry above established that gate 2 is not in the jar and
so could only be answered by making the call. That was right, and it is
now moot: gate 1 stops the call from ever being made. `pm grant` speaks
only to gate 2. This is why holding the permission changed nothing.

**What this does NOT establish.** It says nothing about whether
`USE_CUSTOM_VIRTUAL_MACHINE` would have been granted or refused, because
nothing ever asked. That question is unreachable from a sideloaded app
and only rung 4 can put it. It also does not contradict 2a or 2b: the
members those rungs used are marked `SDK` in the same metadata and still
work. It refines 2a, which over-generalised from a true observation.

**A method worth keeping, and the reason this took one build instead of
several.** The dex carries a per-member `hiddenapi` flag, printed by
`dexdump -d`. `SDK` members are callable by this app; `BLOCKED` members
throw `NoSuchMethodError`. Checked against all nine members rung 2b
touched — `create`, `run`, `setCallback`, `getStatus`, `getName`,
`delete`, `getCapabilities`, `setApkPath`, `setPayloadBinaryName` all
`SDK` and all worked; `getCid()` and `getOs()` both `BLOCKED` and both
threw — it was right 9/9 with no counterexample. It then predicted all
four 2c refusals correctly before the APK was built. **Read the flag
before writing the code.** This also closes the open question left in
the 2b entry about why `getCid()` and `getOs()` failed: they are
blocklisted, and that is now measured rather than guessed.

**Device-wide `hidden_api_policy` is `null`** (default enforcement, with
`hidden_api_blacklist_exemptions` also `null`), so nothing about this
device is unusual and nothing from the previous spike relaxed it.

**Not done, deliberately.** `settings put global hidden_api_policy 1`
disables the blocklist device-wide and would separate "blocked for
everyone" from "blocked for us". It was not run: it is a device-wide
state change on a phone that has to go back to Back Market, and it
proves nothing shippable, because no customer device will have it set.
Recorded as an option, not a plan.

**Consequence for the project, stated plainly.** Rung 4 — the app inside
the OS image, platform-signed — is now the *only* route to a custom
guest, not merely the commercial one. Exemption from the blocklist comes
from being in the `platform` domain, which means the system image or an
APEX, which is exactly what rung 4 builds. There is no sideloaded
shortcut left to find. The gap between 2b and 2c, which the pre-flight
called "the entire product question", is now measured: it is not a
permission that could be granted, it is a domain boundary.

## 2026-09-14 — rung 3 ANSWERED YES. The app wakes its own VM 16 seconds after power-on, with the phone still locked and nobody in the room.

Both halves of rung 3 are answered, and one of them only after a
correction. The wake path worked first try; the VM still failed, for a
reason that had nothing to do with waking. Recorded in the order it
happened, because the wrong answer is the useful part.

### Setup

Same app as 2a/2b/2c — `com.pennyspike.probe2a`, uid 10192, hand-built
by `probe2a/build.sh`, sideloaded, not platform-signed, not privileged.
Same guest as 2b: Google's stock microdroid `EmptyPayloadApp`. 2c closed
off running our own guest from a sideloaded app, so there was nothing to
gain by varying the payload. Rung 3 varies exactly one thing: WHO starts
the VM.

Two new components, both `android:directBootAware="true"`:

    BootReceiver   listens for LOCKED_BOOT_COMPLETED and BOOT_COMPLETED
    VmService      foreground service, foregroundServiceType="specialUse",
                   returns START_STICKY, holds the VirtualMachine handle

Four new permissions. **None of them is privileged** — this matters more
than it looks. `MANAGE_VIRTUAL_MACHINE` needs a cable and cannot ship.
These four are ordinary permissions any Play Store app may declare:

    RECEIVE_BOOT_COMPLETED         granted at install
    FOREGROUND_SERVICE             granted at install
    FOREGROUND_SERVICE_SPECIAL_USE granted at install
    POST_NOTIFICATIONS             runtime prompt

So the wake mechanism proven here is one a shipping product can actually
have. That is not true of anything rung 2 proved.

### Attempt 1 — FAILED. Not the wake path. Disk encryption.

Rebooted with `adb reboot` at 15:08. Nobody touched the phone.

    sinceBoot=14025ms  BootReceiver: LOCKED_BOOT_COMPLETED
    sinceBoot=14030ms  service onCreate, uid=10192
                       STEP1 startForeground OK
                       STEP2 MANAGE_VIRTUAL_MACHINE = GRANTED
                       STEP3 userUnlocked=false

Every part of the wake path worked, 14 seconds after power-on, with the
phone sitting at the lock screen. `pm grant` survived the reboot. Then:

    VirtualMachineException: failed to create directory for VM
      at android.system.virtualmachine.VirtualMachine.createVmDir(VirtualMachine.java:796)
      at android.system.virtualmachine.VirtualMachine.create(VirtualMachine.java:613)
    Caused by: java.nio.file.FileSystemException:
      /data/user/0/com.pennyspike.probe2a/vm: Required key not available

"Required key not available" is file-based encryption. Android gives
every app two data directories:

    /data/user/0/<pkg>      credential-encrypted (CE). The decryption key
                            is derived from the user's PIN. Does not exist,
                            at all, until somebody unlocks the phone once
                            after a boot.
    /data/user_de/0/<pkg>   device-encrypted (DE). Available as soon as the
                            OS is up. Only directBootAware components may
                            touch it.

`VirtualMachine.createVmDir` builds the VM's state directory relative to
the Context it was handed, and the default Context is the CE one. So the
VM could not exist yet.

Then, 5 minutes 57 seconds later, a human typed the PIN:

    sinceBoot=357535ms  BootReceiver: BOOT_COMPLETED
                        STEP3 userUnlocked=true
    sinceBoot=357825ms  run() returned, status=RUNNING
    sinceBoot=359230ms  CB onPayloadReady

That is the second finding of attempt 1, and it is about the product, not
the code: **`BOOT_COMPLETED` does not fire at boot on a phone with a PIN.
It fires at first unlock.** An appliance that waits for `BOOT_COMPLETED`
waits for a person. `LOCKED_BOOT_COMPLETED` is the one that actually
tracks power-on, and only directBootAware components receive it.

### Attempt 2 — SUCCEEDED. One-line change.

    Context ctx = createDeviceProtectedStorageContext();
    VirtualMachineManager vmm = ctx.getSystemService(VirtualMachineManager.class);
    ... new VirtualMachineConfig.Builder(ctx)

Control run first, while unlocked, to prove the change had not simply
broken the working path: VM dir moved to
`/data/user_de/0/com.pennyspike.probe2a/vm/penny3`, created, VM booted,
`requesterUid: 10192`. Good — so any later failure would be about the
lock, not about the edit.

Rebooted at 15:17:21 (kernel `trusty` line is the boot marker). The phone
was deliberately left alone at the lock screen.

    sinceBoot=14353ms  BootReceiver: LOCKED_BOOT_COMPLETED
    sinceBoot=14359ms  service onCreate, uid=10192
                       STEP1 startForeground OK
                       STEP2 MANAGE_VIRTUAL_MACHINE = GRANTED
                       STEP3 userUnlocked=false        <-- still locked
                       STEP3b dataDir=/data/user_de/0/com.pennyspike.probe2a
                       STEP7 deleted a pre-existing VM named penny3
                       STEP8 create() returned VirtualMachine(name:penny3...)
    sinceBoot=14569ms  STEP9 run() returned, status=RUNNING
    sinceBoot=15908ms  CB onPayloadStarted
    sinceBoot=15936ms  CB onPayloadReady

**A VM belonging to a sideloaded app was booted and ready 15.9 seconds
after power-on, with the disk still encrypted, the lock screen up, and
nobody touching the device.**

`BOOT_COMPLETED` did not arrive until `sinceBoot=5723874ms` — 95 minutes
24 seconds later, when a human finally unlocked the phone. By then the VM
had been running unattended for 95 minutes. The service correctly did
nothing on that second start ("VM already booted by an earlier start").

### Corroboration that does not depend on our own log

Our log is the app talking about itself. Two independent checks:

    /proc/3210/stat field 22 = 1381 jiffies, CLK_TCK=100
      -> the process holding the VM started 13.81s after boot

    vm list at 16:53 (uptime 1:35):
      name: "penny3", cid: 2048, requesterUid: 10192, requesterPid: 3210

And the contrast that makes the point:

    /proc/4431/stat field 22 = 573038 jiffies = 5730s
      -> the Terminal app's debian VM started 95.5 minutes after boot,
         i.e. only once a human opened the app

Same device, same boot. Ours came up in 16 seconds unattended; Google's
Terminal app needed hands. The CLAUDE.md trap "the VM does not start
itself after a device reboot" is a fact about the Terminal app, not about
VMs.

### Restart after a kill — also YES

    adb shell am crash com.pennyspike.probe2a

    pid before: 3210
    sinceBoot=5807404ms  service onCreate (new pid 4953)
    sinceBoot=5807405ms  onStartCommand why=RESTARTED-BY-SYSTEM (intent is null)
                         RESTART CONFIRMED
    sinceBoot=5807604ms  run() returned, status=RUNNING
    sinceBoot=5808846ms  CB onPayloadReady
    pid after: 4953
    vm list: penny3, cid 2050, requesterUid 10192, requesterPid 4953

The process died, the VM died with it, and the system recreated the
service on its own with a null intent — no app, no user, no broadcast.
VM back up **1.44 seconds** after the service restarted. That is
START_STICKY doing exactly what a headless appliance needs.

Caveat worth keeping: `am crash` is a simulated crash, not memory
pressure. It does not prove the low-memory killer behaves the same way.

### Android's own account of the decision

    ActivityManager: Background started FGS: Allowed
      [callingPackage: com.pennyspike.probe2a; callingUid: 10192;
       uidState: RCVR; BFGS denied: false;
       intent: ... cmp=com.pennyspike.probe2a/.VmService;
       code:LOCKED_BOOT_COMPLETED;
       tempAllowListReason:<... reasonCode:LOCKED_BOOT_COMPLETED,
       duration:20000, callingUid:1000>;
       targetSdkVersion:37; startForegroundCount:0]

Two things in there are worth not losing.

**The allowance is 20 seconds.** `duration:20000` is the temporary
exemption granted to the boot broadcast. The service has 20 seconds from
the broadcast to call `startForeground` or it is killed. We used about
5 milliseconds, so there is enormous headroom — but a heavier app, or a
cold dex2oat on first boot after an update, could eat into it.

**And a restriction that will bite Penny later:**

    ActivityManager: Foreground service started from background can not have
      location/camera/microphone access: service com.pennyspike.probe2a/.VmService

A foreground service started at boot is permanently denied microphone,
camera and location for the life of that service. Irrelevant to a VM.
Very relevant to a voice assistant. Penny cannot both wake itself at boot
and listen, in the same service, on this mechanism. Not a blocker found
today; a constraint found today.

### Incidental corroboration of 2c from a different direction

At boot, `dex2oat64` ahead-of-time compiled the app and logged the same
hidden-API refusals 2c measured at runtime:

    dex2oat64: hiddenapi: Accessing hidden method
      Landroid/system/virtualmachine/VirtualMachineCustomImageConfig$Builder;-><init>()V
      (runtime_flags=0, domain=platform, api=blocked) from ...base.apk
      (domain=app, TargetSdkVersion=37) using linking: denied

Same for `setKernelPath`, `addDisk`, `build`, `Disk.RODisk`,
`Partition.<init>`, `Disk.addPartition`, and `VirtualMachine.getCid()`.
2c's result was measured by the runtime linker; this is the compiler
reaching the same verdict independently. The blocklist is not a runtime
accident.

Also observed during attempt 1, not during the successful boots:

    virtmgr: avc: denied { use } for path="/dev/null"
      scontext=u:r:untrusted_app_all_virtualizationmanager:s0:c192,...
      tcontext=u:r:zygote:s0 tclass=fd permissive=0 app=com.pennyspike.probe2a

Recorded, not explained. It appeared only alongside the failed CE attempt
and the VM worked fine afterwards, so it is noted rather than chased.

### What rung 3 does NOT establish

- **It does not make anything shippable.** The VM still needs
  `MANAGE_VIRTUAL_MACHINE`, still granted over a cable by a person.
  Unchanged from 2b. What changed is that the WAKE half now uses only
  ordinary permissions, so the wake design survives into rung 4 intact.
- The guest is Google's empty microdroid payload. It boots, signals
  ready, and does nothing. Nothing here says a real workload survives a
  boot, a kill, or memory pressure.
- **Device-encrypted storage is weaker than credential-encrypted
  storage, and this is a real trade-off, not a free win.** Anything in
  `/data/user_de/0/<pkg>` is readable once the device is powered on,
  without the user's PIN. We moved the VM's state there to get it to
  start unattended. For a product whose pitch is confidentiality, what
  ends up in that directory needs deciding deliberately. Raise it before
  rung 4 designs storage.
- No attestation claim. DEBUG_LEVEL_FULL, sample DICE values, same as
  every run so far.
- One reboot, one crash. Not a soak. Not tested across an OS update,
  a low-memory kill, or a battery-dead power cycle.

### Consequence

The load-bearing rung holds. Penny can be a headless appliance: power on,
16 seconds, VM up, no screen, no PIN, no human. It survives having its
process killed and comes back in under two seconds. That was the thing
most likely to quietly kill the whole idea, and it did not.

Rung 2 said the VM machinery answers to an app. Rung 3 says the app does
not need a person. What is left is rung 4 — being part of the OS — which
2c already established is the only route to running our own guest.

## 2026-09-14 — rung 3 REPEATED. Both halves reproduce. Plus a correction to the previous entry's "95 minutes".

The previous entry recorded rung 3 as answered on a single boot. n=1 is
not a result, it is an anecdote. Repeated both halves on the same APK
with no rebuild and no code change. Both reproduce.

### Correction first

The previous entry says the VM "ran unattended for 95 minutes". That is
literally true and was NOT a designed test. The 95 minutes happened
because the session lost connection and the phone sat at the lock screen
until somebody came back to it. It is an observation, not an endurance
measurement, and it must not be cited as one. Nothing else in that entry
depends on it: the VM was up at 15.9 seconds, and how long it took a
human to type the PIN afterwards is independent of that number.

Flagged by Matt, not caught by Claude. Worth remembering — an accidental
number that flatters the result is exactly the kind that gets repeated to
investors and then falls over.

### Repeat of the boot test — reproduces

Second reboot at 17:00, phone deliberately left at the lock screen for
about two minutes before being unlocked.

                              run 1 (15:17)   run 2 (17:00)
    LOCKED_BOOT_COMPLETED       14353ms         13748ms
    userUnlocked at that moment   false           false
    run() returned RUNNING      14569ms         13956ms
    CB onPayloadReady           15936ms         15100ms
    /proc/<pid>/stat field 22   1381 jiffies    1329 jiffies
      (CLK_TCK=100)             = 13.81s        = 13.29s
    BOOT_COMPLETED            5723874ms        182030ms
      i.e. at first unlock      95m 24s         3m 02s
      and it was               accidental      deliberate

So: **VM booted and ready 15.1 and 15.9 seconds after power-on, twice,
both times with `userUnlocked=false`.** `vm list` confirmed
`requesterUid: 10192` on both.

The `BOOT_COMPLETED` row is the second finding, now measured twice with
two very different waits. 95 minutes and 3 minutes, both exactly tracking
when a human typed the PIN, neither tracking power-on. That broadcast
does not mean "booted". It means "somebody unlocked me".

Run 2 is the cleaner of the two for one more reason: `vm list` afterwards
showed ONLY `penny3`. The Terminal app's `debian` VM was absent, because
nobody opened the Terminal app on that boot. Run 1's contrast relied on
the Terminal app having been opened later; run 2 shows the plain fact —
on a boot where no human opens anything, the only VM on the device is the
one our sideloaded app started by itself.

### Repeat of the kill test — reproduces

    adb shell am crash com.pennyspike.probe2a

                              run 1           run 2
    pid before                3210            2795
    pid after                 4953            3759
    service onCreate          5807404ms       206819ms
    CB onPayloadReady         5808846ms       208331ms
    VM back up in             1.442s          1.512s
    onStartCommand intent     null            null
    requesterUid after        10192           10192

Both times the system recreated the service on its own with a null
intent — no app, no user, no broadcast — and the VM was back inside two
seconds.

### What is still NOT established, unchanged from the previous entry

Everything in the previous entry's "does NOT establish" section still
stands, and the correction above tightens one of them:

- **There is still no endurance test.** Two boots and two simulated
  crashes is reproducibility, not a soak. `am crash` is not memory
  pressure; the low-memory killer has never been exercised. The 95
  minutes was an accident and proves only that nothing fell over
  unattended in that window on that one occasion.
- Still needs `pm grant MANAGE_VIRTUAL_MACHINE` over a cable. Not
  shippable. Only the WAKE half uses ordinary permissions.
- The guest is still Google's empty microdroid payload doing nothing.
- Device-encrypted storage is still the confidentiality trade-off that
  bought this result.

## 2026-09-14 — rung 3b DEFINED, not started. Can the thing that wakes at boot also listen?

Matt asked for the microphone question to be written into the spike as a
rung before any of it is built. This entry is the definition and the desk
research behind it. **No code was written and no probe was run.** The only
things touched on the device were four read-only queries, listed below.

### Why it exists

Rung 3's own logs contained the constraint:

    Foreground service started from background can not have
    location/camera/microphone access

Penny is a voice assistant meant to be listening from power-on. Rung 3
proved the app can wake its VM unattended in 15 seconds; it also proved,
incidentally, that the service doing the waking is denied the microphone
for its whole life. Those two facts sit badly together and the spike
should not be closed without testing whether there is a way through.

### Desk research, 14 Sept, before writing any code

Sources read:

  - https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
  - https://developer.android.com/about/versions/17/changes/bg-audio
  - https://source.android.com/docs/automotive/voice/voice_interaction_guide/app_development
  - https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/service/voice/AlwaysOnHotwordDetector.java

Findings:

1. There is a documented exemption list for while-in-use permissions
   (microphone, camera, location) when a foreground service is started
   from the background. Seven entries. Six are unreachable for us
   (system component, widget interaction, notification interaction,
   PendingIntent from a visible app, device-owner policy controller,
   START_ACTIVITIES_FROM_BACKGROUND privileged permission). The seventh
   is: **"Service started by an app providing VoiceInteractionService."**

2. Android 17 tightened background audio further, and the same exemption
   survives that tightening. Verbatim: "Foreground services are granted
   WIU access if they are started by system-server delegation ... or by
   system bindings representing an elevated foreground state to perform
   dedicated functionality (such as for a VoiceInteractionService)."
   This matters because the device is Android 17 (API 37) — the general
   docs describe an older regime and the Android 17 page is the binding
   one here.

3. Android 17 also says the failure is SILENT: "the audio playback and
   volume change APIs fail silently without throwing an exception or
   providing a failure message." It names our exact case as suppressed:
   an app that "starts a foreground service in response to BOOT_COMPLETE
   and attempts to interact with audio ... will be suppressed."
   **Consequence for the probe design: a NO will look like success unless
   the probe inspects the audio buffer.** Checking for a thrown exception
   would have produced a false YES. Recording this before building it, so
   the trap cannot be walked into later and mistaken for a result.

4. The always-on hotword route is closed and should not be attempted.
   `AlwaysOnHotwordDetector` became `@SystemApi` in Android 12, and
   `CAPTURE_AUDIO_HOTWORD` is `signature|privileged` — AOSP's own VIA
   guide says outright that if a voice app needs privileged permissions,
   "OEMs must preload their APK in their system images". That is the same
   wall rung 2c measured from the other side. The probe therefore uses
   ordinary `RECORD_AUDIO` with `AudioRecord`, which is a runtime
   permission any app may request.

5. A VoiceInteractionService is not itself privileged. The manifest shape
   from AOSP's guide:

       service android:permission="android.permission.BIND_VOICE_INTERACTION"
       intent-filter action "android.service.voice.VoiceInteractionService"
       meta-data android:name="android.voice_interaction"
                 android:resource="@xml/interaction_service"

   with `interaction_service.xml` naming a `sessionService` and a
   `recognitionService`. `BIND_VOICE_INTERACTION` is a signature
   permission, but it is the permission the SYSTEM must hold to bind to
   our service — it is not something we have to be granted. Third-party
   assistants ship this way today. AOSP does not state a preinstall
   requirement for the non-privileged path.

### Device reads, 14 Sept, read-only

    adb shell settings get secure assistant                  -> (empty)
    adb shell settings get secure voice_interaction_service   -> (empty)
    adb shell dumpsys role | grep -A3 assistant
        -> name=android.app.role.ASSISTANT
           fallback_enabled=true          (no holder listed)
    adb shell pm list features | grep -e voice -e microphone
        -> feature:android.hardware.microphone
           feature:android.software.voice_recognizers

The assistant role exists on this GrapheneOS build and **nothing holds
it.** There is no Google Assistant on the device to displace. That is a
convenience, not a result — it means the probe does not have to fight an
incumbent, nothing more.

### What rung 3b will build

Inside the existing `probe2a` APK. No new project, no new repo.

  1. Near-empty `VoiceInteractionService`, `VoiceInteractionSessionService`
     and `RecognitionService`, plus `res/xml/interaction_service.xml`.
     Note: **this is the first XML resource in the spike.** Every build so
     far has been deliberately resource-free — that is why the rung 3
     notification uses `android.R.drawable.stat_notify_sync`. `build.sh`
     will need a real `res/` passed to `aapt2 link`. Expect that to be the
     fiddly part, not the voice code.
  2. `RECORD_AUDIO` declared, granted once by hand.
  3. `VmService` attempts a one-second `AudioRecord` capture at boot and
     logs the peak amplitude, not merely whether the call returned.

### What DONE means

Reboot, touch nothing, and the log shows non-zero audio captured while
`userUnlocked=false`, corroborated by something other than our own log —
the OS privacy indicator or `dumpsys media.audio_flinger`. Rung 3 set the
standard that a claim is checked from outside the app that makes it
(`vm list`, `/proc/<pid>/stat`), and 3b keeps it.

### What it will not prove, whatever the answer

  - Filling the assistant slot needs either `settings put secure ...` over
    a cable, or a person tapping through Settings. The tap is shippable,
    the cable is not. Which one this device accepts is untested.
  - The guest VM does nothing with audio. This measures Android handing
    the app a microphone, not voice reaching Penny.
  - `RECORD_AUDIO` is a runtime permission. Whether it can be HELD before
    first unlock is part of the question, not an assumption.

### Cost and stakes

Hours, one device, no OS build, nothing irreversible and nothing that
affects returning the 6a. A NO is a hard constraint that rung 4 inherits
and that Penny's product shape has to absorb. A YES means the wake path
and the listening path can be the same app. Either answer is worth having
before weeks go into rung 4.

Not started. Matt's go-ahead required.

## 2026-09-14 — rung 3b research: can Penny take the assistant slot? Read from AOSP source, not from docs.

Matt asked whether we can take over the assistant slot. Answer from the
source: **yes, with three conditions, and one of them is a device value we
have not read yet.** Still no code written and no probe run.

### Method note, because it nearly went wrong

The first two passes over `VoiceInteractionManagerService.java` gave
OPPOSITE answers to the one question that matters — whether the system
binds the assistant before first unlock. One said binding waits for
`onUserUnlocking`; the other said it happens at boot. Neither was quoted
from source I had read. So the file was pulled raw
(`?format=TEXT` off googlesource, base64-decoded, 2819 lines) and read
directly. **Everything below is quoted from that file, and the earlier
"it waits for unlock" reading is wrong.** Recording the near-miss because
it would have killed rung 3b on a false negative.

### The binding chain, before any unlock. Quoted.

`onBootPhase`, line 225:

    } else if (phase == PHASE_THIRD_PARTY_APPS_CAN_START) {
        mServiceStub.systemRunning(isSafeMode());

`systemRunning`, line 728, runs at that boot phase and does not wait for
a user:

    synchronized (this) {
        setCurrentUserLocked(ActivityManager.getCurrentUser());
        switchImplementationIfNeededLocked(false);
    }

`switchImplementationIfNeededNoTracingLocked`, line 788. **The unlock
check at line 808 wraps ONLY the shortcut-host and app-switch setup** —
lines 809 to 818. The bind block that follows is OUTSIDE it:

    if (mUserManagerInternal.isUserUnlockingOrUnlocked(mCurUser)) {
        ... setShortcutHostPackage / setAllowAppSwitches only ...
    }

    if (force || mImpl == null || mImpl.mUser != mCurUser
            || !mImpl.mComponent.equals(serviceComponent)) {
        ...
        if (hasComponent) {
            setImplLocked(new VoiceInteractionManagerServiceImpl(...));
            mImpl.startLocked();

`startLocked()` in `VoiceInteractionManagerServiceImpl.java`, line 1057,
is the actual bind, and has no unlock gate of its own:

    mBound = mContext.bindServiceAsUser(intent, mConnection,
            Context.BIND_AUTO_CREATE | Context.BIND_FOREGROUND_SERVICE
            | Context.BIND_INCLUDE_CAPABILITIES
            | Context.BIND_ALLOW_BACKGROUND_ACTIVITY_STARTS,
            new UserHandle(mUser));

Note `BIND_FOREGROUND_SERVICE` and `BIND_ALLOW_BACKGROUND_ACTIVITY_STARTS`
— this is exactly the "system binding representing an elevated foreground
state" that the Android 17 background-audio page names as the exemption.

The chain also needs the secure setting to be readable while the disk is
still locked. It is: `packages/SettingsProvider/AndroidManifest.xml` has
`android:directBootAware="true"` on its `<application>`, so
`Settings.Secure.VOICE_INTERACTION_SERVICE` survives the pre-unlock read
at line 790.

### The trap nobody documents: flags 0

Line 797:

    serviceInfo = AppGlobals.getPackageManager()
            .getServiceInfo(serviceComponent, 0, mCurUser);

**Flags are literally `0`** — no `MATCH_DIRECT_BOOT_AWARE`. Before first
unlock PackageManager only matches direct-boot-aware components, so a
VoiceInteractionService that is not `directBootAware` does not resolve,
`hasComponent` is false, and nothing binds. It would then bind later at
`onUserUnlocking` and look like it worked — while having missed the only
window we care about.

**So our VoiceInteractionService must carry `android:directBootAware="true"`,
for the same reason rung 3's BootReceiver and VmService do.** This is the
single most useful thing this research produced and it was not in any
document — only in the flags argument.

### Can we hold the role? Qualification rules, quoted

From `AssistantRoleBehavior.java` (PermissionController,
`role-controller/.../behavior/`), `isAssistantVoiceInteractionService`:

    if (!android.Manifest.permission.BIND_VOICE_INTERACTION.equals(si.permission)) {
        return false;
    }
    ...
    if (sessionService == null || recognitionService == null || !supportsAssist) {
        return false;
    }

So the qualifying bar is: the service is protected by
`BIND_VOICE_INTERACTION`, and its `android.voice_interaction` metadata XML
declares `sessionService`, `recognitionService` AND
`android:supportsAssist="true"`. **`supportsAssist` is easy to omit and
silently disqualifies the app.** There is NO system-app or preinstall
check in the qualification path — `isSystemPackageAsUser` appears only
where extra permissions are granted, not where qualification is decided.

Two conditions we cannot control from the app:

  - `getQualifyingPackagesInternal` skips the whole VoiceInteractionService
    branch `if (!userActivityManager.isLowRamDevice())`. On a low-RAM
    device only an `ACTION_ASSIST` activity qualifies — which would NOT
    give us the microphone exemption. Must read `ro.config.low_ram` on the
    6a. Not yet read.
  - `isVisibleAsUser` returns
    `VisibilityMixin.isVisible("config_showDefaultAssistant", false, ...)`.
    **The default is `false`.** If GrapheneOS leaves that resource off,
    the Settings picker is not shown and the only route is a cable. Must
    read on device. Not yet read.

### How the slot actually gets filled

From `roles.xml`, the ASSISTANT role element carries:

    exclusive="true"  exclusivity="user"  fallBackToDefaultHolder="true"
    showNone="true"   requestable="false" overrideUserWhenGranting="true"
    defaultHolders="config_defaultAssistant"

**`requestable="false"`.** Per AOSP's `Role.md`: "If a role isn't
requestable but is still visible, apps cannot show the request role dialog
to user, but user can still manage the role in Settings page." So Penny
can never pop its own "make me your assistant" dialog. The user goes to
Settings and picks it. That is a worse onboarding story than a one-tap
prompt, and it should be said out loud rather than discovered in a demo.

Also, from `findAvailInteractor` line 857, on automatic selection:

    // Find first system package.  We never want to allow third party services to
    // be automatically selected, because those require approval of the user.

A third-party assistant is therefore never auto-selected — it is only ever
held because a human chose it. Worth knowing for the "device wiped and
restored" story, and there is a known Android behaviour where the
assistant secure settings are cleared on APK reinstall, which would
silently unset Penny on every update. Untested here, flagged.

### Cable route for the probe itself

`adb shell cmd role` on this device exposes:

    add-role-holder [--user USER_ID] ROLE PACKAGE [FLAGS]
    remove-role-holder / clear-role-holders / get-role-holders
    set-bypassing-role-qualification true|false

so rung 3b can set the holder without touching the UI, and
`set-bypassing-role-qualification` exists if the qualification check needs
to be taken out of the picture to isolate a failure. Using it would prove
less, so use it only to diagnose.

### Where this leaves rung 3b

Better than when it was written. The exemption is real in source, the
binding genuinely happens before unlock, and the bind flags are the ones
the Android 17 doc names. The rung is now worth running rather than a coin
toss — provided the two device values come back the right way.

Still not started. Still Matt's call.

### Device state at time of writing

adb dropped mid-research: `system_profiler SPUSBDataType` showed nothing
on the bus and `adb devices` was empty. Same GrapheneOS charging-only-
when-locked behaviour recorded in the traps list — the phone had locked
itself. Not a fault.

## 2026-09-15 — rung 3b ANSWERED YES. A sideloaded app holds the microphone from boot, with the phone locked and nobody in the room.

Measured twice over two reboots, on the same APK, with the second reboot
differing from the first by one XML attribute. The OS corroborates it in
its own audio log with the word "not silenced".

Versions, unchanged from rung 3 except the app:

    Pixel 6a (bluejay), refurbished, bootloader LOCKED, verifiedbootstate=yellow
    GrapheneOS     2026091001
    Android        17, CP2A.260705.006, patch 2026-09-01
    fingerprint    google/bluejay/bluejay:17/CP2A.260705.006/2026091001:user/release-keys
    App            com.pennyspike.probe2a, uid 10192, hand-built, sideloaded,
                   not platform-signed, not privileged
    Toolchain      Temurin 21.0.12.1, build-tools 37.0.0, platform android-37.0
    Build          probe2a/build.sh, now SIX stages (resources added)

### The two device conditions that were unread. Both are favourable.

CLAUDE.md flagged these as not ours to control and gating the whole rung.
Read before any code was written:

    ro.config.low_ram            EMPTY (also ro.lowram empty, and nothing in
                                 dumpsys). MemTotal 5,718,280 kB. So this is
                                 NOT a low-RAM device, the VoiceInteractionService
                                 branch is live, and the ACTION_ASSIST-activity
                                 fallback — which carries no microphone
                                 exemption — does not apply.
    config_showDefaultAssistant  TRUE. Read as bool 0x01110001 out of the
                                 device's own /system/framework/framework-res.apk
                                 with `aapt2 dump resources`, not from a doc.
                                 The framework default is FALSE, so this device
                                 deliberately shows the assistant picker. That
                                 matters commercially: the user-taps-Settings
                                 route exists here.

Assistant slot before any change: role ASSISTANT had NO HOLDER, `settings
secure assistant` empty, `voice_interaction_service` empty. Nothing to
displace.

### What was built

Inside the existing probe2a APK. No new project, no new repo, no Gradle.

    PennyVoiceService          VoiceInteractionService, directBootAware,
                               android:permission=BIND_VOICE_INTERACTION,
                               intent filter + meta-data android.voice_interaction
    PennySessionService        VoiceInteractionSessionService (stub)
    PennySession               VoiceInteractionSession (stub)
    PennyRecognitionService    RecognitionService (stub)
    MicProbe                   the measurement, shared
    MicFgsService              ordinary FGS, foregroundServiceType="microphone"
    MicControlActivity         the control run
    res/xml/voice_interaction.xml
    res/xml/recognition_service.xml

`build.sh` gained `aapt2 compile --dir res` ahead of `aapt2 link -R`, so it
is now 6 stages rather than 5. It still checks that no stub class reached
the dex; still 0.

**The mic attempt was deliberately kept OUT of VmService.** CLAUDE.md's plan
put it there. That was changed on purpose: giving the proven rung 3 wake
service a microphone FGS type risks the system refusing to start it at
boot, which would have taken rung 3's result down with it. The guess was
right — see the first reboot below, where exactly that refusal happened to
the separate service. VmService was never touched and rung 3 reproduced a
third time.

Two attempt sites, because they can legitimately disagree and the
difference is the finding:

    A-assistant   inside PennyVoiceService.onReady(), i.e. in a process the
                  OS itself started to bind the assistant. No FGS of ours.
    B-fgs         an ordinary foreground service started from the boot
                  broadcast, holding foregroundServiceType="microphone".

### The probe does not trust the absence of an exception

Android 17 suppresses background audio "silently without throwing". A
try/catch would report a confident YES when the answer is NO. So MicProbe
reads 16,000 samples (1s, 16kHz, mono, 16-bit) and decides on peak, RMS and
the proportion of non-zero samples. Digital silence is exactly zero on every
sample; a real mic in a quiet room has a noise floor.

CONTROL RUN first, foreground and unlocked, where access is unambiguous:

    MIC [CONTROL-foreground] samples=16000 peak=362 rms=127.22 nonZero=14789 (92.4%)

So the probe works and the microphone is alive. Without this, silence later
could not be told from a broken probe.

### Reboot 1 — the assistant was EVICTED at boot. Not a hypothesis failure.

Rebooted, touched nothing for ~80s, unlocked.

- `PennyVoiceService` never logged at all. The OS never bound it.
- `cmd role get-role-holders android.app.role.ASSISTANT` still returned
  `com.pennyspike.probe2a`, and `settings secure assistant` still held the
  component — but `settings secure voice_interaction_service` came back
  **EMPTY**, and `dumpsys voiceinteraction` said `(No active implementation)`.
- System log, 8.7s after boot:
  `W VoiceInteractionManager: no auto selectable voice recognition services found for user 0`
- Site B was refused outright:

      SecurityException: Starting FGS with type microphone callerApp=...u0a192
      targetSDK=37 requires permissions: all of [FOREGROUND_SERVICE_MICROPHONE]
      and any of [... RECORD_AUDIO] and the app must be in the eligible
      state/exemptions to access the foreground only permission

  Both named permissions were held. The refusal is the last clause.

**If we had stopped here we would have written rung 3b up as NO, and it
would have been wrong.** The cause was a device-state precondition, not the
hypothesis.

### Why it was evicted. Read out of AOSP source, quoted.

`VoiceInteractionManagerService.initForUserNoTracing`, which runs at every
boot:

    String curInteractorStr = Settings.Secure.getStringForUser(
            mContext.getContentResolver(),
            Settings.Secure.VOICE_INTERACTION_SERVICE, userHandle);
    ComponentName curRecognizer = getCurRecognizer(userHandle);

    ...
    if (curRecognizer != null) {
        // If we already have at least a recognizer, then we probably want to
        // leave things as they are...  unless something has disappeared.
        ...
        if (recognizerInfo != null && (curInteractor == null || interactorInfo != null)) {
            if (DEBUG) Slog.d(TAG, "Current interactor/recognizer okay, done!");
            return;
        }
    }

    if (curInteractorInfo == null && mEnableService && !"".equals(curInteractorStr)) {
        curInteractorInfo = findAvailInteractor(userHandle, null);
    }

    if (curInteractorInfo != null) {
        setCurInteractor(...);
    } else {
        // No voice interactor, so clear the setting.
        setCurInteractor(null, userHandle);
    }

On this device `settings secure voice_recognition_service` was **null**. So
`curRecognizer == null`, the early `return` is never reached, execution
falls through to `findAvailInteractor(userHandle, null)` — which will never
auto-select a third-party app ("We never want to allow third party services
to be automatically selected") — and lands on `setCurInteractor(null)`.
Penny is wiped at every boot, silently.

The only way to reach that early return is for the recognizer setting to
name a service that resolves AND reports `isSelectableAsDefault()`. Nearby
in the same file:

    if (!rsi.isSelectableAsDefault()) {
        Slog.d(TAG, "Found non selectableAsDefault recognizer as"
                + " default. Unsetting the default and looking for another one.");
        recognizerInfo = null;
    }

That is the platform attribute `android:selectableAsDefault`, confirmed to
exist on this device as `attr/selectableAsDefault` = 0x01010640 in
framework-res.apk. It was added to `res/xml/recognition_service.xml`, and
`voice_recognition_service` was pointed at our own stub recogniser.

**THE FIX IS ONE ATTRIBUTE AND ONE SETTING**, and neither is in any
document:

    res/xml/recognition_service.xml   android:selectableAsDefault="true"
    settings put secure voice_recognition_service \
        com.pennyspike.probe2a/.PennyRecognitionService

### Reboot 2 — YES. Both sites. Phone locked, nobody in the room.

Rebooted, touched nothing, made noise near the phone, unlocked at 68.8s.

    09:29:02.455 ActivityManager: Start proc 2031:com.pennyspike.probe2a/u0a192
                 for bound-service {com.pennyspike.probe2a/...PennyVoiceService}
    sinceBoot= 9480ms  PennyVoiceService onCreate — the OS bound the assistant
    sinceBoot= 9491ms  MIC [A-assistant] attempt RECORD_AUDIO=GRANTED userUnlocked=false
    sinceBoot=10715ms  MIC [A-assistant] samples=16000 peak=557 rms=198.32
                       nonZero=14662 (91.6%)  -> VERDICT YES, REAL AUDIO

    sinceBoot=12333ms  MicFgsService startForeground(MICROPHONE) ACCEPTED
    sinceBoot=12342ms  MIC [B-fgs] attempt RECORD_AUDIO=GRANTED userUnlocked=false
    sinceBoot=13507ms  MIC [B-fgs] samples=16000 peak=670 rms=253.27
                       nonZero=14497 (90.6%)  -> VERDICT YES, REAL AUDIO

    sinceBoot=68772ms  BOOT_COMPLETED — i.e. the PIN was typed HERE, 55 seconds
                       after the audio was already captured

`userUnlocked=false` on both. The disk was still encrypted.

### Corroborated by the OS, not by our own log

`adb shell dumpsys audio`, the audio server's own Recording Activity table:

    09-15 09:29:03:631 rec start riid:47 uid:10192 session:33 src:MIC not silenced pack:com.pennyspike.probe2a
    09-15 09:29:04:756 rec stop  riid:47 uid:10192 session:33 src:MIC not silenced pack:com.pennyspike.probe2a
    09-15 09:29:06:452 rec start riid:63 uid:10192 session:41 src:MIC not silenced pack:com.pennyspike.probe2a

**"not silenced"** is the platform's own word for it, and it is the exact
inverse of the documented failure mode. Both records predate the unlock.

Also from the OS:

    ActivityManager: Background started FGS: Allowed [callingPackage:
    com.pennyspike.probe2a; ... uidState: FGS; ... allowWiu:52; ...
    reasonCode:LOCKED_BOOT_COMPLETED,duration:20000 ...]

`allowWiu` is while-in-use access being granted. And
`appops get com.pennyspike.probe2a RECORD_AUDIO` returns
`Uid mode: RECORD_AUDIO: foreground` with a recorded
`duration=+1s116ms`, matching the one-second read.

### The controlled comparison, which is the real result

Same APK, same service, same code, same boot broadcast. The ONLY difference
between reboot 1 and reboot 2 is whether the app was actually bound as the
assistant:

    reboot 1, not the assistant   MicFgsService startForeground(MICROPHONE)
                                  REFUSED, SecurityException
    reboot 2, is the assistant    MicFgsService startForeground(MICROPHONE)
                                  ACCEPTED, and recorded real audio

**So the exemption is real, it is conferred by holding the assistant role,
and it extends beyond the assistant process to an ordinary foreground
service started from a boot broadcast.** That directly overturns the
constraint rung 3 inferred — the wake service and the listening service do
NOT have to be two different things.

### Rung 3 did not regress. Third reproduction.

    sinceBoot=12326ms  VmService onCreate uid=10192
    sinceBoot=12326ms  STEP3 userUnlocked=false
    sinceBoot=13967ms  CB onPayloadReady — guest booted

    vm list -> name "penny3", cid 2048, requesterUid 10192, requesterPid 2031

### Two things this does NOT prove

1. **The assistant slot was taken over adb**, with
   `cmd role add-role-holder android.app.role.ASSISTANT` plus
   `settings put secure voice_interaction_service` and
   `voice_recognition_service`. Whether a user tapping through Settings sets
   all three — in particular the recogniser, which is what makes it survive
   a reboot — is UNTESTED. `config_showDefaultAssistant` is true so the
   picker exists, and ASSISTANT is `requestable="false"` so Penny can never
   prompt for it. This is the one remaining question on the shippable route
   and it is a phone-tapping test, not a code test.
2. **The guest VM still does nothing with audio.** This measures Android
   handing the app a microphone. Voice has still never crossed the VM
   boundary.

### Confirmed in passing

    PccSandboxManagerInternal: Package com.pennyspike.probe2a is not qualified
    for hotword detection and can't start a PCC Process

The DSP hotword route is closed to us, exactly as the desk research said.
Nothing was spent on it.

And rung 2c was re-confirmed for free, from dex2oat at install time:

    hiddenapi: Accessing hidden method Landroid/system/virtualmachine/
    VirtualMachineCustomImageConfig$Builder;-><init>()V (runtime_flags=0,
    domain=platform, api=blocked) from ...base.apk (domain=app,
    TargetSdkVersion=37) using linking: denied
