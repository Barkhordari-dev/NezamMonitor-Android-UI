package com.nezammonitor.app.api

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonArray
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

data class ApiLoginResponse(val message: String? = null, val token: String? = null)
data class ApiUserResponse(val user: ApiUserInfo? = null)
data class ApiUserInfo(
    val id: Int = 0,
    val ozv_num: String? = null,
    val name: String? = null,
    val family: String? = null,
    val user_shahrestan: Int = 0
)

class NezamApi {
    companion object {
        const val BASE_URL = "http://service.yazdnezam.ir:8033"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30L, TimeUnit.SECONDS)
        .readTimeout(60L, TimeUnit.SECONDS)
        .writeTimeout(60L, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private var token: String? = null
    var userId: Int = 0
        private set
    var cityId: Int = 0
        private set
    var userName: String = ""
        private set

    fun isLoggedIn(): Boolean = token != null

    suspend fun login(username: String, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val body = gson.toJson(mapOf(
                "ozv_num" to username,
                "ozv_pass" to password,
                "ozv_type" to 0
            ))
            val request = Request.Builder()
                .url("$BASE_URL/panel/api/login")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val json = gson.fromJson(response.body?.string(), ApiLoginResponse::class.java)

            if (json.token != null) {
                token = json.token
                val userResult = getUserInfo()
                if (userResult.isSuccess) Result.success(Unit)
                else Result.failure(Exception("خطا در دریافت اطلاعات کاربر"))
            } else {
                Result.failure(Exception("نام کاربری یا رمز عبور اشتباه است"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("خطا در اتصال: ${e.message}"))
        }
    }

    private suspend fun getUserInfo(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/panel/api/user")
                .header("Authorization", token ?: "")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val json = gson.fromJson(response.body?.string(), ApiUserResponse::class.java)

            if (json.user != null) {
                userId = json.user.id
                cityId = json.user.user_shahrestan
                userName = "${json.user.name ?: ""} ${json.user.family ?: ""}".trim()
                Result.success(Unit)
            } else {
                Result.failure(Exception("کاربر یافت نشد"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCases(): Result<List<JsonObject>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/panel/api/showParvandeNezaratMeybod/$userId/$cityId")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: "[]"
            val array = gson.fromJson(body, JsonArray::class.java)
            val cases = mutableListOf<JsonObject>()
            for (i in 0 until array.size()) {
                cases.add(array[i].asJsonObject)
            }
            Result.success(cases)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getEngineers(dbId: Int): Result<List<JsonObject>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/panel/api/showNazer/$dbId/$cityId")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: "{}"
            val json = gson.fromJson(body, JsonObject::class.java)

            val engineers = mutableListOf<JsonObject>()
            if (json.has(dbId.toString())) {
                val arr = json.getAsJsonArray(dbId.toString())
                for (i in 0 until arr.size()) {
                    engineers.add(arr[i].asJsonObject)
                }
            } else if (json.has("data")) {
                val arr = json.getAsJsonArray("data")
                for (i in 0 until arr.size()) {
                    engineers.add(arr[i].asJsonObject)
                }
            }
            Result.success(engineers)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getFees(dbId: Int): Result<List<JsonObject>> = withContext(Dispatchers.IO) {
        try {
            val body = gson.toJson(mapOf(
                "db_id" to dbId.toString(),
                "sha_id" to cityId.toString(),
                "ozv_id" to userId.toString()
            ))
            val request = Request.Builder()
                .url("$BASE_URL/panel/api/showMali")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val arr = gson.fromJson(response.body?.string(), JsonArray::class.java)
            val fees = mutableListOf<JsonObject>()
            for (i in 0 until arr.size()) {
                fees.add(arr[i].asJsonObject)
            }
            Result.success(fees)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getReports(dbId: Int): Result<List<JsonObject>> = withContext(Dispatchers.IO) {
        try {
            val body = gson.toJson(mapOf(
                "db_id" to dbId.toString(),
                "sha_id" to cityId.toString()
            ))
            val request = Request.Builder()
                .url("$BASE_URL/panel/api/getGozareshat")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val arr = gson.fromJson(response.body?.string(), JsonArray::class.java)
            val reports = mutableListOf<JsonObject>()
            for (i in 0 until arr.size()) {
                reports.add(arr[i].asJsonObject)
            }
            Result.success(reports)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadFile(imageName: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/panel/Panel/public/img/gozaresh/$imageName")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val bytes = response.body?.bytes() ?: byteArrayOf()
                Result.success(bytes)
            } else {
                Result.failure(Exception("HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
