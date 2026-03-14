package com.kitewatch.data.di

import com.kitewatch.domain.repository.AlertRepository
import com.kitewatch.domain.repository.ChargeRateRepository
import com.kitewatch.domain.repository.FundRepository
import com.kitewatch.domain.repository.GttRepository
import com.kitewatch.domain.repository.HoldingRepository
import com.kitewatch.domain.repository.KiteConnectRepository
import com.kitewatch.domain.repository.OrderRepository
import com.kitewatch.domain.repository.TransactionRepository
import com.kitewatch.domain.usecase.SyncOrdersUseCase
import com.kitewatch.domain.usecase.fund.GetFundBalanceUseCase
import com.kitewatch.domain.usecase.portfolio.CalculatePnlUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {
    @Provides
    @Singleton
    fun provideCalculatePnlUseCase(
        orderRepo: OrderRepository,
        chargeRateRepo: ChargeRateRepository,
    ): CalculatePnlUseCase = CalculatePnlUseCase(orderRepo, chargeRateRepo)

    @Provides
    @Singleton
    fun provideGetFundBalanceUseCase(fundRepo: FundRepository): GetFundBalanceUseCase = GetFundBalanceUseCase(fundRepo)

    @Provides
    @Singleton
    @Suppress("LongParameterList")
    fun provideSyncOrdersUseCase(
        kiteConnectRepo: KiteConnectRepository,
        orderRepo: OrderRepository,
        holdingRepo: HoldingRepository,
        transactionRepo: TransactionRepository,
        chargeRateRepo: ChargeRateRepository,
        alertRepo: AlertRepository,
        gttRepo: GttRepository,
    ): SyncOrdersUseCase =
        SyncOrdersUseCase(
            kiteConnectRepo = kiteConnectRepo,
            orderRepo = orderRepo,
            holdingRepo = holdingRepo,
            transactionRepo = transactionRepo,
            chargeRateRepo = chargeRateRepo,
            alertRepo = alertRepo,
            gttRepo = gttRepo,
        )
}
