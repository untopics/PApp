# Mini Hub

One Android app, many tiny single-purpose mini apps. Kotlin + Jetpack Compose, no third-party libraries, no animations. Only the Lawn app uses the network (weather). Data lives in one JSON file per mini app in the app's private storage.

## Build & install

**Android Studio** (easiest): File → Open → this folder. Let it sync (it downloads Gradle 8.11.1 and the SDK it needs), then Run on your phone with USB debugging enabled.

**GitHub Actions** (no local SDK): push this folder to a repo. The included workflow builds a release APK (signed with the debug key, fine for sideloading) and attaches it as the `minihub-apk` artifact. Download it on the phone and install (allow "install unknown apps" for your browser/files app).

**CLI** with a local Android SDK: `gradle :app:assembleRelease` → `app/build/outputs/apk/release/app-release.apk`.

## Structure

```
core/       MiniApp + Launcher interfaces, Registry, JsonStore, shared UI (StepRow, ChoiceRow, ArmedButton, SwipeToDelete, DatePickScreen)
controls/   ValueSlider, Dial (reusable controls library)
launcher/   GridLauncher (cards), RingLauncher (Dial: hold → slide → release)
apps/       calories/, sports/, traditions/, lawn/, todo/  — each fully self-contained
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

Apps can also hook `onHubStart(context)` (called once when the hub activity starts), e.g. to schedule background work.

## Behaviour notes

- **Ring launcher:** press and hold the center button, slide toward an app (selection by angle, haptic tick), release to open. Release on the center cancels. A quick tap keeps the ring open; then tap an app.
- **Calories:** +/−10/50/100 for today, never below 0. Week = last 7 days, Month = last 30 days. The limit field lives only in memory.
- **Weight (in Calories):** the slider right of today's kcal always starts at the latest logged weight. Slide up/down for ±1 kg, committed in 0.1 kg steps; one value per day (a new one replaces today's). Before the first entry it's a number field. The chart shows weight in a band above the kcal bars (same day axis): dots = weigh-ins, line = 7-day average (mean of the weigh-ins in the 7 days ending that day), dashed line = "Goal kg". The band zooms to the visible weights; a goal far outside it is pinned to the band edge with ↑/↓.
- **ValueSlider** (`controls/`): the value follows the finger directly, shown one decimal finer than the step (0.1 → 10.01, 10.02 …); lifting commits it rounded to the step (10.03 → 10.0, 10.05 → 10.1). Haptic tick on each step change. `mode = SliderMode.Bidirectional(start)` (opt-in) opens the track both up and down from the button: up adds, down subtracts. Each half is `trackLength / 2`, or up to the screen edge if there's less room — positioning the control near the screen middle is the caller's job.
- **Dial** (`controls/`): center button with items on a ring or arc, used by the Ring launcher and the Todo lists. Hold the button, slide toward an item (selection by angle), release to pick; release on the button cancels; a quick tap keeps it open for tapping. `anchor` + `anchorPadding` place the button, `startAngle` (0 = up, clockwise) and `sweepAngle` (360 = full circle) shape the arc, e.g. `BottomCenter`, `-90f`, `180f` = half circle above a button at the bottom. The radius grows up to `maxRadius` when items don't fit, then the items shrink.
- **Training plan:** a weekly template (Mon–Sun). Each edit stores a new plan version effective *today*; a day resolves to the newest version that started on or before it. So past days are frozen, and a change during the day becomes that day's plan.
- **Reset all / Copy to all days:** tap twice within 3 s (no dialogs).
- Exercises are defined in `apps/sports/SportsModel.kt` (`EXERCISES`). Don't rename ids of exercises that have data.
- **Traditions:** Austrian family traditions, kindergarten and Burgenland school dates, regional holidays and birthdays. Calendar tab = every day as a row with its entries; Upcoming tab = next 365 days. Tap an entry for details, prep tasks and reminder dates. Built-in entries live in `apps/traditions/TraditionsCatalog.kt` (date rules: fixed, Easter offset, nth weekday, Advent, school calendar) — don't rename ids that have date overrides. Any built-in date can be changed for one year ("Change date"). "+" adds an entry by category (v1: Birthday = name + date of birth; reminders 4 weeks, 2 weeks, 3 days before and on the day). "All"/"Main" toggles optional entries. Notifications: one daily check around 08:00 posts that day's reminders.

### Todo
- Lists with text entries. Switch lists with the **dial at the bottom** (half circle; the hub's ring sits mid-screen): hold and slide, or tap then tap a list. The ringed item is the current list; "+" on the dial adds a list. The dial shows each list's first character (emoji work). ✎ renames / deletes the current list (tap twice; the last list can't be deleted).
- **+** (bottom right) opens the add form with the title focused and the keyboard up. Keyboard ✓ or Save saves; "Save + next" keeps the form open for the next entry.
- **Due date** (optional): slide the button up, 0–60 days ahead (finer in the first two weeks), or pick any date with 📅. ✕ clears.
- **Reminder** (optional): with a due date: on the day / 1 day / 3 days / 1 week before (moves along when the due date changes); without: today / tomorrow / in 3 days / in a week; or any date with 📅. Fires with the daily check around 08:00 like Traditions; a reminder set for today after 08:00 fires right away. Done or deleted entries drop their notification.
- Open entries sort by due date (none last), then creation. Tap the box = done, tap the row = edit, swipe = delete. Done entries collapse under "Done (n)" with "Clear done".

### Lawn
- **Weather:** [Open-Meteo](https://open-meteo.com) (free, no key), location fixed to Oggau (`Place.OGGAU`, stored in the app's JSON so a picker can be added). Fetched when the hub starts (as soon as there is network), when the app opens, on ↻, and every ~6 h in the background while any notifications are enabled.
- **No weather history:** each forecast day is replaced on every fetch and dropped once it's past. Every day keeps its own `fetchedAt`; weather notifications only use entries fetched within the last 24 h.
- **Stage buttons** (New lawn / Developing / Established) only switch the view. Fetching and notifications always run for all stages.
- **Notifications** can be enabled for any combination of stages. Max one per event per day (weather events, germination watering); plan steps and seasonal tasks once per occurrence.
  - New lawn: plan steps on their due date (incl. "organise" lead times for soil, seed, fertilizer, machines), daily germination watering (days 0–21), and frost/heat/wind/heavy rain — only around the planned sowing date.
  - Developing: frost (only Mar–May and Sep–Nov), heat, dry spells, seasonal tasks.
  - Established: heat, dry spells, seasonal tasks.
- Thresholds: `Thresholds` in `LawnModel.kt`. Plan steps, sowing windows and seasonal tasks: `LawnPlan.kt`. Notification rules: `LawnRules.kt`.
