#!/bin/bash

set -eu

D=$(dirname "${BASH_SOURCE[0]}")
bash "$POSTCHAIN_DIR"/multigen.sh --source-dir "$D/src" --output-dir "$D/out" "$D/config/run.xml"
