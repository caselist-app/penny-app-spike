#!/system/bin/sh
# pennyspeak2.sh -- pennyspeak.sh (dc2706fd..., brief X) with ONE functional
# change: BIN runs pennyspeak2 (pennyspeak + a counting progress callback,
# brief Y, 22 Sept) instead of pennyspeak. The usage strings name this file.
# `diff -u pennyspeak.sh pennyspeak2.sh` shows everything. No new variable
# name. The kill grep for 'pennyspeak' still matches 'pennyspeak2'.
# pennyspeak2's extra event=chunk records reach the report through the
# verbatim copy of $OUT.raw, unparsed, like every other PENNYSPEAK line.
#
# pennyspeak.sh's header, unchanged:
# pennyspeak.sh -- run pennyspeak ONCE: one resident Kokoro process, the model
# loaded once, the requested lines spoken in sequence (default all 18) -- and
# capture what the binary does not print: wall time from exec to exit of the
# whole pass, MemAvailable and swap counters either side, LMK kills during the
# pass, the CPU clock ceilings before/min/after with the uptime of each minimum,
# and battery temperature either side. Brief X (stage 3a step 3), 21 Sept.
#
# MADE FROM pennytts.sh REV 3 (90cbeea1...) BY COPYING. These blocks are
# pennytts.sh's, unchanged: the helper functions (mem, vmst, upt, rd, batt), the
# CPU CLOCK CEILINGS block, the COOL GATE, the BEFORE and AFTER blocks, the
# launching subshell's wall clock, the 0.2 s poll with the uptime of each
# minimum, and the LMK kill-line block. `diff -u pennytts.sh pennyspeak.sh`
# shows everything else.
#
# REMOVED from pennytts.sh, and why nothing here depends on it:
#   - the 18-line case table: pennyspeak embeds the same 18 texts (brief X step
#     B, diff rc=0) and is given line NUMBERS, never text.
#   - the per-line "Elapsed seconds" / "Real-time factor" / "Audio duration"
#     greps of the CLI's stderr, derived_load_ms, and the WAV-header parsing:
#     they read the CLI's one-line output. pennyspeak prints its own load_ms,
#     gen_ms, samples, audio_ms and rtf for every line (PENNYSPEAK lines,
#     copied into the report verbatim, unparsed).
#   - rm -f of old outputs: replaced by the NO-OVERWRITE GUARD below, which
#     refuses to run if any output of this tag already exists.
#   - from the copied RUN comment, "the same size as the load time being
#     derived": derived_load_ms no longer exists, so the sentence was false.
#
# THE LINE-LIST VARIABLE IS LINELIST, NOT LINES (21 Sept, brief X step D). The
# phone's /system/bin/sh is mksh R59, where LINES is a built-in (terminal
# height): assigning "0" or "all" read back 24, so the first version sent every
# list to the guard as "24". macOS sh and bash --posix have no such built-in,
# so every Mac test passed. A Mac pass proves logic only.
#
# EXIT CODES of this script, before anything runs: 2 bad arguments (including
# any line list pennyspeak would reject); 9 REFUSED, an output of this tag
# already exists. Otherwise the report's rc= is pennyspeak's own exit code
# (0 ok, 2 bad arguments, 3 create failed, 4 a generate failed, 5 a WAV write
# failed), and this script exits with tee's status.
#
# RENAMED report keys -- same measurement, different span, so never a reused
# name with a changed meaning:
#   wall_ms          -> pass_wall_ms          exec to exit of the WHOLE pass (load + every line + destroy)
#   peak_rss_kB      -> pass_poll_vmhwm_kB    the 0.2 s poll's last VmHWM of the resident process
#   max_vmrss_kB     -> pass_poll_max_vmrss_kB
#   max_rssanon_kB   -> pass_poll_max_rssanon_kB
#   max_rssfile_kB   -> pass_poll_max_rssfile_kB
#   rss_samples      -> pass_poll_rss_samples
# Per-line RSS (VmRSS, VmHWM, RssAnon, RssFile, read by pennyspeak after each
# line) is in the PENNYSPEAK lines, not in these keys.
# NEW report keys: lines, idle_ms (on the first line), and
# lmk_kill_lines_naming_pennyspeak.
#
# usage:  pennyspeak.sh <tag> <taskset-mask|none> <threads> [lines] [idle_ms]
#         lines    "all" (default) or a comma list of 0-17, passed to pennyspeak
#         idle_ms  default 0, passed to pennyspeak
# e.g.    pennyspeak.sh 7a_tts_r1_fp32_x1x1 c0 2                   all 18, X1 pair
#         pennyspeak.sh 7a_tts_xsmoke_i c0 2 0,4                   lines 0 and 4
#
# env:    TTSDIR, COOL, GATECAP, TMAX, MODELDIR, MODELFILE -- exactly as
#         pennytts.sh rev 3, same defaults (COOL=1, GATECAP=240, TMAX unset,
#         MODELDIR=$TTSDIR/penny-kokoro-int8, MODELFILE=model.int8.onnx).
#
# Results land in $TTSDIR/out/: <tag>.report, <tag>.raw (pennyspeak's stdout),
# <tag>.err, <tag>.kills, <tag>.pid, <tag>.wall, and one <tag>_NN.wav per line
# (written by pennyspeak itself).

TAG="$1"; MASK="$2"; THREADS="$3"; LINELIST="${4:-all}"; IDLE_MS="${5:-0}"
if [ -z "$TAG" ] || [ -z "$MASK" ] || [ -z "$THREADS" ]; then
    echo "usage: pennyspeak2.sh <tag> <taskset-mask|none> <threads> [lines: all|0,4,...] [idle_ms]" >&2
    exit 2
fi

TTSDIR="${TTSDIR:-/data/local/tmp/tts}"
COOL="${COOL:-1}"
GATECAP="${GATECAP:-240}"                                       # rev 2
BIN="$TTSDIR/pennyspeak2"
M="${MODELDIR:-$TTSDIR/penny-kokoro-int8}"                      # rev 3
MF="${MODELFILE:-model.int8.onnx}"                              # rev 3
MB=$(stat -c %s "$M/$MF" 2>/dev/null); MB=${MB:-missing}      # rev 3: size from the inode, the file is NOT read
OUTDIR="$TTSDIR/out"
mkdir -p "$OUTDIR"
OUT="$OUTDIR/$TAG"
export LD_LIBRARY_PATH="$TTSDIR"

# ---------------- NO-OVERWRITE GUARD (NEW) ----------------
# pennyspeak overwrites its WAVs silently and pennytts.sh deleted old outputs
# with rm -f; neither may happen to a row. Before the gate and before anything
# runs: if ANY file this pass would write already exists, print REFUSED naming
# it and exit 9 (not 3, which is pennyspeak's own "create failed").
#
# First the line list is checked against EXACTLY what pennyspeak's
# parse_lines() rejects, so a typo fails here with exit 2 instead of after a
# gate wait of up to GATECAP x 5 s. The RAW string is walked item by item
# (never a word-split copy, which would drop empty items): "all", or 1-18
# comma-separated items, each digits only and 0-17 after leading zeros are
# dropped, no repeats, no empty item (so no leading, trailing or double
# comma), under 256 characters. NNS collects the two-digit names pennyspeak
# gives the WAVs (%02d of the number).
bad_lines() { echo "usage: pennyspeak2.sh: bad line list [$LINELIST]" >&2; exit 2; }
if [ "$LINELIST" = "all" ]; then
    NNS="00 01 02 03 04 05 06 07 08 09 10 11 12 13 14 15 16 17"
else
    [ ${#LINELIST} -ge 256 ] && bad_lines
    NNS=""; SEEN=" "; REST="$LINELIST,"
    while [ -n "$REST" ]; do
        L=${REST%%,*}; REST=${REST#*,}
        case "$L" in ''|*[!0-9]*) bad_lines ;; esac
        L=${L#"${L%%[!0]*}"}; L=${L:-0}                         # drop leading zeros, as strtol does
        case "$L" in ???*) bad_lines ;; esac
        [ "$L" -le 17 ] || bad_lines
        case "$SEEN" in *" $L "*) bad_lines ;; esac
        SEEN="$SEEN$L "
        case "$L" in ?) NNS="$NNS 0$L" ;; *) NNS="$NNS $L" ;; esac
    done
fi
for NN in $NNS; do
    if [ -e "${OUT}_$NN.wav" ]; then echo "REFUSED: ${OUT}_$NN.wav exists - nothing run" >&2; exit 9; fi
done
for X in report raw err kills pid wall; do
    if [ -e "$OUT.$X" ]; then echo "REFUSED: $OUT.$X exists - nothing run" >&2; exit 9; fi
done

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
# samples, so timing from it would add up to ~0.3 s to every wall figure.
# `date +'%s %N'` costs one fork
# of toybox date either side (a few ms, included in wall). Seconds and
# nanoseconds are read separately because mksh arithmetic is 32-bit and an
# epoch in nanoseconds overflows it.
(
    T0=$(date +'%s %N')
    # pennyspeak reads voices.bin, tokens.txt, espeak-ng-data and
    # lexicon-gb-en.txt from $M itself, and sets sid 22, lang en, speed 1.0
    # (pennyspeak.cpp). Its stdout -- the PENNYSPEAK lines -- goes to $OUT.raw.
    if [ "$MASK" = "none" ]; then
        "$BIN" "$M" "$MF" "$THREADS" "$OUTDIR" "$TAG" "$LINELIST" "$IDLE_MS" \
            > "$OUT.raw" 2> "$OUT.err" &
    else
        taskset "$MASK" "$BIN" "$M" "$MF" "$THREADS" "$OUTDIR" "$TAG" "$LINELIST" "$IDLE_MS" \
            > "$OUT.raw" 2> "$OUT.err" &
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

# ---------------- PASS WALL (ms) ----------------
# .wall is "s0 ns0 s1 ns1 rc". If toybox date ever returns a non-numeric %N
# the ms figure is marked invalid rather than computed from garbage.
# In pennyspeak.sh this spans the WHOLE pass: exec, load, every line, destroy.
set -- $(cat "$OUT.wall" 2>/dev/null)
S0=$1; N0=$2; S1=$3; N1=$4; RC=$5
case "$N0$N1$S0$S1" in
    ''|*[!0-9]*) WALL_MS=invalid ;;
    *) N0=${N0#"${N0%%[!0]*}"}; N1=${N1#"${N1%%[!0]*}"}
       WALL_MS=$(( (S1 - S0) * 1000 + (${N1:-0} - ${N0:-0}) / 1000000 )) ;;
esac

# ---------------- LMK kills during the run ----------------
# Tag-anchored. 'am_cpu' lines mention lmkd but are NOT kills -- excluded.
logcat -d -b all -t "$LOGSTART" 2>/dev/null \
  | grep -vE ' am_cpu +: ' \
  | grep -iE 'am_kill|lowmemorykiller| lmkd +: |ActivityManager.*has died' \
  > "$OUT.kills"
KILLS=$(wc -l < "$OUT.kills")
KILLS_PS=$(grep -c pennyspeak "$OUT.kills")                    # NEW: kill lines naming the binary

# ---------------- REPORT ----------------
# No `dumpsys meminfo` here, as pennytts.sh: it costs seconds of CPU and would
# heat the package just before the next pass's gate.
{
echo "PENNYSPEAKSH tag=$TAG rc=$RC mask=$MASK threads=$THREADS cool_gate=$COOL lines=$LINELIST idle_ms=$IDLE_MS"
echo "PENNYSPEAKSH bin=$BIN"
echo "PENNYSPEAKSH cool_wait_s     uptime $COOL_WAIT_UP0 -> $COOL_WAIT_UP1"
echo "PENNYSPEAKSH cool_gate_first $GATE_FIRST   (rev 2: the gate's first poll, scaling/cpuinfo per policy)"
echo "PENNYSPEAKSH cool_gate_result $GATE_RESULT wait_s=$GATE_WAIT_S   (rev 2)"
echo "PENNYSPEAKSH uptime_s        before=$UP_B after=$UP_A"
echo "PENNYSPEAKSH pass_wall_ms    $WALL_MS   (exec to exit of the WHOLE pass: load + every line + destroy; taken in the launching subshell)"
echo "PENNYSPEAKSH memavail_kB     before=$MA_B after=$MA_A"
echo "PENNYSPEAKSH memfree_kB      before=$MF_B after=$MF_A"
echo "PENNYSPEAKSH swapfree_kB     before=$SF_B after=$SF_A"
echo "PENNYSPEAKSH cached_kB       before=$CA_B after=$CA_A"
echo "PENNYSPEAKSH pswpin          before=$PSI_B after=$PSI_A"
echo "PENNYSPEAKSH pswpout         before=$PSO_B after=$PSO_A"
echo "PENNYSPEAKSH pgmajfault      before=$PGM_B after=$PGM_A"
echo "PENNYSPEAKSH ceil_x1_kHz     before=$C6_B min=$C6_MIN after=$C6_A   (policy6, cpus $P6, rated $R6 read from cpuinfo_max_freq)"
echo "PENNYSPEAKSH ceil_x1_min_at  uptime=$C6_MIN_UP"
echo "PENNYSPEAKSH ceil_a78_kHz    before=$C4_B min=$C4_MIN after=$C4_A   (policy4, cpus $P4, rated $R4 read from cpuinfo_max_freq; key was ceil_a76_kHz in rev 1)"
echo "PENNYSPEAKSH ceil_a78_min_at uptime=$C4_MIN_UP   (key was ceil_a76_min_at in rev 1)"
echo "PENNYSPEAKSH ceil_a55_kHz    before=$C0_B min=$C0_MIN after=$C0_A   (policy0, cpus $P0, rated $R0 read from cpuinfo_max_freq; rev 2)"
echo "PENNYSPEAKSH ceil_a55_min_at uptime=$C0_MIN_UP   (rev 2)"
echo "PENNYSPEAKSH rated_kHz       policy0=$R0 policy4=$R4 policy6=$R6   (rev 2: read from cpuinfo_max_freq before the gate)"
echo "PENNYSPEAKSH batt_temp_dC    before=$BT_B after=$BT_A   (rev 2: BATTERY, tenths of a degree C -- NOT SoC)"
echo "PENNYSPEAKSH pass_poll_vmhwm_kB       $HWM   (VmHWM at the 0.2 s poll's last read of the resident process; per-line figures are in the PENNYSPEAK lines)"
echo "PENNYSPEAKSH pass_poll_max_vmrss_kB   $RSS_MAX"
echo "PENNYSPEAKSH pass_poll_max_rssanon_kB $ANON_MAX   (anonymous -- NOT reclaimable)"
echo "PENNYSPEAKSH pass_poll_max_rssfile_kB $FILE_MAX   (file-backed -- reclaimable)"
echo "PENNYSPEAKSH pass_poll_rss_samples    $SAMPLES   (sleep 0.2 s between samples, whole pass)"
echo "PENNYSPEAKSH lmk_kill_lines  $KILLS"
echo "PENNYSPEAKSH lmk_kill_lines_naming_pennyspeak $KILLS_PS   (NEW: lines of the above containing 'pennyspeak')"
echo "PENNYSPEAKSH model_path      $M/$MF   (rev 3)"
echo "PENNYSPEAKSH model_bytes     $MB   (rev 3: stat -c %s before the run; the file is not read)"
echo "--- pennyspeak stdout, verbatim ($OUT.raw) ---"; cat "$OUT.raw"
echo "--- binary stderr (tail) ---"; tail -8 "$OUT.err"
echo "--- kill lines ---"; cat "$OUT.kills"
} | tee "$OUT.report"
