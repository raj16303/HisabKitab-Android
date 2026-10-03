# HisabKitab Android App — Step-by-step Guide
Developer: **RS Solanki** · App version 2.1.0 · Package `com.rssolanki.hisabkitab`

## Ye app kaise kaam karti hai
- App ke andar **wahi `Index.html`** hai jo web app mein hai (`app/src/main/assets/index.html`). Isliye screens, Hindi/English, animations, Khata, Aay-Kharch, Reports, Admin sab **100% same** hain.
- Data **usi Google Sheet** mein jata hai. App har kaam aapke Apps Script Web App (`doPost`) ko bhejti hai. Web aur app ke users, parties, entries sab ek hi SaaS database mein rehte hain. Admin approval, login, sab same.
- App mein kuch cheezein web se behtar hain:
  - **Phone ki asli contacts list** (multi-select import) aur single contact picker
  - CSV/Backup seedha **Downloads/HisabKitab** folder mein save, saath mein Share option
  - Statement/Report ka **Print / Save as PDF**
  - WhatsApp aur Call seedhe apps mein khulte hain
  - Phone ka **Back button** (sheet band → pichhla page → app band)

---

## Step 1 — Pehle Apps Script (backend) update karein  ⚠️ zaroori
App ko `doPost` API chahiye, jo **Code.gs v2.1.0** mein hai.
1. Google Sheet → **Extensions → Apps Script**.
2. `Code.gs` ka poora code naye `Code.gs` se replace karein. `Index` HTML file ko bhi naye `Index.html` se replace karein.
3. **Save** → **Deploy → Manage deployments → Edit (pencil) → Version: New version → Deploy**.
4. Check: browser mein `aapka-web-app-url?health=1` kholein. Ye line dikhni chahiye:
   `OK  Android app API (doPost)`
5. Deploy settings: **Execute as: Me**, **Who has access: Anyone**. Agar access "Anyone" nahi hai, to app connect nahi hogi.

## Step 2 — Web App URL copy karein
**Deploy → Manage deployments** mein "Web app URL" copy karein. Ye aisa dikhta hai:
`https://script.google.com/macros/s/AKfycb............/exec`
Ye URL aage app mein lagega.

---

## Step 3 (Option A) — Computer par Android Studio se APK banayein  ✅ recommended
1. **Android Studio** install karein: https://developer.android.com/studio (Windows/Mac/Linux, free).
2. `HisabKitab-Android.zip` ko unzip karein.
3. Android Studio → **Open** → unzip kiya hua `HisabKitab-Android` folder chunein.
4. Pehli baar Gradle Sync hoga (internet chahiye, 5–15 minute lag sakte hain). Neeche "BUILD SUCCESSFUL / Sync finished" aane dein.
   - "SDK not found" aaye to: **Tools → SDK Manager → Android 14 (API 34)** install karein.
5. *(Optional, par aasaan)* URL app mein pehle se daal dein: `app/src/main/res/values/config.xml` kholein aur
   `<string name="server_url" translatable="false">https://script.google.com/macros/s/AKfycb.../exec</string>`
   Aisa karne par app URL nahi poochegi. Khaali chhodenge to app pehli baar khulne par URL poochegi.
6. **APK banayein:** menu **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
   Build poora hone par "locate" dabayein. APK yahan milegi:
   `app/build/outputs/apk/debug/app-debug.apk`
   - Release APK ke liye: **Build → Generate Signed App Bundle / APK** (Step 8 dekhein), ya Terminal mein `./gradlew assembleRelease` chalayein. File: `app/build/outputs/apk/release/app-release.apk`
7. **Seedha phone par chalana ho to:** phone mein **Settings → About phone → Build number par 7 baar tap** karein (Developer options on), phir **Developer options → USB debugging ON**. USB se phone jodein, Android Studio mein upar phone chunein aur ▶ **Run** dabayein.

## Step 3 (Option B) — Bina Android Studio ke, GitHub se APK banayein (cloud build)
Is project mein `.github/workflows/build-apk.yml` pehle se hai. GitHub free mein APK bana deta hai.
1. https://github.com par free account banayein → **New repository** → naam `HisabKitab-Android` → **Create**.
2. **Add file → Upload files** → unzip kiye folder ki saari files/folders upload karein (computer browser se drag-drop sabse aasaan hai) → **Commit changes**.
3. `.github` folder upload na ho to: **Add file → Create new file** → naam mein likhein `.github/workflows/build-apk.yml` → `build-apk.yml` ka content paste karein → Commit.
4. Repository mein **Actions** tab → "Build HisabKitab APK" → green tick aane ka intezaar karein (lagbhag 5–8 minute). Agar apne aap na chale to **Run workflow** dabayein.
5. Build kholein → neeche **Artifacts → HisabKitab-APK** download karein → zip ke andar `app-release.apk` hai.

---

## Step 4 — Phone par install karein
1. APK phone mein bhejein (WhatsApp ke "Document" se, Google Drive se, ya USB se).
2. APK par tap karein → "Install unknown apps" ki permission maangega → us app ke liye **Allow** karein → **Install**.
3. Play Protect warning aaye to **More details → Install anyway** dabayein. Aisa isliye hota hai kyunki app Play Store se nahi aayi.

## Step 5 — Pehli baar app kholna
1. App kholein. Agar `config.xml` mein URL nahi daala tha, to **"Apna HisabKitab server jodein"** screen aayegi.
2. Step 2 wala Web App URL paste karein → **Jodein / Connect**.
3. Login screen aayegi → **Raj / Raj#333** (ya jo bhi user hai). Web wala data yahan dikhega.
4. Server baad mein badalna ho to: **Settings → Server (Web App URL) → Badlein**.

---

## Step 6 — Update kaise karein
| Kya badla | Kya karein |
|---|---|
| Sirf `Code.gs` (backend) | Apps Script mein New version deploy karein. App update ki zaroorat nahi |
| `Index.html` (screens/features) | Naya `Index.html` → `app/src/main/assets/index.html` mein replace karein. `app/build.gradle.kts` mein `versionCode` +1 aur `versionName` badlein → APK dobara banayein → phone par install karein (data aur login bane rahenge). Web ke liye Apps Script mein bhi wahi Index paste karke New version deploy karein |

## Step 7 — Permissions (user ko kya dikhega)
- **Internet:** Google Sheet se baat karne ke liye.
- **Contacts:** sirf "Contacts import" dabane par maangi jaati hai, aur contacts sirf aapke khate mein party jodne ke liye padhe jaate hain. Single contact chunne par permission nahi lagti.

## Step 8 — Play Store par daalna (optional)
1. **Signing key banayein:** Android Studio → **Build → Generate Signed App Bundle / APK → Android App Bundle → Create new** (keystore file aur password **safe rakhein**; ye kho gaya to app update nahi kar payenge).
2. `app/build.gradle.kts` mein release ka `signingConfig = signingConfigs.getByName("debug")` hata dein, aur apni key se **.aab** banayein.
3. Package name badalna ho to `applicationId` badlein (Play par ek baar publish hone ke baad nahi badal sakte).
4. **Google Play Console** account ($25 ek baar) → New app → .aab upload.
5. Contacts permission ki wajah se **Privacy Policy URL** aur "Data safety" form bharna zaroori hai. Likhein: contacts sirf user ke kehne par party jodne ke liye padhe jaate hain, aur data user ki apni Google Sheet mein jaata hai.

## Step 9 — Problem aaye to
| Problem | Hal |
|---|---|
| "Could not reach your HisabKitab server" / "सर्वर से संपर्क नहीं" | URL sahi hai? `/exec` par khatam hota hai? Deploy access **Anyone** hai? Step 1 ke baad **New version** deploy kiya? `?health=1` mein `Android app API (doPost)` OK hai? |
| "इंटरनेट कनेक्शन नहीं है" | Net on karein → **Retry**. Login bana rehta hai |
| Contacts import par "Contacts की अनुमति दें" | Phone **Settings → Apps → HisabKitab → Permissions → Contacts → Allow** |
| CSV kahan gayi? | **Files app → Download → HisabKitab** folder |
| Gradle sync fail | Internet check karein. **File → Invalidate Caches → Restart**. JDK 17 chahiye (Android Studio ke saath aata hai: Settings → Build Tools → Gradle → Gradle JDK = jbr-17) |
| App purana design dikha rahi hai | `assets/index.html` purana hai. Naya copy karke APK dobara banayein |

## Project files
```
HisabKitab-Android/
├─ app/src/main/assets/index.html        ← web wala Index.html (same file)
├─ app/src/main/java/.../MainActivity.kt ← WebView + native bridge (API, contacts, files, print)
├─ app/src/main/res/values/config.xml    ← (optional) Web App URL
├─ app/build.gradle.kts                  ← versionCode / versionName / applicationId
└─ .github/workflows/build-apk.yml       ← GitHub cloud build
```
