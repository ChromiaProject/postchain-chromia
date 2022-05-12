#!/bin/bash

set -eu

D=$(dirname "${BASH_SOURCE[0]}")
DAPP_SRC=../../../enterprise/chromia0/target/chromia0-3.4.2-SNAPSHOT-sources/chromia0/rell
bash "$POSTCHAIN_DIR"/multigen.sh --source-dir "$DAPP_SRC" --output-dir out ../config/run.xml
