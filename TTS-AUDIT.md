# Deep TTS Subsystem Audit — MaskedD (remix-maskedd-by-daniel)

**Audience:** Engineering & product leadership. **Companion docs:** `detailed.md` (full 18-section architecture/security audit), `UX-AUDIT.md` (screen-level UX audit). This document is a complete, code-level audit of the text-to-speech subsystem: every engine, synthesizer, router, cache, normalizer, recorder, and the state machine behind them.

Every claim below was verified against the exact files in this checkout. **All 25 files in `app/src/main/java/com/example/tts/` were read in full**, plus `ApiKeyManager.kt` and `GeminiEmotionalToneAnalyzer.kt` (the AI layer feeding TTS), and the ViewModel/Screen wiring that controls playback.

---

## 1. Executive Verdict

The TTS subsystem is a stack of overlapping, partially-fabricated engines wrapped around exactly **two real capabilities**:

1. **Android system TTS** (`android.speech.tts`) — real, offline, used by two of the three engine types.
2. **ElevenLabs / Google TTS / Fish.audio HTTP calls** — real API code, but **non-functional in this build** because the API keys are never injected into `BuildConfig` (details in §3.1), and two of the three endpoints use the wrong key type. When the cloud path fails it silently falls back to system TTS while the UI keeps advertising an AI voice.

Around these two capabilities, the codebase presents: a "zero-downtime router" that nothing calls, "FireRedTTS-2"/"LongCat-AudioDiT" endpoints that do not exist, a "Kokoro offline neural" engine that is a rebrand of system TTS, a voice-clone feature whose failure path silently returns a pre-made voice, a noise gate that corrupts MP3 bytes, per-sentence "AI emotional analysis" that is a keyword regex on this build, and an error counter that counts its own normal operation as "illegal transitions".

**The single most important finding:** the UI's engine-status and engine-capability claims are not derived from engine state. Users are told "Masked AI Neural Voice Active" while the audio path is system TTS (or nothing). This is not a performance problem; it is a trust problem, and it explains the user report cluster "the AI voice stuff doesn't work."

---

## 2. Component Inventory — What Is Actually Live vs. Dead vs. Fabricated

| File | Lines | Status | Role |
|---|---|---|---|
| `SpeechEngine.kt` | 835 | **LIVE** (core) | Orchestrator: 3 playback paths, state machine, TTS wrap, karaoke loop |
| `CloudVoiceSynthesizer.kt` | 536 | **LIVE but broken by key pipeline** | ElevenLabs/Google/Fish synthesis, dual MediaPlayer, lookahead preload |
| `KokoroLocalTtsEngine.kt` | 222 | **LIVE — rebrand** | System TTS with "best" system voice auto-select; named "Kokoro", no ONNX |
| `InstantVoiceCloneUploader.kt` | 122 | **LIVE — dishonest fallback** | Real ElevenLabs `/voices/add` attempt; any failure → hardcoded Rachel, success UI |
| `VoiceCharacter.kt` (+Manager) | 251 | **LIVE — cosmetic** | 16 characters, 8 distinct voice IDs, personality fields never sent |
| `EnginePlaybackState.kt` | 69 | **LIVE** | State machine; Playing→Playing is illegal yet triggered every sentence |
| `AmbientMusicPlayer.kt` | 293 | **LIVE — real synthesis** | Procedural AudioTrack tones/noise (tracks are sounds, not named assets) |
| `AudioAtmosphereService.kt` | 51 | **LIVE** | Theme→track mapping |
| `AudioSessionManager.kt` | 93 | **LIVE — correct** | MediaPlayer lifecycle + session IDs |
| `LruAudioCacheManager.kt` | 77 | **LIVE — correct** | Room-backed 100 MB LRU |
| `AdaptiveNoiseGate.kt` | 106 | **LIVE — corrupts audio** | Byte-level "gating" of MP3 as if PCM (§3.5) |
| `TextNormalizationPipeline.kt` | 308 | **LIVE — destructive** | Aggressive rewrites of spoken text everywhere |
| `RespiratorySynthesisManager.kt` | 111 | **LIVE — problematic** | Injects literal `[gasp]`/`[sigh]` tokens into TTS payload text |
| `TtsDiagnosticLogger.kt` | 95 | **LIVE** | In-memory telemetry; counters conflate errors |
| `VoiceRecorder.kt` | 94 | **LIVE** | MediaRecorder; permission never requested at runtime |
| `ZeroDowntimeAudioRouter.kt` | 116 | **DEAD** | `getSpeechAudioWithZeroDowntime` has zero call sites (§3.6) |
| `FireRedLongCatSynthesizer.kt` | 80 | **DEAD / FABRICATED** | Endpoints don't exist; called only from the dead router |
| `SentientArticulationEngine.kt` | 174 | **DEAD for audio** | Blueprints/simulation only; UI sheet reads state; nothing feeds speech |
| `BiomechanicalVoiceEngine.kt` | 100 | **DEAD for audio** | Pure simulation; `calculateEffectivePitch/Rate` zero call sites |
| `PunctuationPacingEngine.kt` | 103 | **DEAD** | `analyzePacing` zero call sites |
| `RespiratorySynthesisEngine.kt` | 85 | **DEAD** | Called only by `EmotionalMarkupParser`, which is itself uncalled |
| `EmotionalMarkupParser.kt` | 52 | **DEAD** | No consumers |
| `ReadingMode.kt` | 65 | **LIVE** | 6 modes with pitch/speechRate multipliers |
| `DynamicFormatMode.kt` | 12 | **LIVE (UI)** | Aura bar modes; playback-effect unclear (see UX audit) |
| `GeminiEmotionalToneAnalyzer.kt` (ai/) | 118 | **LIVE — keyword fallback in practice** | Per-sentence LLM call only if Gemini key present |
| `ApiKeyManager.kt` (ai/) | 272 | **LIVE — broken pipeline** | Key pools empty on this build; plaintext prefs |

**Reachability proof (grep-verified):** `getSpeechAudioWithZeroDowntime` (router) is never invoked; `SpeechEngine.kt:487,505` only reads the router's *default* status text for display. `PunctuationPacingEngine.analyzePacing`, `BiomechanicalVoiceEngine.calculateEffectivePitch/Rate`, `EmotionalMarkupParser.analyzeAndInjectMarkup` have zero call sites. `SentientArticulationEngine.processLookahead` is invoked (`LyricReaderViewModel.kt:316`) but its blueprints are never consumed by any synthesis or playback function.

---

## 3. The Three Playback Paths — Walked Through Code

### 3.1 Path A — Cloud "Masked AI Voice" (`TtsEngineType.MASKED_AI_VOICE`, the DEFAULT)

Entry: `SpeechEngine.play()` → `playCloudPlayback()` (`SpeechEngine.kt:467`) → `cloudVoiceSynthesizer.synthesizeAndPlay()` (`CloudVoiceSynthesizer.kt:129`).

Per-sentence pipeline inside `fetchSpeechWithAutoRotation` (`CloudVoiceSynthesizer.kt:190-263`), in order:

1. **Rate limiter**: `ApiKeyManager.allowApiCall()` — global 60 calls/min (`ApiKeyManager.kt:251-271`); when throttled, an 800 ms delay is inserted before the request (`CloudVoiceSynthesizer.kt:191-193`). The limiter does NOT guard the Gemini tone call — double counting is possible when keys exist.
2. **Text pipeline**: `TextNormalizationPipeline.processFullPipeline(rawText)` (§4.3).
3. **Gemini tone analysis**: `GeminiEmotionalToneAnalyzer.analyzeSegmentTone()` (§3.4) — one LLM round-trip **per sentence** if a key exists.
4. **Respiratory artifacts**: `RespiratorySynthesisManager.processSentence()` (§4.4) — injects `[gasp]`/`[sigh]`/`[chuckle]` literal text.
5. **Voice lock**: `VoiceCharacterManager.selectedCharacter` → `elevenLabsVoiceId` (`CloudVoiceSynthesizer.kt:204-206`).
6. **Disk LRU**: MD5(voiceId+processedText) lookup in Room (`LruAudioCacheManager.kt:16-36`); on hit, file is passed through `AdaptiveNoiseGate.processAudioFile` and returned (`CloudVoiceSynthesizer.kt:208-212`).
7. **ElevenLabs** `POST /v1/text-to-speech/{voiceId}` — 3 attempts with key rotation (`CloudVoiceSynthesizer.kt:265-312`), `model_id = "eleven_turbo_v2_5"`.
8. **Google Neural** `texttospeech.googleapis.com/v1/text:synthesize` — **uses `ApiKeyManager.getActiveKey()` — the GEMINI key (`CloudVoiceSynthesizer.kt:315`)**. Gemini API keys are not Google Cloud TTS keys; this endpoint would reject them (403). This path can never succeed even with configured keys.
9. **Fish.audio** `POST https://api.fish.audio/v1/tts` with Bearer Fish key, 3 attempts (`CloudVoiceSynthesizer.kt:369-405`).
10. All-failed → `throw IllegalStateException("All neural voice API candidates exhausted")` → `listener.onErrorAndFallback` → `triggerAutoFallbackToDeviceTts()` + `startNativePlayback()` (`SpeechEngine.kt:534-537`).

**Why the cloud path is dead in this build:** `ApiKeyManager` loads key pools from `BuildConfig` fields `FISH_AUDIO_API_KEY`, `ELEVENLABS_API_KEY`, `GEMINI_API_KEY` via reflection (`ApiKeyManager.kt:57-73`). The Secrets plugin configuration does not generate these fields (verified in `detailed.md` §4), so all three pools stay empty and the getters return `""` (`ApiKeyManager.kt:116-154`). Requests are sent with empty API keys → 401/400 → rotation loops exhaust → fallback to system TTS. **On every launch, with every document, on every sentence.** Meanwhile, real keys exist committed in `.env`/`.env.example` but are never wired into the build.

**Resume/seek behavior:** `pause()` pauses the MediaPlayer mid-file (`CloudVoiceSynthesizer.kt:469-473`); `play()` on resume calls `synthesizeAndPlay`, which begins with `stopPlaybackOnly()` (`:135`) — destroying the paused player — and re-synthesizes the sentence from the top. **Pause→resume always restarts the current sentence from word one.** Same for `seekToWord` (`SpeechEngine.kt:724-740`: stop → update word → play).

**Sentence-boundary recursion bug:** `onPlaybackCompleted` → `playCloudPlayback()` recursively, which calls `transitionTo(EnginePlaybackState.Playing)` while the engine is still in `Playing`. `canTransitionTo` forbids `Playing→Playing` (`EnginePlaybackState.kt:63`), so **every sentence boundary after the first logs "Illegal transition blocked: Playing -> Playing"** to diagnostics (`SpeechEngine.kt:157-168`) and increments the "Blocked Illegal" counter the UI displays — while playback continues normally. The telemetry surface is therefore actively misleading (§3.7).

**"Lookahead" reality:** `preloadAhead` pre-fetches sentences N+1..N+3 into the in-memory audio cache and, once the next file is cached, `prepareNextPlayer()` attaches it via `setNextMediaPlayer` (`CloudVoiceSynthesizer.kt:506-520`) — but only after `playAudioFile` already called `start()` (`:424`), and only when the next file landed in cache before playback of the current began (`:166-175`). Under the sentence-by-sentence recursion, the DualPlayer frequently never engages. The "zero-latency, 0ms" claims in the header comment (`:31-36`) exceed what this code guarantees.

**Cleanup hazard:** `playAudioFile` unconditionally calls `stopPlaybackOnly()` at entry (`:417`), which releases the previous MediaPlayer **including one mid-`onCompletion`**; the completion listener of the old player then sets `mediaPlayer = null` on the NEW player's field (`:444-450`), potentially canceling the tracking job of the active sentence. The outer `synthesizeAndPlay` also already called `stopPlaybackOnly()` at `:135` — two unconditional stops per sentence, no ownership checks.

### 3.2 Path B — "Kokoro Offline AI" (`TtsEngineType.KOKORO_LOCAL_NEURAL`)

Entry: `playKokoroLocalPlayback()` (`SpeechEngine.kt:409`).

- The class comment says "using Android System Neural Voices, ONNX models, and circular utterance buffering" (`KokoroLocalTtsEngine.kt:28-31`). **There is no ONNX, no model file, no local neural runtime anywhere.** The engine constructs a plain `android.speech.tts.TextToSpeech`, filters `tts.voices` to offline English voices, sorts by `voice.quality`, and auto-selects the highest-quality system voice (`:74-92`).
- "Kokoro" is therefore a **branding overlay on the device TTS engine**. The `TtsEngineType` string "100% offline high-fidelity local neural TTS model" (`SpeechEngine.kt:30`) and status "Kokoro Offline Neural TTS Active" (`:429`) overstate what runs: the model is whatever the user's device has.
- The same recursive `Playing→Playing` illegal-transition logging applies (`.onSentenceCompleted` → recursion, `:446-458`).
- Error path: the listener implements only the **deprecated** `onError(utteranceId)` (`:131-141`), which modern TTS engines don't invoke (they report via `onError(utteranceId, errorCode)`); engine failures can be silently swallowed, leaving the loop stuck on a sentence. The `startNativePlayback` fallback at `SpeechEngine.kt:460-462` only fires if the deprecated callback actually fires.
- **Dual-engine conflict:** `SpeechEngine` and `KokoroLocalTtsEngine` each own a separate `TextToSpeech` instance; `playKokoroLocalPlayback` stops the SpeechEngine's TTS (`SpeechEngine.kt:413-414`) but `startNativePlayback` stops `tts` and `cloudVoiceSynthesizer` but **not** `kokoroLocalEngine` — a stray Kokoro utterance can overlap native playback.
- `speakRawText` (`:820-829`) — used by Socratic output — speaks through plain TTS regardless of the selected engine type; pending cloud playback is stopped first.

### 3.3 Path C — Native "Standard TTS" (`DEVICE_NATIVE_TTS`) and the fallback path

Entry: `startNativePlayback()` (`SpeechEngine.kt:542`). This is both the explicit "Standard TTS" engine and the fallback destination for the cloud path.

- Queues current sentence (`QUEUE_FLUSH`) + next (`QUEUE_ADD`) via the circular utterance-ID buffer (`:569-573`); `onDone` enqueues N+2 (`:224-229`). `CircularUtteranceBuffer` (capacity 4) is a bookkeeping queue of utterance IDs, not an audio queue — the class comment at `:56-59` overstates it.
- Word karaoke: `onRangeStart` maps character offsets proportionally from `spokenText` length to original text length (`SpeechEngine.kt:296-308`); a fallback 60 fps loop (`:582-616`) advances words using a **heuristic duration** `wordText.length * 55ms + 110ms` (`:631`) whenever range callbacks are absent for >250 ms. Two independent word-trackers race; the heuristic drifts against real audio (deviations logged as TIMING_OFFSET at `:245-258`).
- The silent-fallback lie: `triggerAutoFallbackToDeviceTts` sets `isAutoFallbackActive = true` and a status message **"Masked AI active (sub-sentence fallback: …)"** (`SpeechEngine.kt:802-812`). It is not sub-sentence; the entire sentence restarts via native TTS. The header continues advertising the AI engine (§3.8).

### 3.4 The "AI emotional analysis" — actually a keyword matcher on this build

`GeminiEmotionalToneAnalyzer.analyzeSegmentTone` calls `https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent` (`GeminiEmotionalToneAnalyzer.kt:44`) with the Gemini key. With no key (this build), `apiKey.isBlank()` → `fallbackKeywordAnalysis` (`:39-41`): a deterministic keyword lookup mapping "joy"/"whisper"/"gasp" etc. to stability/style and artifact hints. On any network error, same fallback (`:96-100`).

Consequences:
- **When keys are missing (shipped state):** zero LLM calls; "AI tone analysis" is a regex — zero cost, zero intelligence; diagnostics still log it as AI processing.
- **When keys exist:** one blocking LLM call per sentence before synthesis begins (`CloudVoiceSynthesizer.kt:198-202`) — serial latency of ~1–3 s per sentence, defeating the "zero-latency" claim, and unbounded cost for long documents. The model name `gemini-3.5-flash` is not a verifiable model ID at this writing; an invalid model returns 404 → silent fallback.
- The tone result feeds only `stability/style/similarityBoost` into ElevenLabs voice settings and artifact selection — it does not change voice or pacing, and it is dropped entirely on the Google/Fish fallback paths (fixed defaults `1.0/0.0`, `CloudVoiceSynthesizer.kt:330-331`).

### 3.5 AdaptiveNoiseGate — byte-level corruption of encoded audio

`AdaptiveNoiseGate.processAudioFile` reads the **entire MP3 file as bytes** and applies a cosine fade to individual bytes (`AdaptiveNoiseGate.kt:85-104`), with the comment "Approximate average for MP3 audio frames" (`:90`) proving the PCM/MP3 conflation. There is no MP3 decoding (no MediaCodec, no mp3 parser). Multiplying encoded MP3 bytes by a fade factor **corrupts the compressed stream**; output files play as garbage or fail to decode.

This gate runs on **every** cloud file, both cache-hit and fresh (`CloudVoiceSynthesizer.kt:208-212, 224-225, 238-239, 252-253`), and the corrupted output is what gets saved to the LRU cache (`:225`) — so poisoning persists across sessions.

**Verdict: the "AI voice" path, even with valid keys, produces corrupted audio through this gate.** This is the strongest single code-level explanation for "the AI voices don't work."

### 3.6 ZeroDowntimeAudioRouter & FireRed/LongCat — marketed, unreachable, and fabricated

- `ZeroDowntimeAudioRouter.getSpeechAudioWithZeroDowntime` (3 tiers, "sub-50ms failover" per class comment `:29-31`) has **zero call sites**; only its default `CircuitBreakerStatus` text is displayed ("Zero Downtime Circuit Breaker Active" — read at `SpeechEngine.kt:487`), and `activeTier` is never mutated by any reachable code.
- `FireRedLongCatSynthesizer` posts to `https://api.firered.ai/v2/speech/synthesize` and `https://api.longcat.ai/v1/audio/diffusion` (`FireRedLongCatSynthesizer.kt:17-18`) with `Authorization: Bearer ${ApiKeyManager.getActiveKey()}` — a **Gemini key** against endpoints that do not exist publicly (FireRedTTS is an open-source model; there is no `api.firered.ai` service). Every call fails; exception logged, null returned. Only reachable via the dead router. This is fabricated functionality presented as Tier 2 of "guaranteed zero speech narration downtime."

### 3.7 Diagnostic telemetry — counters that mean the opposite

`TtsDiagnosticLogger.log` increments `_illegalTransitionCount` whenever `eventType == ILLEGAL_TRANSITION || isError` (`TtsDiagnosticLogger.kt:71-73`). The reader-facing dashboard labels this card "Blocked Illegal" (`TtsDiagnosticDashboardModal.kt:204-219`). Because:
- every cloud/kokoro sentence boundary logs a false `Playing→Playing` illegal transition (§3.1), and
- any error log (key-rotation failures, underflow events) also increments the same counter,

**the dashboard's headline numbers inflate during perfectly normal playback** and conflate errors with blocked transitions. "Avg Sync Offset" is computed only from native `onRangeStart` offsets, ignoring cloud playback entirely. Logs are in-memory only, capped at 150 entries (`:83`), no persistence — telemetry vanishes on process death.

### 3.8 The status-message lie chain (user-visible)

| Where | Claim (code) | Reality |
|---|---|---|
| `PlaybackProgress.engineStatusMessage` default | "Masked AI Neural Voice Active" (`SpeechEngine.kt:49`) | No cloud engine active on shipped build |
| Kokoro path | "Kokoro Offline Neural TTS Active" (`:429`) | System TTS with device voice |
| Native path | "Standard TTS Active (100% Offline)" (`:788`) | True — the only claim that is |
| Cloud fallback | "Masked AI active (sub-sentence fallback: …)" (`:810`) | Whole-sentence native TTS; "sub-sentence" false |
| Router display | "Zero Downtime Circuit Breaker Active" (`ZeroDowntimeAudioRouter.kt:25`, read at `SpeechEngine.kt:487`) | No breaker exists; nothing is routed |
| Voice switch sheet | "Real-time switch without audio pause" (`VoiceCharactersModalBottomSheet.kt:102`) | Selecting a character calls `clearCache()` (`:221`), destroying queued audio |

Every consumer-facing claim about the AI voice is static text, not derived from `EnginePlaybackState` or the winning path. **The fix is to derive the status from the engine state machine and the winning engine, and to label native fallback plainly.**

---

## 4. Feature-Level Findings

### 4.1 Voice characters — 16 names, 8 voices, zero personality flow

- `VoiceCharacterManager.defaultCharacters` (`VoiceCharacter.kt:27-200`) defines 16 characters that reuse **8 distinct ElevenLabs IDs**:
  - `21m00Tcm4TlvDq8ikWAM` (Rachel) → Amina, Rachel, Saffron — three "different" storytellers, identical base voice
  - `AZnzlk1XvdvUeBnXmlld` (Domi) → Leo, Domi, Aria
  - `ErXwobaYiN019PkySvjV` (Kofi) → Kofi, Marcus
  - `EXAVITQu4vr4xnSDxMaL` (Bella) → Bella, Maya
  - `TxGEqnHWrfWFTfGW9XjX` (Zayn) → Zayn, Oliver
- **`expressivePersonalityPrompt` is never sent anywhere.** The ElevenLabs payload contains only `text`, `model_id`, and `voice_settings` (stability/similarity_boost/style) (`CloudVoiceSynthesizer.kt:275-284`). The elaborate per-character narration instructions in `VoiceCharacter` are dead data.
- **`defaultSpeed`/`defaultPitch` per character are never applied** — `fetchSpeechWithAutoRotation` never reads them; the Google path hardcodes `speakingRate: 1.0` (`:330`); the native path applies only mode + custom parameters (`SpeechEngine.applyVoiceParameters`, `:642-656`).
- **Nigerian accent promise:** "Amina — warm rhythmic West African storyteller … Nigerian Accent Reader" (`VoiceCharacter.kt:31-36`) is backed by voice ID `21m00Tcm4TlvDq8ikWAM` — ElevenLabs' *Rachel*, a US-English voice. The UI's accent filter ("🇳🇬 Nigerian / African", `VoiceCharactersModalBottomSheet.kt:53`) therefore matches voices that do not speak with the advertised accent.
- Cloned voices (`addCustomClonedVoice`, `VoiceCharacter.kt:228-250`, and the `VoiceCloneModalDialog` fallback `VoiceCharactersModalBottomSheet.kt:552`) collapse to the same Rachel ID when upload fails; the custom-voice list then contains duplicates of Rachel labeled as unique clones.

### 4.2 Voice cloning — real attempt, dishonest success

`InstantVoiceCloneUploader.uploadVoiceClone` (`InstantVoiceCloneUploader.kt:38-119`) **does** attempt a genuine `POST https://api.elevenlabs.io/v1/voices/add` multipart upload (30 s timeouts, `:32-36`). Defects:

1. **Silent fallback:** on any HTTP failure (and empty key on this build → 401) or network exception, `finalVoiceId` becomes `"21m00Tcm4TlvDq8ikWAM"` (`:88`) — the pre-made Rachel voice. The caller receives a normal `VoiceCharacter` marked `isCustomCloned = true`, and the UI shows "Voice Profile '…' Created!" / "Mapped to Room database & ready for playback." (`InstantVoiceCloneModal.kt:116,124`) — while the user's recording was **never uploaded**.
2. The request body claims `audio/m4a` via `asRequestBody("audio/m4a".toMediaType())` (`:60`) while the recorder writes an `.m4a` file with AAC — acceptable to ElevenLabs, but the pairing is hardcoded rather than derived from the file, and no format check exists (a corrupt/empty recording is uploaded blindly; the server rejects it → silent Rachel fallback).
3. The Room insert swallows exceptions (`:111-116`); the in-memory manager list is the source of truth, so "saved to database" is not guaranteed.
4. No retry, no partial state, no "using fallback voice" disclosure. `RECORD_AUDIO` is never requested at runtime (UX audit §3.7), so on Android 12+ the recorder typically yields nothing or a silent file — and the "✅ Sample Recorded" state (`VoiceCharactersModalBottomSheet.kt:471`) lies about usable audio.

### 4.3 Text normalization — algorithmic mispronunciations by design

`TextNormalizationPipeline` runs on **every** sentence in native and cloud paths (`SpeechEngine.kt:281`, `CloudVoiceSynthesizer.kt:194`). Verified defects:

- **`read` mispronunciation:** the homograph rule defaults "read" → "red" (past tense) whenever no trigger word matches within ±4 tokens (`TextNormalizationPipeline.kt:96`) — "I read every morning" (present habit) will be spoken "red".
- **Aggressive coarticulation slurs** (`:242-261`): `water→wadder`, `letter→ledder`, `butter→budder`, `little→liddle`, `want to→wanna`, `did you→didja`, `give me→gimme`, `don't know→dunno`, `should have→shoulda`, `sort of→sorta` — applied unconditionally, including to formal/academic/quoted text.
- **Number & time rewriting:** every standalone `\b\d{1,4}\b` becomes words (`:215-218`) — "Chapter 12" → "Chapter twelve", but also "iPhone 15" → "iPhone fifteen", "API 2.0" → "A P I two point zero" (acronym rule at `:233` runs before numbers? No — numbers run first, so "API 2" becomes "API two"); time pattern `\b(\d{1,2}):(\d{2})\b` (`:190-196`) converts "12:30" → "twelve thirty" even in code listings or transcripts where it should stay literal. All rewriting is unconditional on document type.
- **SSML generation is dead:** `generateSsml` (`:272-289`) produces a `<speak>` document with micro-breaks, but `ssmlOutput` is never used — native `speak()` gets plain text (`SpeechEngine.kt:283`), cloud gets plain JSON text. The "SSML Micro-Pause Injection" claim in the class comment (`:17`) is unimplemented in the audio path.
- **Word-sync drift:** rewriting changes string lengths between source and spoken text; `onRangeStart` proportionally maps char offsets (`SpeechEngine.kt:296-299`, `KokoroLocalTtsEngine.kt:149-151`). The more rewrite tokens a sentence contains, the more the karaoke highlight drifts from the audio.

### 4.4 Respiratory artifact injection — literal bracket-tokens in payload

`RespiratorySynthesisManager` inserts **literal text** `" [gasp] "`, `" ... [sigh] ... "`, `" [chuckle] "` into the text sent to ElevenLabs/Google/Fish (`RespiratorySynthesisManager.kt:9-12,67-79`; consumed at `CloudVoiceSynthesizer.kt:202`). The cloud wrapper strips only HTML tags before the request (`CloudVoiceSynthesizer.kt:196`), so the bracket tokens reach the provider verbatim and will be spoken or garbled. Meanwhile `RespiratorySynthesisEngine` + `EmotionalMarkupParser` (the more elaborate variants) are dead code — two competing implementations of the same idea, one live (defective) and one dead.

Sentence micro-pause logic (`RespiratorySynthesisManager.kt:91-97`) also inserts mid-sentence commas into long clause-free sentences, further corrupting spoken text and offset mapping.

### 4.5 Ambient audio — genuine, but claims exceed content

- The ambient tracks are procedurally synthesized in real time: binaural-style beats (200/210 Hz for ALPHA, 150/156 Hz THETA, 240/280 Hz GAMMA), chord pads, filtered noise for "Ocean Waves & Rain" (`AmbientMusicPlayer.kt:183-247`). **This is real live synthesis** and functions offline — the one marketing claim that holds.
- "ZEN_BOWLS" is not singing bowls; "OCEAN_RAIN" is white noise + 110 Hz hum; "SOLFEGGIO" is three sine oscillators (`:202-208`). Product names promise more than the math delivers.
- **Default volume 70%** (`:35`) with ducking to 28% only while `setTtsActive(true)` is set (`:79-81,165-169`). Ambient playback starts on every play (cloud & native). No audio-focus handling on the ambient side; only the engine requests focus (`SpeechEngine.kt:350-374`) — ambient music can therefore keep playing over other apps' focus changes when paused (focus is abandoned but ambient is not stopped in `pause()`; `:670-690` stops monitoring but the ambient track keeps playing).
- `AudioAtmosphereService.syncWithReadingMood` silently switches themes when the reading mode changes (`AudioAtmosphereService.kt:42-50`) — a user who picked "Zen bowls" gets "Action … Cinematic Beats" after switching to Fast Reader, without consent.

### 4.6 Concurrency, performance, and battery

- **60 fps karaoke loop** (`delay(16L)`, `Dispatchers.Default`) for the entire playback (`SpeechEngine.kt:582-616`), pushing `_progressState` copies of 16 floats per frame; the cloud path adds a MediaPlayer tracking loop at 33 ms (`CloudVoiceSynthesizer.kt:431-442`) plus recursive `onPlaybackProgress` callbacks — up to three concurrent high-frequency UI-state writers when cloud is playing.
- **Per-word seek churn:** the UI calls `seekToWord` from slider drags (`LyricReaderScreen.kt:382,422,451,508,534`) with no throttle; each call is `stop()` + `play()` (§3.1), i.e. a full tear-down/build-up — on the cloud path, a fresh network synthesis per seek. The RSVP sprint fires `onWordSeek` **per word at 350 WPM default** (`KineticSprintReaderView.kt:41,60-64`) — ~5.8 full stop+play cycles per second.
- **MediaPlayer churn:** one MediaPlayer per sentence (plus a next-player), with `releaseAllActiveMediaPlayers()` on each creation (`AudioSessionManager.kt:44`) — roughly one full MP3 decode lifecycle per sentence; a 3-hour listen at ~40 s/sentence instantiates ~270 players.
- **Every catch swallows:** dozens of empty `catch (_: Exception) {}` blocks (e.g. `CloudVoiceSynthesizer.kt:86`, `KokoroLocalTtsEngine.kt:91,99`, `SpeechEngine.kt:272,371`) convert every failure mode into "no audio, no error" — the top user complaint.
- **Five separate `OkHttpClient` instances** (CloudVoiceSynthesizer, FireRed, GeminiTone, CloneUploader, plus others under `ai/`), each with its own connection pool.

### 4.7 Security notes (TTS-adjacent; full treatment in `detailed.md` §4)

- Committed live keys in `.env`/`.env.example` (Gemini `AIzaSy…`, Fish `sk-fish-…`, ElevenLabs `sk_…`).
- Key pools persisted in **plaintext SharedPreferences** `masked_d_api_keys_pref_v2` (`ApiKeyManager.kt:37-40,99`) — every user-added key is exfil-ready from app-data backups.
- Rotation with an empty pool: `rotatePool` resets all statuses to UNTESTED when exhausted (`ApiKeyManager.kt:203-213`) and returns `""` forever — so the cloud path performs **9 pointless HTTPS round-trips per sentence (3 providers × 3 attempts) with empty keys** before falling back: latency and battery burn on every launch.
- `VoiceRecorder` writes user audio to `cacheDir/voice_clones` with no retention policy; the CloneUploader persists the absolute path in Room (`InstantVoiceCloneUploader.kt:106`) — cache eviction breaks later references.

---

## 5. Data Flow Diagram (as-built)

```
UI (LyricReaderScreen.kt / LibraryScreen.kt / modals)
   │  play() pause() seekToWord() setSpeed() setPitch() setMode() setEngineType() setAmbientTrack()
   ▼
LyricReaderViewModel (speechEngine direct calls) ──► SentientArticulationEngine.processLookahead (UI-only; not fed to audio)
   ▼
SpeechEngine
   ├─ MASKED_AI_VOICE ──► CloudVoiceSynthesizer.synthesizeAndPlay
   │        │                ├─ TextNormalizationPipeline.processFullPipeline (aggressive rewrite)
   │        │                ├─ GeminiEmotionalToneAnalyzer.analyzeSegmentTone  (LLM or keyword fallback)
   │        │                ├─ RespiratorySynthesisManager.processSentence     (injects [gasp] literal)
   │        │                ├─ LruAudioCacheManager (Room, 100 MB, MD5(voice:processedText))
   │        │                ├─ AdaptiveNoiseGate.processAudioFile  ✗ corrupts MP3 (no decode)
   │        │                ├─ ElevenLabs (empty key → 401 → rotate ×3)
   │        │                ├─ Google TTS (Gemini key — always 403)
   │        │                └─ Fish.audio (empty key → 401 → rotate ×3)
   │        │                        │ fail
   │        ▼                        ▼
   │   startNativePlayback ◄──── triggerAutoFallbackToDeviceTts ("Masked AI active (sub-sentence fallback)")  ← message lies
   ├─ KOKORO_LOCAL_NEURAL ─► KokoroLocalTtsEngine (System TTS, best offline voice; recursion logs Playing→Playing)
   └─ DEVICE_NATIVE_TTS ──► startNativePlayback (System TTS + heuristic 60fps karaoke + onRangeStart mapping)

Dead islands: ZeroDowntimeAudioRouter (label-only), FireRedLongCatSynthesizer (fake endpoints),
PunctuationPacingEngine, EmotionalMarkupParser, RespiratorySynthesisEngine, BiomechanicalVoiceEngine (sim),
most of SentientArticulationEngine (blueprints never consumed)
```

---

## 6. Severity-Registered Findings

| # | Finding | File:Line | Severity |
|---|---|---|---|
| 1 | Cloud keys empty on shipped build → all "AI voice" silently falls back to native + 9 pointless HTTPS round-trips/sentence | `ApiKeyManager.kt:57-73,116-154`; Secrets wiring in `detailed.md` §4 | **P0** |
| 2 | Status/engine claims not derived from state; "Masked AI Active…" shown while playing native | `SpeechEngine.kt:49,787-796,802-812`; `SpeechEngine.kt:487` | **P0** |
| 3 | AdaptiveNoiseGate corrupts MP3 by byte-level fade — cloud audio garbage even with valid keys, persists into cache | `AdaptiveNoiseGate.kt:85-104`; consumed `CloudVoiceSynthesizer.kt:208-225` | **P0** |
| 4 | Voice clone silently falls back to Rachel on any failure with success UI | `InstantVoiceCloneUploader.kt:88-118`; `InstantVoiceCloneModal.kt:116-124`; `VoiceCharactersModalBottomSheet.kt:552` | **P0** |
| 5 | Playing→Playing logged as illegal every sentence (cloud & kokoro); diagnostic counter inflates during normal playback | `SpeechEngine.kt:467-540,409-465`; `EnginePlaybackState.kt:63`; `TtsDiagnosticLogger.kt:71-73` | **P1** |
| 6 | Pause/resume and seek restart the current sentence from the top (no intra-sentence resume) | `CloudVoiceSynthesizer.kt:135`; `SpeechEngine.kt:724-740` | **P1** |
| 7 | Text pipeline mispronounces ("read"→"red" default; water→wadder; unconditional number rewriting) and breaks karaoke mapping | `TextNormalizationPipeline.kt:93-98,242-261,215-218` | **P1** |
| 8 | Literal `[gasp]`/`[sigh]`/`[chuckle]` bracket tokens sent to TTS providers | `RespiratorySynthesisManager.kt:8-13,67-79`; `CloudVoiceSynthesizer.kt:196-202` | **P1** |
| 9 | Google TTS called with a Gemini key — can never succeed | `CloudVoiceSynthesizer.kt:314-318` | **P1** |
| 10 | Fabricated FireRed/LongCat endpoints; dead router marketed as "guaranteed zero downtime" | `FireRedLongCatSynthesizer.kt:16-19`; `ZeroDowntimeAudioRouter.kt:25,48` | **P1** |
| 11 | "Kokoro" = system TTS rebrand; no ONNX model exists; deprecated-only error callback can hang the loop | `KokoroLocalTtsEngine.kt:28-31,52-61,74-92,131-141` | **P1** |
| 12 | Characters: 16 names → 8 voices; personas/prompts/character speeds never applied; Nigerian accent claim false | `VoiceCharacter.kt:27-200`; `CloudVoiceSynthesizer.kt:206,275-284,330` | **P1** |
| 13 | Per-sentence blocking Gemini call when keys exist (latency/cost); keyword regex when not | `GeminiEmotionalToneAnalyzer.kt:37-100`; `CloudVoiceSynthesizer.kt:198-202` | **P1** |
| 14 | Unthrottled seek churn (slider + RSVP sprint at ~5.8 full stop+play cycles/s) | `LyricReaderScreen.kt:382,422,451,508,534`; `KineticSprintReaderView.kt:60-64` | **P1** |
| 15 | MediaPlayer lifecycle races across two unconditional `stopPlaybackOnly()` calls per sentence | `CloudVoiceSynthesizer.kt:135,417,444-450` | **P1** |
| 16 | Ambient auto-theme switch without consent; 70% default volume; ambient keeps playing on pause | `AudioAtmosphereService.kt:42-50`; `AmbientMusicPlayer.kt:35`; `SpeechEngine.kt:670-690` | **P2** |
| 17 | Diagnostics: in-memory only, 150-entry cap, counter conflation (isError OR illegal), cloud offsets unmeasured | `TtsDiagnosticLogger.kt:71-83` | **P2** |

---

## 7. Remediation Roadmap

### Phase 0 — Make the truth the product (P0; ~2–3 weeks)

1. **Keys or no keys, one honest path.** Choose: (a) wire the committed keys into `BuildConfig` and fix the Google path to use a real Google Cloud TTS credential + api key in query, or (b) degrade gracefully to "Device TTS" with a clear, user-visible engine chip. Status text must derive from the winning engine — never from a static string (`SpeechEngine.kt:49,787-812`).
2. **Delete AdaptiveNoiseGate from the audio path** (or reimplement it on decoded PCM via MediaCodec/ExoPlayer-effect). Deleting is correct: providers already produce clean MP3.
3. **Voice clone honesty:** surface upload failure (HTTP code, „fallback voice used"), never mark Rachel as a clone; require explicit user confirmation before saving a fallback profile; add runtime `RECORD_AUDIO` permission flow first.
4. **Neutral state machine:** allow `Playing→Playing` (or introduce `ReSynthesizing`), or have recursion go through `Idle`/`Paused` — remove the per-sentence illegal-transition spam so telemetry means something.
5. **Intra-sentence resume:** implement pause/resume at the MediaPlayer position (`pause()`/`resume()` already exist — `CloudVoiceSynthesizer.kt:469,500` — the bug is only that `synthesizeAndPlay` re-enters via `stopPlaybackOnly`; gate re-entry on `isPaused`).
6. **Normalization audit:** drop the unconditional coarticulation/list rule applications or gate them behind an explicit „casual prose" toggle; fix the `read` default; verify with a pronunciation test corpus; delete the unused SSML generator or actually use it.

### Phase 1 — Remove the theater (P1; ~2–3 weeks)

7. Delete or gate behind `BuildConfig.DEBUG`: `ZeroDowntimeAudioRouter`, `FireRedLongCatSynthesizer` (endpoints don't exist), `PunctuationPacingEngine`, `EmotionalMarkupParser`, `RespiratorySynthesisEngine`, `BiomechanicalVoiceEngine`, and the Sentient/Respiratory blueprint machinery — or wire them for real.
8. One respiratory-artifact mechanism only, and one that emits *pause/SSML*, never literal bracket tokens.
9. Character data honesty pass: dedupe the 16→8 voice IDs; either implement `expressivePersonalityPrompt` (send it to a provider that supports it, e.g. ElevenLabs' `text`-based style) or delete it; apply `defaultSpeed/defaultPitch`; correct the Nigerian-accent claims.
10. Merge the three state writers into one playback clock (single `LaunchedEffect` owning word index, progress fraction, and amplitudes); throttle all `seekToWord` calls (≥150 ms debounce + coalesce last-word).
11. Kokoro: either remove the rebrand (call it "Best Device Voice") or actually embed an ONNX/Sherpa-ONNX runtime; implement the non-deprecated `onError(utteranceId, errorCode)` and a watchdog that hands off to native after N seconds of silence.
12. Ambient: default volume 50%, respect audio focus on the ambient track, stop ambient on pause, and remove `syncWithReadingMood` auto-switching (or make it opt-in).

### Phase 2 — Observability & hardening (P2; ongoing)

13. Diagnostics: persist logs (file/Room), separate error counter from illegal-transition counter, measure cloud-path sync via audio position; expose as read-only `debug` screen.
14. Single shared `OkHttpClient`; per-request timeouts already present; add retry budgets and per-provider circuit breakers that actually break (times-pan + cooldown, not just a label).
15. Error surfacing: replace empty catches in the playback path with typed failures → UI error states („No network — switched to device voice") per UX audit §9.
16. Unit-test harnesses: pronunciation corpus tests for `TextNormalizationPipeline`; state-machine transition tests; a fake-provider integration test asserting that with blank keys, the app *reports* device TTS.

---

## 8. Bottom Line

- Real capabilities: system TTS (2 of 3 engine types) and an ambient synthesizer. Everything else is scaffolding with marketing copy attached.
- The cloud path is dead on the shipped build for three independent reasons — empty keys, wrong key type on Google, and byte-level audio corruption — any one of which is fatal; users are told it works.
- The subsystem's error handling is designed to hide failures rather than surface them: empty catches, static status strings, counters that count normal operation as violations.
- Fix the truth layer first (status derivation, honest fallback, clone honesty, state machine spam, noise-gate removal), then decide which of the "AI" claims to actually build — with valid keys, ElevenLabs synthesis through a clean pipeline (minus the gate, minus bracket tokens, minus unconditional rewriting) is genuinely achievable and would likely satisfy the product's core promise.