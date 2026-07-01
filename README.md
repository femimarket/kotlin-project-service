# Kotlin Project Service

A Kotlin Multiplatform library providing a unified API for local file storage, XMP metadata embedding, and state management for generative media (images, video, audio).

This project ports the functionality of the Swift `ProjectService` to Kotlin, enabling consistent metadata handling across Android and Web platforms. It supports embedding AI generation metadata (prompts, models, subjects) into PNG, JPEG, and MP4 files using standard XMP containers, and manages in-memory operation states like character casts and image edits.

## Features

- **Cross-Platform Storage**: Unified API for Android (`Context.filesDir`) and Web (Origin Private File System).
- **XMP Metadata Embedding**: Embeds structured metadata into binary files without altering the visual/audio content.
  - **PNG**: Uses `iTXt` chunks.
  - **JPEG**: Uses `APP1` segments.
  - **MP4**: Uses top-level `uuid` boxes.
- **Metadata Fields**:
  - `prompt`: Stored in `Iptc4xmpExt:AIPromptInformation` and `dc:description`.
  - `model`: Stored in `Iptc4xmpExt:AISystemUsed` and `xmp:CreatorTool`.
  - `subject`: Stored as a bag in `dc:subject`.
  - `rating`: Stored in `xmp:Rating` (used for "like" functionality).
- **Audio Management**: Atomic replacement of audio files with automatic cleanup of previous audio assets.
- **In-Memory State**: Tracks character casts and image edit targets during the application lifecycle.

## Project Structure

```text
├── settings.gradle.kts          # Root settings, includes :library and :demo
├── build.gradle.kts             # Root build configuration
├── library/                     # The core multiplatform library
│   ├── build.gradle.kts         # KMP setup (Android, JS, WasmJs)
│   └── src/
│       ├── androidMain/kotlin/market/femi/
│       │   ├── ProjectService.kt # Android implementation (File I/O + XMP)
│       │   └── XmpMetadata.kt    # Adobe XMP Core integration & binary muxing
│       └── webMain/kotlin/market/femi/
│           └── ProjectService.kt # JS/Wasm binding to npm `project-service`
└── demo/                        # Android demo app
    ├── build.gradle.kts
    └── src/
        ├── main/kotlin/market/femi/demo/MainActivity.kt
        └── androidTest/kotlin/market/femi/demo/ProjectServiceInstrumentedTest.kt
```

## Installation

### Gradle Setup

Add the repository and dependency to your `build.gradle.kts` (or `build.gradle`).

**1. Add GitHub Packages Repository**

```kotlin
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/femimarket/kotlin-project-service")
        credentials {
            username = providers.gradleProperty("gpr.user").orNull
                ?: System.getenv("GITHUB_ACTOR")
            password = providers.gradleProperty("gpr.key").orNull
                ?: System.getenv("GITHUB_TOKEN")
        }
    }
}
```

**2. Add Dependency**

```kotlin
dependencies {
    implementation("io.github.femimarket:project-service:1.0.0") // Replace with actual version
}
```

*Note: For local development, use `implementation(project(":library"))`.*

## Usage

### Android

Initialize the service with the application context before use.

```kotlin
import market.femi.ProjectService

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ProjectService.init(applicationContext)
    }
}
```

Save a file with metadata:

```kotlin
val imageBytes = ... // Your image data
ProjectService.saveFile(
    data = imageBytes,
    named = "generation_01.png",
    prompt = "A futuristic city",
    model = "dall-e-3",
    subject = listOf("city", "future", "neon")
)
```

Retrieve metadata:

```kotlin
val prompt = ProjectService.getPrompt("generation_01.png")
val isLiked = ProjectService.getLike("generation_01.png")
```

### Web (JavaScript/Wasm)

The web target binds to the `project-service` npm package. Ensure the package is installed in your web project.

```kotlin
import market.femi.ProjectService
import org.khronos.webgl.Uint8Array
import kotlinx.coroutines.*

// Initialize if required by the underlying JS library
ProjectService.ready().await()

// Save file
val data = Uint8Array(byteArrayOf(0x89, 0x50, ...)) // PNG header etc.
ProjectService.saveFile(
    data = data,
    name = "web-gen.png",
    prompt = "Web generated image"
).await()

// Get prompt
val prompt = ProjectService.getPrompt("web-gen.png").await()
```

## Architecture & Implementation Details

### Android Implementation (`library/src/androidMain`)

- **Storage**: Files are stored in `Context.getFilesDir()`, which corresponds to the iOS `Documents` directory.
- **XMP Engine**: Uses the Adobe XMP Core Java library (`com.adobe.internal.xmp`).
- **Binary Muxing**: The `XmpMetadata` object handles the low-level binary manipulation to embed/extract XMP packets into specific container formats:
  - **PNG**: Parses `iTXt` chunks. Looks for keyword `XML:com.adobe.xmp`.
  - **JPEG**: Parses `APP1` segments. Looks for header `http://ns.adobe.com/xap/1.0/ `.
  - **MP4**: Parses top-level boxes. Looks for `uuid` box with UUID `BE7ACFCB97A942E89C71999491E3AFAC`.
- **Safety**: `getUrl()` strips path traversal attempts (e.g., `../../../etc/passwd`) to ensure files are always written to the documents directory.

### Web Implementation (`library/src/webMain`)

- **Binding**: Uses Kotlin/JS external declarations to bind to the `project-service` npm package.
- **Storage**: Relies on the underlying JS library's implementation, which typically uses the Origin Private File System (OPFS) or IndexedDB for persistence.
- **Async**: All operations return `Promise` objects, requiring `await` or `.then()` in Kotlin/JS or Kotlin/Wasm.

### In-Memory State

The library maintains volatile in-memory state for:
- `characterCast`: A pair of filenames representing characters in a generation.
- `imageEdit`: A single filename representing the target for image editing.

These are process-lifetime only and are cleared via `clearCharacterCast()` and `clearImageEdit()`.

## Building and Testing

### Build the Library

```bash
./gradlew :library:assemble
```

### Run Android Instrumented Tests

The `demo` module contains instrumented tests that verify the library's behavior on Android devices/emulators.

```bash
./gradlew :demo:connectedAndroidTest
```

### Run Unit Tests (Common)

```bash
./gradlew :library:jvmTest
```

## Key Files

- `library/src/androidMain/kotlin/market/femi/ProjectService.kt`: The main Android API entry point.
- `library/src/androidMain/kotlin/market/femi/XmpMetadata.kt`: Handles XMP packet creation, embedding, and extraction for PNG/JPEG/MP4.
- `library/src/webMain/kotlin/market/femi/ProjectService.kt`: External declarations for the Web/JS target.
- `demo/src/androidTest/kotlin/market/femi/demo/ProjectServiceInstrumentedTest.kt`: Comprehensive test suite mirroring the Swift test suite.

## Publishing

The library is configured for publishing to GitHub Packages.

1. Set `gpr.user` and `gpr.key` in `gradle.properties` or environment variables.
2. Set the version via `-PlibraryVersion=1.0.0`.
3. Run:

```bash
./gradlew :library:publish
```