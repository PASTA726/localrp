# LocalRP — Architecture

LocalRP is deliberately small: one `MainActivity` (~2.6k lines), one Kotlin runtime object, one extracted `Store`, and a set of small pure-decision classes that carry the JVM-tested logic. There are no XML layouts (all screens are built with platform widgets), no network framework and no database — state is JSON in `SharedPreferences`.

> Turkish, offline-first roleplay (RP) app. This document describes how the pieces fit; see README for usage and build steps.

## Modules

```
MainActivity.java      ~2.6k lines — all screens, RP flow, speaker selection, generation orchestration
LocalAiRuntime.kt      llama.cpp via com.arm:ai-chat (Apache-2.0, upstream: github.com/arm/ai-chat)
Store.java             SharedPreferences persistence (extracted from MainActivity in v0.11.x)
RoleplayPrompt.java    pure engine-prompt assembly (persona/cast/history/rules)
AppTheme.java          6 GUI themes as plain color tokens (light + dark)
Design.java            static accessor for the active theme + system-bar painting
Avatar.java            programmatic avatar art for chats/characters
ModelCatalog.java      curated GGUF download list (CPU/GPU columns, verified URLs)
ModelDownload.kt       streaming downloader with cancel, single-writer gate and HTTP-Range resume
DownloadPolicy.kt      pure download decisions (https, filename, Content-Range, size checks)
RuntimePolicy.kt       pure runtime decisions (context budget, predictLength clamp, reload needed)
PersistenceCodec.java  tolerant JSON codec + backup bundle encode/decode/merge
ResponseCleanup.java   shared label/token cleanup for streamed and final response text
ChatWindow.java        chat render-window arithmetic (long chats draw newest 40, older on demand)
```

### MainActivity

Single-activity navigation. Every screen rebuilds a fresh `pageRoot` via `setPage(title, subtitle, tab)` and chains small builders (`addSection`, `addInfoCard`, `addPrimary`, …). Screens:

- `showHome` — hero card, new single/group RP, recent chats, character strip.
- `showCharacters` / character editor — name, description, personality, instructions, example dialogues.
- `showChats` / `showChat` — message bubbles, composer with switchable send/stop button, status line with tok/s and context fill.
- `showModels` — model pick from SAF, engine info card, `bench`, advanced AI entry, catalog entry, stored-model management.
- `showModelCatalog` — device card (RAM/cores), CPU/GPU segmented filter, model cards with download/load states.
- `showAppearance` — 6 themes with swatches; selection persists to `ui_theme`.
- `showAiAdvanced` — system prompt editor, history depth/char-budget sliders, auto/manual context budget, auto-chain count.

**Data store (`Store` inner class).** Characters, groups and per-chat messages are JSON blobs under `SharedPreferences("localrp")`. Messages live under `chat_<id>`; the model cache lives in `files/models/`.

### Speaker selection (group RP)

`chooseNextSpeaker()` picks the next character:

1. **Manual mention** — a character whose name appears word-bounded in the user's message (Unicode-character aware, Turkish letters included) wins; ties resolved by longer name.
2. **Turn rotation** — otherwise the character after the last speaker in the member list.
3. **Fallback** — first member.

`maybeBeginChain()` implements the "character chain" advanced setting (0–3): after a successful visible-chat response, the next speaker is invoked after a 700 ms delay — but only when generation is idle, the model can respond, the chat is still visible, and the per-chat depth (`chainCounts`) is below the limit. Counters reset in `forgetAutoAttempts()` on new sends/clears.

### Context budgeting

- The native engine caps context at 8.192 tokens; GGUF training context may be larger.
- `LocalAiRuntime.contextCharBudget()` ≈ `min(modelContext, 8192) × 2.5` chars (Turkish ≈ 2.5 chars/token), clamped to 2.048–12.000.
- Advanced settings may override per-prompt history depth (2–14 messages), history char budget (1.200–3.600) and a fully manual total budget (3.072–12.000) via `setManualContextBudget`.

### Model loading

Two entry points share one tail (`attachModel()`):

- `loadSelectedModel` — copies a user-picked SAF file into `files/models/`.
- `loadStoredModel` — loads a file already present (in-app downloads, previous copies).

The tail unloads the previous model, reads GGUF metadata in a header pass (name/arch/quant/training context/size class), applies `setSystemPrompt(currentSystemPrompt())` and reports completion. A failed `bench()` can wedge the engine in the `Benchmarking` state; generation attempts then best-effort reset the engine (`cleanUp()`) before reloading, and the UI reports the recovery in the status line. A full app restart remains the manual fallback.

### Downloading (`ModelDownload.kt`)

- Writes to `<fileHint>.part`, `fd.sync()`s, then renames.
- Single-flight gate: an `AtomicBoolean` taken at launch and released in the coroutine's `finally` — a cancelled job keeps the slot until its writer is really gone, so attempts during the small unwind are refused (the UI reports a toast).
- If `.part` exists the request carries `Range: bytes=<part>-`; an HTTP 206 (all catalog URLs were verified resumable) appends and reports offset-adjusted progress; a 200 response restarts cleanly.
- `cancel()` clears `currentUrl` and cancels the job; the read loop relays cancellation via `isActive` at most 256 KB later, and the `.part` stays for resume.
- Storage is checked with `StatFs` (128 MB headroom); metered networks trigger a confirm dialog in the UI layer.
- Progress callback reports `(percent, bytes, total)`; `total` is resolved from `Content-Range` (206) or `Content-Length` (200), `-1` when unknown (periodic byte-only ticks).

### Theming (`AppTheme` / `Design`)

`AppTheme` holds 6 palettes; `Design` exposes them as static accessors (`Design.text()`, `Design.accent()`, …) that `MainActivity` uses everywhere — the only hardcoded colors left are `styles.xml`'s cold-start mirror of the default Lavanta palette (used on the first frame before `Design.applyBars()` runs; see the comment there). `Design.applyBars()` paints status/navigation bars and toggles light appearance icons. On Android 15+ (enforced edge-to-edge) the root view pads itself with the system bar insets so nothing hides under the bars; users on Android 14 get the painted bars.

### Privacy

- Only permission: `INTERNET`, used exclusively for catalog downloads.
- No keys embedded or fetched; no telemetry; prompts never go online unless the user installs an online provider (not in this build).
- Storage: app-private `SharedPreferences` + `files/` (unencrypted; device lock addresses confidentiality), `allowBackup=false`.

## Known limits

- Engine does not expose sampling parameters (temperature/top-p/top-k); steering is prompt- and length-based.
- Streaming works through the engine (`onToken` per token), so bubbles grow incrementally; the streamed text is re-cleaned per token and the crash draft is persisted at most every 1.5 s (rate-limited to keep the token path O(n)).
- The message store keeps an in-memory chat cache since v0.7.4 (no re-parse per screen rebuild) and keeps every write out of unreadable keys; safety caps for restore reads are 256 MB. Backup/restore via SAF shipped in v0.11.0; long chats render a 40-message window since v0.12.0.
- No content-filter layer; RP personas write as fictional characters by system prompt only.
- `MainActivity` is a single ~2.4k-line class by design (no architecture framework); splitting it further was traded away on purpose to keep dependencies at zero.
