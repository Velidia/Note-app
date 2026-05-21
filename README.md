# 📱 Notes Keep Local

A modern, native Android notes application built using **Kotlin** and **Jetpack Compose**. This app provides a fully local, privacy-first note-taking experience with an architecture specifically designed to replicate and match Google Keep's rich data structures.

---

## ✨ Features (Based on Local Room DB)

* 🔒 **100% Offline & Local:** Built on top of Android's **Room Database** framework. All notes, checklists, and configurations stay inside your local `notes_database` safely.
* 📝 **Hybrid Content (Text & Checklists):** Supports both plain text notes and interactive markdown checklist items formatted natively as `[ ] Todo` or `[x] Done`.
* 📌 **Pin & Archive Management:** Keep your dashboard organized with native note prioritization (Pinned notes stay on top) and archiving capabilities.
* 🎨 **Keep-Aligned Custom Themes:** Supports background customization per note using a 6-digit Hex Color (`colorHex`) system to match standard Google Keep aesthetics.
* 🖼️ **Local Image Mapping:** Integrated with **Coil Compose** to seamlessly attach and load local image paths (`imagePath`) within your notes.

---

## 🛠️ Technical Specifications & Stack

* **UI Engine:** Jetpack Compose (using Material 3 BOM)
* **Local Persistence:** Room Database with destructive migration fallback
* **JSON Processing:** Moshi Kotlin (with KSP Codegen compiler tools)
* **Image Loading:** Coil Compose
* **Target SDK:** 36 (Android 16)
* **Min SDK:** 24 (Android 7.0)

---

## 🚀 Local Deployment Guide

### Prerequisites
* [Android Studio](https://developer.android.com/studio) installed.

### Setup Steps

1. **Clone the Repository:**
   ```bash
   git clone [https://github.com/Velidia/Note-app.git](https://github.com/Velidia/Note-app.git)
   cd Note-app
