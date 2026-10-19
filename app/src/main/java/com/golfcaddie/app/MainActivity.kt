package com.golfcaddie.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.golfcaddie.app.ui.CaddieViewModel
import com.golfcaddie.app.ui.ClubsScreen
import com.golfcaddie.app.ui.PlayScreen
import org.osmdroid.config.Configuration

class MainActivity : ComponentActivity() {
    // Built with this activity's Application. The default factory caches the first Application
    // it ever sees in a static field, which goes stale when tests create a fresh one per test.
    private val vm: CaddieViewModel by viewModels { ViewModelProvider.AndroidViewModelFactory(application) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // osmdroid: identify the app to the free tile servers and cache tiles in app storage.
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = filesDir
            osmdroidTileCache = cacheDir.resolve("tiles")
        }
        // Keep the screen on while playing.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            MaterialTheme {
                val state by vm.state.collectAsStateWithLifecycle()
                var tab by rememberSaveable { mutableIntStateOf(0) }

                fun granted(p: String) =
                    ContextCompat.checkSelfPermission(this@MainActivity, p) == PackageManager.PERMISSION_GRANTED
                fun reportPermission() {
                    val fine = granted(Manifest.permission.ACCESS_FINE_LOCATION)
                    vm.onLocationPermission(fine, approximateOnly = !fine && granted(Manifest.permission.ACCESS_COARSE_LOCATION))
                }
                val permission = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions(),
                ) { reportPermission() }
                var askedForLocation by rememberSaveable { mutableStateOf(false) }
                // On every resume, so a change made in system Settings is picked up.
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                    reportPermission()
                    if (!granted(Manifest.permission.ACCESS_FINE_LOCATION) && !askedForLocation) {
                        askedForLocation = true
                        permission.launch(arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        ))
                    }
                }

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(tab == 0, { tab = 0 }, icon = { Text("⛳") }, label = { Text("Play") })
                            NavigationBarItem(tab == 1, { tab = 1 }, icon = { Text("🏌") }, label = { Text("Clubs") })
                        }
                    },
                ) { padding ->
                    Box(Modifier.padding(padding)) {
                        when (tab) {
                            0 -> PlayScreen(state, vm)
                            else -> ClubsScreen(state, vm)
                        }
                    }
                }
            }
        }
    }
}
