package com.kitewatch.feature.onboarding.di

import com.kitewatch.domain.repository.AccountBindingRepository
import com.kitewatch.domain.repository.KiteConnectRepository
import com.kitewatch.domain.repository.TokenStore
import com.kitewatch.domain.usecase.auth.BindAccountUseCase
import com.kitewatch.feature.onboarding.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object OnboardingModule {
    @Singleton
    @Provides
    fun provideBindAccountUseCase(
        kiteConnectRepository: KiteConnectRepository,
        accountBindingRepository: AccountBindingRepository,
        tokenStore: TokenStore,
    ): BindAccountUseCase =
        BindAccountUseCase(
            kiteConnectRepository = kiteConnectRepository,
            accountBindingRepository = accountBindingRepository,
            tokenStore = tokenStore,
        )

    @Provides
    @Named("kite_api_secret")
    fun provideApiSecret(): String = BuildConfig.KITE_API_SECRET
}
