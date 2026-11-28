package com.nexusforge.app.data.templates

import com.nexusforge.app.data.organizer.ProjectAnalyzer
import com.nexusforge.app.data.organizer.ProjectKind

/**
 * A starting point for a new project. [files] receives the project name and returns every file
 * to scaffold (path -> content), so the same template works for any name.
 */
data class ProjectTemplate(
    val id: String,
    val title: String,
    val description: String,
    val kind: ProjectKind,
    val files: (String) -> Map<String, String>
)

object ProjectTemplates {

    val BLANK = ProjectTemplate("blank", "Blank", "An empty project. Start from scratch or let the agent build it.", ProjectKind.BLANK) { emptyMap() }

    val COMPOSE_APP = ProjectTemplate(
        "compose-app", "Compose App", "Android app with Jetpack Compose, Material 3 and a unit test.", ProjectKind.COMPOSE
    ) { name -> composeApp(name) }

    val KOTLIN_LIBRARY = ProjectTemplate(
        "kotlin-library", "Kotlin Library", "Reusable JVM library with Gradle and kotlin.test.", ProjectKind.KOTLIN_LIB
    ) { name -> kotlinLibrary(name) }

    val WEB = ProjectTemplate(
        "web", "Web Page", "HTML, CSS and JavaScript in clean css/ and js/ folders.", ProjectKind.WEB
    ) { name -> webPage(name) }

    val PYTHON = ProjectTemplate(
        "python", "Python App", "Package, entry script and pytest tests.", ProjectKind.PYTHON
    ) { name -> pythonApp(name) }

    val all = listOf(BLANK, COMPOSE_APP, KOTLIN_LIBRARY, WEB, PYTHON)

    fun byId(id: String?): ProjectTemplate? = all.firstOrNull { it.id == id }

    // ------------------------------------------------------------------ naming

    private fun packageFor(name: String): String {
        val id = name.lowercase().filter { it.isLetterOrDigit() }.ifBlank { "app" }
        return "com.example." + if (id.first().isDigit()) "app$id" else id
    }

    private fun classFor(name: String): String {
        val pascal = name.split(Regex("[^A-Za-z0-9]+")).filter { it.isNotBlank() }
            .joinToString("") { it.replaceFirstChar { c -> c.uppercase() } }
        return if (pascal.isBlank()) "App" else if (pascal.first().isDigit()) "App$pascal" else pascal
    }

    private fun fill(template: String, name: String): String {
        val pkg = packageFor(name)
        return template.trimIndent().trimStart('\n')
            .replace("{{NAME}}", name.replace("\"", ""))
            .replace("{{PKG}}", pkg)
            .replace("{{CLASS}}", classFor(name))
            .let { if (it.endsWith("\n")) it else it + "\n" }
    }

    // ------------------------------------------------------------------ Compose app

    private fun composeApp(name: String): Map<String, String> {
        val pkgPath = packageFor(name).replace('.', '/')
        val files = linkedMapOf<String, String>()
        files["settings.gradle.kts"] = fill("""
            pluginManagement {
                repositories {
                    google()
                    mavenCentral()
                    gradlePluginPortal()
                }
            }
            dependencyResolutionManagement {
                repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                repositories {
                    google()
                    mavenCentral()
                }
            }
            rootProject.name = "{{NAME}}"
            include(":app")
        """, name)
        files["build.gradle.kts"] = fill("""
            plugins {
                id("com.android.application") version "8.5.2" apply false
                id("org.jetbrains.kotlin.android") version "1.9.24" apply false
            }
        """, name)
        files["gradle.properties"] = fill("""
            org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
            android.useAndroidX=true
            android.nonTransitiveRClass=true
            kotlin.code.style=official
        """, name)
        files["app/build.gradle.kts"] = fill("""
            plugins {
                id("com.android.application")
                id("org.jetbrains.kotlin.android")
            }

            android {
                namespace = "{{PKG}}"
                compileSdk = 34

                defaultConfig {
                    applicationId = "{{PKG}}"
                    minSdk = 26
                    targetSdk = 34
                    versionCode = 1
                    versionName = "1.0"
                }
                buildTypes {
                    release {
                        isMinifyEnabled = false
                        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
                    }
                }
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
                kotlinOptions { jvmTarget = "17" }
                buildFeatures { compose = true }
                composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
            }

            dependencies {
                implementation(platform("androidx.compose:compose-bom:2024.06.00"))
                implementation("androidx.core:core-ktx:1.13.1")
                implementation("androidx.activity:activity-compose:1.9.1")
                implementation("androidx.compose.ui:ui")
                implementation("androidx.compose.ui:ui-tooling-preview")
                implementation("androidx.compose.material3:material3")
                debugImplementation("androidx.compose.ui:ui-tooling")
                testImplementation("junit:junit:4.13.2")
            }
        """, name)
        files["app/proguard-rules.pro"] = "# Add project specific ProGuard rules here.\n"
        files["app/src/main/AndroidManifest.xml"] = fill("""
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <application
                    android:allowBackup="true"
                    android:label="{{NAME}}"
                    android:supportsRtl="true"
                    android:theme="@style/Theme.App">
                    <activity
                        android:name=".MainActivity"
                        android:exported="true">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                </application>
            </manifest>
        """, name)
        files["app/src/main/res/values/themes.xml"] = fill("""
            <resources>
                <style name="Theme.App" parent="android:Theme.Material.Light.NoActionBar" />
            </resources>
        """, name)
        files["app/src/main/java/$pkgPath/MainActivity.kt"] = fill("""
            package {{PKG}}

            import android.os.Bundle
            import androidx.activity.ComponentActivity
            import androidx.activity.compose.setContent
            import {{PKG}}.ui.HomeScreen
            import {{PKG}}.ui.theme.AppTheme

            class MainActivity : ComponentActivity() {
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    setContent {
                        AppTheme {
                            HomeScreen(title = "{{NAME}}")
                        }
                    }
                }
            }
        """, name)
        files["app/src/main/java/$pkgPath/ui/HomeScreen.kt"] = fill("""
            package {{PKG}}.ui

            import androidx.compose.foundation.layout.Arrangement
            import androidx.compose.foundation.layout.Column
            import androidx.compose.foundation.layout.fillMaxSize
            import androidx.compose.foundation.layout.padding
            import androidx.compose.material3.Button
            import androidx.compose.material3.MaterialTheme
            import androidx.compose.material3.Surface
            import androidx.compose.material3.Text
            import androidx.compose.runtime.Composable
            import androidx.compose.runtime.getValue
            import androidx.compose.runtime.mutableIntStateOf
            import androidx.compose.runtime.remember
            import androidx.compose.runtime.setValue
            import androidx.compose.ui.Alignment
            import androidx.compose.ui.Modifier
            import androidx.compose.ui.unit.dp

            @Composable
            fun HomeScreen(title: String) {
                var taps by remember { mutableIntStateOf(0) }
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(title, style = MaterialTheme.typography.headlineMedium)
                        Text("Tapped " + taps + " times")
                        Button(onClick = { taps++ }) { Text("Tap me") }
                    }
                }
            }
        """, name)
        files["app/src/main/java/$pkgPath/ui/theme/Theme.kt"] = fill("""
            package {{PKG}}.ui.theme

            import androidx.compose.foundation.isSystemInDarkTheme
            import androidx.compose.material3.MaterialTheme
            import androidx.compose.material3.darkColorScheme
            import androidx.compose.material3.lightColorScheme
            import androidx.compose.runtime.Composable

            @Composable
            fun AppTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
                MaterialTheme(
                    colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme(),
                    content = content
                )
            }
        """, name)
        files["app/src/test/java/$pkgPath/ExampleUnitTest.kt"] = fill("""
            package {{PKG}}

            import org.junit.Assert.assertEquals
            import org.junit.Test

            class ExampleUnitTest {
                @Test
                fun addition_isCorrect() {
                    assertEquals(4, 2 + 2)
                }
            }
        """, name)
        files[".gitignore"] = ProjectAnalyzer.gitignoreContent(ProjectKind.COMPOSE)
        files["README.md"] = fill("""
            # {{NAME}}

            An Android app built with Jetpack Compose and Material 3.

            ## Run

            Open the folder in Android Studio and run the `app` configuration, or build with:

            ```
            ./gradlew assembleDebug
            ```

            ## Structure

            - `app/src/main/java/{{PKG}}/` — activity and UI
            - `app/src/main/java/{{PKG}}/ui/` — screens and theme
            - `app/src/test/` — unit tests
        """.replace("{{PKG}}", packageFor(name).replace('.', '/')), name)
        return files
    }

    // ------------------------------------------------------------------ Kotlin library

    private fun kotlinLibrary(name: String): Map<String, String> {
        val pkgPath = packageFor(name).replace('.', '/')
        val files = linkedMapOf<String, String>()
        files["settings.gradle.kts"] = fill("""
            pluginManagement {
                repositories {
                    mavenCentral()
                    gradlePluginPortal()
                }
            }
            rootProject.name = "{{NAME}}"
        """, name)
        files["build.gradle.kts"] = fill("""
            plugins {
                kotlin("jvm") version "1.9.24"
            }

            group = "{{PKG}}"
            version = "0.1.0"

            repositories {
                mavenCentral()
            }

            dependencies {
                testImplementation(kotlin("test"))
            }

            kotlin {
                jvmToolchain(17)
            }

            tasks.test {
                useJUnitPlatform()
            }
        """, name)
        files["gradle.properties"] = "kotlin.code.style=official\n"
        files["src/main/kotlin/$pkgPath/Greeter.kt"] = fill("""
            package {{PKG}}

            /** Builds friendly greetings. Replace this with your library's real API. */
            class Greeter(private val greeting: String = "Hello") {
                fun greet(name: String): String = greeting + ", " + name + "!"
            }
        """, name)
        files["src/test/kotlin/$pkgPath/GreeterTest.kt"] = fill("""
            package {{PKG}}

            import kotlin.test.Test
            import kotlin.test.assertEquals

            class GreeterTest {
                @Test
                fun greetsByName() {
                    assertEquals("Hello, World!", Greeter().greet("World"))
                }
            }
        """, name)
        files[".gitignore"] = ProjectAnalyzer.gitignoreContent(ProjectKind.KOTLIN_LIB)
        files["README.md"] = fill("""
            # {{NAME}}

            A Kotlin library.

            ## Build and test

            ```
            ./gradlew build
            ./gradlew test
            ```

            ## Structure

            - `src/main/kotlin/` — library code
            - `src/test/kotlin/` — tests
        """, name)
        return files
    }

    // ------------------------------------------------------------------ Web

    private fun webPage(name: String): Map<String, String> {
        val files = linkedMapOf<String, String>()
        files["index.html"] = fill("""
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>{{NAME}}</title>
                <link rel="stylesheet" href="css/styles.css">
            </head>
            <body>
                <main class="card">
                    <h1>{{NAME}}</h1>
                    <p id="message">Hello from your new project.</p>
                    <button id="action" type="button">Click me</button>
                </main>
                <script src="js/main.js"></script>
            </body>
            </html>
        """, name)
        files["css/styles.css"] = fill("""
            :root {
                --bg: #0f1115;
                --fg: #f4f4f5;
                --accent: #f97316;
            }

            * { box-sizing: border-box; }

            body {
                margin: 0;
                min-height: 100vh;
                display: grid;
                place-items: center;
                background: var(--bg);
                color: var(--fg);
                font-family: system-ui, sans-serif;
            }

            .card {
                text-align: center;
                padding: 2rem;
            }

            button {
                background: var(--accent);
                color: #111;
                border: 0;
                border-radius: 999px;
                padding: 0.6rem 1.4rem;
                font-size: 1rem;
                cursor: pointer;
            }
        """, name)
        files["js/main.js"] = fill("""
            const message = document.getElementById("message");
            const button = document.getElementById("action");
            let clicks = 0;

            button.addEventListener("click", () => {
                clicks += 1;
                message.textContent = "Clicked " + clicks + " time" + (clicks === 1 ? "" : "s") + ".";
            });
        """, name)
        files[".gitignore"] = ProjectAnalyzer.gitignoreContent(ProjectKind.WEB)
        files["README.md"] = fill("""
            # {{NAME}}

            A small web page.

            Open `index.html` in a browser.

            ## Structure

            - `index.html` — the page
            - `css/` — styles
            - `js/` — scripts
        """, name)
        return files
    }

    // ------------------------------------------------------------------ Python

    private fun pythonApp(name: String): Map<String, String> {
        val pkg = name.lowercase().filter { it.isLetterOrDigit() }.ifBlank { "app" }.let { if (it.first().isDigit()) "app_$it" else it }
        val files = linkedMapOf<String, String>()
        files["main.py"] = fill("""
            from $pkg.core import greet


            def main() -> None:
                print(greet("World"))


            if __name__ == "__main__":
                main()
        """.replace("$" + "pkg", pkg), name)
        files["$pkg/__init__.py"] = ""
        files["$pkg/core.py"] = fill("""
            def greet(name: str) -> str:
                return f"Hello, {name}!"
        """, name)
        files["tests/test_core.py"] = fill("""
            from $pkg.core import greet


            def test_greet():
                assert greet("World") == "Hello, World!"
        """.replace("$" + "pkg", pkg), name)
        files["requirements.txt"] = "pytest\n"
        files[".gitignore"] = ProjectAnalyzer.gitignoreContent(ProjectKind.PYTHON)
        files["README.md"] = fill("""
            # {{NAME}}

            A Python application.

            ```
            pip install -r requirements.txt
            python main.py
            pytest
            ```
        """, name)
        return files
    }
}
