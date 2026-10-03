package com.guibilar.financeinsights

import android.app.Application
import com.guibilar.financeinsights.data.Session
import com.guibilar.financeinsights.data.SessionStore
import com.guibilar.financeinsights.ui.transactions.PickedFile
import kotlinx.coroutines.flow.MutableStateFlow

class FinanceApp : Application() {
    /** One per process: the screens reach the API through it. */
    lateinit var session: Session
        private set

    /** A statement shared to the app, until the import sheet takes it (after signing in, if need be). */
    val sharedStatement = MutableStateFlow<PickedFile?>(null)

    override fun onCreate() {
        super.onCreate()
        session = Session(SessionStore(this))
    }
}
