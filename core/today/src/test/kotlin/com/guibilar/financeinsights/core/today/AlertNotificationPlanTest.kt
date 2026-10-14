package com.guibilar.financeinsights.core.today

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertNotificationPlanTest {
    private val owner = "https://hestia.example.ts.net|dev"

    private fun alert(alertId: String, module: String = "Money") =
        AlertDto(id = "$module.$alertId", alertId = alertId, module = module, title = "Alert $alertId")

    private val all: (String) -> Boolean = { true }

    @Test
    fun `the first poll keeps the ids and notifies nothing`() {
        val plan = planAlertNotifications(listOf(alert("a"), alert("b")), before = null, owner = owner, notifies = all)

        assertTrue(plan.notify.isEmpty())
        assertTrue(plan.summary.isEmpty())
        assertEquals(SeenAlerts(owner, setOf("a", "b")), plan.seen)
    }

    @Test
    fun `a poll for another account is a first poll`() {
        val before = SeenAlerts("https://hestia.example.ts.net|other", setOf("a"))

        val plan = planAlertNotifications(listOf(alert("a"), alert("b")), before, owner, all)

        assertTrue(plan.notify.isEmpty())
        assertEquals(setOf("a", "b"), plan.seen.alertIds)
        assertEquals(owner, plan.seen.owner)
    }

    @Test
    fun `a new id is notified, a seen id is not`() {
        val plan = planAlertNotifications(listOf(alert("a"), alert("b")), SeenAlerts(owner, setOf("a")), owner, all)

        assertEquals(listOf("b"), plan.notify.map { it.alertId })
        assertEquals(setOf("a", "b"), plan.seen.alertIds)
    }

    @Test
    fun `a condition that comes back has a new id, so it is notified again`() {
        // "money.budget-over.b1" was alert "a"; it ended, and came back as alert "c".
        val back = alert("c").copy(id = "money.budget-over.b1")

        val plan = planAlertNotifications(listOf(back), SeenAlerts(owner, setOf("a")), owner, all)

        assertEquals(listOf("c"), plan.notify.map { it.alertId })
    }

    @Test
    fun `a resolved id removes its notification and leaves the seen set`() {
        val shown = listOf(ShownAlert("a", "Money"), ShownAlert("b", "Money"))

        val plan = planAlertNotifications(listOf(alert("b")), SeenAlerts(owner, setOf("a", "b")), owner, all, shown)

        assertEquals(setOf("a"), plan.remove)
        assertEquals(setOf("b"), plan.seen.alertIds)
        assertTrue(plan.notify.isEmpty())
    }

    @Test
    fun `more than three new alerts give one summary`() {
        val alerts = (1..4).map { alert("n$it") }

        val plan = planAlertNotifications(alerts, SeenAlerts(owner, emptySet()), owner, all)

        assertTrue(plan.notify.isEmpty())
        assertEquals(listOf("n1", "n2", "n3", "n4"), plan.summary.map { it.alertId })
    }

    @Test
    fun `three new alerts get one notification each`() {
        val alerts = (1..3).map { alert("n$it") }

        val plan = planAlertNotifications(alerts, SeenAlerts(owner, emptySet()), owner, all)

        assertEquals(3, plan.notify.size)
        assertTrue(plan.summary.isEmpty())
    }

    @Test
    fun `a module that is off is not notified, but its alerts count as seen`() {
        val alerts = listOf(alert("m"), alert("d", module = "Documents"))
        val onlyMoney: (String) -> Boolean = { it == "Money" }

        val plan = planAlertNotifications(alerts, SeenAlerts(owner, emptySet()), owner, onlyMoney)

        assertEquals(listOf("m"), plan.notify.map { it.alertId })
        assertEquals(setOf("m", "d"), plan.seen.alertIds)
    }

    @Test
    fun `a module turned off removes its shown notifications`() {
        val alerts = listOf(alert("m"), alert("d", module = "Documents"))
        val shown = listOf(ShownAlert("m", "Money"), ShownAlert("d", "Documents"))

        val plan = planAlertNotifications(alerts, SeenAlerts(owner, setOf("m", "d")), owner, { it == "Money" }, shown)

        assertEquals(setOf("d"), plan.remove)
    }

    @Test
    fun `the summary goes away only when none of its alerts is open`() {
        val before = SeenAlerts(owner, setOf("a", "b", "c", "d"))

        val oneLeft = planAlertNotifications(listOf(alert("c")), before, owner, all, shownSummary = setOf("a", "b", "c", "d"))
        val noneLeft = planAlertNotifications(listOf(alert("x")), before, owner, all, shownSummary = setOf("a", "b", "c", "d"))

        assertFalse(oneLeft.removeSummary)
        assertTrue(noneLeft.removeSummary)
    }

    @Test
    fun `an alert with no alert id is left out`() {
        val old = alert("").copy(id = "money.unsorted")

        val plan = planAlertNotifications(listOf(old), SeenAlerts(owner, emptySet()), owner, all)

        assertTrue(plan.notify.isEmpty())
        assertTrue(plan.seen.alertIds.isEmpty())
    }
}
