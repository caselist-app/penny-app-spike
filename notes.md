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

## 2026-09-15 — rung 3b addendum: the microphone result REPRODUCES, and the user-tap route FAILS. The recogniser setting is the deciding variable.

Two further reboots, same APK as the YES above. Together with the earlier
pair this is four reboots on the same build, and the variable is isolated.

### Reboot 3 — assistant set by Matt in the Settings UI, by hand. EVICTED.

All state cleared first: role holder removed, and
`voice_interaction_service`, `voice_recognition_service`, `assistant` all
deleted. Confirmed empty and `(No active implementation)`.

Matt then set it on the phone at Settings > Apps > Default apps > Digital
assistant app, choosing "Penny probe 2a". What the UI wrote:

    role holder  com.pennyspike.probe2a          SET
    assistant    com.pennyspike.probe2a/.PennyVoiceService   SET
    interactor   com.pennyspike.probe2a/.PennyVoiceService   SET
    recognizer   null                            *** NOT SET ***

Prediction recorded before the reboot: Penny will be evicted and the
microphone will be silent. Rebooted, touched nothing, unlocked.

    role holder  com.pennyspike.probe2a          survived
    assistant    com.pennyspike.probe2a/...      survived
    interactor   EMPTY                           *** EVICTED ***
    recognizer   null
    dumpsys voiceinteraction -> (No active implementation)

`PennyVoiceService` never logged — the OS never bound it. `MicFgsService`
was refused with the same `SecurityException` as before. **The OS's own
Recording Activity table was completely empty: no recording happened at
all.** Prediction confirmed exactly.

### Reboot 4 — same Settings-set assistant, plus the recogniser set. YES.

The ONLY change from reboot 3: `settings put secure
voice_recognition_service com.pennyspike.probe2a/.PennyRecognitionService`.
The interactor was left exactly as the Settings UI had written it.

    sinceBoot= 9144ms  PennyVoiceService onCreate — the OS bound the assistant
    sinceBoot= 9158ms  MIC [A-assistant] userUnlocked=false
                       VERDICT YES — REAL AUDIO peak=1329 rms=471.76
    sinceBoot=12387ms  MIC [B-fgs] userUnlocked=false, startForeground(MICROPHONE)
                       ACCEPTED, VERDICT YES — REAL AUDIO peak=1201 rms=449.20
    sinceBoot=14033ms  VM onPayloadReady, userUnlocked=false
    sinceBoot=143114ms unlock

OS corroboration, again in its own words:

    09:56:15:293 rec update riid:47 uid:10192 src:MIC not silenced pack:com.pennyspike.probe2a
    09:56:16:403 rec stop   riid:47 uid:10192 src:MIC not silenced pack:com.pennyspike.probe2a
    09:56:18:554 rec update riid:63 uid:10192 src:MIC not silenced pack:com.pennyspike.probe2a

**Rung 3b's YES is now reproduced on two separate reboots**, and rung 3's
VM wake reproduced a fourth time.

### The controlled result

Four reboots, one APK. The assistant survives a reboot if and only if
`voice_recognition_service` is set:

    reboot 1   interactor set, recognizer NULL      -> evicted, no audio
    reboot 2   interactor set, recognizer SET       -> bound, REAL AUDIO
    reboot 3   interactor set by SETTINGS UI,
               recognizer NULL                      -> evicted, no audio
    reboot 4   interactor set by SETTINGS UI,
               recognizer SET                       -> bound, REAL AUDIO

### Why the OS will not fill the recogniser in for us

`initForUserNoTracing` calls `initRecognizer(userHandle)` at the end of
every run, which calls `findAvailRecognizer(null, userHandle)`. On
AOSP main that method cannot return null when any recognition service
exists — it falls back:

    List<RecognitionServiceInfo> nonSelectableAsDefault =
            removeNonSelectableAsDefault(available);
    if (available.size() == 0) {
        Slog.w(TAG, "No selectableAsDefault recognition services found for user "
                + userHandle + ". Falling back to non selectableAsDefault ones.");
        available = nonSelectableAsDefault;
    }

and `RecognitionServiceInfo.getAvailableServices` applies NO system or
privileged filter — a plain `queryIntentServicesAsUser`, with
`selectableAsDefault` defaulting to **true**.

**But this device does not behave like main.** Android 17 logs
`no auto selectable voice recognition services found for user 0` and
returns null, with no fallback, even with
`android:selectableAsDefault="true"` declared and the service resolvable
(`pm query-services -a android.speech.RecognitionService` finds it). So
Android 17 has tightened this beyond the source above, and the exact
filter was NOT determined. **Do not assert why. It is unread.**

Also ruled out on the way: `BIND_RECOGNITION_SERVICE` does not exist as a
platform permission on this device and `RecognitionService` does not
require one. That hypothesis was wrong.

### Eviction is permanent once it happens

    void setCurInteractor(ComponentName comp, int userHandle) {
        Settings.Secure.putStringForUser(..., VOICE_INTERACTION_SERVICE,
                comp != null ? comp.flattenToShortString() : "", userHandle);

It writes an **empty string**, not null. The one restore path is guarded by
`curInteractorStr == null`, and the re-selection path by
`!"".equals(curInteractorStr)`. Both are dead once the value is `""`. So a
Penny that is evicted once never comes back on its own, at any later boot.
(Matches the device: `interactor` prints empty while `recognizer` prints
`null`.)

### What this means commercially

There is no user-reachable way to set the recogniser on this device.
`android.settings.VOICE_INPUT_SETTINGS` resolves to
`com.android.permissioncontroller...AssistantSettingsActivity` — the same
assistant picker — so there is no separate speech-recogniser screen. The
setting is `Settings.Secure`, writable only with `WRITE_SECURE_SETTINGS`
(signature|privileged) or over adb.

**So on this device, as it stands, a user with no cable cannot put Penny in
the assistant slot in a way that survives a power cycle.** Rung 3b's
microphone result stands; the user-tap delivery route does not.

Worth testing before treating as universal: GrapheneOS ships NO speech
recogniser at all, which is why the setting is empty in the first place. On
a stock phone Google's recogniser occupies it, and the early-return path
would then preserve a user-chosen assistant. **This may be a GrapheneOS
consequence rather than an Android one. Untested — we have no stock device.**

Either way rung 4 dissolves it: the OS image sets its own default
recogniser and can preinstall Penny as the assistant.

## 2026-09-15 — rung 2d ANSWERED YES. Our own code ran inside the guest VM. Compiled on the phone, because the NDK was not affordable.

Versions unchanged from the rung 3b entries: GrapheneOS 2026091001, Android 17
build CP2A.260705.006, Pixel 6a bluejay, bootloader LOCKED, Temurin 21.0.12.1,
build-tools 37.0.0, platform android-37.0. Debian guest 13.7 trixie, kernel
6.12.92-android16-6-g4e585dd7f3b7-ab16266940-4k. **New toolchain: gcc 14.2.0
(Debian 14.2.0-19), installed INSIDE the Debian guest on the phone. There is
still no NDK and no clang on the Mac.**

### The question

Rung 2b booted a VM and rung 3 booted one unattended, but every VM this spike
has ever run carried Google's own `MicrodroidEmptyPayloadJniLib.so`, read out
of the `com.android.virt` APEX. That proves the app OWNS a VM. It proves
nothing about the VM being ours to use, because nothing of ours was ever
inside it. 2c closed off supplying our own kernel and rootfs. This asks the
much smaller question that is left, and it is the one that matters
commercially: **inside Google's unmodified microdroid, can a sideloaded app
run its own payload?**

Not to be confused with 2c. 2c wanted a custom GUEST IMAGE and was refused by
the hidden-API blocklist. 2d changes nothing about the machinery: same
microdroid, same two SDK-visible methods 2b already used
(`setApkPath`, `setPayloadBinaryName`), pointed at our own APK and our own
`.so` instead of at the APEX's.

### Part one: the control run, and why it came first

The NDK download (975MB) was going to take hours over a phone tether, so
rather than idle, the genuinely uncertain half was tested with NO compiler at
all. Google's `MicrodroidEmptyPayloadJniLib.so` was pulled off the device and
packed into OUR APK under the name `PennyPayload.so`, changing one variable
only: which APK microdroid reads the payload out of.

    STEP1 MANAGE_VIRTUAL_MACHINE = GRANTED
    STEP3 our apk = /data/app/~~Ufn763DKehSe1MWk2V9ETg==/
          com.pennyspike.probe2a-TKP0vkfSHHKyWuWOhnxHow==/base.apk
          readable=true bytes=53900
    STEP6 create() returned VirtualMachine(name:penny2d,
          payload:PennyPayload.so, package: com.pennyspike.probe2a)
    STEP7 run() returned, status=RUNNING
    CB onPayloadStarted sinceBoot=1452304ms      (0.87s after run())
    CB onPayloadReady  sinceBoot=1452327ms

and from the OS rather than from our own log:

    name: "penny2d", cid: 2049, requesterUid: 10192, requesterPid: 4258

**That answered every packaging and signature worry in one run**, and it is
worth keeping separate from part two precisely because it did: our APK path is
accepted; our throwaway signing key is accepted; the `idsig` is generated
without complaint; the Stored, page-aligned `.so` is mmapped out of our APK in
the guest and dlopened. After this the ONLY remaining unknown in 2d was
whether our own C was correct — which is a much smaller thing to be wrong
about, and a much cheaper one to fix.

The packaging contract was read empirically rather than assumed. The stock
payload's own entry in `EmptyPayloadApp.apk` is `Stored`, 0% — microdroid does
not unpack the APK, it mounts it read-only in the guest and maps the `.so` in
place. Hence `zip -0`, `zipalign -p`, and `android:extractNativeLibs="false"`.
All three are load-bearing and all three fail silently at build and install
time, surfacing only as the VM dying.

### Part two: the compiler problem, and the route actually taken

There was no C compiler on this Mac and no cheap way to get one:

    NDK                      974,984,488 bytes, confirmed by curl -sI
    Homebrew lld             pulls in llvm — LARGER, not smaller. Dead end.

The Mac's only network was a phone tether, so this was a money decision rather
than a technical one, and Matt made it. **A ~100MB partial NDK download was
also lost to Claude's error**: the installer was killed and its temp directory
is wiped on exit, so the copy raced the cleanup. Copy first, kill second.

The route taken instead: **the Debian guest already on the Pixel is arm64, so
it needs no cross-compiler.** `gcc` with `--no-install-recommends` cost
**43MB, 28 packages** — helped enormously by apt's package lists already being
cached from 12-14 Sept, which saved roughly 150MB of index downloads on their
own. Do not run `apt-get update` in that guest without a reason.

**The trap this creates, and the fix, because it is the whole reason the
source looks the way it does.** Debian is glibc. Microdroid is Android, so it
is bionic, and it has no glibc at all. A payload built the obvious way carries
`DT_NEEDED` for `libc.so.6` and `libgcc_s.so.1`, neither of which exists in the
guest, and it would fail to load. So `penny_payload.c` uses **no C library at
all** — it makes the two syscalls it needs (`write`, `nanosleep`) directly with
twelve lines of aarch64 inline assembly. The syscall ABI is a property of the
kernel and is identical whether the userspace above it is glibc, bionic or
nothing, which is why the same source now compiles correctly under either
toolchain with no `#ifdef` anywhere. A C library was never what was being
tested.

The build, in the guest:

    gcc -shared -fPIC -nostdlib -ffreestanding -fno-stack-protector \
        -o libvm_payload.so vm_payload_stub.c
    gcc -shared -fPIC -O1 -nostdlib -ffreestanding -fno-stack-protector \
        -Wl,-z,max-page-size=4096 -Wl,--hash-style=sysv \
        -Wl,-soname,PennyPayload.so \
        -o PennyPayload.so penny_payload.c -L. -lvm_payload

Every flag there is defensive and each one was chosen against a specific way
bionic refuses a file:

    -nostdlib              or gcc links libgcc_s.so.1 and glibc start files
    -ffreestanding         or gcc turns our hand-written loop back into a
                           call to strlen, which does not exist here
    -fno-stack-protector   Debian defaults it ON; it needs __stack_chk_fail
                           from glibc
    -z max-page-size=4096  aarch64 ld defaults to 64k segment alignment; the
                           APK is aligned to 4k, so a 64k-aligned .so could
                           not be mapped in place
    --hash-style=sysv      the form bionic has always supported

The built file was then verified before it was ever packaged, which is cheap
and would have caught any of the above:

    OS/ABI:   UNIX - System V        (bionic rejects other values)
    Type:     DYN, Machine: AArch64
    NEEDED:   libvm_payload.so       — and nothing else. No libc.
    UND syms: AVmPayload_notifyPayloadReady — and nothing else.
    exported: AVmPayload_main  GLOBAL DEFAULT
    LOAD:     align 0x1000 on both segments

`libvm_payload.so` is a twelve-line stub with an empty function body, built
only so the linker emits the `DT_NEEDED` entry; it is never packaged, and
`build.sh` greps the finished APK to prove it (`SOLEAK` must be 0). If it ever
shipped, the guest would load a do-nothing version of the call and
`onPayloadReady` would simply never fire. Same trick, same reason, as the Java
stubs in stage 2.

### The result

    STEP3 our apk = /data/app/~~I7cTfFJAdOQ4M1Uj_ZtwDA==/
          com.pennyspike.probe2a-tOuGPlyQbwXT7hXHwMTYqQ==/base.apk
          readable=true bytes=49804
    STEP5 deleted a pre-existing VM named penny2d
    STEP6 create() returned VirtualMachine(name:penny2d,
          payload:PennyPayload.so, package: com.pennyspike.probe2a)
    STEP7 run() returned, status=RUNNING
    CB onPayloadStarted sinceBoot=4048963ms
    virtmgr: Console(2052): [ 0.661636][T62] PENNY2D: our own payload is
             running inside the guest
    CB onPayloadReady sinceBoot=4048982ms
    SIGNAL 1 of 3: our payload called notifyPayloadReady
    virtmgr: Console(2052): [ 0.667055][T62] PENNY2D: notified ready,
             holding for 5s
    virtmgr: Console(2052): [ 5.667591][T62] PENNY2D: exiting 42
    CB onPayloadFinished exitCode=42
    SIGNAL 2 of 3: exit code 42 — ours, not the stock payload's
    CB onStopped reason=3

**All three signals, and they were designed to be independent so that one dead
log channel could not sink the result:**

1. **The guest console carries our strings.** `Console(2052)` is the guest's
   own console relayed to host logcat by `virtmgr`. Those three lines exist
   nowhere in the stock payload.
2. **`onPayloadReady` fired.** That callback exists only because our C called
   `AVmPayload_notifyPayloadReady()`. Had the symbol not resolved against
   microdroid's real `libvm_payload.so`, the VM would have died at dlopen.
3. **Exit code 42.** `AVmPayload_main` returned it and the host read it back
   through `onPayloadFinished`. The stock payload cannot produce it.

And a fourth, unplanned, which is the most convincing of the lot because
nothing in it comes from a string we wrote: **the guest's own clock shows
0.667s to 5.667s between the second and third console lines — 5.000s
exactly.** That is our `nanosleep` syscall being served by the guest kernel.
The VM was not merely loading our file; it was executing our instructions and
sleeping on our behalf.

Total VM lifetime 6.6s, from `run()` at 11:03:32.894 to `onStopped` at
11:03:39.385, of which 0.87s was boot.

### What this answers, and what it does not

**Answers: rung 2 is now fully closed, and 2c was not the end of it.** A
sideloaded, non-platform-signed, unprivileged app (uid 10192) can run its own
compiled code inside a hardware-isolated VM on a locked, verified-boot Pixel.
That is the thing worth showing anyone. 2c said we cannot bring our own
kernel and rootfs; 2d says we do not need to, because Google's microdroid will
carry our payload.

**Does not answer, and must not be claimed:**

- `pm grant` still cannot ship. `MANAGE_VIRTUAL_MACHINE` is `development`
  protection level and needs a cable. Unchanged by this result, and it is
  still the single reason rung 4 exists.
- Nothing has crossed the host/guest boundary in either direction except a
  console string and an exit code. **Voice has still never been through the VM
  boundary**, and that remains the next real unknown on the voice path.
- The payload is ten lines with no C library. Running Claude Code or anything
  resembling it inside microdroid is a completely different proposition and
  nothing here speaks to it. Microdroid is a minimal Android, not Debian.
- `Using sample DICE values` still applies — these are debuggable VMs
  (`DEBUG_LEVEL_FULL`). No attestation claim rests on any of this.
- This says nothing about the payload surviving a reboot unattended. Rung 3's
  `VmService` was deliberately NOT touched, so that the proven wake result
  could not be put at risk by 2d. Combining the two is untested.

### Method note worth keeping

Doing the control run first, with zero new tooling, while the expensive
download was still pending, turned out to be the highest-value hour of the
day: it moved the entire packaging/signature/APK-path question out of the
unknown column before a single line of C was compiled. When the real run then
worked first time, that was not luck — it was that only one variable was left.

## 2026-09-15 — rung 3c ANSWERED YES, both halves. Audio crossed the VM boundary for the first time.

Versions unchanged from the 2d entry: GrapheneOS 2026091001, Android 17 build
CP2A.260705.006, Pixel 6a bluejay, bootloader LOCKED, verifiedbootstate=yellow.
Temurin 21.0.12.1, build-tools 37.0.0, platform android-37.0. Debian guest 13.7
trixie, kernel 6.12.92-android16-6-g4e585dd7f3b7-ab16266940-4k, gcc 14.2.0
(Debian 14.2.0-19) — still the only C compiler in the project, still on the
phone rather than on the Mac. No new tooling was installed and nothing was
downloaded.

### The question

Three things were proven separately and had never been joined:

    3b   the app holds a live microphone 9.5s after power-on, phone locked
    3    the app owns a VM and wakes it unattended
    2d   the app runs its own compiled code inside that VM

Everything that had ever crossed the host/guest boundary went OUTWARDS and was
one-way: three console strings and an exit code. **Nothing had ever gone IN.**
So rung 3b's samples died in the host process — there was no way to hand them
to the guest. Defined as two questions, deliberately in this order:

    3c-i   can the host send ANY bytes to the payload and get them back?
    3c-ii  can real captured audio make the same trip, and does the payload
           see the same bytes the host recorded?

### Reading the host API off the device, which decided the whole design

The 2b/2c method again — pull `framework-virtualization.jar` from
`/apex/com.android.virt/javalib/`, `dexdump -d`, read the per-member
`hiddenapi` flag before writing a line of code. The relevant rows:

    connectVsock         (J)Landroid/os/ParcelFileDescriptor;   SDK,TEST-API
    connectToVsockServer (J)Landroid/os/IBinder;                SDK,TEST-API
    getConsoleOutput     ()Ljava/io/InputStream;                SDK,TEST-API
    getConsoleInput      ()Ljava/io/OutputStream;               BLOCKED,TEST-API
    MIN_VSOCK_PORT / MAX_VSOCK_PORT = 1024 / 4294967295         SDK,TEST-API
    getCid               ()I                                    BLOCKED

**`getConsoleInput` being BLOCKED is the load-bearing fact.** The console is
the obvious way to push bytes at a guest and it is outbound-only from an app.
vsock is therefore not the better route, it is the only route left. And
`connectVsock` takes a **long, not an int** — getting that wrong would present
as `NoSuchMethodError`, which on this device is also exactly how a hidden-API
block presents, and the afternoon would have been spent on the wrong question.

Nineteen further members of `VirtualMachine` are BLOCKED — `addDisplay`,
`getDisplays`, `sendKeyEvent`, `sendMouseEvent`, `suspend`, `resume`,
`setMemoryBalloon`, `getGuestAgent`, `getRootDir` among them. The SDK-flagged
set an app actually gets is small: create/run/stop/delete, the callback pair,
`getStatus`, `getName`, `getConfig`, `toDescriptor`, the two console readers
and the two vsock connectors. That list is worth keeping — it is the real
surface of a VM from outside the platform.

### What could NOT be read, and why it did not matter

The instruction was to check what microdroid's own `libvm_payload.so` offers
before writing raw syscalls. It was checked, on the device, and **it cannot be
read from the host.** `libvm_payload.so` lives only inside
`/apex/com.android.virt/etc/fs/microdroid.img`, which is EROFS (magic
`e2e1f5e0` at offset 1024), and the blocks holding that file are compressed:

    grep -ac 'AVmPayload'         microdroid.img  ->  0
    grep -ac 'microdroid_manager' microdroid.img  ->  22
    grep -ac 'AF_VSOCK'           microdroid.img  ->  3
    grep -ac 'libvm_payload'      microdroid.img  ->  2

So the image is only PARTLY plaintext — plenty is readable, that file's symbol
table is not. The two `libvm_payload` hits are both name lists (a directory
entry, and the guest's public-library list), not symbols. Extracting it would
have meant erofs tooling on a tethered Mac, i.e. a download.

**It was not bought, because it would not have changed the file.** The only
host/guest byte channel that library is understood to offer is a binder RPC
server, and binder RPC means libbinder_ndk, libc++ and generated AIDL — a C++
toolchain, which is precisely what this spike does not have and deliberately
did not buy in 2d. `AF_VSOCK` is a kernel interface: four syscalls, no library.
Recorded as a genuine gap rather than an answer — if rung 4 ever wants the RPC
route, the symbol list is still unread.

### The payload

`payload/penny3c_payload.c`, built in the Debian guest on the phone with the
identical flag set 2d established (`-nostdlib -ffreestanding
-fno-stack-protector -Wl,-z,max-page-size=4096 -Wl,--hash-style=sysv`), and
verified before packaging exactly as the trap entry says to:

    OS/ABI     UNIX - System V
    NEEDED     libvm_payload.so   — and nothing else. No libc.
    UND syms   AVmPayload_notifyPayloadReady — and nothing else.
    exported   AVmPayload_main
    LOAD       align 0x1000 on both segments
               filesz 0x000138, memsz 0x010138 — the 64k buffer, in .bss

No malloc, because there is no libc: the receive buffer is a static 64KB array
in `.bss`, which the loader zeroes. One second of 16kHz 16-bit mono is 32,000
bytes, so that is twice what 3c-ii sends.

The guest listens and the host connects, and that direction is forced by
`getConsoleInput` being blocked. Wire format kept trivial on purpose, so a
protocol bug and a channel failure cannot look alike:

    host -> guest   4 bytes big-endian length L, then L bytes (L==0 = goodbye)
    guest -> host   4 bytes L, 4 bytes FNV-1a of those L bytes, L bytes echoed

The echo answers 3c-i; the hash answers 3c-ii without shipping a waveform back
for a human to squint at. **The guest logs its hash to the console as well**,
so the comparison survives even if the return leg is the broken thing.

**One ordering decision that is not cosmetic.** `AVmPayload_notifyPayloadReady()`
is called AFTER `listen()`, never before. The host only connects when
`onPayloadReady` arrives, so notifying after listen makes it impossible for the
host to connect before there is anything to connect to. That race would have
surfaced as an intermittent refusal and been read as the channel being shut.

### 3c-i — the control, no audio anywhere near it

    STEP7 run() returned, status=RUNNING
    CB onPayloadStarted  0.65s after run()
    CB onPayloadReady
    3c-i sending: PENNY3C-HOST-TO-GUEST 1789467949859 the quick brown fox
    [3c-i echo] connectVsock(5555) -> ParcelFileDescriptor  after 1ms
    [3c-i echo] sent 55 bytes, our fnv1a=cb22961e
    [3c-i echo] guest replied len=55 fnv1a=cb22961e
    [3c-i echo] hashMatch=true echoMatch=true roundTrip=2ms
    CB onPayloadFinished exitCode=43

and from the guest's own console, relayed by virtmgr — cid 2054, a channel the
host process does not write to:

    PENNY3C: socket ok
    PENNY3C: listening on vsock port 5555
    PENNY3C: notified ready, waiting for the host to connect
    PENNY3C: host connected
    PENNY3C: received 55 bytes, fnv1a=cb22961e
    PENNY3C: echoed it back

**55 bytes in, 55 bytes back, identical, 2ms.** Done first and on its own
exactly as 2d's control run was: if the channel had been shut, audio in the
picture would only have supplied a second candidate explanation for the same
silence.

### 3c-ii — real audio

    AUDIO: samples=16000 peak=1999 rms=655.00 nonZero=15991 (99.9%)
    [3c-ii control] sent 15 bytes, our fnv1a=73f747f3
    [3c-ii control] guest replied len=15 fnv1a=73f747f3   hashMatch=true
    [3c-ii audio]   sent 32000 bytes, our fnv1a=225c918f
    [3c-ii audio]   guest replied len=32000 fnv1a=225c918f
    [3c-ii audio]   hashMatch=true echoMatch=true roundTrip=12ms
    CB onPayloadFinished exitCode=43

guest console, cid 2056:

    PENNY3C: received 15 bytes, fnv1a=73f747f3
    PENNY3C: echoed it back
    PENNY3C: received 32000 bytes, fnv1a=225c918f
    PENNY3C: echoed it back
    PENNY3C: 2 exchange(s) completed, exiting 43

**One second of real microphone audio, 32,000 bytes, into a VM the app owns,
hashed identically at both ends and echoed back byte for byte in 12ms.**

Two controls, not one, and both were necessary. A 15-byte ASCII leg ran in the
SAME VM immediately before the audio, so a failure on the audio leg could not
be blamed on the channel having gone away between runs. And the capture is
judged before it is sent — peak, RMS and non-zero proportion — because **a hash
over 32,000 zeros matches trivially.** Android 17 suppresses background audio
by returning zeros without throwing, so a silent capture would have produced a
perfect checksum match and meant nothing at all. The code refuses to send
anything that looks like silence, precisely so 3c-ii cannot be misread as a
pass. 99.9% non-zero, peak 1999, rms 655 — that is a room, not a suppression.

### No regression

`Probe2dActivity` was re-run from this same APK afterwards: `onPayloadReady`,
`onPayloadFinished exitCode=42`. 2d still reproduces. The build now packages
BOTH payloads — `PennyPayload.so` (2d) and `Penny3cPayload.so` (3c) — side by
side, because microdroid loads only the one `setPayloadBinaryName()` asks for
and two files cost nothing. Keeping a proven result runnable is the same
instinct that keeps `VmService` unedited.

**`VmService` was NOT touched.** Rung 3's wake result was never put at risk,
same as 2d.

### What this answers

**The voice path is now joined end to end in the host process and one step into
the guest.** A sideloaded, unprivileged, non-platform-signed app on a locked
verified-boot Pixel can capture real audio and deliver it, intact and verified,
into a hardware-isolated VM it owns, running code it wrote. Every link in that
chain is now measured rather than assumed.

### What this does NOT answer, and must not be claimed

- **This ran unlocked, in the foreground, launched by hand over adb.** Rung 3
  and 3b measured the locked, unattended, pre-first-unlock case; 3c did not.
  Joining them — audio crossing the boundary at boot with nobody in the room —
  is untested and is the obvious next thing.
- **One second, once.** 32,000 bytes in a single shot. There is no streaming,
  no sustained capture, no backpressure, and nothing ran for longer than 3
  seconds. This says nothing about holding a channel open for hours.
- **The guest does nothing WITH the audio.** It hashes it and echoes it. There
  is no recognition, no model, no processing of any kind. "Audio reached the
  guest" is not "Penny heard you", and the gap between those two is most of the
  product.
- The payload still has no C library, let alone a runtime. 2d's warning stands
  unchanged: microdroid is a minimal Android, not Debian, and nothing here
  speaks to running Claude Code or a model inside it.
- `pm grant` still cannot ship. `MANAGE_VIRTUAL_MACHINE` is `development`
  protection level and needs a cable. Unchanged, and still the only reason
  rung 4 exists.
- Still `DEBUG_LEVEL_FULL`, still `Using sample DICE values`. No attestation
  claim rests on any of this.
- The vsock channel is plaintext between two processes on the same phone. No
  encryption question was asked and none is answered.

### Method notes worth keeping

- **Read the dex flag first, again, and it paid for itself again.** Four
  minutes with `dexdump` produced the whole design: vsock because the console
  is blocked inbound, `long` because the descriptor says `(J)`, port 5555
  because the bounds are 1024 and 4294967295. Not one runtime refusal was hit.
- **Each `write()` to fd 1 in the guest becomes its own console line.** The
  guest kernel frames the console per write, so `say("received "); sayu(n);
  say(" bytes")` arrives as three separate timestamped lines and a naive
  `grep PENNY3C` drops the number entirely — it looks like the payload printed
  nothing. Build the whole line in a buffer and write it once, or grep on the
  thread id (`T61`) rather than on the prefix.
- The control-first discipline from 2d transferred intact and is now three for
  three: control run in 2d, echo before audio in 3c-i, ASCII leg before the
  PCM leg inside 3c-ii.

## 2026-09-15 — rung 3d DEFINED, not yet run. The join: boot, locked, unattended.

Written BEFORE the test, deliberately. Every rung so far was defined before it
was run and that is what made the negative results readable — 2c is only a
useful NO because "done" was written down first. This one is the easiest to
fudge after the fact, because three separate results already exist and a
sloppy reading could staple them together and call it a chain.

### The question

Rungs 3, 3b, 3c and 2d are each proven, on the same APK, and **not one of them
has ever run in the same boot as the others**:

    3    the app owns a VM and it is up 14-16s after power-on, locked     4 reboots
    3b   the app holds a live microphone 9.5s after power-on, locked      2 reboots
    2d   the app runs its own compiled code inside that VM               unlocked, by hand
    3c   real captured audio crosses into that code, hash-verified       unlocked, by hand

3d asks whether the whole thing happens at once, at boot, with the phone locked
and nobody in the room:

    power on -> LOCKED_BOOT_COMPLETED -> assistant binds
             -> microphone captures real audio
             -> VM boots, payload listens on vsock
             -> audio crosses into the guest
             -> guest hashes it, echoes it
             -> host verifies the hash matches what it recorded
    ...all of it before the PIN is typed.

**This is an integration test, not a capability question.** No new API is
touched, no new permission is asked for, nothing is downloaded. If it fails it
almost certainly fails on ordering or on a device-state precondition, not on a
platform refusal — and telling those two apart is most of the work.

### Done means, and it is all six

1. `userUnlocked=false` logged at the moment of the exchange, not just at boot.
2. Audio judged REAL on the samples — peak, RMS, proportion non-zero. A
   checksum over 32,000 zeros matches perfectly and proves nothing, so the host
   refuses to send silence, exactly as 3c did.
3. The guest's own console independently reports the same FNV-1a. That channel
   is written by the guest kernel, not by this app, which is why it counts.
4. Guest exit code 43.
5. `sinceBoot=` timestamps place all of it before the first unlock.
6. Reproduced on a second reboot. Rung 3b's first reboot would have been
   written up as a NO and would have been wrong.

Anything short of six is written up as what it is. A partial chain — say, mic
yes, VM yes, exchange never happened — is a real and useful result and must not
be rounded up.

### Preconditions. The run is void without them.

    settings secure voice_recognition_service = com.pennyspike.probe2a/.PennyRecognitionService
    role holder android.app.role.ASSISTANT    = com.pennyspike.probe2a
    RECORD_AUDIO                              granted

All three reset silently and none logs a complaint. Read them immediately
before every `adb reboot`. An empty recogniser evicts Penny at boot and
produces a false NO on the microphone half — that trap cost a reboot on 15
Sept and is written up in the 3b entry.

### The four things it faces that no earlier rung did

- **`VmService` has to hold the vsock channel.** It is the one file this spike
  has never edited, because it carries the rung 3 result proven over four
  reboots. A COPY gets edited; the original stays runnable from the same APK.
  Same discipline that kept 2d and 3c out of it.
- **The two halves came from different processes.** 3b's audio came from a
  service exempted by the assistant role. 3c's VM and vsock came from a
  foreground activity, unlocked. At boot there is no activity and no human, so
  both must happen in one directBootAware service — and whether the assistant
  exemption reaches a service that is also holding a VM is untested.
- **Ordering, which is the likeliest way this fails without being a real NO.**
  The microphone is available at ~9.5s; the VM is not ready until ~14s. The
  audio exists before there is anywhere to send it. Either buffer it or capture
  on `onPayloadReady` — but decide deliberately, because "the send failed" and
  "the boundary does not work at boot" look identical in a log.
- **The 20-second boot FGS exemption.** `startForeground` must be called inside
  it. Rung 3 used ~5ms so there is headroom, but a cold dex2oat on the first
  boot after an install eats into it.

### Practicalities

GrapheneOS kills the USB data path while locked, so after `adb reboot` the
cable is dead until somebody unlocks by hand. Everything is read from `logcat`
after the unlock and proved by `sinceBoot=`. Expect 2-3 reboots. And make real
noise near the phone while it is locked — a silent room and a suppressed
microphone are indistinguishable.

### Explicitly NOT in scope

Streaming or sustained capture — 3d is one second, once, same as 3c. The guest
doing anything WITH the audio. Endurance. Attestation. And the delivery
problem: `pm grant` and the assistant slot both still need a cable, and a yes
here does not change that.

## 2026-09-15 — rung 3d ANSWERED YES. The whole chain ran at boot, locked, with nobody in the room.

Defined earlier today, before it was run. All six conditions met, on two
reboots, on one APK. A sideloaded, unprivileged, non-platform-signed app woke
itself after power-on, took the microphone while the phone was still at the
lock screen, booted a VM it owns running code it wrote, and delivered one real
second of captured audio into that VM intact and verified — **15.1 and 15.5
seconds after power-on, with `userUnlocked=false`, disk still encrypted, and
the PIN not typed for another 134 and 101 seconds.**

    GrapheneOS 2026091001, Android 17 CP2A.260705.006, Pixel 6a bluejay
    bootloader LOCKED, verifiedbootstate=yellow
    com.pennyspike.probe2a, uid 10192, throwaway-signed, sideloaded

### The six conditions, and what each was checked against

    1  userUnlocked=false AT THE EXCHANGE   PENNY3D CHAIN start, both boots
    2  the audio real on the samples        peak 1506/2723, 90.5%/90.9% non-zero
    3  the guest console's own fnv1a        cid 2049, matched both boots
    4  guest exit code 43                   onPayloadFinished, both boots
    5  every timestamp before first unlock  134.5s and 101.3s of margin
    6  reproduced on a second reboot        11:43:11 and 11:46:43

### Boot 1 — 09-15 11:43

    12067ms  BootReceiver: LOCKED_BOOT_COMPLETED, Penny3dService accepted
    12085ms  startForeground(SPECIAL_USE|MICROPHONE) OK, userUnlocked=false
    12466ms  run() returned, status=RUNNING
    13945ms  onPayloadReady — our code is listening on vsock 5555
    13946ms  CHAIN start userUnlocked=false
    13948ms  control leg: 21 bytes, fnv1a=7bb16a1b, hashMatch echoMatch, 2ms
    13948ms  AUDIO samples=16000 peak=1506 rms=454.47 nonZero=14479 (90.5%)
    15108ms  audio leg: 32000 bytes, fnv1a=ac4b8dcf, hashMatch=true
             echoMatch=true roundTrip=24ms
             guest exit 43
    149579ms BOOT_COMPLETED — i.e. the first unlock, 134.5s later

Guest console, cid 2049, thread T61 — a channel this app does not write to:

    PENNY3C: listening on vsock port 5555
    PENNY3C: notified ready, waiting for the host to connect
    PENNY3C: host connected
    PENNY3C: received / 32000 / bytes, fnv1a= / ac4b8dcf
    PENNY3C: echoed it back
    PENNY3C: 2 exchange(s) completed, exiting 43

### Boot 2 — 09-15 11:46

    12601ms  LOCKED_BOOT_COMPLETED, accepted
    13011ms  run() returned RUNNING
    14339ms  onPayloadReady
    14340ms  CHAIN start userUnlocked=false
    14343ms  control 21 bytes fnv1a=f7615654, matched
    14343ms  AUDIO samples=16000 peak=2723 rms=513.67 nonZero=14536 (90.9%)
    15513ms  audio 32000 bytes fnv1a=312decbb, hashMatch=true echoMatch=true
             roundTrip=12ms, guest exit 43
    116840ms first unlock, 101.3s later

Guest console cid 2049 thread T63 reported `32000` and `312decbb`
independently. Different hash from boot 1, as it must be — it is a different
second of sound.

### The control, run first, as every rung here has

Before either reboot, the same service was started by hand over adb with the
phone unlocked and in the foreground:

    adb shell am start-foreground-service -n com.pennyspike.probe2a/.Penny3dService

23 bytes control leg matched, AUDIO peak=1868 rms=604.52 99.9% non-zero,
32000 bytes fnv1a=425948d7 matched at both ends, exit 43. The service itself
logged `CHAIN WARNING: the user is already unlocked. Whatever happens below,
it is NOT an answer to rung 3d.` That control is what makes the two reboots
readable: had they failed, the fault would have been the boot context and
nothing else, because the identical code had already been seen to work.

### What was built, and what deliberately was not

`Penny3dService` is a COPY of `VmService`, not an edit of it. `VmService` is
still byte-for-byte what it was when rung 3 was proven, and it ran on both of
these reboots and brought `penny3` up as usual. Same discipline that kept 2d
and 3c in their own components. Three services now start from the same boot
broadcast — `VmService`, `MicFgsService`, `Penny3dService` — each independent,
none able to take the others down.

The guest payload was **not rebuilt**. `Penny3cPayload.so` from rung 3c was
reused unchanged, straight out of `build-payloads/`. Same wire protocol, same
question; recompiling it would have added a variable for nothing and cost a
round trip into the Debian guest.

### The ordering decision, written down before the run

The microphone is available at ~9.5s and the VM is not ready until ~14s, so
the audio would exist before there was anywhere to send it. Two options:
buffer an early capture, or capture when the guest says it is listening.
**Took the second**, for three reasons recorded in the class comment before
the first reboot: `MicFgsService` is already capturing at ~12-13s on the same
boot and a second `AudioRecord` at that moment risked contention that would
have looked exactly like OS suppression; it keeps "no audio" and "audio but no
crossing" as two separable log lines; and asking for the microphone LATER is
the harder case, so a yes covers the easier one.

### Five things measured here that were not known before

- **One foreground service may declare `specialUse|microphone` and start at
  boot.** Rung 3 used `specialUse` alone, rung 3b used `microphone` alone.
  Combined, accepted on both reboots and in the control. So the wake service,
  the listening service and the VM holder can all be the same object.
- **The assistant-role microphone exemption reaches a service that is also
  holding a VM.** 3b showed the exemption reaches beyond the assistant's own
  process; this shows it is not narrowed by what else the service is doing.
- **Three simultaneous microphone captures in one app at boot all returned
  real audio.** A-assistant at ~10.7s, B-fgs at ~13.8s, 3d at ~14.3s. The
  contention worry that drove the ordering decision was unfounded — worth
  knowing, and it means the buffering variant is available if ever needed.
- **The microphone is still there at ~14s, not only at ~9.5s.** 3b measured
  the earliest moment; nothing had measured whether it persists.
- **Two VMs boot unattended before first unlock,** `penny3` and `penny3d`,
  both `requesterUid: 10192`, on a 6GB phone at 256MB each. Rung 1 saw two
  VMs coexist; neither had ever been brought up before a human was involved.

Rung 3 has now reproduced on **six** reboots, rung 3b's microphone on **four**.

### What this answers

The four separate results are one result. A sideloaded app on a locked,
verified-boot Pixel can, with nobody present and the disk still encrypted:
wake itself, hold a live microphone, own and boot a hardware-isolated VM, run
its own compiled code inside it, and carry real captured audio across the
boundary into that code with the bytes verifiably intact at both ends. Nothing
in that sentence needed a human, a screen, or an unlock.

That is the whole voice path end to end, minus the part that understands what
was said.

### What this does NOT answer, and none of it is a detail

- **The guest still does nothing WITH the audio.** It hashes it and echoes it.
  No recognition, no model, no processing of any kind. "Audio reached the
  guest at boot" is not "Penny heard you", and the gap between them is most of
  the product. This is now the largest unanswered thing in the repo.
- **One second, once.** Same as 3c. The VM lived three seconds. No streaming,
  no sustained capture, no backpressure, no long-held channel, no endurance.
  Do not let 3d be stretched into "Penny listens continuously from boot".
- **Delivery is unchanged and is still the commercial blocker.** `pm grant`
  needs a cable, and the assistant slot needs `voice_recognition_service`
  which has no user-reachable screen. Everything here still requires a person
  with a USB cable. Rung 4 is the only thing that changes that.
- **Still `DEBUG_LEVEL_FULL`, still non-protected, still sample DICE values.**
  No attestation claim rests on any of this, and this device cannot run a
  protected VM at all.
- **The VM's state lives in device-encrypted storage**, readable without the
  PIN. That trade-off is load-bearing in this result, not incidental to it.
- **Two reboots is reproducibility, not reliability.** Nothing was tested
  under memory pressure, on a cold dex2oat boot after an OS update, or on a
  battery rather than mains.

### Method notes

- **Control first, for the fourth rung running.** 2d's control payload, 3c-i
  before 3c-ii, 3c-ii's ASCII leg before its PCM leg, and now an unlocked
  foreground run of the exact same service before either reboot. It has never
  once been wasted work.
- **Write the ordering decision down BEFORE the run.** A log cannot tell you
  afterwards why you chose to capture late rather than buffer early, and "the
  send failed" and "the boundary does not work at boot" are indistinguishable
  in one.
- **The console fragmentation trap bit again and cost a minute.** `grep
  PENNY3C` returns lines reading `received ` and ` bytes, fnv1a=` with the
  values missing — each `write()` to fd 1 is its own console line. Grep the
  payload's thread id instead, and note it CHANGES between boots: T61 on boot
  1, T63 on boot 2, T62 in the control. Find it with `grep "PENNY3C: host
  connected"` first, then grep that thread.

## 2026-09-15 — rung 3e-i ANSWERED YES, with a ceiling. 2GB and 8 CPUs are given freely; 4GB kills the phone.

Defined in advance as the gate in front of the largest unanswered thing in
this repo. Every VM this spike had ever booted had 256MB and ONE CPU. A small
language model needs roughly 1.5GB and several threads. If microdroid would
not hand a sideloaded app that, no benchmark would matter and the
model-in-the-guest plan would be dead before it was written.

**It hands it over without argument. `com.pennyspike.probe2a`, uid 10192,
sideloaded and throwaway-signed, created and booted a microdroid VM with
2048MB of RAM and 8 vCPUs, and the guest served a vsock round trip from
inside it. Measured twice.**

    GrapheneOS 2026091001, Android 17 CP2A.260705.006, Pixel 6a bluejay
    bootloader LOCKED, verifiedbootstate=yellow
    host: MemTotal 5,718,280 kB (5.45GiB), 8 cores, 3GB zram swap
    com.pennyspike.probe2a, uid 10192, unprivileged, not platform-signed

### The dex flags, read before a line of code was written

The method that has now got rungs 2a through 3d right, and the trap it
avoids: a blocklisted member and a typo both present as `NoSuchMethodError`.
Out of the device's own `framework-virtualization.jar`, `dexdump -d`:

    VirtualMachineConfig$Builder.setMemoryBytes  (J)   hiddenapi 0x0020 SDK
    VirtualMachineConfig$Builder.setCpuTopology  (I)   hiddenapi 0x0020 SDK
    VirtualMachineConfig.CPU_TOPOLOGY_MATCH_HOST  I    hiddenapi 0x0020 SDK
    VirtualMachineConfig.CPU_TOPOLOGY_ONE_CPU     I    hiddenapi 0x0020 SDK

`0x0020` is the same flag `DEBUG_LEVEL_FULL` and `setApkPath` carry, both of
which 2b and 2d called successfully. Predicted OPEN; open it was. Ten members
checked this way across four rungs now, no counterexample.

Note `setMemoryBytes` takes a **long**, the same width trap as
`connectVsock`.

### The numbers

Host free memory was measured first — CLAUDE.md records that host-side
memory had never been measured in this spike, and every earlier statement
about pressure was read from inside a guest. This is the host.

    ask     cpu        run()      onPayloadReady   vsock   host MemAvailable    LMK
    256MB   ONE_CPU    accepted   +0.7s            3ms     1481MB -> 1203MB     nothing killed
    2048MB  MATCH_HOST accepted   +4.3s            4ms     1473MB ->  810MB     nothing killed
    2048MB  MATCH_HOST accepted   +3.9s            7ms     2885MB -> 1022MB     nothing killed
    3072MB  MATCH_HOST accepted   +5.1s            15ms    2809MB ->  312MB     5 processes killed
    4096MB  MATCH_HOST accepted   NEVER            —       2861MB -> —          OUR APP killed
    6144MB  MATCH_HOST accepted   NEVER            —       2876MB -> —          —

**More than one CPU: YES, and it is not a token second core.** Counted from
the host kernel rather than claimed by our app — crosvm runs one thread per
vCPU, and during the 2GB run `ps -AT` showed `crosvm_vcpu0` through
`crosvm_vcpu7`. Eight vCPUs on an eight-core phone. The two 256MB VMs alive
at the same moment contributed one `crosvm_vcpu0` each, which is what makes
the eight readable as one VM's.

### Corroboration, from the guest and from the hypervisor, not from our log

Neither of these is written by the app, which is the point.

**The hypervisor's own command line**, logged by virtmgr:

    256MB  ONE_CPU     "--cpus", "num-cores=1,sve=[auto=true]"   "--mem", "273"
    2048MB MATCH_HOST  "--cpus", "sve=[auto=true]"               "--mem", "2065"

At MATCH_HOST there is no `num-cores` at all — crosvm is told to take the
host's topology.

**The guest kernel's own reading of its memory.** microdroid sizes zram to
its RAM, and the guest prints the figure itself. Three VMs were alive on the
phone at once and each sized its own:

    Console(2086)  Adding  242896k swap on /dev/block/zram0     penny3,  256MB
    Console(2087)  Adding  242896k swap on /dev/block/zram0     penny3d, 256MB
    Console(2088)  Adding 2038096k swap on /dev/block/zram0     penny3e, 2048MB

2,038,096 kB is the guest kernel saying it found ~2GB. Same boot, same phone,
next to two guests that found 243MB.

**The guest served.** `onPayloadReady` fires only because our own C called
`AVmPayload_notifyPayloadReady()`, a 42-byte vsock round trip came back with
a matching FNV-1a and a byte-for-byte echo, and the guest exited 43 of its
own accord. A VM that was merely registered does none of those.

### Where the ceiling is, and whose ceiling it is

**Nothing ever refused.** `VirtualMachineConfig.Builder` accepted every
figure, including 6144MB — more than the phone physically has — and
`VirtualizationService` accepted every one too, returning `STATUS_RUNNING`
in 10-22ms each time. There is no API-level cap, no permission check on
size, and no sanity check against physical memory.

The ceiling is Android's low-memory killer, and it arrives without warning:

- **3072MB boots and works**, but took host MemAvailable to 312MB and the
  killer took `com.android.launcher3`, `com.android.inputmethod.latin`,
  `app.grapheneos.networklocation`, `android.ext.services` and
  `com.google.android.iwlan`. The VM was fine. The phone was not.
- **4096MB never became ready.** The killer worked up the list and reached
  our own app: `Process com.pennyspike.probe2a (pid 15136) has died: fg TOP`
  — foreground and top of the stack was not enough. Killing the app kills
  the handle, which kills the VM. This is also why no verdict was logged:
  the probe was dead before its own watchdog could fire, which is worth
  remembering as a failure mode that looks like a hang.
- **6144MB** likewise, and it is above physical memory, so it could never
  have worked.

**So the usable figure on this phone is 2GB.** It is the largest tested that
booted, served and killed nothing. 3GB is available if the phone is allowed
to be a VM host and little else.

### What this does NOT answer, and the first one is the important one

- **Being GIVEN 2GB is not being able to USE it, and this run actively
  suggests the gap is real.** On the first 2GB run the host only surrendered
  ~660MB of the 2048MB granted. The guest kernel does not touch pages it has
  not needed yet, so a VM can be handed memory the host would not actually
  be able to find if it were asked for. **That is rung 3e-ii and it is not a
  formality** — it is the difference between a number in a config and a
  model that fits.
- **Where a model FILE would live is untouched.** Rung 3d's shutdown log
  carried `init: Unknown /data fs type: tmpfs`, and this run's guests each
  built a zram swap device sized to their whole RAM. If microdroid's writable
  storage is a RAM disk, a 1.5GB model file costs 1.5GB of RAM on top of what
  the model needs to run, and the 2GB ceiling becomes 2GB for BOTH. That is
  rung 3e-iii and it is not yet confirmed.
- **Nothing was run inside the VM.** The payload is 3c's, unchanged: it
  listens, hashes 42 bytes, echoes, exits. There is no model, no runtime, no
  threads, and nothing that uses a second CPU. "8 vCPUs were created" is a
  statement about crosvm, not about anything the guest did with them.
- **This was unlocked, in the foreground, over adb.** Whether a 2GB VM comes
  up at boot before first unlock is untested. Rung 3d's numbers were all at
  256MB, and a 2GB VM takes ~4s longer to reach ready, which eats into the
  20-second foreground-service exemption window.
- **The two 256MB VMs were running throughout**, so 2GB is the headroom
  ALONGSIDE rung 3's and 3d's VMs, not instead of them. That makes it a
  conservative figure, not an optimistic one.
- **Nothing here is about the Terminal app's Debian VM, which was not
  running.** It holds 3.6GB when it is. On a phone where a user has also
  opened the Terminal app, none of these figures hold.
- Still `DEBUG_LEVEL_FULL`, still non-protected, still sample DICE values.

### Method notes

- **The control caught a bug in the probe, not in the platform, and it was
  the fifth rung running that this has paid for itself.** The first 256MB run
  logged `STEP7 RUNNING` and then nothing — which reads exactly like a VM
  that was accepted and died. The guest console said otherwise: `PENNY3C:
  notified ready, waiting for the host to connect`. The guest was fine. The
  fault was that `setCallback()` and the watchdog were handed the same
  single-thread executor, so the watchdog blocked the callback it was waiting
  for. Had that been the 2048MB run, it would have been written up as the
  ceiling, and it would have been wrong.
- **`adb logcat -G 64M` before anything.** A DEBUG_LEVEL_FULL guest console
  floods the ring buffer; the 4096MB and 6144MB runs' own log lines were
  evicted before they could be read, which looked like a probe that printed
  nothing. Three VMs' consoles at once is several hundred lines a second.
- **Parameterise the probe, do not rebuild between attempts.** Memory and CPU
  topology come in as intent extras, so all six runs above came off ONE APK.
  Rebuilding would have put the packaging, the signature and the APK path
  back in the frame on every attempt, and the whole point of a bisect is that
  one thing moves.
- **Count from the host kernel when the guest cannot tell you.** microdroid's
  console pipe is attached after the kernel's SMP bringup, so the guest's own
  CPU count is not in the log at all — `grep -i cpu` over 331 console lines
  returns nothing. Counting crosvm's vCPU threads with `ps -AT` on the host
  gives the same fact from a source that has no reason to flatter us.
- `Penny3cPayload.so` reused unchanged, not rebuilt — no round trip into the
  Debian guest for a compiler, and no new variable.
- `VmService` and `Penny3dService` were not touched. `penny3` was up
  throughout and is still up.

## 2026-09-15 — rung 3e-ii ANSWERED YES. The memory is real: 1792MB written, read back and held inside a 2048MB VM, twice. The ceiling is a zram live-lock at ~1870MB.

Pixel 6a, GrapheneOS 2026091001, Android 17 CP2A.260705.006, bootloader
LOCKED. `com.pennyspike.probe2a`, uid 10192, sideloaded, throwaway-signed.
Unlocked and in the foreground over adb. Build tools 37.0.0, Temurin 21.
Guest payload compiled by `gcc (Debian 14.2.0-19) 14.2.0` inside the phone's
own Debian guest, as every payload in this repo has been.

### The question, and why 3e-i did not answer it

3e-i proved microdroid will hand a sideloaded app a VM configured for 2048MB
and 8 vCPUs. It did not prove the guest can reach any of it. Linux hands out
address space eagerly and pages lazily, so `setMemoryBytes(2GB)` succeeding
and a guest HAVING 2GB are two different claims. 3e-i's own figures said so:
on its first 2048MB run the host appeared to surrender only ~660MB. Until
something inside the guest demands the memory, "the VM has 2GB" is a line in
a config file.

### What was built, and the one decision that would have invalidated the result

`probe2a/payload/penny3eii_payload.c`, a THIRD payload in the same APK
alongside 2d's and 3c's (`PENNY_PAYLOAD_3EII_SO` in `build.sh`), and
`Probe3eiiActivity`, its own component with its own VM name `penny3eii`.
`VmService`, `Penny3dService` and `Probe3eActivity` were not touched; all
three still ran during these measurements and `PENNY3D` logged exit code 43
mid-run, so rung 3d did not regress.

The guest mmaps 16MB at a time, fills every byte, reads every byte back and
checks it, and keeps it mapped. Three things in that sentence are
load-bearing and the third is the one that mattered:

- **mmap succeeding proves nothing.** Linux overcommits; a successful mmap of
  2GB on a phone with 300MB free is normal and reserves address space, not
  memory. So every page is written to.
- **Nothing is unmapped between chunks**, because the question is how much is
  held AT ONCE.
- **The fill is PSEUDO-RANDOM (xorshift64*), not zeros.** microdroid gives
  every guest a zram swap device sized to the guest's ENTIRE RAM — the 2GB
  guest's own console reads `zram0: detected capacity change from 0 to
  4076200` then `Adding 2038096k swap on /dev/block/zram0`. zram is
  COMPRESSED swap living in that same RAM. A payload filling pages with zeros
  or a repeated byte would have sailed to 2GB having proved nothing whatever,
  because the guest kernel would have compressed it away into a few megabytes.
  Incompressible bytes close that escape. **Written down before the run, not
  after.**

This is the first payload in this repo that allocates, which is why 3c's
could not be reused.

### Results

    VM      asked    written AND verified   guest time   outcome
    256MB    64MB     64MB                    271ms      clean (CONTROL)
    2048MB 1536MB   1536MB                   1410ms      clean
    2048MB 1792MB   1792MB                   2014ms      clean
    2048MB 1792MB   1792MB                   1664ms      clean, REPRODUCED
    2048MB 1920MB   1856MB then wedged          --       guest kernel
                                                         live-locked, VM
                                                         powered down at 94s

Guest exit code 44 on every clean run. `status=0` every time — no mmap was
ever refused and no read-back ever mismatched. Not one byte came back wrong
in 5.1GB of written-then-verified memory.

**Throughput 1.78-2.15 GB/s of write-plus-verify traffic.** That is RAM
speed. Anything travelling through zram would have been an order of magnitude
slower, so the number is itself evidence the pages were genuinely resident.

### The guest's own accounting, which is the corroboration that matters

A 2048MB config gives the guest `MemTotal: 2038100 kB` — about 57MB of
hypervisor overhead, consistent across all four 2GB runs.

Touching 1792MB moved guest `MemFree` from 1,930,160 kB to 90,036 kB. That is
a drop of 1,840,124 kB against 1,835,008 kB asked for: **one for one**. If
zram had absorbed any of it the drop would have been smaller than the amount
written, and it was not. The pseudo-random fill did its job.

### THE CEILING, AND IT IS NOT AN OOM KILL

At 1856MB held in a 2038MB guest, the guest kernel began swapping to zram and
deadlocked on itself:

    kswapd0: page allocation failure: order:0, mode:0xc00(GFP_NOIO)
      ... zs_malloc+0x1f8 / alloc_zspage+0x58 / __alloc_pages+0x220
      ... zram_submit_bio / __swap_writepage / shrink_folio_list / kswapd

**To free memory it had to allocate memory, and there was none.** With
incompressible pages zram cannot win that trade. The VM did not OOM-kill the
payload and did not return an error — it hung for 94 seconds and then powered
itself down through a normal shutdown (`init: Stopping 4 services by sending
SIGKILL`, `reboot: Power down`). On the host side that presents as a channel
that goes quiet: no reply, no exception, no `has died` line, nothing.

So the ceiling on a 2048MB VM is **between 1856MB and 1872MB**, and the
figure to build on is **1792MB — 88% of the guest's RAM**, measured twice.

### The host pays UP FRONT. This corrects rung 3e-i.

3e-i recorded that the host surrendered only ~660MB of the 2048MB granted and
named the gap as the open question. **That reading was wrong**, and it was
wrong because Android's low-memory killer was freeing memory at the same
moment the figure was taken, so the two movements partly cancelled. Measured
cleanly here, on the run where nothing was killed mid-reading:

    HOST before the VM exists    MemFree 2,257,952 kB   MemAvailable 2,879,040 kB
    HOST at onPayloadReady       MemFree   288,756 kB   MemAvailable   487,428 kB
    HOST after touching 1792MB   MemFree   137,088 kB   MemAvailable   388,736 kB

**~1.92GB left the host at VM CREATION, before the guest had touched a single
page.** The subsequent 1792MB of guest writes cost the host only another
~150MB, which is crosvm's own bookkeeping, not the guest's pages. Confirmed
on the repeat run, where the drop was split across RAM and Android's own swap
(MemFree -1.50GB, SwapFree -453MB, together ~1.95GB).

**crosvm does not hand out memory lazily. It takes it when the VM is created.**
That is better news than 3e-i's open question implied: there is no hidden gap
between what is granted and what is paid for, and nothing can quietly fail
later because the host over-promised.

### What it costs the phone, every time

Booting a 2048MB VM drives the low-memory killer on every single run. The
first run killed thirteen processes — including `com.android.virtualization.
terminal`, i.e. the Terminal app's 3.6GB Debian VM, which had been opened
fifteen minutes earlier to compile this very payload. Later runs killed five
or six: launcher, IME, Settings, media, contacts, cellbroadcast. Our own app
was never killed at 2048MB, which matches 3e-i.

### What this does NOT answer

- **Nothing RAN.** Writing and verifying 1.8GB is memory, not compute. There
  is no model, no runtime, no file, no threads — the payload is single
  threaded and used exactly one of the 8 vCPUs 3e-i measured. "The guest can
  hold 1.8GB" is not "the guest can run a model in 1.8GB".
- **Where a model FILE would live is still rung 3e-iii, and this run makes it
  worse, not better.** The guest's `/data` is `tmpfs` (3d's shutdown log:
  `init: Unknown /data fs type: tmpfs`) and zram has already claimed a swap
  device the size of the whole guest. A 1.5GB model file copied into the
  guest may cost 1.5GB of RAM before anything loads it, against a measured
  ceiling of 1792MB. NOT RUN.
- **The ceiling is with an EMPTY guest.** 1792MB left 88MB free in a guest
  running nothing but init and our payload. A real runtime eats into that,
  and the live-lock above is what waits at the bottom.
- **Residency was two seconds, not two hours.** The pages were held for the
  length of the verify pass. Nothing here is an endurance or a
  memory-pressure-over-time result.
- **Unlocked, foreground, over adb.** A 2GB VM at boot before first unlock is
  still untested, and 3e-i's ~4s extra boot time still eats into the
  20-second foreground-service window.
- Still `DEBUG_LEVEL_FULL`, still non-protected, still sample DICE values.

### Method notes

- **The control ran first, for the fifth rung running.** A 256MB VM told to
  take 64MB: 64MB written, verified, `status=0`, exit 44, in 271ms. That
  settled the third payload slot in `build.sh`, the new activity, the new
  wire message and the ELF packaging before any figure that mattered was
  measured.
- **The ELF was verified before packaging**, per the trap: `NEEDED` only
  `libvm_payload.so`, exactly one undefined symbol
  (`AVmPayload_notifyPayloadReady`), OS/ABI `UNIX - System V`, `LOAD` align
  `0x1000`. All four right first time; the flags in CLAUDE.md's glibc/bionic
  trap were copied verbatim and none had to be rediscovered.
- **Both figures are intent extras**, so the whole bisect — 64, 1536, 1792,
  1920 — ran off ONE build and one install. Memory was the only thing that
  moved.
- **The host sampled its own `/proc/meminfo` every 500ms throughout.** That
  is what caught the up-front allocation and corrected 3e-i; the guest cannot
  report it, because the guest only knows what it was able to take, never
  what the phone had to give up to supply it.
- **Opening the Terminal app cost one manual round trip and the compiler was
  already there** — `~/penny3c/libvm_payload.so`, the link stub from 3c, was
  reused, so nothing was downloaded and nothing was installed in the guest.

## 2026-09-15 — rung 3e-iii ANSWERED YES, and the answer is better than the question. A model file does NOT come out of the guest's RAM budget: microdroid will give a sideloaded app a real, persistent, encrypted ext4 disk, and a 1.5GB file on it costs zero permanent memory.

### The question

3e-ii proved 1792MB of a 2048MB VM is genuinely writable, read back and held.
That is the ceiling on everything the guest does. The fear was that a 1.5GB
model FILE would have to sit in RAM before anything loaded it — rung 3d's
shutdown log reads `init: Unknown /data fs type: tmpfs`, and tmpfs is a RAM
disk — in which case the file and the running model both come out of the same
1792MB and it does not fit. That would have closed the plan down.

### What was built

`payload/penny3eiii_payload.c`, the fourth payload in this APK, and
`Probe3eiiiActivity`, its own component with its own VM name `penny3eiii`.
`VmService`, `Penny3dService`, `Probe3eActivity` and `Probe3eiiActivity` were
not touched.

**The payload is a COMMAND SERVER, not a single-shot, and that decision is the
reason this rung finished in one afternoon.** There is no C compiler on the
Mac, so every payload rebuild costs a manual round trip into the phone's Debian
guest. A single-shot payload would have needed a rebuild per question. Instead
the host opens one vsock connection and sends a sequence of commands, and the
guest keeps its state between them:

    1 MOUNTS     print /proc/mounts and /proc/meminfo
    2 LISTDIR    print a directory
    3 FILEWRITE  write N MB of incompressible bytes to <path>
    4 TOUCH      3e-ii's anonymous mmap-and-fill loop, cumulative
    5 MMAPREAD   mmap <path> read-only and fault in every page

The plan is an intent extra (`--es plan "3,1536,/path;?4,1792,"`), steps
separated by `;`, each `cmd,mb,path`, and a step prefixed `?` is a PROBE whose
failure is a result rather than a fault. Nine separate experiments ran off ONE
payload build. Only the Java was rebuilt, which needs no phone.

### What was found, in the order it was found

**1. There is no writable filesystem in a default microdroid guest at all.**
Not "it is a RAM disk" — there is nothing to write to. The mount table, read by
the payload out of the guest's own `/proc/mounts`:

    /dev/block/dm-0  /         erofs  ro
    tmpfs            /data     tmpfs  rw,size=131072k
    tmpfs            /mnt      tmpfs  rw,mode=755,gid=1000
    zipfuse          /mnt/apk  fuse.zipfuse  ro
    tmpfs            /dev, /apex, /linkerconfig, /mnt/installer,
                     /mnt/androidwritable

`/data` IS tmpfs, confirming the 3d inference — and it is additionally capped
at **131072k, i.e. 128MB**, on a 256MB guest. But the payload cannot write
there regardless. Six locations were probed in one run and every one refused:

    /data/...                 errno 13  EACCES
    /mnt/...                  errno 13  EACCES
    /mnt/androidwritable/...  errno 13  EACCES
    /dev/...                  errno 13  EACCES
    /tmp/...                  errno  2  ENOENT   (does not exist)
    relative path             errno 30  EROFS    (cwd is the erofs root)

No SELinux denial was logged for any of them, so these are plain Unix
permission refusals: the payload does not run as root. `opendir /` also
returned EACCES, and `opendir /mnt/apk` returned errno 22 EINVAL — zipfuse
serves files but would not serve a directory listing to this payload.

**So "is the guest's writable storage RAM?" was the wrong question. There was
no writable storage to ask about.**

**2. The thing that was missing, found by reading the dex, not by guessing.**
`VirtualMachineConfig.Builder.setEncryptedStorageBytes(J)` is
`hiddenapi 0x0020 (SDK,TEST-API)` — the same flag `setApkPath`,
`setMemoryBytes` and `DEBUG_LEVEL_FULL` carry, so it is callable by an app in
the `app` domain. Read off the device's own `framework-virtualization.jar`
with `dexdump -d` before a line of code was written. **That method has now
been right 11 times out of 11.** It takes a **long**, like `setMemoryBytes`.

One extra line in the config, and the guest's mount table gains:

    /dev/block/mapper/crypt  /mnt/encryptedstore  ext4  rw,discard

A real ext4 filesystem on a dm-crypt device. Not tmpfs. And the payload can
write to it.

**3. It is a real disk, and the proof does not depend on believing a figure.**
A **256MB** guest was told to write a **384MB** file — one and a half times its
own total RAM. It succeeded. Guest MemFree oscillated rather than falling:

    wrote  64MB -> MemFree 63160 kB
    wrote 128MB -> MemFree 29132 kB
    wrote 192MB -> MemFree 31380 kB
    wrote 256MB -> MemFree 25048 kB
    wrote 320MB -> MemFree 31264 kB
    wrote 384MB -> MemFree 26132 kB

The kernel was writing back and reclaiming page cache as it went. 384MB in
2581ms is ~149 MB/s. 3e-ii measured this guest's RAM at 1.78-2.15 GB/s, so
this is an order of magnitude slower than memory and is a block device.

**4. THE NUMBER THE RUNG EXISTS TO PRODUCE. A 1.5GB model file costs nothing.**
2048MB VM, 2048MB encrypted storage:

    step                                    result   guest ms   status
    write 1536MB to /mnt/encryptedstore     1536MB      7321     0
    mmap the whole file, fault every page   1536MB       163     0
    touch+verify 1792MB anonymous           1792MB      1797     0

**1792MB is 3e-ii's exact ceiling, and it is unchanged with a 1.5GB file
present and mapped.** Guest MemFree went 280748 -> 84740 kB across the
anonymous touch: the kernel dropped 1.6GB of page cache to supply it, which is
precisely what clean file-backed pages are for and precisely what anonymous
pages cannot do. No live-lock, no OOM kill, no corruption.

**5. It PERSISTS across VM instances, and a cold read runs at 835 MB/s.**
The store is destroyed with the VM, so every figure above was a file written
moments earlier into a warm cache. `--ei keep 1` was added to skip
`vmm.delete()` and use `getOrCreate` instead, and the two halves were run as
separate VMs:

    run A   2048MB VM, fresh store, wrote 1536MB, exited
    run B   a NEW VM on the SAME store found the file, 1610612736 bytes

Run B's mmap faulted in all 1536MB from the disk in **1841ms = 835 MB/s**,
with `Cached` climbing 105460 -> 1547180 kB in lockstep with MemFree falling
1917328 -> 336180 kB. Then, with the whole file mapped and resident, it touched
and verified **1792MB of anonymous memory, status 0**. Second reproduction of
the combined ceiling, this time from cold.

### The answers, against what the rung asked for

- **Is the guest's writable storage RAM?** The default storage is RAM (`/data`
  is tmpfs, capped at 128MB) and is unreachable by the payload anyway. But
  that is not the end of it: **encrypted storage is not RAM.** It is
  host-backed ext4 on dm-crypt.
- **What does a 1.5GB file cost?** **Nothing permanent.** It occupies page
  cache while hot and the kernel returns every byte of it the moment anonymous
  memory is demanded. Measured twice.
- **Does the APK route avoid that cost?** Moot, and deliberately not run. The
  escape hatch was worth testing only if writable storage cost RAM. Encrypted
  storage is strictly better than the APK route on every axis: it is writable,
  it persists, it is encrypted, and it does not add its size to every install
  of the APK. `build.sh` gained the `PENNY_BLOB_MB` machinery to test the APK
  route and it was left in place unused — the packaging is proven and it costs
  nothing to keep the option.

### What this does NOT say

- **Nothing was RUN.** 1.5GB written, read back at 835 MB/s and mapped is a
  file, not a model. No inference, no runtime, no tokens. The largest
  unanswered thing in this repo is unchanged.
- **How the model gets INTO the store is untested and is a real gap.** The
  store is encrypted and keyed to the VM, so the host cannot write it — the
  guest must. 3c proved bytes cross inwards over vsock, but pushing 1.5GB that
  way has never been tried and nothing here measures it. That is now the
  obvious next question rather than an afterthought.
- **Persistence was measured across two VM instances minutes apart, not across
  a reboot.** Whether the store survives a power cycle is untested.
- 835 MB/s is one cold read of one file on an idle phone. No endurance, no
  contention, nothing on battery.
- Unlocked, in the foreground, over adb. The boot case is untested here.
- Still `DEBUG_LEVEL_FULL`, still non-protected, still sample DICE values.
  No attestation claim rests on any of it.
- The 128MB `/data` cap was read on a 256MB guest. Whether it scales with VM
  size was not checked, and does not matter now that it is not the route.

### Method notes

- **Nothing was downloaded.** The Debian guest was already up, gcc was already
  installed and `~/penny3c/libvm_payload.so` — 3c's link stub — was reused. The
  jar the dex flag was read from was already in the scratchpad from rung 2.
- **`-nostdlib` was left off the first compile** and it failed with
  `cannot find crti.o`. The guest has gcc but no libc development files, which
  is the whole reason these payloads use no C library.
- **The payload defines its own `memcpy` and `memset`.** gcc may turn a bounded
  copy loop into a call to either EVEN under `-ffreestanding`, and with
  `-nostdlib` that is an undefined symbol which surfaces at `dlopen` inside the
  guest where nothing useful is logged. Eight lines, and the attribute
  `optimize("no-tree-loop-distribute-patterns")` is not decoration — without it
  gcc rewrites the loop inside `memcpy` as a call to itself.
- **The four-check ELF verification passed first time** and is worth its
  seconds: `NEEDED libvm_payload.so` alone, exactly one undefined symbol
  (`AVmPayload_notifyPayloadReady`), OS/ABI `UNIX - System V`, LOAD align
  `0x1000`.
- **`getOrCreate` reuses the STORED config, and the APK path changes on every
  reinstall.** Cost one run: `FileNotFoundException: ENOENT` out of
  `VirtualMachineConfig.toVsConfig`, which reads as a missing VM and is not.
  Do not reinstall between two runs that share a store.
- **Probe steps that continue on failure earned their keep immediately.** Six
  write locations were ruled out in one 30-second run instead of six.
- No regression: rung 3d logged `exitCode=43` mid-run, `penny3` stayed up
  throughout, and there was not one `has died` line all session. The phone
  finished at MemFree 2272952 kB. The Terminal app's Debian VM was stopped by
  hand before the 2GB runs, as 3e-ii's results were also taken without it.

## 2026-09-15 — rungs 3e-iv, 3f, 3g and 3h DEFINED, none run. The four things worth asking this handset before rung 4, and the one that nearly went unasked.

Written before any of them is attempted, as 3d and 3e-iii were. The method
matters more than the result here: a log cannot tell you afterwards what you
decided to measure and why, and three times in this repo a hypothesis would
have been written up wrongly had the reasoning not been recorded first.

### Why these four and not others

The spike has measured, exhaustively, whether there is ROOM on this phone: 2GB
and 8 vCPUs given (3e-i), 1792MB genuinely usable (3e-ii), a 1.5GB model file
costing no permanent RAM (3e-iii). Every one of those was taken **unlocked, in
the foreground, over adb, on an idle phone with the Terminal app's Debian VM
deliberately shut down**, and **not one of them asked a CPU to compute
anything.**

So the gap is no longer "is there room". It is (a) does the room still exist on
a phone that is locked, in use, and has just been rebooted, and (b) is the
processor in that room any good. Those are the four below.

The handset goes back to Back Market and is replaced with a 7a, so anything
that needs THIS device needs doing before the swap. The protected-VM question
is deliberately NOT on this list: it needs the 7a, not this one, and one run of
the existing probe settles it.

### 3e-iv — does the encrypted store survive a reboot, and can it be read before first unlock?

Numbered into the 3e group because it is storage, not a new axis.

3e-iii proved the store survives the VM being destroyed and rebuilt, minutes
apart, on a running phone. No power cycle was tried.

**The second half is the one that nearly went unasked, and it is the more
important of the two.** This spike's entire value is that the app works before
anybody types the PIN. Rung 3 had to move the VM's directory to
device-encrypted storage (`/data/user_de/0/<pkg>`) for exactly that reason. If
the encrypted store's key is tied to the user's credential, then the model
cannot be read at boot — and 3d's and 3e-iii's results would apply only to a
phone somebody has already unlocked. That is a materially different product.
**Test the pre-unlock read explicitly; do not infer it from the file
existing.**

Method: write a known-length file with a known checksum; reboot; re-open from a
new VM with `--ei keep 1` and verify BOTH size and checksum, because a file
that reappears short is a different answer from one that is gone. Read it with
`userUnlocked=false`, the way 3d measured everything.

**Do not reinstall the APK between the two runs** — `getOrCreate` reuses the
stored config, the APK path changes on every install, and the failure reads as
a missing VM. That trap cost a run today and a reboot invites a rebuild.

DONE MEANS: yes/no it survives the power cycle, yes/no it is readable locked,
and the checksum either way.

### 3f — is the guest CPU real?

**The largest measurable unknown left in this repo.** 3e-i was given 8 vCPUs;
3e-ii and 3e-iii moved gigabytes; every payload here is single-threaded and
does nothing but copy bytes. "Can a useful model run in 1792MB" splits into
*is there room* — answered — and *is the processor any good* — never asked.

1. **Single-core throughput**, integer and floating point, a fixed loop
   reporting milliseconds. The comparison is the same C compiled by the same
   gcc and run in the phone's Debian guest: same silicon, same day, a
   known-good Linux. **It is not a bare-metal control and must not be written
   up as one** — it is a sanity number that would catch a guest running at a
   tenth of expected speed.
2. **Scaling 1 -> 2 -> 4 -> 8 threads inside microdroid.** Needs no host
   control at all, and is arguably the more decision-relevant half: if eight
   vCPUs do not go roughly eight times faster, 3e-i's `CPU_TOPOLOGY_MATCH_HOST`
   result is a line in a config file rather than eight usable cores.
   **The hard part is threads with no C library** — `clone` by hand, a stack
   per thread from `mmap`, no pthreads. Expect that to be the whole difficulty,
   and run the single-core half FIRST so a failure there is not misread as a
   threading bug.

DONE MEANS: a single-core figure with its comparison, and a 1/2/4/8 curve.

### 3g — does the 2GB VM survive a real phone?

Two halves, both commercial risks rather than laboratory ones.

**3g-i. Will a 2GB VM start at boot, locked, with nobody in the room?** 3d
proved 256MB VMs do, twice. A 2048MB VM takes ~4s longer to reach
`onPayloadReady` (3e-i: +4.3s against +0.7s at 256MB) and the boot broadcast's
foreground-service exemption is **20 seconds**. The margin is real but has
never been measured, and a cold `dex2oat` on the first boot after an update
eats into it. **If a 2GB VM cannot make that window, the unattended wake story
only works for VMs too small to hold a model — and those two results have never
been in the same room.** Needs its OWN service, COPIED not edited, exactly as
`Penny3dService` was copied from `VmService`. Control: the same service at
256MB on the same boot, so "the service shape is wrong" and "2GB is too slow"
stay separable.

**3g-ii. What happens on a phone somebody is using?** Booting a 2048MB VM drove
the low-memory killer every time and the first 3e-ii run killed thirteen
processes, including the Debian VM opened fifteen minutes earlier to compile
that very payload. Our own app was never killed at 2048MB — on an idle phone
with nothing else running. Open the camera, a browser and several apps by hand,
then start the VM. Control: today's idle figures, already recorded.

DONE MEANS: 3g-i, yes/no plus time-to-ready and what died; 3g-ii, yes/no our
app survives plus the casualty list.

### 3h — can a gigabyte be pushed into the guest?

The question 3e-iii opened and could not answer. The encrypted store is keyed
to the VM, so the host cannot write it — the guest must — and the only inbound
channel is vsock, which has carried **32,000 bytes, once, in a single shot**
(3c). Nothing here measures sustained transfer in either direction.

Method: the host generates incompressible bytes and streams them in chunks; the
guest writes them straight to `/mnt/encryptedstore` and checksums as it goes,
so truncation and corruption cannot be confused. Sample memory on BOTH sides
throughout — **the interesting failure is the guest buffering the whole
transfer in RAM and hitting 3e-ii's zram live-lock, which presents as a hang
with no error and no `has died` line.** Control the size upwards: 32KB (3c's
proven figure), then 64MB, then 1536MB.

**No model and no download needed** — the bytes are generated on the device.

DONE MEANS: a throughput figure and yes/no on 1.5GB arriving intact.

### Sequencing, and one efficiency worth taking

3e-iv, 3g-i and 3g-ii are reboot-and-device-state work needing no new guest
code, so they belong in one sitting. 3f and 3h both need a payload rebuild,
which costs a manual trip into the phone's Debian guest for gcc — so **give
them ONE payload between them, a command server in the shape 3e-iii proved**,
and pay that cost once. That shape answered nine experiments off one build
today. **Add a fifth payload; never edit the four already in the APK.**

### Deliberately NOT on this list

- **The endurance soak.** The oldest open question in the project and still
  real, but it is hours of wall clock for a result that changes no decision
  this month, and a measured ~1.5-second self-restart already covers most of
  what it was protecting against. Worth doing; not worth doing first.
- **Protected VMs.** Needs the 7a, not this handset. One run of the existing
  `getCapabilities()` probe settles whether the cause is the 6a's silicon,
  GrapheneOS or Android 17.
- **Anything requiring a real model.** Not available, and three of the four
  above were chosen precisely because they do not need one.

## 2026-09-15 — rung 3e-iv, first half ANSWERED YES: the encrypted store survives a power cycle intact. Second half UNANSWERABLE over adb, and that is a method finding worth more than the result.

Two questions were defined for 3e-iv and only one of them could be asked with
the equipment in the APK. The half that was asked came back clean. The half
that could not be asked turns out to be blocked by something with nothing to
do with encryption, virtualisation or storage — the phone's USB port.

**Answer 1: does the encrypted store survive a REBOOT? YES, exactly.**
**Answer 2: can it be read BEFORE first unlock? STILL UNANSWERED.** It was
read 34 seconds AFTER first unlock, and that is not the same question. See
"Why the pre-unlock half could not be asked" below — it is not a failure of
nerve, it is a closed door, and the route around it is now known.

### What was already on disk before this run

`model.bin`, 1610612736 bytes, written by rung 3e-iii into penny3eiii's
encrypted store earlier the same day. 3e-iii had proved the file survived the
VM being destroyed and rebuilt minutes apart on a running phone. It had never
seen a power cycle, because none was tried.

The APK was deliberately NOT rebuilt or reinstalled between the write and this
read — `getOrCreate` reuses the VM's stored config and the APK path changes on
every install, which is the trap that cost a run on 15 Sept. `pm path`
confirmed the same path either side of the reboot:

    /data/app/~~uO7OYJk2jGyYQMs7z9Vvgg==/com.pennyspike.probe2a-WVgHUDfQnA89rlKzidwLFw==/base.apk

### The run

`adb reboot` at 14:08:49. Boot completed; the phone was left at the lock
screen. USB came back only after the PIN was typed at 14:17:23 — see below.
Then, with no rebuild and no reinstall:

    adb shell am start -n com.pennyspike.probe2a/.Probe3eiiiActivity \
        --ei mem 2048 --ei storage 2048 --ei keep 1 \
        --es plan "?5,0,/mnt/encryptedstore/model.bin"

`--ei keep 1` is what makes this askable at all: it skips `vmm.delete()` and
uses `getOrCreate`, so the run attaches to the store the previous boot's VM
left behind rather than making a fresh one.

### Result: the file is there, whole, and reads from cold at ~598 MB/s

    guest console (cid 2050), which the host process does not write to:
      PENNY3EIII: /mnt/encryptedstore/model.bin is 1610612736 bytes,
                  mmaping it read-only
      PENNY3EIII: DONE cmd 5 status 0 result 1536 MB,
                  guest MemFree 1916244 -> 334308 kB, 2570 ms

    host:
      REPLY step 1 cmd=5 status=0 (ok) result=1536MB
        guestMemFree 1916244 -> 334308 kB (delta 1581936 kB = 1544 MB)
        guestMs=2570 extra=0 hostWallMs=2571
      CB onPayloadFinished exitCode=45

1610612736 bytes is the figure 3e-iii wrote, to the byte. Every one of the
1536MB faulted in — the guest walked the mapping on a 4096-byte stride and
the kernel supplied every page, reporting progress every 64MB with guest
`Cached` climbing 105392 -> 1547132 kB in lockstep. Status 0, and exit code
45, which is this payload's own completion signal.

**It was a genuinely cold read on BOTH sides and that is the part worth
keeping.** The phone had rebooted eight minutes earlier, so the host's page
cache held nothing of this file; host `Cached` went 1120240 -> 3348232 kB
across the read, ~2.2GB entering cache for a 1.5GB file. The VM was newly
created, so the guest's cache was empty too — its `Cached` started at
105392 kB. The bytes came off UFS.

2570ms for 1536MB is ~598 MB/s, against 3e-iii's 835 MB/s for the same file
from the same store. Both are single cold reads; this one was taken on a
phone eight minutes into a boot and still settling, with ~20 processes being
killed around it. Treat the pair as "several hundred MB/s", not as a
regression.

### What this does NOT say, and one limit is sharper than it looks

**Content was not verified.** The payload checks the file's SIZE and that
every page faults in. It does not checksum the bytes. A store that returned
1610612736 bytes of zeroes would have produced this exact log. Size plus a
complete fault-in is strong — a truncated or absent file is definitively
ruled out, and 1.5GB of pages did come off the disk — but it is not the same
as proving the contents. **3e-iii's original method note asked for both size
and checksum and only size was delivered.** Verifying content needs a new
guest command, so fold a verify-file command into the shared 3f/3h payload
rather than making a trip for it alone.

Also: persistence is now measured across one power cycle, not many, and not
across an OS update, and the store was never unmounted uncleanly — the VM
shut down normally each time.

### Why the pre-unlock half could not be asked, and it is not about storage

The plan was to run that same `am start` at the lock screen with
`userUnlocked=false`. It cannot be done, for a reason that outranks the one
that was anticipated.

The anticipated obstacle was real but secondary: **`Probe3eiiiActivity` is not
`directBootAware`**, and neither is the `<application>` tag. Before first
unlock the OS hides every non-direct-boot-aware component, so `am start` on it
would have been refused. (The VM half would have been fine — 3e-iii does use
`createDeviceProtectedStorageContext()`.)

The real obstacle is that the command could never have reached the phone at
all. **GrapheneOS sets the USB-C port to charging-only while locked.** After
`adb reboot`, `adb get-state` was polled every 5 seconds for two minutes and
returned "no devices/emulators found" every time, and
`system_profiler SPUSBDataType` on the Mac counted **zero** USB devices — not
an adb problem, macOS could not see the phone on the bus at all. The port came
alive the instant the PIN was typed. This trap is already in CLAUDE.md from
rung 3 on 14 Sept; what is new is the consequence for method.

**CONSEQUENCE, and it generalises well beyond 3e-iv: nothing on this device
can be measured pre-unlock by sending it a command. Any pre-unlock question
must be asked by a component that starts ITSELF at boot and writes its answer
to logcat, to be read back after an unlock.** That is exactly the shape rungs
3, 3b and 3d used, and it is now clear that shape was forced rather than
merely convenient.

So the pre-unlock read needs a `directBootAware` service started from
`LOCKED_BOOT_COMPLETED`, which needs a rebuild — and a rebuild changes the APK
path, which permanently strands penny3eiii's stored config and the store with
it. The new service must therefore own its own VM name, write its own file,
and take its own reboot. **That is a two-reboot job with a build in the middle,
and it is worth doing: if the store's key is tied to the user's credential, no
model can be read at boot and every unattended result in this repo applies only
to a phone somebody has already unlocked.** Written up as rung 3e-v.

### Two free reproductions on the same boot, neither asked for

Neither was part of the plan; both were read out of the same logcat.

**Rung 3 reproduced for the SEVENTH time.** `penny3` came up unattended, and
`vm list` showed `requesterUid: 10192`, cid 2049, pid 1925 — new cid, new pid,
same result.

**Rung 3d reproduced for the THIRD time, on a boot nobody set up for it**:

    14:09:21.783  BootReceiver: LOCKED_BOOT_COMPLETED  sinceBoot=12726ms
    14:09:21.812  STEP2 MANAGE_VIRTUAL_MACHINE=GRANTED RECORD_AUDIO=GRANTED
                        userUnlocked=false
    14:09:23.685  CB onPayloadReady sinceBoot=14628ms
    14:09:24.807  AUDIO: startedAt=14632ms samples=16000 peak=2951
                        rms=1109.19 nonZero=14514 (90.7%)
    14:09:24.876  [audio] guest replied len=32000 fnv1a=4362a8d0
    14:09:24.884  [audio] hashMatch=true echoMatch=true roundTrip=21ms
    14:09:25.907  CB onPayloadFinished exitCode=43

Real audio, captured at the lock screen with `userUnlocked=false`, delivered
into the guest and echoed back with a matching FNV-1a, guest exit 43. First
unlock was at **14:17:23**, which is **494 seconds** after power-on — so the
whole chain finished with more than eight minutes of margin before a human
touched the phone. That is by far the largest margin yet recorded; 3d's own
runs had 134s and 101s.

### The low-memory killer, third sighting, and it is getting predictable

Creating the 2048MB VM again drove the killer, as it has every time. ~20
processes went in the seconds after `run()`:

    .ShannonImsService, com.shannon.rcsservice, app.grapheneos.gmscompat,
    com.android.permissioncontroller, com.android.keychain,
    app.seamlessupdate.client, android.process.acore, com.android.traceur,
    app.grapheneos.carrierconfig2, com.google.euiccpixel,
    com.android.localtransport, .adservices, android.process.media,
    com.android.imsserviceentitlement, com.android.angle,
    com.stevesoltys.seedvault, app.grapheneos.backup.contacts,
    com.android.DeviceAsWebcam, com.android.devicediagnostics,
    com.android.printspooler

**Every single one is `cch` — cached, empty.** The kill reasons run
`cch +95 CEM` down to `cch +45 CEM`, i.e. the killer worked from the cheapest
tier down and never had to touch anything a user would notice. Our own app
survived, and so did `penny3` throughout. This is the cheap end of the same
behaviour 3e-ii saw when it killed thirteen. **It is still not evidence about
rung 3g-ii**, which asks what happens when the processes in the way are a
camera and a browser somebody is actually using, not empty cached ones.

### Versions

GrapheneOS 2026091001, Android 17 (CP2A.260705.006, patch 2026-09-01),
bootloader bluejay-17.0-15199431 locked, verifiedbootstate=yellow.
Pixel 6a (bluejay), 6GB. No rebuild — same APK throughout, build-tools
37.0.0, Temurin 21.0.12.1, platform-tools 37.0.1.

### Verdict

**3e-iv first half: YES.** A 1.5GB file written by a sideloaded app's guest
into microdroid's encrypted store is still there, at exactly the right size
and fully readable, after a full power cycle — and reads back from cold at
several hundred MB/s. A model shipped into that store would not have to be
re-fetched at every boot.

**3e-iv second half: NOT ANSWERED, and now known to be unaskable the way it
was written.** Carried forward as rung 3e-v, which needs its own
`directBootAware` service, its own VM, its own store and two reboots.

## 2026-09-15 — rungs 3f and 3h BOTH ANSWERED YES, off one payload. The guest CPU is real and at parity with a known-good Linux on the same silicon; 1.5GB goes in over vsock in 15 seconds, intact.

Two rungs, one guest payload, one trip into the Debian guest for gcc. The
fifth `.so` in the APK; the four before it were not touched and still run.

### Why they shared a build

There is no C compiler on the Mac and there is not going to be one — the NDK
is a 975MB download over a phone tether. So every payload rebuild costs a
manual round trip: Matt opens the Terminal app by hand, a port is forwarded,
the file is compiled inside the Debian guest on the phone and copied back.
3e-iii proved the way to pay that once — make the payload a COMMAND SERVER
rather than a single-shot, so the host connects once and sends a sequence of
commands down the same socket while the process keeps its state between them.
That shape answered nine experiments off one build there. Here it answered two
whole rungs, a loose end, and four unplanned re-runs when a number turned out
to be measuring the wrong thing.

`payload/penny3f_payload.c`, seven commands:

    1 INFO          dump the guest's /proc/cpuinfo, /proc/mounts, /proc/meminfo
    2 CPU_INT       integer kernel, one thread, arg = millions of iterations
    3 CPU_FP        floating-point kernel, one thread
    4 THREADS_INT   arg = iterations EACH, arg2 = thread count
    5 THREADS_FP    as above, floating point
    6 STREAM        arg = MB, arg2 = plus KB, path = where the guest writes
    7 VERIFY        read a file back and checksum it

### THE CONTROL IS THE SAME FILE, and that is the part worth keeping

3f's specified control was "the same C compiled by the same gcc and run in the
phone's Debian guest". That is usually a promise about discipline. Here it is
literal: **one source file builds both binaries.**

    (default)        -> Penny3fPayload.so, a microdroid payload
    -DPENNY_CONTROL  -> penny3f_control, a static Debian executable

Same source, same gcc 14.2.0, same `-O1`, same flags, and — because the file
has no C library in EITHER build — the same raw aarch64 syscalls. The syscall
ABI belongs to the kernel and is identical above glibc, above bionic and above
nothing at all, which is the same reason 2d's payload talks to the kernel
directly. Nothing differs between the two binaries except the operating system
underneath them.

    gcc -shared -fPIC -O1 -nostdlib -ffreestanding -fno-builtin \
        -fno-stack-protector -Wl,-z,max-page-size=4096 \
        -Wl,--hash-style=sysv \
        -o Penny3fPayload.so penny3f_payload.c -L. -lvm_payload

    gcc -O1 -fPIC -no-pie -static -nostdlib -ffreestanding -fno-builtin \
        -fno-stack-protector -DPENNY_CONTROL \
        -o penny3f_control penny3f_payload.c

It is **NOT a bare-metal control and must never be written up as one.** Debian
is itself a guest in a VM the Terminal app owns. It is a sanity number, and its
job was to catch a microdroid guest running at a tenth of expected speed.

All four pre-packaging ELF checks passed first time: `NEEDED libvm_payload.so`
and nothing else, exactly one undefined dynamic symbol
(`AVmPayload_notifyPayloadReady`), OS/ABI `UNIX - System V`, `LOAD` align
`0x1000`.

### THREADS WITH NO C LIBRARY — the part budgeted as the whole difficulty

It was the hard part and it worked first time, which is worth recording
because the budget said otherwise.

There is no pthreads and no libc to hold one. A thread is made by hand: mmap a
stack, `clone()` into it, join by polling a shared flag. The trampoline cannot
be C — the child returns from the syscall on a brand new stack with no return
address on it, so a C function would `ret` into nothing. Eighteen instructions
of assembly, verified by disassembling the object on the Mac before the file
ever went near the phone:

    penny_clone_thread:   x0=fn x1=arg x2=stack_top x3=flags
        mov x9, x0 / mov x10, x1        fn and arg survive the svc because the
        mov x0, x3 / mov x1, x2         aarch64 syscall ABI preserves every
        mov x2, xzr / x3, xzr / x4, xzr register but x0 — the same trick
        mov x8, #220 ; svc #0           glibc's own aarch64 clone uses
        cbz x0, 1f ; ret                parent: returns tid or -errno
    1:  mov x0, x10 ; blr x9            child: straight into the work
        mov x8, #93 ; svc #0            then exit(2) — this thread, not the group

Three things that would each have cost a round trip if got wrong:

- **aarch64 takes clone's arguments in CLONE_BACKWARDS order** — flags, stack,
  parent_tid, TLS, child_tid. That is NOT the x86-64 order. Swapping the last
  two hands the kernel a garbage TLS pointer.
- **No CLONE_SETTLS.** The children inherit the parent's `tpidr_el0` and never
  touch thread-local storage, because a binary with no libc and
  `-fno-stack-protector` has none. If that ever stops being true this is where
  it breaks.
- **Stacks are allocated and never freed.** A child sets its done flag and then
  calls exit; unmapping its stack in that window is a race worth nobody's time.
  Eight stacks of 1MB in a 2GB guest.
- **`dmb ish` before the done flag.** aarch64 is weakly ordered, so `volatile`
  constrains the compiler and not the processor. Without the barrier the flag
  can become visible before the timings it is announcing.

**microdroid did not refuse `clone`.** That was a live risk — a payload
sandbox blocking it would have been a real finding — and the errno path was
written to report it rather than hang. It was never taken.

### 3f, half one: single core

200M iterations, three runs each, same binary both sides, same afternoon.

    INT 200M iters      microdroid      Debian control
    run 1               531 ms          582 ms
    run 2               397 ms          463 ms
    run 3               366 ms          411 ms

    FP 200M iters       microdroid      Debian control
    run 1               444 ms          438 ms
    run 2               447 ms          443 ms
    run 3               437 ms          434 ms

**The guest is at parity, and on the integer kernel its best run was FASTER
than Debian's** (366ms against 411ms). Both spreads are scheduler placement,
not measurement error — see the core-identity finding below.

**A corroboration nobody planned.** Both kernels are deterministic and the
payload reports the final value. microdroid and Debian returned
**bit-identical** results:

    INT sink   0xae0c3710f848e024     both
    FP sink    0xc316fb657f0b8495     both

Same arithmetic, to the last bit, in two different operating systems. That is
not a timing claim — it is proof the same code ran, which no millisecond figure
could give on its own.

### The guest's own view of its CPUs, which the console could not give

CLAUDE.md records that the guest's CPU count is NOT in the guest console:
microdroid attaches the console pipe after the kernel's SMP bringup, so
`grep -i cpu` over the whole console returns nothing. **Reading
`/proc/cpuinfo` from inside the payload solves that**, and it returns more than
a count.

    guest processors 0..7   parts: d44 d05 d0b d0b d0b d0b d05 d05
    host  processors 0..7   parts: d05 d05 d05 d05 d0b d0b d44 d44

`0xd44` is Cortex-X1, `0xd0b` is Cortex-A76, `0xd05` is Cortex-A55. The guest
sees **eight CPUs with real physical core identities read off the silicon** —
not a uniform synthetic CPU.

**But the mixes do not match, and the reason matters.** The host is the real
Tensor: 2 X1 + 2 A76 + 4 A55. The guest reported 1 X1 + 4 A76 + 3 A55. Each
guest vCPU is an unpinned host thread, and the guest kernel reads `MIDR_EL1` on
each vCPU once at boot — so the identity recorded is whichever physical core
that thread happened to be sitting on at that instant. **"8 vCPUs" is eight
unpinned threads on a heterogeneous host, not a topology.** Nothing in the
guest can pin them.

### 3f, half two: scaling 1 -> 2 -> 4 -> 8

Every thread runs the SAME iteration count, so ideal hardware returns the same
wall time whatever the thread count, and the curve is read as work per
millisecond rather than as a speed-up of a fixed job.

    INT, 200M iters PER THREAD        microdroid              Debian control
    threads   wall ms  M iters/sec    wall ms  M iters/sec
    1         445      449            585      342
    2         540      740            480      833
    4         583      1372           558      1434
    8         855      1871           849      1884
    8 again   905      1767
    8 again   838      1909

**At eight threads the two environments are within 1%: 1871 and 1909 against
Debian's 1884 M iterations/second.** Eight threads is about **4.2x** one
thread.

**That is not a shortfall and 8x was never on the table.** Four of the eight
cores are Cortex-A55s at roughly a third of the throughput of an X1. The
per-thread spread the payload logs is that heterogeneity made visible rather
than inferred — from the guest's own console, at 8 threads:

    INT  597 604 667 722 730 758 829 836 ms     ratio 1.40
    FP   606 768 789 881 889 901 908 949 ms     ratio 1.57

Eight threads with eight near-identical times would have been the surprising
result — it would have meant `CPU_TOPOLOGY_MATCH_HOST` was a number in a config
file rather than eight usable cores.

### 3h: the size ladder

`setEncryptedStorageBytes` for somewhere to put it — 3e-iii's finding, the only
writable filesystem a microdroid guest has. The host generates incompressible
bytes and streams them as length-prefixed chunks; the guest writes each chunk
straight through to `/mnt/encryptedstore` and checksums as it goes, so
truncation and corruption cannot be confused. Then VERIFY reads the file back
off the disk and checksums it again — three independent hashes of the same
bytes.

    size              guest ms   guest MB/s   INTACT
    32,768 B                 4   --           yes
    67,108,864 B          1479   43           yes
    1,610,612,736 B      15401   99           yes

    ck64  32KB      0x359e1ca1fc3a1672
    ck64  64MB      0x757b795dd5138044
    ck64  1536MB    0x1695ce2a1dce440a

Every one of those matched at all three points: what the host sent, what the
guest received, and what came back off the disk afterwards. Read-back from the
store ran at 2206 MB/s warm.

**1,610,612,736 bytes is exactly the figure 3e-iii wrote and 3e-iv read back
after a reboot.** It is the same 1536MB, arriving by a different route.

**The checksum here is NOT 3c's and its numbers must never be compared with
3c's or 3d's.** 3c hashed byte-at-a-time, which is a serial multiply chain of
roughly four cycles a byte — several seconds over 1.5GB at each end, charged
straight to the throughput figure. This one is FNV-1a over 64-bit words, same
construction, eight times fewer rounds, still order-sensitive.

### THE CONTROL THAT MATTERS: 1536MB into a 256MB guest

The interesting way 3h fails is the guest buffering the transfer in RAM and
hitting 3e-ii's live-lock — which presents as a hang with no exception, no
`has died` line and no reply. The payload holds exactly one 1MB chunk in
`.bss`, so it cannot buffer by accident. The control proves it does not.

**A 256MB guest received 1536MB — six times its own total RAM — intact, in
20863ms (73 MB/s), same checksum.** And its memory did not fall:

    received   64 MB   MemFree 60780 kB   Cached 104460 kB
    received  448 MB   MemFree 29148 kB   Cached 122772 kB
    received  832 MB   MemFree 42032 kB   Cached 107232 kB
    received 1216 MB   MemFree 46540 kB   Cached 118948 kB

MemFree **oscillates** and Cached stays near 110MB. The kernel writes back and
reclaims continuously. Compare the 2048MB guest, where the kernel had room not
to bother and simply let page cache grow to 1.68GB — same result, lazier route.

Read-back in the 256MB guest ran at 548 MB/s, off the disk rather than out of
cache, because there was no cache to hold it.

### THE TRAP THIS RUN PAID FOR: a throughput figure that measured the probe

**The first 1.5GB run reported 26 MB/s and it was wrong** — not wrong about the
bytes, which arrived intact, but wrong about what was being measured. 58
seconds for 1.5GB, and the guest's own disk had done 210 MB/s in 3e-iii, so the
disk was ruled out immediately. The channel was the obvious suspect.

It was neither. Splitting the host-side timer into "generate and hash" against
"write to the socket", which cost a Java-only rebuild and **no trip to the
phone**, put it beyond argument:

    256MB, first generator     generate+hash 7977 ms (32 MB/s)
                               socket writes 1660 ms (154 MB/s)

The bottleneck was this repo's own Java, shifting each 64-bit word out a byte
at a time and then re-reading the whole buffer to hash it. Rewritten as one
pass — hash the word where it is generated, store it with a LITTLE_ENDIAN
`ByteBuffer.putLong` — and:

    256MB, one-pass generator  generate+hash 1362 ms (187 MB/s)
                               socket writes  704 ms (363 MB/s)
    1536MB                     generate+hash 8815 ms (174 MB/s)
                               socket writes 5717 ms (268 MB/s)

**The rewrite produced the identical checksum on every size**, which is the
proof it changed the speed and not the bytes.

**The generalisable rule: when a transfer figure is disappointing, time the
generator before blaming the channel.** A probe that manufactures its own data
is measuring itself unless it is instrumented to say otherwise, and the cost of
finding that out here was two minutes because it needed no compiler.

**The honest headline is therefore the GUEST's figure, not the host's** — 15401
ms for 1,610,612,736 bytes, **99 MB/s including fsync**, because that one is
bounded by receiving and committing rather than by manufacturing. The channel
alone sustained 268 MB/s.

### The loose end from 3e-iv, half closed

3e-iv verified the stored file's SIZE and not its CONTENT, and asked for a
verify-file command to be folded into this payload. **It is in, and it was
exercised on files up to 1.5GB**: CMD_VERIFY reads a file back and returns its
checksum, and every stream in this session was verified that way.

**What that does NOT close: 3e-iv's own file is gone.** The reinstall this
build required strands `penny3eiii`'s stored config and its store with it,
exactly as the `getOrCreate` trap predicts, and CLAUDE.md authorised that
because 3e-iv's read was done. So the method gap is closed and the specific
question — does a file survive a reboot with its CONTENT intact, not merely its
size — is now cheaply askable and **has not been asked.** It needs one run with
`--ei keep 1` to write and checksum, a reboot, and one more with `--ei keep 1`
to read and compare.

### What these two rungs do NOT say

- **Nothing here is a model.** A dependency-chain benchmark is not inference.
  It says the processor issues instructions at the rate a real Cortex issues
  them; it says nothing about memory bandwidth under a real working set, cache
  behaviour, NEON or dot-product throughput, or what a quantised model would
  actually do. **"The CPU is real" is not "a model will run well."**
- **No NEON, no SVE, no matmul.** Both kernels are scalar and single-issue by
  design, because the point was to catch a fake CPU, not to profile one. The
  features line says the guest has `asimd`, `asimddp` and `fphp`; nothing here
  used them.
- **Single-thread figures are a lottery.** The host scheduler may put a thread
  on an X1 or an A55 and nothing in the guest can pin it. Three runs each, and
  the spread (531/397/366) is that lottery, not noise in the timer.
- **The Debian control is a VM too.** It is a sanity number, not bare metal.
- **The 4.2x scaling is on an idle phone.** Rung 3g-ii's question — what
  happens when the cores are already busy with something a person is using — is
  untouched by this.
- **1.5GB in 15 seconds is one transfer, once, on an idle unlocked phone over
  adb.** No streaming under memory pressure, no interrupted-and-resumed
  transfer, no second reproduction of the 1536MB case at the fast generator
  (the slow-generator run is a second reproduction of the RESULT, not of the
  figure).
- **Where the bytes come FROM is still unanswered.** This pushed bytes the host
  manufactured. A real model arrives over a network, and nothing here measures
  that, or where it is staged on the host, or what it costs.
- Unlocked, foreground, over adb throughout. Still `DEBUG_LEVEL_FULL`, still
  non-protected, still sample DICE values, so no attestation claim rests on
  any of it.

### Nothing regressed

`logcat | grep "has died"` returned **zero** lines across the whole session,
including three separate 2048MB VMs — better than 3e-ii's thirteen and 3e-iv's
twenty, because the Terminal app's Debian VM was up and holding its memory the
whole time rather than being killed and restarted. Our own app was never
killed.

`VmService` was not touched and `penny3` came back on its own after each
reinstall killed the process — START_STICKY doing what rung 3 measured, at cid
2078, `requesterUid: 10192`. The Terminal app's `debian` sat alongside it at
`requesterUid: 10179`, cid 2051. Two owners, enumerated together, as in rung 1.

`Probe3eActivity`, `Probe3eiiActivity`, `Probe3eiiiActivity` and
`Penny3dService` were not touched. The four earlier payloads are still in the
APK, still Stored, still page-aligned.

### Versions

GrapheneOS 2026091001, Android 17 (CP2A.260705.006, patch 2026-09-01),
bootloader bluejay-17.0-15199431 locked, verifiedbootstate=yellow.
Pixel 6a (bluejay), 6GB. Debian 13.7 trixie, kernel
6.12.92-android16-6-g4e585dd7f3b7-ab16266940-4k, gcc 14.2.0 (Debian 14.2.0-19).
build-tools 37.0.0, Temurin 21.0.12.1, platform-tools 37.0.1.
`Penny3fPayload.so` 15,392 bytes, sha256
1314006353fd7fa950322e0e00fb58f631ee7459ffa58fd98dac460b0b2c7591.

### Verdict

**3f: YES.** The guest CPU is real. Eight vCPUs carrying genuine physical core
identities, single-core at parity with a known-good Linux on the same silicon
on the same afternoon — bit-identical results from both — and 4.2x aggregate
scaling across eight threads, within 1% of what the same binary achieves in
Debian. `CPU_TOPOLOGY_MATCH_HOST` is eight usable cores, not a number in a
config file. Hand-rolled threads with no C library work inside microdroid and
`clone` is not refused.

**3h: YES.** 1,610,612,736 bytes crossed into the guest over vsock and landed
on the encrypted store intact, verified at three independent points, in 15.4
seconds — 99 MB/s end to end including fsync, with the channel alone sustaining
268 MB/s. And a guest with 256MB of RAM took the same gigabyte and a half
without buffering a byte of it, which is the result that says this scales down
rather than merely working once at a comfortable size.

**The delivery route for a model is now measured end to end: it can be pushed
in, it lands on a real disk, it costs no permanent RAM, and it survives a
reboot.** What has never been measured is a model doing anything once it is
there.

## 2026-09-15 — rungs 3e-v and 3g-i BOTH ANSWERED YES, off one build and two reboots. The encrypted store opens before first unlock, and a 2GB VM wakes unattended. The last two questions this handset could answer are closed.

Two rungs, no guest C, one Java-only build, two power cycles. Both answered on
the same boots, each with its own control, and four earlier rungs reproduced
free alongside them.

    3e-v   is the encrypted store readable BEFORE first unlock?   YES
    3g-i   will a 2048MB VM start at boot, locked, unattended?    YES

3e-v was the one that could still have closed the plan down. If the store's
dm-crypt key had been tied to the user's credential, no model could be read at
boot and every unattended result in this repo — rung 3's wake, 3b's microphone,
3d's whole voice path — would have applied only to a phone somebody had already
unlocked once. It is not tied to the credential.

### Rung 3e-v — the encrypted store opens before first unlock, with the right BYTES

A `directBootAware` service started itself from `LOCKED_BOOT_COMPLETED`, created
a VM with an encrypted store attached, opened a file an earlier run had written
to that store, read all 67,108,864 bytes of it and checksummed them — **14.4
seconds after power-on, with `userUnlocked=false` and the PIN not typed for
another 197.6 seconds.**

    sinceBoot  12,026 ms   onStartCommand, userUnlocked=false
    sinceBoot  12,059 ms   startForeground(SPECIAL_USE) accepted
    sinceBoot  14,279 ms   onPayloadReady, userUnlocked=false
    sinceBoot  14,431 ms   67,108,864 bytes, ck64 0x757b795dd5138044
                           expected 67,108,864 / 0x757b795dd5138044
                           SIZE MATCH, CONTENT MATCH
    sinceBoot 211,992 ms   LockSettingsService: unlockUser started

**Margin 197.6 seconds.** The largest in this repo after 3e-iv's 494s.

**The checksum is checked by the machine, not by reading two logs.**
`Probe3fActivity`'s byte generator is a fixed-seed xorshift64*, and the
generator advances per 8-byte word without resetting at a chunk boundary, so a
file of a given length has exactly ONE correct ck64. `Penny3evService` runs the
identical generator with a null output stream, producing nothing and computing
only, and compares. The expected value was therefore never copied out of an
earlier run's log. **This also closes 3e-iv's loose end** — does a stored file
survive a power cycle with its CONTENT intact rather than merely its SIZE — and
closes it in a better place than the loose end asked for, because the check ran
before first unlock rather than after it.

**Corroborated from the guest's own console**, cid 2049, a channel the host
process does not write to:

    [1.248723] ext4 filesystem being mounted at /mnt/encryptedstore
    [1.426141] PENNY3F: verify /mnt/encryptedstore/penny3ev.bin read 67108864
               bytes (stat said 67108864), ck64 0x757b795dd5138044, 147 ms

The guest mounted its encrypted disk 1.25 seconds into its own boot, with the
host still at the lock screen.

**THE CONTROL, AND IT RODE THE SAME SOCKET BECAUSE IT HAD TO.** The same
service read the same store again after the unlock, on the same boot, and got
`ck64 0x757b795dd5138044` and 67,108,864 bytes — identical. Between the two
reads literally nothing changed but the PIN.

    PRE-UNLOCK             67,108,864 B  0x757b795dd5138044  147 ms
    AFTER-UNLOCK CONTROL   67,108,864 B  0x757b795dd5138044  112 ms

It could not have been a second connection. `penny3f_payload.c` calls
`accept4()` exactly ONCE and then loops on commands until a zero-length frame,
so a second `connectVsock` would never have been accepted. The socket was held
open across the 197-second unlock wait and the control command went down the
same one. Forced by the payload, and it happens to be the strongest form the
control could take.

### Rung 3g-i — a 2048MB VM wakes unattended, and the rung was misframed

The rung was defined as "can a 2GB VM make the 20-second foreground-service
exemption, given it takes ~4s longer to reach ready than a 256MB one". **That
is the wrong reading of the window, and taking it at face value would have
produced a reassuring non-answer.**

`Background started FGS: Allowed ... duration:20000` governs `startForeground()`
and nothing after it. Every service in this app calls `startForeground` as the
first statement of `onStartCommand` and only then hands VM work to another
thread. Measured this session, on the boot that answered both rungs:

    .VmService        reasonCode:LOCKED_BOOT_COMPLETED  duration:20000
    .MicFgsService    reasonCode:LOCKED_BOOT_COMPLETED  duration:20000
    .Penny3dService   reasonCode:LOCKED_BOOT_COMPLETED  duration:20000
    .Penny3evService  reasonCode:LOCKED_BOOT_COMPLETED  duration:20000
    .Penny3giService  reasonCode:LOCKED_BOOT_COMPLETED  duration:20000

`Penny3evService` reached `startForeground` 33ms into `onStartCommand`;
`Penny3giService` 2ms. **A VM that takes four seconds longer to reach
`onPayloadReady` cannot miss a window it was never racing.** Five foreground
services in one app all took the exemption on the same boot, which was itself
untested.

What was actually worth measuring, and was:

    boot   VM      ready sinceBoot   into the attempt   userUnlocked   guest MemTotal
    1      256MB       59,133 ms           1,381 ms        false          239,796 kB
    1      2048MB      65,688 ms           4,746 ms        false        2,038,164 kB
    2      256MB       17,012 ms           1,901 ms        false          239,796 kB
    2      2048MB      23,519 ms           4,639 ms        false        2,038,164 kB

Both boots, both sizes, `CMD_INFO` answered `status=0` and the guest exited 46.
Margins before first unlock: 171.9s on boot 1, 188.5s on boot 2.

**The guest's own `/proc/meminfo` reading `MemTotal: 2038164 kB` is the part
that matters** — it is not our process saying 2048MB was accepted, it is the
guest kernel saying 2048MB was delivered.

**THE CONTROL IS ON THE SAME BOOT, FROM THE SAME SERVICE, AND ONLY THE MEMORY
FIGURE DIFFERS.** Same broadcast, same payload, same `CPU_TOPOLOGY_MATCH_HOST`,
same code path — 256MB first so it is banked before the risky one runs, and
waited out to `onStopped` so its memory is genuinely back. "2048MB is too much
at boot" and "the service is shaped wrong" therefore stay separable, and the
control came up both times.

**The low-memory killer result is better than expected, and the shape is a
finding.** Our own app was never killed on either boot.

    boot 1   2GB VM started at 57.7s    ZERO kills
    boot 2   2GB VM started at 15.1s    4 kills, all oom_score_adj 905, all cch CEM
             com.android.permissioncontroller, com.android.cellbroadcastreceiver.module,
             com.google.euiccpixel, app.seamlessupdate.client

Against 3e-ii's thirteen kills on an idle unlocked phone. **The difference
between the two boots is WHEN the 2GB VM started**, and it was accidental:
`Penny3giService` waits for `Penny3evService` to bank its verdict or for a
45-second timeout, and on boot 1 3e-v failed so the timeout ran, while on boot 2
3e-v succeeded and released it at 15.1s. Starting a 2GB VM while the boot is
still settling costs four cached processes; starting it a minute later costs
none. Every casualty was `cch` — cached and empty, nothing a user would notice.

### Four earlier rungs reproduced free, on the same boot, all pre-unlock

    rung 3    VM up, onPayloadReady sinceBoot=14,154ms, userUnlocked=false
              EIGHTH reproduction
    rung 3b   MIC [A-assistant] 10,577ms peak 1329 rms 340.68 90.8% non-zero
              MIC [B-fgs]       13,214ms peak 1265 rms 403.94 90.9% non-zero
              FIFTH and SIXTH
    rung 3d   control leg 5ms, AUDIO peak 1632 rms 570.38 90.9% non-zero,
              32,000 bytes crossed at 15,258ms, userUnlocked=false, exit 43
              FOURTH

Boot 1 reproduced rung 3 (14,430ms) and rung 3d (15,590ms, exit 43) too.

### The build, and what it deliberately did not touch

One Java-only build. **No guest payload was compiled, no trip into the Debian
guest, no compiler.** `Penny3fPayload.so` from rungs 3f/3h was reused unchanged:
its `CMD_STREAM` (6) writes a file and returns a checksum and its `CMD_VERIFY`
(7) reads one back and returns a checksum, which is exactly write-then-verify.
The payload budget for this handset stayed spent, as CLAUDE.md said it should.

Two new services, both COPIES and not edits:

    Penny3evService   directBootAware, specialUse, START_STICKY
                      VM penny3ev, 256MB, ONE_CPU, 256MB encrypted store
    Penny3giService   directBootAware, specialUse, START_STICKY
                      VMs penny3gic (256MB) and penny3gi (2048MB), MATCH_HOST

`VmService`, `Penny3dService`, `MicFgsService`, `Probe3eActivity`,
`Probe3eiiActivity`, `Probe3eiiiActivity` and `Probe3fActivity` were not
touched; each carries a committed result and each reproduced during this
session. `BootReceiver` gained two more starts and now starts five services
from one broadcast, each independent and none able to take another down.

### What cost time, and the three mistakes worth writing down

**1. The single-thread executor trap, caught by a smoke test rather than by a
reboot.** `Penny3giService` handed `setCallback()` and its own blocking wait
loop the same `Executors.newSingleThreadExecutor()`, so `runBoth()` sat on the
very thread `onPayloadReady` had to arrive on. A perfectly healthy 256MB VM
reported as never ready for three minutes. This is the trap already in
CLAUDE.md from 3e-i and it was walked straight into anyway. **The smoke test is
what caught it** — `Penny3giService` was run by hand over adb before any reboot
was spent, precisely because it owns no store and deletes its VMs, so it could
not contaminate 3e-v. Running that smoke test cost four minutes and saved a
reboot. Do it every time.

**2. Boot 1 of 3e-v died on the `getOrCreate` trap, and the first diagnosis was
WRONG.** The failure:

    VirtualMachineException: Failed to open APK
      at VirtualMachineConfig.toVsConfig(VirtualMachineConfig.java:944)
      at VirtualMachine.run(VirtualMachine.java:1790)
    Caused by: java.io.FileNotFoundException: open failed: ENOENT

This is verbatim CLAUDE.md's `getOrCreate` trap. But the reasoning that
followed was wrong: the stored config looked as though it had been written
minutes earlier by the current install, so "stale path" was ruled out, and
`setEncryptedStorageBytes` was suspected instead.

Two zero-code tests settled it without a rebuild, and both used
`Probe3fActivity`, which is proven from 3h:

    delete+create WITH encrypted storage     works  32MB in and back, ck matched
    getOrCreate on a VM this APK created     works  same ck, read across instances

So both the storage call and `getOrCreate` were exonerated, and the fault was
specific to `penny3ev`. Rather than guess a third time, a diagnostic was added
that reads the VM's own directory off disk from inside the app — which `adb
shell` cannot do on a user build — and dumps anything small and textual. It
named the cause in one line:

    VMDIR config.xml >> ... <string name="apkPath">/data/app/
    ~~F0K0m_D81CSMn57ecg2Afw==/com.pennyspike.probe2a-lLAwbUuEeI_QizpSaf2qAQ==
    /base.apk</string> ...

**That is the FIRST of the day's three installs.** `penny3ev` had been created
under it and its stored config still pointed at an APK two later installs had
replaced. The trap was exactly what it looked like at first glance; the second
guess was the wrong one. **The lesson is not "remember the trap" — it was
remembered. It is that a stale stored config cannot be ruled out by reasoning
about when the VM was created, because that reasoning is unfalsifiable from
outside. Read `config.xml`.** The diagnostic is now permanent in
`Penny3evService.dumpVmDir()` and costs nothing until something fails.

**3. The recovery that had to be added, and its one dangerous property.**
`Penny3evService` now catches a `run()` failure, dumps the VM directory,
`delete()`s and `create()`s fresh, and logs `STORE WAS RESET` loudly. Deleting
is safe there and only there: a VM that cannot run has no readable store to
lose. **But it means a reinstall between two boots silently converts "the store
was re-opened" into "a new store was created", which would read as a file that
was never there rather than as a stranded one.** The loud log line is the only
thing standing between that and a false NO. Never reinstall between the two
boots of a persistence experiment.

### Versions

GrapheneOS 2026091001, Android 17, build CP2A.260705.006, patch 2026-09-01,
Pixel 6a bluejay, bootloader locked, verifiedbootstate yellow.
SDK platform android-37.0, build-tools 37.0.0, Temurin 21.0.12.1,
platform-tools 37.0.1. APK sha256
2e89918fdd783dac94946ebd81806fd5f9a8a791f47686faa6f4b609070642df, 156,613 bytes.
`Penny3fPayload.so` unchanged from rungs 3f/3h, 15,392 bytes.

### Verdict

**3e-v: YES.** The encrypted store is not tied to the user's credential. A
sideloaded, unprivileged app opened its own encrypted ext4 disk 14.4 seconds
after power-on with the phone still at the lock screen, read back 67,108,864
bytes and proved they were the right bytes by a checksum computed independently
of the write — 197.6 seconds before anybody typed a PIN, with the same read
after the unlock returning the identical checksum as a control. **A model
shipped into that store can be read at boot with nobody in the room.** Every
unattended result in this repo keeps its meaning.

**3g-i: YES.** A 2048MB VM reached its payload at boot, locked and unattended,
on two reboots — 65.7s and 23.5s after power-on — with a 256MB control on the
same boot from the same service each time, and the guest's own kernel reporting
`MemTotal: 2038164 kB`. Our app was never killed. The 20-second exemption was
never the constraint, because `startForeground` is called before any VM work
and was accepted 2-33ms into `onStartCommand` for all five services.

**Together they join the two halves that had never been in the same room: the
unattended wake and the memory a model needs.** A VM large enough to hold a
model now wakes on its own at boot, on a locked phone, and can read a model out
of persistent encrypted storage before anyone touches the screen.

### What this does NOT say

**Nothing was RUN.** 64MB read back and checksummed is a file, not a model, and
`CMD_INFO` is not a workload. The largest unanswered thing in this repo is
unchanged: the guest still does nothing with the audio, and nothing here loads,
parses or executes a model of any kind.

**A store was never CREATED before first unlock.** Boot 1 tried and died on the
stale-config trap, and the file 3e-v read was written on an unlocked phone
during the diagnostic run at 15:52. So "re-open an existing store pre-unlock" is
answered YES twice over; "create a new store pre-unlock" is untested and is the
first-boot-after-factory-reset case. It costs one constant and two reboots.

**3g-ii is still not run.** Both boots were an idle phone with nothing open and
the Terminal app's Debian VM shut down. What the low-memory killer does when
the processes in the way are a camera and a browser somebody is actually using
has never been measured, and the four `cch CEM` casualties here are not
evidence about it.

**Two boots is reproducibility, not reliability.** Nothing ran longer than
about twenty seconds, nothing was tested on battery, and there is still no
endurance test anywhere in this repo. Still `DEBUG_LEVEL_FULL`, still
non-protected, still sample DICE values, so no attestation claim rests on any
of it. And delivery is unchanged: `pm grant` and `voice_recognition_service`
both still need a cable.

## 2026-09-15 — rung 3g-ii ANSWERED. A 2GB VM on a phone somebody is USING does not kill what is on the screen. It kills everything behind it, and the keyboard.

The last question this handset could answer without a build. Three runs, one
afternoon, no reboot, no reinstall. The APK is unchanged:
`sha256 2e89918fdd783dac94946ebd81806fd5f9a8a791f47686faa6f4b609070642df`,
156,613 bytes, the same install all five of 15 Sept's rebuilds ended at.

### The answer

**Our app was never killed, in any run.** The low-memory killer walks strictly
from the cheapest tier down, and on a loaded phone it goes far deeper than it
does on an idle one — but it stops at `oom_score_adj` **201** and does not enter
the foreground band (200/100/0).

    run  phone state   foreground app   kills   deepest adj   our app   camera
    A    idle          our own probe      37       905        survived    --
    B    in use        our own probe      16       201        survived   KILLED (700)
    C    in use        the CAMERA         14       201        survived   SURVIVED (0)

**201 twice is the finding.** It is `com.android.inputmethod.latin`, the
keyboard, killed with reason `prcp IMPB` — perceptible, important background.
Android restarts it immediately because the system needs it. In run C it died
**1.9 seconds AFTER the guest was already ready**, so the pressure does not stop
at `onPayloadReady`.

**Run B's dead camera is an artefact of the probe, not of the VM, and run C is
what proves it.** `Probe3fActivity` is an activity, so starting it takes the
screen and demotes whatever was there to "previous app" (adj 700). The camera
died in run B with reason `prev LAST` — killed as the app behind, not the app in
front. Run C removed that confound by starting the 2048MB VM from
`Penny3giService`, a background foreground-service that never touches the
screen, and the camera sat at adj 0 throughout and lived.

### The three runs, in full

**Run A — the idle control, 16:09:00.750.** Taken before any of this, on an idle
unlocked phone, AC power, level 100, ~15 minutes' uptime, Debian VM down.

    HOST before   MemFree 649,560 kB  MemAvailable 1,430,772 kB  Cached 1,833,492 kB
    run()         accepted in 22ms
    onPayloadReady  3,862ms after ASK
    guest         MemTotal 2,038,100 kB, MemFree 1,925,740 -> 1,925,252 kB
    CMD_INFO      status=0, guest exit 46
    HOST after    MemFree 402,088 kB  Cached 3,115,752 kB
    kills         37, every one adj >= 905, all `cch`
    floor         905 — com.android.settings, com.android.externalstorage,
                  com.android.providers.calendar, com.android.cellbroadcastreceiver

**Run B — loaded, our probe in the foreground, 16:21:42.224.** Camera, browser,
Gallery, Clock, Calculator and Files opened by hand, camera brought to the front,
screen on and unlocked. Identical command to run A.

    HOST before   MemFree 157,880 kB  MemAvailable 1,035,184 kB  Cached 1,941,588 kB
    run()         accepted in 39ms
    onPayloadReady  4,035ms after ASK
    guest         MemTotal 2,038,100 kB, MemFree 1,929,824 -> 1,925,404 kB
    CMD_INFO      status=0, guest exit 46
    HOST after    MemFree 584,512 kB
    kills         16, floor 201
    casualties    app.grapheneos.camera     adj 700  `prev LAST`
                  com.android.inputmethod.latin  adj 201  `prcp IMPB`
                  app.vanadium.browser 900, cellbroadcastreceiver 905,
                  settings 915, shell 925, documentsui 930, media 935,
                  calculator2 940, externalstorage 945, permissioncontroller 955,
                  deskclock 960, gallery3d 970, three Vanadium child processes
    survivors     com.android.launcher3 (100), com.pennyspike.probe2a (100->0)

Confirmed dead afterwards by `pidof`: camera, browser, gallery, clock. The
keyboard had already restarted itself.

**Run C — loaded, the CAMERA in the foreground, 16:31:43.304.** Same apps
reopened by hand, camera on screen. The 2048MB VM started from
`Penny3giService` instead, so nothing took the screen.

    resident before, by adj   0 app.grapheneos.camera
                              100 com.android.launcher3
                              100 com.pennyspike.probe2a
                              201 com.android.inputmethod.latin
                              700 com.android.documentsui
                              900-960 calculator2, deskclock, gallery3d, vanadium x4
    HOST before   MemFree 326,352 kB  MemAvailable 1,216,232 kB
    CONTROL 256MB ready in 2,390ms, guest MemFree 143,776 kB, exit 46, ZERO kills
    HOST between  MemFree 1,260,424 kB
    TEST 2048MB   run() accepted in 50ms, READY 7,424ms after the attempt began
    guest         MemFree 1,927,220 kB
    HOST after    MemFree 2,332,156 kB
    kills         14, floor 201, ALL of them after the 2048MB VM was created
    survivors     app.grapheneos.camera (0), com.android.launcher3 (100),
                  com.pennyspike.probe2a (100)

**The timing is what makes run C count.** The 256MB control caused not one kill.
The first kill landed at 16:31:46.153, 2.8s after `TEST 2048MB start`, and the
last at 16:31:52.633. So the 14 casualties are attributable to the 2048MB VM and
to nothing else in the run.

**The 2GB VM is slower under load and that is new.** 7,424ms to ready in run C
against 4,262ms for the same service on the same phone forty minutes earlier,
and against ~4.3s in 3e-i's idle measurements. It still got every byte:
`MemTotal 2,038,100 kB` from the guest's own kernel each time.

### Two things about the method that cost time and are worth keeping

**`am force-stop` restarts all five boot services, and every run therefore
carries a restart storm.** The idle control had been force-stopped 2.1s before
its `am start` (the trap that says always force-stop before `am start`), which
means `VmService`, `Penny3dService`, `Penny3evService`, `MicFgsService` and
`Penny3giService` all came back and re-ran — `Penny3giService` bringing up its
own 256MB control and its own 2048MB VM at 16:09:01-16:09:12, overlapping the
probe. Run B was checked and had the identical storm at 16:21:43-16:21:54. The
two runs are comparable only because the storm is on both sides. **Check for it
before comparing any two runs in this app** — five services restarting is a
different experiment from one probe starting.

**`Penny3giService` runs once per process and says so.** A second
`am start-foreground-service` logged `already started by an earlier delivery —
nothing to do` and did nothing at all. Re-arming it needs a force-stop, which is
how run C was obtained. A smoke test that appears to do nothing may be a guard,
not a failure — read the log before assuming the service is broken.

### What the casualty lists actually contain

Nine of the processes killed across the three runs are AOSP system components,
not user apps: `com.android.settings`, `com.android.permissioncontroller`,
`android.process.media`, `android.process.acore`, `com.android.externalstorage`,
`com.android.keychain`, `com.android.rkpdapp`, `com.android.packageinstaller`,
`com.android.inputmethod.latin`. `rkpdapp` is remote key provisioning — part of
the attestation machinery that is the reason this project is on a phone at all.

**And "the apps you have open" is mostly a list of cached processes.** The
snapshot taken before run B, after six apps were opened by hand and the camera
brought to the front, found only five processes below adj 900 on the whole
device: the camera at 0, the launcher at 100, our app at 100, the keyboard at
201 and the previous app at 700. Everything else Android had already demoted to
900-970 the moment the user switched away. The camera's HAL and `cameraserver`
sit at -700 to -1000 and are never candidates.

### What this does NOT say

**It is not an argument that the memory is free.** 16 and 14 processes died. A
user would see the browser reload its pages, the gallery restart, and the
keyboard blink. The claim is narrow and it is the only one the log supports:
**the app on the screen survived, twice, and so did ours.**

**One 2GB VM, not a resident one.** Every VM here lived for seconds and then
exited 46. Nothing measures a 2GB VM held for the length of a session while
somebody keeps using the phone, which is the actual product shape.

**Nothing was RUN.** `CMD_INFO` reads `/proc/meminfo` and exits. No model, no
recognition, no workload — unchanged and still the largest gap in this repo.

**The margin to the foreground band is one tier and it is not guaranteed.**
The killer reached 201; the next tiers are 200, 100 and 0. Rung 3e-i already
showed that band is reachable: at 4096MB it killed our own foreground TOP app,
which took the VM handle with it. 2048MB stopped short twice. That is a measured
boundary on this handset at this load, not a law.

**A stripped phone was NOT tested, and it is a rung 4 question.** Matt's point
during the run — the phone is Penny, so competing apps can be deleted — is
recorded because it is the right question and this handset cannot answer it.
What the OS image contains is exactly what rung 4 decides. Two things to carry
into it. First, most of what died is the operating system, which comes back on
any build. Second, fewer apps is not more headroom but fewer cheap victims: the
idle phone had 37 disposable cached processes and the killer never went below
905 because it ate its fill and stopped, while the loaded phone had fewer and
went to 201. A bare image has almost none in front of the things that matter.
Whether that nets out for or against is untested and must not be asserted
either way.

**Three runs on one afternoon, one phone, AC power, screen on, Debian VM down.**
Nothing here is on battery, and the screen never went off.


## 2026-09-15 (evening) — the endurance soak is VOID, store-create is PARKED, and Android's own memory budget is measured for the first time

Four things, none of them a rung. One void result withdrawn, one question
parked with its reason, one baseline measured, one correction to CLAUDE.md.

**THE SOAK IS VOID AND NOTHING IS CLAIMED FROM IT.** `PennySoakService` was
built and committed (543f9f0) and it ran, but it ran on a store this session
contaminated by hand and the run is withdrawn rather than reported.

The contamination, written down because the lesson generalises. After the
smoke test the app was force-stopped. `PennySoakService` returns
`START_STICKY`, so Android recreated it **with a null intent** — on an
unlocked phone, with no `vm` extra, so `mVmName` fell back to the default
`pennysoak` and the service created and wrote the store there and then. The
boot that followed therefore found the store already present and full
(`status=0`, 67,108,864 B on the first verify at 18,923ms) and never
exercised the create path at all.

**A guard that lives in the intent is not a guard.** Anything that must not
happen on an unlocked phone has to test the phone, not the intent, because
the intent is the first thing the system throws away when it restarts a
sticky service.

That boot also produced the first time in this repo our own app has been
killed at 2048MB, and it is worth recording against 3g-ii:

    19:36:48.285  PennySoak  2048MB VM created
    19:36:55.922  Penny3gi   2048MB VM created
    19:36:57.474  KILL  com.android.launcher3      adj 100
    19:36:57.525  KILL  com.pennyspike.probe2a     adj 100
    19:37:11.969  KILL  com.pennyspike.probe2a     adj 100   (again)

50 kills, 2 kills of our own app, settling only on the third attempt.
**3g-ii's comfortable reading — that the killer stops at adj 201 — holds for
ONE 2048MB VM and not for two.** Two crossed into the foreground band (100)
and took us with it. That is an accidental finding from a botched boot, not a
designed run, so it is recorded as a warning rather than as a result: do not
start two 2GB VMs on the same boot.

The soak was stopped deliberately rather than by force-stop, which would have
restarted it through the same sticky path:

    adb shell pm disable-user --user 0 com.pennyspike.probe2a

`vm list` then returned `Running VMs: []` with no `crosvm` and no app process.
No data was cleared, so `penny3ev`'s store is untouched and `pm enable`
reverses it.

**STORE-CREATE BEFORE FIRST UNLOCK IS PARKED, and the reason is provisional.**
It is parked pending the native-model benchmark, NOT settled. If the model
runs natively on Android the question only matters for a store inside a VM,
and there may be no VM. If the benchmark comes back badly and the VM returns,
this question returns with it, unchanged and still cheap: one constant, one
build, two reboots.

**ANDROID'S OWN MEMORY BUDGET — measured, and it had never been measured on an
idle phone with nothing of ours in it.** Every host reading in this repo so
far was taken around a VM. This one is the floor underneath all of them.

Conditions: app disabled, full reboot, unlocked by hand and then untouched —
home screen only, Terminal app not opened, no VM of any kind. Read **5.8
minutes after boot, so possibly still settling**; a second reading at ~20
minutes idle follows below.

    adb shell cat /proc/meminfo

    MemTotal        5,718,280 kB     5.45 GB
    MemFree            75,380 kB
    MemAvailable      940,640 kB     919 MB
    Cached          1,094,964 kB
    AnonPages       3,313,572 kB
    SwapTotal       3,145,724 kB     zram
    SwapFree        2,574,588 kB     571,136 kB in swap
    ZRAM (dumpsys)    197,464 kB physical holding 829,696 kB swapped

zram is configured and in use at idle. That is ordinary Android behaviour on
this build and is recorded as context, not as a symptom.

**THERE ARE TWO HONEST NUMBERS AND THEY MUST BOTH BE QUOTED.** They differ by
a factor of three and each answers a different question.

    /proc/meminfo  MemAvailable      940,640 kB    what the kernel hands over
                                                   without killing anything
    dumpsys meminfo  Free RAM      3,068,208 kB    of which 2,311,420 kB is
                                                   cached app processes that
                                                   Android will kill on demand

So the budget is **~919 MB free of charge, and up to ~2.9 GB if the cached
band is evicted** — which is the same mechanism 3g-ii measured from the VM
side, seen from the other end. A workload above 919 MB is not refused; it is
paid for in cached processes.

`ps -A -o RSS --sort=-RSS` shows ~40 system processes clustered near 200 MB
RSS each, which is Zygote sharing rather than 8 GB of real usage — RSS
double-counts shared pages. `dumpsys` puts real used PSS at 2,293,216 kB.

**THE MMAP FACT, both halves, recorded because it changes what peak RSS means
and not for any other purpose.** llama.cpp memory-maps the GGUF, so the
weights are file-backed and clean. While the model is idle those pages are
reclaimable — the kernel can drop them and re-read from flash. **During
generation that reclaimability is theoretical: every token touches
essentially all the weights, so the whole file is resident and hot, and peak
RSS is approximately the GGUF size plus the KV cache.** Budget against the
second half, not the first.

**THE 6a NUMBER IS A FLOOR, NOT THE PRODUCT BUDGET.** This handset has 6 GB
and is the cheapest device in the plan; the 7a that replaces it has more.
Anything that fits here fits on the product. Anything that does not fit here
is not thereby ruled out — it has to be re-measured on the 7a before it is
called a no.

**CORRECTION TO CLAUDE.md — "Host-side memory was NEVER measured" is wrong and
has been wrong since 15 Sept.** That open thread predates rung 3e-ii and was
never retired when the work that answered it landed. It has been measured at
least three times:

    3e-ii    host MemFree sampled three times through one run —
             2,257,952 -> 288,756 -> 137,088 kB — which is the measurement
             that proved crosvm takes its memory at VM CREATION
    3g-i     host memory read around a 2048MB VM at boot
    3g-ii    host kill lists and adj bands across three runs

The line is replaced in CLAUDE.md in the same commit as this entry. The half
of it that IS still true is kept: the q9 sampler read the guest, so
"two VMs caused no pressure" remains a statement about the guest only.

**What none of this says.** No model has been downloaded and nothing has been
run. 919 MB is one reading on one boot at 5.8 minutes, and a second at ~20
minutes is taken next specifically because settling is the obvious
alternative explanation. The soak question — does any of this survive hours
rather than seconds — is exactly as unanswered as it was this morning.

## 2026-09-15 (evening, later) — the 919 MB figure was a phone still settling: the idle budget is ~2.02 GB, and native-vs-VM is CLOSED rather than pending

Two things in this entry. A second memory reading that corrects — not
contradicts — the one taken 20 minutes earlier in the entry above. And a
correction to how the previous entry and CLAUDE.md framed the store-create
park, which named the wrong condition for reopening it.

### The second reading, and it moves by 1.18 GB

Same boot, same conditions as the 5.8-minute reading: app disabled via
`pm disable-user`, `vm list` returning `Running VMs: []`, Debian VM down, no
app opened by hand, AC power, screen on, nobody touching the phone. The only
variable is time since boot. Taken by a background loop on the Mac that
polled `/proc/uptime` every 30s and fired once it passed 1500s.

    uptime 1520.07 s (25.3 min), Running VMs: []

                        5.8 min        25.3 min        delta
    MemTotal          5,718,280 kB   5,718,280 kB            --
    MemFree              75,380 kB   1,220,200 kB    +1,144,820
    MemAvailable        940,640 kB   2,119,020 kB    +1,178,380
    Cached            1,094,964 kB   1,124,860 kB       +29,896
    AnonPages         3,313,572 kB   1,726,304 kB    -1,587,268
    SwapTotal         3,145,724 kB   3,145,724 kB            --
    SwapFree          2,574,588 kB   1,074,428 kB    -1,500,160
    Zram physical       197,464 kB     496,856 kB      +299,392
    dumpsys Free RAM  3,068,208 kB   3,748,470 kB      +680,262

**919 MB was correct as measured and wrong as a budget.** It is what the
kernel would have handed over at 5.8 minutes after boot, and that is a true
statement about a phone five minutes past boot. It is not the idle figure.
**~2.02 GB is the idle figure for now** — "for now" because 25 minutes may
not be the plateau either; see below.

**The mechanism, stated plainly, because the number is easy to misread as
memory that appeared.** Nothing was freed. Android compressed roughly 1.5 GB
of idle anonymous pages into roughly 300 MB of zram: `AnonPages` fell
1,587,268 kB, `SwapFree` fell 1,500,160 kB (so that much more is now held in
swap), and the physical cost of holding it rose only 299,392 kB. That is a
~5:1 compression ratio on pages nothing had touched for twenty minutes.
`dumpsys` agrees from the other side: `486,672K physical used for 1,968,128K
in swap`.

**The consequence is the part that matters for tomorrow.** Swap is now about
two-thirds spent — 1,074,428 kB free of 3,145,724 kB — and that is headroom a
model run will eat into, because the pages a running model touches are
anonymous and hot and cannot be compressed away while they are in use. The
~2.02 GB is therefore not a number to plan against on its own.
**Peak RSS plus KV cache during generation is still the number that decides
anything**, and nothing in this repo has measured it, because nothing in this
repo has ever run a model.

**25 minutes may not be the plateau.** A third reading is being taken at
~60 minutes uptime by the same loop with the threshold raised to 3600, on the
same untouched boot, specifically to find out whether the curve is still
climbing. **Every per-run `MemAvailable before` reading taken tomorrow must
record the phone's uptime alongside it**, or it cannot be placed against this
curve and is not comparable to any other run.

### The correction: native-vs-VM is CLOSED, not pending

The entry above, and CLAUDE.md's Sequencing section as committed in 245b14d,
both said the store-create question is parked "pending the native benchmark"
and implied the VM returns if that benchmark comes back badly. **That is the
wrong condition and it is corrected here.** The previous entry is left
standing as written, per this repo's rule that earlier entries are never
rewritten; this is the later entry and it wins.

**Running the model inside a VM is closed on today's evidence.** Three
grounds, every one of them measured in this repo rather than reasoned about:

    3e-ii    crosvm takes ~1.92GB from the host AT VM CREATION, against a
             total MemAvailable of 2,119,020 kB idle (940,640 kB at 5.8 min)
    3f       the guest cannot pin cores -- each vCPU is an unpinned host
             thread, and the guest MIDR mix d44 d05 d0b d0b d0b d0b d05 d05
             does not match the host's d05 d05 d05 d05 d0b d0b d44 d44
    2c/open  getCapabilities() returns 2, CAPABILITY_NON_PROTECTED_VM only,
             so this device cannot make a protected VM and the host can read
             the guest anyway

**A poor native benchmark does NOT reopen it.** The VM pays all three of those
costs on top of whatever native costs, so it cannot be the better answer to a
speed problem. If the model is too slow natively on this handset, the reply is
a different model, a different quantisation, or the 7a — not a VM.

**The only thing that reopens it is model isolation becoming a requirement.**
If it does, the store-create question comes back unchanged and still cheap:
one constant (a VM name that has never existed, so `getOrCreate` has no prior
directory), one build, two reboots.

**What the native benchmark therefore is.** An ABSOLUTE feasibility
measurement of this handset — does a small model run usefully here at all —
and not a comparison against anything. It is not an open to-do that native-vs-
VM is waiting on.

CLAUDE.md's Sequencing bullet is corrected in the same commit as this entry.

**What none of this says.** Still no model has been run. The second reading is
one reading on one boot, and the third is not in yet. The compression ratio
observed here is on pages Android chose to swap because they were cold; it
says nothing about what zram will manage against a model's working set, and
it must not be read as 1.5 GB of spare capacity. And all of it is an idle
phone on AC power with nothing open, which is not the product shape.

## 2026-09-15 (evening, later still) — the models and the toolchain are on the Mac, all ten files verified against Hugging Face's own hashes

Nothing measured here. This is a record of what was downloaded, from where,
and at what version, made because the Mac goes back on a phone tether
tomorrow and anything not fetched tonight is not being fetched.

### The models

Ten GGUF files, **19,771,681,856 bytes** total, in `~/Documents/penny-models`.
Wire time about twelve minutes on a home LAN at ~28-33 MB/s.

**Every file is VERIFIED against the LFS oid Hugging Face publishes for it**,
which is the file's sha256. The check is a local `shasum -a 256` compared
against that published value, and the byte count compared against the
published size. Both must match. A locally-computed hash on its own proves
only that the download was internally consistent, not that it is the right
file, and the manifest is explicit about which was done. The published
hashes are kept alongside in `HF-PUBLISHED.txt` (87 of them, every GGUF in
the six repos consulted) so the check can be re-run offline.

    repo                                 file                              bytes
    unsloth/Qwen3-1.7B-GGUF         Qwen3-1.7B-Q4_K_M.gguf           1,107,409,472
    unsloth/Qwen3.5-2B-GGUF         Qwen3.5-2B-Q4_K_M.gguf           1,280,835,840
    unsloth/gemma-4-E2B-it-GGUF     gemma-4-E2B-it-Q4_K_M.gguf       3,106,738,272
    unsloth/Qwen3-1.7B-GGUF         Qwen3-1.7B-Q4_0.gguf             1,056,782,912
    unsloth/Qwen3.5-2B-GGUF         Qwen3.5-2B-Q4_0.gguf             1,214,873,856
    unsloth/gemma-4-E2B-it-GGUF     gemma-4-E2B-it-Q4_0.gguf         3,041,378,400
    unsloth/Qwen3-1.7B-GGUF         Qwen3-1.7B-Q8_0.gguf             1,834,426,944
    ggml-org/Qwen3-4B-GGUF          Qwen3-4B-Q4_K_M.gguf             2,497,280,640
    ggml-org/Qwen3-1.7B-GGUF        ggml-org/Qwen3-1.7B-Q4_K_M.gguf  1,282,439,264
    google/gemma-4-E2B-it-qat-q4_0-gguf
                                    google/gemma-4-E2B_q4_0-it.gguf  3,349,516,256

Full hashes are in `~/Documents/penny-models/MANIFEST.txt`, one row per file
with repo, byte count, sha256 and the check result. Not reproduced here
because the manifest is the record and copying it invites the two to drift.

**Why unsloth and not ggml-org for the Qwen3-1.7B files, against the stated
preference.** ggml-org's `Qwen3-1.7B-GGUF` carries only Q4_K_M, Q8_0 and f16
— **it has no Q4_0 at all**, and Q4_0 is the whole point of half this set,
because the 6a has `asimddp` but not `i8mm` and llama.cpp repacks Q4_0 for
the dot-product path. A Q4_K_M from one quantiser against a Q4_0 from another
is not a comparison. So all three Qwen3-1.7B files come from unsloth, which
has all three. ggml-org supplies Qwen3-4B, which it does carry.

**The ggml-org control file, and it is not redundant.** ggml-org's
Qwen3-1.7B Q4_K_M is 1,282,439,264 bytes against unsloth's 1,107,409,472 —
**a 175 MB gap on nominally the same model at the same quantisation**, from
different choices about which tensors stay at higher precision. It is kept in
its own `ggml-org/` subdirectory precisely because the filename collides.
Downloaded so that "Q4_K_M" can be tested as a quantiser's decision rather
than as a fixed thing, if the two ever disagree on speed or footprint.

**Gemma 4 E2B is the outlier and its file size says why.** 3.1 GB at Q4_K_M,
nearly three times Qwen3-1.7B. "E2B" is *effective* 2B; the file and the
memory footprint reflect the full parameter count, not the effective one.
It is not a like-for-like against a 1.7B and must not be written up as one.

**Google's QAT Gemma was added as a tenth file.** `gemma-4-E2B_q4_0-it.gguf`,
3,349,516,256 bytes, from Google's own repo. It is quantisation-aware
trained rather than quantised after the fact, so it is the one Q4_0 in the
set expected to hold quality; it is also 308 MB larger than unsloth's plain
Q4_0. Downloading it needed no licence acceptance. Included so that "Q4_0 is
worse" can be tested against the best available Q4_0 rather than assumed.

**Qwen3-4B is a 7a ceiling probe and is NOT for the 6a runs.** 2.5 GB at
Q4_K_M against an idle `MemAvailable` of ~2.02 GB on this handset.

Skipped deliberately: anything sub-1B (they collapse at 4-bit on structured
output) and anything Llama (the licence rules it out).

### The toolchain

    NDK              30.0.16248370   via sdkmanager
                     /opt/homebrew/share/android-commandlinetools/ndk/30.0.16248370
                     toolchains/llvm/prebuilt/darwin-x86_64/bin/clang present
    cmake            3.22.1-g37088a8 via sdkmanager
                     /opt/homebrew/share/android-commandlinetools/cmake/3.22.1
    ninja            1.10.2          via sdkmanager, SAME directory as cmake
                     /opt/homebrew/share/android-commandlinetools/cmake/3.22.1/bin/ninja
    ninja (brew)     1.13.2          /opt/homebrew/bin/ninja -- see the trap
    llama.cpp        38a5b42d9a3e82e0a586bcd1caed121f36c87a73
                     "HIP: Enable AllReduce for ROCm (#27825)"
                     ~/Documents/llama.cpp, FULL clone, 453MB of history

**The NDK directory is real this time.** The `ndk/30.0.16248370` that existed
before tonight was the empty shell left by the killed download earlier today
— `build.sh` tolerated it and resolved `CLANG` to empty. It was removed and
re-installed, and `clang` is now present at the path above. Checking the
directory exists is not the check; checking for `clang` is.

**A brew ninja was installed and it should not have been.** The agreed route
was sdkmanager, to keep the toolchain in one place. The download script ran
`brew install ninja` as well, before it was established that the sdkmanager
cmake package already ships ninja in its own `bin/`. **Both are now present
and the wrong one wins on PATH**: brew's 1.13.2 at `/opt/homebrew/bin` is
found first, the SDK's 1.10.2 is not on PATH at all. The build must name
the SDK binary by full path, or it silently picks up Homebrew's. Neither
`cmake` nor the SDK `ninja` is on PATH; both need their full path.

### What none of this says

Nothing has been built and nothing has been run. Ten verified files on a Mac
is not a model on a phone: the binary is not compiled, nothing has been
pushed to `/data/local/tmp`, and no tok/s figure exists for this handset at
any quantisation. The sizes above are file sizes on disk, not peak RSS —
peak RSS during generation is roughly the GGUF size plus the KV cache, and
it is the number that decides feasibility. It remains unmeasured.

## 2026-09-15 (evening, last) — the settling curve flattens at ~2.0 GB, and an independent audit found six things wrong in CLAUDE.md

Third and final `/proc/meminfo` reading on the same untouched boot, plus the
corrections from a read-only audit of this repo. Nothing was built, nothing was
installed, nothing on the phone was changed. Conditions identical to the first
two readings: booted ~19:59, unlocked by hand ~20:04 and not touched since,
home screen only, no app opened, our app `pm disable-user`d, `vm list` returning
`Running VMs: []` at every reading, Terminal app down, AC power, screen on.

### The curve, three readings, one boot

                        5.8 min        25.3 min       60.5 min    25.3 -> 60.5
    MemTotal          5,718,280 kB   5,718,280 kB   5,718,280 kB          --
    MemFree              75,380 kB   1,220,200 kB   1,057,296 kB    -162,904
    MemAvailable        940,640 kB   2,119,020 kB   2,012,348 kB    -106,672
    Cached            1,094,964 kB   1,124,860 kB   1,179,608 kB     +54,748
    AnonPages         3,313,572 kB   1,726,304 kB   1,855,920 kB    +129,616
    SwapTotal         3,145,724 kB   3,145,724 kB   3,145,724 kB          --
    SwapFree          2,574,588 kB   1,074,428 kB   1,187,836 kB    +113,408
    Zram physical       197,464 kB     496,856 kB     483,388 kB     -13,468
    dumpsys Free RAM  3,068,208 kB   3,748,470 kB   3,657,804 kB     -90,666

**25 min was close enough to the plateau.** The whole movement is in the first
leg: `MemAvailable` +1,178,380 kB from 5.8 to 25.3 min, then **-106,672 kB**
from 25.3 to 60.5 — a 5.0% fall, in the opposite direction. The phone is not
still climbing; it is oscillating around roughly 2.0 GB.

**The second leg ran the compression backwards, slightly.** From 25.3 to 60.5
min `SwapFree` went UP 113,408 kB while `AnonPages` also went UP 129,616 kB and
zram physical went DOWN 13,468 kB. Pages were faulted back OUT of zram into
ordinary anonymous memory — decompressed — which is the reverse of what the
first leg did. Something on the phone touched pages it had let go cold. Nothing
was running that we started, and what did it was not identified.

**So the idle figure for the 6a, read three times on one boot with nothing
running, is `MemAvailable` ~2.0 GB of 5.72 GB total.** The 919 MB of the first
reading stays correct as measured and wrong as a budget; ~2.12 GB at 25 min was
0.1 GB optimistic. Quote **2,012,348 kB at 60.5 min** as the figure with the
most settling behind it, and quote the uptime with it every time.

**Swap stays two-thirds spent at idle** — `SwapFree` 1,187,836 of 3,145,724 kB,
better than 25 min's 1,074,428 but nowhere near the 2,574,588 of 5.8 min. That
is headroom a model run eats into, and a model's working set is hot anonymous
memory that cannot be compressed away while it is in use. `MemAvailable` on its
own is still not a plan. **Peak RSS plus KV cache during generation is the
number that decides anything and it is still unmeasured** — that is tomorrow.

`dumpsys meminfo` must keep being quoted alongside: 3,657,804 kB free at 60.5
min, of which 2,199,920 kB is cached app processes Android will kill on demand
and 987,048 kB is genuinely free. The two numbers measure different things and
neither replaces the other.

### What the audit found. Six corrections, all verified before writing

**1. THE APK HASH AND PATH IN CLAUDE.md WERE WRONG.** Read off the phone at
~20:50, app disabled, 58 min uptime:

    adb shell sha256sum $(adb shell pm path com.pennyspike.probe2a | sed 's/package://')
    9efe27cb0aa802243123be26bcc0ee5ff9da52f6685a20831a699daf699902fe
      /data/app/~~AfIhpIXq9pYEyRdOP2bSQw==/
      com.pennyspike.probe2a-nou1Eur-h0XqviaxcCE9ww==/base.apk

CLAUDE.md recorded `2e89918fdd783dac...` at
`/data/app/~~F-GoV1zaM_NBcthOOhbUPQ==/...-5mEXn4-0AsY5FdvzYVuz6Q==`. Both the
hash and the directory named an earlier install. **The phone's APK is
byte-identical to `probe2a/build/probe2a.apk` on this Mac** — same
`9efe27cb...`, same 156,613 bytes, written 15 Sept 16:51, which is after
`PennySoakService` was saved. So the soak service IS in the installed APK, the
on-disk build HAS been installed, and the audit's alternative case does not
apply. Corrected in CLAUDE.md with the command to re-check it.

**2. `pm enable` IS NOT SAFE AS THINGS STAND, and this is the one that could
have cost a store.** The installed APK's `BootReceiver` starts six services, two
of which each ask for 2048MB on the next boot: `PennySoakService` immediately
and `Penny3giService` after ~15-45s. That is exactly the two-2GB-VM trap already
recorded from 15 Sept evening, which took `com.android.launcher3` and our own
app at **adj 100**, 50 kills, settling on the third attempt. And
`Penny3evService.java` ~345-352 deletes the `penny3ev` store and recreates it on
**any** `run()` failure — the recovery path that logs `STORE WAS RESET`. So an
enable under that pressure can destroy the verified 64MB store, ck64
`0x757b795dd5138044`. **Recorded as a warning in LIVE DEVICE STATE, not fixed.**
Neutralising one of the two 2GB services is a code change and it waits.

**3. `BootReceiver` STARTS SIX SERVICES, AND CLAUDE.md SAID FIVE IN SIX
PLACES.** Counted in the source: `VmService`, `MicFgsService`, `Penny3dService`,
`Penny3evService`, `Penny3giService`, `PennySoakService`. Five hold a VM;
`MicFgsService` holds none, which is why only five VM names appear in the
device-state block. All six places corrected, and each now distinguishes the
boot that was measured (five services) from the APK now installed (six).
**"All five foreground services took the exemption on one boot" stays true of
that boot and is not a claim about six.** Six on one boot is untested.

**4. `sh build.sh` WITH NO PAYLOAD VARIABLES SET IS NOW DESTRUCTIVE, AND IT WAS
HARMLESS UNTIL THE NDK ARRIVED.** Read off the script, not run. `CLANG` used to
resolve to empty, so the `else` branch was dead. Now it resolves, and a bare run
does two things:

- compiles `payload/penny_payload.c` with `"$CLANG" -shared -fPIC -O2` and
  **none** of the flags recorded as not optional — no `-nostdlib`,
  `-ffreestanding`, `-fno-builtin`, `-fno-stack-protector`,
  `-Wl,-z,max-page-size=4096` or `-Wl,--hash-style=sysv` — and at `-O2` where
  every proven payload was `-O1`. Whether that binary would load in microdroid
  is **untested**, and beside the point: it is not the binary any rung was
  answered with.
- packages **only** `PennyPayload.so`. Every `cp` and `zip` for the other four
  sits behind `if [ -n "$PENNY_PAYLOAD_3*_SO" ]`. Rungs 3c, 3d, 3e-ii, 3e-iii,
  3e-v, 3f, 3g-i, 3g-ii and 3h all name a payload that would not be in the APK,
  and installing it would strand every store as well.

Recorded as a trap; the CLANG-resolves-to-empty line is corrected. **Not fixed.**

**5. `debug.keystore` AND `build-payloads/` ARE GIT-IGNORED AND EXIST ONLY ON
THIS MAC.** `.gitignore` carries `probe2a/build/`, `probe2a/debug.keystore` and
`probe2a/build-payloads/`. The keystore is 2,602 bytes, dated 14 Sept 14:10.
Losing it means the next install is signed with a different key, which Android
refuses as an update — so it becomes an uninstall and a fresh install, and
**every encrypted store on the device dies with it**, `penny3ev`'s verified 64MB
file included. `probe2a/build-payloads/` holds the only copies of the five
payloads: `PennyPayload.so` 6,208 B and `Penny3cPayload.so` 6,504 B (15 Sept
11:25), `Penny3eiiPayload.so` 10,712 B (12:45), `Penny3eiiiPayload.so` 11,064 B
(13:12), `Penny3fPayload.so` 15,392 B (14:45). Each was compiled by hand in the
Debian guest on the phone, and the Terminal app has to be opened by hand to do
it again. **A backup command was issued; whether the .so files should be
committed is a question for Matt and is not decided here.**

**6. Four smaller ones.** `PennySoakService.java`'s header claimed rung 3e-v had
eight reproductions — it has **two** (the pre-unlock read and the after-unlock
control down the same socket); eight is rung 3's VM-wake count. Corrected in the
comment. `penny3f_payload.c` claimed its FNV-1a "folds to 32 bits at the end" —
it does not, and every value it has reported is 16 hex digits, e.g. ck64
`0x757b795dd5138044`. Corrected in the comment. **Both are comment-only edits to
source that has already been compiled; no `.so` was rebuilt and the five files
in `build-payloads/` are untouched. The source files no longer hash to what
built them.** CLAUDE.md's "before any app code, establish whether a JDK is
present" was answered on 14 Sept and is struck through. And rung 3g-i's write-up
quoted exit code 46 in a way that reads as a success signal: **46 is an
identifier of which payload path ran, `status=0` is the verdict**, and a run
that answered nothing would log 46 just the same. Both places corrected.

### What this entry does NOT say

The 60.5-minute reading is one reading at one uptime on one boot — three points
is a curve flattening, not a plateau proven, and no reading was taken past 60
min. What decompressed ~113 MB back out of zram between 25 and 60 min was not
identified, only observed. **No measurement here involves a model**: peak RSS
and KV cache during generation remain unmeasured, which is the number that
decides feasibility, and it is tomorrow's first job. Every audit finding except
the APK hash was read out of source or `.gitignore` rather than exercised — the
`build.sh` bare run was NOT performed, the two-2GB-VM pressure was NOT re-run,
and `pm enable` was NOT issued. Nothing was fixed in code: items 2 and 4 are
recorded warnings only. Nothing was pushed to the phone and the app is still
disabled.

**Correction to the entry above, same evening.** `penny3ev`'s store is **not
"believed INTACT"** — it is PROBABLY STRANDED. It was verified at 16:31:39 under
the fifth install; the sixth build went on at 16:51 (sha256 `9efe27cb...`,
confirmed on the phone tonight), and by this repo's own `getOrCreate`
stale-config trap that reinstall invalidates the VM's stored APK path, so
`Penny3evService`'s recovery path deletes and recreates the store on its next
`run()`. Whether it already did so on the 19:36 boot — where that service ran
alongside the soak — **cannot be answered**: logcat dies on reboot and the
buffer's oldest line is now 09-15 19:59:34, checked ~21:05. LIVE DEVICE STATE
corrected. Parked, not investigated; the ck64 is derivable from the fixed-seed
generator, so the file is re-creatable rather than lost.

**Fourth reading, same untouched boot, 120.6 min uptime.** `Running VMs: []`,
app still disabled, nothing opened, AC power, screen on — conditions identical
to the other three.

                        5.8 min      25.3 min      60.5 min     120.6 min
    MemAvailable      940,640 kB  2,119,020 kB  2,012,348 kB  1,929,032 kB
    MemFree            75,380 kB  1,220,200 kB  1,057,296 kB    938,232 kB
    Cached          1,094,964 kB  1,124,860 kB  1,179,608 kB  1,214,492 kB
    AnonPages       3,313,572 kB  1,726,304 kB  1,855,920 kB  1,942,980 kB
    SwapFree        2,574,588 kB  1,074,428 kB  1,187,836 kB  1,261,820 kB
    Zram physical     197,464 kB    496,856 kB    483,388 kB    477,172 kB
    dumpsys Free RAM 3,068,208 kB 3,748,470 kB  3,657,804 kB  3,527,022 kB

**THE ENTRY ABOVE CALLED THIS OSCILLATION. WITH A FOURTH POINT IT IS NOT — it
is a slow monotonic decline.** `MemAvailable` falls at every step after the
25-minute peak: 2,119,020 -> 2,012,348 -> 1,929,032 kB, i.e. -106,672 then
-83,316. So does `MemFree`, and so does `dumpsys` Free RAM (3,748,470 ->
3,657,804 -> 3,527,022 kB). Three falls in a row with the same signature is a
trend, not noise in one direction three times; "oscillating around 2.0 GB" was
wrong and is corrected here.

**The signature is the same on both legs and it is the first leg running
backwards.** 25.3 -> 120.6 min: `AnonPages` +216,676 kB, `SwapFree` +187,392 kB,
zram physical -19,684 kB. Cold anonymous pages are being faulted back OUT of
zram and decompressed into ordinary memory, steadily, on a phone nobody is
touching. `Cached` climbs +89,632 kB over the same span. What is doing it was
observed, not identified.

Rate over the 95.3 minutes from the peak: **~2.0 MB/min of `MemAvailable`
lost.** Extrapolating that is not supported by four points and is not done here.

**The practical consequence for tomorrow is unchanged in shape and firmer in
detail: there is no single idle number for this phone.** ~2.12 GB at 25 min,
~2.01 GB at 60 min, ~1.93 GB at 121 min, all on one untouched boot with nothing
running. Every `MemAvailable` reading taken around a benchmark run MUST carry
its uptime, and a before/after pair around one run is only comparable to itself.
Note also that `dumpsys` cached-pss stayed flat near 2.2 GB across all three
later readings while genuinely-free RAM fell 1,136,820 -> 987,048 -> 828,752 kB:
the reclaimable pool is not what is shrinking.

**What this does NOT say.** Four readings on one boot, all idle, all on AC power
with the screen on. Nothing was running and nothing was measured under load, so
this describes an untouched phone drifting and says nothing about what happens
once a model is resident. It does not establish a floor — no reading was taken
past 120.6 min, and whether the decline continues, flattens or reverses is
unknown. The cause of the steady decompression was not investigated.

**Transcription fix to the table immediately above, made minutes after it was
committed (d6229ea).** Its `SwapFree` row carried `2,574,588 kB` in the 25.3-min
column, which is the 5.8-min value repeated. The correct figure is
**1,074,428 kB**, as recorded in the first evening entry's two-reading delta
table and unchanged since. Corrected in place rather than left to mislead; the
cell is the only thing altered, and no figure derived from it changes — the
25.3 -> 120.6 min `SwapFree` movement quoted in the prose (+187,392 kB) was
computed from the right number.

## 2026-09-16 — llama-bench cross-compiles for the 6a, with dotprod and without i8mm. Four things in the protocol written last night were wrong, and the phone is not on the bus.

First half of step 3. The binary exists, it is aarch64, it carries the
instructions this handset has and none of the ones it does not. Nothing has
been pushed and nothing has been run. Step 0 did not complete — see the end.

### The configure line, exactly as run

Run from `~/Documents/llama.cpp` at commit
`38a5b42d9a3e82e0a586bcd1caed121f36c87a73` ("HIP: Enable AllReduce for ROCm
(#27825)"), working tree clean. `build-android/` was `rm -rf`'d first so this
is a configure from nothing.

    NDK=/opt/homebrew/share/android-commandlinetools/ndk/30.0.16248370
    SDKCM=/opt/homebrew/share/android-commandlinetools/cmake/3.22.1/bin

    "$SDKCM/cmake" \
      -DCMAKE_TOOLCHAIN_FILE=$NDK/build/cmake/android.toolchain.cmake \
      -DANDROID_ABI=arm64-v8a \
      -DANDROID_PLATFORM=android-28 \
      -DCMAKE_BUILD_TYPE=Release \
      -DBUILD_SHARED_LIBS=OFF \
      -DGGML_NATIVE=OFF \
      -DGGML_CPU_ARM_ARCH=armv8.2-a+dotprod+fp16 \
      -DGGML_OPENMP=OFF \
      -DGGML_LLAMAFILE=OFF \
      -DLLAMA_OPENSSL=OFF \
      -DLLAMA_BUILD_EXAMPLES=OFF \
      -DLLAMA_BUILD_SERVER=OFF \
      -DLLAMA_BUILD_TESTS=OFF \
      -G Ninja \
      -DCMAKE_MAKE_PROGRAM="$SDKCM/ninja" \
      -B build-android

    "$SDKCM/cmake" --build build-android --target llama-bench -j 8

Both `cmake` and `ninja` are named by full path, so the SDK's ninja 1.10.2 is
used and Homebrew's 1.13.2 on `PATH` is not. No network was reached by either
step.

### `GGML_CPU_ARM_ARCH` is the right knob, and a global `-march` is not

**The protocol written last night said to pass `-march=armv8.2-a+dotprod+fp16`.
The flag value is right; the mechanism is not, and llama.cpp provides a
targeted one.** `docs/build.md:669` at this commit warns specifically against
global `-march` flags because they "raise the baseline instruction set for
generic code" — every source file in the project, not just the kernels.
`ggml/CMakeLists.txt:184` exposes `GGML_CPU_ARM_ARCH`, which
`ggml/src/ggml-cpu/CMakeLists.txt:172-173` turns into `-march=<value>` appended
to `ARCH_FLAGS` **for the `ggml-cpu` target alone**. Same instruction set where
it is wanted, stock baseline everywhere else.

**Passing nothing at all would have been the real mistake, and it is the
default this doc recommends.** With `GGML_NATIVE=OFF` — which is mandatory for
cross-compilation — and neither `GGML_CPU_ARM_ARCH` nor `GGML_CPU_ALL_VARIANTS`
set, the `else()` branch at `ggml/src/ggml-cpu/CMakeLists.txt:171-216` adds no
`-march` whatsoever. The NDK's `arm64-v8a` baseline is plain `armv8-a`: no
dotprod, no fp16 vector arithmetic. That binary would run on this phone and
would understate it, because llama.cpp's Q4_0 repack path and its quantised dot
kernels are compiled behind `__ARM_FEATURE_DOTPROD`. A feasibility figure from
a build with dotprod compiled out is not a feasibility figure for this silicon.

### The configure proved the flag before any binary existed

cmake's own feature probes, quoted from the configure output:

    -- Checking for ARM features using flags:
    --   -march=armv8.2-a+dotprod+fp16
    -- Performing Test HAVE_DOTPROD                  - Success
    -- Performing Test HAVE_SVE                      - Failed
    -- Performing Test HAVE_MATMUL_INT8              - Failed
    -- Performing Test HAVE_FMA                      - Success
    -- Performing Test HAVE_FP16_VECTOR_ARITHMETIC   - Success
    -- Performing Test HAVE_SME                      - Failed
    -- Adding CPU backend variant ggml-cpu: -march=armv8.2-a+dotprod+fp16

`HAVE_MATMUL_INT8` is i8mm. Failed, SVE failed, SME failed, which is the whole
point: Tensor G1's Cortex-X1/A76/A55 have none of the three, and a binary
containing them dies with SIGILL in a way that reads as a broken build rather
than a wrong flag.

### The binary

    build time         25 s wall, -j 8, 131 ninja steps, from a clean
                       build-android/. Configure was a separate invocation
                       and was not timed.
    compiler           Android clang 21.0.0, NDK 30.0.16248370
                       (16134705, +pgo, -bolt, +lto, -mlgo, based on r574158c)
    ggml version       0.24.0, ggml commit 38a5b42d9
    unstripped         105,462,376 B, with debug_info
                       sha256 2bb2a48e1f411f1527655a422997de19192fe7cb
                              24d1d274a47f96fec3e4d084
    stripped           4,708,216 B  <- this is the one to push
                       sha256 44015c0614b3f1c0f4ee3240fb8f3a37503420ab
                              7285a36a10ad14abaaaeb84e
    file               ELF 64-bit LSB pie executable, ARM aarch64, version 1
                       (SYSV), dynamically linked, interpreter
                       /system/bin/linker64, stripped
    NEEDED             libm.so, libdl.so, libc.so — and nothing else

**"Static" means static against ggml, llama and libc++, not a static ELF, and
the distinction must not be blurred.** `BUILD_SHARED_LIBS=OFF` plus the NDK's
default `c++_static` means there is no `libllama.so`, no `libggml*.so` and no
`libc++_shared.so` to push and no `LD_LIBRARY_PATH` to set — which is the whole
reason the protocol asked for it. The three `NEEDED` entries above are bionic,
which every Android process links and which cannot be statically linked on a
general Android binary. `docs/android.md`'s `LD_LIBRARY_PATH=lib` instruction
does not apply to this build.

### `readelf -A` DOES NOT WORK ON aarch64, and the protocol named it as the check

Written into CLAUDE.md last night, before the build, as "Verify before pushing:
`readelf -A` on the binary must not list `i8mm` or `sve`." Run against this
binary it returns:

    BuildAttributes {
    }

An empty block. The `Tag_CPU_arch` build attributes that check relies on are a
32-bit ARM ELF feature; aarch64 objects do not carry them. **A check that
returns empty for every binary passes every binary, including one full of
`smmla`.** It would have given a clean bill of health to exactly the build it
was written to catch.

**The check that does work is to disassemble and count instructions.** Over
900,738 lines of `llvm-objdump -d`:

    i8mm      smmla / ummla / usmmla            0      must be 0
    SVE/SME   ptrue / whilelo / smstart /
              smstop / any z<n>. register       0      must be 0
    dotprod   sdot / udot                     898      must be > 0
    fp16      any .8h vector operand         1566      expected > 0

The two zeroes are the safety check. **The 898 is the one worth having**: it is
positive evidence the dotprod kernels were compiled in, which the absence-only
check could never have given, and it is the difference between measuring this
handset and measuring a generic armv8-a.

### Three more things in last night's protocol bullet that are wrong

**1. `docs/android.md` does not suggest `armv8.7a`.** The bullet's stated reason
for the flag was that the doc recommends `armv8.7a`. At commit 38a5b42d it
recommends the opposite, in as many words: "Do not add a global `-march` flag
unless you intentionally want to raise the baseline instruction set for every
compiled source", and `docs/build.md:669` adds "Global -march flags such as
`-march=armv8.7a` flag are not required for a portable Android `arm64-v8a`
build." The only `armv8.7a` in the tree is
`docs/backend/snapdragon/CMakeUserPresets.json`, which is a Snapdragon preset
and carries `+i8mm` explicitly. **The conclusion — do not use armv8.7a here —
is right and is now better supported than it was. The premise was wrong.**
Presumably true of an older revision of that doc; not checked, and not worth
checking.

**2. `LLAMA_CURL=OFF` is a dead option.** `CMakeLists.txt:195` lists it under
`llama_option_depr(WARNING LLAMA_CURL)` with no replacement mapping. Passing it
produces a deprecation warning and changes nothing. The live option that keeps
the network library out is `LLAMA_OPENSSL=OFF` (`CMakeLists.txt:144`, default
ON), which is what was passed and what `docs/android.md` names.

**3. `llama-cli` was not built, and building it would have wanted the
network.** `tools/CMakeLists.txt:23-27` puts `cli` inside
`if (LLAMA_BUILD_SERVER)`, alongside `ui` and `server` — at this commit
`llama-cli` links `llama-server-impl`, so it cannot be had without the server
tree. `tools/ui/CMakeLists.txt` then provisions prebuilt web assets from a
Hugging Face bucket (`LLAMA_UI_HF_BUCKET "ggml-org/llama-ui"`) **at build
time**. The Mac is on a phone tether and nothing is being downloaded, so the
build was stopped at `llama-bench`. `llama-bench` supplies every column the
benchmark protocol asks for — pp tok/s, tg tok/s, threads, `-p` — so nothing in
the protocol is blocked by this. `llama-cli` is only wanted for an interactive
sanity check, and it is Matt's call whether it is worth a download later.

### Step 0 did not complete: the phone is not on the USB bus

Attempted before the build, 12:05 on 16 Sept.

    adb devices                  List of devices attached   (empty)
    system_profiler SPUSBDataType   0 devices with a Product ID

**Zero devices on the bus means macOS sees nothing at all**, so no adb question
can help — this repo's own trap entry says to run `system_profiler` first for
exactly this reason, and it was. Cable, or the GrapheneOS charging-only-while-
locked behaviour, or a dead data path; nothing here distinguishes them. So
**uptime, `vm list`, `pm path` and `MemAvailable` are all unread**, and whether
the phone rebooted overnight is unknown. The last recorded state — app
`pm disable-user`d, `Running VMs: []`, booted ~19:59 on 15 Sept — is from last
night and is not confirmed today.

### What this entry does NOT say

**Nothing has been run and nothing has been pushed.** There is no tok/s figure
for this handset at any quantisation, no peak RSS, no KV cache measurement, and
no model on the phone. The instruction counts prove what the compiler emitted,
not that the binary executes — it has never been run on any device, and the
SIGILL this build was shaped to avoid is unobserved rather than avoided until
it runs. 25 s is one build on one Mac and is recorded because the protocol asked
for it, not because it means anything. `GGML_CPU_ARM_ARCH=armv8.2-a+dotprod+fp16`
is an argued choice, not a measured one: no build at the plain `armv8-a`
baseline was made, so the claim that dotprod matters here is llama.cpp's source
structure plus this repo's 3f reading of `asimddp` in the guest's feature line,
and is **not** a measured speed difference on this phone. Whether KleidiAI
(`GGML_CPU_KLEIDIAI`, off here and off by default) would beat these kernels is
untested. And step 0's device checks are outstanding, so every figure below
this line waits on a phone that is currently not visible.

## 2026-09-16 — step 0, and llama-bench RUNS on the phone. Fifth memory reading on the same 16-hour boot: the decline decelerates. `system_profiler` is a dead check and the trap that names it is now misleading.

Device state read, the binary pushed and executed, the prediction written
before any model is pushed. No model is on the phone and no benchmark has
been run.

### Step 0, read 12:13 on 16 Sept

    adb                    ALIVE, device 25301JEGR11115
    phone clock            Wed Sep 16 12:13:13 BST 2026
    uptime                 58,392.47 s = 973.2 min = 16.22 h
    vm list                Running VMs: []
    crosvm / app process   NONE
    pm path                /data/app/~~AfIhpIXq9pYEyRdOP2bSQw==/
                           com.pennyspike.probe2a-nou1Eur-h0XqviaxcCE9ww==/base.apk
    pm list packages -d    com.pennyspike.probe2a  -> still DISABLED
    /data free             102 G of 110 G

**The phone did NOT reboot overnight.** 973.2 minutes before 12:13 is ~19:59
on 15 Sept, which is the boot the four memory readings were taken on. So this
is a fifth reading on that same boot, and the APK path is unchanged from the
one read off the phone at ~20:50 last night.

**But "untouched" cannot be claimed and is not claimed.** The four readings
last night were on a phone verified as sitting at the home screen. Between
21:05 last night and 12:13 today the phone was out of contact, was unlocked at
some point (adb requires it on this build), and what if anything was opened is
unknown. Conditions today are: same boot, our app disabled, no VM, AC power —
**not** the "untouched since 20:04" of last night's series.

### The fifth memory reading, and it corrects an extrapolation nobody made

                        5.8 min      25.3 min     60.5 min    120.6 min    973.2 min
    MemAvailable      940,640 kB  2,119,020 kB 2,012,348 kB 1,929,032 kB 1,771,920 kB
    MemFree            75,380 kB  1,220,200 kB 1,057,296 kB   938,232 kB    79,008 kB
    Cached          1,094,964 kB  1,124,860 kB 1,179,608 kB 1,214,492 kB 1,873,292 kB
    AnonPages       3,313,572 kB  1,726,304 kB 1,855,920 kB 1,942,980 kB 2,025,388 kB
    SwapFree        2,574,588 kB  1,074,428 kB 1,187,836 kB 1,261,820 kB 1,266,044 kB
    dumpsys Free RAM 3,068,208 kB 3,748,470 kB 3,657,804 kB 3,527,022 kB 3,552,852 kB

**The decline is real and it is decelerating, sharply.** Last night's entry
measured ~2.0 MB/min of `MemAvailable` lost between 25.3 and 120.6 min and
explicitly declined to extrapolate it. That restraint was right: the rate from
120.6 to 973.2 min is **-157,112 kB over 852.6 min = 0.18 MB/min**, an eleventh
of it. Holding 2.0 MB/min would have predicted the phone running out entirely
before morning. The curve peaks at ~25 min, falls quickly, then flattens.

**So the figure with the most settling behind it is `MemAvailable` 1,771,920 kB
at 973.2 min uptime**, and it is ~157 MB below the 120-minute reading and ~240
MB below the 60-minute one. The practical rule is unchanged and now has a
sixteen-hour point behind it: **there is no single idle number, and every
reading must carry its uptime.**

**The mechanism changed between the two legs, which is why the rate did.** From
120.6 to 973.2 min, `SwapFree` barely moved (+4,224 kB) — the steady
decompression out of zram that drove the 25-to-120-minute leg has stopped.
What moved instead is `Cached`, +658,800 kB, and `MemFree`, which collapsed
from 938,232 to 79,008 kB. The kernel spent the night turning free pages into
page cache, which is ordinary and mostly reclaimable; `dumpsys` agrees the
reclaimable pool is intact (2,194,608K cached pss + 1,237,884K cached kernel,
against only 120,360K genuinely free). **The two figures diverge further than
ever here** — 1.77 GB from `MemAvailable` against 3.55 GB of `dumpsys` Free RAM
— and both must still be quoted.

### `system_profiler SPUSBDataType` RETURNS NOTHING ON THIS MAC, AND THE TRAP SAYS TO TRUST IT

This is the correction that matters most, because the trap it breaks is one
that cost an afternoon and has been relied on three times since.

CLAUDE.md says: "Run `system_profiler SPUSBDataType` on the Mac **before**
`adb devices` — empty output means macOS sees nothing on the bus at all."
Measured today, with the phone connected and `adb devices` reporting
`25301JEGR11115  device`:

    $ system_profiler SPUSBDataType
    $ echo $?
    0

**Empty output, exit 0, with a live device on the cable.** The command produces
nothing on this Mac today whether or not anything is plugged in, so it
distinguishes nothing. It is not a check; it is a constant.

**This invalidates the evidence in the 16 Sept entry above, though not its
conclusion.** That entry reported "zero devices on the bus" at 12:05 as if it
established something. It did not — `adb devices` was independently empty at
that moment, and the phone genuinely was not reachable, so the conclusion held
for a different reason than the one given. The `system_profiler` line in that
entry should be read as worthless, not as corroboration. Earlier entries are
never rewritten in this repo; this is the later one and it wins.

Why the command is empty was not investigated. Candidates not tested: a macOS
26 change to the `SPUSBDataType` data type, a permissions gate, or the Mac's
own USB topology reporting moving elsewhere. **The replacement check is
`adb devices` itself**, which is the thing actually being asked, plus
`adb get-state`. The trap is corrected in CLAUDE.md in the same commit.

### The binary RUNS. The SIGILL this build was shaped to avoid is now avoided, not merely unobserved.

    adb push bin/llama-bench-stripped /data/local/tmp/llama-bench
      4,708,216 bytes in 0.032 s
    adb shell chmod 755 /data/local/tmp/llama-bench
    adb shell sha256sum /data/local/tmp/llama-bench
      44015c0614b3f1c0f4ee3240fb8f3a37503420ab7285a36a10ad14abaaaeb84e
      -- byte-identical to the Mac's copy
    adb shell /data/local/tmp/llama-bench --help
      prints the full usage block, EXIT=0

**Three things settled at once by one 4.7 MB push, before spending 1.1 GB on a
model.** `/data/local/tmp` grants the `shell` user execute — it is
`drwxrwx--x shell shell`, and the binary ran. The aarch64 build loads and links
against bionic with no `LD_LIBRARY_PATH` and no `.so` alongside it, which is
what `BUILD_SHARED_LIBS=OFF` was for. And it did not die with SIGILL, which is
the failure the whole `-march` argument was about — the 16 Sept entry above was
careful to say that failure was "unobserved rather than avoided"; it is now
avoided.

`/data/local/tmp` already contains a `microdroid/` directory dated 14 Sept from
the rung work. **Not touched, not deleted, and nothing here needs it gone.**

### The host's cores, read off the phone, and the taskset masks check out

    processor  0 1 2 3   CPU part 0xd05  Cortex-A55  max 1,803,000 kHz
    processor  4 5       CPU part 0xd0b  Cortex-A76  max 2,253,000 kHz
    processor  6 7       CPU part 0xd44  Cortex-X1   max 2,802,000 kHz

    Features: fp asimd evtstrm aes pmull sha1 sha2 crc32 atomics fphp
              asimdhp cpuid asimdrdm lrcpc dcpop asimddp

`asimddp` is dotprod and `fphp`/`asimdhp` are fp16 — the two the build enabled.
**`i8mm` and `sve` do not appear**, which is the device confirming from its own
side what the build was shaped around.

**The protocol's masks are hex CPU masks and they land where the labels say**,
which is worth stating because the big cores are numbered last on this chip and
the obvious reading of "c0" is wrong:

    taskset c0  = 0xc0 = 1100 0000 = cpus 6,7      = the X1 pair
    taskset f0  = 0xf0 = 1111 0000 = cpus 4,5,6,7  = X1 pair + A76 pair

### THE PREDICTION, written before any model is on the phone

Recorded now so the write-up is judged against it rather than fitted to it.
The standing prediction in CLAUDE.md is "token generation barely improves
beyond 2 threads (memory-bandwidth bound); prompt processing scales." Made
specific, for **Qwen3-1.7B Q4_K_M** (unsloth, 1,107,409,472 bytes):

    P1  tg at 2 threads pinned to c0 (X1 pair) lands between 8 and 16 tok/s.
    P2  tg at 4 threads on f0 is LESS than 1.3x the 2-thread c0 figure.
        Token generation reads essentially the whole model per token, so it
        is bounded by memory bandwidth, and two more cores do not add any.
    P3  pp512 DOES scale: pp at 4 threads on f0 is at least 2x pp at
        1 thread on c0. Prompt processing is a batched matmul and is
        compute-bound.
    P4  unpinned 4 threads is no faster than pinned f0, and probably slower,
        because the scheduler can place a thread on an A55 at 1.80 GHz and
        every layer waits for the slowest thread.
    P5  peak RSS lands between 1.1 and 1.4 GB, and NO lowmemorykiller kills
        occur during any Qwen3-1.7B run. MemAvailable was 1,771,920 kB at
        973.2 min uptime.
    P6  -p 64 returns a LOWER pp tok/s than -p 512, because a smaller batch
        amortises the weight reads over fewer tokens.

**P5 is the one that decides feasibility and P1 is the one that decides
usefulness.** If P5 fails the model does not fit this handset; if P1 comes in
far below 8 tok/s the silicon does not run a 1.7B usefully whatever else is
true.

### What this entry does NOT say

**No model has been pushed and no benchmark has been run**, so there is still
no tok/s figure, no peak RSS and no KV cache measurement for this handset at
any quantisation. `--help` exiting 0 proves the binary loads and links; it
executes none of the quantised kernels, so it is not evidence that the dotprod
paths run correctly — only that the binary is not wholesale illegal on this
CPU. The fifth memory reading is one reading at one uptime and the phone's
history over the preceding fifteen hours is unknown, so it extends the curve
without being a controlled continuation of it. Why `system_profiler` returns
empty was observed, not diagnosed. And the six predictions above are
predictions: none of them has been tested.

## 2026-09-16 — the kernels execute and the model talks, and the finding of the day is that peak RSS is 2.13 GiB for a 1.03 GiB model. Two of six predictions already fail.

Smoke run only. One model, one thread count, one 8.7-second run. The matrix
has NOT been run and will not be until the 2.13 GiB is explained.

### CORRECTION to the heading of the 16 Sept step-0 entry above

That entry is headed "the SIGILL this build was shaped to avoid is now
avoided, not merely unobserved." **That was wrong when written and is right
now, for a different and narrower reason.** It rested on `llama-bench --help`,
which executes no quantised kernel at all — and i8mm instructions could only
ever be inside those kernels — so at the time SIGILL was exactly as unobserved
as before. Its own "What this entry does NOT say" section said so; the heading
contradicted it. Earlier entries are never rewritten here, so this is the
correction and it wins.

**What the smoke run now establishes, stated at its true width.** Quantised
kernels ran and returned correct text, so SIGILL is ruled out **for the tensor
types Qwen3-1.7B Q4_K_M actually contains — the Q4_K and Q6_K paths.**
**The Q4_0 repack path has still never executed on this phone.** That is a
different set of kernels, it is where llama.cpp's ARM dot-product repacking
lives, and it is the whole reason half the model set was downloaded at Q4_0.
Nothing here says anything about it.

### The model talks. Verbatim, and this is the half a tok/s figure cannot give.

    $ cd /data/local/tmp && taskset c0 ./llama-simple \
        -m Qwen3-1.7B-Q4_K_M.gguf -n 48 'The capital of France is'

    The capital of France is Paris. The capital of Spain is Madrid. The capital
    of Italy is Rome. The capital of Germany is Berlin. The capital of Japan is
    Tokyo. The capital of Brazil is Brasília. The capital of Mexico is Mexico
    City. The capital

    EXIT=0

Coherent, factually correct in all seven, and the accent in "Brasília" survived
— so the tokeniser round-trips multi-byte UTF-8. **A broken kernel still
reports a tok/s; it does not do this.** That is why `llama-simple` was built,
and it took one 3.8 MB binary and no network.

### The row

Qwen3-1.7B Q4_K_M, unsloth, 1,107,409,472 bytes, sha256
`b139949c5bd74937ad8ed8c8cf3d9ffb1e99c866c823204dc42c0d91fa181897` — computed
ON THE PHONE after the push and matched against MANIFEST.txt, where it is
recorded as VERIFIED against Hugging Face's own published LFS oid. Pushed at
32.2 MB/s in 32.8 s.

    conditions   uptime 59,078.89 s (984.6 min) before, 59,087.56 s after
                 run 8.67 s wall. AC power, screen on, no VM
                 (Running VMs: [] at step 0), our app pm disable-user'd,
                 nothing opened by hand. Model WARM in host page cache —
                 pushed 90 seconds earlier. Not a cold-start figure.
    command      pennybench.sh smoke c0 -- -m <model> -t 2 -p 16 -n 16
    mask         c0 = cpus 6,7 = the Cortex-X1 pair at 2.802 GHz

    pp16                  68.20 +/- 1.84 t/s
    tg16                  18.48 +/- 0.18 t/s
    peak RSS (VmHWM)   2,230,332 kB   = 2.13 GiB
    MemAvailable       2,050,024 -> 2,536,008 kB
    MemFree              193,548 -> 1,247,868 kB
    SwapFree           1,089,660 ->   627,824 kB   (-461,836)
    Cached             2,076,392 -> 1,513,232 kB   (-563,160)
    LMK kills                  2 processes (4 log lines)
    rc                         0

Kills, verbatim, both at the cheapest tier:

    12:24:12.675 lowmemorykiller: Kill 'com.shannon.rcsservice:shannonrcsservice'
      (2843), uid 10151, oom_score_adj 995 to free 160564kB rss, 9456kB anon rss,
      41988kB swap, 0kB dmabuf_pss, 0kB dmabuf_rss;
      reason: low watermark is breached
    12:24:12.742 ActivityManager: Process com.shannon.rcsservice:shannonrcsservice
      (pid 2843) has died: cch  +95 CEM
    12:24:13.552 lowmemorykiller: Kill '.ShannonImsService' (2848), uid 10154,
      oom_score_adj 995 to free 146592kB rss, ... ;
      reason: low watermark is breached
    12:24:13.623 ActivityManager: Process .ShannonImsService (pid 2848)
      has died: cch  +95 CEM

Both are adj 995 and `cch +95 CEM` — cached and empty, nothing a user would
notice, the same tier 3e-iv and 3g-ii recorded from the VM side. **The shape
is familiar; what is new is that a 16-token test on a 1 GB model reached it
at all.**

### PEAK RSS IS 2.13 GiB FOR A 1.03 GiB MODEL, AND THAT IS THE FINDING

**P5 predicted 1.1-1.4 GB and no kills. It fails on both halves.** The
prediction was written before the model was pushed precisely so this could not
be fitted afterwards, and it was wrong by roughly 800 MB — about twice the
file, on the smallest test that will be run all day. **16 tokens in, 16 tokens
out.** Every other run in the protocol is larger.

**It is measured and NOT explained.** The candidate is llama-bench's default
batch and micro-batch sizing allocating compute buffers for far more tokens
than the test uses, with a 151k-vocab logits buffer attached; a second
candidate is llama.cpp making a repacked copy of the weights for the ARM
kernels, which would by itself account for "roughly double the file". Nothing
here distinguishes them and neither is asserted. Three checks follow in order,
each its own run: `-v` for llama.cpp's own buffer accounting, an `RssAnon` /
`RssFile` split in the wrapper, and `-b 16 -ub 16`.

**The consequence for the rest of the protocol, if the ratio holds — and it is
not yet known to hold.** Idle `MemAvailable` on this handset was 2,012,348 kB
at 60.5 min and 1,771,920 kB at 973.2 min. At roughly 2x the file:
Qwen3.5-2B (1,280,835,840 B) lands near 2.4 GiB, and **Gemma 4 E2B
(3,106,738,272 B) lands near 5.8 GiB on a phone with 5.45 GiB of physical
memory.** Gemma may simply not run here. That is an extrapolation from one
measurement and is written as a flag for the next runs, not as a result.

### THE BEFORE/AFTER MEMORY PAIR LIES UNLESS THE KILL COUNT IS BESIDE IT

`MemAvailable` went **UP** across the run, 2,050,024 -> 2,536,008 kB, and
`MemFree` went up by over a gigabyte. Read on its own that says the run cost
nothing and left the phone better off.

**It went up BECAUSE the killer freed memory.** The run evicted 563,160 kB of
page cache, pushed 461,836 kB more into swap, and killed two processes worth
~307 MB of RSS between them. The protocol requires MemAvailable before and
after; **this entry adds that the pair is uninterpretable without the kill
count on the same line**, and a run that killed nothing is not comparable to
one that did. Recorded as a protocol point, not as a trap, because the wrapper
already captures both.

### The predictions so far

    P1  tg 8-16 t/s at 2 threads on c0     18.48 -- ABOVE the band, but this
                                           is tg16 and the matrix uses tg128.
                                           NOT settled; early indication only.
    P2  tg gains <1.3x at 4 threads        untested
    P3  pp scales >=2x 1->4 threads        untested
    P4  unpinned no faster than pinned f0  untested
    P5  peak RSS 1.1-1.4 GB, no kills      **FAILS BOTH HALVES.** 2.13 GiB,
                                           two processes killed.
    P6  -p 64 lower pp t/s than -p 512     untested

### What this entry does NOT say

**One run, one model, one thread count, 8.7 seconds.** No pp512, no tg128, no
thread scaling, no unpinned comparison, no second model, nothing on battery,
nothing under load. The model was warm in page cache from a push 90 seconds
earlier, so this is not a cold-load figure and says nothing about time to first
token from flash. `tg16` and `pp16` are not the protocol's tests and their
numbers must not be quoted as the handset's speed. **The 2.13 GiB peak is
measured and unexplained**, so no conclusion about Gemma or Qwen3.5-2B rests on
it yet. The Q4_0 repack path has never executed. And nothing here is a
native-versus-VM statement: that comparison is closed and this is an absolute
feasibility measurement of the 6a.

## 2026-09-16 — the 2.13 GiB is a repacked SECOND COPY of the weights. 97.9% of it accounted from llama.cpp's own buffer lines. Whether it is a load-time spike or the working set is NOT yet known.

Step 1 of three on the RSS question. Identical row to the smoke run plus `-v`.
No matrix run. Nothing else changed.

### llama.cpp says where the memory went

    load_tensors:   CPU_Mapped model buffer size =  1043.68 MiB
    load_tensors:   CPU_REPACK model buffer size =  1049.96 MiB
    llama_kv_cache:        CPU KV buffer size =       28.00 MiB
    sched_reserve:         CPU compute buffer size =    9.52 MiB
    llama_context:         CPU output buffer size =     0.58 MiB

    total accounted                                 2131.74 MiB
    measured VmHWM   2,230,572 kB                 = 2178.29 MiB
    unaccounted                                      46.55 MiB  (2.1%)

**97.9% of the peak is accounted for, and the excess is one thing: a repacked
duplicate of the weights.** llama.cpp rewrites quantised tensors into
ARM dot-product layouts — the log names `q4_K_8x4` and `q6_K_8x4` — and holds
them in a `CPU_REPACK` buffer alongside the memory-mapped original. The
remaining 46.55 MiB is the binary, the tokeniser caches (`token to piece cache
size = 0.9311 MB`), stack and allocator overhead.

**Not every tensor is repacked**, which is worth recording because the two
buffers are nonetheless almost the same size:

    done_getting_tensors: tensor 'token_embd.weight' (q6_K) (and 113 others)
      cannot be used with preferred buffer type CPU_REPACK, using CPU instead

So the mapped buffer is the whole 1.03 GiB file and the repack buffer is a
second copy of the 114 tensors that could be converted — and that second copy
comes out marginally LARGER than the whole file. Why it is larger rather than
smaller was not investigated.

### THE QUESTION THIS DOES NOT ANSWER, AND IT IS THE ONE THAT MATTERS

**Is 2.13 GiB a load-time spike, or the working set during generation?**
Nothing here says. The argument that it is a spike is specific and plausible:
once a tensor has been repacked, its mmap'd original is never read again, so
those pages are clean, file-backed and reclaimable — the kernel can drop them
under pressure and never needs to write them back. On that reading the phone
pays 2.13 GiB briefly at load and then settles near the repack buffer alone.

**Two claims were drafted and are deliberately NOT made here**: that "any
llama.cpp process on this chip pays it", and that this is "not a benchmark
artefact that would go away in a real app". Both assume the peak is the
steady state, and that is exactly what is unmeasured. They are recorded as
pending, not as findings. Two runs settle it: the `RssAnon`/`RssFile` split,
which says whether the repack copy is anonymous and therefore unreclaimable;
and a `-mmp 0` run, where the loader has no mmap to duplicate.

### The row

    conditions   uptime 59,589.66 s (993.2 min) before, 59,598.01 s after,
                 run 8.35 s wall. AC power, screen on, no VM, app disabled,
                 model warm in page cache. Identical command to the smoke
                 run plus -v.

    pp16                  69.02 +/- 1.25 t/s   (smoke run: 68.20 +/- 1.84)
    tg16                  18.53 +/- 0.20 t/s   (smoke run: 18.48 +/- 0.18)
    peak RSS (VmHWM)   2,230,572 kB            (smoke run: 2,230,332 kB)
    MemAvailable       2,502,796 -> 2,576,932 kB
    MemFree            1,160,216 -> 1,255,964 kB
    SwapFree             664,364 ->   570,588 kB
    Cached             1,566,524 -> 1,544,968 kB
    LMK kills                  0
    rc                         0

**Peak RSS reproduced to within 240 kB of 2,230,332 kB — 0.01%.** Two runs,
same configuration, and the figure is stable enough to treat differences in
later rows as real.

**ZERO kills this time, and the reason is the trap from the previous entry
working in reverse.** This run began with `MemAvailable` at 2,502,796 kB
instead of 2,050,024 kB, because the smoke run's two kills had already freed
that memory. **Same peak RSS, same phone, different starting headroom,
opposite kill outcome.** A kill count is a property of the run AND of what the
phone happened to have free, and neither run's count means anything without
the starting figure beside it.

### The `-b 16 -ub 16` experiment is already answered and was NOT run

Planned as step 3 and dropped on this evidence. `-v` prints:

    llama_context: n_ctx     = 256
    llama_context: n_batch   = 16
    llama_context: n_ubatch  = 16

**llama-bench already sizes batch, micro-batch and context to the test**, so
`-b 16 -ub 16` would have re-run an identical configuration. The compute buffer
it produced is 9.52 MiB, which cannot be part of a 1 GiB discrepancy under any
reading. **The ubatch question is real but belongs to the pp512 row**, where
the default micro-batch and the 151,936-entry vocabulary's logits buffer are
large enough to matter, and it is carried forward to there rather than
abandoned. Replaced in the sequence by a `-mmp 0` run.

### What this entry does NOT say

**Nothing about generation.** The peak was measured across a whole
llama-bench invocation — load, repack, warmup, pp16, tg16 — and VmHWM cannot
say when in that sequence it occurred, which is the entire open question. The
repack buffer is described as anonymous by inference from what it is, and has
NOT been measured as anonymous. Whether repacking can be disabled, and what
that would cost in tok/s, is untested and unlooked-at. No conclusion about
Qwen3.5-2B or Gemma 4 E2B follows from this entry — the extrapolation flagged
in the previous entry stands unresolved, and if the peak is a load-time spike
it is the wrong basis for one. Still one model, one thread count, tiny batch,
warm cache, idle phone, AC power. The Q4_0 repack path has still never run.

## 2026-09-16 — the 2.13 GiB splits almost exactly in half: ~1.10 GiB anonymous and unreclaimable, ~1.04 GiB file-backed. The unreclaimable floor is roughly the file size, not double it.

Step 2 of the RSS chase. `pennybench.sh` extended to track max `RssAnon` and
max `RssFile` alongside `VmHWM` (committed b14764a), then the identical smoke
row re-run. No matrix run. The model, the mask, the thread count and the
`-p 16 -n 16` are unchanged from the two runs before it.

### The split

    peak RSS (VmHWM)      2,230,268 kB   2178.00 MiB
    max VmRSS             2,230,268 kB   2178.00 MiB
    max RssAnon           1,155,516 kB   1128.43 MiB   NOT reclaimable
    max RssFile           1,086,744 kB   1061.27 MiB   reclaimable
    samples                      23 at 5 Hz

**The two maxima sum to 2,242,260 kB, about 12 MB MORE than `VmHWM`.** That is
expected and is why they are tracked separately: `RssAnon` and `RssFile` are
not monotonic and do not peak at the same instant, so their sum is not itself
a peak and must never be quoted as one.

### Both halves reconcile against llama.cpp's own buffer lines

Taking the figures the `-v` run printed, from the entry above:

    ANONYMOUS
      CPU_REPACK model buffer         1049.96 MiB
      CPU KV buffer                     28.00
      CPU compute buffer                 9.52
      CPU output buffer                  0.58
      accounted                       1088.06 MiB
      measured max RssAnon            1128.43 MiB
      difference                        40.37 MiB   allocator overhead, stack

    FILE-BACKED
      CPU_Mapped model buffer         1043.68 MiB
      accounted                       1043.68 MiB
      measured max RssFile            1061.27 MiB
      difference                        17.59 MiB   the binary and bionic

**So the `CPU_REPACK` buffer is anonymous, as inferred in the previous entry
and now measured.** The inference is retired and replaced by the reading.

### THE NUMBER THAT CONSTRAINS THE PHONE IS ~1.10 GiB, NOT 2.13 GiB

Anonymous pages cannot be dropped: to free them the kernel must compress them
into zram or swap them, and a model's weights are touched every token, so in
practice they stay. File-backed clean pages can simply be dropped and re-read
from UFS. **The hard floor for this model is therefore max `RssAnon`,
1,155,516 kB — roughly the size of the GGUF, not twice it.**

**This does NOT mean the 2.13 GiB peak is harmless**, and two specific things
stand against reading it that way.

**First, nothing was reclaimed in this run and that is not evidence.**
`max VmRSS` equals `VmHWM` exactly, so the file-backed half was never dropped
while the process lived. But the run began with `MemAvailable` at 2,538,708 kB
and killed nothing — the kernel had no reason to reclaim anything. **That is
the absence of pressure, not a demonstration that those pages would yield
under it.**

**Second, part of the file-backed half is HOT, and this is the caveat that
qualifies the whole "it is only a load-time spike" argument.** The `-v` run
logged:

    done_getting_tensors: tensor 'token_embd.weight' (q6_K) (and 113 others)
      cannot be used with preferred buffer type CPU_REPACK, using CPU instead

Those 114 tensors were never repacked, so they have no anonymous copy and are
read **out of the mmap** during generation. Their pages are file-backed and
live. Only the mmap'd originals of the tensors that WERE repacked go cold
after load. **What fraction of the 1,086,744 kB is hot rather than cold is
NOT measured here**, and until it is, "the file-backed half is reclaimable"
is true of an unknown portion of it rather than of all of it.

### The row

    conditions   uptime 60,407.04 s (1006.8 min) before, 60,415.46 s after,
                 run 8.42 s wall. AC power, screen on, no VM, app disabled,
                 model warm in page cache. Command identical to the smoke
                 run; only the wrapper changed.

    pp16                  69.04 +/- 0.83 t/s
    tg16                  18.05 +/- 0.21 t/s
    peak RSS (VmHWM)   2,230,268 kB
    max RssAnon        1,155,516 kB
    max RssFile        1,086,744 kB
    MemAvailable       2,538,708 -> 2,605,020 kB
    MemFree            1,210,092 -> 1,290,776 kB
    SwapFree             614,364 ->   492,080 kB
    Cached             1,551,496 -> 1,537,476 kB
    LMK kills                  0
    rc                         0

**Peak RSS across three runs of the identical row: 2,230,332 / 2,230,572 /
2,230,268 kB — a spread of 304 kB, 0.01%.** The measurement is stable enough
that any difference in a later row is a real difference and not noise.

tg16 came in at 18.05 against 18.48 and 18.53 on the two earlier runs — a 2.6%
spread across three runs, which is the scale of run-to-run variation on this
row and is the figure to judge later rows against.

### Prediction for -mmp 0, written before the run

Quoted verbatim, as written before step 3 was run:

> With `-mmp 0` there is no mapping to duplicate: the loader reads each tensor
> straight into its destination, so repacked tensors land directly in the
> repack buffer and only the 114 non-repackable ones need a separate copy.
> **Peak RSS lands near 1.2-1.4 GB rather than 2.13 GB, and RssFile collapses
> to near zero** — the binary and libraries only — with essentially all of it
> anonymous.
>
> **The failure mode that would refute it:** peak stays near 2.1 GB but with
> RssAnon at ~2.1 GB instead of 1.13. That would mean the loader still
> materialises the whole model before repacking, and it would be *worse* than
> mmap, because none of it could then be reclaimed. If that happens, the two
> lines I drafted earlier stand and go into the entry with this as their
> evidence.

The two lines referred to are the ones withheld in the previous entry: that
"any llama.cpp process on this chip pays it" and that this is "not a benchmark
artefact that would go away in a real app". **Both remain unmade.**

### What this entry does NOT say

**Still nothing about generation specifically.** VmHWM, RssAnon and RssFile
are maxima across the whole invocation — load, repack, warmup, pp16, tg16 —
and none of them says at which phase the peak occurred, which remains the open
question. The reclaimability of the file-backed half is argued from what
file-backed clean pages ARE, and is neither measured under pressure nor
apportioned between the cold repacked originals and the 114 hot un-repacked
tensors. Whether repacking can be disabled, and at what cost in tok/s, is
still untested. No conclusion about Qwen3.5-2B or Gemma 4 E2B follows: the
earlier extrapolation from 2.13 GiB is now doubly unsafe, because the binding
figure may be ~1.10 GiB instead. One model, one thread count, tiny batch, warm
cache, idle phone, AC power, 8.4 seconds. The Q4_0 repack path has still never
run, and no pp512 or tg128 has been measured at all.

## 2026-09-16 — no-mmap removes 800 MiB of DEAD repacked originals, not 800 MiB of working set. Peak RSS 1,410,496 kB, 99.6% anonymous, zero kills. `-mmp` does not exist at this commit.

Step 3 of the RSS chase, and the last of it. Same model, same mask, same thread
count, same `-p 16 -n 16` as the three rows before it. No matrix run.

### `-mmp 0` DOES NOT EXIST AT COMMIT 38a5b42d, AND THE FIRST ATTEMPT FAILED

The flag named in the step-2 prediction was taken from older llama.cpp. Run as
written it returned `rc=1`, printed usage, and loaded no model:

    error: invalid parameter for argument: -mmp

The replacement is `-lm, --load-mode <auto|none|mmap|mlock|mmap+mlock|dio>`.
That `none` is the no-mmap mode was read out of the source, not guessed —
`src/llama-model-loader.cpp:559`:

    this->use_mmap = load_mode == LLAMA_LOAD_MODE_MMAP
                  || load_mode == LLAMA_LOAD_MODE_MMAP_MLOCK
                  || load_mode == LLAMA_LOAD_MODE_AUTO;

`LLAMA_LOAD_MODE_NONE` is none of those, so `use_mmap` is false. `-v` was added
as well; step 1 measured that flag's cost at 240 kB of peak RSS, 0.01%.

### llama.cpp's own buffer lines, verbatim from /data/local/tmp/out/nommap.err

    133:  load_tensors: loading model tensors, this can take a while... (load_mode = none)
    980:  done_getting_tensors: tensor 'token_embd.weight' (q6_K) (and 113 others)
            cannot be used with preferred buffer type CPU_REPACK, using CPU instead
    981:  load_tensors:          CPU model buffer size =   243.90 MiB
    982:  load_tensors:   CPU_REPACK model buffer size =  1049.96 MiB
    1199: llama_context:        CPU  output buffer size =     0.58 MiB
    1228: llama_kv_cache:        CPU KV buffer size =    28.00 MiB
    1250: sched_reserve:        CPU compute buffer size =     9.52 MiB

    1185: llama_context: n_ctx                 = 256
    1187: llama_context: n_batch               = 16
    1188: llama_context: n_ubatch              = 16

**The `CPU_Mapped model buffer size = 1043.68 MiB` line of the three mmap runs
is GONE, and a `CPU model buffer size = 243.90 MiB` stands in its place.** That
243.90 MiB is the 114 tensors the repacker cannot convert. Everything else went
straight into `CPU_REPACK`, which is byte-for-byte the size it was under mmap —
1049.96 MiB, unchanged to two decimal places across all four runs.

    ANONYMOUS, accounted
      CPU model buffer (the 114 non-repackable)   243.90 MiB
      CPU_REPACK model buffer                    1049.96
      CPU KV buffer                                28.00
      CPU compute buffer                            9.52
      CPU output buffer                             0.58
      accounted                                  1331.96 MiB
      measured max RssAnon                       1372.45 MiB
      difference                                   40.49 MiB

The 40.49 MiB residue is the same allocator-and-stack overhead step 2 measured
at 40.37 MiB on the mmap path. Two runs, two loaders, the same number.

### The row

    conditions   uptime 61,060.93 s (1017.7 min, 16.96 h) before,
                 61,070.11 s after, run 9.18 s wall. SAME BOOT as all
                 three earlier rows (step 2 was 1006.8 min). AC power,
                 screen on, no VM, app pm disable-user'd, nothing opened
                 by hand. Model warm in host page cache.
    command      pennybench.sh nommap c0 -- -m Qwen3-1.7B-Q4_K_M.gguf
                 -t 2 -p 16 -n 16 -lm none -v
    mask         c0 = cpus 6,7 = the Cortex-X1 pair at 2.802 GHz

    pp16                  67.54 +/- 0.54 t/s
    tg16                  17.96 +/- 0.28 t/s
    peak RSS (VmHWM)   1,410,496 kB   1377.44 MiB   1.345 GiB
    max VmRSS          1,410,496 kB
    max RssAnon        1,405,388 kB   1372.45 MiB   99.6% of peak
    max RssFile            5,272 kB      5.15 MiB   the binary and bionic
    samples                   25 at 5 Hz
    MemAvailable       2,617,156 -> 2,801,012 kB   (+183,856)
    MemFree            1,299,380 -> 1,547,560 kB   (+248,180)
    SwapFree             508,976 ->   227,308 kB   (-281,668)
    Cached             1,539,712 -> 1,476,696 kB   (-63,016)
    LMK kills                  0   (nommap.kills is empty)
    rc                         0

llama-bench's own table, verbatim from `/data/local/tmp/out/nommap.bench`, and
note it prints the load mode as a column of its own:

    | model                    |   size | params | backend | threads |   lm | test |            t/s |
    | qwen3 1.7B Q4_K - Medium | 1.03 GiB | 1.72 B | CPU     |       2 | none | pp16 |   67.54 ± 0.54 |
    | qwen3 1.7B Q4_K - Medium | 1.03 GiB | 1.72 B | CPU     |       2 | none | tg16 |   17.96 ± 0.28 |
    build: 38a5b42d9 (10989)

### The prediction held, and it was quoted in full before the run

> Peak RSS lands near 1.2-1.4 GB rather than 2.13 GB, and RssFile collapses to
> near zero — the binary and libraries only — with essentially all of it
> anonymous.

1.345 GiB, RssFile 5,272 kB, 99.6% anonymous. **Held on all three counts.** The
named failure mode — peak staying near 2.1 GB with RssAnon at ~2.1 GB, meaning
the loader materialises the whole model before repacking — did not occur, so
the two lines withheld since the `-v` entry stay withheld and are now retired
rather than pending. It is not the case that any llama.cpp process on this chip
pays 2.13 GiB; **a process that does not mmap pays 1.35 GiB.**

### WHAT NO-MMAP ACTUALLY REMOVES, AND THE FIRST READING OF IT WAS WRONG

Reported in the message before this entry as a "trade": peak down 800 MiB, but
the unreclaimable anonymous half up 244 MiB. **That framing is wrong and is
corrected here.**

The 249,872 kB that moved into anonymous memory is the 243.90 MiB `CPU model
buffer` — the 114 tensors that cannot be repacked. Under mmap those same
tensors were file-backed, and the step-2 entry already recorded the reason that
does not help: **they are read out of the mapping on every token.** A clean
file-backed page that is touched every token is not spare capacity. Reclaiming
it means faulting it straight back from UFS, which is thrashing, not saving.
It was never available memory in the first place.

So the hot working set is the same figure on both paths:

    with mmap     RssAnon 1128.43 MiB + the ~243.90 MiB of mapping that is
                  read every token                        ~= 1372.3 MiB
    without mmap  RssAnon                                  = 1372.45 MiB

**~1372 MiB, ~1.34 GiB, either way — the two agree to about 0.1 MiB.** Nothing
hot was converted from reclaimable to unreclaimable, because none of it was
reclaimable in any useful sense.

**What no-mmap removes is the part that was genuinely dead.** Under mmap the
mapping holds the whole 1043.68 MiB file, of which only ~243.90 MiB is ever
read after load; the other ~817 MiB is the mmap'd originals of tensors that
have already been repacked into anonymous memory and will never be read again.
Measured saving 819,772 kB — 800.56 MiB — against ~817 MiB of dead pages
predicted from the buffer lines. **That is the whole of it.**

**Stated as what it is for this handset: a win.** The 2.13 GiB peak was the
number that killed `com.shannon.rcsservice` and `.ShannonImsService` on the
smoke run, and it is a load-time artefact of mmap that a real app can simply
not have. 1.35 GiB against 2.13 GiB is 800 MiB of headroom returned on the
smallest model in the set, on a phone whose idle `MemAvailable` has been read
between 1,771,920 kB and 2,617,156 kB.

**The cost, recorded rather than waved past: SwapFree fell 281,668 kB across
9.18 seconds**, against 122,284 kB on step 2's mmap run of the same length.
2.3x the swap traffic. Anonymous pages under pressure can only be compressed
into zram or written out — they cannot be dropped — so a peak that is 99.6%
anonymous puts its pressure somewhere with a CPU cost rather than somewhere
free. Zero processes were killed, and the run began with 2,617,156 kB
available; on a tighter phone that pressure has to go somewhere else.

pp16 67.54 and tg16 17.96 both sit at or just below the bottom of the mmap
spread (pp16 68.20/69.02/69.04, tg16 18.05/18.48/18.53), and the run took 9.18 s
against 8.42 s — consistent with reading 1.03 GiB from UFS rather than mapping
it, but one run each and within the 2.6% run-to-run variation step 2 recorded.
**No speed claim is made either way.**

### DECISION: EVERY MATRIX RUN USES `-lm none`

Two reasons, and neither is about tok/s.

1. **It is what a real app would do.** A Penny that loads a model once and
   generates from it has no use for a mapping of bytes it has already repacked.
2. **It is the only mode where peak RSS IS the working set.** Under mmap,
   VmHWM mixes ~817 MiB of dead pages in with the live ones and the number
   cannot be used to size anything. At `-lm none` the peak is 99.6% anonymous
   and every byte of it is memory the phone genuinely has to find.

CLAUDE.md's Benchmark protocol bullet is updated in the same commit.

### PREDICTION FOR GEMMA 4 E2B, WRITTEN NOW, BEFORE ANY GEMMA RUN

`gemma-4-E2B-it-Q4_K_M.gguf`, 3,106,738,272 bytes = 2963.0 MiB = 2.894 GiB,
sha256 `740185b21d22ceb83a11c3aa62ad5842ef32c70f6096d756bbee85a1e4ec34b8`,
MANIFEST.txt VERIFIED vs HF LFS oid.

**The repack copy scales with the file** — `CPU_REPACK` came out at 1049.96 MiB
for a 1043.68 MiB mapped buffer here, i.e. the repackable tensors cost about
what they cost in the file. At `-lm none` the repack buffer plus the
non-repackable buffer together approximate the whole file, so:

> **Gemma 4 E2B at `-lm none` needs roughly 3.0-3.1 GB of ANONYMOUS memory**
> — ~2963 MiB of weights in the two model buffers, plus KV, compute and output
> buffers, plus ~40 MiB of allocator overhead, and the KV buffer will be larger
> than Qwen3-1.7B's 28.00 MiB at any real context. **Idle `MemAvailable` on
> this handset has been read between 1,771,920 kB and 2,617,156 kB. 3.1 GB of
> unreclaimable anonymous memory does not fit in that.** Expect the
> lowmemorykiller to take processes during the load, and expect either a failed
> load or a run that only completes because the killer freed enough first.
> Under mmap the peak would be higher still — roughly file + repack, ~5.8 GiB
> on a phone with 5.45 GiB — so mmap is not the escape.
>
> **This is a 7a question, not a no.** Per CLAUDE.md's closed decision, the 6a
> is temporary and every ceiling is "on the 6a". A Gemma that does not fit in
> 5.45 GiB of physical memory alongside Android must be re-measured on the
> device that replaces this one before it is called a no.

Gemma is still run, and run last, exactly as the protocol says — the point of
writing this down now is that the result cannot then be fitted to it.

### What this entry does NOT say

**Still nothing about generation specifically.** VmHWM, RssAnon and RssFile are
maxima across the whole invocation — load, repack, warmup, pp16, tg16 — and
none of them says at which phase the peak occurred. The "~1372 MiB hot either
way" figure rests on treating the 243.90 MiB `CPU model buffer` as the same
bytes that were hot in the mapping; that is what the buffer IS, but the hot
fraction of the mmap was never measured directly under pressure and still has
not been.

**No pp512, no tg128, no thread scaling, no unpinned row, no second model.**
This is `-p 16 -n 16`, one thread count, one mask, 9.18 seconds, fourth run of
the same row. The tok/s figures here are not the protocol's tests and must not
be quoted as this handset's speed.

**The speed cost of `-lm none` is NOT established.** pp16 and tg16 came in at
the bottom of the mmap spread and the run was 0.76 s longer, but that is one
observation against three, inside the recorded run-to-run variation, on a model
warm in the host's page cache — which is the condition most favourable to a
no-mmap load and says nothing about a cold read from UFS. Time to first token
from cold is untested and is explicitly not in today's protocol.

**Zero kills is a property of this run AND of its starting headroom.** It began
with `MemAvailable` at 2,617,156 kB, left over from earlier runs' kills. The
same peak on a tighter phone is not known to kill nothing.

**The Gemma prediction is arithmetic on one model's buffer lines**, not a
measurement, and Gemma has never been on this phone. The `CPU_REPACK`-scales-
with-file assumption rests on a single ratio from a single model.

**The Q4_0 repack path has still never executed on this phone.** Nothing about
`-lm none` changes that. And nothing here is a native-versus-VM statement:
that comparison is closed and this is an absolute feasibility measurement of
the 6a.

## 2026-09-16 — the matrix STOPPED at row 4 of 8. The X1 pair halves its own clock ceiling under sustained load: 2.802 -> 1.426 GHz, recovering over ~110 s of idle. Every `c0` figure in this repo is a throttled figure.

Four rows of the eight-row Qwen3-1.7B Q4_K_M matrix ran, all `taskset c0`, all
`-lm none`, all `rc=0`, all **zero LMK kills**. Row 4 returned a `tg128` figure
23% away from row 3's on the identical core mask and thread count, which is the
stop condition Matt set ("a figure that contradicts the previous row by more
than the 2.6% run-to-run spread"). The matrix was halted there and the cause
was read off the phone rather than guessed at. **It is not a benchmark
artefact. It is the handset.**

### The four rows

All four: same boot as every row of the RSS chase (uptime 61,873 -> 62,384 s,
1031.2 -> 1039.7 min, 17.19 -> 17.33 h). AC power, screen on, no VM
(`Running VMs: []`), our app `pm disable-user`'d, nothing opened by hand, model
warm in host page cache. `pennybench.sh` on the phone sha256
`e5a81104e5accb035001a7fa0b8e23e5e37cf00130c504eb1e418b3c599638e7`, verified
byte-identical to the repo copy immediately before row 1. Model
`Qwen3-1.7B-Q4_K_M.gguf`, 1,107,409,472 B, sha256
`b139949c5bd74937ad8ed8c8cf3d9ffb1e99c866c823204dc42c0d91fa181897`.

    row  mask  -t   -p    pp t/s          tg128 t/s       wall s   peak RSS kB
    r1   c0     1   512   32.40 +/- 0.78   9.89 +/- 0.38   162.19    1,491,612
    r2   c0     1    64   30.54 +/- 0.69   9.86 +/- 0.36    81.04    1,415,860
    r3   c0     2   512   45.39 +/- 5.78  10.35 +/- 0.13   132.62    1,491,668
    r4   c0     2    64   49.44 +/- 2.55  12.75 +/- 2.27    64.86    1,415,856

    row  RssAnon kB   RssFile kB  samples  MemAvail before -> after   kills  rc
    r1    1,486,052       5,268      453   2,814,600 -> 2,819,400       0     0
    r2    1,410,424       5,152      225   2,846,208 -> 2,801,836       0     0
    r3    1,486,116       5,264      327   2,837,504 -> 2,827,340       0     0
    r4    1,410,348       5,228      164   2,841,332 -> 2,827,572       0     0

    row  MemFree before -> after      SwapFree before -> after   Cached before -> after
    r1   1,556,860 -> 1,585,976       244,972 -> 206,500         1,480,404 -> 1,458,992
    r2   1,609,204 -> 1,564,340       207,780 -> 208,804         1,461,396 -> 1,461,108
    r3   1,602,292 -> 1,602,132       209,060 -> 208,692         1,461,112 -> 1,451,088
    r4   1,612,836 -> 1,599,724       208,948 -> 210,996         1,451,352 -> 1,451,468

Exact uptimes, because the gaps between rows turn out to be the whole story:

    r1  61,873.50 -> 62,035.69      gap to r2  25.67 s
    r2  62,061.36 -> 62,142.40      gap to r3  15.28 s
    r3  62,157.68 -> 62,290.30      gap to r4  28.34 s
    r4  62,318.64 -> 62,383.50

### WHAT STOPPED IT

`tg128` does not depend on `-p`. Rows 3 and 4 differ in nothing else — same
`c0`, same 2 threads, same model, same `-lm none`, same boot, 28 seconds apart.
They returned **10.35 +/- 0.13** and **12.75 +/- 2.27**, 23.2% apart. That is
nine times the 2.6% run-to-run spread established across the four identical
smoke rows.

The error bars are the tell, and they point the same way. Row 3's `tg128` is
+/- 0.13 (1.3% — tight); row 4's is +/- 2.27 (17.8% — the five repetitions
inside that one measurement disagreed violently with each other). Row 3's
`pp512` is +/- 5.78 on 45.39, 12.7%. A figure whose repetitions scatter that
much inside a single process is not measuring the thing it names.

### THE CAUSE, READ OFF `/sys`, NOT INFERRED FROM THE TIMINGS

Immediately after row 4 exited, with the phone otherwise idle:

    cpu   scaling_cur_freq   scaling_max_freq   cpuinfo_max_freq
    0     1,328,000          1,803,000          1,803,000        A55
    4       696,000          2,253,000          2,253,000        A76
    6       500,000          1,426,000          2,802,000        X1
    7     1,106,000          1,426,000          2,802,000        X1

**`scaling_max_freq` on the X1 pair is 1,426,000 kHz against a
`cpuinfo_max_freq` of 2,802,000 — the policy ceiling itself is at 50.9% of the
cores' rated clock.** That is not the governor choosing a low operating point;
`scaling_cur_freq` is what the governor chose. It is the *ceiling* that has
been lowered, by something in the platform, to a value the governor cannot
exceed.

**The A55 and A76 clusters were untouched.** `policy0` sat at 1,803,000 and
`policy4` at 2,253,000 — each exactly its own `cpuinfo_max_freq` — for every
reading taken, throughout. Only the X1 pair is capped.

`policy6`: governor `sched_pixel`, driver `exynos_cpufreq`. Its
`scaling_available_frequencies` are 500,000 851,000 984,000 1,106,000
1,277,000 1,426,000 1,582,000 1,745,000 1,826,000 2,048,000 2,188,000
2,252,000 2,401,000 2,507,000 2,630,000 2,704,000 2,802,000.

### AND IT RECOVERS, WHICH IS THE PROOF IT WAS OUR LOAD

`policy6/scaling_max_freq` polled while the phone did nothing at all. The
first three readings carry no uptime stamp — they were taken in the window
between row 4 exiting at 62,383.50 s and the timestamped series starting at
62,472.79 s, so they are bounded to that 89-second window and ordered, but not
individually placed. Everything from 62,472.79 on is stamped.

    (within 62,383.50 - 62,472.79)   1,426,000
    (within 62,383.50 - 62,472.79)   2,048,000
    (within 62,383.50 - 62,472.79)   2,188,000
    62,472.79                        2,507,000
    62,476.86                        2,630,000
    62,485.13                        2,704,000
    62,493.32                        2,802,000   <-- full rated clock
    62,497.40 .. 62,517.97           2,802,000   held, six further readings

**The ceiling climbed monotonically back to the cores' rated 2.802 GHz and
stayed there, reaching full clock 109.82 seconds after row 4 exited**, with the
phone idle and nothing of ours running. A cap that lifts itself as the chip
cools was put there by the load that heated it.

**Stated at its true width: this is an inference, not a direct measurement.**
`scaling_max_freq` was never read *before* row 1, nor *during* any row. What was
measured is the ceiling immediately after a run and its recovery curve
afterwards. The one cheap measurement that would close the gap — poll
`policy6/scaling_max_freq` at 1 Hz from inside `pennybench.sh` while the child
runs — has not been made. **What writes that value was not identified either:**
`/sys/class/thermal/` is `Permission denied` to the shell user on this build, so
the thermal zones and cooling devices that would name the governor of the cap
cannot be read from here.

### WHY THIS INVALIDATES THE MATRIX AS DESIGNED, AND IT IS ARITHMETIC

The X1 pair needs on the order of 110 seconds of idle to return to 2.802 GHz.
The gaps between these rows were **25.67, 15.28 and 28.34 seconds.**

So row 1 is the only row in the matrix that began on a cool chip. Every
subsequent row started part-way down a recovery curve, at a clock nobody
recorded, and then drove itself further down over its own 65-163 seconds of
runtime. **Each row's figure is a function of how long the previous row ran and
how long ago it stopped** — neither of which is a property of the thing the row
is supposed to be measuring.

That accounts for all four anomalies without any further hypothesis:

- **`tg128` falls as cumulative load rises.** 17.96 (step 3's `tg16`, an 8.67 s
  process on a chip that had been idle) -> 12.75 (r4, 64.86 s) -> 10.35 (r3,
  132.62 s). The ordering is by heat, not by anything else.
- **Row 3's `tg128` is tight (+/- 0.13) and row 4's is wide (+/- 2.27).** By the
  time row 3 reached its `tg128` it had already done 5x512 tokens of prompt
  processing and was pinned at the floor — steady, and steadily slow. Row 4 had
  done only 5x64 and was still falling *through* its own five repetitions.
- **`pp64` 49.44 BEATS `pp512` 45.39 at 2 threads, inverting P6.** A `pp512`
  repetition is eight times as long under load as a `pp64` one, so it throttles
  harder inside its own measurement. At 1 thread — not enough heat to trip the
  cap — P6 holds the right way round, 30.54 < 32.40.
- **The 1-thread rows agree to 0.30%** (`tg128` 9.89 and 9.86). One X1 core
  working does not trip the cap, so those two rows are the only clean pair here.

### THE PREDICTIONS, JUDGED ON WHAT FOUR ROWS CAN JUDGE

    P1  tg 8-16 t/s at 2 threads on c0
        HOLDS, twice, on the protocol's own test. tg128 = 10.35 and 12.75,
        both inside the band. And it holds on a THROTTLED X1 pair, which is
        the harder case, so the band is not in danger from thermal recovery.
        The smoke run's tg16 = 18.48 was never this figure and must not be
        quoted as it.
    P2  tg at 4 threads on f0 < 1.3x the 2-thread c0 figure
        UNTESTED. Needs f0, which was not reached.
    P3  pp512 at 4 threads on f0 >= 2x pp512 at 1 thread on c0
        UNTESTED. Needs f0. Partial datum only: 1 -> 2 threads on c0 gave
        32.40 -> 45.39, a 1.40x that is itself thermally contaminated.
    P4  unpinned 4 threads no faster than pinned f0
        UNTESTED. Neither leg was reached.
    P5  peak RSS 1.1-1.4 GB, and NO kills on any Qwen3-1.7B run
        THE KILLS HALF NOW HOLDS: zero LMK kills across all four rows,
        against two processes killed on the mmap smoke run. That reversal is
        `-lm none` doing what the protocol bullet said it would.
        The RSS half is MARGINAL and depends on reading "GB" as GiB:
        1,415,856 kB = 1.350 GiB at -p 64, inside the band; 1,491,668 kB =
        1.423 GiB at -p 512, just outside the top of it. Under mmap it was
        2.13 GiB and failed outright. NOT settled either way; -p 512 is
        1.6% over and that is inside no spread anyone has established.
    P6  -p 64 returns a LOWER pp t/s than -p 512
        UNSETTLED, and it is the clearest casualty of the throttle. Holds at
        1 thread (30.54 < 32.40, as predicted). Inverts at 2 threads
        (49.44 > 45.39). The inversion is explained by the cap rather than
        by batching, so P6 is neither confirmed nor refuted by these rows.

### THE MEMORY RESULT IS CLEAN AND IS NOT AFFECTED BY ANY OF THIS

Clock has no bearing on footprint, and the four rows say something the RSS
chase could not.

**Peak RSS is set by `-p` and is indifferent to `-t`.** At `-p 512`, 1,491,612
kB at 1 thread and 1,491,668 kB at 2 — **56 kB apart, 0.004%**. At `-p 64`,
1,415,860 and 1,415,856 kB — **4 kB apart**. Thread count costs nothing.

**The batch buffer is the whole of the variation, and it is small.** Against
step 3's `-p 16` row at 1,410,496 kB:

    -p  16     1,410,496 kB      baseline
    -p  64     1,415,856 kB      +5,360 kB   (+5.23 MiB)
    -p 512     1,491,612 kB      +81,116 kB  (+79.22 MiB)

So the ubatch question carried forward from the smoke entry has an answer for
this model: going from a 16-token micro-batch to the default 512 costs **79.2
MiB**, not the hundreds of MiB that was one of the two original candidates for
the 2.13 GiB. It is a real cost and a bounded one.

**The 99.6% anonymous share holds at every batch size**: 99.63, 99.62, 99.63,
99.62% across the four rows, with `RssFile` never above 5,268 kB — the binary
and bionic, and nothing else. At `-lm none` the peak is the working set, which
is what the protocol bullet committed to.

**And zero kills, four times, with `MemAvailable` between 2,814,600 and
2,846,208 kB before each row.** That headroom is far above the 1,771,920 kB
read at 973.2 min on this same boot, so these four rows are NOT a test of the
tight case and must not be quoted as one.

### What this entry does NOT say

**Four rows of eight.** Nothing on `f0`, nothing unpinned, nothing at 4
threads. P2, P3 and P4 are untested and no statement about thread scaling
beyond two, about the A76 pair, or about the scheduler is available from this.

**It does not say the X1 pair runs at 1.426 GHz.** It says the ceiling was at
1.426 GHz at one instant, immediately after 65 seconds of two-thread load at
the end of a 510-second sequence of four runs. The clock during each row was
never read; the figure at the start of each row was never read. The recovery
curve is measured; the descent is not.

**It does not identify what lowers the cap.** Thermal is the obvious candidate
and the recovery-on-idle shape fits it, but `/sys/class/thermal/` is unreadable
to the shell user here, no temperature was recorded at any point, and a
power/current limiter or a platform HAL policy would present identically from
where this was measured. "Throttle" is used in this entry as a description of
the behaviour, not as a claim about the mechanism.

**It is not a thermal run.** CLAUDE.md names a thermal run as out of scope for
today and it remains so. This is the thermal behaviour arriving uninvited in
the middle of a throughput matrix; nobody set out to characterise it, no
sustained soak was performed, and 110 seconds of recovery observed once is a
reading, not a time constant.

**It says nothing about whether the throttled figures or the peak-clock figures
are the ones that answer the feasibility question.** A phone that has been
generating tokens for thirty seconds is a throttled phone, so the low numbers
may well be the honest product figures — but that is a judgement about what to
measure, it is Matt's to make, and it has not been made.

**No second model, nothing on battery, nothing under load, no cold-load
figure**; the model was warm in host page cache for all four rows. The Q4_0
repack path has still never executed on this phone. And none of this is a
native-versus-VM statement — that comparison is closed and this is an absolute
feasibility measurement of the 6a.

## 2026-09-16 — the matrix re-run COOLED, with the descent measured instead of inferred. The X1 ceiling falls to 1,106,000 kHz DURING a row, and the A76 pair is capped too — "only the X1 pair is capped" was wrong.

### CORRECTION to the entry immediately above, and to the CLAUDE.md bullet it produced

That entry is headed **"Every `c0` figure in this repo is a throttled figure."**
**That overstates what was measured and contradicts the entry's own body**,
which argues that row 1 began on a cool chip and that the two 1-thread rows do
not trip the cap. The ceiling was read **after row 4 only** — never before any
row and never during one — so the correct statement is that every `c0` figure
in this repo **MAY** be throttled, and which ones are was unknown when that
entry was written. CLAUDE.md has been corrected in place; earlier entries are
never rewritten here, so this paragraph is the correction and it wins.

**Three further claims in that entry are hereby marked down from findings to
fits, before this entry's own measurements are read.**

1. "That accounts for all four anomalies without any further hypothesis" is a
   **fit, not a finding.** The `tg128` ordering offered there runs by each row's
   own runtime and ignores what the chip had accumulated at the row's *start*.
   Row 4 began **28.34 s after a 132.62 s run**; row 3 began **15.28 s after an
   81.04 s run**. By any reading of accumulated load, row 4 started at least as
   hot as row 3 and plausibly hotter — and row 4 came out **faster** (12.75 vs
   10.35). The mechanism is **consistent with** the timings. It does not explain
   them, and no further weight is put on it here.
2. "One X1 core working does not trip the cap" was **never measured.** The two
   1-thread rows agreeing to 0.30% is equally consistent with both of them being
   capped, by the same amount, throughout. Nothing distinguishes those cases
   from the readings taken.
3. "Only the X1 pair is capped" is **refuted by this entry's first row** — see
   below. It rested on readings taken while the phone was idle or recovering,
   which is exactly when the A76 cap is not engaged.

### The instrument was changed first, and this is what changed

`pennybench.sh` now reads `policy6/scaling_max_freq` (X1 pair, cpus 6-7, rated
2,802,000) and `policy4/scaling_max_freq` (A76 pair, cpus 4-5, rated 2,253,000)
before and after the child, and tracks the **minimum of each inside the existing
poll loop**. Before/after readings bound a row; only the minimum shows the
descent. Repo copy sha256
`67eefed108ff79ec65029ccb8ba6311bfa62926965c1f277667c0aa8d2b7b1f6`, pushed and
confirmed byte-identical on the phone before the first cooled row.

**A second correction, to the four throttled rows and to every earlier entry
that quotes a sampling rate.** The loop sleeps 0.2 s *between* samples and does
work either side, so the achieved rate is well below 5 Hz: row 1 took 453
samples over 162.19 s, i.e. **2.79 Hz**. "5 Hz" in the smoke, `-v`, anon/file,
no-mmap and four-row entries is the sleep interval, not the rate. The wrapper
now prints the sample count with the interval and no rate at all.

**Every row below was gated on BOTH `policy6` = 2,802,000 AND `policy4` =
2,253,000**, polled in the same shell invocation that launches the row, so
nothing intervenes between the gate passing and the child starting. The gate
value and the wrapper's own `before=` reading are independent reads and agree
on every row.

### Row C1 — c0, 2 threads, -p 512, -n 128, `-lm none`, `-v`

    conditions   uptime 63,807.06 s (1063.5 min, 17.72 h) before, 63,910.38 s
                 after, run 103.32 s wall. SAME BOOT as every row of the RSS
                 chase and of the throttled four. AC power, screen on, no VM,
                 app pm disable-user'd, model warm in host page cache.
    gate         PASSED on the first check, 0 waits, uptime 63,807.00,
                 c6=2,802,000 c4=2,253,000
    command      pennybench.sh r3c_c0_t2_p512v c0 -- -t 2 -p 512 -n 128
                 -lm none -v

    pp512                 55.36 +/- 5.11 t/s
    tg128                 14.11 +/- 0.59 t/s
    ceil X1  before/min/after   2,802,000 / 1,106,000 / 1,745,000 kHz
    ceil A76 before/min/after   2,253,000 / 1,836,000 / 2,253,000 kHz
    peak RSS (VmHWM)   1,491,536 kB   1456.58 MiB   1.4225 GiB
    max RssAnon        1,486,064 kB   1451.23 MiB   99.63% of peak
    max RssFile            5,188 kB      5.07 MiB
    samples                  239   (0.2 s sleep between samples)
    MemAvailable       2,813,952 -> 2,837,352 kB   (+23,400)
    MemFree            1,575,984 -> 1,675,456 kB   (+99,472)
    SwapFree             231,732 ->   184,156 kB   (-47,576)
    Cached             1,460,916 -> 1,387,396 kB   (-73,520)
    LMK kills                  0
    rc                         0

**THE DESCENT IS NOW MEASURED, AND IT GOES FURTHER DOWN THAN THE INFERENCE
DID.** The X1 ceiling starts at its rated 2,802,000 and reaches **1,106,000
kHz — 39.5% of rated** — inside this single 103-second row. The 1,426,000
reading the previous entry took *after* row 4 was not the floor; it was already
part-way back up. The row ends at 1,745,000, recovering while it finishes.

**AND THE A76 PAIR IS CAPPED TOO, WHICH REFUTES "ONLY THE X1 PAIR IS CAPPED".**
`policy4` falls from 2,253,000 to **1,836,000 kHz, 81.5% of rated**, and returns
to rated by the end of the row. **`taskset c0` never schedules anything onto
cpus 4-5**, so the A76 cluster was idle throughout and was capped anyway. Two
clusters moving together while only one of them is loaded says the cap is
imposed across the package rather than per-cluster — which is what a thermal or
power limiter looks like, and is not what a per-core-load governor looks like.
Stated at its width: this is one row, and `policy0` (A55) was not sampled.

**Cooled against throttled, same row, same everything but the starting
ceiling** — the previous entry's row 3 at notes.md 6062:

    test     throttled r3     cooled C1      change
    pp512    45.39 +/- 5.78   55.36 +/- 5.11  +22.0%
    tg128    10.35 +/- 0.13   14.11 +/- 0.59  +36.3%
    wall     132.62 s         103.32 s        -22.1%

**The error bar did not close.** `pp512` is +/- 5.11 cooled against +/- 5.78
throttled — 9.2% relative, still the widest in the matrix. Cooling the start
does not make the measurement steady, because the row throttles itself *during*
its own five repetitions regardless of where it began. That is the finding the
min column was added to get, and it means **no `pp512` figure on this handset
is reproducible to better than about 10% however it is started.**

### THE BATCH BUFFER, TIED TO LLAMA.CPP'S OWN LINES RATHER THAN TO SUBTRACTION

The previous entry attributed "+79.2 MiB for the 512 micro-batch" by
subtracting two peak-RSS figures. **No buffer line was read, because those rows
did not run `-v`.** This row did. From `r3c_c0_t2_p512v.err` on the phone, with
line numbers:

    980:  done_getting_tensors: tensor 'token_embd.weight' (q6_K) (and 113
            others) cannot be used with preferred buffer type CPU_REPACK,
            using CPU instead
    981:  load_tensors:          CPU model buffer size =   243.90 MiB
    982:  load_tensors:   CPU_REPACK model buffer size =  1049.96 MiB

    the pp512 context
    1185: llama_context: n_ctx     = 512
    1187: llama_context: n_batch   = 512
    1188: llama_context: n_ubatch  = 512
    1199: llama_context:        CPU  output buffer size =     0.58 MiB
    1228: llama_kv_cache:        CPU KV buffer size =    56.00 MiB
    1250: sched_reserve:        CPU compute buffer size =   304.75 MiB

    the tg128 context, created after the pp512 one is torn down
    1269: llama_context: n_ctx     = 256
    1271: llama_context: n_batch   = 128
    1272: llama_context: n_ubatch  = 128
    1283: llama_context:        CPU  output buffer size =     0.58 MiB
    1312: llama_kv_cache:        CPU KV buffer size =    28.00 MiB
    1334: sched_reserve:        CPU compute buffer size =    76.19 MiB

**RESERVED AND RESIDENT ARE DIFFERENT NUMBERS AND THE GAP IS LARGE.** Against
step 3's `-p 16 -n 16` row, whose `-v` gave `n_batch` 16 and a compute buffer of
**9.52 MiB** with KV **28.00 MiB**:

    reserved, -p 512 vs -p 16   compute 304.75 - 9.52 = 295.23 MiB
                                KV       56.00 - 28.00 =  28.00 MiB
                                total reserved difference  323.23 MiB
    resident, -p 512 vs -p 16   VmHWM 1,491,536 - 1,410,496 = 81,040 kB
                                                            =  79.14 MiB

So llama.cpp **reserves 323 MiB more** at `-p 512` than at `-p 16`, and the
process is **79 MiB more resident**. Both numbers are real and they answer
different questions: 79 MiB is what the phone actually had to find, 323 MiB is
what was committed. Anonymous mappings are faulted lazily, so most of that
compute buffer is never touched.

How much of it *is* touched, **by subtraction and labelled as such**: the
buffers that must be resident sum to 243.90 + 1049.96 + 0.58 + 56.00 = 1350.44
MiB, against a measured `RssAnon` peak of 1451.23 MiB, leaving **~100.8 MiB of
the 304.75 MiB compute buffer ever resident, about a third.** That is a
subtraction across two different instruments and is not a buffer line; it is
offered as an estimate and nothing rests on it.

**`CPU_REPACK` is 1049.96 MiB here, identical to all five earlier runs**, and
`CPU model buffer` 243.90 MiB identical to the no-mmap row. The batch size
moves the compute and KV buffers and nothing else.

### Row C2 — c0, 2 threads, -p 64, -n 128, `-lm none`

    conditions   uptime 64,078.45 s (1067.9 min) before, 64,131.02 s after,
                 run 52.57 s wall. Same boot and same conditions as C1.
    gate         PASSED on the first check, 0 waits, uptime 64,078.41,
                 c6=2,802,000 c4=2,253,000
    command      pennybench.sh r4c_c0_t2_p64 c0 -- -t 2 -p 64 -n 128 -lm none

    pp64                  61.79 +/- 1.80 t/s
    tg128                 14.91 +/- 0.92 t/s
    ceil X1  before/min/after   2,802,000 / 1,277,000 / 2,188,000 kHz
    ceil A76 before/min/after   2,253,000 / 2,253,000 / 2,253,000 kHz
    peak RSS (VmHWM)   1,415,980 kB   1382.79 MiB   1.3504 GiB
    max RssAnon        1,410,488 kB   1377.43 MiB   99.61% of peak
    max RssFile            5,208 kB      5.09 MiB
    samples                  123
    MemAvailable       2,848,012 -> 2,809,444 kB   (-38,568)
    MemFree            1,682,152 -> 1,644,552 kB   (-37,600)
    SwapFree             188,252 ->   188,508 kB   (+256)
    Cached             1,388,840 -> 1,388,920 kB   (+80)
    LMK kills                  0
    rc                         0

**P6 NOW INVERTS ON A COOLED CHIP TOO, AND THE THROTTLE EXPLANATION FOR IT
FAILS.** `pp64` 61.79 beats `pp512` 55.36 at 2 threads, both gated at rated
clock — a 11.6% inversion, wider than the 8.2% inversion the throttled pair
showed. The previous entry attributed that inversion to `pp512` repetitions
being eight times longer under load. That attribution is now **refuted for the
2-thread case**: cooling both rows did not remove the inversion, it widened it.
Whatever makes `pp64` faster than `pp512` here is not the clock ceiling. P6 is
addressed properly in the judgement below.

**The A76 cap did NOT engage on this row** — `policy4` min 2,253,000, flat at
rated throughout 52.57 s, against C1's 1,836,000 over 103.32 s. So the
package-wide cap observed on C1 is a function of sustained load and not a
constant, and C1's A76 reading is not yet reproduced.

**A tg128 gap that is flagged, not waved through.** C1 returned 14.11 +/- 0.59
and C2 14.91 +/- 0.92 on the identical mask, thread count and test — **5.67%
apart, above the 2.6% run-to-run spread**. It was not treated as a stop because
the two rows are no longer identical in the respect that matters and the
wrapper now proves it: C1's X1 ceiling reached 1,106,000 and C2's only
1,277,000, and C2 is the faster row. The direction matches. **That is a
consistency check on two points, not an explanation, and it is not offered as
one.** What it does establish is that gating on a cool start cut the r3/r4
`tg128` discrepancy from **23.2% to 5.67%** without removing it: a cool start
bounds the problem, it does not solve it.

### Row C3 — f0, 4 threads, -p 512, -n 128, `-lm none`

`taskset f0` = 0xf0 = cpus 4,5,6,7 = the A76 pair plus the X1 pair.

    conditions   uptime 64,202.61 s (1070.0 min) before, 64,303.02 s after,
                 run 100.41 s wall. Same boot and same conditions.
    gate         PASSED on the first check, 0 waits, uptime 64,202.55,
                 c6=2,802,000 c4=2,253,000
    command      pennybench.sh r5_f0_t4_p512 f0 -- -t 4 -p 512 -n 128 -lm none

    pp512                 66.19 +/- 3.84 t/s
    tg128                 12.78 +/- 2.41 t/s
    ceil X1  before/min/after   2,802,000 / 1,106,000 / 1,106,000 kHz
    ceil A76 before/min/after   2,253,000 /   910,000 / 1,024,000 kHz
    peak RSS (VmHWM)   1,491,364 kB   1456.41 MiB   1.4223 GiB
    max RssAnon        1,485,820 kB   1451.00 MiB   99.63% of peak
    max RssFile            5,252 kB      5.13 MiB
    samples                  159
    MemAvailable       2,846,648 -> 2,715,256 kB   (-131,392)
    MemFree            1,680,292 -> 1,522,940 kB   (-157,352)
    SwapFree             188,764 ->   146,588 kB   (-42,176)
    Cached             1,390,632 -> 1,414,028 kB   (+23,396)
    LMK kills                  0
    rc                         0

**THE HARDEST CAP MEASURED SO FAR, AND BOTH CLUSTERS ARE IN IT.** X1 down to
1,106,000 (39.5% of rated) and **A76 down to 910,000 kHz — 40.4% of rated.**
Neither had recovered when the row ended: X1 still at 1,106,000, A76 at
1,024,000. With four cores loaded instead of two, the cap engages on both
clusters and stays engaged past the end of the run. C1 saw the A76 reach only
1,836,000 with the cluster idle; loading it takes it to 910,000.

**`tg128` at 4 threads on f0 is 12.78 +/- 2.41 — SLOWER than 2 threads on c0
cooled (14.11 and 14.91), and its error bar is 18.9%.** Two more cores made
token generation worse. That is the direction P2 predicted, past the point P2
predicted: P2 said "less than 1.3x", and the measured ratio is **0.88x and
0.86x against the two cooled c0 rows** — below 1.0, not merely below 1.3.
Whether that is memory bandwidth alone or bandwidth plus the deeper cap on
these four cores is **not separable from this row**, because the A76 cluster is
both the extra compute and the extra heat.

**`pp512` 66.19 against C1's 55.36 on 2 cooled X1 cores: 1.20x for twice the
cores.** Prompt processing does scale, and it scales poorly.

### Row C4 — f0, 4 threads, -p 64, -n 128, `-lm none`

    conditions   uptime 64,406.02 s (1073.4 min) before, 64,459.93 s after,
                 run 53.91 s wall. Same boot and same conditions.
    gate         PASSED after 9 five-second waits (~45 s of forced cooling —
                 the first row that had to wait), uptime 64,405.98,
                 c6=2,802,000 c4=2,253,000
    command      pennybench.sh r6_f0_t4_p64 f0 -- -t 4 -p 64 -n 128 -lm none

    pp64                  84.68 +/- 3.00 t/s
    tg128                 14.23 +/- 1.84 t/s
    ceil X1  before/min/after   2,802,000 / 1,106,000 / 2,188,000 kHz
    ceil A76 before/min/after   2,253,000 / 1,328,000 / 1,328,000 kHz
    peak RSS (VmHWM)   1,415,824 kB   1382.64 MiB   1.3502 GiB
    max RssAnon        1,410,316 kB   1377.26 MiB   99.61% of peak
    max RssFile            5,216 kB      5.09 MiB
    samples                   86
    MemAvailable       2,746,720 -> 2,694,492 kB   (-52,228)
    MemFree            1,555,308 -> 1,499,360 kB   (-55,948)
    SwapFree             147,100 ->   148,892 kB   (+1,792)
    Cached             1,416,240 -> 1,416,348 kB   (+108)
    LMK kills                  0
    rc                         0

**`pp64` 84.68 beats `pp512` 66.19 at 4 threads — a 27.9% inversion, the widest
yet, and the second cooled pair to invert.** P6 predicted the opposite. Two
cooled pairs, at two different core counts, both inverted, with the inversion
WIDENING as cores are added (11.6% at 2 threads, 27.9% at 4). The clock is not
doing this.

**`tg128` 14.23 against C3's 12.78 on the identical mask and thread count** —
11.3% apart, with error bars of 12.9% and 18.9% that overlap heavily. Same
pattern as the C1/C2 pair and the same partial covariate: C3's A76 ceiling
reached 910,000 and C4's only 1,328,000, and C4 is the faster row. Recorded;
not claimed as explained.

### Row C5 — UNPINNED, 4 threads, -p 512, -n 128, `-lm none`

No `taskset`. The scheduler may place any of the four threads on any of the
eight cores, A55s at 1.803 GHz included.

    conditions   uptime 64,534.01 s (1075.6 min) before, 64,640.04 s after,
                 run 106.03 s wall. Same boot and same conditions.
    gate         PASSED after 7 five-second waits, uptime 64,533.95,
                 c6=2,802,000 c4=2,253,000
    command      pennybench.sh r7_un_t4_p512 none -- -t 4 -p 512 -n 128
                 -lm none

    pp512                 63.52 +/- 4.71 t/s
    tg128                 11.74 +/- 1.76 t/s
    ceil X1  before/min/after   2,802,000 /   851,000 /   984,000 kHz
    ceil A76 before/min/after   2,253,000 /   910,000 / 1,024,000 kHz
    peak RSS (VmHWM)   1,491,144 kB   1456.20 MiB   1.4221 GiB
    max VmRSS          1,490,976 kB   (168 kB below VmHWM — the high-water
                                       mark caught a moment between samples;
                                       first row where the two differ)
    max RssAnon        1,485,724 kB   1450.90 MiB   99.64% of peak
    max RssFile            5,048 kB      4.93 MiB
    samples                  167
    MemAvailable       2,738,252 -> 2,764,940 kB   (+26,688)
    MemFree            1,546,176 -> 1,591,304 kB   (+45,128)
    SwapFree             149,148 ->   126,684 kB   (-22,464)
    Cached             1,416,364 -> 1,397,600 kB   (-18,764)
    LMK kills                  0
    rc                         0

**P4 HOLDS, on both tests.** Unpinned is slower than pinned `f0` at the same
thread count: `pp512` 63.52 against 66.19 (-4.0%) and `tg128` 11.74 against
12.78 (-8.1%). The prediction was "no faster than pinned f0, and probably
slower", and it is slower on both.

**THE DEEPEST X1 CAP OF THE DAY, 851,000 kHz — 30.4% of rated.** Unpinned is
also the row that drove the ceiling lowest, and the A76 matched C3's 910,000.
Neither cluster had recovered at the end (984,000 and 1,024,000). Why an
unpinned run should cap harder than a pinned one is **not answerable from
these rows** and is not guessed at here; that threads also ran on the A55
cluster, which was never sampled, is one of several possibilities.

### Row C6 — UNPINNED, 4 threads, -p 64, -n 128, `-lm none`

    conditions   uptime 64,742.46 s (1079.0 min) before, 64,795.39 s after,
                 run 52.93 s wall. Same boot and same conditions.
    gate         PASSED after 12 five-second waits (~60 s, the longest wait of
                 the run), uptime 64,742.42, c6=2,802,000 c4=2,253,000
    command      pennybench.sh r8_un_t4_p64 none -- -t 4 -p 64 -n 128 -lm none

    pp64                  82.34 +/- 2.80 t/s
    tg128                 14.47 +/- 1.73 t/s
    ceil X1  before/min/after   2,802,000 /   984,000 / 2,188,000 kHz
    ceil A76 before/min/after   2,253,000 / 1,328,000 / 1,328,000 kHz
    peak RSS (VmHWM)   1,415,508 kB   1382.33 MiB   1.3499 GiB
    max RssAnon        1,410,272 kB   1377.22 MiB   99.63% of peak
    max RssFile            4,956 kB      4.84 MiB
    samples                   87
    MemAvailable       2,710,096 -> 2,688,780 kB   (-21,316)
    MemFree            1,512,376 -> 1,512,160 kB   (-216)
    SwapFree             133,852 ->   122,956 kB   (-10,896)
    Cached             1,405,576 -> 1,403,528 kB   (-2,048)
    LMK kills                  0
    rc                         0

**P4 holds on `pp64`** (82.34 unpinned against 84.68 pinned f0, -2.8%) and is a
**wash on `tg128`** (14.47 against 14.23, +1.7%, against error bars of 12.0%
and 12.9% that overlap almost completely). At `-p 512` unpinned was clearly
slower on both tests; at `-p 64` only the prompt half separates.

**Third cooled P6 inversion**: `pp64` 82.34 beats `pp512` 63.52 by 29.6%,
unpinned. Three cooled pairs, three inversions, at 2 threads pinned, 4 threads
pinned and 4 threads unpinned.

### Row C7 — c0, 1 thread, -p 512, -n 128, `-lm none`. THE P3 BASELINE, AND IT CONTRADICTS ROW 1. THE MATRIX STOPPED HERE.

Run because P3 compares four threads on `f0` against **one thread on c0**, and
no cooled 1-thread `pp512` row existed. Row 1 of the throttled four began on a
chip that had been idle 803 s, but its ceiling was never read, so it is not a
measured cooled row.

    conditions   uptime 64,868.03 s (1081.1 min) before, 65,114.76 s after,
                 run 246.73 s wall. Same boot and same conditions.
    gate         PASSED after 5 five-second waits, uptime 64,867.97,
                 c6=2,802,000 c4=2,253,000
    command      pennybench.sh r1c_c0_t1_p512 c0 -- -t 1 -p 512 -n 128 -lm none

    pp512                 22.68 +/- 5.38 t/s     (23.7% relative error bar)
    tg128                  6.00 +/- 0.27 t/s
    ceil X1  before/min/after   2,802,000 /   984,000 / 1,106,000 kHz
    ceil A76 before/min/after   2,253,000 / 1,836,000 / 2,253,000 kHz
    peak RSS (VmHWM)   1,491,292 kB   1456.34 MiB   1.4222 GiB
    max RssAnon        1,486,020 kB   1451.19 MiB   99.65% of peak
    max RssFile            4,980 kB      4.86 MiB
    samples                  563
    MemAvailable       2,695,176 -> 2,753,668 kB   (+58,492)
    MemFree            1,504,524 -> 2,668,432 kB   (+1,163,908)
    SwapFree             140,364 ->    93,416 kB   (-46,948)
    Cached             1,413,100 ->   308,964 kB   (-1,104,136)
    LMK kills                  0
    rc                         0

**IT IS 30.0% SLOWER ON `pp512` AND 39.3% SLOWER ON `tg128` THAN ROW 1 — the
same configuration, and this is the row that was supposed to be the CLEAN
one.** Row 1 (notes.md 6060, throttled sequence) returned 32.40 and 9.89 in
162.19 s. This row, gated at rated clock on both clusters, returned 22.68 and
6.00 in **246.73 s** — 52.1% longer for identical work. Every other
cooled-versus-throttled comparison today went the other way, by 17-25%.

**The matrix stopped here under the rule Matt set, and the contradiction is NOT
explained.** What is recorded, and no more:

- **`Cached` collapsed by 1,104,136 kB during this single row**, 1,413,100 ->
  308,964, while `MemFree` rose by 1,163,908 kB. The kernel dropped roughly a
  gigabyte of clean page cache and handed it to nobody. No other row did this;
  the largest `Cached` movement before it was -73,520 kB (C1). Checked 34 s
  after the row ended: `Cached` still only 427,980 kB, so it is a persistent
  loss, not a momentary dip.
- **Swap is nearly exhausted.** `SwapFree` 93,416 kB of `SwapTotal`
  3,145,724 — **97.0% spent** at the end of this row. Across today it ran
  244,972 (r1) -> 93,416 (C7). CLAUDE.md records 1,266,044 kB free at 973.2 min
  on this same boot, so roughly 1.17 GB of swap headroom has gone during this
  session's eleven runs.
- **One thread DOES trip the cap**, which the previous entry guessed it did
  not. `policy6` reached **984,000 kHz, 35.1% of rated**, on a single-threaded
  row. That guess is now refuted by measurement rather than left standing.
- **Zero LMK kills**, and `MemAvailable` never below 2,695,176 kB. Nothing was
  killed and nothing ran short.

**Three candidates, none tested, none asserted:** swap exhaustion making the
process's own 1.45 GiB anonymous working set expensive to hold; the page-cache
collapse being cause rather than effect; or row 1's 32.40 being the outlier
rather than this row's 22.68. **Arithmetic that bears on the third and is worth
having: 55.36 / 22.68 = 2.44x from one thread to two, which is superlinear and
implausible, while 55.36 / 32.40 = 1.71x is not.** That points at this row
being the anomaly, not row 1 — but it is an argument from plausibility, not a
measurement, and it is offered as such.

**The cheap next measurement, not taken because the stop rule applies:** re-run
this identical row once and see which figure reproduces. `pennybench.sh` should
also sample `/proc/vmstat` (`pswpin`, `pswpout`, `pgmajfault`) either side —
those counters were read once afterwards, with no baseline, so they say
nothing.

### THE MATRIX AS IT STANDS — seven cooled rows, every one gated at rated clock

    row  mask  -t   -p    pp t/s           tg128 t/s        wall s   notes.md
    C7   c0     1   512   22.68 +/- 5.38    6.00 +/- 0.27   246.73     6651
    C1   c0     2   512   55.36 +/- 5.11   14.11 +/- 0.59   103.32     6337
    C2   c0     2    64   61.79 +/- 1.80   14.91 +/- 0.92    52.57     6449
    C3   f0     4   512   66.19 +/- 3.84   12.78 +/- 2.41   100.41     6497
    C4   f0     4    64   84.68 +/- 3.00   14.23 +/- 1.84    53.91     6541
    C5   un     4   512   63.52 +/- 4.71   11.74 +/- 1.76   106.03     6577
    C6   un     4    64   82.34 +/- 2.80   14.47 +/- 1.73    52.93     6619

    row  ceil X1 before/min/after      ceil A76 before/min/after     peak RSS kB
    C7   2802000 /  984000 / 1106000   2253000 / 1836000 / 2253000     1,491,292
    C1   2802000 / 1106000 / 1745000   2253000 / 1836000 / 2253000     1,491,536
    C2   2802000 / 1277000 / 2188000   2253000 / 2253000 / 2253000     1,415,980
    C3   2802000 / 1106000 / 1106000   2253000 /  910000 / 1024000     1,491,364
    C4   2802000 / 1106000 / 2188000   2253000 / 1328000 / 1328000     1,415,824
    C5   2802000 /  851000 /  984000   2253000 /  910000 / 1024000     1,491,144
    C6   2802000 /  984000 / 2188000   2253000 / 1328000 / 1328000     1,415,508

**The X1 ceiling fell below half its rated clock on EVERY cooled row without
exception**, floor 851,000 (30.4%) to 1,277,000 (45.6%). **The A76 ceiling fell
on every row but C2**, floor 910,000 (40.4%). **No row ended at the X1's rated
clock**; three ended at 1,106,000 or below. The cap is not an edge case on this
handset — at this workload it is the normal operating condition, and gating on
a cool start buys the first few seconds of a row and nothing after that.

**The four throttled rows from the previous entry (notes.md 6059-6063) remain in the
record as what they are: rows with an unrecorded starting clock. They are not
quoted as this chip's speed anywhere, here or later.**

### THE PREDICTIONS, JUDGED

    P1  tg 8-16 t/s at 2 threads pinned to c0
        HOLDS. Cooled tg128 = 14.11 (C1) and 14.91 (C2), both inside.
        BUT NOT SAFELY, and the earlier claim that "throttled is the harder
        case so the band is not in danger" was wrong in one direction:
        throttled is the harder case for the FLOOR only. The band's ceiling
        is 16 and C2 came within 1.09 t/s of it; tg16 on a cool chip was
        17.96, already outside. A faster cooled 2-thread row can fail P1 on
        the TOP side. It did not here.
    P2  tg at 4 threads on f0 < 1.3x the 2-thread c0 figure
        HOLDS, and by far more than predicted. Matched by -p: 12.78/14.11 =
        0.91x at -p 512, 14.23/14.91 = 0.95x at -p 64. Four threads are
        SLOWER than two, not merely short of 1.3x. Token generation on this
        handset does not benefit from the A76 pair at all.
    P3  pp512 at 4 threads on f0 >= 2x pp512 at 1 thread on c0
        HOLDS ON BOTH AVAILABLE BASELINES, which is why the C7 contradiction
        does not cost it. Against cooled C7: 66.19/22.68 = 2.92x. Against
        throttled row 1: 66.19/32.40 = 2.04x. Both clear the 2x bar, so P3
        is answered whichever of the two 1-thread figures is right.
    P4  unpinned 4 threads no faster than pinned f0
        HOLDS on three of four comparisons and is a wash on the fourth.
        pp512 63.52 vs 66.19 (-4.0%), tg128 11.74 vs 12.78 (-8.1%),
        pp64 82.34 vs 84.68 (-2.8%); tg128 at -p 64 is 14.47 vs 14.23
        (+1.7%), inside error bars of 12.0% and 12.9%. Never faster by more
        than its own noise.
    P5  peak RSS 1.1-1.4 GB, and NO kills on any Qwen3-1.7B run
        THE KILLS HALF HOLDS OUTRIGHT: zero LMK kills across all eleven
        rows run today at -lm none, against two processes killed on the one
        mmap smoke run.
        THE RSS HALF HOLDS AT -p 64 AND FAILS AT -p 512, and it does not
        turn on units. At -p 64: 1,415,508-1,415,980 kB = 1.350 GiB, inside
        the band (1.450 GB on the decimal reading, 3.5% over). At -p 512:
        1,491,144-1,491,668 kB = 1.422 GiB AND 1.491 GB, outside the band
        on BOTH readings. The default 512 micro-batch is what puts it over.
    P6  -p 64 returns a LOWER pp t/s than -p 512
        FAILS. Three cooled pairs, three inversions, widening with cores:
        2 threads c0 61.79 vs 55.36 (+11.6%), 4 threads f0 84.68 vs 66.19
        (+27.9%), 4 threads unpinned 82.34 vs 63.52 (+29.6%). The previous
        entry attributed the 2-thread inversion to throttling; gating both
        rows at rated clock WIDENED it, which refutes that attribution.
        Why a 64-token batch beats a 512-token batch here is NOT explained.
        The one cheap test that would separate batch size from micro-batch
        size -- vary -ub independently of -p -- has not been run.

### What this entry does NOT say

**The matrix is not complete and C7 is a live contradiction.** Seven cooled
rows exist; one of them disagrees with its own throttled counterpart by 30% in
the direction no other row went, and that is recorded as unexplained rather
than reasoned away. Nothing in P1-P6 above rests on C7 except P3, which clears
its bar on either baseline.

**"Cooled" means the row STARTED at rated clock on both sampled clusters.** It
does not mean the row ran at rated clock, and every row proves it did not. The
gate buys a known starting point, nothing more. `policy0` (the A55 cluster) was
never sampled on any row, so the unpinned rows in particular have a third of
the chip unobserved.

**The cap's mechanism is still unidentified.** `/sys/class/thermal/` is
`Permission denied` to the shell user on this build; no temperature was read at
any point today. "Throttle" describes the behaviour. A power or current limiter
would look identical from here, and so would a platform HAL policy.

**One reading each.** Every cooled row is a single run. The only figure
reproduced under matched conditions today is `tg128` at 1 thread (9.89 and
9.86, 0.30% apart, both throttled). Everything else is n=1 and the error bars
on the four-thread `tg128` rows run to 18.9%.

**No thermal run, and the sustained question is NEXT, not done.** Matt's
decision was to cool between rows and not to run sustained today; CLAUDE.md
names a thermal run as out of scope. So this entry measures the handset with
its best foot forward and says nothing about what it does after ten minutes of
continuous generation — which is the product shape.

**Swap is nearly gone and that is not a controlled variable.** `SwapFree` fell
from 244,972 kB at the first row to 93,416 kB at the last, 97.0% of swap spent.
Every row today ran on a phone with less swap headroom than the row before it,
and no row was repeated at a matched swap level. Whether that affects any
figure here is unknown.

**One model, one quantisation, one boot, `-lm none` throughout.** Nothing on
Qwen3.5-2B, nothing on Gemma 4 E2B, nothing at Q4_0 — the Q4_0 repack path has
still never executed on this phone. Nothing on battery, nothing under load,
nothing with the screen off, no cold-load figure, no time-to-first-token. The
model was warm in host page cache at the start of every row except possibly
C7, whose page cache collapsed mid-row. And none of this is a
native-versus-VM statement: that comparison is closed and this is an absolute
feasibility measurement of the 6a.

## 2026-09-16 — the boot was the variable: on a fresh one the 1-thread row returns 31.45, C7's 22.68 does not reproduce, and the first LMK kill of the day lands on the row with the most realistic starting memory

**Two corrections to the previous entry's judgement lines first, both raised by
Matt, both cases of a one-line verdict overstating its own body.**

**P6's line said the cooled re-run "refutes" the throttle attribution. It does
not, and the corrected reading is "inseparable from throttling".** Gating both
rows at rated clock widened the inversion, which rules out *the cool start*
as the explanation — it does not rule out throttling, because the min column in
that same entry shows every cooled row fell to roughly 1.1 GHz DURING itself,
and a `pp512` repetition is about eight times longer than a `pp64` one, so the
512-token row spends far more of its life under a lowered ceiling. The gate
controls where a row starts and nothing about where it ends. **P6 reads: FAILS,
and the cause is inseparable from throttling on the evidence available — the
`-ub` test that would separate batch size from micro-batch size has not been
run.**

**P2's line said the A76 pair gives "no benefit at all" to token generation.
Its own C3 body says something weaker and the body is right.** C3 records that
the four-thread rows ran with `policy4` capped to 910,000 kHz — 40.4% of rated
— so what was measured is two X1s plus two heavily-capped A76s, not two X1s
plus two A76s. Memory bandwidth and the deeper A76 cap cannot be separated
from each other by any row in this matrix. **P2 reads: HOLDS, and by more than
predicted — four threads are slower than two, not merely short of 1.3x — but
WHY is not established: the A76 pair ran capped to 40.4% of rated on the rows
in question, so "bandwidth-bound" and "the A76s were throttled out of
usefulness" are not separable here.**

Both lines are corrected in this entry rather than in place; notes.md is
append-only and the previous entry stands as written.

### THE BOOT WAS REPLACED

The previous entry's last row, C7, ran on a boot whose page cache had collapsed
by the size of the model and whose swap was 97.0% spent. Matt's rule, written
into CLAUDE.md before this reboot: that boot is contaminated and no further row
on it counts.

    rebooted        16 Sept, 14:22:50 BST. The old boot had run 66,065 s
                    (18.35 h). Unlocked by hand -- GrapheneOS keeps the USB
                    port charging-only while locked, so adb cannot reach the
                    phone until somebody types the PIN. adb returned at
                    uptime 191 s.
    app state       com.pennyspike.probe2a still `disable-user`'d; the
                    disable survives a reboot, as recorded. `vm list`
                    returned `Running VMs: []`.

**The protocol's two readings, on the fresh boot, no VM, app disabled, AC
power, screen on:**

    uptime s   MemFree     MemAvailable   Cached      SwapFree    AnonPages
    302.94     1,309,740   2,193,244      1,112,604   1,008,636   1,655,920
    1515.90    1,224,220   2,159,732      1,161,304   1,095,420   1,717,960

    uptime s   pswpin    pswpout   pgmajfault   ZRAM
    302.94      36,151   573,277      48,862    486,688K phys / 1,908,224K swap
    1515.90    101,067   617,921     114,054    488,520K phys / 2,019,840K swap

All three policies read their rated ceilings at both samples.

**The SHAPE of the settling curve differs from the boot of 15 Sept and the
25-minute figures agree anyway.** That earlier boot climbed — 940,640 kB at
5.8 min to 2,119,020 kB at 25.3 min. This one started high at 2,193,244 kB and
drifted slightly DOWN to 2,159,732 kB. **The two ~25-minute figures are within
1.9% of each other**, which is the reading CLAUDE.md calls the idle budget, and
it is reproduced across two boots eleven days apart in phone-time. The path to
it is not reproduced, and only the endpoint should be quoted.

`Cached` came back to 1.11-1.16 GB. The contaminated boot was still stuck at
437,720 kB forty-three minutes after C7 ended.

**Method note, recorded rather than asked about:** the model was NOT pre-warmed
into page cache before the re-run. Under `-lm none` llama-bench reads the whole
file at load regardless, and load time is excluded from the reported t/s, so
page cache is in the same state before the first timed repetition either way.

### Row B2-R1 — the C7 re-run. c0, 1 thread, -p 512, -n 128, -lm none. FRESH BOOT.

    gate         PASSED on the first check, 0 waits, uptime 1537.68,
                 policy6 = 2,802,000 AND policy4 = 2,253,000
    conditions   uptime 1,537.77 s (25.6 min) before, 1,702.15 s after,
                 164.38 s wall. AC power, screen on, no VM, app disabled.
                 Model COLD in page cache -- first row on this boot.
    command      pennybench.sh b2r1_c0_t1_p512 c0 -- -t 1 -p 512 -n 128 -lm none
    model        Qwen3-1.7B-Q4_K_M.gguf, sha256 b139949c5bd74937ad8ed8c8cf3d9
                 ffb1e99c866c823204dc42c0d91fa181897, verified on the phone
                 against MANIFEST.txt

    pp512                 31.45 +/- 0.70 t/s      (2.23% error bar)
    tg128                 10.13 +/- 0.64 t/s
    ceil X1   before/min/after    2,802,000 / 1,426,000 / 2,630,000 kHz
              min first seen at uptime 1,623.21 -- 86 s into the row
    ceil A76  before/min/after    2,253,000 / 2,253,000 / 2,253,000 kHz
              min_at uptime 1,537.77 -- 0 s in, i.e. it NEVER fell
    peak RSS (VmHWM)   1,492,260 kB   1457.29 MiB   1.4231 GiB
    max RssAnon        1,486,176 kB   99.59% of peak
    max RssFile            5,792 kB
    rss_samples              413   (2.51 Hz over 164.38 s)
    MemAvailable       2,128,524 -> 2,585,832 kB   (+457,308)
    MemFree            1,189,280 -> 1,611,984 kB   (+422,704)
    SwapFree           1,122,300 ->   648,700 kB   (-473,600)
    Cached             1,161,500 -> 1,199,624 kB   (+38,124 -- did NOT collapse)
    pswpin               111,056 ->   111,483      (+427 pages)
    pswpout              621,181 ->   750,420      (+129,239 pages, ~505 MiB)
    pgmajfault           124,045 ->   124,533      (+488)
    ZRAM               548,596K physical for 2,489,344K in swap
    LMK kills                  1 kill + 1 ActivityManager line   (see below)
    rc                         0

### THE C7 VERDICT: 32.40 reproduces, the hypothesis STANDS, C7 was the environment

The falsification condition was written into CLAUDE.md and committed (8261aa7,
plus the reword Matt asked for) BEFORE this row ran: if the re-run reproduces
~32.40 / 9.89 the memory-starvation hypothesis stands; if it reproduces
~22.68 / 6.00 it does not.

    row                                pp512            tg128            wall s
    row 1, throttled, old boot    32.40 +/- 0.78    9.89 +/- 0.38        162.19
    B2-R1, fresh boot, gated      31.45 +/- 0.70   10.13 +/- 0.64        164.38
    C7,  contaminated boot, gated 22.68 +/- 5.38    6.00 +/- 0.27        246.73

B2-R1 lands on row 1: **2.93% apart on `pp512`, with error bars that overlap**
(30.75-32.15 against 31.62-33.18), 2.43% apart on `tg128`, 1.35% on wall time.
Against C7 it is 38.7% faster on `pp512` and 68.8% faster on `tg128`.

**Three corroborating signals, none of which is the headline number:**

- **The error bar came back.** C7's `pp512` was +/- 5.38 (23.7%); this row's is
  +/- 0.70 (2.23%), in line with every other row in the matrix. A starved
  machine is erratic across repetitions; this one was not.
- **`Cached` rose 38,124 kB instead of falling 1,104,136 kB.** The eviction
  that defined C7 did not happen here, on a row of the same length running the
  same model on the same mask.
- **`pgmajfault` moved 488 across the whole row.** The process was not
  thrashing. `pswpout` rose 129,239 pages against `pswpin` of 427 — pages went
  out to zram and did not come back, which is Android making room, not this
  process fighting for its own working set. Those counters did not exist as a
  before/after pair until this row; C7 is now unrepeatable in that respect and
  its mechanism stays inferred from `Cached` alone.

**What the verdict is and is not.** It is: C7's 22.68 / 6.00 is not this
handset's 1-thread speed, and the contamination rule keeps both its force and
its explanation. It is not: proof that page-cache eviction is the mechanism.
The re-run changed the whole boot, not one variable — swap, cache, uptime,
kill history and process population all moved together. **The hypothesis
survived a test it could have failed, which is the most this design can give.**

**The X1 ceiling behaved normally rather than collapsing:** min 1,426,000 kHz
at 86 s in, recovering to 2,630,000 by the end — the highest after-row X1
reading of any row today. **The A76 pair never moved from rated at any of 413
samples**, the only row all session where it did not, and the opposite of C1,
where it fell while `taskset c0` scheduled nothing onto it.

### THE KILL, AND WHY IT REWRITES P5 RATHER THAN BEING AN EXCEPTION TO IT

Four seconds into the row, at model load:

    09-16 14:48:51.160 lowmemorykiller: Kill 'com.shannon.rcsservice:
      shannonrcsservice' (2800), uid 10151, oom_score_adj 985 to free
      160808kB rss, 9588kB anon rss, 41776kB swap, 0kB dmabuf_pss,
      0kB dmabuf_rss; reason: low watermark is breached
    09-16 14:48:51.232 ActivityManager: Process ... (pid 2800) has died:
      cch  +85 CEM

One cached, empty process at `oom_score_adj 985` — the disposable band,
nothing a user would notice, and nowhere near this process or anything on the
screen. Twelve prior `-lm none` rows produced zero kills between them.

**The first reading of this was that it is "the reverse of expectation" —
kills on the fresh boot, none on the tired one. Matt's correction, and it is
this repo's own protocol point turned on its own data:**

    row set                 MemAvailable BEFORE the row        kills
    C1-C7, old boot         2,695,176 - 2,848,012 kB           0 each
    B2-R1, fresh boot       2,128,524 kB                       1

**Every zero-kill row on the old boot started with 2.70-2.85 GB available, and
that headroom was itself the product of earlier kills** — the boot had been
running 17-18 hours, had already shed its cheap processes, and had spent 97%
of its swap getting there. **B2-R1 started at 2,128,524 kB on a phone that had
killed nothing**, which is the ordinary condition a real app would meet.
**So the fresh-boot row is the representative measurement and the twelve
zero-kill rows were flattered by prior kills.** They are not wrong; they are
measurements of a phone that had already paid the price once.

**P5, rewritten. It was: "peak RSS 1.1-1.4 GB, and NO kills on any Qwen3-1.7B
run."**

    P5 (kills half)  FAILS under the realistic starting condition. With
                     MemAvailable at 2,128,524 kB -- a phone that has not
                     already been cleared by hours of kills -- loading
                     Qwen3-1.7B Q4_K_M at -lm none takes one cached process
                     at oom_score_adj 985. The twelve zero-kill rows all
                     started 570,000-720,000 kB higher, on a boot that had
                     freed that headroom by killing things earlier.
    P5 (RSS half)    HOLDS AT -p 64 AND FAILS AT -p 512, unchanged and not a
                     matter of units. -p 64: 1,415,508-1,415,980 kB =
                     1.350 GiB, inside the band. -p 512: 1,491,144-1,492,260
                     kB = 1.422 GiB AND 1.492 GB, outside on both readings.
                     B2-R1's 1,492,260 kB is the largest peak of any -lm none
                     row and extends the range by 592 kB. The default 512
                     micro-batch is what puts it over.

The stop rule was applied and Matt's call was to continue: one cached adj-985
process at load, `rc=0`, figures reproducing row 1 — recorded, counted against
P5, carry on.

### What this entry does NOT say

**One row.** B2-R1 is a single run on a single boot. It agrees with row 1
within error bars, which is two figures from two different thermal and memory
states agreeing — not two repetitions under matched conditions.

**The mechanism behind C7 is still not identified, only supported.** The
re-run replaced every variable at once. `pswpin`/`pswpout`/`pgmajfault` now
exist as a before/after pair and would catch the next occurrence directly, but
they were not recorded during C7 and cannot be recovered.

**The kill is one kill, on one row.** "Loading this model costs one cached
process from a fresh boot" rests on a single observation, not on a repetition,
and no row has been run at a deliberately controlled starting `MemAvailable`.
The claim that the old boot's headroom came from prior kills is read off that
boot's own kill history and its 97%-spent swap; no experiment isolated it.

**Still nothing on Qwen3.5-2B, nothing on Gemma 4 E2B, nothing at Q4_0** — the
Q4_0 repack path has still never executed on this phone. No thermal run, no
sustained run, nothing on battery, nothing with the screen off, no
time-to-first-token. `policy0` (the A55 cluster) has still never been sampled.
And none of this is a native-versus-VM statement: that comparison is closed and
this is an absolute feasibility measurement of the 6a.

## 2026-09-16 — PREDICTION FOR Qwen3.5-2B Q4_K_M, WRITTEN BEFORE THE FILE IS ON THE PHONE

Written to the protocol's rule — the prediction goes in notes.md and is
committed BEFORE the run, so the result cannot be fitted to it.

    model      Qwen3.5-2B-Q4_K_M.gguf
    size       1,280,835,840 B = 1221.5 MiB = 1.1929 GiB
    sha256     aaf42c8b7c3cab2bf3d69c355048d4a0ee9973d48f16c731c0520ee914699223
               MANIFEST.txt, VERIFIED vs HF LFS oid
    versus     Qwen3-1.7B-Q4_K_M.gguf, 1,107,409,472 B = 1056.1 MiB
               +173,426,368 B = +165.4 MiB = +15.66%
    planned    c0, 2 threads, -p 512 then -p 64, -n 128, -lm none, each gated
               on policy6 = 2,802,000 AND policy4 = 2,253,000

**Phone state at the moment of deletion and push, read now:** Qwen3-1.7B
deleted from `/data/local/tmp` at uptime 2154.22 s (35.9 min) on the 14:22
boot, 102 G free on `/data`. `MemFree` 2,197,316 kB, **`MemAvailable`
2,583,580 kB**, `Cached` 611,936 kB (down from 1,199,624 kB — the `rm` freed
the model's own page cache), `SwapFree` 682,492 kB, `AnonPages` 1,261,616 kB.

### 1. PEAK RSS

The measured relationship on Qwen3-1.7B at `-lm none`, both readings taken
today: peak RSS 1,492,260 kB (1457.3 MiB) at `-p 512` and 1,415,980 kB
(1383.0 MiB) at `-p 64`. Over a 1056.1 MiB file that is **+401.2 MiB and
+326.9 MiB of non-file memory**, and **+74.3 MiB is the cost of the 512-token
micro-batch alone**. Two ways to carry that across, and they disagree because
KV, compute and output buffers are not file-proportional:

    additive   (file + the same non-file memory)   1622.9 / 1548.4 MiB
    scaled     (file x 1.380 / x 1.310)            1685.7 / 1600.2 MiB

> **PREDICTED peak RSS at `-p 512`: 1,620-1,690 MiB = 1,659,000-1,730,000 kB.**
> **PREDICTED peak RSS at `-p 64`:  1,545-1,605 MiB = 1,582,000-1,644,000 kB.**
> The additive figure is the point estimate in each case (1,662,000 kB and
> 1,586,000 kB) because the KV and compute buffers scale with context and
> batch, not with file size — but a 2B model has more layers and more KV heads
> than a 1.72B one, so the true answer should sit ABOVE additive and below
> scaled. **The `-p 512` figure lands outside P5's 1.1-1.4 GB band on any
> reading, as Qwen3-1.7B's already does.**

### 2. tg128 AT 2 THREADS ON c0

Token generation on this handset is memory-bandwidth bound — that is the
standing prediction and P2 has held twice. If it is bytes-per-token that binds,
the figure scales with the inverse of the weight bytes read per token, i.e. by
the file-size ratio 1056.1 / 1221.5 = 0.8646 applied to the cooled Qwen3-1.7B
rows (C1 14.11 at `-p 512`, C2 14.91 at `-p 64`):

> **PREDICTED tg128 at 2 threads on c0: 12.2 t/s at `-p 512` and 12.9 t/s at
> `-p 64`, band 11.5-13.5 t/s on both rows.** Below P1's 8-16 t/s ceiling and
> comfortably inside its floor, so **P1 is predicted to hold on this model
> too**. If the measured figure comes in materially BELOW 11.5, the binding
> constraint is not bandwidth alone — architecture or the clock cap is doing
> work the file-size ratio does not capture.

### 3. KILLS

B2-R1 took one cached process at `oom_score_adj 985` with a peak of
1,492,260 kB against `MemAvailable` 2,128,524 kB — a kill with ~636,000 kB of
apparent headroom, because the killer fires on watermarks during the load
transient, not on the final margin. This model asks for **~170,000-240,000 kB
more anonymous memory** than that row did, against a current `MemAvailable` of
2,583,580 kB which will have drifted by the time the row runs.

> **PREDICTED: kills DO occur, on the `-p 512` row for certain and probably on
> the `-p 64` row too. Expect 1-5 kill lines, all in the cached band
> (`oom_score_adj` 900+, `cch` reasons), and expect NOTHING below 900** —
> no `prcp`, no IME, nothing in the foreground bands. **`rc=0` and both rows
> complete.** If anything at `oom_score_adj` < 900 dies, or a row returns
> non-zero, the prediction fails and the model is at this handset's edge rather
> than inside it.

### What this prediction does NOT say

It says nothing about `pp512`. The pp figure depends on compute and on the
clock cap, both of which this repo has failed to predict twice today, and a
band wide enough to be safe would be worthless. It says nothing about Q4_0 —
the repack path has still never executed on this phone. And it is a prediction
about the 6a only; the 7a re-measures anything that fails here.

## 2026-09-16 — Qwen3.5-2B Q4_K_M on the 6a: it runs, at 11.21 t/s and 1.74 GiB, and it costs three cached processes. The prediction written twenty minutes earlier fails on BOTH memory and speed, and P6's inversion does not survive a second model.

    model      Qwen3.5-2B-Q4_K_M.gguf, 1,280,835,840 B = 1221.5 MiB
    sha256     aaf42c8b7c3cab2bf3d69c355048d4a0ee9973d48f16c731c0520ee914699223
               computed ON THE PHONE after the push, matched to MANIFEST.txt's
               HF-LFS-verified value. Size on the phone 1,280,835,840 B.
    pushed     16 Sept 14:59, 39.29 s at 31.1 MB/s over adb, after
               Qwen3-1.7B-Q4_K_M.gguf was deleted from /data/local/tmp --
               one model on the phone at a time, and the Mac's copy is
               manifest-verified so nothing is lost.
    llama-bench reads it as `qwen35 2B Q4_K - Medium`, 1.18 GiB, **1.88 B
    params** -- 9.3% more parameters than Qwen3-1.7B's 1.72 B.

### Row Q1 — c0, 2 threads, -p 512, -n 128, -lm none

    gate         PASSED on the first check, 0 waits, uptime 2277, both rated
    conditions   uptime 2,277.10 s (38.0 min) before, 2,398.12 s after,
                 121.02 s wall. AC power, screen on, no VM, app disabled.
    command      pennybench.sh q1_c0_t2_p512 c0 -- -t 2 -p 512 -n 128 -lm none

    pp512                 51.41 +/- 1.57 t/s      (3.05% error bar)
    tg128                 11.21 +/- 1.00 t/s      (8.92%)
    ceil X1   before/min/after    2,802,000 / **500,000** / 2,401,000 kHz
              min_at uptime 2,361.72 -- **84 s into the row**
    ceil A76  before/min/after    2,253,000 / 2,253,000 / 2,253,000 kHz
              min_at uptime 2,277.10 -- 0 s in, i.e. it NEVER fell
    peak RSS (VmHWM)   1,822,804 kB   1780.1 MiB   1.7384 GiB
    max RssAnon        1,816,464 kB   99.65% of peak
    max RssFile            6,036 kB
    rss_samples              276   (2.28 Hz over 121.02 s)
    MemAvailable       2,561,040 -> 2,709,720 kB   (+148,680)
    MemFree              916,792 -> 1,956,664 kB   (+1,039,872)
    SwapFree             691,708 ->   537,340 kB   (-154,368)
    Cached             1,869,136 ->   981,404 kB   (-887,732)
    pswpin               122,080 ->   122,433      (+353 pages)
    pswpout              750,420 ->   801,817      (+51,397 pages, ~201 MiB)
    pgmajfault           135,232 ->   135,611      (+379)
    ZRAM               570,852K physical for 2,597,632K in swap
    LMK kills                  1 kill (2 lines), MemAvailable before 2,561,040 kB
    rc                         0

    09-16 15:01:10 lowmemorykiller: Kill '.ShannonImsService' (2803), uid 10154,
      oom_score_adj 975 ... reason: low watermark is breached
    09-16 15:01:10 ActivityManager: ... has died: cch  +75 CEM

**500,000 kHz is the lowest X1 ceiling ever recorded in this repo — 17.8% of
rated**, against a previous floor of 851,000 (30.4%, C5). It was first seen 84 s
into a 121 s row, the same place in the row as B2-R1's minimum. The A76 pair
again never moved from rated, the second row running for the second time.

**The `Cached` drop of 887,732 kB does NOT trip the contamination rule and the
difference is instructive.** `Cached` stood at 1,869,136 kB before the row
because the `adb push` had just filled it with this very file; the drop is 71%
of the model's size and what left was the file's own page cache, which under
`-lm none` nothing is mapping and nothing needs after load. C7's collapse was
the same shape by a different mechanism — there the drop happened to a boot
with 97% of its swap already spent, and `pgmajfault` here moved 379 in total.
`SwapFree` ended at 17.1% of `SwapTotal`, above the 10% floor.

### Row Q2 — c0, 2 threads, -p 64, -n 128, -lm none

    gate         PASSED after 3 waits (~15 s), uptime 2432, both rated
    conditions   uptime 2,432.69 s (40.5 min) before, 2,504.55 s after,
                 71.86 s wall.
    command      pennybench.sh q2_c0_t2_p64 c0 -- -t 2 -p 64 -n 128 -lm none

    pp64                  48.14 +/- 1.19 t/s      (2.47% error bar)
    tg128                 10.94 +/- 0.62 t/s      (5.67%)
    ceil X1   before/min/after    2,802,000 / 1,277,000 / 1,745,000 kHz
              min_at uptime 2,475.14 -- 43 s into the row
    ceil A76  before/min/after    2,253,000 / 2,253,000 / 2,253,000 kHz
              min_at uptime 2,432.69 -- 0 s in, NEVER fell
    peak RSS (VmHWM)   1,768,596 kB   1727.1 MiB   1.6866 GiB
    max RssAnon        1,763,176 kB   99.69% of peak
    max RssFile            5,384 kB
    rss_samples              166   (2.31 Hz over 71.86 s)
    MemAvailable       2,710,124 -> 2,922,636 kB   (+212,512)
    MemFree            1,950,824 -> 1,866,088 kB   (-84,736)
    SwapFree             569,084 ->   484,552 kB   (-84,532)
    Cached               985,972 -> 1,280,348 kB   (+294,376)
    pswpin               130,285 ->   130,601      (+316 pages)
    pswpout              801,817 ->   858,675      (+56,858 pages, ~222 MiB)
    pgmajfault           143,481 ->   143,845      (+364)
    ZRAM               589,296K physical for 2,660,660K in swap
    LMK kills                  3 kills (6 lines), MemAvailable before 2,710,124 kB
    rc                         0

    09-16 15:03:46 lowmemorykiller: Kill 'app.seamlessupdate.client' (3038),
      oom_score_adj 945     -> died: cch  +45 CEM
    09-16 15:03:46 lowmemorykiller: Kill 'com.android.DeviceAsWebcam' (3544),
      oom_score_adj 915     -> died: cch  +15 CEM
    09-16 15:03:46 lowmemorykiller: Kill 'com.android.keychain' (3193),
      oom_score_adj 915     -> died: cch  +15 CEM

All three inside one 102 ms window at model load, all `cch` — cached and empty
— and the deepest reached was **915**, well clear of the 900 line and far from
the 201 the 2GB VM reached in rung 3g-ii. Nothing on the screen, no IME, no
foreground process.

### THE PREDICTION, JUDGED. It fails on both measured halves it named.

    PREDICTED peak RSS -p 512   1,659,000 - 1,730,000 kB  (point est 1,662,000)
    MEASURED                    1,822,804 kB
    ** FAILS -- 5.4% above the top of the band, 9.7% above the point estimate **

    PREDICTED peak RSS -p 64    1,582,000 - 1,644,000 kB  (point est 1,586,000)
    MEASURED                    1,768,596 kB
    ** FAILS -- 7.6% above the top of the band, 11.5% above the point estimate **

    PREDICTED tg128 @2t c0      12.2 (-p 512) and 12.9 (-p 64), band 11.5-13.5
    MEASURED                    11.21 +/- 1.00  and  10.94 +/- 0.62
    ** FAILS on both point estimates. The -p 512 row's error bar (10.21-12.21)
       reaches into the band; the -p 64 row's (10.32-11.56) barely grazes its
       floor. Both are slower than predicted, in the same direction. **

    PREDICTED kills             1-5 kill lines, all cached band, nothing below
                                oom_score_adj 900, rc=0, both rows complete
    MEASURED                    Q1: 1 kill / 2 lines, adj 975
                                Q2: 3 kills / 6 lines, adj 945, 915, 915
    ** HOLDS on everything that matters and is one over on the letter of it:
       the band half holds outright -- every casualty cached, deepest 915,
       nothing below 900, rc=0 on both rows. Q2's SIX log lines exceed the
       "1-5 lines" I wrote, though it is THREE kills; the line count was a
       careless unit and the kill count is the quantity meant. Recorded as
       written rather than reinterpreted after the fact. **

**WHY THE MEMORY PREDICTION FAILED, and it is the useful part.** Both models of
it assumed the non-file memory either stays constant or scales with the file.
It did neither:

    model            file kB     peak kB @-p512   non-file kB   peak/file
    Qwen3-1.7B     1,081,455        1,492,260       410,805       1.380
    Qwen3.5-2B     1,250,816        1,822,804       571,988       1.457

**The file grew 15.6% and the non-file memory grew 39.2%.** So peak RSS is not
a fixed multiple of the model file and never was — the KV, compute and output
buffers scale with the model's SHAPE (layers, KV heads, context), which the
file size does not carry. **Any future estimate from file size alone will
understate, and this is the second time today a memory figure has come in above
what was reasoned to it.** The reliable route is llama.cpp's own buffer lines,
which is how the 2.13 GiB was accounted earlier today.

The `-p 512` micro-batch costs **54,208 kB** on this model against 76,280 kB on
Qwen3-1.7B — the one component that did NOT grow.

### AGAINST Qwen3-1.7B, SAME MASK, SAME THREAD COUNT, BOTH COOLED AND GATED

    row              pp t/s          tg128 t/s       wall s    peak RSS kB
    C1  1.7B -p512   55.36 +/- 5.11  14.11 +/- 0.59  103.32     1,491,536
    Q1  2B   -p512   51.41 +/- 1.57  11.21 +/- 1.00  121.02     1,822,804
                     -7.1%           -20.6%          +17.1%     +22.2%

    C2  1.7B -p64    61.79 +/- 1.80  14.91 +/- 0.92   52.57     1,415,980
    Q2  2B   -p64    48.14 +/- 1.19  10.94 +/- 0.62   71.86     1,768,596
                     -22.1%          -26.6%          +36.7%     +24.9%

**Token generation fell further than bytes-per-token predicts.** The file ratio
is 0.865 and the parameter ratio 0.915; the measured `tg128` ratios are 0.794
and 0.734. So the extra cost is not only more weight bytes to read — 9.3% more
parameters bought a 20.6-26.6% slowdown. **Pure memory-bandwidth scaling does
not account for it, and what does is not established here.**

### P6 DOES NOT SURVIVE A SECOND MODEL, AND THAT IS THE FINDING OF THIS PAIR

P6 predicted `-p 64` returns a LOWER pp figure than `-p 512`. On Qwen3-1.7B it
failed three times over — `-p 64` was 11.6% to 29.6% FASTER. **On Qwen3.5-2B it
holds: pp64 48.14 against pp512 51.41, `-p 64` is 6.4% SLOWER**, which is the
direction P6 named.

**And the throttling cuts the wrong way for an easy explanation.** Q1 ran with
its X1 ceiling down to 500,000 kHz; Q2's only reached 1,277,000. **The row with
far worse throttling returned the higher pp figure.** So on this model the
inversion is absent even though the thermal conditions favoured it appearing.

    model         pp512    pp64    -p 64 is
    Qwen3-1.7B    55.36   61.79    +11.6% FASTER   (P6 fails)
    Qwen3.5-2B    51.41   48.14     -6.4% SLOWER   (P6 holds)

**Whatever produces the inversion is a property of the model, not of the
handset**, and it is still unexplained. The `-ub` test that would separate
batch size from micro-batch size remains unrun on either model.

### What this entry does NOT say

**One reading per row.** Q1 and Q2 are single runs. Neither is repeated, and
`tg128` at `-p 512` carries an 8.92% error bar.

**The two rows are not thermally matched to each other.** Both were gated at
rated clock, and they then went to 500,000 and 1,277,000 kHz respectively. The
gate controls the start and nothing else, as every row today has shown.

**Nothing at 1 thread, nothing at 4 threads, nothing on `f0`, nothing
unpinned.** Matt's reduced scope for this model was two rows, so P1-P4 are not
re-judged on it — only P5 and P6 are, and P5 only on the RSS and kills this
pair produced.

**`SwapFree` is down to 484,552 kB of 3,145,724 — 15.4%**, from 1,122,300 kB at
the start of B2-R1. Three rows have spent two thirds of this boot's swap. The
contamination rule's floor is 10% and Gemma has not run yet.

**Nothing at Q4_0** — the repack path has still never executed on this phone —
no thermal run, nothing on battery, no time-to-first-token, and `policy0` still
never sampled. And this is an absolute feasibility measurement of the 6a, not a
native-versus-VM comparison, which is closed.

## 2026-09-16 — Gemma 4 E2B RUNS on the 6a: 9.99 t/s, peak RSS 3.09 GiB, rc=0 — and it costs 34 background processes killed in 2.4 seconds. The prediction written this morning holds on memory and on kills, and its one wrong sentence is the instructive part.

    model      gemma-4-E2B-it-Q4_K_M.gguf, 3,106,738,272 B = 2963.0 MiB
    sha256     740185b21d22ceb83a11c3aa62ad5842ef32c70f6096d756bbee85a1e4ec34b8
               computed ON THE PHONE, matched to MANIFEST.txt's
               HF-LFS-verified value. Size on the phone 3,106,738,272 B.
    pushed     16 Sept 15:07, 94.78 s at 31.3 MB/s, after Qwen3.5-2B was
               deleted. 99 G free on /data afterwards.
    llama-bench reads it as `gemma4 E2B Q4_K - Medium`, 2.88 GiB, **4.65 B
    params** -- the total parameter count, not the "E2B" effective figure.

### Row G1 — c0, 2 threads, -p 64, -n 128, -lm none. ONE ROW, as scoped.

    gate         PASSED on the first check, 0 waits, uptime 2765, both rated
    conditions   uptime 2,765.45 s (46.1 min) before, 2,851.22 s after,
                 85.77 s wall. AC power, screen on, no VM, app disabled.
    command      pennybench.sh g1_c0_t2_p64 c0 -- -t 2 -p 64 -n 128 -lm none

    pp64                  30.53 +/- 0.55 t/s      (1.80% error bar)
    tg128                  9.99 +/- 0.60 t/s      (6.01%)
    ceil X1   before/min/after    2,802,000 / 1,106,000 / 1,745,000 kHz
              min_at uptime 2,826.25 -- 61 s into the row
    ceil A76  before/min/after    2,253,000 / 1,836,000 / 2,253,000 kHz
              min_at uptime 2,844.43 -- **79 s in. The A76 pair FELL on this
              row**, the first of the four rows since the reboot where it did,
              and `taskset c0` scheduled nothing onto it.
    peak RSS (VmHWM)   3,244,696 kB   3168.6 MiB   3.0944 GiB   3.3226 GB
    max RssAnon        3,239,088 kB   3163.2 MiB   3.0890 GiB   99.83% of peak
    max RssFile            5,328 kB
    rss_samples              196   (2.29 Hz over 85.77 s)
    MemAvailable       3,101,624 -> 3,784,816 kB   (+683,192)
    MemFree              264,548 -> 3,521,020 kB   (+3,256,472)
    SwapFree             245,000 -> 1,599,500 kB   (+1,354,500 -- it ROSE)
    Cached             3,060,868 ->   494,968 kB   (-2,565,900)
    pswpin               134,616 ->   487,778      (**+353,162 pages, ~1.35 GiB**)
    pswpout              922,767 -> 1,364,003      (**+441,236 pages, ~1.68 GiB**)
    pgmajfault           147,883 ->   502,889      (**+355,006**)
    ZRAM               324,724K physical for 1,524,464K in swap
    LMK kills                 34 kills (68 lines), MemAvailable before
                              3,101,624 kB
    rc                         0

**IT RAN. `rc=0`, both tests completed, 9.99 t/s of token generation.** The
morning's prediction allowed for "a failed load"; there was none.

### THE COST: 34 PROCESSES, ALL IN 2.434 SECONDS, ALL AT THE SAME DEPTH

    window          09-16 15:09:19.652 -> 15:09:22.086   (2.434 s, at load)
    kills                34, each with its own `has died` line
    oom_score_adj        **905 on all 34 of them, without exception**
    reason               19  min watermark is breached even after kill
                         12  low watermark is breached and swap is low
                          2  low watermark is breached
                          1  min watermark is breached and swap is low
    every casualty       `cch CEM` -- cached and empty

**The killer emptied the entire cached band and stopped at its edge.** Nothing
below 905 died: no `prcp`, no IME, nothing in the 200/100/0 foreground bands,
and not the benchmark. For scale, rung 3g-ii's 2GB VM on a phone somebody was
using reached **adj 201**; this reached 905 and stayed there, because the phone
was idle and had 34 disposable processes to give. Among them: `com.android.settings`,
`android.process.acore`, `android.process.media`, `com.android.packageinstaller`,
`com.android.externalstorage`, `com.android.rkpdapp` (remote key provisioning,
part of the attestation story that justifies this phone) and
`com.android.shell` — which is adb's own family, and the benchmark survived it.

**Swap reached zero.** One kill line reads `swap is low (0kB < 314572kB)`.
`SwapFree` then ROSE from 245,000 to 1,599,500 kB across the row, because 34
processes' swapped pages were freed with them — the row ended with more swap
headroom than it started, by killing for it.

### THIS IS THE FIRST ROW IN THE REPO THAT SHOWS WHAT STARVATION LOOKS LIKE ON THE COUNTERS

The `/proc/vmstat` counters were added this morning specifically because C7's
mechanism had to be inferred from `Cached` alone. This row is the positive
control they never had:

    row              pgmajfault   pswpin       pswpout      verdict
    B2-R1 1.7B            +488        +427     +129,239     comfortable
    Q2    2B              +364        +316      +56,858     comfortable
    G1    Gemma        +355,006   +353,162     +441,236     thrashing

**Three orders of magnitude.** ~1.35 GiB read back IN from swap and ~1.68 GiB
pushed out, in 85.77 s, on a row whose own peak was 3.09 GiB. That is a
process whose working set does not fit and which is paying for it continuously
— and it is exactly the signature C7 was hypothesised to have and could not be
shown to have. **It does not retrospectively prove anything about C7**; it
establishes what the counters read when the hypothesis is true, so the next
occurrence is decidable rather than arguable.

`Cached` fell 2,565,900 kB — 84.6% of the model file, which the `adb push` had
put there minutes earlier.

### THE PREDICTION (notes.md ~5990, written this morning before any Gemma run), JUDGED

    PREDICTED  "roughly 3.0-3.1 GB of ANONYMOUS memory"
    MEASURED   max RssAnon 3,239,088 kB = 3.0890 GiB = 3.3168 GB
    ** HOLDS on the GiB reading, which is the one the prediction's own working
       used (it reasoned in MiB from a 2963.0 MiB file). On the decimal GB
       reading it is 7.0% over the top of the band. The unit ambiguity is the
       prediction's fault, not the measurement's, and it is recorded rather
       than resolved in the prediction's favour. **

    PREDICTED  "Expect the lowmemorykiller to take processes during the load,
                and expect either a failed load or a run that only completes
                because the killer freed enough first."
    MEASURED   34 kills in 2.434 s AT LOAD, then rc=0 and both tests complete
    ** HOLDS, and precisely: the second branch is what happened. MemFree went
       264,548 -> 3,521,020 kB across the row. The killer freed enough first. **

    PREDICTED  "3.1 GB of unreclaimable anonymous memory does not fit in
                [idle MemAvailable of 1,771,920-2,617,156 kB]"
    MEASURED   MemAvailable before the row was 3,101,624 kB, and it fit
    ** THE SENTENCE IS WRONG AND THE ARITHMETIC BEHIND IT IS RIGHT. Two things
       it did not account for. First, `MemAvailable` was 3,101,624 kB rather
       than an idle 2.0 GB because the `adb push` had just filled `Cached` with
       3,060,868 kB of this very file, and `MemAvailable` counts reclaimable
       page cache. Second, and the real answer: `MemAvailable` is not a
       ceiling. It is what the kernel will hand over WITHOUT killing anything,
       and the kernel is entirely willing to kill. The model did not fit in
       what the phone had spare; it fit in what the phone was prepared to take
       from everything else. **

    NOT TESTED  "Under mmap the peak would be higher still ... so mmap is not
                the escape." No mmap row was run on Gemma.
    NOT TESTED  "the KV buffer will be larger than Qwen3-1.7B's 28.00 MiB".
                llama.cpp's buffer lines were not captured for this row.

### PEAK RSS IS NOT A FIXED MULTIPLE OF THE MODEL FILE, AND THREE MODELS NOW PROVE IT

    model          file kB     peak RSS kB   non-file kB   peak / file
    Qwen3-1.7B   1,081,455       1,492,260      410,805       1.380
    Qwen3.5-2B   1,250,816       1,822,804      571,988       1.457
    Gemma 4 E2B  3,033,924       3,244,696      210,772       **1.070**

All three at `-lm none`, `-p 512` for Qwen3-1.7B and `-p 64` for the other two.
**The multiplier ranges 1.07 to 1.46 and does not move with file size** — the
largest file has the smallest overhead. Twelve hours ago this repo predicted
Qwen3.5-2B from a multiplier and was 9.7% low; predicting Gemma from
Qwen3.5-2B's multiplier would have been 36% HIGH. **Estimating peak RSS from
file size is not a method.** llama.cpp's own buffer lines are, and they are
what accounted for the 2.13 GiB earlier today.

### SPEED: THE FILE IS 2.43x BIGGER AND TOKEN GENERATION IS 8.7% SLOWER

    row              file MiB    pp64 t/s          tg128 t/s        wall s
    Q2  Qwen3.5-2B     1221.5    48.14 +/- 1.19    10.94 +/- 0.62    71.86
    G1  Gemma 4 E2B    2963.0    30.53 +/- 0.55     9.99 +/- 0.60    85.77
                       x2.43     -36.6%            **-8.7%**         +19.4%

**Prompt processing fell by more than a third; token generation barely moved.**
On a purely bandwidth-bound reading a 2.43x larger weight file should have cost
far more than 8.7% per token. **The candidate explanation is the architecture —
"E2B" is an effective-2B configuration and llama-bench's 4.65 B is the total
parameter count, so the bytes touched per token may be far below the file size
— but nothing here measured that, and it is offered as a candidate, not a
finding.** It is also the most commercially interesting number of the day: the
largest model tested generates tokens at 9.99 t/s on this handset.

### What this entry does NOT say

**One row, once.** No repetition, no second `-p` value, no other mask, no other
thread count. Every Gemma figure here is n=1.

**It ran on a boot that had already run three benchmark rows**, with `SwapFree`
at 245,000 kB (7.8% of `SwapTotal`) when the row started — **below the 10%
floor the contamination rule names**. The rule was written for a boot whose
cache had collapsed AND whose swap was spent; this boot's `Cached` was 3.06 GB
at the start, so it does not meet the rule as written. **But this row would be
worth repeating on a fresh boot before the 34-kill figure is quoted as the
cost on a phone in its normal state**, and that is a real caveat on the
headline, not a formality.

**34 kills on an IDLE phone with 34 disposable processes to take.** rung 3g-ii
showed that a phone somebody is using has fewer cheap victims and the killer
goes deeper — to adj 201 there. **What Gemma costs on a phone in use is
untested**, and it is the number that would matter to a product.

**Nothing at Q4_0** — the repack path has still never executed on this phone —
no thermal run, no sustained run, nothing on battery, no time-to-first-token,
`policy0` never sampled, and the model was not asked to produce text, so
nothing here says its output is sensible. And this is an absolute feasibility
measurement of the 6a, not a native-versus-VM comparison, which is closed.

## 2026-09-16 — NATIVE llama.cpp FEASIBILITY ON THE 6a. The answer is YES for 1.7-2B, at 10-15 tokens/second and 1.35-1.74 GiB, and the constraint that bites is memory rather than compute.

This is the closing entry for the native benchmark work started this morning.
It gathers every row run, judges all six predictions, answers the question the
work was set up to ask, and says what it does not answer. Every figure below is
from `-lm none` unless the line says otherwise; the four mmap figures from the
RSS chase are in their own entries and are not repeated as speed results.

### THE APPARATUS

    handset      Pixel 6a (bluejay), Tensor G1: 2x Cortex-X1 (cpus 6-7,
                 policy6, rated 2,802,000 kHz), 2x Cortex-A76 (cpus 4-5,
                 policy4, 2,253,000), 4x Cortex-A55 (cpus 0-3, policy0,
                 1,803,000 -- NEVER SAMPLED). 5,718,280 kB MemTotal,
                 3,145,724 kB of zram swap. GrapheneOS 2026091001,
                 Android 17, bootloader LOCKED. AC power, screen on.
    binary       llama-bench, llama.cpp commit 38a5b42d9 (10989), built on
                 the Mac with NDK 30.0.16248370,
                 -DGGML_CPU_ARM_ARCH=armv8.2-a+dotprod+fp16, GGML_NATIVE=OFF,
                 BUILD_SHARED_LIBS=OFF. Disassembly: 0 smmla/ummla/usmmla,
                 0 SVE/SME, 898 sdot/udot. sha256 44015c0614b3f1c0f4ee3240fb
                 8f3a37503420ab7285a36a10ad14abaaaeb84e.
    wrapper      pennybench.sh, sha256 484d75d4f006822afe3354cd8dd4bc7fba45
                 f42d5178639c644440643e3b92ab, byte-identical on the phone.
                 It supplies every measured column except pp/tg t/s.
    correctness  llama-simple returned correct text from the Q4_K/Q6_K
                 kernels before any timing was taken. **The Q4_0 repack path
                 has still never executed on this phone.**
    masks        c0 = 0xc0 = cpus 6,7 (the X1 pair). f0 = 0xf0 = cpus 4,5,6,7
                 (X1 + A76). "un" = unpinned.

### EVERY ROW RUN, IN THREE GROUPS THAT MUST NOT BE MIXED

**Group A — Qwen3-1.7B Q4_K_M, 15 Sept boot, NO thermal gate.** Run back to
back with 15-28 s between them, against a ~110 s recovery. Each row therefore
started at an unrecorded clock. **These are not quoted as this chip's speed.**

    row  mask  -t   -p    pp t/s          tg128 t/s       wall s  peak RSS kB  kills
    r1   c0     1   512   32.40 +/- 0.78   9.89 +/- 0.38  162.19   1,491,612     0
    r2   c0     1    64   30.54 +/- 0.69   9.86 +/- 0.36   81.04   1,415,860     0
    r3   c0     2   512   45.39 +/- 5.78  10.35 +/- 0.13  132.62   1,491,668     0
    r4   c0     2    64   49.44 +/- 2.55  12.75 +/- 2.27   64.86   1,415,856     0

**Group B — Qwen3-1.7B Q4_K_M, 15 Sept boot, GATED at rated clock on both
sampled policies.** The matrix as designed. C7 is struck through: it ran on a
boot whose page cache had collapsed by the size of the model and whose swap was
97.0% spent, and the re-run on a fresh boot disagrees with it by 39-69%.

    row  mask  -t   -p    pp t/s          tg128 t/s       wall s  peak RSS kB  kills
    C1   c0     2   512   55.36 +/- 5.11  14.11 +/- 0.59  103.32   1,491,536     0
    C2   c0     2    64   61.79 +/- 1.80  14.91 +/- 0.92   52.57   1,415,980     0
    C3   f0     4   512   66.19 +/- 3.84  12.78 +/- 2.41  100.41   1,491,364     0
    C4   f0     4    64   84.68 +/- 3.00  14.23 +/- 1.84   53.91   1,415,824     0
    C5   un     4   512   63.52 +/- 4.71  11.74 +/- 1.76  106.03   1,491,144     0
    C6   un     4    64   82.34 +/- 2.80  14.47 +/- 1.73   52.93   1,415,508     0
    --   ----   -   ---   --------------  --------------  ------   ---------   ---
    C7   c0     1   512   22.68 +/- 5.38   6.00 +/- 0.27  246.73   1,491,292     0
         VOID -- contaminated boot. Superseded by B2-R1 below.

**Group C — 16 Sept 14:22 boot, GATED, one row per line, three models.** This
is the clean group: a boot that had killed nothing, `MemAvailable` 2.13-3.10 GB
at the start of each row, and the swap and major-fault counters recorded either
side.

    row     model         mask -t  -p   pp t/s          tg128 t/s       wall s
    B2-R1   Qwen3-1.7B     c0   1  512  31.45 +/- 0.70  10.13 +/- 0.64  164.38
    Q1      Qwen3.5-2B     c0   2  512  51.41 +/- 1.57  11.21 +/- 1.00  121.02
    Q2      Qwen3.5-2B     c0   2   64  48.14 +/- 1.19  10.94 +/- 0.62   71.86
    G1      Gemma 4 E2B    c0   2   64  30.53 +/- 0.55   9.99 +/- 0.60   85.77

    row     peak RSS kB   RssAnon %  MemAvail before kB  kills  deepest adj  rc
    B2-R1     1,492,260      99.59         2,128,524       1        985       0
    Q1        1,822,804      99.65         2,561,040       1        975       0
    Q2        1,768,596      99.69         2,710,124       3        915       0
    G1        3,244,696      99.83         3,101,624      34        905       0

**B2-R1 replaces C7 and reproduces r1**: 31.45 / 10.13 against 32.40 / 9.89,
error bars overlapping on `pp512`. The falsification condition was committed
before that row ran.

### THE CLOCK, WHICH TURNED OUT TO BE THE STORY OF THE DAY

`scaling_max_freq` is the CEILING the governor may not exceed. Both sampled
clusters lower their own ceiling under load, and the wrapper samples it
throughout each row rather than after it.

    row     X1 before/min/after kHz        min at    A76 before/min/after kHz
    C1      2802000/1106000/1745000         --       2253000/1836000/2253000
    C2      2802000/1277000/2188000         --       2253000/2253000/2253000
    C3      2802000/1106000/1106000         --       2253000/ 910000/1024000
    C4      2802000/1106000/2188000         --       2253000/1328000/1328000
    C5      2802000/ 851000/ 984000         --       2253000/ 910000/1024000
    C6      2802000/ 984000/2188000         --       2253000/1328000/1328000
    B2-R1   2802000/1426000/2630000       86 s       2253000/2253000/2253000
    Q1      2802000/ 500000/2401000       84 s       2253000/2253000/2253000
    Q2      2802000/1277000/1745000       43 s       2253000/2253000/2253000
    G1      2802000/1106000/1745000       61 s       2253000/1836000/2253000

- **The X1 ceiling fell below half its rated clock on every gated row without
  exception.** Floor **500,000 kHz on Q1 — 17.8% of rated**, and it was reached
  84 s into a 121 s row.
- **The A76 pair falls too, and it falls on rows where `taskset c0` schedules
  nothing onto it** — C1 and G1 both. Floor 910,000 kHz, 40.4% of rated.
  **The limiter acts across the package, not per cluster.** An earlier claim in
  CLAUDE.md that only the X1 pair is capped came from one reading taken after a
  row had ended, during recovery, and is refuted.
- Three of the four Group C rows saw their minimum **43-86 s into the row**, so
  the descent is well inside a single row's runtime.
- **Gating buys a known starting clock and nothing else.** No row ended at the
  X1's rated clock.
- **What writes the cap is NOT identified.** `/sys/class/thermal/` is
  `Permission denied` to the shell user, so no temperature was read at any
  point. "Throttle" describes the behaviour; a power or current limiter, or a
  platform HAL policy, would look identical from here.
- `policy0`, the four A55s, was never sampled on any row. The unpinned rows in
  particular have a third of the chip unobserved.

### MEMORY

    model         file kB     peak RSS kB   non-file kB  peak/file  RssAnon %
    Qwen3-1.7B  1,081,455  1,415,508-1,492,260  ~410,805    1.380     99.6%
    Qwen3.5-2B  1,250,816  1,768,596-1,822,804  ~571,988    1.457     99.7%
    Gemma 4 E2B 3,033,924        3,244,696       210,772    1.070     99.8%

- **At `-lm none`, peak RSS IS the working set** — 99.6-99.8% anonymous, and
  anonymous pages cannot be dropped, only compressed into zram. Under mmap the
  same model peaks at 2,230,268 kB, but ~817 MiB of that is dead
  already-repacked originals and the figure cannot be used to size anything.
- **Peak RSS is NOT a fixed multiple of the model file.** The multiplier ranges
  **1.07 to 1.46** across three models and does not move with file size: the
  largest file has the smallest overhead. Predicting Qwen3.5-2B from
  Qwen3-1.7B's multiplier was 9.7% low; predicting Gemma from Qwen3.5-2B's
  would have been 36% high. **llama.cpp's own buffer lines are the method;
  file size is not.**
- **The `-p 512` default micro-batch costs 54,208-76,280 kB** over `-p 64`, and
  it is what pushes Qwen3-1.7B out of P5's band.
- **Swap on this handset is zram** — compressed in RAM at ~4.25:1, not disk.
  `/proc/swaps` and every attribute under `/sys/block/zram0` are
  `Permission denied` to the shell user; `dumpsys meminfo` is readable and is
  where the ZRAM line comes from.

### THE KILLS, AND THE PROTOCOL POINT THEY FORCED

    starting MemAvailable   rows            kills
    2,695,176-2,848,012 kB  all 11 of Groups A and B      0
    2,128,524 kB            B2-R1 (Qwen3-1.7B)            1   adj 985
    2,561,040 kB            Q1    (Qwen3.5-2B)            1   adj 975
    2,710,124 kB            Q2    (Qwen3.5-2B)            3   adj 945, 915, 915
    3,101,624 kB            G1    (Gemma 4 E2B)          34   all adj 905

**Every zero-kill row started 570,000-720,000 kB higher than B2-R1 did, on a
boot that had been running 17-18 hours, had already shed its cheap processes
and had spent 97% of its swap getting there.** That headroom was the product of
earlier kills. The fresh-boot rows are the representative condition and the
eleven zero-kill rows were flattered. **A kill count is meaningless without the
row's `MemAvailable` before it**, and that is now a protocol rule.

**No casualty of any row was below `oom_score_adj` 905.** Every one was `cch` —
cached and empty. Nothing in the foreground bands, no IME, and never the
benchmark. For contrast, rung 3g-ii's 2GB VM on a phone somebody was using
reached adj 201.

**Gemma's 34 kills landed in 2.434 seconds, at load**, and the row then
completed with `rc=0`. `MemFree` went 264,548 -> 3,521,020 kB across it. The
model did not fit in what the phone had spare; it fit in what the phone was
prepared to take from everything else. **`MemAvailable` is not a ceiling — it
is what the kernel hands over WITHOUT killing, and the kernel is willing to
kill.**

**The vmstat counters separate comfort from starvation by three orders of
magnitude**, and G1 is the positive control the C7 argument never had:

    row     pgmajfault   pswpin       pswpout
    B2-R1        +488        +427     +129,239
    Q1           +379        +353      +51,397
    Q2           +364        +316      +56,858
    G1       +355,006   +353,162     +441,236

### THE SIX PREDICTIONS, WRITTEN BEFORE THE FIRST RUN, JUDGED

    P1  tg 8-16 t/s at 2 threads pinned to c0
        **HOLDS, and it is the most robust of the six** -- it holds on all
        THREE models, which none of the others was tested against.
        Qwen3-1.7B 14.11 and 14.91; Qwen3.5-2B 11.21 and 10.94; Gemma 4 E2B
        9.99. All five figures inside 8-16. It is not safe at the top: C2's
        14.91 came within 1.09 of the ceiling and a cooled tg16 read 17.96,
        already outside. A faster model than these would fail P1 upward.

    P2  tg at 4 threads on f0 < 1.3x the 2-thread c0 figure
        HOLDS, and by far more than predicted -- four threads are SLOWER than
        two, not merely short of 1.3x: 12.78/14.11 = 0.91x at -p 512,
        14.23/14.91 = 0.95x at -p 64.
        **WHY is NOT established, and the earlier judgement line saying the
        A76 pair gives "no benefit at all" overstated its own body.** On C3
        and C5 the A76 ceiling was pinned to 910,000 kHz, 40.4% of rated, so
        what was measured is two X1s plus two heavily-capped A76s. Memory
        bandwidth and the A76 cap are not separable by any row in this matrix.

    P3  pp512 at 4 threads on f0 >= 2x pp512 at 1 thread on c0
        HOLDS, on the clean baseline and on both earlier ones.
        66.19 / 31.45 = 2.10x against B2-R1, the fresh-boot row.
        66.19 / 32.40 = 2.04x against r1. Prompt processing scales with
        cores; token generation does not. That contrast is the single
        clearest result of the day and it held across every comparison.

    P4  unpinned 4 threads no faster than pinned f0
        HOLDS on three of four comparisons and is a wash on the fourth.
        pp512 -4.0%, tg128 -8.1%, pp64 -2.8%; tg128 at -p 64 is +1.7%, inside
        error bars of 12.0% and 12.9%. Never faster by more than its own
        noise. **Pinning is not a speed win; it is what makes a figure mean
        something**, and unpinned figures are still never quoted as the
        chip's speed.

    P5  peak RSS 1.1-1.4 GB, and NO kills on any Qwen3-1.7B run
        **KILLS HALF: FAILS under the realistic starting condition.** At
        MemAvailable 2,128,524 kB -- a phone that has not already been
        cleared by hours of kills -- loading Qwen3-1.7B at -lm none takes one
        cached process at adj 985. The eleven zero-kill rows started far
        higher on a boot that had freed that headroom by killing things.
        **RSS HALF: HOLDS AT -p 64 AND FAILS AT -p 512**, and not on units.
        -p 64: 1,415,508-1,415,980 kB = 1.350 GiB, inside. -p 512:
        1,491,144-1,492,260 kB = 1.422 GiB AND 1.492 GB, outside on both
        readings. The default 512 micro-batch is what puts it over.
        On the two larger models it fails outright, as expected of a
        prediction written about Qwen3-1.7B.

    P6  -p 64 returns a LOWER pp t/s than -p 512
        **MODEL-DEPENDENT, which no one predicted.**
            Qwen3-1.7B   -p 64 is +11.6% to +29.6% FASTER   P6 FAILS, x3
            Qwen3.5-2B   -p 64 is  -6.4% SLOWER              P6 HOLDS
        The 2-thread inversion was first attributed to throttling; gating both
        rows at rated clock WIDENED it, which rules out the cool start.
        **It does not rule out throttling** -- every gated row fell to ~1.1 GHz
        DURING itself and a pp512 repetition is ~8x longer under load, so the
        earlier line saying the attribution was "refuted" overstated it. The
        honest reading is **inseparable from throttling on the evidence
        available**. Against that, Q1 ran with a 500,000 kHz X1 floor and
        still beat Q2, so on Qwen3.5-2B the thermal conditions favoured the
        inversion appearing and it did not. **The one cheap test that would
        separate batch size from micro-batch size -- vary `-ub` independently
        of `-p` -- has not been run on either model.**

### THE PLAIN ANSWER

**Can this silicon run a 1.7-2B model usefully? YES.**

On a Pixel 6a with Tensor G1, GrapheneOS and a locked bootloader, on AC power
with the screen on, a 1.7B model quantised to Q4_K_M generates **14.1-14.9
tokens per second** pinned to the two Cortex-X1 cores, and a 2B model
**10.9-11.2**. Prompt processing runs at **48-85 t/s** depending on cores and
batch. Peak resident memory is **1.35-1.42 GiB for the 1.7B and 1.69-1.74 GiB
for the 2B**, all of it anonymous. Loading the 2B on a phone that has not
already been cleared costs **one to three cached, empty background processes**,
none deeper than `oom_score_adj` 915, and nothing a person would notice.

For scale: unhurried speech is about 2.5 words a second, and reading aloud
about 3. **Both models generate faster than a person reads out loud**, by
roughly three to five times, and the smaller one has meaningful headroom on top
of that.

**The constraint that bites is memory, not compute.** The chip is fast enough
and gets slower as it works — the X1 ceiling falls to between 18% and 46% of
rated inside every row — but it never fell so far that the models stopped being
usable. Memory is where the edges are: the `-p 512` default costs 54-76 MB for
nothing on these models, peak RSS cannot be predicted from file size, and the
phone buys the headroom by killing background processes.

**And the ceiling was found, higher than expected.** Gemma 4 E2B — a 2.89 GiB
file, 2.4 times the size of the 2B — **also ran, at 9.99 t/s with a peak of
3.09 GiB and `rc=0`**, which the morning's prediction allowed might fail to
load. It cost 34 background processes killed in 2.4 seconds. So the honest
statement of the limit is not "3 GB does not fit" but **"3 GB fits, and the
price is the whole cached band."** On a phone somebody is actually using, that
price is unmeasured and rung 3g-ii says it would be paid deeper.

### WHAT THIS DOES NOT SAY

**It is not a native-versus-VM comparison.** That decision is closed on other
grounds — crosvm takes ~1.92 GB from the host at VM creation, the guest cannot
pin cores, and this device cannot make a protected VM. Nothing here reopens it,
and a good native figure is not an argument about a VM.

**It is not a product measurement, and the shape of the difference is known.**
Every row here is 52-247 seconds on an idle phone on AC power with the screen
on and nothing else running. **There is no thermal run, no sustained run,
nothing on battery, nothing with the screen off, and no measurement of a model
held resident for hours** — which is the product shape. The clock data says
plainly that a longer run would be slower: every row was still descending when
it ended.

**No time-to-first-token, and no figure with a cached prefix.** `llama-bench`
excludes load time from its t/s, so nothing here says how long a user waits
before the first word.

**No output was judged.** `llama-simple` returned correct text once, before any
timing, which is what rules out a broken kernel. No benchmark row produced text
a person read. **Tokens per second is not quality**, and nothing in this repo
has looked at whether any of these three models says anything useful.

**n=1 nearly everywhere.** Only `tg128` at 1 thread has been measured twice
under matched conditions (9.89 and 9.86, 0.30% apart). Error bars on the
four-thread `tg128` rows run to 18.9%, and B2-R1's agreement with r1 is two
different thermal states agreeing, not a repetition.

**Q4_0 has never run on this phone**, so llama.cpp's ARM dot-product repack
path is untested here. `policy0` — four of the eight cores — was never sampled.
The mechanism behind the clock cap was never identified because no temperature
is readable. And Gemma's single row ran on a boot with `SwapFree` at 7.8%,
below the contamination rule's own floor; it is reported with that caveat
rather than without it.

**Everything above is "on the 6a".** This handset is temporary and goes back to
Back Market; the 7a that replaces it has more memory. Anything that fits here
fits there, and anything that failed here must be re-measured there before it
is called a no.

## 2026-09-16 — three corrections to the closing entry, and G1 repeated on a fresh boot: it is FASTER (35.05 / 11.21) and kills MORE (39), so the caveat was right and G1 is the figure that was depressed

### CORRECTION 1 — TOKENS ARE NOT WORDS. The closing entry's "three to five times faster than a person reads aloud" is wrong and the figure is 2.5-4x.

notes.md 7798 reads: *"unhurried speech is about 2.5 words a second, and reading
aloud about 3. Both models generate faster than a person reads out loud, by
roughly three to five times."* **It divided tokens per second by words per
second as if the units matched. They do not.** A token is roughly 0.75 of an
English word, so:

    10-15 tokens/s  x 0.75  =  7.5-11.25 words/s
    against reading aloud at ~3 words/s
    = **2.5x to 3.75x, call it 2.5-4x** -- not 3-5x

Raised by Matt. The corrected sentence: **both models generate text faster than
a person reads it aloud, by roughly two and a half to four times.** The
conclusion it supports is unchanged — the models are comfortably faster than
reading speed — but the multiple was inflated by a third and the arithmetic was
wrong, not merely imprecise. **Every future comparison of a t/s figure to human
speech must apply the ~0.75 words-per-token conversion explicitly.** There is
no copy of this claim in CLAUDE.md to correct; it existed only in that entry.

### CORRECTION 2 — "MEMORY RATHER THAN COMPUTE" IS A CLAIM ABOUT 1-4 MINUTE ROWS AND IS NOT ESTABLISHED FOR SUSTAINED USE.

The closing entry's headline and its body at 7803 both say the constraint that
bites is memory, not compute. **That entry's own clock section contradicts any
broader reading of it: every row was still descending in clock when it ended.**
Raised by Matt, and he is right that the two cannot both stand unqualified.

**The claim as it must now be written, here and in CLAUDE.md: memory binds
rather than compute IN ROWS OF 1-4 MINUTES; the sustained case is unmeasured
and the thermal cap may bind there.** Every row in this repo is 52-247 seconds
on an idle phone on AC power with the screen on. A model held resident and
working for ten minutes has never been run, and the clock data is the reason to
expect it to be different rather than the reason to assume it is not.
CLAUDE.md's Sequencing bullet is corrected in the same commit as this entry.

### CORRECTION 3 — "NO ROW ENDED AT THE X1's RATED CLOCK" IS REFUTED BY THE ROW BELOW.

The closing entry states it twice. **G2 ended with `policy6/scaling_max_freq` at
2,802,000 kHz — rated — after falling to 1,426,000 during the row.** It is the
first row in this repo to finish at the X1's rated ceiling. The claim was true
of all fifteen rows when it was written and is now false; the shape it was
describing (the cap engages inside every row) is untouched.

### THE BOOT

    rebooted     16 Sept 15:20:52 BST, ending a 3,463 s (57.7 min) boot.
                 Unlocked by hand. adb returned at uptime 57 s, 15:22:09.
    app state    com.pennyspike.probe2a still `disable-user`'d.
                 `Running VMs: []`. AC power, screen on.
    on the phone gemma-4-E2B-it-Q4_K_M.gguf, 3,106,738,272 B, the only model.
                 pennybench.sh sha256 484d75d4...3a92ab, byte-identical to the
                 repo copy, re-checked after the reboot.

    uptime s   MemFree     MemAvailable   Cached      SwapFree    AnonPages
     308.17    1,298,152   2,190,520      1,120,932     988,156   1,656,008
    1500.30    1,197,664   2,135,144      1,162,652   1,126,396   1,746,476

    uptime s   pswpin    pswpout   pgmajfault   ZRAM
     308.17     30,544   570,929      43,305    481,996K / 1,868,288K in swap
    1500.30    106,806   615,920     119,694    485,120K / 2,012,928K in swap

**The settling curve now reproduces across two consecutive fresh boots to
within 1.2%**, which is better than anything else measured in this repo:

    boot            ~5 min                  ~25 min
    14:22 boot      2,193,244 @ 302.94      2,159,732 @ 1515.90
    15:21 boot      2,190,520 @ 308.17      2,135,144 @ 1500.30
    apart              0.12%                   1.14%

Both start high and drift down, where the 15 Sept boot climbed from 940,640 kB.
**~2.1-2.2 GB at five minutes and ~2.14-2.16 GB at twenty-five is the idle
figure for this handset on a fresh boot**, and it is now read twice.

**`policy0` WAS SAMPLED, for the first time in this repo** — 1,803,000 kHz, its
rated clock, at both readings. It has still never been sampled DURING a row,
which is the gap the closing entry names; this closes only the "never read at
all" half.

### Row G2 — Gemma 4 E2B, c0, 2 threads, -p 64, -n 128, -lm none. FRESH BOOT. G1 repeated.

    gate         PASSED on the first check, 0 waits, uptime 1654, both rated
    conditions   uptime 1,654.51 s (27.6 min) before, 1,732.90 s after,
                 78.39 s wall. AC power, screen on, no VM, app disabled.
                 **SwapFree at the start 1,142,524 kB = 36.3% of SwapTotal**,
                 against G1's 245,000 kB = 7.8%.
    command      pennybench.sh g2_c0_t2_p64_freshboot c0 -- -t 2 -p 64 -n 128
                 -lm none

    pp64                  35.05 +/- 0.61 t/s      (1.74% error bar)
    tg128                 11.21 +/- 0.34 t/s      (3.03%)
    ceil X1   before/min/after    2,802,000 / 1,426,000 / **2,802,000** kHz
              min_at uptime 1,720.55 -- 66 s into the row.
              **Ended at RATED. First row in this repo to do so.**
    ceil A76  before/min/after    2,253,000 / 2,253,000 / 2,253,000 kHz
              min_at 0 s in -- never fell, where on G1 it did
    peak RSS (VmHWM)   3,337,804 kB   3259.6 MiB   3.1832 GiB
    max RssAnon        3,332,116 kB   99.83% of peak
    max RssFile            5,416 kB
    rss_samples              181   (2.31 Hz over 78.39 s)
    MemAvailable       2,104,964 -> 3,850,448 kB   (+1,745,484)
    MemFree            1,161,388 -> 3,491,272 kB   (+2,329,884)
    SwapFree           1,142,524 -> 1,511,416 kB   (+368,892 -- it ROSE)
    Cached             1,165,892 ->   585,428 kB   (-580,464)
    pswpin               110,844 ->   404,057      (+293,213 pages, ~1.12 GiB)
    pswpout              615,920 -> 1,292,648      (+676,728 pages, ~2.58 GiB)
    pgmajfault           123,732 ->   419,735      (+296,003)
    ZRAM               368,536K physical for 1,588,228K in swap
    LMK kills                 39 kills (78 lines), MemAvailable before
                              2,104,964 kB
    rc                         0

    window       09-16 15:48:52.283 -> 15:48:57.571   (5.288 s, at load)
    adj          35 at 905, 2 at 915, 1 at 935, 1 at 945
    deepest      **905. Nothing below it. Every one `cch` -- cached and empty.**
    reason       19 low watermark is breached and swap is low
                 13 low watermark is breached
                  7 min watermark is breached even after kill

### G1 vs G2: MATT'S PREDICTION HOLDS ON KILLS, AND THE SPEED WENT THE OTHER WAY

    row   boot state                 MemAvail   SwapFree   pp64    tg128   wall
    G1    3 rows in, swap 7.8%      3,101,624    245,000   30.53    9.99  85.77
    G2    fresh boot, swap 36.3%    2,104,964  1,142,524   35.05   11.21  78.39
                                     -32.1%      +366%    +14.8%  +12.2%  -8.6%

    row   kills  deepest adj  window   peak RSS kB   pgmajfault
    G1      34       905      2.434 s   3,244,696     +355,006
    G2      39       905      5.288 s   3,337,804     +296,003

**The kills prediction holds: 39 against 34, from 1.0 GB less starting
headroom.** The reasoning behind it holds too — a fresh boot has not yet been
cleared, so there is more to take. What it did NOT predict, and neither did I,
is that **the deepest casualty is 905 on both runs**. Two runs an hour apart,
starting a gigabyte apart in available memory, and the killer stopped at the
same tier. On an idle phone the cached band is deep enough to absorb a 3.18 GiB
model twice over, and the number of victims moves while the DEPTH does not.

**G2 is 14.8% faster on `pp64` and 12.2% faster on `tg128`, with tighter error
bars on both** (1.74% and 3.03% against 1.80% and 6.01%). **So the caveat
attached to G1 was the right call and it was the understatement, not the
overstatement: G1 ran with `SwapFree` at 7.8% — below the contamination rule's
own 10% floor — and it was the depressed figure.** Same shape as C7 against
B2-R1 earlier today, at a third of the magnitude.

**G2 is the Gemma figure to quote. G1 stays in the record as what it is.**

**And it changes the closing entry's most interesting comparison in the
direction that makes it stronger:**

    row   model        file MiB   pp64 t/s   tg128 t/s
    Q2    Qwen3.5-2B     1221.5     48.14      10.94
    G2    Gemma 4 E2B    2963.0     35.05      **11.21**
                         x2.43     -27.2%      **+2.5%**

**A file 2.43 times larger generates tokens 2.5% FASTER**, not 8.7% slower as
G1 suggested. Prompt processing still falls by a quarter. The candidate
explanation is unchanged and still unverified — "E2B" is an effective-2B
configuration and llama-bench's 4.65 B is the total parameter count, so the
bytes touched per token may be far below the file size. **Nothing here measured
that. It is a candidate, not a finding**, and it is now the most interesting
unanswered question this benchmark work produced.

Peak RSS rose 2.9% between the two runs (3,337,804 against 3,244,696 kB). Both
are ~1.07x the model file, so the multiplier finding is unaffected.

### What this entry does NOT say

**Two runs, and they disagree by 12-15%.** G1 and G2 are not a reproduction of
a figure; they are two runs under different memory conditions that differ in
the direction those conditions predict. Neither is repeated under its own
conditions. There is still no n=2 on Gemma at matched starting memory.

**39 kills on an IDLE phone with 39 disposable processes to take.** What Gemma
costs on a phone somebody is using is untested, and rung 3g-ii says the killer
goes deeper there — to adj 201 for a 2GB VM. That remains the number that would
matter to a product and it has not been measured for any model.

**The corrections above change three sentences and no measurement.** No figure
in the closing entry moves except Gemma's, which is superseded by G2 here.

**Still not done, and these are the next session's first four items:**
cold-load time, time-to-first-token with a cached prefix, the `-ub` test that
would separate batch size from micro-batch size, and a sustained run. Also
still: Q4_0, `policy0` during a row, anything on battery, and any judgement of
output quality.

## 2026-09-16 — PREDICTIONS, written before any code and before any run: cold load time and cached-prefix TTFT on the 6a

This entry is written first, deliberately, so the numbers below can be judged
rather than rationalised. Nothing has been built, nothing has been pushed to the
phone, and no model is on it. The two questions are the first two of the four
the closing entry named as "still not done".

    Q-A   cold-load time at `-lm none`: wall time from process start to
          ready-to-generate, split into file read, repack, and context
          creation. Cold and warm both measured, and which is which recorded.
    Q-B   time to first token with a cached prefix: a ~400-token system prompt
          and a 20-token user turn, on c0 at 2 threads, with the prefix
          processed fresh, and then with the processed state saved to disk and
          reloaded in a new process.

Models, in order, and no third: Qwen3-1.7B-Q4_K_M
(sha256 `b139949c5bd74937ad8ed8c8cf3d9ffb1e99c866c823204dc42c0d91fa181897`,
1,107,409,472 B) then Qwen3.5-2B-Q4_K_M
(`aaf42c8b7c3cab2bf3d69c355048d4a0ee9973d48f16c731c0520ee914699223`,
1,280,835,840 B). **Not Gemma.**

### THE BLOCKER Q-B WAS ASKED TO CHECK FIRST, ANSWERED: `examples/save-load-state` DOES NOT EXIST at commit 38a5b42d9

Checked on the Mac, in the tree every binary in this repo was built from.
`examples/save-load-state/` is absent, and it is absent from
`examples/CMakeLists.txt`. It is not an offline-build problem like `llama-cli`'s
— the directory is simply not in the tree at this commit.

**The API it used IS present and IS linked into the static library already
built.** `include/llama.h` declares `llama_state_get_size`,
`llama_state_get_data`, `llama_state_set_data`, `llama_state_save_file`,
`llama_state_load_file` and the whole `llama_state_seq_*` family, all
`LLAMA_API`. `src/libllama.a` in `build-android/` carries them. And
`examples/simple` — which DID build offline, and produced the `llama-simple` now
on the phone — links `llama` and nothing else: no `common`, no curl, no
OpenSSL, no server.

**A BUILDLESS ROUTE WAS PROPOSED AND IS REFUTED. `llama-simple` CANNOT MEET A
SINGLE PINNED CONDITION.** An earlier draft of this entry claimed that
`llama-simple` — already on the phone, sha256 `3d6b6afa8bf5d914604626b4b75b4aa7
e0befdd4aae8e8cb4a1ee816ed3d1ac5`, pushed 16 Sept 12:18 — could answer Q-A's
headline and B1 with nothing built, on the strength of the
`llama_perf_context_print()` line it emits. **That was asserted from the fact
that it prints a `load time` at all, without reading what the number covers or
what the program sets.** Matt required both to be read. Both refute it.

    (a) flags accepted     -m <path>, -n <n_predict>, -ngl <n_gpu_layers>, and
                           then everything remaining is the prompt.
                           examples/simple/simple.cpp:29-63. That is all three.
                           There is NO -t, NO -c and NO -lm.
    (b) n_threads          NEVER SET. It takes llama_context_default_params(),
                           i.e. GGML_DEFAULT_N_THREADS = 4
                           (src/llama-context.cpp:3626, ggml/include/ggml.h:232).
                           **The protocol pins 2. Unreachable.**
    (c) n_ctx / n_batch    ctx_params.n_ctx   = n_prompt + n_predict - 1
                           ctx_params.n_batch = n_prompt
                           (simple.cpp:108-111) -- both DERIVED FROM THE PROMPT.
                           **This entry pins n_ctx at 1024. Unreachable.**
    (d) load_mode          NEVER SET, so LLAMA_LOAD_MODE_AUTO
                           (src/llama-model.cpp:2767) = mmap.
                           **Every figure in this repo is `-lm none`.**

**And (e), the one that would have poisoned the answer rather than merely
limiting it: the printed `load time` is not a load time.**
`data.t_load_ms = 1e-3 * t_load_us` (src/llama-context.cpp:3344). `t_load_us` is
first set at `src/llama-context.cpp:95` from the `time_meas` scope that wraps
model loading (`src/llama.cpp:339-340`) — and is then **OVERWRITTEN at
`src/llama-context.cpp:742`**:

    // get a more accurate load time, upon first eval
    if (n_queued_tokens > 0 && !has_evaluated_once) {
        t_load_us = ggml_time_us() - t_start_us;
        has_evaluated_once = true;
    }

with the reason given at `src/llama.cpp:337-338`: *"loading time will be
recalculated after the first eval, so we take page faults deferred by mmap()
into consideration"*. **So the number is process start through the end of the
first decode** — model load, tokenise, context creation and first eval in one
figure — and it splits into none of the three phases Q-A asks for. Taken at
face value it would have been reported as a load time and been wrong by the
whole of the first eval.

**THE ANSWER TO "WHY BUILD ANYTHING" IS THEREFORE: the binary on the phone
cannot meet the conditions this protocol already pins**, and its one relevant
number is a composite that cannot be separated. The smallest change is whatever
adds a thread count, a context size and a load mode, and clocks the phases
apart. That the same file can also carry `use_extra_bufts` and the state
save/load is a consequence of it existing, not the justification for it.

**THE REPACK IS NOW OBSERVED ON THE PHONE, NOT READ FROM SOURCE.** The claim
above that Q4_K and Q6_K take the repack path was made from
`ggml/src/ggml-cpu/repack.cpp` alone; Matt required it measured. Read back off
the handset from `/data/local/tmp/out/nommap.err`, written 16 Sept 12:57 by a
real `llama-bench -v` run of this binary:

    load_tensors:          CPU model buffer size =   243.90 MiB
    load_tensors:   CPU_REPACK model buffer size =  1049.96 MiB
    197 `repack tensor` lines: 168 with q4_K_8x4, 29 with q6_K_8x4

Identical counts in all three verbose logs on the phone (`nommap.err`,
`r3c_c0_t2_p512v.err`, `rssv.err`). **1,049.96 MiB of the 1,293.86 MiB total
model buffer — 81% of it — is repacked**, against a 1,056.13 MiB file. That is
larger than the source reading suggested and it raises what A4 is a share of.

**AND THE TWO BUFFER LINES DO NOT SUM TO THE FILE. OBSERVED, UNEXPLAINED, AND
LEFT UNEXPLAINED HERE.** Raised by Matt on reading the figures above.

    CPU        model buffer size      243.90 MiB
    CPU_REPACK model buffer size    1,049.96 MiB
    sum                             1,293.86 MiB
    Qwen3-1.7B-Q4_K_M on disk       1,056.13 MiB  (1,107,409,472 B)
    difference                     +  237.73 MiB  resident OVER the file

No explanation is offered and none should be read into the ordering of those
lines. **AMENDED 16 Sept AFTER ROW 4, AND STILL NOT AN EXPLANATION.** The
`--extra-bufts 0` control loads the same file into a SINGLE 1,050.43 MiB buffer
with no `CPU_REPACK` line and zero `repack tensor` lines, and its peak RSS is
251,596 kB (245.70 MiB) below row 2's. The buffer sum is 243.43 MiB higher with
the repack on, and the peak RSS is 245.70 MiB higher — **the two agree to
2.27 MiB**, and both are close to the 237.73 MiB excess recorded above. All of
that is **CONSISTENT WITH** the repacked representation being the source of the
excess. **It is not established**, for two reasons that must be kept in view:
row 4 is a CONTROL and the plan excludes its figures from being quoted as
results, so this is a flagged consistency and not a measurement; and the
arithmetic points at the repacked tensors being LARGER than their file form
(806.53 MiB unrepacked against 1,049.96 MiB repacked) rather than at a
duplicate of them being retained, which are different mechanisms with the same
total. Which of the two it is has NOT been determined here. It is recorded because it bears on two things already written down:
**A2**, which predicted `llama_model_load_from_file` at 2.5-5.0 s on an
assumption of ~1,056 MiB moved off disk, and **the peak-RSS-over-file
multiplier table in the closing entry at notes.md 7662**, which reports 1.07 to
1.46 across three models and concludes that llama.cpp's own buffer lines are the
method and file size is not. These two lines are those buffer lines, and they
already exceed the file before a context exists. Whether the 237.73 MiB is
accounted for inside that multiplier or sits alongside it is not established
here.

**The smallest form of the harness is one new C++ source** 
in THIS repo, `pennyload.cpp`, compiled with the exact NDK command line ninja
recorded for `llama-simple` and linked against the static libraries already
sitting in `~/Documents/llama.cpp/build-android/`:

    clang++ --target=aarch64-none-linux-android28 --sysroot=<ndk sysroot>
            -DGGML_USE_CPU -O3 -DNDEBUG -fPIE
            -I<llama.cpp>/include -I<llama.cpp>/ggml/include
      ... link: src/libllama.a ggml/src/libggml.a ggml/src/libggml-cpu.a
                ggml/src/libggml-base.a -pthread -lm -ldl -latomic
                -static-libstdc++

No cmake reconfigure, no ninja run, no download, and **not one byte of the
llama.cpp tree is modified** — it stays at 38a5b42d9 exactly as every existing
figure was measured against. The ggml kernels are the identical object files
`llama-bench` and `llama-simple` were linked from, so the same 898 `sdot/udot`,
the same 0 `smmla`, the same `armv8.2-a+dotprod+fp16`.

### THE SPLIT Q-A ASKS FOR IS OBTAINABLE WITHOUT PATCHING llama.cpp, AND HERE IS HOW

File read and repack are interleaved inside `llama_model_load_from_file`: the
loader reads each tensor and hands it to the buffer's `set_tensor`, and on an
extra (repack) buffer that call does the conversion. They cannot be timed apart
by putting a clock either side of one call.

`llama_model_params` carries **`bool use_extra_bufts`** — a runtime switch that
turns weight repacking off. So the split is two runs of one binary:

    use_extra_bufts = true    read + repack      (this is the normal path)
    use_extra_bufts = false   read only
    difference                = the repack cost

**And repacking IS happening on these runs, which had not been established.**
`ggml/src/ggml-cpu/repack.cpp:4605` selects `q4_K_8x4_q8_K` for `GGML_TYPE_Q4_K`
when `ggml_cpu_has_neon() && ggml_cpu_has_dotprod()` and `ne[1] % 8 == 0`, and
the same for `GGML_TYPE_Q6_K` at line ~4660. This build has NEON and dotprod.
**This does not contradict "the Q4_0 repack path has never executed on this
phone"** — that remains true and is about `GGML_TYPE_Q4_0` specifically, a
different branch of the same function.

The run with `use_extra_bufts=false` is a LOAD-TIME control only. It selects
different matmul kernels, so no speed figure from it is comparable with
anything, and none will be quoted.

### THREE DESIGN DECISIONS, RECORDED BEFORE THE RUN BECAUSE A LOG CANNOT TELL YOU AFTERWARDS

**1. COLD MEANS AFTER A REBOOT, AND PUSHING THE MODEL IS WHAT FORCES THAT.**
There is no root on this device, so `/proc/sys/vm/drop_caches` is unavailable.
`adb push` writes the file THROUGH the page cache, so the first read after a
push is warm, not cold — a "cold" row run straight after a push would measure
nothing and would look exactly like a fast disk. The only way to an empty page
cache here is a power cycle. So: push, reboot, unlock by hand, take the
protocol's fresh-boot `MemAvailable` readings, then the first load is cold and
the second immediately after is warm. One reboot per model, two in total.

**2. `n_ctx` IS PINNED AT 1024 FOR EVERY ROW IN BOTH QUESTIONS.** Context size
sets the KV allocation and therefore part of both the context-creation time and
the peak RSS, so leaving it at the model default (40,960 for Qwen3-1.7B,
262,144 for Qwen3.5-2B) would make the two models incomparable and would change
the memory figures against every row already measured. 1024 holds the ~400-token
prefix, the 20-token turn and generation with room to spare.

**3. TTFT IS REPORTED TWICE AND THE TWO MUST NOT BE COLLAPSED.** "Resident"
means the model is already loaded and only the prefix is in question.
"Cold process" means a new process that must load the model as well. The second
is the wake case and it is the one that answers the question this work was set
to answer; the first is what isolates the prefix cache from the model load.

### THE INSTRUMENT

`pennybench.sh` is hard-coded to run `/data/local/tmp/llama-bench`. It gains one
change and one only: a `PENNYBIN` environment variable defaulting to that same
path, so the wrapper can run `pennyload` instead while every other column —
clock ceilings before/min/after with the uptime each minimum was seen at, VmHWM,
RssAnon/RssFile maxima, `MemAvailable`/`MemFree`/`SwapFree`/`Cached` either
side, `pswpin`/`pswpout`/`pgmajfault` either side, and the tag-anchored LMK kill
grep — is untouched. Its sha256 will change and the new value will be recorded
with the rows, superseding `484d75d4…`. Every row is gated on BOTH
`policy6/scaling_max_freq` = 2,802,000 and `policy4/scaling_max_freq` =
2,253,000 in the same shell invocation, as from 16 Sept.

### THE PREDICTIONS, WITH THE ARITHMETIC THEY COME FROM

**KV cache per token is DERIVED from each file's own GGUF metadata, read on the
Mac before any run, not guessed:**

    Qwen3-1.7B   arch qwen3    28 blocks, head_count_kv 8, K 128, V 256/2...
                 28 x 8 x (128+128) x 2 B (f16)  =  114,688 B/token = 112 KiB
    Qwen3.5-2B   arch qwen35   24 blocks, full_attention_interval 4, so SIX
                 attention layers and EIGHTEEN SSM layers. head_count_kv 2,
                 K 256, V 256.
                 6 x 2 x (256+256) x 2 B  =  12,288 B/token = 12 KiB
                 PLUS a FIXED recurrent state (ssm.inner_size 2048,
                 ssm.state_size 128, ssm.group_count 16, conv_kernel 4) that
                 does NOT grow with the prefix.

**Qwen3.5-2B is a HYBRID and that was not known before this entry.** Its
`qwen35.ssm.*` keys make eighteen of its twenty-four layers recurrent. Hybrid
state save and load ARE implemented at this commit
(`src/llama-memory-hybrid.cpp:190/197` delegating to the attention and recurrent
memories in turn), so Q-B is askable of it — but it is the one part of this plan
that could still fail on a code path rather than on a number.

    #   prediction                                          point   band
    --  --------------------------------------------------  ------  -------------
    A1  Qwen3-1.7B cold load, start -> ready to generate      4.2 s  3.0 - 6.0 s
    A2  ... of which read+repack (llama_model_load_from_file) 3.5 s  2.5 - 5.0 s
    A3  ... of which context creation                         0.4 s  0.2 - 0.8 s
    A4  repack's share of A2                                   52%   40 - 65%
    A5  Qwen3-1.7B WARM load, immediately after                3.2 s  2.2 - 4.5 s
    A6  Qwen3.5-2B cold load                                   4.9 s  3.5 - 7.0 s
    B1  TTFT fresh, model RESIDENT, 420 tokens processed       7.7 s  6.0 - 9.0 s
    B2  TTFT cached, model RESIDENT, state loaded + 20 tok     0.5 s  0.35 - 1.5 s
    B3  TTFT cached, COLD PROCESS (load + state + 20 tok)      4.9 s  3.5 - 8.5 s
    B4  cost of saving the state to disk                      0.25 s  0.05 - 1.0 s
    B5  state file size, Qwen3-1.7B, ~420 tokens            45.9 MiB  40 - 60 MiB
    B6  state file size, Qwen3.5-2B, ~420 tokens            ~25 MiB   10 - 60 MiB

B1 comes from the gated c0/2-thread `pp512` figure of 55.36 t/s (row C1,
notes.md 7592): 420 / 55.36 = 7.59 s, plus one decode at 14.11 t/s = 0.07 s.
B5 is 420 x 112 KiB. A2's read half assumes 700-1000 MB/s off UFS for
1,056.1 MiB, which is the same order as the 598 and 835 MB/s cold reads rungs
3e-iv and 3e-iii measured through dm-crypt inside a VM.

**B6 carries a prediction that is more interesting than its number: Qwen3.5-2B's
state file will be SMALLER than Qwen3-1.7B's despite the model being 16% larger,
because six attention layers at 2 KV heads cost 12 KiB a token against
Qwen3-1.7B's 112 KiB.** If that holds, prefix caching is nearly an order of
magnitude cheaper on the hybrid, and the fixed recurrent state means its file
barely grows with a longer prompt. The band is wide because the recurrent
state's size is the one figure here not derived from first principles.

### FAILURE CONDITIONS, ONE PER PREDICTION, WRITTEN NOW

- **A1/A6 fail** outside their bands.
- **A4/A5 are the pair that matters and they fail together.** The claim is that
  **the repack, not the file read, is the larger half of load time** — so a warm
  load saves only the read and the model still takes seconds to become ready.
  **It fails if warm load is >= 90% of cold** (the read was never the cost, so
  nothing was saved and the split is wrong at the other end) **or <= 40% of
  cold** (the read dominated and repack is cheap, which kills the claim
  outright). Either outcome is reported as a failed prediction, not reworded.
- **B1 fails** outside 6.0-9.0 s. Below 6.0 s would mean llama-bench's `pp`
  figure does not transfer to a single real prompt and is measuring something
  narrower; that is a finding about the instrument, not about the chip.
- **B2 fails above 1.5 s.** A prefix cache that costs more than a second and a
  half has not bought a wake anything.
- **B5 fails** outside 40-60 MiB; **B6 fails** if the Qwen3.5-2B file is LARGER
  than the Qwen3-1.7B one, which is the directional claim and the one worth
  being wrong about.
- **B4 fails above 1.0 s.**
- **The whole of Q-B fails on Qwen3.5-2B** if `llama_state_save_file` or
  `llama_state_load_file` returns false on a hybrid model. That is reported as
  the result it is — a hybrid's prefix cannot be cached at this commit — and
  nothing is patched to get around it.

### THE PREDICTED PLAIN ANSWER, SO IT CAN BE JUDGED TOO

**Predicted: resident wins, and not narrowly.** A model held resident with a
cached prefix puts the first word in front of the user in **about half a
second**; a cold process that reloads the model puts it there in **four to seven
seconds**, and a saved prefix does not help with that because the model load is
the whole of the cost. The price of resident is 1.35-1.42 GiB of anonymous
memory held permanently for the 1.7B, which on a fresh boot with ~2.1 GB
`MemAvailable` is most of the headroom the phone has.

### WHAT THIS ENTRY DOES NOT SAY

Nothing has been measured. Every number above is a prediction and several rest
on figures taken under other conditions — B1 on a gated c0 row from a different
boot, A2's read half on cold-read rates measured inside a VM through dm-crypt
rather than natively. The recurrent-state size in B6 is not derived. No
judgement of output quality is planned here, no sustained run, no thermal run,
no `-ub` test, no Q4_0, no VM, and no third model.

### THE INSTRUMENT AS BUILT, 16 Sept — recorded before any row runs

**`pennybench.sh` revision 3.** Three hunks, nine lines: `BIN="${PENNYBIN:-/data/
local/tmp/llama-bench}"`, the two launch lines use `"$BIN"`, and the report gains
`PENNYBENCH bin=$BIN` so every row records which binary produced it. **The
default is unchanged, so every row measured on or before 16 Sept reproduces
with no variable set.**

    sha256  0f5cb2b5b6a68243f81d699a939652c6b4150ec3e296b3050a34caf4a7ff208c
    supersedes 484d75d4..., which superseded 67eefed1..., which superseded
    e5a81104...

**`pennyload.cpp`, 407 lines, in THIS repo.** Links `llama.h` and nothing else —
no `common` — exactly as `examples/simple` does, against the static libraries
already built at 16 Sept 12:08:

    66,071,426 B  build-android/src/libllama.a
       774,674 B  build-android/ggml/src/libggml.a
     7,954,690 B  build-android/ggml/src/libggml-cpu.a
     9,446,196 B  build-android/ggml/src/libggml-base.a

    pennyload.cpp        baf75cd8631687ba9138326ee0d7ced1f750bf51ae2a74425ba30ad7240a6b15
    build-pennyload.sh   0659d5d5c7d6b1d70cbda3f4ed4271f821eee3ee85a4ea9083a1e698f5f16f04
    build/pennyload            44,634,904 B  8228c9fb86490bddadf35d190c839510613270795dea7d453e9938a1621aa5d0
    build/pennyload-stripped    3,817,808 B  be2cab2cba9ec0768d23dd61f9fbfde2f9de86cc95cbe374416e655cde8c0158

    ELF 64-bit LSB pie executable, ARM aarch64, SYSV, interpreter
    /system/bin/linker64, NEEDED libm.so libdl.so libc.so, stripped.

**Instruction census on the stripped binary, the same check `llama-bench` had:**

    smmla | ummla | usmmla        0
    SVE / SME (ptrue, whilelo,
    smstart, smstop, z<n>.)       0
    sdot | udot                 898

**898 is llama-bench's own count, to the instruction.** That is the check that
this binary linked the identical `ggml-cpu` objects rather than a differently
configured rebuild, and it is stronger than comparing flags.

**ONE DEVIATION FROM THE RECORDED NINJA LINE, NAMED RATHER THAN IMPLIED AWAY.**
`build-pennyload.sh` drops llama.cpp's own extra warning set
(`-Wmissing-declarations -Wmissing-noreturn -Wcast-qual -Wno-unused-function
-Wunreachable-code-break -Wunreachable-code-return -Wmissing-prototypes
-Wextra-semi`) and `-Xclang -fno-pch-timestamp`, and adds `-std=c++17` in place
of cmake's `target_compile_features(cxx_std_17)`. **Every one of those is a
warning or a precompiled-header flag and none affects code generation**; the
codegen-relevant flags (`--target`, `--sysroot`, `-O3 -DNDEBUG -fPIE
-fdata-sections -ffunction-sections -funwind-tables -fstack-protector-strong
-D_FORTIFY_SOURCE=2`) are copied verbatim. The 898 count is the evidence, not
the argument. It compiled with zero warnings under `-Wall -Wextra -Wpedantic`
and `-Wl,--fatal-warnings`.

### THE FOUR DEFINITIONS, FIXED HERE BEFORE ANY ROW RUNS

Every run makes **two** `llama_decode` calls, never one — the system prompt,
then the user turn — so the prefix and the turn are separable in every row.

    T0   first statement of main()
    T1   after ggml_backend_load_all()
    T2   FIRST progress_callback, progress == 0.0   (llama-model-loader.cpp:1626)
    T3   LAST  progress_callback, progress == 1.0   (llama-model-loader.cpp:1785)
    T4   after llama_model_load_from_file()
    T5   after llama_init_from_model()              <- READY TO GENERATE
    T6   after llama_tokenize()
    T7   after llama_state_load_file()              (--load-state only)
    T9a  after the SYSTEM-PROMPT llama_decode()  -- or == T7 on a cached run
    T8   after llama_state_save_file()              (--save-state only),
         sitting BETWEEN T9a and T9b
    T9b  after the USER-TURN llama_decode()
    T10  after llama_sampler_sample()               <- FIRST TOKEN EXISTS

    B4    state save cost           =  T8  - T9a
    TTFT resident, FRESH            =  T10 - T6
    TTFT resident, CACHED           =  T10 - T5
    B3    cold process, cached      =  T10 - T0

**The state file's byte size is printed beside its save time and beside its load
time in every row that has one**, as `state_bytes=`, read with `stat()` rather
than inferred.

**On a `--load-state` run the system prompt is deliberately NOT tokenised.** T6
falls inside the TTFT-resident-cached window (T10 - T5) and a real cached path
would not redo that work; the restored token count comes from the state file, as
`state_tokens_restored=`.

### FOUR MORE THINGS FIXED HERE, ALL AT MATT'S INSTRUCTION

- **Sampler is greedy** — `llama_sampler_init_greedy()`, printed as
  `sampler=greedy` in every row. Generation is therefore deterministic, which is
  what makes the control below mean anything.
- **The `-n 64 --print` rows compare ALL 64 tokens between the fresh run and the
  cached run, not the first.** The binary prints `token_ids=` (the full list) and
  `token_fnv1a64=` (FNV-1a over the 32-bit ids) so the comparison is mechanical.
  **Match or mismatch is recorded as a result either way** — a mismatch says the
  reloaded state is not the state that was saved, which would be the more
  important finding of the two.
- **NO CHAT TEMPLATE IS APPLIED.** Both prompt files are tokenised verbatim and
  the binary prints `chat_template=NONE` in every row. Tokenize flags are fixed
  and printed: the system prompt at `add_special=1 parse_special=1`, the user
  turn at `add_special=0 parse_special=1`. (Qwen3-1.7B's GGUF carries
  `tokenizer.ggml.add_bos_token = False`, so `add_special` adds nothing for that
  model; the flag is recorded regardless of what it does.)
- **Prompt text goes to stdout only after a line reading `--- text ---`**, so the
  `PENNYLOAD` lines parse cleanly and text is buffered rather than written
  inside any timed window.

### WHAT THE INSTRUMENT DOES NOT SAY, BEFORE IT HAS RUN ONCE

**`pennyload` has never been executed.** It is an aarch64 Android binary, so it
cannot run on the Mac, and nothing has been pushed to the phone. It compiles and
links; that is all that is known. **The first thing it does on the phone is a
smoke test on an unlocked phone**, the habit CLAUDE.md records from the
`mExecutor` bug, before any measured row and before any reboot is spent.

**No chat template means these are not conversational turns.** The model sees a
block of instruction text followed by a block of question text with no role
markers, no `<|im_start|>`, and no generation prompt. Whatever it generates is
text produced under those conditions and nothing else. **Any text printed is
recorded as text produced, never as a judgement of output quality** — quality is
out of scope for this session and stays in the "does NOT say" list of the
result entry.

**The `--extra-bufts 0` control run selects different matmul kernels**, so no
speed figure from it is comparable with anything and none will be quoted. It
exists to subtract `t_tensor_band_ms` and for no other purpose.

### THE TWO PROMPT FILES, written 16 Sept before any run

    prompts/penny_system.txt   1,911 bytes
      sha256 9496977025bffba32886e447cf10ab2281c439dc7c4811124827762fdb730ff4
    prompts/penny_user.txt        74 bytes
      sha256 f6d8be90cd53c97f2593a4d38d8fb6f766cdc8f1365787de835a72c76fab1210

The system prompt is Penny-shaped by design and by nothing more: five paragraphs
of routing instructions naming thirteen tools (`calendar_read`,
`calendar_write`, `contacts_lookup`, `message_send`, `alarm_set`, `timer_set`,
`note_append`, `note_search`, `weather_local`, `music_control`, `call_place`,
`device_setting`, `location_current`), a confirmation rule, an ambiguity rule
and a don't-invent rule. **The content is not the variable and nothing in this
work depends on it; the LENGTH is the variable.** It is not product design and
must not be read into the Penny project — CLAUDE.md keeps product thinking out
of this repo, and this is a fixture.

The user turn is one general-knowledge question, chosen so that a correct
answer is checkable by eye if a `--print` row is ever read, and so that it needs
no tool under the system prompt's own routing rule.

**NO TOKEN COUNT IS QUOTED FOR EITHER FILE AND NONE MAY BE UNTIL A RUN HAS
PRINTED ONE.** Byte counts are measured; token counts are whatever `pennyload`
reports as `sys_tokens=` and `user_tokens=` on the phone, against each model's
own tokeniser. The target was ~400 and ~20; whether it was hit is unknown until
then, and the two models tokenise differently in any case — Qwen3-1.7B and
Qwen3.5-2B have different vocabularies (`tokenizer.ggml.pre` `qwen2` against
`qwen35`, eos 151645 against 248046), so ONE file will produce TWO token counts
and both get recorded.

Both files are read verbatim, trailing newline included, with no chat template.


### THE SMOKE TEST, 16 Sept 16:50 — pennyload's first execution, and NOT A ROW

The habit CLAUDE.md records from the `mExecutor` bug: run the thing by hand on
an unlocked phone before spending a reboot on it. `pennyload` had never been
executed anywhere — it is an aarch64 Android binary, so it cannot run on the
Mac — and this is the run that changes that.

    PENNYBIN=/data/local/tmp/pennyload  pennybench.sh smoke_pennyload c0 -- \
      -m Qwen3-1.7B-Q4_K_M.gguf -t 2 -lm none -n 1 \
      --sys-file penny_system.txt --user-file penny_user.txt --tag smoke

Conditions, and every one of them disqualifies the numbers below from being a
row: phone UNLOCKED and in the foreground over adb, NO thermal gate (the
protocol's `policy6`/`policy4` rated-clock poll was not applied), page cache
WARM because the model was pushed on this same boot, and the boot was 89
minutes old — uptime 5334.39 s at the PRE reading, 5346.79 s at the POST.
AC power, screen on, app disabled, `Running VMs: []`.

`rc=0`. It loads, links, reads both prompt files, decodes twice, samples, and
prints every mark the four definitions asked for.

**THE TWO TOKEN COUNTS, WHICH ARE THE FIRST THING HERE THAT IS A FACT RATHER
THAN A SMOKE FIGURE.** The prompt-files entry above refused to quote a token
count until a run printed one. It has:

    sys_tokens  = 407    penny_system.txt, 1,911 B, add_special=1 parse_special=1
    user_tokens =  15    penny_user.txt,      74 B, add_special=0 parse_special=1

on Qwen3-1.7B's tokeniser. **The system prompt hit its ~400 target; the user
turn missed its ~20 and is 15.** Both are model-specific and Qwen3.5-2B will
produce two different numbers from the same two files.

Read from `/data/local/tmp/out/smoke_pennyload.bench` on the phone, not from
the terminal:

    progress_calls     312
    t_backend_ms          2.57
    t_model_open_ms     349.88   header + hparams + vocab + alloc
    t_tensor_band_ms   2124.62   tensor data read + repack
    t_model_tail_ms       1.97
    t_model_total_ms   2476.47   llama_model_load_from_file
    t_ctx_create_ms      35.83   llama_init_from_model
    t_ready_ms         2514.86   READY TO GENERATE
    t_tokenize_ms         3.36
    t_sys_decode_ms    6027.60   407 tokens, one llama_decode
    t_user_decode_ms    326.01   15 tokens
    t_sample_ms           0.73
    ttft_fresh_ms      6354.34   T10-T6
    ttft_cold_proc_ms  8872.56   T10-T0
    first_token_id     32313
    token_fnv1a64      0xe110526cecfd31d0

and from `smoke_pennyload.err`, `CPU model buffer 243.90 MiB` with
`CPU_REPACK 1049.96 MiB` — the same two lines the predictions entry recorded
from `llama-bench`, so `pennyload` is taking the same repack path.

**THE STATE PATH IS STILL UNEXECUTED.** `--save-state` and `--load-state` were
not passed, so `llama_state_save_file` and `llama_state_load_file` have never
been called on this handset. Every line of the Q-B machinery is unproven, and
the whole of Q-B still rests on two functions that have not run once.

**FOUR FIGURES FROM THIS RUN EXIST ONLY IN THE OPERATOR'S TERMINAL AND ARE
MARKED AS SUCH.** `pennybench.sh` revision 3 wrote its `PENNYBENCH` report to
stdout and to no file, so peak RSS **1,538,944 kB with 99.6% anonymous**,
`MemAvailable` before the run **3,669,276 kB**, the PRE/POST uptimes quoted
above, and the X1 ceiling falling to **2,188,000 kHz about 7 s in** are
reported-from-terminal, not re-readable from the phone. They are recorded here
because they are the only copy. The kill count is the exception and IS on the
phone: `smoke_pennyload.kills` is **0 bytes**, i.e. zero kill lines, with that
3,669,276 kB of `MemAvailable` in front of it.

**`pennybench.sh` REVISION 4 EXISTS BECAUSE OF THAT**, committed alongside this
paragraph. Six added lines, all inside the REPORT section: a brace group and
`| tee "$OUT.report"`. Nothing above that line changed, so every row measured
on or before 16 Sept still reproduces. sha256
`c5b9f03a7c5add80196e8ed584c1091cc62390e24fafe3cda91004c8512ffc99`, superseding
rev 3's `0f5cb2b5…`; pushed and hashed on the phone at 17:01, 8,402 B.
**From here, a row's numbers are read from `out/<tag>.report` and
`out/<tag>.bench`. Scrollback is not a record.**

**WHAT THE SMOKE TEST DOES NOT SAY**

**NO FIGURE ABOVE IS A ROW AND NONE MAY BE QUOTED AS ONE.** Warm, ungated,
unlocked, foreground, on an 89-minute boot, once. Every prediction A1-A6 and
B1-B6 is judged against gated rows that have not been run.

**The system-prompt decode works out at ~67.5 t/s (407 tokens in 6027.60 ms)
and that is at most a hint about B1, not a measurement of it.** B1 predicts
7.7 s for 420 tokens from a GATED c0 two-thread `pp512` of 55.36 t/s; this ran
with no thermal gate on a chip whose X1 ceiling had already fallen to 78% of
rated seven seconds in, at a different prompt length, through a different
binary, in one sample with no error bar. A faster number under worse thermal
conditions is interesting and is not evidence until a gated row says so.

**Peak RSS 1,538,944 kB is at `n_ctx` 1024 and is NOT comparable with the
`-p 512` rows in the closing entry.** Those rows carry their own context and KV
allocation; this one holds a 1024-token KV cache plus a 304.75 MiB compute
buffer that `sched_reserve` sized for `n_ubatch` 512. The closing entry's
1.35-1.42 GiB peak-RSS band for this model was measured under llama-bench's
conditions and this figure does not extend, contradict or replace it.

Nothing was gated, nothing was cold, nothing was repeated, no reboot was spent,
and no text was printed — `-n 1` generated exactly one token and `--print` was
not passed, so the run says nothing whatever about output quality.

### penny_user.txt LENGTHENED TO LAND ON 20 TOKENS, 16 Sept 17:06

The smoke test measured the user turn at **15** tokens against a ~20 target, so
the file was lengthened. One clause added — the question is still one general-
knowledge question, still answerable by eye, and still needs no tool under the
system prompt's own routing rule.

    was   "What is the capital of Australia, and roughly how many people live
           there?"                                     74 B  f6d8be90…  15 tok
    now   "What is the capital of Australia, roughly how many people live
           there, and when was it founded?"            95 B  b61e0a99…  20 tok

    sha256 b61e0a992e5e8b4cd479c8596c63381b95372ee8b0c3982b895259f9b7a4121b
    (supersedes f6d8be90cd53c97f2593a4d38d8fb6f766cdc8f1365787de835a72c76fab1210)

**The 20 is MEASURED, not aimed at.** Read from a `pennyload -n 1` run on the
phone at 17:06 — `user_tokens=20`, `user_bytes=95`, `add_special=0
parse_special=1`, Qwen3-1.7B's tokeniser, `rc=0`. That run was a tokeniser
check and nothing else; no timing from it is quoted anywhere and it did not go
through `pennybench.sh`. `sys_tokens` was 407 on the same run, unchanged.

**Every figure in the smoke-test entry above was taken at 15 user tokens** and
the file has now changed under it. The smoke entry keeps its numbers as what
they were; no row has been run at either length.

**What this does NOT say.** Qwen3.5-2B has a different vocabulary and will
produce a different count from the same 95 bytes; that count is unknown until
that model is on the phone. The count says nothing about whether the question
is a good one — content is not the variable here, length is.

### THE STATE SMOKE PAIR, 16 Sept 17:06 — the save/load path RUNS, and the 64 tokens are IDENTICAL. STILL NOT ROWS.

`llama_state_save_file` and `llama_state_load_file` had never been called on
this handset. They have now, both returning true, on the first attempt, with no
patch to llama.cpp and none to `pennyload`.

Conditions, and they disqualify every timing below from being a row exactly as
the smoke test's did: phone UNLOCKED and foreground over adb, **NO thermal
gate**, page cache **WARM** (the model was pushed on this boot), boot 106
minutes old, AC power, screen on, app disabled, `Running VMs: []`. Both rows
`c0 -t 2 -lm none -n 64 --print`, `n_ctx` 1024, greedy, no chat template,
`sys_tokens=407` / `user_tokens=20`.

**Both were run through `pennybench.sh` revision 4, and this is the first
`.report` file this repo has ever had.** Written by the phone at 17:06 —
`smoke_state_save.report` 4,545 B and `smoke_state_load.report` 4,338 B, mtime
`2026-09-16 17:06`, minutes after they were created. That is the check that
they are phone-written: a file that arrived by `adb push` carries the Mac's
mtime instead (the model on this phone reads 15 Sept 20:25 for that reason).
Every figure below is read back from `out/<tag>.report` and `out/<tag>.bench`
rather than from a terminal.

    tag                     smoke_state_save        smoke_state_load
    run_type                fresh+save              cached
    rc                      0                       0
    state_bytes             46,685,237              46,685,237
    state_tokens            407 saved               407 restored
    t_state_save_ms         19.87  (== B4)          --
    t_state_load_ms         --                      13.88  (T7-T6)
    t_tokenize_ms           3.36                    0.34   (sys NOT tokenised)
    t_ready_ms              2528.49                 2526.53
    t_sys_decode_ms         6030.87                 -- (skipped entirely)
    t_user_decode_ms        377.98                  362.26
    TTFT resident           6429.61 ms              377.21 ms
    TTFT cold process       8961.46 ms              2903.74 ms
    gen_tokens / gen_tps    64 / 14.83              64 / 14.93
    peak_rss_kB             1,541,308               1,500,276
    max_rssanon_kB          1,535,536               1,494,556
    lmk_kill_lines          0                       0
    MemAvailable before     3,658,716 kB            3,650,728 kB
    ceil_x1 min             2,188,000 kHz, 8 s in   2,401,000 kHz, 3 s in
    ceil_a76 min            2,253,000 (rated)       2,253,000 (rated)
    uptime before           6306.83 s               6331.34 s

**THE IDENTITY CONTROL PASSES, AND IT IS THE RESULT HERE RATHER THAN ANY
TIMING.** Both runs generated 64 tokens under a greedy sampler and the two
sequences are the same sequence:

    token_fnv1a64  0xcba17a2fcbba49f4   on BOTH runs
    first_token_id 32313                on both
    all 64 ids     identical, position for position

So the state that came back off disk is the state that went onto it. Had the
reloaded prefix differed from the decoded one in any way that reached the
logits, greedy decoding would have diverged and the hash would have said so.
The four definitions fixed before the run make this comparison mechanical
rather than a matter of reading two logs side by side.

**The text produced, identical on both runs**, recorded as text produced and
not as any judgement of output quality:

    Okay, the user is asking about the capital of Australia, the population,
    and the founding date. I need to answer these directly. The capital is
    Canberra. The population is around 4 million. The founding date is 1901.
    I should provide these answers without routing.
    The capital of Australia is Canberra

Canberra is right; **the other two facts are wrong** (Canberra's population is
of the order of 450,000, and 1901 is federation rather than anything about
Canberra). It is one greedy 64-token sample with **no chat template** — the
model sees instruction text then question text with no role markers and no
generation prompt — so this is not a basis for a quality claim in either
direction, and quality remains out of scope for this session.

**WHAT THE PAIR SAYS THAT IS NOT A TIMING.** The save path works on this model;
the load path works; `state_tokens_restored` comes back as 407, the count that
went in; the file is 46,685,237 B, i.e. 44.52 MiB for 407 tokens, which is
114,706 B/token against the 114,688 B/token the predictions entry DERIVED from
this model's own GGUF metadata — 18 bytes a token of header and bookkeeping.
**A byte count is not a timing**: unlike a t/s figure it is not sensitive to
thermal state or to gating, so the gated row is expected to report the same
46,685,237 B. That expectation is a claim and is checked when the row runs.

**WHAT IT DOES NOT SAY**

**NO TIMING ABOVE IS A ROW AND NONE MAY BE QUOTED AGAINST A PREDICTION.**
Ungated, warm, unlocked, foreground, once each. B2, B3 and B4 are judged
against gated rows on a cold boot that have not been run. Two of these figures
happen to fall inside their predicted bands and one falls below its band; all
three are noted here as hints and none is scored.

The load run's `t_ready_ms` of 2526.53 is a **WARM** model load. Cold is after
a reboot and the reboot has not happened, so `ttft_cold_proc_ms` 2903.74 is a
warm-process figure and is not B3.

**The 17x gap between 6429.61 ms and 377.21 ms is measured under one thermal
state, not two.** The fresh side also carries the 19.87 ms of the save itself
inside its own TTFT window, because the save sits between T9a and T9b by
design.

Peak RSS is 41,032 kB LOWER on the cached run (1,500,276 against 1,541,308 kB).
No explanation is offered and none should be read into it; it is one sample
each. Both are at `n_ctx` 1024 and neither is comparable with the `-p 512` rows
in the closing entry.

The state file was written to `/data/local/tmp/smoke_state.bin` and **deleted
at 17:07**, verified gone by `ls`. Nothing depends on it; the gated rows write
their own.

Nothing was gated, nothing was cold, nothing was repeated, and no reboot was
spent. Qwen3.5-2B is untouched — the hybrid-model question the predictions
entry flagged as the one thing that could fail on a code path rather than on a
number is **still open**, because this pair ran on Qwen3-1.7B only.

### THE ROW PLAN FOR Q-A AND Q-B, written 16 Sept 17:1x BEFORE ANY REBOOT IS SPENT

Ten rows, four boots, of which **two boots are mandatory and two are optional
and are Matt's to spend**. Every measured row is GATED. Nothing below has run.

**THE GATE, IDENTICAL ON EVERY MEASURED ROW.** Polled until BOTH clusters read
rated, with the row launched in the same shell invocation so nothing intervenes
between the check and the start:

    while [ "$(cat /sys/devices/system/cpu/cpufreq/policy6/scaling_max_freq)" != 2802000 ] ||
          [ "$(cat /sys/devices/system/cpu/cpufreq/policy4/scaling_max_freq)" != 2253000 ]; do
        sleep 5
    done; PENNYBIN=/data/local/tmp/pennyload ./pennybench.sh <tag> c0 -- <pennyload args>

Recovery from a row is ~110 s of idle (measured 16 Sept), so the gate will
usually block for about that long between rows. Every row is `c0` (the X1
pair), `-t 2`, `-lm none`, `n_ctx` 1024, greedy, no chat template,
`--sys-file penny_system.txt --user-file penny_user.txt` at 407 and 20 tokens
on Qwen3-1.7B. Common prefix below is written `GATE;` for brevity; the real
command is the loop above with the row's own tag and args.

#### BOOT 1 — Qwen3-1.7B-Q4_K_M, MANDATORY. Model already on the phone.

Before any row, the protocol's two fresh-boot readings, each with uptime and
wallclock read in the SAME invocation:

    adb shell 'echo "uptime_s=$(cut -d\  -f1 /proc/uptime) wallclock=$(date +%H:%M:%S)"; \
               grep -E "^(MemTotal|MemAvailable|MemFree|SwapFree|Cached):" /proc/meminfo'

taken at **~5 min** and again at **~25 min** after power-on. Row 1 runs AFTER
the 25-minute reading, which is also where `MemAvailable` peaks on this
handset.

**NOTHING BETWEEN THE REBOOT AND ROW 1 MAY READ THE MODEL FILE. IN PARTICULAR,
NO `sha256sum` OF THE `.gguf`.** Added at Matt's instruction, and it is the
same rule the state file gets on boot 2 for the same reason: hashing a
1,056 MiB file reads every byte of it into the page cache, which is precisely
the cache row 1 exists to measure cold. Presence is confirmed with `ls -la`
ONLY -- name, size and mode, no read of the contents:

    adb shell 'ls -la /data/local/tmp/Qwen3-1.7B-Q4_K_M.gguf'
    -> must read 1107409472 bytes

The file's sha256 was verified against MANIFEST.txt on the phone at 16 Sept
16:55 and again is not re-checked until AFTER row 1 has run. Size plus that
earlier verification is what stands in for it in the meantime. The same rule
applies to the model swap before boot 3: the `sha256sum` at step 3 there runs
BEFORE the reboot, which is why it is harmless.

    tag   q17_r1_cold_fresh          COLD page cache, gated, fresh, no state
    GATE; ./pennybench.sh q17_r1_cold_fresh c0 -- \
      -m /data/local/tmp/Qwen3-1.7B-Q4_K_M.gguf -t 2 -lm none -n 64 --print \
      --sys-file /data/local/tmp/penny_system.txt \
      --user-file /data/local/tmp/penny_user.txt --tag r1
    SCORES  A1 (t_ready_ms), A2 (t_model_total_ms), A3 (t_ctx_create_ms),
            B1 (ttft_fresh_ms, T10-T6)
    WHY NO --save-state HERE: the save sits between T9a and T9b by design, so
    it lands INSIDE the TTFT window and cost ~20 ms on the smoke pair. Row 1's
    B1 is kept clean; the save moves to row 2.

    tag   q17_r2_warm_fresh_save     WARM page cache, gated, fresh + save
    GATE; ./pennybench.sh q17_r2_warm_fresh_save c0 -- \
      -m ... -t 2 -lm none -n 64 --print --sys-file ... --user-file ... \
      --save-state /data/local/tmp/q17_state.bin --tag r2
    SCORES  A5 (t_ready_ms, and the ratio to row 1 -- A5 FAILS if warm >= 90%
            or <= 40% of cold), B4 (t_state_save_ms), B5 (state_bytes)
    NOTE    its ttft_fresh_ms carries the save inside it and is NOT a second
            clean B1. Its token_fnv1a64 is the reference for rows 3 and 5.
    WRITES  q17_state.bin, which rows 3 and 5 consume.

    tag   q17_r3_warm_cached         WARM page cache, gated, --load-state
    GATE; ./pennybench.sh q17_r3_warm_cached c0 -- \
      -m ... -t 2 -lm none -n 64 --print --user-file ... \
      --load-state /data/local/tmp/q17_state.bin --tag r3
    SCORES  B2 (ttft_cached_ms, T10-T5) and NOTHING ELSE.
    **B2 IS PAGE-CACHE INDEPENDENT AS FAR AS THE MODEL GOES, AND THAT IS WHY
    IT SITS HERE.** T10-T5 starts after the model is already loaded, so a warm
    boot does not flatter the model half of it.
    **BUT THE STATE FILE IS WARM IN THE PAGE CACHE ON THIS ROW, AND THE LABEL
    SAYS SO.** Row 2 wrote `q17_state.bin` seconds earlier, so row 3's
    `t_state_load_ms` is a warm read of 44.5 MiB, not a cold one. Row 3's B2
    is therefore recorded as **"B2, state file WARM"**, and row 5 is the
    cold-file version of the same quantity. If the two differ, the difference
    is the cost of reading the state file off UFS rather than out of RAM, and
    that is worth having rather than worth hiding. Row 3's T10-T0 is NOT B3 --
    see the B3 section below.

    tag   q17_r4_warm_nobufts        WARM, gated, CONTROL, --extra-bufts 0
    GATE; ./pennybench.sh q17_r4_warm_nobufts c0 -- \
      -m ... -t 2 -lm none --extra-bufts 0 -n 1 \
      --sys-file ... --user-file ... --tag r4
    SCORES  A4, by subtraction: repack cost = row2.t_model_total -
            row4.t_model_total, **both WARM and both gated**.
    **THE SUBTRACTION MUST BE WARM-AGAINST-WARM.** Repack is CPU work and is
    cache-independent; the file read is not. Subtracting a warm no-repack row
    from row 1's COLD figure would fold the disk read into the repack and
    invert the answer A4 exists to give. A4 is then reported as a share of
    BOTH row 2's warm t_model_total and row 1's cold A2, each labelled.
    NO SPEED, TTFT OR RSS FIGURE FROM ROW 4 IS QUOTED ANYWHERE -- it selects
    different matmul kernels. `-n 1` because only the load matters.

    END OF BOOT 1: **`sync` FIRST**, then hash the state file and record it.
    adb shell 'sync; sha256sum /data/local/tmp/q17_state.bin; ls -la /data/local/tmp/q17_state.bin'

    **WHY THE `sync` — ADDED 16 Sept BY MATT, AFTER ROW 2.** Row 2 measured
    B4 at 17.91 ms for 44.5 MiB, which is a write into the page cache:
    `llama_state_save_file` issues NO `fsync`. So at the end of boot 1 the
    file's bytes may exist only in RAM. Hashing it would read them straight
    back out of that cache and report a hash for something not yet on UFS,
    and a reboot could then land row 5 on a short or absent file. `sync`
    flushes it first, and **the fact that `sync` was run is recorded in the
    row entry**, so that if the boot-2 hash differs it is a real result about
    the storage rather than an unflushed page cache.
    (Hashing here is safe and hashing on boot 2 before row 5 is not: boot 1's
    cache is about to be destroyed by the reboot anyway.)

#### BOOT 2 — Qwen3-1.7B, OPTIONAL, ONE REBOOT, AND IT IS THE ONLY TRUE B3

    tag   q17_r5_coldcache_cached    COLD page cache -- BOTH the model AND the
                                     state file -- gated, --load-state,
                                     THE FIRST RUN OF THE BOOT
    GATE; ./pennybench.sh q17_r5_coldcache_cached c0 -- \
      -m ... -t 2 -lm none -n 64 --print --user-file ... \
      --load-state /data/local/tmp/q17_state.bin --tag r5
    SCORES  B3 (ttft_cold_proc_ms, T10-T0) -- and this row is the only place
            in the plan where B3 can be scored.

**SETTLING B3, BECAUSE THE TWO CASES WERE ABOUT TO BE BLURRED.** Matt raised
this and he is right. `pennyload` prints `ttft_cold_proc_ms` on EVERY run,
including row 3, and it is tempting to read row 3's as B3. **It is not.**

    row 3   new process, state file on disk, SAME boot as rows 1-2
            -> the model file's pages are in the page cache, so the model load
               inside it is a WARM load. Call it TTFT-cached-warm-process.
    row 5   new process, state file on disk, FIRST run after a power cycle
            -> nothing has read the model on this boot, so the load is COLD.
               This is the wake case: the phone came up, nobody touched it,
               and the first thing that happens is a cached prefix answering.

B3's own prediction (4.9 s, band 3.5-8.5 s) was built as cold-ready plus state
load plus one 20-token decode, so it assumes a COLD load and only row 5
supplies one. Row 3's T10-T0 is recorded as its own quantity, under its own
name, and **is never quoted as B3**. The smoke pair's 2903.74 ms is the same
mistake avoided in advance: it was warm, and it is not B3 either.

**THE COST IS ONE EXTRA REBOOT PER MODEL** — two in total if both are wanted —
because a boot can supply exactly one cold read of the model file, and boot 1
has to spend that on row 1 to answer A1/A2.
**BOOT 2 IS APPROVED — Matt, 16 Sept, before the first reboot — so row 5 is
planned on, not optional. BOOT 4 IS UNDECIDED** and the hybrid's B3 is
therefore not planned on. If boot 4 is never spent, that B3 is reported as NOT
MEASURED and row 8's warm-process figure is reported under its own name, with
this reasoning attached so a later reader knows it is blank rather than
forgotten.

**THE STATE FILE MUST SURVIVE THE REBOOT, AND IT DOES.**
`/data/local/tmp` sits on the `/data` partition, which is exactly where the
`penny3ev` and `penny3eiii` encrypted stores survived power cycles in rungs
3e-iv and 3e-v. It is not tmpfs. But it is stated rather than assumed, and it
is CHECKED: sha256 at the end of boot 1, and sha256 again on boot 2 **AFTER
row 5 has run, never before it.**

**HASHING IT FIRST WOULD DESTROY THE ROW.** Reading 44.5 MiB to hash it pulls
the state file into the page cache, and row 5's whole point is that both the
model AND the state file are read cold. So the order on boot 2 is: unlock, the
two protocol readings, row 5, then the hash. If the two hashes differ the row
is VOID and is re-run; if they match, the file row 5 read is the file boot 1
wrote.

#### BOOT 3 — Qwen3.5-2B-Q4_K_M, MANDATORY. Model swap happens BEFORE the reboot.

One model on the phone at a time. `adb push` writes THROUGH the page cache, so
the push must happen before the power cycle or the "cold" row is measuring a
cache that was filled ninety seconds earlier. Order, all of it Matt's:

    1  adb shell rm /data/local/tmp/Qwen3-1.7B-Q4_K_M.gguf
    2  adb push ~/Documents/penny-models/Qwen3.5-2B-Q4_K_M.gguf /data/local/tmp/
    3  adb shell sha256sum /data/local/tmp/Qwen3.5-2B-Q4_K_M.gguf
       -> must read aaf42c8b7c3cab2bf3d69c355048d4a0ee9973d48f16c731c0520ee914699223
    4  adb reboot        <- this is what makes the next read cold

Then rows 6-9, identical in shape to rows 1-4 with the new model path and tags
`q35_r6_cold_fresh`, `q35_r7_warm_fresh_save` (writes `q35_state.bin`),
`q35_r8_warm_cached`, `q35_r9_warm_nobufts`.

    SCORES  A6 (row 6 t_ready_ms), B6 (row 7 state_bytes), and row 8's B2
            equivalent. A1-A5 are Qwen3-1.7B's and are not re-scored here.
    **THE ONE THING HERE THAT CAN FAIL ON A CODE PATH RATHER THAN A NUMBER.**
    Qwen3.5-2B is a HYBRID -- eighteen of its twenty-four layers are recurrent.
    If `llama_state_save_file` returns false on row 7, or
    `llama_state_load_file` returns false on row 8, `pennyload` prints
    `FAILED state_save=false` / `FAILED state_load=false` and exits 3.
    **THAT IS THE RESULT AND NOTHING IS PATCHED TO GET AROUND IT** -- it is
    reported as "a hybrid's prefix cannot be cached at this commit", B6 is
    reported as not obtainable, and row 9 still runs because A4's control does
    not depend on the state path. The smoke pair proves the path works on a
    dense model only; it says nothing about the hybrid.

#### BOOT 4 — Qwen3.5-2B, OPTIONAL, the hybrid's true B3

    tag   q35_r10_coldcache_cached   as row 5, with q35_state.bin.
    Only meaningful if row 7 and row 8 both succeeded.

#### WHAT VOIDS A ROW, DECIDED BEFORE THE RUNS

- **The contamination rule.** If `Cached` falls by roughly the model's size
  inside a row, or `SwapFree` drops below ~10% of `SwapTotal`, the boot is
  spent: the row is void, the phone is rebooted, the two protocol readings are
  retaken, and the row re-runs. Rev 4 records `pswpin`, `pswpout` and
  `pgmajfault` either side so the next occurrence is measured, not inferred.
- **A kill count is reported only with the row's `MemAvailable` before it.**
  A zero on a long-running boot is flattered by that boot's earlier kills.
- Every row's numbers are read from `out/<tag>.report` and `out/<tag>.bench`.
  Scrollback is not a record.
- `token_fnv1a64` on rows 2, 3 and 5 must all match, and on rows 7, 8 and 10.
  A mismatch is reported as the more important finding of the two, exactly as
  the smoke pair's match was.

#### WHAT THIS PLAN DOES NOT SAY

Nothing here has been measured; it is an order of operations. It spends two
reboots and asks for two more. It does NOT include: a sustained or thermal run,
`-ub`, Q4_0, anything on battery, `policy0` sampled during a row, any VM, any
third model, or any judgement of output quality. Gemma is not in it. The
`-n 64 --print` text from every row is recorded as text produced and nothing
more. And the plan assumes the phone comes back on adb after each reboot only
because a human unlocks it — GrapheneOS keeps the port charging-only while
locked, so every boot in this plan has a hand-unlock in it that is not
automatable and is not measured.

## 2026-09-16 — Q-A AND Q-B, THE MEASURED ROWS. Boot 1, Qwen3-1.7B-Q4_K_M.

Rows are appended here one at a time and committed BEFORE the next one runs.
Nothing lives only in `/data/local/tmp/out/` and a terminal.

**THE BOOT.** `adb reboot` issued 16 Sept 17:16:57, last pre-reboot reading
uptime 6943.86 s in the same invocation. Unlocked by hand; adb answered at
uptime 31.69 s. Qwen3-1.7B-Q4_K_M.gguf was already on the phone from the
15:20:52 boot and **was not hashed on this boot before row 1** — presence
confirmed by `ls -la` alone, 1,107,409,472 B, because hashing 1,056 MiB would
read every byte into the page cache row 1 exists to measure cold. App
`enabled=3` (disable-user'd), `Running VMs: []`, AC power, screen on.

**THE THREE FRESH-BOOT READINGS**, each with uptime and wallclock read in the
SAME `adb shell` invocation:

    uptime s   wallclock   MemAvailable   MemFree     Cached      SwapFree
      31.69    17:17:49        726,468     80,828     873,560   2,739,964
     303.33    17:22:21      2,282,044  1,618,340     887,812     888,060
    1506.32    17:42:24      2,284,200  1,587,984     919,096     889,596

All three cluster ceilings read rated at every one of them — `policy6`
2,802,000, `policy4` 2,253,000, `policy0` 1,803,000.

**The 31.69 s reading is the earliest this repo has ever read `MemAvailable` on
a fresh boot** and it was a bonus: adb answered far sooner than expected. It is
recorded, not used — 726,468 kB is a phone 32 seconds into settling.

**THE 5-MINUTE AND 25-MINUTE FIGURES REPRODUCE THE HANDSET'S KNOWN CURVE, AND
SWAP DOES NOT.** `MemAvailable` 2,282,044 kB at 303 s and 2,284,200 kB at
1,506 s, against 2,190,520 kB at 308.17 s and 2,135,144 kB at 1,500.30 s on the
15:20:52 boot (notes.md 7915) — within 4.2% and 7.0%. But `SwapFree` compared
at the MATCHED marks: **889,596 kB at 1,506 s here against 1,126,396 kB at
1,500.30 s there.** And the shapes differ: this boot went 888,060 -> 889,596 kB
between 303 s and 1,506 s, i.e. **flat**, where the 15:21 boot RECOVERED over
the same window (988,156 -> 1,126,396, +138,240 kB). Recorded as an observed
difference between two boots and nothing more; nothing here identifies what
recovered swap on one boot and not the other.

### ROW 1 — `q17_r1_cold_fresh`. COLD page cache, gated. A1, A2, B1 PASS; A3 FAILS.

    started uptime 1520.56 s   ended 1536.00 s   rc=0
    c0, -t 2, -lm none, n_ctx 1024, n_ubatch 512, greedy, chat_template=NONE
    sys_tokens 407, user_tokens 20, -n 64 --print, NO --save-state
    gate: policy6 2,802,000 AND policy4 2,253,000 read in the launching
          invocation; both at rated, so the gate did not block
    first run of the boot -- nothing had read the .gguf since power-on

    t_backend_ms          3.04
    t_model_open_ms     398.49   header + hparams + vocab + alloc
    t_tensor_band_ms   3581.59   tensor data read + repack
    t_model_tail_ms       2.04
    t_model_total_ms   3982.13   == A2
    t_ctx_create_ms      36.62   == A3
    t_ready_ms         4021.78   == A1
    t_tokenize_ms         3.56
    t_sys_decode_ms    5996.47   407 tokens, one llama_decode
    t_user_decode_ms    371.75   20 tokens
    t_sample_ms           0.89
    ttft_fresh_ms      6369.12   == B1  (T10-T6)
    ttft_cached_ms     6372.68
    ttft_cold_proc_ms 10394.46   NOT B3 -- see below
    gen_tokens 64      gen_ms 4188.65      gen_tps 15.04
    first_token_id 32313     token_fnv1a64 0xcba17a2fcbba49f4
    peak_rss_kB 1,541,140    max_rssanon_kB 1,535,512 (99.6%)
    max_rssfile_kB 5,304     rss_samples 38

**`gen_tps` 15.04 IS 63 DECODES, NOT 64.** `pennyload.cpp:390` divides by
`gen_ids.size() - 1`, because the first token was already produced by the
sampler at T10 and the generation loop decodes the remaining 63.
63,000 / 4188.65 = 15.0406 t/s; the same interval over 64 would read 15.2794.
The divisor is stated so no later reader has to infer it.

**MEMORY AND KILLS.**

    MemAvailable   before 2,277,952 kB   after 2,669,292 kB
    MemFree        before 1,577,080      after 1,649,232
    SwapFree       before   889,596      after   720,380
    Cached         before   919,096      after 1,241,384   (ROSE)
    pswpin         before    30,361      after    30,672
    pswpout        before   597,499      after   689,982
    pgmajfault     before    43,278      after    43,663
    ceil_x1        before 2,802,000   min 2,048,000 (73.1%) 10 s in   after rated
    ceil_a76       before 2,253,000   min 2,253,000 (rated)           after rated
    lmk_kill_lines 8

**EIGHT LINES, FOUR PROCESSES, AND THE `MemAvailable` IN FRONT OF THEM IS
2,277,952 kB.** All four `reason: low watermark is breached`, all four `cch`:

    com.android.DeviceAsWebcam   oom_score_adj 915   cch +15 CEM
    com.android.keychain         oom_score_adj 915   cch +15 CEM
    com.android.deskclock        oom_score_adj 905   cch CEM
    com.google.euiccpixel        oom_score_adj 905   cch CEM

Nothing in the foreground band, nothing a user would notice, and our own
process was never touched. **This is the fresh-boot condition the B2-R1 rule
names as representative**: a boot that has not already bought itself headroom
by killing its own processes over hours. The twelve zero-kill `-lm none` rows
in the closing entry were all on a 17-18 hour boot and were flattered by it.

**The contamination condition is NOT met.** `Cached` ROSE by 322,288 kB across
the row rather than collapsing by the model's size, and `SwapFree` ended at
720,380 kB — 22.9% of `SwapTotal`, above the ~10% floor. `pgmajfault` moved 385.

### THE PREDICTIONS, SCORED

    #   prediction              point   band        measured    verdict
    A1  cold load -> ready       4.2 s  3.0-6.0     4.0218 s    PASS
    A2  ... read + repack        3.5 s  2.5-5.0     3.9821 s    PASS
    A3  ... context creation     0.4 s  0.2-0.8     0.03662 s   **FAIL, LOW**
    B1  TTFT fresh, resident     7.7 s  6.0-9.0     6.3691 s    PASS

**A3 FAILS BY AN ORDER OF MAGNITUDE AND IT WAS ALREADY KNOWN TO BE FAILING.**
The 16:50 smoke test read `t_ctx_create_ms` **35.83 ms**, and the state pair
read 34.46 and 32.44 ms — all three before this row, all on the same `n_ctx`
1024. **The prediction was deliberately left frozen rather than revised**, on
the rule that a prediction written before the work is judged, not edited to
fit what the instrument later showed. Row 1's 36.62 ms is the gated,
cold-boot confirmation of a figure the smoke runs had already put a factor of
ten below its band. Creating a 1024-token KV cache on this model is tens of
milliseconds, not hundreds.

**A1 PASSED WHILE ITS TWO COMPONENTS MISSED IN OPPOSITE DIRECTIONS.** A2 ran
482 ms ABOVE its 3.5 s point and A3 came in 363 ms BELOW its 0.4 s point, and
the two nearly cancel. A1 is a sum and its pass is partly arithmetic luck; A2
and A3 are the figures with meaning.

**B1 PASSED AT THE BOTTOM OF ITS BAND.** 6.369 s against a 6.0-9.0 band built
from the gated c0 two-thread `pp512` of 55.36 t/s (row C1, notes.md 7592):
427 tokens across `t_sys_decode` + `t_user_decode` = 6368.22 ms works out at
67.05 t/s, i.e. **21% faster than the `llama-bench` figure the prediction was
derived from**, on a gated row this time rather than the ungated smoke. Why a
single real prompt outruns `llama-bench`'s `pp512` is NOT established here and
must not be guessed at; it is the instrument question B1's failure condition
anticipated, arriving as a pass rather than a fail.

### WHAT ROW 1 DOES NOT SAY

**One sample, no error bar.** The X1 ceiling was already descending inside the
row (2,048,000 kHz, 73.1% of rated, 10 s in), so no figure here is this chip's
speed and none is a sustained figure.

**A5 IS NOT SCORED.** `t_tensor_band_ms` 3581.59 here against 2124.62 and
2115.08 on the two 16:50-17:06 smoke runs suggests the cold read costs ~1.46 s,
but those were UNGATED, on a different boot, and A5 is defined as a warm load
immediately after a cold one. Row 2 is the controlled pair and A5 is scored
there.

**A4, B2, B3, B4, B5, A6 and B6 are all untouched.** A4 needs row 4's
`--extra-bufts 0` control; B4 and B5 need row 2's save; B2 needs row 3; B3
needs row 5 on boot 2; A6 and B6 need Qwen3.5-2B, which is not on the phone.

**`ttft_cold_proc_ms` 10,394.46 ms IS NOT B3.** `pennyload` prints it on every
run. B3 is a cached run whose model load is cold — row 5 — and this row had no
state file in existence at all.

The 64 generated tokens are byte-identical to both smoke-pair runs
(`0xcba17a2fcbba49f4`) on a different boot and a cold load, which is a useful
determinism check and is not a quality claim. The text says Canberra's
population is around 4 million and that it was founded in 1901; both are wrong.
It is one greedy 64-token sample with NO chat template, so it is recorded as
text produced and settles nothing about output quality in either direction.

Qwen3-1.7B on the 6a, on AC power, screen on, unlocked, idle, once.

### ROW 2 — `q17_r2_warm_fresh_save`. WARM page cache, gated. A5, B4, B5 PASS.

    gate passed  uptime 1771.69 s, wallclock 17:46:49, both clusters at rated
                 -- 236 s after row 1 ended, so the ~110 s recovery had run
    started uptime 1771.76 s   ended 1785.85 s   rc=0
    c0, -t 2, -lm none, n_ctx 1024, n_ubatch 512, greedy, chat_template=NONE
    sys_tokens 407, user_tokens 20, -n 64 --print, PLUS --save-state
    WARM: row 1 read the same model file four minutes earlier

    t_backend_ms          3.62
    t_model_open_ms     364.13
    t_tensor_band_ms   2431.20
    t_model_tail_ms       2.23
    t_model_total_ms   2797.56
    t_ctx_create_ms      38.75
    t_ready_ms         2839.93   == A5
    t_tokenize_ms         3.56
    t_sys_decode_ms    5940.43   407 tokens
    t_state_save_ms      17.91   == B4
    state_bytes    46,685,237    == B5   (44.523 MiB, 407 tokens saved)
    t_user_decode_ms    360.32   20 tokens, timed from T8
    t_sample_ms           0.85
    ttft_fresh_ms      6319.51   NOT a second clean B1 -- see below
    ttft_cached_ms     6323.06
    ttft_cold_proc_ms  9163.00   NOT B3
    gen_tokens 64      gen_ms 4266.65      gen_tps 14.77  (63 decodes)
    first_token_id 32313     token_fnv1a64 0xcba17a2fcbba49f4
    peak_rss_kB 1,541,012    max_rssanon_kB 1,535,508 (99.6%)
    max_rssfile_kB 5,220     rss_samples 35

    MemAvailable   before 2,639,848 kB   after 2,681,152 kB
    MemFree        before 1,608,244      after 1,643,540
    SwapFree       before   820,732      after   751,100
    Cached         before 1,254,932      after 1,260,820
    pswpin         before    94,541      after    94,559
    pswpout        before   731,365      after   748,829
    pgmajfault     before   107,636      after   107,654
    ceil_x1        before 2,802,000   min 2,048,000 (73.1%) 6 s in   after rated
    ceil_a76       before 2,253,000   min 2,253,000 (rated)          after rated
    lmk_kill_lines 0

### THE PREDICTIONS, SCORED

    #   prediction              point     band        measured     verdict
    A5  warm load -> ready       3.2 s    2.2-4.5     2.8399 s     PASS
    B4  state save cost         0.25 s    0.05-1.0    0.01791 s    PASS
    B5  state file size       45.9 MiB    40-60       44.523 MiB   PASS

**A5's REAL TEST IS THE RATIO, NOT THE SECONDS, AND THE DIRECTIONAL CLAIM
SURVIVES.** The failure condition written before any run: A5 fails if warm is
**>= 90%** of cold (the read was never the cost, nothing was saved) or **<= 40%**
(the read dominated and repack is cheap, which kills the claim outright).

    warm ready / cold ready  =  2839.93 / 4021.78  =  **70.61%**

Between the two, so the claim stands: **the repack, not the file read, is the
larger half of load time, and a warm load still takes 2.84 s to become ready.**

**THE THREE DELTAS ARE THREE DIFFERENT QUANTITIES AND MUST NOT BE SWAPPED.**
Matt caught one being quoted for another:

    cold - warm, READY (T5-T0)          4021.78 - 2839.93  =  1,181.85 ms
    cold - warm, MODEL LOAD (T4-T1)     3982.13 - 2797.56  =  1,184.57 ms
    cold - warm, TENSOR BAND (T3-T2)    3581.59 - 2431.20  =  1,150.39 ms

The ready figure is 4,021.78 **ms**, not seconds. Essentially the whole saving
sits in the tensor band — 1,150.39 of the 1,181.85 ms — which is where the file
read lives, and that is consistent with the repack being CPU work that a warm
cache cannot make cheaper. **It is not yet the A4 measurement**: this is
cold-against-warm, and A4 is warm-against-warm with the repack switched off.
Row 4 is the one that separates them.

**B4 AND B5 ARE BOTH FAR INSIDE THEIR BANDS AND ONE OF THEM MEANS LESS THAN IT
LOOKS.** B4 at 17.91 ms is a fourteenth of its lower bound: writing 44.5 MiB
took under 20 ms, which is a write into the page cache rather than a write to
UFS, and no `fsync` is issued by `llama_state_save_file`. **So B4 measures the
cost of handing the bytes to the kernel, not the cost of them reaching
storage.** B5 at 46,685,237 B is 114,706 B/token over 407 tokens against the
114,688 B/token derived from this model's GGUF metadata before any run — 18
bytes a token of header. It reproduces the smoke pair's byte count exactly,
which is what a deterministic size should do.

**THE ZERO KILL COUNT IS FLATTERED, AND THE TWO FIGURES SIDE BY SIDE ARE WHY.**

    row 1   MemAvailable before  2,277,952 kB   ->  8 kill lines, 4 processes
    row 2   MemAvailable before  2,639,848 kB   ->  0 kill lines

Row 2 started with **361,896 kB more** than row 1, and a large part of that is
exactly what row 1's four cached-process kills freed. This is the B2-R1 rule
happening inside a single boot rather than across two: **the second row on a
boot inherits headroom the first row bought, so its zero is not evidence that
loading this model costs no kills.** Row 1's eight lines are the representative
figure for a cold start on this handset; row 2's zero is a statement about row
2's starting conditions.

### WHAT ROW 2 DOES NOT SAY

One sample, no error bar, and the X1 ceiling fell to 2,048,000 kHz (73.1% of
rated) 6 s in — the identical floor row 1 reached, on a row that started from
rated after a full recovery.

**`ttft_fresh_ms` 6319.51 IS NOT A SECOND CLEAN B1.** The 17.91 ms state save
sits between T9a and T9b by design, so it lands inside the T10-T6 window. B1
stands on row 1 alone. The prompt throughput here works out at 67.77 t/s over
427 tokens (5940.43 + 360.32 ms), against row 1's 67.05 — two samples that
agree to 1.1%, which is a consistency check and not an error bar.

**A4 IS STILL UNSCORED.** Row 4's `--extra-bufts 0` control is what gives it,
warm against warm. Nothing in this row isolates repack from read.

`ttft_cold_proc_ms` 9163.00 is a WARM-process figure and is not B3. B3 is row 5
on boot 2.

B5 passing on a dense model says nothing about **B6** on Qwen3.5-2B, whose
eighteen recurrent layers may make `llama_state_save_file` return false
outright. That is boot 3's question and is untouched.

`q17_state.bin` now exists at 46,685,237 B, phone-written 17:46. **It is
deliberately NOT hashed yet** — the hash is taken at the end of boot 1, and on
boot 2 only AFTER row 5 has run, because reading 44.5 MiB to hash it would warm
the cache row 5 exists to measure cold.

The 64 tokens are again `0xcba17a2fcbba49f4`, identical across a cold load, a
warm load and two ungated smoke runs. Text recorded as text produced; two of
its three facts are wrong and quality is out of scope.

### ROW 3 — `q17_r3_warm_cached`. WARM model, WARM state file, gated. B2 PASSES.

    gate passed  uptime 2282.78 s, wallclock 17:55:20, both clusters at rated
                 -- 497 s after row 2 ended
    started uptime 2282.88 s   ended 2291.03 s   rc=0
    c0, -t 2, -lm none, n_ctx 1024, greedy, chat_template=NONE
    --load-state, NO --sys-file: the system prompt is neither read nor
    tokenised, by design (T6 falls inside the cached TTFT window)
    user_tokens 20, -n 64 --print

    t_backend_ms          1.87
    t_model_open_ms     370.45
    t_tensor_band_ms   2527.36
    t_model_total_ms   2900.36
    t_ctx_create_ms      57.72
    t_ready_ms         2959.95
    t_tokenize_ms         0.33   (user turn only)
    t_state_load_ms      19.17   state file WARM in page cache
    state_bytes    46,685,237    state_tokens_restored 407
    t_user_decode_ms    401.99   20 tokens
    t_sample_ms           0.81
    ttft_cached_ms      422.31   == B2, "state file WARM"
    ttft_cold_proc_ms  3382.26   NOT B3
    gen_tokens 64      gen_ms 4104.77      gen_tps 15.35  (63 decodes)
    first_token_id 32313     token_fnv1a64 0xcba17a2fcbba49f4
    peak_rss_kB 1,500,212    max_rssanon_kB 1,494,604 (99.6%)

    MemAvailable   before 2,679,200 kB   after 2,878,632 kB
    SwapFree       before   767,996      after   530,940
    Cached         before 1,269,556      after 1,529,716   (ROSE 260,160)
    pswpin         before    98,658      after    98,706
    pswpout        before   748,829      after   819,972
    pgmajfault     before   111,797      after   111,854
    ceil_x1        before 2,802,000   min 2,401,000 (85.7%) 7 s in  after rated
    ceil_a76       before 2,253,000   min 2,253,000 (rated)         after rated
    lmk_kill_lines 2

### THE PREDICTION, SCORED

    #   prediction                        point   band        measured   verdict
    B2  TTFT cached, model RESIDENT        0.5 s  0.35-1.5    0.42231 s  PASS

**B2 IS "STATE FILE WARM" AND THE LABEL IS NOT A FORMALITY.** Row 2 wrote
`q17_state.bin` nine minutes earlier on this same boot, so `t_state_load_ms`
19.17 ms is a read of 44.5 MiB **out of RAM**, not off UFS — the mirror image
of B4's no-`fsync` write. Row 5, on boot 2, is the cold-file version of exactly
this quantity, and the difference between the two is the cost of the storage
read. Until row 5 runs, **B2 is measured only in its easiest form.**

**THE SPEEDUP, WHICH IS THE POINT OF Q-B, AND IT IS QUOTED AGAINST BOTH FRESH
ROWS:**

    row 1 ttft_cached  6372.68 ms  ->  row 3  422.31 ms   15.09x
    row 2 ttft_cached  6323.06 ms  ->  row 3  422.31 ms   14.97x

A 407-token prefix that costs ~6.0 s to process fresh costs **19.17 ms to
restore**, and the whole path from a ready model to a first token falls from
~6.37 s to 0.42 s. What remains in that 422 ms is almost entirely the 20-token
user turn: `t_user_decode_ms` 401.99 ms, i.e. **95.2% of B2 is decoding the
turn itself**, not restoring the prefix. Prefix caching has moved the cost
somewhere else entirely, and the remaining cost is the user's own words.

**TWO KILL LINES, ONE PROCESS, WITH `MemAvailable` BEFORE AT 2,679,200 kB:**
`app.grapheneos.carrierconfig2`, `oom_score_adj 905`, `cch CEM`, `reason: low
watermark is breached`. Cached band, nothing a user would see. Same flattering
caveat as row 2 — this row started 401,248 kB above row 1 — so the honest
ordering is row 1's eight lines at 2,277,952 kB, then two here, then zero on
row 2, and only row 1 is a cold-start figure.

**`Cached` ROSE 260,160 kB and `SwapFree` FELL 237,056 kB across the row.**
`pgmajfault` moved 57. The contamination condition is not met, but SwapFree is
now 530,940 kB — **16.9% of `SwapTotal`**, against the ~10% floor that voids a
boot. It has fallen on every row (889,596 -> 720,380 -> 751,100 -> 530,940 kB
at the row boundaries). Row 4 is the last row of boot 1 and will be watched for
it.

### WHAT ROW 3 DOES NOT SAY

**IT IS NOT B3 AND `ttft_cold_proc_ms` 3382.26 MUST NEVER BE QUOTED AS ONE.**
This is a new process, but its model load was warm — rows 1 and 2 had already
read the file. B3 is a cached run whose model load is cold, which only row 5 on
boot 2 supplies. This is the distinction the plan settled in advance and this
row is exactly the shape that would have been misreported without it.

**`t_ctx_create_ms` 57.72 ms is the highest of the three rows** (36.62, 38.75,
57.72). No explanation is offered; three samples on one boot, and A3 was
already scored on row 1.

One sample. The X1 ceiling fell to 2,401,000 kHz (85.7%), a shallower dip than
rows 1 and 2 — this row does ~6 s less arithmetic, since it never decodes the
system prompt. `gen_tps` 15.35 over 63 decodes is the fastest of the three and
is not an error bar.

`state_tokens_restored` is 407, the count row 2 saved, and the 64 generated
tokens hash to `0xcba17a2fcbba49f4` — identical across a cold fresh load, a
warm fresh load, a cached load, and two ungated smoke runs. **The restored
prefix produces the same output as the decoded one**, which is the control, and
it is not a quality claim. Text as before: Canberra right, the other two facts
wrong, quality out of scope.

A4 is still unscored — row 4 next.

### ROW 4 — `q17_r4_warm_nobufts`. The `--extra-bufts 0` CONTROL, warm, gated. A4 PASSES.

    gate passed  uptime 2429.84 s, wallclock 17:57:47, both clusters at rated
    started uptime 2429.92 s   ended 2447.29 s   rc=0
    c0, -t 2, -lm none, **--extra-bufts 0**, -n 1, n_ctx 1024
    WARM, and warm is what makes the subtraction valid

    t_model_open_ms     383.94
    t_tensor_band_ms    411.44   <- against row 2's 2431.20
    t_model_total_ms    797.67
    t_ctx_create_ms      33.16
    t_ready_ms          833.77
    progress_calls         311   (row 1/2/3 all reported 312)
    lmk_kill_lines           0

    MemAvailable   before 2,887,072 kB   after 2,863,200 kB
    SwapFree       before   568,828      after   569,084
    Cached         before 1,532,872      after 1,535,688
    pswpout        before   819,972      after   819,972   (did not move)
    pgmajfault     before   121,393      after   121,439
    ceil_x1        before 2,802,000   min 1,826,000 (65.2%) 16 s in  after rated
    ceil_a76       before 2,253,000   min 2,253,000 (rated)          after rated

**SWAPFREE AGAINST THE FLOOR, EXPLICITLY.** The void condition is `SwapFree`
below ~10% of `SwapTotal` = **314,572 kB of 3,145,724**.

    before  568,828 kB   =  18.08% of SwapTotal
    after   569,084 kB   =  18.09%

**It did not cross, and it did not fall — it rose by 256 kB.** `pswpout` did
not move at all across the row. Swap had also recovered between rows 3 and 4
(530,940 -> 568,828 kB while the phone sat idle through the gate), so the
monotonic decline noted after row 3 was not monotonic. Boot 1 is not
contaminated and row 4 stands without that caveat.

### A4, SCORED — WARM AGAINST WARM, FROM THE TENSOR BAND ONLY

    row 2  t_tensor_band_ms   2431.20   repack ON,  warm
    row 4  t_tensor_band_ms    411.44   repack OFF, warm
    repack cost                2019.76 ms

    #   prediction                    point   band     measured   verdict
    A4  repack's share of A2            52%   40-65%   **50.72%**  PASS

A2 is the COLD `llama_model_load_from_file` figure, 3982.13 ms (row 1), which
is what the predictions entry defined A4 as a share of. 2019.76 / 3982.13 =
**50.72%**, two-thirds of a point from the 52% predicted.

**THE SAME NUMBER AS A SHARE OF OTHER DENOMINATORS, LABELLED, BECAUSE IT IS
EASY TO QUOTE THE WRONG ONE:**

    2019.76 / 3982.13 (row 1 cold total)   50.72%   <- this is A4
    2019.76 / 2797.56 (row 2 warm total)   72.20%
    2019.76 / 2431.20 (row 2 warm band)    83.08%

**On a WARM load the repack is 83% of the tensor band and 72% of the whole
load.** The read is what a warm cache removes, and what is left is almost
entirely repack — which is the same conclusion A5's 70.61% ratio reached from
the other direction, now with the repack isolated rather than inferred.

**THE SUBTRACTION IS TAKEN FROM THE TENSOR BAND AND NOT FROM `t_model_total`**,
at Matt's instruction. `t_model_open_ms` differs between the two rows (364.13
against 383.94) for reasons this row does not establish, and folding that
difference into the repack figure would put ~20 ms of unexplained work inside
it. The band is where `set_tensor` — and therefore the repack — actually runs.

**`progress_calls` IS 311 HERE AND 312 ON EVERY OTHER ROW.** Recorded as
observed. No explanation is offered.

**THE BUFFER LINES, SIDE BY SIDE, READ OFF THE TWO `.err` FILES ON THE PHONE.**
This is the structural half of the same difference the 2019.76 ms measures:

    row 2, repack ON        CPU        model buffer size     243.90 MiB
                            CPU_REPACK model buffer size   1,049.96 MiB
                            sum                            1,293.86 MiB
                            "repack tensor" lines                  197

    row 4, repack OFF       CPU        model buffer size   1,050.43 MiB
                            NO CPU_REPACK line at all
                            "repack tensor" lines                    0

    Qwen3-1.7B-Q4_K_M on disk                              1,056.13 MiB

With the repack off there is ONE buffer and it is 5.70 MiB BELOW the file.
The 197 tensors that row 2 repacks would, unrepacked, account for
1050.43 - 243.90 = **806.53 MiB**; repacked they occupy **1,049.96 MiB**.

### WHAT ROW 4 DOES NOT SAY

**NO SPEED, TTFT OR RSS FIGURE FROM THIS ROW IS QUOTED AS A RESULT**, per the
rule written into the plan before it ran: `--extra-bufts 0` selects different
matmul kernels, so nothing it does at runtime is comparable with any other row.
**The row confirms that rule rather than testing it**: its system-prompt decode
took 15,089.42 ms against row 2's 5,940.43 — **2.54x slower** — which is what
switching off the ARM dot-product repack does to the kernels, and is exactly
why its arithmetic figures are excluded.

**Two things sit in `q17_r4_warm_nobufts.report` that the plan's rule excludes,
and they are FLAGGED rather than used.** Its `peak_rss_kB` is 1,289,416 against
row 2's 1,541,012, and its `ceil_x1` minimum is 1,826,000 kHz (65.2% of rated,
the deepest of boot 1) reached 16 s in. The RSS difference looks structural
rather than kernel-speed — a repacked weight copy is memory the process holds —
but **the plan excluded row 4's RSS in advance and this entry honours that**.
If the repack's memory cost is worth having it needs its own designed
comparison, and that is a question for Matt, not a figure to lift from a
control row.

One sample. `-n 1`, so there is no generation here at all.

### END OF BOOT 1 — `sync`, THEN THE STATE FILE HASHED

Run at uptime 2477.46 s, wallclock 17:58:35, in one invocation, `sync` first:

    adb shell 'sync; ...; sha256sum /data/local/tmp/q17_state.bin; ls -la ...'

    q17_state.bin   46,685,237 B   mtime 2026-09-16 17:46
    sha256  707e0ea3c1cc490187616a67ba0097747c8b8c58fcd2dcf38e1870a31a8f6f4d

**`sync` WAS RUN AND THAT IS THE POINT OF RECORDING IT.** Row 2's B4 of
17.91 ms proves `llama_state_save_file` issues no `fsync`, so without the
`sync` these bytes might have been hashed out of the page cache while still
unwritten. They are flushed. **So if boot 2's hash differs from
`707e0ea3…`, that is a real result about the file surviving a power cycle and
not an artefact of an unflushed write.**

Boot 1 closing state, same invocation: `MemAvailable` 2,870,644 kB, `SwapFree`
589,820 kB (18.75% of `SwapTotal`), `Cached` 1,535,700 kB.

**Boot 1 is complete: rows 1-4 run, A1, A2, A4, A5, B1, B2, B4 and B5 scored,
A3 failed low.** Outstanding on Qwen3-1.7B: **B3 only**, which needs boot 2 and
is approved. Qwen3.5-2B's A6 and B6 need boot 3 and its model is not on the
phone.

## 2026-09-16 — BOOT 2, Qwen3-1.7B. ROW 5, THE ONLY TRUE B3.

**THE STATE FILE SURVIVED THE POWER CYCLE, BYTE FOR BYTE.** Hashed at uptime
1544.55 s, wallclock 18:32:29, **AFTER** row 5 ran:

    boot 1, after `sync`   707e0ea3c1cc490187616a67ba0097747c8b8c58fcd2dcf38e1870a31a8f6f4d
    boot 2, after row 5    707e0ea3c1cc490187616a67ba0097747c8b8c58fcd2dcf38e1870a31a8f6f4d
    **MATCH.**  46,685,237 B both times, mtime 2026-09-16 17:46.

The ordering is what makes it worth anything: hashing BEFORE the row would have
pulled 44.5 MiB into the page cache and destroyed the cold read the row exists
to measure. And the `sync` on boot 1 means a mismatch would have been a real
result about storage rather than an unflushed write. Neither file was read
before the row — presence confirmed by `ls -la` only.

**THE BOOT.** `adb reboot` 18:06:24 (last pre-reboot reading uptime 2946.71 s,
same invocation). Unlocked by hand; adb answered at uptime 201.70 s. App
`enabled=3`, `Running VMs: []`, AC power, screen on.

    uptime s   wallclock   MemAvailable   MemFree     Cached      SwapFree
     201.70    18:10:06      1,043,824     68,236   1,206,340   2,625,020
     301.38    18:11:46      2,185,268  1,347,412   1,065,320     993,788
    1507.69    18:31:52      2,197,612  1,312,536   1,111,096     997,372

All three ceilings rated at all three readings. Against boot 1 at the matched
marks: `MemAvailable` 2,185,268 vs 2,282,044 kB at ~5 min (4.2% lower) and
2,197,612 vs 2,284,200 kB at ~25 min (3.8% lower); `SwapFree` 997,372 vs
889,596 kB at ~25 min. **The two boots are close but not identical**, and one
difference is worth stating because it is in the page cache: `Cached` read
1,206,340 kB at 201.70 s here, against 873,560 kB at 31.69 s and 887,812 kB at
303.33 s on boot 1. **Nothing of ours was in it** — neither file had been read —
but the boots did not start the same, and that is recorded rather than
explained.

### ROW 5 — `q17_r5_coldcache_cached`. COLD model AND COLD state file. B3 PASSES.

    gate passed  uptime 1520.91 s, wallclock 18:32:05, both clusters at rated
    started uptime 1520.98 s   ended 1530.08 s   rc=0
    FIRST run of the boot. Nothing had read the .gguf or the state file.
    c0, -t 2, -lm none, n_ctx 1024, greedy, --load-state, no --sys-file
    user_tokens 20, -n 64 --print

    t_backend_ms          2.80
    t_model_open_ms     418.46
    t_tensor_band_ms   3216.54
    t_model_total_ms   3637.26
    t_ctx_create_ms      52.46
    t_ready_ms         3692.53
    t_tokenize_ms         0.33
    t_state_load_ms      59.16   state file COLD -- off UFS
    state_bytes    46,685,237    state_tokens_restored 407
    t_user_decode_ms    345.96
    t_sample_ms           0.76
    ttft_cached_ms      406.21   B2-equivalent, cold state file
    ttft_cold_proc_ms  4098.74   == **B3**
    gen_tokens 64      gen_ms 4177.14      gen_tps 15.08  (63 decodes)
    first_token_id 32313     token_fnv1a64 0xcba17a2fcbba49f4
    peak_rss_kB 1,500,196    max_rssanon_kB 1,494,564 (99.6%)

    MemAvailable   before 2,191,292 kB   after 2,713,172 kB
    SwapFree       before   997,372      after   651,260   (20.70% of total)
    Cached         before 1,111,096      after 1,331,588   (ROSE 220,492)
    pswpin         before    36,933      after    37,035
    pswpout        before   577,326      after   715,772
    pgmajfault     before    49,795      after    49,951
    ceil_x1        before 2,802,000   min 2,507,000 (89.5%) 7 s in  after rated
    ceil_a76       before 2,253,000   min 2,253,000 (rated)         after rated
    lmk_kill_lines 8

### THE PREDICTION, SCORED

    #   prediction                          point   band       measured   verdict
    B3  TTFT cached, COLD PROCESS            4.9 s  3.5-8.5    4.0987 s   PASS

**THIS IS THE WAKE CASE AND IT IS THE ONE THE WHOLE OF Q-B WAS SET TO ANSWER.**
The phone came up, nobody touched it, nothing had read either file, and a new
process loaded the model, restored a 407-token prefix from disk and had a first
token in **4.099 seconds**.

**THE COLD STATE FILE COSTS 39.99 ms MORE THAN THE WARM ONE**, and that is the
number row 3's label was reserving:

    row 3  t_state_load  19.17 ms   state file warm in page cache
    row 5  t_state_load  59.16 ms   state file cold, read off UFS
    difference           39.99 ms

44.5 MiB off UFS in 59.16 ms is ~753 MiB/s, the same order as the 598 and
835 MB/s cold reads rungs 3e-iv and 3e-iii measured through dm-crypt inside a
VM. **So reading the prefix off storage is not where the wake cost is.** The
B2-equivalent figures barely move: 406.21 ms here against row 3's 422.31 ms —
the cold row is 16.10 ms FASTER overall despite the 39.99 ms slower state read,
because its user-turn decode ran 345.96 ms against 401.99 ms. Two samples, one
each, and nothing here explains the decode difference.

**WHERE THE 4.099 s ACTUALLY GOES, AND IT IS NOT THE PREFIX:**

    t_ready_ms        3692.53   90.1%   loading the model
    t_state_load_ms     59.16    1.4%   restoring the 407-token prefix
    t_user_decode_ms   345.96    8.4%   the 20-token user turn
    everything else      ~1.09   0.0%

**The model load is 90% of the wake, and the cached prefix is 1.4% of it.**
Against the two fresh cold-process figures — row 1's 10,394.46 ms and row 2's
9,163.00 ms — the cached path is **2.54x and 2.24x** faster, and what it
removes is the ~6.0 s system-prompt decode. What it cannot remove is the load.

**EIGHT KILL LINES, FOUR PROCESSES, WITH `MemAvailable` BEFORE AT 2,191,292 kB:**

    .ShannonImsService                        oom_score_adj 985   cch +85 CEM
    com.shannon.rcsservice:shannonrcsservice  oom_score_adj 975   cch +75 CEM
    app.seamlessupdate.client                 oom_score_adj 945   cch +45 CEM
    com.android.localtransport                oom_score_adj 915   cch +15 CEM

All cached, all `reason: low watermark is breached`, none in the foreground
band, our own process untouched. **The same count as row 1's eight, at a
similar fresh-boot `MemAvailable` (2,191,292 here, 2,277,952 there) — so the
first row of a boot costs about four cached processes on this handset whether
it decodes a system prompt or restores one.** Different four processes each
time; the killer takes whatever is cheapest.

`SwapFree` ended at 651,260 kB, **20.70%** of `SwapTotal`, well above the
314,572 kB floor; `Cached` ROSE 220,492 kB. Not contaminated.

### WHAT ROW 5 DOES NOT SAY

**The cold model load here (`t_ready_ms` 3692.53 ms) is NOT a second A1.** A1
was scored on row 1 at 4021.78 ms on a different boot; this row is 329.25 ms
faster, with the tensor band 365.05 ms faster. Two cold loads on two boots that
did not start identically — see the `Cached` difference above — and neither is
an error bar for the other. **A1 stands on row 1 alone.**

One sample. The X1 ceiling fell to 2,507,000 kHz (89.5%), the shallowest dip of
any row, because this row does no system-prompt decode.

**B3's band was wide (3.5-8.5 s) and the pass is therefore weak evidence about
the prediction and strong evidence about the phone.** The interesting content
is the 90/1.4/8.4 split, not that 4.099 falls inside a five-second band.

`state_tokens_restored` is 407 and `token_fnv1a64` is `0xcba17a2fcbba49f4` —
identical across a cold fresh load, a warm fresh load, a warm cached load, a
**cold** cached load on a different boot, and two ungated smoke runs. The state
file round-tripped a power cycle and still produces the same 64 tokens. That is
the control; it is not a quality claim, and the text still gets two of its three
facts wrong.

**Qwen3-1.7B is DONE: A1-A5 and B1-B5 are all scored, A3 the only failure.**
Outstanding for this repo's predictions: **A6 and B6, which need Qwen3.5-2B**,
which is not on the phone. Boot 3 is a separate decision and the hybrid's state
path may refuse outright.

## 2026-09-18 — BOOT 3 OPENED for Qwen3.5-2B-Q4_K_M. Nothing measured yet. The 5-minute protocol reading was MISSED by a bug in this session's own poll, and the reading that replaced it is at 8.55 minutes and is not a substitute.

This entry exists because the session that swapped the model was interrupted
before it wrote anything down, and two days passed. HEAD was still `25d16b1`
(row 5) and `notes.md` was unchanged since 16 Sept 18:33, so the model swap, the
boot it was made for, and that boot's loss existed only in a terminal. Nothing
here is a measurement. It is the state row 6 will run on, written down before
it runs.

**THE 16 SEPT BOOT 3 IS GONE AND WAS NEVER SPENT.** The plan's boot 3 was
brought up at 18:38 on 16 Sept and row 6 was waiting on its 25-minute mark when
the session was interrupted. No row ran on it. Today's is a second boot 3.

### 1. THE MODEL SWAP — NOT RUN BY THIS SESSION, AND SAID SO

**The `rm` of `Qwen3-1.7B-Q4_K_M.gguf`, the `rm` of `q17_state.bin` and the
`adb push` of `Qwen3.5-2B-Q4_K_M.gguf` were run in the interrupted session on
16 Sept. This session did not run them and does not have their output.** It
cannot be quoted, and the scrollback that held it is gone. What stands in its
place is the resulting listing, read today:

    $ adb shell ls -l /data/local/tmp
    total 1264147
    -rw-rw-rw- 1 shell shell 1280835840 2026-09-15 20:26 Qwen3.5-2B-Q4_K_M.gguf
    -rwxr-xr-x 1 shell shell    4708216 2026-09-16 12:08 llama-bench
    -rwxr-xr-x 1 shell shell    3805208 2026-09-16 12:18 llama-simple
    drwxrwxrwx 4 shell shell       3452 2026-09-14 11:35 microdroid
    drwxrwxrwx 2 shell shell       8192 2026-09-16 18:32 out
    -rw-rw-rw- 1 shell shell       1911 2026-09-16 16:48 penny_system.txt
    -rw-rw-rw- 1 shell shell         95 2026-09-16 17:05 penny_user.txt
    -rwxr-xr-x 1 shell shell       8402 2026-09-16 16:57 pennybench.sh
    -rwxr-xr-x 1 shell shell    3817808 2026-09-16 16:29 pennyload

Qwen3-1.7B-Q4_K_M.gguf and q17_state.bin are both absent, which is the evidence
that the two `rm`s happened; there is no record of the commands themselves. The
`.gguf`'s mtime reads 2026-09-15 20:26 and **that is the Mac file's mtime, not
the push time** — `adb push` preserves the source mtime, the trap CLAUDE.md
already carries. So the listing dates the Mac's copy and says nothing about
when the transfer ran.

**THE HASH THIS SESSION DID RUN**, 18 Sept, on the previous boot at uptime
77,353 s, deliberately BEFORE the reboot so that warming 1.25 GB of page cache
cost nothing:

    $ adb shell 'echo "start $(date +%H:%M:%S)"; sha256sum /data/local/tmp/Qwen3.5-2B-Q4_K_M.gguf; echo "end   $(date +%H:%M:%S)"'
    start 10:17:25
    aaf42c8b7c3cab2bf3d69c355048d4a0ee9973d48f16c731c0520ee914699223  /data/local/tmp/Qwen3.5-2B-Q4_K_M.gguf
    end   10:17:26

    on phone        aaf42c8b7c3cab2bf3d69c355048d4a0ee9973d48f16c731c0520ee914699223
    MANIFEST.txt    aaf42c8b7c3cab2bf3d69c355048d4a0ee9973d48f16c731c0520ee914699223
    MATCH.  1,280,835,840 bytes both.

The MANIFEST.txt line reads `unsloth/Qwen3.5-2B-GGUF  Qwen3.5-2B-Q4_K_M.gguf
1280835840  aaf42c8b… VERIFIED vs HF LFS oid`, so the phone's copy is verified
against Hugging Face's published hash and not merely against itself.

**THE HASH ALSO ESTABLISHED THAT THE FILE WAS COLD ON THAT BOOT**, which is a
free result and is recorded as one. Memory either side of it, each read in its
own invocation:

    before  uptime 77,252.51 s  10:15:57   MemAvailable   885,664   Cached   972,876   SwapFree 2,520,080
    after   uptime 77,353.94 s  10:17:38   MemAvailable 2,122,348   Cached 2,227,704   SwapFree   943,120
    change                                              +1,236,684          +1,254,828        -1,576,960

`Cached` rose 1,254,828 kB against a model of 1,250,816 kB, so essentially none
of it was resident and the whole file came off UFS. The stamps are 10:17:25 and
10:17:26 at one-second resolution, i.e. between 1 and 2 seconds, i.e. 610 MB/s
to 1.22 GB/s — the same order as the 598 and 835 MB/s cold reads of rungs
3e-iv and 3e-iii, and **too coarse to quote tighter than that band**. The cost
is the line worth keeping: holding those pages pushed **1,576,960 kB of
anonymous memory out to zram** on a 21.5-hour boot.

### 2. THE REBOOT

    $ adb shell 'echo "last pre-reboot reading: uptime_s=$(cut -d\  -f1 /proc/uptime)  wallclock=$(date +%H:%M:%S)"' && adb reboot && echo "reboot sent"
    last pre-reboot reading: uptime_s=77389.83  wallclock=10:18:14
    reboot sent

`adb reboot` ran in the same command line, immediately after that reading, so
**10:18:14 is the wall-clock of the reading and the reboot went in within the
same invocation**; it is not a separately observed reboot time.

Matt unlocked the phone by hand. adb answered 45 seconds later.

### 3. THE CONNECT READING — uptime 24.80 s

Taken by a polling loop that waited for `adb get-state` and then ran ONE
invocation. **Wall-clock WAS read in the same invocation as the uptime.**
MemTotal and the cluster ceilings were NOT in it:

    $ adb shell 'echo "ADB BACK: uptime_s=$(cut -d\  -f1 /proc/uptime)  wallclock=$(date +%H:%M:%S)"; grep -E "^(MemAvailable|MemFree|Cached|SwapFree):" /proc/meminfo'
    ADB BACK: uptime_s=24.80  wallclock=10:18:59
    MemFree:           86676 kB
    MemAvailable:    1324400 kB
    Cached:          1461544 kB
    SwapFree:        3118588 kB

`Cached` 1,461,544 kB at 24.80 s is roughly 590 MB above the two boots the
earlier rows ran on at a comparable point — 873,560 kB at 31.69 s on 16 Sept
boot 1, 879,432 kB at 34.20 s on the abandoned 16 Sept boot 3. **Nothing of
ours is in it.** Recorded as an observed difference between boots and not
explained, the same way row 5 recorded boot 2's elevated `Cached`.

`SwapFree` 3,118,588 of 3,145,724 kB — 99.1% free, the cleanest start of any
boot in this plan.

### 4. THE READING AT 8.55 MINUTES — **NOT THE PROTOCOL'S ~5 MINUTE READING**

    $ adb shell 'echo "uptime_s=$(cut -d\  -f1 /proc/uptime)  wallclock=$(date +%H:%M:%S)"; grep -E "^(MemTotal|MemAvailable|MemFree|SwapFree|Cached):" /proc/meminfo; echo "ceil_x1=$(cat /sys/devices/system/cpu/cpufreq/policy6/scaling_max_freq)  ceil_a76=$(cat /sys/devices/system/cpu/cpufreq/policy4/scaling_max_freq)  ceil_a55=$(cat /sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq)"'
    uptime_s=512.80  wallclock=10:27:07
    MemTotal:        5718284 kB
    MemFree:         1596300 kB
    MemAvailable:    2310624 kB
    Cached:           940392 kB
    SwapFree:         847612 kB
    ceil_x1=2802000  ceil_a76=2253000  ceil_a55=1803000

512.80 s is **8.55 minutes**. The protocol asks for ~5 minutes and this boot
does not have one — see section 5. **It is not compared against boot 1's
303.33 s figures anywhere in this entry**: different marks on a curve that is
still moving at both of them, and a percentage between them would be an
invented comparison. It stands as a reading at 8.55 minutes and nothing else.
All three cluster ceilings rated.

### 5. THE POLL BUG THAT COST THE 5-MINUTE READING

The reading was queued to a background loop that polled the phone's uptime. The
loop sent:

    adb shell cut -d' ' -f1 /proc/uptime

**`adb shell` re-joins its arguments with spaces before the remote shell sees
them**, so the quoted space delimiter collapsed and the phone received
`cut -d -f1 /proc/uptime`. Reproduced deliberately afterwards:

    $ adb shell cut -d' ' -f1 /proc/uptime
    cut: Needs -CFfcb (see "cut --help")
    exit=1

The loop's uptime variable was therefore empty on every pass, its guard clause
treated that as "not a number yet" and it span until it was stopped. No reading
was taken at 300 s. The fixed poll reads the file whole and does the arithmetic
on the Mac, verbatim:

    u=$(adb shell cat /proc/uptime 2>/dev/null | tr -d '\r' | awk '{print int($1)}')

**The general trap, which is not yet in CLAUDE.md: any `adb shell` argument
that relies on its own quoting is unsafe unless the whole remote command is
wrapped in a single quoted string.** Every reading in this repo that worked
used the wrapped form; this poll did not, and it is the same family as the
`--es` semicolon trap already recorded.

Cost: one comparison point. Row 6 runs after the 25-minute reading and is
unaffected.

### 6. NOTHING HAS READ THE `.gguf` ON THIS BOOT — WHAT THAT RESTS ON

Three checks, not an assertion:

    $ adb shell ps -A | grep -E "pennyload|llama|sha256"
    (exit 1 -- no matching process)

    $ adb shell ls -la /data/local/tmp/out/ | grep -c q35
    0

and the only `sha256sum` of the model this session ran was at wall-clock
10:17:25, **before** the `adb reboot` at 10:18:14, so it warmed a page cache
the power cycle then threw away. No row, smoke run or hash has touched the file
since 10:18:59.

What this does NOT establish: `ps` is a point-in-time sample, so it proves no
such process is running now rather than that none ever ran. The `out/` count
and the reboot ordering are what carry the claim.

### 7. MemTotal DIFFERS FROM CLAUDE.md BY 4 kB, AND BOTH FIGURES ARE READ

    16 Sept and earlier, in CLAUDE.md   MemTotal 5,718,280 kB
    18 Sept 10:15:57, uptime 77,252 s   MemTotal 5,718,280 kB   (read)
    18 Sept 10:27:07, uptime    512 s   MemTotal 5,718,284 kB   (read)

**Both are read off `/proc/meminfo`, neither is typed from memory**, and the
two appear in the quoted invocations above. The 4 kB appeared across this
reboot. Nothing here explains it and no conclusion is drawn from it; it is
recorded so that a later reading of 5,718,284 is not mistaken for a
transcription error in CLAUDE.md.

### WHAT THIS ENTRY DOES NOT SAY

**Nothing was measured.** No row has run on this boot, `out/` contains no `q35`
file, and A6 and B6 are exactly as unanswered as they were on 16 Sept.

The model swap's own commands are not in the record and cannot be recovered —
only their effect. The cold-read band of 610 MB/s to 1.22 GB/s is one
observation at one-second resolution on a 21.5-hour boot, not a storage figure.
The 8.55-minute reading is not a 5-minute reading and is not treated as one.
Whether this boot behaves like 16 Sept boot 1 is not claimed: its `Cached` at
connect was ~590 MB higher, and the only later reading is at a mark boot 1 has
no counterpart for.

## 2026-09-18 — AMENDMENT to the entry above, before row 6 runs. The transcript was NOT gone, so the swap's own commands ARE quotable; the cold-read floor was stated with a lower bound it does not have; and MemTotal's 4 kB is not new.

Appended rather than edited into the entry above, per this repo's rule that a
wrong claim and its correction are both history. Four corrections, three of
them Matt's and the fourth found while checking the first.

### 1. THE 16 SEPT TRANSCRIPT IS ON DISK. THE ENTRY ABOVE WAS WRONG TO SAY OTHERWISE.

The entry says "the scrollback that held it is gone" and that the `rm`/`rm`/
`push` "cannot be quoted". **Both statements are false.** Matt asked where the
879,432 kB at 34.20 s figure had come from if the session was gone, which is
the question that broke it open: that figure had been pasted into this session
in Matt's own opening message, i.e. it was second-hand and relayed, not read.
Checking for its source found the session's full transcript at

    ~/.claude/projects/-Users-mattstevenson-Documents-penny-app-spike/
      0705355e-8fa1-43e3-a39c-815af692f53c.jsonl

**The correct provenance of 879,432 kB at 34.20 s: it is READ, not remembered.**
It was relayed through Matt's message, and is now confirmed against the
transcript's own tool output, which is the 16 Sept boot-3 connect reading and
is quoted in full below.

**THE DELETION, 16 Sept, boot 2, quoted from the transcript:**

    $ adb shell 'echo "uptime_s=$(cut -d\  -f1 /proc/uptime) wallclock=$(date +%H:%M:%S)"; rm -f /data/local/tmp/Qwen3-1.7B-Q4_K_M.gguf /data/local/tmp/q17_state.bin; echo "--- after delete ---"; ls -la /data/local/tmp/Qwen3-1.7B-Q4_K_M.gguf /data/local/tmp/q17_state.bin 2>&1; echo "--- dir ---"; ls -la /data/local/tmp/; df -h /data | tail -1'
    uptime_s=1771.76 wallclock=18:36:16
    --- after delete ---
    ls: /data/local/tmp/Qwen3-1.7B-Q4_K_M.gguf: No such file or directory
    ls: /data/local/tmp/q17_state.bin: No such file or directory
    --- dir ---
    total 12101
    drwxrwx--x 4 shell shell    3452 2026-09-16 18:36 .
    drwxr-x--x 5 root  root     3452 2026-09-13 21:07 ..
    -rwxr-xr-x 1 shell shell 4708216 2026-09-16 12:08 llama-bench
    -rwxr-xr-x 1 shell shell 3805208 2026-09-16 12:18 llama-simple
    drwxrwxrwx 4 shell shell    3452 2026-09-14 11:35 microdroid
    drwxrwxrwx 2 shell shell    8192 2026-09-16 18:32 out
    -rw-rw-rw- 1 shell shell    1911 2026-09-16 16:48 penny_system.txt
    -rw-rw-rw- 1 shell shell      95 2026-09-16 17:05 penny_user.txt
    -rwxr-xr-x 1 shell shell    8402 2026-09-16 16:57 pennybench.sh
    -rwxr-xr-x 1 shell shell 3817808 2026-09-16 16:29 pennyload
    /dev/block/dm-15 110G 8.0G  102G   8% /data/user/0

Both files gone, confirmed by `ls` failing on each by name, and 102G free.

**THE PUSH, same session, next command:**

    $ cd ~/Documents/penny-models && ls -la Qwen3.5-2B-Q4_K_M.gguf && grep -i "Qwen3.5-2B-Q4_K_M" MANIFEST.txt && echo "=== push ===" && time adb push Qwen3.5-2B-Q4_K_M.gguf /data/local/tmp/ && echo "=== hash on phone ===" && adb shell 'sha256sum /data/local/tmp/Qwen3.5-2B-Q4_K_M.gguf; ls -la /data/local/tmp/Qwen3.5-2B-Q4_K_M.gguf'
    -rw-r--r--@ 1 mattstevenson  staff  1280835840 15 Sep 20:26 Qwen3.5-2B-Q4_K_M.gguf
    unsloth/Qwen3.5-2B-GGUF                  Qwen3.5-2B-Q4_K_M.gguf                 1280835840  aaf42c8b7c3cab2bf3d69c355048d4a0ee9973d48f16c731c0520ee914699223 VERIFIED vs HF LFS oid
    === push ===
    Qwen3.5-2B-Q4_K_M.gguf: 1 file pushed, 0 skipped. 31.4 MB/s (1280835840 bytes in 38.886s)
    adb push Qwen3.5-2B-Q4_K_M.gguf /data/local/tmp/  3.05s user 0.98s system 10% cpu 39.038 total
    === hash on phone ===
    aaf42c8b7c3cab2bf3d69c355048d4a0ee9973d48f16c731c0520ee914699223  /data/local/tmp/Qwen3.5-2B-Q4_K_M.gguf
    -rw-rw-rw- 1 shell shell 1280835840 2026-09-15 20:26 /data/local/tmp/Qwen3.5-2B-Q4_K_M.gguf

**So the file was hashed on the phone on 16 Sept as well as on 18 Sept, and
both reads return `aaf42c8b…`.** The push ran at 31.4 MB/s over USB, 1.28 GB in
38.886 s — which is the transfer figure the entry above said did not exist, and
it is a USB figure, not a storage one.

**THE 16 SEPT BOOT 3 READING, quoted in full rather than relayed:**

    $ adb shell 'echo "uptime_s=$(cut -d\  -f1 /proc/uptime) wallclock=$(date +%H:%M:%S)"; grep -E "^(MemTotal|MemAvailable|MemFree|SwapFree|SwapTotal|Cached):" /proc/meminfo; echo "ceil_x1_kHz=..."; ...'
    uptime_s=34.20 wallclock=18:38:04
    MemTotal:        5718284 kB
    MemFree:          144188 kB
    MemAvailable:     794512 kB
    Cached:           879432 kB
    SwapTotal:       3145724 kB
    SwapFree:        2709244 kB
    ceil_x1_kHz=2802000 ceil_a76_kHz=2253000 ceil_a55_kHz=1803000
    --- vm list ---
    Running VMs: []
    --- app ---
    enabled=3
    --- model, ls only ---
    -rw-rw-rw- 1 shell shell 1280835840 2026-09-15 20:26 /data/local/tmp/Qwen3.5-2B-Q4_K_M.gguf

and the reboot that opened it, one invocation:

    uptime_s=1825.28 wallclock=18:37:10
    MemAvailable:    2652636 kB
    Cached:          1839580 kB
    SwapFree:         759804 kB
    === reboot ===
    adb reboot issued, rc=0

**THE METHOD POINT, which is the part worth keeping: an interrupted session's
tool output survives in `~/.claude/projects/<project>/<session-uuid>.jsonl` and
is recoverable.** "The scrollback is gone" was assumed, not checked, and it was
asserted in a committed entry. A figure relayed through a message is
second-hand until it is matched against that file. Check before writing that
something cannot be quoted.

### 2. THE COLD-READ FLOOR HAS NO UPPER BOUND, AND THE UNITS WERE WRONG

The entry above says "between 1 and 2 seconds, i.e. 610 MB/s to 1.22 GB/s".
**Two wall-clock stamps at one-second resolution, `start 10:17:25` and
`end 10:17:26`, bound the elapsed time at 0 to 2 seconds, not 1 to 2** — the
two stamps can fall either side of a single tick with almost no time between
them. The band's upper end was invented by assuming a full second had passed.

Corrected: 1,250,816 kB is 1,221.5 MiB, and over the 2-second worst case that
is **at least ~610 MiB/s, with NO UPPER BOUND OBTAINABLE FROM THIS METHOD.**
Also MiB/s, not MB/s: the figure is computed from kB read out of `/proc/meminfo`
and divided by 1024, so it is binary throughout and was mislabelled decimal.

The comparison to rungs 3e-iii and 3e-iv's 835 and 598 MB/s is withdrawn from
this entry — those were measured inside a VM by a payload that timed its own
read, which is a different instrument, and a floor cannot be compared with two
point figures anyway.

### 3. "NOTHING OF OURS IS IN IT" IS NOT CHECKABLE

Section 3 above says of the 1,461,544 kB of `Cached` at 24.80 s that "nothing
of ours is in it". **Nothing was run to establish what that cache contains**,
and there is no per-file page-cache readout in this repo's toolkit.

The claim it should have made, which the evidence in section 6 does support:
**nothing of ours has RUN on this boot.** No `pennyload`, `llama-bench` or
`sha256sum` process, no `q35` file in `out/`, and the only hash of the model ran
before the reboot. What is in that 1.46 GB is unknown and stays unknown.

### 4. MemTotal's 4 kB IS NOT NEW TO THIS REBOOT — found while checking the above

Section 7 says the 4 kB difference "appeared across this reboot". **It did
not.** The 16 Sept boot-3 reading quoted in section 1 of this amendment already
reads `MemTotal: 5718284 kB`, two days earlier. Counting every `MemTotal` line
in this project's transcripts gives **three distinct host values**:

    $ grep -ho "MemTotal:  *[0-9]* kB" *.jsonl | sort | uniq -c | sort -rn
      136 MemTotal: 5718280 kB
       56 MemTotal:        5718280 kB
       39 MemTotal:        5718284 kB
       22 MemTotal: 5718284 kB
        4 MemTotal:        5718276 kB
    (the 2038164 / 2038100 / 239796 / 239732 lines are guest kernels, not the host)

**5,718,276, 5,718,280 and 5,718,284 kB have all been read off this handset** —
a spread of 8 kB, 0.00014% of total. So CLAUDE.md's 5,718,280 is one of three
observed values rather than a figure that has since changed, and a reading of
5,718,284 is neither new nor a transcription error. Nothing here explains why
it varies between boots and nothing is concluded from it.

### WHAT THIS AMENDMENT DOES NOT SAY

It does not change a single measured figure, because the entry above contains
none. Row 6 has still not run.

The transcript recovery establishes what the commands were and what they
printed; it does not make them this session's commands, and the entry above
stays correct that this session did not run them. The 31.4 MB/s push is one
USB transfer, once, and is not a storage or a link figure. And the three
MemTotal values are counted across transcripts of varying age — the count says
they were all read at some point, not which boot each belongs to.

## 2026-09-18 — ROW 6, `q35_r6_cold_fresh`. Qwen3.5-2B-Q4_K_M's cold load on boot 3. **A6 PASSES at 4.666 s**, and the 2B costs 1.74 GiB and three cached processes.

First row on Qwen3.5-2B. Gated, cold page cache, the first run of the boot —
nothing had read the `.gguf` since power-on, on the evidence in the boot-3 entry
above. Every figure below is read from `out/q35_r6_cold_fresh.report` and
`.bench` on the phone, not from scrollback.

**THE GATE AND THE ROW, one shell invocation**, the same form rows 1-5 used:

    while [ policy6 != 2802000 ] || [ policy4 != 2253000 ]; do sleep 5; done
    PENNYBIN=/data/local/tmp/pennyload ./pennybench.sh q35_r6_cold_fresh c0 -- \
      -m /data/local/tmp/Qwen3.5-2B-Q4_K_M.gguf -t 2 -lm none -n 64 --print \
      --sys-file /data/local/tmp/penny_system.txt \
      --user-file /data/local/tmp/penny_user.txt --tag r6

    rc=0   mask=c0   uptime before 1525.63 s   after 1544.62 s   (18.99 s)

### THE 2B TOKENISES THE SAME TWO FILES DIFFERENTLY, AND IT MATTERS

    PENNYLOAD sys_file=...penny_system.txt sys_bytes=1911 sys_tokens=413 sys_add_special=1 sys_parse_special=1
    PENNYLOAD user_file=...penny_user.txt  user_bytes=95  user_tokens=21  user_add_special=0 user_parse_special=1

**413 and 21, against Qwen3-1.7B's 407 and 20 on the identical files.** Same
bytes, different vocabulary, so every per-token figure below is over 434 tokens
where rows 1-5 were over 427. `chat_template=NONE` on both, so nothing is
inserted around them.

### EVERY PHASE LINE, AS READ

    PENNYLOAD tag=r6 run_type=fresh rc=0
    PENNYLOAD model=/data/local/tmp/Qwen3.5-2B-Q4_K_M.gguf model_bytes=1280835840
    PENNYLOAD threads=2 n_ctx=1024 n_batch=2048 n_ubatch=512 n_gpu_layers=99
    PENNYLOAD load_mode=none extra_bufts=1 sampler=greedy
    PENNYLOAD chat_template=NONE
    PENNYLOAD progress_calls=322
    PENNYLOAD t_backend_ms      2.62      (T1-T0)
    PENNYLOAD t_model_open_ms   769.44    (T2-T1,  header+hparams+vocab+alloc)
    PENNYLOAD t_tensor_band_ms  3863.26   (T3-T2,  tensor data read + repack)
    PENNYLOAD t_model_tail_ms   5.95      (T4-T3)
    PENNYLOAD t_model_total_ms  4638.66   (T4-T1)
    PENNYLOAD t_ctx_create_ms   24.32     (T5-T4)
    PENNYLOAD t_ready_ms        4665.60   (T5-T0)  == **A6**
    PENNYLOAD t_tokenize_ms     3.39      (T6-T5)
    PENNYLOAD t_state_load_ms   n/a       (fresh run)
    PENNYLOAD t_sys_decode_ms   7713.13   (T9a-T6, 413 tokens, one llama_decode)
    PENNYLOAD t_state_save_ms   n/a       (no --save-state)
    PENNYLOAD state_file=(none) state_bytes=-1
    PENNYLOAD t_user_decode_ms  454.53    (T9b-T9a, 21 tokens)
    PENNYLOAD t_sample_ms       1.29      (T10-T9b)
    PENNYLOAD ttft_fresh_ms     8168.95   (T10-T6)
    PENNYLOAD ttft_cached_ms    8172.34   (T10-T5)
    PENNYLOAD ttft_cold_proc_ms 12837.94  (T10-T0)
    PENNYLOAD gen_tokens=64 gen_ms=5108.18 gen_tps=12.33
    PENNYLOAD first_token_id=3710
    PENNYLOAD token_fnv1a64=0x19d53b5da9186ff6

**`ttft_cold_proc_ms` IS LABELLED `== B3` BY THE TOOL AND IS NOT B3.** The
label is printed on every run. B3 is the cached-prefix wake, and this row is
fresh — it decodes the system prompt rather than restoring it. The hybrid's B3
would be row 10 on a fourth boot, which is undecided. Row 1 carried the same
label and the same correction; it is repeated because the line is in the file.

### A6, SCORED

    #   prediction                        point   band        measured    verdict
    A6  Qwen3.5-2B cold load -> ready      4.9 s  3.5-7.0 s   4.6656 s    **PASS**

234 ms below the point, comfortably inside the band, on the first and only
sample.

**THE LOAD SCALES WITH THE FILE, ALMOST EXACTLY.** Against row 1's cold load of
Qwen3-1.7B on 16 Sept boot 1 — a different boot, so this is a comparison and
not an error bar:

    t_ready_ms        4021.78 -> 4665.60   +643.82 ms   +16.01%
    model bytes    1,107,409,472 -> 1,280,835,840      +15.66%

**16.01% more time for 15.66% more bytes.** That the two agree to within a
third of a percentage point is the kind of coincidence one sample cannot
distinguish from a law, and it is recorded as the former.

**BUT THE SPLIT INSIDE THE LOAD IS NOT PROPORTIONAL, AND THAT IS THE FINDING:**

    phase               row 1 (1.7B)   row 6 (2B)    delta
    t_model_open_ms          398.49       769.44    +370.95   **+93.09%**
    t_tensor_band_ms        3581.59      3863.26    +281.67      +7.86%
    t_ctx_create_ms           36.62        24.32     -12.30     -33.59%

**`t_model_open_ms` nearly DOUBLED while the tensor band grew 7.86%.** That
phase is header, hparams, vocabulary and allocation — not tensor bytes — so it
does not scale with file size, and the 2B's vocabulary is the obvious suspect
given it tokenises the same file to 413 tokens rather than 407. **Suspect, not
established: nothing here isolates it**, and `progress_calls` moved only
311->322. It is worth one cheap check on some later row and is not chased now.

`t_ctx_create_ms` FELL, on a 1024-token KV cache — the same phase whose 0.2-0.8 s
band A3 failed by an order of magnitude on the 1.7B. 24.32 ms here makes that
failure worse, not better, and on a second model.

### MEMORY, KILLS, AND THE CLOCK

    PENNYBENCH memavail_kB   before 2,270,852   after 2,740,524
    PENNYBENCH memfree_kB    before 1,536,804   after 1,918,048
    PENNYBENCH swapfree_kB   before   862,460   after   476,412    (15.14% of total)
    PENNYBENCH cached_kB     before   954,680   after 1,048,668    (**ROSE** 93,988)
    PENNYBENCH pswpin        before    34,617   after    35,382    (+765)
    PENNYBENCH pswpout       before   609,195   after   744,580    (+135,385)
    PENNYBENCH pgmajfault    before    47,572   after    48,426    (+854)
    PENNYBENCH ceil_x1_kHz   before 2,802,000   min 2,048,000   after 2,802,000
    PENNYBENCH ceil_x1_min_at uptime=1532.24    (7 s into the row, 73.09% of rated)
    PENNYBENCH ceil_a76_kHz  before 2,253,000   min 2,253,000   after 2,253,000
    PENNYBENCH ceil_a76_min_at uptime=1525.63   (0 s into the row -- never fell)
    PENNYBENCH peak_rss_kB   1,824,632   (VmHWM, monotonic)
    PENNYBENCH max_rssanon_kB 1,819,144  (99.70% -- NOT reclaimable)
    PENNYBENCH max_rssfile_kB     5,216
    PENNYBENCH rss_samples       46
    PENNYBENCH lmk_kill_lines     6

**PEAK RSS 1,824,632 kB = 1.740 GiB, 99.70% anonymous**, against the 1.7B's
1,541,140 kB on row 1 — **+283,492 kB, +18.39%** for +15.66% of file. It sits
exactly on the 1.69-1.74 GiB the 16 Sept `llama-bench` rows reported for this
model, which is a different instrument agreeing, and at `-lm none` every byte of
it is memory the phone has to find.

**SIX KILL LINES, THREE PROCESSES, AND `MemAvailable` IN FRONT OF THEM IS
2,270,852 kB:**

    com.android.DeviceAsWebcam   oom_score_adj 915   cch +15 CEM
    com.android.keychain         oom_score_adj 915   cch +15 CEM
    com.android.deskclock        oom_score_adj 905   cch CEM

All three `reason: low watermark is breached`, all cached, none in the
foreground band, our own process untouched. **Against row 1's four processes at
`MemAvailable` 2,277,952 kB — 7,100 kB apart, the closest two starting
conditions in this plan — the 2B at 1.74 GiB costs THREE where the 1.7B at
1.47 GiB cost FOUR.** Two of the three are the same processes row 1 took
(`DeviceAsWebcam`, `keychain`). So on this evidence the larger model did not
cost more kills; the killer takes what is cheapest and stops when the watermark
is met.

**THE CONTAMINATION RULE IS NOT MET.** `Cached` ROSE 93,988 kB rather than
falling by the model's size, and `SwapFree` ended at 476,412 kB — **15.14% of
`SwapTotal`, above the ~10% floor** but the lowest any row in this plan has
ended at. `pswpout` moved 135,385 pages. Worth watching on row 7.

**THE X1 PAIR FELL TO 73.09% OF RATED, 7 SECONDS IN, AND THE A76 PAIR NEVER
MOVED.** `ceil_x1` min 2,048,000 kHz is the same floor and the same timing row 1
recorded (2,048,000 at 10 s in), so the two models drive the limiter
comparably at `c0 -t 2`. Both clusters back at rated by the end of the row.
`policy0` was not sampled.

### BUFFER LINES

The `.report` carries only the tail of the loader's stderr, and this is it
verbatim:

    sched_reserve:        CPU compute buffer size =   498.02 MiB
    sched_reserve: graph nodes  = 1411
    sched_reserve: graph splits = 1
    sched_reserve: reserve took 11.98 ms, sched copies = 1
    ~llama_context:        CPU compute buffer size is 498.0196 MiB, matches expectation of 498.0196 MiB

**The per-tensor and model-buffer lines are NOT in the `.report`** — they are in
`q35_r6_cold_fresh.err`, which was not read, so no accounting of the 1.74 GiB
against llama.cpp's own buffer figures is attempted here. The 498.02 MiB compute
buffer is the one number available and it is stated alone.

`ZRAM: 580,400K physical used for 2,397,184K in swap (3,145,724K total swap)` —
4.13:1, and swap on this handset is zram, as established 16 Sept.

### THE TEXT

64 tokens, `first_token_id=3710`, `token_fnv1a64=0x19d53b5da9186ff6`. The model
echoes the question and opens a `<think>` block:

    What is the capital of Australia, roughly how many people live there, and
    when was it founded?

    <think>
    Thinking Process:

    1.  **Analyze the Request:**
        *   **Persona:** Penny, a voice assistant running entirely on this phone.

**64 tokens is not enough to reach an answer** — it is still planning when the
budget runs out. Recorded as text produced and nothing more. **No quality
judgement**, and note the 1.7B's answer got two of its three facts wrong.

`gen_tps=12.33` is 63 decodes over 5108.18 ms, the same divisor row 1 documented
(`pennyload.cpp:390`), against the 1.7B's 15.04.

### WHAT ROW 6 DOES NOT SAY

**One sample, no error bar, and the comparisons to row 1 cross two boots.** Row
1 ran on 16 Sept boot 1; this is 18 Sept boot 3, whose `Cached` at connect was
~590 MB higher and which has no ~5-minute reading. The 16.01%/15.66% agreement
and the "three kills against four" are observations across that gap, not
controlled pairs.

**B6 IS NOT SCORED HERE and the hybrid's state path is still untested.** Row 6
carries no `--save-state`; whether `llama_state_save_file` succeeds on a model
with eighteen recurrent layers is row 7's question, and it can still fail
outright rather than return a number.

Nothing sustained: 18.99 seconds of wall time, the X1 ceiling still at 73.09% of
rated when the row ended. AC power, screen on, unlocked, foreground, over adb,
app disabled, no VM. `t_model_open_ms` doubling is attributed to nothing — the
vocabulary is a suspect and was not tested. And `ttft_cold_proc_ms` 12,837.94 ms
is a fresh cold process, not a wake figure.

## 2026-09-18 — AMENDMENT to row 6, before row 7. The 25-minute reading was taken and never written down; the two buffer lines do NOT sum to the file; and two sentences are withdrawn.

Appended, not edited in. No model read: everything below comes from
`q35_r6_cold_fresh.err`, already on the phone, and from a reading taken before
the row.

### 1. THE 25-MINUTE PROTOCOL READING — TAKEN, AND MISSING FROM THE RECORD UNTIL NOW

**The poll DID fire.** The boot-3 entry was committed at 10:30 and the poll
fired at 10:43, so the reading existed in the operator's terminal and never
reached `notes.md` — the exact failure that entry was written to prevent. It
is the second reading of this boot and it belongs beside the 8.55-minute one:

    $ adb shell 'echo "uptime_s=$(cut -d\  -f1 /proc/uptime)  wallclock=$(date +%H:%M:%S)"; grep -E "^(MemTotal|MemAvailable|MemFree|SwapFree|Cached):" /proc/meminfo; echo "ceil_x1=$(cat .../policy6/scaling_max_freq)  ceil_a76=$(cat .../policy4/scaling_max_freq)  ceil_a55=$(cat .../policy0/scaling_max_freq)"; echo "--- presence, ls only, no read ---"; ls -la /data/local/tmp/Qwen3.5-2B-Q4_K_M.gguf; /apex/com.android.virt/bin/vm list'
    uptime_s=1503.14  wallclock=10:43:38
    MemTotal:        5718284 kB
    MemFree:         1550512 kB
    MemAvailable:    2273652 kB
    Cached:           946808 kB
    SwapFree:         862460 kB
    ceil_x1=2802000  ceil_a76=2253000  ceil_a55=1803000
    --- presence, ls only, no read ---
    -rw-rw-rw- 1 shell shell 1280835840 2026-09-15 20:26 /data/local/tmp/Qwen3.5-2B-Q4_K_M.gguf
    Running VMs: []

**So row 6 did NOT gate straight in from nowhere.** The reading is at
uptime 1503.14 s and the row's own `before` figures are at 1525.63 s, 22.49 s
later — `MemAvailable` 2,273,652 then 2,270,852 kB, `SwapFree` 862,460 kB in
both, so the phone did not move between them. All three ceilings rated at the
reading and the gate therefore passed without blocking.

**Boot 3's protocol readings, complete, both labelled by their real marks:**

    uptime s   wallclock   MemAvailable   MemFree     Cached     SwapFree
      24.80    10:18:59      1,324,400     86,676   1,461,544   3,118,588   (connect)
     512.80    10:27:07      2,310,624  1,596,300     940,392     847,612   (8.55 min)
    1503.14    10:43:38      2,273,652  1,550,512     946,808     862,460   (25.05 min)

The 8.55-minute and 25.05-minute figures are 36,972 kB apart on `MemAvailable`
— 1.6% — so this boot's curve is flat across that window.

### 2. THE BUFFER LINES, AND THEY DO NOT SUM TO THE FILE

    $ adb shell 'grep -i "model buffer size" .../q35_r6_cold_fresh.err'
    load_tensors:          CPU model buffer size =   399.94 MiB
    load_tensors:   CPU_REPACK model buffer size =  1208.95 MiB

    CPU + CPU_REPACK   = 1608.89 MiB
    the .gguf itself   = 1221.50 MiB   (1,280,835,840 B)
    difference         = **+387.39 MiB, i.e. the buffers EXCEED the file by 31.7%**

**They do NOT sum to the file, and the excess is the answer to what the repack
costs in memory rather than in time.** Row 4 measured the repack's TIME on the
1.7B by subtraction; this is the first figure in this repo for its SPACE, and it
is 387.39 MiB on a 1,221.5 MiB model. Not explained here beyond the arithmetic.

**THE REPACK, COUNTED BY KERNEL** — 187 tensors, four target formats:

    $ adb shell 'grep "repack tensor" .../q35_r6_cold_fresh.err | sed "s/.*with //" | sort | uniq -c'
         98 q4_K_8x4
         36 q5_K_8x4
         17 q6_K_8x4
         36 q8_0_4x4
        187 total

Sample line, verbatim: `repack: repack tensor blk.0.ffn_down.weight with q6_K_8x4`.
**Four kernels, not one** — a Q4_K_M file is not uniformly Q4_K, and 36 tensors
repack to `q8_0_4x4`, which is the heaviest of the four per weight. The `8x4`
and `4x4` suffixes are the ARM dot-product repack layouts this build was
compiled for.

**AGAINST PEAK RSS, and this does not close either:**

    peak RSS                       1781.87 MiB   (1,824,632 kB)
    CPU + CPU_REPACK buffers       1608.89 MiB   -> peak is 172.98 MiB ABOVE
    those plus compute (498.02)    2106.91 MiB   -> peak is 325.04 MiB BELOW

So peak RSS sits between the model buffers and the model buffers plus the
declared compute buffer. **The 498.02 MiB compute buffer was therefore not
fully resident at peak** — declared and allocated is not touched. Stated as
arithmetic; no mechanism is claimed, and nothing here apportions the 172.98 MiB.

### 3. TWO SENTENCES IN THE ROW 6 ENTRY ARE WITHDRAWN

**(a)** "It sits exactly on the 1.69-1.74 GiB the 16 Sept `llama-bench` rows
reported for this model" — **withdrawn.** "Exactly" is an adjective on a
measurement and the range is not a point. Replaced by the plain comparison:
**row 6's peak RSS is 1,824,632 kB = 1.740 GiB; the 16 Sept `llama-bench` rows
for Qwen3.5-2B reported 1.69-1.74 GiB. The figure falls at the top of that
range.** The two were produced by different binaries under different flags and
neither is a check on the other.

**(b)** "the killer takes what is cheapest and stops when the watermark is met"
— **withdrawn in full.** That is a mechanism, and nothing in row 6 tests it.
What stays is the observation, unchanged: **three cached processes at
`MemAvailable` 2,270,852 kB for the 2B at 1.74 GiB, against row 1's four at
2,277,952 kB for the 1.7B at 1.47 GiB, two of the three being processes row 1
also took.** Why is not addressed.

### WHAT THIS AMENDMENT DOES NOT SAY

A6's pass is untouched; no measured figure changes. The 387.39 MiB of repack
excess is one model, one row, and is not compared with the 1.7B — that would
need the same grep over `q17_r1_cold_fresh.err`, which was not run. The kernel
counts are line counts from a log, not bytes: **nothing here says how the
387.39 MiB divides between the four formats**, and the 36 `q8_0_4x4` tensors are
flagged as the heaviest per weight without their size being measured.

## 2026-09-18 — ROW 7, `q35_r7_warm_fresh_save`. **THE HYBRID'S PREFIX CAN BE CACHED**, and its state file is 24.12 MiB — 45.83% SMALLER than the 1.7B's. B6 PASSES on both its number and its direction. The A5-equivalent FAILS, and the page cache is why.

The row that could have failed on a code path rather than a number. It did not.

    PENNYLOAD tag=r7 run_type=fresh+save rc=0
    PENNYLOAD state_file=/data/local/tmp/q35_state.bin state_bytes=25288618 state_tokens_saved=413
    $ adb shell ls -la /data/local/tmp/q35_state.bin
    -rw-rw-rw- 1 shell shell 25288618 2026-09-18 11:35 /data/local/tmp/q35_state.bin

**`llama_state_save_file` returned TRUE on a model whose eighteen of twenty-four
layers are recurrent.** No `FAILED state_save=false`, no rc=3. The plan's
standing "whole of Q-B fails on Qwen3.5-2B" condition is not met, and row 8 is
live. The loader's own stderr shows the write happening:

    state_write_data: writing state
    state_write_data: - writing model info
    state_write_data: - writing memory module

**A DECLARED WARMTH PROBLEM, WRITTEN DOWN BEFORE THE ROW WAS LAUNCHED.** Row 2
followed row 1 by 236 s; row 7 followed row 6 by **50 minutes**, spent writing
up two amendments. `Cached` read **1,122,276 kB at uptime 4576.83 s against a
model of 1,250,816 kB**, so the file could not be fully resident, and the row
was launched anyway with the warmth to be read off its own tensor band rather
than assumed. **It was not warm, and the A5-equivalent fails because of it.**

### THE GATE AND THE ROW, one shell invocation

    GATE PASSED uptime_s=4605.32 wallclock=11:35:20
    PENNYBIN=/data/local/tmp/pennyload ./pennybench.sh q35_r7_warm_fresh_save c0 -- \
      -m /data/local/tmp/Qwen3.5-2B-Q4_K_M.gguf -t 2 -lm none -n 64 --print \
      --sys-file /data/local/tmp/penny_system.txt \
      --user-file /data/local/tmp/penny_user.txt \
      --save-state /data/local/tmp/q35_state.bin --tag r7

    rc=0   mask=c0   uptime before 4605.42 s   after 4624.26 s   (18.84 s)
    sys_tokens=413  user_tokens=21   (unchanged from row 6)

### EVERY PHASE LINE, AS READ

    PENNYLOAD t_backend_ms      8.30
    PENNYLOAD t_model_open_ms   758.83
    PENNYLOAD t_tensor_band_ms  3663.29
    PENNYLOAD t_model_tail_ms   7.68
    PENNYLOAD t_model_total_ms  4429.79
    PENNYLOAD t_ctx_create_ms   30.08
    PENNYLOAD t_ready_ms        4468.17
    PENNYLOAD t_tokenize_ms     4.67
    PENNYLOAD t_sys_decode_ms   7879.20   (413 tokens)
    PENNYLOAD t_state_save_ms   16.87     (T8-T9a)  == B4-equivalent
    PENNYLOAD state_bytes=25288618 state_tokens_saved=413
    PENNYLOAD t_user_decode_ms  498.09    (T9b-T8 -- AFTER the save)
    PENNYLOAD t_sample_ms       3.54
    PENNYLOAD ttft_fresh_ms     8397.71
    PENNYLOAD ttft_cached_ms    8402.38
    PENNYLOAD ttft_cold_proc_ms 12870.55
    PENNYLOAD gen_tokens=64 gen_ms=5143.28 gen_tps=12.25
    PENNYLOAD first_token_id=3710
    PENNYLOAD token_fnv1a64=0x19d53b5da9186ff6

`ttft_fresh_ms` carries the save inside it and is not a clean TTFT, the same
note row 2 carries. `ttft_cold_proc_ms` is again labelled `== B3` by the tool
and is again NOT B3 — this row is fresh.

### B6, SCORED — AND THE DIRECTIONAL CLAIM IS THE RESULT

    #   prediction                              point      band        measured      verdict
    B6  state file size, Qwen3.5-2B, ~420 tok   ~25 MiB   10-60 MiB   24.117 MiB   **PASS**

    #   B4-equivalent (not a re-score of B4)
        state save cost                         0.25 s    0.05-1.0 s   0.01687 s    inside

**AND THE FAIL CONDITION WRITTEN BEFORE ANY RUN — "B6 fails if the Qwen3.5-2B
file is LARGER than the Qwen3-1.7B one, which is the directional claim and the
one worth being wrong about" — IS NOT MET. IT IS SMALLER:**

    Qwen3-1.7B   46,685,237 B = 44.523 MiB   over 407 tokens = 112.02 KiB/token
    Qwen3.5-2B   25,288,618 B = 24.117 MiB   over 413 tokens =  59.80 KiB/token
    the 2B's file is **54.17% of the 1.7B's — smaller by 45.83%** — on a model
    16% LARGER

**A bigger model with a cheaper prefix.** The prediction's stated reason was six
attention layers at 2 KV heads costing ~12 KiB a token against the 1.7B's
112 KiB. Taking that 12 KiB at face value, 413 tokens of attention would be
4.84 MiB, leaving **19.28 MiB that is not token-proportional** — the fixed
recurrent state. **That split is arithmetic on an ASSUMED 12 KiB, not a
measurement**: nothing here reads the file's internal structure, and the 12 KiB
came from the prediction rather than from the model. The claim it does support,
because it needs no assumption, is the observed 59.80 against 112.02 KiB/token.

**THE CONSEQUENCE, IF THE FIXED PART IS REALLY FIXED, IS THAT THE HYBRID'S
PREFIX FILE BARELY GROWS WITH THE PROMPT — AND THAT IS UNTESTED.** It needs one
run at a different prompt length and has not been done.

### THE A5-EQUIVALENT FAILS HIGH, AND THE CACHE IS MEASURABLY THE REASON

    warm t_ready 4468.17 / cold t_ready 4665.60 = **95.77%**
    fail condition (written before any run): FAILS if >= 90% or <= 40%
    -> **FAIL, HIGH**

For comparison the 1.7B's own pair, row 2 against row 1: **70.61%, a pass.**

**The tensor band says plainly that this row was not warm:**

    2B   row 7 3663.29 vs row 6 3863.26  = 94.82%   only  199.97 ms saved
    1.7B row 2 2431.20 vs row 1 3581.59  = 67.88%        1150.39 ms saved

A genuinely warm load of the 1.7B saved 1.15 seconds off the band. This saved
0.20 s. **So the A5-equivalent's failure is a statement about the page cache on
this row, not about the repack-versus-read split**, and it must not be quoted as
evidence against A5's directional claim. The claim is untested on the 2B, and
testing it needs row 7 re-run within a minute or two of a cold load — which
costs a reboot, because this boot's cold read is spent.

**This is the cost of taking 50 minutes between two rows that were designed to
be seconds apart**, and it was foreseen and recorded before the launch rather
than discovered afterwards.

### THE CONTROL HOLDS

    row 6   first_token_id=3710   token_fnv1a64=0x19d53b5da9186ff6
    row 7   first_token_id=3710   token_fnv1a64=0x19d53b5da9186ff6   **MATCH**

Same 64 tokens from a fresh load with a state save bolted on. That is the
reference rows 8 and 10 must reproduce.

### MEMORY, KILLS AND THE CLOCK

    PENNYBENCH memavail_kB   before 2,636,476   after 3,106,172
    PENNYBENCH memfree_kB    before 1,738,304   after 1,936,628
    PENNYBENCH swapfree_kB   before   666,364   after   612,692   (19.48% of total)
    PENNYBENCH cached_kB     before 1,122,352   after 1,394,936   (**ROSE** 272,584)
    PENNYBENCH pswpin        before   128,227   after   128,675   (+448)
    PENNYBENCH pswpout       before   793,604   after   905,248   (+111,644)
    PENNYBENCH pgmajfault    before   141,836   after   142,445   (+609)
    PENNYBENCH ceil_x1_kHz   before 2,802,000   min 1,826,000   after 2,802,000
    PENNYBENCH ceil_x1_min_at uptime=4611.88    (6 s into the row, **65.17%** of rated)
    PENNYBENCH ceil_a76_kHz  before 2,253,000   min 2,253,000   after 2,253,000
    PENNYBENCH peak_rss_kB   1,824,196
    PENNYBENCH max_rssanon_kB 1,819,144
    PENNYBENCH max_rssfile_kB     4,796
    PENNYBENCH lmk_kill_lines    16

**PEAK RSS 1,824,196 kB against row 6's 1,824,632 — 436 kB apart, 0.024%.**
`max_rssanon_kB` is 1,819,144 in BOTH rows, to the kilobyte. The 24.12 MiB state
buffer does not show up in peak RSS, which is consistent with the save streaming
out rather than being assembled in memory, and is not proof of it.

**SIXTEEN KILL LINES, EIGHT PROCESSES, WITH `MemAvailable` BEFORE AT
2,636,476 kB — MORE MEMORY AND MORE KILLS THAN ROW 6, AND THAT COMPLICATES THE
RULE:**

    row 6   MemAvailable before 2,270,852 kB   ->  3 processes,  6 lines
    row 7   MemAvailable before 2,636,476 kB   ->  8 processes, 16 lines

    euiccpixel, traceur, carrierconfig2, .adservices, imsserviceentitlement,
    localtransport, backup.contacts, seedvault -- all oom_score_adj 905, all cch

**And the reason strings are more severe**: row 6's were all `low watermark is
breached`; row 7's include `min watermark is breached` and four of
`min watermark is breached even after kill`. **So `MemAvailable` before a row
did NOT predict its kills here, and the B2-R1 rule needs the caveat.** What
differed: this row started with `pswpin` at 128,227 against row 6's 34,617 and
`pgmajfault` at 141,836 against 47,572 — the phone had been swapping heavily in
the fifty minutes between them, on a boot now 77 minutes old. **That is a
correlation across two rows and nothing here establishes it as the cause.**

**Not contaminated:** `Cached` ROSE 272,584 kB, `SwapFree` ended at 19.48% of
`SwapTotal` — above the ~10% floor, and higher than row 6's 15.14%.

**The X1 ceiling fell further than row 6's** — 1,826,000 kHz, 65.17% of rated,
6 s in, against row 6's 2,048,000 (73.09%) at 7 s. The A76 pair never moved in
either. Both back at rated by the end. `policy0` not sampled.

`ZRAM: 560,168K physical used for 2,432,936K in swap` — 4.34:1.

### WHAT ROW 7 DOES NOT SAY

**It does not say a hybrid's prefix can be LOADED.** `llama_state_save_file`
succeeded; `llama_state_load_file` has not been called on this file, and it can
still return false. That is row 8, and until it runs, "the hybrid's prefix can be
cached" means only that a file was written.

**The A5-equivalent's failure is not a finding about the model.** It is a failure
on a row whose page cache did not hold the model, established by its own tensor
band, and the underlying repack-versus-read claim stays untested on the 2B.

B6 passes on one file at one prompt length. The 19.28 MiB "fixed" remainder rests
on a 12 KiB/token assumption taken from the prediction, not measured, and the
claim that the file barely grows with prompt length is untested.

One sample, 18.84 seconds, X1 at 65% of rated when it ended. AC power, screen
on, unlocked, foreground, over adb, app disabled, no VM. The kill comparison
crosses fifty minutes of a boot that was swapping throughout, and the text is
again 64 tokens that stop inside a `<think>` block, with no quality judgement
made.

## 2026-09-18 — ROW 8, `q35_r8_warm_cached`. **THE HYBRID'S PREFIX LOADS**: 413 tokens restored in 9.58 ms, and the B2-equivalent is 392.28 ms. Q-B is now answered on both models. Also corrects two phrases in row 7.

### CORRECTION TO ROW 7, first, because row 7 overstated what it had

**Row 7's heading says "THE HYBRID'S PREFIX CAN BE CACHED" and it had established
SAVE only — it should read "can be SAVED; load untested until row 8".**
`llama_state_save_file` returning true says a file was written, not that anything
can read it back, and row 8 is where that was settled. Second: row 7 says the
A5-equivalent's failure is because of the page cache — "the page cache is
measurably the reason" and "the page cache is why". **Both are withdrawn and
replaced by: the failure is CONSISTENT WITH a partially evicted model file, on
two pieces of evidence — the tensor band saved only 199.97 ms against the 1.7B
pair's 1150.39 ms, and `Cached` read 1,122,276 kB against a 1,250,816 kB model —
and is NOT ESTABLISHED.** Nothing isolated the cache as the cause. **The
A5-equivalent is not a boot-3 score** — the plan at notes.md 8938 scores A6, B6
and row 8's B2 on this boot and nothing else — **so no boot is spent chasing it.
It is left as untested on the 2B.**

### THE GATE AND THE ROW, one shell invocation, no write-up in between

    GATE PASSED uptime_s=5174.04 wallclock=11:44:49
    PENNYBIN=/data/local/tmp/pennyload ./pennybench.sh q35_r8_warm_cached c0 -- \
      -m /data/local/tmp/Qwen3.5-2B-Q4_K_M.gguf -t 2 -lm none -n 64 --print \
      --user-file /data/local/tmp/penny_user.txt \
      --load-state /data/local/tmp/q35_state.bin --tag r8

    rc=0   mask=c0   uptime before 5174.13 s   after 5184.01 s   (9.88 s)
    PENNYLOAD sys_file=(none) sys_bytes=-1 sys_tokens=-1
    PENNYLOAD user_file=...penny_user.txt user_bytes=95 user_tokens=21

No `--sys-file`: the 413-token prefix comes from the file, and the row proves it
by restoring exactly that many.

### THE LOAD SUCCEEDED

    PENNYLOAD tag=r8 run_type=cached rc=0
    PENNYLOAD t_state_load_ms   9.58   (T7-T6)
    PENNYLOAD state_file=/data/local/tmp/q35_state.bin state_bytes=25288618 state_tokens_restored=413

**`state_tokens_restored=413`, the figure row 7 saved.** No
`FAILED state_load=false`, no rc=3. From the loader's own stderr:

    state_read_data: reading state
    state_read_data: - reading model info
    state_read_data: - reading memory module

**24.117 MiB in 9.58 ms is ~2.46 GiB/s, warm** — the file was written nine
minutes earlier and was in the page cache. Against the 1.7B's row 3, also warm:
44.523 MiB in 19.17 ms, ~2.27 GiB/s. **Same order, so the hybrid's smaller state
is cheaper in wall time in proportion to its size and not otherwise.**

### EVERY PHASE LINE, AS READ

    PENNYLOAD t_backend_ms      2.65
    PENNYLOAD t_model_open_ms   758.47
    PENNYLOAD t_tensor_band_ms  2725.80
    PENNYLOAD t_model_tail_ms   6.61
    PENNYLOAD t_model_total_ms  3490.87
    PENNYLOAD t_ctx_create_ms   19.97
    PENNYLOAD t_ready_ms        3513.49
    PENNYLOAD t_tokenize_ms     0.32
    PENNYLOAD t_state_load_ms   9.58
    PENNYLOAD t_user_decode_ms  381.30   (21 tokens)
    PENNYLOAD t_sample_ms       1.08
    PENNYLOAD ttft_fresh_ms     391.96   (T10-T6)
    PENNYLOAD ttft_cached_ms    392.28   (T10-T5)  == **B2-equivalent**
    PENNYLOAD ttft_cold_proc_ms 3905.77  (T10-T0)
    PENNYLOAD gen_tokens=64 gen_ms=4979.73 gen_tps=12.65
    PENNYLOAD first_token_id=3710
    PENNYLOAD token_fnv1a64=0x19d53b5da9186ff6

### B2-EQUIVALENT, SCORED

    #   prediction                                     point    band         measured     verdict
    B2  TTFT cached, model resident, state + 20 tok     0.5 s   0.35-1.5 s   0.39228 s   **PASS**

    for comparison, Qwen3-1.7B:  row 3 (warm state file) 422.31 ms
                                 row 5 (cold state file) 406.21 ms

**392.28 ms on the 2B against 422.31 and 406.21 ms on the 1.7B — the bigger
model's cached first token is the fastest of the three.** The 413-token prefix
costs 9.58 ms to restore; the 21-token user turn costs 381.30 ms, which is 97.2%
of the whole figure. **The prefix is not where the time goes, on either model.**

**AGAINST THE SAME MODEL'S FRESH FIGURE ON THE PREVIOUS ROW: 8397.71 ms fresh
against 391.96 ms cached, 21.42x.** What the cache removes is row 7's 7879.20 ms
system-prompt decode.

**`ttft_cold_proc_ms` 3905.77 IS NOT B3 ON THIS BOOT AND MUST NOT BE QUOTED AS
ONE.** B3 is the cached wake from a COLD process — first run after a power cycle,
model unread. This row ran ninth-of-the-boot with the model already in cache
(see the tensor band below) and the state file nine minutes old. The hybrid's
true B3 is row 10 on a fourth boot, which is undecided; if that boot is never
spent, B3 for Qwen3.5-2B is reported NOT MEASURED, exactly as the plan says.

### THE LOAD WAS WARMER THAN ROW 7's, WHICH IS THE REVERSE OF THE INTENDED ORDER

    t_tensor_band_ms   row 6 (cold) 3863.26   row 7 3663.29   row 8 2725.80
    t_ready_ms         row 6 (cold) 4665.60   row 7 4468.17   row 8 3513.49

Row 8's band is **74.41% of row 7's and 70.56% of row 6's cold**; its `t_ready`
is **78.63% of row 7's and 75.31% of row 6's**. `Cached` before row 8 read
1,436,804 kB against the model's 1,250,816 kB — enough to hold it, where row 7's
1,122,352 kB was not. **So the warmest load of this boot is the third row, not
the second**, and row 8's `t_ready` is the closest thing this boot has to a
genuinely warm load. **It is NOT scored as the A5-equivalent**: A5 is defined as
a warm load immediately after a cold one, this is two rows later, and rescoring a
prediction against a row it was not defined on is exactly what the frozen-
prediction rule forbids.

### THE CONTROL HOLDS ON THE THIRD ROW RUNNING

    row 6 (fresh)         first_token_id=3710   token_fnv1a64=0x19d53b5da9186ff6
    row 7 (fresh + save)  first_token_id=3710   token_fnv1a64=0x19d53b5da9186ff6
    row 8 (state loaded)  first_token_id=3710   token_fnv1a64=0x19d53b5da9186ff6

**Identical 64 tokens from a decoded prefix and from a restored one.** That is
the result that makes the state file worth anything: what came back off disk
produces the same output as computing it. Same `token_ids` string in all three.

### MEMORY, KILLS AND THE CLOCK

    PENNYBENCH memavail_kB   before 3,061,764   after 3,037,604
    PENNYBENCH memfree_kB    before 1,849,364   after 1,890,232
    PENNYBENCH swapfree_kB   before   687,700   after   656,980   (**20.88%** of total)
    PENNYBENCH cached_kB     before 1,436,804   after 1,383,168   (FELL 53,636)
    PENNYBENCH pswpin        before   157,179   after   157,205   (+26)
    PENNYBENCH pswpout       before   915,380   after   923,084   (+7,704)
    PENNYBENCH pgmajfault    before   171,301   after   171,328   (+27)
    PENNYBENCH ceil_x1_kHz   before 2,802,000   min 2,252,000   after 2,802,000
    PENNYBENCH ceil_x1_min_at uptime=5181.07    (7 s into the row, **80.37%** of rated)
    PENNYBENCH ceil_a76_kHz  before 2,253,000   min 2,253,000   after 2,253,000
    PENNYBENCH peak_rss_kB   1,786,192   (1.703 GiB)
    PENNYBENCH max_rssanon_kB 1,781,044
    PENNYBENCH max_rssfile_kB     4,852
    PENNYBENCH rss_samples       24
    PENNYBENCH lmk_kill_lines     0

**ZERO KILLS — AND `MemAvailable` BEFORE IT IS 3,061,764 kB, THE HIGHEST OF ANY
ROW IN THIS PLAN. THE ZERO IS FLATTERED AND IS REPORTED AS SUCH.** This boot had
already killed eleven processes across rows 6 and 7, which is precisely the
condition the B2-R1 rule names: a boot that has bought itself headroom by killing
its own cheap processes. **"The cached path causes no kills" is NOT a claim this
row supports.**

**PEAK RSS 1,786,192 kB = 1.703 GiB, 38,004 kB BELOW row 7's** — the cached row
is the cheaper of the two in peak memory despite holding a restored 413-token
state, which is consistent with it never running the 413-token system-prompt
decode. Consistent with, not established: nothing here measures the decode's
transient separately.

**Not contaminated.** `Cached` fell 53,636 kB — not by anything near the model's
size — and `SwapFree` ended at 20.88% of `SwapTotal`, the highest of the three
rows on this boot (row 6 15.14%, row 7 19.48%), well above the ~10% floor.
`pgmajfault` moved 27 across the whole row, against row 6's 854.

**The X1 ceiling fell least of the three rows** — 2,252,000 kHz, 80.37% of rated,
7 s in, against row 7's 65.17% and row 6's 73.09%. The row is also the shortest,
at 9.88 s. The A76 pair never moved on any of the three. `policy0` not sampled.

`ZRAM: 559,572K physical used for 2,450,856K in swap` — 4.38:1.

### WHAT ROW 8 DOES NOT SAY

**It is not B3 and it is not a wake measurement.** Warm model, warm state file,
ninth row of a 86-minute boot. The hybrid's wake figure needs boot 4 and has not
been taken.

**The zero kills are flattered by eleven earlier kills on this boot** and say
nothing about what the cached path costs on a fresh one.

B2-equivalent is one sample at one prompt length, and the 21-token user turn is
97.2% of it — so the figure is mostly a measurement of decoding 21 tokens, and a
longer user turn moves it directly. The 21.42x against row 7's fresh TTFT is one
pair on one boot.

**Row 8 says nothing about whether the state file survives a reboot.** Rows 3 and
5 established that for the 1.7B; for the 2B the file has only been written and
read back nine minutes apart on the same boot, with no `sync` taken and no hash
recorded either side.

Nothing sustained: 9.88 seconds, AC power, screen on, unlocked, foreground, over
adb, app disabled, no VM. The text is again 64 tokens stopping inside a `<think>`
block, and no quality judgement is made of it.

## 2026-09-18 — ROW 9, `q35_r9_warm_nobufts`. The `--extra-bufts 0` CONTROL on Qwen3.5-2B. **Zero repack lines, one buffer, and the repack is 45.47% or 65.68% of the cold load depending which warm row you subtract — which is the honest answer, not a number.**

**Label fix, carried forward to any closing entry:** row 8's B2 table is headed
"TTFT cached, model resident, state + 20 tok". **It is 21 tokens on Qwen3.5-2B**,
not 20 — the 1.7B's figure. The prediction's own wording said 20 because it was
written for the 1.7B; the 2B's `user_tokens=21` is what row 8 measured.

**A DEVIATION FROM THE PLAN, STATED UP FRONT.** Row 4, the same control on the
1.7B, ran `-n 1` because only the load matters. Row 9 kept row 6's `-n 64
--print`. **The two controls are therefore not identical in shape.** It changes
nothing in the subtraction — `t_model_total_ms` is measured before any token is
generated and is indifferent to `-n` — but it made the row 25.68 s long instead
of a few seconds, and the clock paid for it (see below).

### THE CONTROL IS CLEAN — THIS IS THE POINT OF THE ROW

    $ adb shell 'grep -i "model buffer size" .../q35_r9_warm_nobufts.err'
    load_tensors:          CPU model buffer size =  1211.05 MiB
    $ adb shell 'grep -c "repack tensor" .../q35_r9_warm_nobufts.err'
    0

**ONE buffer line, NO `CPU_REPACK` line, ZERO repack tensor lines** — against
row 6's two lines and 187 repacked tensors across four kernels. `extra_bufts=0`
is echoed in the run's own header. The control did what it was for.

    row 6   CPU 399.94 + CPU_REPACK 1208.95 = 1608.89 MiB   187 repack lines
    row 9   CPU 1211.05                     = 1211.05 MiB     0 repack lines
    difference                                  397.84 MiB

And `1211.05 MiB against the .gguf's 1221.50 MiB` — **10.45 MiB less than the
file**, so the un-repacked buffer is very slightly smaller than the file on disk
rather than equal to it. Not explained here.

### THE TIME: A4-EQUIVALENT, AND IT HAS TWO ANSWERS

    t_tensor_band_ms    row 6 (cold) 3863.26   row 7 3663.29   row 8 2725.80   **row 9 616.72**
    t_model_total_ms    row 6        4638.66   row 7 4429.79   row 8 3490.87   **row 9 1395.09**

Row 9's band is **22.63% of row 8's, 16.84% of row 7's and 15.96% of row 6's
cold load.**

**A4 is defined as the repack's share of A2 (the COLD `t_model_total`), obtained
by warm-against-warm subtraction of the bands. Both available warm references
give a different answer:**

    reference          repack = band - 616.72    share of A2 (4638.66)   share of that row's band
    row 8 (warmest)         2109.08 ms                **45.47%**              77.37%
    row 7                   3046.57 ms                **65.68%**              83.16%

    for comparison, Qwen3-1.7B row 4:  2019.76 ms      50.72%                  83.07%

**A4's band was 40-65%. One reference lands inside it and the other lands on its
edge, so no A4-equivalent verdict is recorded.** The spread is 20 percentage
points and it is entirely an artefact of which row is treated as warm.

**NEITHER REFERENCE ROW WAS WARM THE WAY ROW 2 WAS, AND THAT IS THE CAVEAT THAT
MATTERS.** Row 2 followed row 1 by 236 s with the model fully in cache. On this
boot: row 7 ran 50 minutes after its cold partner with `Cached` at 1,122,352 kB
against a 1,250,816 kB model, and row 8 — the warmest band of the boot — ran
with `Cached` at 1,436,804 kB, enough to hold it. **So row 8 is the better
reference and 45.47% is the better figure, but "better" here is a judgement
about cache states read from `Cached`, not a controlled pair**, and the 1.7B's
50.72% was.

The `share of that row's band` column is the one figure that is stable across
references — **77.37% and 83.16%, against the 1.7B's 83.07%** — because it does
not divide by a cold row measured on a different thermal and cache state.

### THE SPACE: THE CHECK MATTERS MORE THAN THE FIGURE

**Row 9's peak RSS is quoted ONLY as a difference. It is not this model's RSS**
— `--extra-bufts 0` selects different matmul kernels and the process is not the
one any other row measured.

    row 8   peak_rss_kB 1,786,192
    row 9   peak_rss_kB 1,416,884
    difference            369,308 kB = **360.65 MiB**

    against the buffer-line difference                 397.84 MiB
    gap                                                 37.19 MiB

**So the repack's cost in space, measured two independent ways, agrees to within
37.19 MiB — 9.3% of the figure.** The 387.39 MiB the row-6 amendment derived
from buffer lines alone now has a second, process-level witness at 360.65 MiB.
Neither is exact and the gap is not accounted for.

### MEMORY, KILLS AND THE CLOCK

    PENNYBENCH memavail_kB   before 3,031,840   after 3,011,292
    PENNYBENCH memfree_kB    before 1,869,512   after 1,588,552
    PENNYBENCH swapfree_kB   before   709,972   after   709,972   (**unchanged**, 22.57%)
    PENNYBENCH cached_kB     before 1,384,860   after 1,646,192   (ROSE 261,332)
    PENNYBENCH pswpin        before   170,391   after   170,421   (+30)
    PENNYBENCH pswpout       before   923,084   after   923,084   (**unchanged**)
    PENNYBENCH pgmajfault    before   184,524   after   184,555   (+31)
    PENNYBENCH ceil_x1_kHz   before 2,802,000   min 1,426,000   after 2,802,000
    PENNYBENCH ceil_x1_min_at uptime=5461.69    (25 s into the row, **50.89%** of rated)
    PENNYBENCH ceil_a76_kHz  before 2,253,000   min 2,253,000   after 2,253,000
    PENNYBENCH max_rssanon_kB 1,411,664
    PENNYBENCH rss_samples       62
    PENNYBENCH lmk_kill_lines     0

**ZERO KILLS, `MemAvailable` BEFORE 3,031,840 kB — AND FLATTERED AGAIN.** This
boot had killed eleven processes across rows 6 and 7 before this row started.
Same caveat as row 8: it says nothing about a fresh boot.

**NOT ONE PAGE WAS SWAPPED IN EITHER DIRECTION.** `swapfree_kB` and `pswpout`
are byte-identical either side — the only row in this plan where that is true.
The un-repacked process is ~360 MiB smaller and the phone simply had the room.

**THE X1 CEILING FELL TO 1,426,000 kHz, 50.89% of rated, 25 s in — the deepest
of the four rows on this boot** (row 6 73.09%, row 7 65.17%, row 8 80.37%), and
it is the direct cost of the `-n 64` deviation: this row ran 25.68 s against
row 8's 9.88 s, and the minimum was recorded at the very end of it. 1,426,000 is
the same figure the 16 Sept matrix recorded after a 65-second two-thread run.
Both clusters back at rated afterwards. `policy0` not sampled.

### WHAT ROW 9 DOES NOT SAY

**No speed figure from this row is quoted anywhere** — no tok/s, no TTFT, no
generation figure. `--extra-bufts 0` selects different matmul kernels, so every
throughput number it produced describes a configuration nothing would ship.

**There is no A4-equivalent verdict**, and that is the finding rather than a gap
in the work: the subtraction needs a warm reference and this boot has two, 20
percentage points apart. A clean A4-equivalent needs a cold row and a warm row
minutes apart on one boot, which is a boot this plan did not budget.

The space agreement (360.65 against 397.84 MiB) is two measurements of one row
pair, and the 37.19 MiB gap is unexplained. The 1211.05 MiB buffer being
10.45 MiB smaller than the file is observed and not accounted for.

Zero kills and zero swap on a boot 91 minutes old that had already killed eleven
processes. AC power, screen on, unlocked, foreground, over adb, app disabled, no
VM. One sample.

## 2026-09-18 — END OF BOOT 3. Four rows, A6 and B6 and the B2-equivalent all pass, the hybrid saves AND loads a prefix, and **B3 for Qwen3.5-2B is NOT MEASURED**. Boot 4 is Matt's and the default is not to spend it.

### THE STATE FILE, `sync`ed AND HASHED — one invocation

Same discipline as the end of boot 1: **`sync` first**, because
`llama_state_save_file` issues no `fsync` and the bytes may exist only in the
page cache, then hash. Safe to read it here because boot 3's own cold reads are
spent.

    $ adb shell 'sync; echo "uptime_s=... wallclock=..."; ...; sha256sum /data/local/tmp/q35_state.bin; ls -la ...; ls -l /data/local/tmp; vm list'
    uptime_s=5575.98  wallclock=11:51:31  date=2026-09-18
    MemTotal:        5718284 kB
    MemFree:         1586140 kB
    MemAvailable:    3010084 kB
    Cached:          1649788 kB
    SwapTotal:       3145724 kB
    SwapFree:         739156 kB
    ceil_x1=2802000  ceil_a76=2253000  ceil_a55=1803000
    --- state file ---
    6f461e459f2ef0e61a6e6f7579250e115f92f5c3cb43cba18d9600a15f432a8f  /data/local/tmp/q35_state.bin
    -rw-rw-rw- 1 shell shell 25288618 2026-09-18 11:35 /data/local/tmp/q35_state.bin
    --- ls -l /data/local/tmp ---
    total 1288875
    -rw-rw-rw- 1 shell shell 1280835840 2026-09-15 20:26 Qwen3.5-2B-Q4_K_M.gguf
    -rwxr-xr-x 1 shell shell    4708216 2026-09-16 12:08 llama-bench
    -rwxr-xr-x 1 shell shell    3805208 2026-09-16 12:18 llama-simple
    drwxrwxrwx 4 shell shell       3452 2026-09-14 11:35 microdroid
    drwxrwxrwx 2 shell shell      12288 2026-09-18 11:49 out
    -rw-rw-rw- 1 shell shell       1911 2026-09-16 16:48 penny_system.txt
    -rw-rw-rw- 1 shell shell         95 2026-09-16 17:05 penny_user.txt
    -rwxr-xr-x 1 shell shell       8402 2026-09-16 16:57 pennybench.sh
    -rwxr-xr-x 1 shell shell    3817808 2026-09-16 16:29 pennyload
    -rw-rw-rw- 1 shell shell   25288618 2026-09-18 11:35 q35_state.bin
    --- vm ---
    Running VMs: []

**`6f461e459f2ef0e61a6e6f7579250e115f92f5c3cb43cba18d9600a15f432a8f`,
25,288,618 bytes.** That hash exists so that IF boot 4 is ever spent, the file
row 10 reads can be proved to be the file row 7 wrote — the same control the
1.7B's `707e0ea3…` provided across boots 1 and 2. **It is recorded whether or
not boot 4 happens**, because it costs nothing now and cannot be obtained later.

### THE BOOT, END TO END

    reboot issued          10:18:14 (last pre-reboot reading, same invocation)
    adb back               uptime   24.80 s   10:18:59
    reading                uptime  512.80 s   10:27:07   (8.55 min, NOT the ~5 min mark)
    reading                uptime 1503.14 s   10:43:38   (25.05 min)
    row 6 q35_r6_cold_fresh      1525.63 -> 1544.62 s    rc=0
    row 7 q35_r7_warm_fresh_save 4605.42 -> 4624.26 s    rc=0
    row 8 q35_r8_warm_cached     5174.13 -> 5184.01 s    rc=0
    row 9 q35_r9_warm_nobufts    5436.68 -> 5462.36 s    rc=0
    close                  uptime 5575.98 s   11:51:31

Four rows, all gated on both clusters at rated in the launching invocation, all
`rc=0`. `MemAvailable` ended at 3,010,084 kB, `SwapFree` at 739,156 kB — 23.50%
of total, above the ~10% floor. **No row on this boot met the contamination
condition.** Nineteen processes killed across the boot, all cached, all at
`oom_score_adj` 905-915, none in the foreground band, our process never touched.

### WHAT BOOT 3 SCORED

    #   prediction                                   point      band          measured       verdict
    A6  Qwen3.5-2B cold load -> ready                 4.9 s   3.5-7.0 s      4.6656 s       **PASS**
    B6  state file size, Qwen3.5-2B, ~420 tokens    ~25 MiB   10-60 MiB     24.117 MiB      **PASS**
    B2  TTFT cached, resident, state + 21 tokens      0.5 s   0.35-1.5 s     0.39228 s      **PASS**
        (B2-equivalent; the prediction says 20 tokens, which is the 1.7B's count)

**And the one that was not a number at all: `llama_state_save_file` AND
`llama_state_load_file` both return true on a model with eighteen of
twenty-four layers recurrent.** The plan's standing branch — "the whole of Q-B
fails on Qwen3.5-2B … a hybrid's prefix cannot be cached at this commit" — is
not taken. **The hybrid's prefix file is 45.83% SMALLER than the dense 1.7B's**
(59.80 against 112.02 KiB/token) on a model 16% larger, which is the directional
claim B6 was written to risk being wrong about.

Not scored, and recorded as not scored: **the A5-equivalent** (fails high at
95.77%, consistent with a partially evicted model file and not established; the
plan does not score A5 on boot 3) and **the A4-equivalent** (two warm references
20 percentage points apart — 45.47% against row 8, 65.68% against row 7 — so no
verdict).

### B3 FOR Qwen3.5-2B — **NOT MEASURED**

**This is a blank, not an oversight, and the plan named it in advance:** B3 is
the cached wake from a COLD process — the first run after a power cycle, with
neither the model nor the state file read on that boot. Only a row 10 on a
fourth boot can supply one. Boot 4 was written into the plan as OPTIONAL and
**Matt's to spend, and the default is not to spend it.** So B3 for the hybrid is
reported NOT MEASURED, and row 8's `ttft_cold_proc_ms` of 3905.77 ms is reported
under its own name — a warm-model, warm-state-file, ninth-row-of-the-boot figure
— and is never quoted as B3.

**A PREDICTION, MARKED AS A PREDICTION AND NOT A MEASUREMENT.** Built the same
way B3's own was, from this boot's measured parts:

    cold t_ready (row 6, MEASURED)                              4665.60 ms
    cold state load (PREDICTED: row 5's 59.16 ms scaled
      24.117/44.523 MiB)                                          ~32    ms
    user turn (row 8's cached-path t_user_decode, MEASURED)      381.30 ms
    ----------------------------------------------------------------------
    **predicted B3, Qwen3.5-2B  ~5.08 s**   against the 1.7B's MEASURED 4.099 s

**Nothing has been run that produces this number.** The cold state-load term is
a scaling of a different model's figure and the user-turn term comes from a row
whose model was already in cache. If boot 4 is ever spent, this prediction is
what it is judged against, and it is frozen here so it cannot be revised to fit.

### WHAT THE END OF BOOT 3 DOES NOT SAY

**The 2B's state file has never survived a power cycle.** It was written and
read back nine minutes apart on one boot. The `sync` and the hash above are the
preparation for testing that and are not the test. The 1.7B's equivalent
question was answered on boot 2; the hybrid's is open.

**Boot 3 has no ~5-minute protocol reading**, and its 8.55-minute substitute is
not compared with boot 1's 303.33 s figures anywhere.

Every row here is one sample. No row ran longer than 25.68 s, the X1 ceiling was
below rated inside every one of them and reached 50.89% in row 9, and nothing on
this boot is a sustained or thermal figure. AC power, screen on, unlocked,
foreground, over adb, app disabled, no VM throughout. Two of the four rows
returned zero kills and both are flattered by the nineteen this boot had already
taken. And no output text was judged for quality on any row — all four stopped
inside a `<think>` block at 64 tokens.

## 2026-09-18 — Q-A AND Q-B, CLOSED. Twelve predictions, ELEVEN PASS and A3 fails low. A cached prefix takes the wake from 10.39 s to 4.10 s on Qwen3-1.7B, and 90% of what is left is the model load. The hybrid caches its prefix for less than half the bytes, and its B3 is NOT MEASURED.

The closing entry for the row plan written 16 Sept at notes.md 8772. **Nine
measured rows, three boots spent, one optional boot unspent.** Every figure
below is quoted from a row entry by its tag and every one of those was read
from `out/<tag>.report` and `out/<tag>.bench` on the phone. Nothing here is a
new measurement and nothing is re-derived: where a row entry recorded a caveat,
the caveat travels with the number.

    boot 1  16 Sept, Qwen3-1.7B-Q4_K_M   q17_r1_cold_fresh, q17_r2_warm_fresh_save,
                                         q17_r3_warm_cached, q17_r4_warm_nobufts
    boot 2  16 Sept, Qwen3-1.7B          q17_r5_coldcache_cached
    boot 3  18 Sept, Qwen3.5-2B-Q4_K_M   q35_r6_cold_fresh, q35_r7_warm_fresh_save,
                                         q35_r8_warm_cached, q35_r9_warm_nobufts
    boot 4  NOT SPENT                    q35_r10_coldcache_cached -- Matt's call,
                                         default is not to spend it

All nine rows `rc=0`, all nine gated on `policy6` = 2,802,000 AND `policy4` =
2,253,000 in the launching shell invocation, all `c0`, `-t 2`, `-lm none`,
`n_ctx` 1024, greedy, `chat_template=NONE`. **No row on any of the three boots
met the contamination condition.**

### THE SCORECARD — TWELVE PREDICTIONS, EACH AGAINST THE ROW IT WAS DEFINED ON

    #   prediction                       point     band        measured      row    verdict
    --  -------------------------------  --------  ----------  ------------  -----  --------
    A1  1.7B cold load -> ready           4.2 s    3.0-6.0 s   4.0218 s      r1     PASS
    A2  ... read + repack (T4-T1)         3.5 s    2.5-5.0 s   3.9821 s      r1     PASS
    A3  ... context creation (T5-T4)      0.4 s    0.2-0.8 s   0.03662 s     r1     **FAIL, LOW**
    A4  repack's share of A2               52%     40-65%      50.72%        r4/r2  PASS
    A5  1.7B warm load, ratio to cold      3.2 s   fail >=90%  70.61%        r2     PASS
                                                   or <=40%
    A6  2B cold load -> ready             4.9 s    3.5-7.0 s   4.6656 s      r6     PASS
    B1  TTFT fresh, resident, 427 tok     7.7 s    6.0-9.0 s   6.3691 s      r1     PASS
    B2  TTFT cached, resident,            0.5 s    0.35-1.5 s  0.42231 s     r3     PASS
        state + 20 tok
    B3  TTFT cached, COLD PROCESS         4.9 s    3.5-8.5 s   4.0987 s      r5     PASS
    B4  cost of saving state to disk     0.25 s    0.05-1.0 s  0.01791 s     r2     PASS
    B5  state file size, 1.7B          45.9 MiB    40-60 MiB   44.523 MiB    r2     PASS
    B6  state file size, 2B             ~25 MiB    10-60 MiB   24.117 MiB    r7     PASS

**ELEVEN PASS, ONE FAILS.** A3 fails low by an order of magnitude and was
already known to be failing before row 1 ran — the 16:50 smoke test read
35.83 ms on the same `n_ctx` — and **it was deliberately left frozen rather
than revised**, on the rule that a prediction written before the work is judged
and not edited to fit. Creating a 1024-token KV cache on these models is tens of
milliseconds, not hundreds.

**A1's pass is partly arithmetic luck and the entry for r1 says so**: A2 came in
482 ms above its point and A3 363 ms below its own, and the two nearly cancel
inside the sum A1 is.

**LABEL FIX, CARRIED IN FROM THE r9 ENTRY.** B2's row above reads "state +
20 tok" because that is the prediction's own wording, written for Qwen3-1.7B.
**On Qwen3.5-2B the same `penny_user.txt` is 21 tokens** (`user_tokens=21`, r6),
so the 2B's B2-equivalent at r8 is a 21-token turn. The two files are byte-
identical across both models; the vocabularies are not.

**THE 2B's OWN THREE SCORES**, per the plan, which scores A6, B6 and r8's B2 on
boot 3 and re-scores nothing from the 1.7B:

    A6  r6  4.6656 s      PASS
    B6  r7  24.117 MiB    PASS, and the directional claim held -- see below
    B2  r8  0.39228 s     PASS  (B2-equivalent, 21-token turn)

    B4-equivalent  r7  16.87 ms   inside B4's band, not a re-score of B4
    A5-equivalent  r7  95.77%     FAILS HIGH -- and is NOT a boot-3 score
    A4-equivalent  r9  45.47% or 65.68% -- NO VERDICT RECORDED

**The last two are deliberately unscored and the reasons are in their own
entries.** The A5-equivalent fails high on a row whose model file was not
resident (`Cached` 1,122,352 kB against a 1,250,816 kB model, r7), which is
consistent with a partially evicted file and is not established; the plan does
not score A5 on boot 3 and no boot was spent chasing it. The A4-equivalent has
two warm references twenty percentage points apart and r9 records the absence of
a verdict as the finding rather than picking one.

### THE ONE THAT WAS NOT A NUMBER: THE HYBRID'S STATE PATH

The plan carried a standing branch — "**the whole of Q-B fails on Qwen3.5-2B**
if `llama_state_save_file` or `llama_state_load_file` returns false on a hybrid
model … reported as a hybrid's prefix cannot be cached at this commit, and
nothing is patched to get around it." Eighteen of Qwen3.5-2B's twenty-four
layers are recurrent.

**BOTH RETURN TRUE. THE BRANCH IS NOT TAKEN.**

    r7   state_bytes=25288618  state_tokens_saved=413      t_state_save_ms 16.87
    r8   state_tokens_restored=413                         t_state_load_ms  9.58

and the tokens prove the round trip rather than the return value doing it:

    r6 (fresh)          first_token_id=3710  token_fnv1a64=0x19d53b5da9186ff6
    r7 (fresh + save)   first_token_id=3710  token_fnv1a64=0x19d53b5da9186ff6
    r8 (state loaded)   first_token_id=3710  token_fnv1a64=0x19d53b5da9186ff6

**A restored hybrid prefix produces the same 64 tokens as a decoded one.** The
same control holds on the dense model across four rows and two boots —
`0xcba17a2fcbba49f4` on r1, r2, r3 and r5.

### Q-A, ANSWERED: THE LOAD IS THE TENSOR BAND, AND THE TENSOR BAND IS MOSTLY REPACK

    phase (ms)            1.7B cold   1.7B warm   2B cold   2B warm(r8)
                             r1          r2         r6          r8
    t_backend_ms              3.04        3.62       2.62        2.65
    t_model_open_ms         398.49      364.13     769.44      758.47
    t_tensor_band_ms       3581.59     2431.20    3863.26     2725.80
    t_model_tail_ms           2.04        2.23       5.95        6.61
    t_model_total_ms       3982.13     2797.56    4638.66     3490.87
    t_ctx_create_ms          36.62       38.75      24.32       19.97
    t_ready_ms             4021.78     2839.93    4665.60     3513.49

**The tensor band is 89.0% of the cold load on the 1.7B (r1) and 82.8% on the
2B (r6).** Context creation is 0.9% and 0.5% of them. `t_model_open_ms` nearly
doubled between the two models — 398.49 (r1) to 769.44 (r6) — while the band
grew 7.86%; r6 names the 2B's vocabulary as a suspect and records that nothing
isolates it.

**THE REPACK IS THE LARGER HALF OF THE BAND, MEASURED BY SWITCHING IT OFF:**

    1.7B   r2 band (repack on, warm)   2431.20
           r4 band (repack off, warm)   411.44
           repack                      2019.76 ms  = **50.72% of A2** = A4, PASS
                                                   = 83.08% of the warm band
                                                   = 72.20% of the warm load

    2B     r8 band (repack on, warm)   2725.80    r7 band  3663.29
           r9 band (repack off)         616.72
           repack                      2109.08 ms (vs r8) or 3046.57 (vs r7)
                                       = 45.47%      or 65.68% of A2 -- NO VERDICT

**And the file read, which is what a warm cache removes**, taken from the 1.7B
only and resting on the plan's own premise that repack is CPU work and
cache-independent:

    r1 cold band 3581.59 - repack 2019.76  =  ~1561.83 ms for 1,056.13 MiB
                                           =  ~676 MiB/s off UFS

That subtraction crosses a cold row and a warm one, which is the direction the
plan warns against for A4 and the reverse of it here; **it is an inference under
a stated premise, not a measurement, and no prediction is scored on it.** The
equivalent figure for the 2B cannot be given at all, because its repack term has
two values.

**THE STRUCTURAL HALF AGREES WITH THE TIME HALF, ON BOTH MODELS.** From the
loaders' own buffer lines:

    1.7B   r2  CPU 243.90 + CPU_REPACK 1049.96 = 1293.86 MiB   197 repack tensors
           r4  CPU 1050.43, no CPU_REPACK                        0 repack tensors
           file on disk                          1056.13 MiB
           buffer excess over file               +237.73 MiB
           peak RSS r2 - r4 = 1,541,012 - 1,289,416 kB = 245.70 MiB

    2B     r6  CPU 399.94 + CPU_REPACK 1208.95 = 1608.89 MiB   187 repack tensors
           r9  CPU 1211.05, no CPU_REPACK                        0 repack tensors
           file on disk                          1221.50 MiB
           buffer excess over file               +387.39 MiB
           peak RSS r8 - r9 = 1,786,192 - 1,416,884 kB = 360.65 MiB

**On both models the repack's cost in space shows up twice and the two witnesses
agree** — 243.43 against 245.70 MiB on the 1.7B (2.27 MiB apart), 397.84 against
360.65 MiB on the 2B (37.19 MiB apart, 9.3%). r9's peak RSS is quoted here only
as that difference, per the rule the plan set for control rows, and neither
model's gap is accounted for.

### Q-B, ANSWERED: THE PREFIX CACHE REMOVES SIX SECONDS, AND WHAT IS LEFT IS THE MODEL LOAD

**Every TTFT figure in the plan, by row, with what each one actually is:**

    case                                        1.7B                2B
    ----------------------------------------    ---------------     ---------------
    fresh, model resident (T10-T6)              6369.12  r1 = B1     8168.95  r6
    fresh + save, resident                      6319.51  r2          8397.71  r7
    cached, resident, state file WARM (T10-T5)   422.31  r3 = B2      392.28  r8 = B2-eq
    cached, resident, state file COLD            406.21  r5
    cold process, fresh (T10-T0)               10394.46  r1
                                                9163.00  r2
    warm process, cached (T10-T0)               3382.26  r3          3905.77  r8
    COLD process, cached (T10-T0)               4098.74  r5 = **B3**  **NOT MEASURED**

    speedups, each within one model
      r1 ttft_cached 6372.68 -> r3 422.31                        15.09x
      r7 ttft_fresh  8397.71 -> r8 391.96                                21.42x
      r1 cold process 10394.46 -> r5 4098.74                      2.54x
      r2 cold process  9163.00 -> r5 4098.74                      2.24x

**WHERE THE WAKE TIME GOES — r5, the only true B3 in the plan:**

    t_ready_ms        3692.53   90.1%   loading the model
    t_state_load_ms     59.16    1.4%   restoring the 407-token prefix
    t_user_decode_ms   345.96    8.4%   the 20-token user turn

**And where the RESIDENT cached figure goes, on both models:**

    r3  t_user_decode 401.99 of 422.31 ms  = 95.2%
    r8  t_user_decode 381.30 of 392.28 ms  = 97.2%

**So on both models and in both cases the prefix is not where the time is.**
Restoring it costs 19.17 ms warm and 59.16 ms cold on the 1.7B (r3, r5) and
9.58 ms on the 2B (r8). What the cache removes is the system-prompt decode —
5996.47 ms on r1, 7713.13 ms on r6. What it cannot remove is the load.

**THE STATE FILES, AND B6's DIRECTIONAL CLAIM IS THE RESULT WORTH KEEPING:**

    Qwen3-1.7B   r2   46,685,237 B = 44.523 MiB   over 407 tok = 112.02 KiB/token
    Qwen3.5-2B   r7   25,288,618 B = 24.117 MiB   over 413 tok =  59.80 KiB/token

**The hybrid's file is 54.17% of the dense model's — smaller by 45.83% — on a
model 16% larger.** B6's failure condition was written as "fails if the
Qwen3.5-2B file is LARGER", and it is not. r7 records that the split between the
token-proportional and fixed parts rests on an assumed 12 KiB/token taken from
the prediction rather than measured, and that the claim the file barely grows
with prompt length is untested.

**SURVIVING A POWER CYCLE: ANSWERED FOR THE DENSE MODEL, OPEN FOR THE HYBRID.**

    1.7B  q17_state.bin  707e0ea3c1cc490187616a67ba0097747c8b8c58fcd2dcf38e1870a31a8f6f4d
          hashed after `sync` at end of boot 1, and again on boot 2 AFTER r5 ran
          -> MATCH, 46,685,237 B both times
    2B    q35_state.bin  6f461e459f2ef0e61a6e6f7579250e115f92f5c3cb43cba18d9600a15f432a8f
          hashed after `sync` at end of boot 3, 25,288,618 B
          -> **never read back across a reboot; the hash exists so that it could be**

### THE PREDICTED PLAIN ANSWER, JUDGED

Written 16 Sept before any run: *"Predicted: resident wins, and not narrowly. A
model held resident with a cached prefix puts the first word in front of the
user in about half a second; a cold process that reloads the model puts it there
in four to seven seconds, and a saved prefix does not help with that because the
model load is the whole of the cost."*

**Half a second: RIGHT, three times.** 422.31 ms (r3), 406.21 ms (r5),
392.28 ms (r8).

**Four to seven seconds: RIGHT on the one model where it was measured.**
4098.74 ms (r5). The 2B's equivalent is not measured.

**"A saved prefix does not help with that": WRONG AS WRITTEN, and this is the
one substantive miss in the plain answer.** The saved prefix takes the cold
process from 10,394.46 ms (r1) to 4098.74 ms (r5) — **2.54x** — because it
removes the ~6.0 s system-prompt decode. What is right is the reason attached
to it: the model load is 90.1% of what remains (r5), so the prefix cache cannot
bring a cold process anywhere near the resident figure. **It helps a great deal
and it does not close the gap**, and those are two different statements that the
predicted sentence collapsed into one.

**The price of resident, restated rather than predicted, is higher than the
sentence quoted.** It said 1.35-1.42 GiB for the 1.7B, taken from the 16 Sept
`llama-bench` rows. `pennyload`'s own gated rows read:

    1.7B   r1  peak_rss_kB 1,541,140  = 1.470 GiB   (99.6% anonymous)
    2B     r6  peak_rss_kB 1,824,632  = 1.740 GiB   (99.70% anonymous)
           r8  peak_rss_kB 1,786,192  = 1.703 GiB   -- the cached path is cheaper
                                                       than the fresh one by
                                                       38,004 kB

Different binary, different flags, so neither is a check on the other; the
`pennyload` figure is the one measured under the conditions of this plan.

### THE CONDITIONS THE ROWS RAN UNDER, COLLECTED

**KILLS.** The first row of a boot is the representative condition and every
later row on that boot is flattered by it:

    row  MemAvailable before   lines/processes
    r1        2,277,952 kB      8 / 4     first row of boot 1
    r2        2,639,848         0 / 0
    r3        2,679,200         2 / 1
    r4        2,887,072         0 / 0
    r5        2,191,292         8 / 4     first row of boot 2
    r6        2,270,852         6 / 3     first row of boot 3
    r7        2,636,476        16 / 8     **more memory AND more kills**
    r8        3,061,764         0 / 0
    r9        3,031,840         0 / 0

**The first row of a boot costs about four cached processes on this handset
whether it decodes a system prompt (r1) or restores one (r5)**, and the 2B at
1.74 GiB cost three where the 1.7B at 1.47 GiB cost four, at starting conditions
7,100 kB apart (r6 against r1). **r7 breaks the pattern and complicates the
B2-R1 rule**: it started with 365,624 kB more than r6 and took eight processes
rather than three, on a boot that had been swapping for fifty minutes. r7
records that as a correlation across two rows and not a cause. Nothing in the
foreground band on any of the nine rows; our own process never touched.

**THE CLOCK.** The X1 ceiling fell below rated inside every one of the nine
rows and no row ended below rated:

    r1 73.1%   r2 73.1%   r3 85.7%   r4 65.2%   r5 89.5%
    r6 73.09%  r7 65.17%  r8 80.37%  r9 50.89%

**The A76 pair never moved on any of the nine rows** — `policy4` read 2,253,000
before, min and after on all of them — where the 16 Sept cooled matrix saw it
fall on six of seven rows. Observed, not explained; those rows were longer and
differently shaped. **`policy0` was not sampled during any row in this plan**,
which is the same gap the 16 Sept entry left open.

**SWAP AND CACHE.** No row met the contamination condition. The lowest any row
ended at was r6's `SwapFree` 476,412 kB, 15.14% of `SwapTotal`, against the ~10%
floor. r9 is the only row in the plan where `swapfree_kB` and `pswpout` were
byte-identical either side.

### WHAT Q-A AND Q-B DO NOT SAY

**B3 FOR Qwen3.5-2B IS NOT MEASURED, AND IT IS A BLANK RATHER THAN AN
OVERSIGHT.** Boot 4 was written into the plan as optional and is Matt's to
spend, and the default is not to spend it. The frozen prediction stands at
**~5.08 s** in the end-of-boot-3 entry, built from r6's measured cold ready plus
a *scaled* ~32 ms cold state load plus r8's measured user turn, and it is marked
there as a prediction that nothing has been run to produce. **r8's
`ttft_cold_proc_ms` of 3905.77 ms is a warm-model, warm-state-file, ninth-row
figure and is never quoted as B3.**

**THE A4-EQUIVALENT AND THE A5-EQUIVALENT ARE UNSCORED ON THE 2B** for the
reasons in r9 and r8 respectively, and the 2B's warm pair is not the controlled
pair the 1.7B had: r2 followed r1 by 236 s, r7 followed r6 by fifty minutes.

**EVERY ROW IS ONE SAMPLE.** There are no error bars anywhere in this plan. The
cross-model comparisons (r1 against r6, r2 against r7) cross two boots and two
days, and the two boots did not start identically.

**NOTHING HERE IS A WAKE MEASURED AT BOOT.** r5 is the first run of a boot with
nothing having read either file, which is the cold-process case B3 is defined
as — but it was launched by hand over adb on an unlocked phone. **The unattended
boot path proven in rungs 3, 3b and 3d and this cold-process figure have never
been run together**, and nothing in this plan starts a model from a boot
broadcast.

**NOTHING SUSTAINED AND NOTHING THERMAL.** The longest row in the plan is r9 at
25.68 s and the shortest is r8 at 9.88 s. **A model held resident and working
for hours — which is the actual product shape — is untested**, and the X1
ceiling was still descending at the end of several rows.

Not done, and named as not done: the `-ub` test that separates batch size from
micro-batch size; a sustained run; Q4_0; anything on battery; `policy0` sampled
during a row; anything VM-hosted; any third model; and **any judgement of output
quality** — all nine rows' text stops inside a `<think>` block at 64 tokens,
and the 1.7B's sample gets two of its three facts about Canberra wrong.

Boot 3 has no ~5-minute protocol reading; its 8.55-minute substitute is recorded
under its own label and is compared with nothing.

All of it on AC power, screen on, unlocked, foreground, over adb, with the app
`pm disable-user`'d and `Running VMs: []` throughout, on a Pixel 6a running
GrapheneOS 2026091001 / Android 17 CP2A.260705.006, against llama.cpp at commit
38a5b42d9 built `armv8.2-a+dotprod+fp16`. **It is an absolute feasibility
measurement of this handset and never a native-versus-VM comparison**, which is
closed.

## 2026-09-18 — AMENDMENT to the Q-A/Q-B closing entry. "All nine rows' text stops inside a `<think>` block" is FALSE — only the 2B's rows do, and the 1.7B's rows reached an answer. And row 9's page cache ROSE during the row, so 45.47% is a FLOOR, not a figure.

Appended, not edited in. Both corrections come from row entries already in this
file; nothing was re-run and nothing new was read off the phone.

### 1. THE TEXT CLAIM IS WRONG, AND ITS OWN SENTENCE CONTRADICTED ITSELF

The closing entry's "does NOT say" section reads: *"all nine rows' text stops
inside a `<think>` block at 64 tokens, and the 1.7B's sample gets two of its
three facts about Canberra wrong."* **The two halves cannot both be true — a
run that stops inside a reasoning block has not stated three facts about
Canberra — and it is the first half that is wrong.** Corrected:

    rows                model        what the row entry records
    ----------------    ----------   --------------------------------------------
    r1, r2, r3, r5      1.7B         an ANSWER, no `<think>` block: Canberra, a
                                     population of ~4 million and a founding date
                                     of 1901. r1: "both are wrong". r3: "Canberra
                                     right, the other two facts wrong."
    r4                  1.7B         NOTHING. `-n 1`, and r4 says "there is no
                                     generation here at all".
    r6, r7, r8          2B           stops INSIDE a `<think>` block at 64 tokens.
                                     r6 quotes the opening verbatim: the model
                                     echoes the question, then "<think> Thinking
                                     Process:".
    r9                  2B           `-n 64 --print`, but r9 quotes no text and no
                                     figure from it, per the control rule. The
                                     end-of-boot-3 entry records all four boot-3
                                     rows as stopping inside a `<think>` block.

**So the two models behave differently at a 64-token budget and the closing
entry flattened that into one sentence.** Qwen3-1.7B reaches an answer inside 64
greedy tokens with no chat template; Qwen3.5-2B opens a reasoning block and is
still planning when the budget runs out.

**What this still does NOT say.** Whether the 1.7B's answer completed on its own
or merely fitted inside 64 tokens is not established — no row recorded an
end-of-generation reason. **No quality judgement is made or reversed by this
correction**: the 1.7B's three facts are recorded as two wrong because its own
row entries record them that way, and the 2B's text is recorded as text produced
and nothing more. 64 tokens was a budget chosen for the rows, not either model's
own stopping point, and neither model saw a chat template.

### 2. ROW 9's `Cached` ROSE DURING THE ROW, SO THE A4-EQUIVALENT IS A FLOOR

From r9's own table, unchanged:

    PENNYBENCH cached_kB   before 1,384,860   after 1,646,192   (**ROSE 261,332**)

**A row whose page cache grows by 255 MiB while it runs is not established as a
fully warm read.** At `-lm none` the model is read with `read()` rather than
mapped, so a file read grows `Cached` without producing major faults — which is
one reading of the 261,332 kB rise sitting beside `pgmajfault` +31 and `pswpin`
+30 in the same table. **That is a reading of two figures against each other and
not a measurement**, and nothing here establishes how much of r9's 616.72 ms
band was disk.

**THE DIRECTION IS UNAMBIGUOUS EVEN THOUGH THE SIZE IS NOT.** r9's band is the
subtrahend in every A4-equivalent figure. If any part of it is file read rather
than the read-only floor, the band is INFLATED, the difference is too SMALL, and
the repack is UNDERSTATED:

    repack vs r8  =  2725.80 - 616.72  =  2109.08 ms  ->  45.47% of A2   **a floor**
    repack vs r7  =  3663.29 - 616.72  =  3046.57 ms  ->  65.68% of A2   **a floor**
    share of that row's own band          77.37% and 83.16%             **floors**

**So 45.47% is best read as a floor on the repack's share of A2, not as a
figure**, and the twenty-point spread between the two references is unaffected —
both move the same way. **No A4-equivalent verdict is recorded and this does not
create one.**

For contrast, and it is why r9's rise is worth flagging rather than shrugging
at: **r8, the warm reference, is the only row on boot 3 whose `Cached` FELL**
(1,436,804 -> 1,383,168, down 53,636 kB), where r6 rose 93,988, r7 rose 272,584
and r9 rose 261,332. The row treated as warm did not need to pull the file in;
the control row did. **Stated, not resolved.** Resolving it needs a `--extra-bufts 0`
row run within a minute or two of a warm one on the same boot, which is a boot
this plan did not budget and which nothing here proposes spending.

Nothing else in the closing entry changes: the twelve-prediction scorecard, A4's
own 50.72% PASS on the 1.7B (r4 against r2, where r4's `Cached` moved 2,816 kB
and `pswpout` did not move at all), Q-B's figures and B3's NOT MEASURED status
are all untouched.

## 2026-09-18 — BRIEF S AMENDMENTS and PREDICTIONS, written before any code, any build and any row: the sustained run

Brief S asks the question every row in this repo has deferred: a model held
resident, a cached prefix, a user turn arriving repeatedly, for an hour. The
longest row in the Q-A/Q-B plan was r9 at 25.68 s and the shortest r8 at 9.88 s.
Nothing here has run for more than half a minute.

This entry is written before `pennyload` gains a line, before `pennybench.sh`
gains a line, before anything is deleted from the phone and before anything is
pushed to it. Three corrections to the brief come first, because two of them
change what a row measures and the third changes what a sentence in the
write-up may say.

### CORRECTION 1 — `oom_score_adj` IS -1000 FROM `adb shell`, SO S-B1 AS BRIEFED COULD NOT FAIL

Read on boot 3 before any of this was built:

    adb shell 'sh -c "cat /proc/\$\$/oom_score_adj"'   ->   -1000

**-1000 is the value adbd hands down, and it means the low-memory killer will
not take the process at any pressure.** "Our process survived an hour holding
1.43 GiB" is not a finding if the process was never a candidate. The brief had
already asked for `oom_score_adj_child` to be printed "so 'our process was not
killed' has a number beside it"; the number turns out to be the one that empties
the claim.

**DECIDED BY MATT, 18 Sept: raise it to 200**, and the write is tested before
any reboot rather than assumed. Tested on boot 3, one invocation, a throwaway
`sleep` launched from `adb shell`:

    pid=9181  pre_oom_score_adj=-1000
              write_stderr=[]  write_rc=0
              post_oom_score_adj=200
              uptime_s=8625.00  wallclock=12:42:20

**SELinux does not refuse it**, the write returns 0 with empty stderr, and the
read-back is 200. Raising `oom_score_adj` is permitted to one's own uid;
lowering it needs `CAP_SYS_RESOURCE`, which is why -1000 -> 200 works and 200 ->
-1000 would not.

**200 IS THE PERCEPTIBLE / FOREGROUND-SERVICE BAND. It is NOT the cached band
and it is NOT the foreground-app band.** Android's bands, as this repo has
already measured them in 3g-ii and 3e-i: 0 is the foreground app, 100/200 are
the visible and perceptible bands, 900+ is cached and empty. 3g-ii's killer
cleared the whole cached band and then took `com.android.inputmethod.latin` at
**adj 201** (`prcp IMPB`), twice, and stopped there without entering 200/100/0.
200 is therefore the closest available imitation of **a foreground service
started from a boot broadcast**, which is the shape rungs 3, 3b and 3d proved
and the shape any real Penny would use. It is one point below the deepest adj
the LMK has ever been observed to reach on this handset.

`pennybench.sh` prints the pre-write value and the value read back AFTER the
write, side by side, so the line is a reading rather than an intention:

    PENNYBENCH oom_score_adj_child pre=<v> post=<v>   (written by the wrapper)

**AND THE CONSEQUENCE FOR THE RECORD: rows 1-9 of the Q-A/Q-B plan ALL RAN AT
-1000.** Every one was launched from `adb shell` through this same wrapper.
Their kill lists — 8 lines / 4 processes on r1, 8/4 on r5, 6/3 on r6, 16/8 on
r7 — **could never have named `pennyload`**, whatever the pressure. Those kill
counts stay exactly as reported; what changes is that "our own process was never
touched" (the closing entry's phrase) was not a measurement on any of the nine
rows and must not be read as one. It is a measurement from S1 onwards.

### CORRECTION 2 — THE BRIEF'S REASON FOR `state_set_data` OVER `seq_rm` IS NARROWER THAN IT SAYS

The brief says "`seq_rm` on a partial range is not supported on recurrent
memory, so it would silently make a 2B row a different experiment". Read at
`src/llama-memory-recurrent.cpp:161-216`, commit 38a5b42d9, that is not quite
right and the correct version is still a good reason.

Partial `seq_rm` on recurrent memory **is** implemented at this commit, by a
per-token snapshot index — but it is **bounded** (`rollback <= n_rs_seq`),
**single-use** (it refuses while a rollback is already pending: `const bool
pending = rs_idx[seq_id] != 0`), and it **returns `false`** rather than
throwing when either condition fails. Rolling back one of these turns means
discarding 85 tokens (20 user + 64 generated + 1), so it would need
`n_rs_seq >= 85` and a clean pending flag on every one of 60 or 200 turns.

**So the accurate reason is: a `false` return part-way through a row would turn
a sustained row into a differently-shaped one with no exception raised**,
whereas `llama_state_set_data` is unconditional and is the exact path Q-B
measured at rows 3, 5 and 8. The per-turn figures then compare directly with
B2's 422.31 ms (r3) and 406.21 ms (r5). None of this bites on Qwen3-1.7B, which
is dense and has no recurrent layers; it is recorded so that the 2B can be run
later on the same instrument without the reason having to be re-derived.

API signatures verified by reading `include/llama.h:814-835` at 38a5b42d9, not
recalled:

    size_t llama_state_get_size(struct llama_context * ctx);
    size_t llama_state_get_data(struct llama_context * ctx, uint8_t * dst, size_t size);
    size_t llama_state_set_data(struct llama_context * ctx, const uint8_t * src, size_t size);

`get_size`'s own comment — "Only use when saving the state, not when restoring
it" — is about restoring; the snapshot is a save, so it is the right call.

### CORRECTION 3 — BATTERY TEMPERATURE IS READ FROM sysfs IN THE SERIES, NOT FROM `dumpsys`. NAMED AS A DEVIATION.

The brief specifies `dumpsys battery`. The series samples every 10 s for an
hour, i.e. ~360 times, and `dumpsys` is a binder call into `system_server` —
the process most likely to be perturbed by exactly the memory pressure the row
is measuring. `/sys/class/power_supply/battery/temp` is a direct read of the
same value.

**They agree, read in ONE invocation so the comparison is not across time:**

    sysfs_temp_dC=280  dumpsys_temp_dC=279  uptime_s=8865.42  wallclock=12:46:20

1 dC apart. The series uses sysfs; `dumpsys battery` is still read once before
and once after each row as the cross-check, so the deviation is bounded by a
check at both ends. **It is BATTERY temperature in tenths of a degree C, not SoC
temperature, and it is labelled that way everywhere.** `/sys/class/thermal/` is
`Permission denied` to the shell user on this build, so no SoC temperature can
be read at all.

### THE DEFINITIONS, FIXED BEFORE ANY ROW

Verbatim from brief S, with the one extension the `oom_score_adj` correction
above forces on `survived`:

    ttft_turn_k  = restore + user decode + first sample, turn k
                   (== ttft_cached per turn)
    gen_tps_k    = (n-1) decodes / gen_ms, the same divisor as today
                   (pennyload.cpp:390)
    settled      = median of the turns in the LAST quarter of the row
    decline      = settled against the median of the FIRST quarter,
                   as a percentage
    survived     = pennyload rc=0 AND turns_done == --turns AND no kill line
                   naming pennyload AND oom_score_adj_child post=200 printed
    duty         = busy_ms / interval_ms, reported per row from the turn lines

**EVERY S-A FIGURE IS COMPUTED ON THOSE TWO MEDIANS AND NOTHING ELSE.** Not on
turn 1 against turn n, not on a mean, not on a line fitted through the row, and
not on any turn chosen after the fact. S-A1 and S-A2 are the `decline` of
`ttft_turn` and of `gen_tps` on S1; S-A3 is `settled` `gen_tps` on S2 expressed
as a percentage of **turn 1**, which is the one place the brief names turn 1
rather than a median and is written that way because its fail condition is.

**The quarters, so the arithmetic is not chosen later.** S1 has 60 turns: first
quarter = turns 1-15, last quarter = turns 46-60. S2 has 200: first quarter =
turns 1-50, last quarter = turns 151-200. **A median over an even-sized set is
the mean of the two middle values** — so S2's medians are the mean of the 25th
and 26th, and of the 175th and 176th, of each sorted quarter. S1's quarters have
15 turns each, so each median is a single value, the 8th of the sorted quarter.

**A row that stops early is scored on the turns it produced and is reported as
not having survived**, since `turns_done == --turns` is part of `survived`; its
quarters are then taken over `turns_done`, and the entry says so in the row.

### WHAT THE PREDICTIONS ARE BUILT FROM — the four Qwen3-1.7B rows, quoted by tag

All four ran `c0`, `-t 2`, `-lm none`, `n_ctx` 1024, `n_ubatch` 512, greedy,
`chat_template=NONE`, `-n 64`, gated at rated on both ceilings. Every figure
below was read from `out/<tag>.report` and `out/<tag>.bench` at the time and is
quoted from its row entry, not re-measured:

    row  t_state_load  t_user_decode  t_sample  ttft_cached  gen_tps  gen_ms   peak_rss_kB
    r1   n/a (fresh)      371.75        0.89      6372.68     15.04   4188.65   1,541,140
    r2   n/a (fresh)      360.32        0.85      6323.06     14.77   4266.65   1,541,012
    r3     19.17 warm     401.99        0.81       422.31     15.35   4104.77   1,500,212
    r5     59.16 cold     345.96        0.76       406.21     15.08   4177.14   1,500,196

    user decode   mean of four   370.005 ms      spread 345.96 - 401.99  (+/- 7.6%)
    sample        mean of four     0.8275 ms
    gen_tps       mean of four    15.06 t/s      spread 14.77 - 15.35    (+/- 1.9%)
    gen_ms        mean of four  4184.30 ms
    peak RSS on the CACHED path (r3, r5)  1,500,212 kB = 1.4307 GiB, 99.6% anonymous

**The cached path is the one S1 runs**, so 1,500,212 kB is the resident figure
these predictions use — not r1's 1,541,140 kB, which is the fresh path.

### THE ARITHMETIC, PREDICTION BY PREDICTION

**The turn, decomposed.** `ttft_turn` = restore + user decode + first sample.

*Restore.* r3's `t_state_load_ms` of 19.17 ms read 46,685,237 B from a warm page
cache, parsed the session header and copied into the KV cache.
`llama_state_set_data` from an in-memory snapshot does strictly less: no file
read, no header. So 19.17 ms is an upper reference, not a target.
**Point 15 ms, band 8-25 ms.** The brief's own "~10-20 ms already measured" sits
inside that.

*User decode.* 370.005 ms, the mean of all four rows, spread 345.96-401.99. The
same 20-token `penny_user.txt` every turn — this is prompt processing, which the
16 Sept matrix showed DOES scale with cores (P3, 2.10x from 1 to 4 threads), so
it is the clock-sensitive half of a turn.

*Sample.* 0.8275 ms. Negligible and carried only so the sum is honest.

    ttft_turn (turn 1)   15 + 370.005 + 0.83  =  385.8 ms
                         POINT 0.39 s   BAND 0.33 - 0.50 s

That sits just below B2's measured 422.31 ms (r3) and 406.21 ms (r5), which is
the direction the file read being removed predicts.

    turn wall time (busy)   0.386 + 4.184  =  4.57 s     BAND 4.0 - 5.5 s
    S1 duty                 4.57 / 60      =  7.6%
    S1 duration             60 x 60 s      =  60.1 min
    S2 duration at turn-1 speed   200 x 4.57  =  914 s = 15.2 min
    S2 duration at the predicted settled speed  200 x 5.62 = 1124 s = 18.7 min
                                                POINT 17 min   BAND 15 - 19 min

**S-A1 — S1 `ttft_turn` settled. PREDICT decline 3%, band 0-8%. FAILS above 25%.**
Each turn is ~4.6 s of work followed by ~55 s of idle. The measured ceiling
recovery on this handset is 109.8 s of idle after a **65 s** two-thread load
(16 Sept); the closer anchor is the Q-A/Q-B rows themselves, which were 9.9-25.7
s long, dipped the X1 ceiling to 73.1-89.5% of rated, and **every one of the nine
ended back AT rated**. An S1 turn is shorter than the shortest of those rows and
has twelve times its own length to recover in. There is no mechanism in the
record by which this row declines much.

**S-A2 — S1 `gen_tps` settled. PREDICT decline 3%, band 0-8%; settled ~14.6 t/s,
band 13.8-15.3. FAILS above 25% decline (below ~11.3 t/s).** Same reasoning, and
generation is the *less* clock-sensitive half — see S-A3.

**S-A3 — S2 `gen_tps` settled. PREDICT settled at 80% of turn 1, i.e. ~12.0 t/s
from ~15.0; band 70-92% (10.5-13.8 t/s). FAILS below 50% of turn 1 (7.5 t/s).**

The arithmetic is the cooled C-series of 16 Sept, which is the only place in
this repo where the same configuration was run at two different row lengths and
the clock floor recorded for both.

    matched pair, UNPINNED, 4 threads -- the deepest clock floor in the record
      C6   -p  64   52.93 s   X1 ceiling floor  984,000 kHz = 35.1% of rated   tg128 14.47
      C5   -p 512  106.03 s   X1 ceiling floor  851,000 kHz = 30.4% of rated   tg128 11.74
      2.00x the row length, a 13.4% lower floor
      11.74 / 14.47 = 0.8113   ->   tg DOWN 18.87%

    matched pair, c0, 2 threads -- S2's own configuration
      C2   -p  64   52.57 s   X1 ceiling floor 1,277,000 kHz = 45.6%   tg128 14.91
      C1   -p 512  103.32 s   X1 ceiling floor 1,106,000 kHz = 39.5%   tg128 14.11
      14.11 / 14.91 = 0.9463   ->   tg DOWN 5.37%

**Across all seven cooled rows the X1 floor spanned 851,000 to 1,277,000 kHz — a
1.50x clock range, and 30.4% to 45.6% of rated — while `tg128` spanned 11.74 to
14.91, a 1.27x range.** Token generation is memory-bandwidth bound on this
handset: P2 held on 16 Sept, four threads are SLOWER than two. **If generation
scaled with clock, a fall to 30% of rated would give 15.06 x 0.30 = 4.5 t/s and
S-A3 would fail outright. The prediction is that it will not, and the C-series
is the whole reason.**

S2 is C1's configuration run roughly ten times as long, so it should reach a
floor below C1's 39.5% — the unpinned pair's 18.87% tg loss at 30.4% is taken as
the point rather than C1's own 5.37%, which is why the point is 80% and not 95%.

**S-A4 — `fnv_all_equal = 1` on every row. PREDICT 1.**
Greedy sampling on a restored prefix has already produced the identical 64
tokens across four rows and two boots on this model —
`token_fnv1a64 0xcba17a2fcbba49f4` on r1, r2, r3 and r5, with
`first_token_id 32313` on all four — and across three rows on the 2B. **What is
new is that the restore is from an in-memory snapshot rather than a file, and
that it happens 60 or 200 times inside ONE process.** After turn k the context
holds prefix + 20 + 64 tokens; `state_set_data` must return it to exactly the
407-token prefix. If any residue survives, turn 2 differs from turn 1 and the
fnv breaks at turn 2, not at turn 60. **That is the thing S-A4 actually tests,
and a mismatch is the more important finding.**

**S-B1 — S1 survives. PREDICT SURVIVES, at adj 200, and by one point.**
3g-ii measured the LMK on a loaded phone clearing the entire cached band and
then taking the IME at **adj 201**, twice, and stopping — never entering
200/100/0 — under a 2048MB VM, which 3e-ii measured as taking **~1.92 GB** from
the host at creation. S1 holds **1,500,212 kB = 1.43 GiB**, which is less. So
the prediction is survival at one point below the deepest adj the killer has
been seen to reach, under a smaller allocation than the one that took it there.
**If it dies, it dies at exactly the band 3g-ii said the killer stops at, and
that would be the headline.**

**S-B2 — MemAvailable at the end of S1. PREDICT the SERIES settles at ~900,000
kB, band 600,000 - 1,400,000 kB. FAILS if kill lines continue past the first
five minutes of the row.**

    native baseline, NO model, app disabled, one boot (CLAUDE.md):
        2,119,020 kB at 25.3 min   2,012,348 at 60.5 min   1,929,032 at 120.6 min
        decline between the last two ~2.0 MB/min
    S1's last turn lands at roughly 25 min (protocol reading) + 60 min = ~88 min
        2,012,348 - (88 - 60.5) x 2000  =  ~1,957,000 kB   with no model
    model held (r3/r5 cached path)                          -1,500,212 kB
    naive difference                                        =   456,788 kB
    but the LMK gives memory back: every 1.7B row that killed RAISED MemAvailable
        r1  2,277,952 -> 2,669,292   +391,340    (8 lines / 4 processes)
        r5  2,191,292 -> 2,713,172   +521,880    (8 lines / 4 processes)
        mean of the two                +456,610
    456,788 + ~450,000  =  ~907,000 kB           POINT ~900,000 kB

**THIS MUST BE READ FROM THE SERIES WHILE THE MODEL IS RESIDENT, NOT FROM
`PENNYBENCH memavail_kB after`.** The wrapper's after-reading is taken once the
child has exited and the 1.43 GiB has gone back, so it will read high — r3's was
2,878,632 kB and r5's 2,713,172 kB. **Predicted `PENNYBENCH memavail_kB after`
for S1: 2.6 - 2.9 GB, and it says nothing about the hour.** That is precisely
why the brief added a series.

On the kill half: the first row of a boot costs about four cached processes on
this handset — r1 8 lines / 4 processes, r5 8/4, r6 6/3 — and every one of them
happened at model load. **Predict 3-8 kill lines on S1, all inside the first
60 s, NONE after minute 5.** A steady kill rate under a resident model is the
finding that changes the design, and it is what the fail condition is written
to catch.

**S-B3 — SwapFree stays above 10% of SwapTotal (314,572 kB of 3,145,724)
through S1. PREDICT PASS, settled ~650,000 kB, band 400,000 - 1,000,000
(13 - 32%). The margin is not large and the prediction is specifically that it
FLATTENS.**

Loading this model costs swap, measured three times:

    r5   997,372 -> 651,260   -346,112 kB   in one ~13 s row
    r3   767,996 -> 530,940   -237,056 kB   in one ~9 s row
    r2   820,732 -> 751,100    -69,632 kB

Those are the cost of making room for 1.43-1.47 GiB of anonymous memory by
compressing other processes into zram. **In S1 the model is loaded ONCE and held
for an hour; the turns allocate nothing new after the KV cache exists.** So the
prediction is a fall of 300,000-500,000 kB inside the first two minutes and then
a flat line. **If it instead keeps draining at the loading rate it crosses the
10% floor inside the hour, the row is void under the standing contamination rule
and the boot is spent.** Starting SwapFree on a fresh boot at ~25 min is
predicted at 1,000,000-1,400,000 kB; the record has no fresh-boot figure at that
uptime, only r5's 997,372 kB as the first row of boot 2 and boot 3's close at
739,156 kB, so this one number is the weakest in the entry.

**S-C1 — ceilings between S1 turns. PREDICT the X1 pair IS at 2,802,000 at the
start of every turn after the first, and >= 95% of series samples read rated;
point 97%.**

    measured recovery   109.8 s of idle from 1,426,000 kHz after a 65 s 2-thread run (16 Sept)
    closer anchor       the nine Q-A/Q-B rows, 9.88 - 25.68 s long:
                          X1 floor 73.1% (r1) to 89.5% (r5) of rated
                          r3 min 2,401,000 (85.7%) at 7 s in, ended AT rated
                          r5 min 2,507,000 (89.5%) at 7 s in, ended AT rated
                          EVERY one of the nine ended at rated
    an S1 turn is 4.57 s -- shorter than the shortest of those rows -- with 55 s after it

Sampling arithmetic: duty is 7.6%, so ~8% of 10 s samples land inside a turn;
and the Q-A/Q-B minima were first seen **7 s into** their rows, which is later
than a whole S1 turn lasts. So the fraction of samples below rated is predicted
to be *smaller* than the duty, hence 97%.

Two more, carried in the same prediction: **`policy4` (A76, rated 2,253,000)
never moves on S1** — it did not move on any of the nine Q-A/Q-B rows, before,
min or after. And **`policy0` (A55, rated 1,803,000) is sampled during a row for
the first time in this repo**; CLAUDE.md has named that gap twice and the series
closes it. **No prediction is made for `policy0`** — nothing in the record
supports one.

For S2: **predict the X1 ceiling spends most of the row below rated with a floor
below C1's 1,106,000 kHz (39.5%), point 900,000 - 1,000,000, band 851,000 -
1,277,000** — the C-series envelope — because S2 is C1's configuration at ten
times the length.

**S-C2 — battery temperature. NO PREDICTION, per the brief; nothing in this repo
has ever read it.** Recorded as a first reading, not a baseline: on boot 3,
idle, AC power, screen on, level 100, `dumpsys battery` temperature **267 =
26.7 C** at uptime 7725.22 / 12:27:20, and **280 / 279 dC = 28.0 / 27.9 C** at
uptime 8865.42 / 12:46:20. The phone warmed ~1.2 C in 19 minutes doing nothing,
which sets the resolution any S1 slope has to beat to mean anything.

**The instrument's own two new figures, predicted so they can be judged:**

    prefix_snapshot_bytes   POINT ~46,684,000 B   BAND 46.0 - 47.0 MB
        r2's state FILE was 46,685,237 B for 407 tokens = 112.02 KiB/token,
        against 114,688 B/token derived from the GGUF metadata -- so the state
        is sized by used cells, not by n_ctx. The in-memory snapshot drops the
        session-file header, so it is predicted slightly SMALLER than the file.
    t_snapshot_ms           POINT 12 ms   BAND 5 - 25 ms
        B4 measured 17.91 ms to build the state AND write it to disk (r2);
        get_data does no write.

### THE PREDICTED PLAIN ANSWER, SO IT CAN BE JUDGED TOO

**Predicted: the conversational hour is uneventful and the back-to-back
quarter-hour is where the chip shows.** One turn a minute for sixty minutes
costs this handset a 1.43 GiB resident set, about four cached processes killed
once at the start, and roughly 7.6% of its time; the turns at the end look like
the turns at the beginning to within a few percent, because 55 seconds of idle
is ten times what the X1 pair needs to come back to rated. Run the same turns
with no gap and generation slows by about a fifth — not by the two-thirds the
clock falls by — because generation on this phone is bound by memory bandwidth
and not by clock. **The thing most likely to be wrong here is the memory half,
not the timing half**: S-B2's ~900 MB and S-B3's flat swap line are both
inferences from rows that lasted seconds, applied to an hour.

### WHAT THIS ENTRY DOES NOT SAY

Nothing has been measured. Every number above is a prediction and all of them
rest on rows of 9.88 to 25.68 s, or on the 16 Sept cooled matrix whose rows were
52.57 to 246.73 s — **none of them on anything that ran for an hour, which is
the entire point of the row being predicted.** The C-series pairs used for S-A3
differ in `-p` as well as in length, so "row length" and "prompt size" are not
separated in them. S-B2's baseline interpolation assumes the ~2.0 MB/min decline
measured between 60.5 and 120.6 min continues to ~88 min, and CLAUDE.md
separately records that the decline decelerates sharply beyond 120 min, so the
extrapolation is only defensible inside the interval it came from. S-B3's
starting figure is the weakest number in the entry. The Qwen3.5-2B is not
predicted at all and is not being run. Nothing here is at boot, nothing is on
battery, nothing has a second model in memory, and **an hour is not a day.**

## 2026-09-18 — BRIEF S SMOKE TESTS on boot 3. **NOT A RESULT ROW — NEITHER OF THEM.** The single-turn path is unchanged on the phone, the loop runs, and one bug was found and fixed before any row.

Two tests, both on the Qwen3.5-2B still resident from boot 3, both through
`pennybench.sh` rev 5 with the new binary pushed as `/data/local/tmp/pennyload_s`
so that `/data/local/tmp/pennyload` was **not** overwritten until both passed.
**NOTHING HERE IS A MEASUREMENT OF THE HANDSET.** Boot 3 has had nine rows and
four smoke runs on it, its swap is two-thirds spent, and no figure below is
quoted anywhere as a result. They exist to test the instrument.

    pennyload_s    8363d84e…  first build, used for smoke A
                   f52fc604…  after the fix below, used for smoke A2 and B2
    pennybench_s.sh 96163d04…  rev 5, unchanged throughout

### SMOKE A — THE SINGLE-TURN PATH IS UNCHANGED, AND THE PROOF IS AN A/B ON THE PHONE

Row 8's exact invocation, `--tag r8` included so the tag line matches, run on
the NEW binary and then on the OLD one forty-six seconds apart on the same boot:

    GATE x1=2802000 a76=2253000 uptime_s=9889.77  wallclock=13:03:24   new binary
    GATE x1=2802000 a76=2253000 uptime_s=9935.48  wallclock=13:04:10   old binary

    PENNYBIN=/data/local/tmp/pennyload_s sh pennybench_s.sh smoke_a_identity c0 -- \
      -m /data/local/tmp/Qwen3.5-2B-Q4_K_M.gguf -t 2 -lm none -n 64 --print \
      --user-file /data/local/tmp/penny_user.txt \
      --load-state /data/local/tmp/q35_state.bin --tag r8

**28 output lines from each, and after blanking every number the line set and
order are IDENTICAL.** So is the generation, three ways:

    old binary   first_token_id=3710   token_fnv1a64=0x19d53b5da9186ff6
    new binary   first_token_id=3710   token_fnv1a64=0x19d53b5da9186ff6
    all 64 token_ids identical: True

**`0x19d53b5da9186ff6` is the figure row 7, row 8 and row 6 all recorded**, so
the new binary reproduces the hybrid's restored-prefix generation exactly.
Repeated on the FIXED binary afterwards (`smoke_a_identity2`, uptime 10183.22,
13:08:18) with the same three results — the fix touched only loop-mode output,
but that was verified rather than reasoned.

**The timings differ.** `t_state_load_ms` read 41.08 ms against row 8's 9.58 ms,
and `t_tensor_band_ms` 2914.17 against 2725.80. **The conditions, and NO CAUSE
IS CLAIMED for either gap.** Row 8 ran at uptime 5174.13 on this same boot and
read `q35_state.bin` nine minutes after row 7 wrote it. Smoke A ran at uptime
9889.77, wallclock 13:03:24; the state file's mtime read 2026-09-18 11:35 — it
was written on the phone by row 7, not pushed, so that mtime is the real write
time and the file was **about 1 h 28 min old**. By then the boot carried **nine
rows and four smoke runs**, and `SwapFree` was **682,300 kB of 3,145,724 —
two-thirds spent** (read in the smoke B report either side). **These are two
runs at different moments on a tired boot and neither is comparable with row 8's
figures.** The fnv is the part that carries.

### SMOKE B — THE LOOP RUNS, AND SO DOES THE OVERRUN PATH

    PENNYBIN=/data/local/tmp/pennyload_s sh pennybench_s.sh smoke_b_loop2 c0 -- \
      -m …Qwen3.5-2B-Q4_K_M.gguf -t 2 -lm none -n 64 \
      --user-file …penny_user.txt --load-state …q35_state.bin \
      --turns 3 --interval-s 5 --tag s_loop2
    GATE x1=2802000 a76=2253000 uptime_s=10128.03 wallclock=13:07:23

    PENNYLOAD prefix_snapshot_bytes=25286954 got=25286954 t_snapshot_ms=10.56
    TURN k=1 uptime_s=10132.48 t_restore_ms=4.00 ttft_turn_ms=387.81 gen_tps=12.62 fnv=0x19d53b5da9186ff6 busy_ms=5379.57 overrun=1
    TURN k=2 uptime_s=10137.86 t_restore_ms=4.57 ttft_turn_ms=454.77 gen_tps=12.25 fnv=0x19d53b5da9186ff6 busy_ms=5597.04 overrun=1
    TURN k=3 uptime_s=10143.45 t_restore_ms=3.80 ttft_turn_ms=466.69 gen_tps=11.90 fnv=0x19d53b5da9186ff6 busy_ms=5761.23 overrun=1
    turns_done=3 turns_requested=3 turns_overrun=3 fnv_all_equal=1
    DUTY busy_median_ms=5597.04 interval_ms=5000 duty_pct=111.94

**`fnv_all_equal=1`, and the value is the same one the FILE restore produces.**
That is the mechanism S-A4 tests: `llama_state_set_data` from an in-memory
snapshot returns the context to the 413-token prefix well enough that turn 3
generates the identical 64 tokens as turn 1 and as row 8. A residue left behind
by the restore would have broken it at turn 2.

**The snapshot is 25,286,954 B against the state FILE's 25,288,618 — smaller by
1,664 B**, which is the session-file header the in-memory form does not carry.
That was predicted before the build ("predicted slightly SMALLER than the file")
and is confirmed here on the 2B; the 1.7B's own figure is still unmeasured.

**`overrun=1` on all three turns is correct behaviour, not a fault.** A 2B turn
is ~5.4-5.8 s of work and the interval was set to 5 s deliberately, so the
overrun path and `duty_pct` above 100 are exercised. S1's interval is 60 s.

**`t_restore_ms` is 3.80-4.57 ms.** No prediction is scored on that — the
predictions are for Qwen3-1.7B, whose snapshot is 46.7 MB against this 25.3 MB,
and the frozen band stays as written.

### THE BUG, FOUND BY THE FIRST RUN OF SMOKE B AND FIXED BEFORE ANY ROW

With `turns_done=3`, `q = turns_done / 4 = 0`, so both quarters were empty and
`median_of` returned its -1.0 sentinel. The report printed:

    PENNYLOAD quarters       n_per_quarter=0  q1=turns 1-0  q4=turns 4-3
    PENNYLOAD SETTLED ttft_turn_ms q1_median=-1.00 q4_median=-1.00 decline_pct=0.00
    PENNYLOAD SETTLED gen_tps_turn1=12.73 settled_pct_of_turn1=-7.86

**`-7.86` is a real-looking number computed from a sentinel, and `decline_pct`
read 0.00 — which is exactly the figure S-A1 and S-A2 are scored on.** The guard
stopped a crash and produced something worse than a crash. **S1 and S2 never
reach this** (q is 15 and 50), **but a row that stops early does — and S-B1
explicitly allows a row to stop early, scored on `turns_done`.** Fixed to print
`n/a` in words. Verified on the re-run above.

### THE INSTRUMENT ALSO ANSWERED THREE THINGS IT WAS NOT ASKED

- **`oom_score_adj_child pre=-1000 post=200` on every run**, including the one
  that failed at argument parsing. The write works through the wrapper, not only
  in the hand test.
- **`ceil_a55` is readable during a row and did not move**: 1,803,000 at every
  series sample on both loop runs. That is the first time `policy0` has been
  sampled during a row in this repo, and it closes the gap CLAUDE.md names
  twice — **at the 10 s resolution of the series, and on a 22 s row, which is
  not the same as saying it never moves.**
- **`dumpsys battery` LAGS sysfs.** Read at both ends of smoke B: sysfs 278 ->
  280 dC while dumpsys read 276 at both. Earlier in the day, idle, they agreed
  to 1 dC. `dumpsys` updates on battery-change broadcasts rather than on demand.
  **The deviation to sysfs was decided for a different reason — 360 binder calls
  into `system_server` — and this is a second, better one.**

### ONE FAILURE, AND IT WAS THE OPERATOR'S

The first smoke B invocation omitted `--user-file` and returned rc=2 with
`pennyload: -m and --user-file are required`. That is Claude's command being
wrong, not the instrument. Recorded because the wrapper's behaviour under it is
useful: `rc=2` reached the report, the oom and battery lines were still written,
and `series_file … (0 samples)` was correct — the child exited in 0.43 s, before
the poll loop ran once.

### WHAT THE SMOKE TESTS DO NOT SAY

**Neither is a result and neither is gated as one would be** — the gate was read
and both ceilings were at rated, but these ran back to back on a boot with nine
rows and four runs already on it. No figure here is this handset's speed, and
the `gen_tps` of 11.90-12.73 must never be quoted against row 8's 12.65. Three
turns is not sixty, five seconds is not sixty seconds, and nothing here ran long
enough to throttle, swap or be killed — `lmk_kill_lines 0` on a row that lasted
22 seconds says nothing. The series produced three samples; S1 will produce
~360, and nothing here tests the sampler over an hour. The 1.7B has not been
touched: every figure above is Qwen3.5-2B.

## 2026-09-18 — AMENDMENT to the brief S smoke-test entry. A false sentence replaced, and the three-count disassembly check run on the binary that is actually on the phone.

Both raised by Matt on reading the entry above, before the reboot for brief S's
boot. Nothing was re-run on the phone for either; the first is a correction of
words, the second is a check on a local file.

### 1. "THIS RAN TWO DAYS LATER" WAS FALSE

The smoke A paragraph read: *"because row 8 ran nine minutes after the state
file was written and this ran two days later with the file no longer as warm."*
**`q35_state.bin` was written by row 7 on the morning of 18 Sept, on this same
boot — about an hour and a half before the smoke test, not two days.** Its mtime
read 2026-09-18 11:35 when the file was listed at 13:09:55, and because row 7
wrote it on the phone rather than it being pushed, that mtime is the genuine
write time and not the `adb push` mtime trap.

**The sentence also asserted a cause — "with the file no longer as warm" — that
nothing measured.** It has been replaced above with the conditions and no cause:
state file ~1 h 28 min old, a boot carrying nine rows and four smoke runs,
`SwapFree` 682,300 kB of 3,145,724 (two-thirds spent). Why `t_state_load_ms`
read 41.08 ms rather than 9.58 ms is **not established here and nothing is
offered.**

The replacement is an edit rather than an append, at Matt's instruction; the
sentence it replaced is quoted in full above so the error stays in the history.

### 2. THE THREE-COUNT CHECK ON `f52fc604…`, THE BINARY ON THE PHONE

The check was run at build time on 18 Sept 13:07 and its result is in the
`ff009f5` commit message, but it was never shown in a message to Matt with the
binary's own hash beside it, which is the form the protocol asks for. Re-run on
the local copy, one invocation, hash first:

    f52fc60411b55e5ed9eb34e8307f32b45d6bed6f06de85a5347bc02ec2f4ffe9  build/pennyload-stripped
    3,836,992 B

    disassembly lines                              735,079
    i8mm    (smmla|ummla|usmmla)                          0
    SVE/SME (ptrue|whilelo|smstart|smstop|z<n>. operand)  0
    dotprod (sdot|udot)                                 898

**0 / 0 / 898**, the same three counts as the 16 Sept `llama-bench` build and as
`be2cab2c…`. And the file on the phone is the same file:

    f52fc60411b55e5ed9eb34e8307f32b45d6bed6f06de85a5347bc02ec2f4ffe9  /data/local/tmp/pennyload

**What this does NOT say.** The counts are of a disassembly of the whole binary,
so 898 `sdot|udot` says the dot-product kernels are compiled in, not that any of
them executed. `llvm-objdump` is the check because `readelf -A` returns an empty
`BuildAttributes` block for every aarch64 binary and would pass a build full of
`smmla`. Nothing here re-verifies the static libraries, which are the same
object files at 38a5b42d9 that every figure in this repo was measured against.

## 2026-09-18 — BRIEF S, THE BOOT AND ITS PROTOCOL READINGS. Boot 4 of the handset's row work, the first boot dedicated to the sustained run.

Boot 3 was spent: nine Q-A/Q-B rows, four brief-S smoke runs, and the model
swap. `adb reboot` issued 13:17:19 Mac time, last reading before it
`uptime_s=10719.15 wallclock=13:17:14` in one invocation. Matt unlocked by hand;
the USB port is charging-only while locked on GrapheneOS, so adb cannot answer
before that.

    ADB UP   uptime_s=52.95   wallclock=13:18:26
             -> boot start ~13:17:33

**CONDITIONS, read at `uptime_s=85.41 wallclock=13:18:59`, before any row:**

    AC powered: true   status: 2 (charging)   level: 100
    battery temperature 330 dC = 33.0 C
    screen_off_timeout=30000   stay_on_while_plugged_in=15   mWakefulness=Awake
    com.pennyspike.probe2a     still disabled -- the disable survived the reboot
    Running VMs: []
    policy0=1803000  policy4=2253000  policy6=2802000   -- all three at rated
    /data/local/tmp: Qwen3-1.7B-Q4_K_M.gguf, pennyload (f52fc604...),
                     pennybench.sh rev 5 (96163d04...), penny_system.txt,
                     penny_user.txt, llama-bench, llama-simple, out/, microdroid/
                     NO q17_state.bin -- so S0 must regenerate it

**`pm disable-user` SURVIVING A REBOOT IS NOW OBSERVED RATHER THAN ASSUMED.**
CLAUDE.md has said it survives since 15 Sept; this is the reading. Whether the
two `pm grant`s and the four assistant preconditions survived a
disable/enable cycle is still UNTESTED and nothing here tests it.

**THIS BOOT STARTS WARM, AND THAT IS A CONDITION ON EVERYTHING BELOW.** Battery
temperature read 33.0 C at 85 s, against 26.7 C at idle on boot 3 this morning
and 27.9-29.1 C across the smoke tests. The reboot followed a 32.8 s model push
and four model loads. **S-C2's start figure is therefore not a cold start**, and
no slope computed from it may be quoted as though the phone began at rest.

### THE ~5 MINUTE READING — one invocation, value and wallclock together

    MemTotal     5,718,280 kB
    MemFree      1,323,484 kB
    MemAvailable 2,218,088 kB
    Cached       1,123,100 kB
    SwapTotal    3,145,724 kB
    SwapFree       996,604 kB   (31.68% of SwapTotal)
    AnonPages    1,647,332 kB
    uptime_s=303.84   wallclock=13:22:37   batt_temp_dC=318 (31.8 C)

**`MemAvailable` 2,218,088 kB at 5.06 min, and that is 1.28 GB ABOVE the only
other ~5-minute reading in the repo.** The native baseline of 15 Sept read
**940,640 kB at 5.8 min** on an untouched boot with the app disabled, and
CLAUDE.md warns in terms that it "was a phone still settling and must not be
used as a budget". This reading is far closer to boot 3's 8.55-minute substitute
of 2,310,624 kB and to the 25-minute figures of 2,135,144-2,284,200 kB across
five boots.

**NO CAUSE IS OFFERED AND NONE SHOULD BE READ IN.** Two boots' 5-minute readings
1.28 GB apart is an observation about the spread of early-boot readings on this
handset, from a sample of two, taken three days apart under conditions that were
not controlled to match. What it does bear on is the standing warning: **the
5-minute mark is not a stable point on this phone**, and that warning now has a
second reading behind it rather than one.

Battery temperature fell 330 -> 318 dC over the same 218 s, so the phone was
shedding the heat of the push and the reboot while this was taken.

### S0 — `q17_s0_regen`. NOT A RESULT ROW; it exists to regenerate `q17_state.bin`. Figures only, no write-up.

Gate and launch in one invocation:
`GATE PASSED x1=2802000 a76=2253000 uptime_s=377.78 wallclock=13:23:51`.
`c0`, `-t 2`, `-lm none`, `n_ctx` 1024, `-n 64 --print`, `--sys-file`,
`--save-state`, greedy, `chat_template=NONE`. **COLD** — the reboot cleared the
page cache the 13:11 push had warmed, so nothing had read the .gguf since
power-on.

    t_backend_ms          2.90
    t_model_open_ms     475.40
    t_tensor_band_ms   3371.09
    t_model_tail_ms       2.73
    t_model_total_ms   3849.22
    t_ctx_create_ms      36.70
    t_ready_ms         3888.82   COLD
    t_tokenize_ms         4.35
    t_sys_decode_ms    6301.49   407 tokens
    t_state_save_ms      21.90
    state_bytes    46,685,237    407 tokens saved
    t_user_decode_ms    401.15   20 tokens
    t_sample_ms           0.81
    ttft_fresh_ms      6725.34
    ttft_cached_ms     6729.69
    ttft_cold_proc_ms 10618.51
    gen_tokens 64      gen_ms 4346.77      gen_tps 14.49  (63 decodes)
    first_token_id 32313     token_fnv1a64 0xcba17a2fcbba49f4
    sys_tokens 407   user_tokens 20   progress_calls 312
    peak_rss_kB 1,540,948    max_rssanon_kB 1,535,544

    uptime_s       before   377.89   after   393.61
    MemAvailable   before 2,210,640  after 2,609,868
    MemFree        before 1,311,544  after 1,672,136
    SwapFree       before   997,116  after   487,932   (15.51% of SwapTotal)
    Cached         before 1,126,228  after 1,164,132
    pswpout        before   567,983  after   695,302
    pgmajfault     before    42,013  after    42,134
    ceil_x1        before 2,802,000  min 2,048,000 (73.1%) 7 s in   after rated
    ceil_a76       before 2,253,000  min 2,253,000 (rated)          after rated
    oom_score_adj_child  pre=-1000  post=200
    batt_temp_dC   before 316   after 317
    lmk_kill_lines 0   **with MemAvailable before the row 2,210,640 kB**
    series         2 samples

**THE REGENERATED STATE FILE IS BYTE-FOR-BYTE THE ONE DELETED ON 16 SEPT.**
Hashed on the phone after `sync`:

    /data/local/tmp/q17_state.bin   46,685,237 B
    707e0ea3c1cc490187616a67ba0097747c8b8c58fcd2dcf38e1870a31a8f6f4d

CLAUDE.md records the original as `707e0ea3…`, 46,685,237 B, hashed at the end
of boot 1 and again on boot 2 after r5. **The original was written by
`be2cab2c…` and this by `f52fc604…`, on a different boot two days later.**

**S-B3's committed start-of-row prediction (1.0-1.4 GB) is already false before
S1 starts: 487,932 kB = 15.51%. Recorded, not revised.**

### THE 60-SECOND SAMPLER TOOK ZERO READINGS. THERE IS NO TREND TABLE.

A sampler was armed at ~13:26 to read `MemAvailable`, `SwapFree`, `Cached`,
`pswpin`, `pswpout` and battery temperature every 60 s until the 25-minute mark,
so that the swap question had a trend behind it. **It produced nothing.** Its
entire output, four passes:

    /system/bin/sh: no closing quote
    /system/bin/sh: no closing quote
    /system/bin/sh: no closing quote
    /system/bin/sh: no closing quote

The command was `adb shell 'echo "t=$(…) … wall=$(date +%H:%M:%S)'` — **the
double quote was never closed before the closing single quote.** This is the
same family as the `adb shell` argument-rejoining trap recorded earlier today,
and it was walked into **inside a poll loop**, which is the precise failure that
entry describes: "Inside a poll loop the variable was empty on every pass, the
loop spun for twenty minutes and no reading was ever taken." It cost the trend
and nothing else — S0 and both protocol readings are unaffected.

Killed before the gate; `ps -ax | grep "adb shell"` returns nothing.

**ONE valid reading exists**, from a correctly-formed command, and it is one
reading and not a trend:

    uptime_s   wallclock   MemAvailable      SwapFree     source
    393.61     --          2,609,868 kB      487,932 kB   S0's after-block
    717.00     13:29:31    2,586,968 kB      628,476 kB   single read

**`SwapFree` rose 140,544 kB in the 323 s between them**, so swap was recovering
rather than flat — but that is two points, one of which came from a row's
after-block rather than from an idle sample, and **no rate is claimed from it.**

### THE ~25 MINUTE READING — one invocation, every value and the wallclock together

    uptime_s=1501.63 wallclock=13:42:35 MemAvailable=2347476 SwapFree=666876
    Cached=1226340 MemFree=1277764 AnonPages=1297800 batt_temp_dC=312
    ceil_x1=2802000 ceil_a76=2253000 ceil_a55=1803000

Quoted from the command's own output, unedited. 25.03 minutes after power-on.

    MemAvailable   2,347,476 kB
    SwapFree         666,876 kB   (21.20% of SwapTotal 3,145,724)
    Cached         1,226,340 kB
    MemFree        1,277,764 kB
    AnonPages      1,297,800 kB
    battery        312 dC = 31.2 C
    ceilings       X1 2,802,000 / A76 2,253,000 / A55 1,803,000 -- ALL AT RATED

**`MemAvailable` 2,347,476 kB is ABOVE the five-boot ~25-minute range**, which
CLAUDE.md records as 2,135,144 to 2,284,200 kB — higher than the top of it by
63,276 kB. **This boot has had a row run on it and those five had not**, so it
is not a like-for-like sixth member of that set and is not offered as one.

**`SwapFree` RECOVERED between S0 and here: 487,932 -> 666,876 kB, +178,944 kB
over 1,108 s.** S0 ended at 15.51% of `SwapTotal` and S1 starts at 21.20%.
**S-B3's floor is 10% (314,572 kB), so the margin at the start of S1 is
352,304 kB.** The recovery is recorded because the sampler that was supposed to
measure it took no readings; these are two points either side of an 18-minute
gap and no rate is claimed.

**Battery: 330 dC at 85 s -> 318 at 303.84 s -> 316/317 across S0 -> 312 at
1501.63 s.** The phone shed heat throughout the idle period. **S1 therefore
starts at 31.2 C, not at rest**, and S-C2's slope must be read against that.

### S1 — `q17_s1_conv_60m`. THE CONVERSATIONAL HOUR. Figures only, no write-up; the write-up comes after S2.

    GATE PASSED x1=2802000 a76=2253000 uptime_s=1572.88 wallclock=13:43:46

Conditions read in the SAME invocation as the gate and the launch: `AC powered:
true`, `status: 4`, `level: 100`, `temperature: 308`, `mWakefulness=Awake`,
`screen_off_timeout=30000`, `stay_on_while_plugged_in=15` — and the phone
UNLOCKED, which adb answering at all demonstrates, since GrapheneOS keeps the
port charging-only while locked.

`c0`, `-t 2`, `-c 1024`, `-lm none`, `-n 64`, `--load-state q17_state.bin`,
`--turns 60 --interval-s 60`, greedy, `chat_template=NONE`.
Ran uptime 1572.98 -> 5122.48, i.e. **3,549.50 s = 59.16 min**.

    rc                       0
    turns_done               60 of 60 requested, turns_overrun=0
    oom_score_adj_child      pre=-1000  post=200
    lmk_kill_lines           4  (= 2 processes, 2 ActivityManager lines)
    SwapFree min             623,100 kB at uptime 1593.32  (19.81% of SwapTotal)
    contamination rule       DID NOT TRIP
    survived                 YES -- rc=0, turns_done==60, no kill naming
                             pennyload, oom post=200 printed

**Contamination, both limbs.** `Cached` 1,230,620 -> 1,040,276, a fall of
**190,344 kB against a model of 1,081,454 kB** — 17.6% of the model, not
"roughly the model size". `SwapFree` never below 623,100 kB = 19.81%, against
the 10% floor of 314,572 kB. Neither limb met.

    PENNYLOAD prefix_snapshot_bytes=46683597 got=46683597 t_snapshot_ms=19.61
    PENNYLOAD turns=60 interval_s=60 n_gen_per_turn=64
    PENNYLOAD turns_done=60 turns_requested=60 turns_overrun=0 fnv_all_equal=1
    PENNYLOAD first_token_id=32313 token_fnv1a64=0xcba17a2fcbba49f4   (turn 1)
    PENNYLOAD ttft_turn_ms   min=345.04 median=354.39 max=391.12
    PENNYLOAD gen_tps        min=14.09 median=14.49 max=15.17
    PENNYLOAD quarters       n_per_quarter=15  q1=turns 1-15  q4=turns 46-60
    PENNYLOAD SETTLED ttft_turn_ms q1_median=355.16 q4_median=353.61 decline_pct=-0.44
    PENNYLOAD SETTLED gen_tps      q1_median=14.49  q4_median=14.52  decline_pct=-0.20
    PENNYLOAD SETTLED gen_tps_turn1=15.17 settled_pct_of_turn1=95.66
    PENNYLOAD DUTY busy_median_ms=4704.44 interval_ms=60000 duty_pct=7.84

`decline_pct` positive means SLOWER. **Both are negative: the last quarter was
fractionally faster than the first.** The snapshot is 1,640 B smaller than the
46,685,237 B state file — the session header, the same shape the 2B smoke test
showed at 1,664 B.

**PER-TURN TABLE — first five, every tenth, last five.**

      k  uptime_s  t_restore  t_user_dec  ttft_turn    gen_ms  gen_tps   busy_ms ovr
      1   1576.99      13.38      366.20     380.40   4151.59    15.17   4532.06 0
      2   1636.99      13.31      351.37     364.87   4350.53    14.48   4715.44 0
      3   1696.99      19.96      328.29     348.42   4275.89    14.73   4624.48 0
      4   1756.99      15.09      339.85     355.16   4352.37    14.47   4707.64 0
      5   1816.99      18.31      327.95     346.44   4289.58    14.69   4636.16 0
     10   2117.00      15.66      330.02     345.86   4374.81    14.40   4720.77 0
     20   2717.00      17.78      331.84     349.80   4325.98    14.56   4675.91 0
     30   3317.01      13.73      334.98     348.90   4358.79    14.45   4707.86 0
     40   3917.01      13.27      341.22     354.67   4358.21    14.46   4712.99 0
     50   4517.02      19.88      334.93     354.99   4332.82    14.54   4688.00 0
     56   4877.02      13.64      333.47     347.30   4386.54    14.36   4733.92 0
     57   4937.02      19.87      348.14     368.23   4329.45    14.55   4697.82 0
     58   4997.02      11.48      339.02     350.69   4379.99    14.38   4730.74 0
     59   5057.02      16.47      344.68     361.33   4409.45    14.29   4770.94 0
     60   5117.02      17.77      345.38     363.34   4416.91    14.26   4780.56 0

    t_restore_ms    min  11.48   median  15.86   max  23.81
    t_user_decode   min 327.95   median 338.92   max 370.34
    busy_ms         min 4532.06  median 4704.43  max 4846.66
    gen_tokens      64 on every one of the 60 turns

**ONE distinct `fnv` across all sixty turns: `0xcba17a2fcbba49f4`** — the value
r1, r2, r3, r5 and S0 all produced. Sixty in-memory state restores in one
process leave no residue.

**THE TWO KILLS, with `MemAvailable` before the row.**

    MemAvailable before the row   2,332,040 kB   (first series sample 2,269,872)

    13:43:50.664 lowmemorykiller Kill '.ShannonImsService' (2426), uid 10154,
                 oom_score_adj 945 to free 142640kB rss, 0kB anon rss,
                 51380kB swap; reason: min watermark is breached
    13:43:50.715 lowmemorykiller Kill 'com.shannon.rcsservice:shannonrcsservice'
                 (2428), uid 10151, oom_score_adj 935 to free 148460kB rss,
                 9628kB anon rss, 41724kB swap; reason: min watermark is
                 breached even after kill
    13:43:50.750 ActivityManager .ShannonImsService (pid 2426) has died: cch +45 CEM
    13:43:50.763 ActivityManager com.shannon.rcsservice (pid 2428) has died: cch +35 CEM

Against the series: sample 1 at uptime 1573.63 reads `MemAvailable 2,269,872`,
sample 2 at 1583.17 reads `963,264`. **Both kills fall between those two
samples — inside the model load, ~4 s into a 3,550 s row. ZERO kills in the
remaining 59 minutes.**

**SERIES SUMMARY — 355 samples, uptime 1573.63 -> 5113.08.**

    column            min                 at uptime      max                 at uptime
    ceil_x1           2,802,000            1573.63       2,802,000            1573.63
    ceil_a76          2,253,000            1573.63       2,253,000            1573.63
    ceil_a55          1,803,000            1573.63       1,803,000            1573.63
    MemAvailable_kB     911,212            4523.19       2,269,872            1573.63
    MemFree_kB           79,060            4023.36       1,197,464            1573.63
    SwapFree_kB         623,100            1593.32         667,132            1573.63
    Cached_kB         1,028,524            4043.38       1,236,320            1573.63
    VmRSS_kB              9,844            1573.63       1,548,652            5063.25
    VmHWM_kB              9,844            1573.63       1,548,660            5063.25
    pswpout             733,248            1573.63         778,774            4043.38
    pgmajfault          123,500            1573.63         144,684            5113.08
    batt_temp_dC            301            1703.28             328            5113.08
    batt_level              100            1573.63             100            1573.63

**THE LAST LINE OF THE SERIES, VERBATIM — S-B2 IS SCORED ON THE END OF THE ROW,
NOT ON THE MINIMUM:**

    uptime_s ceil_x1 ceil_a76 ceil_a55 MemAvailable_kB MemFree_kB SwapFree_kB Cached_kB VmRSS_kB VmHWM_kB pswpout pgmajfault batt_temp_dC batt_level
    5113.08 2802000 2253000 1803000 932128 111844 662268 1040272 1548652 1548660 778774 144684 328 100

**`MemAvailable` at the END of the row is 932,128 kB.** The committed prediction
was a point of ~900,000 kB with a 600,000-1,400,000 band; 932,128 is **3.57%
above the point**. The minimum of 911,212 kB at uptime 4523.19 is 2.24% below
the end figure, so the two do not disagree.

**`VmRSS` moved 2,756 kB across the hour** — 1,545,896 at the second sample to
1,548,652 at the second-to-last. Sixty restores and sixty generations, and the
resident set is flat.

**THE CEILINGS, AND THE TWO SOURCES DO NOT AGREE.**

    PENNYBENCH ceil_x1_kHz     before 2,802,000  min 1,826,000  after 2,802,000
    PENNYBENCH ceil_x1_min_at  uptime=3440.91   (1868 s into the row)
    PENNYBENCH ceil_a76_kHz    before 2,253,000  min 2,253,000  after 2,253,000
    series at rated:  X1 355/355 = 100.00%   A76 100.00%   A55 100.00%

**The wrapper's 0.2 s poll caught the X1 ceiling at 1,826,000 kHz = 65.17% of
rated, 31 minutes into the row. The 10-second series never sampled it.** So
"100.00% of series samples at rated" is a fact about the SAMPLING RATE, not
about the clock, and the two figures must always be quoted together. A 4.7 s
turn does dip the ceiling; 55 s of idle restores it long before the next turn.
**`policy0` held 1,803,000 at all 355 samples — the first time the A55 cluster
has been watched through a row in this repo.**

**BATTERY TEMPERATURE — BATTERY, NOT SoC.** `/sys/class/thermal/` is
`Permission denied` to the shell user on this build.

    start (first series sample, uptime 1573.63)   306 dC = 30.6 C
    min                                           301 dC at uptime 1703.28
    max                                           328 dC at uptime 5113.08
    end (wrapper after-reading)                   328 dC = 32.8 C
    dumpsys cross-check                           308 before, 327 after
    slope start -> end   +22 dC over 3539.45 s  =  +0.0373 C/min

It FELL for the first two minutes (306 -> 301) while the phone shed the
reboot's heat, then climbed. 2.2 C over an hour at 7.84% duty.

**PEAK RSS.**

    peak_rss_kB      1,548,672  = 1.4771 GiB
    max_vmrss_kB     1,548,664
    max_rssanon_kB   1,543,040  = 99.64% anonymous
    max_rssfile_kB       5,336
    rss_samples          6,592

48,460 kB above r3's 1,500,212 kB on the same cached path. The in-memory prefix
snapshot is 46,683,597 B = 45,589 kB, which accounts for 94% of the difference.
**Consistent, not established** — nothing here isolates it.

**OTHER CONDITIONS.**

    uptime_s     before 1572.98   after 5122.48
    memavail_kB  before 2,332,040 after 2,477,280   (after = child exited)
    memfree_kB   before 1,260,944 after 1,658,484
    swapfree_kB  before   667,132 after   662,268
    cached_kB    before 1,230,620 after 1,040,276
    pswpin       before   110,201 after   130,969
    pswpout      before   733,248 after   778,774
    pgmajfault   before   123,500 after   144,692
    ZRAM         544,888K physical for 2,283,776K in swap (3,145,724K total)

### WHAT S1 DOES NOT SAY

**One row, once.** Sixty turns, one boot, one handset, no error bars, nothing
reproduced. **The hour was 7.84% busy** — 4.7 s of work then 55 s of idle, sixty
times — so it says nothing about continuous use, which is what S2 asks. The X1
ceiling dipped to 65.17% inside it and recovered every time; that recovery is
what the gap buys, and S2 removes it.

**`survived` is at `oom_score_adj 200`, which is an imitation and the margin was
never tested.** This was a shell process launched over adb with its adj raised
by the wrapper, not a foreground service started from a boot broadcast. The two
processes the killer took were at 945 and 935 — the cached band — so **the
killer never came near 200.** S-B1 passed without the question being pressed.

**The prefix is the same 20 tokens every turn and the model never sees its own
output.** Every turn begins from an identical 407-token state, so nothing tests
a growing conversation, a longer prompt, or a context that fills. The KV cache
is restored, never extended.

**Nothing was judged for quality, and sixty identical answers are not sixty
answers.** All sixty generations are byte-identical by construction — that is
what `fnv_all_equal=1` means. The only text sample on this model, from S0, gets
two of its three facts about Canberra wrong.

**Battery temperature is not SoC temperature**, and an hour at 100% on AC says
nothing about a phone on battery. On the 6a; the 7a re-measures anything that
fails here. **No prediction is scored in this entry** — the scorecard belongs to
the closing entry, after S2.

### S2 — `q17_s2_b2b_15m`. TWO HUNDRED TURNS BACK TO BACK. Figures only.

    GATE PASSED x1=2802000 a76=2253000 uptime_s=5445.44 wallclock=14:48:19

Conditions read in the SAME invocation as the gate and the launch: `AC powered:
true`, `status: 2`, `level: 100`, `temperature: 330`, `mWakefulness=Awake`,
`screen_off_timeout=30000`, `stay_on_while_plugged_in=15`, phone UNLOCKED.
`c0`, `-t 2`, `-c 1024`, `-lm none`, `-n 64`, `--load-state q17_state.bin`,
`--turns 200 --interval-s 0`.
Ran uptime 5445.52 -> 7007.03 = **1,561.51 s = 26.03 min.**

    rc                       0
    turns_done               200 of 200 requested, turns_overrun=0
    oom_score_adj_child      pre=-1000  post=200
    lmk_kill_lines           0
    SwapFree min             708,248 kB at uptime 6166.45  (22.51% of SwapTotal)
    contamination rule       DID NOT TRIP
    survived                 YES -- rc=0, turns_done==200, zero kills, oom
                             post=200 printed

**Contamination.** `Cached` 1,046,980 -> 1,015,764, a fall of **31,216 kB
against a model of 1,081,454 kB** — 2.9%. `SwapFree` never below 708,248 kB =
22.51%, floor 314,572 kB. Neither limb met. **ZERO kills, with `MemAvailable`
before the row 2,313,308 kB.**

    PENNYLOAD prefix_snapshot_bytes=46683597 got=46683597 t_snapshot_ms=17.40
    PENNYLOAD turns=200 interval_s=0 n_gen_per_turn=64
    PENNYLOAD turns_done=200 turns_requested=200 turns_overrun=0 fnv_all_equal=1
    PENNYLOAD first_token_id=32313 token_fnv1a64=0xcba17a2fcbba49f4   (turn 1)
    PENNYLOAD ttft_turn_ms   min=404.58 median=876.49 max=933.30
    PENNYLOAD gen_tps        min=8.06 median=9.09 max=15.04
    PENNYLOAD quarters       n_per_quarter=50  q1=turns 1-50  q4=turns 151-200
    PENNYLOAD SETTLED ttft_turn_ms q1_median=870.88 q4_median=897.01 decline_pct=3.00
    PENNYLOAD SETTLED gen_tps      q1_median=9.22  q4_median=8.24  decline_pct=10.64
    PENNYLOAD SETTLED gen_tps_turn1=15.04 settled_pct_of_turn1=54.77
    PENNYLOAD DUTY busy_median_ms=7806.34 interval_ms=0 duty_pct=-1.00

`duty_pct=-1.00` is the sentinel for `interval_ms=0`; duty is 100% by
construction on this row. `turns_overrun=0` is likewise definitional with no
interval and carries no information here.

**PER-TURN TABLE — first five, every twentieth, last five.**

       k  uptime_s  t_restore  t_user_dec  ttft_turn    gen_ms  gen_tps   busy_ms
       1   5449.65      11.62      392.22     404.58   4188.53    15.04   4593.16
       2   5454.25       7.99      406.29     414.49   4402.00    14.31   4816.54
       3   5459.06      10.00      438.72     448.93   4432.03    14.21   4881.00
       4   5463.94       8.06      449.75     458.02   4504.75    13.99   4962.82
       5   5468.91       8.79      465.70     474.70   4615.48    13.65   5090.23
      20   5554.03      12.25      614.70     627.22   5231.76    12.04   5859.03
      40   5705.91      11.84      857.38     869.67   6837.69     9.21   7707.41
      60   5860.74      13.36      860.55     874.36   6887.42     9.15   7761.85
      80   6016.05      17.05      857.45     874.89   6936.91     9.08   7811.87
     100   6171.62      14.35      864.82     879.62   7005.76     8.99   7885.43
     120   6329.01      15.00      864.94     880.39   7209.31     8.74   8089.76
     140   6490.44      15.24      862.54     878.22   7172.81     8.78   8051.10
     160   6654.48      16.67      893.62     910.71   7480.93     8.42   8391.71
     180   6824.96      21.40      884.98     906.83   7615.39     8.27   8522.27
     196   6962.35      17.37      905.04     922.85   7740.06     8.14   8662.97
     197   6971.02      20.03      896.16     916.59   7679.54     8.20   8596.21
     198   6979.61      15.64      883.41     899.51   7818.63     8.06   8718.23
     199   6988.33      17.39      902.17     919.99   7750.82     8.13   8670.88
     200   6997.00      19.63      899.73     919.81   7623.53     8.26   8543.41

    ttft_turn_ms  min 404.58  median 876.49  max 933.30
    gen_tps       min   8.06  median   9.09  max  15.04
    t_restore_ms  min   7.99  median  15.00  max  21.41
    busy_ms       min 4593.16 median 7806.34 max 8718.23

**ONE distinct `fnv` across all two hundred turns: `0xcba17a2fcbba49f4`.**
Two hundred restores, `fnv_all_equal=1`, identical to S1, S0 and rows 1/2/3/5.

**THE DECAY IS FRONT-LOADED AND THEN NEARLY FLAT.** `gen_tps` falls 15.04 ->
9.21 over the first forty turns (turn 40 at uptime 5705.91, 260 s in), then
9.21 -> 8.26 over the remaining 160 turns and 1,291 s. **Most of the loss
happens in the first four minutes.**

**S-A3, SCORED, WITH THE ARITHMETIC.**

    settled = median of the LAST QUARTER = median of turns 151-200 = 8.24 t/s
    turn 1                                                        = 15.04 t/s
    8.24 / 15.04 = 0.5477   ->   54.77% of turn 1
    FAIL CONDITION: settled BELOW 50% of turn 1
    54.77% > 50%   ->   **PASS, by 4.77 percentage points**

**The committed POINT was 80% of turn 1 with a band of 70-92%. Measured 54.77%
is OUTSIDE that band, low.** The prediction was built from the cooled C-series,
whose deepest matched pair (C5 against C6, unpinned, 4 threads) showed tg down
18.87% at an X1 floor of 30.4% of rated — and from that a ~20% decline was
predicted. **The measured decline against turn 1 is 45.2%.** The C-series rows
were 52-247 s; this row was 1,561 s. **The arithmetic underestimated, and it
underestimated because it extrapolated from rows an order of magnitude shorter.**
Recorded as a band miss with the fail condition passed.

**SERIES SUMMARY — 157 samples, uptime 5446.15 -> 7006.20.**

    column            min                 at uptime      max                 at uptime
    ceil_x1             984,000            5576.16       2,802,000            5446.15
    ceil_a76            696,000            6946.47       2,253,000            5446.15
    ceil_a55            738,000            5576.16       1,803,000            5446.15
    MemAvailable_kB     865,388            5466.17       2,431,828            7006.20
    MemFree_kB           86,488            6946.47       1,638,504            7006.20
    SwapFree_kB         708,248            6166.45         853,500            5446.15
    Cached_kB           999,776            5456.28       1,052,688            5446.15
    VmRSS_kB              9,948            5446.15       1,548,932            6936.48
    VmHWM_kB              9,948            5446.15       1,548,952            7006.20
    pswpout             782,312            5446.15         823,319            6166.45
    pgmajfault          195,923            5446.15         203,465            7006.20
    batt_temp_dC            326            5476.34             407            6696.07
    batt_level              100            5446.15             100            5446.15

**LAST LINE OF THE SERIES, VERBATIM — AND IT IS A TEARDOWN SAMPLE, NOT AN
END-OF-ROW SAMPLE:**

    uptime_s ceil_x1 ceil_a76 ceil_a55 MemAvailable_kB MemFree_kB SwapFree_kB Cached_kB VmRSS_kB VmHWM_kB pswpout pgmajfault batt_temp_dC batt_level
    7006.20 984000 799000 738000 2431828 1638504 722584 1015764 156676 1548952 823319 203465 407 100

`VmRSS` reads **156,676 kB** against a `VmHWM` of 1,548,952 — the process was
already releasing memory when this sample was taken, which is why
`MemAvailable` reads its maximum here. **The resident-state figure is the
minimum, 865,388 kB at uptime 5466.17**, and S-B2 is not scored on this row.

**THE CEILINGS, FROM BOTH SOURCES, AND THIS TIME THEY AGREE.**

    PENNYBENCH ceil_x1_kHz     before 2,802,000  min 984,000  after 984,000
    PENNYBENCH ceil_x1_min_at  uptime=5574.61   (129 s into the row)
    PENNYBENCH ceil_a76_kHz    before 2,253,000  min 696,000  after 799,000
    PENNYBENCH ceil_a76_min_at uptime=6943.69   (1498 s into the row)

    series at rated:  X1    1 of 157 =  0.64%   series min   984,000 = 35.12%
                      A76  96 of 157 = 61.15%   series min   696,000 = 30.89%
                      A55  42 of 157 = 26.75%   series min   738,000 = 40.93%

**The X1 ceiling reached its floor 129 seconds in and NEVER RECOVERED — the
`after` reading is still 984,000.** S1's after-reading was 2,802,000. Only ONE
of 157 samples read rated, and that was the first, taken before the model had
loaded.

**THE A55 CLUSTER THROTTLES. THIS IS THE FIRST TIME `policy0` HAS BEEN SEEN TO
MOVE IN THIS REPO.** It fell to 738,000 kHz = 40.93% of its 1,803,000 rated
clock, and spent 115 of 157 samples below rated — on a row where `taskset c0`
scheduled nothing onto it. That is the same package-wide behaviour the 16 Sept
cooled matrix found for `policy4`, now observed on the third cluster. S1, at
7.84% duty, held all three at rated in every one of its 355 samples.

**BATTERY TEMPERATURE — BATTERY, NOT SoC.**

    start (first series sample, uptime 5446.15)   328 dC = 32.8 C
    min                                           326 dC at uptime 5476.34
    max                                           407 dC at uptime 6696.07
    end (wrapper after-reading)                   407 dC = 40.7 C
    dumpsys cross-check                           330 before, 406 after
    slope start -> end   +79 dC over 1560.05 s  =  +0.304 C/min

**Against S1's +0.0373 C/min: 8.2x the rate.** It reached its maximum at uptime
6696.07, 1,250 s in, and held 407 for the last 310 s — so it was levelling off,
not still climbing, by the end.

**PEAK RSS AND OTHER CONDITIONS.**

    peak_rss_kB      1,548,952   = 1.4772 GiB   (S1: 1,548,672 -- 280 kB apart)
    max_rssanon_kB   1,544,036   = 99.68% anonymous
    max_rssfile_kB       4,804
    rss_samples          2,785
    uptime_s     before 5445.52   after 7007.03
    memavail_kB  before 2,313,308 after 2,438,028   (after = child exited)
    swapfree_kB  before   853,500 after   722,584
    cached_kB    before 1,046,980 after 1,015,764
    pswpin       before   182,186 after   189,535
    pswpout      before   782,312 after   823,319
    pgmajfault   before   195,922 after   203,465
    ZRAM         536,136K physical for 2,404,708K in swap (3,145,724K total)

**THE ROW TOOK 26.03 MINUTES AGAINST A PREDICTED 15-19.** The prediction was
200 turns at a predicted settled 5.62 s each = 1,124 s; measured median
`busy_ms` is 7,806.34 and the row ran 1,561.51 s. **Outside the band, high**,
and for the same reason S-A3 missed low: the throttling is deeper than the
C-series extrapolation allowed for.

### WHAT S2 DOES NOT SAY

**One row, once**, on one boot, one handset, no error bars, nothing reproduced.
**It is the opposite extreme from S1, not a midpoint** — 100% duty for 26
minutes is as unrealistic a conversation as 7.84% duty is undemanding, and the
product shape is somewhere between them and is untested.

**Zero kills does not mean a resident model is free.** The row started with
`MemAvailable` 2,313,308 kB on a boot that had already killed two processes
during S1, so the cheap victims were partly spent — CLAUDE.md's own rule says a
kill count is meaningless without that context, and this one is flattered by it.

**`survived` is again at `oom_score_adj 200` and again untested**, because
nothing was killed at all.

**The thermal figure is a battery figure.** `/sys/class/thermal/` is unreadable
to the shell user, so what the SoC did is unknown, and 40.7 C on the battery
after 26 minutes at 100% duty on AC with the screen on says nothing about the
same work on battery with the screen off.

**Every generation is byte-identical by construction** and no output was judged.
The prefix is the same 407 tokens and the turn the same 20 tokens, two hundred
times; nothing tests a growing context. On the 6a.

### CEILING RECOVERY AFTER A 1,561 s LOAD — A READING, ONE BOOT, NOT A BASELINE. And it is a weak one.

Sampling was armed immediately after S2 to read both ceilings and battery
temperature every 10 s until `policy6` read 2,802,000 AND `policy4` read
2,253,000. **It produced exactly ONE sample, and all three clusters were
already at rated in it:**

    uptime_s=7906.21 wallclock=15:29:20 x1=2802000 a76=2253000 a55=1803000 batt_temp_dC=321

    S2's AFTER reading   uptime 7019.81   wallclock 15:14:33
    first sample         uptime 7906.21   wallclock 15:29:20
    elapsed                886.40 s = 14.77 min

**THIS IS AN UPPER BOUND AND NOTHING MORE.** Both clusters had already
recovered when the first sample was taken, so the only true statement is that
recovery took **at most 886.40 s**. Neither the time each cluster returned nor
the shape of the curve was observed.

**AND THE 886.40 s IS ITSELF UNRELIABLE AS A BOUND.** The loop was armed at
about 15:17 and its first sample is stamped 15:29:20 — a gap of roughly twelve
minutes that is **not explained here and should not be guessed at.** Had the
loop run when it was armed, the bound would have been ~150-180 s instead, and
whether both clusters were already rated at that point is unknown. **So the
figure to carry forward is: recovery from a 1,561 s row completed within
886.40 s, and the repo's only other datum remains 16 Sept's 109.8 s from a 65 s
load.** Nothing here refines it.

Battery over the same window: **407 dC at S2's close -> 321 dC at 15:29:20 ->
317 dC at 15:30:14**, i.e. 40.7 C to 31.7 C in about 16 minutes at idle on AC.

## 2026-09-18 — BRIEF S, CLOSED. A resident model with a cached prefix answers once a minute for an hour with NO measurable decay; run back to back it loses 45% of its speed in the first four minutes and then holds. Every row survived; nothing was ever killed above adj 935.

The closing entry for brief S. **Two measured rows, one boot, one model.**
Everything below is quoted from a row entry by its tag, and every figure in
those came from `out/<tag>.report`, `.bench` and `.series` on the phone.
Nothing here is a new measurement.

    boot 4   18 Sept, Qwen3-1.7B-Q4_K_M    q17_s0_regen (not a result row),
                                           q17_s1_conv_60m, q17_s2_b2b_15m
    S3       NOT RUN -- Matt's call, not taken

Both rows `rc=0`, both gated on `policy6` = 2,802,000 AND `policy4` = 2,253,000
in the launching invocation, both `c0`, `-t 2`, `-c 1024`, `-lm none`, `n_ctx`
1024, `-n 64`, greedy, `chat_template=NONE`, `--load-state q17_state.bin`.
AC power, screen on, unlocked, app `pm disable-user`'d, `Running VMs: []`.
**Neither row met the contamination condition on either limb.**

### THE SCORECARD

**The brief named NINE conditions; I committed SEVEN further point estimates
alongside them. Sixteen in all, not twelve.** All sixteen are below; none has
been moved, and the three that miss their band are marked as misses even where
the fail condition passes.

    #     prediction                        point    band          fail if        measured        verdict      band
    ----  --------------------------------  -------  ------------  -------------  --------------  -----------  ------
    S-A1  S1 ttft_turn settled decline       3%      0-8%          > 25%          -0.44%          PASS         MISS
    S-A2  S1 gen_tps settled decline         3%      0-8%          > 25%          -0.20%          PASS         MISS
    S-A3  S2 gen_tps settled / turn 1       80%      70-92%        < 50%          54.77%          PASS         MISS
    S-A4  fnv_all_equal on every row          1      --            any 0          1 and 1         PASS         n/a
    S-B1  S1 survives                       yes      --            otherwise      YES             PASS         n/a
    S-B2  MemAvailable, end of S1      ~900,000 kB   0.6-1.4 GB    kills past     932,128 kB      PASS         HIT
                                                                   minute 5       none past 4 s   PASS
    S-B3  SwapFree through S1          ~650,000 kB   0.4-1.0 GB    < 314,572      623,100 kB      PASS         HIT
    S-C1  X1 at rated, S1 series           97%       >= 95%        --             100.00%         PASS         HIT
    S-C2  battery temperature          no prediction --            --             reported        n/a          n/a
    ----  derived point estimates, committed in the same entry
    D1    ttft_turn, S1 turn 1             0.39 s    0.33-0.50 s   --             380.40 ms       --           HIT
    D2    turn busy time, S1               4.57 s    4.0-5.5 s     --             4704.43 ms      --           HIT
    D3    S1 duty                           7.6%     --            --             7.84%           --           HIT
    D4    S2 duration                        17 min  15-19 min     --             26.03 min       --           **MISS**
    D5    prefix_snapshot_bytes      ~46,684,000 B   46.0-47.0 MB  --             46,683,597 B    --           HIT
    D6    t_snapshot_ms                      12 ms   5-25 ms       --             19.61 / 17.40   --           HIT
    D7    t_restore per turn                 15 ms   8-25 ms       --             15.86 / 15.00   --           HIT

**EVERY FAIL CONDITION PASSED. FOUR BANDS MISSED: S-A1, S-A2, S-A3 and D4.**

S-A1 and S-A2 miss **below** their bands — I predicted a 3% decline over the
hour and measured a slight improvement, so the band was wrong in the direction
of pessimism and the fail condition was never in danger.

**S-A3 and D4 miss for the same reason and it is the substantive error in this
brief.** S-A3's 80% point came from the cooled C-series' deepest matched pair
(C5 against C6: tg down 18.87% at an X1 floor of 30.4% of rated), and D4's 17
minutes came from assuming that decline. **Those C rows were 52-247 s; S2 ran
1,561 s.** Extrapolating across an order of magnitude in duration underestimated
the throttling: measured decline against turn 1 is **45.2%**, not ~20%, and the
row took **26.03 min**, not 17. S-A3 still passes because 54.77% clears the 50%
floor — **by 4.77 percentage points.**

**S-A4 is the one I would most have wanted to fail and it did not.** `gen_tps`
and TTFT are timings and could drift for a dozen reasons; the fnv is a
correctness check. `0xcba17a2fcbba49f4` on all 60 of S1's turns and all 200 of
S2's — **260 in-memory state restores in two processes, no residue** — and the
same value r1, r2, r3, r5 and S0 produced, on two binaries and three boots.

### S-A, ANSWERED: "does the per-turn TTFT and tok/s settle at a floor or keep falling"

**At one turn a minute: they do not fall at all.** Over 60 turns and 59.16
minutes at 7.84% duty:

    SETTLED ttft_turn_ms  q1_median 355.16   q4_median 353.61   decline -0.44%
    SETTLED gen_tps       q1_median  14.49   q4_median  14.52   decline -0.20%
    ttft_turn_ms  min 345.04  median 354.39  max 391.12
    gen_tps       min  14.09  median  14.49  max  15.17

The last quarter of the hour was **fractionally faster** than the first.
Turn 60 ran at 14.26 t/s against turn 1's 15.17.

**Back to back they fall hard, fast, and then very nearly settle.** Over 200
turns and 26.03 minutes at 100% duty:

    turn   1   gen_tps 15.04      turn  40   9.21   (260 s in)
    turn  60         9.15         turn 100   8.99
    turn 140         8.78         turn 200   8.26
    SETTLED gen_tps  q1_median 9.22  q4_median 8.24  decline 10.64%
    settled / turn 1 = 8.24 / 15.04 = 54.77%

**15.04 -> 9.21 over the first 40 turns and 260 seconds; 9.21 -> 8.26 over the
remaining 160 turns and 1,291 seconds.** 87% of the total loss happens in the
first 17% of the row. So the answer is **"falls, then settles"** — it does reach
a floor, and the floor is a little over half the cool figure.

TTFT tracks it: 404.58 ms on turn 1, 876.49 ms median, 933.30 ms max.

### S-B, ANSWERED: "what does the phone do to memory over that hour"

**S1, the hour.** `MemAvailable` 2,269,872 kB at the first series sample (model
not yet loaded) -> **932,128 kB at the last**, minimum 911,212 kB at uptime
4523.19. `SwapFree` 667,132 -> minimum 623,100 (19.81% of `SwapTotal`) ->
662,268. `Cached` fell 190,344 kB against a 1,081,454 kB model — 17.6%, nowhere
near the contamination threshold. **`VmRSS` moved 2,756 kB across the whole
hour**, 1,545,896 to 1,548,652. Peak RSS 1,548,672 kB = 1.4771 GiB, 99.64%
anonymous.

**Kills: two processes, both in the first four seconds, both at model load.**
`.ShannonImsService` at `oom_score_adj 945` and `com.shannon.rcsservice` at
`935`, reason `min watermark is breached`, with `MemAvailable` before the row
2,332,040 kB. **Zero kills in the remaining 59 minutes.**

**S2, the quarter-hour at full duty: ZERO kills**, `SwapFree` minimum 708,248
(22.51%), `MemAvailable` minimum 865,388, `Cached` down 31,216 kB (2.9% of the
model). Peak RSS 1,548,952 kB — **280 kB from S1's**, across a completely
different duty cycle.

**WHAT "SURVIVED AT adj 200" ESTABLISHES, AND WHAT IT DOES NOT.** Both rows met
the committed definition in full: `rc=0`, `turns_done == --turns`, no kill line
naming `pennyload`, and `oom_score_adj_child post=200` printed and read back
off `/proc`. **That establishes that the wrapper's raise works, that the process
ran to completion at a foreground-service-band adj, and that nothing in 86
minutes of resident 1.47 GiB caused the killer to reach it.**

**It does NOT establish that the process would survive pressure.** The deepest
the killer went on either row was **adj 935**, and it stopped after two cached
processes four seconds into S1. **It never came within seven hundred points of
200.** S-B1 passed without the question being pressed, and the honest statement
is "the phone never tried", not "our process withstood it". 3g-ii's 2 GB VM
drove the killer to adj 201; a 1.47 GiB resident model on this boot drove it to
935.

### S-C, ANSWERED: the ceilings and the battery

**BETWEEN TURNS, at one a minute, the clock is at rated — with one figure that
must always travel beside it.**

    S1  series:     X1 355 of 355 samples at 2,802,000 = 100.00%
                    A76 100.00%   A55 100.00%
        poll loop:  X1 min 1,826,000 = 65.17% of rated, at uptime 3440.91,
                    1868 s into the row; after-reading 2,802,000

**The 10-second series says the X1 pair never left rated; the 0.2-second poll
caught it at 65.17% half an hour in.** The dip is real and shorter than ten
seconds. **"100.00% at rated" is a fact about the sampling rate, not about the
clock**, and this repo must not quote it alone. What it does show is that 55
seconds of idle fully restores the ceiling before the next turn — which is the
thing S-C1 asked.

**BACK TO BACK, THE FLOOR IS REACHED IN TWO MINUTES AND NEVER LEFT, AND THE A55
CLUSTER THROTTLES.**

    S2  poll loop:  X1  min 984,000 = 35.12%, at 129 s in; AFTER still 984,000
                    A76 min 696,000 = 30.89%, at 1498 s in; after 799,000
        series:     X1    1 of 157 at rated =  0.64%   (that one was sample 1)
                    A76  96 of 157          = 61.15%
                    A55  42 of 157          = 26.75%, min 738,000 = 40.93%

**`policy0` throttles. This is the first time it has been observed to move in
this repo** — CLAUDE.md has twice named it as never sampled during a row. It
fell to 40.93% of its 1,803,000 rated clock and spent 115 of 157 samples below
rated **on a row where `taskset c0` scheduled nothing onto it at all.** That is
the package-wide limiter the 16 Sept cooled matrix found on `policy4`, now seen
on the third cluster, and it is the direct evidence for "the limiter acts across
the package, not per cluster".

**S2's X1 ceiling never recovered inside the row**: its `after` reading is
984,000 where S1's was 2,802,000.

**BATTERY TEMPERATURE — BATTERY, NOT SoC.** `/sys/class/thermal/` is
`Permission denied` to the shell user on this build, so no SoC temperature
exists anywhere in this brief.

    row   start      min              max              end     slope
    S1    306 dC     301 at 1703.28   328 at 5113.08   328 dC  +0.0373 C/min
    S2    328 dC     326 at 5476.34   407 at 6696.07   407 dC  +0.304 C/min

**S2 heats 8.2x faster than S1**, and reached 40.7 C at 1,250 s in and held
there for the final 310 s — levelling off, not still climbing. S1 *fell* for its
first two minutes, still shedding the reboot's heat. `dumpsys battery` agreed
within 1-3 dC at every check and **lagged sysfs**, updating on broadcasts.

### WHAT BRIEF S DOES NOT SAY

**One boot, one handset, one model, one row each.** No error bars, nothing
reproduced, and S1 and S2 ran consecutively on the same boot so S2 inherited
whatever S1 left.

**The two rows are the extremes, and the product is between them.** 7.84% duty
and 100% duty. A real assistant is neither. **Nothing here measures the shape in
between**, and S-A3's answer — settles at 54.77% — is a fact about back-to-back
work, not about a phone someone talks to.

**The user turn is the same 20 tokens, 260 times, and the context never grows.**
Every turn restores an identical 407-token prefix; the model never sees its own
output, never continues a conversation, and the KV cache is never extended.
**A growing context is untested and is the obvious next thing.**

**No output was judged, and 260 identical answers are not 260 answers.**
`fnv_all_equal=1` means every generation was byte-identical by construction. The
only text on this model in this brief, from S0, gets two of its three facts
about Canberra wrong.

**No STT and no TTS, and no second model in memory.** The bake-off — Kokoro-82M
and `kokoro-onnx` int8 under sherpa-onnx, with STT and the LLM resident
together — is the next item and nothing here touches it. This measured ONE model
holding 1.47 GiB.

**Battery temperature is not SoC temperature. AC power is not battery.** Both
rows ran at 100% charge on mains with the screen on. What this silicon does on
battery, with the screen off, is unmeasured.

**`oom_score_adj 200` is an imitation of a foreground service, not one.** These
were shell processes launched over adb with their adj raised by the wrapper, not
services started from a boot broadcast. **And the killer never approached 200**
— it stopped at 935.

**Nothing ran at boot.** The unattended wake path of rungs 3, 3b and 3d and
these sustained figures have still never been run together.

**Zero kills on S2 is flattered by S1.** S2 began on a boot that had already
killed two processes, so the cheap victims were partly spent — the repo's own
rule says a kill count means nothing without `MemAvailable` before it, and S2's
2,313,308 kB came partly from S1's casualties.

**A day is not an hour, and S3 was not run.** Whether the conversational shape
recovers after S2, or whether the boot is spent, is untested and was Matt's call
not to spend.

On the 6a; the 7a re-measures anything that fails here.

## 2026-09-18 — TTS RUNG 1: PREDICTIONS, written before the clone, the build or any run

Branch tts-kokoro, worktree ~/Documents/penny-app-spike-tts, created from main
at 86b716a. Nothing has been built and nothing has run on the phone.

The artefact under test, as it sits on the Mac (computed on the Mac, not
verified against any published hash, because none exists for this repackaging):

    ~/kokoro-models/penny-kokoro-int8/model.int8.onnx
      92,363,779 bytes
      sha256 a089794d1293b91e82f3f2b8bed5417d04ac64447d6ada5f21045cde0799bf99
    folder: 145M by du (disk blocks, 120+ espeak-ng-data files), brief says ~130 MB
    voices.bin 28,200,960 · tokens.txt 687 · lexicon-gb-en.txt 6,366,635
    speaker id 22 (bf_isabella), lang "en", 24 kHz

The 18 lines are the LINES list of ~/kokoro-models/abtest_sherpa.py, which
diffs identical to abtest_penny.py's. The Mac reference clips for step 4d are
~/kokoro-models/abtest-penny/NN-int8.wav (level-matched, which changes
amplitude and not sample count).

Predictions, copied from the brief unchanged. Each is judged in the rung 1
write-up, and changed before then only with a stated reason:

- **P-T1. Derived load** (wall − the binary's Elapsed: process start + model
  load + WAV write) **0.8–2.0 s** from flash.
- **P-T2. Generate RTF on the X1 pair at 2 threads, pinned: 0.3–0.8.** Short
  lines worse than long ones (fixed per-call overhead).
- **P-T3. Peak RSS of the process: 250–450 MB** (VmHWM).
- **P-T4. No clock descent visible within one 18-line pass** (policy6 and
  policy4 min equal to before across the pass).

One addition of my own, stated so it can fail:

- **P-T5. The phone's WAVs match the Mac clips' sample counts within 5 ms
  (120 samples at 24 kHz) on all three pulled lines.** Same model bytes, same
  lexicon, same espeak-ng-data; the only differences are sherpa-onnx version
  (Mac 1.13.8, phone = the commit built) and CPU arithmetic. If P-T5 fails, the
  front end (tokenisation / phonemisation) differs between the two builds, and
  that is a finding, not noise.

What these predictions do not cover: the app, resident vs reload (rung 2), the
LLM running beside TTS (rung 3), thermals beyond one pass, and pronunciation.

## 2026-09-18 — TTS RUNG 1, MAC SIDE: sherpa-onnx cloned, onnxruntime fetched and hash-matched, pennytts.sh written. NOT built: the build fetches nine more archives the brief did not list

Branch tts-kokoro. Mac only; the phone has not been touched from this branch.

**Clone.** `git clone --depth 1 https://github.com/k2-fsa/sherpa-onnx` into
~/Documents/sherpa-onnx. HEAD **a5b4a944c5186a68bcdc0ac3011e4c541781ac84**
(17 Sept 2026, "Add conversion scripts for 7M Kokoro distills"), the commit the
brief was checked against. 51M on disk including 7.2M of .git.

**onnxruntime.** No wget on this Mac, so fetched by curl into
~/Documents/sherpa-onnx-deps/ and unzipped there (the brief's fallback route):

    onnxruntime-android-1.28.2.zip   33,765,715 bytes
      sha256 01518867f78241138b6aa25925802e843a4fa9085af8d303d49e35bbb52aff4d
      MATCHES the hash sherpa-onnx pins at a5b4a94,
      cmake/onnxruntime-android-aarch64.cmake:18
    jni/arm64-v8a/libonnxruntime.so  ELF 64-bit LSB shared object, ARM aarch64
      sha256 33847ad43bffe204699fd4a27f7f3603452a8cdaf2f9a44983a0bc31ffcf2da1 (computed)

The build will use it via SHERPA_ONNX_ONNXRUNTIME_ROOT (read at
build-android-arm64-v8a.sh:38 and :97-99 when BUILD_SHARED_LIBS=ON, which is the
script's default at :15-16), so the script's own wget never runs.

**Toolchain.** NDK /opt/homebrew/share/android-commandlinetools/ndk/30.0.16248370
(the llama.cpp one, per build-pennyload.sh:11). cmake is not on PATH; the SDK's
cmake 3.22.1 is, at .../cmake/3.22.1/bin, and sherpa-onnx requires >= 3.15
(CMakeLists.txt:5). ninja at /opt/homebrew/bin/ninja.

**What the brief's download list missed.** build-android-arm64-v8a.sh configures
with cmake, and at configure time CMakeLists.txt:544-578 FetchContent-downloads
these from GitHub/GitLab. Each is sha256-pinned in sherpa-onnx's cmake/*.cmake, so
cmake refuses a file that does not match; GitHub archive URLs report no
Content-Length, so their sizes are not known before download:

    kaldi-native-fbank v1.22.3        (always)
    kaldi-decoder v0.3.0              (always)
    eigen 5.0.1 (gitlab.com)          (via kaldi-decoder)
    openfst v1.8.5-2026-07-09         (via kaldi-decoder)
    simple-sentencepiece v0.7         (always)
    nlohmann json v3.12.0             (always)
    espeak-ng @ed530aa (csukuangfj)   (TTS on)
    piper-phonemize @f3ff95a          (TTS on)
    hclust-cpp 2026-02-25             (speaker diarization on, the script's default)

Build is held until Matt approves these.

**Mac reference sample counts for step 4d** (abtest-penny/NN-int8.wav, written
by ~/kokoro-models/.venv's sherpa-onnx **1.13.8** Python wheel — NOT the a5b4a94
source the phone will run; a version difference is one explanation to rule out
if P-T5 fails):

    00 "On it."                 19,812 samples  0.826 s  24 kHz PCM_16
    05 "Revenue ... £12,480.50" 165,742 samples 6.906 s
    15 the long line            250,017 samples 10.417 s

**CLI defaults checked at a5b4a94, so the phone matches the Mac run:**
max_num_sentences 1 (offline-tts.h:34 — the Mac script set 1), speed 1.0
(passed explicitly anyway), kokoro length_scale 1.0.

**pennytts.sh**, at the worktree root beside pennybench.sh (which it copies in
shape). One line per invocation: `pennytts.sh <tag> <mask|none> <threads>
<0-17|text>`. The 18 lines are embedded by number so "£" and apostrophes never
cross adb quoting. Wall time is taken in the launching subshell immediately
either side of the binary, not by the 0.2 s poll loop (which would add up to
~0.3 s — the size of the thing being derived). COOL=1 (default) gates launch on
both clock ceilings reading rated; COOL=0 for lines 2-18 of a pass. Audio length
is read from the WAV header after checking the "data" tag at byte 36
(wave-writer.cc writes a canonical 44-byte int16 header). Outputs to
/data/local/tmp/tts/out/, separate from pennybench's /data/local/tmp/out. No
dumpsys per line (it would heat the package between lines of a pass).
Checked on the Mac: `sh -n` and `bash -n` pass; the ms parsing of wall and
Elapsed tested against sample values. **Not run under mksh or toybox**: the Mac
has neither, so `date +'%s %N'`, `od -tu4 -j` and fractional `sleep` on the
phone are unconfirmed until the first row. The script marks wall as `invalid`
rather than computing it from a non-numeric %N.

**Known limitation, stated before it bites:** a short line ("On it.") may run
only ~1 s, i.e. ~3 poll samples. VmHWM is monotonic, so the last sample is a
floor on the peak, not the peak, if the peak lands in the final <0.2 s. The
first row reports `rss_samples`; if it is under ~5, the peak for short lines is
quoted as "at least".

**A risk to check at the first run, not a finding:** the llama.cpp build hit
SIGILL from i8mm instructions this Tensor G1 does not have. The onnxruntime .so
here is a prebuilt that selects kernels at runtime (MLAS), so the same failure
is not expected — but it has not run on this CPU yet.

What this entry does not say: that sherpa-onnx builds with this NDK and cmake,
that the binary runs on the 6a, or anything measured on the phone.

## 2026-09-18 — TTS RUNG 1, BUILD NOT RUN: with ENABLE_BINARY=ON the script fetches ELEVEN archives, not nine — websocketpp and asio are also pulled, and neither is approved

Branch tts-kokoro, after merging main (5d1c4ac) as **8f519a1**. Mac only. The
phone has not been touched from this branch. Nothing was configured, compiled
or downloaded; `~/Documents/sherpa-onnx/build-android-arm64-v8a/` does not exist
and the clone's `git status` is clean.

**Record correction to the first entry's artefact list.**
~/kokoro-models/penny-kokoro-int8 also holds **lexicon-us-en.txt, 5,956,885 B**,
which that list left out. It will be pushed with the folder in step 2; the
runs use lexicon-gb-en.txt only.

**Why the build is held.** The command Matt approved was

    ANDROID_NDK=/opt/homebrew/share/android-commandlinetools/ndk/30.0.16248370 \
    SHERPA_ONNX_ONNXRUNTIME_ROOT=/Users/mattstevenson/Documents/sherpa-onnx-deps/onnxruntime-android-1.28.2 \
    SHERPA_ONNX_ENABLE_BINARY=ON ./build-android-arm64-v8a.sh

with nine archives approved and "anything beyond nine is a STOP". Reading the
cmake path at a5b4a94 before running it:

    CMakeLists.txt:55     option(SHERPA_ONNX_ENABLE_WEBSOCKET ... ON)
    CMakeLists.txt:562-565  if(SHERPA_ONNX_ENABLE_WEBSOCKET)
                              include(websocketpp)
                              include(asio)

build-android-arm64-v8a.sh:172-193 does not pass SHERPA_ONNX_ENABLE_WEBSOCKET
and reads no environment variable for it, so the option stays ON and configure
fetches two more:

    websocketpp @b9aeec6e  cmake/websocketpp.cmake:5,7
      SHA256=1385135ede8191a7fbef9ec8099e3c5a673d48df0c143958216cd1690567f583
    asio 1-24-0            cmake/asio.cmake:4,6
      SHA256=cbcaaba0f66722787b1a7c33afe1befb3a012b5af3ad7da7ff0f6b8c9b7a8a5b

Neither exists in ~/Downloads or /tmp (the local fallbacks those cmake files
check first). **This applies to the script's default build too, not only to
ENABLE_BINARY=ON** — the include is unconditional on the binary flag — so the
first entry's list of nine was short by two from the start. The websocket
executables themselves (sherpa-onnx/csrc/CMakeLists.txt:762) are only built
with ENABLE_BINARY=ON, and sherpa-onnx-offline-tts (:526) does not link them.

What is NOT fetched on this path, checked in the cmake source: onnxruntime
(cmake/onnxruntime.cmake:170-184 takes libonnxruntime.so from
SHERPA_ONNXRUNTIME_LIB_DIR, which the script sets at :98 from
SHERPA_ONNX_ONNXRUNTIME_ROOT; SHERPA_ONNX_USE_PRE_INSTALLED_ONNXRUNTIME_IF_AVAILABLE
defaults ON at CMakeLists.txt:74); cargs (only via c-api-examples, and C_API is
OFF at :160-162); pybind11, googletest, portaudio (PYTHON, TESTS, PORTAUDIO all
OFF). **Not checked**: whether any of the nine approved archives fetches
something of its own at configure time — that is only visible in the configure
log, and will be read there.

**A second thing the approved command would hit first.** `cmake` is not on PATH
(`which -a cmake`: not found), and the script calls it bare at :172 under
`set -ex`, so the command as written stops at "cmake: command not found" before
any download. The build needs
`PATH=/opt/homebrew/share/android-commandlinetools/cmake/3.22.1/bin:$PATH`
(cmake 3.22.1-g37088a8; sherpa-onnx requires >= 3.15). The script builds with
`make -j4` (Unix Makefiles), not ninja, so CLAUDE.md's ninja-shadowing trap
does not apply to it.

**Standing instruction for the first row, from Matt, recorded before it runs.**
Before the first row on the phone, read all THREE ceilings — policy0, policy4,
policy6 `scaling_max_freq` — in ONE wrapped `adb shell` invocation with uptime.
pennytts.sh gates on policy6 and policy4 only, and the 18 Sept trap says
policy0 throttles unseen. pennytts.sh is NOT changed for this.

Held for Matt: approve websocketpp + asio, or build with
SHERPA_ONNX_ENABLE_WEBSOCKET=OFF (which the script cannot pass without an edit
to it or a hand-run of its cmake line).

What this entry does not say: that sherpa-onnx builds with this NDK and cmake,
what the nine approved archives weigh or whether they match their pins, that
nothing further is fetched beyond the eleven, or anything about the phone.

## 2026-09-18 — TTS RUNG 1, BUILD STOPPED IN CONFIGURE: two UNAPPROVED archives were fetched (kissfft, kaldifst — pulled by approved archives' own cmake) before the stop fired. No binary exists.

Branch tts-kokoro. Mac only; the phone has not been touched from this branch.

**The edit to the clone (Matt's option 2).** sherpa-onnx clone = a5b4a94 + this one uncommitted line:

    diff --git a/build-android-arm64-v8a.sh b/build-android-arm64-v8a.sh
    index 8e05154..5b592a7 100755
    --- a/build-android-arm64-v8a.sh
    +++ b/build-android-arm64-v8a.sh
    @@ -183,6 +183,7 @@ cmake -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK/build/cmake/android.toolchain.cmake"
         -DSHERPA_ONNX_ENABLE_TESTS=OFF \
         -DSHERPA_ONNX_ENABLE_CHECK=OFF \
         -DSHERPA_ONNX_ENABLE_PORTAUDIO=OFF \
    +    -DSHERPA_ONNX_ENABLE_WEBSOCKET=OFF \
         -DSHERPA_ONNX_ENABLE_JNI=$SHERPA_ONNX_ENABLE_JNI \
         -DSHERPA_ONNX_LINK_LIBSTDCPP_STATICALLY=OFF \
         -DSHERPA_ONNX_ENABLE_C_API=$SHERPA_ONNX_ENABLE_C_API \

`bash -n` passes. Configure log line 71 reads `-- SHERPA_ONNX_ENABLE_WEBSOCKET OFF`,
and websocketpp and asio were not fetched.

**Command, run from ~/Documents/sherpa-onnx, stdout+stderr to a log:**

    PATH=/opt/homebrew/share/android-commandlinetools/cmake/3.22.1/bin:$PATH \
    ANDROID_NDK=/opt/homebrew/share/android-commandlinetools/ndk/30.0.16248370 \
    SHERPA_ONNX_ONNXRUNTIME_ROOT=/Users/mattstevenson/Documents/sherpa-onnx-deps/onnxruntime-android-1.28.2 \
    SHERPA_ONNX_ENABLE_BINARY=ON ./build-android-arm64-v8a.sh

cmake 3.22.1-g37088a8. Started 17:17:35 BST (read in the launching command). It
never reached `make`: no install/ directory exists. No build wall time is
quoted, because the build did not complete.

**What went wrong, in order.** The nine approved archives were named from
sherpa-onnx's own cmake/*.cmake. Two of them fetch dependencies of their own at
configure time, which the earlier entries did not look for (their cmake files
only exist once the archive is unpacked):

    kaldi-native-fbank v1.22.3 -> its CMakeLists.txt:136 include(kissfft)
      kissfft @febd4cae  pinned _deps/kaldi_native_fbank-src/cmake/kissfft.cmake:9,11
    kaldi-decoder v0.3.0 -> its CMakeLists.txt:56 include(kaldifst)
      kaldifst v1.8.0    pinned _deps/kaldi_decoder-src/cmake/kaldifst.cmake:4-5

Configure log lines 141 and 152 name them: `Downloading kissfft from
https://github.com/mborgerding/kissfft/archive/febd4cae….zip` and `Downloading
kaldifst from https://github.com/k2-fsa/kaldifst/archive/refs/tags/v1.8.0.tar.gz`.

**The stop was mine and it was wrong.** The watcher I ran beside the build
counted `_deps/*-subbuild` directories and killed at more than nine. It should
have matched names against the approved list. So it let kissfft (the 3rd fetch)
and kaldifst (the 4th) through, and fired only at the 10th subbuild,
piper-phonemize, at ~17:24. Its `pkill` stopped the script but not the cmake
children; the ps listing showed cmake still downloading piper-phonemize, and I
killed those four PIDs by hand. `pgrep` then returned none running (17:24:17). **Two
archives outside the approval are on disk, and hclust-cpp (approved) was never
reached.**

**Every archive on disk.** Bytes are from `stat -f %z` on the file in
`_deps/<name>-subbuild/<name>-populate-prefix/src/`. The sha256 was COMPUTED by
me and compared with the pin line named. **No cmake log line shows the hash
check**: FetchContent runs quiet by default and prints download output only on
failure. The only evidence from cmake itself is that each completed archive has
its `-populate-download` stamp, which ExternalProject writes only after its
hash check passes.

    archive                 approved  bytes        sha256 vs pin   pin line
    kaldi-native-fbank      yes           71,144   MATCH           cmake/kaldi-native-fbank.cmake:6
    kaldi-decoder           yes           51,199   MATCH           cmake/kaldi-decoder.cmake:5
    eigen 5.0.1             yes        2,967,272   MATCH           cmake/eigen.cmake:5
    openfst 1.8.5-2026-07-09 yes       1,501,685   MATCH           cmake/openfst.cmake:7
    simple-sentencepiece    yes          355,335   MATCH           cmake/simple-sentencepiece.cmake:6
    nlohmann json 3.12.0    yes        9,678,593   MATCH           cmake/json.cmake:6
    espeak-ng @ed530aa      yes       18,011,501   MATCH           cmake/espeak-ng-for-piper.cmake:5
    piper-phonemize @f3ff95a yes       1,916,928   PARTIAL         cmake/piper-phonemize.cmake:5
                                                   (killed mid-transfer; the size grew
                                                   1,032,192 -> 1,916,928 between two
                                                   reads; its sha256 is of a truncated
                                                   file and means nothing)
    hclust-cpp              yes       not fetched  --              cmake/hclust-cpp.cmake
    kissfft @febd4cae       NO            74,252   MATCH           _deps/kaldi_native_fbank-src/cmake/kissfft.cmake:11
    kaldifst v1.8.0         NO           172,147   MATCH           _deps/kaldi_decoder-src/cmake/kaldifst.cmake:5

    complete approved archives on disk: 7, 32,636,729 B
    unapproved on disk: 2, 246,399 B

**Left as it is, for Matt:** `~/Documents/sherpa-onnx/build-android-arm64-v8a/`
with all of the above, including the partial piper-phonemize zip. Nothing was
deleted. The clone's `git status` is ` M build-android-arm64-v8a.sh` only.

**What the full fetch list now looks like, as far as it has been read.**
The nine, plus kissfft and kaldifst, is eleven. **Unread**: whether
simple-sentencepiece, json, espeak-ng, piper-phonemize (which never finished
unpacking) or hclust-cpp fetch anything of their own. The configure log up to
the kill shows none from the first seven. piper-phonemize and hclust-cpp are the
two still unread.

What this entry does not say: that sherpa-onnx builds with this NDK, anything
about a binary, its libraries or its hashes, that the fetch list is complete at
eleven, or anything about the phone.

## 2026-09-18 — TTS RUNG 1, BUILD FAILED AT LINK: configure completed with all ELEVEN fetches pinned and matching, then every executable failed to link — libonnxruntime.so 1.28.2 needs API-24 libc symbols and the script targets android-21. No binary exists.

Branch tts-kokoro. Mac only; the phone has not been touched from this branch.

**Rule change from Matt, applying from this run:** any archive cmake fetches
during configure is approved if it is sha256-pinned in the cmake file that
requests it. kissfft and kaldifst are approved on that basis. The stop
conditions are only (a) a fetch with no pin, or (b) a hash-check failure. No
watcher and no pkill.

**Deleted before the rerun**, from
~/Documents/sherpa-onnx/build-android-arm64-v8a/_deps/, and nothing else:
`piper_phonemize-subbuild/` (41 entries, 2,260 KB, including the partial
`f3ff95afc03640bc1399e113e83361192a2fafb4.zip` of 1,916,928 B),
`piper_phonemize-src/` (empty) and `piper_phonemize-build/` (empty).

**Run 2.** It used the same command as the previous entry, with the clone's
one-line WEBSOCKET=OFF edit still in place (clone = a5b4a94 + that line). The
wall time was read in the launching command:
START 17:27:42 BST, END 17:31:05 BST, **rc=2, wall_s=203**.
Configure completed ("Configuring done", "Generating done", "Build files have
been written", log lines 223-225). `make -j4` stopped at [ 87%].

**Every archive fetched across both runs.** Run 2 re-used run 1's archives, and
their mtimes are unchanged (17:17:40-17:23:53). Only piper-phonemize (17:29:56)
and hclust-cpp (17:29:58) were fetched in run 2. Bytes are from `stat -f %z`.
Each sha256 was COMPUTED on the Mac and compared with the pin line named. No
cmake log line prints the hash check (FetchContent is quiet on success). Each
archive has its `-populate-download` stamp, which ExternalProject writes only
after the check passes.

    archive                    orig nine  bytes        sha256 vs pin  pin file:line
    kaldi-native-fbank v1.22.3 yes            71,144   MATCH          cmake/kaldi-native-fbank.cmake:6
    kaldi-decoder v0.3.0       yes            51,199   MATCH          cmake/kaldi-decoder.cmake:5
    eigen 5.0.1                yes         2,967,272   MATCH          cmake/eigen.cmake:5
    openfst 1.8.5-2026-07-09   yes         1,501,685   MATCH          cmake/openfst.cmake:7
    simple-sentencepiece v0.7  yes           355,335   MATCH          cmake/simple-sentencepiece.cmake:6
    nlohmann json v3.12.0      yes         9,678,593   MATCH          cmake/json.cmake:6
    espeak-ng @ed530aa         yes        18,011,501   MATCH          cmake/espeak-ng-for-piper.cmake:5
    piper-phonemize @f3ff95a   yes         9,805,962   MATCH          cmake/piper-phonemize.cmake:5
    hclust-cpp 2026-02-25      yes            22,523   MATCH          cmake/hclust-cpp.cmake:7
    kissfft @febd4cae          no             74,252   MATCH          _deps/kaldi_native_fbank-src/cmake/kissfft.cmake:11
    kaldifst v1.8.0            no            172,147   MATCH          _deps/kaldi_decoder-src/cmake/kaldifst.cmake:5

    original nine: 42,465,214 B   the two added: 246,399 B   all eleven: 42,711,613 B
    (plus the discarded partial piper-phonemize zip from run 1, 1,916,928 B)

**Nothing unpinned was fetched.** The whole build tree has exactly eleven
`*-populate-download` stamps and no other `-prefix` or `-stamp` directory.
Two fetch routes exist in the unpacked sources and did NOT run:
piper-phonemize's own `ExternalProject_Add` (its CMakeLists.txt:46-70) sits
under `if(NOT DEFINED ESPEAK_NG_DIR)`, and ESPEAK_NG_DIR is set. espeak-ng's
sonic `FetchContent_Declare` (its cmake/deps.cmake:18-19) sits under
`USE_LIBSONIC`, which is OFF.

**The failure.** Four executables failed to link, and make stopped before
reaching any others:
sherpa-onnx-offline-audio-tagging, sherpa-onnx-offline,
sherpa-onnx-keyword-spotter and sherpa-onnx. `build/bin/` is empty.
**sherpa-onnx-offline-tts was never linked.** It links the same
libonnxruntime.so, so the same failure is expected, but it was not observed.
Each failure is the same five undefined symbols, all referenced by
`onnxruntime-android-1.28.2/jni/arm64-v8a/libonnxruntime.so` and rejected
by `--no-allow-shlib-undefined`:

    __register_atfork@LIBC  stderr@LIBC  __gnu_strerror_r@LIBC
    __write_chk@LIBC_N  __fwrite_chk@LIBC_N

The script defaults to `SHERPA_ONNX_ANDROID_PLATFORM=android-21`
(build-android-arm64-v8a.sh:164-166; CMakeCache ANDROID_PLATFORM=android-21).
Checked with the NDK's own llvm-readelf --dyn-syms against its libc.so stubs
(sysroot/usr/lib/aarch64-linux-android/<api>/libc.so):

    API 21: none of the five exported
    API 23: the three LIBC ones, not the two LIBC_N ones
    API 24: all five

libonnxruntime.so's version-needs list is LIBC and LIBC_N, and LIBC_N is API
24 (Nougat). So this prebuilt needs API >= 24 to link against. The NDK r30
range is 21-37 (meta/platforms.json). The 6a runs Android 17, API 37.

**Likely fix, not applied (it changes the command):** add
`SHERPA_ONNX_ANDROID_PLATFORM=android-24` to the command. The script already
reads it at :164, so the clone needs no edit. The build dir's cache holds
android-21 and the toolchain detection from the first configure. The cleanest
rerun deletes `CMakeCache.txt` and `CMakeFiles/` at the build-dir top level
only; `_deps/` and its eleven verified archives stay. Held for Matt.

What this entry does not say: that android-24 links; anything about
sherpa-onnx-offline-tts, `file` on it, its needed libs, or its sha256;
anything about install/lib/libonnxruntime.so (the script copies it only after
make succeeds); or anything about the phone.

## 2026-09-18 — TTS RUNG 1, BUILT at android-24: sherpa-onnx-offline-tts is aarch64, links only libonnxruntime.so plus Android system libs, and libonnxruntime.so is byte-identical to the hash-matched prebuilt. BUT it is a MIXED build: 318 objects were compiled for android-21 in run 2 and reused.

Branch tts-kokoro. Mac only; the phone has not been touched from this branch.

**Correction to the header at notes-tts.md:317.** It says "every executable
failed to link". Four failed: sherpa-onnx-offline-audio-tagging,
sherpa-onnx-offline, sherpa-onnx-keyword-spotter and sherpa-onnx. make stopped
there, and the rest were not attempted. That entry's body was already right.

**android-24 is a required build parameter for this onnxruntime prebuilt**
(onnxruntime-android-1.28.2, libonnxruntime.so sha256 33847ad4…), because it
needs LIBC_N symbols. The 7a build carries SHERPA_ONNX_ANDROID_PLATFORM=android-24
or higher.

**Deleted before the rerun, at the top level of
~/Documents/sherpa-onnx/build-android-arm64-v8a/ only:** `CMakeCache.txt`
(37,948 B, held ANDROID_PLATFORM=android-21) and `CMakeFiles/` (162 entries).
`_deps/` and every per-subdirectory CMakeFiles/ under the build tree were left.

**Run 3, command (from ~/Documents/sherpa-onnx; clone = a5b4a94 + the one
uncommitted WEBSOCKET=OFF line):**

    PATH=/opt/homebrew/share/android-commandlinetools/cmake/3.22.1/bin:$PATH \
    ANDROID_NDK=/opt/homebrew/share/android-commandlinetools/ndk/30.0.16248370 \
    SHERPA_ONNX_ONNXRUNTIME_ROOT=/Users/mattstevenson/Documents/sherpa-onnx-deps/onnxruntime-android-1.28.2 \
    SHERPA_ONNX_ENABLE_BINARY=ON SHERPA_ONNX_ANDROID_PLATFORM=android-24 ./build-android-arm64-v8a.sh

    START 2026-09-18 17:34:38 BST   END 17:34:58 BST   rc=0   wall_s=20
    (read in the launching command)

Configure: log line 47 passes `-DANDROID_PLATFORM=android-24`, and
CMakeCache.txt:21 reads `ANDROID_PLATFORM:UNINITIALIZED=android-24`.

**Nothing downloaded again.** The log prints the eleven "Downloading …" status
lines on every configure, but every archive under _deps/ has the same size and
mtime as before the run (17:17:40-17:29:58). There is no new archive and no new
subbuild.

**THE CAVEAT: the 20 s wall is because this was NOT a clean android-24 build.**
Run 2 compiled 318 objects (log count of "Building C/CXX object") for
android-21 before it failed at link. Run 3 compiled only 44: the ones run 2
never reached, which were 5 in sherpa-onnx-core, one main.cc per executable
(including sherpa-onnx-offline-tts.cc.o, 17:34:50), 23 in sherpa-onnx-jni and
1 in ssentencepiece_core. It then linked 30 targets. The regenerated rules name
android24 (sherpa-onnx-core.dir/build.make: 690 occurrences), but CMake's
Makefile generator does not rebuild an object when only its rule's target
triple changes. So most of libsherpa-onnx-core, and every _deps library,
inside sherpa-onnx-offline-tts was compiled at __ANDROID_API__ 21. The binary's
.note.android.ident records API 0x18 = 24, NDK r30 16248370 (from the crt
objects at link). The mix is expected to run on API 37 (API-21 code is
forward-compatible), but it is not the build that was asked for, and it is
not reproducible from the command above alone.

**The artefacts, as built:**

    install/bin/sherpa-onnx-offline-tts   2,426,304 B
      file: ELF 64-bit LSB pie executable, ARM aarch64, dynamically linked,
            interpreter /system/bin/linker64, BuildID[sha1]=2266ef9f1bfe5cae7e31b160728b743f9ee672f1, stripped
      sha256 cd23a509cacad4fa17a41e85d8ebc60a3c84fe34ae9716b629b7a15bb4e85a15 (computed)
      RUNPATH $ORIGIN/../lib:$ORIGIN/../../../sherpa_onnx/lib
      needed (llvm-readelf --needed-libs, NDK r30):
        libonnxruntime.so   install/lib/
        libandroid.so libc.so libdl.so liblog.so libm.so   Android system libs (not yet checked on the phone)
    install/lib/libonnxruntime.so        22,249,560 B
      sha256 33847ad43bffe204699fd4a27f7f3603452a8cdaf2f9a44983a0bc31ffcf2da1
      = the unzipped prebuilt's jni/arm64-v8a/libonnxruntime.so (notes-tts.md:69),
        whose zip matched sherpa-onnx's pin at cmake/onnxruntime-android-aarch64.cmake:18
      needed: libc.so libdl.so liblog.so libm.so (system)
    install/lib/libsherpa-onnx-jni.so    4,892,296 B (not needed by the CLI binary)

sherpa-onnx-core is linked statically into the binary; install/lib holds no
libsherpa-onnx-core.so, and the binary does not NEED one. libc++ is the NDK's
static one (the script's default), so there is no libc++_shared.so to push.

**Recommended before anything is pushed, held for Matt:** a clean android-24
rebuild. Delete everything in build-android-arm64-v8a/ except `_deps/*-subbuild`
(which holds the eleven verified archives and their download stamps), rerun
the same command, and take the new sha256. With the same FetchContent stamps
it should not download again, and a full compile was ~3 min in run 2.

What this entry does not say: that the binary runs on the 6a; that the mixed
build behaves differently from a clean one (untested either way); that the five
system libs resolve on the phone; or anything measured.

## 2026-09-18 — TTS RUNG 1, CLEAN android-24 BUILD AND PUSH: 356 objects compiled from scratch, nothing downloaded, new binary bd7d26e8…; every pushed file hashes identically on the phone; --help exits 0 on the 6a. No row run.

Branch tts-kokoro. Boot 4 (up since 13:17:34, a spent boot: S0/S1/S2 and
eight model loads ran on it).

**Deleted, with Matt's approval, from ~/Documents/sherpa-onnx/build-android-arm64-v8a/.**
Top level: `bin`, `cmake_install.cmake`, `CMakeCache.txt`, `CMakeFiles`,
`CPackConfig.cmake`, `CPackSourceConfig.cmake`, `install`,
`install_manifest.txt`, `lib`, `Makefile`, `sherpa-onnx`, `sherpa-onnx.pc`.
In _deps: all eleven `*-build` directories. **Kept: `_deps/*-subbuild` (11) AND
`_deps/*-src` (11).** The approval named only -subbuild. Before deleting,
I found that each subbuild's `<name>-populate-download` stamp also covers
extraction (kissfft-subbuild build.make:96-101: download, verify and extract
all run in one step, whose stamp depends only on urlinfo.txt and the mkdir
stamp). With the stamps present, cmake would not re-extract, and configure
would have met eleven empty source dirs. Matt chose to keep -src. It holds
extracted sources only; `find . -name '*.o'` outside the subbuilds returned 0
before the run.

**Run 4** (same command as run 3, SHERPA_ONNX_ANDROID_PLATFORM=android-24;
clone = a5b4a94 + the one WEBSOCKET=OFF line):

    START 2026-09-18 17:40:48 BST   END 17:42:08 BST   rc=0   wall_s=80
    "Building C/CXX object": 356   (run 2: 318, run 3: 44)   "Linking": 30
    log line 47 -DANDROID_PLATFORM=android-24; CMakeCache.txt:21 android-24
    no "linux-android21" in any build.make; "linux-android24" 999 times

**Nothing downloaded.** All eleven archives keep their sizes and mtimes
(17:17:40-17:29:58, the same eleven values as in the previous entry). There are
still exactly eleven subbuilds.

**The artefact:**

    install/bin/sherpa-onnx-offline-tts   2,432,496 B
      ELF 64-bit LSB pie executable, ARM aarch64, dynamically linked,
      interpreter /system/bin/linker64, BuildID[sha1]=a0028df3343558e8e5ea599edbe74a411906214a, stripped
      sha256 bd7d26e8f1cca82da2596fce2fe1957b2a2ed139f772a7655ec5983cb83c4f2d
      DIFFERS from the mixed build's cd23a509… (2,426,304 B), as expected
      needed: libonnxruntime.so (install/lib) + libandroid libc libdl liblog libm (system)
    install/lib/libonnxruntime.so        22,249,560 B
      sha256 33847ad43bffe204699fd4a27f7f3603452a8cdaf2f9a44983a0bc31ffcf2da1 (unchanged)

**Before the push, one wrapped invocation:** uptime_s=15908.91,
wallclock 2026-09-18 17:42:42 BST, MemAvailable 2,115,568 kB, SwapFree
802,404 kB, scaling_max_freq/cpuinfo_max_freq policy0 1803000/1803000, policy4
2253000/2253000, policy6 2802000/2802000. `/data/local/tmp/tts` did not exist.
AC power, screen on, unlocked; nothing of ours running.

**Push** (adb push; `mkdir /data/local/tmp/tts` first; chmod 755 on the binary
and pennytts.sh; nothing outside /data/local/tmp/tts touched):

    sherpa-onnx-offline-tts   2,432,496 B in 0.005 s
    libonnxruntime.so        22,249,560 B in 0.219 s
    penny-kokoro-int8/       360 files, 150,880,597 B in 3.093 s (46.5 MB/s)
    pennytts.sh                  12,363 B

**sha256, phone (sha256sum) against the Mac (shasum -a 256), all MATCH:**

    sherpa-onnx-offline-tts  bd7d26e8f1cca82da2596fce2fe1957b2a2ed139f772a7655ec5983cb83c4f2d
    libonnxruntime.so        33847ad43bffe204699fd4a27f7f3603452a8cdaf2f9a44983a0bc31ffcf2da1
    model.int8.onnx          a089794d1293b91e82f3f2b8bed5417d04ac64447d6ada5f21045cde0799bf99
    voices.bin               1c5a5b983d3d50d8586d437a51f3faa2da7919ce76a013c081e65671a3447c29
    lexicon-gb-en.txt        c4cbb37316f62210dff52718a7afcaae24f50c032cc75ab47ae67b831d1049e7
    lexicon-us-en.txt        7daaab53a181be9885b853a8582bf1838186317e5dadacbcef9c426d6fa0da14
    tokens.txt               6ebb6bb288f20f3ae8d004d3c2ca27697da27c037d75e81a60e2a6a663f95425
    pennytts.sh              79ec84160a73d3aafb11e808a73b9ad733fc49f0b50b2973e06a7f698dc604b2
                             (= the copy committed at 25b6ed3)

espeak-ng-data: 355 files, 37 dirs on the phone; 355 files, 37 dirs on the
Mac (17,991,651 B). Files were counted, not hashed individually.

**After the push, one wrapped invocation:** uptime_s=15932.39, 17:43:06,
MemAvailable 2,097,528 kB, SwapFree 802,660 kB.

**Smoke (--help only, no model, no taskset), run from /data/local/tmp/tts with
LD_LIBRARY_PATH=/data/local/tmp/tts:** exit **rc=0** at uptime 15942.84,
17:43:16. The output (156 lines, 12,646 B) was written to
/data/local/tmp/tts/help.txt, the one file created beyond the push. First two
lines, verbatim:

    /Users/mattstevenson/Documents/sherpa-onnx/sherpa-onnx/csrc/parse-options.cc:PrintUsage:415
    (empty)

Line 3 is "Offline/Non-streaming text-to-speech with sherpa-onnx". The usage
lists every flag pennytts.sh passes: --kokoro-model --kokoro-voices
--kokoro-tokens --kokoro-lang --kokoro-lexicon --kokoro-data-dir --num-threads
--sid --speed --output-filename. It also lists --kokoro-dict-dir and
--kokoro-length-scale, which pennytts.sh does not pass.

This proves the binary and libonnxruntime.so load, link and run on this CPU
against bionic at API 37. It proves nothing about the model path: no ONNX
session was created and no onnxruntime kernel ran, so the SIGILL risk named in
the second entry is still untested.

What this entry does not say: that the model loads or generates audio on the
6a; that pennytts.sh runs under the phone's shell (its `date +%N` guard is
still unexercised); anything measured; or that any of it is a baseline. Boot
4 is spent.

## 2026-09-18 — TTS RUNG 1, ROW 4a: "On it." ×5, X1 pair, 2 threads, cool-gated — all rc=0; wall 3,021-3,082 ms, elapsed 1,098-1,162 ms, RTF 1.324-1.401, peak RSS 286,280-287,112 kB; WAV 19,899 samples vs the Mac's 19,812. Boot 4, spent; nothing here is a baseline.

Branch tts-kokoro. **Boot 4** (up since 13:17:34). It had already carried S0, S1
(59.16 min) and S2 (26.03 min) and eight model loads, so every figure below is
from a spent boot. **No figure here is a baseline**, and each is "on the 6a".

**Correction: the line at notes-tts.md:540 ("AC power, screen on, unlocked;
nothing of ours running") was not read when it was written.** Read at 17:46:11,
in one wrapped invocation (uptime 16117.09 -> 16117.52):

    battery/status   Charging     capacity 100    battery/temp 278 (dC, battery not SoC)
    online           main-charger 1, usb 1, tcpm-source-psy-i2c-max77759tcpc 1, dc 0
    dumpsys power    mWakefulness=Awake, mWakefulnessChanging=false
                     "Display Power:" printed only an object reference
                     (com.android.server.power.PowerManagerService$1@ec9ada9), not a state
    pgrep -l         pennyload: nothing; llama: nothing
    pm list packages -d   package:com.pennyspike.probe2a  (disabled)
    ceilings         policy0 1803000, policy4 2253000, policy6 2802000

So: USB/mains charging at 100%, the device awake, none of our processes, the
app disabled. "Unlocked" was not read directly; adb answered, and CLAUDE.md
says the port is charging-only while locked.

**A void first attempt, recorded.** At 17:46:22 (uptime 16128.52) the first
ack-1 invocation redirected the script's stderr to
/data/local/tmp/tts/out/ack-1.sherr before out/ existed (the script creates it).
The phone's shell refused the redirect ("can't create … No such file or
directory", rc=1) and **pennytts.sh never ran**. That was confirmed afterwards:
no out/, no sherpa process, and 0.11 s between the hand reads either side.
My error. The row below is the second invocation, with stderr to
/data/local/tmp/tts/ack-1.sherr, which ended up empty (0 B) on all five rows.

**Command, per row, one adb shell invocation each, hand-read ceilings either side:**

    COOL=1 sh /data/local/tmp/tts/pennytts.sh ack-N c0 2 0

c0 = cpus 6-7 (X1 pair). 2 threads, sid 22, lang en, lexicon-gb-en.txt,
speed 1.0. Binary bd7d26e8…, libonnxruntime.so 33847ad4…

**ack-1's .report, verbatim** (from /data/local/tmp/tts/out/ack-1.report):

    PENNYTTS tag=ack-1 rc=0 mask=c0 threads=2 cool_gate=1
    PENNYTTS text=On it.
    PENNYTTS bin=/data/local/tmp/tts/sherpa-onnx-offline-tts
    PENNYTTS cool_wait_s     uptime 16146.57 -> 16146.65
    PENNYTTS uptime_s        before=16146.69 after=16150.43
    PENNYTTS wall_ms         3021   (exec to exit, taken in the launching subshell)
    PENNYTTS elapsed_ms      1098   (the binary's own Elapsed: generate only)
    PENNYTTS derived_load_ms 1923   (wall - elapsed: process start + load + WAV write + 2 date forks)
    PENNYTTS Number of threads: 2
    PENNYTTS Audio duration: 0.829 s
    PENNYTTS Real-time factor (RTF): 1.098/0.829 = 1.324
    PENNYTTS wav             sr=24000 ch=1 bytes=39798 samples=19899 ms=829
    PENNYTTS memavail_kB     before=2073044 after=2044564
    PENNYTTS memfree_kB      before=766112 after=735300
    PENNYTTS swapfree_kB     before=804196 after=804196
    PENNYTTS cached_kB       before=1475944 after=1476864
    PENNYTTS pswpin          before=238174 after=238174
    PENNYTTS pswpout         before=852970 after=852970
    PENNYTTS pgmajfault      before=253999 after=254012
    PENNYTTS ceil_x1_kHz     before=2802000 min=2704000 after=2802000   (policy6, cpus 6-7, rated 2802000)
    PENNYTTS ceil_x1_min_at  uptime=16149.37
    PENNYTTS ceil_a76_kHz    before=2253000 min=2253000 after=2253000   (policy4, cpus 4-5, rated 2253000)
    PENNYTTS ceil_a76_min_at uptime=16146.69
    PENNYTTS peak_rss_kB     286736   (VmHWM, monotonic)
    PENNYTTS max_vmrss_kB    283232
    PENNYTTS max_rssanon_kB  232232   (anonymous -- NOT reclaimable)
    PENNYTTS max_rssfile_kB  50680   (file-backed -- reclaimable)
    PENNYTTS rss_samples     9   (sleep 0.2 s between samples; a short run may get very few)
    PENNYTTS lmk_kill_lines  0
    --- binary stderr (tail) ---
    /Users/mattstevenson/Documents/sherpa-onnx/sherpa-onnx/csrc/parse-options.cc:Read:374 /data/local/tmp/tts/sherpa-onnx-offline-tts --num-threads=2 --kokoro-model=/data/local/tmp/tts/penny-kokoro-int8/model.int8.onnx --kokoro-voices=/data/local/tmp/tts/penny-kokoro-int8/voices.bin --kokoro-tokens=/data/local/tmp/tts/penny-kokoro-int8/tokens.txt --kokoro-data-dir=/data/local/tmp/tts/penny-kokoro-int8/espeak-ng-data --kokoro-lexicon=/data/local/tmp/tts/penny-kokoro-int8/lexicon-gb-en.txt --kokoro-lang=en --sid=22 --speed=1.0 --output-filename=/data/local/tmp/tts/out/ack-1.wav 'On it.' 

    Number of threads: 2
    Elapsed seconds: 1.098 s
    Audio duration: 0.829 s
    Real-time factor (RTF): 1.098/0.829 = 1.324
    The text is: On it.. Speaker ID: 22
    Saved to /data/local/tmp/tts/out/ack-1.wav successfully!
    --- kill lines ---

ack-1.wall raw: `1789750001 050402574 1789750004 071966214 0`. That is five
numeric fields, so toybox `date +'%s %N'` returns real nanoseconds on this
build and the %N guard did not fire. ack-1.err (797 B) is exactly the binary
stderr shown above: the parse-options echo of the command, then "Number of
threads", "Elapsed seconds", "Audio duration", "Real-time factor", "The text
is", "Saved to".

**The five rows.** All figures are from out/ack-N.report, .wall and .err. Derived
load = **wall minus elapsed, which includes process start and WAV write** (and
the model load, and two `date` forks).

    row    wall_ms  elapsed_ms  derived_load_ms  RTF    peak_rss_kB  rss_samples  x1 min     a76 min    memavail_kB before -> after
    ack-1  3021     1098        1923             1.324  286,736      9            2,704,000  2,253,000  2,073,044 -> 2,044,564
    ack-2  3082     1121        1961             1.352  286,996      8            2,630,000  2,253,000  2,075,484 -> 2,046,420
    ack-3  3037     1157        1880             1.395  286,628      8            2,630,000  2,253,000  2,043,172 -> 2,048,156
    ack-4  3037     1162        1875             1.401  286,280      8            2,401,000  2,253,000  2,047,384 -> 2,050,980
    ack-5  3063     1151        1912             1.388  287,112      9            2,630,000  2,253,000  2,041,976 -> 2,034,592

    wall .raw   ack-2 1789750024 574685707 1789750027 656700194 0
                ack-3 1789750029 281759521 1789750032 319538901 0
                ack-4 1789750033 950758058 1789750036 988496870 0
                ack-5 1789750038 587688440 1789750041 651081996 0
    audio 0.829 s on every row; wav sr=24000 ch=1 bytes=39798 samples=19899 on every row
    max_rssanon_kB 232,232-235,628; max_rssfile_kB 50,680-51,848
    swapfree unchanged within each row (804,196 on ack-1, 804,452 on 2-5)
    pgmajfault +13 on ack-1, 0 on ack-2/3/4, +8 on ack-5
    lmk_kill_lines 0 on all five; .kills files 0 lines

The x1 min column is policy6, rated 2,802,000: ack-4's 2,401,000 is 85.7% of
rated, and the others are 93.9-96.5%. policy4 never moved.

**Hand-read ceilings (policy0/4/6 scaling_max_freq), each in the same adb
invocation as the row:**

    ack-1 before 16146.43 17:46:40  1803000/2253000/2802000   after 16151.31 17:46:45  same
    ack-2 before 16169.95 17:47:03  1803000/2253000/2802000   after 16174.48 17:47:08  same
    ack-3 before 16174.62 17:47:08  same                      after 16179.10 17:47:13  same
    ack-4 before 16179.33 17:47:13  same                      after 16183.74 17:47:17  same
    ack-5 before 16184.00 17:47:18  same                      after 16188.76 17:47:22  same

**policy0 is read by hand only, and only before and after.** pennytts.sh does
not poll it, so a dip in policy0 during a row would not be seen.

**Cooling between rows was the gate only.** COOL=1 waits until policy6 and
policy4 read rated, and they already did at every start: cool_wait was
0.04-0.10 s on every row. Gaps from one row's `after` to the next row's gate
start: ack-1 -> ack-2 19.68 s (spent reading ack-1's files), then 1.11 s,
1.20 s and 1.16 s. So ack-3 to ack-5 ran nearly back to back, with the X1
ceiling already recovered to rated between rows.

**Cold vs warm.** Only ack-1 is "cold" in the process sense. The model file was
written by adb push at 17:42, so it was likely already in page cache even for
ack-1 (Cached rose 920 kB on ack-1, pgmajfault +13). **No row here measures a
load from flash.**

**The WAV.** ack-1.wav was pulled to the Mac at
~/kokoro-models/phone-6a-rung1/ack-1.wav (39,842 B). Its sha256
4d7c10b9844e86a345fbd45a158eca46a9d02b59fdf87449c0c942b12f6caca4 matches the
phone's copy. Header (python `wave`): 24,000 Hz, mono, 16-bit, **19,899
frames = 829.12 ms**, "data" tag at 36. The Mac clip abtest-penny/00-int8.wav
reads 19,812 frames = 825.50 ms. **Difference: 87 samples = 3.625 ms**, inside
P-T5's 120-sample / 5 ms limit for this line. All five phone WAVs have the
same sha256 (4d7c10b9…), so generation is deterministic across the five
processes. The phone's WAV is not byte-identical to the Mac's (different
lengths).

What this entry does not say: anything about lines other than "On it."; a
load time from flash (the file was likely cached); what policy0 did during a
row; whether the 87-sample difference comes from the front end or from the
1.13.8-vs-a5b4a94 version gap; anything about thermals beyond ~42 s of
intermittent load; the predictions' verdicts (those come in the rung 1
write-up); or anything on a fresh boot. Nothing here is a baseline.

## 2026-09-18 — TTS RUNG 1, ROWS 4b AND 4c: the 18 lines on the X1 pair at 2 threads and unpinned at 4 threads, back to back — 36 of 36 rc=0, no kills, no stderr warnings; X1 ceiling fell to 73.09% of rated across 4b and to one 17.84% reading in 4c; the three pulled WAVs are within 87 samples (3.625 ms) of the Mac's. Boot 4, spent; nothing here is a baseline.

Branch tts-kokoro. Boot 4 (up since 13:17:34; S0/S1/S2, eight model loads
and row 4a had run on it). AC/USB charging at 100% and awake, as read at
17:46:11 (previous entry). **Nothing here is a baseline.** Every figure is "on
the 6a". **The model is page-cached** (pushed at 17:42, and loaded 5 times in
4a), so no derived load below includes a read from flash. Derived load is
always **wall minus elapsed, which includes process start and WAV write** (and
the model load, and two `date` forks).

**Commands.** Each pass ran as ONE adb shell invocation: a hand read of
policy0/4/6 scaling_max_freq with uptime, then
`COOL=1 sh /data/local/tmp/tts/pennytts.sh <p>-00 <mask> <threads> 0`, then
lines 1-17 with COOL=0 back to back, then the hand read again. 4b: tags
p2-00…p2-17, mask c0, 2 threads. 4c: tags p4-00…p4-17, mask none, 4 threads.
Every figure below was parsed by script from out/<tag>.report and .err
(pulled to the Mac, 246 files), not from scrollback. RTF is the binary's own
line.

    ROW 4b: mask c0 (X1 pair), 2 threads
    line  elapsed_ms  audio_ms  RTF    wall_ms  derived_load_ms  peak_rss_kB  rss_n  x1_min_kHz  a76_min_kHz  memavail_kB before -> after  kills
    00         1132       829  1.365     3104             1972      287,392      9   2,802,000    2,253,000  2,076,496 -> 2,040,228      0
    01          959       758  1.266     2930             1971      283,656      8   2,802,000    2,253,000  2,045,320 -> 2,037,368      0
    02         1032       814  1.268     2989             1957      284,532      8   2,704,000    2,253,000  2,033,484 -> 2,045,612      0
    03         1502      1159  1.296     3405             1903      290,724      9   2,401,000    2,253,000  2,042,100 -> 2,034,260      0
    04         4881      4545  1.074     6775             1894      362,492     18   2,188,000    2,253,000  2,054,932 -> 2,048,848      0
    05         7370      6907  1.067     9344             1974      439,460     24   2,252,000    2,253,000  2,043,824 -> 2,057,596      0
    06         4229      3593  1.177     6179             1950      350,340     16   2,252,000    2,253,000  2,036,880 -> 2,016,736      0
    07         6254      5815  1.075     8218             1964      437,848     22   2,401,000    2,253,000  2,030,784 -> 2,056,848      0
    08         5285      4704  1.124     7244             1959      402,264     19   2,252,000    2,253,000  2,061,548 -> 2,040,968      0
    09         5167      4520  1.143     7105             1938      367,124     19   2,048,000    2,253,000  2,036,796 -> 2,038,416      0
    10         4006      3450  1.161     5972             1966      345,576     16   2,188,000    2,253,000  2,036,636 -> 2,049,496      0
    11         3887      3395  1.145     5853             1966      344,000     15   2,188,000    2,253,000  2,047,168 -> 2,042,276      0
    12         4221      3513  1.202     6152             1931      347,428     16   2,188,000    2,253,000  2,063,148 -> 2,057,688      0
    13         3554      3065  1.159     5509             1955      339,508     14   2,401,000    2,253,000  2,047,152 -> 2,029,380      0
    14         9913      7450  1.331    11887             1974      328,580     30   2,188,000    2,253,000  2,051,352 -> 2,058,944      0
    15        11730     10416  1.126    13667             1937      577,228     34   2,048,000    2,253,000  2,046,872 -> 2,052,752      0
    16         4269      3109  1.373     6236             1967      323,132     16   2,188,000    2,253,000  2,055,460 -> 2,030,352      0
    17         6040      4707  1.283     7977             1937      350,988     21   2,188,000    2,253,000  2,045,712 -> 2,040,848      0
    pass: first line start uptime 16414.46, last line end 16560.31, span 145.85 s
    MemAvailable low-water across the pass (before/after readings only): 2,016,736 kB (line 06)
    X1 min across the pass: 2,048,000 kHz = 73.09% of rated, lines/uptime [(9, '16481.31'), (15, '16536.06')]; A76 min 2,253,000
    peak RSS max 577,228 kB (line 15); rc all 0: True; kills total 0

    ROW 4c: unpinned, 4 threads
    line  elapsed_ms  audio_ms  RTF    wall_ms  derived_load_ms  peak_rss_kB  rss_n  x1_min_kHz  a76_min_kHz  memavail_kB before -> after  kills
    00         1302       829  1.570     3185             1883      286,408      8   2,048,000    2,253,000  2,065,612 -> 2,032,884      0
    01         1087       758  1.435     2972             1885      283,764      8   2,401,000    2,253,000  2,025,920 -> 2,046,756      0
    02         1135       814  1.395     3087             1952      284,216      8   2,252,000    2,253,000  2,042,468 -> 2,038,480      0
    03         1670      1159  1.441     3619             1949      291,168     10   2,401,000    2,253,000  2,057,672 -> 2,044,820      0
    04         5376      4545  1.183     7317             1941      363,116     19     500,000    2,253,000  2,043,748 -> 2,038,888      0
    05         8111      6907  1.174    10080             1969      441,496     27   1,826,000    2,253,000  2,060,812 -> 2,025,672      0
    06         4605      3593  1.282     6567             1962      350,708     17   2,048,000    2,253,000  2,045,044 -> 2,014,792      0
    07         6979      5815  1.200     8906             1927      438,232     23   1,745,000    2,253,000  2,067,788 -> 2,034,392      0
    08         6360      4704  1.352     8464             2104      402,688     22   1,745,000    2,253,000  2,024,948 -> 2,051,684      0
    09         6065      4520  1.342     8176             2111      367,452     21   1,745,000    2,253,000  2,056,952 -> 2,038,200      0
    10         4545      3450  1.317     6611             2066      345,828     17   1,582,000    2,253,000  2,047,472 -> 2,041,476      0
    11         4534      3395  1.335     6619             2085      344,620     17   1,582,000    2,253,000  2,043,592 -> 2,048,604      0
    12         4883      3513  1.390     6918             2035      348,748     18   1,582,000    2,253,000  2,040,508 -> 2,031,208      0
    13         4193      3065  1.368     6217             2024      339,204     16   1,582,000    2,253,000  2,051,952 -> 2,034,224      0
    14        11226      7450  1.507    13251             2025      328,404     32   1,426,000    2,253,000  2,053,728 -> 2,062,316      0
    15        12659     10416  1.215    14711             2052      577,488     35   1,582,000    2,253,000  2,059,616 -> 2,031,312      0
    16         4860      3109  1.563     6925             2065      324,080     18   1,582,000    2,253,000  2,035,616 -> 2,024,972      0
    17         7178      4707  1.525     9317             2139      350,616     23   1,582,000    2,253,000  2,037,492 -> 2,036,160      0
    pass: first line start uptime 16590.48, last line end 16749.2, span 158.72 s
    MemAvailable low-water across the pass (before/after readings only): 2,014,792 kB (line 06)
    X1 min across the pass: 500,000 kHz = 17.84% of rated, lines/uptime [(4, '16616.33')]; A76 min 2,253,000
    peak RSS max 577,488 kB (line 15); rc all 0: True; kills total 0

**Row 4b, conditions.**
- Hand read before line 0: uptime 16414.17, 17:51:08, policy0 1,803,000 /
  policy4 2,253,000 / policy6 2,802,000 (all rated).
- Hand read after line 17: uptime 16561.22, 17:53:35, 1,803,000 / 2,253,000 /
  2,802,000 (all rated).
- The X1 ceiling first moved on line 02 (2,704,000). Its minimum was
  2,048,000 (73.09%) on lines 09 (uptime 16481.31) and 15 (16536.06), then
  2,188,000 on 16-17. The A76 ceiling never moved.
- policy0 was read by hand only, before and after, and read rated both times.
- All 18 rc=0; every .wall has five numeric fields; 0 kill lines; every
  script-stderr file (tts/p2-NN.sherr) is 0 B.

**Gate between the passes, by hand**, polling all three ceilings until rated, in
one invocation: start uptime 16581.12 (17:53:55), end 16581.20 (17:53:55),
**1 poll, 0.08 s**. All three were already rated, 19.9 s after 4b's hand read.

**Row 4c, conditions.**
- Hand read before line 0: uptime 16590.20, 17:54:04, 1,803,000 / 2,253,000 /
  2,802,000 (all rated).
- Hand read after line 17: uptime 16749.96, 17:56:43, policy0 1,803,000,
  policy4 2,253,000, **policy6 2,252,000 (80.37% of rated)**.
- The X1 ceiling moved on line 00 already (2,048,000). **Line 04 read
  500,000 kHz = 17.84% of rated at uptime 16616.33**, one 0.2 s poll sample
  near that line's end (the line ran 16609.08-16616.90); how long it held is
  not known. The pass then sat at 1,582,000 (56.46%) from line 10 to line 17,
  with 1,426,000 (50.89%) on line 14. The A76 ceiling never moved.
- **Unpinned means the scheduler chose the cores; which cores the four threads
  ran on was not recorded.** The X1 ceiling applies to cpus 6-7 whether or not
  any thread ran there.
- All 18 rc=0; every .wall has five numeric fields; 0 kill lines; every
  tts/p4-NN.sherr is 0 B.

**Stated as fact, not judged.** Elapsed on 4c exceeds 4b on all 18 lines.
Sample counts are identical between the passes on all 18 lines, and p2-00/05/15
are byte-identical to p4-00/05/15.

**The three WAVs**, pulled to ~/kokoro-models/phone-6a-rung1/ (sha256 by
shasum on the Mac and sha256sum on the phone, each MATCH):

    p2-00.wav   4d7c10b9844e86a345fbd45a158eca46a9d02b59fdf87449c0c942b12f6caca4
    p2-05.wav   5d6cf04333b87efdbf06b834c42070c458d2a9ca40f124c6b6b81da0375258d5
    p2-15.wav   c24121e107ca63f3c349b485450723ca85a430c41af8ee8a948f9cc7315b2244

    header (python wave; all 24 kHz mono 16-bit, "data" at 36)
    line  phone samples  Mac samples  difference
    00       19,899         19,812    +87 = +3.625 ms
    05      165,773        165,742    +31 = +1.292 ms
    15      249,994        250,017    -23 = -0.958 ms

p2-00.wav is byte-identical to ack-1.wav (4d7c10b9…).

**Stderr and text.** No line in any of the 36 .err files falls outside the
expected set (command echo, Number of threads, Elapsed, Audio duration, RTF,
"The text is", Saved to), so there were no warnings. "The text is:" shows every line
arrived intact, including "£12,480.50" and the apostrophes. The binary appends
its own "." in that log line. Lines named for a later pronunciation check,
**not listened to**:
- 04 "3:45 pm", "24th"
- 05 "£12,480.50", "17.5%"
- 07 "API", "502", "GitHub"
- 08 "OAuth", "MCP"
- 09 "CI"
- 12 read/read and record/record (heteronyms)
- 14 "2 o'clock"
- 11 speaks the word "Penny"

Line 14 is four sentences. Its RTF (1.331 on 4b, 1.507 on 4c) is above the
other long lines, and its peak RSS (328,580 kB) is below line 04's shorter
single sentence. That fits per-sentence generation (max_num_sentences 1), but
it is not shown here.

What this entry does not say: the prediction verdicts (the write-up);
anything from flash; what policy0 did during either pass; which cores 4c
used; how long 4c's 500,000 kHz reading lasted; MemAvailable between the
before/after reads of any line (the low-water figures are over those reads
only); anything about pronunciation (not listened to); anything on a fresh
boot. Nothing here is a baseline.

## 2026-09-18 — TTS RUNG 1, WRITE-UP: it runs on the 6a, and it is slower than predicted — RTF 1.067-1.570 on every one of 41 rows, none under 1.0. P-T5 HELD; P-T2, P-T3 and P-T4 MISSED; P-T1 NOT JUDGED. Product assumption: 2 threads pinned to the X1 pair.

Branch tts-kokoro. All rung 1 rows ran on **boot 4**, which had already carried
S0, S1, S2 and eight model loads. Every figure is "on the 6a". Binary
sherpa-onnx-offline-tts bd7d26e8… (sherpa-onnx a5b4a94 + the WEBSOCKET=OFF line,
android-24, NDK r30), libonnxruntime.so 33847ad4…, model.int8.onnx a089794d…,
sid 22, lang en, lexicon-gb-en.txt.

**(1) The tables, as committed; not re-pasted.**
- Row 4a, "On it." ×5, c0, 2 threads, cool-gated: notes-tts.md:685-690.
- Row 4b, the 18 lines, c0, 2 threads, back to back: notes-tts.md:766-785.
- Row 4c, the 18 lines, unpinned, 4 threads, back to back: notes-tts.md:791-810.

**(2) The predictions (notes-tts.md:32-47), each judged against the figure named.**

- **P-T1, derived load 0.8-2.0 s from flash: NOT JUDGED.** The model was
  page-cached on every row (pushed at 17:42, before the first row), so no load
  from flash was measured.
- **P-T2, generate RTF 0.3-0.8 on the X1 pair at 2 threads: MISSED.**
  4a 1.324-1.401; 4b 1.067 (line 05) to 1.373 (line 16); 4c 1.174 (line 05)
  to 1.570 (line 00). **Lines under 1.0: 0 of 5, 0 of 18, 0 of 18.** Lines
  inside 0.3-0.8: none.
  (The sub-claim "short lines worse": 4b lines 00-03 read 1.266-1.365, against
  1.067-1.143 on the single-sentence lines of 4.5 s or more. Line 16, at 3.1 s
  of audio, read 1.373, and line 14, multi-sentence, read 1.331.)
- **P-T3, peak RSS 250-450 MB: MISSED.** The range across all 41 rows was
  283,656 kB (p2-01, 277.0 MiB) to 577,488 kB (p4-15, 564.0 MiB). kB here is
  /proc's KiB, and "MB" is read as MiB (450 MiB = 460,800 kB), as the repo
  does elsewhere. On that reading, the band is left **only by line 15, at
  10.416 s of audio** (577,228 kB on 4b and 577,488 on 4c). The largest
  in-band row is line 05, at 6.907 s (441,496 kB). If "MB" is read as 10^6
  bytes, line 05 (439,460 / 441,496 kB = 450.0 / 452.1 MB) also leaves it.
  MISSED either way.
- **P-T4, no clock descent within one 18-line pass: MISSED.** The 4b X1
  ceiling (policy6) fell to **2,048,000 kHz (73.09% of 2,802,000) at uptime
  16481.31** (line 09), and again on line 15 (16536.06). It first moved on
  line 02. policy4 held at 2,253,000 throughout. (4c: X1 to 500,000 in one
  sample at 16616.33, then 1,582,000 from line 10.)
- **P-T5, sample counts within 5 ms of the Mac: HELD.** Line 00 +3.625 ms
  (+87), line 05 +1.292 ms (+31), line 15 -0.958 ms (-23).

**(3) The product assumption: 2 threads pinned to the X1 pair (c0).** Elapsed
on 4c (unpinned, 4 threads) exceeded 4b on **18 of 18 lines**, so 4 threads
unpinned is not "clearly faster". **Caveat, stated:** 4c ran after 4b on the
same package, and its X1 ceiling sat lower (1,582,000 from line 10, against
2,048,000-2,188,000 on 4b), so the comparison is not controlled for heat.
It does not show that 4 threads is slower on a cool chip.

**(4) Derived load with the model cached is 1,875-2,139 ms across 41 rows**
(wall minus elapsed, which includes process start and WAV write). That bears
on rung 2's reload-on-wake threshold (cold start ≤ 1.5 s), and does not
answer it: rung 2 is resident vs reload, and none of these rows is a load
from flash.

**(5) For Matt to listen to** (pronunciation is out of scope for this brief;
unchanged from the 4b/4c entry). The WAVs on the Mac are
~/kokoro-models/phone-6a-rung1/p2-00.wav, p2-05.wav, p2-15.wav and ack-1.wav.
- 04 "3:45 pm", "24th"
- 05 "£12,480.50", "17.5%"
- 07 "API", "502", "GitHub"
- 08 "OAuth", "MCP"
- 09 "CI"
- 12 read/read and record/record (heteronyms)
- 14 "2 o'clock"
- 11 speaks the word "Penny"

**The phone as left**, one wrapped invocation: uptime_s=17039.79,
2026-09-18 18:01:33 BST, MemAvailable 2,078,096 kB, SwapFree 811,612 kB,
policy0 1,803,000, policy4 2,253,000, policy6 2,802,000 (all rated), battery
temp 301 dC. /data/local/tmp/tts/ holds 47 entries: the binary,
libonnxruntime.so, penny-kokoro-int8/, pennytts.sh, help.txt, out/ (246 files)
and 41 *.sherr files (all 0 B). It is left exactly as it is.
**Nothing outside /data/local/tmp/tts/ was created, changed or deleted from this
branch.** The /data/local/tmp listing is the one read at 17:06, plus tts/.

**(6) What rung 1 does NOT say.**
- Nothing about load from flash: the model was page-cached on every row.
- Nothing about resident RSS: every row is a fresh process (rung 2).
- Nothing about TTS beside the LLM (rung 3).
- Nothing about the app, AudioTrack or time-to-first-audio (rung 4).
- Nothing about a fresh boot: all of it ran on spent boot 4.
- Nothing about policy0 during a row: it was read by hand before and after
  each pass only, and read rated every time.
- Nothing about which cores 4c's threads used.
- Nothing about pronunciation: not listened to.
- Nothing about the 7a.

## 2026-09-18 — TTS RUNG 1, ADDITION (row 4d-A76), PREDICTION BEFORE ANY RUN: the 18 lines on the A76 pair (mask 30), 2 threads. P-T6: RTF 1.0-2.0.

Branch tts-kokoro. **An addition to rung 1, named as such.** It is not in the
brief's rung 1 list (rows 4a-4d). It runs on boot 4 as it stands: spent,
already carrying S0/S1/S2, eight model loads and rows 4a/4b/4c (41 TTS
processes). Nothing from it is a baseline.

**Mask 30 = cpus 4-5 = the A76 pair.** CLAUDE.md names policy4 as the A76
pair (CLAUDE.md:2528, "A76 pair (`policy4`, rated 2,253,000)") and labels masks
"c0 = X1 pair, f0 = X1+A76" (CLAUDE.md:2670), so 0x30 is cpus 4-5 by
elimination. It does not state "policy4 = cpus 4-5" in so many words. Read
on the phone at uptime ~17100 (related_cpus): policy0 = 0 1 2 3, policy4 = 4 5,
policy6 = 6 7.

**P-T6. Generate RTF on the A76 pair at 2 threads, pinned: 1.0-2.0.** Taken
from the brief's rung 3 prediction (TTS on the A76 pair, RTF 1.0-2.0, with the
LLM on the X1). It is written here before any a2 row runs, and it does not move.

**Why tonight.** Rung 3 plans to put TTS on the A76 pair beside the LLM on the
X1 pair, and to report TTS RTF "with and without the LLM". The "without"
figure on the A76 pair has never been measured. It needs no LLM and no fresh
boot, and the binary, model and wrapper are already on the phone and
hash-verified. Measured now, rung 3's comparison has its TTS-alone side. It
is still from a spent boot, so rung 3 may re-measure it on its fresh boot.

Run as 4b was: hand gate on all three ceilings at rated first, then one adb
invocation with a hand read of policy0/4/6 before line 0, `COOL=1 … a2-00 30 2 0`,
lines 1-17 with COOL=0 back to back, and a hand read after line 17. The tags
are a2-00 … a2-17.

## 2026-09-18 — TTS RUNG 1, ADDITION RESULT (row a2, A76 pair, 2 threads): 18 of 18 rc=0, no kills, neither ceiling moved; RTF 1.822-2.523 with 7 of 18 lines above 2.0 — P-T6 MISSED. The 18-line elapsed sum is 143,543 ms against 4b's 85,431 ms on the X1 pair.

Branch tts-kokoro. **This is a rung 1 addition.** It ran on spent boot 4, after rows
4a/4b/4c. Nothing here is a baseline, and every figure is "on the 6a". The model
is page-cached. Derived load is **wall minus elapsed, which includes process
start and WAV write** (and the model load). Every figure was parsed from
out/a2-NN.report, .err and .wall, pulled to the Mac (72 files). RTF is the
binary's own line. P-T6 was committed at cf2fbde, before any a2 row ran.

**Hand gate**, all three ceilings polled until rated, in one invocation: start
uptime 17315.61 (18:06:09), end 17315.70 (18:06:09). It took 1 poll, 0.09 s:
policy0 1,803,000, policy4 2,253,000, policy6 2,802,000.

**Command**, one adb shell invocation: a hand read, then
`COOL=1 sh /data/local/tmp/tts/pennytts.sh a2-00 30 2 0`, then lines 1-17 with
COOL=0 back to back, then a hand read. Mask 30 = cpus 4-5 (see the previous
entry).

    ROW a2 (rung 1 addition): mask 30 (A76 pair, cpus 4-5), 2 threads
    line  elapsed_ms  audio_ms  RTF    wall_ms  derived_load_ms  peak_rss_kB  rss_n  a76_min_kHz  x1_min_kHz  memavail_kB before -> after  kills
    00         2092       829  2.523     5229             3137      287,428     14    2,253,000   2,802,000  2,055,984 -> 2,039,436      0
    01         1820       758  2.402     5002             3182      283,568     13    2,253,000   2,802,000  2,044,812 -> 2,024,548      0
    02         1943       814  2.388     5098             3155      284,660     13    2,253,000   2,802,000  2,041,928 -> 2,024,772      0
    03         2677      1159  2.309     5864             3187      291,468     15    2,253,000   2,802,000  2,027,868 -> 2,031,976      0
    04         8356      4545  1.838    11510             3154      363,260     30    2,253,000   2,802,000  2,053,820 -> 2,019,048      0
    05        12585      6907  1.822    15791             3206      441,196     40    2,253,000   2,802,000  2,022,408 -> 2,017,004      0
    06         7169      3593  1.995    10345             3176      350,996     27    2,253,000   2,802,000  2,064,856 -> 2,013,740      0
    07        10769      5815  1.852    13968             3199      436,904     35    2,253,000   2,802,000  2,026,260 -> 2,038,044      0
    08         9095      4704  1.934    12300             3205      402,552     32    2,253,000   2,802,000  2,033,104 -> 2,026,384      0
    09         8760      4520  1.938    11922             3162      367,212     31    2,253,000   2,802,000  2,036,380 -> 2,020,920      0
    10         6664      3450  1.932     9821             3157      345,980     26    2,253,000   2,802,000  2,017,860 -> 2,037,632      0
    11         6602      3395  1.944     9808             3206      343,748     25    2,253,000   2,802,000  2,045,896 -> 2,065,264      0
    12         6922      3513  1.970    10082             3160      347,864     26    2,253,000   2,802,000  2,068,568 -> 2,037,132      0
    13         6048      3065  1.973     9228             3180      340,308     24    2,253,000   2,802,000  2,048,756 -> 2,032,748      0
    14        16117      7450  2.164    19293             3176      327,996     48    2,253,000   2,802,000  2,035,312 -> 2,023,312      0
    15        19073     10416  1.831    22352             3279      577,280     56    2,253,000   2,802,000  2,025,740 -> 2,010,196      0
    16         6916      3109  2.225    10119             3203      323,404     26    2,253,000   2,802,000  2,005,696 -> 2,032,756      0
    17         9935      4707  2.111    13088             3153      350,452     33    2,253,000   2,802,000  2,031,556 -> 2,011,008      0
    pass: first line start uptime 17316.13, last line end 17543.78, span 227.65 s
    A76 min 2,253,000 kHz = 100.00% of rated at [(0, '17316.13'), (1, '17322.77'), (2, '17329.09'), (3, '17335.89'), (4, '17343.14'), (5, '17356.26'), (6, '17373.73'), (7, '17385.77'), (8, '17401.35'), (9, '17415.15'), (10, '17428.47'), (11, '17439.98'), (12, '17451.31'), (13, '17462.81'), (14, '17473.65'), (15, '17494.46'), (16, '17518.47'), (17, '17530.11')]; first moved on line []
    X1 min 2,802,000 kHz; lines where X1 moved: []
    MemAvailable low-water (before/after reads only) 2,005,696 kB (line 16); kills 0; rc all 0 True; .wall all numeric True
    RTF min 1.822 max 2.523; lines in 1.0-2.0: 11/18; under 1.0: 0; over 2.0: 7
    peak RSS 283,568-577,280 kB; derived load 3137-3279 ms
    elapsed sum, 18 lines: 4b (p2, X1) 85,431 ms; a2 (A76) 143,543 ms; ratio a2/4b 1.680
    samples identical to 4b on all 18: True; stderr extra lines: []

**Hand reads (policy0/4/6):** before line 0, uptime 17315.85 (18:06:09),
1,803,000 / 2,253,000 / 2,802,000. After line 17, uptime 17544.65
(18:09:58), 1,803,000 / 2,253,000 / 2,802,000. All were rated both times.

**Ceilings during the pass:** the A76 ceiling (policy4) read rated, 2,253,000,
in every 0.2 s sample of every line. **The X1 ceiling (policy6) did not move
either**: 2,802,000 on every line. policy0 was read by hand only, before and
after. All 18 rc=0; every .wall has five numeric fields; 0 kill lines; every
tts/a2-NN.sherr is 0 B; no stderr line outside the expected set. Sample counts
are identical to 4b on all 18 lines.

**Elapsed sum, 18 lines:** 4b (X1 pair, 2 threads) **85,431 ms**; a2 (A76 pair,
2 threads) **143,543 ms**. a2/4b = 1.680.

**P-T6, RTF on the A76 pair at 2 threads 1.0-2.0: MISSED.** Min 1.822 (line
05), max 2.523 (line 00). 11 of 18 lines fall inside 1.0-2.0, and **7 of 18 are
above 2.0**: lines 00-03 (the four short lines, 2.309-2.523), 14 (four
sentences, 2.164), 16 (2.225) and 17 (2.111). None is under 1.0.

Derived load on the A76 pair was 3,137-3,279 ms, against 1,875-2,139 ms across
the 41 rows of 4a/4b/4c. The model load runs on the pinned cores too.

What this entry does not say: what the A76 pair does beside the LLM (rung 3);
anything on a fresh boot; what policy0 did during the pass; why neither
ceiling moved on this pass when the X1 ceiling moved on 4b (no reason is
offered); anything from flash; anything about resident RSS, the app or the
7a. Nothing here is a baseline.

## 2026-09-19 — CLAUDE.md slimmed on 19 Sept 2026; full previous text at git show 121dc4b:CLAUDE.md

No phone, no code. CLAUDE.md is loaded into every session and after every
compaction, and at 26,692 words it had become the largest thing in context.
The slim version is the current state only. Everything cut is in this file, at
the entry cited beside it in the new CLAUDE.md, or in `git show 121dc4b:CLAUDE.md`.

    before (121dc4b)   26,692 words   176,423 characters   2,724 lines
    after              5,593 words    36,823 characters    602 lines

Target was under 4,000 words; the brief put "anything with no home in notes.md
STAYS" above the target. What holds it at 5,593: the seven sections kept in
full (What this repo is, How to read, Findings, Constraints, Reporting rules,
Decisions, Do not — 1,286 words, byte-identical except one added paragraph in
How to read); the verbatim hashes, masks and the llama.cpp build line copied
from notes.md 5030-5057; the 6a LIVE block at one line per item; and the items
grep found nowhere in notes.md (the `--es` semicolon trap, `setprop
persist.logd.size` refused, the pasted-newline trap, Terminal app hidden, port
forwarding, subnet, CIDs, SSH `IdentitiesOnly`, `.bashrc`, `input text`,
`ss -ltn`, git/node/pip3 absent, `[200~`, `avb_custom_key`, the Mac/VM shell
commands, `verifiedbootstate=yellow`, and the open threads on Claude Code in
the guest, phone vs Linux box, OS patching and the revert checksum
`03992adc…`).

**Removed or cut to one index line:**
- Every rung's long form — 1, the API question, 2a, 2b, 2c, 2d, "what rung 2
  buys", 3, 3b (with its eviction and user-tap story), 3c, 3d, 3e-i to 3e-v, 3f,
  3h, 3g-i, 3g-ii — now one table row each with its notes.md line.
- Sequencing's done items (native feasibility, Q-A/Q-B, Brief S) — one index
  row each; Sequencing became "What is next".
- The LIVE block's history — kept as current values only. Five stale or
  contradictory lines resolved, later wins: the two "logcat buffer" lines
  (now "not read since the 18 Sept 13:17 reboot; logcat -G does not survive a
  reboot"), "NOTHING is on the phone yet", "NO q17_state.bin EXISTS", the
  15:30:14 "last read" (superseded by TTS rung 1's 18:09:58 read, notes.md
  13870), and "adb ALIVE".
- The toolchain story (why the NDK was not installed, then was), the APK
  build story, and `~~Before any app code~~`.
- The traps' long form: current ones cut to one or two lines each with their
  notes.md line; VM-payload traps listed under one "VM-era traps" heading,
  with a pointer to `git show 121dc4b:CLAUDE.md` lines 1603-2150.
- Open threads already answered: Q9 endurance, the join, "one second is not a
  stream", the memory/storage/delivery/store/compute gates, wake+memory,
  `libvm_payload.so`, the ten-line payload, the q9 sampler log, and the
  assistant-by-tapping thread (answered FAILED, notes.md 1900).
- The Benchmark protocol's story of how each rule was found, and the
  superseded wrapper hashes (rev 1-4). Two protocol lines changed because they
  were stale: "B3 … boot 4 is unspent" (boot 4 is spent; B3 remains NOT
  MEASURED) and "no thermal run" (Brief S was the thermal run).

What this entry does NOT say: that every sentence of every cut block is
repeated word for word in the cited entry — each citation is the entry where
grep found the content, checked by heading, not read end to end. The 6a's
device state in the new file is as last recorded; no phone was read.

## 2026-09-19 — BRIEF T, T0 PART ONE: the Pixel 7a flash record and the first read-only reads. Locked, yellow, GS201, three clusters at policy0/4/6 (A55 x4 / A78 x2 / X1 x2), MemTotal 7,640,308 kB. Nothing pushed, nothing installed, nothing changed on the phone.

First entry for the 7a. Handset: Pixel 7a (lynx), serial 37291JEHN04619.
Every `adb` command in this entry was `adb -s 37291JEHN04619`, the whole remote
command in one single-quoted string. `adb devices -l` at session start listed
this one device and no other; the 6a was not attached and was not touched.

### FLASH RECORD — every line is MATT'S REPORT, NOT READ BY THE BUILDER

- Before flashing: stock Android 17, security update 5 July 2026, "Pixel up to
  date". (Matt's report, not read by the builder)
- Web installer, in Chrome 153.0.8010.53. (Matt's report, not read by the builder)
- Unlock, Download, Flash, Lock: all four completed. (Matt's report, not read by the builder)
- Bootloader screen read "locked" afterwards. (Matt's report, not read by the builder)
- Boot key compared by Matt, on screen, against the install page's Pixel 7a
  line `508d75dea10c5cbc3e7632260fc0b59f6055a8a49dd84e693b6d8899edbb01e4`.
  (Matt's report, not read by the builder)
- "Disable OEM unlocking" unticked at the end of setup; OEM unlocking read ON
  in Developer options afterwards. (Matt's report, not read by the builder)
- PIN set. (Matt's report, not read by the builder)
- USB debugging on, "Always allow" ticked for this Mac. (Matt's report, not
  read by the builder)

### LOCK STATE AND BUILD — read from the phone, one invocation

    ro.boot.flash.locked=[1]
    ro.boot.verifiedbootstate=[yellow]
    ro.boot.vbmeta.device_state=[locked]
    sys.oem_unlock_allowed=[]
    ro.oem_unlock_supported=[]
    ro.build.fingerprint=[google/lynx/lynx:17/CP2A.260705.006/2026091000:user/release-keys]
    ro.build.version.security_patch=[2026-09-01]
    ro.build.version.release=[17]
    ro.build.id=[CP2A.260705.006]
    ro.build.version.incremental=[2026091000]
    ro.product.device=[lynx]
    ro.product.model=[Pixel 7a]
    ro.soc.manufacturer=[Google]
    ro.soc.model=[GS201]
    ro.hardware=[lynx]
    ro.boot.hardware.platform=[gs201]
    ro.bootloader=[lynx-17.0-15199429]
    ro.serialno=[37291JEHN04619]
    uname_r=6.1.176-android14-11-gbba346ef9364
    uptime_s=672.50 wallclock=13:41:46 date=2026-09-19

Against the brief's predictions: flash.locked 1, verifiedbootstate `yellow`
(locked, custom key — correct for GrapheneOS), vbmeta.device_state `locked`,
product.device `lynx` — all as predicted. GrapheneOS build **2026091000**; the
6a's last-recorded build is 2026091001 (CLAUDE.md, not re-read). Security patch
2026-09-01, Android 17, build ID CP2A.260705.006 — the same build ID and patch
as the 6a's record.

**OEM-unlock state is NOT readable from the shell on this build.**
`sys.oem_unlock_allowed` and `ro.oem_unlock_supported` are both empty.
`getprop | grep -iE "oem_unlock|oemunlock|flash.locked|frp|unlock"` returned
only `ro.boot.flash.locked` and `ro.frp.pst`. `settings get global
oem_unlock_allowed` returned `null`. `dumpsys oem_lock` printed nothing, rc=0.
`cmd oem_lock` returned `No shell command implementation.` The service exists
(`service list`: `233 oem_lock: [android.service.oemlock.IOemLockService]`).
**I did NOT try `service call oem_lock <n>`**: the transaction numbers would be
guessed, and the same interface carries a setter. So the only record that OEM
unlocking is ON is Matt's on-screen reading above.

### IDENTITY, MEMORY AND CLUSTERS — one invocation

    MemTotal:        7640308 kB
    MemAvailable:    2928748 kB
    SwapTotal:       3820148 kB
    SwapFree:        1747316 kB
    uptime_s=694.52 wallclock=13:42:08
    --- nproc=8
    /sys/devices/system/cpu/cpufreq/policy0
    /sys/devices/system/cpu/cpufreq/policy4
    /sys/devices/system/cpu/cpufreq/policy6
    /sys/devices/system/cpu/cpufreq/policy0 related_cpus=[0 1 2 3] cpuinfo_max_freq=[1803000] scaling_max_freq=[1803000] cpuinfo_min_freq=[300000]
    /sys/devices/system/cpu/cpufreq/policy4 related_cpus=[4 5] cpuinfo_max_freq=[2348000] scaling_max_freq=[2348000] cpuinfo_min_freq=[400000]
    /sys/devices/system/cpu/cpufreq/policy6 related_cpus=[6 7] cpuinfo_max_freq=[2850000] scaling_max_freq=[2850000] cpuinfo_min_freq=[500000]

Core type per CPU, `/proc/cpuinfo` (Features, implementer and part, pasted in
one line per CPU):

    processor 0-3  CPU implementer 0x41  CPU part 0xd05
    processor 4-5  CPU implementer 0x41  CPU part 0xd41
    processor 6-7  CPU implementer 0x41  CPU part 0xd44
    Features (identical on all eight): fp asimd evtstrm aes pmull sha1 sha2 crc32
      atomics fphp asimdhp cpuid asimdrdm lrcpc dcpop asimddp

0x41 is Arm; 0xd05 / 0xd41 / 0xd44 are Arm's part numbers for Cortex-A55 /
Cortex-A78 / Cortex-X1. **That mapping is from Arm's published part-number list
as I know it, not read from a document this session.** The cluster boundaries
themselves are READ (related_cpus), and they match the public spec the brief
gave as a prediction: 2x X1 at 2.85 GHz (2,850,000 kHz), 2x A78 at 2.35 GHz
(2,348,000 kHz), 4x A55 at 1.80 GHz (1,803,000 kHz).

**The Features line has `asimddp` (dotprod) and `fphp asimdhp` (fp16) and has
NO `i8mm`, NO `sve`, NO `sve2`, NO `sme`.** That is the same constraint the 6a
imposed, so the `armv8.2-a+dotprod+fp16` build is the right target here too
and an `armv8.7a` build would SIGILL on this phone as it would on the 6a.

Layout against the 6a's: **the same policy numbers and the same cpu ranges**
(policy0 = cpus 0-3, policy4 = cpus 4-5, policy6 = cpus 6-7). Different: the
middle pair is A78, not A76; the middle pair's rated clock is 2,348,000, not
2,253,000; the big pair's is 2,850,000, not 2,802,000. policy0's rated
1,803,000 is the same figure as the 6a's. Masks therefore keep their cpu
meaning: `c0` = cpus 6-7 (X1 pair), `30` = cpus 4-5 (A78 pair), `f0` = cpus
4-7.

MemTotal 7,640,308 kB against the 6a's 5,718,280 kB: +1,922,028 kB. SwapTotal
3,820,148 kB against the 6a's 3,145,724 kB: +674,424 kB. **SwapFree was
1,747,316 kB = 45.74% of SwapTotal at uptime 694.52 s, on a boot where
nothing of ours has run.** MemAvailable 2,928,748 kB at the same uptime. These
are first readings on a bring-up boot, not protocol readings and not a
baseline; the phone had been set up by hand since this boot began.

### SETTINGS, APP, adj — one invocation

    stay_on_while_plugged_in=[0]
    pm_path_probe2a=[]
    pm_list_pennyspike=[]
    -1000
    self_oom=-1000

**The probe app is NOT installed** (`pm path com.pennyspike.probe2a` empty,
`pm list packages | grep -i penny` empty). **An `adb shell` process on this
phone has `oom_score_adj` -1000**, read twice (child `sh`, and `/proc/self`),
the same as the 6a.

**`stay_on_while_plugged_in` is 0 on the 7a.** Read again with the screen
timeout, one invocation:

    screen_off_timeout=30000 stay_on_while_plugged_in=0   mWakefulness=Dozing uptime_s=766.27 wallclock=13:43:20

(The `Broken pipe` line printed beside it came from `grep -m1` closing the
pipe early on `dumpsys power`; the value was read.) The 6a ran brief S with
`stay_on_while_plugged_in=15` (notes.md 12030). **With 0 and a 30 s timeout,
an hour-long row on the 7a would turn the screen off and lock, which voids the
row.** The brief forbids changing settings on the phone, so this is recorded and
raised with Matt, not changed.

### CHARGING LIMIT

`settings list global|secure|system`, grepped for
`charg|batt|adaptive_charg|limit|optimi|dock_defend|health`:

    == global
    battery_charging_state_enforce_level=-1
    battery_charging_state_update_delay=-1
    charging_started_sound=/product/media/audio/ui/ChargingStarted.ogg
    low_battery_sound=/product/media/audio/ui/LowBattery.ogg
    low_battery_sound_timeout=0
    wear_charging_experience_enabled=0
    wireless_charging_started_sound=/product/media/audio/ui/WirelessChargingStarted.ogg
    == secure
    charging_sounds_enabled=1
    charging_vibration_enabled=1
    disable_adaptive_auth_limit_lock=0
    sysui_qs_tiles=wifi,bt,cell,airplane,dnd,flashlight,rotation,alarm,screenrecord,battery,mictoggle,cameratoggle,location
    == system

`dumpsys battery` (in full, the relevant lines): `AC powered: true`, `Max
charging current: 3000000`, `status: 2`, `health: 2`, `level: 82`,
`temperature: 345`, `Charging state: 1`, `Charging policy: 1`. `/sys/class/
power_supply/battery/` has no `charge_control_limit`,
`charge_control_end_threshold` or `charge_stop_threshold` (No such file); it
has `charge_type=3`.

**No settings key naming a charge limit exists in any of the three
namespaces.** `Charging policy: 1` is, as I know Android's BatteryManager
constants, CHARGING_POLICY_DEFAULT (the 80% limit would read 4,
ADAPTIVE_LONGLIFE) — **from memory, not read from source this session.** So:
no limit is active by that reading; whether GrapheneOS shows a charging-limit
switch in the Settings app on this build is NOT established — nothing on the
screen was read. Nothing was changed.

### THERMAL AND BATTERY TEMPERATURE — one invocation

    ls: /sys/class/thermal/: Permission denied
    --- zone0 type:
    cat: /sys/class/thermal/thermal_zone0/type: Permission denied
    cat: /sys/class/thermal/thermal_zone0/temp: Permission denied
    --- battery:
    sysfs_temp_dC=345 capacity=82 dumpsys=[ AC powered: true USB powered: false status: 2 level: 82 temperature: 345 ] uptime_s=706.02 wallclock=13:42:19

**`/sys/class/thermal/` is Permission denied to the shell on the 7a**, the
directory and a zone file alike — the same as the 6a. **No SoC temperature can
be read on this phone either.** Battery temperature from sysfs works:
345 dC, and `dumpsys battery` agreed at 345 in the same invocation. Every T row
therefore carries BATTERY temperature only, labelled as such.

### WHAT CARRIES A 6a NUMBER — pennybench.sh rev 5, pennyload.cpp, the gate

`grep -nE "2802|2253|1803|policy|a76|A76|x1|X1|a55|A55|cpus [0-9]" pennybench.sh`:

    41:# policy4 "was never observed to move" and that "only the X1 pair is capped".
    42:# BOTH ARE REFUTED BY MEASUREMENT. policy4 (A76 pair, rated 2,253,000) fell on
    47:# load. policy6 (X1 pair, rated 2,802,000) fell below half rated on every
    53:CEIL6=/sys/devices/system/cpu/cpufreq/policy6/scaling_max_freq
    54:CEIL4=/sys/devices/system/cpu/cpufreq/policy4/scaling_max_freq
    55:# policy0 is the A55 cluster, rated 1,803,000. It has NEVER been sampled during
    58:CEIL0=/sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq
    137:echo "uptime_s ceil_x1 ceil_a76 ceil_a55 MemAvailable_kB ..." > "$SER"
    205:echo "PENNYBENCH ceil_x1_kHz     before=$C6_B min=$C6_MIN after=$C6_A   (policy6, cpus 6-7, rated 2802000)"
    207:echo "PENNYBENCH ceil_a76_kHz    before=$C4_B min=$C4_MIN after=$C4_A   (policy4, cpus 4-5, rated 2253000)"

- Lines 53, 54, 58: the three paths **exist on the 7a and point at the same
  cpu ranges** (read above). They measure the right clusters.
- **Line 205: `rated 2802000` is wrong on the 7a** (read: 2,850,000).
- **Line 207: `rated 2253000` is wrong** (read: 2,348,000) **and `a76` is the
  wrong core name** (0xd41, A78).
- Line 137: the series header names the columns `ceil_x1 ceil_a76 ceil_a55`.
  The values under them are correct (policy6 / policy4 / policy0); the middle
  column's NAME is the 6a's core.
- Lines 41-58 are comments recording 6a history; they change nothing that runs.
- **Nothing in pennybench.sh compares a reading with a rated figure** — the
  numbers on 205/207 are printed text only. The min/before/after values are
  read from sysfs. So rev 5 on the 7a would record correct numbers under two
  wrong labels.

`pennyload.cpp` carries no frequency, mask or cluster. Its one device-shaped
default is the thread count:

    209:        "  -t <n>              n_threads and n_threads_batch   (default 2)\n"
    237:    int  n_threads    = 2;

Every brief S row passed `-t 2` explicitly, so the default is never relied on.

**The gate command is where a 6a number would bite.** As written in notes.md
8778-8781 and used through brief S:

    while [ "$(cat /sys/devices/system/cpu/cpufreq/policy6/scaling_max_freq)" != 2802000 ] ||
          [ "$(cat /sys/devices/system/cpu/cpufreq/policy4/scaling_max_freq)" != 2253000 ]; do
        sleep 5
    done; PENNYBIN=/data/local/tmp/pennyload ./pennybench.sh <tag> c0 -- <pennyload args>

**On the 7a that loop would never exit** — policy6 at rated reads 2,850,000,
never 2,802,000. It also gates two clusters, and brief T says ALL clusters. The
T gate has to compare each policy's `scaling_max_freq` with its own
`cpuinfo_max_freq`, for policy0, policy4 and policy6. `c0` keeps its meaning
(cpus 6-7, the X1 pair) on this phone.

### WHAT THIS ENTRY DOES NOT SAY

Nothing has been pushed to the 7a, nothing installed, no binary run, no hash
taken on the phone. OEM unlocking being ON is Matt's reading only; the shell
cannot see it. The charging-limit answer is "no settings key, and `Charging
policy: 1`", with the meaning of 1 from memory. The A55/A78/X1 names are from
the part numbers as I know them. MemAvailable and SwapFree here are first
readings on a hand-set-up bring-up boot, not the ~5 and ~25 minute protocol
readings, and not a baseline. The screen-on setting is not changed and must be
decided before any hour-long row.

## 2026-09-19 — BRIEF T, T0 PART TWO (i): "Stay awake" re-read after Matt switched it on; pennybench.sh REVISION 6 — labels read off the phone, one report line added, nothing measured differently. rev 6 sha256 ddb39f3c68c32f4a8cc30fc4aa0cf6d374b8377e805cf062e0318fdd34a8aa24, 13,820 B.

### STAY AWAKE — Matt switched it ON by hand in Developer options; re-read, not set

    stay_on_while_plugged_in=15 screen_off_timeout=30000 wake=mWakefulness=Awake uptime_s=1308.69 wallclock=13:52:22

15 = stay on while plugged into AC, USB or wireless (1+2+4+8 bitmask — as I
know Android's BatteryManager plug flags, from memory). The same value the 6a
ran brief S with (notes.md 12030). `screen_off_timeout` is unchanged at 30000;
with stay-on set, the timeout applies only when unplugged. Neither was set by
the builder. `adb devices -l` now reports `transport_id:15` where part one's
read `14`: the USB connection re-enumerated between the two reads, same serial.

### pennybench.sh REVISION 6

Decided by Matt, 19 Sept: labels only; key names (`ceil_x1_kHz`,
`ceil_a76_kHz`) and the series header STAY as they are. rev 5 on the 7a would
have printed correct numbers beside the 6a's rated figures (lines 205/207 of
rev 5, notes.md 14000 onward).

The changes, and nothing else:

1. Header comment naming rev 6.
2. Six reads before the row: `cpuinfo_max_freq` and `related_cpus` for policy6,
   policy4 and policy0, into R6/P6, R4/P4, R0/P0, through a helper `rd` that
   prints `UNREAD` if a read fails or comes back empty — never an empty string.
3. The two ceiling lines print `cpus $P?` and `rated $R? read from
   cpuinfo_max_freq` in place of the written-in `cpus 6-7, rated 2802000` and
   `cpus 4-5, rated 2253000`.
4. One new report line, straight after the ceiling lines:
   `PENNYBENCH rated_kHz policy0=<R0> policy4=<R4> policy6=<R6>   (read from cpuinfo_max_freq before the row)`

**One variable name changed from the draft, and why.** The draft's helper held
its value in `V`. `V` is the poll loop's VmHWM variable, and the series writes
`${V:--1}`. If the first `/proc/$PID/status` read came back empty, the first
series line would carry the helper's leftover value in the VmHWM column
instead of `-1`. The helper now uses `RDV`, which appears nowhere else in the
file (`grep -nwE 'R0|R4|R6|P0|P4|P6|POL|rd|RDV'` finds only the new lines).

`sh -n pennybench.sh` on the Mac's `/bin/sh`: OK. It is not yet parsed by the
phone's shell; that happens after the push.

    rev 5  96163d047d7a91cd3f937cba71c4bce9270e6f84afcb4d700fe7a1513e0889af  12,790 B  (git show HEAD:pennybench.sh before this commit)
    rev 6  ddb39f3c68c32f4a8cc30fc4aa0cf6d374b8377e805cf062e0318fdd34a8aa24  13,820 B

The poll loop, the 0.2 s sleep, the 10 s series, its fourteen columns, the
files written, the kill grep and every other report line are byte-identical to
rev 5 (`git diff` shows 3 hunks: 6 comment lines added, 7 lines of reads added,
2 lines changed, 1 line added).

What this entry does NOT say: that rev 6 has run. It has not been pushed or
smoke-tested; that is the next step, on this (bring-up) boot. Nothing about the
phone's shell parsing it is known yet.
