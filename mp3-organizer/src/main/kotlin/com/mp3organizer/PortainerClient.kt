package com.mp3organizer

import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

private val logger = KotlinLogging.logger {}

/**
 * Portainer API client to manage PostgreSQL container
 */
class PortainerClient(
    private val baseUrl: String = "http://localhost:9000",
    private val bearerToken: String
) {
    private val client = OkHttpClient()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    
    data class Endpoint(
        val Id: Int,
        val Name: String,
        val Type: Int
    )
    
    data class Stack(
        val Id: Int,
        val Name: String,
        val EndpointId: Int
    )
    
    /**
     * Get local Docker endpoint
     */
    suspend fun getLocalEndpoint(): Endpoint? {
        val request = Request.Builder()
            .url("$baseUrl/api/endpoints")
            .header("Authorization", "Bearer $bearerToken")
            .build()
        
        return client.execute(request) { response ->
            val body = response.body?.string() ?: return@execute null
            val endpoints = com.fasterxml.jackson.databind.ObjectMapper()
                .readValue(body, Array<Endpoint>::class.java)
            endpoints.find { it.Type == 1 } // Docker standalone
        }
    }
    
    /**
     * Deploy PostgreSQL stack via Portainer
     */
    suspend fun deployPostgresStack(endpointId: Int, stackName: String = "mp3-organizer-db") {
        val dockerComposeContent = File("docker-compose.yml").readText()
        
        val stackPayload = mapOf(
            "Name" to stackName,
            "EndpointId" to endpointId,
            "SwarmId" to null,
            "StackFileContent" to dockerComposeContent
        )
        
        val json = com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(stackPayload)
        
        val request = Request.Builder()
            .url("$baseUrl/api/stacks/create/standalone")
            .header("Authorization", "Bearer $bearerToken")
            .post(json.toRequestBody(jsonMediaType))
            .build()
        
        client.execute(request) { response ->
            if (response.isSuccessful) {
                logger.info("PostgreSQL stack deployed successfully")
            } else {
                logger.error("Failed to deploy stack: ${response.code} ${response.message}")
            }
        }
    }
    
    /**
     * Start a container
     */
    suspend fun startContainer(endpointId: Int, containerName: String) {
        val request = Request.Builder()
            .url("$baseUrl/api/endpoints/$endpointId/docker/containers/$containerName/start")
            .header("Authorization", "Bearer $bearerToken")
            .post("".toRequestBody(jsonMediaType))
            .build()
        
        client.execute(request) { response ->
            if (response.isSuccessful || response.code == 304) {
                logger.info("Container $containerName started")
            } else {
                logger.warn("Container $containerName start: ${response.code}")
            }
        }
    }
    
    /**
     * Check if container exists and is running
     */
    suspend fun isContainerRunning(endpointId: Int, containerName: String): Boolean {
        val request = Request.Builder()
            .url("$baseUrl/api/endpoints/$endpointId/docker/containers/json?all=true")
            .header("Authorization", "Bearer $bearerToken")
            .build()
        
        return client.execute(request) { response ->
            val body = response.body?.string() ?: return@execute false
            val containers = com.fasterxml.jackson.databind.ObjectMapper()
                .readValue(body, Array<Map<String, Any>>::class.java)
            containers.any { c ->
                val names = (c["Names"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                val status = c["Status"] as? String ?: ""
                names.any { it.contains(containerName, ignoreCase = true) } && 
                status.startsWith("Up", ignoreCase = true)
            }
        }
    }
    
    private suspend fun <T> OkHttpClient.execute(
        request: Request,
        block: suspend (okhttp3.Response) -> T
    ): T {
        return kotlin.coroutines.suspendCoroutine { cont ->
            val call = newCall(request)
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: java.io.IOException) {
                    cont.resumeWith(Result.failure(e))
                }
                
                override fun onResponse(call: Call, response: okhttp3.Response) {
                    cont.resumeWith(runCatching { block(response) })
                }
            })
        }
    }
}

/**
 * Main entry point for Portainer integration
 */
fun main(args: Array<String>) = runBlocking {
    val bearerToken = System.getenv("PORTAINER_BEARER_TOKEN") 
        ?: args.firstOrNull()
        ?: run {
            logger.error("Usage: provide PORTAINER_BEARER_TOKEN env var or pass as argument")
            return@runBlocking
        }
    
    val portainer = PortainerClient(bearerToken = bearerToken)
    
    logger.info("Connecting to Portainer at localhost:9000...")
    
    val endpoint = portainer.getLocalEndpoint()
        ?: run {
            logger.error("No local Docker endpoint found")
            return@runBlocking
        }
    
    logger.info("Found endpoint: ${endpoint.Name} (ID: ${endpoint.Id})")
    
    val containerName = "postgres-mp3-organizer"
    
    if (portainer.isContainerRunning(endpoint.Id, containerName)) {
        logger.info("PostgreSQL container is already running")
    } else {
        logger.info("Deploying PostgreSQL stack...")
        portainer.deployPostgresStack(endpoint.Id)
        
        // Wait for container to start
        kotlinx.coroutines.delay(5000)
        
        if (portainer.isContainerRunning(endpoint.Id, containerName)) {
            logger.info("PostgreSQL container started successfully")
        } else {
            logger.warn("Container may still be starting...")
        }
    }
    
    logger.info("Database ready at localhost:5432")
    logger.info("  Database: mp3db")
    logger.info("  Username: mp3user")
    logger.info("  Password: mp3password")
}
