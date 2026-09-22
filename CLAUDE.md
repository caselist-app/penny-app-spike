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

**This file was slimmed on 19 Sept 2026.** The full previous text, with every
rung's story and every trap in long form, is at `git show 121dc4b:CLAUDE.md`.
Every line below that cites "notes.md N" points at the entry heading at line N.

**Slimmed again on 22 Sept** (notes.md 23581): moved text sits verbatim in
`docs/`, never read at session start. The full text before that is
`git show 9413698:CLAUDE.md`.

## Pixel 6a — finished with

Pixel 6a (bluejay), GrapheneOS 2026091001, bootloader LOCKED against a custom
key (`verifiedbootstate=yellow` is correct), OEM unlocking left ENABLED so it
can go back to stock. Full state of play: `docs/6a-record.md`.

Frozen device state: `docs/6a-frozen-device-state.md`,
sha256 effd849c2f3f0414369b926bf9035cebb6c644c93284b36ce2e99d885d0b619d.
The 6a is finished with and never plugged in during 7a work.

## LIVE DEVICE STATE — Pixel 7a (from 19 Sept; the Brief T row phone)

Read from the phone on 19 Sept unless marked. Story in notes.md 14000 (T0 part
one), 14252 (stay awake, rev 6), 14306 (gate, push, smoke tests).
**Every adb command uses `adb -s 37291JEHN04619`, never bare `adb`.**

    Handset       Pixel 7a (lynx), serial 37291JEHN04619, ro.soc.model GS201
    GrapheneOS    2026091000, Android 17, build ID CP2A.260705.006, patch 2026-09-01
    Fingerprint   google/lynx/lynx:17/CP2A.260705.006/2026091000:user/release-keys
    Bootloader    lynx-17.0-15199429; flash.locked=1, verifiedbootstate=yellow, vbmeta.device_state=locked
    Kernel        6.1.176-android14-11-gbba346ef9364
    Boot key      508d75dea10c5cbc3e7632260fc0b59f6055a8a49dd84e693b6d8899edbb01e4 — compared on screen by Matt; not read by the builder
    OEM unlocking ON per Matt's on-screen reading; not readable from shell (sys.oem_unlock_allowed empty, dumpsys oem_lock empty)
    Memory        MemTotal 7,640,308 kB; SwapTotal 3,820,148 kB, zram
    Boot          every boot so far is SPENT (history: docs/7a-boot-history.md). Last: the 21 Sept matrix boot (~12:41:48), spent by briefs U, V, W and X; left at X R4 DONE, uptime 32119.25, 21:37:08, battery 285 dC (notes.md 23273).
    Stay awake    stay_on_while_plugged_in=15 (set by Matt by hand), screen_off_timeout=30000
    Charging      no charge-limit settings key; dumpsys battery "Charging policy: 1" (= default, builder's memory)
    adb shell     oom_score_adj -1000
    Thermal       /sys/class/thermal Permission denied; battery temp from /sys/class/power_supply/battery/temp only, not SoC
    Probe app     NOT installed
    First reads   MemAvailable 2,928,748 kB, SwapFree 1,747,316 kB at uptime 694.52 s, bring-up boot — not a baseline

**7a CPU layout, masks and ceiling paths** (related_cpus and cpuinfo_max_freq, read):

    policy0  cpus 0-3  4x A55  rated 1,803,000 kHz  /sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq
    policy4  cpus 4-5  2x A78  rated 2,348,000 kHz  /sys/devices/system/cpu/cpufreq/policy4/scaling_max_freq
    policy6  cpus 6-7  2x X1   rated 2,850,000 kHz  /sys/devices/system/cpu/cpufreq/policy6/scaling_max_freq
    core names  from Arm part numbers 0xd05/0xd41/0xd44, builder's memory, not a checked source
    masks       c0 = cpus 6-7 (X1 pair)   30 = cpus 4-5 (A78 pair)   f0 = cpus 4-7 (X1+A78)
    features    asimddp, fphp, asimdhp; NO i8mm, NO sve — armv8.2-a+dotprod+fp16 stays the target
    row shape   -t 2, mask c0
    gate        the T gate, notes.md 14306: each policy's scaling_max_freq against its OWN cpuinfo_max_freq. The 6a gate at notes.md 8778 never exits here.

`/data/local/tmp` on the 7a, pushed and hashed 19 Sept (notes.md 14306):

    pennyload               3,836,992 B      sha256 f52fc60411b55e5ed9eb34e8307f32b45d6bed6f06de85a5347bc02ec2f4ffe9  (= build/pennyload-stripped; 0 smmla / 0 SVE / 898 sdot over 735,079 lines)
    pennybench.sh rev 7     17,519 B         sha256 ca3f8414c3295ff953c96591dd61b46de2008fac7563311703d8491826e484d7  (rev 6 ddb39f3c… overwritten 21 Sept; series interval 7 s, policy0 polled at 0.2 s — notes.md 16698)
    Qwen3-1.7B-Q4_K_M.gguf  1,107,409,472 B  sha256 b139949c5bd74937ad8ed8c8cf3d9ffb1e99c866c823204dc42c0d91fa181897  (= MANIFEST.txt's HF-LFS value)
    penny_system.txt        1,911 B          sha256 9496977025bffba32886e447cf10ab2281c439dc7c4811124827762fdb730ff4
    penny_user.txt          95 B             sha256 b61e0a992e5e8b4cd479c8596c63381b95372ee8b0c3982b895259f9b7a4121b  (first recorded hash, Mac copy)
    q17_state.bin           46,685,237 B     sha256 707e0ea3c1cc490187616a67ba0097747c8b8c58fcd2dcf38e1870a31a8f6f4d  (written ON the 7a by smoke a; = the 6a's)
    token_fnv1a64           0xcba17a2fcbba49f4  (T-A4 reference on the 7a; the same value as the 6a's)
    out/                    7a_t0_smoke_a.*, 7a_t0_smoke_b.*

`/data/local/tmp/tts` on the 7a, pushed from the Mac's existing files and hashed 21 Sept, 363 files, phone list = Mac list (notes.md 19086):

    sherpa-onnx-offline-tts 2,432,496 B      sha256 bd7d26e8f1cca82da2596fce2fe1957b2a2ed139f772a7655ec5983cb83c4f2d  (= the 6a's; sherpa-onnx a5b4a94, android-24, NDK r30)
    libonnxruntime.so       22,249,560 B     sha256 33847ad43bffe204699fd4a27f7f3603452a8cdaf2f9a44983a0bc31ffcf2da1
    pennytts.sh rev 2       19,278 B         sha256 3200e06cfed274d34cc459a919d7a3c08307eeea2749867caba805ae49c3d17b  (three-policy gate, GATECAP 240, optional TMAX — notes.md 18977)
    pennytts.sh rev 3       20,351 B         sha256 90cbeea1eb05e3857a5702ca97ab9439d7cae94a34b6a7a0e66a5fa69c784b88  OVERWROTE rev 2 on the phone 21 Sept ~16:50 (brief W step D, notes.md 20666). MODELDIR/MODELFILE env, defaults = rev 2's; keys model_path, model_bytes last (notes.md 20593)
    pennyspeak              2,466,712 B      sha256 9be8e0e44d868460f6408209c9590ea7c1291ce6823b4a2c60352d6d540ba14f  (= build/pennyspeak/pennyspeak-stripped; resident Kokoro over sherpa-onnx's C API, brief X, notes.md 21761; pushed 21 Sept ~18:36, chmod 755; = the Mac build by sha256 only)
    pennyspeak.sh           18,157 B         sha256 dc2706fdd1b497fbc2528a00a90e71a936ef014c08c4ed723ce3f598ba48c698  -rw-rw-rw-, run as `sh pennyspeak.sh`; OVERWROTE 9a306482… (Matt's named exception) 21 Sept ~19:18 (brief X step D2, notes.md 22063)
    penny-kokoro-int8/      360 files, 38 dirs, 150,880,597 B; every file hashed, list at rows/7a_v/7a_tts_push_phone.sha256
      model.int8.onnx       92,363,779 B     sha256 a089794d1293b91e82f3f2b8bed5417d04ac64447d6ada5f21045cde0799bf99
      voices.bin            28,200,960 B     sha256 1c5a5b983d3d50d8586d437a51f3faa2da7919ce76a013c081e65671a3447c29
      tokens.txt            687 B            sha256 6ebb6bb288f20f3ae8d004d3c2ca27697da27c037d75e81a60e2a6a663f95425
      lexicon-gb-en.txt     6,366,635 B      sha256 c4cbb37316f62210dff52718a7afcaae24f50c032cc75ab47ae67b831d1049e7
      lexicon-us-en.txt     5,956,885 B      sha256 7daaab53a181be9885b853a8582bf1838186317e5dadacbcef9c426d6fa0da14
    penny-kokoro-fp32/      pushed 21 Sept ~16:50 (brief W step D, notes.md 20666); 360 files, 384,051,680 B; list at rows/7a_w/7a_tts_w_push_phone.sha256 (= Mac list, diff rc=0)
      model.fp32.onnx       325,534,862 B    sha256 a0986d39118221f730dd3322900071075bab81b9b71cf44ef67617066f62409f  (kokoro-v1.0.onnx + sherpa fp32's 16 metadata_props, notes.md 20440; input has no hash from its originating project, matched only against the fastrtc/kokoro-onnx HF mirror)
      voices.bin, tokens.txt, both lexicons, espeak-ng-data/   byte-identical copies of penny-kokoro-int8's (hashes above)
    tts/out/                7a_tts_smoke_00.*, 7a_tts_smoke_04.*, 7a_tts_v1_x1x1_00..17.* and 7a_tts_v2_a78a78_00..17.* (108 files each, pulled to rows/7a_v/, notes.md 19620, 19842); 7a_tts_wsmoke_{int8_00,fp32_00,fp32_04}.*, 7a_tts_w1_fp32_x1x1_*, 7a_tts_w2_fp32_a78a78_*, 7a_tts_w3_int8_x1x1_* (570 files in out/ after W3; pulled to rows/7a_w/, notes.md 20666, 21181, 21348, 21499)
    tts/out/ (brief X)      7a_tts_xsmoke_fp32.*, 7a_tts_xsmoke_int8.*, 7a_tts_xsmoke_fp32_rev.* (8 files each; 594 files in out/ after step D2; pulled to rows/7a_x/, notes.md 22063)

Verify with: `adb -s 37291JEHN04619 shell 'pm path com.pennyspike.probe2a'` (expect empty).

## Mac toolchain

Command-line only — no Android Studio, deliberately (notes.md 277):

    Temurin JDK    21.0.12.1 arm64, /Library/Java/JavaVirtualMachines/temurin-21.jdk
    SDK root       /opt/homebrew/share/android-commandlinetools
    platform       platforms;android-37.0    (Android 17 is API 37)
    build-tools    build-tools;37.0.0
    platform-tools 37.0.1
    Gradle         9.7.1  — INSTALLED BUT NOT USED
    adb/fastboot   /opt/homebrew/bin, Homebrew android-platform-tools
    NDK            30.0.16248370   INSTALLED 15 Sept evening
    cmake          3.22.1-g37088a8 INSTALLED 15 Sept evening, sdkmanager
    ninja          1.10.2          INSTALLED 15 Sept evening, sdkmanager,
                                   inside the cmake package's own bin/
    ninja (brew)   1.13.2          also present, and it SHADOWS the above

**Neither `cmake` nor the SDK's `ninja` is on `PATH`, and a second ninja is.**
Name both by full path
(`/opt/homebrew/share/android-commandlinetools/cmake/3.22.1/bin/`) or the build
silently uses Homebrew's. (notes.md 4693, 5023)

**The APK is hand-built, not Gradle-built.** `probe2a/build.sh` runs eight
stages (aapt2 compile/link, javac stubs, javac app, d8, payload package-and-align,
apksigner) and packs FIVE guest payloads, each Stored and page-aligned, set by
`PENNY_PAYLOAD_SO`, `PENNY_PAYLOAD_3C_SO`, `PENNY_PAYLOAD_3EII_SO`,
`PENNY_PAYLOAD_3EIII_SO` and `PENNY_PAYLOAD_3F_SO`. **Add a payload, never edit
one. Never run `build.sh` bare** — see the traps. (notes.md 338, 2034, 4802)

`build-pennyload.sh` builds `pennyload` from the llama.cpp objects below with no
cmake and no download. `pennytts.sh` is TTS rung 1's wrapper.

## Answered — one line each

| Rung | Question | Answer | Date | notes.md |
|---|---|---|---|---|
| 1, API | Shell boots microdroid? API reachable? | YES; `@SystemApi` via stubs | 14 Sept | 9, 173, 422 |
| 2a, 2b, 2d | Sideloaded app owns a VM, runs our code? | YES, after two `pm grant`s | 14-15 Sept | 422, 582, 2034 |
| 2c | Owns a VM, OUR image? | NO, hidden-API blocklist | 14 Sept | 854 |
| scope | What rung 2 buys | `pm grant` is adb-only and cannot ship. **Never mistake a working `pm grant` prototype for a product.** | 14 Sept | 232, 854 |
| 3, 3d, 3g-i | Wakes at boot, locked? | YES | 14-15 Sept | 979, 1225, 2574, 4001 |
| 3b | Microphone at boot, locked? | YES, assistant role | 15 Sept | 1626, 1900 |
| 3c | Bytes and audio into guest? | YES, vsock | 15 Sept | 2243 |
| 3e-i, ii | VM big enough? Memory real? | 2GB YES, real to 1792MB; 4GB kills our app | 15 Sept | 2748, 2921 |
| 3e-iii to v | Model file store? | encrypted ext4; survives reboot; readable before unlock | 15 Sept | 3096, 3433, 4001 |
| 3f, 3h | CPU real? 1GB in? | YES, cannot pin cores; 1.5GB at 99 MB/s | 15 Sept | 3629 |
| 3g-ii | Phone in use? | killer stops at adj 201 | 15 Sept | 4285 |
| native | Native 1.7-2B? | YES, 10-15 t/s | 16 Sept | 7543 |
| Q-A/Q-B | Cold load, cached TTFT | 11 of 12 pass; 4.099 s | 16-18 Sept | 10995 |
| S | Sustained, 6a | S1 no decay; S2 holds 54.77% | 18 Sept | 12639 |
| TTS 1 | Kokoro int8 on the 6a? | RTF 1.067-1.570, never real time | 18 Sept | 13753 |
| T1 | 7a, one turn a minute for an hour: does it decay? | NO — settled gen_tps 0.96% slower, ttft 0.07%, 60/60, no kills | 19 Sept | 15016 |
| T2 | 7a, 200 turns back to back: where does it settle? | 57.51% of turn 1, at an X1 ceiling of 984,000 kHz; 1 cached kill at load | 19 Sept | 15163 |
| T3 | 7a, does it recover after T2 with no cooling gate? | YES — settled 99.01% of T1's; within 6.1% by turn 3; X1 rated within 150 s | 19 Sept | 15333 |
| U-A2/A3 | 7a, which core mask and thread count decodes fastest? | mask `c0`, `-t 2` — U1 last-10 11.175 t/s, wall 553.21 s; U4 10.510/603.76, U3 9.835/636.02, U2 7.995/764.54. U1 ran FIRST and U2-U4 LAUNCHED WARM, so mask is not separable from row order | 21 Sept | 18705 |
| U-A1 | Is the A78 pair spared by the thermal limiter? | NO — loaded it falls to 63.50% of rated (56.56% in U4). T2's "policy4 never moved" was an IDLE cluster | 21 Sept | 17821 |
| U-A4 | Is `token_fnv1a64` stable across thread counts? | NO — `-t 2`/`-t 3`/`-t 4` give three values, identical first token, `fnv_all_equal=1` within every row. It is a reproducibility check WITHIN a thread count only | 21 Sept | 18353 |

6a rows shortened; full rows and TTS rung 1: `docs/6a-record.md`.

## What is next

- **Brief T CLOSED** (notes.md 15557): on the 7a one turn a minute does not decay; 200 back to back settle at 57.51% of turn 1. Every 6a number was a
  PREDICTION for the 7a, never a baseline, and stays that way.
- **Brief U CLOSED** (notes.md 18705): closed stage 1b; row shape `c0`, `-t 2`; no llama.cpp adoption brief.
- **DECIDED 21 Sept, via the reviewer (notes.md 18705 section 5):** the row
  shape for stages 2-4 stays **mask `c0`, `-t 2`** — reopens only if a
  one-boot-per-row U1-vs-U4 comparison puts U4 ahead once settled, or if
  prefill at `-t 3`/`-t 4` is measured and pays. **E3 stays OPEN and is NOT
  built now** — decided after stage 3a, which measures whether Kokoro needs the
  X1 pair. **`settled_pct_of_turn1` is retired as a headline metric** for any
  row whose turn 1 is not its peak; absolute last-10 medians and wall times
  lead.
- **Brief V CLOSED** (notes.md 20053): Kokoro int8 on the 7a, fresh process, X1 pair RTF median 1.081, 0 of 18 lines under 1.0.
- **Brief W CLOSED** (notes.md 21734): fp32 0.75× int8's generate time on the X1 pair, all 18 lines under RTF 1.0; over 1.0 on every A78 line.
- **Brief X CLOSED** (notes.md 23543): resident Kokoro within 1% of fresh; Kokoro needs the X1 pair; fp32 goes to steps 4-5. **NEXT: brief Y, time to first audio.**
- **Not done, in the order Brief S left them:** the `-ub` test that separates
  batch size from micro-batch size; the on-device VOICE bake-off — Kokoro-82M
  `bf_isabella` and `kokoro-onnx` int8 under sherpa-onnx (**Matt's decision,
  18 Sept**), measured with STT and the LLM **resident at the same time**. TTS
  rungs 2-4 (resident, beside the LLM, in the app) are not done.
- **Also not done:** S3 on the 6a (not taken, Matt's call); a growing context
  (the S turn is the same 20 tokens, KV cache restored, never extended) —
  **brief U's matrix says NOTHING about prefill on any shape, because every
  turn of every row restores the same 407-token prefix (notes.md 18705)**;
  Q4_0;
  anything on battery; B3 on Qwen3.5-2B; **any judgement of output quality**.
- **PARKED: can a NEW encrypted store be created before first unlock?** Only
  matters for a model inside a VM, which is closed. Reopens only if model
  isolation becomes a requirement; then it costs one new VM name
  (`penny3ev2`), one build and two reboots. (notes.md 4457)
- **Rung 4 — the OS image. DO NOT START IT.** Build GrapheneOS from source,
  preinstall the app, sign with our platform key, flash, lock, verify
  attestation covers the app. Weeks. Not now.
- **Do not flip `settings put global hidden_api_policy 1`** (the one 2c
  experiment left). Device-wide, proves nothing shippable. Raise it with Matt
  before ever doing it. (notes.md 854)

Do not work ahead of the current rung.

## Open threads worth not losing

- **Device-encrypted storage is a confidentiality trade-off.** Waking before
  first unlock needs `/data/user_de/0/<pkg>`, readable without the PIN. Decide
  deliberately what may live there before rung 4 designs storage. (notes.md 979)
- **The 6a does NOT support protected VMs** — `getCapabilities()` returns 2.
  Unknown whether it is the silicon, GrapheneOS or Android 17. (notes.md 529)
- **Nothing yet hears anything.** Audio has reached a guest (3c/3d) and TTS runs
  natively, but no recognition has run on this handset. (notes.md 2574)
- Claude Code has never done real work in the guest. `git` is absent. One
  arithmetic prompt at 317MiB says nothing about an agent holding a long
  context and running tools.
- Whether the phone earns its place at all, versus a small Linux box with
  no permission games and no patch pipeline. The attestation story is
  what justifies the phone.
- Who patches the OS, and how fast, if we fork. Needs an answer before
  rung 4.
- This 6a goes back to Back Market and is replaced with a 7a. The revert
  path is verified on paper — build CP2A.260705.006, checksum
  `03992adc723742de3fa12c6a40fb19ecbcd7f388a9eaacf93e27af466a79cb33` —
  but has never been exercised. Do the dry run on a calm evening.

## Traps that have already cost time

**Current — for native work on either phone:**

- The phone's shell is mksh R59, not macOS sh. LINES and COLUMNS are mksh built-ins and silently ignore assignment (found 21 Sept, brief X step D: LINES read back 24 whatever was assigned). A script is not tested until it has run on the phone; a Mac pass proves logic only. Every new variable name gets an echo-back test on the device first.
- **`adb shell` re-joins its arguments.** Wrap the WHOLE remote command in ONE
  single-quoted string, or read the file whole and do arithmetic on the Mac. A
  remote command failing in a way that makes no sense for the tool is this.
  (notes.md 9675)
- **An unclosed quote in an `adb shell` poll loop reads nothing and spins
  silently.** Dry-run the exact invocation once in the foreground before
  arming any loop. (notes.md 12015)
- **A 10 s series misses sub-10 s ceiling dips.** Never quote a series
  "fraction at rated" without the poll-loop `ceil_*_kHz min=` beside it.
  (notes.md 12639)
- **The last `.series` line can be a teardown sample.** If `VmRSS` is far below
  `VmHWM`, use the second-to-last line or the row minimum, and say which.
  (notes.md 12015)
- **`policy0` throttles under sustained load** (738,000 of 1,803,000 on S2,
  with nothing scheduled on it). The cap is package-wide; pinning away from a
  cluster does not keep its clock up. (notes.md 12639)
- **An interrupted session's tool output is in the transcript**,
  `~/.claude/projects/<path-with-dashes>/<session-uuid>.jsonl`. Grep it before
  claiming anything cannot be quoted. (notes.md 9875)
- **`system_profiler SPUSBDataType` is a dead check on this Mac** — empty with
  a phone attached. Use `adb devices` and `adb get-state`. (notes.md 5227)
- **GrapheneOS keeps USB charging-only while locked.** Nothing pre-unlock can be
  measured by SENDING a command; a pre-unlock question must be asked by a
  `directBootAware` component that starts itself from `LOCKED_BOOT_COMPLETED`
  and logs, read back after unlock. (notes.md 3433)
- **`adb logcat -G 64M` before a flood-heavy measurement; it dies on reboot.**
  The persistent route is refused: `setprop persist.logd.size 64M` returns
  `Failed to set property ... See dmesg for error reason` (SELinux, measured 15
  Sept). So a boot window is always recorded at the default buffer size; have
  boot-time components re-emit one `SUMMARY` line at a quiet moment.
  (notes.md 2748)
- **`brew install gradle` runs on Homebrew's JDK 26**, not Temurin 21, and the
  failure reads as a code error. Gradle is not used; if it returns, pin
  `org.gradle.java.home`. (notes.md 277)
- **Copy first, kill second.** `sdkmanager` wipes its own temp dir on exit.
  (notes.md 2034)
- **`sh build.sh` with no `PENNY_PAYLOAD_*_SO` set is DESTRUCTIVE** — it
  compiles a payload with the NDK without the required flags and ships an APK
  with only `PennyPayload.so`, stranding every store. Set all five to the files
  in `probe2a/build-payloads/`. (notes.md 4802)
- **The wake path:** `BOOT_COMPLETED` fires at first unlock, not boot — use
  `LOCKED_BOOT_COMPLETED` with `directBootAware`; the VM dir must use
  `createDeviceProtectedStorageContext()` or it fails `Required key not
  available`; the boot broadcast's `duration:20000` exemption governs
  `startForeground` only, not VM time. (notes.md 979, 4001)
- **A boot-started FGS is denied the microphone unless the app holds the
  assistant role**; a third-party assistant is evicted at every boot unless
  `android:selectableAsDefault="true"` and `settings put secure
  voice_recognition_service` are both set; a suppressed microphone returns
  silent zeros without throwing — decide on the samples. (notes.md 1626, 1900)
- Pasting a long command into the Mac terminal can insert a real newline
  at the wrap point, splitting it in two. It cost a bogus `pm grant`
  failure on 14 Sept. Keep commands to one short line; do not chain two
  `adb shell` calls with `;` in a single quoted string.
- `adb shell input text` eats `>` `|` and `;` unless wrapped in single
  quotes.
- **A `;` inside an `--es` extra is eaten by the device's shell.** `adb shell
  am start ... --es plan "1;2,200,0,"` fails with `/system/bin/sh: 3,200,0,:
  inaccessible or not found` — the double quotes are stripped by the Mac's
  shell and the phone's shell then splits on the semicolon. Wrap the value in
  single quotes INSIDE the double quotes: `--es plan "'1;2,200,0,'"`. Same
  family as the `input text` trap; cost a minute on 15 Sept.
- Pasting into a terminal whose session was killed mid-stream can leak
  literal `[200~` bracketed-paste characters. Retype rather than paste.
- Returning to stock: erase `avb_custom_key` while the bootloader is
  **unlocked**, before flashing. Getting this order wrong is the one way
  to strand the device.

**VM-era traps** are verbatim in `docs/vm-era-traps.md`. **Before ANY VM work
(stage 8), read it, then git show 121dc4b:CLAUDE.md, lines 1603-2150.**

`adb`, `fastboot`, `git` and all Android build tooling exist **only on the Mac**.

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
  there is. ONE exception, added 22 Sept: `docs/` holds verbatim text
  moved out of this file to keep it under Claude Code's 40,000-character
  warning. A `docs/` file is created only by a slimming brief, contains
  only text that was already in this file, and is never edited
  afterwards — corrections go in `notes.md` like everything else. It is
  not a place for new prose, and nothing in it is read at session start.
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

The rules only. How each was found is in the notes.md entry cited beside it.

- **Build.** llama.cpp at commit `38a5b42d9a3e82e0a586bcd1caed121f36c87a73`,
  configured from nothing; the exact working line is **notes.md 5030-5057**
  (entry at 5023), also verbatim in `docs/llama-build-command.md`.

- **Build flags: `-DGGML_CPU_ARM_ARCH=armv8.2-a+dotprod+fp16`, NEVER
  `armv8.7a`, and never as a global `-march`.** (llama.cpp commit
  `38a5b42d9a3e82e0a586bcd1caed121f36c87a73`; notes.md 5030-5057)
  `armv8.7a` turns on i8mm, which Tensor G1's Cortex-X1/A76/A55 do not
  have, so the compiler emits `smmla`-family instructions and the binary
  dies with SIGILL on the phone — reading as a broken build, not a wrong
  flag.
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
  **`examples/simple` (`llama-simple`) IS buildable offline** — build it: a
  tok/s figure with no printed text cannot show the model produces sensible
  output. (notes.md 5023)
- **The wrapper is `pennybench.sh` at the repo root**, revision 7, sha256
  `ca3f8414c3295ff953c96591dd61b46de2008fac7563311703d8491826e484d7`, 17,519 B.
  It supplies every column except pp/tg tok/s: `VmHWM` polled at 5 Hz, meminfo
  and uptime either side, clock ceilings `before=`/`min=`/`after=` with
  `min_at`, `pswpin`/`pswpout`/`pgmajfault`, a 10 s `.series` (14 columns,
  `ceil_a55` = policy0, battery from sysfs), and the kill grep
  (`am_kill|lowmemorykiller| lmkd : |has died`, **excluding `am_cpu`**). Push it
  beside the binary; the phone copy must hash to the repo copy. **Every row's
  numbers are read from `out/<tag>.report`, `.bench` and `.series`, never from
  scrollback.** (notes.md 5227, 6282, 11824)
- **EVERY MATRIX RUN USES `-lm none`. `-mmp` DOES NOT EXIST AT THIS COMMIT**
  (`-mmp 0` prints usage and loads nothing). `-lm none` is what a real app
  would do, and the only mode where peak RSS IS the working set. (notes.md 5805)
- **COOL BETWEEN ROWS — decided 16 Sept by Matt.** Rows back to back measure
  heat, not cores; the limiter is package-wide. Every row is gated on ALL
  cluster ceilings reading rated (`scaling_max_freq` = `cpuinfo_max_freq`),
  polled and launched in the same shell invocation so nothing intervenes. **Do
  not compare a `c0` figure with another unless both carry their thermal
  state.** (notes.md 6038, 6282, 12639)
- **Contamination: if `Cached` drops by roughly the model size inside a row,
  or `SwapFree` falls below ~10% of `SwapTotal`, the boot is spent** — no
  further row on it counts. Reboot, take the ~5 min and ~25 min `MemAvailable`
  readings, re-run. (notes.md 6842)
- **A kill count is meaningless without the row's `MemAvailable` before it**,
  and a long-running boot flatters it. Every row reports kills WITH
  `MemAvailable` before; a zero on a tired boot is never quoted as "this model
  causes no kills". (notes.md 6842)
- **An `adb shell` process has `oom_score_adj` -1000 and cannot be LMK-killed.**
  `pennybench.sh` writes **200** to the child (the perceptible /
  foreground-service band) and prints `pre=` and `post=`. Even then, "survived"
  is weak evidence unless the killer came close. (notes.md 11427)
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
- Nothing VM-hosted. The thermal run is done (Brief S, notes.md 12639).
- Done = a closing entry that exists and says what failed — not that every
  prediction passed. (notes.md 7543, 10995, 12639)

## Do not

- Do not start rung 4.
- Do not compact, summarise or reorganise `notes.md` in either repo.
- Do not write product or architecture thinking into this repo.
- Do not disable OEM unlocking on this device while it still has to go
  back.
- Do not re-lock the bootloader on a partial image.
