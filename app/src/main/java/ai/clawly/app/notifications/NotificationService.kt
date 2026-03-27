package ai.clawly.app.notifications

import ai.clawly.app.BuildConfig
import ai.clawly.app.MainActivity
import ai.clawly.app.R
import ai.clawly.app.analytics.AnalyticsTracker
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class NotificationService : FirebaseMessagingService() {

    @Inject
    lateinit var analyticsTracker: AnalyticsTracker

    @Inject
    lateinit var pushTokenManager: PushTokenManager

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New Firebase token received: $token")

        analyticsTracker.track(
            "Push_Token_Refreshed",
            mapOf("token_length" to token.length.toString())
        )

        pushTokenManager.handleNewFirebaseToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val title = message.notification?.title ?: getString(R.string.app_name)
        val body = message.notification?.body ?: return
        val platform = message.data["platform"]

        val shouldShow = when (platform) {
            "web2" -> BuildConfig.IS_WEB2
            "web3" -> BuildConfig.IS_WEB3
            else -> true
        }

        if (!shouldShow) {
            Log.d(TAG, "Skipping push for platform=$platform")
            return
        }

        analyticsTracker.track(
            "Push_Notification_Received",
            mapOf(
                "title" to title,
                "platform" to (platform ?: "all")
            )
        )

        showNotification(title, body)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = getString(R.string.notification_channel_name)
            val description = getString(R.string.notification_channel_description)
            val channel = NotificationChannel(
                CHANNEL_ID,
                channelName,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                this.description = description
                enableLights(true)
                enableVibration(true)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(title: String, message: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_crown)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            with(NotificationManagerCompat.from(this)) {
                notify(System.currentTimeMillis().toInt(), notification)
            }
        } catch (e: SecurityException) {
            analyticsTracker.track(
                "Push_Notification_Permission_Error",
                mapOf("error" to e.message.orEmpty())
            )
        }
    }

    companion object {
        private const val TAG = "NotificationService"
        private const val CHANNEL_ID = "clawly_notifications"
    }
}
