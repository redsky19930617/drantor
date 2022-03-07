package org.owntracks.android.support.receiver

import android.app.ForegroundServiceStartNotAllowedException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.AndroidEntryPoint
import org.owntracks.android.services.BackgroundService
import org.owntracks.android.support.Preferences
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class StartBackgroundServiceReceiver : BroadcastReceiver() {

    @Inject
    lateinit var preferences: Preferences

    override fun onReceive(context: Context, intent: Intent) {
        if ((Intent.ACTION_MY_PACKAGE_REPLACED == intent.action ||
                    Intent.ACTION_BOOT_COMPLETED == intent.action)
            && preferences.autostartOnBoot
        ) {
            Timber.v("android.intent.action.BOOT_COMPLETED received")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Timber.v("running startForegroundService")
                val startIntent = Intent(context, BackgroundService::class.java)
                startIntent.action = intent.action
                try {
                    context.startForegroundService(startIntent)
                } catch (e: ForegroundServiceStartNotAllowedException) {
                    Timber.e("Unable to start foreground service, because Android has prevented it. This should not happen if intent action is ${Intent.ACTION_MY_PACKAGE_REPLACED} or ${Intent.ACTION_BOOT_COMPLETED}. intent action was ${intent.action}")
                }
            } else {
                Timber.v("running legacy startService")
                context.startService(Intent(context, BackgroundService::class.java))
            }
        }
    }
}