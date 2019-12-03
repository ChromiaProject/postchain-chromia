#!/bin/bash

set -eu

[[ -z ${DEBUG+x} ]] && POSTCHAIN_SH='./postchain.sh' || POSTCHAIN_SH="./postchain-debug.sh"
NODE_ID=$1
COMMAND=$2
BRID=$(cat bc-target/blockchains/0/brid.txt)

NODE_PORTS=()
NODE_PORTS[0]=9870
NODE_PORTS[1]=9871
NODE_PORTS[2]=9872

NODE_PUBKEYS=()
NODE_PUBKEYS[0]=0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57
NODE_PUBKEYS[1]=035676109c54b9a16d271abeb4954316a40a32bcce023ac14c8e26e958aa68fba9

run_cmd () {
    CMD=$1
    shift
    $POSTCHAIN_SH "$CMD" -nc config/config."$NODE_ID".properties "$@"
}

case $COMMAND in
    reset)
        run_cmd wipe-db
        run_cmd add-blockchain -brid "$BRID" -cid 0 -bc bc-target/blockchains/0/0.xml
        run_cmd peerinfo-add -h 127.0.0.1 -p ${NODE_PORTS[$NODE_ID]} -pk ${NODE_PUBKEYS[$NODE_ID]}
        [ "$NODE_ID" -ne 0 ] && run_cmd peerinfo-add -h 127.0.0.1 -p ${NODE_PORTS[0]} -pk ${NODE_PUBKEYS[0]}
        true
        ;;
    run)
        run_cmd run-node -cid 0
        ;;
esac

