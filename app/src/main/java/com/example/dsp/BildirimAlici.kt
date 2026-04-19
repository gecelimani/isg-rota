package com.example.dsp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

class BildirimAlici : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel("dsp_kanal", "ISG ROTA", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Günlük çalışma hatırlatıcısı"
        }
        notificationManager.createNotificationChannel(channel)

        val acilisIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, DashboardActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        val bildirim = NotificationCompat.Builder(context, "dsp_kanal")
            .setSmallIcon(R.drawable.ic_star)
            .setContentTitle("📚 ISG ROTA")
            .setContentText("Bugün henüz çalışmadın! Birkaç soru çözmek için tıkla.")
            .setAutoCancel(true)
            .setContentIntent(acilisIntent)
            .build()

        notificationManager.notify(1, bildirim)
    }
}
