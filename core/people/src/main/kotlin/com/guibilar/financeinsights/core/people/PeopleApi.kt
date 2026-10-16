package com.guibilar.financeinsights.core.people

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** The values of the `status` filter of [PeopleApi.loans]. */
object LoanStatus {
    const val OPEN = "open"
    const val RETURNED = "returned"
    const val ALL = "all"
}

/**
 * The People API of Hestia.Api, under `<server>/api/people`. Make it from the signed-in client:
 * `client.api<PeopleApi>()`. A read-only user can call the GET endpoints only.
 */
interface PeopleApi {
    /** Reach out, the birthdays in the next 30 days, and the loan counts. */
    @GET("people/summary")
    suspend fun summary(): PeopleSummary

    /** The groups in use, by name, with their counts. */
    @GET("people/groups")
    suspend fun groups(): List<GroupCount>

    /** A page of people: by name, or by the reach-out day (`sort = "contact"`). At most 100 a page. */
    @GET("people")
    suspend fun people(
        @Query("group") group: String? = null,
        @Query("q") q: String? = null,
        @Query("sort") sort: String? = null,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
    ): PersonPage

    @GET("people/{id}")
    suspend fun person(@Path("id") id: String): Person

    @POST("people")
    suspend fun create(@Body person: PersonDetails): Person

    /** New details (all of them). The records and the links stay. */
    @PUT("people/{id}")
    suspend fun update(@Path("id") id: String, @Body details: PersonDetails): Person

    /** Deletes the person with the contacts, the gift ideas, the loans, and the links. */
    @DELETE("people/{id}")
    suspend fun delete(@Path("id") id: String)

    @POST("people/{id}/interactions")
    suspend fun logInteraction(@Path("id") personId: String, @Body interaction: InteractionDetails): Interaction

    @DELETE("people/interactions/{id}")
    suspend fun deleteInteraction(@Path("id") id: String)

    @POST("people/{id}/gifts")
    suspend fun addGift(@Path("id") personId: String, @Body gift: GiftDetails): GiftIdea

    /** All values; set `givenOn` when the gift was given. */
    @PUT("people/gifts/{id}")
    suspend fun updateGift(@Path("id") id: String, @Body gift: GiftDetails): GiftIdea

    @DELETE("people/gifts/{id}")
    suspend fun deleteGift(@Path("id") id: String)

    /** The loans of all people; [status] is a [LoanStatus] value. */
    @GET("people/loans")
    suspend fun loans(@Query("status") status: String = LoanStatus.OPEN): List<Loan>

    @POST("people/{id}/loans")
    suspend fun addLoan(@Path("id") personId: String, @Body loan: LoanDetails): Loan

    /** All values; set `returnedOn` when the thing came back. */
    @PUT("people/loans/{id}")
    suspend fun updateLoan(@Path("id") id: String, @Body loan: LoanDetails): Loan

    @DELETE("people/loans/{id}")
    suspend fun deleteLoan(@Path("id") id: String)

    /** 409 for a link that exists; 502 when finance cannot check a transaction. */
    @POST("people/{id}/links")
    suspend fun addLink(@Path("id") id: String, @Body link: NewPersonLink): PersonLink

    @DELETE("people/{id}/links/{linkId}")
    suspend fun removeLink(@Path("id") id: String, @Path("linkId") linkId: String)

    /** The people linked to one thing, for example a home item. */
    @GET("people/by-link")
    suspend fun byLink(@Query("kind") kind: String, @Query("id") id: String): List<PersonSummary>

    /** Imports contacts (T105); with dryRun only the preview. A contact that matches a person only fills its empty details. */
    @POST("people/import")
    suspend fun importPeople(@Body request: PeopleImportRequest): PeopleImportResult
}
