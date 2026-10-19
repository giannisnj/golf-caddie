#!/usr/bin/env bash
# Builds the course pack the app ships for a region (app/src/main/assets/courses/<region>.json):
# every course with mapped holes within RADIUS meters of a point, downloaded from OpenStreetMap
# with the same query the app uses, plus ground heights from Open-Meteo.
#
#   tools/build_bundle.sh <region> <lat> <lon> <radius_m> [work_dir]
#   tools/build_bundle.sh canberra -35.30 149.12 30000
#
# The Overpass servers are often busy (502s, 40-second answers), so each download is retried
# and finished ones are kept in work_dir: if it stops part-way, run it again to carry on.
set -euo pipefail
REGION=${1:?region}; LAT=${2:?lat}; LON=${3:?lon}; R=${4:?radius_m}
WORK=${5:-build/bundle-$REGION}
ROOT=$(cd "$(dirname "$0")/.." && pwd)
mkdir -p "$WORK"; WORK=$(cd "$WORK" && pwd)
UA="GolfCaddie-dev/0.1 (course pack build)"
# overpass-api.de's two servers: when one keeps answering 502, the other often doesn't.
EPS=(https://overpass-api.de/api/interpreter https://z.overpass-api.de/api/interpreter https://lz4.overpass-api.de/api/interpreter)

# POSTs query $1 to Overpass into file $2, retrying until it gets a complete JSON answer.
overpass() {
  for try in 1 2 3 4 5 6 7 8; do
    ep=${EPS[$(( (try - 1) % ${#EPS[@]} ))]}
    code=$(curl -s -A "$UA" --max-time 200 -o "$2.tmp" -w "%{http_code}" --data-urlencode "data=$1" "$ep" || true)
    if [ "$code" = 200 ] && python3 -c "import json,sys; d=json.load(open(sys.argv[1])); assert 'remark' not in d" "$2.tmp" 2>/dev/null; then
      mv "$2.tmp" "$2"; return 0
    fi
    echo "  HTTP $code from ${ep#https://}, retrying in $((try * 15)) s" >&2; sleep $((try * 15))
  done
  echo "  gave up on $2" >&2; return 1
}

# 1. Which courses have holes: course outlines and hole lines, as centre points only.
if [ ! -s "$WORK/list.json" ]; then
  A="(around:$R,$LAT,$LON)"
  echo "Listing courses within $R m..."
  overpass "[out:json][timeout:90];(way[\"leisure\"=\"golf_course\"]$A;relation[\"leisure\"=\"golf_course\"]$A;way[\"golf\"=\"hole\"]$A;);out center tags;" "$WORK/list.json"
fi
COURSES=$(python3 - "$WORK/list.json" <<'EOF'
import json, math, sys
els = json.load(open(sys.argv[1]))["elements"]
holes = [e["center"] for e in els if e["tags"].get("golf") == "hole"]
for c in els:
    if c["tags"].get("leisure") != "golf_course": continue
    p = c["center"]
    near = sum(1 for h in holes if math.hypot((h["lat"] - p["lat"]) * 111000,
               (h["lon"] - p["lon"]) * 111000 * math.cos(math.radians(p["lat"]))) < 1500)
    if near: print(f'{c["type"]}_{c["id"]} {p["lat"]:.5f},{p["lon"]:.5f}')
EOF
)

# 2. Each course with the app's own query (Overpass.queryAround), one at a time.
FILES=()
while read -r id ll; do
  out="$WORK/$id.json"; FILES+=("$out")
  [ -s "$out" ] && continue
  echo "Downloading $id..."
  A="(around:2500,$ll)"
  overpass "[out:json][timeout:90];(way[\"leisure\"=\"golf_course\"]$A;relation[\"leisure\"=\"golf_course\"]$A;way[\"golf\"~\"^(hole|green|tee|fairway|bunker|water_hazard|lateral_water_hazard)\$\"]$A;relation[\"golf\"~\"^(green|bunker|water_hazard|lateral_water_hazard)\$\"]$A;node[\"golf\"=\"pin\"]$A;way[\"natural\"=\"water\"]$A;relation[\"natural\"=\"water\"][\"water\"!~\"^(river|canal|stream)\$\"]$A;way[\"natural\"=\"wood\"]$A;way[\"landuse\"=\"forest\"]$A;);out geom;" "$out"
  sleep 5
done <<< "$COURSES"

# 3. Parse, add heights, write the pack.
mkdir -p "$ROOT/app/src/main/assets/courses"
cd "$ROOT"
./gradlew -q -PcoreOnly :core:run --args="bundle app/src/main/assets/courses/$REGION.json $REGION $LAT $LON $R $(date -u +%F) ${FILES[*]}"
