package com.neko.neuecode.di

import com.neko.neuecode.data.vpn.UnbundledOfficialOpenVpn3Bridge
import com.neko.neuecode.domain.vpn.OfficialOpenVpn3Bridge
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class VpnModule {
    @Binds
    @Singleton
    abstract fun bindOfficialOpenVpn3Bridge(
        impl: UnbundledOfficialOpenVpn3Bridge,
    ): OfficialOpenVpn3Bridge
}
