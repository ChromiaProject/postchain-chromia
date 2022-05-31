#!/bin/sh

# shellcheck disable=SC2155
export POSTCHAIN_CLIENT_BLOCKCHAIN_RID=$(cat "$RELL_OUT"/blockchains/0/brid.txt)
CONF_AS_BYTEARRAY=$(xxd "$RELL_OUT"/blockchains/0/0.gtv -p -c100000) # -p for hex and -c for bytes on each line (we want all)
./client.sh post-tx --await --config "$RELL_OUT/node-config.properties" propose_blockchain "$1" 'x"'$CONF_AS_BYTEARRAY'"' "$2"
