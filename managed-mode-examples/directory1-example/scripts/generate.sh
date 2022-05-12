#!/bin/bash

set -eu

D=$(dirname "${BASH_SOURCE[0]}")
bash "$POSTCHAIN_DIR"/multigen.sh --source-dir "$D/directory1/rell" --output-dir out ../config/run.xml
