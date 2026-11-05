# DineTime

Offline-first meal planner demo: Jetpack Compose, Navigation 3, Couchbase Lite, Couchbase Server and Sync Gateway,
built on CQRS.

- **Reads DB** – collection `meals` (what the UI shows).
- **Writes DB** – collection `meal_commands` (every create/update/delete is a command).
- **Fake external server** – runs inside the app (`fakeserver/`). It watches commands through Sync Gateway's admin
  REST API, acknowledges them, validates them, and writes the real `meals` document to Couchbase Server.

## Status flow

| Status | Meaning |
|---|---|
| `PENDING` | Temp meal doc (`tmp::<id>`) saved locally and command queued. |
| `PROCESSING` | Command acknowledged by the server (replicated back). |
| `ACTIVE` | Real meal doc replicated back; the Reconciler deletes the temp doc. |
| `ERROR` | Unrecoverable 4xx. The error is shown, you fix the input and resubmit. 5xx is retried with backoff. |

The temp doc is never pushed to the server (push filter on `isTemp`).

## 1. Prerequisites

| Tool | Version / notes |
|---|---|
| Android Studio | Recent release that supports AGP 9.x |
| JDK | 17+ (Android Studio's bundled JBR is fine) |
| Android SDK | compileSdk per `app/build.gradle.kts`; minSdk 26 |
| Emulator or device | API 26+; API 33+ asks for notification permission |
| Docker + Compose v2 | For Couchbase Server and Sync Gateway; about 2 GB free RAM |
| Network | First Gradle sync and image pulls need internet |

Ports used on the host: 8091-8096, 11210 (Couchbase), 4984 (SG public), 4985 (SG admin).

## 2. Start the backend

```bash
cd infra
docker compose up -d          # couchbase:community-7.6.2 + sync-gateway:4.1.2-community
# wait ~30-60 s for Couchbase to boot, then:
./setup.sh
```

`setup.sh` is idempotent. It:
1. Initialises the cluster (index storage mode `forestdb`, which Community Edition requires for Sync Gateway indexes).
2. Creates bucket `dinetime`, scope `dinetime`, collections `meals` and `meal_commands`.
3. Creates the Sync Gateway database `dinetime` with a sync function that scopes docs to `user::<ownerId>`.
4. Creates the sync user `demo` / `password`.

Verify:
```bash
curl -s localhost:4984/                                   # {"couchdb":"Welcome",... "version":"Couchbase Sync Gateway/4.1.2..."}
curl -s -u Administrator:password localhost:4985/dinetime.dinetime.meals/_all_docs
```
Couchbase web console: http://localhost:8091 (`Administrator` / `password`).

> Couchbase Lite 4.x needs Sync Gateway 4.x. With SG 3.x replication fails with `HTTP 400 Invalid revision ID`.

Reset to a clean slate: `docker compose down -v && docker compose up -d && ./setup.sh`

## 3. Build and run the app

```bash
./gradlew :app:assembleDebug
~/Android/Sdk/platform-tools/adb install -r -g app/build/outputs/apk/debug/app-debug.apk
```
Or open the project in Android Studio and press Run.

- **Emulator** – works out of the box. The default host `10.0.2.2` is the emulator's alias for your machine.
- **Physical device** – use the same Wi-Fi as your computer, then in the app go to Settings → Sync Gateway → Host and
  enter your computer's LAN IP. Tap Save. Cleartext HTTP/WS is allowed for this demo.
- Grant the notification permission when asked, otherwise reminders will not show.
- For exact-alarm reminders on Android 12+/14+, allow "Alarms & reminders" for DineTime in system settings.

Defaults (Settings screen): host `10.0.2.2`, public port `4984`, admin port `4985`, database `dinetime`,
user `demo` / `password`, simulator admin `Administrator` / `password`.

Unit tests: `./gradlew :app:testDebugUnitTest`

## 4. Test script

The sync bar at the top of Home shows replication state ("Synced · idle", "Can't reach server · working offline").

1. **Unplanned meal** – Log meal, enter a title, Save. Expect `Pending`, then `Processing`, then `Active`. In
   Couchbase (or via `_all_docs`) a `meal::<id>` doc appears. Locally the `tmp::` doc is gone.
2. **Group by day** – use the week arrows and All / Planned / Unplanned filters. Meals are grouped by day of week.
3. **Planned meal with reminder** – Plan meal, choose a time a few minutes ahead, choose a reminder lead
   (for example 1 min before). The bell icon on Home lists upcoming reminders. At the reminder time a notification
   fires. Default lead is under Settings.
4. **Offline** – Dev panel (bug icon) → turn off "Replication on". Create and edit meals. They stay `Pending` and the
   sync bar shows an in-flight count. Turn replication back on and they progress to `Active`.
5. **Server paused / slow** – Dev panel → "Pause processing" keeps requests `Pending`. "Latency per step" slows the
   transitions so they are easy to see.
6. **400 error** – either save a blank title, plan a meal in the past, create a duplicate `scheduledAt`, or enable
   "Reject next create/update with 400". The meal shows `Error` with the payload error. Open it, fix, and resubmit.
7. **500 retry** – Dev panel → "500 ×1" or "500 ×3". The server retries with backoff and the meal ends `Active`.
8. **Dropped ack** – "Drop next acknowledgement" leaves the command un-acked, so the UI stays `Pending` until the server
   retries.
9. **Edit / delete** – open an `Active` meal and edit or delete it. An edit shows an overlay temp doc (`Pending`),
   then returns to `Active`. Editing is blocked while a command is in flight.
10. **Logs** – the Dev panel lists commands and a server log. Commands can be inspected server side with:
    ```bash
    curl -s -u Administrator:password "localhost:4985/dinetime.dinetime.meal_commands/_all_docs?include_docs=true"
    ```

> Steps 3 and 5-9 are written from the implemented behaviour; only step 1 has been verified end to end on an emulator.

### Simulator behaviour and failure injection

- **Ownership:** every command carries the `deviceId` of the install that created it (shown in the Dev panel). Each app's simulator processes only its own commands, so several emulators/devices sharing one Sync Gateway no longer steal each other's commands. Commands from other devices are counted as "ignored". Commands without a `deviceId` (old builds) are processed only if "Also process legacy commands" is on. **Update every install to this build** or old builds will still complete other devices' commands.
- **One-shot:** "Reject next with 400" and "`500 ×N`" are snapshotted onto the next command the simulator accepts, then cleared.
- **Persistent:** the "Always 500" / "Always 400" switches fail every command until switched off ("Reset all injections" clears everything). Use these for deterministic tests.
- A command that has reached `COMPLETED` or `FAILED_USER_ACTION` is never overwritten by a later pass.
- 500 retries show as a "Retrying (n)" badge.

### Theme

The UI uses Material 3 Expressive with the palette and Comic Neue font from `theme/`. Settings has a "Use wallpaper colours" switch (Android 12+, off by default). Material3 is pinned to `1.5.0-beta01` in `gradle/libs.versions.toml` because the BOM only provides 1.4.0, where the expressive APIs are unavailable. The first font load needs network (Google Fonts provider); bundled copies are the fallback.

## 5. Troubleshooting

| Symptom | Fix |
|---|---|
| "Can't reach server · working offline" | Check `docker compose ps`; `curl localhost:4984`; host setting (`10.0.2.2` on emulator, LAN IP on device). |
| Stuck `Pending`, "Cannot reach Sync Gateway admin API" in Dev panel | Admin port `4985` blocked, or wrong admin credentials in Settings. |
| `HTTP 400 Invalid revision ID` in logcat | Sync Gateway is 3.x; use 4.1.2 (see `infra/docker-compose.yml`). |
| `setup.sh` hangs or SG never starts | Couchbase was not ready. Wait and rerun. Confirm `storageMode=forestdb` was applied (Community Edition). |
| SG container exits with `permission denied ... sg_error.log` | Keep `log_file_path` set to `/tmp/sg_logs` in `infra/sync-gateway-config.json`. |
| Gradle: Kotlin metadata version error | Keep serialization 1.9.0 and coroutines 1.10.2 (Kotlin 2.2.10). |
| No notifications | Grant the notification and exact alarm permissions; the reminder must be in the future. |
| Fresh device state | `adb shell pm clear com.dinetime`, and reset the backend as in section 2. |

`CouchbaseLite/DATABASE ... Cannot get cookies` warnings in logcat are harmless.

## Presentation

`slides/` holds the interactive talk deck (React + TypeScript + Vite) with an in-browser round-trip simulator. See [`slides/README.md`](slides/README.md). Quick start: `cd slides && npm install && npm run dev`.

## 6. Layout

```
app/src/main/java/com/dinetime/
  core/        settings store
  domain/      models, merge/status/grouping logic
  data/        Couchbase provider, replicators, repository
  sync/        Reconciler (status transitions, temp doc cleanup)
  fakeserver/  Sync Gateway admin client and server simulator
  reminders/   AlarmManager scheduler, receivers
  ui/          home, editor, detail, settings/reminders, dev panel
infra/         docker-compose.yml, sync-gateway-config.json, setup.sh
```
