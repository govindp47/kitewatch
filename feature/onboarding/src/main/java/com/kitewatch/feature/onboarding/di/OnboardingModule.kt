package com.kitewatch.feature.onboarding.di

import com.kitewatch.domain.repository.AccountBindingRepository
import com.kitewatch.domain.repository.KiteConnectRepository
import com.kitewatch.domain.repository.TokenStore
import com.kitewatch.domain.usecase.auth.BindAccountUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.scopes.ActivityScoped

@Module
@InstallIn(ActivityComponent::class)
object OnboardingModule {
    @ActivityScoped
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
}
