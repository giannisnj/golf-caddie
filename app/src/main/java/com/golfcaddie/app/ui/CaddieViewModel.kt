package com.golfcaddie.app.ui

import android.app.Application
import android.location.Location
import android.location.LocationManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.golfcaddie.app.data.BagRepository
import com.golfcaddie.app.data.ConditionsRepository
import com.golfcaddie.app.data.CourseRepository
import com.golfcaddie.app.data.Settings
import com.golfcaddie.app.location.gpsUpdates
import com.golfcaddie.app.map.MapSources
import com.golfcaddie.app.map.MapStyle
import com.golfcaddie.app.map.OfflineMaps
import com.golfcaddie.core.Advice
import com.golfcaddie.core.Bag
import com.golfcaddie.core.Caddie
import com.golfcaddie.core.Club
import com.golfcaddie.core.ClubRecommender
import com.golfcaddie.core.ClubStats
import com.golfcaddie.core.Conditions
import com.golfcaddie.core.Course
import com.golfcaddie.core.DistanceUnit
import com.golfcaddie.core.Geo
import com.golfcaddie.core.GeoPoint
import com.golfcaddie.core.HazardAdvisor
import com.golfcaddie.core.Hole
import com.golfcaddie.core.PlaysLike
import com.golfcaddie.core.PlaysLikeCalculator
import com.golfcaddie.core.Recommendation
import com.golfcaddie.core.ShotRecord
import com.golfcaddie.core.Simulator
import com.golfcaddie.core.Situation
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** A shot that's been started (club chosen, start spot saved) but not yet walked to the ball. */
data class PendingShot(val club: Club, val start: GeoPoint, val aim: GeoPoint? = null)

/** Saving the course's map images for offline use. */
sealed interface OfflineMapStatus {
    data object Unknown : OfflineMapStatus
    /** No imagery with a licence that allows offline copies covers this course. */
    data object NotAvailable : OfflineMapStatus
    data class NotSaved(val share: Double) : OfflineMapStatus
    data class Saving(val done: Int, val total: Int) : OfflineMapStatus
    data object Saved : OfflineMapStatus
    data class Failed(val reason: String) : OfflineMapStatus
}

data class UiState(
    val unit: DistanceUnit = DistanceUnit.YARDS,
    val bag: Bag = Bag.default(),
    val courses: List<Course> = emptyList(),
    val course: Course? = null,
    val loading: Boolean = false,
    val message: String? = null,
    /** Simulation mode: position comes from map taps and the step buttons, not GPS. */
    val simulate: Boolean = false,
    val hasLocationPermission: Boolean = false,
    /** Location allowed, but only "Approximate": far too rough for golf distances. */
    val preciseLocationOff: Boolean = false,
    val position: GeoPoint? = null,
    val accuracyMeters: Float? = null,
    /** OSM id of the hole being played (numbers repeat at clubs with several loops). */
    val holeId: Long? = null,
    /** True when the player picked the hole by hand; turns off auto-detection. */
    val holeLocked: Boolean = false,
    val situation: Situation? = null,
    /** How far the shot to the middle of the green plays, after slope, wind and temperature. */
    val playsLike: PlaysLike? = null,
    val recommendation: Recommendation? = null,
    val advice: List<Advice> = emptyList(),
    val pendingShot: PendingShot? = null,
    val mapStyle: MapStyle = MapStyle.SATELLITE,
    val liveWeather: Boolean = true,
    val conditions: Conditions? = null,
    val fetchingElevation: Boolean = false,
    val offlineMap: OfflineMapStatus = OfflineMapStatus.Unknown,
) {
    /** The hole being shown: the one you're on, or the one picked with ◀ ▶ before there's a position. */
    val hole: Hole? get() = situation?.hole ?: course?.holes?.firstOrNull { it.id == holeId }
}

class CaddieViewModel(app: Application) : AndroidViewModel(app) {
    private val courseRepo = CourseRepository(app)
    private val bagRepo = BagRepository(app)
    private val conditionsRepo = ConditionsRepository(app)
    private val offlineMaps = OfflineMaps(app)
    private val settings = Settings(app)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var gpsJob: Job? = null
    private var lastFix: Location? = null
    /** Bumped for every course download; replies from an older, superseded one are ignored. */
    private var loadSeq = 0
    private var weatherJob: Job? = null
    private var lastWeatherAttemptMillis = 0L
    private var elevationJob: Job? = null
    private var offlineJob: Job? = null

    init {
        val cached = courseRepo.cachedCourses()
        val course = cached.firstOrNull { it.id == settings.courseId } ?: cached.firstOrNull()
        _state.update {
            it.copy(
                bag = bagRepo.load(), courses = cached, course = course,
                unit = settings.unit, mapStyle = settings.mapStyle, liveWeather = settings.liveWeather,
            )
        }
        course?.let(::prepareCourse)
    }

    // ---- Location -------------------------------------------------------------------------

    /** Called on every resume too, so a permission changed in system Settings is picked up. */
    fun onLocationPermission(precise: Boolean, approximateOnly: Boolean = false) {
        val before = _state.value
        _state.update { it.copy(hasLocationPermission = precise, preciseLocationOff = approximateOnly) }
        if (before.hasLocationPermission != precise || gpsJob == null) restartGps()
    }

    private fun restartGps() {
        gpsJob?.cancel()
        gpsJob = null
        val s = _state.value
        if (s.simulate || !s.hasLocationPermission) return
        gpsJob = viewModelScope.launch {
            gpsUpdates(getApplication()).collect { onFix(it) }
        }
    }

    private fun onFix(fix: Location) {
        // GPS and the coarser network provider both report. A network fix doesn't replace a
        // GPS fix less than 10 s old; GPS fixes are always taken, even when accuracy dips.
        val prev = lastFix
        if (prev != null && fix.provider != LocationManager.GPS_PROVIDER &&
            prev.provider == LocationManager.GPS_PROVIDER &&
            fix.elapsedRealtimeNanos - prev.elapsedRealtimeNanos < 10_000_000_000L
        ) return
        lastFix = fix
        _state.update { it.copy(accuracyMeters = if (fix.hasAccuracy()) fix.accuracy else null) }
        setPosition(GeoPoint(fix.latitude, fix.longitude))
    }

    fun setSimulate(on: Boolean) {
        _state.update { it.copy(simulate = on, accuracyMeters = null) }
        if (on && _state.value.position == null) jumpToTee()
        if (!on) {
            // Don't keep showing the made-up position as if it were real; wait for GPS.
            lastFix = null
            _state.update { it.copy(position = null) }
            recompute()
        }
        restartGps()
    }

    /** Map tap. In simulation mode this "teleports" you there. */
    fun onMapTap(point: GeoPoint) {
        if (_state.value.simulate) setPosition(point)
    }

    fun jumpToTee() {
        val s = _state.value
        val hole = currentHole(s) ?: s.course?.holes?.firstOrNull() ?: return
        _state.update { it.copy(holeId = hole.id) }
        setPosition(Simulator.atTee(hole))
    }

    /** Simulation: walk [meters] straight toward the green. */
    fun stepTowardGreen(meters: Double) {
        val s = _state.value
        val hole = currentHole(s) ?: return
        setPosition(Simulator.stepTowardGreen(s.position ?: hole.tee, hole, meters))
    }

    private fun setPosition(p: GeoPoint) {
        _state.update { it.copy(position = p) }
        recompute()
    }

    // ---- Course & hole ----------------------------------------------------------------------

    fun findCoursesNearMe() {
        val p = _state.value.position
        if (p == null) {
            _state.update { it.copy(message = "No position yet. Search by name, or wait for GPS.") }
            return
        }
        loadCourses { courseRepo.coursesNear(p) }
    }

    fun searchCourses(name: String) {
        if (name.isBlank()) return
        loadCourses {
            val place = courseRepo.geocode(name) ?: error("Nothing found for \"$name\"")
            courseRepo.coursesNear(place)
        }
    }

    private fun loadCourses(block: suspend () -> List<Course>) {
        val seq = ++loadSeq
        viewModelScope.launch {
            _state.update { it.copy(loading = true, message = null) }
            try {
                val courses = block()
                if (seq != loadSeq) return@launch // a newer search replaced this one
                _state.update {
                    it.copy(
                        courses = courses,
                        message = if (courses.isEmpty()) "No mapped golf holes found there in OpenStreetMap." else null,
                    )
                }
                courses.firstOrNull()?.let(::selectCourse)
            } catch (e: Exception) {
                if (seq == loadSeq) _state.update { it.copy(message = e.message ?: "Download failed") }
            } finally {
                if (seq == loadSeq) _state.update { it.copy(loading = false) }
            }
        }
    }

    fun selectCourse(course: Course) {
        settings.courseId = course.id
        _state.update { it.copy(course = course, holeId = course.holes.firstOrNull()?.id, holeLocked = false) }
        prepareCourse(course)
        if (_state.value.simulate) jumpToTee() else recompute()
    }

    /** Fetches what a newly chosen course still needs: its ground heights, and offline map status. */
    private fun prepareCourse(course: Course) {
        offlineJob?.cancel()
        _state.update { it.copy(offlineMap = OfflineMapStatus.Unknown) }
        offlineJob = viewModelScope.launch {
            val source = MapSources.offlineSourceFor(course)
            val status = if (source == null) {
                OfflineMapStatus.NotAvailable
            } else {
                val share = runCatching { offlineMaps.savedShare(course, source) }.getOrDefault(0.0)
                if (share >= 0.95) OfflineMapStatus.Saved else OfflineMapStatus.NotSaved(share)
            }
            _state.update { if (it.course?.id == course.id) it.copy(offlineMap = status) else it }
        }
        if (course.elevation != null || elevationJob?.isActive == true) return
        elevationJob = viewModelScope.launch {
            _state.update { it.copy(fetchingElevation = true) }
            try {
                val withHeights = course.copy(elevation = conditionsRepo.elevation(course))
                courseRepo.saveCourse(withHeights)
                _state.update { s ->
                    s.copy(
                        course = if (s.course?.id == course.id) withHeights else s.course,
                        courses = s.courses.map { if (it.id == course.id) withHeights else it },
                    )
                }
                recompute()
            } catch (e: Exception) {
                // No signal: distances still work, just without uphill/downhill until next time.
            } finally {
                _state.update { it.copy(fetchingElevation = false) }
            }
        }
    }

    /** Manual hole change from the ◀ ▶ buttons. */
    fun shiftHole(delta: Int) {
        val s = _state.value
        val holes = s.course?.holes ?: return
        if (holes.isEmpty()) return
        val idx = holes.indexOfFirst { it.id == s.holeId }
        // No hole yet: ▶ goes to the first, ◀ to the last.
        val next = if (idx == -1) {
            if (delta > 0) holes.first() else holes.last()
        } else {
            holes[(idx + delta).mod(holes.size)]
        }
        _state.update { it.copy(holeId = next.id, holeLocked = true) }
        if (s.simulate) setPosition(Simulator.atTee(next)) else recompute()
    }

    fun setAutoHole() {
        _state.update { it.copy(holeLocked = false) }
        recompute()
    }

    private fun currentHole(s: UiState) = s.course?.holes?.firstOrNull { it.id == s.holeId }

    private fun recompute() {
        _state.update { s ->
            val course = s.course
            val pos = s.position
            val nothing = s.copy(situation = null, playsLike = null, recommendation = null, advice = emptyList())
            if (course == null || pos == null) return@update nothing
            val hole = (if (s.holeLocked) currentHole(s) else Caddie.locateHole(course, pos, s.holeId))
                ?: return@update nothing
            val situation = Caddie.situation(course, hole, pos)
            val conditions = s.conditions?.takeIf { s.liveWeather && it.isFresh() }
            val plays = PlaysLikeCalculator.compute(pos, hole.greenCenter, course.elevation, conditions)
            val rec = ClubRecommender.recommend(plays.playsMeters, s.bag, situation.onGreen)
            s.copy(
                holeId = hole.id,
                situation = situation,
                playsLike = plays,
                recommendation = rec,
                advice = HazardAdvisor.advise(situation, rec, s.bag, plays.conditionFactor),
            )
        }
        maybeRefreshWeather()
    }

    // ---- Weather -----------------------------------------------------------------------------

    private fun Conditions.isFresh() = System.currentTimeMillis() - fetchedAtMillis < WEATHER_MAX_AGE_MS

    /** Fetches wind and temperature when there's none or it's over 15 minutes old. */
    private fun maybeRefreshWeather() {
        val s = _state.value
        val at = s.position ?: return
        if (!s.liveWeather || weatherJob?.isActive == true) return
        if (s.conditions?.let { System.currentTimeMillis() - it.fetchedAtMillis < WEATHER_REFRESH_MS } == true) return
        // After a failure (no signal), wait a couple of minutes before trying again.
        if (System.currentTimeMillis() - lastWeatherAttemptMillis < WEATHER_RETRY_MS) return
        lastWeatherAttemptMillis = System.currentTimeMillis()
        weatherJob = viewModelScope.launch {
            runCatching { conditionsRepo.weather(at) }.onSuccess { c ->
                _state.update { it.copy(conditions = c) }
                recompute()
            }
        }
    }

    fun setLiveWeather(on: Boolean) {
        settings.liveWeather = on
        _state.update { it.copy(liveWeather = on) }
        lastWeatherAttemptMillis = 0
        recompute()
    }

    // ---- Offline map ---------------------------------------------------------------------------

    fun saveOfflineMap() {
        val course = _state.value.course ?: return
        if (_state.value.offlineMap is OfflineMapStatus.Saving) return
        offlineJob?.cancel()
        offlineJob = viewModelScope.launch {
            _state.update { it.copy(offlineMap = OfflineMapStatus.Saving(0, 0)) }
            val status = try {
                when (val r = offlineMaps.save(course) { done, total ->
                    _state.update { it.copy(offlineMap = OfflineMapStatus.Saving(done, total)) }
                }) {
                    OfflineMaps.Result.NotAvailable -> OfflineMapStatus.NotAvailable
                    is OfflineMaps.Result.Saved ->
                        if (r.tiles == 0) OfflineMapStatus.Failed("No imagery covers this course") else OfflineMapStatus.Saved
                }
            } catch (e: Exception) {
                OfflineMapStatus.Failed(e.message ?: "Download failed")
            }
            _state.update { it.copy(offlineMap = status) }
        }
    }

    // ---- Clubs & shots ----------------------------------------------------------------------

    /** Remember where you're hitting from and aiming at; call [finishShot] when you reach the ball. */
    fun startShot(club: Club) {
        val s = _state.value
        val p = s.position ?: return
        val aim = s.hole?.let { Caddie.aimPoint(it, p, ClubStats.of(club, s.bag.shots).typicalMeters) }
        _state.update { it.copy(pendingShot = PendingShot(club, p, aim)) }
    }

    /** From the watch: start a shot with the club whose id is [clubId]. */
    fun startShot(clubId: String) {
        _state.value.bag.clubs.firstOrNull { it.id == clubId }?.let(::startShot)
    }

    fun finishShot() {
        val s = _state.value
        val pending = s.pendingShot ?: return
        val end = s.position ?: return
        val meters = Geo.distance(pending.start, end)
        if (meters < ClubStats.MIN_SHOT_METERS) {
            // Probably tapped before walking to the ball; keep the shot open.
            _state.update {
                it.copy(message = "Only ${formatDistance(meters, it.unit)} from where you hit. " +
                    "Walk to your ball first, or tap Cancel to discard.")
            }
            return
        }
        _state.update { it.copy(pendingShot = null) }
        addShot(pending.club.id, meters, pending.start, end, pending.aim)
        _state.update {
            it.copy(message = "Saved ${pending.club.name}: ${formatDistance(meters, it.unit)}")
        }
    }

    fun cancelShot() = _state.update { it.copy(pendingShot = null) }

    fun addShot(clubId: String, meters: Double, start: GeoPoint? = null, end: GeoPoint? = null, aim: GeoPoint? = null) {
        if (meters < ClubStats.MIN_SHOT_METERS) return
        val shot = ShotRecord(UUID.randomUUID().toString(), clubId, meters, System.currentTimeMillis(), start, end, aim)
        updateBag { it.copy(shots = it.shots + shot) }
    }

    fun deleteShot(id: String) = updateBag { b -> b.copy(shots = b.shots.filterNot { it.id == id }) }

    fun setManualMax(clubId: String, meters: Double?) = updateClub(clubId) { it.copy(manualMaxMeters = meters) }

    fun setInBag(clubId: String, inBag: Boolean) = updateClub(clubId) { it.copy(inBag = inBag) }

    private fun updateClub(id: String, change: (Club) -> Club) =
        updateBag { b -> b.copy(clubs = b.clubs.map { if (it.id == id) change(it) else it }) }

    private fun updateBag(change: (Bag) -> Bag) {
        _state.update { it.copy(bag = change(it.bag)) }
        bagRepo.save(_state.value.bag)
        recompute()
    }

    // ---- Settings ----------------------------------------------------------------------------

    fun setUnit(unit: DistanceUnit) {
        settings.unit = unit
        _state.update { it.copy(unit = unit) }
    }

    fun setMapStyle(style: MapStyle) {
        settings.mapStyle = style
        _state.update { it.copy(mapStyle = style) }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    private companion object {
        const val WEATHER_REFRESH_MS = 15 * 60 * 1000L
        /** Older than this, wind and temperature are ignored rather than trusted. */
        const val WEATHER_MAX_AGE_MS = 60 * 60 * 1000L
        const val WEATHER_RETRY_MS = 2 * 60 * 1000L
    }
}

fun formatDistance(meters: Double, unit: DistanceUnit): String =
    "${Math.round(unit.fromMeters(meters))} ${unit.label}"
