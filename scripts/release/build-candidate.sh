#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
image=${1:?Usage: build-candidate.sh registry/image:unique-version}
[[ "$image" == ratanaksaren/smartphset-api:* && "$image" != *:latest ]] || { echo 'Use the existing repository and a unique version tag.' >&2; exit 1; }
if docker image inspect "$image" >/dev/null 2>&1; then
  echo 'Tag already exists locally. Choose a new unique version; do not overwrite a release.' >&2
  exit 1
fi
./gradlew --no-daemon test build
# Existing Dockerfile rebuilds bootJar in its Java 25 builder; it does not copy the host JAR.
docker build --platform linux/amd64 --tag "$image" .
docker image inspect --format '{{.Id}} {{.Architecture}} {{.Created}}' "$image"
printf 'Candidate built locally only: %s\n' "$image"
