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
