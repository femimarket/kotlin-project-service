# Kotlin Project Service

A Kotlin Multiplatform library for embedding AI-generated media metadata (prompts, models, subjects) into files using XMP standards. It provides a unified API for Android and Web platforms, mirroring the behavior of the Swift `ProjectService` implementation.

## Overview

This project serves as the backend logic for managing AI-generated assets. It handles:
- **Metadata Embedding**: Injects XMP metadata into PNG, JPEG, and MP4 files.
- **Storage Management**: Manages local file storage (Android) or Origin Private File System (Web).
- **State Management**: Tracks "likes", audio files, and operation arguments (character casts, image edits) in memory.

### Supported Formats
- **Images**: PNG, JPEG/JPG
- **Video**: MP4, M4V, MOV

### Metadata Mapping
The library maps high-level concepts to specific XMP fields to ensure compatibility with Adobe standards and AI tooling:

| Concept | XMP Field(s) |
| :--- | :--- |
| **Prompt** | `Iptc4xmpExt:AIPromptInformation` (Primary)<br>`dc:description[x-default]` (Fallback) |
| **Model** | `Iptc4xmpExt:AISystemUsed` (Primary)<br>`xmp:CreatorTool` (Fallback) |
| **Subject** | `dc:subject` (Bag) |
| **Like/Rating** | `xmp:Rating` (5 = Liked, 0 = Not Liked) |

## Architecture

The project is structured as a Kotlin Multiplatform (KMP) library with a demo application.

### Project Structure
```text
├── library/          # The core KMP library
│   ├── build.gradle.kts
│   └── src/
│       ├── androidMain/kotlin/market/femi/
│       │   ├── ProjectService.kt   # Android implementation (File I/O + XMP)
│       │   └── XmpMetadata.kt      # Low-level XMP packet muxing (Adobe XMP Core)
│       └── webMain/kotlin/market/femi/
│           └── ProjectService.kt   # Web binding (JS Interop to npm package)
├── demo/             # Android demo app
│   └── src/
│       ├── main/kotlin/market/femi/demo/
│       │   └── MainActivity.kt     # Minimal host activity
│       └── androidTest/kotlin/...  # Instrumented tests
└── settings.gradle.kts
```

### Platform Implementations

#### Android (`library/src/androidMain`)
- **Storage**: Uses `Context.getFilesDir()` as the private documents directory.
- **XMP Engine**: Uses the **Adobe XMP Core** Java library (`com.adobe.internal.xmp`).
- **Muxing**: `XmpMetadata.kt` manually parses and injects XMP packets into binary containers:
  - **PNG**: Injects into `iTXt` chunk with keyword `XML:com.adobe.xmp`.
  - **JPEG**: Injects into `APP1` segment with header `http://ns.adobe.com/xap/1.0/ `.
  - **MP4**: Injects into a top-level `uuid` box with UUID `BE7ACFCB97A942E89C71999491E3AFAC`.

#### Web (`library/src/webMain`)
- **Storage**: Relies on the **Origin Private File System (OPFS)** via the external npm package `project-service`.
- **Interface**: Uses Kotlin/JS external declarations (`@JsModule`) to bind to the JavaScript API.
- **Async**: All operations return `Promise` types, requiring `await` or `suspend` wrappers in Kotlin code.

## Installation

### Gradle Setup

Add the repository and dependency to your `build.gradle.kts` (or `build.gradle`).

**1. Add Repository**
If publishing to GitHub Packages, ensure the repository is configured in `settings.gradle.kts` or the module's `repositories` block. For local development, ensure the library is included in `settings.gradle.kts`:

```kotlin
include(":library")
include(":demo")
```

**2. Add Dependency**
In your app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":library")) // For local development
    // OR
    implementation("io.github.femimarket:project-service:<version>") // For published version
}
```

### Android Specifics
Ensure your `AndroidManifest.xml` has the necessary permissions if you plan to access external storage (though this library defaults to internal app storage, which requires no permissions).

## Usage

### Android

1. **Initialize**: Call `ProjectService.init()` with your application context. This should be done once, typically in `Application.onCreate()`.
2. **Save Files**: Use `ProjectService.saveFile()` to write bytes and embed metadata.
3. **Read Metadata**: Use getters like `getPrompt()`, `getModel()`, `getSubject()`.

```kotlin
import market.femi.ProjectService

// Initialization
ProjectService.init(applicationContext)

// Save a PNG with metadata
val imageBytes = ... // your byte array
ProjectService.saveFile(
    data = imageBytes,
    named = "generated_art.png",
    prompt = "A futuristic city",
    model = "dalle-3",
    subject = listOf("city", "future", "neon")
)

// Retrieve metadata
val prompt = ProjectService.getPrompt("generated_art.png") // "A futuristic city"
val isLiked = ProjectService.getLike("generated_art.png")  // false
```

### Web (Kotlin/JS or Kotlin/Wasm)

The Web implementation is asynchronous. You must handle `Promise` results.

```kotlin
import market.femi.ProjectService
import kotlinx.coroutines.await

// Ensure the service is ready
ProjectService.ready().await()

// Save a file
val data = Uint8Array(...)
ProjectService.saveFile(
    data = data,
    name = "web_art.png",
    prompt = "Web generated art"
).await()

// Read metadata
val prompt = ProjectService.getPrompt("web_art.png").await()
```

## Key APIs

### `ProjectService` Object

| Method | Description |
| :--- | :--- |
| `init(context)` | **Android Only**. Binds the application context. |
| `documents` | **Android Only**. Returns the `File` object for the app's private storage directory. |
| `saveFile(data, name, prompt?, model?, subject?)` | Saves bytes to storage. Embeds XMP metadata if provided. |
| `like(file, liked)` | Sets the like state by writing `xmp:Rating`. |
| `getAllGenerations()` | Returns a list of all files in the storage directory. |
| `saveAudio(data, name)` | Saves an audio file. Deletes any existing audio files first. |
| `getAudio()` | Returns the single audio file in storage, or null. |
| `getPrompt(file)` | Reads the prompt from XMP metadata. |
| `getModel(file)` | Reads the model name from XMP metadata. |
| `getSubject(file)` | Reads the subject keywords from XMP metadata. |
| `getLike(file)` | Returns true if `xmp:Rating` is between 1 and 5. |
| `getUrl(file)` | Returns the `File` (Android) or URL string (Web) for the file, sanitizing path traversal. |
| `setCharacterCast(a, b)` | Stores a pair of filenames for character-cast operations (in-memory). |
| `getCharacterCast()` | Retrieves the stored character-cast pair. |
| `clearCharacterCast()` | Clears the stored character-cast pair. |
| `setImageEdit(file)` | Stores a filename for image-edit operations (in-memory). |
| `getImageEdit()` | Retrieves the stored image-edit filename. |
| `clearImageEdit()` | Clears the stored image-edit filename. |

## Testing

### Android Instrumented Tests
The `demo` module contains instrumented tests that verify the library's behavior on Android devices/emulators. These tests ensure that XMP metadata is correctly embedded and read back.

**Run Tests:**
```bash
./gradlew :demo:connectedAndroidTest
```

**Key Test Cases:**
- **Metadata Embedding**: Verifies that `prompt`, `model`, and `subject` are correctly written to XMP fields (`Iptc4xmpExt`, `dc`, `xmp`).
- **Format Support**: Tests embedding in PNG, JPEG, and MP4 containers.
- **Path Traversal**: Ensures `getUrl()` sanitizes filenames to prevent directory traversal attacks.
- **Audio Handling**: Verifies that saving audio deletes previous audio files but preserves images.

## Building and Publishing

### Build
```bash
./gradlew build
```

### Publish to GitHub Packages
The library is configured to publish to GitHub Packages. You need to provide credentials via `gradle.properties` or environment variables.

**`gradle.properties`:**
```properties
gpr.user=YOUR_GITHUB_USERNAME
gpr.key=YOUR_GITHUB_TOKEN
libraryVersion=1.0.0
```

**Publish Command:**
```bash
./gradlew publish
```

## Non-Obvious Conventions

1. **XMP Namespace Registration**: The Android implementation explicitly registers the `Iptc4xmpExt` namespace in its `init` block to ensure compatibility with the Rust/Swift implementations.
2. **MP4 UUID Box**: MP4 files use a specific UUID (`BE7ACFCB97A942E89C71999491E3AFAC`) for the XMP container. This is a standard used by Adobe for video metadata.
3. **In-Memory State**: `characterCast` and `imageEdit` states are stored in volatile variables in memory. They are **not** persisted to disk. If the app process dies, this state is lost.
4. **Audio Exclusivity**: The library enforces a "single audio file" rule. Saving a new audio file automatically deletes any existing audio files in the storage directory.
5. **Path Sanitization**: `getUrl()` uses `File(file).name` to strip directory paths, preventing path traversal attacks (e.g., `../../../etc/passwd` becomes `passwd`).