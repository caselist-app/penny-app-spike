#!/system/bin/sh
# pennytts.sh -- run sherpa-onnx-offline-tts ONCE against penny-kokoro-int8 and
# capture what the binary does not print: wall time from exec to exit, peak RSS
# and its anonymous/file split, MemAvailable and swap counters either side, LMK
# kills during the run, the CPU clock ceilings before/min/after, and the audio
# length read from the WAV header. The sibling of pennybench.sh, same shapes.
#
# REVISION 1, 18 Sept, TTS rung 1 (branch tts-kokoro). The rev 1 cool gate
# waited for two written-in clock figures, the ones that were rated on the
# phone it ran on, and the report printed them as "rated". Nothing numbered it.
#
# REVISION 2, 21 Sept, for brief V (stage 3a step 1). Made the way pennybench.sh
# rev 6 and rev 7 were made: SUBSTITUTIONS and ADDITIONS, no removals.
#   SUBSTITUTIONS
#   1. THE COOL GATE. Rev 1 polled policy6 and policy4 every 1 s, with no cap,
#      until each equalled a written-in figure -- figures that are not the rated
#      clocks of every phone, so on a phone with other clocks it never exits.
#      Rev 2 polls ALL THREE policies every 5 s and compares each one's
#      scaling_max_freq with its OWN cpuinfo_max_freq, read at run time. After
#      GATECAP failed polls (default 240, i.e. ~20 min) it launches anyway and
#      says GATE TIMED OUT - LAUNCHED WARM. Modelled on the brief U gate
#      recorded verbatim at notes.md 17230.
#   2. "rated" in the report is read from each policy's cpuinfo_max_freq, with
#      the cpu list from related_cpus, instead of being written in -- as
#      pennybench.sh rev 6.
#   3. ONE KEY RENAME: ceil_a76_kHz -> ceil_a78_kHz and ceil_a76_min_at ->
#      ceil_a78_min_at. policy4 is the A76 pair on the Tensor G1 and the A78
#      pair on the G2; the brief V ruling is to name the second.
#      NO OTHER EXISTING KEY IS RENAMED OR MOVED.
#   ADDITIONS
#   4. policy0 (the A55 cluster) joins the 0.2 s poll with before/min/after and
#      min_at, as pennybench.sh rev 7. The poll now does a THIRD cat per
#      iteration, so REV 2 rss_samples COUNTS ARE NOT COMPARABLE WITH REV 1's.
#   5. Battery temperature (sysfs, tenths of a degree C -- NOT SoC) before and
#      after the line.
#   6. An optional battery limb on the gate: TMAX=<dC>. UNSET BY DEFAULT, and
#      when unset the gate tests clocks only. A caller who does not set TMAX
#      gets a clocks-only gate. When set, the gate also waits for the battery
#      to read <= TMAX.
#   7. Report keys cool_gate_first, cool_gate_result, ceil_a55_kHz,
#      ceil_a55_min_at, rated_kHz and batt_temp_dC. Placed so that no existing
#      key changes its order relative to any other.
#
# usage:  pennytts.sh <tag> <taskset-mask|none> <threads> <line>
#         <line> is 0-17 (the 18 lines of ~/kokoro-models/abtest_sherpa.py, in
#         order, embedded below so no "£" or apostrophe has to survive adb
#         quoting) or any other string, which is spoken as given.
# e.g.    pennytts.sh ack-1 c0 2 0              "On it.", X1 pair, 2 threads
#         pennytts.sh p4-13 none 4 13           line 13, unpinned, 4 threads
#
# env:    TTSDIR  default /data/local/tmp/tts (binary, *.so, penny-kokoro-int8/)
#         COOL    default 1: before launching, poll every 5 s until policy0,
#                 policy4 and policy6 each read scaling_max_freq equal to their
#                 own cpuinfo_max_freq (the cool-between-rows rule of 16 Sept),
#                 in this same shell so nothing intervenes. COOL=0 launches
#                 immediately -- used for lines 2-18 of a pass, where the
#                 descent across the pass is the thing being measured.
#         GATECAP default 240: failed polls before the gate gives up and
#                 launches anyway, labelled LAUNCHED WARM. Only read if COOL=1.
#         TMAX    default UNSET: battery limit in dC. If set, the gate also
#                 needs battery temp <= TMAX. Only read if COOL=1.
#
# Figures are labelled by the row tag; this file names no phone. Results land
# in $TTSDIR/out/<tag>.* (.wav, .err, .kills, .report). This is a separate out/
# from pennybench.sh's /data/local/tmp/out so the two cannot overwrite each
# other's rows.

TAG="$1"; MASK="$2"; THREADS="$3"; LINE="$4"
if [ -z "$TAG" ] || [ -z "$MASK" ] || [ -z "$THREADS" ] || [ -z "$LINE" ]; then
    echo "usage: pennytts.sh <tag> <taskset-mask|none> <threads> <line 0-17|text>" >&2
    exit 2
fi

TTSDIR="${TTSDIR:-/data/local/tmp/tts}"
COOL="${COOL:-1}"
GATECAP="${GATECAP:-240}"                                       # rev 2
BIN="$TTSDIR/sherpa-onnx-offline-tts"
M="$TTSDIR/penny-kokoro-int8"
OUTDIR="$TTSDIR/out"
mkdir -p "$OUTDIR"
OUT="$OUTDIR/$TAG"
rm -f "$OUT.wav" "$OUT.err" "$OUT.pid" "$OUT.wall" "$OUT.kills" "$OUT.report"
export LD_LIBRARY_PATH="$TTSDIR"

case "$LINE" in
 0) TEXT="On it." ;;
 1) TEXT="Done." ;;
 2) TEXT="Yes?" ;;
 3) TEXT="One second." ;;
 4) TEXT="Your call is at 3:45 pm on Thursday the 24th of September." ;;
 5) TEXT="Revenue this month is £12,480.50, up 17.5% on August." ;;
 6) TEXT="That's 3 of 14 done, and the other 11 are due by Friday." ;;
 7) TEXT="The API returned a 502, so I retried the webhook and opened a pull request on GitHub." ;;
 8) TEXT="The OAuth token expired, so I've refreshed it and restarted the MCP server." ;;
 9) TEXT="CI is green on the staging branch, and the migration ran in under 4 seconds." ;;
10) TEXT="Six separate sessions since Saturday suggest something's slipping." ;;
11) TEXT="Penny picked the practical path and parked the rest for Thursday." ;;
12) TEXT="I read the record this morning, and I'll record the read-through later." ;;
13) TEXT="Do you want me to send it now, or wait until you've read it?" ;;
14) TEXT="Three things. First, the contract needs signing. Second, the invoice is overdue. Third, your 2 o'clock has moved to 4." ;;
15) TEXT="I went through everything that came in overnight, and most of it can wait, but there's one email from the investor you spoke to last week that reads like a soft yes, so I'd reply to that before anything else." ;;
16) TEXT="That's everything. Nothing else needs you tonight." ;;
17) TEXT="Morning Matt. Two things need you today, and one of them can wait until after lunch." ;;
 *) TEXT="$LINE" ;;
esac

mem()  { grep "^$1:" /proc/meminfo | tr -s ' ' | cut -d' ' -f2; }
vmst() { grep "^$1 " /proc/vmstat | tr -s ' ' | cut -d' ' -f2; }
upt()  { cut -d' ' -f1 /proc/uptime; }

# ---------------- CPU CLOCK CEILINGS (as pennybench.sh) ----------------
CEIL6=/sys/devices/system/cpu/cpufreq/policy6/scaling_max_freq
CEIL4=/sys/devices/system/cpu/cpufreq/policy4/scaling_max_freq
CEIL0=/sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq   # rev 2
# Rev 2: rated clock and cpu list per policy, READ, not written in
# (pennybench.sh rev 6, lines 96-100). UNREAD if a read fails.
POL=/sys/devices/system/cpu/cpufreq
rd() { RDV=$(cat "$1" 2>/dev/null); [ -n "$RDV" ] && echo "$RDV" || echo UNREAD; }
R6=$(rd $POL/policy6/cpuinfo_max_freq); P6=$(rd $POL/policy6/related_cpus)
R4=$(rd $POL/policy4/cpuinfo_max_freq); P4=$(rd $POL/policy4/related_cpus)
R0=$(rd $POL/policy0/cpuinfo_max_freq); P0=$(rd $POL/policy0/related_cpus)
# Rev 2: BATTERY temperature, tenths of a degree C. NOT SoC temperature --
# /sys/class/thermal/ is Permission denied to the shell user. (pennybench.sh
# rev 7, lines 110 and 112.)
BATT_T=/sys/class/power_supply/battery/temp
batt()  { cat "$1" 2>/dev/null || echo -1; }

# ---------------- COOL GATE ----------------
# Rev 2. The loop is the brief U gate (notes.md 17230) with its variable names
# kept: each policy's scaling_max_freq against its OWN cpuinfo_max_freq, both
# re-read on every poll; every rated figure must be non-empty; with TMAX set,
# the battery must read non-empty and <= TMAX. The battery is read with a bare
# cat, NOT batt(), so an unreadable battery is empty and fails the limb rather
# than reading -1 and passing it. n counts FAILED polls; the first poll that
# passes breaks before n is incremented, so polls_failed=0 means it passed on
# the first read.
COOL_WAIT_UP0=$(upt)
GATE_FIRST="not run"; GATE_RESULT="GATE NOT RUN (COOL=$COOL)"; n=0
if [ "$COOL" = "1" ]; then
    n=0; warm=0
    while :; do
        s0=$(cat $POL/policy0/scaling_max_freq 2>/dev/null); r0=$(cat $POL/policy0/cpuinfo_max_freq 2>/dev/null)
        s4=$(cat $POL/policy4/scaling_max_freq 2>/dev/null); r4=$(cat $POL/policy4/cpuinfo_max_freq 2>/dev/null)
        s6=$(cat $POL/policy6/scaling_max_freq 2>/dev/null); r6=$(cat $POL/policy6/cpuinfo_max_freq 2>/dev/null)
        bt=$(cat $BATT_T 2>/dev/null)
        [ $n = 0 ] && GATE_FIRST="p0=$s0/$r0 p4=$s4/$r4 p6=$s6/$r6 batt_dC=$bt uptime_s=$COOL_WAIT_UP0"
        if [ -n "$r0" ] && [ -n "$r4" ] && [ -n "$r6" ] && [ "$s0" = "$r0" ] && [ "$s4" = "$r4" ] && [ "$s6" = "$r6" ]; then
            if [ -z "$TMAX" ]; then break; fi
            [ -n "$bt" ] && [ "$bt" -le "$TMAX" ] && break
        fi
        n=$((n+1)); [ $n -ge $GATECAP ] && warm=1 && break
        sleep 5
    done
    if [ $warm = 1 ]; then GATE_RESULT="GATE TIMED OUT - LAUNCHED WARM"; else GATE_RESULT="GATE PASSED"; fi
    GATE_RESULT="$GATE_RESULT p0=$s0/$r0 p4=$s4/$r4 p6=$s6/$r6 batt_dC=$bt tmax_dC=${TMAX:-unset} polls_failed=$n cap=$GATECAP"
fi
COOL_WAIT_UP1=$(upt)
# Wait in seconds to 0.01 s. /proc/uptime always prints two decimals, so
# dropping the dot gives centiseconds; mksh arithmetic is integer-only.
GU0=$(echo "$COOL_WAIT_UP0" | tr -d .); GU1=$(echo "$COOL_WAIT_UP1" | tr -d .)
GWC=$((GU1 - GU0)); GATE_WAIT_S=$(printf '%d.%02d' $((GWC / 100)) $((GWC % 100)))

# ---------------- BEFORE ----------------
UP_B=$(upt)
MA_B=$(mem MemAvailable); MF_B=$(mem MemFree)
SF_B=$(mem SwapFree);     CA_B=$(mem Cached)
PSI_B=$(vmst pswpin); PSO_B=$(vmst pswpout); PGM_B=$(vmst pgmajfault)
C6_B=$(cat $CEIL6 2>/dev/null); C4_B=$(cat $CEIL4 2>/dev/null)
C0_B=$(cat $CEIL0 2>/dev/null)                                 # rev 2
BT_B=$(batt $BATT_T)                                           # rev 2
LOGSTART=$(date +'%m-%d %H:%M:%S.000')

# ---------------- RUN ----------------
# The wall clock is taken INSIDE a subshell, immediately either side of the
# binary, and not by the poll loop below: the poll loop sleeps 0.2 s between
# samples, so timing from it would add up to ~0.3 s to every wall figure --
# the same size as the load time being derived. `date +'%s %N'` costs one fork
# of toybox date either side (a few ms, included in wall). Seconds and
# nanoseconds are read separately because mksh arithmetic is 32-bit and an
# epoch in nanoseconds overflows it.
(
    T0=$(date +'%s %N')
    if [ "$MASK" = "none" ]; then
        "$BIN" --num-threads="$THREADS" \
            --kokoro-model="$M/model.int8.onnx" --kokoro-voices="$M/voices.bin" \
            --kokoro-tokens="$M/tokens.txt" --kokoro-data-dir="$M/espeak-ng-data" \
            --kokoro-lexicon="$M/lexicon-gb-en.txt" --kokoro-lang=en \
            --sid=22 --speed=1.0 --output-filename="$OUT.wav" "$TEXT" \
            > /dev/null 2> "$OUT.err" &
    else
        taskset "$MASK" "$BIN" --num-threads="$THREADS" \
            --kokoro-model="$M/model.int8.onnx" --kokoro-voices="$M/voices.bin" \
            --kokoro-tokens="$M/tokens.txt" --kokoro-data-dir="$M/espeak-ng-data" \
            --kokoro-lexicon="$M/lexicon-gb-en.txt" --kokoro-lang=en \
            --sid=22 --speed=1.0 --output-filename="$OUT.wav" "$TEXT" \
            > /dev/null 2> "$OUT.err" &
    fi
    P=$!
    echo "$P" > "$OUT.pid"
    wait "$P"; RC=$?
    T1=$(date +'%s %N')
    echo "$T0 $T1 $RC" > "$OUT.wall"
) &
SUB=$!

# taskset execs the binary, so $P is the binary's own pid.
while [ ! -s "$OUT.pid" ] && [ -d "/proc/$SUB" ]; do sleep 0.01; done
PID=$(cat "$OUT.pid" 2>/dev/null)

# VmHWM only grows, so the last read is the peak. VmRSS/RssAnon/RssFile do not,
# so each max is tracked. Clock ceilings move the other way; each MIN is kept
# with the uptime it was first seen at. See pennybench.sh for the reasoning.
HWM=0; RSS_MAX=0; ANON_MAX=0; FILE_MAX=0; SAMPLES=0
C6_MIN=$C6_B; C4_MIN=$C4_B; C0_MIN=$C0_B                       # rev 2: C0_MIN
C6_MIN_UP=$UP_B; C4_MIN_UP=$UP_B; C0_MIN_UP=$UP_B              # rev 2: C0_MIN_UP
while [ -n "$PID" ] && [ -d "/proc/$PID" ]; do
    S=$(grep -E '^(VmHWM|VmRSS|RssAnon|RssFile):' "/proc/$PID/status" 2>/dev/null)
    if [ -n "$S" ]; then
        SAMPLES=$((SAMPLES + 1))
        V=$(echo "$S" | grep VmHWM   | tr -s ' ' | cut -d' ' -f2)
        R=$(echo "$S" | grep VmRSS   | tr -s ' ' | cut -d' ' -f2)
        A=$(echo "$S" | grep RssAnon | tr -s ' ' | cut -d' ' -f2)
        F=$(echo "$S" | grep RssFile | tr -s ' ' | cut -d' ' -f2)
        [ -n "$V" ] && HWM=$V
        [ -n "$R" ] && [ "$R" -gt "$RSS_MAX"  ] && RSS_MAX=$R
        [ -n "$A" ] && [ "$A" -gt "$ANON_MAX" ] && ANON_MAX=$A
        [ -n "$F" ] && [ "$F" -gt "$FILE_MAX" ] && FILE_MAX=$F
    fi
    K6=$(cat $CEIL6 2>/dev/null); K4=$(cat $CEIL4 2>/dev/null)
    K0=$(cat $CEIL0 2>/dev/null)                                # rev 2
    [ -z "$C6_MIN" ] && C6_MIN=$K6                              # rev 2
    [ -z "$C4_MIN" ] && C4_MIN=$K4                              # rev 2
    [ -z "$C0_MIN" ] && C0_MIN=$K0                              # rev 2
    if [ -n "$K6" ] && [ -n "$C6_MIN" ] && [ "$K6" -lt "$C6_MIN" ]; then
        C6_MIN=$K6; C6_MIN_UP=$(upt)
    fi
    if [ -n "$K4" ] && [ -n "$C4_MIN" ] && [ "$K4" -lt "$C4_MIN" ]; then
        C4_MIN=$K4; C4_MIN_UP=$(upt)
    fi
    if [ -n "$K0" ] && [ -n "$C0_MIN" ] && [ "$K0" -lt "$C0_MIN" ]; then   # rev 2
        C0_MIN=$K0; C0_MIN_UP=$(upt)
    fi
    sleep 0.2
done
wait "$SUB"

# ---------------- AFTER ----------------
UP_A=$(upt)
MA_A=$(mem MemAvailable); MF_A=$(mem MemFree)
SF_A=$(mem SwapFree);     CA_A=$(mem Cached)
PSI_A=$(vmst pswpin); PSO_A=$(vmst pswpout); PGM_A=$(vmst pgmajfault)
C6_A=$(cat $CEIL6 2>/dev/null); C4_A=$(cat $CEIL4 2>/dev/null)
C0_A=$(cat $CEIL0 2>/dev/null)                                 # rev 2
BT_A=$(batt $BATT_T)                                           # rev 2

# ---------------- WALL, ELAPSED, DERIVED LOAD (all in ms) ----------------
# .wall is "s0 ns0 s1 ns1 rc". If toybox date ever returns a non-numeric %N
# the ms figure is marked invalid rather than computed from garbage.
set -- $(cat "$OUT.wall" 2>/dev/null)
S0=$1; N0=$2; S1=$3; N1=$4; RC=$5
case "$N0$N1$S0$S1" in
    ''|*[!0-9]*) WALL_MS=invalid ;;
    *) N0=${N0#"${N0%%[!0]*}"}; N1=${N1#"${N1%%[!0]*}"}
       WALL_MS=$(( (S1 - S0) * 1000 + (${N1:-0} - ${N0:-0}) / 1000000 )) ;;
esac
# "Elapsed seconds: 0.523 s" -- always %.3f, so dropping the dot gives ms.
EL=$(grep '^Elapsed seconds:' "$OUT.err" | tr -s ' ' | cut -d' ' -f3)
RTF_LINE=$(grep '^Real-time factor' "$OUT.err")
DUR_LINE=$(grep '^Audio duration' "$OUT.err")
THR_LINE=$(grep '^Number of threads' "$OUT.err")
EL_MS=""
case "$EL" in
    *.???) EL_MS=${EL%.*}${EL#*.}; EL_MS=${EL_MS#"${EL_MS%%[!0]*}"}; EL_MS=${EL_MS:-0} ;;
esac
if [ "$WALL_MS" != "invalid" ] && [ -n "$EL_MS" ]; then
    LOAD_MS=$((WALL_MS - EL_MS))
else
    LOAD_MS=invalid
fi

# ---------------- AUDIO LENGTH FROM THE WAV HEADER ----------------
# sherpa-onnx's WriteWave (wave-writer.cc) writes a canonical 44-byte header,
# PCM int16: channels at byte 22, sample rate at 24, "data" at 36, data size
# at 40. The "data" tag is checked before the size is trusted.
WAV_SR=""; WAV_CH=""; WAV_BYTES=""; WAV_SAMPLES=""; WAV_MS=""
if [ -s "$OUT.wav" ]; then
    TAGDATA=$(dd if="$OUT.wav" bs=1 skip=36 count=4 2>/dev/null)
    WAV_CH=$(od -An -tu2 -j22 -N2 "$OUT.wav" | tr -d ' ')
    WAV_SR=$(od -An -tu4 -j24 -N4 "$OUT.wav" | tr -d ' ')
    if [ "$TAGDATA" = "data" ]; then
        WAV_BYTES=$(od -An -tu4 -j40 -N4 "$OUT.wav" | tr -d ' ')
        WAV_SAMPLES=$((WAV_BYTES / 2 / WAV_CH))
        WAV_MS=$((WAV_SAMPLES * 1000 / WAV_SR))
    else
        WAV_SAMPLES="header-not-canonical(tag=$TAGDATA)"
    fi
fi

# ---------------- LMK kills during the run ----------------
# Tag-anchored. 'am_cpu' lines mention lmkd but are NOT kills -- excluded.
logcat -d -b all -t "$LOGSTART" 2>/dev/null \
  | grep -vE ' am_cpu +: ' \
  | grep -iE 'am_kill|lowmemorykiller| lmkd +: |ActivityManager.*has died' \
  > "$OUT.kills"
KILLS=$(wc -l < "$OUT.kills")

# ---------------- REPORT ----------------
# No `dumpsys meminfo` here, unlike pennybench.sh: it costs seconds of CPU, and
# an 18-line pass calls this script 18 times back to back -- it would heat the
# package between lines. Swap on this handset is already established as zram
# (16 Sept); take a dumpsys reading once per pass, by hand, if it is needed.
{
echo "PENNYTTS tag=$TAG rc=$RC mask=$MASK threads=$THREADS cool_gate=$COOL"
echo "PENNYTTS text=$TEXT"
echo "PENNYTTS bin=$BIN"
echo "PENNYTTS cool_wait_s     uptime $COOL_WAIT_UP0 -> $COOL_WAIT_UP1"
echo "PENNYTTS cool_gate_first $GATE_FIRST   (rev 2: the gate's first poll, scaling/cpuinfo per policy)"
echo "PENNYTTS cool_gate_result $GATE_RESULT wait_s=$GATE_WAIT_S   (rev 2)"
echo "PENNYTTS uptime_s        before=$UP_B after=$UP_A"
echo "PENNYTTS wall_ms         $WALL_MS   (exec to exit, taken in the launching subshell)"
echo "PENNYTTS elapsed_ms      ${EL_MS:-missing}   (the binary's own Elapsed: generate only)"
echo "PENNYTTS derived_load_ms $LOAD_MS   (wall - elapsed: process start + load + WAV write + 2 date forks)"
echo "PENNYTTS $THR_LINE"
echo "PENNYTTS $DUR_LINE"
echo "PENNYTTS $RTF_LINE"
echo "PENNYTTS wav             sr=$WAV_SR ch=$WAV_CH bytes=$WAV_BYTES samples=$WAV_SAMPLES ms=$WAV_MS"
echo "PENNYTTS memavail_kB     before=$MA_B after=$MA_A"
echo "PENNYTTS memfree_kB      before=$MF_B after=$MF_A"
echo "PENNYTTS swapfree_kB     before=$SF_B after=$SF_A"
echo "PENNYTTS cached_kB       before=$CA_B after=$CA_A"
echo "PENNYTTS pswpin          before=$PSI_B after=$PSI_A"
echo "PENNYTTS pswpout         before=$PSO_B after=$PSO_A"
echo "PENNYTTS pgmajfault      before=$PGM_B after=$PGM_A"
echo "PENNYTTS ceil_x1_kHz     before=$C6_B min=$C6_MIN after=$C6_A   (policy6, cpus $P6, rated $R6 read from cpuinfo_max_freq)"
echo "PENNYTTS ceil_x1_min_at  uptime=$C6_MIN_UP"
echo "PENNYTTS ceil_a78_kHz    before=$C4_B min=$C4_MIN after=$C4_A   (policy4, cpus $P4, rated $R4 read from cpuinfo_max_freq; key was ceil_a76_kHz in rev 1)"
echo "PENNYTTS ceil_a78_min_at uptime=$C4_MIN_UP   (key was ceil_a76_min_at in rev 1)"
echo "PENNYTTS ceil_a55_kHz    before=$C0_B min=$C0_MIN after=$C0_A   (policy0, cpus $P0, rated $R0 read from cpuinfo_max_freq; rev 2)"
echo "PENNYTTS ceil_a55_min_at uptime=$C0_MIN_UP   (rev 2)"
echo "PENNYTTS rated_kHz       policy0=$R0 policy4=$R4 policy6=$R6   (rev 2: read from cpuinfo_max_freq before the gate)"
echo "PENNYTTS batt_temp_dC    before=$BT_B after=$BT_A   (rev 2: BATTERY, tenths of a degree C -- NOT SoC)"
echo "PENNYTTS peak_rss_kB     $HWM   (VmHWM, monotonic)"
echo "PENNYTTS max_vmrss_kB    $RSS_MAX"
echo "PENNYTTS max_rssanon_kB  $ANON_MAX   (anonymous -- NOT reclaimable)"
echo "PENNYTTS max_rssfile_kB  $FILE_MAX   (file-backed -- reclaimable)"
echo "PENNYTTS rss_samples     $SAMPLES   (sleep 0.2 s between samples; a short run may get very few; rev 2 counts not comparable with rev 1)"
echo "PENNYTTS lmk_kill_lines  $KILLS"
echo "--- binary stderr (tail) ---"; tail -8 "$OUT.err"
echo "--- kill lines ---"; cat "$OUT.kills"
} | tee "$OUT.report"
