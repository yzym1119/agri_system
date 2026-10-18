#!/usr/bin/env bash
set -euo pipefail
test_directory="$(cd -- "$(dirname -- "$0")" && pwd)"
node --test "$test_directory/pos-pricing-regression.test.cjs" "$test_directory/credit-refund-regression.test.cjs" "$test_directory/delivery-regression.test.cjs" "$test_directory/authority-regression.test.cjs" "$test_directory/pickup-regression.test.cjs" "$test_directory/receipt-regression.test.cjs" "$test_directory/permission-view-regression.test.cjs" "$test_directory/ui-pos-regression.test.cjs" "$test_directory/ui-reliability-regression.test.cjs" "$test_directory/ui-tables-regression.test.cjs"
