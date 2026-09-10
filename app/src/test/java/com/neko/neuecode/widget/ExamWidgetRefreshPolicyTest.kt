package com.neko.neuecode.widget

import com.neko.neuecode.ui.navigation.MainDestinations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ExamWidgetRefreshPolicyTest {

    @Test
    fun examWidget_neverFetchesRemoteOrSchedulesPeriod() {
        assertFalse(ExamWidgetRefreshPolicy.mayFetchRemote)
        assertFalse(ExamWidgetRefreshPolicy.pageAutoSyncsOnOpen)
        assertEquals(0, ExamWidgetRefreshPolicy.updatePeriodMillis)
    }

    @Test
    fun examWidgetLaunch_doesNotTriggerExamOrScheduleSync() {
        assertFalse(ExamWidgetRefreshPolicy.widgetLaunchTriggersExamSync)
        assertFalse(MainDestinations.shouldTriggerExamSync(MainDestinations.widgetExamRoute))
        assertFalse(MainDestinations.shouldTriggerExamSync("academic/exams"))
        assertFalse(MainDestinations.shouldTriggerExamSync(null))
        assertFalse(MainDestinations.shouldRefreshScheduleWidgets(MainDestinations.widgetExamRoute))
        assertFalse(MainDestinations.shouldRefreshScheduleWidgets("academic/exams"))
    }
}
