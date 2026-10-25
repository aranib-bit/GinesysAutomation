# Windows Grid readiness

Status: **framework is ready, no Windows Grid exists, Jenkins offers Linux only.**
Goal when enabled: run the same suites on Chrome, Firefox and Edge inside a Windows 11 container
(`dockurr/windows`) and compare the reports with Linux runs.

## What already exists in code

| Switch | Effect |
|---|---|
| `-Dtarget.os=linux\|windows` (default `linux`) | `DockerGridProvider` sets `platformName` on the session so the Grid routes to a node of that OS. Not applied to the plain `REMOTE` grid. Resolved by `TargetOsResolver`. |
| `max.concurrent.sessions.linux` / `.windows` (`execution.properties`; blank = no cap; windows ships as `2`) | Caps `SessionPool.capacity()` for that OS (`ConcurrencyCap`). `max.concurrent.sessions` overrides both for a one-off run. Keep the Windows node's `--max-sessions` >= this value. |
| Browser/OS version recording | `BrowserFingerprint` records browser, version, platform and driver version when each session is created. Shown in the coverage workbook (Notes sheet and per-test columns, with a drift warning if one browser ran on two major versions), the Extent header, the failure diagnostics table, and `reports/runs/<runId>/environment.json`. Works on Linux today. |

Linux behaviour is unchanged: `target.os` defaults to `linux` and the Linux cap is blank.

## Why it is not enabled

`dockurr/windows` needs `/dev/kvm`. Checked on 2026-10-04 on the Jenkins PC (Dell Latitude 3420, i3-1115G4, 4 logical cores,
16 GB): no `/dev/kvm` in the WSL2 / Docker Desktop VM (the KVM kernel modules are installed but not loaded), Docker
gets only 4 cores / 8 GB, and no other Linux host is configured. A Windows 11 guest needs about 4 cores / 8 GB on top of
Jenkins and the Linux Grid, so it does not fit.

**Needed host:** Linux with `/dev/kvm`, 8+ cores, 16+ GB RAM, 64+ GB free disk, Docker + compose, reachable from Jenkins
(`DOCKER_HOST=ssh://...` preferred).

## Checklist to enable later

1. **Host preflight.** `ls /dev/kvm`; boot the stock dockur example (`VERSION=11`, web viewer on 8006).
2. **Grid topology.** The current Grid (`selenium/standalone-all-browsers`) is hub + node in one process and cannot accept a
   remote node. Split `docker/docker-compose.selenium-grid.yml` into `selenium/hub` (ports 4442/4443/4444) + a Linux
   `node-all-browsers`, or run one shared hub for the Windows node. Each node needs its own client-reachable `SE_NODE_HOST`.
3. **Windows image.** New `docker/windows/` with a compose file (`dockurr/windows`, `/dev/kvm`, `/dev/net/tun`, `NET_ADMIN`,
   persistent `/storage`) and `oem/install.bat`: JDK 17, Chrome, Firefox, Edge, drivers, `selenium-server`, auto-logon,
   1920x1080, updates/Defender/lock screen off, a startup task that registers the node. Pin browser versions.
4. **Jenkinsfile.** Add the `OS` choice; pass `-Dtarget.os`; per-OS compose file / `DOCKER_HOST` / hub URL; wait for the node to
   register (`/status`) with a longer timeout; lock name with a `-win` suffix plus a global Windows lock; optional
   `MAX_SESSIONS` and `HEADLESS` parameters; keep the Windows VM running between builds.
5. **Download directory.** `BrowserOptions` sends a Jenkins-host path to the remote browser, which does not exist on any
   Grid node (already true on Linux). Use the Grid download API (`se:downloadsEnabled`) or a per-OS guest path before relying
   on Export-to-Excel tests.
6. **Licence.** dockurr images ship trial keys; confirm the licensing policy.

## Known risks

- `ViewportEnforcer` uses CDP `devtools.v153`, tied to the Chrome major version; keep Windows and Linux Chrome/Edge majors aligned.
- Edge options lack `--no-sandbox` / `--disable-dev-shm-usage`, which Chrome has; a possible source of report differences.
- One Windows VM at 4 cores / 8 GB supports about 2-3 parallel sessions (hence the cap).
