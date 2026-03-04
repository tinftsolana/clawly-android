package ai.clawly.app.di

import android.content.Context
import ai.clawly.app.data.preferences.SolanaAuthPreferences
import ai.clawly.app.data.remote.solana.SolanaApiService
import ai.clawly.app.data.repository.SolanaAuthRepositoryImpl
import ai.clawly.app.data.repository.SolanaPaymentsRepositoryImpl
import ai.clawly.app.domain.repository.SolanaAuthRepository
import ai.clawly.app.domain.repository.SolanaPaymentsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SolanaModule {

    @Provides
    @Singleton
    fun provideSolanaApiService(): SolanaApiService {
        return SolanaApiService()
    }

    @Provides
    @Singleton
    fun provideSolanaAuthPreferences(
        @ApplicationContext context: Context
    ): SolanaAuthPreferences {
        return SolanaAuthPreferences(context)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SolanaBindingsModule {

    @Binds
    @Singleton
    abstract fun bindSolanaAuthRepository(
        impl: SolanaAuthRepositoryImpl
    ): SolanaAuthRepository

    @Binds
    @Singleton
    abstract fun bindSolanaPaymentsRepository(
        impl: SolanaPaymentsRepositoryImpl
    ): SolanaPaymentsRepository
}
