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
