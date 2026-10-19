package com.golfcaddie.app

import android.content.Context
import com.golfcaddie.app.ui.CaddieViewModel
import com.golfcaddie.app.ui.toWatchState
import com.golfcaddie.core.WatchState
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Keeps the Wear OS watch app in step with the phone: publishes what to show as a Data Layer
 * item, and turns the watch's buttons into shots. Does nothing on phones without Google Play
 * services (or with no watch paired).
 */
class WatchLink(private val context: Context, private val vm: CaddieViewModel) {
    private val onMessage = MessageClient.OnMessageReceivedListener { event ->
        when (event.path) {
            WatchState.START_SHOT_PATH -> vm.startShot(event.data.decodeToString())
            WatchState.FINISH_SHOT_PATH -> vm.finishShot()
            WatchState.CANCEL_SHOT_PATH -> vm.cancelShot()
        }
    }

    private val available: Boolean =
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

    /** Starts syncing; cancel the returned job (or its scope) to stop. */
    fun start(scope: CoroutineScope): Job? {
        if (!available) return null
        val messages = Wearable.getMessageClient(context)
        messages.addListener(onMessage)
        return scope.launch {
            try {
                vm.state.map { it.toWatchState() }.distinctUntilChanged().collect { publish(it) }
            } finally {
                messages.removeListener(onMessage)
            }
        }
    }

    private suspend fun publish(state: WatchState) {
        val request = PutDataMapRequest.create(WatchState.DATA_PATH).apply {
            dataMap.putString(WatchState.DATA_KEY, state.encode())
        }.asPutDataRequest().setUrgent()
        // No watch, or the watch is out of range: the next change will try again.
        runCatching { Wearable.getDataClient(context).putDataItem(request).await() }
    }
}
