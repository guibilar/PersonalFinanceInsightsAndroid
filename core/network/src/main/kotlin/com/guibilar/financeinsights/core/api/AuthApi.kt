package com.guibilar.financeinsights.core.api

import com.guibilar.financeinsights.core.model.ChangePasswordInput
import com.guibilar.financeinsights.core.model.CurrentUser
import com.guibilar.financeinsights.core.model.LoginInput
import com.guibilar.financeinsights.core.model.SignedOutSessions
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * Signing in and the account (the `Auth` paths under `<server>/api/`), the same for every module. [login] sets the
 * session cookie, and every other call of every module's API needs it.
 */
interface AuthApi {
    @POST("Auth/login")
    suspend fun login(@Body login: LoginInput): CurrentUser

    /** Ends this session; the server deletes the cookie. */
    @POST("Auth/logout")
    suspend fun logout()

    @GET("Auth/me")
    suspend fun me(): CurrentUser

    /** Every other session ends; this one goes on. A wrong current password is a 400 on currentPassword. */
    @POST("Auth/change-password")
    suspend fun changePassword(@Body change: ChangePasswordInput)

    /** Ends every other session of this account (other phones and browsers); this one stays. */
    @POST("Auth/sign-out-others")
    suspend fun signOutOthers(): SignedOutSessions
}
