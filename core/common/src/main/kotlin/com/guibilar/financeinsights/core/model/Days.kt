package com.guibilar.financeinsights.core.model

import java.time.LocalDate

/** The calendar day of an API date ("2026-09-15T00:00:00Z", "2026-09-15"): the API stores days at midnight UTC. */
fun isoDay(date: String): LocalDate = LocalDate.parse(date.take(10))
