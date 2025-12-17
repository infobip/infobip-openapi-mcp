#!/bin/bash

set -euo pipefail

args=(
  "-B"
  "release:prepare"
)
if [ -n "$RELEASE_VERSION" ]; then
  args+=("-DreleaseVersion=$RELEASE_VERSION")
fi
if [ -n "$DEVELOPMENT_VERSION" ]; then
  args+=("-DdevelopmentVersion=$DEVELOPMENT_VERSION")
fi
mvn "${args[@]}"
