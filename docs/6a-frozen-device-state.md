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

