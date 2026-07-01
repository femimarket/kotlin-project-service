# Kotlin Project Service

A Kotlin Multiplatform library for embedding AI-generated metadata (prompts, models, subjects) into media files (PNG, JPEG, MP4) and managing local file storage.

This project provides a unified API for Android and Web platforms, mirroring the functionality of the original Swift `ProjectService`. It handles XMP metadata injection for images and videos, allowing downstream applications to store and retrieve generation context directly within the media files.

## Features

- **Metadata Embedding**: Embeds AI generation details (prompt, model, subject) into PNG, JPEG, and MP4 files using standard XMP metadata structures.
- **Cross-Platform**: Supports Android (via native Java/Kotlin) and Web (via JavaScript/Wasm interop with an npm package).
- **File Management**: Local storage management including saving files, retrieving audio, and listing generations.
- **Like/Rating System**: Supports marking files as "liked" via XMP ratings.
- **In-Memory State**: Manages transient operation arguments like character casts and image edit targets.

## Architecture

The project is structured as a Kotlin Multiplatform library (`:library`) with an Android demo application (`:demo`).

### Project Structure

```text
.
├── settings.gradle.kts          # Root settings, includes library and demo
├── build.gradle.kts             # Root build configuration
├── library/                     # The core multiplatform library
│   ├── build.gradle.kts         # KMP configuration (Android, JS, Wasm)
│   └── src/
│       ├── androidMain/kotlin/market/femi/
│       │   ├── ProjectService.kt # Android implementation (local storage + XMP)
│       │   └── XmpMetadata.kt    # Adobe XMP Core integration for Android
│       └── webMain/kotlin/market/femi/
│           └── ProjectService.kt # JS/Wasm binding to npm `project-service`
└── demo/                        # Android demo app
    ├── build.gradle.kts
    └── src/
        ├── main/kotlin/market/femi/demo/MainActivity.kt
        └── androidTest/kotlin/market/femi/demo/ProjectServiceInstrumentedTest.kt
```

### Key Components

1.  **`ProjectService` (Android)**:
    -   Located in `library/src/androidMain/kotlin/market/femi/ProjectService.kt`.
    -   Manages file I/O in the app's private `filesDir` (analogous to iOS `Documents`).
    -   Delegates metadata operations to `XmpMetadata`.
    -   Handles in-memory state for `characterCast` and `imageEdit`.

2.  **`XmpMetadata` (Android)**:
    -   Located in `library/src/androidMain/kotlin/market/femi/XmpMetadata.kt`.
    -   Uses Adobe XMP Core to parse and serialize XMP packets.
    -   Implements container-specific muxing:
        -   **PNG**: Injects into `iTXt` chunk with keyword `XML:com.adobe.xmp`.
        -   **JPEG**: Injects into `APP1` segment with header `http://ns.adobe.com/xap/1.0/ `.
        -   **MP4**: Injects into a top-level `uuid` box with UUID `BE7ACFCB97A942E89C71999491E3AFAC`.
    -   Maps logical fields to XMP properties:
        -   `prompt` → `dc:description` and `Iptc4xmpExt:AIPromptInformation`
        -   `model` → `xmp:CreatorTool` and `Iptc4xmpExt:AISystemUsed`
        -   `subject` → `dc:subject` (Bag)
        -   `like` → `xmp:Rating` (1-5)

3.  **`ProjectService` (Web)**:
    -   Located in `library/src/webMain/kotlin/market/femi/ProjectService.kt`.
    -   Provides a Kotlin binding to the external npm package `project-service`.
    -   Uses `JsInterop` to call asynchronous methods returning `Promise` objects.
    -   Relies on the underlying JS implementation for storage (likely OPFS) and XMP handling.

## Installation & Build

### Prerequisites

-   JDK 11 or higher
-   Android SDK (for Android targets)
-   Node.js (for Web targets)

### Building the Library

To build the library for all supported platforms:

```bash
./gradlew :library:assemble
```

### Publishing

The library is configured to publish to GitHub Packages. To publish:

1.  Ensure you have a `gpr.user` and `gpr.key` (or `GITHUB_TOKEN` env var) set in your Gradle properties or environment.
2.  Run:

```bash
./gradlew :library:publish
```

You can override the version using:

```bash
./gradlew :library:publish -PlibraryVersion=1.0.0
```

## Usage

### Android

1.  **Add Dependency**: Include the library in your `build.gradle.kts`:

    ```kotlin
    dependencies {
        implementation("io.github.femimarket:library:1.0.0") // Use the published version
    }
    ```

2.  **Initialize**: Call `ProjectService.init()` once, typically in your `Application.onCreate()`:

    ```kotlin
    class MyApplication : Application() {
        override fun onCreate() {
            super.onCreate()
            ProjectService.init(applicationContext)
        }
    }
    ```

3.  **Save a File with Metadata**:

    ```kotlin
    val imageData: ByteArray = ... // Your image bytes
    ProjectService.saveFile(
        data = imageData,
        named = "generated_image.png",
        prompt = "A futuristic city",
        model = "dalle-3",
        subject = listOf("city", "futuristic")
    )
    ```

4.  **Read Metadata**:

    ```kotlin
    val prompt = ProjectService.getPrompt("generated_image.png")
    val model = ProjectService.getModel("generated_image.png")
    val subject = ProjectService.getSubject("generated_image.png")
    val isLiked = ProjectService.getLike("generated_image.png")
    ```

5.  **Manage Audio**:

    ```kotlin
    val audioData: ByteArray = ...
    ProjectService.saveAudio(audioData, "sound.m4a")
    val currentAudio = ProjectService.getAudio() // Returns the File object
    ```

6.  **Manage In-Memory State**:

    ```kotlin
    ProjectService.setCharacterCast("hero.png", "villain.png")
    val cast = ProjectService.getCharacterCast() // Pair<String, String>?

    ProjectService.setImageEdit("portrait.png")
    val editTarget = ProjectService.getImageEdit() // String?
    ```

### Web (JavaScript/Wasm)

1.  **Add Dependency**: Ensure the `project-service` npm package is available in your web project. The Kotlin library binds to it.

2.  **Initialize**: Call `ready()` to ensure the underlying service is initialized.

    ```kotlin
    suspend fun initWeb() {
        ProjectService.ready().await()
    }
    ```

3.  **Save a File**:

    ```kotlin
    import kotlin.js.Promise
    import org.khronos.webgl.Uint8Array

    val data = Uint8Array(byteArrayOf(0x89, 0x50, ...)) // PNG bytes
    ProjectService.saveFile(
        data = data,
        name = "web_image.png",
        prompt = "Web generated image",
        model = "web-model",
        subject = jsArrayOf("web", "image")
    ).await()
    ```

4.  **Read Metadata**:

    ```kotlin
    val prompt = ProjectService.getPrompt("web_image.png").await()
    ```

## Testing

Instrumented tests are provided in the `demo` module to verify the Android implementation.

```bash
./gradlew :demo:connectedAndroidTest
```

These tests exercise the `ProjectService` API, ensuring metadata embedding, retrieval, and file management work correctly for PNG, JPEG, and MP4 formats.

## Non-Obvious Conventions

-   **Path Traversal Protection**: `ProjectService.getUrl()` strips directory traversal attempts (e.g., `../../../etc/passwd`) to ensure files are always saved within the app's private storage directory.
-   **Audio Exclusivity**: `saveAudio()` deletes any existing audio files in storage before saving the new one. It does not affect images.
-   **Idempotent Initialization**: `ProjectService.init()` can be called multiple times; it is idempotent.
-   **XMP Namespace Registration**: The Android implementation explicitly registers the `Iptc4xmpExt` namespace to ensure compatibility with the Rust/Swift implementations.
-   **MP4 UUID Box**: For MP4 files, XMP data is stored in a `uuid` box with a specific UUID. This is a non-standard but widely supported method for embedding XMP in video.
-   **Web Async**: The Web implementation is fully asynchronous due to the nature of JS interop and potential OPFS operations. All methods return `Promise` objects.