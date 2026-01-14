package com.example.aqi

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface AqiApiService {
    @GET("feed/geo:{lat};{lon}/")
    suspend fun getHyperlocalAqi(
        @Path("lat") lat: Double,
        @Path("lon") lon: Double,
        @Query("token") token: String
    ): AqiResponse

    @GET("feed/@{uid}/")
    suspend fun getAqiByStationId(
        @Path("uid") uid: String,
        @Query("token") token: String
    ): AqiResponse

    @GET("feed/{city}/")
    suspend fun getAqiByCityName(
        @Path("city") city: String,
        @Query("token") token: String
    ): AqiResponse

    companion object {
        private const val BASE_URL = "https://api.waqi.info/"

        fun create(): AqiApiService {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(AqiApiService::class.java)
        }
    }
}
