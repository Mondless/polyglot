#!/usr/bin/env sh
set -eu
if ! command -v gradle >/dev/null 2>&1; then
  echo "Gradle 9.6.0 is not installed or is not on PATH." >&2
  exit 1
fi
exec gradle build
