#!/bin/bash

set -eu

if [ "$#" -ne 2 ]; then
    echo "-----------------------------"
    echo "Usage ./run.sh NODE_ID COMMAND"
    echo "-----------------------------"
    echo "For example:"
    echo "./run.sh 0 run"
    echo "./run.sh 0 reset"
    echo "-----------------------------"
    exit 2
fi

[[ -z ${DEBUG+x} ]] && POSTCHAIN_SH='./postchain.sh' || POSTCHAIN_SH="./postchain-debug.sh"
NODE_ID=$1
COMMAND=$2

NODE_PORTS=()
NODE_PORTS[0]=9870
NODE_PORTS[1]=9871
NODE_PORTS[2]=9872
NODE_PORTS[3]=9873

NODE_PUBKEYS=()
NODE_PUBKEYS[0]=0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57
NODE_PUBKEYS[1]=035676109c54b9a16d271abeb4954316a40a32bcce023ac14c8e26e958aa68fba9
NODE_PUBKEYS[2]=03f811d3e806e6d093a4bcce49c145ba78f9a4b2fbd167753ecab2a13530b081f8
NODE_PUBKEYS[3]=03ef3f5be98d499b048ba28b247036b611a1ced7fcf87c17c8b5ca3b3ce1ee23a4

run_cmd () {
    CMD=$1
    shift
    bash "$POSTCHAIN_DIR"/postchain.sh "$CMD" --debug -nc ../config/config."$NODE_ID".properties "$@"
}

case $COMMAND in
    reset)
        BRID=$(cat out/blockchains/0/brid.txt)
        run_cmd wipe-db
        run_cmd add-blockchain -cid 0 -bc out/blockchains/0/0.xml
        run_cmd peerinfo-add -h 127.0.0.1 -p ${NODE_PORTS[$NODE_ID]} -pk ${NODE_PUBKEYS[$NODE_ID]}
        [ "$NODE_ID" -ne 0 ] && run_cmd peerinfo-add -h 127.0.0.1 -p ${NODE_PORTS[0]} -pk ${NODE_PUBKEYS[0]}
        true
        ;;
    run)
        run_cmd run-node -cid 0
        ;;
esac

