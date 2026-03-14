package com.kitewatch.data.di

import com.kitewatch.data.repository.AlertRepositoryImpl
import com.kitewatch.data.repository.ChargeRateRepositoryImpl
import com.kitewatch.data.repository.FundRepositoryImpl
import com.kitewatch.data.repository.GttRepositoryImpl
import com.kitewatch.data.repository.HoldingRepositoryImpl
import com.kitewatch.data.repository.KiteConnectRepositoryImpl
import com.kitewatch.data.repository.OrderRepositoryImpl
import com.kitewatch.data.repository.SyncEventRepositoryImpl
import com.kitewatch.data.repository.TransactionRepositoryImpl
import com.kitewatch.domain.repository.AlertRepository
import com.kitewatch.domain.repository.ChargeRateRepository
import com.kitewatch.domain.repository.FundRepository
import com.kitewatch.domain.repository.GttRepository
import com.kitewatch.domain.repository.HoldingRepository
import com.kitewatch.domain.repository.KiteConnectRepository
import com.kitewatch.domain.repository.OrderRepository
import com.kitewatch.domain.repository.SyncEventRepository
import com.kitewatch.domain.repository.TransactionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindOrderRepository(impl: OrderRepositoryImpl): OrderRepository

    @Binds
    @Singleton
    abstract fun bindHoldingRepository(impl: HoldingRepositoryImpl): HoldingRepository

    @Binds
    @Singleton
    abstract fun bindTransactionRepository(impl: TransactionRepositoryImpl): TransactionRepository

    @Binds
    @Singleton
    abstract fun bindGttRepository(impl: GttRepositoryImpl): GttRepository

    @Binds
    @Singleton
    abstract fun bindFundRepository(impl: FundRepositoryImpl): FundRepository

    @Binds
    @Singleton
    abstract fun bindChargeRateRepository(impl: ChargeRateRepositoryImpl): ChargeRateRepository

    @Binds
    @Singleton
    abstract fun bindAlertRepository(impl: AlertRepositoryImpl): AlertRepository

    @Binds
    @Singleton
    abstract fun bindSyncEventRepository(impl: SyncEventRepositoryImpl): SyncEventRepository

    @Binds
    @Singleton
    abstract fun bindKiteConnectRepository(impl: KiteConnectRepositoryImpl): KiteConnectRepository
}
