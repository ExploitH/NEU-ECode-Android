package com.neko.neuecode.ui.enrollment

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnrollmentPortalSessionDecoderTest {
    @Test
    fun `decodes evaluateJavascript string into process session`() {
        val inner = """
            {"ok":true,"authorization":"test-token","batchId":"batch-1","batchName":"round-3","typeCode":"04","campus":"01","courseTypes":["XGKC","FANKC"]}
        """.trimIndent()
        val evaluated = Gson().toJson(inner)

        val session = requireNotNull(EnrollmentPortalSessionDecoder.decode(evaluated))

        assertEquals("test-token", session.headers.authorization)
        assertEquals("batch-1", session.headers.batchId)
        assertEquals("round-3", session.batchName)
        assertEquals(listOf("XGKC", "FANKC"), session.courseTypes)
        assertTrue(session.javaClass.name.contains("data.remote.enrollment"))
    }

    @Test
    fun `rejects incomplete or malformed extraction`() {
        assertNull(EnrollmentPortalSessionDecoder.decode("null"))
        assertNull(EnrollmentPortalSessionDecoder.decode("\"{\\\"ok\\\":false}\""))
        assertNull(EnrollmentPortalSessionDecoder.decode("not-json"))
    }
}
