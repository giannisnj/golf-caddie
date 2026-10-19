package com.golfcaddie.app

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.golfcaddie.app.ui.CaddieViewModel
import com.golfcaddie.app.ui.ClubsScreen
import com.golfcaddie.app.ui.CourseScreen
import com.golfcaddie.app.ui.PlayScreen
import com.golfcaddie.app.ui.theme.GolfCaddieTheme
import com.golfcaddie.app.ui.theme.GolfIcons
import com.golfcaddie.app.ui.theme.golf
import org.osmdroid.config.Configuration

class MainActivity : ComponentActivity() {
    // Built with this activity's Application. The default factory caches the first Application
    // it ever sees in a static field, which goes stale when tests create a fresh one per test.
    private val vm: CaddieViewModel by viewModels { ViewModelProvider.AndroidViewModelFactory(application) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // osmdroid: identify the app to the tile servers and keep map images in app storage.
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = filesDir
            // In app files, not the cache folder: Android may empty the cache when storage runs
            // low, which would delete map images saved for offline use.
            osmdroidTileCache = filesDir.resolve("tiles").apply { mkdirs() }
        }
        // Keep the screen on while playing.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Mirror distances to a paired Wear OS watch while the app is open.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { WatchLink(this@MainActivity, vm).start(this) }
        }

        // The green hole bar runs up under the status bar, so its icons are light.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))

        setContent {
            GolfCaddieTheme {
                val state by vm.state.collectAsStateWithLifecycle()
                var tab by rememberSaveable { mutableIntStateOf(0) }

                fun granted(p: String) =
                    ContextCompat.checkSelfPermission(this@MainActivity, p) == PackageManager.PERMISSION_GRANTED
                fun reportPermission() {
                    val fine = granted(Manifest.permission.ACCESS_FINE_LOCATION)
                    vm.onLocationPermission(fine, approximateOnly = !fine && granted(Manifest.permission.ACCESS_COARSE_LOCATION))
                }
                var openSettingsIfRefused by remember { mutableStateOf(false) }
                val permission = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions(),
                ) {
                    reportPermission()
                    // Asked from "Allow location" and still no precise location: Android has stopped
                    // asking, so the switch is in Settings.
                    if (openSettingsIfRefused && !granted(Manifest.permission.ACCESS_FINE_LOCATION)) {
                        startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
                        )
                    }
                    openSettingsIfRefused = false
                }
                val fixLocation = {
                    openSettingsIfRefused = true
                    permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }
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
                    // Each screen's green header handles the status bar itself.
                    contentWindowInsets = WindowInsets(0),
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = { TabBar(tab) { tab = it } },
                ) { padding ->
                    Box(Modifier.padding(padding)) {
                        when (tab) {
                            0 -> PlayScreen(state, vm, onOpenCourse = { tab = 2 }, onFixLocation = fixLocation)
                            1 -> ClubsScreen(state, vm)
                            else -> CourseScreen(state, vm, onCoursePicked = { tab = 0 })
                        }
                    }
                }
            }
        }
    }
}

/** Three tabs with proper icons; the active one filled, its label bold. */
@Composable
private fun TabBar(tab: Int, onSelect: (Int) -> Unit) {
    val g = MaterialTheme.golf
    val c = MaterialTheme.colorScheme
    val line = c.outlineVariant
    NavigationBar(
        containerColor = g.navBar,
        tonalElevation = 0.dp,
        modifier = Modifier.drawBehind { drawLine(line, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) },
    ) {
        listOf(
            Triple("Play", GolfIcons.Play, GolfIcons.Play),
            Triple("Clubs", GolfIcons.Clubs, GolfIcons.ClubsFilled),
            Triple("Course", GolfIcons.Course, GolfIcons.Course),
        ).forEachIndexed { i, (label, icon, active) ->
            val on = tab == i
            NavigationBarItem(
                selected = on,
                onClick = { onSelect(i) },
                icon = { Icon(if (on) active else icon, contentDescription = null, Modifier.size(24.dp)) },
                label = {
                    Text(label, style = MaterialTheme.typography.labelMedium.copy(letterSpacing = MaterialTheme.typography.bodySmall.letterSpacing, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold))
                },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = c.onPrimaryContainer,
                    selectedTextColor = c.onSurface,
                    indicatorColor = g.navIndicator,
                    unselectedIconColor = c.onSurfaceVariant,
                    unselectedTextColor = c.onSurfaceVariant,
                ),
            )
        }
    }
}
