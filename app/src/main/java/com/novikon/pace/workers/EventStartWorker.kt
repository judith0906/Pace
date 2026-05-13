package com.novikon.pace.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.novikon.pace.data.CircleChatEventsManager
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.suspendCancellableCoroutine
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import kotlin.coroutines.resume

// Worker que arranca los eventos cuya hora ya ha llegado, sin necesidad de
// que ningún usuario esté en el chat. WorkManager lo despierta puntualmente
// gracias a la alarma exacta programada en scheduleEventAlarm().
class EventStartWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val circleId = inputData.getString(KEY_CIRCLE_ID) ?: return Result.failure()
        val eventsManager = CircleChatEventsManager(applicationContext)
        eventsManager.checkAndStartDueEvents(circleId)
        return Result.success()
    }

    companion object {
        const val KEY_CIRCLE_ID = "circle_id"

        // Programa una OneTimeWorkRequest con alarma exacta para la hora del evento.
        // Se llama justo después de crear un evento.
        fun scheduleForEvent(
            context: Context,
            circleId: String,
            scheduledAtMillis: Long
        ) {
            val now = System.currentTimeMillis()
            val delay = (scheduledAtMillis - now).coerceAtLeast(0L)

            val data = androidx.work.Data.Builder()
                .putString(KEY_CIRCLE_ID, circleId)
                .build()

            val request = androidx.work.OneTimeWorkRequestBuilder<EventStartWorker>()
                .setInitialDelay(delay, java.util.concurrent.TimeUnit.MILLISECONDS)
                .setInputData(data)
                // Tag con el circleId para poder cancelarlo si se borra el evento
                .addTag("event_start_$circleId")
                .build()

            androidx.work.WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    // ID único por círculo+hora: evita duplicados si se re-crea el evento
                    "event_start_${circleId}_$scheduledAtMillis",
                    androidx.work.ExistingWorkPolicy.REPLACE,
                    request
                )
        }
    }
}