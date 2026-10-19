#!/usr/bin/env python3
"""
Downloads a golf course from the main OpenStreetMap API (not Overpass) and writes it in
Overpass `out geom` JSON, the format the app and the core CLI read.

Useful when Overpass servers are down or block your network. The OSM API is meant for
editing, so use this for testing a handful of courses, not from the app.

    tools/osm_api_fetch.py "Royal Canberra Golf Club" out.json
    tools/osm_api_fetch.py --bbox 149.08,-35.31,149.11,-35.29 out.json
"""
import json
import sys
import time
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET

UA = "GolfCaddie-dev/0.1 (testing)"
PAD_DEG = 0.002  # ~200 m around the course


def http_get(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=120) as r:
        return r.read()


def find_course(name):
    """Nominatim lookup; returns (display_name, [min_lon, min_lat, max_lon, max_lat])."""
    q = urllib.parse.urlencode({"q": name, "format": "jsonv2", "limit": 5})
    results = json.loads(http_get("https://nominatim.openstreetmap.org/search?" + q))
    time.sleep(1)  # Nominatim policy: max 1 request per second
    if not results:
        sys.exit(f"Nominatim found nothing for {name!r}")
    golf = [r for r in results if r.get("type") == "golf_course"] or results
    r = golf[0]
    s, n, w, e = map(float, r["boundingbox"])
    return r["display_name"], [w, s, e, n]


def wanted(tags):
    return ("golf" in tags or tags.get("leisure") == "golf_course"
            or tags.get("natural") in ("water", "wood") or tags.get("landuse") == "forest")


def to_overpass(osm_xml):
    root = ET.fromstring(osm_xml)
    nodes, ways, out = {}, {}, []

    def tags_of(el):
        return {t.get("k"): t.get("v") for t in el.findall("tag")}

    for n in root.findall("node"):
        nodes[n.get("id")] = {"lat": float(n.get("lat")), "lon": float(n.get("lon"))}
        t = tags_of(n)
        if t.get("golf") == "pin":
            out.append({"type": "node", "id": int(n.get("id")), **nodes[n.get("id")], "tags": t})
    for w in root.findall("way"):
        geom = [nodes[nd.get("ref")] for nd in w.findall("nd") if nd.get("ref") in nodes]
        ways[w.get("id")] = geom
        t = tags_of(w)
        if wanted(t):
            out.append({"type": "way", "id": int(w.get("id")), "tags": t, "geometry": geom})
    for r in root.findall("relation"):
        t = tags_of(r)
        if not wanted(t):
            continue
        members = [
            {"type": "way", "ref": int(m.get("ref")), "role": m.get("role"), "geometry": ways[m.get("ref")]}
            for m in r.findall("member")
            if m.get("type") == "way" and m.get("ref") in ways
        ]
        out.append({"type": "relation", "id": int(r.get("id")), "tags": t, "members": members})
    return {"version": 0.6, "generator": "osm_api_fetch.py (OSM API 0.6)", "elements": out}


def main():
    args = sys.argv[1:]
    if len(args) == 3 and args[0] == "--bbox":
        bbox, label, out_path = [float(v) for v in args[1].split(",")], args[1], args[2]
    elif len(args) == 2:
        label, bbox = find_course(args[0])
        out_path = args[1]
    else:
        sys.exit(__doc__)
    w, s, e, n = bbox
    bbox = [w - PAD_DEG, s - PAD_DEG, e + PAD_DEG, n + PAD_DEG]
    url = "https://api.openstreetmap.org/api/0.6/map?bbox=" + ",".join(f"{v:.6f}" for v in bbox)
    data = to_overpass(http_get(url))
    with open(out_path, "w") as f:
        json.dump(data, f)
    print(f"{label}\n  -> {out_path}: {len(data['elements'])} golf features")


if __name__ == "__main__":
    main()
