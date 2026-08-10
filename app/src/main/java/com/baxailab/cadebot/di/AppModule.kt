package com.baxailab.cadebot.di

import android.content.Context
import com.baxailab.cadebot.BuildConfig
import com.baxailab.cadebot.data.mock.MockMenuService
import com.baxailab.cadebot.data.remote.CadebotApiService
import com.baxailab.cadebot.data.remote.PaymentApiService
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
    fun provideMenuService(@ApplicationContext ctx: Context) = MockMenuService(ctx)

    @Provides
    @Singleton
    fun provideCadebotApiService(): CadebotApiService =
        CadebotApiService(BuildConfig.CADEBOT_API_URL)

    @Provides
    @Singleton
    fun providePaymentApiService(): PaymentApiService =
        PaymentApiService(BuildConfig.PAYMENT_API_URL)
}
