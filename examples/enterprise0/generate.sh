#!/bin/bash

set -eu

bash "$POSTCHAIN_DIR"/multigen.sh --source-dir "../enterprise/enterprise0/target/enterprise0-3.4.2-SNAPSHOT-sources/enterprise0/rell" --output-dir out "$1"