package com.mp3organizer.playlist

import com.mp3organizer.Playlist
import com.mp3organizer.PlaylistTrack
import mu.KotlinLogging
import java.io.File
import javax.xml.stream.XMLOutputFactory

private val logger = KotlinLogging.logger {}

/**
 * Générateur de playlist au format XSPF (XML Shareable Playlist Format)
 * Compatible VLC, Winamp, et autres lecteurs
 */
object XspfGenerator {
    
    /**
     * Génère un fichier XSPF depuis une playlist
     */
    fun generate(playlist: Playlist, outputFile: File): File {
        logger.info("Génération playlist XSPF: ${outputFile.name}")
        
        outputFile.parentFile.mkdirs()
        
        val factory = XMLOutputFactory.newInstance()
        val writer = factory.createXMLStreamWriter(outputFile.writer(Charsets.UTF_8))
        
        try {
            writer.apply {
                writeStartDocument("UTF-8", "1.0")
                writeStartElement("playlist")
                writeAttribute("version", "1")
                writeAttribute("xmlns", "http://xspf.org/ns/0/")
                
                // Métadonnées de la playlist
                writeOptionalElement("title", "Playlist IA: ${playlist.description}")
                writeOptionalElement("info", "Généré automatiquement par mp3-organizer")
                
                writeStartElement("trackList")
                
                playlist.tracks.forEachIndexed { index, track ->
                    writeTrackElement(track, index + 1)
                }
                
                writeEndElement() // trackList
                writeEndElement() // playlist
                writeEndDocument()
                flush()
            }
            
            logger.info("Playlist générée avec succès: ${playlist.tracks.size} tracks")
            return outputFile
            
        } catch (e: Exception) {
            logger.error("Erreur génération XSPF: ${e.message}", e)
            throw e
        } finally {
            writer.close()
        }
    }
    
    private fun XMLStreamWriter.writeOptionalElement(name: String, value: String?) {
        if (value != null) {
            writeStartElement(name)
            writeCharacters(value)
            writeEndElement()
        }
    }
    
    private fun XMLStreamWriter.writeTrackElement(track: PlaylistTrack, trackNum: Int) {
        writeStartElement("track")
        
        // Emplacement du fichier (URI absolute)
        writeOptionalElement("location", "file://${track.path}")
        
        // Métadonnées
        writeOptionalElement("title", track.title)
        writeOptionalElement("creator", track.artist)
        writeOptionalElement("album", track.album)
        writeOptionalElement("tracknum", trackNum.toString())
        
        // Duration en millisecondes si disponible
        track.duration?.let { durationSeconds ->
            writeOptionalElement("duration", (durationSeconds * 1000).toString())
        }
        
        writeEndElement() // track
    }
    
    private fun XMLStreamWriter.writeElement(name: String, value: String) {
        writeStartElement(name)
        writeCharacters(value)
        writeEndElement()
    }
}
