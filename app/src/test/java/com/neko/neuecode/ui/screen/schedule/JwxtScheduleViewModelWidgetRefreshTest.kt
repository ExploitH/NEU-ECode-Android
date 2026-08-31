package com.neko.neuecode.ui.screen.schedule

import com.neko.neuecode.data.local.schedule.JwxtScheduleCacheStore
import com.neko.neuecode.data.local.schedule.ScheduleSettings
import com.neko.neuecode.data.local.schedule.ScheduleSettingsStore
import com.neko.neuecode.data.remote.campus.CampusIntranetProbe
import com.neko.neuecode.data.repository.JwxtScheduleRepository
import com.neko.neuecode.domain.jwxt.JwxtScheduleDocument
import com.neko.neuecode.domain.model.Result
import com.neko.neuecode.widget.ScheduleWidgetRefresher
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Test

class JwxtScheduleViewModelWidgetRefreshTest {

    @Test
    fun saveSettings_refreshesScheduleWidgetsImmediately() {
        val repository = mockk<JwxtScheduleRepository>(relaxed = true)
        val cacheStore = mockk<JwxtScheduleCacheStore>()
        val settingsStore = mockk<ScheduleSettingsStore>(relaxed = true)
        val intranetProbe = mockk<CampusIntranetProbe>(relaxed = true)
        val widgetRefresher = mockk<ScheduleWidgetRefresher>(relaxed = true)
        every { cacheStore.load() } returns null
        every { settingsStore.load() } returns ScheduleSettings()
        val viewModel = JwxtScheduleViewModel(
            repository = repository,
            cacheStore = cacheStore,
            settingsStore = settingsStore,
            intranetProbe = intranetProbe,
            widgetRefresher = widgetRefresher,
        )

        viewModel.saveSettings(
            defaultTermCode = "2026-2027-1",
            termStartEpochDay = 20_700L,
        )

        verify(exactly = 1) {
            settingsStore.save(
                ScheduleSettings(
                    defaultTermCode = "2026-2027-1",
                    termStartEpochDay = 20_700L,
                ),
            )
        }
        verify(exactly = 1) { widgetRefresher.refresh() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun successfulScheduleSync_refreshesWidgetsAfterCacheWrite() {
        val testDispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        try {
            val repository = mockk<JwxtScheduleRepository>(relaxed = true)
            val cacheStore = mockk<JwxtScheduleCacheStore>()
            val settingsStore = mockk<ScheduleSettingsStore>(relaxed = true)
            val intranetProbe = mockk<CampusIntranetProbe>()
            val widgetRefresher = mockk<ScheduleWidgetRefresher>(relaxed = true)
            val document = mockk<JwxtScheduleDocument>(relaxed = true)
            every { cacheStore.load() } returns null
            every { cacheStore.save(document) } just Runs
            every { settingsStore.load() } returns ScheduleSettings()
            every { intranetProbe.probe() } returns CampusIntranetProbe.Result(
                host = CampusIntranetProbe.PING_HOST,
                reachable = true,
                shouldAbortScheduleSync = false,
                detail = "ok",
            )
            coEvery { repository.loadMySchedule(any(), any(), any()) } returns Result.Success(document)
            coEvery { repository.listRecentTerms(any()) } returns Result.Error(
                exception = IllegalStateException("not needed"),
                message = "not needed",
            )
            val viewModel = JwxtScheduleViewModel(
                repository = repository,
                cacheStore = cacheStore,
                settingsStore = settingsStore,
                intranetProbe = intranetProbe,
                widgetRefresher = widgetRefresher,
            )

            viewModel.refresh()

            verify(timeout = 5_000, exactly = 1) { cacheStore.save(document) }
            verify(timeout = 5_000, exactly = 1) { widgetRefresher.refresh() }
        } finally {
            Dispatchers.resetMain()
        }
    }
}
