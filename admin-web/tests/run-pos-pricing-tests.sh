#!/usr/bin/env bash
set -euo pipefail
test_directory="$(cd -- "$(dirname -- "$0")" && pwd)"
node --test "$test_directory/pos-pricing-regression.test.cjs" "$test_directory/credit-refund-regression.test.cjs"
