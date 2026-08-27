# MacroMandate 👁️📟

**MacroMandate** is a high-performance, privacy-conscious nutrition and calorie surveillance application built for Android with Jetpack Compose. Designed with a retro tactical HUD / authoritarian terminal aesthetic, it empowers users to log meals effortlessly via computer vision, monitor daily macronutrient compliance, and maintain full control over their data.

---

## ⚡ Key Features

- 📸 **AI Food & Drink Vision**: Capture or select meals to estimate calories, protein, carbohydrates, and fats via Hugging Face multimodal vision endpoints.
- ✍️ **Manual Refueling Fallback**: Quickly record custom meals and macros directly when offline or in low-light environments.
- ✏️ **In-Place Meal Correction**: Edit food names, calories, macros, and liquid status at any time.
- 📊 **Metabolic Adherence Dashboard**: Real-time intake HUD displaying today's calories, remaining target delta, and macronutrient distribution.
- 📈 **7-Day Trend Intelligence**: Target-scaled weekly caloric and macro visualizations with at-a-glance day metrics.
- 📑 **Weekly Dossier Summary**: Synthesize 7-day nutritional intelligence debriefs with one-tap clipboard copy and markdown export.
- 🗺️ **Geospatial Surveillance**: Opt-in coordinates watermarking and interactive meal location radar.
- 🎨 **Terminal Theme Customizer**: Dynamic UI switching between *Cyber Cyan*, *Phosphor Green*, *Amber CRT*, and *Stark Mono*.
- 💾 **Data Portability & Zero Lock-in**:
  - Full **JSON Backup & Restore** (Version 1 Schema)
  - Standards-compliant **CSV Dossier Export** (with spreadsheet formula injection safeguards)
- 🔒 **Security & Privacy First**:
  - API keys masked in UI and kept strictly in private sandbox storage.
  - Zero credential leakage in logs, analytics, or backups.
  - Periodic audit buffer pruning and manual log clearing.

---

## 🛠️ Architecture & Tech Stack

- **UI Framework**: Modern declarative Jetpack Compose with Material 3 & Custom Tactical Tokens.
- **Architecture**: MVVM + Repository pattern with Kotlin Coroutines & StateFlow.
- **Persistence**: Room Database with full schema validation and DataStore Preferences.
- **Networking**: Retrofit 2 + OkHttp 4 with OpenAI-compatible Chat Completions format.
- **Camera Pipeline**: CameraX with downsampled bitmap decoding to prevent OOM errors.
- **Background Tasks**: WorkManager for periodic compliance surveillance and reminder scheduling.
- **Widgets**: Jetpack Glance tactical home-screen widget.

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Ladybug / Meerkat or newer.
- Android SDK 35+ (compiles against SDK 37, minimum SDK 29).
- JDK 17.

### Build and Run
```bash
# Clone the repository
git clone https://github.com/sharek/MacroMandate.git
cd MacroMandate

# Run all unit tests
./gradlew test

# Assemble debug APK
./gradlew assembleDebug

# Install on connected device via ADB
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Configuration (`local.properties`)
```properties
# Optional: Pre-configure Hugging Face API Token (or enter in-app in Settings)
HUGGINGFACE_API_KEY=hf_your_token_here

# Optional: Vision Model Endpoint override
MANDATE_API_BASE_URL=https://router.huggingface.co/
MANDATE_MODEL_ID=google/gemma-4-31B-it
```

---

## 📄 Release & Signing

For details on configuring production signing keystores and building release APKs/AABs with R8 code shrinking enabled, refer to [RELEASE_GUIDE.md](file:///c:/Users/shareef01/AndroidStudioProjects/MacroMandate/RELEASE_GUIDE.md).

---

## 🛡️ License

Built for sovereign dietary discipline. All rights reserved.
