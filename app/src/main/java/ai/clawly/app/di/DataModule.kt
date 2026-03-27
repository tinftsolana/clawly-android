package ai.clawly.app.di

import ai.clawly.app.data.auth.FirebaseAuthService
import ai.clawly.app.data.local.ChatPersistenceService
import ai.clawly.app.data.preferences.GatewayPreferences
import ai.clawly.app.data.preferences.SolanaAuthPreferences
import ai.clawly.app.data.remote.ControlPlaneService
import ai.clawly.app.data.repository.AuthProviderRepositoryImpl
import ai.clawly.app.data.repository.ChatRepositoryImpl
import ai.clawly.app.data.repository.CreditsRepositoryImpl
import ai.clawly.app.domain.repository.AuthProviderRepository
import ai.clawly.app.domain.repository.ChatRepository
import ai.clawly.app.domain.repository.CreditsRepository
import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideChatPersistenceService(
        @ApplicationContext context: Context
    ): ChatPersistenceService {
        return ChatPersistenceService(context)
    }

    @Provides
    @Singleton
    fun provideChatRepository(
        persistenceService: ChatPersistenceService
    ): ChatRepository {
        return ChatRepositoryImpl(persistenceService)
    }

    @Provides
    @Singleton
    fun provideControlPlaneService(
        preferences: GatewayPreferences,
        firebaseAuthService: FirebaseAuthService,
        solanaAuthPreferences: SolanaAuthPreferences
    ): ControlPlaneService {
        return ControlPlaneService(preferences, firebaseAuthService, solanaAuthPreferences)
    }

    @Provides
    @Singleton
    fun provideAuthProviderRepository(
        repositoryImpl: AuthProviderRepositoryImpl
    ): AuthProviderRepository {
        return repositoryImpl
    }

    @Provides
    @Singleton
    fun provideCreditsRepository(
        @ApplicationContext context: Context
    ): CreditsRepository {
        return CreditsRepositoryImpl(context)
    }
}
