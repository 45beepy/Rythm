# Rhythm 🎶

A native Android music player built from scratch with Kotlin and Jetpack Compose. This project is a step-by-step learning journey to understand every "nook and corner" of modern Android development, from the ground up.

---

## 🛠️ Technology Stack

* **IDE:** Android Studio Narwhal 3 (2025.1.3)
* **Language:** Kotlin
* **UI Toolkit:** Jetpack Compose (Material 3)
* **Build System:** Gradle with Kotlin DSL (`.kts`)
* **Permissions:** Accompanist Permissions
* **Version Control:** Git & GitHub

---

## 🚀 Development Log & Milestones

This project is being built in "lessons," with each major step committed to Git.

### Lesson 1: Project Foundation
* Set up the project using the "Empty Activity (Material3)" template.
* Configured the project for API 26 (Android 8.0) and Kotlin DSL.
* Added the `<uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />` to the `AndroidManifest.xml` to declare our intent.

### Lesson 2: Permissions & Media Loading
* Added the `com.google.accompanist:accompanist-permissions` library to handle runtime permissions.
* Created a `PermissionGatedContent` composable to manage the `READ_MEDIA_AUDIO` permission flow.
* Defined a `Song` data class to model our music data.
* Implemented a `SongLoader` composable that, upon grant, uses a `ContentResolver` to query the `MediaStore` for all audio files on the device.

### Lesson 2.5: Version Control Setup
* Initialized a local Git repository.
* Configured a `.gitignore` file to exclude build files, IDE settings, and local properties.
* Created an empty, private remote repository on GitHub.
* Renamed the local `master` branch to `main` to match modern standards.
* Learned to use a **Personal Access Token (PAT)** for GitHub authentication from the command line.
* Successfully pushed the initial commit to `origin main`.

### Lesson 3: Building the Song List UI
* Replaced the simple "Loaded X songs" text with a functional UI.
* Added a `CircularProgressIndicator` to show a loading state while `SongLoader` is working.
* Created a `SongList` composable using `LazyColumn` for efficient, "lazy" list rendering.
* Designed a `SongListItem` composable to display a single song's title, artist, and duration, using `Row`, `Column`, `Icon`, and `Text`.
* Pushed the new UI code to GitHub.

---

## 🌎 Next Steps

The next major feature is to implement the playback "engine."

* [ ] Set up a **Jetpack Media3** `MediaLibraryService` to handle background audio.
* [ ] Connect the UI to the service to play a song when it's clicked.
* [ ] Build the main "Now Playing" screen.

---

## ⚙️ How to Build

1.  Clone the repository:
    ```bash
    git clone [https://github.com/45beepy/Rythm.git](https://github.com/45beepy/Rythm.git)
    ```
2.  Open the project in Android Studio (Narwhal 3 or newer).
3.  Let Gradle sync and download all the required dependencies.
4.  Build and run on an Android emulator or a physical device.
5.  **Note:** You must have local audio files (FLAC, MP3, etc.) on the device/emulator for the app to find and display them.