package com.neko.neuecode.data.remote.campus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CampusIntranetProbeTest {

    @Test
    fun probe_abortsWhenPingExitIsNonZero() {
        val probe = CampusIntranetProbe(
            ping = { CampusIntranetProbe.PingOutcome(exitCode = 1, timedOut = false) },
            tcpConnect = { _, _, _ -> error("tcp should not run after ping fail") },
        )

        val result = probe.probe()

        assertFalse(result.reachable)
        assertTrue(result.shouldAbortScheduleSync)
        assertEquals("ipgw.neu.edu.cn", result.host)
    }

    @Test
    fun probe_abortsWhenPingTimesOut() {
        val probe = CampusIntranetProbe(
            ping = { CampusIntranetProbe.PingOutcome(exitCode = -1, timedOut = true) },
            tcpConnect = { _, _, _ -> error("tcp should not run after ping timeout") },
        )

        val result = probe.probe()

        assertTrue(result.shouldAbortScheduleSync)
        assertTrue(result.detail.contains("超时") || result.detail.contains("ping"))
    }

    @Test
    fun probe_abortsWhenJwxtTcpFailsEvenIfPingSucceeds() {
        val probe = CampusIntranetProbe(
            ping = { CampusIntranetProbe.PingOutcome(exitCode = 0, timedOut = false) },
            tcpConnect = { host, port, _ ->
                assertEquals("jwxt.neu.edu.cn", host)
                assertEquals(443, port)
                false
            },
        )

        val result = probe.probe()

        assertFalse(result.reachable)
        assertTrue(result.shouldAbortScheduleSync)
        assertTrue(result.detail.contains("jwxt"))
    }

    @Test
    fun probe_allowsSyncWhenPingAndJwxtTcpSucceed() {
        val probe = CampusIntranetProbe(
            ping = { CampusIntranetProbe.PingOutcome(exitCode = 0, timedOut = false) },
            tcpConnect = { _, _, _ -> true },
        )

        val result = probe.probe()

        assertTrue(result.reachable)
        assertFalse(result.shouldAbortScheduleSync)
    }
}
