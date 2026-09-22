- **Briefs T and U are CLOSED** (notes.md 15557, 18705). Every 6a number was a
  PREDICTION for the 7a, never a baseline, and stays that way.
- **Stage 1b is DONE.** Brief U closed it: step A OpenCL present and public but
  NOTHING BUILT OR LOADED against it (15860); step A2 a build 5 days 16 h newer
  buys this chip nothing on pp407 at this shape, so **there is no llama.cpp
  adoption brief** (16241, corrected 16542); step B `pennybench.sh` rev 7;
  step D the U1-U4 matrix (18705).
- **Brief V (stage 3a step 1, Kokoro int8 on the 7a) is CLOSED** (notes.md 20053).
  V1 X1 pair c0 2t, spent boot, model page-cached, NOT a row-boot figure: RTF 1.005-1.309, median 1.081, 0 of 18 under 1.0, elapsed sum 79,357 ms, X1 min 87.96%.
  V2 A78 pair 30 2t, spent boot, model page-cached, NOT a row-boot figure, RAN SECOND 1.5 C warmer: RTF 1.280-1.740, median 1.367, elapsed sum 100,262 ms (1.263x V1, not a full-clock ratio), A78 never left rated.
  Phone WAVs differ from the Mac's on line 12 (-170 ms) and line 17 (+12 ms); P-T5 held on 3 lines only; a listen to line 12 is owed.
- **NEXT: stage 3a step 2 (fp32).** It needs a download, and **Matt approves each file** before it is fetched. **DONE — Brief W CLOSED (notes.md 21734).** fp32 0.75× int8's generate time on the X1 pair (all 18 lines under RTF 1.0), 0.96× on the A78 pair (over 1.0 on every line); ~130 MB more fresh-process peak RSS, ~270-380 ms more load. **NEXT: stage 3a step 3 (resident Kokoro)** — reviewer recommends fp32 with int8 run beside it, and says the A78-pair-for-voice allocation reopens (step 5 weighs more); both are Matt's decisions.
