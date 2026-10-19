# Golf Caddie

An Android app (iPhone later) that recommends a club from where you're standing on the course,
using free OpenStreetMap course data and your own distance history with each club.

## How it works

1. **Course data**: free from [OpenStreetMap](https://www.openstreetmap.org) via the
   [Overpass API](https://wiki.openstreetmap.org/wiki/Overpass_API). Mappers trace each hole as a
   tee-to-green line (`golf=hole`, with `ref` = hole number and `par`), plus greens, tees, bunkers and
   water. The app downloads every golf feature within 3 km and caches it on the phone for offline play.
   Look a course up by name with OSM's free Nominatim geocoder.
2. **Your position**: the phone's GPS through Android's built-in `LocationManager` (no Google Play
   Services or API key needed).
3. **Distances**: front, center and back of the green (plus the pin, when someone has mapped it), and
   the reach/carry distance of any bunker or water between you and the green.
4. **Club pick**: the shortest club whose *typical* distance reaches the target. "Typical" is the average of
   your last 20 shots with that club, with mishits dropped. Until you have 3 shots it's 95% of your max,
   and before that it's an average golfer's distance. If a shorter club reaches only with your best
   swing, the app suggests it as an alternative.
5. **Recording shots**: tap *Record a shot*, pick the club, walk to the ball, tap *Ball is here*.
   The GPS distance is saved to that club's history. You can also type in shots or your max per club.

Maps: OpenStreetMap streets, or Esri World Imagery satellite tiles (free with attribution for
personal/development use; a published app should register for a free ArcGIS key).

## Project layout

| Module | What | Builds without Android SDK |
|---|---|---|
| `core/` | Pure Kotlin: geo math, OSM parsing, hole detection, hazards, club stats and recommender, position simulator, desktop CLI | yes (`-PcoreOnly`) |
| `app/` | Android app: Jetpack Compose UI, osmdroid map, GPS, storage | needs Android SDK |

`core` uses no Android or Java-only APIs, so it can become a Kotlin Multiplatform module and be
shared with the iPhone app.

## Build and run on Android

Open the folder in **Android Studio** (Ladybug or newer), let it sync, and run the `app` configuration on
a phone or emulator. Or from the command line, with the Android SDK installed:

```sh
./gradlew :app:installDebug
```

## Testing with a fake position

- **In the app**: turn on **Simulate GPS**. Tap anywhere on the map to stand there, use **Tee** to jump
  to the current hole's tee, and **+25 yd** to walk toward the green. Use ◀ ▶ to change holes.
- **Android emulator**: leave Simulate off. In *Extended controls (⋯) > Location*, set a point or play
  a GPX/KML route, or run `adb emu geo fix <lon> <lat>` (longitude comes first).
- **Real device**: pick a mock-location app under *Developer options > Select mock location app*.
- **Desktop, no phone**: download a real course and run the caddie against fake positions:

  ```sh
  tools/fetch_course.sh 56.3433 -2.8030 2500 my_course.json          # St Andrews: lat lon radius_m output
  ./gradlew -PcoreOnly :core:run --args="my_course.json"                 # walk every hole
  ./gradlew -PcoreOnly :core:run --args="my_course.json 56.3440 -2.8060"  # stand at a lat/lon
  ```

- **Unit tests**: `./gradlew -PcoreOnly :core:test` plays a synthetic 3-hole course
  (`core/src/test/resources/synthetic_course.json`) from fake positions.

## Limitations

- Coverage depends on OpenStreetMap. Many courses are mapped in detail, some only as an outline with no
  holes. Anyone can add holes at openstreetmap.org, and the app reports when a course has none.
- Distances are straight-line. Elevation, wind and lie aren't factored in yet.
- Pin positions are rarely mapped, so the target is usually the center of the green.
