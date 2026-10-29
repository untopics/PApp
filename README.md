# Mini Hub

One Android app, many tiny single-purpose mini apps. Kotlin + Jetpack Compose, no third-party libraries, no network, no animations. Data lives in one JSON file per mini app in the app's private storage.

## Build & install

**Android Studio** (easiest): File → Open → this folder. Let it sync (it downloads Gradle 8.11.1 and the SDK it needs), then Run on your phone with USB debugging enabled.

**GitHub Actions** (no local SDK): push this folder to a repo. The included workflow builds a release APK (signed with the debug key, fine for sideloading) and attaches it as the `minihub-apk` artifact. Download it on the phone and install (allow "install unknown apps" for your browser/files app).

**CLI** with a local Android SDK: `gradle :app:assembleRelease` → `app/build/outputs/apk/release/app-release.apk`.

## Structure

```
core/       MiniApp + Launcher interfaces, Registry, JsonStore, shared UI (StepRow, ChoiceRow, ArmedButton)
launcher/   GridLauncher (cards), RingLauncher (hold → slide → release)
apps/       calories/, sports/  — each fully self-contained
```

### Add a mini app
1. Create `apps/<name>/` with an `object MyApp : MiniApp` (id, name, emoji icon, accent, `@Composable Content(ctx)`).
2. Persist via `ctx.store` (its own file, `read()` / `write()` JSONObject).
3. Add it to `Registry.apps`.

### Add a launcher
Implement `Launcher` and add it to `Registry.launchers`. Switch launchers with the icons top-right on the home screen (choice is remembered).

### Cross-app data (prepared, unused so far)
Apps can expose read-only values by overriding `readMetric(store, metric, date)`; other apps call `ctx.query(appId, metric, date)`.
- `calories`: `kcal`
- `sports`: `done:<exerciseId>`, `plan:<exerciseId>`

## Behaviour notes

- **Ring launcher:** press and hold the center button, slide toward an app (selection by angle, haptic tick), release to open. Release on the center cancels. A quick tap keeps the ring open; then tap an app.
- **Calories:** +/−10/50/100 for today, never below 0. Week = last 7 days, Month = last 30 days. The limit field lives only in memory.
- **Training plan:** a weekly template (Mon–Sun). Each edit stores a new plan version effective *today*; a day resolves to the newest version that started on or before it. So past days are frozen, and a change during the day becomes that day's plan.
- **Reset all / Copy to all days:** tap twice within 3 s (no dialogs).
- Exercises are defined in `apps/sports/SportsModel.kt` (`EXERCISES`). Don't rename ids of exercises that have data.
