#!/usr/bin/env python3
"""Grows assets/sunnah.json and assets/duas.json so the 6-hourly widgets go weeks without repeating.

All Arabic text is COPIED from open datasets, never retyped:
  * Sahih al-Bukhari (fawazahmed0/hadith-api, ara-bukhari): https://cdn.jsdelivr.net/gh/fawazahmed0/hadith-api@1/editions/ara-bukhari.json
  * Hisn al-Muslim (rn0x/Adhkar-json):                      https://raw.githubusercontent.com/rn0x/Adhkar-json/main/adhkar.json
  * Quran (Tanzil text via alquran.cloud):                  https://api.alquran.cloud/v1/quran/quran-simple

Usage: tools/extend_widget_content.py <dir with ara-bukhari.json adhkar.json quran_simple.json> <assets-dir>
The script only ADDS entries (existing ones are kept, duplicates skipped) and is safe to re-run.
Hadith are picked by number (SUNNAH below): short, self-contained, general guidance. Please proofread before changing the list.
"""
import json, os, random, re, sys

src, assets = sys.argv[1], sys.argv[2]
DIA = "[ً-ٰٟۖ-ۭـ]"
plain = lambda s: re.sub(DIA, "", s)
key = lambda s: re.sub(r"[^\u0621-\u064A ]", "", plain(s)).replace("ـ", "")   # base letters only: used to spot duplicates
tidy = lambda s: re.sub(r"\s+", " ", s.replace("‏", "")).strip()

# --------------------------------------------------------------- sunnah (Sahih al-Bukhari numbers, same numbering as the app's source line)
SUNNAH = [9, 12, 33, 55, 69, 136, 142, 161, 654, 794, 882, 898, 1445, 1899, 1903, 1923, 1933, 2075, 2076, 2128, 2305, 2446, 2447,
          2457, 2472, 2621, 2707, 2736, 3250, 3336, 3484, 4006, 5027, 5144, 5173, 5186, 5645, 5662, 5674, 5742, 5754, 5973,
          5986, 5989, 5997, 6019, 6029, 6117, 6145, 6168, 6231, 6245, 6288, 6304, 6308, 6324, 6338, 6340, 6369, 6371, 6474, 6485,
          6490, 6499, 6507, 6616, 7376, 7505, 574, 998]
# Left out on purpose: sayings that are cut off in the dataset and mislead without the rest (e.g. 5673/6467 "no one enters Paradise by his deeds"
# without "except by Allah's mercy", 129 "whoever meets Allah without shirk enters Paradise", 7233 "do not wish for death"), and rulings/war/legal items.
TOPIC = {2: "الإيمان", 3: "العلم", 4: "الوضوء", 8: "الصلاة", 9: "مواقيت الصلاة", 11: "يوم الجمعة", 19: "قيام الليل", 24: "الصدقة والزكاة",
         30: "الصيام", 34: "البيع والمعاملة", 42: "الماء", 46: "المظالم", 51: "الهدية", 53: "الإصلاح بين الناس", 59: "بدء الخلق",
         66: "فضل القرآن", 67: "النكاح", 69: "النفقات", 70: "الطعام", 75: "المرضى", 76: "الطب", 78: "الأدب وحسن الخلق", 79: "الاستئذان",
         80: "الدعاء", 81: "الرقائق", 83: "الأيمان", 92: "الفتن", 96: "الاعتصام بالسنة", 97: "التوحيد"}

bukhari = {h["hadithnumber"]: h for h in json.load(open(os.path.join(src, "ara-bukhari.json"), encoding="utf-8"))["hadiths"]}
pat = re.compile(r'صلى الله عليه (?:و)?سلم[^"\n]{0,40}?"\s*([^"]{8,400}?)\s*"')

def saying(n):
    h = bukhari[n]
    for m in pat.finditer(h["text"]):
        t = tidy(m.group(1))
        if 14 <= len(plain(t)) <= 100 and not re.search(r"[A-Za-z0-9\[\]()؟]", t):
            return t, h["reference"]["book"]
    return None, None

sunnah_path = os.path.join(assets, "sunnah.json")
sunnah = json.load(open(sunnah_path, encoding="utf-8"))
have = {key(x["t"]) for x in sunnah} | {x["s"] for x in sunnah}
added_s = []
for n in SUNNAH:
    t, book = saying(n)
    s = f"صحيح البخاري · {n}"
    if t is None or s in have or key(t) in have:
        continue
    have.add(key(t)); have.add(s)
    added_s.append({"t": t, "s": s, "k": TOPIC.get(book, "حديث نبوي")})

# --------------------------------------------------------------- duas
duas_path = os.path.join(assets, "duas.json")
duas = json.load(open(duas_path, encoding="utf-8"))
have = {key(x["t"]) for x in duas}
added_d = []

SKIP_CAT = ["العدو", "المحتضر", "الميت", "الفرط", "إتيان", "الخلاء", "القبر", "دفن", "المبتلى", "الطيرة", "الشرك", "وسوسة", "الوسوسة", "الشيطان",
            "الكرب", "الغضب", "الذبح", "الاستسقاء", "الكسوف", "الرعد", "الريح", "العطاس", "السلطان", "الظالم", "الحجر", "الجمرة", "التلبية",
            "عرفة", "الطواف", "السفر", "المركوب", "القنوت", "المصيبة", "للكافر", "الكافر", "سابه", "أصاب", "يئس", "خاف قوما", "الرؤيا",
            "استصعب", "قضاء الدين", "يقع ما لا يرضاه", "إذا أفطر عند", "لطلب الطعام", "عرض عليك", "أقرض", "للمتزوج", "المقيم للمسافر",
            "المسافر للمقيم", "نزل مترلا", "المريض", "الآذان", "الجلسة"]
SKIP_START = ("يصلي على", "يدعو لنفسه", "تبلي ويخلف", "قال النبي", "وقال صلى", "مطرنا بفضل", "الباقيات", "إن أفضل الدعاء", "من قال:", "سمع الله لمن",
              "لا بأس طهور", "أحبك الذي", "بارك الله لك في أهلك", "اللهم اجعله لنا فرطا", "ذهب الظمأ")
for cat in json.load(open(os.path.join(src, "adhkar.json"), encoding="utf-8")):
    if any(b in cat["category"] for b in SKIP_CAT):
        continue
    for x in cat["array"]:
        t = x["text"].strip()
        if re.search(r"\)\)\s*\([^()]*\)\.?$", t):               # "((dua)) (three times)" -> "dua"
            t = re.sub(r"\s*\([^()]*\)\.?$", "", t)
        t = t.replace("((", "").replace("))", "")
        t = re.sub(r"^\(\s*|\s*\)$", "", t).strip()
        D = "[\u064B-\u065F\u0670]*"
        t = re.sub(r"\s*ثلاث" .replace("ثلاث", D.join("ثلاث")) + D + r"\s*" + D.join("مرات") + D + r"\.?$", "", t).strip()
        if len(t) > 110 or len(plain(t)) < 18 or re.search(r"[A-Za-z0-9()﴿]", t) or plain(t).startswith(SKIP_START) or key(t) in have:
            continue
        if not t.endswith((".", "؟", "!")):
            t += "."
        have.add(key(t)); added_d.append({"t": t, "c": cat["category"]})

# supplications from the Quran (the text is the ayah itself, the source line names the surah and ayah)
quran = json.load(open(os.path.join(src, "quran_simple.json"), encoding="utf-8"))["data"]["surahs"]
MARKS = "[ۖ-ۜ۞۩۟-࣓ۨ-ࣿ]"
BASM = re.sub(MARKS, "", quran[0]["ayahs"][0]["text"]).replace("﻿", "").strip()
def ayah(s, a):
    t = re.sub(MARKS, "", quran[s - 1]["ayahs"][a - 1]["text"]).replace("﻿", "").strip()
    return t[len(BASM):].strip() if s not in (1, 9) and a == 1 and t.startswith(BASM) else t
def sname(s):
    n = re.sub(r"^سورة\s*", "", plain(quran[s - 1]["name"])).strip()
    return "آل عمران" if n == "ال عمران" else n
LEAD = ("وَقُلْ ", "قَالَا ", "قَالَ ", "فَقَالُوا ")             # narrative / instruction lead-ins are dropped
AFTER = {(2, 201): "يَقُولُ ", (18, 10): "فَقَالُوا ", (25, 74): "يَقُولُونَ ", (28, 24): "فَقَالَ ", (20, 114): "وَقُل "}  # keep only what follows the LAST marker
QURAN = [(7, 23), (7, 151), (14, 40), (14, 41), (17, 24), (20, 25, 28), (20, 114), (23, 29), (23, 97, 98), (23, 118), (10, 85), (2, 201), (3, 8), (18, 10), (25, 74), (28, 24)]
for q in QURAN:
    s, a1, a2 = q[0], q[1], q[-1]
    t = tidy(" ".join(ayah(s, a) for a in range(a1, a2 + 1)))
    if (s, a1) in AFTER and AFTER[(s, a1)] in t:
        t = t[t.rindex(AFTER[(s, a1)]) + len(AFTER[(s, a1)]):].strip()
    for lead in LEAD:
        if t.startswith(lead):
            t = t[len(lead):]; break
    ref = f"{sname(s)} {a1}" if a1 == a2 else f"{sname(s)} {a1}-{a2}"
    if len(t) <= 110 and key(t) not in have:
        have.add(key(t)); added_d.append({"t": t, "c": f"دعاء من القرآن · {ref}"})

# --------------------------------------------------------------- write (seeded shuffle so neighbours differ in topic, same result on every run)
for path, old, new in ((sunnah_path, sunnah, added_s), (duas_path, duas, added_d)):
    allitems = old + new
    random.Random(7).shuffle(allitems)
    json.dump(allitems, open(path, "w", encoding="utf-8"), ensure_ascii=False, indent=0)
    print(os.path.basename(path), len(old), "+", len(new), "=", len(allitems))
