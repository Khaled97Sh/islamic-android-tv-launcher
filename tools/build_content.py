#!/usr/bin/env python3
"""Builds app/src/main/assets/{ayat,azkar}.json from two open datasets (text is copied, never retyped):

  * Quran (quran-simple edition, alquran.cloud / Tanzil text): https://api.alquran.cloud/v1/quran/quran-simple
  * Hisn al-Muslim adhkar (rn0x/Adhkar-json):  https://raw.githubusercontent.com/rn0x/Adhkar-json/main/adhkar.json

Usage: tools/build_content.py <dir-with-quran_simple.json-and-adhkar.json> <assets-dir>
The ayah *choice* (REFS below) is curated: short, complete, encouraging ayat. Review tools/ayat_review.txt.
"""
import json, re, sys, os, random

src, out = sys.argv[1], sys.argv[2]
quran = json.load(open(os.path.join(src, "quran_simple.json"), encoding="utf-8"))["data"]["surahs"]
adhkar = json.load(open(os.path.join(src, "adhkar.json"), encoding="utf-8"))

# ---------------------------------------------------------------- ayat
# "surah:ayah" or "surah:from-to"
# Syntax: "S:A" one ayah | "S:A-B" each ayah separately | "S:A-B+" joined into one block |
#         "S:A@k" only clause k of the ayah (clauses are split where the Quran text has a pause sign; -1 = last)
REFS = """1:2 1:3-4+ 1:5 1:6 2:2 2:138 2:152 2:153 2:156 2:163 2:201 3:8 3:31 3:139 3:160 3:173 3:200 4:28 4:86 4:126 4:147
5:35 6:18 6:103 6:162 7:56 7:180 7:199 8:10 8:64 9:51 9:119 9:129 10:62 11:90 11:115 12:92 13:28 14:7@0 14:41 15:9 15:49 15:99
16:18 16:96 16:128 17:80 18:46 18:107 19:96 20:5 20:8 20:14 20:25-28+ 20:46 20:82 20:114@-1 21:35 21:83 21:107 22:77
23:1-2+ 23:116 23:118 25:58 25:74 26:78-80+ 26:81-83+ 26:217 28:88 29:69 31:26 33:3 33:41-42+ 35:2 35:15 36:58 36:82 36:83
39:10 39:36 39:62 39:66 40:55 40:60 40:65 42:19 42:28 42:43 47:7 49:10 51:56 51:58 53:39 53:42 53:43-44+ 54:17 55:1-4+ 55:13
55:26-27+ 55:29 55:60 55:78 56:74 57:1 57:3 59:24 61:13 62:10 64:1 64:11 65:3 67:1 68:4 71:10 73:8-9+ 76:9 82:6 85:14 85:15-16+
87:1 87:14-15+ 87:17 89:27-28+ 89:29-30+ 91:9 92:7 93:1-3+ 93:4-5+ 93:6-8+ 93:9-11+ 94:1-4+ 94:5-6+ 94:7-8+ 95:4 95:8 96:1-2+ 96:3-5+
97:3 97:5 98:7 99:7 103:1-3+ 106:3-4+ 108:1-2+ 109:6 110:1-2+ 110:3 112:1-2+ 112:3-4+ 113:1 114:1-3+""".split()

MAX_PLAIN = 105   # letters + spaces without diacritics; keeps every ayah to <= 2-3 lines on the card
# a few ayat begin with a narrative lead-in that is dropped (text after the marker is kept)
AFTER = {"18:10": "فَقَالُوا "}

def strip_marks(s):
    s = s.replace("﻿", "")
    # waqf / recitation-sign marks that the TV's system font may draw as boxes
    return re.sub("[ۖ-ۜ۞۩۟-࣓ۨ-ࣿ]", "", s).strip()

BASMALA = strip_marks(quran[0]["ayahs"][0]["text"])

def plain(s):  # remove harakat for names/matching
    return re.sub("[ً-ٰٟۖ-ۭ]", "", s).replace("ٱ", "ا")

def indic(n):
    return str(n).translate(str.maketrans("0123456789", "٠١٢٣٤٥٦٧٨٩"))

ayat, seen = [], set()
def surah_name(sn):
    name = re.sub(r"^سورة\s*", "", plain(quran[sn - 1]["name"]).replace("\u0640", "")).strip()
    return "آل عمران" if name == "ال عمران" else name

def ayah_text(sn, n):
    t = strip_marks(quran[sn - 1]["ayahs"][n - 1]["text"])
    if sn != 1 and n == 1 and t.startswith(BASMALA):
        t = t[len(BASMALA):].strip()
    return t

for ref in REFS:
    join = ref.endswith("+"); ref = ref.rstrip("+")
    clause = None
    if "@" in ref:
        ref, clause = ref.split("@"); clause = int(clause)
    s_, rng = ref.split(":"); sn = int(s_)
    a_, b_ = (rng.split("-") if "-" in rng else (rng, rng))
    nums = list(range(int(a_), int(b_) + 1))
    if clause is not None:
        parts = [p for p in re.split(r"\s{2,}", ayah_text(sn, nums[0])) if p.strip()]
        texts = [(nums[0], parts[clause])]
    elif join:
        texts = [(nums[0], " ".join(ayah_text(sn, n) for n in nums))]
    else:
        texts = [(n, ayah_text(sn, n)) for n in nums]
    for n, t in texts:
        marker = AFTER.get(f"{sn}:{n}")
        if marker and marker in t:
            t = t.split(marker, 1)[1]
        t = re.sub(r"\s{2,}", " ", t).strip()
        label_n = indic(n) if len(nums) == 1 or clause is not None else indic(f"{nums[0]}-{nums[-1]}" if join else n)
        if not t or (sn, n, join) in seen or len(plain(t)) > MAX_PLAIN:
            continue
        seen.add((sn, n, join))
        ayat.append({"t": t, "r": f"{surah_name(sn)} {label_n}"})

# ---------------------------------------------------------------- adhkar
def clean(t):
    t = re.sub(r"\[[^\]]*\]", "", t)                 # notes such as [وإذا أمسى قال: ...]
    t = t.replace("((", "").replace("))", "").replace("*", " ")
    t = re.sub(r"\s+([،.؛:!؟])", r"\1", t)
    t = re.sub(r"\s+", " ", t).strip()
    return t

by_id = {c["id"]: c for c in adhkar}
def items(cid, max_len=400):
    return [clean(i["text"]) for i in by_id[cid]["array"] if 8 < len(clean(i["text"])) <= max_len]

def kind(t):
    p = plain(t)
    if "اصبح" in p: return "m"
    if re.search("امسى|امسينا|امسيت", p): return "e"
    return "n"

cat1 = items(1, 700)
morning = [t for t in cat1 if kind(t) in "mn"]
evening = [t for t in cat1 if kind(t) in "en"]
# well-known evening wordings (diacritised by hand; short and unambiguous)
evening += [
    "أَمْسَيْنَا وَأَمْسَى الْمُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ، لَا إِلَهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ",
    "اللَّهُمَّ بِكَ أَمْسَيْنَا، وَبِكَ أَصْبَحْنَا، وَبِكَ نَحْيَا، وَبِكَ نَمُوتُ، وَإِلَيْكَ الْمَصِيرُ",
]
shared = []
for cid in (27, 129, 130, 107):
    shared += items(cid, 300)
morning += items(3, 300) + shared
evening += items(2, 300) + shared

general = []
for cid in (1, 8, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 34, 35, 40, 41, 44, 45, 46, 82, 89, 93, 106, 122, 123, 124, 126, 129, 130, 131, 132, 107):
    general += [t for t in items(cid, 170) if len(plain(t)) <= 120 and (kind(t) == "n" or cid != 1)]
general += ["سُبْحَانَ اللَّهِ", "الْحَمْدُ لِلَّهِ", "لَا إِلَهَ إِلَّا اللَّهُ", "اللَّهُ أَكْبَرُ",
            "لَا حَوْلَ وَلَا قُوَّةَ إِلَّا بِاللَّهِ", "أَسْتَغْفِرُ اللَّهَ الْعَظِيمَ وَأَتُوبُ إِلَيْهِ",
            "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ، سُبْحَانَ اللَّهِ الْعَظِيمِ"]

def uniq(xs):
    seen, r = set(), []
    for x in xs:
        k = plain(x)
        if k not in seen: seen.add(k); r.append(x)
    return r

morning, evening, general = uniq(morning), uniq(evening), uniq(general)
os.makedirs(out, exist_ok=True)
json.dump(ayat, open(os.path.join(out, "ayat.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=0)
json.dump({"morning": morning, "evening": evening, "general": general},
          open(os.path.join(out, "azkar.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=0)
with open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "ayat_review.txt"), "w", encoding="utf-8") as f:
    f.write("\n".join(f"{a['r']}\t{a['t']}" for a in ayat))
print(f"ayat={len(ayat)} morning={len(morning)} evening={len(evening)} general={len(general)}")
print("max plain len: ayah", max(len(plain(a['t'])) for a in ayat), "general", max(len(plain(g)) for g in general))
