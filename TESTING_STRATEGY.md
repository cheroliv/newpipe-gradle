# 🧪 Stratégie de Tests - NewPipe Gradle Ecosystem

**Dernière mise à jour** : 2026-04-14

---

## Vue d'Ensemble

La stratégie de tests suit 3 niveaux :

1. **Tests Unitaires** : Mock des dépendances externes (Ollama, YouTube API)
2. **Tests Fonctionnels** : Tests des tâches Gradle avec TestKit
3. **Tests d'Intégration** : Tests avec vrais services (Ollama, YouTube)

---

## Tests Unitaires

### Pattern : WireMock pour Ollama

```kotlin
@ExtendWith(MockKExtension::class)
class LlmClientTest {

    @Test
    fun `returns response from Ollama`() = runTest {
        // Setup : Mock Ollama API
        wiremock.stubFor(
            post("/api/chat")
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody("""{"message": {"content": "Hello"}}""")
                )
        )
        
        // Execute
        val llm = LlmClient(baseUrl = wiremock.baseUrl())
        val response = llm.chat("test")
        
        // Assert
        assertThat(response).isEqualTo("Hello")
    }
}
```

**Dépendances** :
```kotlin
testImplementation("com.github.tomakehurst:wiremock:3.0.1")
testImplementation("io.mockk:mockk:1.13.9")
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
```

---

### Pattern : Mock NewPipeExtractor

```kotlin
class YouTubeDownloaderTest {

    @Test
    fun `extracts video info from URL`() = runTest {
        // Setup : Mock HTTP response
        val mockResponse = """
            {
                "videoDetails": {
                    "title": "Test Video",
                    "author": "Test Channel"
                }
            }
        """.trimIndent()
        
        wiremock.stubFor(
            get(urlPathMatching("/watch.*"))
                .willReturn(aResponse().withBody(mockResponse))
        )
        
        // Execute
        val downloader = YouTubeDownloader()
        val info = downloader.getVideoInfo("https://youtube.com/watch?v=test")
        
        // Assert
        assertThat(info.title).isEqualTo("Test Video")
        assertThat(info.uploaderName).isEqualTo("Test Channel")
    }
}
```

---

## Tests Fonctionnels

### Pattern : Gradle TestKit

```kotlin
@TempDir
lateinit var testProjectDir: File

@Test
fun `buildSessions creates session file`() {
    // Setup : Minimal Gradle project
    File(testProjectDir, "settings.gradle.kts").writeText("")
    File(testProjectDir, "build.gradle.kts").writeText("""
        plugins {
            id("com.cheroliv.newpipe") version "0.0.4"
        }
        
        newpipe {
            configPath = file("musics.yml").absolutePath
        }
    """)
    
    // Execute
    val result = GradleRunner.create()
        .withProjectDir(testProjectDir)
        .withArguments("buildSessions")
        .withPluginClasspath()
        .build()
    
    // Assert
    assertThat(result.task(":buildSessions")).isNotNull
    assertThat(result.output).contains("BUILD SUCCESSFUL")
}
```

**Dépendances** :
```kotlin
testImplementation("org.gradle:gradle-test-kit:8.5")
```

---

### Pattern : Scénarios avec Cucumber

```gherkin
# src/test/scenarios/auth.feature
Feature: OAuth2 Authentication

  Scenario: Successful Device Flow authentication
    Given sessions.yml contains account without refreshToken
    When I run ./gradlew authSessions
    And I complete Device Flow on google.com/device
    Then sessions.yml contains refreshToken
    And token is not displayed in logs

  Scenario: Skip already authenticated account
    Given sessions.yml contains account with refreshToken
    When I run ./gradlew authSessions
    Then account is marked "✓ Refresh token present — skipping"
    And sessions.yml is not modified
```

```kotlin
// CucumberTestRunner.kt
@Cucumber
class CucumberTestRunner
```

---

## Tests d'Intégration

### Pattern : Tag pour Tests Réels

```kotlin
@Tag("real-youtube")
class YouTubeAuthIntegrationTest {

    @Test
    fun `download member-only video with authenticated session`() {
        // Requires: TEST_YOUTUBE_REFRESH_TOKEN env variable
        val refreshToken = System.getenv("TEST_YOUTUBE_REFRESH_TOKEN")
            ?: throw SkipTestException("No refresh token")
        
        // Setup
        val session = Session(
            id = "test-account",
            clientId = System.getenv("TEST_YOUTUBE_CLIENT_ID"),
            clientSecret = System.getenv("TEST_YOUTUBE_CLIENT_SECRET"),
            refreshToken = refreshToken
        )
        
        // Execute
        val manager = SessionManager(listOf(session))
        val downloader = DownloaderImpl(manager)
        val info = downloader.getVideoInfo("https://youtube.com/watch?v=MEMBER_ONLY_ID")
        
        // Assert
        assertThat(info).isNotNull
        assertThat(info.isMemberOnly).isTrue()
    }

    @Test
    fun `refresh expired token automatically`() {
        // Setup : Session with expired accessToken
        val session = Session(
            id = "test-account",
            clientId = "...",
            clientSecret = "...",
            refreshToken = "...",
            accessTokenExpiry = Instant.now().minusSeconds(3600) // Expired
        )
        
        // Execute
        val manager = SessionManager(listOf(session))
        manager.next() // Triggers refresh
        
        // Assert
        assertThat(session.accessToken).isNotBlank()
        assertThat(session.accessTokenExpiry).isAfter(Instant.now())
    }
}
```

---

### Configuration pour Tests d'Intégration

```kotlin
// build.gradle.kts
tasks.test {
    useJUnitPlatform {
        // Exclude integration tests by default
        excludeTags("real-youtube", "real-ollama")
    }
}

// Task spéciale pour exécuter les tests d'intégration
tasks.register<Test>("integrationTest") {
    useJUnitPlatform {
        includeTags("real-youtube", "real-ollama")
    }
    
    // Inject environment variables
    environment("TEST_YOUTUBE_CLIENT_ID", project.findProperty("youtube.clientId"))
    environment("TEST_YOUTUBE_CLIENT_SECRET", project.findProperty("youtube.clientSecret"))
    environment("TEST_YOUTUBE_REFRESH_TOKEN", project.findProperty("youtube.refreshToken"))
}
```

---

### Pattern : Skip Test si Prérequis Manquants

```kotlin
class SkipTestException(message: String) : Exception(message)

@EachTest
fun `test with external dependency`() {
    // Check prerequisites
    if (!ollamaIsRunning()) {
        throw SkipTestException("Ollama is not running")
    }
    
    if (!youtubeCredentialsPresent()) {
        throw SkipTestException("YouTube credentials not configured")
    }
    
    // Test logic...
}

fun ollamaIsRunning(): Boolean {
    return try {
        URL("http://localhost:11434/api/tags").openStream().use { true }
    } catch (e: Exception) {
        false
    }
}
```

---

## Coverage

### Configuration Kover

```kotlin
// build.gradle.kts
plugins {
    id("org.jetbrains.kotlinx.kover") version "0.7.6"
}

kover {
    reports {
        total {
            html {
                onCheck = true
            }
        }
    }
    
    verify {
        rule {
            minBound(80, CoverageUnit.LINE)
        }
    }
}
```

### Exécuter les Tests avec Coverage

```bash
# Tous les tests
./gradlew test

# Tests unitaires seulement
./gradlew test --tests "*Test"

# Tests fonctionnels
./gradlew functionalTest

# Tests d'intégration (nécessite credentials)
./gradlew integrationTest

# Coverage report
./gradlew koverHtmlReport

# Ouvrir le rapport
open build/reports/kover/html/index.html
```

---

## Tests Spécifiques par Composant

### AuthSessionTask

```kotlin
class AuthSessionTaskTest {

    @Test
    fun `skips account with refreshToken`() {
        // Setup
        val sessionsYml = """
            sessions:
              - id: "test"
                clientId: "..."
                refreshToken: "existing-token"
        """.trimIndent()
        
        // Execute
        val task = AuthSessionTask()
        task.sessionsPath = sessionsYml
        
        // Assert : No Device Flow triggered
        // (mock HTTP server should not receive /device/code request)
    }
}
```

### SessionManager

```kotlin
class SessionManagerTest {

    @Test
    fun `distributes requests in round-robin`() {
        // Setup
        val sessions = listOf(
            Session("session-1", ...),
            Session("session-2", ...),
            Session("session-3", ...)
        )
        val manager = SessionManager(sessions)
        
        // Execute
        val s1 = manager.next()
        val s2 = manager.next()
        val s3 = manager.next()
        val s4 = manager.next() // Should wrap around
        
        // Assert
        assertThat(s1.id).isEqualTo("session-1")
        assertThat(s2.id).isEqualTo("session-2")
        assertThat(s3.id).isEqualTo("session-3")
        assertThat(s4.id).isEqualTo("session-1") // Wrap
    }

    @Test
    fun `excludes invalid sessions`() {
        // Setup
        val sessions = listOf(
            Session("session-1", ...),
            Session("session-2", ...)
        )
        val manager = SessionManager(sessions)
        
        // Execute
        manager.markInvalid("session-1")
        val next = manager.next()
        
        // Assert
        assertThat(next.id).isEqualTo("session-2")
    }
}
```

### DownloadMusicTask

```kotlin
class DownloadMusicTaskTest {

    @Test
    fun `downloads single video`() {
        // Setup : Mock YouTubeDownloader
        val mockDownloader = object : VideoInfoProvider {
            override suspend fun getVideoInfo(url: String): VideoMetadata {
                return VideoMetadata(
                    title = "Test Video",
                    uploaderName = "Test Channel",
                    duration = 180,
                    url = "https://youtube.com/watch?v=test"
                )
            }
            
            override suspend fun downloadBestAudio(metadata: VideoMetadata, file: File, progress: (Long, Long, Int) -> Unit) {
                file.writeBytes(byteArrayOf(0x00, 0x01, 0x02)) // Fake audio
            }
        }
        
        // Execute
        val task = DownloadMusicTask()
        // Inject mock (via reflection or constructor)
        task.download()
        
        // Assert : MP3 file created with correct metadata
    }
}
```

---

## Checklist de Validation

### Avant de Commit

- [ ] Tests unitaires passent : `./gradlew test`
- [ ] Coverage >= 80% : `./gradlew koverHtmlReport`
- [ ] Tests fonctionnels passent : `./gradlew functionalTest`
- [ ] Aucun secret dans les tests (vérifier `.gitignore`)

### Avant de Merge

- [ ] Tests d'intégration passent (si credentials disponibles)
- [ ] Performance acceptable (< 5min pour tous les tests)
- [ ] Documentation mise à jour

---

## Voir Aussi

- **Architecture technique** : `doc/ARCHITECTURE.md`
- **Guide d'authentification** : `doc/AUTH_GUIDE.md`
- **EPIC Download Authenticated** : `doc/EPIC_DOWNLOAD_AUTHENTICATED.md`

---

**Dernière mise à jour** : 2026-04-14
