package com.golfcaddie.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import com.golfcaddie.core.WatchState
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Shows what the phone app sends over the Wear OS Data Layer, and sends shot commands back.
 * The phone does the GPS, course data and club maths; the watch is a display and a remote.
 */
class WatchActivity : ComponentActivity(), DataClient.OnDataChangedListener {
    private val state = mutableStateOf(WatchState(status = WAITING_FOR_PHONE))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WatchScreen(
                state = state.value,
                onStartShot = { id -> send(WatchState.START_SHOT_PATH, id) },
                onFinishShot = { send(WatchState.FINISH_SHOT_PATH, "") },
                onCancelShot = { send(WatchState.CANCEL_SHOT_PATH, "") },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        val client = Wearable.getDataClient(this)
        client.addListener(this)
        // Show the phone's latest state straight away rather than waiting for the next change.
        lifecycleScope.launch {
            runCatching { client.dataItems.await() }.getOrNull()?.let { items ->
                try {
                    items.firstOrNull { it.uri.path == WatchState.DATA_PATH }?.let(::show)
                } finally {
                    items.release()
                }
            }
        }
    }

    override fun onPause() {
        Wearable.getDataClient(this).removeListener(this)
        super.onPause()
    }

    override fun onDataChanged(events: DataEventBuffer) {
        events.filter { it.type == DataEvent.TYPE_CHANGED && it.dataItem.uri.path == WatchState.DATA_PATH }
            .lastOrNull()?.let { show(it.dataItem) }
        events.release()
    }

    private fun show(item: DataItem) {
        val json = DataMapItem.fromDataItem(item).dataMap.getString(WatchState.DATA_KEY) ?: return
        runCatching { WatchState.decode(json) }.onSuccess { state.value = it }
    }

    /** Sends a command to the phone(s) this watch is connected to. */
    private fun send(path: String, payload: String) {
        lifecycleScope.launch {
            runCatching {
                val nodes = Wearable.getNodeClient(this@WatchActivity).connectedNodes.await()
                val messages = Wearable.getMessageClient(this@WatchActivity)
                nodes.forEach { messages.sendMessage(it.id, path, payload.toByteArray()).await() }
            }
        }
    }
}
