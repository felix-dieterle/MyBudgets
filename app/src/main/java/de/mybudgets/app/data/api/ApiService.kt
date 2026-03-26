package de.mybudgets.app.data.api

import de.mybudgets.app.data.api.dto.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    @GET("accounts")
    suspend fun getAccounts(): Response<List<AccountDto>>

    @POST("accounts")
    suspend fun createAccount(@Body account: AccountDto): Response<Map<String, Long>>

    @PUT("accounts/{id}")
    suspend fun updateAccount(@Path("id") id: Long, @Body account: AccountDto): Response<Map<String, Boolean>>

    @DELETE("accounts/{id}")
    suspend fun deleteAccount(@Path("id") id: Long): Response<Map<String, Boolean>>

    @GET("transactions")
    suspend fun getTransactions(
        @Query("account_id") accountId: Long? = null,
        @Query("category_id") categoryId: Long? = null,
        @Query("from") from: Long? = null,
        @Query("to") to: Long? = null,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): Response<List<TransactionDto>>

    @POST("transactions")
    suspend fun createTransaction(@Body transaction: TransactionDto): Response<Map<String, Long>>

    @PUT("transactions/{id}")
    suspend fun updateTransaction(@Path("id") id: Long, @Body transaction: TransactionDto): Response<Map<String, Boolean>>

    @DELETE("transactions/{id}")
    suspend fun deleteTransaction(@Path("id") id: Long): Response<Map<String, Boolean>>

    @GET("categories")
    suspend fun getCategories(): Response<List<CategoryDto>>

    @POST("sync")
    suspend fun sync(@Body payload: Map<String, Any>): Response<SyncResponse>

    @GET("version")
    suspend fun getVersion(): Response<Map<String, String>>

    /** AI suggestion via openrouter.ai (proxied through PHP backend) */
    @POST("ai/suggest")
    suspend fun aiSuggest(@Body payload: Map<String, String>): Response<Map<String, String>>
}
