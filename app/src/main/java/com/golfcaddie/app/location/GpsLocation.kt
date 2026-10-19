package com.golfcaddie.app.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private const val MAX_LAST_KNOWN_AGE_NANOS = 2 * 60 * 1_000_000_000L

/**
 * Live position from the phone's GPS through the plain Android LocationManager
 * (no Google Play Services needed). This is also what the emulator's
 * "Extended controls > Location" and `adb emu geo fix <lon> <lat>` feed.
 * Callers must hold ACCESS_FINE_LOCATION.
 */
@SuppressLint("MissingPermission")
fun gpsUpdates(context: Context): Flow<Location> = callbackFlow {
    val manager = context.getSystemService(LocationManager::class.java)
    // Every method is overridden: on Android 10 and older they have no default implementation.
    val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            trySend(location)
        }
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        @Deprecated("Deprecated in Android")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }
    val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        .filter { manager.allProviders.contains(it) }
    // A cached fix is only useful if it's recent: yesterday's would put you at home.
    providers.mapNotNull { manager.getLastKnownLocation(it) }
        .filter { SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos < MAX_LAST_KNOWN_AGE_NANOS }
        .maxByOrNull { it.elapsedRealtimeNanos }
        ?.let { trySend(it) }
    providers.forEach {
        manager.requestLocationUpdates(it, 1_000L, 1f, listener, Looper.getMainLooper())
    }
    awaitClose { manager.removeUpdates(listener) }
}
