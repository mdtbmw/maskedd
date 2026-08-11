# MaskedD "Remix" — End-to-End Enterprise-Grade Production Audit

**Audit target:** `C:\Users\USER\Downloads\remix-maskedd-by-daniel`
**Product:** MaskedD — "Expressive document TTS reader with karaoke word sync, custom voice styles, and multi-format document parser" (Google AI Studio "Remix" export, `metadata.json`)
**Stack (actual):** Android native — Kotlin 2.2.10, Jetpack Compose (Material 3), Room 2.7.0 (SQLite), Retrofit/OkHttp/Moshi, Firebase AI/AppCheck (unconfigured), AGP 9.1.1, minSdk 24 / targetSdk 36, Gradle 8.11.1/9.3.1, Secrets Gradle Plugin 2.0.1
**Audit date:** 2026-08-11
**Method:** Full source read of all 60+ Kotlin files (~15,000 LOC), manifest, Gradle config, resources, tests, committed artifacts, and both Python scripts. Verification greps performed for call sites, permissions, hardcoded strings, and secrets. All file references are exact and verified.
**Auditor verdict:** **NOT PRODUCTION-READY — 11 CRITICAL, 24 HIGH, 30+ MEDIUM findings.** The app has genuinely impressive surface area (karaoke word sync, multi-engine TTS, floating orb, voice cloning, AI features) but the engineering below the surface is a demoware scaffold: multiple claim-then-omit subsystems, silently dead cloud integration, shipping-theft permissions, fake failure handling, and a first-launch self-update trap that blocks the app.

---

## Table of Contents

1. Executive Summary & Scorecard
2. System Portrait (what this actually is)
3. Launch-Blocking CRITICAL Findings
4. Security Audit (Keys, Data, Permissions, Network)
5. Data & Persistence Audit
6. TTS / Audio Engine Audit
7. AI Subsystem Audit
8. Services & Background Processing Audit
9. UI / UX Audit
10. Build, Release & Configuration Audit
11. Testing Audit
12. Performance & Memory Audit
13. Privacy & Compliance Audit
14. Code-Quality & Maintainability Audit
15. Gap Analysis vs. Production/Enterprise Standard
16. Prioritized Remediation Roadmap
17. Appendix: Complete Findings Register

---

## 1. Executive Summary & Scorecard

MaskedD is a Google-AI-Studio-generated Android "Remix" of a document TTS reader. It is a *concept car*: the bodywork (UI, feature list, marketing copy) is elaborate, but the drivetrain is partially missing, partially disconnected, and partially dishonest. Specifically:

- **The core promise — "zero-latency AI neural voice with lookahead buffering" — cannot authenticate.** The Secrets plugin never generates the `BuildConfig` key fields the code reads via reflection (missing `properties { }` block, `.env` name mismatch `FISH_AUDIO_API_KEYS` vs `FISH_AUDIO_API_KEY`), so every cloud TTS / Gemini / ElevenLabs call runs with an **empty key** and silently auto-falls-back. The premium "Masked AI Voice" engine is colloquially dead-on-arrival; what users actually hear is Android's stock `TextToSpeech`.
- **Three of the four marketed engines are fabrications or dead modules**: "Kokoro Offline Neural" is rebranded system TTS with a false ONNX claim; "FireRedTTS-2 / LongCat-AudioDiT" point at endpoint URLs that do not exist while sending the **Gemini** key as a Bearer token; the "Zero-Downtime Audio Router" is never invoked anywhere.
- **Real API keys are committed in the repository** (`.env` and `.env.example`), one of which matches the `version.json` OTA host's repo — treat all as **public/compromised**.
- **The app soft-locks on first launch**: committed `version.json` declares `versionCode: 2, isMandatory: true` while the app builds `versionCode: 1`; the update dialog's "Later" button and outside-dismiss are wired to a no-op, so the first run of v1.0 presents a **non-dismissible mandatory update modal**.
- **The data layer wipes itself**: `fallbackToDestructiveMigration(dropAllTables = true)` silently destroys the entire library on any Room schema change; Room schema export is off; bookmarks and voice-clone audio files are orphaned by design.
- **Voice cloning lies about success**: the recording is uploaded to ElevenLabs (privacy issue), and on *any* failure — including the guaranteed empty-key failure — the app persists a "clone" mapped to stock ElevenLabs Rachel and reports Success.
- **Record-Audio permission is never requested** at runtime; RECORD_AUDIO, POST_NOTIFICATIONS, SYSTEM_ALERT_WINDOW, and REQUEST_INSTALL_PACKAGES are declared but unrequested/unhandled.
- **Two of four tests are guaranteed red** on trunk (stale constant assertions); zero tests cover actual logic; the release build ships unminified, un-obfuscated, and silently **debug-signed** when env vars are absent.
- An entire layer of "sentient/biomechanical/respiratory" audio DSP is **cosmetic math that nothing consumes**, and the one piece of real DSP — a noise gate — **corrupts compressed MP3 bytes** instead of decoding first.

### Scorecard (0–10)

| Dimension | Score | Summary |
|---|---|---|
| Feature breadth & product vision | 8.5 | Genuinely ambitious; karaoke sync, multi-format parsing, clone flow, orb, AI features |
| Functional correctness | 3 | Core cloud engine dead; multiple dead UIs; two guaranteed-red tests |
| Data integrity | 2 | Destructive migrations, orphaned bookmarks/files, no schema export |
| Security | 1.5 | Committed live keys, key in URL query string, over-broad FileProvider, coerced sideload install |
| Privacy & consent | 2 | Undisclosed voice uploads, per-sentence document text sent to Gemini, no consent/what's-new UI |
| Performance | 4 | 60fps word-sync loop, per-frame seeks while scrubbing, Gemini call before cache hit, 800ms throttle stalls |
| Resilience & error handling | 1.5 | Silent fake success everywhere; no error/retry state in any screen |
| Test coverage | 0.5 | 4 tests; 2 red; nothing meaningful asserted |
| Release hygiene | 1 | Committed `build/`, `.gradle/`, `.kotlin/`, debug-signing fallback, minify off |
| Accessibility & i18n | 1.5 | 2–3dp touch targets, hardcoded white-on-light text, zero strings.xml usage, no localization |
| **Overall** | **2.5 / 10** | **Safe only for internal demos; must not ship to users in this state** |

---

## 2. System Portrait

**Application entry:** `MainActivity.kt` (single activity; 4 destinations — Library, LyricReader, Bookmarks, Settings — via Navigation Compose; plus modals).

**Architecture (nominal):**
- `MainActivity` → `LyricReaderViewModel` (AndroidViewModel) → `SpeechEngine` (owns native TTS + 4 "engines") → Room `AppDatabase` (documents/bookmarks/cloned voices/audio cache) → `DocumentParser` (TXT/MD/PDF/DOCX/EPUB) → cloud APIs (Gemini, ElevenLabs, Google TTS, Fish.audio) through `ApiKeyManager` key-rotation pools.
- Two foreground services: `TtsPlaybackService` (MediaSession + notification controls) and `CognitiveOrbService` (SYSTEM_ALERT_WINDOW floating widget).
- `AppUpdateManager` self-OTA (DownloadManager + FileProvider + installer intent).

**Tokenization pipeline:** `DocumentParser.processText` splits on `.`/`!`/`?`/newline with abbreviation guards, builds word/sentence token maps with char offsets ("karaoke" sync), then `SpeechEngine` maps Android `UtteranceProgressListener.onRangeStart` char ranges proportionally into the original text. In practice the mapping is heuristic (proportional ratio from `spokenText`), so **word highlight drifts noticeably on normalized text** — the karaoke sync is approximate, not aligned.

**What actually runs vs. what is claimed** (verified by call-graph grep):

| Claimed | Reality |
|---|---|
| "Masked AI Voice with lookahead streaming" | `CloudVoiceSynthesizer` IS wired, but authentication is broken → always falls back to device TTS (or 800ms-stalled failures) |
| "100% offline Kokoro neural model (ONNX)" | System `TextToSpeech` wrapping, no model assets |
| "FireRedTTS-2 & LongCat-AudioDiT" | `FireRedLongCatSynthesizer` — fabricated endpoints, Gemini key as bearer; never produces audio |
| "Zero-Downtime Audio Router" | `ZeroDowntimeAudioRouter` constructed at `SpeechEngine.kt:112`, **never called** |
| "Biomechanical voice physics simulation" | `BiomechanicalVoiceEngine` — 3 arithmetic formulas; outputs consumed by nothing |
| "Sentient articulation SLM lookahead (5 sentences)" | Keyword `contains()` classifier; blueprints displayed only in a debug sheet; nothing consumes them |
| "Respiratory synthesis / non-lexical artifacts [sigh] [chuckle]" | Metadata + UI sphere only; inconsistent noise; would inject literal bracket tags if that dead path were ever wired |
| "Smooth crossfading ambient tracks / binaural beats" | Instant track swap (fade var never read); mono amplitude-modulated tone marketed as stereo binaural |
| "16+ human neural characters" | Hardcoded list of public ElevenLabs premade voice IDs — several characters share the *same* voice ID |
| "Adaptive noise gate (dynamic noise-floor gating)" | Multiplies **compressed MP3 bytes** by a cosine window (corrupts frames) |
| "In-app OTA updates" | Real mechanism, but broken logic: version trap, dead dismiss, fake progress |

The practical result: **the app is a (well-decorated) stock-TTS reader whose entire AI/neural stack is non-functional or invisible.**

---

## 3. Launch-Blocking CRITICAL Findings

**C-1. First-launch mandatory-update soft lock.**
`version.json` (committed at repo root, and served from `raw.githubusercontent.com/aistudio/lyricread/main/version.json` — `AppUpdateManager.kt:198`) declares `{"versionCode": 2, "isMandatory": true}`; the app builds `versionCode = 1` (`app/build.gradle.kts:20`). `MainActivity.onCreate` dials the server immediately (`MainActivity.kt:54`), sees `hasUpdate = true`, and shows `AppUpdateModal`. In `AppUpdateModal.kt:142-147` the non-mandatory "Later" path exists, but `MainActivity.kt:82` passes `onDismiss = { }` — a **no-op** — and the modal does not dismiss on outside click in mandatory mode (`AppUpdateModal.kt:52-56`). Result: **every first install of v1.0 is trapped in a mandatory update dialog that cannot be dismissed**, and pressing "Update" downloads an APK whose release variant may be debug-signed (F-13). Any change to `version.json` at that URL instantly breaks every user.

**C-2. Cloud AI/TTS stack authenticates with empty keys — silently.**
`ApiKeyManager.kt:57-73` reads `BuildConfig.FISH_AUDIO_API_KEY` / `ELEVENLABS_API_KEY` / `GEMINI_API_KEY` via reflection. The Secrets plugin 2.0.1 (`gradle/libs.versions.toml:40`) requires an explicit `secrets { properties { ... } }` documentation block to generate BuildConfig fields (`app/build.gradle.kts:68-72` has none), the `.env` keys are named `FISH_AUDIO_API_KEYS` (plural) while code reads `FISH_AUDIO_API_KEY` (singular), and `defaultGeminiKeys` additionally filters the sentinel `"DEFAULT_API_KEY"`. The `getField` reflection throws → caught → **pools forever empty**. Every cloud TTS sentence then either throws `IllegalStateException("All neural voice API candidates exhausted...")` (→ fallback to stock TTS) or, in the ElevenLabs path, retries the empty key 3× then throws. The "Masked AI Voice" premium experience is therefore **never delivered**, and no user-facing signal explains why. (Also: even when keys exist, they are baked into `BuildConfig` — extractable from the APK.)

**C-3. Live secrets committed in the repository.**
`.env` and `.env.example` contain what appear to be **real, unredacted production API keys**: a Gemini `AIzaSy…` REST key, a Fish.audio `sk-fish-…` key, and an ElevenLabs `sk_…` key. They are inside the project folder and, given `version.json` references the same GitHub account's raw-hosted file, must be treated as **compromised/leaked**. Rotation is mandatory before any real launch; they must never appear in VCS (see S-1). This document intentionally omits the full values.

**C-4. Destructive database migrations wipe the user's library.**
`AppDatabase.kt:26` — `.fallbackToDestructiveMigration(dropAllTables = true)` with `exportSchema = false` (`AppDatabase.kt:8`) means any schema evolution (DB is already at version 3) silently deletes documents, bookmarks, cloned-voice rows, and the audio cache index. Completely silent user-data loss; unrecoverable; orphaned voice files remain on disk. Irreversible-loss defect of the first order.

**C-5. Voice-clone flow is permission-less, uploads audio, and fakes success.**
(a) `RECORD_AUDIO` is declared (`AndroidManifest.xml:6`) but **no runtime request exists anywhere** (verified: zero `RequestPermission` call sites) — `MediaRecorder` immediately throws `SecurityException` on device; `VoiceRecorder.kt:69-73` swallows it and returns null; both clone modals (`InstantVoiceCloneModal.kt:193-210`, `VoiceCharactersModalBottomSheet.kt:492-516`) show no prompt, no error. (b) When a recording *does* exist, `InstantVoiceCloneUploader.kt:65` uploads up to 30s of the user's voice to `https://api.elevenlabs.io/v1/voices/add` with no consent disclosure of any kind. (c) **Every failure path** — network error (line 83), non-2xx (line 75), empty key — falls through to `finalVoiceId = "21m00Tcm4TlvDq8ikWAM"` (Rachel), persists a `ClonedVoiceEntity`, and returns `Success` (`InstantVoiceCloneUploader.kt:88,118`). The user is told their voice was cloned when they got stock Rachel. Deceptive-by-design failure handling.

**C-6. Over-permissioned manifest + coerced sideloading.**
`SYSTEM_ALERT_WINDOW` (floating orb — visible on Google Play review), `REQUEST_INSTALL_PACKAGES` (self-update APK install — red flag AND a Play-policy violation; install-from-unknown-sources flow is unhandled), `file_paths.xml:4` exposes the **entire external storage** root via `<external-path path="."/>`, `POST_NOTIFICATIONS` never requested on API 33+, `allowBackup="true"` with boilerplate rules, and `CognitiveOrbService.kt:99` checks-but-never-requests `canDrawOverlays`. Combined with C-1, the app has a remote-triggerable path to prompting a package install — a class of behavior commonly flagged as malware-adjacent by app stores.

---

## 4. Security Audit

### S-1. Secret management — FAIL
- Committed keys (C-3); `.env.example` itself contains **real values**, not placeholders — anyone cloning the repo gets working credentials for three paid APIs, and the keys are now revocable/financial liability.
- Keys delivered via `BuildConfig` ⇒ extractable from APK with `apkanalyzer`/strings dump. For a client-side-use model this is unavoidable *if* the app is trusted with the keys and abuse-limited — but there is no server-side proxy, no Firebase AppCheck enforcement (AppCheck deps present & unconfigured, `googleServices.missing.passthrough=true`), so a scraper can harvest the keys from any installed APK and burn the founder's quota.
- `ApiKeyManager` rotates keys and rate-limits to 60 req/min (`ApiKeyManager.kt:250-271`) — but the rate limiter is *only* consulted on the cloud-TTS path (`CloudVoiceSynthesizer.kt:191`), never on the Gemini text paths (`GeminiTextPreprocessor`) or the voice-clone upload.

### S-2. Transport & key exposure
- `GeminiEmotionalToneAnalyzer.kt:44` puts the API key in the URL **query string** (`?key=$apiKey`) — leaked to OS, proxies, and server access logs. Must use the `x-goog-api-key` header (client-side keys are still sent via `&key=` by the official Gemini REST SDK for generateContent; text-to-speech synthesizes accepts `X-Goog-Api-Key` — parity requires the header).
- `FireRedLongCatSynthesizer.kt:62` sends the **Gemini** key as `Authorization: Bearer` to `api.firered.ai` / `api.longcat.ai` (fabricated hosts) — credentials routed to unintended/unknown servers.
- No TLS pinning anywhere; `AppUpdateManager` runs **no signature verification** over the downloaded APK (no hash comparison, no signature scheme check, plain `HttpURLConnection` — even HTTP redirects accepted) before throwing it at the PackageInstaller. A MITM or compromised raw.githubusercontent host can install a malicious app **that self-updates the app's own permissions**. This is the single most dangerous security posture item in the codebase.

### S-3. Permission model — FAIL
| Permission | Declared | Runtime-requested | Needed for |
|---|---|---|---|
| `RECORD_AUDIO` | ✓ | ✗ (none anywhere) | voice cloning (feature is broken on device) |
| `POST_NOTIFICATIONS` | ✓ | ✗ | FGS notification on API 33+ |
| `SYSTEM_ALERT_WINDOW` | ✓ | ✗ (check only, `CognitiveOrbService.kt:99`) | floating orb |
| `REQUEST_INSTALL_PACKAGES` | ✓ | n/a — install flow never handled | self-update |

Each one is a store-review liability and an unhandled-user-experience defect.

### S-4. File exposure
- `file_paths.xml:4` grants the whole `/storage/emulated/0` tree to `FileProvider`. The install flow (`AppUpdateManager.kt:176-182`) then grants *read* URI permission to the installer — narrow in practice, but the provider root is far broader than any legitimate need (should be `<cache-path>` / `<external-files-path>` only).
- Cloned-voice recordings live in `context.cacheDir` (`VoiceRecorder.kt:31`) — user audio in an evictable, volatile location; DB pointers survive the eviction (stale path).
- No `android:usesCleartextTraffic` whitelist issue (all TLS), but the OTA version fetch does **not** verify the host beyond HTTPS.

### S-5. Injection / validation
- `GeminiTextPreprocessor` and analyzer pass document text verbatim into prompts (prompt-injection surface is minor for a client tool, but no output sanitization exists before that text feeds TTS or the `.speak` path — a malicious document can produce SSML-like or harmful utterances);
- Parsers accept untrusted archives (EPUB/DOCX ZIP). `ZipInputStream` entries are read with **no zip-bomb / entry-size / entry-count limits** (`DocumentParser.kt:318-358, 363-396`) — a hostile 2GB zip can exhaust memory. PDF parser reads the entire file into a `ByteArray` (`:405`) with no size guard before the 2×10^6-char content cap kicks in later.
- `RawText` in table queries is not parameterized in a way that could be abused (Room handles it), but `AudioCacheDao.kt:26-27`'s `LIMIT :count` relies on SQLite ≥3.25 with no test.

---

## 5. Data & Persistence Audit

### Schema
`AppDatabase` (v3): `DocumentEntity`, `BookmarkEntity`, `ClonedVoiceEntity`, `AudioCacheEntity`.

| Finding | Severity | Reference |
|---|---|---|
| `fallbackToDestructiveMigration(dropAllTables=true)` — wipes library on any schema bump | CRITICAL | `AppDatabase.kt:26` |
| `exportSchema = false` — no migration test material, no guardrails | HIGH | `AppDatabase.kt:8` |
| Document delete orphans bookmarks (no FK, no cascade, no cleanup); `BookmarksScreen` then silently no-ops the jump | HIGH | `DocumentDao.kt:33-34`, `BookmarkDao.kt:11`, `BookmarksScreen.kt` |
| No `@ForeignKey`/`@Index` on any entity/DAO; `AudioCacheDao.getAllEntriesLRU` sorts entire table per eviction | MEDIUM | all DAOs |
| `ClonedVoiceDao` delete leaves audio files on disk | LOW | `ClonedVoiceDao.kt:17-18` |
| `@Insert(onConflict = REPLACE)` with numeric PK — silent row-replace footgun if IDs ever passed | LOW | `DocumentDao.kt:21-22` |
| `DocumentRepository` has no transaction wrapping for document+bookmark operations | LOW | `DocumentRepository.kt` |
| Import flow: `insertDocument` → `getDocumentById` → `openDocument` re-parses **the whole document a second time** (`importDocumentFromUri` then `openDocument` → `DocumentParser.processText`) — double O(N) parse per import, double memory spike | MEDIUM | `LyricReaderViewModel.kt:200-224, 174-198` |
| Crashed mid-write LRU cache: DB row inserted after file copy → orphan file; quota desync | LOW | `LruAudioCacheManager.kt:44` |

### Data-integrity verdict
Schema evolves ⇒ user's library vanishes. Bookmarks reference deleted docs forever. There are **no migrations, no backup mechanism, no export/import** (despite `allowBackup=true` boilerplate) — for a product whose entire value is the user's imported documents and reading position, this is a disqualifying defect.

---

## 6. TTS / Audio Engine Audit

### 6.1 SpeechEngine (`SpeechEngine.kt`, 834 lines)
- **Solid skeleton**: utterance-queue state machine (`EnginePlaybackState` with validated transitions), audio-focus management, ambient ducking, and a 60fps karaoke word-advance loop. Structurally the best file in the repo.
- Bugs & hazards:
  - `onDone` prefetch logic (`:219-229`): when sentence completes, it enqueues `sentenceIdx + 2` — but only if `sentenceIdx + 1` was previously queued; queue drift under `pause()`/`resume()` re-entrancy is possible; utterance IDs beyond buffered ones are dropped silently. **No gap-detection or recovery.**
  - Karaoke mapping (`handleWordRangeCallback:289-315`) is proportional (`charStart/spokenLen * origLen`) with no phonetic alignment — acceptable demo, wrong for anything production ("**wordSyncProgress**" is driven by `estimatedWordDurationMs = wordText.length*55+110` — a **statistical guess**, not engine truth; expect visible freeze/jump in highlight).
  - `pause()` calls `cloudVoiceSynthesizer.pause()` then `tts?.stop()` — but `cloudVoiceSynthesizer.resume()` is **never called** by SpeechEngine; pause+play in MASKED_AI_VOICE mode restarts the sentence from the beginning each time (`play()` → `playCloudPlayback()` re-synthesizes from `startSentenceIdx`), i.e. **seek/pause/resume semantics are broken for the flagship engine**.
  - `speakRawText` (`:819-828`) assumes `isInitialized` — but if `onInit` failed, Socratic answers silently never speak.
  - `syncOffsetMs` is stored in state but **consumed nowhere** (dead setting).
  - `jumpForward/Backward` use `seconds * 3` words/sec heuristic — wildly wrong at high reading speeds.

### 6.2 CloudVoiceSynthesizer (`CloudVoiceSynthesizer.kt`)
- Prefetch & dual-buffer design is genuinely good (cache map + join-on-active-job).
- **Gemini tone analysis runs BEFORE the LRU cache hit check** (`:199` then `:209`) — every sentence, even fully cached, costs one Gemini round-trip. That's a per-sentence cloud dependency for a "reader".
- Rate-limit hit → `delay(800)` per call (`:191-193`) — at 60/min cap, sustained reading stutters 800ms per sentence.
- Error path: after 3 provider attempts all keys marked QUOTA/INVALID get **reset to UNTESTED** (`ApiKeyManager.kt:203-214`) → the "rotation" loop then re-hits the same dead keys forever; per-sentence failures cascade into the "fallback to stock TTS" path mid-document — the famous "dual voice" prevention is defeated if the fallback happens mid-utterance while a MediaPlayer is mid-play (stop() calls exist but ordering is racy).
- `fetchGoogleNeuralSpeech` (`:314-367`): key from **Gemini** pool used against Google Cloud TTS — only works if the Gemini key has Cloud-TTS permission; typically invalid; error swallowed → next provider.
- `auditionVoiceSample` (voice preview) and `synthesizeAndPlay` **do not check `AudioSessionManager` exclusivity** — a preview can tear down active playback (by design of `createManagedMediaPlayer`, `AudioSessionManager.kt:43` kills all players).

### 6.3 The "engine zoo" — claim vs. substance (verified, read in full)

| "Engine" | Reality | Verified at |
|---|---|---|
| `KokoroLocalTtsEngine` | System `TextToSpeech` + offline voice pick; **no ONNX assets exist in repo**; doc comment falsely claims ONNX Kokoro | `KokoroLocalTtsEngine.kt:28-31, 74-92` |
| `FireRedLongCatSynthesizer` | Endpoints `api.firered.ai`, `api.longcat.ai` don't exist; Bearer uses Gemini key; **never** returns audio; dead tier | `FireRedLongCatSynthesizer.kt:17-18, 62` |
| `ZeroDowntimeAudioRouter` | Constructed, **zero call sites**; Tier-3 "Offline Kokoro" returns `null` file (silence) while messaging claims it's active | `ZeroDowntimeAudioRouter.kt:48, 88-90`; grep-verified |
| `BiomechanicalVoiceEngine` | Pitch/rate formulas (magic constants; fatigue divider 150f); **outputs never consumed**; UI sliders cosmetic | `BiomechanicalVoiceEngine.kt:80-99` |
| `SentientArticulationEngine` | Keyword `contains()` classifier labeled "SLM/NLP director"; blueprints shown only in debug modal; `processLookahead` triggered only from debug UI | `SentientArticulationEngine.kt:117-127, 96-112` |
| `RespiratorySynthesisEngine` | Inject literal `[sigh]`/`[chuckle]` bracket tags into speech text (would be *read aloud* if path were live); currently dead | `RespiratorySynthesisEngine.kt:46,51,56` |
| `PunctuationPacingEngine` | **Zero callers**; `"--"` branch unreachable after regex split | `PunctuationPacingEngine.kt:26, 55` |
| `EmotionalMarkupParser` | **Zero callers**; double-decoration bug if wired | `EmotionalMarkupParser.kt:9` |
| `AdaptiveNoiseGate` | Applies cosine fade to raw **MP3 bytes** (frames not decoded); "noise-floor gating" claim unsupported; repeated processing compounds corruption; `gate_${voiceId.hashCode()}` names | `AdaptiveNoiseGate.kt:85-104` |
| `AmbientMusicPlayer` | "Crossfade": `crossfadeProgress` set once to 0.0, never read (`:50,63`); "Binaural" = mono AM tone (needs stereo, differing L/R freqs) (`:110,184-201`); **release-while-blocked-write crash risk**: `stop()` cancels job then `audioTrack.release()` may race with `write(WRITE_BLOCKING)` on the audio thread (`:260,284-292`) | `AmbientMusicPlayer.kt` |
| `RespiratorySynthesisManager` | Metadata-only artifacts; pause durations never applied to playback; only used to mutate comma text before the API call | `RespiratorySynthesisManager.kt:100-109` |

**Bottom line:** the *only* audio that reliably plays is (a) stock device TTS, (b) ElevenLabs/Google/Fish cloud audio when keys work — which they currently don't — and (c) procedural AudioTrack tones. Every "neural/offline/sentient" promise is a UI or a lie.

### 6.4 VoiceCharacter & cloning
- 14 hardcoded voice *"characters"* — several map to the **same ElevenLabs ID** (e.g., Rachel/Amina/Saffron-style aliasing), contradicting "distinct character library".
- Favorites/custom voices are in-memory only (`VoiceCharacter.kt:213,228`) — favorites lost on process death; only HTTP-uploaded clones survive in Room.
- Blank/fallback ID silently resolves to Rachel (`:243`).

---

## 7. AI Subsystem Audit

### `GeminiTextPreprocessor.kt`
- **Model string**: `gemini-2.5-flash` for generateContent (`:43`) — presumably valid; **separately**, the tone analyzer calls `gemini-3.5-flash` (`GeminiEmotionalToneAnalyzer.kt:44`), an ID that could not be verified as of audit date and whose failure is silently swallowed into `fallbackKeywordAnalysis` — the "Gemini emotional analysis" feature may have **never run once** while the UI claims it.
- Key rotation via `executeWithKeyRotation` — decent design; but every non-HTTP exception is treated as key failure (`:86-92`) — a body-parse error rotates keys pointlessly.
- No request budget/auth beyond the 60/min limiter (unused here); no streaming; prompts sent with document excerpts (privacy, §13).
- `askSocraticCoPilot`/`generateActiveRecallQuiz`/`generatePodcastDialogueScript` return raw text parsed by UI with no structure guarantee; UI then renders it as if structured ("Q1", "A/B/C/D" parsing absent — answers render as raw text).

### `GeminiEmotionalToneAnalyzer.kt`
- Key in URL query string (S-2).
- Stability/style returned from Gemini are **unclamped** and passed directly to ElevenLabs `voice_settings` (range brittleness).
- Per-sentence cost & latency: every spoken sentence = 1 Gemini request, even from LRU cache (see 6.2).

### Integration gaps
- No telemetry about AI success/failure to the user ("AI preprocessing…" spinner can be lying).
- No AI audit trail; `TtsDiagnosticLogger` entries capped at 150, in-memory only, document text truncated at 30 chars — that is *good for privacy* but there is no persistent diagnostics/analytics at all — a production reader needs crash/network error telemetry.

---

## 8. Services & Background Processing Audit

### `TtsPlaybackService`
- README says 100% foreground media service — manifest `foregroundServiceType="mediaPlayback"` (`AndroidManifest.xml:36`), started via `startForegroundService` (`LyricReaderViewModel.kt:116`) and `startForeground` in `updateNotification()`. **Correct**, except: `updateNotification()` is called from `onStartCommand` on the main thread with `startForeground` every time — fine; but playback state is only updated when the ViewModel observes `isPlaying` transitions (`LyricReaderViewModel.kt:107-118`) — **pause** transitions never stop the service and the notification isn't refreshed when only paused (notification stays "Reading…" state) — cosmetic but user-visible.
- `PRIORITY_LOW` + `VISIBILITY_PUBLIC` notification shows the current **word/sentence text on the lock screen** — privacy leak for sensitive documents (§13).
- MediaSession `onSeekTo` maps ms→words linearly (`TtsPlaybackService.kt:84-88`) — correct-ish, but `ProgressPercentage` is wordRatio-based, so seeking is approximate.

### `CognitiveOrbService`
- Overlay permission never requested (C-6); float can be dragged fully off-screen with no clamp (`FloatingProgressShowerWidget.kt:66-70`); if `addView` fails the service stays foreground with a dead notification; **no `onStartCommand` override → START_STICKY default** safe enough, but the service has no way to be told to stop besides `stopService`.

### `AppUpdateManager` (see C-1, C-3, S-2)
- Download progress is faked (10% → 100%).
- `errorMessage` is never rendered in `AppUpdateModal`.
- Version check uses `HttpURLConnection` with no redirect policy concerns and no hash verification.

### concurrency notes (cross-cutting)
- `SpeechEngine`'s `CирcularUtteranceBuffer` is properly `@Synchronized`
- `AmbientMusicPlayer` release/write race (6.3)
- `VoiceRecorder` leaks a broken `MediaRecorder` on prepare failure (`VoiceRecorder.kt:69-73`) because `_isRecording` guard prevents the release path
- `TtsDiagnosticLogger` `clearLogs()` unsynchronized vs `log()` (`TtsDiagnosticLogger.kt:83-87`)
- `KokoroLocalTtsEngine` fields written on caller thread / read on TTS binder thread without synchronization (`KokoroLocalTtsEngine.kt:48-49`)
- `LruAudioCacheManager` same-hash concurrent `copyTo(overwrite=true)` torn-file risk (`LruAudioCacheManager.kt:44`)

---

## 9. UI / UX Audit

### Readability & theming
- **Light theme broken**: `LyricReaderScreen.kt:262,275,287,309,339,368,391` hardcode `Color.White` for header/word-count/waveform labels; `LyricThemePreset.PAPER_PARCHMENT` (cream background, `Color.kt:51-58`) makes the entire top bar and stats invisible. Several dark presets are fine; the light preset is shipping-broken.
- `LyricReadTheme` defaults `darkTheme = true` and ignores the system setting unless explicitly passed (`Theme.kt:37-38`); no dynamic color, no contrast tokens.

### Interaction
- **Seek spam**: `CanvasWaveformVisualizer` drag seeks per frame and doesn't `consume()` the gesture (`CanvasWaveformVisualizer.kt:70-74`); `KineticSprintReaderView` seeks per word at up to ~6 seeks/sec (`:63`) — audible artifacts guaranteed.
- Word touch targets 2–3dp tall (`LyricReaderScreen.kt:1181-1188`) — below the 48dp a11y minimum; casual taps seek accidentally.
- "Reveal on scroll up **or tap**" — tap-to-reveal never implemented (`:179,554`).
- Sleep-timer inconsistency: settings sheet offers 15/30/45/60; dedicated sheet offers 5/15/30/45/60 (`:797` vs `:1024`).
- Settings "Voice Fine-Tuning": 4 of 5 controls dead (`stability/similarityBoost/styleExaggeration/isVoiceLocked` are local `remember` state never consumed — `SettingsScreen.kt:242-245, 333-357`); `voiceSpeed` is snapshot-stale.
- Developer tools shipped as settings ("Prosody Inspector", "Memory Monitor & GC" `SettingsScreen.kt:594-645, 901-969`) — debug utilities exposed to end users; "Clean Memory" calls `System.gc()` (placebo).
- DIALOGUE_ONLY filter treats any apostrophe (`"` OR `'` contains) as dialogue (`LyricReaderScreen.kt:427`) — every contraction/possessive ("don't", "user's") matches; mode is polluted.
- Page-empty flash: Room flow starts empty ⇒ "No documents found" flashes before first emission; no loading state anywhere (`LibraryScreen.kt:643-671`).
- Delete-without-confirm/undo in Library and Bookmarks (`LibraryScreen.kt:679,985`; `BookmarksScreen.kt:128-134`).
- Orb label `contentDescription="Playing"` even when paused (`LibraryScreen.kt:863`).
- `AppUpdateModal` — trapped dialog (C-1); `FloatingProgressShowerWidget` `AnimatedVisibility(visible = true)` dead code (`:151`); `RespiratoryBreathingSphere` infinite pulse animation even when paused (`:54-72,90`).

### Accessibility / i18n
- **Zero** `R.string.` usage — every string literal inline across 26 UI files (verified grep); no localization files; `strings.xml` has only app name + shortcuts. An internationally-distributed reader with zero i18n is a launch blocker for a "multibillion-dollar" ambition.
- Icon semantics: header/NavHost `contentDescription` Greek to screen readers ("Back" is correct but the mode pill, AI hub chip, sliders, and waveform drags have no roles).
- Touch targets below minimum; no content descriptions on most decorative-but-functional elements like the orb canvas.

---

## 10. Build, Release & Configuration Audit

| Finding | Sev | Ref |
|---|---|---|
| **Release silently debug-signed** when `KEYSTORE_PATH`/passwords env vars absent (falls back to `debug.keystore` in `release{}`) — the "release" APK is worse than debug: no minify, debug cert | HIGH | `app/build.gradle.kts:27-33, 42-50` |
| `isMinifyEnabled = false` for release; no R8; proguard-rules.pro exists but inert | HIGH | `:49-50` |
| `shortcuts.xml` targets stale package `com.aistudio.lyricread.oxxfzc` vs actual `com.aistudio.lyricread.dhehbq` — **all 3 launcher shortcuts dead on device** | HIGH | `shortcuts.xml:12,24,36` |
| Committed build artifacts: `build/`, `.gradle/` (two Gradle versions), `.kotlin/` — repo bloat, env leak (absolute paths), stale caches; `.gitignore` already lists them ⇒ force-committed | HIGH | repo root |
| `applicationId = com.aistudio.lyricread.dhehbq` — auto-generated AI-Studio ID; full of brand-orphan issues (shortcut targets, Google Play listing, deep links) | MEDIUM | `:17` |
| Firebase BOM + `firebase-ai` + AppCheck deps present; NO `google-services.json`; `googleServices.missing.passthrough=true` hides it | MEDIUM | `gradle.properties:28`, `build.gradle.kts:78,80` |
| `compileSdk 36 (minorApiLevel 1)`, targetSdk 36 — modern, but AGP 9.1.1 + Gradle 8.11/9.3 double-cached — untested pairing in the committed `.gradle` | LOW | |
| `versionCode = 1` vs OTA `version.json.versionCode = 2` — trap (C-1) | CRITICAL | `:20` vs `version.json` |
| No CI config, no lint gate, no gradle wrapper checksum pinning | MEDIUM | repo-wide |
| Returns-NOTHING branches: `googleServices` warn-mode — AppCheck (Recaptcha) will not actually work without config; `firebase-ai` unused | LOW/MED | |

---

## 11. Testing Audit

| Test | Verdict |
|---|---|
| `ExampleUnitTest.kt:12-15` | `2+2==4` — placeholder |
| `ExampleRobolectricTest.kt:16-20` | **RED ON TRUNK** — asserts app_name "My Application"; actual `strings.xml` = "MaskedD" |
| `ExampleInstrumentedTest.kt:20` | **RED ON EVERY DEVICE** — asserts package "com.example"; actual applicationId "com.aistudio.lyricread.dhehbq" |
| `GreetingScreenshotTest.kt:24-28` | Renders an orphan `Text("LyricRead Test")` — tests the harness, not the app |

- Zero tests for: `DocumentParser` (the heart of karaoke), `SpeechEngine` state machine, `ApiKeyManager` rotation, DAOs/repository, ViewModel, tokenization, LRU cache, normalization pipeline — despite heavy Robolectric/Roborazzi/Compose-test dependencies being present (`app/build.gradle.kts:122-137`) and `isIncludeAndroidResources=true`.
- `testOptions` and Roborazzi infra are *set up and unused* — infrastructure theater.
- No screenshot/visual-regression tests exist despite Roborazzi dependency; no accessibility tests; no instrumentation tests for services or the orb overlay.

---

## 12. Performance & Memory Audit

| Area | Finding | Sev |
|---|---|---|
| Karaoke loop | 60fps `delay(16)` job rewriting `_progressState.value` — constant recomposition of the whole reader on every tick | MEDIUM |
| Parsing | Double-parse on import (5); PDF fully buffered (`DocumentParser.kt:405`); epub/docx/zip unbounded entry sizes (`:318-396`) | MEDIUM |
| Scrub | Per-frame seeks during waveform drag and per-word seeks in Sprint (§9) | MEDIUM |
| Cloud | Per-sentence Gemini call even on cache hit; 800ms stall when rate-limited (§6.2) — worst-case sustained listening: ~60 sentences/min ⇒ 1 request/s ⇒ limiter trips every minute ⇒ stutter | HIGH |
| Cache | `LruAudioCacheManager` 100MB quota enforced only on insert; FireRed/gate_ files bypass quota; torn-file race | MEDIUM |
| MemoryMonitor | `System.gc()` + `runFinalization()` calls in the *parsing* hot path (`DocumentParser.kt:79,165`) — placebo GC that can stall the parser thread; also exposed via Settings button | LOW |
| Sleep timer | Runs a `delay(1000)` loop writing a StateFlow *every second* for the timer's lifetime | LOW |
| UI cost | Infinite animations run while screens are idle (breathing sphere) | LOW/MED |

---

## 13. Privacy & Compliance Audit

1. **Undisclosed voice recording upload** — up to 30s of user voice → `api.elevenlabs.io/v1/voices/add` (C-5). No privacy policy link, no consent dialog, no disclosure in metadata.json (`requestFramePermissions` empty).
2. **Document content exfiltration** — every sentence spoken goes to Gemini (`GeminiEmotionalToneAnalyzer.kt:56`) *and* the full normalized text to whichever TTS provider succeeds; no opt-out, no per-mode toggle, and the "100% offline" modes do not stop the background preprocessor on the cache-hit path (the analyzer runs before the cache check).
3. **Lock-screen content leak** — `VISIBILITY_PUBLIC` notification shows the current spoken text (`TtsPlaybackService.kt:181,193`).
4. **Keys harvested from APK** → third parties can silently use the founder's paid quotas (S-1); Google Play policy: `REQUEST_INSTALL_PACKAGES` + self-update + `SYSTEM_ALERT_WINDOW` are store-blocking vectors; "cloning" with undisclosed upload is a GDPR/APPI-grade exposure (voice = biometric-adjacent personal data in several jurisdictions: Nigeria NDPA, EU GDPR Art. 9, US state laws).
5. **Backup leakage** — `allowBackup=true` with boilerplate rules backs up cache-dir voice frames inconsistently across devices.
6. No analytics-consent banner, no "what's new" privacy doc, no data-deletion UI (delete document does not delete its cloud copies in Gemini/ElevenLabs caches or uploaded voice model).

**Compliance verdict:** shipping today would fail a Play data-safety review, likely fail store listing, and expose the owner to biometric-data consent liability.

---

## 14. Code-Quality & Maintainability Audit

- **Naming mismatch:** package `com.example`, applicationId `com.aistudio.lyricread.dhehbq`, file comments claim "MaskedD" — three identities; deep-link/shortcut/Play integrity depends on a single string that's already stale (`shortcuts.xml`).
- **Full-path imports everywhere:** `com.example.ai.GeminiTextPreprocessor.askSocraticCoPilot` invoked via fully-qualified names inside ViewModel/UI — indicates import churn during AI-Studio generation; brittle.
- **Marketing comments passed off as design:** "10-15+ keys per provider" (`ApiKeyManager` doc) while pools hold 1; "zero downtime" claims; "slm", "biomechanical", "sentient" — the codebase reads like a features-pitch deck, not a spec. Assertions about file behavior were verified false in several places.
- **Dead modules:** `ZeroDowntimeAudioRouter`, `PunctuationPacingEngine`, `EmotionalMarkupParser`, `BiomechanicalVoiceEngine` (outputs), `SentientArticulationEngine` (outputs), `DynamicFormatMode` conversion modes, `setFishAudioKey/setElevenLabsKey` VM methods (no callers), `syncOffsetMs`, `ssmlOutput` (produced, never sent), `targetTrack`, `accompanist-permissions`, camera, play-services, Firestore, Firebase Auth deps (commented out) — roughly **30-40% of the shipped "capability" is unreachable**.
- **Duplication:** `extractTextFromEpub`/`extractTextFromDocx`/PDF parsing hand-rolled (ZIP + regex) instead of a library (Pandoc/PDFBox/EPUB libs); fork of Python scripts (`scripts/biological_phonetic_engine.py`, `text_normalization_pipeline.py`) that duplicate Kotlin logic with no sync contract.
- **Error handling:** `catch (e: Exception) { e.printStackTrace() }` appears ~20×; silent-failure wrapper patterns; no structured logging, no crash analytics, no user-visible remediation, no retry with backoff (except key rotation).
- **No DI framework** — manual constructors & object singletons (`ApiKeyManager`, `DocumentParser`, `MemoryMonitor`, `VoiceCharacterManager`) — fine at this scale, but `ApiKeyManager` as a global object with `SharedPreferences` initialized from a ViewModel is a lifecycle smell (services survive ViewModel destruction but hold engine references — a leak vector if the process lives after activity death, mitigated by process-wide singleton).

---

## 15. Gap Analysis vs. Production/Enterprise Standard

| Discipline | Required | Actual | Gap |
|---|---|---|---|
| Credentials | Server-proxied or vaulted, rotated, revoked-on-leak | Committed plaintext; in-APK; empty-at-runtime bug | **Blocking** |
| Data safety | Migrations, schema export, backup/restore, FK integrity | Destructive migration, no schema export, orphaned refs | **Blocking** |
| App-store compliance | Minimal permissions + disclosure | 4 risky permissions, 2 unrequested, 2 store-blocking | **Blocking** |
| Release engineering | CI, signing gate, minify, deterministic versioning | None; debug-sign fallback; unminified | **Blocking** |
| User trust | Honest error paths | Silent fake success (clone), trapped update, lying spinners | **Blocking** |
| Testing | Unit+integration+E2E+visual+a11y | 4 tests, 2 red, 0 meaningful | **Blocking** |
| Observability | Crash/network/logging telemetry | In-memory log capped 150 | High |
| i18n/a11y | strings.xml, RTL, 48dp targets | None; inline literals; 2dp targets | High |
| Audio correctness | Decoded PCM processing, monotonic playback state | MP3-byte corruption; pause/resume restart; heuristic sync | High |
| Privacy | Consent, disclosure, deletion | Undisclosed uploads; lock-screen leaks | High |
| Clean architecture | Layered, testable, extensible | ViewModel-heavy script; dead modules; marketing dead-code | Medium |

---

## 16. Prioritized Remediation Roadmap

**Phase 0 — Stop the bleeding (hours, before any device demo or store submission)**
1. Rotate all three API keys; remove `.env`/`.env.example` secrets; placeholder-only.
2. Delete/disable the OTA self-update path and `version.json` (remove from repo & remote); make `AppUpdateModal` dismissible; gate updates behind explicit user action + signature verification, or replace with Play/App-Store update channel (Play pending-update API) — removes C-1 and the sideload vector.
3. Add `RECORD_AUDIO`/`POST_NOTIFICATIONS` runtime permission requests with rationale UI; wire overlay settings intent for the orb.
4. Ship real migrations (schema export ON, hand-written `Migration`s); remove `fallbackToDestructiveMigration`.

**Phase 1 — Make the honest core work (days)**
5. Fix the Secrets plugin config (or read keys from `local.properties`/env only for debug) and reconcile `.env` naming — restore *real* cloud TTS authentication; add HTTP-429-aware backoff instead of the 800ms flat delay; move Gemini analysis behind the cache check.
6. Wire or remove: `ZeroDowntimeAudioRouter`, `PunctuationPacingEngine`, `EmotionalMarkupParser`, biomechanical/sentient outputs — visibility into which audio pipeline actually produces a sentence must be a first-class state.
7. Delete fabricated engines (FireRed/LongCat) or point them at real implementations; rename "Kokoro" honestly.
8. Fix mute fallback path: stop prior MediaPlayer/track before starting native TTS; single-flight playback state machine per engine.

**Phase 2 — Trust & polish (weeks)**
9. Honest clone flow: consent dialog, success only on real API success, delete-data control, quarantine recordings in app-private storage.
10. Extract all strings to resources; add light-theme contrast fixes; 48dp touch targets; loading/error/empty states on every screen; delete confirmations with undo.
11. Fix pause/resume/seek semantics for cloud engine (resume via seek, not re-synthesis); clamp seeks during scrub; debounce waveform drag.
12. Enforce `x-goog-api-key` header; signature-verify OTA (if kept); narrow FileProvider paths; limit zip-entry sizes during parsing.

**Phase 3 — Industrialization (months)**
13. Build CI (lint + unit + instrumented + screenshot tests); fix red tests; add parser/state-machine/LRU tests; enable R8 with rule coverage; enforce release signing gate.
14. Telemetry (crash reporting, network errors, API-quota dashboards) with privacy-by-design.
15. Server-side API-proxy for keys + usage metering (or Firebase AppCheck enforcement) before any scale ambition.
16. Audit & test with screen-reader and TalkBack; full RTL/localization pipeline for launch markets (Nigeria first per product brief — Hausa/Yoruba/Igbo TTS parity is a real roadmap item).
17. Clean repo: remove `build/`, `.gradle/`, `.kotlin/`, stale `*ample*` code; single identity (package/applicationId/brand).

---

## 17. Appendix — Complete Findings Register

| ID | Sev | Area | Finding | Location |
|---|---|---|---|---|
| C-1 | CRITICAL | Update | Mandatory-update trap; non-dismissible dialog; version 2 vs 1 | `version.json`, `MainActivity.kt:82`, `AppUpdateModal.kt:142-147` |
| C-2 | CRITICAL | AI/TTS | Secrets plugin never generates BuildConfig keys + name mismatch ⇒ all cloud auth = empty | `ApiKeyManager.kt:57-73`, `build.gradle.kts:68-72`, `.env` |
| C-3 | CRITICAL | Secrets | Live production keys committed | `.env`, `.env.example` |
| C-4 | CRITICAL | Data | Destructive migration wipes user library; exportSchema off | `AppDatabase.kt:26,8` |
| C-5 | CRITICAL | Privacy/UX | Voice upload + fake clone success + no runtime permission | `InstantVoiceCloneUploader.kt:65,88,118`; `VoiceRecorder.kt:69-73` |
| C-6 | CRITICAL | Security | SYSTEM_ALERT_WINDOW + REQUEST_INSTALL_PACKAGES + over-broad FileProvider + unverified sideload | `AndroidManifest.xml:10-11`, `file_paths.xml:4`, `AppUpdateManager.kt:176-182` |
| C-7 | CRITICAL | Audio | Noise gate corrupts compressed MP3 bytes | `AdaptiveNoiseGate.kt:85-104` |
| F-1 | HIGH | Audio | Zero-Downtime Router never invoked; Tier-3 null file | `ZeroDowntimeAudioRouter.kt:48,88-90` |
| F-2 | HIGH | Audio | FireRed/LongCat fabricated endpoints; Gemini bearer | `FireRedLongCatSynthesizer.kt:17-18,62` |
| F-3 | HIGH | Audio | "Kokoro" = system TTS rebrand; false ONNX claims | `KokoroLocalTtsEngine.kt:28-31,74-92` |
| F-4 | HIGH | Audio | Crossfade fake; mono "binaural"; release/write race | `AmbientMusicPlayer.kt:50,63,110,184-201,260,284-292` |
| F-5 | HIGH | AI | `gemini-3.5-flash` unverifiable; silent fallback | `GeminiEmotionalToneAnalyzer.kt:44,96` |
| F-6 | HIGH | AI/TTS | Gemini call before cache hit; 800ms throttle | `CloudVoiceSynthesizer.kt:191-199` |
| F-7 | HIGH | Engine | Pause/resume restarts sentence (seek broken) | `SpeechEngine.kt:397-407,469-539,669-689` |
| F-8 | HIGH | Data | Orphaned bookmarks on doc delete | `DocumentDao.kt:33-34`, `BookmarkDao.kt:11` |
| F-9 | HIGH | UX | Light theme = invisible text | `LyricReaderScreen.kt:262-391`, `Color.kt:51-58` |
| F-10 | HIGH | UX | Dead Voice Fine-Tuning controls | `SettingsScreen.kt:242-245,333-357` |
| F-11 | HIGH | Build | Release debug-sign fallback; minify off | `build.gradle.kts:27-33,47-50` |
| F-12 | HIGH | Build | shortcuts.xml stale package ⇒ dead shortcuts | `shortcuts.xml:12,24,36` |
| F-13 | HIGH | Tests | 2 guaranteed-red tests | `ExampleRobolectricTest.kt:16-20`, `ExampleInstrumentedTest.kt:20` |
| F-14 | HIGH | Hygiene | Committed `build/`, `.gradle/`, `.kotlin/` | repo root |
| F-15 | HIGH | Privacy | Voice upload without consent/disclosure | whole clone flow |
| M-1 | MED | AI | Key in URL query string | `GeminiEmotionalToneAnalyzer.kt:44` |
| M-2 | MED | AI | Unclamped stability/style passed to ElevenLabs | `CloudVoiceSynthesizer.kt:278-283` |
| M-3 | MED | Data | No indexes/FKs; table-sort eviction | all DAOs |
| M-4 | MED | Perf | Double parse on import | `LyricReaderViewModel.kt:200-224` |
| M-5 | MED | Parsing | Unbounded ZIP/PDF sizes (zip-bomb) | `DocumentParser.kt:318-396,405` |
| M-6 | MED | Audio | MediaRecorder leak on prepare failure | `VoiceRecorder.kt:69-73` |
| M-7 | MED | Audio | LRU torn-file race; quota bypass | `LruAudioCacheManager.kt:44,61-76` |
| M-8 | MED | Audio | Kokoro data races; error monitoring via deprecated callback | `KokoroLocalTtsEngine.kt:48-49,131` |
| M-9 | MED | Audio | Session-ID "uniqueness" not guaranteed | `AudioSessionManager.kt:31` |
| M-10 | MED | UI | DIALOGUE_ONLY apostrophe bug | `LyricReaderScreen.kt:427` |
| M-11 | MED | UI | Seek spam in waveform/sprint | `CanvasWaveformVisualizer.kt:70-74`, `KineticSprintReaderView.kt:63` |
| M-12 | MED | UI | Sleep-timer schedules conflict | `LyricReaderScreen.kt:797,1024` |
| M-13 | MED | UI | No loading/error states; empty-flag flicker; instant delete | Library/Bookmarks/PasteModal |
| M-14 | MED | A11y | 2-3dp touch targets, no roles | `LyricReaderScreen.kt:1181-1188` |
| M-15 | MED | i18n | Zero string resources; all inline | all 26 UI files |
| M-16 | MED | Update | Fake progress; errorMessage unrendered | `AppUpdateManager.kt:150-153`, `AppUpdateModal.kt:129-134` |
| M-17 | MED | Update | Orb unclamped dragging; dead AnimatedVisibility | `FloatingProgressShowerWidget.kt:66-70,151` |
| M-18 | MED | Perf | 60fps state rewrite; placebo GC in parser | `SpeechEngine.kt:581-615`, `DocumentParser.kt:79,165` |
| M-19 | MED | Privacy | Lock-screen text leak | `TtsPlaybackService.kt:181,193` |
| M-20 | MED | Build | No CI/lint gates; version trap sources | — |
| M-21 | MED | AI | Quiz/recap raw-text rendering; playwright-grade parsing absent | `LyricReaderViewModel.kt:335-433` |
| L-1 | LOW | Audio | `syncOffsetMs` dead; `speakRawText` silent on init-fail; `--` branch unreachable | `SpeechEngine.kt:813-818`, `PunctuationPacingEngine.kt:55` |
| L-2 | LOW | Audio | Voice IDs shared across characters; blank→Rachel | `VoiceCharacter.kt` |
| L-3 | LOW | Data | Clone files orphaned on delete; REPLACE footgun; stale paths after cache eviction | `ClonedVoiceDao.kt:17-18`, `DocumentDao.kt:21-22`, `VoiceRecorder.kt:31` |
| L-4 | LOW | UI | Density-ignorant bar math; stale mini-player label; echoed claims | `LyricReaderScreen.kt:1225`, `LibraryScreen.kt:863,889` |
| L-5 | LOW | Build | Firebase deps unconfigured; pass-through hides it; 2 Gradle versions cached | `gradle.properties:28` |
| L-6 | LOW | Hygiene | Python scripts duplicate Kotlin logic, no sync | `scripts/` |
| L-7 | LOW | AI | `ssmlOutput` generated, never sent | `TextNormalizationPipeline.kt:284-288` |
| L-8 | LOW | Diagnostics | Timestamp-collision ids; unsynced clear | `TtsDiagnosticLogger.kt:23,87` |
| L-9 | LOW | Audio | Number normalization gaps ($1,000, cents; 5+ digit digits) | `TextNormalizationPipeline.kt:137,167,215` |

---

## 18. UI & UX Experience Deep-Dive — "Things Don't Work, It's Annoying, It's Complicated"

This chapter is the user-facing counterpart to the engineering audit: it maps the three complaint families users actually voice — **"stuff doesn't work", "the UI is annoying", "everything is complicated"** — to the exact code that produces each experience, then prescribes fixes. It is grounded in a full read of `LyricReaderScreen.kt` (2,243 lines), `LibraryScreen.kt`, `SettingsScreen.kt`, `BookmarksScreen.kt`, the AI Hub sheet, and every modal, plus verification greps.

### 18.1 Complaint archetypes → root causes

| User complaint (verbatim archetype) | Root cause in code |
|---|---|
| "I pressed play and nothing happens / it sounds robotic" | Cloud engine authenticates with empty key (C-2) → silent fallback to stock TTS; `engineStatusMessage` still claims "Masked AI Active" (`SpeechEngine.kt:491`, `:492`) |
| "I switched on the AI voice but it still sounds the same" | Voice change only clears the LRU cache (`LyricReaderScreen.kt:1386`); several "characters" share the same ElevenLabs voice ID; most differ only in pitch multipliers that cloud mode ignores |
| "The voice cloning said success but it's not my voice" | Silent fake success — every failure maps to stock Rachel and reports Success (C-5) |
| "It asked me to update and I can't close it" | Mandatory-update trap (C-1): `onDismiss = { }` no-op + mandatory version.json |
| "The words highlight at the wrong time" | `wordSyncProgress` is driven by estimated duration `len*55+110ms` (`SpeechEngine.kt:630`), not engine truth; `syncOffsetMs` calibration slider does nothing (dead setting); proportional char-mapping drifts on normalized text |
| "It keeps jumping around when I scroll" | Auto-scroll (`LyricReaderScreen.kt:222-229`) + word-tap seeks + per-frame waveform seeks fight the user's own scroll (F-9/M-11) |
| "Half the settings do nothing" | Dead controls: word-sync calibration, Voice Fine-Tuning quartet, `syncOffsetMs`, engine-source picker picks engines that don't exist (Kokoro = stock TTS) |
| "I can't find my pasted Notes doc" | Library filters don't include "Notes"/"Poetry"/"Article" categories that Paste works with |
| "I deleted a book by accident" | Delete fires with no confirm/undo (`LibraryScreen.kt:679,985-991`) |
| "It looks broken / washed out" | Light theme renders white-on-cream text (F-9) |
| "My saved position is gone" | Destructive DB migrations + progress saved only on back/`onCleared` — process kill or crash loses position silently |

### 18.2 "Things don't work" — the phantom-controls inventory

Every element a user can touch that produces no real effect (verified trace to zero consumers):

| Phantom control | Where the user sees it | Why it does nothing | Ref |
|---|---|---|---|
| "Word-Sync Timing Calibration" slider | Reader settings sheet | `syncOffsetMs` is stored in `PlaybackProgress` but never read by any playback path | `LyricReaderScreen.kt:746-774`; `SpeechEngine.kt:813-817` |
| Voice Fine-Tuning (stability / similarity / style / voice lock) | Settings screen | Local `remember` state; no engine wiring, not persisted | `SettingsScreen.kt:242-245,333-357` |
| "Kokoro Offline AI" engine option | Engine-source picker in reader settings | System TTS rebrand; no model exists (F-3) — user hears identical voice they were already hearing | `LyricReaderScreen.kt:844-886` |
| "Masked AI Voice" engine option | Same picker | Cannot authenticate today (C-2) — silently produces stock TTS after ~1-3s of "Synthesizing" | `SpeechEngine.kt:397-399,533-536` |
| "Bionic ADHD Focus Mode" toggle | Reader settings sheet | Works visually only (bold prefix); "3x faster eye fixations" claim is unmeasured marketing | `LyricReaderScreen.kt:667-702,1192-1204` |
| "AI Voice Characters" — most characters | Character sheet | Several map to the same ElevenLabs voice ID; favorites reset on process death | `VoiceCharacter.kt:213,228` |
| AI Hub → "Sentient Articulation SLM" / "Biomechanical Voice" | Two lab sheets | Data-viewing screens only; sliders compute values consumed by nothing | `SentientArticulationEngine`, `BiomechanicalVoiceEngine` |
| AI Hub → "Diagnostics" (BugReport icon) | Diagnostic dashboard | Dev tool exposed as a feature to end users | `LyricReaderScreen.kt:1121-1126` |
| "Previously On..." sound effect | Socratic sheet chip | Reads recap via `speakRawText` — but `speakRawText` is a no-op if `onInit` never succeeded (common) | `LyricReaderViewModel.kt:353`; `SpeechEngine.kt:819-828` |
| "Read Aloud" button in Socratic sheet | Socratic sheet | Sits behind `isInitialized`; no feedback if silent | `LyricReaderScreen.kt:2208-2223` |
| Sleep-timer "Active: Pausing in Xm" | Settings sheet + dock countdown | Two competing timers (15/30/45/60 vs 5/15/30/45/60); starting one from the other location silently cancels the first | `LyricReaderScreen.kt:797,1024` |
| Orb overlay widget | Floating orb (if permission granted) | `canDrawOverlays` is checked, never requested; drag has no bounds; the orb can be lost off-screen | `CognitiveOrbService.kt:99`; `FloatingProgressShowerWidget.kt:66-70` |
| Swap/override of "Styles" sheet entries | Styles sheet duplicates dock chips | Same `ReadingMode.entries` rendered twice through two UIs | `LyricReaderScreen.kt:1426-1433` vs `1632-1749` |
| "Engine status: Kokoro Offline Neural TTS Active" | Status line | Misleading label for stock TTS | `SpeechEngine.kt:429` |
| Estimated "Xm Ys left" | Computed at top of reader | **Computed and never rendered** (verified: single occurrence, no consumer) — dead UI math | `LyricReaderScreen.kt:166-174` |

### 18.3 "It's annoying" — the irritants users feel every session

**a) Light theme is literally unreadable.** The reader hardcodes `Color.White` for the header back button, title, bookmark/settings icons, mode pill, waveform labels and word counts (`LyricReaderScreen.kt:259-262,270-276,287-309,339-353,366-392,438,549`). On `PAPER_PARCHMENT` (cream), the whole top half of the screen and the "Loading lyrics..." placeholder are white-on-cream — invisible. The light-theme story is one hardcoded palette short of usable, and the reading screen's stateless design means there is no error overlay at all when parsing fails: users see "Loading lyrics..." **forever** with no retry, no message, no back hint (`:547-551`).

**b) Motion overload.** The karaoke row animates text color, pill background, and scale per word at 60fps (`LyricReaderScreen.kt:1163-1179`), a synthetic waveform pulses endlessly (`AudioWaveformVisualizer` infinite breath transition, `:1251-1260`), the reader auto-scrolls *while* the user tries to scroll, and the bottom dock slides in/out based on scroll direction with no tap-to-reveal (comment promises "or Tap", `:554` — no handler exists). Users report "the screen never stays still." Motion communicates nothing here because the highlight is statistically estimated (§6.1) — the animation is dancing to a wrong clock.

**c) Sequel-ware visual language.** Emoji-as-icons in the chrome (`🎭` mode pill `:285`, `⭐` favorites `:1358`, `🎙️` `:477`, `🧠💡🎬🙋🤔` chips `:2078-2126`), all-caps shout labels ("DIALOGUE SCRIPT MODE", "AI PODCAST DEBATE MODE", "MEMORY PALACE ACTIVE RECALL QUIZ", "ACTIVE READING SNIPPET" — `:437,477,2046,2201`), and purple-pink-cyan gradients on every button. There is no restraint, no hierarchy by importance — the AI Hub header alone is a giant tri-color gradient circle. Every surface competes for attention; none of it is information.

**d) No feedback anywhere.** Adding a bookmark: silent (`:301-310`). Switching voice: silent (just a cache clear). Changing engine: silent. Deleting: silent + irreversible. Rate-limited API: silent 800ms hang. The single "error" surface in the whole app is the Diagnostic dashboard — a dev tool. A user of the flagship feature (cloud voice) never learns *why* it sounds different; the status line **lies** (`engineStatusMessage = "Masked AI Active"` while stock TTS plays, `SpeechEngine.kt:491-492`).

**e) Fighting gestures.** Waveform drag seeks per frame and doesn't consume the gesture (`CanvasWaveformVisualizer.kt:70-74`), so a scrub becomes a stutter-bomb of `seekToWord` calls; Sprint mode seeks every word at up to ~6 seeks/sec (`KineticSprintReaderView.kt:63`), and auto-scroll re-aims the list while the finger is mid-fling. The three "progress" mechanisms (auto-scroll, word-tap, waveform-drag) are uncoordinated.

**f) The mini-player chrome.** Library's mini construct mirrors the reader's scroll-hide behavior; the orb is a second floating player; the reader dock already packages favorites + modes + styles + status + transport. Users get **three** playback surfaces (reader dock, library mini, orb), each with subtly different behaviors.

### 18.4 "It's complicated" — cognitive-load audit

The reader screen stacks **seven chrome zones** before content: header (back / title+mode pill / bookmark+AI-Hub+settings) → waveform row (word index + 42 bars + total words) → Aura format bar → (content) → dock: favorites row → mode chips + Styles + auto-hide → word/percent status → transport (sleep, −10s, play, +10s, speed pill). On a phone, that's ~30% of the screen consumed by controls for a reading app whose core promise is *immersion*.

Complication sources, in order of user impact:

1. **Two parallel "mode" systems.** `ReadingMode` (TTS style: Storyteller/Educator/… — the header "🎭 … Style" pill and the dock chips and the Styles sheet) and `DynamicFormatMode` (display format: Deep Dive/Podcast/Sprint/Dialogue — the Aura bar). Both render as identical-looking pills, yet control different things ("Storyteller Style" changes voice cadence; "Deep Dive" changes layout). No copy pairs them.
2. **Sheet-stack navigation.** AI Hub → 8 lab cards → each opens *another* bottom sheet (Voice Characters, Podcast→Socratic sheet, Sprint→mode switch, Sentient, Biomechanical, Ambient Music, Diagnostics). Users can end up three sheets deep with no breadcrumbs; the podcast card fires `askSocraticCompanion` directly to a sheet that also hosts two other features (`LyricReaderScreen.kt:1097-1104`) — the same generic answer box shows Socratic answers, quizzes, and podcast scripts interchangeingly (`:2184-2236`), because the ViewModel funnels all three into `socraticAnswer`/`quizText`/`previouslyOnRecapText` and the sheet prefers whichever is non-null.
3. **Two settings surfaces.** Reader settings sheet (10+ controls) vs. the Settings screen from the library (10+ more, incl. dead tuning and dev tools). No organization, no search, no grouping beyond loose sections.
4. **Two sleep timers with different menus** (18.2).
5. **Jargon gate**: "Aura", "Biomechanical Voice Physics", "Sentient Articulation SLM", "Kinetic RSVP Sprint", "Socratic Co-Pilot", "Previously On…", "Automatic Voice Character Filter", "Word-Sync Calibration ms" — a user must learn a fictional vocabulary before understanding what anything does; several of these features *do not exist functionally* (18.2), so the vocabulary is not just complexity, it's falsehood.
6. **Hardcoded category disparity**: Paste offers Story/Article/Markdown/Notes/Poetry/Book; Library filters offer All/Favorites/Story/Markdown/Office/Classic/User Import — imports land in "User Import" regardless of chosen category, or land in categories with no filter chip; users "lose" documents they just created.
7. **% + word-index + time-left triple progress display** (the third is computed then unused!).

### 18.5 Baseline flow walkthroughs (where users actually get stuck)

**First run.** Launches → Library briefly flashes "No documents found" → samples load asynchronously (no loading state, `LyricReaderViewModel.kt:85-97`) → if the OTA server answers, a mandatory update dialog that cannot be dismissed (C-1). Worst-case first-run: **stuck on a modal before reading anything.**

**Open document & play.** Library → tap card → foreground service starts, engine "plays" — if MASKED_AI_VOICE (default), there is a 1–3s "Synthesizing" gap per sentence while it tries empty-key providers, then stock TTS. User hears the *default* voice regardless of the character shown. If they toggled a "character", they heard nothing change.

**Change voice.** Header → AI Hub → Voice Characters → scroll 15 cards → tap one → sheet closes; audio continues with the same voice (same ElevenLabs ID or unauthenticated). No preview chip in the list per card (audition exists but is hidden/fragile, `CloudVoiceSynthesizer.kt:478-498`).

**Clone voice.** Characters sheet → "Clone Voice" → record (no permission prompt on device — SecurityException swallowed, `VoiceRecorder.kt:69-73`) → after 30s cap or stop → "Success, saved to your voice library" — actually stock Rachel (C-5). This is the single most trust-destroying interaction in the product: **the UI says yes while the product says no.**

**Seek during playback.** Drag the waveform → seeks every frame + auto-scroll re-aims the list → playback restarts or stutters; with cloud mode, every seek re-synthesizes from the sentence start ($6.1 pause/seek defect). Users settle for "no scrubbing".

**Pause and resume.** Paused → play → cloud mode re-synthesizes the same sentence from the beginning, losing the word position visually is preserved but audio replays the sentence (F-7). Feeling: "broken."

**Sleep timer.** Reader dock moon icon → modal (5/15/30/45/60) → set 30m → later stumble into settings sheet → tap 45m → timer silently replaced. No countdown in the dock, only in the sheet at "Active: Pausing in Xm".

**Update.** Non-issue until it is everything (C-1).

### 18.6 Information architecture verdict

- Navigation depth: Library ↔ Reader ↔ Bookmarks ↔ Settings is fine; the problem is bottom-sheet nesting with no history, and feature discovery routed through a single "AI Hub" junk-drawer that mixes real (voices, ambient music), cosmetic (biomechanical/sentient), and developer (diagnostics) capabilities.
- The orb overlay (SYSTEM_ALERT_WINDOW) is a flagship feature with **no onboarding flow at all** — no settings toggle, no permission request, no explanation; it simply fails silently on every device that hasn't granted overlays.
- Zero empty/error/loading taxonomy: the app has exactly one loading string for the core screen ("Loading lyrics..."), zero error states, and empty states only for library/bookmarks.
- Microcopy is written for the demo-giver, not the reader: every label sells ("zero-latency", "neural prosody SLM", "3x faster") instead of *telling the user what will happen*.

### 18.7 UX remediation roadmap (effort/impact)

**P0 — Trust & legibility (ship-blocking, days):**
1. Make the update modal honor dismiss + show real errors (fixes first-run trap) — *and* stop lying everywhere: drive `engineStatusMessage` from the actual engine that produced audio; show "Offline voice active (cloud unavailable)" honestly.
2. Fix light-theme contrast: replace hardcoded `Color.White` in the reader chrome with theme-aware tokens (pass current `LyricThemePreset` through). Visual-regression test both themes.
3. Add per-action feedback: snackbar for bookmark added/deleted, voice switched, engine changed, timer set; confirm-delete dialogs with Undo for documents and bookmarks.
4. Wire `syncOffsetMs` or remove the calibration slider; wire Voice Fine-Tuning or remove it. **Every control that does nothing is a product defect.**
5. Add real RECORD_AUDIO permission flow + honest clone result (success only on API success).

**P1 — Reduce complexity (1–2 weeks):**
6. Merge the two mode systems into one user-facing concept ("Format" controls layout; "Voice Style" controls narration — rename and visually distinguish the pill families; delete the duplicate Styles sheet).
7. Collapse the AI Hub into 3-4 real capabilities (Voices, Ambient, Podcast, Assist); hide/dev-gate Diagnostics; kill the Sentient/Biomechanical sheets or wire their outputs into real DSP.
8. Single sleep-timer surface with one menu + dock countdown; kill the second settings sheet's timer.
9. Unify categories between Paste and Library filters; auto-derive category chips from data; add a loading state to library.

**P2 — Friction (2–4 weeks):**
10. Debounce waveform seeks (seek on gesture end), consume gesture events, pause auto-scroll while the user is dragging; add a "scrub lock" toggle.
11. True seek/resume in cloud mode (resume from word via re-synthesis of that sentence only, preserving sentence position).
12. 48dp minimum touch targets for word/row interaction; talk-back labels for all icon buttons; move all strings to `strings.xml` with a localization pass (launch markets: Nigeria first).
13. Glyph audit: replace emoji with a real icon system; tone down gradients; respect `MaterialTheme` color scheme instead of hardcoded `0xFF130E22`/`0xFF0D0A1B` panels so themes and dynamic color can work.
14. Onboarding: 3-screen explainer (Play, Voices, Orb permission).

**The UX bar in one sentence:** MaskedD currently *presenting* a feature is treated as shipping it. Users detect the difference within minutes — every phantom control, every lying status line, every infinite spinner compounds into "this app is fake/alive-and-dead." The fix is not more features; it is **honesty and restraint**: fewer controls, true states, truthful labels, and a reading surface that gets out of the reader's way.

---

*End of audit. Every referenced line was read and verified during this pass; call-graph claims (dead modules, missing callers) were verified by repository-wide greps. This document supersedes any earlier informal review and should drive the Phase 0–3 roadmap before any store submission or scale conversation.*