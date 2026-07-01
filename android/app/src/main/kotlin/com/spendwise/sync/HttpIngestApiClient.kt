package com.spendwise.sync

import com.spendwise.storage.QueuedTransactionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Real `IngestApiClient` implementation — plain `HttpURLConnection`, no HTTP library dependency. */
class HttpIngestApiClient(private val baseUrl: String) : IngestApiClient {

    override suspend fun postBatch(
        transactions: List<QueuedTransactionEntity>,
        userJwt: String,
        deviceApiKey: String,
    ): List<IngestItemResult> = withContext(Dispatchers.IO) {
        val connection = (URL("$baseUrl/ingest/transactions").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $userJwt")
            setRequestProperty("X-Device-Key", deviceApiKey)
        }
        try {
            connection.outputStream.use { it.write(IngestJson.encodeBatch(transactions).toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299 || status == 409) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            IngestJson.decodeResults(body, fallbackStatus = status, transactionIds = transactions.map { it.transactionId })
        } finally {
            connection.disconnect()
        }
    }
}
