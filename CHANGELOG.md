# Changelog

All notable LocalRP changes. The app targets Turkish-speaking users; entries are bilingual when a change matters for English readers.

## v0.7.1 — 2026-10-24

- Model download: `.part` files are kept and **resumed from where they left off** (HTTP Range/206); downloads refuse to run twice (single-flight gate with honest "try again in a second" feedback); the catalog card mounts its progress label and cancel button immediately and updates the percentage in place.
- Cancel semantics hardened: a cancelled download's slot is only released when its writer truly exits; connections are always `disconnect()`ed.
- Repository: Gradle 8.13 wrapper committed and verified (including a from-network build on a fresh cache); private working notes untracked; `ARCHITECTURE.md` added; README states Android 13+, ARM64 and the `com.arm:ai-chat:0.1.0` (Apache-2.0) dependency.
- Small cleanups: dead code removed, error dialog guarded, "İniniyor" → "İndiriliyor".

## v0.7.0 — 2026-10-24

- **Model recommendation center** split into CPU and GPU categories with verified Hugging Face URLs/sizes, per-device memory/cores card and in-app streaming download UI.
- **6 GUI themes** (Lavanta, Okyanus, Orman, Gölge, Deniz Gecesi, Gün Batımı) with instant application and a new "Görünüm" bottom-tab; message bubbles, cards, headers and navigation all follow the palette.
- **Advanced AI settings**: editable system prompt, history message/char budget sliders, auto vs manual context budget, and an auto **character chain** (0–3) with hard caps and per-chat counters.
- Model can now be loaded directly from the app-private storage (no copy step) via `loadStoredModel`.

## v0.6.0

- GGUF model info card (name/architecture/quantization/training context/size), engine benchmark (`bench`), response metrics (duration, tokens, tok/s, context fill).
- Context budget derived from the model's training context (2.048–12.000 chars, engine capped at 8.192 tokens).
- Eight AI scene themes (Serbest → Korku) that steer response atmosphere; theme switches rebuild the model context.

## v0.5.0

- Stable bottom chat composer, dynamic send/stop button, draft protection, message timestamps, long-press copy.
- Group speaker selection: mention-based (Turkish-aware), turn rotation, manual pin ("Sıradaki konuşsun"), auto-chain basics.
- Model copy management, loading progress, error recovery chat-side.
