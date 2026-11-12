package com.neko.neuecode.data.remote.campus

import org.junit.Assert.*
import org.junit.Test

class ProcessWaitCompatibilityTest {
    @Test fun returnsWhenProcessExits() {
        val process = ProcessBuilder("sh", "-c", "exit 0").start()
        try {
            assertTrue(CampusIntranetProbe.waitForProcess(process, 2000))
            assertEquals(0, process.exitValue())
        } finally { process.destroy() }
    }

    @Test fun boundsWaitForLiveProcess() {
        val process = ProcessBuilder("sleep", "5").start()
        try {
            assertFalse(CampusIntranetProbe.waitForProcess(process, 30))
        } finally { process.destroy() }
    }
}
