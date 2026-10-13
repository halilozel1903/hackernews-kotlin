#!/usr/bin/env bash
# Captures README screenshots of the sample app on a running emulator.
# The sample opens each screen from the `scene` extra and loads it live from the official Hacker News API
# (hacker-news.firebaseio.com): real stories, real comments, real profiles. There is no fixture or fallback data in
# the app; each scene logs SCENE_READY once its data has loaded, or SCENE_FAILED when a request fails, and a failed or
# unfinished scene fails the run. Every capture is also checked for the expected text and for a blank image.
#
#   bash scripts/screenshots.sh tablet   # pixel_tablet in landscape: Top stories and a discussion side by side, light and dark
#   bash scripts/screenshots.sh phone    # pixel_7: Top stories, a discussion, Ask HN and pg's profile, light and dark
set -euo pipefail
source "$(dirname "$0")/screenshot-lib.sh"

device="${1:-phone}"

# The text each scene must show; the capture fails without it. None of these appear on the loading or error screens.
expected_text() {
  case "$1" in
    top) echo "points" ;;
    story) echo "Discussion" ;;
    ask) echo "points" ;;
    user) echo "karma" ;;
    tablet) echo "Discussion" ;;
  esac
}

suffix() {
  if [ "$1" = dark ]; then echo "-dark"; else echo ""; fi
}

install_sample
if [ "$device" = tablet ]; then
  ensure_landscape
  for mode in light dark; do
    set_night_mode "$mode"
    fresh_launch --es scene tablet
    wait_for_scene tablet
    capture "tablet-reader$(suffix "$mode")" "$(expected_text tablet)"
  done
else
  for mode in light dark; do
    set_night_mode "$mode"
    for scene in top story ask user; do
      fresh_launch --es scene "$scene"
      wait_for_scene "$scene"
      capture "phone-$scene$(suffix "$mode")" "$(expected_text "$scene")"
    done
  done
fi
