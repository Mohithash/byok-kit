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

The engine is now **v1.1 "full features"**, and every factory app gets: follow‑up threads on any answer, regenerate and edit & rerun, 40+ answer languages, answer length plus standing instructions, read aloud, voice typing, PDF/Markdown export, share‑in from other apps, launcher shortcuts, favourites/notes/search/filters on history, backup & restore, Material You colours, a token usage counter, and cancel plus automatic retry. The reusable parts (client, store, theme, settings card, read‑aloud) are ported back into this kit below.

**App store:** https://mohithash.github.io/byok-store/ (web) · https://github.com/Mohithash/StoreApp (native Android store with one-tap install).

## Kit
- `template/src/ai/AiClient.kt` — raw‑HTTP client for Anthropic Messages (JSON‑schema `output_config.format`, image blocks) and OpenAI‑compatible chat completions; `Schema` helpers. Default model `claude-opus-5-5`; `AiProvider.suggestedModels` lists picks per provider.
  - **Retries:** 408, 409, 429, 5xx, 529 and dropped connections are retried up to 3 tries with backoff, honouring `retry-after`. A generation may take up to 10 minutes (answers aren't streamed); a read timeout after the request was sent is reported, not retried, so a slow answer is never generated and billed three times.
  - **Cancel:** requests are cancellable. Cancelling the calling coroutine (e.g. a `Job` behind a Stop button) disconnects the socket at once.
  - **Usage:** `chat(...)` still returns `String`; `chatFull(...)` returns `AiReply(text, usage, model)`, and `client.onUsage = { u -> … }` sees the `AiUsage` of every billed call (including refused or truncated ones), which is enough for a running token counter.
  - **`listModels(settings)`:** returns the model ids the key can use (Anthropic `/v1/models`, or OpenAI‑compatible `/models`).
  - **OpenAI‑compatible URLs:** a base URL may end in `/v1` or omit it; one that names its own version (Gemini's `…/v1beta/openai`) is used as‑is (`AiSettings.openAiRoot`). On api.openai.com the client sends `max_completion_tokens` (required by o‑series / gpt‑5 models, with low reasoning effort); other servers get `max_tokens` (≤ 8000), switching once if a gateway asks for the other.
  - **Errors and truncation:** errors read in plain words (bad key, wrong model/base URL, rate limit, overloaded). A JSON reply cut off by `max_tokens`/`length` fails with a clear "ran past the length limit" error instead of broken JSON. A refusal includes the provider's explanation when there is one.
  - **Fallback:** on the Claude API (blank base URL) with Opus 5.5 / Opus 5 / Sonnet 5.5 / Fable 5.1, requests opt into the server‑side refusal fallback (`fallbacks: "default"` + `anthropic-beta: server-side-fallback-2026-07-01`). If a server rejects it, it is turned off for the rest of the process and the request is resent plain. `output_config.effort` is handled the same way and is never sent to Haiku / Sonnet 4.5 / Claude 3.
- `template/src/data/JsonStore.kt` — typed SharedPreferences store exposing `StateFlow`s; thread‑safe, with `update(key, ser, default) { … }` (atomic read‑modify‑write), `raw`/`putRaw` (backup & restore) and `remove` — raw writes and removals also update any open flow for that key.
- `template/src/ui/Ui.kt` — design kit: `HeroCard` (gradient + expressive shape blob), `StatCard`, `ShapeIcon`, `EmptyState`, `TrendChart`, `BarChart`, `AnimatedNumber`.
- `template/src/ui/Photo.kt` — downscale + EXIF‑correct JPEG for vision requests.
- `template/src/ui/Speech.kt` — read‑aloud on the device TTS engine. `val speaker = rememberSpeaker()` creates one and shuts it down when the screen leaves. Then `speaker.toggle(text)` (or `speak(text, locale)` / `stop()`). Markdown is flattened to plain speech and long text is chunked under the engine limit. `speaker.speaking` / `speaker.available` are Compose state, ready for a play/stop button.
- `template/src/ui/theme/Theme.kt` — `AppTheme(darkTheme = isSystemInDarkTheme(), dynamic = false)`. Pass the user's light/dark choice as `darkTheme`; `dynamic = true` swaps the generated `Brand` palette for Material You on Android 12+ (`dynamicColorAvailable` says whether to show the switch). Use `LocalHeroDeep.current` instead of `Brand.heroDeep` so hero gradients follow the wallpaper too.
- `template/src/ui/screens/AiSettingsCard.kt` — provider / key / model / base URL with a round‑trip test. The model field has a dropdown of `suggestedModels`, and ⟳ replaces it with the key's live `listModels` list. Test reports the model that actually answered; a plain `http://` base URL shows an "unencrypted" warning.
- `new_app.py <Dir> "<Name>" <pkg> <primary> <secondary> <tertiary> "<iconPath>"` — generates project, derived light/dark palette, adaptive icon, release keystore. Also generates `res/xml/backup_rules.xml` + `full_backup_content.xml`, which keep Android backup on but exclude the `secrets.xml` shared prefs, the manifest `<queries>` for TTS and speech recognition (Android 11+ package visibility), and `res/xml/network_security_config.xml` allowing plain http so the OpenAI‑compatible provider can reach a model server on the user's own device or LAN (Ollama, LM Studio) — everything else stays HTTPS.
- `ship.sh <Dir> "<Name>" <version> <notes.md>` — builds signed APK + AAB, commits, creates private repo, tags, publishes GitHub release.
- `PRIVACY_TEMPLATE.md` — Play‑ready privacy policy for BYOK apps.

### Keep the API key out of backups
Generated apps exclude `secrets.xml` from cloud backup and device transfer, so put `AiSettings` in its own store and everything else in the default one:

```kotlin
val secrets = JsonStore(context, "secrets")          // → shared_prefs/secrets.xml, never backed up
val ai by secrets.flow("ai", AiSettings.serializer(), AiSettings()).collectAsState()   // in a composable
AiSettingsCard(ai, client, onSave = { secrets.set("ai", AiSettings.serializer(), it) }, onMessage = ::toast)
```

For an existing app, copy the two `res/xml` files and the manifest attributes from `new_app.py`, then on first launch move the old key: read it from the old store, `secrets.set(...)` it, and `remove(...)` it from the old store.

## Play Store checklist (per app)
1. Upload the `.aab` from the GitHub release.
2. Privacy policy URL → the repo's `PRIVACY.md` (make the repo public or host the file).
3. Data safety: no data collected by the developer; user‑entered content sent to a third‑party AI provider only on user action, with the user's own key.
4. Keep `release.jks` + `keystore.properties` from each project directory backed up — updates must be signed with the same key (or enrol in Play App Signing on first upload).
