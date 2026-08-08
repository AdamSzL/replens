#!/usr/bin/env bash
#
# Strips glyphs we never render from the font files before they go in the APK.
#
# Google Fonts ships the full character set — Latin, Cyrillic, Vietnamese — at
# ~325 KB per weight. We render Latin and Polish, which is ~102 KB. Re-run this
# after adding a weight, a family, or a language.
#
#   ./tools/subset-fonts.sh ~/Downloads/Montserrat/static
#
set -euo pipefail

SOURCE="${1:?usage: subset-fonts.sh <dir-with-ttf>}"
OUTPUT="core/designsystem/src/main/res/font"

# Basic Latin + Latin-1 Supplement (accents, and the × in the zoom control)
# + Latin Extended-A (Polish: ą ć ę ł ń ó ś ź ż)
# + General Punctuation (en dash, curly quotes, ellipsis)
UNICODES="U+0020-007F,U+00A0-00FF,U+0100-017F,U+2000-206F"

# Keep every layout feature: 'tnum' gives the rep counter tabular figures, so it
# stops reflowing as it counts past 9. Dropping features saves little and costs that.
FEATURES='*'

mkdir -p "$OUTPUT"
for weight in Regular SemiBold; do
    target="$OUTPUT/montserrat_$(echo "$weight" | tr '[:upper:]' '[:lower:]').ttf"
    pyftsubset "$SOURCE/Montserrat-$weight.ttf" \
        --unicodes="$UNICODES" \
        --layout-features="$FEATURES" \
        --output-file="$target"
    printf "%-40s %5s KB\n" "$(basename "$target")" "$(( $(stat -f%z "$target") / 1024 ))"
done
