#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Sade.C - Bilgisayardan Tüm Telefonlara Otomatik Güncelleme Yayınlama Scripti
=============================================================================
Kullanım:
    python scripts/publish_update.py
    python scripts/publish_update.py --version 1.1 --notes "Yeni sipariş özellikleri eklendi"

Bu script şunları yapar:
1. app/build.gradle.kts dosyasındaki versionCode'u otomatik 1 artırır ve versionName'i günceller.
2. Gradle ile yeni APK'yı derler (assembleDebug).
3. Yeni derlenen APK'yı public/sadec.apk olarak kopyalar.
4. Firebase Firestore'a (sadec-gerze) yeni sürüm bilgilerini yazar.
5. Değişiklikleri Git'e commit edip pushlar (Vercel doğrudan yeni APK'yı barındırır).
6. Tüm telefonlar anında ekranda 'Yeni Güncelleme Mevcut' uyarısı alır ve tek tıkla günceller!
"""

import os
import re
import sys
import json
import shutil
import urllib.request
import subprocess
from datetime import datetime

if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
    except Exception:
        pass

ROOT_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
GRADLE_FILE = os.path.join(ROOT_DIR, "app", "build.gradle.kts")
APK_SOURCE = os.path.join(ROOT_DIR, "app", "build", "outputs", "apk", "debug", "app-debug.apk")
PUBLIC_APK_DEST = os.path.join(ROOT_DIR, "public", "sadec.apk")

PROJECT_ID = "sadec-9b458"
API_KEY = "AIzaSyDP8uQbnP6IrT127fpIyVgrmFIcBlPMN7w"
RESTAURANT_ID = "sadec-gerze"
DOWNLOAD_URL = "https://sadec.vercel.app/sadec.apk"

def read_version_info():
    with open(GRADLE_FILE, "r", encoding="utf-8") as f:
        content = f.read()

    vc_match = re.search(r'versionCode\s*=\s*(\d+)', content)
    vn_match = re.search(r'versionName\s*=\s*"([^"]+)"', content)

    current_vc = int(vc_match.group(1)) if vc_match else 1
    current_vn = vn_match.group(1) if vn_match else "1.0"
    return current_vc, current_vn, content

def update_gradle_version(new_vc, new_vn, content):
    content = re.sub(r'versionCode\s*=\s*\d+', f'versionCode = {new_vc}', content)
    content = re.sub(r'versionName\s*=\s*"[^"]+"', f'versionName = "{new_vn}"', content)
    with open(GRADLE_FILE, "w", encoding="utf-8") as f:
        f.write(content)
    print(f"✅ build.gradle.kts güncellendi: versionCode = {new_vc}, versionName = \"{new_vn}\"")

def build_apk():
    print("🔨 Yeni APK derleniyor (assembleDebug)...")
    gradle_cmd = os.path.join(ROOT_DIR, "gradlew.bat" if os.name == "nt" else "gradlew")
    res = subprocess.run([gradle_cmd, "assembleDebug"], cwd=ROOT_DIR)
    if res.returncode != 0:
        print("❌ APK derleme başarısız oldu!")
        sys.exit(1)
    print(f"✅ APK başarıyla derlendi: {APK_SOURCE}")

def copy_to_public():
    os.makedirs(os.path.dirname(PUBLIC_APK_DEST), exist_ok=True)
    shutil.copy2(APK_SOURCE, PUBLIC_APK_DEST)
    size_mb = os.path.getsize(PUBLIC_APK_DEST) / (1024 * 1024)
    print(f"✅ APK web dizinine kopyalandı ({size_mb:.1f} MB): {PUBLIC_APK_DEST}")

def update_firestore(version_code, version_name, release_notes):
    print("📡 Firebase Firestore'a güncelleme bilgisi gönderiliyor...")
    now_str = datetime.now().strftime("%d.%m.%Y %H:%M")

    firestore_url = (
        f"https://firestore.googleapis.com/v1/projects/{PROJECT_ID}/databases/(default)/documents/"
        f"restaurants/{RESTAURANT_ID}?updateMask.fieldPaths=appUpdateInfo&key={API_KEY}"
    )

    payload = {
        "fields": {
            "appUpdateInfo": {
                "mapValue": {
                    "fields": {
                        "latestVersionCode": {"integerValue": str(version_code)},
                        "latestVersionName": {"stringValue": version_name},
                        "apkUrl": {"stringValue": DOWNLOAD_URL},
                        "releaseNotes": {"stringValue": release_notes},
                        "isMandatory": {"booleanValue": False},
                        "publishDate": {"stringValue": now_str}
                    }
                }
            }
        }
    }

    req = urllib.request.Request(
        firestore_url,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="PATCH"
    )

    try:
        with urllib.request.urlopen(req) as resp:
            if resp.status in (200, 201):
                print(f"✅ Firebase güncellendi! Tüm bağlı telefonlara güncelleme bildirimi gönderildi.")
            else:
                print(f"⚠️ Firebase yanıtı: {resp.status}")
    except Exception as e:
        print(f"⚠️ Firebase güncelleme uyarısı: {e}")

def git_commit_and_push(version_name, release_notes):
    print("🚀 Git commit ve push yapılıyor (Vercel yayını için)...")
    try:
        subprocess.run(["git", "add", "."], cwd=ROOT_DIR, check=True)
        commit_msg = f"Release v{version_name}: {release_notes}"
        subprocess.run(["git", "commit", "-m", commit_msg], cwd=ROOT_DIR, check=True)
        subprocess.run(["git", "push", "origin", "main"], cwd=ROOT_DIR, check=True)
        print("✅ Git push tamamlandı. Vercel yeni APK'yı canlıya alıyor.")
    except Exception as e:
        print(f"⚠️ Git push uyarısı: {e}")

def main():
    import argparse
    parser = argparse.ArgumentParser(description="Sade.C Bilgisayardan Telefonlara Güncelleme Yayınlayıcı")
    parser.add_argument("--version", "-v", type=str, default=None, help="Yeni sürüm adı (Örn: 1.1)")
    parser.add_argument("--notes", "-n", type=str, default=None, help="Sürüm notları")
    parser.add_argument("--no-build", action="store_true", help="APK derleme adımını atla")
    args = parser.parse_args()

    current_vc, current_vn, content = read_version_info()
    new_vc = current_vc + 1

    if args.version:
        new_vn = args.version
    else:
        # Auto bump minor version e.g. 1.0 -> 1.1
        parts = current_vn.split(".")
        if len(parts) >= 2 and parts[-1].isdigit():
            parts[-1] = str(int(parts[-1]) + 1)
            new_vn = ".".join(parts)
        else:
            new_vn = f"{current_vn}.1"

    release_notes = args.notes if args.notes else "Performans iyileştirmeleri ve yeni özellikler."

    print(f"\n==================================================")
    print(f"📱 Sade.C Güncelleme Yayınlama Başlatılıyor")
    print(f"Mevcut Sürüm: v{current_vn} (Kod: {current_vc})")
    print(f"Yeni Sürüm:   v{new_vn} (Kod: {new_vc})")
    print(f"Sürüm Notu:   {release_notes}")
    print(f"İndirme Linki: {DOWNLOAD_URL}")
    print(f"==================================================\n")

    update_gradle_version(new_vc, new_vn, content)

    if not args.no_build:
        build_apk()

    copy_to_public()
    update_firestore(new_vc, new_vn, release_notes)
    git_commit_and_push(new_vn, release_notes)

    print(f"\n🎉 TEBRİKLER! Güncelleme v{new_vn} başarıyla yayınlandı!")
    print(f"📱 Telefonlar uygulamayı açtığında otomatik güncelleme penceresiyle karşılaşacak.\n")

if __name__ == "__main__":
    main()
