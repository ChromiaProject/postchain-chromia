#!/bin/bash

set -eu

D=$(dirname "${BASH_SOURCE[0]}")
bash "$D/../pmc/pmc.sh" "$@"
