# BYOK app kit

Scaffold + shared code for a family of **bring‑your‑own‑key** Android apps (Jetpack Compose, Material 3 Expressive, Room, AGP 9.4, compileSdk 37.1).

| App | Category | Repo |
|---|---|---|
| Calorie Bank | Health & Fitness | https://github.com/Mohithash/CalorieBank |
| Pantry Chef | Food & Drink | https://github.com/Mohithash/PantryChef |
| Mindstream | Lifestyle / Wellbeing | https://github.com/Mohithash/Mindstream |
| Lingo Loop | Education | https://github.com/Mohithash/LingoLoop |
| Ledgerly | Finance | https://github.com/Mohithash/Ledgerly |
| Rep Coach | Health & Fitness | https://github.com/Mohithash/RepCoach |
| Cram | Education | https://github.com/Mohithash/Cram |
| Trip Weaver | Travel & Local | https://github.com/Mohithash/TripWeaver |
| Braindump | Productivity | https://github.com/Mohithash/Braindump |
| Sprout | House & Home / Lifestyle | https://github.com/Mohithash/Sprout |
| Draftly | Communication / Business | https://github.com/Mohithash/Draftly |
| Story Nest | Parenting / Books | https://github.com/Mohithash/StoryNest |

## Factory (603 more apps) + Store
A config-driven engine at https://github.com/Mohithash/byok-factory builds one app per JSON spec (Gradle flavors). See its `CATALOG.md` for all 603 apps with listings and releases.

**App store:** https://mohithash.github.io/byok-store/ (web) · https://github.com/Mohithash/StoreApp (native Android store with one-tap install).

## Kit
- `template/src/ai/AiClient.kt` — raw‑HTTP client for Anthropic Messages (JSON‑schema `output_config.format`, image blocks) and OpenAI‑compatible chat completions; `Schema` helpers.
- `template/src/data/JsonStore.kt` — typed SharedPreferences store exposing `StateFlow`s.
- `template/src/ui/Ui.kt` — design kit: `HeroCard` (gradient + expressive shape blob), `StatCard`, `ShapeIcon`, `EmptyState`, `TrendChart`, `BarChart`, `AnimatedNumber`.
- `template/src/ui/Photo.kt` — downscale + EXIF‑correct JPEG for vision requests.
- `template/src/ui/screens/AiSettingsCard.kt` — provider / key / model / base URL with a round‑trip test.
- `new_app.py <Dir> "<Name>" <pkg> <primary> <secondary> <tertiary> "<iconPath>"` — generates project, derived light/dark palette, adaptive icon, release keystore.
- `ship.sh <Dir> "<Name>" <version> <notes.md>` — builds signed APK + AAB, commits, creates private repo, tags, publishes GitHub release.
- `PRIVACY_TEMPLATE.md` — Play‑ready privacy policy for BYOK apps.

## Play Store checklist (per app)
1. Upload the `.aab` from the GitHub release.
2. Privacy policy URL → the repo's `PRIVACY.md` (make the repo public or host the file).
3. Data safety: no data collected by the developer; user‑entered content sent to a third‑party AI provider only on user action, with the user's own key.
4. Keep `release.jks` + `keystore.properties` from each project directory backed up — updates must be signed with the same key (or enrol in Play App Signing on first upload).
