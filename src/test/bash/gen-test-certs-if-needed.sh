#!/bin/bash

OUTPUT_DIR="$1"

if [ ! -f "$OUTPUT_DIR/redis.key" -o ! -f "$OUTPUT_DIR/redis.crt" -o ! -f "$OUTPUT_DIR/ca.crt" ]; then
  ./gen-test-certs.sh
fi
