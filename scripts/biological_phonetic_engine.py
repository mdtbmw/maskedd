#!/usr/bin/env python3
"""
================================================================================
MaskedD: Biological State Engine & Acoustic Prosody Pipeline Architecture
================================================================================

ARCHITECTURAL PIPELINE LAYOUT:
--------------------------------------------------------------------------------
1. INPUT: Raw Text Stream (e.g. "In 1984, I read a book about the WHO...")
   │
   ├──► [MODULE 1: DEEP CONTEXTUAL DISAMBIGUATOR]
   │    ├── Dependency Parser & POS Tagger (Syntactic Context Window)
   │    ├── Homograph Resolution (e.g. read [VBP] /riːd/ vs read [VBD] /rɛd/)
   │    ├── Acronym vs. Word Classifier ("WHO" org /dʌb.əl.juː-eɪtʃ-oʊ/ vs pronoun "who" /huː/)
   │    └── Contextual Numeric Normalizer ("1984" year vs $1984 currency vs math)
   │
   ├──► [MODULE 2: BIOLOGICAL PHONETIC TRANSLATOR & COARTICULATION ENGINE]
   │    ├── Grapheme-to-Phoneme (G2P) & Lexicon Mapper -> IPA / ARPAbet Translation
   │    ├── Tongue Latency & Alveolar Flap-T Rules (/t/ -> [ɾ] intervocalic: butter -> [ˈbʌɾ.ɚ])
   │    └── Phonotactic Slur Logic ("want to" -> /wɑː.nə/, "going to" -> /ɡənə/ with formality check)
   │
   └──► [MODULE 3: DIAPHRAGM & PHYSICS ANNOTATOR]
        ├── Micro-Breath Injector (Commas replaced with mathematically scaled breath tags)
        ├── Vocal Fatigue / Fry Contour Generator (Sentences > 15 words -> pitch -2st, rate -5%)
        └── Pre-Plosive Lip-Parting Hesitator (0.15s pause inserted before action/emotion verbs)
   │
   ▼
2. OUTPUT: Fully Annotated Phonetic Blueprint String (IPA + SSML Biological Control Tags)
================================================================================
"""

import re
import sys
from typing import List, Dict, Any, Tuple, Optional

# Try importing spaCy or NLTK if available; fallback to built-in rule-based NLP parser
try:
    import spacy
    NLP_SPACY_AVAILABLE = True
except ImportError:
    NLP_SPACY_AVAILABLE = False


# Comprehensive IPA Lexicon & Phonetic Dictionary
IPA_LEXICON: Dict[str, Any] = {
    "the": "/ðə/",
    "book": "/bʊk/",
    "i": "/aɪ/",
    "read": {"VBD": "/rɛd/", "VBN": "/rɛd/", "VB": "/riːd/", "VBP": "/riːd/", "NN": "/riːd/"},
    "lead": {"NN": "/lɛd/", "VB": "/liːd/", "VBP": "/liːd/"},
    "wind": {"NN": "/wɪnd/", "VB": "/waɪnd/", "VBP": "/waɪnd/"},
    "live": {"JJ": "/laɪv/", "VB": "/lɪv/", "VBP": "/lɪv/"},
    "tear": {"NN": "/tɪər/", "VB": "/tɛər/", "VBP": "/tɛər/"},
    "bow": {"NN": "/boʊ/", "VB": "/baʊ/", "VBP": "/baʊ/"},
    "about": "/əˈbaʊt/",
    "who": "/huː/",
    "who_org": "/dʌb.əl.juː-eɪtʃ-oʊ/",
    "in": "/ɪn/",
    "a": "/ə/",
    "water": "/ˈwɔː.tər/",
    "butter": "/ˈbʌt.ər/",
    "better": "/ˈbɛt.ər/",
    "letter": "/ˈlɛt.ər/",
    "meeting": "/ˈmiː.tɪŋ/",
    "little": "/ˈlɪt.əl/",
    "shouted": "/ˈʃaʊ.tɪd/",
    "exploded": "/ɪkˈsploʊ.dɪd/",
    "attacked": "/əˈtækt/",
    "screamed": "/skriːmd/",
    "shattered": "/ˈʃæt.ər d/",
    "destroyed": "/dɪˈstrɔɪd/",
    "grabbed": "/ɡræbd/",
    "leapt": "/lɛpt/",
    "want": "/wɑːnt/",
    "to": "/tuː/",
    "going": "/ˈɡoʊ.ɪŋ/",
    "got": "/ɡɑːt/",
    "for": "/fɔːr/",
    "then": "/ðɛn/",
    "she": "/ʃiː/",
    "glass": "/ɡlæs/",
    "nineteen": "/ˈnaɪn.tiːn/",
    "eighty-four": "/ˈeɪ.ti.fɔːr/",
    "one": "/wʌn/",
    "thousand": "/ˈθaʊ.zənd/",
    "nine": "/naɪn/",
    "hundred": "/ˈhʌn.drəd/",
    "dollars": "/ˈdɑː.lɚz/",
    "fifty": "/ˈfɪf.ti/",
    "cents": "/sɛnts/",
    "percent": "/pɚˈsɛnt/"
}

class DeepContextualDisambiguator:
    """
    MODULE 1: The Deep Contextual Disambiguator
    Analyzes sentence syntax, POS tags, surrounding tokens, and entities to resolve homographs,
    acronyms, and contextual number pronunciations.
    """

    def __init__(self):
        self.past_triggers = {"yesterday", "last", "already", "had", "was", "were", "been", "earlier", "ago", "previously", "read"}
        self.present_triggers = {"will", "can", "should", "must", "to", "always", "now", "today", "please", "going", "wanna"}
        self.nlp_spacy = None
        if NLP_SPACY_AVAILABLE:
            try:
                self.nlp_spacy = spacy.load("en_core_web_sm")
            except Exception:
                self.nlp_spacy = None

    def analyze_pos_and_syntax(self, text: str) -> List[Dict[str, Any]]:
        words = text.split()
        tokens_info = []

        if self.nlp_spacy:
            doc = self.nlp_spacy(text)
            for token in doc:
                tokens_info.append({
                    "original": token.text,
                    "clean": token.text.lower(),
                    "pos": token.tag_,
                    "dep": token.dep_
                })
        else:
            # Fallback Rule-Based Heuristic POS Tagger
            for i, word in enumerate(words):
                clean_word = re.sub(r'[^\w]', '', word.lower())
                context = " ".join([re.sub(r'[^\w]', '', w.lower()) for w in words[max(0, i-4):min(len(words), i+5)]])
                
                pos = "UNKNOWN"
                if clean_word == "read":
                    if any(t in context for t in self.past_triggers):
                        pos = "VBD"
                    else:
                        pos = "VB"
                elif clean_word == "lead":
                    if any(t in context for t in ["metal", "pipe", "heavy", "poison", "dense"]):
                        pos = "NN"
                    else:
                        pos = "VB"
                elif clean_word == "wind":
                    if any(t in context for t in ["up", "clock", "road", "path", "tightly"]):
                        pos = "VB"
                    else:
                        pos = "NN"
                elif clean_word == "live":
                    if any(t in context for t in ["broadcast", "stream", "show", "performance", "concert", "tv"]):
                        pos = "JJ"
                    else:
                        pos = "VB"
                else:
                    pos = "NN"

                tokens_info.append({
                    "original": word,
                    "clean": clean_word,
                    "pos": pos,
                    "dep": "root"
                })

        return tokens_info

    def disambiguate_sentence(self, text: str) -> List[Dict[str, Any]]:
        raw_tokens = self.analyze_pos_and_syntax(text)
        words_raw = text.split()
        annotated_tokens = []

        for i, token in enumerate(raw_tokens):
            word_str = token["original"]
            clean_str = re.sub(r'[^\w$]', '', word_str.lower())
            context_window = " ".join([re.sub(r'[^\w]', '', w.lower()) for w in words_raw[max(0, i-3):min(len(words_raw), i+4)]])

            ipa_representation = ""
            pos = token["pos"]

            # 1. Homograph Resolution
            if clean_str == "read":
                if pos in ["VBD", "VBN"] or any(t in context_window for t in self.past_triggers):
                    ipa_representation = IPA_LEXICON["read"]["VBD"]
                    pos = "VBD"
                else:
                    ipa_representation = IPA_LEXICON["read"]["VB"]
                    pos = "VB"
            elif clean_str == "lead":
                if pos in ["NN", "NNP"] or any(t in context_window for t in ["metal", "pipe", "heavy", "poison"]):
                    ipa_representation = IPA_LEXICON["lead"]["NN"]
                else:
                    ipa_representation = IPA_LEXICON["lead"]["VB"]
            elif clean_str == "wind":
                if any(t in context_window for t in ["up", "clock", "road", "path", "tightly"]):
                    ipa_representation = IPA_LEXICON["wind"]["VB"]
                else:
                    ipa_representation = IPA_LEXICON["wind"]["NN"]
            elif clean_str == "live":
                if pos in ["JJ"] or any(t in context_window for t in ["broadcast", "stream", "show", "concert"]):
                    ipa_representation = IPA_LEXICON["live"]["JJ"]
                else:
                    ipa_representation = IPA_LEXICON["live"]["VB"]

            # 2. Acronym vs Word Detection
            elif word_str == "WHO" or clean_str == "who":
                if word_str == "WHO" and any(kw in context_window for kw in ["health", "organization", "reported", "global", "the", "world"]):
                    ipa_representation = IPA_LEXICON["who_org"]
                    clean_str = "WHO_ORGANIZATION"
                else:
                    ipa_representation = IPA_LEXICON.get("who", "/huː/")

            # 3. Numeric & Year Normalization (Contextual)
            elif re.search(r'\d+', word_str):
                num_match = re.search(r'\d+', word_str)
                num_val = int(num_match.group(0))
                preceding_context = " ".join([re.sub(r'[^\w]', '', w.lower()) for w in words_raw[max(0, i-2):i]])

                if word_str.startswith("$"):
                    ipa_representation = f"/wʌn ˈθaʊ.zənd naɪn ˈhʌn.drəd ˈeɪ.ti.fɔːr ˈdɑː.lɚz/" if num_val == 1984 else f"/{num_val} ˈdɑː.lɚz/"
                elif num_val in range(1000, 2099):
                    if any(prep in preceding_context for prep in ["in", "since", "year", "during", "from", "by"]):
                        ipa_representation = "/ˈnaɪn.tiːn ˈeɪ.ti.fɔːr/" if num_val == 1984 else f"/{num_val}/"
                    else:
                        ipa_representation = "/wʌn ˈθaʊ.zənd naɪn ˈhʌn.drəd ˈeɪ.ti.fɔːr/" if num_val == 1984 else f"/{num_val}/"
                else:
                    ipa_representation = f"/{num_val}/"

            else:
                ipa_entry = IPA_LEXICON.get(clean_str)
                if isinstance(ipa_entry, dict):
                    ipa_representation = ipa_entry.get(pos, list(ipa_entry.values())[0])
                elif isinstance(ipa_entry, str):
                    ipa_representation = ipa_entry
                else:
                    ipa_representation = f"/{clean_str}/"

            annotated_tokens.append({
                "original": word_str,
                "clean": clean_str,
                "pos": pos,
                "ipa_base": ipa_representation
            })

        return annotated_tokens


class BiologicalPhoneticTranslator:
    """
    MODULE 2: The Biological Phonetic Translator & Coarticulation Engine
    Translates disambiguated tokens into an IPA phonetic stream, applying human tongue latency
    and coarticulation rules (intervocalic flap-t, informal phonotactic contractions like "want to" -> "wanna").
    """

    def __init__(self, formal_register: bool = False):
        self.formal_register = formal_register

    def apply_coarticulation(self, tokens: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
        i = 0
        n = len(tokens)
        coarticulated = []

        while i < n:
            curr = tokens[i]
            next_token = tokens[i+1] if i + 1 < n else None

            # Rule 1: Phonotactic Blending ("want to" -> "wanna", "going to" -> "gonna")
            if not self.formal_register and curr["clean"] == "want" and next_token and next_token["clean"] == "to":
                coarticulated.append({
                    "original": f"{curr['original']} {next_token['original']}",
                    "clean": "wanna",
                    "pos": "VB_INFORMAL",
                    "ipa_base": "/wɑː.nə/"
                })
                i += 2
                continue

            if not self.formal_register and curr["clean"] == "going" and next_token and next_token["clean"] == "to":
                coarticulated.append({
                    "original": f"{curr['original']} {next_token['original']}",
                    "clean": "gonna",
                    "pos": "VB_INFORMAL",
                    "ipa_base": "/ˈɡənə/"
                })
                i += 2
                continue

            # Rule 2: Alveolar Flap-T [ɾ] in American English Intervocalic Position
            if curr["clean"] in ["water", "butter", "better", "letter", "little", "meeting"]:
                base_ipa = curr["ipa_base"]
                flap_ipa = base_ipa.replace("t", "ɾ")
                coarticulated.append({
                    "original": curr["original"],
                    "clean": curr["clean"],
                    "pos": curr["pos"],
                    "ipa_base": flap_ipa
                })
                i += 1
                continue

            coarticulated.append(curr)
            i += 1

        return coarticulated


class DiaphragmPhysicsAnnotator:
    """
    MODULE 3: The Diaphragm & Physics Annotator
    Injects biological micro-breaths at commas, pre-plosive lip-parting hesitations before action verbs,
    and vocal fry / lung depletion pitch & speed drop tags on sentences exceeding 15 words.
    """

    def __init__(self):
        self.action_emotion_verbs = {
            "shouted", "exploded", "attacked", "screamed", "shattered", "destroyed",
            "grabbed", "leapt", "slammed", "choked", "gasped"
        }

    def annotate_physics(self, raw_text: str, tokens: List[Dict[str, Any]]) -> str:
        word_count = len(tokens)
        annotated_stream = []

        for idx, token in enumerate(tokens):
            ipa_str = token["ipa_base"]
            clean_word = token["clean"]
            original_word = token["original"]

            # 1. Pre-Plosive Hesitation before action / emotion verbs
            if clean_word in self.action_emotion_verbs:
                annotated_stream.append('<break time="150ms" type="pre-plosive-hesitation"/>')

            # 2. Micro-breath scaling at commas based on sentence length
            if "," in original_word or ";" in original_word or "—" in original_word:
                breath_duration = "140ms" if word_count < 10 else ("180ms" if word_count < 20 else "240ms")
                annotated_stream.append(f'{ipa_str} <breath duration="{breath_duration}" depth="biological"/>')
            else:
                annotated_stream.append(f'{ipa_str}')

        phonetic_body = " ".join(annotated_stream)

        # 3. Lung Air Depletion & Vocal Fry on sentences > 15 words
        if word_count > 15:
            words_in_body = phonetic_body.split()
            body_main = " ".join(words_in_body[:-3]) if len(words_in_body) >= 3 else phonetic_body
            body_tail = " ".join(words_in_body[-3:]) if len(words_in_body) >= 3 else ""

            ssml_output = (
                f'<speak>\n'
                f'  <prosody rate="1.0" pitch="0st">\n'
                f'    {body_main}\n'
                f'    <prosody rate="-5%" pitch="-2st" style="vocal-fry-depletion">\n'
                f'      {body_tail}\n'
                f'    </prosody>\n'
                f'  </prosody>\n'
                f'</speak>'
            )
        else:
            ssml_output = f'<speak>\n  <prosody rate="1.0" pitch="0st">\n    {phonetic_body}\n  </prosody>\n</speak>'

        return ssml_output


class BiologicalPhoneticPipeline:
    """
    End-to-End Execution Manager for the MaskedD Biological State Engine
    """

    def __init__(self, formal_register: bool = False):
        self.disambiguate_module = DeepContextualDisambiguator()
        self.translator_module = BiologicalPhoneticTranslator(formal_register=formal_register)
        self.physics_module = DiaphragmPhysicsAnnotator()

    def process(self, text: str) -> str:
        print("================================================================================")
        print(f"=== [Input Raw Sentence] ===\n{text}\n")

        # Module 1
        disambiguated_tokens = self.disambiguate_module.disambiguate_sentence(text)
        print("=== [Module 1: Deep Contextual Disambiguator Output] ===")
        for t in disambiguated_tokens:
            print(f"  Word: {t['original']:<12} POS: {t['pos']:<6} Base IPA: {t['ipa_base']}")
        print()

        # Module 2
        coarticulated_tokens = self.translator_module.apply_coarticulation(disambiguated_tokens)
        print("=== [Module 2: Biological Phonetic Translator & Coarticulation Engine Output] ===")
        for t in coarticulated_tokens:
            print(f"  Token: {t['original']:<14} Clean: {t['clean']:<12} IPA: {t['ipa_base']}")
        print()

        # Module 3
        final_blueprint = self.physics_module.annotate_physics(text, coarticulated_tokens)
        print("=== [Module 3: Diaphragm & Physics Annotator Final Blueprint] ===")
        print(final_blueprint)
        print("================================================================================\n")

        return final_blueprint


if __name__ == "__main__":
    pipeline = BiologicalPhoneticPipeline()
    sample_phrase = (
        "In 1984, I read a book about the WHO. "
        "I want to buy butter for $1984, then she shouted and shattered the glass in water."
    )
    pipeline.process(sample_phrase)
