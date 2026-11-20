"""MineScore's translations.

English is the source: assets/minescore/lang/en_us.json (written by gen_resources.py), the English the code
shows through Tr (found here in the code, plus the list TranslationSourceTest writes to
common/build/minescore-text.json during the build), and the Manual (assets/minescore/minescore/manual/en_us.json).

tools/lang/en_source.json numbers every distinct English string ("0042": "Insert before"); each language's
tools/lang/<file>.json gives the same numbers in that language. gen_resources.py turns them into the game's
language files and translated Manuals. Anything a language leaves out stays English, and strings that read the
same everywhere (MIDI, Hz, dynamics like mf) aren't numbered at all.

    python tools/i18n.py           what's missing or out of date in each language
    python tools/i18n.py --number  number any new English (after changing the mod's text)
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
JAVA = ROOT / "common" / "src" / "main" / "java"
ASSETS = ROOT / "common" / "src" / "main" / "resources" / "assets" / "minescore"
DATA_TEXT = ROOT / "common" / "build" / "minescore-text.json"
LANG_DIR = Path(__file__).resolve().parent / "lang"
SOURCE = LANG_DIR / "en_source.json"

# Minecraft language code -> the translation file it uses. Variants share their main language's.
LANGUAGES = {
    "de_de": "de_de", "es_es": "es_es", "es_mx": "es_es", "es_ar": "es_es", "fr_fr": "fr_fr", "fr_ca": "fr_fr",
    "it_it": "it_it", "pt_br": "pt_br", "pt_pt": "pt_br", "nl_nl": "nl_nl", "pl_pl": "pl_pl", "ru_ru": "ru_ru",
    "uk_ua": "uk_ua", "tr_tr": "tr_tr", "sv_se": "sv_se", "ja_jp": "ja_jp", "ko_kr": "ko_kr", "zh_cn": "zh_cn", "zh_tw": "zh_tw",
}

# The same in every language.
UNTRANSLATED = {"MIDI", "NBS", "WAV", "WSOLA", "MineScore", "FE/t", "Hz", "dB", "MB", "RPM", "SU/RPM", "D.C.", "D.S.", "D.C. al Coda",
                "D.C. al Fine", "D.S. al Coda", "D.S. al Fine", "Coda", "Fine", "Segno", "To Coda", "f", "ff", "fff", "fp", "mf", "mp",
                "p", "pp", "ppp", "sf", "sfz", "s", "L", "R", "M", "EQ", "1/4", "1/8", "1/16", "2×12", "%s (%s)", "%s: %s",
                "%s / %s FE", "MineScore: 16×", "Ch"}


def text_key(english):
    """Must match com.minescore.Tr.key (Java's String.hashCode for the overflow suffix)."""
    slug = re.sub(r"[^a-z0-9]+", "_", english.lower()).strip("_")

    def java_hash(s):
        h = 0
        for unit in s.encode("utf-16-be").hex(" ", 2).split():
            h = (31 * h + int(unit, 16)) & 0xFFFFFFFF
        return format(h, "x")

    if len(slug) > 72:
        slug = slug[:72] + "_" + java_hash(english)
    return "minescore.text." + (slug or java_hash(english))


def code_text():
    """English written in the code through Tr.s/Tr.c/Tr.en, plus the data list from the build."""
    found = set()
    literal = re.compile(r'Tr\.(?:s|c|en)\(\s*"((?:[^"\\]|\\.)*)"')
    for f in JAVA.rglob("*.java"):
        for m in literal.finditer(f.read_text(encoding="utf-8")):
            found.add(json.loads('"' + m.group(1) + '"'))
    if DATA_TEXT.exists():
        data = json.loads(DATA_TEXT.read_text(encoding="utf-8"))
        wrong = [t for t, k in data.items() if text_key(t) != k]
        if wrong:
            raise SystemExit(f"text_key doesn't match Tr.key for: {wrong[:3]}")
        found |= set(data)
    return sorted(t for t in found if re.search(r"[A-Za-z]", t))


def manual_strings(manual):
    return [manual["title"]] + manual["intro"] + [c["title"] for c in manual["chapters"]] + [p for c in manual["chapters"] for p in c["text"]]


def english():
    return {"lang": json.loads((ASSETS / "lang" / "en_us.json").read_text(encoding="utf-8")),
            "text": code_text(),
            "manual": json.loads((ASSETS / "minescore" / "manual" / "en_us.json").read_text(encoding="utf-8"))}


def wanted(en):
    """Every English string that needs translating, in a steady order."""
    out = []
    for s in list(en["lang"].values()) + en["text"] + manual_strings(en["manual"]):
        if s not in out and s not in UNTRANSLATED and re.search(r"[A-Za-z]{2}", s):
            out.append(s)
    return out


def source():
    return json.loads(SOURCE.read_text(encoding="utf-8")) if SOURCE.exists() else {}


def number_new(en):
    """Gives any English not numbered yet the next numbers; returns how many."""
    src = source()
    have = set(src.values())
    n = max((int(k) for k in src), default=-1) + 1
    added = 0
    for s in wanted(en):
        if s not in have:
            src[f"{n:04d}"] = s
            n += 1
            added += 1
    LANG_DIR.mkdir(exist_ok=True)
    SOURCE.write_text(json.dumps(src, ensure_ascii=False, indent=0) + "\n", encoding="utf-8")
    return added


def translation(file):
    """English -> this language, from tools/lang/<file>.json (numbers) and en_source.json."""
    f = LANG_DIR / f"{file}.json"
    if not f.exists():
        return {}
    numbered = json.loads(f.read_text(encoding="utf-8"))
    src = source()
    return {src[k]: v for k, v in numbered.items() if k in src and v}


def lang_file(en, tr):
    """The game's lang file for one language: its translated lang keys, then the code's text under Tr's keys."""
    out = {k: tr[v] for k, v in en["lang"].items() if v in tr}
    for s in en["text"]:
        if s in tr:
            out[text_key(s)] = tr[s]
    return dict(sorted(out.items()))


def manual_file(en, tr):
    m = en["manual"]
    t = lambda s: tr.get(s, s)  # noqa: E731
    return {"title": t(m["title"]), "intro": [t(p) for p in m["intro"]],
            "chapters": [{**c, "title": t(c["title"]), "text": [t(p) for p in c["text"]]} for c in m["chapters"]]}


def check(text_in, text_out):
    """Placeholders (%s, %%) must survive translation."""
    return sorted(re.findall(r"%%|%s|%\d\$s", text_in)) == sorted(re.findall(r"%%|%s|%\d\$s", text_out))


if __name__ == "__main__":
    en = english()
    if "--number" in sys.argv:
        print(f"numbered {number_new(en)} new strings")
    src = source()
    need = wanted(en)
    print(f"{len(need)} strings to translate, ~{sum(len(s.split()) for s in need)} words")
    for file in sorted(set(LANGUAGES.values())):
        tr = translation(file)
        missing = [s for s in need if s not in tr]
        bad = [s for s, v in tr.items() if not check(s, v)]
        print(f"  {file}: {len(missing)} untranslated" + (f", {len(bad)} with broken placeholders: {bad[:2]}" if bad else ""))
