# Golf Caddie

An Android app (iPhone later), with a Wear OS watch companion, that recommends a club from where
you're standing on the course, using free OpenStreetMap course data and your own distance history
with each club.

## How it works

1. **Course data**: free from [OpenStreetMap](https://www.openstreetmap.org) via the
   [Overpass API](https://wiki.openstreetmap.org/wiki/Overpass_API). Mappers trace each hole as a
   tee-to-green line (`golf=hole`, with `ref` = hole number and `par`), plus greens, tees, fairways,
   bunkers and water. The app downloads the golf features within 3 km and keeps them on the phone for
   offline play. Look a course up by name with OSM's free Nominatim geocoder (Course tab).
   **Canberra's 11 courses are built into the app** (`app/src/main/assets/courses/canberra.json`, with
   slopes), so finding them is instant and needs no signal. The public Overpass servers are often busy:
   in October 2026 they took 30–45 s to answer and often returned 502, and both backup servers were down.
   Elsewhere the app works through the servers in turn, retrying for up to about 3 minutes.
2. **Your position**: the phone's GPS through Android's built-in `LocationManager` (no Google Play
   Services or API key needed).
3. **Distances**: front, center and back of the green, and the reach/carry distance of any bunker or
   water near the line to the green, and which side it's on. Clubs are picked for the middle of the
   green: a flag mapped in OSM is shown, but it's only where the flag stood on the day it was mapped.
4. **Plays like**: the distance adjusted for slope, wind and temperature, and the club is picked for
   that. Ground heights come from [Open-Meteo](https://open-meteo.com)'s free elevation API (fetched
   once per course, then offline); wind and temperature from its free weather API, refreshed every
   15 minutes while you play (switch off in the Course tab). Rules of thumb, in `PlaysLike.kt`:
   ~1 m longer per m uphill; into the wind ~1% per mph, downwind about half that; ~0.12% per °C away
   from 21 °C.
5. **Club pick**: the shortest club whose *typical* distance reaches the plays-like distance. "Typical"
   is the average of your last 20 shots with that club, with mishits dropped. Until you have 3 shots
   it's 95% of your max, and before that it's an average golfer's distance.
6. **Your spread**: once a club has 5 shots, its usual range (the middle 60% of your shots) comes from
   your own data; until then a typical spread for the club type. Shots walked with GPS also record where
   you were aiming, so the app learns if a club tends left or right. The Clubs tab shows a gapping
   chart of every club's range.
7. **Hazard advice**: each club's distance is treated as a bell curve fitted to your range. When at
   least 1 in 5 of your shots with the recommended club would finish in a bunker or water, the app says
   so, and for hazards across the line suggests a club that carries it 95% of the time, or one that
   lays up short. Carry is estimated from total distance by club type.
8. **Recording shots**: tap *Record a shot* (or *Hit* on the watch), pick the club, walk to the ball,
   tap *Ball is here*. You can also type in shots or your max per club.
9. **Watch**: the Wear OS app shows the hole, distances, plays-like distance, club and range, and the
   main hazard warning, and can start and finish a shot. The phone does the work and sends it over the
   Wear OS Data Layer.

### Maps and offline use

Three map styles (the chip on the Play screen cycles them):

| Style | Source | Offline |
|---|---|---|
| Satellite | ACT Government imagery in Canberra (CC BY, ACT Government and Aerometrex); Esri World Imagery elsewhere | **Canberra: yes**, save it in the Course tab. Elsewhere no: Esri and NSW imagery services set `exportTilesAllowed: false` |
| Street | OpenStreetMap standard tiles | No: OSM's tile policy forbids bulk downloading |
| Course plan | Drawn from the course's OSM data (fairways, greens, bunkers, water, trees) | **Always**: no map images needed |

Distances, slopes, advice and the watch all work with no signal; only map images and live weather need it.

## Design

The screens follow the Golf Caddie design canvas made in Claude Design (October 2026): a light theme
built for direct sun and a dark one for evenings (it follows the phone's setting). Green is the caddie
talking, amber means watch out, red means something is broken, blue is you.

- Theme: `app/src/main/java/com/golfcaddie/app/ui/theme/Theme.kt` (Material 3 colour roles, the extra
  `GolfColors`, the Barlow type scale with tabular figures, corner radii); icons in `GolfIcons.kt`.
- Font: [Barlow](https://github.com/jpt/barlow), © The Barlow Project Authors, SIL Open Font License,
  in `res/font/` with its licence in `assets/licenses/`.
- Play: the hole bar, the map with its style and unit controls, and the caddie panel, which pulls up
  over the map. Clubs: a gapping chart that opens each club. Course: current course, live weather,
  offline map, find a course.

## Project layout

| Module | What | Builds without Android SDK |
|---|---|---|
| `core/` | Pure Kotlin: geo math, OSM parsing, hole detection, hazards, plays-like, club stats, spread and advice, tile maths, watch payload, position simulator, desktop CLI | yes (`-PcoreOnly`) |
| `app/` | Android phone app: Jetpack Compose UI, osmdroid map, GPS, weather/elevation, offline maps, watch link | needs Android SDK |
| `wear/` | Wear OS app: Wear Compose UI, Data Layer client | needs Android SDK |

`core` uses no Android or Java-only APIs (except its CLI), so it can become a Kotlin Multiplatform
module and be shared with the iPhone app.

## Build and run

Open the folder in **Android Studio** (Ladybug or newer), let it sync, and run `app` on a phone or
emulator and `wear` on a Wear OS watch or emulator. Or from the command line, with the Android SDK:

```sh
./gradlew :app:installDebug      # phone
./gradlew :wear:installDebug     # watch (pair it with the phone first)
```

The phone and watch apps share the application id `com.golfcaddie.app`, which the Data Layer requires;
for a release, sign both with the same key.

## Testing with a fake position

- **In the app**: open the ⋮ menu and turn on **Simulate GPS**. Tap anywhere on the map to stand there,
  use **Tee** to jump to the current hole's tee, and **+25 yd** to walk toward the green. Use the arrows
  either side of the hole number to change holes.
- **Android emulator**: leave Simulate off. In *Extended controls (⋯) > Location*, set a point or play
  a GPX/KML route, or run `adb emu geo fix <lon> <lat>` (longitude comes first).
- **Real device**: pick a mock-location app under *Developer options > Select mock location app*.
- **Desktop, no phone**: download a real course and run the caddie against fake positions:

  ```sh
  tools/fetch_course.sh 56.3433 -2.8030 2500 my_course.json          # St Andrews: lat lon radius_m output
  ./gradlew -PcoreOnly :core:run --args="elevation my_course.json heights.json"   # optional: slopes
  ./gradlew -PcoreOnly :core:run --args="my_course.json --elevation=heights.json --wind=6@315 --temp=12"
  ./gradlew -PcoreOnly :core:run --args="my_course.json 56.3440 -2.8060"  # stand at a lat/lon
  ```

  (`--wind=<m/s>@<degrees it blows from>`.) If the Overpass servers are down or block your network,
  `tools/osm_api_fetch.py "Royal Canberra Golf Club" out.json` looks the course up by name and downloads
  it from the main OpenStreetMap API instead (for testing only).

## Rebuilding the built-in courses

```sh
tools/build_bundle.sh canberra -35.30 149.12 30000
```

This downloads every course with mapped holes within 30 km of Canberra, one at a time and retrying
when Overpass is busy, adds slopes from Open-Meteo, and writes `app/src/main/assets/courses/canberra.json`.
Any `<region>.json` in that folder is used the same way, so another city is a second run with its own
centre and radius.

## Tests

- `./gradlew -PcoreOnly :core:test` (no Android SDK needed): a synthetic course, real OpenStreetMap
  courses that each exposed a bug (Moore Park, Sydney: outline drawn too small; Domat/Ems, Switzerland:
  27 holes in loops), real Open-Meteo heights for Moore Park, plays-like, spread, hazard advice and tile maths.
- `./gradlew :app:testDebugUnitTest`: runs the real phone app on a simulated Android device (Robolectric,
  no emulator): simulate mode, the real GPS path fed fake fixes, plays-like on the hilliest hole, hazard
  advice, the gapping chart, the course plan, the watch payload, and a real offline save of Royal Canberra's
  ACT imagery shown with the network cut. Screenshots go to `app/build/screenshots/`.
- `./gradlew :wear:testDebugUnitTest`: the watch screen on a simulated round watch. Screenshots go to
  `wear/build/screenshots/`.

## Limitations

- The built-in Canberra courses are a snapshot (the date is in the file). Changes mapped in OSM after
  that need a rebuild of the pack.
- Coverage depends on OpenStreetMap: in October 2026 all 11 Canberra courses and about two thirds of
  Sydney's had their holes mapped (`docs/coverage_canberra_sydney_2026-10.csv`). Anyone can add holes at
  openstreetmap.org, and the app reports when a course has none.
- Slopes come from a 90 m terrain model: right for the rise across a hole, blind to small mounds.
- Plays-like and carry figures are rules of thumb, not launch-monitor physics; lie isn't factored in.
- Hazards beside the line count with a fixed share for missing to that side, until per-club left/right
  spread is modelled from enough walked shots.
- Open-Meteo is free for non-commercial use; a published app would need its commercial plan.
- The phone-to-watch link (Wear OS Data Layer) is tested on each side separately, not yet between a real
  paired phone and watch.
