#!/system/bin/sh
# pennybench.sh -- run llama-bench and capture the columns llama-bench does not
# print: peak RSS, MemAvailable before/after, uptime before/after, LMK kills
# during the run, the CPU clock CEILINGS before, during and after, and the
# swap/major-fault counters either side.
#
# REVISION 5, 18 Sept, for brief S (the sustained run). TWO additions, and
# nothing else in the file changed:
#   1. A TIME SERIES to $OUT.series, one line every 10 s while the child lives.
#      Before/after readings bound an hour-long row but say nothing about what
#      happened inside it -- which is the whole question S-B and S-C ask.
#   2. oom_score_adj of the child, written and then READ BACK. See below.
#
# REVISION 6, 19 Sept, for brief T (the Pixel 7a). LABELS ONLY. The rated
# figure and cpu list printed beside each ceiling were written-in 6a numbers;
# they are now read off the phone (cpuinfo_max_freq, related_cpus) once, before
# the row, and printed UNREAD if a read fails. One report line added,
# rated_kHz. No measurement, poll interval, column, file or key name changed.
#
# usage:  pennybench.sh <tag> <taskset-mask|none> -- <llama-bench args...>
# e.g.    pennybench.sh smoke c0 -- -m /data/local/tmp/model.gguf -t 2 -p 16 -n 16

TAG="$1"; shift
MASK="$1"; shift
[ "$1" = "--" ] && shift

OUTDIR=/data/local/tmp/out
mkdir -p "$OUTDIR"
OUT="$OUTDIR/$TAG"

# Which binary the row runs. Defaults to llama-bench, so every row measured on
# or before 16 Sept reproduces byte-for-byte with no argument set. PENNYBIN=<path>
# points it at pennyload for the cold-load and TTFT rows, which need -t, -c and
# -lm -- flags llama-bench and llama-simple do not both accept.
BIN="${PENNYBIN:-/data/local/tmp/llama-bench}"

mem()  { grep "^$1:" /proc/meminfo | tr -s ' ' | cut -d' ' -f2; }
vmst() { grep "^$1 " /proc/vmstat | tr -s ' ' | cut -d' ' -f2; }
upt()  { cut -d' ' -f1 /proc/uptime; }

# ---------------- CPU CLOCK CEILINGS ----------------
# scaling_max_freq is the CEILING the governor may not exceed, not the operating
# point the governor chose (scaling_cur_freq is that). Both sampled clusters
# lower their own ceiling under sustained load.
#
# CORRECTED 16 Sept, after the cooled re-run: the earlier comment here said
# policy4 "was never observed to move" and that "only the X1 pair is capped".
# BOTH ARE REFUTED BY MEASUREMENT. policy4 (A76 pair, rated 2,253,000) fell on
# six of the seven cooled rows, floor 910,000 kHz = 40.4% of rated on C3 and C5,
# and it fell on C1 while `taskset c0` scheduled nothing onto it at all -- so
# the limiter acts across the package, not per cluster. The single after-row
# reading that produced the old claim was taken during recovery, not during
# load. policy6 (X1 pair, rated 2,802,000) fell below half rated on every
# cooled row without exception, floor 851,000 kHz = 30.4%.
#
# The minima below are the whole point: before/after readings bound the run
# but do not show the DESCENT. The min over the poll loop does, and the uptime
# at which each minimum was first seen says WHERE in the row it happened.
CEIL6=/sys/devices/system/cpu/cpufreq/policy6/scaling_max_freq
CEIL4=/sys/devices/system/cpu/cpufreq/policy4/scaling_max_freq
# policy0 is the A55 cluster, rated 1,803,000. It has NEVER been sampled during
# a row in this repo -- CLAUDE.md names that gap twice. It is carried in the
# series only; its before/min/after are computable from that file.
CEIL0=/sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq
# Rev 6: rated clock and cpu list per policy, READ, not written in. On the 6a
# policy6/4 were 2802000/2253000; on the 7a they are 2850000/2348000.
POL=/sys/devices/system/cpu/cpufreq
rd() { RDV=$(cat "$1" 2>/dev/null); [ -n "$RDV" ] && echo "$RDV" || echo UNREAD; }
R6=$(rd $POL/policy6/cpuinfo_max_freq); P6=$(rd $POL/policy6/related_cpus)
R4=$(rd $POL/policy4/cpuinfo_max_freq); P4=$(rd $POL/policy4/related_cpus)
R0=$(rd $POL/policy0/cpuinfo_max_freq); P0=$(rd $POL/policy0/related_cpus)

# BATTERY temperature, tenths of a degree C. NOT SoC temperature --
# /sys/class/thermal/ is Permission denied to the shell user on this build, so
# no SoC temperature can be read at all. Read from sysfs rather than
# `dumpsys battery` because the series samples ~360 times in an hour and
# dumpsys is a binder call into system_server, the process most likely to be
# perturbed by exactly the memory pressure this row measures. The two were
# compared in ONE invocation on 18 Sept and agreed: sysfs 280, dumpsys 279.
# dumpsys is still read once before and once after, as the cross-check.
BATT_T=/sys/class/power_supply/battery/temp
BATT_L=/sys/class/power_supply/battery/capacity
batt()  { cat "$1" 2>/dev/null || echo -1; }

# ---------------- BEFORE ----------------
UP_B=$(upt)
MA_B=$(mem MemAvailable); MF_B=$(mem MemFree)
SF_B=$(mem SwapFree);     CA_B=$(mem Cached)
PSI_B=$(vmst pswpin); PSO_B=$(vmst pswpout); PGM_B=$(vmst pgmajfault)
C6_B=$(cat $CEIL6 2>/dev/null); C4_B=$(cat $CEIL4 2>/dev/null)
BT_B=$(batt $BATT_T); BL_B=$(batt $BATT_L)
BDUMP_B=$(dumpsys battery 2>/dev/null | grep -iE '^  (temperature|level|status|AC powered)' | tr -s ' \n' ' ')
LOGSTART=$(date +'%m-%d %H:%M:%S.000')

# ---------------- RUN (background, so VmHWM can be polled) ----------------
if [ "$MASK" = "none" ]; then
    "$BIN" "$@" > "$OUT.bench" 2> "$OUT.err" &
else
    taskset "$MASK" "$BIN" "$@" > "$OUT.bench" 2> "$OUT.err" &
fi
PID=$!

# ---------------- oom_score_adj OF THE CHILD ----------------
# A process launched from `adb shell` inherits oom_score_adj -1000 from adbd and
# CANNOT be LMK-killed at any pressure -- so "our process survived" would be a
# non-result, and every kill list in this repo before 18 Sept was taken that
# way. Raising is permitted to one's own uid; lowering needs CAP_SYS_RESOURCE,
# which is why -1000 -> 200 works and the reverse would not. SELinux does not
# refuse it on this build (tested on boot 3: write_rc=0, read-back 200).
#
# 200 is the PERCEPTIBLE / FOREGROUND-SERVICE band -- the closest imitation of a
# service started from a boot broadcast, which is the shape rungs 3, 3b and 3d
# proved. It is NOT cached (900+) and NOT the foreground app (0); 3g-ii's killer
# stopped at adj 201 under a 2GB VM, so 200 sits one point inside the band it
# did not enter.
#
# BOTH values are read off /proc, so the line below is a reading and not an
# intention. PENNYOOM=<n> overrides for a deliberate control.
OOM_TARGET="${PENNYOOM:-200}"
OOM_PRE=$(cat /proc/$PID/oom_score_adj 2>/dev/null)
echo "$OOM_TARGET" > /proc/$PID/oom_score_adj 2>/dev/null
OOM_POST=$(cat /proc/$PID/oom_score_adj 2>/dev/null)

# VmHWM is itself a high-water mark and only ever grows, so the last value read
# before the process exits IS the peak. VmRSS, RssAnon and RssFile are NOT
# monotonic -- they fall when pages are reclaimed -- so each max is tracked
# here, independently. The clock ceilings are not monotonic either and move the
# other way, so each MINIMUM is tracked, with the uptime it was first seen at.
#
# The split is the point: weights loaded by mmap are file-backed and the kernel
# can drop them under pressure; everything llama.cpp allocates itself, the
# repacked weight copy included, is anonymous and cannot be dropped.
#
# NOTE: ANON_MAX and FILE_MAX may occur at DIFFERENT moments, so their sum can
# exceed VmHWM and is not itself a peak. Read them separately.
#
# NOTE ON RATE: the loop sleeps 0.2 s BETWEEN samples and does work either
# side, so the achieved rate is well below 5 Hz -- row 1 of the matrix took
# 453 samples over 162.19 s, i.e. 2.79 Hz. Earlier entries called this "5 Hz"
# and that was the sleep interval, not the rate. The achieved rate is printed.
#
# THE SERIES, added in rev 5. One line every 10 s of UPTIME -- not every Nth
# iteration, because the poll loop's achieved rate is ~2.79 Hz and varies with
# load, so an iteration count would drift. The deadline is carried in integer
# seconds; Android's sh has no floating-point arithmetic.
HWM=0; RSS_MAX=0; ANON_MAX=0; FILE_MAX=0; SAMPLES=0
C6_MIN=$C6_B; C4_MIN=$C4_B
C6_MIN_UP=$UP_B; C4_MIN_UP=$UP_B
SER="$OUT.series"
echo "uptime_s ceil_x1 ceil_a76 ceil_a55 MemAvailable_kB MemFree_kB SwapFree_kB Cached_kB VmRSS_kB VmHWM_kB pswpout pgmajfault batt_temp_dC batt_level" > "$SER"
NEXT_SER=${UP_B%.*}
while [ -d "/proc/$PID" ]; do
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
    [ -z "$C6_MIN" ] && C6_MIN=$K6
    [ -z "$C4_MIN" ] && C4_MIN=$K4
    if [ -n "$K6" ] && [ -n "$C6_MIN" ] && [ "$K6" -lt "$C6_MIN" ]; then
        C6_MIN=$K6; C6_MIN_UP=$(upt)
    fi
    if [ -n "$K4" ] && [ -n "$C4_MIN" ] && [ "$K4" -lt "$C4_MIN" ]; then
        C4_MIN=$K4; C4_MIN_UP=$(upt)
    fi
    U=$(upt); UI=${U%.*}
    if [ -n "$UI" ] && [ "$UI" -ge "$NEXT_SER" ]; then
        echo "$U ${K6:--1} ${K4:--1} $(cat $CEIL0 2>/dev/null || echo -1) $(mem MemAvailable) $(mem MemFree) $(mem SwapFree) $(mem Cached) ${R:--1} ${V:--1} $(vmst pswpout) $(vmst pgmajfault) $(batt $BATT_T) $(batt $BATT_L)" >> "$SER"
        NEXT_SER=$((UI + 10))
    fi
    sleep 0.2
done
wait $PID; RC=$?

# ---------------- AFTER ----------------
UP_A=$(upt)
MA_A=$(mem MemAvailable); MF_A=$(mem MemFree)
SF_A=$(mem SwapFree);     CA_A=$(mem Cached)
PSI_A=$(vmst pswpin); PSO_A=$(vmst pswpout); PGM_A=$(vmst pgmajfault)
C6_A=$(cat $CEIL6 2>/dev/null); C4_A=$(cat $CEIL4 2>/dev/null)
BT_A=$(batt $BATT_T); BL_A=$(batt $BATT_L)
BDUMP_A=$(dumpsys battery 2>/dev/null | grep -iE '^  (temperature|level|status|AC powered)' | tr -s ' \n' ' ')
SER_LINES=$(( $(wc -l < "$SER") - 1 ))

# ---------------- LMK kills during the run ----------------
# Tag-anchored. 'am_cpu' lines mention lmkd but are NOT kills -- excluded.
logcat -d -b all -t "$LOGSTART" 2>/dev/null \
  | grep -vE ' am_cpu +: ' \
  | grep -iE 'am_kill|lowmemorykiller| lmkd +: |ActivityManager.*has died' \
  > "$OUT.kills"
KILLS=$(wc -l < "$OUT.kills")

# ---------------- REPORT ----------------
# Revision 4, 16 Sept: the whole report is tee'd to $OUT.report as well as
# to stdout, so every row's peak RSS, kills, clock ceilings and MemAvailable
# exist as a file on the phone rather than only in the operator's terminal.
# Nothing above this line changed.
{
echo "PENNYBENCH tag=$TAG rc=$RC mask=$MASK"
echo "PENNYBENCH bin=$BIN"
echo "PENNYBENCH args=$*"
echo "PENNYBENCH uptime_s        before=$UP_B after=$UP_A"
echo "PENNYBENCH memavail_kB     before=$MA_B after=$MA_A"
echo "PENNYBENCH memfree_kB      before=$MF_B after=$MF_A"
echo "PENNYBENCH swapfree_kB     before=$SF_B after=$SF_A"
echo "PENNYBENCH cached_kB       before=$CA_B after=$CA_A"
echo "PENNYBENCH pswpin          before=$PSI_B after=$PSI_A   (pages read back IN from swap)"
echo "PENNYBENCH pswpout         before=$PSO_B after=$PSO_A   (pages written OUT to swap)"
echo "PENNYBENCH pgmajfault      before=$PGM_B after=$PGM_A   (major faults: had to hit storage or swap)"
echo "PENNYBENCH ceil_x1_kHz     before=$C6_B min=$C6_MIN after=$C6_A   (policy6, cpus $P6, rated $R6 read from cpuinfo_max_freq)"
echo "PENNYBENCH ceil_x1_min_at  uptime=$C6_MIN_UP   ($((${C6_MIN_UP%.*} - ${UP_B%.*})) s into the row)"
echo "PENNYBENCH ceil_a76_kHz    before=$C4_B min=$C4_MIN after=$C4_A   (policy4, cpus $P4, rated $R4 read from cpuinfo_max_freq)"
echo "PENNYBENCH ceil_a76_min_at uptime=$C4_MIN_UP   ($((${C4_MIN_UP%.*} - ${UP_B%.*})) s into the row)"
echo "PENNYBENCH rated_kHz policy0=$R0 policy4=$R4 policy6=$R6   (read from cpuinfo_max_freq before the row)"
echo "PENNYBENCH oom_score_adj_child pre=$OOM_PRE post=$OOM_POST   (target $OOM_TARGET; both READ off /proc)"
echo "PENNYBENCH batt_temp_dC    before=$BT_B after=$BT_A   (BATTERY, tenths of a degree C -- NOT SoC)"
echo "PENNYBENCH batt_level      before=$BL_B after=$BL_A"
echo "PENNYBENCH batt_dumpsys    before=[$BDUMP_B] after=[$BDUMP_A]   (cross-check on the sysfs figures)"
echo "PENNYBENCH series_file     $SER   ($SER_LINES samples, one per 10 s of uptime)"
echo "PENNYBENCH peak_rss_kB     $HWM   (VmHWM, monotonic)"
echo "PENNYBENCH max_vmrss_kB    $RSS_MAX"
echo "PENNYBENCH max_rssanon_kB  $ANON_MAX   (anonymous -- NOT reclaimable)"
echo "PENNYBENCH max_rssfile_kB  $FILE_MAX   (file-backed -- reclaimable)"
echo "PENNYBENCH rss_samples     $SAMPLES   (sleep 0.2 s between samples; achieved rate is lower)"
echo "PENNYBENCH lmk_kill_lines  $KILLS"
# /proc/swaps is Permission denied to the shell user on this build (checked
# 16 Sept), so the swap device cannot be listed directly. dumpsys answers the
# same question and is readable: its ZRAM line gives physical bytes used, bytes
# held in swap, and total swap -- i.e. swap on this handset IS zram, compressed
# in RAM, and the compression ratio is visible. /sys/block/zram0 exists but
# every attribute under it is also Permission denied.
echo "--- swap device (is swap zram?) ---"
cat /proc/swaps 2>&1 | head -3
ls -d /sys/block/zram0 2>&1
dumpsys meminfo 2>/dev/null | grep -iE "zram|swap"
echo "--- series head/tail (full file at $SER) ---"
head -3 "$SER"; echo "..."; tail -3 "$SER"
echo "--- llama-bench stdout ---"; cat "$OUT.bench"
echo "--- llama-bench stderr (tail) ---"; tail -5 "$OUT.err"
echo "--- kill lines ---"; cat "$OUT.kills"
} | tee "$OUT.report"
