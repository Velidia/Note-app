# Notes Keep Local

A modern, native Android notes application built using **Kotlin** and **Jetpack Compose**. This app provides a fully local, privacy-first note-taking experience with an architecture specifically designed to replicate and match Google Keep's rich data structures.

---

## Features (Based on Local Room DB)

* **100% Offline & Local:** Built on top of Android's **Room Database** framework. All notes, checklists, and configurations stay inside your local `notes_database` safely.
* **Hybrid Content (Text & Checklists):** Supports both plain text notes and interactive markdown checklist items formatted natively as `[ ] Todo` or `[x] Done`.
* **Pin & Archive Management:** Keep your dashboard organized with native note prioritization (Pinned notes stay on top) and archiving capabilities.
* **Keep-Aligned Custom Themes:** Supports background customization per note using a 6-digit Hex Color (`colorHex`) system to match standard Google Keep aesthetics.
* **Local Image Mapping:** Integrated with **Coil Compose** to seamlessly attach and load local image paths (`imagePath`) within your notes.
* **Google Keep Import:** Import notes and images directly from a Google Takeout ZIP or a single Keep JSON file, with automatic **duplicate detection** so re-imports stay clean.
* **ZIP Backup & Restore:** Export every note and image into a single re-importable ZIP, with non-destructive database migration that preserves your existing data.
* **Privacy Hardened:** Android cloud backup and auto-sync are fully disabled — your data never leaves the device unless you choose to export it.

---

## Technical Specifications & Stack

* **UI Engine:** Jetpack Compose (using Material 3 BOM)
* **Local Persistence:** Room Database with non-destructive migration (`imagePath` column added in v2)
* **Duplicate Prevention:** SHA-256 attachment signature matching on import
* **JSON Processing:** Moshi Kotlin (with KSP compiler tooling)
* **Image Loading:** Coil Compose
* **Target SDK:** 36 (Android 16)
* **Min SDK:** 24 (Android 7.0)
* **Version:** 2.0.1

---

## Local Deployment Guide

### Prerequisites
* [Android Studio](https://developer.android.com/studio) installed.

### Setup Steps

1. **Clone the Repository:**
   ```bash
   git clone https://github.com/Velidia/Note-app.git
   cd Note-app
   ```

2. **Open in Android Studio:**
   - Launch Android Studio.
   - Choose **Open** and select the cloned `Note-app` directory.

3. **Sync & Run:**
   - Wait for Gradle to finish syncing.
   - Run the `app` configuration on an emulator or a physical device (minimum API 24).

### Building from the Command Line

From the project root on Windows PowerShell:

```powershell
.\gradlew.bat assembleDebug
```

The resulting APK is written to `app/build/outputs/apk/debug/`. No Gemini API key, Google account, or internet permission is required to build or run the app.

---

## Backup & Restore

Open **Settings > Local Backup > Export ZIP Backup** to save all notes and images. The ZIP can be restored later via the **Pick ZIP / JSON** button.

To import from Google Keep, download your data from [Google Takeout](https://takeout.google.com/) and select the resulting ZIP — no extraction needed.

---

## Privacy

The database and attachments are stored in the app's internal storage. Android cloud backup is disabled. Data only leaves the device when you explicitly choose an export location for your backup.

---

## License

This project is licensed under the **MIT License** — see the [LICENSE](LICENSE) file for details.
