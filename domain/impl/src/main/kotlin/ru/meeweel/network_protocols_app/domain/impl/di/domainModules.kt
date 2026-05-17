package ru.meeweel.network_protocols_app.domain.impl.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import ru.meeweel.network_protocols_app.data.config.BackendEndpointServiceImpl
import ru.meeweel.network_protocols_app.data.config.BackendEndpointStore
import ru.meeweel.network_protocols_app.data.network.ExperimentBackendClient
import ru.meeweel.network_protocols_app.data.telemetry.DeviceTelemetryCollector
import ru.meeweel.network_protocols_app.runner.BackendExperimentRunner
import ru.meeweel.network_protocols_app.runner.ExperimentRunner
import ru.meeweel.network_protocols_app.service.BackendEndpointService

val domainDataModule = module {
    single<CoroutineScope> {
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }

    single {
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        }
    }

    single {
        OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                },
            )
            .build()
    }

    single { BackendEndpointStore(androidContext()) }
    single { DeviceTelemetryCollector(androidContext()) }
    single { ExperimentBackendClient(get(), get()) }
    single<BackendEndpointService> { BackendEndpointServiceImpl(get(), get(), get()) }
}

val domainModule = module {
    single<ExperimentRunner> { BackendExperimentRunner(get(), get(), get(), get()) }
}

val domainModules = listOf(
    domainDataModule,
    domainModule,
)
