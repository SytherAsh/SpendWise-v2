package com.spendwise.ui.di

import android.content.Context
import com.spendwise.storage.DeviceSessionStore
import com.spendwise.sync.SyncConfig
import com.spendwise.ui.api.SessionAuthInterceptor
import com.spendwise.ui.api.SessionEvents
import com.spendwise.ui.api.SpendWiseApi
import com.spendwise.ui.api.TokenRefreshAuthenticator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDeviceSessionStore(@ApplicationContext context: Context): DeviceSessionStore =
        DeviceSessionStore(context)

    @Provides
    @Singleton
    fun provideSessionEvents(): SessionEvents = SessionEvents()

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        // Omit nulls so PUT /users/me/preferences keeps the backend's partial-update
        // semantics (absent field = keep current value).
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(session: DeviceSessionStore, sessionEvents: SessionEvents): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(SessionAuthInterceptor(session))
            .authenticator(
                TokenRefreshAuthenticator(session, SyncConfig.DEFAULT_API_BASE_URL) {
                    sessionEvents.notifySessionExpired()
                },
            )
            .build()

    @Provides
    @Singleton
    fun provideSpendWiseApi(client: OkHttpClient, json: Json): SpendWiseApi =
        Retrofit.Builder()
            // Retrofit requires the base URL to end in '/' for relative endpoint paths.
            .baseUrl(SyncConfig.DEFAULT_API_BASE_URL.trimEnd('/') + "/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(SpendWiseApi::class.java)
}
