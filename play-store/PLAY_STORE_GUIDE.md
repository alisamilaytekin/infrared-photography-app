# SpectraS — Google Play Store Yayınlama Rehberi

Bu klasördeki tüm dosyalar **Google Play Console** yüklemesi için eksiksiz olarak hazırlanmıştır.

---

## 1. Hazır Teknik Dosyalar
- **Paket Adı (`applicationId`):** `com.alisamilaytekin.spectras`
- **Hedef Android Sürümü:** `targetSdk = 36` (Android 16 — Google Play 2026 zorunluluğu karşılandı)
- **Play Store Paketi (`.aab`):** GitHub Releases (`v5.0`) sayfasındaki **`SpectraS-playstore.aab`** dosyası
- **Uygulama İkonu (`512 × 512 px`):** `play-store/icon-512x512.png`
- **Özellik Grafiği (`1024 × 500 px`):** `play-store/feature-graphic-1024x500.png`
- **Gizlilik Politikası Sayfası:** `docs/privacy-policy.html`

---

## 2. Gizlilik Politikası Linkini Aktifleştirme (Ücretsiz — 30 sn)
1. GitHub reponuzda **Settings → Pages** menüsüne girin.
2. **Source:** `Deploy from a branch` · **Branch:** `main` · **Folder:** `/docs` seçip **Save** deyin.
3. 1 dakika sonra gizlilik politikanız şu adreste yayında olur:
   `https://alisamilaytekin.github.io/infrared-photography-app/privacy-policy.html`
4. Bu linki Google Play Console → **App content → Privacy policy** alanına yapıştırın.

---

## 3. Özel Yayın İmza Anahtarı (Upload Keystore) Oluşturma *(Şiddetle Önerilir)*
Repodaki eski `kameras-release.jks` herkese açık olduğu için Google Play'e yüklemeden önce **yalnızca sizde duracak** yeni bir anahtar üretin:

```powershell
keytool -genkeypair -v -keystore spectras-upload.jks -alias spectras -keyalg RSA -keysize 2048 -validity 10000
```

Ardından GitHub reponuzda **Settings → Secrets and variables → Actions → New repository secret** kısmına şu 4 sırrı ekleyin (GitHub Actions otomatik algılayıp `.aab` dosyasını sizin özel anahtarınızla imzalar):
- `PLAY_KEYSTORE_BASE64`: PowerShell'de `[Convert]::ToBase64String([IO.File]::ReadAllBytes("spectras-upload.jks")) | Set-Clipboard` çıktısı
- `PLAY_KEYSTORE_PASSWORD`: Anahtar deposu şifreniz
- `PLAY_KEY_ALIAS`: `spectras`
- `PLAY_KEY_PASSWORD`: Anahtar şifreniz

> **Önemli:** `spectras-upload.jks` dosyasını Google Drive / USB gibi güvenli bir yerde yedekleyin, asla GitHub'a yüklemeyin.

---

## 4. Mağaza Metinleri (Kopyala-Yapıştır)

### İngilizce (Varsayılan — Global)
- **App Name (29/30 karakter):**
  `SpectraS: Pro Color & RAW Cam`
- **Short Description (79/80 karakter):**
  `Live 12-band HSL, 360° color pipette, R↔B infrared swap, RAW DNG & photo studio`
- **Full Description:**
  > Shoot and grade colors live in the viewfinder—or edit any photo from your gallery with the exact same pro color engine.
  >
  > **SpectraS** combines a precision Camera2 RAW (DNG) camera with a real-time Darkroom / Lightroom-style color grading studio built for **Infrared / Aerochrome photographers**, film lovers, and everyday creative shooters.
  >
  > **KEY FEATURES**
  > • **3 Dedicated Shooting Modes:** Switch effortlessly between **Aesthetic & Filters** (crisp Auto-HDR film & portrait looks), **Infrared (IR Lab)** (590nm–850nm profiles & 1-tap R↔B swap), and **Pro Camera** (full manual & RAW studio).
  > • **26 Built-in Film & Infrared Presets:** Instant Portra 400, Kodak Gold, Classic Cuban, Aerochrome IR, IR 590nm Gold (R↔B), 665nm False Color, 720nm/850nm B&W IR, Teal & Orange, Cyberpunk Neon, and more.
  > • **Real-Time R↔B Channel Swap:** Live red-blue channel swap in the viewfinder and photo editor—essential for 590nm/665nm/720nm infrared filters.
  > • **Live 12-Band Color Mixer & Histogram:** Tap or select multiple color bands to shift Hue (-180° to +180°), Saturation, and Luminance in real time.
  > • **360° Point Color Pipette:** Tap any color in the scene and transform it smoothly without edge halos.
  > • **Built-in Photo Editor Studio:** Edit your captured shots or any gallery photo with the exact same GPU/CPU grading engine, before/after compare, and full-res export.
  > • **True Pro Camera Controls:** Spot White Balance, Kelvin & Tint, Exposure EV, AE-L Lock, Spot Metering Box, 5-zone RGB + Luma Histogram, Highlight Zebra, Horizon Level, Flash/Torch, Front & Rear Cameras, and universal RAW (DNG) + JPEG capture.
  > • **Ghost Mode Floating HUD & 13 Languages:** Translucent controls automatically fade out while dragging sliders, with full localization in 13 global languages.
  > • **100% Private & Offline:** Zero accounts, zero tracking, zero cloud uploads.

### Türkçe (Türkçe Mağaza Kaydı)
- **Uygulama Adı (29/30 karakter):**
  `SpectraS: Pro Renk & RAW Kam.`
- **Kısa Açıklama (77/80 karakter):**
  `Canlı 12 bant HSL, 360° pipet, R↔B kızılötesi takas, RAW DNG ve foto stüdyosu`
- **Tam Açıklama:**
  > Çekim anında canlı renk düzenleyin ya da galerinizdeki fotoğrafları aynı profesyonel renk motoruyla anında dönüştürün.
  >
  > **SpectraS**, profesyonel Camera2 RAW (DNG) kamerasını Lightroom / Darkroom tarzı canlı renk derecelendirme stüdyosuyla birleştirir. **Kızılötesi (Infrared / Aerochrome)** fotoğrafçıları, film severler ve yaratıcı mobil fotoğrafçılar için tasarlanmıştır.
  >
  > **ÖNE ÇIKAN ÖZELLİKLER**
  > • **3 Özel Çekim Modu:** **Estetik & Filtreler** (canlı film ve portre görünümleri), **Kızılötesi (IR Lab)** (590nm–850nm profilleri ve tek dokunuş R↔B takas) ve **Pro Kamera** (tam manuel & RAW kontrol) arasında anında geçiş yapın.
  > • **26 Hazır Film & Kızılötesi Ön Ayarı:** Portra 400, Kodak Gold, Classic Cuban, Aerochrome IR, IR 590nm Altın (R↔B), 665nm Yapay Renk, 720nm/850nm S&B IR, Teal & Orange, Cyberpunk Neon ve daha fazlası tek dokunuşla hazır.
  > • **Canlı R↔B Kanal Takası:** 590nm / 665nm / 720nm kızılötesi filtreler için gerçek zamanlı Kırmızı ↔ Mavi kanal değişimi.
  > • **Canlı 12 Bantlı Renk Mikseri:** Histogram üzerinden tekil veya çoklu renk bandı seçip Ton (-180°..+180°), Doygunluk ve Parlaklığı canlı ayarlayın.
  > • **360° Noktasal Renk Pipeti:** Görüntüdeki herhangi bir renge dokunun ve kenar halesi olmadan başka bir renge çevirin.
  > • **Dahili Fotoğraf Düzenleme Stüdyosu:** Yeni çektiğiniz veya galerinizdeki herhangi bir fotoğrafı aynı Pro motorla düzenleyin, öncesi/sonrası karşılaştırın ve tam çözünürlükte kaydedin/paylaşın.
  > • **Gerçek Pro Kamera Araçları:** Noktasal Beyaz Dengesi, Kelvin & Ton, Pozlama (EV), AE-L Işık Kilidi, Taşınabilir Ölçüm Kutusu, 5-Bölge RGB + Parlaklık Histogramı, Zebra, Su Terazisi, Flaş/Fener, Ön/Arka Kamera ve Evrensel RAW (DNG) + JPEG desteği.
  > • **Hayalet Mod (Ghost Mode) & 13 Dil:** Kaydırıcıları sürüklerken panel şeffaflaşır, vizör asla küçülmez; 13 küresel dil desteği sunar.
  > • **%100 Çevrimdışı ve Gizli:** Hesap yok, veri toplama yok, reklam takibi yok.

---

## 5. Veri Güvenliği (Data Safety) Formu Yanıtları
- **"Does your app collect or share any of the required user data types?"** → **No (Hayır)**
- **Category:** Photography (Fotoğrafçılık)
- **Content Rating (IARC):** Tüm yaş grupları (PEGI 3 / Everyone)

## 6. Kapalı Test & Yayınlama Adımları (Bireysel Hesap)
1. **Testing → Closed testing** bölümünden yeni sürüm oluşturup `SpectraS-playstore.aab` dosyasını yükleyin.
2. En az **12 test kullanıcısının** Gmail adresini listeye ekleyip davet linkini paylaşın.
3. 12 kişinin uygulamayı **14 gün boyunca aralıksız** yüklü tutması sonrası **Production (Üretim)** başvurusu açılır ve inceleme ardından Google Play'de yayınlanır.
