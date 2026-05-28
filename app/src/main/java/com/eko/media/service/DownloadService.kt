package com.eko.media.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.eko.media.EkoMediaApp
import com.eko.media.R
import com.eko.media.model.DownloadStatus
import com.eko.media.model.DownloadTask
import com.eko.media.ui.MainActivity
import com.eko.media.util.DownloadEngine
import com.eko.media.util.VideoExtractor
import kotlinx.coroutines.*

class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val notifManager by lazy { getSystemService(NotificationManager::class.java) }

    companion object {
        const val ACTION_DOWNLOAD = "eko.action.DOWNLOAD"
        const val ACTION_CANCEL   = "eko.action.CANCEL"
        const val EXTRA_TASK_JSON = "eko.extra.TASK_JSON"
        const val NOTIF_ID = 1001

        // Shared queue accessible from ViewModel
        val activeDownloads = mutableMapOf<String, DownloadTask>()

        fun startDownload(context: Context, task: DownloadTask) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_DOWNLOAD
                putExtra("task_id", task.id)
                putExtra("task_url", task.url)
                putExtra("format_id", task.format.formatId)
                putExtra("format_ext", task.format.ext)
                putExtra("title", task.title)
                putExtra("audio_only", task.format.isAudioOnly)
            }
            context.startForegroundService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification("EKO Engine ready", 0))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DOWNLOAD -> handleDownload(intent)
            ACTION_CANCEL   -> stopSelf()
        }
        return START_STICKY
    }

    private fun handleDownload(intent: Intent) {
        val taskId    = intent.getStringExtra("task_id") ?: return
        val url       = intent.getStringExtra("task_url") ?: return
        val formatId  = intent.getStringExtra("format_id") ?: "720"
        val ext       = intent.getStringExtra("format_ext") ?: "mp4"
        val title     = intent.getStringExtra("title") ?: "video"
        val audioOnly = intent.getBooleanExtra("audio_only", false)

        scope.launch {
            val task = activeDownloads[taskId] ?: return@launch
            task.status = DownloadStatus.DOWNLOADING

            // 1. Get direct download URL from cobalt
            val directUrl = VideoExtractor.buildDownloadUrl(url, task.format)
            if (directUrl == null) {
                task.status = DownloadStatus.FAILED
                notifManager.notify(NOTIF_ID, buildNotification("❌ Failed: $title", 0))
                return@launch
            }

            // 2. Download file
            val fileName = "$title.$ext"
            DownloadEngine.download(
                context    = this@DownloadService,
                url        = directUrl,
                fileName   = fileName,
                onProgress = { prog ->
                    task.progress = prog.percent
                    task.speed    = DownloadEngine.formatSpeed(prog.speedBps)

                    if (prog.done) {
                        task.status    = DownloadStatus.COMPLETED
                        task.localPath = prog.filePath
                        notifManager.notify(NOTIF_ID,
                            buildNotification("✅ Done: $title", 100))
                        stopSelfResult(startId)
                    } else if (prog.error != null) {
                        task.status = DownloadStatus.FAILED
                        notifManager.notify(NOTIF_ID,
                            buildNotification("❌ Error: ${prog.error}", 0))
                    } else {
                        notifManager.notify(NOTIF_ID,
                            buildNotification("⬇ $title — ${prog.percent}%  ${task.speed}", prog.percent))
                    }
                }
            )
        }
    }

    private fun buildNotification(text: String, progress: Int): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, EkoMediaApp.CHANNEL_DOWNLOAD)
            .setContentTitle("EKO MEDIA")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_download)
            .setContentIntent(pendingIntent)
            .setOngoing(progress in 1..99)
            .apply {
                if (progress in 1..99) {
                    setProgress(100, progress, false)
                }
            }
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
