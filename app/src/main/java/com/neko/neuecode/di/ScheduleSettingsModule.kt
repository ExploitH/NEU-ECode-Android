package com.neko.neuecode.di

import com.neko.neuecode.data.local.schedule.PrefsScheduleSettingsStore
import com.neko.neuecode.data.local.schedule.ScheduleSettingsStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ScheduleSettingsModule {
    @Binds
    @Singleton
    abstract fun bindScheduleSettingsStore(store: PrefsScheduleSettingsStore): ScheduleSettingsStore
}
