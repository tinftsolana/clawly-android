package ai.clawly.app.di

import ai.clawly.app.data.local.ChatPersistenceService
import ai.clawly.app.data.preferences.GatewayPreferences
import ai.clawly.app.data.remote.ControlPlaneService
import ai.clawly.app.data.remote.gateway.DeviceIdentityManager
import ai.clawly.app.data.remote.gateway.GatewayService
import ai.clawly.app.data.repository.AuthProviderRepositoryImpl
import ai.clawly.app.data.repository.ChatRepositoryImpl
import ai.clawly.app.domain.repository.AuthProviderRepository
import ai.clawly.app.domain.repository.ChatRepository
import ai.clawly.app.domain.repository.WalletRepository
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
        preferences: GatewayPreferences
    ): ControlPlaneService {
        return ControlPlaneService(preferences)
    }

    @Provides
    @Singleton
    fun provideAuthProviderRepository(
        preferences: GatewayPreferences,
        controlPlaneService: ControlPlaneService,
        gatewayService: GatewayService,
        deviceIdentityManager: DeviceIdentityManager,
        walletRepository: WalletRepository
    ): AuthProviderRepository {
        return AuthProviderRepositoryImpl(preferences, controlPlaneService, gatewayService, deviceIdentityManager, walletRepository)
    }
}
