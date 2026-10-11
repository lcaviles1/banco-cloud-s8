#!/bin/sh
set -eu
docker compose -f compose.yml -f compose.scale.yml up -d --scale accounts-service=2 --scale payments-service=2 --scale customers-service=2 --wait --wait-timeout 240
