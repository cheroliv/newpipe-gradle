package com.cheroliv.newpipe

import com.cheroliv.newpipe.FuncTestsConstants.CONFIG_FILE
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

object FuncTestsConstants {
    const val CONFIG_FILE = "musics.yml"
}