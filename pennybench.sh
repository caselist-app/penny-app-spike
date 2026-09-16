#!/system/bin/sh
# pennybench.sh -- run llama-bench and capture the four columns llama-bench does
# not print: peak RSS, MemAvailable before/after, uptime before/after, and LMK
# kills during the run.
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

# ---------------- BEFORE ----------------
UP_B=$(cut -d' ' -f1 /proc/uptime)
MA_B=$(mem MemAvailable); MF_B=$(mem MemFree)
SF_B=$(mem SwapFree);     CA_B=$(mem Cached)
LOGSTART=$(date +'%m-%d %H:%M:%S.000')

# ---------------- RUN (background, so VmHWM can be polled) ----------------
if [ "$MASK" = "none" ]; then
    /data/local/tmp/llama-bench "$@" > "$OUT.bench" 2> "$OUT.err" &
else
    taskset "$MASK" /data/local/tmp/llama-bench "$@" > "$OUT.bench" 2> "$OUT.err" &
fi
PID=$!

# VmHWM is itself a high-water mark and only ever grows, so the last value read
# before the process exits IS the peak. Polled at 5 Hz.
HWM=0
while [ -d "/proc/$PID" ]; do
    V=$(grep VmHWM "/proc/$PID/status" 2>/dev/null | tr -s ' ' | cut -d' ' -f2)
    [ -n "$V" ] && HWM=$V
    sleep 0.2
done
wait $PID; RC=$?

# ---------------- AFTER ----------------
UP_A=$(cut -d' ' -f1 /proc/uptime)
MA_A=$(mem MemAvailable); MF_A=$(mem MemFree)
SF_A=$(mem SwapFree);     CA_A=$(mem Cached)

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
echo "PENNYBENCH peak_rss_kB     $HWM"
echo "PENNYBENCH lmk_kill_lines  $KILLS"
echo "--- llama-bench stdout ---"; cat "$OUT.bench"
echo "--- llama-bench stderr (tail) ---"; tail -5 "$OUT.err"
echo "--- kill lines ---"; cat "$OUT.kills"
