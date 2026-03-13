package com.kitewatch.network.di

import com.kitewatch.domain.repository.KiteConnectRepository
import com.kitewatch.network.kiteconnect.KiteConnectRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkBindingsModule {
    @Binds
    @Singleton
    abstract fun bindKiteConnectRepository(impl: KiteConnectRepositoryImpl): KiteConnectRepository
}
