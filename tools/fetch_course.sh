#!/usr/bin/env bash
# Downloads every golf feature within RADIUS meters of a point from OpenStreetMap
# (free Overpass API) into an Overpass JSON file the app and the CLI can read.
#
#   tools/fetch_course.sh <lat> <lon> [radius_m] [out.json]
#   tools/fetch_course.sh 56.3433 -2.8030 2500 st_andrews.json
set -euo pipefail
LAT=${1:?lat}; LON=${2:?lon}; R=${3:-2500}; OUT=${4:-course.json}
AROUND="(around:$R,$LAT,$LON)"
QUERY="[out:json][timeout:60];(way[\"leisure\"=\"golf_course\"]$AROUND;relation[\"leisure\"=\"golf_course\"]$AROUND;way[\"golf\"]$AROUND;node[\"golf\"=\"pin\"]$AROUND;way[\"natural\"=\"water\"]$AROUND;);out geom;"
curl -sS --fail -A "GolfCaddie-dev/0.1" https://overpass-api.de/api/interpreter --data-urlencode "data=$QUERY" -o "$OUT"
echo "Saved $OUT ($(wc -c < "$OUT") bytes)"
