package ai.clawly.app.di

import ai.clawly.app.data.preferences.GatewayPreferences
import ai.clawly.app.data.remote.gateway.DeviceIdentityManager
import ai.clawly.app.data.remote.gateway.GatewayService
import ai.clawly.app.data.remote.gateway.GatewayServiceImpl
import ai.clawly.app.domain.manager.SkillsManager
import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object GatewayModule {

    @Provides
    @Singleton
    fun provideGatewayPreferences(
        @ApplicationContext context: Context
    ): GatewayPreferences {
        return GatewayPreferences(context)
    }

    @Provides
    @Singleton
    fun provideDeviceIdentityManager(
        preferences: GatewayPreferences
    ): DeviceIdentityManager {
        return DeviceIdentityManager(preferences)
    }

    @Provides
    @Singleton
    fun provideGatewayService(
        preferences: GatewayPreferences,
        deviceIdentityManager: DeviceIdentityManager
    ): GatewayService {
        return GatewayServiceImpl(preferences, deviceIdentityManager)
    }

    @Provides
    @Singleton
    fun provideSkillsManager(): SkillsManager {
        return SkillsManager()
    }
}
