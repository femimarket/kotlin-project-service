# Project Service

A Kotlin Multiplatform library for local file storage and XMP metadata management. It provides a unified API to save, retrieve, and manage media files (images, video, audio) with embedded metadata, mirroring the behavior of a Swift `ProjectService` implementation.

The library supports **Android** (native) and **Web** (JS/Wasm) targets.

## Features

- **XMP Metadata Embedding**: Embeds structured metadata into PNG, JPEG, and MP4 files using standard Adobe XMP namespaces.
- **Metadata Fields**:
  - `prompt`: Stored in `Iptc4xmpExt:AIPromptInformation` and `dc:description`.
  - `model`: Stored in `Iptc4xmpExt:AISystemUsed` and `xmp:CreatorTool`.
  - `subject`: Stored as a bag in `dc:subject`.
  - `rating`: Stored in `xmp:Rating` (used for "like" functionality).
- **File Management**:
  - Save/Retrieve files with optional metadata.
  - Manage a single "audio" file (replaces previous audio on save).
  - List all generated files.
- **In-Memory State**:
  - `characterCast`: Stores a pair of filenames for character casting operations.
  - `imageEdit`: Stores a filename for image editing operations.
- **Security**: URL resolution strips path traversal attempts (e.g., `../../../etc/passwd`).

## Architecture

The project is structured as a Kotlin Multiplatform library (`:project-service`) with an Android demo app (`:demo`).

### Key Files

- **`project-service/build.gradle.kts`**: Defines the KMP targets (`androidLibrary`, `js`, `wasmJs`) and dependencies (Adobe XMP Core for Android).
- **`project-service/src/androidMain/kotlin/market/femi/ProjectService.kt`**: The Android implementation. Uses `Context.getFilesDir()` as the storage root and delegates XMP operations to `XmpMetadata`.
- **`project-service/src/androidMain/kotlin/market/femi/XmpMetadata.kt`**: Low-level XMP packet manipulation. Handles binary parsing/injection for PNG (`iTXt`), JPEG (`APP1`), and MP4 (`uuid` box) containers using Adobe XMP Core.
- **`project-service/src/webMain/kotlin/market/femi/ProjectService.kt`**: A JS/Wasm binding to the external `project-service` npm package. It exposes a Promise-based API for browser environments.
- **`demo/src/androidTest/kotlin/market/femi/demo/ProjectServiceInstrumentedTest.kt`**: Comprehensive instrumented tests that verify the Android implementation's behavior, including metadata embedding, file I/O, and edge cases.

### XMP Container Muxing

The library embeds XMP packets into media files using specific container formats:
- **PNG**: Embedded in the `iTXt` chunk with keyword `XML:com.adobe.xmp`.
- **JPEG**: Embedded in the `APP1` segment with header `http://ns.adobe.com/xap/1.0/ `.
- **MP4**: Embedded in a top-level `uuid` box with UUID `BE7ACFCB97A942E89C71999491E3AFAC`.

## Installation

### Gradle Setup

Add the repository and dependency to your `build.gradle.kts` or `build.gradle`.

**Repository**:
```kotlin
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/femimarket/kotlin-project-service")
        credentials {
            username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
            password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
        }
    }
}
```

**Dependency**:
```kotlin
dependencies {
    implementation("io.github.femimarket:project-service:<version>")
}
```

*Note: The default version is `dev`. To publish a specific version, use `-PlibraryVersion=<version>`.*

## Usage

### Android

1. **Initialize**: Call `ProjectService.init(context)` once, preferably in your `Application.onCreate()`.
2. **Save Files**: Use `saveFile` to write bytes with optional metadata.
3. **Retrieve Data**: Use getters like `getPrompt`, `getModel`, `getSubject`, `getLike`.
4. **Manage Audio**: Use `saveAudio` to replace the current audio file.
5. **In-Memory State**: Use `setCharacterCast`, `getImageEdit`, etc., for transient operation state.

#### Example

```kotlin
import market.femi.ProjectService

// Initialization (e.g., in Application class)
ProjectService.init(applicationContext)

// Save an image with metadata
val imageBytes = ... // your image data
ProjectService.saveFile(
    data = imageBytes,
    named = "generated.png",
    prompt = "A futuristic city",
    model = "dalle-3",
    subject = listOf("city", "future")
)

// Retrieve metadata
val prompt = ProjectService.getPrompt("generated.png") // "A futuristic city"
val isLiked = ProjectService.getLike("generated.png") // false

// Like the file
ProjectService.like("generated.png", true)
ProjectService.getLike("generated.png") // true

// Save audio (replaces previous audio)
ProjectService.saveAudio(audioBytes, "sound.m4a")
val currentAudio = ProjectService.getAudio() // Returns the File object
```

### Web (JS/Wasm)

The Web target relies on an external `project-service` npm package. The Kotlin code provides a binding to this package.

```kotlin
import market.femi.ProjectService

// Ensure the npm package is installed and the binding is available
ProjectService.ready().await()

// Save file (returns Promise)
ProjectService.saveFile(
    data = uint8Array,
    name = "image.png",
    prompt = "Hello World"
).await()

// Get prompt (returns Promise<String?>)
val prompt = ProjectService.getPrompt("image.png").await()
```

## Building and Testing

### Build the Library

```bash
./gradlew :project-service:assemble
```

### Run Android Instrumented Tests

These tests run on a connected device or emulator and verify the core logic.

```bash
./gradlew :demo:connectedAndroidTest
```

### Run Unit Tests

```bash
./gradlew :project-service:test
```

## Non-Obvious Conventions

1. **Storage Root**: On Android, files are stored in `Context.getFilesDir()`, which corresponds to the app's private internal storage. This is the analog of iOS's `Documents/` directory.
2. **Audio Handling**: `saveAudio` deletes *all* existing audio files before writing the new one. It does not affect images or other media.
3. **Path Traversal Protection**: `getUrl(file)` always resolves to a file inside the `documents` directory, stripping any parent directory references (`..`) from the filename.
4. **Idempotent Clears**: `clearCharacterCast()` and `clearImageEdit()` are idempotent; calling them when the state is already null has no effect.
5. **XMP Namespace Registration**: The `Iptc4xmpExt` namespace is registered in the `XmpMetadata` object's `init` block to ensure compatibility with the Adobe XMP Core library.