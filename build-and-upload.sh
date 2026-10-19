#!/bin/bash
# Builds the phone and watch apps (debug builds, signed with this machine's debug key: both
# must share a key for the watch link to work) and puts them on nathanubuntuserver.
set -e
cd "$(dirname "$0")"

echo "Building phone and watch APKs..."
./gradlew :app:assembleDebug :wear:assembleDebug
cp app/build/outputs/apk/debug/app-debug.apk dist/GolfCaddie-phone.apk
cp wear/build/outputs/apk/debug/wear-debug.apk dist/GolfCaddie-watch.apk

# The server's name doesn't always resolve on the LAN; fall back to its address, checking it
# against the host key saved under its name.
HOST=nathanubuntuserver
getent hosts "$HOST" >/dev/null || HOST=192.168.0.181
SSH_OPTS=(-o HostKeyAlias=nathanubuntuserver)

echo "Uploading to $HOST..."
ssh "${SSH_OPTS[@]}" "nathan@$HOST" 'mkdir -p /home/nathan/projects/golf-caddie'
scp "${SSH_OPTS[@]}" dist/GolfCaddie-phone.apk dist/GolfCaddie-watch.apk "nathan@$HOST:/home/nathan/projects/golf-caddie/"

echo "Done."
echo "http://192.168.0.181:8080/files/home/nathan/projects/golf-caddie/GolfCaddie-phone.apk"
echo "http://192.168.0.181:8080/files/home/nathan/projects/golf-caddie/GolfCaddie-watch.apk"
