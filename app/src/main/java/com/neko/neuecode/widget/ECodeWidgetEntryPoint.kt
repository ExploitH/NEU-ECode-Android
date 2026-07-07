package com.neko.neuecode.widget

import com.neko.neuecode.data.repository.ECodeQrRepository
import com.neko.neuecode.data.repository.PersonalRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ECodeWidgetEntryPoint {
    fun personalRepository(): PersonalRepository
    fun eCodeQrRepository(): ECodeQrRepository
}
