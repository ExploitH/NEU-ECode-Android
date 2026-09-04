package com.neko.neuecode.widget

import com.neko.neuecode.data.local.datastore.UserPreferences
import com.neko.neuecode.data.repository.ECodePayCodeRepository
import com.neko.neuecode.data.repository.PersonalRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ECodeWidgetEntryPoint {
    fun personalRepository(): PersonalRepository
    fun eCodePayCodeRepository(): ECodePayCodeRepository
    fun userPreferences(): UserPreferences
}
