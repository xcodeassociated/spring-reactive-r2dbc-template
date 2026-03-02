#!/bin/bash

set -euo pipefail

DIRS=(
  "kafka+keycloak/volumes/postgres"
  "kafka+keycloak/volumes/keycloak"
  "kafka+keycloak/volumes/kafka-broker"
  "postgres/volumes/postgres"
)

for dir in "${DIRS[@]}"; do
  if [ -d "$dir" ]; then
    echo "Directory exists: $dir"
  else
    echo "Directory does not exist, creating: $dir"
    mkdir -p "$dir"
  fi
done

echo "done"