package com.cheroliv.newpipe

import com.cheroliv.newpipe.FuncTestsConstants.CLIENT_SECRETS_DIR
import com.cheroliv.newpipe.FuncTestsConstants.CONFIG_FILE
import com.cheroliv.newpipe.FuncTestsConstants.FAKE_CLIENT_ID
import com.cheroliv.newpipe.FuncTestsConstants.FAKE_CLIENT_SECRET
import java.io.File
import kotlin.text.Charsets.UTF_8

fun File.createConfigFile() {
    val content = """
        artistes:
          - name: "Amine La Colombe"
            tunes:
              - "https://www.youtube.com/watch?v=FzzEaLVEr-k"
          - name: "Cheb Omar"
            playlists:
              - "https://www.youtube.com/watch?v=SOV6F_AXzRI&list=RDEMkdRT82xA368-J0oQtZ063A&start_radio=1"
    """.trimIndent()
    val configFile = resolve(CONFIG_FILE)
    if (configFile.exists()) configFile.delete()
    else configFile.createNewFile()
    configFile.writeText(content, UTF_8)
}

/**
 * Creates client_secrets/ with a fake Google OAuth2 JSON file of type "installed".
 * Simulates a file downloaded from Google Cloud Console.
 */
fun File.createClientSecretsDir() {
    val dir = resolve(CLIENT_SECRETS_DIR).also { it.mkdirs() }
    dir.resolve("client_secret_test-account.apps.googleusercontent.com.json")
        .writeText(
            """
            {
              "installed": {
                "client_id": "$FAKE_CLIENT_ID",
                "project_id": "test-project",
                "auth_uri": "https://accounts.google.com/o/oauth2/auth",
                "token_uri": "https://oauth2.googleapis.com/token",
                "auth_provider_x509_cert_url": "https://www.googleapis.com/oauth2/v1/certs",
                "client_secret": "$FAKE_CLIENT_SECRET"
              }
            }
            """.trimIndent(),
            UTF_8
        )
}

/**
 * Resolves the real sessions.yml at newpipe-gradle/ root.
 * Working directory during tests is newpipe-gradle/newpipe/.
 */
fun realSessionsFile(): File =
    File(System.getProperty("user.dir")) // newpipe-gradle/newpipe/
        .parentFile                       // newpipe-gradle/
        .resolve("sessions.yml")

/**
 * Resolves the real musics.yml at newpipe-gradle/ root.
 */
fun realMusicsFile(): File =
    File(System.getProperty("user.dir"))
        .parentFile
        .resolve("musics.yml")

object FuncTestsConstants {
    const val CONFIG_FILE        = "musics.yml"
    const val CLIENT_SECRETS_DIR = "client_secrets"
    const val FAKE_CLIENT_ID     = "123456789-faketest.apps.googleusercontent.com"
    const val FAKE_CLIENT_SECRET = "GOCSPX-fakeSecretForTests"
}