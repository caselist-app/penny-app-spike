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

## State of play — Pixel 6a

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

`verifiedbootstate=yellow` is CORRECT here: locked, verifying against a
custom key. `green` would mean Google's key, i.e. stock.

## LIVE DEVICE STATE — Pixel 6a (FROZEN 19 Sept; the 6a is finished with)

As last recorded. Nothing below was re-read on 19 Sept: at that session's
start `adb devices` listed no phone. Check any line before relying on it.

    Boot          boot 4, rebooted 18 Sept 13:17:19, unlocked by hand. SPENT: S0, S1, S2 and TTS rung 1 (4a/4b/4c/a2) all ran on it. S3 NOT run.
    Last read     uptime 17544.65 s, 18:09:58 on 18 Sept: policy0 1,803,000 / policy4 2,253,000 / policy6 2,802,000, all rated (notes.md 13870).
    Before that   uptime 17039.79 s, 18:01:33 on 18 Sept: MemAvailable 2,078,096 kB, SwapFree 811,612 kB, battery 301 dC (notes.md 13753).
    adb           not connected at the start of the 19 Sept session. GrapheneOS keeps the port charging-only while locked.
    logcat buffer not read since the 18 Sept 13:17 reboot; logcat -G does not survive a reboot.

**OUR APP IS DISABLED** — `adb shell pm disable-user --user 0
com.pennyspike.probe2a`, 15 Sept ~19:57. Nothing of ours starts at boot, no
data was cleared, every store survives. Reverse with `adb shell pm enable
com.pennyspike.probe2a`. **WARNING — DO NOT `pm enable` AS THINGS STAND.** The
installed APK starts SIX boot services, and two of them each ask for 2048MB on
the next boot (`PennySoakService` immediately, `Penny3giService` after
~15-45s) — the two-2GB-VM trap, which on 15 Sept evening took
`com.android.launcher3` and our own app at **adj 100**, 50 kills. Under that
pressure `Penny3evService.java` ~345-352 DELETES the `penny3ev` store on ANY
`run()` failure (`STORE WAS RESET`). **Neutralise one of the two 2GB services
before enabling.** Whether the five pm grants and four assistant preconditions
survive disable/enable is UNTESTED. (notes.md 4457, 4802)

    installed APK sha256  9efe27cb0aa802243123be26bcc0ee5ff9da52f6685a20831a699daf699902fe  156,613 B = probe2a/build/probe2a.apk (notes.md 4802)
    APK path              /data/app/~~AfIhpIXq9pYEyRdOP2bSQw==/com.pennyspike.probe2a-nou1Eur-h0XqviaxcCE9ww==
    All VMs               DOWN as of 15 Sept (vm list not run since). Terminal app / Debian VM DOWN; it holds ~3.6GB when open. adb forward tcp:2222 DEAD.
    penny3ev store        PROBABLY STRANDED by the 15 Sept 16:51 reinstall — no result may rest on it; ck64 0x757b795dd5138044 is re-creatable (notes.md 4802)
    pennysoak store       CONTAMINATED; that run is VOID (notes.md 4457)
    penny3f / penny3eiii  GONE. penny3gi/penny3gic: no store, runs once per process.

`/data/local/tmp` on the 6a, as read 18 Sept 13:11 plus TTS rung 1's `tts/`:

    Qwen3-1.7B-Q4_K_M.gguf  1,107,409,472 B  sha256 b139949c5bd74937ad8ed8c8cf3d9ffb1e99c866c823204dc42c0d91fa181897  (= MANIFEST.txt's HF-LFS value)
    q17_state.bin           46,685,237 B     sha256 707e0ea3c1cc490187616a67ba0097747c8b8c58fcd2dcf38e1870a31a8f6f4d  (re-creatable from penny_system.txt; deterministic across binaries and boots)
    token_fnv1a64           0xcba17a2fcbba49f4  (the reference: rows 1/2/3/5, S0 and all 260 S restores produced it; notes.md 12639)
    pennyload               3,836,992 B      sha256 f52fc60411b55e5ed9eb34e8307f32b45d6bed6f06de85a5347bc02ec2f4ffe9  (= build/pennyload-stripped)
    pennybench.sh rev 5     12,790 B         sha256 96163d047d7a91cd3f937cba71c4bce9270e6f84afcb4d700fe7a1513e0889af
    llama-bench             4,708,216 B      sha256 44015c0614b3f1c0f4ee3240fb8f3a37503420ab7285a36a10ad14abaaaeb84e
    llama-simple            3,805,208 B      sha256 3d6b6afa…
    penny_system.txt        1,911 B          sha256 9496977025bffba32886e447cf10ab2281c439dc7c4811124827762fdb730ff4
    penny_user.txt          95 B             hash not on record
    out/, microdroid/       left alone
    tts/                    TTS rung 1: sherpa-onnx-offline-tts, libonnxruntime.so, penny-kokoro-int8/, pennytts.sh, help.txt, out/, 41 *.sherr (notes.md 13753)

Qwen3.5-2B-Q4_K_M.gguf and q35_state.bin are DELETED (q35_state.bin is not
re-creatable without a row). One model at a time: delete before pushing the
next. `adb push` preserves the SOURCE mtime, so a phone-side mtime dates the
Mac's copy, never the transfer. **The Q4_0 repack path has NEVER executed on
this phone.** (notes.md 9675, 9875, 11824, 12015)

Models on the Mac: `~/Documents/penny-models`, 10 GGUF files, 19,771,681,856
bytes, every one verified against Hugging Face's published LFS sha256. See
MANIFEST.txt there. (notes.md 4693)

**6a CPU layout, taskset masks and ceiling paths** (pennybench.sh:53-58,
notes.md 13840):

    policy0  cpus 0-3  4x Cortex-A55  rated 1,803,000 kHz  /sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq
    policy4  cpus 4-5  2x Cortex-A76  rated 2,253,000 kHz  /sys/devices/system/cpu/cpufreq/policy4/scaling_max_freq
    policy6  cpus 6-7  2x Cortex-X1   rated 2,802,000 kHz  /sys/devices/system/cpu/cpufreq/policy6/scaling_max_freq
    masks    c0 = cpus 6-7 (X1 pair)   30 = cpus 4-5 (A76 pair)   f0 = cpus 4-7 (X1+A76)

Swap is zram, 3,145,724 kB total (`dumpsys meminfo`; `/proc/swaps` and
`/sys/block/zram0/*` are Permission denied). `/sys/class/thermal/` is
Permission denied to shell; battery temperature is read from
`/sys/class/power_supply/battery/temp` in dC and is never SoC temperature.
An `adb shell` process has `oom_score_adj` **-1000**. (notes.md 6282, 11427)

**6a native memory baseline** — MemTotal 5,718,280 kB. On ONE boot, no VM, app
disabled: MemAvailable **940,640 kB at 5.8 min, 2,119,020 at 25.3 min,
2,012,348 at 60.5 min, 1,929,032 at 120.6 min, 1,771,920 at 973.2 min.** At ~25
min across five boots: **2,135,144 to 2,284,200 kB**. Boot 4: **2,218,088 kB at
303.84 s** and **2,347,476 kB at 1501.63 s**. **THERE IS NO SINGLE IDLE NUMBER.
Never quote one without its uptime.** The 5-minute mark is not a stable point.
(notes.md 4457, 4589, 4802, 5227, 12015)

Verify with: `adb shell pm path com.pennyspike.probe2a`.

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
    Boot          bring-up boot SPENT (push, smoke a, smoke b; smoke a met the Cached contamination limb). ROW BOOT SPENT: T1 (notes.md 15016), T2 (15163), T3 (15333), the pulls and the post-T3 re-hash (15521). Brief T is CLOSED (15557). THAT ROW BOOT ENDED UNOBSERVED — on 21 Sept the phone was found on a NEW boot of ~07:39, cause not established, Matt states he did not reboot it (notes.md 15860, 16057). That 21 Sept boot is SPENT by brief U step A2 (notes.md 16241) and by the step D rehearsal (17317). MATRIX BOOT, 21 Sept ~12:41:48, IS SPENT: protocol readings (notes.md 17433, 17531), U1 (17584), U2 (17821), U3 (18087), U4 (18353). U2, U3 and U4 were all LAUNCHED WARM — each gate ran its full 240 polls without reaching TMAX=267. Left at uptime 8617.02, battery 361 dC, 15:05:25; caffeinate stopped. Brief V step B smoke (notes.md 19086), V1 (19620) and V2 (19842) also ran on it; left at uptime 12825.46, battery 288 dC, 16:15:34.
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
    penny-kokoro-int8/      360 files, 38 dirs, 150,880,597 B; every file hashed, list at rows/7a_v/7a_tts_push_phone.sha256
      model.int8.onnx       92,363,779 B     sha256 a089794d1293b91e82f3f2b8bed5417d04ac64447d6ada5f21045cde0799bf99
      voices.bin            28,200,960 B     sha256 1c5a5b983d3d50d8586d437a51f3faa2da7919ce76a013c081e65671a3447c29
      tokens.txt            687 B            sha256 6ebb6bb288f20f3ae8d004d3c2ca27697da27c037d75e81a60e2a6a663f95425
      lexicon-gb-en.txt     6,366,635 B      sha256 c4cbb37316f62210dff52718a7afcaae24f50c032cc75ab47ae67b831d1049e7
      lexicon-us-en.txt     5,956,885 B      sha256 7daaab53a181be9885b853a8582bf1838186317e5dadacbcef9c426d6fa0da14
    tts/out/                7a_tts_smoke_00.*, 7a_tts_smoke_04.*, 7a_tts_v1_x1x1_00..17.* and 7a_tts_v2_a78a78_00..17.* (108 files each, pulled to rows/7a_v/, notes.md 19620, 19842)

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
| 1 | Can the shell user boot microdroid? | YES (penny-box d5a8371); stock `EmptyPayloadApp`, sample DICE values — no attestation claim rests on anything here | 14 Sept | 9 |
| API | Is `android.system.virtualmachine` reachable? | `@SystemApi`; compile-only stubs signed off the device's dex; `getSystemService(VirtualMachineManager.class)`, no `getInstance` | 14 Sept | 173, 422 |
| 2a | Can a sideloaded app touch the API? | YES, uid 10192, after two `pm grant`s | 14 Sept | 422 |
| 2b | Does it own a VM with Google's payload? | YES, `requesterUid: 10192` | 14 Sept | 582 |
| 2c | Does it own a VM running OUR guest image? | NO — every custom-image member is on the hidden-API blocklist | 14 Sept | 854 |
| 2d | Does our own code run inside microdroid? | YES, exit 42 | 15 Sept | 2034 |
| scope | What rung 2 buys | `pm grant` is adb-only and cannot ship. **Never mistake a working `pm grant` prototype for a product.** | 14 Sept | 232, 854 |
| 3 | Does the app wake its VM at boot, locked? | YES, 15-16 s after power-on, `userUnlocked=false`; restart after kill ~1.5 s | 14 Sept | 979, 1225 |
| 3b | Can it take the microphone at boot, locked? | YES, via the assistant role — but only survives a reboot with `voice_recognition_service` set, which no user screen sets | 15 Sept | 1626, 1900 |
| 3c | Can bytes and audio get INTO the guest? | YES, over vsock; the console is outbound-only | 15 Sept | 2243 |
| 3d | Does the whole chain run at boot, locked? | YES, on two reboots | 15 Sept | 2574 |
| 3e-i | Will microdroid give a VM big enough for a model? | 2GB/8 vCPU YES; 4GB kills our own app | 15 Sept | 2748 |
| 3e-ii | Is the memory real? | YES to 1792MB of 2048MB; ceiling is a zram live-lock | 15 Sept | 2921 |
| 3e-iii | Where does a model FILE live? | encrypted ext4 via `setEncryptedStorageBytes`, no RAM cost | 15 Sept | 3096 |
| 3e-iv | Does the store survive a reboot? | YES (size); pre-unlock unaskable over adb | 15 Sept | 3433 |
| 3e-v | Is the store readable before first unlock? | YES, content checksummed | 15 Sept | 4001 |
| 3f | Is the guest CPU real? | YES, parity with Debian; 8 unpinned threads, not a topology | 15 Sept | 3629 |
| 3h | Can a gigabyte be pushed in? | YES, 1.5GB at 99 MB/s | 15 Sept | 3629 |
| 3g-i | Will a 2GB VM start at boot, locked? | YES, two reboots | 15 Sept | 4001 |
| 3g-ii | What happens on a phone in use? | the killer stops at adj 201; the app on screen survives | 15 Sept | 4285 |
| native | Can the 6a run a 1.7-2B model natively? | YES, 10-15 t/s at 1.35-1.74 GiB | 16 Sept | 7543 |
| Q-A/Q-B | Cold load and cached-prefix TTFT | 11 of 12 pass; 4.099 s to first token from cold with a cached prefix (1.7B) | 16-18 Sept | 10995 |
| S | Sustained: one turn a minute for an hour, then 200 back to back | S1 no decay; S2 loses 45% in ~4 min then holds at 54.77%; nothing killed above adj 935 | 18 Sept | 12639 |
| TTS 1 | Does Kokoro int8 run on the 6a? | runs, RTF 1.067-1.570, never real time — see below | 18 Sept | 13753 |
| T1 | 7a, one turn a minute for an hour: does it decay? | NO — settled gen_tps 0.96% slower, ttft 0.07%, 60/60, no kills | 19 Sept | 15016 |
| T2 | 7a, 200 turns back to back: where does it settle? | 57.51% of turn 1, at an X1 ceiling of 984,000 kHz; 1 cached kill at load | 19 Sept | 15163 |
| T3 | 7a, does it recover after T2 with no cooling gate? | YES — settled 99.01% of T1's; within 6.1% by turn 3; X1 rated within 150 s | 19 Sept | 15333 |
| U-A2/A3 | 7a, which core mask and thread count decodes fastest? | mask `c0`, `-t 2` — U1 last-10 11.175 t/s, wall 553.21 s; U4 10.510/603.76, U3 9.835/636.02, U2 7.995/764.54. U1 ran FIRST and U2-U4 LAUNCHED WARM, so mask is not separable from row order | 21 Sept | 18705 |
| U-A1 | Is the A78 pair spared by the thermal limiter? | NO — loaded it falls to 63.50% of rated (56.56% in U4). T2's "policy4 never moved" was an IDLE cluster | 21 Sept | 17821 |
| U-A4 | Is `token_fnv1a64` stable across thread counts? | NO — `-t 2`/`-t 3`/`-t 4` give three values, identical first token, `fnv_all_equal=1` within every row. It is a reproducibility check WITHIN a thread count only | 21 Sept | 18353 |

**TTS rung 1 — CLOSED on the 6a, 18 Sept (branch `tts-kokoro`, merged 19
Sept).** Kokoro int8 (`penny-kokoro-int8`, sid 22) under sherpa-onnx
`sherpa-onnx-offline-tts` sha256 bd7d26e8f1cca82da2596fce2fe1957b2a2ed139f772a7655ec5983cb83c4f2d, NDK r30, android-24, a fresh process
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
root.

## What is next

- **Briefs T and U are CLOSED** (notes.md 15557, 18705). Every 6a number was a
  PREDICTION for the 7a, never a baseline, and stays that way.
- **Stage 1b is DONE.** Brief U closed it: step A OpenCL present and public but
  NOTHING BUILT OR LOADED against it (15860); step A2 a build 5 days 16 h newer
  buys this chip nothing on pp407 at this shape, so **there is no llama.cpp
  adoption brief** (16241, corrected 16542); step B `pennybench.sh` rev 7;
  step D the U1-U4 matrix (18705).
- **DECIDED 21 Sept, via the reviewer (notes.md 18705 section 5):** the row
  shape for stages 2-4 stays **mask `c0`, `-t 2`** — reopens only if a
  one-boot-per-row U1-vs-U4 comparison puts U4 ahead once settled, or if
  prefill at `-t 3`/`-t 4` is measured and pays. **E3 stays OPEN and is NOT
  built now** — decided after stage 3a, which measures whether Kokoro needs the
  X1 pair. **`settled_pct_of_turn1` is retired as a headline metric** for any
  row whose turn 1 is not its peak; absolute last-10 medians and wall times
  lead.
- **NEXT: stage 2 / 3a per the plan.**
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

**VM-era traps: see notes.md 422, 529, 854, 979, 2034, 2243, 2574, 2748, 2921,
3096, 3629, 4001, 4457.** Hidden-API members, over-granted VM memory, two 2GB
VMs reaching adj 100, guards in the intent, `am force-stop` restarting sticky
services (use `pm disable-user`), zram live-lock, incompressible fill, memory
taken at VM creation, no writable guest filesystem, `getOrCreate` stale config,
payload `memcpy`/`memset` and `-nostdlib`, generator-bound transfer figures,
guest cpuinfo and `clone`, same-source host control, CPU count not in the
console, `setCallback` and a watchdog on one executor, Debian-built payloads,
`apt-get update` cost, `getConsoleInput` blocked, one console line per write,
changing payload thread ids, `specialUse|microphone`, `libvm_payload.so`
unreadable, `getInstance` absent, `adb install -r` not restarting an activity.
**Before ANY VM work (stage 8), read the full trap text: git show 121dc4b:CLAUDE.md, lines 1603-2150.**

VM-era traps with no home in notes.md, kept here:

- The Terminal app is **hidden** until enabled at Settings > System >
  Developer options > "Linux development environment". Not in the app
  drawer. Absence of an icon proves nothing.
- Port forwarding does **not** survive a VM restart. Symptom is
  `Connection closed by 127.0.0.1 port 2222` with every indicator looking
  healthy. Fix is `adb shell am force-stop com.android.virtualization.terminal`,
  reopen the app by hand, then rebuild `adb forward`.
- The VM's whole subnet is rebuilt on every device reboot. Never pin an
  address, the gateway's included.
- VM CIDs are allocated in creation order and swap between runs. Never
  pin one.
- SSH needs `-i ~/.ssh/penny-box -o IdentitiesOnly=yes` or it fails with
  a misleading `Permission denied (publickey)`.
- A non-interactive SSH command does not source `.bashrc`, so `claude`
  must be called as `/home/droid/.local/bin/claude`.
- `adb shell ss -ltn` is refused by SELinux. Use the guest's own journal.
- `git` is ABSENT from the guest image, as are node, npm, pip3 and unzip.

The shell from the Mac, both commands, in this order:

    adb forward tcp:2222 tcp:2222
    ssh -i ~/.ssh/penny-box -o IdentitiesOnly=yes droid@localhost -p 2222

Which prompt is which — say it every time, or Matt will run it in the
wrong place. The Mac is `mattstevenson@Matts-MacBook-Pro-2`. The VM is
`droid@debian`. Claude Code inside the VM is a third place. `adb`,
`fastboot`, `git` and all Android build tooling exist **only on the Mac**.

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

The rules only. How each was found is in the notes.md entry cited beside it.

- **Build.** llama.cpp at commit `38a5b42d9a3e82e0a586bcd1caed121f36c87a73`,
  configured from nothing; the exact working line is **notes.md 5030-5057**
  (entry at 5023):

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
- **The wrapper is `pennybench.sh` at the repo root**, revision 5, sha256
  `96163d047d7a91cd3f937cba71c4bce9270e6f84afcb4d700fe7a1513e0889af`, 12,790 B.
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
