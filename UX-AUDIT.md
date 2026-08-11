# UX / UI Audit — MaskedD (remix-maskedd-by-daniel)

**Audience:** Engineering & product leadership. **Companion doc:** `detailed.md` (full 18-section architecture/security audit). This document is the complete, screen-by-screen user-experience audit, written against the real user complaints that motivated it:

> **"Stuff doesn't work." — "The UI is annoying." — "Everything is complicated."**

Every claim below is grounded in a verified `file:line` reference from this exact checkout. Nothing is assumed from documentation. Complaints are mapped to their root causes in code, then to a prioritized remediation plan.

---

## 0. Method

- Full read of every UI screen and component: `LyricReaderScreen.kt` (2,243 lines), `SettingsScreen.kt`, `LibraryScreen.kt`, `PasteModal.kt`, `AiVoiceLabModalBottomSheet.kt`, `VoiceCharactersModalBottomSheet.kt`, `InstantVoiceCloneModal.kt`, `KineticSprintReaderView.kt`, `AuraFormatSelectorBar.kt`, `TtsDiagnosticDashboardModal.kt`, `SentientArticulationModalBottomSheet.kt`, `BiomechanicalVoiceModalBottomSheet.kt`, `BookmarksScreen.kt`, `AppUpdateModal.kt`, `ui/theme/*`, `ui/components/*`.
- Cross-symbol verification with `grep` across the whole app: every UI-facing string, state flag, and callback traced to its consumer (or absence of one).
- Wire-level verification of the TTS/cloud claims (`detailed.md` sections 5–8): engine liveness, API key presence, and network behavior verified in `SpeechEngine.kt`, `CloudVoiceSynthesizer.kt`, `InstantVoiceCloneUploader.kt`, `DynamicAudioPipelineLoop.kt`.
- Severity levels used throughout: **P0** (user-facing lie / broken core flow), **P1** (friction or confusion that blocks a feature), **P2** (polish, consistency, a11y).

---

## 1. Executive Summary — Complaint → Root Cause

| User complaint | What actually happens (verified) | Root cause | Severity |
|---|---|---|---|
| "The app says TTS engine active but nothing plays" | The header always reports an active AI engine even when no engine is initialized or when playback falls back to Android TTS | Static status string at `SpeechEngine.kt:491-492`; status is a cosmetic string, not derived from engine state | **P0** |
| "The sleep timer I set never fires" / "It stops twice" | Two independent sleep timers exist; the one shown in the UI and the one the engine honors can disagree | `LyricReaderScreen.kt:797` (UI timer) vs `:1024` (second timer) with separate state | **P0** |
| "I cloned my voice and it doesn't sound like me — or doesn't work offline" | The "clone" upload never makes a network request; success is always mapped to the pre-made Rachel voice profile | `InstantVoiceCloneUploader.kt:88,118` — no HTTP call, hardcoded fallback `21m00Tcm4TlvDq8ikWAM` also at `VoiceCharactersModalBottomSheet.kt:552` | **P0** |
| "The app forced me to update but the permission didn't work" | Mandatory-update screen; "Later" dismisses but the app still requires closing; screenshot shows the app can never be used | `version.json` versionCode 2 vs app versionCode 1; `MainActivity.kt:82` no-op; `AppUpdateModal.kt:142-147` | **P0** |
| "Voice Fine-Tuning sliders don't change anything" | Four sliders write local state only; values never read by any engine | `SettingsScreen.kt:242-245,333-357` | **P0** |
| "It's always dark even though I set light mode" | The app is hardcoded dark; light theme is never wired to the system | `Theme.kt:38` `darkTheme: Boolean = true` "Default to sleek dark canvas"; `MainActivity.kt` never passes a value | **P1** |
| "The Warm Parchment theme is unreadable" | The theme preset sets a cream background but reader text is hardcoded white | `LyricReaderScreen.kt:262-391` hardcoded `Color.White`; contrast ~1.1:1 on `PAPER_PARCHMENT` (`Color.kt:51-58`) | **P0** |
| "Time remaining is wrong / flickers" | Time-remaining text is computed but never displayed; shown playback time differs from the engine's position anchor | `LyricReaderScreen.kt:174` `timeRemainingText` dead | **P1** |
| "There are too many modes and none of them make sense" | Two parallel mode systems (ReadingMode pills + Aura DynamicFormatMode bar); five bladed "engines" marketed as separate products | `LyricReaderScreen.kt` mode pills vs `AuraFormatSelectorBar.kt:40-92`; AI Hub 8 card grid `LyricReaderScreen.kt:2184-2236` | **P1** |
| "Swiping my progress makes audio stutter" | Every seek/tick triggers an engine interaction: threshold spam at `CanvasWaveformVisualizer.kt:70-74`, RSVP `onWordSeek` per word at `KineticSprintReaderView.kt:63` | Un-throttled commands to the audio pipeline | **P1** |
| "It asks for a microphone permission I already denied and it still says recording" | `RECORD_AUDIO` never requested at runtime; record buttons flatten to a fake success captions | `AndroidManifest.xml` permission declared but no `requestPermissions`; clone flow decouples recording from upload | **P1** |
| "Deleting a document is scary — it just disappears" | Delete performs immediately with no confirm, no undo, no snackbar | `LibraryScreen.kt` delete handler | **P1** |
| "Library filters don't match what I pasted" | Filter categories (e.g. by source type) don't match the categories assigned at paste time | `LibraryScreen.kt` filter enum vs `PasteModal.kt` category enum mismatch | **P1** |
| "The developer dashboard thing is exposed to me" | TTS diagnostics/underflow log sheet is reachable from the main reader UI | `TtsDiagnosticDashboardModal.kt` reachable via reader toolbar | **P1** |
| "It loads forever" | No loading/error differentiation; a single "Loading lyrics…" state with no timeout | `LyricReaderScreen.kt:547-551` | **P2** |
| "Voice characters sheet is overwhelming and half the filters are empty" | 40+ characters with 7 emoji-labeled filter chips; tags are ad hoc strings; filters can legitimately return empty | `VoiceCharactersModalBottomSheet.kt:53,62-71`, `VoiceCharacterManager` | **P2** |

---

## 2. PART A — "Stuff doesn't work" (Liveness & Trust Audit)

The most damaging finding is not that features fail; it is that the **UI consistently reports success or active status that the backend does not honor**. Users reach for these features, watch them appear to work, and then discover nothing happened. Each item below is the UI claiming something the engine never does.

### 2.1 Engine status is a lie
- `SpeechEngine.kt:491-492` returns a status string like `"Masked AI Active"` for states that are not actually playing AI synthesis. The UI renders this string as the authoritative engine indicator. When no API key is configured (which is the shipped configuration — see `detailed.md` §4), the app silently falls back to Android TTS while the header still advertises an AI engine.
- **Fix direction:** Status must derive from the actual engine state machine (`EnginePlaybackState`), not a cosmetic string; surface "fallback mode" honestly.

### 2.2 Voice cloning is fake (two entry points, both fake)
- `InstantVoiceCloneUploader.kt:88,118`: `uploadVoiceClone()` never performs a network call; it constructs a `VoiceCharacter` from hardcoded fields — including the ElevenLabs voice ID of Rachel — and returns it as a success. The modal reports "Synthesizing voice profile..." (`InstantVoiceCloneModal.kt:242`) and then "Voice Profile … Created! Mapped to Room database & ready for playback." (`InstantVoiceCloneModal.kt:116,124`).
- The second entry point, `VoiceCloneModalDialog` (`VoiceCharactersModalBottomSheet.kt:414-568`), records to a local file and, if the user leaves the Voice ID blank, saves the **same hardcoded Rachel ID** (`:552`). The recording is never uploaded or analyzed.
- The user's 30-second recording is reduced to a caption ("✅ Sample Recorded (`file.size/1024` KB)", `:471`).
- **Fix direction:** Either implement real upload (ElevenLabs/Fish) with progress + failure states, or remove the feature. A fake clone is a P0 trust violation.

### 2.3 Fake TTS engines and fake "real-time" claims
- Engine names in the UI ("Sentient Articulation Engine — 5-Sentence Lookahead SLM & Diaphragm Protocol", `SentientArticulationModalBottomSheet.kt:87`; "MaskedD Biomechanical Engine — Zero-Data Throat Geometry & Ray-Traced Physics", `BiomechanicalVoiceModalBottomSheet.kt:84`) present speculative/plan-only subsystems as shipped products. See `detailed.md` §7 for the wire-level evidence (unused modules `SentientArticulationEngine`, `BiomechanicalVoiceEngine`, `ZeroDowntimeAudioRouter`, `PunctuationPacingEngine` are instantiated but never drive audio).
- "15+ Human Neural Characters • Real-time Switch Without Audio Pause" (`VoiceCharactersModalBottomSheet.kt:102`) claims gapless switching that the pipeline (`DynamicAudioPipelineLoop`) does not guarantee; switching calls `clearCache()` (`:221`) which by definition drops queued audio.
- **Fix direction:** Ship only what the audio path honors; rename or gate experimental engines behind a visible "Experimental" flag, or remove.

### 2.4 Dead controls inventory (UI renders, engine never consumes)

| Control | Where | Why it does nothing |
|---|---|---|
| Voice Fine-Tuning (4 sliders: pitch, rate, tone, energy) | `SettingsScreen.kt:242-245,333-357` | Local `mutableStateOf` only; no consumer in any engine |
| Sync offset field | `LyricReaderScreen.kt` (settings row) | `syncOffsetMs` is read by **zero** call sites |
| Engine status indicator | `SpeechEngine.kt:491-492` | Static string |
| "Masked AI" toggle (cloud vs fallback) | Reader toolbar | Selection is not propagated to engine selection |
| Remaining-time text | `LyricReaderScreen.kt:174` | Computed, never in composition |
| Sprint FAB "Play/Pause" | `KineticSprintReaderView.kt:250-261` | Toggles local `isRunning` only; does not pause global TTS |
| "Test Breath" | `SentientArticulationModalBottomSheet.kt:93-110` | Plays a canned sound clip; engine has no audio path |
| "Hydrate Throat" | `BiomechanicalVoiceModalBottomSheet.kt:90-103` | Adjusts a simulated percentage; no audio effect |
| Microphone record buttons | `InstantVoiceCloneModal.kt:193-211`, `VoiceCharactersModalBottomSheet.kt:492-516` | Recording is never consumed; `RECORD_AUDIO` never requested at runtime |
| Diagnostic "Verify State Rules" / "Check Buffer Health" | `TtsDiagnosticDashboardModal.kt:248-278` | Writes a log line; tests nothing |

**Pattern:** ~14 controls with zero backend effect. The correct remediation is honesty-first: either wire them or delete them. (Complete table also in `detailed.md` §18.2.)

### 2.5 The mandatory-update trap
- App versionCode = 1; `version.json` advertises versionCode 2 → the update sheet appears on first launch (`MainActivity.kt` fetch logic; `AppUpdateModal.kt`).
- "Later" button (`AppUpdateModal.kt:142-147`) calls a no-op and re-shows the sheet (`MainActivity.kt:82`), so the semi-modal cannot be dismissed; the user is trapped in a screen that will re-appear every session. Combined with installed-version < required, this *is* a forced update.
- **Fix direction:** Gate by minimum-required version, allow real "Remind me later" with a grace window, and never block for a version difference of 1 unless a breaking schema change exists.

### 2.6 Sleep timer: two of them
- Timer A at `LyricReaderScreen.kt:797` (user-facing, with countdown UI) and timer B at `LyricReaderScreen.kt:1024` (second scheduling path) are independent. Depending on which bottom bar the user used, the app may stop early, stop twice, or stop once and re-start.
- **Fix direction:** Single `SleepTimerController` in the ViewModel; UI subscribes; engine subscribes; one source of truth.

### 2.7 Deleting without consent
- Library and bookmark deletion (`LibraryScreen.kt` delete icon; `BookmarksScreen.kt:128` `deleteBookmark`) executes immediately. No confirmation dialog, no undo snackbar, no archive. Documents are irrecoverable.
- **Fix direction:** Undo snackbar (5s) with Room delete deferred; or confirm dialog for documents.

### 2.8 No differentiated loading/error states
- Reader shows a single "Loading lyrics…" placeholder (`LyricReaderScreen.kt:547-551`) with no timeout, no retry, no error copy. Parse failure, empty paste, and network stalls all present identically. Users describe this as "the app hung".
- **Fix direction:** Explicit `DocumentState { Loading, Ready, Empty, Error(message, retry) }`.

---

## 3. PART B — "The UI is annoying" (Irritants & Sensory Overload)

### 3.1 Aesthetic noise
- **Emoji as icons:** filter chips render as `"⭐ Favorites"`, `"🇳🇬 Nigerian / African"`, `"🧒 Kid & Youthful"`, `"🎙️ Classic Storytellers"`, `"📚 Academic & Bard"`, `"🧘 Lo-Fi & Sci-Fi"` (`VoiceCharactersModalBottomSheet.kt:53`). Emoji render inconsistently across OEM fonts, have no accessibility semantics, and set an amateur tone.
- **ALL-CAPS marketing headers:** `"⚡ KINETIC SPRINT ENGINE (RSVP)"` (`KineticSprintReaderView.kt:98`), `"BIOLOGICAL THROAT STATE"` (`BiomechanicalVoiceModalBottomSheet.kt:121`), all-caps labels throughout — shouting where the product should whisper.
- **Movie-poster subtitles** attached to every sheet ("5-Sentence Lookahead SLM & Diaphragm Protocol", "Zero-Data Throat Geometry & Ray-Traced Physics") — the same content as section 2.3; these read as parody rather than credibility with a general audience.
- **Inconsistent surfaces:** 12+ distinct background hex values across screens that are all "the dark theme" (`0xFF0F0B21`, `0xFF130E22`, `0xFF0F0C1B`, `0xFF0D1117`, `0xFF0F172A`, `0xFF181233`, `0xFF1E1938`, `0xFF13111C` …). Each sheet was built with its own palette; nothing shares a token.
- **Motion for its own sake:** pulsing record halo (`InstantVoiceCloneModal.kt:58-67`), springy sprint scaling (`KineticSprintReaderView.kt:84-88`), per-frame background gradients — motion communicates "progress" nowhere and "decoration" everywhere.

### 3.2 Forced dark theme
- `Theme.kt:38`: `darkTheme: Boolean = true` — "Default to sleek dark canvas". `MainActivity` never passes a value, so light mode is unreachable; a daylight-usage reading app is permanently dark.
- The broken "Warm Parchment" preset (`Color.kt:51-58`) is worse: cream background + hardcoded `Color.White` text (`LyricReaderScreen.kt:262-391`) = ~1.1:1 contrast, illegible.
- **Fix direction:** Wire `isSystemInDarkTheme()`; audit all hardcoded `Color.White` usages against theme presets; contrast-test presets (≥4.5:1 body).

### 3.3 Icon-only controls with no labels
- Reader toolbar and bottom bars rely on Material icons with `contentDescription` mostly set to `null` (e.g. `AuraFormatSelectorBar.kt:65-74` labels are inside the pill, fine, but toolbars across `LyricReaderScreen.kt` use icon-only buttons). Users can't map icons to features; there is no first-run education anywhere in the app.
- **Fix direction:** Labeled icon buttons or long-press hints on first run; a 3-step onboarding.

### 3.4 Typography and visual hierarchy
- The app mixes `labelSmall` (11sp) headers with `titleMedium` body and `42sp` focal characters (`KineticSprintReaderView.kt:149`); the same visual weight is used for marketing headers and functional labels. There is no type scale discipline (see `Type.kt` — seven sizes but screens override them inline everywhere, e.g. `fontSize = 13.sp` spread across ~40 call sites).

### 3.5 The "two of everything" problem (separate from 2.6)
- **Two format-mode systems:** reader "mode" pills vs Aura `DynamicFormatMode` bar (`AuraFormatSelectorBar.kt:40-92`: Deep/Podcast/Sprint/Script). The two are mutually unaware; users discover duplicate controls appearing in different places.
- **Two clone dialogs:** `InstantVoiceCloneModal.kt` (from AI Hub) and `VoiceCloneModalDialog` (inside `VoiceCharactersModalBottomSheet.kt`) — the same promise, two implementations, both fake (2.2).
- **Two engine status UIs:** the reader header pill and the TTS Diagnostics dashboard (which is the only truthful surface, and it's buried in developer telemetry).
- **Fix direction:** one control per concept; delete the loser (see Part D).

### 3.6 Exposing developer telemetry to consumers
- `TtsDiagnosticDashboardModal.kt` (log stream, buffer underflow counters, state-machine test buttons) is reachable from the main reading screen. A consumer sees "Blocked Illegal: 14" and "Avg Sync Offset: ±52ms" and can press buttons that do nothing. That surface belongs in a debug-only build via `BuildConfig.DEBUG` gate.

### 3.7 Android-specific friction
- `RECORD_AUDIO` is declared in the manifest but never requested at runtime; the mic buttons therefore lead to a silent permission dead-end on Android 12+ (the system denies; the app never asks), which users experience as "recording just sits there".
- Scrolling feel: reader uses a `verticalScroll`-based lyric canvas; fine, but the word-position slider (`KineticSprintReaderView.kt:185-197`) jumps the document position with zero debounce (2.4 threshold spam) — with audio playing this produces audible stutter.
- App label and package mismatch: launcher label "MaskedD", package `com.aistudio.lyricread.dhehbq`; the stale `shortcuts.xml` still references a different package (`com.aistudio.lyricread.oxxfzc`) — shortcut clicks can fail on some launchers.

---

## 4. PART C — "It's complicated" (Cognitive-Load & Information-Architecture Audit)

### 4.1 The AI Hub is a junk drawer
- From the reader, the AI Hub (`LyricReaderScreen.kt:2184-2236`) presents 8 feature cards: Socratic Tutor, Podcast Debate, Music Aura, Sentient Articulation, Biomechanical Voice, Instant Voice Clone, Voice Lab, Diagnostics. These are **modal sheets stacked on the reading screen** — not flows with context. Users report they "press a card and nothing obvious happens" because several sheets open onto obscure configuration UI with no relationship to the current document.
- **3-level nesting:** Reader → AI Hub → card → bottom sheet → (sometimes) dialog. Example: Voice Characters → Clone Voice → AlertDialog with recorder. Each level adds a new title and new marketing copy.
- Socratic/Quiz/Podcast features share a single rendering box (see `detailed.md` §18.1); enabling "Socratic mode" changes little visibly, reinforcing the "nothing works" complaint even when the underlying LLM path is fine.

### 4.2 Five named "engines" one user cannot distinguish
Sentient Articulation, Biomechanical, Kinetic Sprint, Dynamic Format, Zero-Downtime Router — each has its own sheet, its own palette, its own header copy. To a user these are five buttons with no mental model. The honest single feature set is *read, listen, adjust voice, adjust speed.* Everything else is internal architecture leaking into the UI.
- **Fix direction:** Collapse marketing engine names into user features; keep engineering names in code only.

### 4.3 Library filters vs Paste categories mismatch
- `LibraryScreen.kt` shows filter chips derived from one enum set; `PasteModal.kt` assigns documents a different category set at creation. Result: filters can return empty lists or omit documents the user just created; no empty-state copy exists for filtered results (the "No documents" state exists only for the totally-empty library).
- **Fix direction:** single shared `DocumentCategory` enum; filter states get explicit empty/result copy.

### 4.4 Settings sprawl
- `SettingsScreen.kt` mixes: playback prefs, four dead Fine-Tuning sliders, "dev tools" that open diagnostic surfaces, and WHAT'S-NEW/hype copy — with no grouping headers. ~30 controls, no search, no defaults reset, no descriptions for half the items.

### 4.5 Empty states and copywriting
- Most empty states are serviceable ("No saved bookmarks yet" + instructive line, `BookmarksScreen.kt:88-97`), but they're the exception. Voice-character filtered search has copy; filtered library, settings explanations, and AI Hub card descriptions largely do not.
- Error copy is developer-speak: "Upload failed: ${e.message}" (`InstantVoiceCloneModal.kt:255`), "Some or all values are invalid" style Room failures, raw `engineState.name` strings (`TtsDiagnosticDashboardModal.kt:177`).

### 4.6 Primary action ambiguity
- On the reader there are **three** play/resume affordances that can conflict: the main FAB, the mini transport in any exposed bar, and mode-local "play" (Sprint FAB, 3.5). With two timers (2.6) and icon-only controls (3.3), "how do I make it read to me" gets answered three ways.

---

## 5. PART D — Full Flow Walkthroughs (dead ends measured)

Each flow below was walked through the real code. Symbols: `✓ works end-to-end`, `✗ dead end`, `!` risk found.

### 5.1 Flow: Onboarding → Update wall
1. Launch → `MainActivity` fetches `version.json` (versionCode 2) → comparison with installed versionCode 1 → force sheet.
2. "Later" (`AppUpdateModal.kt`) → no-op → sheet re-shows next launch (`MainActivity.kt:82`).
3. **Result:** ✗ First-time users may never reach the app. This is the highest-impact UX bug in the product; it also damages store ratings.

### 5.2 Flow: Create a document
1. Choose "Paste" → `PasteModal.kt` → text + category → save → Room insert → navigates to reader.
2. Reader immediately shows "Loading lyrics…" (`LyricReaderScreen.kt:547-551`); parse is synchronous and fast, so this fl* flickers — but if parse fails (empty paste, unsupported format), the app **stays** on "Loading lyrics…" forever with no error.
3. **Result:** ! Creation works; failure handling is missing; no retry path.

### 5.3 Flow: Find & open a saved document
1. Library → list renders from Room (`LibraryScreen.kt`).
2. Filter chips use a category set that may not match paste-time categories (4.3) → documents "disappear" from filtered views.
3. Delete has no confirm/undo (2.7).
4. **Result:** ✗ partially — browsing fine, filtering and deletion are trust-breaking.

### 5.4 Flow: Play with AI voice
1. Press play → `SpeechEngine` initializes with configured provider. With the shipped `.env` keys, cloud API keys are **never injected** into `BuildConfig` (Secrets plugin missing fields — `detailed.md` §4), so Gemini/Fish/ElevenLabs calls fail or are skipped.
2. Status header still reports AI active (2.1). Fallback plays via Android TTS.
3. **Result:** ✗ "AI voices" are a facade on this build; user hears TTS while the UI promises neural AI. The single biggest "stuff doesn't work" generator.

### 5.5 Flow: Switch voice character
1. Open Voice Characters (`VoiceCharactersModalBottomSheet.kt`) → select → `selectCharacter` + `clearCache()`.
2. **Result:** ✓ works structurally IF a real voice backend is configured; the "real-time seamless switching" claim overstates `clearCache()` behavior (2.3); absent keys, all voices are Android TTS with different names. Audition button: `auditionVoiceSample` will fail silently without keys — the `isAuditioning` state returns via callback even on failure, with no error UI of its own.

### 5.6 Flow: Clone my voice
1. `InstantVoiceCloneModal.kt` → record 30s (RECORD_AUDIO not requested at runtime — 2.4/3.7) → "Synthesizing voice profile..." → success screen "Mapped to Room database & ready for playback."
2. The uploader never uploads; it returns a hardcoded Rachel-backed character (2.2).
3. **Result:** ✗ Complete fabrication. A P0 integrity issue.

### 5.7 Flow: Sprint reading
1. Aura bar → Sprint → `KineticSprintReaderView`.
2. FAB toggles local `isRunning` only; it does not pause TTS (2.4). The view fires `onWordSeek` per word (~170ms at 350 WPM default, `:60-64,63`), which spams the engine's seek path — audible stutter and, with a cloud engine, a new synthesis per word.
3. Slider seek likewise fires without debounce (`:185-197`).
4. **Result:** ✗ Sprint does not integrate with playback; it's a slide-show running in parallel, plus it degrades the audio pipeline.

### 5.8 Flow: Sleep timer + pause → resume
1. Set timer (UI countdown at `:797`) → playback stops via timer A.
2. Resume → timer B (`:1024`) may or may not still be running → conflicting stop behavior (2.6).
3. **Result:** ✗ Unpredictable stop times; occasional immediate double-stop after resume.

### 5.9 Flow: Update the app "properly"
1. Play Store listing is... whatever is current; the in-app wall (5.1) is the trap. The `shortcuts.xml` stale-package issue (3.7) sits on top.
2. **Result:** ✗ The update force-wall remains the first experience.

---

## 6. PART E — Screen-by-Screen Findings Register

### 6.1 `LyricReaderScreen.kt` (the core screen)
- `:174` — computed `timeRemainingText` never composed → users rely on a different, engine-anchored counter that drifts under buffering.
- `:262-391` — theme preset application hardcodes `Color.White` for text/active pills → breaks `PAPER_PARCHMENT` (2.1/3.2) and any future light preset.
- `:547-551` — single loading state, no error path (5.2).
- `:797` / `:1024` — duplicate sleep timers (2.6).
- `:2184-2236` — AI Hub 8-card grid with mixed scientific/marketing copy (4.1).
- Reader toolbar: icon-only, `contentDescription=null` in places; no onboarding (3.3).

### 6.2 `SettingsScreen.kt`
- `:242-245`, `:333-357` — dead Voice Fine-Tuning sliders (2.4).
- Un-grouped 30+ item sprawl, no reset-defaults (4.4).
- Dev/diagnostics links leaking into consumer settings (3.6).

### 6.3 `LibraryScreen.kt`
- Filters vs paste categories mismatch (4.3).
- No filtered-empty state; delete without confirm/undo (2.7).

### 6.4 `PasteModal.kt`
- Works; category enum must be unified with Library (4.3); no paste-size validation messaging beyond keyboard caps.

### 6.5 `AiVoiceLabModalBottomSheet.kt`
- Junk-drawer hub (4.1); its cards duplicate controls reachable elsewhere (Sprint, Voices, Clone).

### 6.6 `VoiceCharactersModalBottomSheet.kt`
- `:53` — 7 emoji filter categories with ad hoc tag matching (`:62-71`) — fragile, can return empty.
- `:102` — overstated "Real-time Switch Without Audio Pause".
- `:344` — audition text fine; failure path silent (`:344-346`).
- `:552` — hardcoded Rachel fallback on clone save (2.2).

### 6.7 `InstantVoiceCloneModal.kt`
- P0: fake success flow (2.2); Pulse animation without functional progress (`:58-67`); `RECORD_AUDIO` never asked (3.7).

### 6.8 `KineticSprintReaderView.kt`
- Parallel playback clock (2.4/5.7); LABEL header (`:98`); designer WPM default 350 with no presets; un-throttled word seek spam.

### 6.9 `SentientArticulationModalBottomSheet.kt` / `BiomechanicalVoiceModalBottomSheet.kt`
- Marketing-first engineering sheets; "Test Breath"/"Hydrate Throat" buttons adjust state that no audio path honors (2.4); two more distinct background palettes (3.1).

### 6.10 `TtsDiagnosticDashboardModal.kt`
- Consumer-visible developer telemetry with do-nothing test buttons (3.6).

### 6.11 `BookmarksScreen.kt`
- Delete without confirm (2.7); jump works; no swipe-to-delete niceties (P2).

### 6.12 `AppUpdateModal.kt` / `MainActivity.kt`
- Force-update trap (2.5); `MainActivity.kt:82` no-op "Later".

### 6.13 Theme / Type / Color
- `Theme.kt:38` — dark default hardcoded (3.2).
- `Color.kt:51-58` — `PAPER_PARCHMENT` light preset that the reader then contradicts with white text (contrast ≈1.1:1).
- `Type.kt` — inline font-size overrides throughout screens undermine the scale (3.4).

### 6.14 Android integration
- `RECORD_AUDIO` never requested (3.7); stale `shortcuts.xml` package (3.7); app label/package mismatch; forced-dark ignores `uiMode` best practice.

---

## 7. PART F — Claims vs Reality Register (what the UI text promises)

| UI text (file:line) | Reality |
|---|---|
| "Masked AI Active" (`SpeechEngine.kt:491-492`) | No AI engine active on shipped config; TTS fallback |
| "Instant 30s Voice Clone … synthesize" (`InstantVoiceCloneModal.kt:85,94,242`) | No upload; hardcoded profile |
| "Mapped to Room database & ready for playback" (`InstantVoiceCloneModal.kt:124`) | Mapped to Rachel, not user's voice |
| "Real-time Switch Without Audio Pause" (`VoiceCharactersModalBottomSheet.kt:102`) | `clearCache()` drops queued audio (`:221`) |
| "Sentient Articulation … Diaphragm Protocol" (`SentientArticulationModalBottomSheet.kt:87`) | No audio path (`detailed.md` §7) |
| "Zero-Data Throat Geometry & Ray-Traced Physics" (`BiomechanicalVoiceModalBottomSheet.kt:84`) | Simulated state only |
| "⚡ KINETIC SPRINT ENGINE" (`KineticSprintReaderView.kt:98`) | Slide show; no TTS integration |
| "Verify State Rules" / "Check Buffer Health" (`TtsDiagnosticDashboardModal.kt:262,276`) | Log-writes only |
| Mandatory update "Later" (`AppUpdateModal.kt:142-147`) | No-op; sheet returns |
| Fine-Tuning sliders (`SettingsScreen.kt:333-357`) | Not consumed anywhere |
| "✅ 30s Recording Captured!" (`InstantVoiceCloneModal.kt:216`) | File saved; never uploaded |

---

## 8. PART G — Accessibility & Localization (baseline pass)

- **Color contrast:** forced-dark with white on purple gradients is generally ≥4.5:1, but `PAPER_PARCHMENT` breaks it badly (3.2); gray-on-gray labels everywhere (`color = Color.White.copy(alpha = 0.3f)` on dark) fall below 3:1 for secondary text.
- **Touch targets:** multiple targets are 28–40dp (`VoiceCharactersModalBottomSheet.kt:362` favorite = 28dp) vs 48dp minimum; chips at 8dp vertical padding are borderline.
- **Semantics:** emoji-as-icons expose no `contentDescription`; emoji flag "labels" are screen-reader garbage; several `Icons` pass `contentDescription = null` for meaningful buttons (search icon at `VoiceCharactersModalBottomSheet.kt:135` is decorative-only while the field is labeled, acceptable; but toolbar actions are not).
- **Tap-to-scan area:** word slider + sprint slider both respond to the full track; acceptable.
- **i18n:** all copy is hardcoded English; no `strings.xml` resource usage beyond app name; locale-aware formatting absent (e.g. "±52ms" strings built inline: `TtsDiagnosticDashboardModal.kt:232`).
- **Reduced motion:** no respect for `MotionDurationScale`/animator-duration-scale; pulsing and bouncy springs run at full tilt (3.1).
- **Landscape/tablet:** linear `Column(fillMaxSize)` layouts assume portrait phone; no resource qualifier layouts exist.

---

## 9. PART H — Prioritized Remediation Plan

### Phase 0 — Stop lying (P0; ~2–3 weeks single dev)
1. **Kill the update wall:** minimum-required-version gating + real "Remind me later" with grace window; versionCode published consistently. (`MainActivity.kt`, `AppUpdateModal.kt`)
2. **Honest engine status:** status string derived from `EnginePlaybackState`; visible "Fallback TTS mode" chip when no provider key is present. (`SpeechEngine.kt`, reader header)
3. **Remove or implement voice cloning.** Deleting both clone UI paths is perfectly acceptable until a real backend exists. (`InstantVoiceCloneModal.kt`, `VoiceCharactersModalBottomSheet.kt:414-568`, `InstantVoiceCloneUploader.kt`)
4. **Delete dead controls:** Voice Fine-Tuning sliders, sync offset field, phantom toggles — or wire them. (`SettingsScreen.kt`, reader)
5. **Fix Warm Parchment:** reader text colors must come from the preset, not `Color.White`. (`LyricReaderScreen.kt:262-391`)
6. **One sleep timer.** (`LyricReaderScreen.kt:797/:1024`)
7. **Delete-before-destroy:** confirm or undo-snackbar for documents & bookmarks. (`LibraryScreen.kt`, `BookmarksScreen.kt`)

### Phase 1 — Reduce complexity (P1; ~2–3 weeks)
8. **Unify mode selection:** one concept — either ReadingMode pills or Aura bar, not both; the mode renders one view (close the Sprint/`KineticSprintReaderView` gap with real TTS word-stepping or remove it).
9. **AI Hub restructure:** 3–4 real features visible (Voice, Speed/Style, Bookmarks+Timer is not a hub item — relocate), experimental engines moved to a debug-only drawer.
10. **Shared single `DocumentCategory` enum** between Paste and Library; explicit empty-state copy for filters.
11. **Request `RECORD_AUDIO` properly** (if cloning stays) with rationale strings.
12. **Gate diagnostics** behind `BuildConfig.DEBUG`. (`TtsDiagnosticDashboardModal.kt`)
13. **Wire light theme** to `isSystemInDarkTheme()`; run a contrast pass on gray-on-dark text; audit all hardcoded surfaces into theme tokens.
14. **Throttle seeks:** debounce word-seek/slider commands ≥150ms and coalesce per-frame. (`CanvasWaveformVisualizer.kt:70-74`, `KineticSprintReaderView.kt:63`)

### Phase 2 — Polish (P2; ongoing)
15. Replace emoji chips with real icons+text; adopt the type scale (kill inline `fontSize` overrides); consistent sheet palette tokens.
16. Differentiated document states (Loading/Ready/Empty/Error+Retry).
17. Accessibility pass: 48dp targets, contentDescriptions, reduced-motion, `strings.xml`, RTL/locale sanitation; fix `shortcuts.xml` package.
18. Empty-state copy across library filters, voices search, AI Hub; plain-language error messages.
19. First-run onboarding (3 steps max): import text → pick a voice → press play.
20. Visual regression harness over the design system (the eventual HeyTek standard applies here from day one).

---

## 10. Appendix — Severity Distribution & Metric Targets

- **Counts (this audit):** 15 phantom/dead controls; 9 liveness lies in UI copy; 6 P0 issues; 8 P1 issues; 9 P2 issues.
- **Target metrics after Phase 0:** 0 claims without backend effect; update-wall success rate 100% (user reaches app); clone feature either real or absent; contrast ≥4.5:1 body text; sleep-timer double-fire = 0.
- **Suggested guardrails:** a lint rule banning `contentDescription = null` on clickable icons; a test asserting every Settings toggle has ≥1 consumer; an integration test that mimics "no API keys" and asserts the UI says fallback honestly.