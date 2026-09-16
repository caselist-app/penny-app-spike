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
