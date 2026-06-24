package com.example.data

import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.spec.AlgorithmParameterSpec
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PingTargetResult(
    val host: String,
    val success: Boolean,
    val latencyMs: Long
)

data class NetworkPingResult(
    val isConnected: Boolean,
    val targets: List<PingTargetResult>
)

object UrlDecryptor {
    private const val KEY_HEX = "000102030405060708090a0b0c0d0e0f"
    private const val IV_HEX  = "101112131415161718191a1b1c1d1e1f"

    fun decrypt(encryptedText: String): String? {
        return try {
            val keyBytes = hexStringToByteArray(KEY_HEX)
            val ivBytes = hexStringToByteArray(IV_HEX)
            
            val keySpec = SecretKeySpec(keyBytes, "AES")
            val ivSpec: AlgorithmParameterSpec = IvParameterSpec(ivBytes)
            
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            
            val decodedBytes = Base64.decode(encryptedText.trim(), Base64.DEFAULT)
            val decryptedBytes = cipher.doFinal(decodedBytes)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun hexStringToByteArray(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}

class BypassEngine {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    fun replaceMac(url: String, newMac: String): String {
        val regex = Regex("(?<=mac=)[^&]+")
        return if (regex.containsMatchIn(url)) {
            regex.replace(url, newMac)
        } else {
            url
        }
    }

    suspend fun getSessionId(sessionUrl: String, macAddress: String): String? = withContext(Dispatchers.IO) {
        val decryptedUrl = if (!sessionUrl.startsWith("http")) {
            UrlDecryptor.decrypt(sessionUrl)
        } else {
            sessionUrl
        } ?: return@withContext null

        val finalUrl = replaceMac(decryptedUrl, macAddress)
        
        val request = Request.Builder()
            .url(finalUrl)
            .header("accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
            .header("accept-language", "en-US,en;q=0.9")
            .header("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0")
            .header("cookie", "sensorsdata2015jssdkcross=%7B%22distinct_id%22%3A%2219e0ddbd9f2152-0df941f2efc6b08-4c657b58-1327104-19e0ddbd9f3a60%22%2C%22first_id%22%3A%22%22%2C%22props%22%3A%7B%22%24latest_traffic_source_type%22%3A%22%E8%87%AA%E7%84%B6%E6%90%9C%E7%B4%A2%E6%B5%81%E9%87%8F%22%2C%22%24latest_search_keyword%22%3A%22%E6%9C%AA%E5%8F%96%E5%88%B0%E5%80%BC%22%2C%22%24latest_referrer%22%3A%22https%3A%2F%2Fgemini.google.com%2F%22%7D%2C%22identities%22%3A%22eyIkaWRlbnRpdHlfY29va2llX2lkIjoiMTllMGRkYmQ5ZjIxNTItMGRmOTQxZjJlZmM2YjA4LTRjNjU3YjU4LTEzMjcxMDQtMTllMGRkYmQ5ZjNhNjAifQ%3D%3D%22%2C%22history_login_id%22%3A%7B%22name%22%3A%22%22%2C%22value%22%3A%22%22%7D%2C%22%24device_id%22%3A%2219e0ddbd9f2152-0df941f2efc6b08-4c657b58-1327104-19e0ddbd9f3a60%22%7D")
            .build()
            
        try {
            client.newCall(request).execute().use { response ->
                val finalResUrl = response.request.url.toString()
                
                val regex = Regex("[?&]sessionId=([a-zA-Z0-9]+)")
                val match = regex.find(finalResUrl)
                if (match != null) {
                    match.groupValues[1]
                } else {
                    val bodyString = response.body?.string() ?: ""
                    val bodyMatch = regex.find(bodyString)
                    bodyMatch?.groupValues[1]
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun loginVoucher(sessionId: String, voucher: String): Pair<String?, String?> = withContext(Dispatchers.IO) {
        val postUrl = "https://portal-as.ruijienetworks.com/api/auth/voucher/?lang=en_US"
        val jsonMediaType = "application/json; charset=utf-8".toMediaType()
        val jsonPayload = """
            {
                "accessCode": "$voucher",
                "sessionId": "$sessionId",
                "apiVersion": 1
            }
        """.trimIndent()
        
        val body = jsonPayload.toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(postUrl)
            .post(body)
            .header("authority", "portal-as.ruijienetworks.com")
            .header("accept", "*/*")
            .header("content-type", "application/json")
            .header("origin", "https://portal-as.ruijienetworks.com")
            .header("referer", "https://portal-as.ruijienetworks.com/download/static/maccauth/src/index.html?RES=./../expand/res/mrlev58jlgslg49ervu&IS_EG=0&sessionId=$sessionId")
            .header("user-agent", "Mozilla/5.0 (Linux; Android 12; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Mobile Safari/537.36")
            .build()
            
        try {
            client.newCall(request).execute().use { response ->
                val resText = response.body?.string() ?: ""
                
                if (resText.lowercase().contains("error") || resText.lowercase().contains("invalid")) {
                    Pair(null, resText)
                } else {
                    val regex = Regex("token=(.*?)&")
                    val match = regex.find(resText)
                    if (match != null) {
                        Pair(match.groupValues[1], null)
                    } else {
                        Pair(null, resText)
                    }
                }
            }
        } catch (e: Exception) {
            Pair(null, "Connection error: ${e.message}")
        }
    }

    suspend fun queryGateway(gatewayIp: String, token: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val finalReqUrl = "http://$gatewayIp:2060/wifidog/auth?token=$token&phoneNumber=RshoKaUser"
        
        val request = Request.Builder()
            .url(finalReqUrl)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
            .header("Connection", "keep-alive")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Mobile Safari/537.36")
            .build()
            
        try {
            client.newCall(request).execute().use { response ->
                val responseUrl = response.request.url.toString()
                val successConditions = listOf(
                    "http://www.baidu.com",
                    "http://www.baidu.com/",
                    "http://portal-as.ruijienetworks.com/download/static/maccauth/src/success.html?",
                    "success"
                )
                
                val isSuccess = successConditions.any { cond -> responseUrl.contains(cond) }
                Pair(isSuccess, responseUrl)
            }
        } catch (e: Exception) {
            Pair(false, e.message ?: "Unknown Gateway Error")
        }
    }

    suspend fun getSmartPing(): NetworkPingResult = withContext(Dispatchers.IO) {
        val targets = listOf("google.com", "cloudflare.com", "8.8.8.8")
        val results = mutableListOf<PingTargetResult>()
        var isConnected = false
        
        val shortClient = OkHttpClient.Builder()
            .connectTimeout(2500, TimeUnit.MILLISECONDS)
            .readTimeout(2500, TimeUnit.MILLISECONDS)
            .build()

        for (target in targets) {
            val url = "https://$target"
            val startTime = System.currentTimeMillis()
            var success = false
            var duration = 0L

            try {
                val request = Request.Builder()
                    .url(url)
                    .head()
                    .build()
                shortClient.newCall(request).execute().use { response ->
                    success = response.isSuccessful || response.code in 200..499
                    duration = System.currentTimeMillis() - startTime
                    if (success) {
                        isConnected = true
                    }
                }
            } catch (e: Exception) {
                // Try direct TCP socket helper to bypass strict HTTP limitations
                try {
                    val socket = java.net.Socket()
                    val address = java.net.InetSocketAddress(target, 443)
                    socket.connect(address, 1500)
                    duration = System.currentTimeMillis() - startTime
                    success = true
                    isConnected = true
                    socket.close()
                } catch (ex: Exception) {
                    success = false
                }
            }
            results.add(PingTargetResult(target, success, duration))
        }
        
        NetworkPingResult(isConnected, results)
    }
}
