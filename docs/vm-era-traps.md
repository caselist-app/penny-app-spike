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
`droid@debian`. Claude Code inside the VM is a third place.
