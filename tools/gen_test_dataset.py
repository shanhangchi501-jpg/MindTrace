"""Generate a synthetic encrypted backup for MindTrace manual testing.

The dataset is fictional. It focuses on a schizophrenia-spectrum profile with
positive/negative symptoms and 0-10 intensity scores, so the trends page and
the visit PDF can be checked end to end.

Usage: python tools/gen_test_dataset.py
"""

import datetime
import json
import os
import re
import struct
from pathlib import Path

from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.pbkdf2 import PBKDF2HMAC

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "app/src/main/java/com/mindtrace/app/data/SymptomCatalog.kt"
OUT = ROOT / "dist"
PASSWORD = "MindTraceTest2026"
END = datetime.date(2026, 10, 21)
START = END - datetime.timedelta(days=29)
TZ = datetime.timezone(datetime.timedelta(hours=8))

# Four deliberately missing check-in days. They become gaps in every chart.
MISSING = {
    START + datetime.timedelta(days=3),
    START + datetime.timedelta(days=11),
    START + datetime.timedelta(days=17),
    START + datetime.timedelta(days=24),
}
DATES = [START + datetime.timedelta(days=i) for i in range(30)]


def read_symptoms():
    src = CATALOG.read_text(encoding="utf-8")
    pattern = re.compile(r'SymptomDef\("([^"]+)", "([^"]+)", "([^"]+)", listOf\(([^)]*)\)\)')
    symptoms = []
    for index, match in enumerate(pattern.finditer(src)):
        sid, name, category, tags = match.groups()
        tag_list = re.findall(r'"([^"]+)"', tags)
        symptoms.append(
            {
                "id": sid,
                "name": name,
                "category": category,
                "conditionTags": ",".join(tag_list),
                "isCustom": False,
                "enabled": False,
                "sortOrder": index,
                "archived": False,
            }
        )
    return symptoms


SYMPTOMS = read_symptoms()

SCHIZOPHRENIA_SUGGESTED = {
    "hallucination", "delusion", "paranoia", "disorganized_speech",
    "disorganized_behavior", "catatonia", "passivity", "flat_affect",
    "alogia", "avolition", "anhedonia", "withdrawal", "self_neglect",
    "concentration", "memory", "sleep_decrease", "agitation",
}
DEPRESSION_SUGGESTED = {
    "low_mood", "anhedonia", "fatigue", "insomnia", "early_wake",
    "appetite_loss", "concentration", "guilt", "hopelessness",
    "withdrawal", "crying",
}
ENABLED = SCHIZOPHRENIA_SUGGESTED | DEPRESSION_SUGGESTED

# Day index -> 0-10 intensity. Only listed days have the symptom selected.
SEVERITY = {
    "hallucination": {
        0: 6, 1: 7, 2: 5, 4: 6, 5: 8, 6: 7, 7: 5, 8: 4, 9: 3, 10: 6,
        12: 5, 13: 4, 14: 3, 15: 5, 16: 6, 18: 4, 19: 3, 20: 5, 21: 4,
        22: 3, 25: 2, 26: 3, 29: 2,
    },
    "paranoia": {
        0: 5, 1: 6, 2: 4, 5: 5, 7: 7, 8: 6, 10: 5, 13: 4, 15: 5,
        19: 4, 22: 3, 26: 2, 29: 1,
    },
    "disorganized_speech": {
        0: 5, 4: 6, 6: 7, 8: 5, 10: 4, 14: 5, 19: 4, 21: 3,
    },
    "disorganized_behavior": {4: 4, 7: 5, 14: 3, 20: 2},
    "catatonia": {8: 3},
    "passivity": {1: 4, 6: 5, 10: 3, 18: 2},
    "flat_affect": {
        0: 5, 1: 6, 2: 4, 5: 5, 6: 6, 7: 7, 9: 4, 10: 5, 12: 4, 13: 5,
        14: 6, 16: 5, 18: 4, 20: 6, 21: 5, 22: 4, 23: 5, 25: 3, 27: 4,
        28: 3, 29: 3,
    },
    "alogia": {0: 4, 2: 3, 6: 5, 7: 4, 12: 3, 16: 4, 21: 3, 25: 2},
    "avolition": {
        0: 6, 2: 5, 5: 6, 6: 5, 9: 4, 12: 5, 13: 4, 16: 5, 18: 3,
        20: 4, 23: 3, 26: 3, 28: 2, 29: 2,
    },
    "self_neglect": {
        5: 4, 7: 3, 9: 5, 12: 4, 15: 4, 18: 3, 20: 3, 23: 2, 26: 2,
    },
    "concentration": {
        0: 7, 2: 6, 5: 6, 7: 5, 9: 5, 12: 6, 14: 5, 16: 4, 18: 4,
        20: 3, 22: 3, 26: 2,
    },
}

MOOD = [2, 2, 3, 3, 2, 2, 3, 3, 4, 3, 3, 3, 4, 4, 3, 4, 4, 3, 4, 4, 4, 3, 4, 4, 4, 4, 4, 5, 4, 4]
ANXIETY = [8, 7, 7, 6, 7, 8, 7, 6, 5, 6, 5, 6, 5, 4, 5, 4, 4, 5, 4, 4, 3, 4, 3, 4, 3, 3, 3, 2, 3, 2]
SLEEP = [4.5, 4.0, 5.0, 5.5, 4.5, 4.0, 5.0, 5.5, 6.0, 5.5, 6.0, 5.0, 6.0, 6.5, 6.0, 6.5, 7.0, 6.0, 6.5, 7.0, 7.0, 6.5, 7.0, 7.0, 7.5, 7.0, 7.5, 8.0, 7.5, 8.0]

TRIGGERS = {
    0: ["睡眠不足"],
    1: ["工作 / 学习压力"],
    2: ["孤独 / 独处"],
    5: ["睡眠不足", "工作 / 学习压力"],
    7: ["人际冲突"],
    8: ["睡眠不足"],
    10: ["药物调整"],
    12: ["孤独 / 独处"],
    14: ["工作 / 学习压力"],
    18: ["睡眠不足"],
    20: ["人际冲突"],
    22: ["季节 / 天气"],
    25: ["工作 / 学习压力"],
    26: ["重要事件"],
    28: ["孤独 / 独处"],
}


def ts_for(day, hour, minute=0):
    return int(datetime.datetime(day.year, day.month, day.day, hour, minute, tzinfo=TZ).timestamp() * 1000)


def build_checkins():
    entries = []
    cid = 1
    for index, day in enumerate(DATES):
        if day in MISSING:
            continue
        selected = [
            sid for sid, values in SEVERITY.items()
            if index in values
        ]
        # Keep a stable, readable order in the stored comma-separated string.
        order = [s["id"] for s in SYMPTOMS]
        selected.sort(key=order.index)
        selected.sort(key=lambda sid: ("阳性症状", "阴性症状").index(next(
            s["category"] for s in SYMPTOMS if s["id"] == sid
        )) if next(s["category"] for s in SYMPTOMS if s["id"] == sid) in ("阳性症状", "阴性症状") else 0)
        severity = ",".join(f"{sid}:{SEVERITY[sid][index]}" for sid in selected)
        notes = ""
        if index in (0, 4):
            notes = "昨晚几乎没睡，楼道里的声音听起来像有人在说话。今天把这件事写下来，复诊时告诉医生。"
        elif index in (8, 13):
            notes = "声音比前几天少一些，但出门还是觉得别人在议论我。下午没有出门。"
        elif index in (20, 25):
            notes = "这周能按时吃饭和洗澡了。还是有声音，但知道那不是真的。"
        elif index in (28, 29):
            notes = "状态比月初稳一些，幻觉偶尔出现。准备把最近两周的变化给医生看。"
        entries.append(
            {
                "id": cid,
                "date": day.isoformat(),
                "createdAt": ts_for(day, 21, 30),
                "updatedAt": ts_for(day, 21, 40),
                "mood": MOOD[index],
                "anxiety": ANXIETY[index],
                "irritability": max(1, min(10, ANXIETY[index] - 1)),
                "energy": max(1, min(10, MOOD[index] + 3)),
                "sleepHours": SLEEP[index],
                "sleepQuality": max(2, min(9, 9 - (ANXIETY[index] // 2))),
                "appetite": max(3, min(9, 8 - (index % 4))),
                "concentration": max(2, min(9, 9 - (index % 5))),
                "symptoms": ",".join(selected),
                "symptomSeverity": severity,
                "triggers": ",".join(TRIGGERS.get(index, [])),
                "suicidalIdeation": 1 if index == 5 else 0,
                "medicationNote": "晚上按时服药" if index % 6 else "有一次忘记带药，晚些补服",
                "sideEffects": "口干、上午有点困" if index % 7 == 0 else "",
                "notes": notes,
            }
        )
        cid += 1
    return entries


CHECKINS = build_checkins()
CREATED = ts_for(END, 10, 0)

MEDICATIONS = [
    {
        "id": 1, "name": "奥氮平", "dosage": "5", "unit": "mg", "times": "22:00",
        "asNeeded": False, "startDate": "2026-09-01", "endDate": None, "active": True,
        "notes": "睡前服用。复诊时反馈嗜睡和体重变化。", "createdAt": CREATED - 50 * 86400000,
    },
    {
        "id": 2, "name": "舍曲林", "dosage": "50", "unit": "mg", "times": "08:00",
        "asNeeded": False, "startDate": "2026-09-01", "endDate": None, "active": True,
        "notes": "早饭后服用。", "createdAt": CREATED - 50 * 86400000,
    },
    {
        "id": 3, "name": "劳拉西泮", "dosage": "1", "unit": "mg", "times": "",
        "asNeeded": True, "startDate": "2026-09-10", "endDate": None, "active": True,
        "notes": "仅在明显焦虑、难以入睡时使用。", "createdAt": CREATED - 41 * 86400000,
    },
]


def build_logs():
    logs = []
    lid = 1
    for index, day in enumerate(DATES):
        for medication_id, hour in ((1, 22), (2, 8)):
            missed = index in ({7, 19} if medication_id == 1 else {4, 22})
            logs.append(
                {
                    "id": lid,
                    "medicationId": medication_id,
                    "date": day.isoformat(),
                    "time": f"{hour:02d}:00",
                    "status": "missed" if missed else "taken",
                    "takenAt": None if missed else ts_for(day, hour, 5),
                    "note": "忘记服药" if missed else "",
                }
            )
            lid += 1
    for index, hour, minute, note in (
        (10, 15, 20, "明显焦虑时服用"),
        (17, 23, 10, "入睡困难时服用"),
        (26, 16, 40, "外出前焦虑，服用一次"),
    ):
        day = DATES[index]
        logs.append(
            {
                "id": lid,
                "medicationId": 3,
                "date": day.isoformat(),
                "time": "",
                "status": "taken",
                "takenAt": ts_for(day, hour, minute),
                "note": note,
            }
        )
        lid += 1
    return logs


SCALES = [
    {
        "id": 1, "type": "PHQ9", "answers": "2,3,2,2,1,2,2,1,0", "score": 15,
        "severity": "moderately_severe", "createdAt": CREATED - 29 * 86400000,
    },
    {
        "id": 2, "type": "GAD7", "answers": "2,2,2,2,1,2,1", "score": 12,
        "severity": "moderate", "createdAt": CREATED - 29 * 86400000 + 600000,
    },
    {
        "id": 3, "type": "ISI", "answers": "3,3,2,2,2,2,2", "score": 16,
        "severity": "moderate", "createdAt": CREATED - 24 * 86400000,
    },
    {
        "id": 4, "type": "PHQ9", "answers": "1,2,1,2,1,1,1,0,0", "score": 9,
        "severity": "mild", "createdAt": CREATED - 6 * 86400000,
    },
    {
        "id": 5, "type": "GAD7", "answers": "1,1,1,1,1,1,1", "score": 7,
        "severity": "mild", "createdAt": CREATED - 6 * 86400000 + 600000,
    },
    {
        "id": 6, "type": "WHO5", "answers": "3,2,3,2,2", "score": 12,
        "severity": "low", "createdAt": CREATED - 2 * 86400000,
    },
]

JOURNALS = [
    {
        "id": 1, "date": "2026-09-28", "createdAt": CREATED - 23 * 86400000,
        "updatedAt": CREATED - 23 * 86400000,
        "title": "声音很多的一天",
        "content": "昨晚只睡了四个多小时。今天在地铁上听见有人在叫我的名字，回头看又没有人。下午不敢去超市，怕别人知道我在想什么。晚上把药吃了，早一点关灯。",
        "mood": 2, "tags": "幻觉,睡眠",
    },
    {
        "id": 2, "date": "2026-10-07", "createdAt": CREATED - 14 * 86400000,
        "updatedAt": CREATED - 14 * 86400000,
        "title": "复诊前整理",
        "content": "把最近两周的声音、多疑和睡眠写下来。奥氮平吃完后上午很困，但晚上能睡久一点。想问医生：声音变少是不是药物起效，还是因为最近少出门。",
        "mood": 3, "tags": "复诊,用药",
    },
    {
        "id": 3, "date": "2026-10-18", "createdAt": CREATED - 3 * 86400000,
        "updatedAt": CREATED - 3 * 86400000,
        "title": "今天出门了",
        "content": "下午和姐姐在小区里走了十分钟。还是觉得有人在看我，但没有中途回家。晚上自己洗了澡，把第二天的衣服准备好。声音出现了一次，很快就过去了。",
        "mood": 4, "tags": "家人,散步,恢复",
    },
]


def main():
    for symptom in SYMPTOMS:
        symptom["enabled"] = symptom["id"] in ENABLED

    payload = {
        "version": 1,
        "exportedAt": CREATED,
        "profile": {
            "id": 1,
            "displayName": "周屿",
            "birthYear": 1995,
            "gender": "男",
            "conditions": "schizophrenia,depression",
            "note": "2026 年 9 月首次因幻听和被害感就诊。目前规律服药，准备每两周复诊。",
            "updatedAt": CREATED,
        },
        "checkIns": CHECKINS,
        "symptoms": SYMPTOMS,
        "medications": MEDICATIONS,
        "medicationLogs": build_logs(),
        "scales": SCALES,
        "journals": JOURNALS,
        "safetyPlan": {
            "id": 1,
            "warningSigns": "连续两晚几乎不睡、声音命令我做事、觉得家人也被替换了。",
            "copingStrategies": "把声音写下来并标注“这不是真的”；给姐姐打电话；离开人多的地方；听熟悉的音乐。",
            "reasonsToLive": "姐姐、养的多肉、下个月想去看的电影、想继续把记录做下去。",
            "environmentSteps": "药物由姐姐保管一部分；症状严重时不独自出门；把复诊电话放在桌上。",
            "professionalNote": "李医生（示例），周三下午门诊，市精神卫生中心。",
            "updatedAt": CREATED,
        },
        "contacts": [
            {
                "id": 1, "name": "姐姐", "relation": "家人", "phone": "13800000002",
                "kind": "trusted", "sortOrder": 0,
            },
            {
                "id": 2, "name": "李医生（示例）", "relation": "主治医生",
                "phone": "010-00000000", "kind": "professional", "sortOrder": 1,
            },
        ],
        "diagnoses": [
            {
                "id": 1, "standard": "ICD10", "code": "F20.0", "name": "偏执型精神分裂症",
                "nameEn": "Paranoid schizophrenia", "status": "confirmed",
                "diagnosedDate": "2026-09-01", "clinician": "李医生（示例）",
                "note": "首次诊断，伴幻听与被害观念。", "createdAt": CREATED - 50 * 86400000,
            },
            {
                "id": 2, "standard": "ICD10", "code": "F32.1", "name": "中度抑郁发作",
                "nameEn": "Moderate depressive episode", "status": "confirmed",
                "diagnosedDate": "2026-09-01", "clinician": "李医生（示例）",
                "note": "与精神分裂症谱系同时记录。", "createdAt": CREATED - 50 * 86400000 + 1000,
            },
        ],
    }

    plain = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    salt = os.urandom(16)
    iv = os.urandom(12)
    key = PBKDF2HMAC(algorithm=hashes.SHA256(), length=32, salt=salt, iterations=200_000).derive(
        PASSWORD.encode("utf-8")
    )
    ciphertext = AESGCM(key).encrypt(iv, plain, None)
    blob = (
        b"MTR\x01"
        + struct.pack(">i", len(salt)) + salt
        + struct.pack(">i", len(iv)) + iv
        + struct.pack(">i", len(ciphertext)) + ciphertext
    )

    OUT.mkdir(exist_ok=True)
    path = OUT / "MindTrace-0.3.0-schizophrenia-demo.mtrbackup"
    path.write_bytes(blob)

    # Decrypt and validate the fixture before writing the report.
    assert blob[:4] == b"MTR\x01"
    offset = 4
    salt_len = struct.unpack(">i", blob[offset:offset + 4])[0]; offset += 4
    salt2 = blob[offset:offset + salt_len]; offset += salt_len
    iv_len = struct.unpack(">i", blob[offset:offset + 4])[0]; offset += 4
    iv2 = blob[offset:offset + iv_len]; offset += iv_len
    cipher_len = struct.unpack(">i", blob[offset:offset + 4])[0]; offset += 4
    cipher2 = blob[offset:offset + cipher_len]
    key2 = PBKDF2HMAC(
        algorithm=hashes.SHA256(), length=32, salt=salt2, iterations=200_000
    ).derive(PASSWORD.encode("utf-8"))
    restored = json.loads(AESGCM(key2).decrypt(iv2, cipher2, None).decode("utf-8"))
    assert restored["profile"]["displayName"] == "周屿"
    assert len(restored["checkIns"]) == len(CHECKINS)
    assert restored["checkIns"][0]["symptomSeverity"]

    # Print a small summary that can be compared with the UI/PDF.
    from collections import defaultdict
    days = defaultdict(int)
    levels = defaultdict(list)
    for entry in CHECKINS:
        severity = dict(
            (pair.rsplit(":", 1)[0], int(pair.rsplit(":", 1)[1]))
            for pair in entry["symptomSeverity"].split(",") if pair
        )
        for sid in entry["symptoms"].split(","):
            if not sid:
                continue
            days[sid] += 1
            if sid in severity:
                levels[sid].append(severity[sid])
    names = {s["id"]: s["name"] for s in SYMPTOMS}
    print(f"wrote {path} ({len(blob)} bytes)")
    print(f"password: {PASSWORD}")
    print(f"check-ins: {len(CHECKINS)} (missing {len(MISSING)} days), journals: {len(JOURNALS)}")
    print("symptom stats (days, avg, max):")
    for sid in sorted(days, key=lambda s: (-days[s], s)):
        values = levels[sid]
        average = sum(values) / len(values) if values else 0
        print(f"  {names[sid]:<16} {days[sid]:>2} days  avg {average:.1f}  max {max(values) if values else '-'}")


if __name__ == "__main__":
    main()
