package com.golfcaddie.app.ui

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.golfcaddie.app.data.BagRepository
import com.golfcaddie.app.data.CourseRepository
import com.golfcaddie.app.location.gpsUpdates
import com.golfcaddie.core.Bag
import com.golfcaddie.core.Caddie
import com.golfcaddie.core.Club
import com.golfcaddie.core.ClubRecommender
import com.golfcaddie.core.Course
import com.golfcaddie.core.DistanceUnit
import com.golfcaddie.core.GeoPoint
import com.golfcaddie.core.Geo
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
data class PendingShot(val club: Club, val start: GeoPoint)

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
    val position: GeoPoint? = null,
    val accuracyMeters: Float? = null,
    val holeNumber: Int? = null,
    /** True when the player picked the hole by hand; turns off auto-detection. */
    val holeLocked: Boolean = false,
    val situation: Situation? = null,
    val recommendation: Recommendation? = null,
    val pendingShot: PendingShot? = null,
    val satellite: Boolean = true,
)

class CaddieViewModel(app: Application) : AndroidViewModel(app) {
    private val courseRepo = CourseRepository(app)
    private val bagRepo = BagRepository(app)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var gpsJob: Job? = null
    private var lastFix: Location? = null

    init {
        val cached = courseRepo.cachedCourses()
        _state.update { it.copy(bag = bagRepo.load(), courses = cached, course = cached.firstOrNull()) }
    }

    // ---- Location -------------------------------------------------------------------------

    fun onLocationPermission(granted: Boolean) {
        _state.update { it.copy(hasLocationPermission = granted) }
        restartGps()
    }

    private fun restartGps() {
        gpsJob?.cancel()
        val s = _state.value
        if (s.simulate || !s.hasLocationPermission) return
        gpsJob = viewModelScope.launch {
            gpsUpdates(getApplication()).collect { onFix(it) }
        }
    }

    private fun onFix(fix: Location) {
        // GPS and the coarser network provider both report; don't let a rough network
        // fix replace a recent, more precise GPS fix.
        val prev = lastFix
        if (prev != null && fix.accuracy > prev.accuracy * 2 && fix.time - prev.time < 10_000) return
        lastFix = fix
        _state.update { it.copy(accuracyMeters = if (fix.hasAccuracy()) fix.accuracy else null) }
        setPosition(GeoPoint(fix.latitude, fix.longitude))
    }

    fun setSimulate(on: Boolean) {
        _state.update { it.copy(simulate = on, accuracyMeters = null) }
        if (on && _state.value.position == null) jumpToTee()
        restartGps()
    }

    /** Map tap. In simulation mode this "teleports" you there. */
    fun onMapTap(point: GeoPoint) {
        if (_state.value.simulate) setPosition(point)
    }

    fun jumpToTee() {
        val s = _state.value
        val hole = currentHole(s) ?: s.course?.holes?.firstOrNull() ?: return
        _state.update { it.copy(holeNumber = hole.number) }
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
        viewModelScope.launch {
            _state.update { it.copy(loading = true, message = null) }
            try {
                val courses = block()
                _state.update {
                    it.copy(
                        courses = courses,
                        message = if (courses.isEmpty()) "No mapped golf holes found there in OpenStreetMap." else null,
                    )
                }
                courses.firstOrNull()?.let(::selectCourse)
            } catch (e: Exception) {
                _state.update { it.copy(message = e.message ?: "Download failed") }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    fun selectCourse(course: Course) {
        _state.update { it.copy(course = course, holeNumber = course.holes.firstOrNull()?.number, holeLocked = false) }
        if (_state.value.simulate) jumpToTee() else recompute()
    }

    /** Manual hole change from the ◀ ▶ buttons. */
    fun shiftHole(delta: Int) {
        val s = _state.value
        val holes = s.course?.holes ?: return
        if (holes.isEmpty()) return
        val idx = holes.indexOfFirst { it.number == s.holeNumber }.coerceAtLeast(0)
        val next = holes[(idx + delta).mod(holes.size)]
        _state.update { it.copy(holeNumber = next.number, holeLocked = true) }
        if (s.simulate) setPosition(Simulator.atTee(next)) else recompute()
    }

    fun setAutoHole() {
        _state.update { it.copy(holeLocked = false) }
        recompute()
    }

    private fun currentHole(s: UiState) = s.course?.holes?.firstOrNull { it.number == s.holeNumber }

    private fun recompute() {
        _state.update { s ->
            val course = s.course
            val pos = s.position
            if (course == null || pos == null) return@update s.copy(situation = null, recommendation = null)
            val hole = if (s.holeLocked) currentHole(s) else Caddie.locateHole(course, pos, s.holeNumber)
            if (hole == null) return@update s.copy(situation = null, recommendation = null)
            val situation = Caddie.situation(course, hole, pos)
            s.copy(
                holeNumber = hole.number,
                situation = situation,
                recommendation = ClubRecommender.recommend(situation.targetMeters, s.bag, situation.onGreen),
            )
        }
    }

    // ---- Clubs & shots ----------------------------------------------------------------------

    /** Remember where you're hitting from; call [finishShot] when you reach the ball. */
    fun startShot(club: Club) {
        val p = _state.value.position ?: return
        _state.update { it.copy(pendingShot = PendingShot(club, p)) }
    }

    fun finishShot() {
        val s = _state.value
        val pending = s.pendingShot ?: return
        val end = s.position ?: return
        val meters = Geo.distance(pending.start, end)
        _state.update { it.copy(pendingShot = null) }
        addShot(pending.club.id, meters, pending.start, end)
        _state.update {
            it.copy(message = "Saved ${pending.club.name}: ${formatDistance(meters, it.unit)}")
        }
    }

    fun cancelShot() = _state.update { it.copy(pendingShot = null) }

    fun addShot(clubId: String, meters: Double, start: GeoPoint? = null, end: GeoPoint? = null) {
        val shot = ShotRecord(UUID.randomUUID().toString(), clubId, meters, System.currentTimeMillis(), start, end)
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

    fun setUnit(unit: DistanceUnit) = _state.update { it.copy(unit = unit) }
    fun setSatellite(on: Boolean) = _state.update { it.copy(satellite = on) }
    fun clearMessage() = _state.update { it.copy(message = null) }
}

fun formatDistance(meters: Double, unit: DistanceUnit): String =
    "${Math.round(unit.fromMeters(meters))} ${unit.label}"
