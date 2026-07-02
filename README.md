# Project Service

A Kotlin Multiplatform library for local file storage and XMP metadata management. It provides a unified API to save, retrieve, and manage media files (images, video, audio) with embedded metadata, mirroring the behavior of the Swift `ProjectService` on iOS.

The library supports **Android** and **Web** (JS/Wasm) targets.

## Features

- **XMP Metadata Embedding**: Embeds prompt, model, and subject metadata into PNG, JPEG, and MP4 files using standard XMP namespaces (`dc:description`, `Iptc4xmpExt:AIPromptInformation`, etc.).
- **File Management**: Save, retrieve, and list files in app-private storage (Android) or Origin Private File System (Web).
- **Audio Handling**: Dedicated methods to save and retrieve a single "active" audio file, automatically clearing previous audio files.
- **Like/Rating System**: Manage like states via `xmp:Rating` metadata.
- **In-Memory State**: Temporary storage for operation arguments like `characterCast` and `imageEdit`.
- **Security**: URL resolution strips path traversal attempts to prevent directory escape.

## Architecture

The project is structured as a Kotlin Multiplatform library (`:project-service`) with an Android demo app (`:demo`).

### Key Files

- **`project-service/src/androidMain/kotlin/market/femi/ProjectService.kt`**: The Android implementation. Uses `Context.getFilesDir()` for storage and delegates XMP operations to `XmpMetadata`.
- **`project-service/src/androidMain/kotlin/market/femi/XmpMetadata.kt`**: Low-level XMP packet manipulation using Adobe XMP Core. Handles binary muxing for PNG (`iTXt`), JPEG (`APP1`), and MP4 (`uuid` box).
- **`project-service/src/webMain/kotlin/market/femi/ProjectService.kt`**: JavaScript/Wasm binding to the `project-service` npm package. Uses `@JsModule` to interface with the underlying WebAssembly/JS implementation.
- **`demo/src/androidTest/kotlin/market/femi/demo/ProjectServiceInstrumentedTest.kt`**: Comprehensive instrumented tests that verify the Android implementation matches the expected behavior (ported from Swift tests).

### Metadata Mapping

The library maps high-level concepts to specific XMP fields:

| Concept | XMP Field(s) |
| :--- | :--- |
| **Prompt** | `Iptc4xmpExt:AIPromptInformation` (primary), `dc:description[x-default]` (fallback) |
| **Model** | `Iptc4xmpExt:AISystemUsed` (primary), `xmp:CreatorTool` (fallback) |
| **Subject** | `dc:subject` (Bag) |
| **Like/Rating** | `xmp:Rating` (5 = Liked, 0 = Not Liked) |

### Container Muxing

XMP packets are embedded into files using Adobe's standard handlers:
- **PNG**: `iTXt` chunk with keyword `XML:com.adobe.xmp`.
- **JPEG**: `APP1` segment with header `http://ns.adobe.com/xap/1.0/ `.
- **MP4**: Top-level `uuid` box with UUID `BE7ACFCB97A942E89C71999491E3AFAC`.

## Installation

### Gradle Setup

Add the Maven repository and dependency to your `build.gradle.kts` or `build.gradle`.

**Repository:**
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

**Dependency:**
```kotlin
dependencies {
    implementation("io.github.femimarket:project-service:<version>")
}
```

*Note: For Android, ensure you have the Adobe XMP Core dependency available if not transitively included. The library uses `com.adobe.internal.xmp:xmpcore`.*

## Usage

### Android

1. **Initialize**: Call `ProjectService.init()` with your application context.
2. **Save Files**: Use `saveFile()` to write bytes with optional metadata.
3. **Retrieve Data**: Use getter methods like `getPrompt()`, `getModel()`, `getSubject()`, and `getLike()`.

```kotlin
import market.femi.ProjectService

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ProjectService.init(applicationContext)
    }
}

// In an Activity or ViewModel
fun saveImage() {
    val imageBytes = ... // your image data
    ProjectService.saveFile(
        data = imageBytes,
        named = "generated.png",
        prompt = "A futuristic city",
        model = "dalle-3",
        subject = listOf("city", "future")
    )
}

fun readMetadata() {
    val prompt = ProjectService.getPrompt("generated.png")
    val isLiked = ProjectService.getLike("generated.png")
}
```

### Web (JS/Wasm)

The Web target exposes a Promise-based API via the `project-service` npm package.

```kotlin
import market.femi.ProjectService
import kotlin.js.Promise
import org.khronos.webgl.Uint8Array

// Initialize (if required by the underlying JS implementation)
ProjectService.ready().await()

// Save
val data = Uint8Array(...)
ProjectService.saveFile(data, "image.png", prompt = "Hello World").await()

// Read
val prompt = ProjectService.getPrompt("image.png").await()
```

## Building and Running

### Prerequisites
- JDK 11 or higher
- Android SDK (for Android targets)
- Node.js (for Web targets)

### Build Commands

```bash
# Build the library
./gradlew :project-service:assemble

# Run Android unit tests
./gradlew :project-service:test

# Run Android instrumented tests (requires device/emulator)
./gradlew :demo:connectedAndroidTest

# Build Web artifacts
./gradlew :project-service:jsBrowserDistribution
./gradlew :project-service:wasmJsBrowserDistribution
```

### Demo App

The `:demo` module provides a minimal Android app to verify the library works in a real environment.

1. Open the project in Android Studio.
2. Run the `demo` configuration.
3. The app displays the path to the `ProjectService` documents directory.

## Testing

The project includes comprehensive instrumented tests in `demo/src/androidTest/kotlin/market/femi/demo/ProjectServiceInstrumentedTest.kt`. These tests verify:
- File saving and retrieval (PNG, MP4).
- Metadata embedding and reading (Prompt, Model, Subject, Like).
- Audio file handling (save, replace, delete).
- In-memory state management (Character Cast, Image Edit).
- Security (Path traversal prevention).

Run tests with:
```bash
./gradlew :demo:connectedAndroidTest
```

## Non-Obvious Conventions

- **Idempotent Init**: `ProjectService.init()` can be called multiple times; it only sets the context if not already set.
- **Audio Replacement**: `saveAudio()` deletes *all* existing audio files in the storage directory before saving the new one. It does not affect images.
- **Path Traversal**: `getUrl()` uses `File(file).name` to strip directory components, ensuring files are always saved within the `documents` directory.
- **Empty Subject**: Passing an empty list for `subject` results in `null` being returned by `getSubject()`, effectively removing the subject metadata.
- **Like State**: `getLike()` returns `true` if `xmp:Rating` is between 1 and 5. It returns `false` if the rating is 0 or absent.