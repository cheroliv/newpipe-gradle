package com.mp3organizer

import kotlinx.coroutines.runBlocking
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

fun main(args: Array<String>) = runBlocking {
    logger.info("MP3 Organizer - PostgreSQL + R2DBC + Kotlin Coroutines")
    logger.info("Usage: Use Gradle tasks instead of running directly")
    logger.info("")
    logger.info("Available tasks:")
    logger.info("  ./gradlew scanMp3        - Scan MP3 files and store in DB")
    logger.info("  ./gradlew exportJson     - Export DB to JSON")
    logger.info("  ./gradlew exportYaml     - Export DB to YAML")
    logger.info("  ./gradlew exportXml      - Export DB to XML")
    logger.info("  ./gradlew exportSql      - Export DB to SQL INSERT statements")
    logger.info("  ./gradlew businessQueries - Run business queries")
    logger.info("  ./gradlew organizeFiles  - Organize files by artist")
    logger.info("  ./gradlew fullProcess    - Run all tasks in sequence")
}
