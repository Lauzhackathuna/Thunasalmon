"""Validate complete EN/FR keys and positional format arguments without extra dependencies."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1] / "shared/src/commonMain/composeResources"

def read(folder):
    strings = {}
    for file in sorted((root / folder).glob("*.xml")):
        for node in ET.parse(file).getroot().findall("string"):
            name = node.attrib["name"]
            assert name not in strings, f"Duplicate {folder}/{name}"
            strings[name] = "".join(node.itertext())
    return strings

en, fr = read("values"), read("values-fr")
# Proper names and native language names intentionally use the base resource fallback.
invariant_names = {"maya_name", "samir_name", "camille_name", "language_english", "language_french"}
assert not (en.keys() ^ fr.keys()) - invariant_names, f"Missing translations: {(en.keys() ^ fr.keys()) - invariant_names}"
for name in en.keys() & fr.keys():
    placeholders = lambda text: sorted(re.findall(r"%\d+\$[dsf]", text))
    assert placeholders(en[name]) == placeholders(fr[name]), f"Argument mismatch: {name}"
print(f"Validated {len(en.keys() & fr.keys())} EN/FR string pairs and their format arguments.")
