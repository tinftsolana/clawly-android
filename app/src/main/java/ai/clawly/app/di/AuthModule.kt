package ai.clawly.app.di

import ai.clawly.app.data.auth.FirebaseAuthService
import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    @Provides
    @Singleton
    fun provideFirebaseAuthService(
        @ApplicationContext context: Context
    ): FirebaseAuthService {
        return FirebaseAuthService(context)
    }
}
