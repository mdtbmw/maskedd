#!/usr/bin/env python3
"""
MaskedD: Text Normalization & Acoustic Prosody Pipeline Executable
===================================================================
A standalone CLI utility for testing the MaskedD Biological State Engine.
"""

import sys
from biological_phonetic_engine import BiologicalPhoneticPipeline

def run_cli():
    print("MaskedD Biological Phonetic State Engine Pipeline Test")
    print("------------------------------------------------------")
    if len(sys.argv) > 1:
        text_input = " ".join(sys.argv[1:])
    else:
        text_input = (
            "In 1984, I read a book about the WHO. "
            "I want to buy butter for $1984, then she shouted and shattered the glass in water."
        )

    pipeline = BiologicalPhoneticPipeline()
    result_blueprint = pipeline.process(text_input)
    print("Execution complete. Output Blueprint ready for acoustic renderer.")

if __name__ == "__main__":
    run_cli()
