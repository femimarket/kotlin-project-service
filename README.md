# Kotlin Project Service

A Kotlin Multiplatform library for embedding AI-generated media metadata (prompt, model, subject) into images and videos using XMP standards. It provides a unified API for Android, iOS, Linux, and Web (JS/Wasm) platforms, mirroring the behavior of the original Swift implementation.

## Overview

`ProjectService` acts as a local file storage boundary. It handles:
- **Metadata Embedding**: Injects XMP metadata into PNG, JPEG, and MP4 files.
- **Metadata Extraction**: Reads embedded metadata back from files.
- **File Management**: Stores files in app-private storage (Android) or OPFS (Web).
- **State Management**: Manages in-memory state for operations like "Character Cast" and "Image Edit".

### Supported Formats & Metadata Mapping

The library embeds metadata into the native container formats using the Adobe XMP standard:

| Format | Container Mechanism | Metadata Fields |
| :--- | :--- | :--- |
| **PNG** | `iTXt` chunk (`XML:com.adobe.xmp`) | `dc:description`, `Iptc4xmpExt:AIPromptInformation` |
| **JPEG** | `APP1` segment (`http://ns.adobe.com/xap/1.0/`) | `xmp:CreatorTool`, `Iptc4xmpExt:AISystemUsed` |
| **MP4** | Top-level `uuid` box (`BE7ACFCB...`) | `dc:subject`, `xmp:Rating` |

**Field Mapping Details:**
- **Prompt**: Stored in `Iptc4xmpExt:AIPromptInformation` (primary) and `dc:description[x-default]` (fallback).
- **Model**: Stored in `Iptc4xmpExt:AISystemUsed` (primary) and `xmp:CreatorTool` (fallback).
- **Subject**: Stored in `dc:subject` (Bag).
- **Like/Rating**: Stored in `xmp:Rating` (5 = Liked, 0 = Not Liked).

## Installation

### Gradle Setup

Add the library to your `build.gradle.kts` (or `build.gradle`) dependencies. Ensure you have the Maven repository configured if publishing locally, or use the GitHub Packages repository.

```kotlin
dependencies {
    implementation("io.github.femimarket:project-service:<version>")
}
```

**Note for Android:**
The library depends on `com.adobe.xmpcore`. Ensure your project allows this dependency.

**Note for Web:**
The Web target (`js` and `wasmJs`) depends on an external npm package `project-service`. Ensure your build environment can resolve npm dependencies.

## Usage

### Android

1. **Initialize**: Call `ProjectService.init()` with your Application Context before using any other methods.
2. **Save Files**: Use `saveFile()` to write bytes with optional metadata.
3. **Read Metadata**: Use `getPrompt()`, `getModel()`, `getSubject()`, etc.

```kotlin
import market.femi.ProjectService

// In Application.onCreate()
ProjectService.init(applicationContext)

// Save an image with metadata
val imageBytes = ... // your byte array
ProjectService.saveFile(
    data = imageBytes,
    named = "generated.png",
    prompt = "A futuristic city",
    model = "dalle-3",
    subject = listOf("city", "future")
)

// Read metadata back
val prompt = ProjectService.getPrompt("generated.png") // "A futuristic city"
```

### iOS

The iOS implementation mirrors the Android API. Initialize with the documents directory context.

### Web (JS/Wasm)

The Web API is asynchronous and returns Promises. It uses the Origin Private File System (OPFS).

```kotlin
import market.femi.ProjectService

// Initialize
ProjectService.ready().await()

// Save
ProjectService.saveFile(
    data = uint8Array,
    name = "image.png",
    prompt = "A cat"
).await()

// Read
val prompt = ProjectService.getPrompt("image.png").await()
```

## Architecture

### Project Structure

- **`library/`**: The core multiplatform library.
  - `src/commonMain/`: Shared interfaces (if any) and common logic.
  - `src/androidMain/`: Android-specific implementation using Adobe XMP Core.
  - `src/iosMain/`: iOS-specific implementation (using Swift/Rust bindings).
  - `src/webMain/`: Web-specific implementation binding to the `project-service` npm package.
  - `src/linuxX64/`: Linux-specific implementation.
- **`demo/`**: An Android application that consumes the library.
  - `src/main/kotlin/.../MainActivity.kt`: Minimal UI demonstrating initialization.
  - `src/androidTest/kotlin/.../ProjectServiceInstrumentedTest.kt`: Comprehensive instrumented tests.

### Key Files

- **`library/src/androidMain/kotlin/market/femi/ProjectService.kt`**: The main entry point for Android. Handles file I/O, path traversal protection, and delegates metadata operations to `XmpMetadata`.
- **`library/src/androidMain/kotlin/market/femi/XmpMetadata.kt`**: Low-level XMP packet manipulation. Handles extraction and injection of XMP data into PNG, JPEG, and MP4 containers.
- **`library/src/webMain/kotlin/market/femi/ProjectService.kt`**: Kotlin/JS/Wasm external binding to the `project-service` npm module.

### Non-Obvious Conventions

1. **Path Traversal Protection**: `ProjectService.getUrl()` strips directory traversal attempts (e.g., `../../../etc/passwd`) to ensure files are always saved within the app's private documents directory.
2. **Atomic Writes**: Files are written to a temporary file first, then renamed/copied to the final destination to prevent corruption.
3. **Audio Handling**: `saveAudio()` deletes any existing audio files in storage before saving the new one. It identifies audio files by extension (mp3, m4a, wav, etc.).
4. **In-Memory State**: `setCharacterCast()` and `setImageEdit()` store state in memory only. This state is lost if the process restarts.
5. **Metadata Fallbacks**: When reading metadata, the library checks specific XMP fields first (e.g., `Iptc4xmpExt:AIPromptInformation`) and falls back to standard fields (e.g., `dc:description`) if the primary field is absent.

## Building and Testing

### Build

```bash
./gradlew :library:assemble
```

### Run Demo

```bash
./gradlew :demo:installDebug
./gradlew :demo:connectedAndroidTest
```

### Run Unit Tests

```bash
./gradlew :library:jvmTest
./gradlew :library:iosSimulatorArm64Test
```

### Publish

To publish to GitHub Packages:

```bash
./gradlew publish -Pgpr.user=YOUR_USERNAME -Pgpr.key=YOUR_TOKEN
```

Or set `gpr.user` and `gpr.key` in `gradle.properties`.