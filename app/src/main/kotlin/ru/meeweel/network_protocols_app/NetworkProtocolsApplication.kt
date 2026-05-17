package ru.meeweel.network_protocols_app

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import ru.meeweel.network_protocols_app.di.networkProtocolsModule
import ru.meeweel.network_protocols_app.domain.impl.di.domainModules

class NetworkProtocolsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@NetworkProtocolsApplication)
            modules(domainModules + networkProtocolsModule)
        }
    }
}
