package com.guibilar.financeinsights

import android.app.Application
import com.guibilar.financeinsights.data.Session
import com.guibilar.financeinsights.data.SessionStore

class FinanceApp : Application() {
    /** One per process: the screens reach the API through it. */
    lateinit var session: Session
        private set

    override fun onCreate() {
        super.onCreate()
        session = Session(SessionStore(this))
    }
}
