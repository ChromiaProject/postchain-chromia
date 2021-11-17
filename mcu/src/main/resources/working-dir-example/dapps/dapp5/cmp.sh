#!/bin/sh

# Compile a dapp

set -eu
. ./env.sh

# clear target dir
rm -rf $TARGET_DIR
mkdir $TARGET_DIR

# compiling a dapp
bash ../../docker/scripts/multigen.sh -d $SRC_DIR -o $TARGET_DIR $SRC_DIR/$MANIFEST
cat $TARGET_DIR/blockchains/0/brid.txt
printf '\n'

