package com.guibilar.financeinsights.core.today

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * The Today API of Hestia.Api: the cards of all modules in one answer. Make it from the signed-in client:
 * `client.api<TodayApi>()`.
 */
interface TodayApi {
    /**
     * The cards for today. With [refresh] true, the server makes a new answer and does not use its short cache
     * (30 seconds). Null leaves the parameter out.
     */
    @GET("today")
    suspend fun today(@Query("refresh") refresh: Boolean? = null): TodayDto
}
