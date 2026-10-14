package com.guibilar.financeinsights.core.tasks

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** The values of the `due` filter of [TasksApi.tasks]. */
object TaskDue {
    const val TODAY = "today"

    /** Open tasks due before today. */
    const val OVERDUE = "overdue"

    /** The next 7 days after today. */
    const val UPCOMING = "upcoming"

    /** No due date. */
    const val NONE = "none"
}

/**
 * The Tasks API of Hestia.Api, under `<server>/api/tasks`. Make it from the signed-in client: `client.api<TasksApi>()`.
 * A read-only user can call the GET endpoints only.
 */
interface TasksApi {
    /** Adds a task. */
    @POST("tasks")
    suspend fun create(@Body task: NewTask): TaskItem

    /**
     * A page of tasks: by due date (tasks without one last), then the manual order, then the oldest first. [due] is a
     * [TaskDue] value. Null leaves a filter out. At most 100 a page.
     */
    @GET("tasks")
    suspend fun tasks(
        @Query("status") status: TaskStatus? = null,
        @Query("tag") tag: String? = null,
        @Query("due") due: String? = null,
        @Query("q") q: String? = null,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
    ): TaskPage

    /** The counts of open tasks due today, overdue, and upcoming, with the server's today. */
    @GET("tasks/summary")
    suspend fun summary(): TaskCounts

    /** The tasks linked to one thing, for example a document. */
    @GET("tasks/by-link")
    suspend fun byLink(@Query("kind") kind: String, @Query("id") id: String): List<TaskSummary>

    /** One task, with its last 10 completions. */
    @GET("tasks/{id}")
    suspend fun task(@Path("id") id: String): TaskItem

    /** New details (all of them). The status, the completions, and the links stay. */
    @PUT("tasks/{id}")
    suspend fun update(@Path("id") id: String, @Body details: TaskDetails): TaskItem

    /** Deletes the task, its completions, and its links. */
    @DELETE("tasks/{id}")
    suspend fun delete(@Path("id") id: String)

    /** Done: a task without a repeat is done; a repeated task moves to its next due date. 409 when it is done already. */
    @POST("tasks/{id}/done")
    suspend fun done(@Path("id") id: String): TaskItem

    /** Undoes the last done. 409 when there is nothing to undo. */
    @POST("tasks/{id}/undo")
    suspend fun undo(@Path("id") id: String): TaskItem

    /** Links the task to a document or a transaction. */
    @POST("tasks/{id}/links")
    suspend fun addLink(@Path("id") id: String, @Body link: NewTaskLink): TaskLink

    @DELETE("tasks/{id}/links/{linkId}")
    suspend fun removeLink(@Path("id") id: String, @Path("linkId") linkId: String)
}
