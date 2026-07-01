import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    id("maven-publish")
}

group = "io.github.femimarket"
// Use -PlibraryVersion=… (or a gradle.properties entry) to override; defaults
// to "dev" so publishing works without any gradle.properties change.
version = providers.gradleProperty("libraryVersion").getOrElse("dev")

kotlin {
    // Shipping Android + Web only. JVM/iOS/Linux omitted until implemented.
    androidLibrary {
        namespace = "market.femi"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        withJava() // enable java compilation support
        withHostTestBuilder {}.configure {}
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }
    // Web targets (browser). Both the JS/IR and Kotlin/Wasm backends share a
    // single `webMain`/`webTest` source set holding the JsInterop binding.
    js(IR) {
        browser()
        nodejs()
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        nodejs()
    }

    sourceSets {
        webMain.dependencies {
            implementation(libs.kotlinx.browser)
            implementation(npm("project-service", "github:femimarket/js-project-service"))
        }
        commonMain.dependencies {
            //put your multiplatform dependencies here
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }

        androidMain.dependencies {
            // Adobe XMP Core (Java) — same toolkit lineage as the Swift/Rust
            // xmp-toolkit binding; used to build/parse XMP packets.
            implementation(libs.adobe.xmpcore)
        }
    }
}


publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/femimarket/kotlin-project-service")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull
                    ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.key").orNull
                    ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
