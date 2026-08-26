package com.neko.neuecode.data.remote.enrollment

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EnrollmentPayloadParserTest {
    @Test
    fun `schedule maps live field names and string numbers`() {
        val payload = """
            {
              "code": 200,
              "data": {
                "schoolTermName": "2026-2027 autumn",
                "sectionMap": {
                  "01": {"sectionCode":"01","sectionName":"first","beginTime":"08:00","endTime":"08:45"}
                },
                "scheduleList": [
                  {
                    "JXBID":"CLASS-1","KCH":"CS101","KCM":"Data Structures","SKJS":"Teacher Chen",
                    "SKXQ":"6","KSJC":"1","JSJC":"2","SKZC":"1-16","SKZCMC":"weeks 1-16",
                    "secretVal":"must-not-survive"
                  }
                ]
              }
            }
        """.trimIndent()

        val schedule = EnrollmentPayloadParser.parseSchedule(payload)

        assertEquals("2026-2027 autumn", schedule.termName)
        assertEquals("08:00", schedule.sections.getValue("01").beginTime)
        assertEquals(6, schedule.entries.single().weekday)
        assertEquals(1, schedule.entries.single().startSection)
        assertFalse(Gson().toJson(schedule).contains("must-not-survive"))
    }

    @Test
    fun `catalog recursively maps tcList and preserves explicit zero values`() {
        val payload = """
            {
              "code":"200",
              "data": {
                "total":"3",
                "rows":[{
                  "KCH":"AI100","KCM":"Intro to AI","XF":"2.5","QZXKRS":"12","KRL":"90",
                  "tcList":[
                    {"JXBID":"CLASS-A","SKJS":"Teacher A","QZXKRS":"0","teachingClassType":"XGKC","secretVal":"secret-a"},
                    {"JXBID":"CLASS-B","SKJS":"Teacher B","QZXKRS":"15","classCapacity":"30","teachingClassType":"XGKC"}
                  ]
                }]
              }
            }
        """.trimIndent()

        val page = EnrollmentPayloadParser.parseCatalog(payload, "XGKC", pageNumber = 1, pageSize = 2)

        assertEquals(2, page.courses.size)
        assertEquals(0, page.courses.first { it.clazzId == "CLASS-A" }.selectedCount)
        assertEquals(90, page.courses.first { it.clazzId == "CLASS-A" }.capacity)
        assertEquals(30, page.courses.first { it.clazzId == "CLASS-B" }.capacity)
        assertTrue(page.hasMore)
        assertFalse(Gson().toJson(page).contains("secret-a"))
    }

    @Test
    fun `catalog infers pagination when total is absent`() {
        val fullPage = """
            {"code":200,"data":{"rows":[
              {"JXBID":"A","KCM":"A"},
              {"JXBID":"B","KCM":"B"}
            ]}}
        """.trimIndent()
        val shortPage = """
            {"code":200,"data":{"rows":[{"JXBID":"C","KCM":"C"}]}}
        """.trimIndent()

        val first = EnrollmentPayloadParser.parseCatalog(fullPage, "ALLKC", pageNumber = 1, pageSize = 2)
        val second = EnrollmentPayloadParser.parseCatalog(shortPage, "ALLKC", pageNumber = 2, pageSize = 2)

        assertTrue(first.hasMore)
        assertEquals(3, first.total)
        assertFalse(second.hasMore)
        assertEquals(3, second.total)
    }

    @Test
    fun `selected parser drops secret fields and keeps nullable weight`() {
        val payload = """
            {
              "code":200,
              "data":{"rows":[{
                "KCH":"CS200","KCM":"Computer Networks","SKJS":"Teacher Wang","teachingClassType":"FANKC",
                "tcList":[{"JXBID":"CLASS-C","TRQZ":"25","QZXKRS":"0","KRL":"50","secretVal":"selected-secret"}]
              }]}
            }
        """.trimIndent()

        val selected = EnrollmentPayloadParser.parseSelected(
            payload,
            EnrollmentReadEndpoint.ALL_SELECTED,
            "all selected"
        ).single()

        assertEquals("CLASS-C", selected.teachingClassId)
        assertEquals(25, selected.currentWeight)
        assertEquals(0, selected.selectedCount)
        assertFalse(Gson().toJson(selected).contains("selected-secret"))
        assertNull(
            EnrollmentPayloadParser.parseSelected(
                "{\"code\":200,\"data\":[{\"JXBID\":\"NO-WEIGHT\"}]}",
                EnrollmentReadEndpoint.ALL_SELECTED,
                "all selected"
            ).single().currentWeight
        )
    }

    @Test
    fun `session business errors are not treated as ordinary protocol failures`() {
        assertThrows(EnrollmentSessionExpiredException::class.java) {
            EnrollmentPayloadParser.parseCatalog(
                "{\"code\":403,\"msg\":\"token expired\"}",
                "ALLKC",
                1,
                100
            )
        }
        assertThrows(EnrollmentProtocolException::class.java) {
            EnrollmentPayloadParser.parseCatalog(
                "{\"code\":500,\"msg\":\"system busy\"}",
                "ALLKC",
                1,
                100
            )
        }
    }
}
