#!/usr/bin/env bash
#
# Extracts every frame of each recorded clip, scaled to the analysis stream's
# long edge, ready to push to a device for fixture generation.
#
# Decoded here rather than on the device because Android's MediaMetadataRetriever
# returns only near-keyframe frames for this HEVC footage — about 4 fps out of 30,
# by either addressing mode. ffmpeg gets every frame, and the intermediate images
# make the exact input to ML Kit inspectable.
#
#   ./tools/extract-fixture-frames.sh ~/replens-recordings /tmp/fixture-frames
#
set -euo pipefail

SOURCE="${1:?usage: extract-fixture-frames.sh <clip-dir> <output-dir>}"
OUTPUT="${2:?usage: extract-fixture-frames.sh <clip-dir> <output-dir>}"

# Matches ANALYSIS_LONG_EDGE in FixtureGenerator: a body then occupies the pixel
# height the app actually sees.
LONG_EDGE=640
# The generator derives timestamps from the frame index, so a clip at another rate
# would be silently mistimed. Caught here, where ffprobe can check it.
EXPECTED_FPS=30

mkdir -p "$OUTPUT"
for clip in "$SOURCE"/*.mp4; do
    name=$(basename "$clip" .mp4)
    fps=$(ffprobe -v error -select_streams v:0 \
        -show_entries stream=avg_frame_rate -of csv=p=0 "$clip" |
        awk -F/ '{printf "%.0f", $1/$2}')
    if [ "$fps" != "$EXPECTED_FPS" ]; then
        echo "$name: ${fps} fps, expected ${EXPECTED_FPS} — re-record or fix the generator" >&2
        exit 1
    fi
    rm -f "${OUTPUT:?}/${name}__"*.jpg
    # Flat, not one directory per clip: adb creates pushed directories owned by
    # shell with no access for others, so the app can list its own files/ but
    # cannot traverse into anything adb made. Files land readable; directories
    # don't.
    #
    # passthrough, not the default: some clips declare a 120 fps container rate
    # while holding 30 fps of real frames, and ffmpeg would duplicate each one
    # four times to fill it — silently quadrupling the rows and the timeline.
    ffmpeg -y -v error -i "$clip" -fps_mode passthrough \
        -vf "scale=-2:$LONG_EDGE" -q:v 2 "$OUTPUT/${name}__%05d.jpg"
    echo "$name: $(find "$OUTPUT" -name "${name}__*.jpg" | wc -l | tr -d ' ') frames"
done
