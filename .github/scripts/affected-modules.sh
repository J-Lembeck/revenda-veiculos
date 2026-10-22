#!/bin/bash
set -euo pipefail

modules=(vehicles-service customers-service sales-service payment-gateway-mock)
shared='^(common/|pom\.xml$|\.mvn/|mvnw|\.github/)'

all=false
changed=""
if [[ "${EVENT:-}" != "pull_request" ]]; then
    all=true
else
    changed=$(git diff --name-only HEAD^1 HEAD)
    if grep -qE "$shared" <<<"$changed"; then
        all=true
    fi
fi

selected=()
for module in "${modules[@]}"; do
    if [[ "$all" == true ]] || grep -q "^$module/" <<<"$changed"; then
        selected+=("\"$module\"")
    fi
done

echo "modules=[$(IFS=,; echo "${selected[*]}")]"
