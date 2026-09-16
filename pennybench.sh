#!/system/bin/sh
# pennybench.sh -- run llama-bench and capture the columns llama-bench does not
# print: peak RSS, MemAvailable before/after, uptime before/after, LMK kills
# during the run, and the CPU clock CEILINGS before, during and after.
#
# usage:  pennybench.sh <tag> <taskset-mask|none> -- <llama-bench args...>
# e.g.    pennybench.sh smoke c0 -- -m /data/local/tmp/model.gguf -t 2 -p 16 -n 16

TAG="$1"; shift
MASK="$1"; shift
[ "$1" = "--" ] && shift

OUTDIR=/data/local/tmp/out
mkdir -p "$OUTDIR"
OUT="$OUTDIR/$TAG"

mem() { grep "^$1:" /proc/meminfo | tr -s ' ' | cut -d' ' -f2; }

# ---------------- CPU CLOCK CEILINGS ----------------
# The X1 pair (cpus 6,7 = policy6) lowers its own scaling_max_freq under
# sustained load. That is the CEILING the governor may not exceed, not the
# operating point the governor chose (scaling_cur_freq is that). Read
# 1,426,000 kHz against a rated cpuinfo_max_freq of 2,802,000 immediately
# after row 4 of the matrix on 16 Sept, climbing back to rated over ~110 s of
# idle. policy4 is the A76 pair, rated 2,253,000, and was never observed to
# move -- it is sampled here so "only the X1 pair is capped" is MEASURED on
# every row instead of carried forward from one reading.
#
# The minima below are the whole point: before/after readings bound the run
# but do not show the DESCENT. The min over the poll loop does.
CEIL6=/sys/devices/system/cpu/cpufreq/policy6/scaling_max_freq
CEIL4=/sys/devices/system/cpu/cpufreq/policy4/scaling_max_freq

# ---------------- BEFORE ----------------
UP_B=$(cut -d' ' -f1 /proc/uptime)
MA_B=$(mem MemAvailable); MF_B=$(mem MemFree)
SF_B=$(mem SwapFree);     CA_B=$(mem Cached)
C6_B=$(cat $CEIL6 2>/dev/null); C4_B=$(cat $CEIL4 2>/dev/null)
LOGSTART=$(date +'%m-%d %H:%M:%S.000')

# ---------------- RUN (background, so VmHWM can be polled) ----------------
if [ "$MASK" = "none" ]; then
    /data/local/tmp/llama-bench "$@" > "$OUT.bench" 2> "$OUT.err" &
else
    taskset "$MASK" /data/local/tmp/llama-bench "$@" > "$OUT.bench" 2> "$OUT.err" &
fi
PID=$!

# VmHWM is itself a high-water mark and only ever grows, so the last value read
# before the process exits IS the peak. VmRSS, RssAnon and RssFile are NOT
# monotonic -- they fall when pages are reclaimed -- so each max is tracked
# here, independently. The clock ceilings are not monotonic either and move the
# other way, so each MINIMUM is tracked.
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
HWM=0; RSS_MAX=0; ANON_MAX=0; FILE_MAX=0; SAMPLES=0
C6_MIN=$C6_B; C4_MIN=$C4_B
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
    [ -n "$K6" ] && [ -n "$C6_MIN" ] && [ "$K6" -lt "$C6_MIN" ] && C6_MIN=$K6
    [ -n "$K4" ] && [ -n "$C4_MIN" ] && [ "$K4" -lt "$C4_MIN" ] && C4_MIN=$K4
    sleep 0.2
done
wait $PID; RC=$?

# ---------------- AFTER ----------------
UP_A=$(cut -d' ' -f1 /proc/uptime)
MA_A=$(mem MemAvailable); MF_A=$(mem MemFree)
SF_A=$(mem SwapFree);     CA_A=$(mem Cached)
C6_A=$(cat $CEIL6 2>/dev/null); C4_A=$(cat $CEIL4 2>/dev/null)

# ---------------- LMK kills during the run ----------------
# Tag-anchored. 'am_cpu' lines mention lmkd but are NOT kills -- excluded.
logcat -d -b all -t "$LOGSTART" 2>/dev/null \
  | grep -vE ' am_cpu +: ' \
  | grep -iE 'am_kill|lowmemorykiller| lmkd +: |ActivityManager.*has died' \
  > "$OUT.kills"
KILLS=$(wc -l < "$OUT.kills")

# ---------------- REPORT ----------------
echo "PENNYBENCH tag=$TAG rc=$RC mask=$MASK"
echo "PENNYBENCH args=$*"
echo "PENNYBENCH uptime_s        before=$UP_B after=$UP_A"
echo "PENNYBENCH memavail_kB     before=$MA_B after=$MA_A"
echo "PENNYBENCH memfree_kB      before=$MF_B after=$MF_A"
echo "PENNYBENCH swapfree_kB     before=$SF_B after=$SF_A"
echo "PENNYBENCH cached_kB       before=$CA_B after=$CA_A"
echo "PENNYBENCH ceil_x1_kHz     before=$C6_B min=$C6_MIN after=$C6_A   (policy6, cpus 6-7, rated 2802000)"
echo "PENNYBENCH ceil_a76_kHz    before=$C4_B min=$C4_MIN after=$C4_A   (policy4, cpus 4-5, rated 2253000)"
echo "PENNYBENCH peak_rss_kB     $HWM   (VmHWM, monotonic)"
echo "PENNYBENCH max_vmrss_kB    $RSS_MAX"
echo "PENNYBENCH max_rssanon_kB  $ANON_MAX   (anonymous -- NOT reclaimable)"
echo "PENNYBENCH max_rssfile_kB  $FILE_MAX   (file-backed -- reclaimable)"
echo "PENNYBENCH rss_samples     $SAMPLES   (sleep 0.2 s between samples; achieved rate is lower)"
echo "PENNYBENCH lmk_kill_lines  $KILLS"
echo "--- llama-bench stdout ---"; cat "$OUT.bench"
echo "--- llama-bench stderr (tail) ---"; tail -5 "$OUT.err"
echo "--- kill lines ---"; cat "$OUT.kills"
