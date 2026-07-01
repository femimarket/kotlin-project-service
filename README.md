# ProjectService

A Kotlin Multiplatform library for embedding AI generation metadata (prompts, models, subjects) into media files (PNG, JPEG, MP4) using XMP metadata standards. It provides a unified API for Android, iOS, Linux, and Web platforms, mirroring the behavior of a Swift reference implementation.

## Overview

`ProjectService` acts as a local file storage boundary. It allows applications to save binary data (images, videos, audio) to a private storage directory while embedding structured metadata directly into the file containers. This ensures that AI-generated content carries its provenance (prompt, model used, subject tags) with it, even when shared outside the app.

### Key Features
- **Cross-Platform**: Supports Android, iOS (arm64/simulator), Linux, JavaScript, and WebAssembly.
- **XMP Metadata Embedding**: Embeds metadata into PNG (`iTXt`), JPEG (`APP1`), and MP4 (`uuid` box) containers.
- **Standard Fields**: Maps logical fields to standard XMP namespaces:
  - `prompt` → `dc:description` and `Iptc4xmpExt:AIPromptInformation`
  - `model` → `xmp:CreatorTool` and `Iptc4xmpExt:AISystemUsed`
  - `subject` → `dc:subject` (Bag)
  - `like` → `xmp:Rating`
- **Audio Handling**: Manages a single "current" audio file in storage, replacing previous audio files automatically.
- **In-Memory State**: Stores temporary operation arguments (character casts, image edit targets) in memory.
- **Web Support**: Uses `project-service` npm package for browser environments, leveraging the Origin Private File System (OPFS).

## Architecture

The project consists of two main modules:

1.  **`:library`**: The core multiplatform library.
    -   `src/androidMain/`: Android-specific implementation using Adobe XMP Core.
    -   `src/webMain/`: JavaScript/Wasm binding to the `project-service` npm package.
    -   `src/commonMain/`: Shared interfaces (if any, currently minimal as Android/Web are the primary implementations shown).
2.  **`:demo`**: An Android application that consumes the library to demonstrate usage and run instrumented tests.

### Metadata Muxing Strategy
The library handles binary container formats by extracting existing XMP packets, modifying them, and re-injecting them.
-   **PNG**: Looks for `iTXt` chunks with keyword `XML:com.adobe.xmp`.
-   **JPEG**: Looks for `APP1` segments starting with `http://ns.adobe.com/xap/1.0/ `.
-   **MP4**: Looks for top-level `uuid` boxes with UUID `BE7ACFCB97A942E89C71999491E3AFAC`.

## Installation

### Gradle Setup

Add the Maven repository to your `settings.gradle.kts` or `build.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/femimarket/ProjectService")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull
                    ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.key").orNull
                    ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
```

Add the dependency to your module's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.github.femimarket:project-service:1.0.0") // Use appropriate version
}
```

For Android, ensure you have the Adobe XMP Core dependency (usually pulled transitively or added explicitly if needed):
```kotlin
implementation(libs.adobe.xmpcore)
```

## Usage

### Android

1.  **Initialize**: Call `ProjectService.init()` with your application context. This should be done once, typically in `Application.onCreate()`.
2.  **Save Files**: Use `saveFile()` to write data with optional metadata.
3.  **Retrieve Data**: Use getter methods like `getPrompt()`, `getModel()`, `getSubject()`, and `getLike()`.

```kotlin
import market.femi.ProjectService

// Initialization
ProjectService.init(applicationContext)

// Save an image with metadata
val imageData = ... // ByteArray
ProjectService.saveFile(
    data = imageData,
    named = "generated_image.png",
    prompt = "A futuristic city",
    model = "dalle-3",
    subject = listOf("city", "future", "neon")
)

// Retrieve metadata
val prompt = ProjectService.getPrompt("generated_image.png") // "A futuristic city"
val isLiked = ProjectService.getLike("generated_image.png")  // false

// Like an image
ProjectService.like("generated_image.png", liked = true)
```

### Web (JavaScript/Wasm)

The web implementation relies on the `project-service` npm package. Ensure it is installed in your web project.

```javascript
import { ProjectService } from 'project-service';

// Wait for readiness
await ProjectService.ready();

// Save file
await ProjectService.saveFile(
    new Uint8Array(imageData),
    "generated.png",
    { prompt: "A futuristic city", model: "dalle-3" }
);

// Get prompt
const prompt = await ProjectService.getPrompt("generated.png");
```

## API Reference

### `ProjectService` Object

#### Initialization
-   `init(context: Context)`: Binds the application context. Idempotent.

#### File Operations
-   `saveFile(data: ByteArray, named: String, prompt: String? = null, model: String? = null, subject: List<String>? = null)`: Saves binary data to the documents directory, embedding XMP metadata if provided.
-   `getAllGenerations(): List<File>`: Lists all files in the app's private storage directory.
-   `getUrl(file: String): File`: Resolves a filename to a `File` object in the documents directory. Strips path traversal attempts.
-   `saveAudio(data: ByteArray, named: String)`: Saves audio data. Deletes any existing audio files in storage before saving.
-   `getAudio(): File?`: Returns the single audio file in storage, or null.

#### Metadata Retrieval
-   `getPrompt(file: String): String?`: Reads prompt from `Iptc4xmpExt:AIPromptInformation` or `dc:description`.
-   `getModel(file: String): String?`: Reads model from `Iptc4xmpExt:AISystemUsed` or `xmp:CreatorTool`.
-   `getSubject(file: String): List<String>?`: Reads subject keywords from `dc:subject`.
-   `getLike(file: String): Boolean`: Returns true if `xmp:Rating` is between 1 and 5.
-   `like(file: String, liked: Boolean)`: Sets `xmp:Rating` to 5 (liked) or 0 (not liked).

#### In-Memory State (Process-Lifetime)
-   `setCharacterCast(a: String, b: String)`: Stores a pair of filenames for character-cast operations.
-   `getCharacterCast(): Pair<String, String>?`: Retrieves the stored character-cast pair.
-   `clearCharacterCast()`: Clears the stored character-cast pair.
-   `setImageEdit(file: String)`: Stores a filename for image-edit operations.
-   `getImageEdit(): String?`: Retrieves the stored image-edit filename.
-   `clearImageEdit()`: Clears the stored image-edit filename.

## Building and Testing

### Prerequisites
-   JDK 11 or higher
-   Android SDK (API 21+ for minSdk, API 34+ for compileSdk)
-   Gradle 8+

### Build Commands

```bash
# Build the library
./gradlew :library:assemble

# Build the demo app
./gradlew :demo:assembleDebug
```

### Running Tests

#### Unit Tests (JVM)
Run common and JVM-specific unit tests:
```bash
./gradlew :library:jvmTest
```

#### Android Instrumented Tests
Run tests on a connected Android device or emulator:
```bash
./gradlew :demo:connectedAndroidTest
```
The instrumented tests in `demo/src/androidTest/kotlin/market/femi/demo/ProjectServiceInstrumentedTest.kt` verify the library's behavior exactly as a downstream consumer would experience it, including XMP embedding and retrieval.

## Project Structure

```
.
├── build.gradle.kts          # Root build configuration
├── settings.gradle.kts       # Project settings and plugin management
├── library/                  # Multiplatform library module
│   ├── build.gradle.kts      # Library build config (MPP, publishing)
│   └── src/
│       ├── androidMain/
│       │   └── kotlin/market/femi/
│       │       ├── ProjectService.kt  # Android implementation
│       │       └── XmpMetadata.kt     # XMP embedding logic
│       └── webMain/
│           └── kotlin/market/femi/
│               └── ProjectService.kt  # JS/Wasm binding
└── demo/                     # Android demo app
    ├── build.gradle.kts      # Demo app config
    └── src/
        ├── androidTest/
        │   └── kotlin/market/femi/demo/
        │       └── ProjectServiceInstrumentedTest.kt # Instrumented tests
        └── main/
            └── kotlin/market/femi/demo/
                └── MainActivity.kt # Minimal host activity
```

## Non-Obvious Conventions

1.  **Storage Location**: On Android, files are stored in `Context.getFilesDir()`, which is the app's private internal storage directory. This is the analog of iOS's `Documents/` directory.
2.  **Path Traversal Protection**: `getUrl()` automatically strips directory traversal sequences (e.g., `../../../etc/passwd`) to prevent writing files outside the documents directory.
3.  **Atomic Writes**: Files are written to a temporary file first, then renamed to the target name to ensure atomicity and prevent corruption.
4.  **Audio Exclusivity**: The library assumes only one audio file exists at a time. Saving a new audio file deletes all existing audio files.
5.  **XMP Namespace Registration**: The `Iptc4xmpExt` namespace is registered dynamically in the `XmpMetadata` init block to ensure compatibility with the Adobe XMP Core library.
6.  **Web Dependencies**: The web implementation depends on an external npm package (`project-service`). Ensure this package is available in your web project's dependencies.