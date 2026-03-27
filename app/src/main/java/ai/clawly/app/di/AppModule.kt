package ai.clawly.app.di

import ai.clawly.app.analytics.AmplitudeAnalyticsService
import ai.clawly.app.analytics.AnalyticsTracker
import ai.clawly.app.data.preferences.GatewayPreferences
import ai.clawly.app.data.service.PurchaseService
import ai.clawly.app.data.service.TTSService
import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideTTSService(
        @ApplicationContext context: Context,
        preferences: GatewayPreferences
    ): TTSService {
        return TTSService(context, preferences)
    }

    @Provides
    @Singleton
    fun provideAnalyticsService(
        @ApplicationContext context: Context
    ): AmplitudeAnalyticsService {
        return AmplitudeAnalyticsService(context)
    }

    @Provides
    @Singleton
    fun provideAnalyticsTracker(
        service: AmplitudeAnalyticsService
    ): AnalyticsTracker = service

    @Provides
    @Singleton
    fun providePurchaseService(): PurchaseService {
        return PurchaseService()
    }
}
