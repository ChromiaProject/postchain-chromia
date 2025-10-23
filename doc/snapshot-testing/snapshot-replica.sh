#!/bin/bash

set -e

if [ "$1" == "start-node" ]; then

  docker run --rm -it --name snapshot-replica \
    --volume /var/run/docker.sock:/var/run/docker.sock \
    --mount type=bind,source="$(pwd)/node_config",target=/config,readonly \
    -e JAVA_TOOL_OPTIONS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=${SUSPEND:-n},address=*:$((32900))" \
    -e POSTCHAIN_DEBUG=true \
    -e FORCE_SNAPSHOT=true \
    -p 9879:9870/tcp \
    -p 9889:9881/tcp \
    -p 7749:7740/tcp \
    -p 7759:7750/tcp \
    -p 50055:50051/tcp \
    -p 32900:32900/tcp \
    chromaway/chromia-server \
    run-server --node-config /config/node.properties

elif [ "$1" == "init" ]; then

  docker run --rm \
    --mount type=bind,source="$(pwd)"/system_chains/,target=/opt/chromaway/postchain/system_chains,readonly \
    registry.gitlab.com/chromaway/postchain-chromia/chromaway/chromia-server:3.34.2 \
    admin blockchain initialize -t 172.17.0.1:50055 -cid 0 -bc ./system_chains/dc.xml

elif [ "$1" == "replica-add" ] && [ -n "$2" ]; then

  docker run --rm \
    --mount type=bind,source="$(pwd)"/system_chains/,target=/opt/chromaway/postchain/system_chains,readonly \
    registry.gitlab.com/chromaway/postchain-chromia/chromaway/chromia-server:3.34.2 \
    admin replica add -t 172.17.0.1:50055 -brid "$3" --pubkey 0327F6EAE0B4A10B55051734179A0A5C5C3C4FA05E728594607E2B92096E29B405

else
  echo "Usage: $0 ..."
  echo "  start-node          Start a postchain server"
  echo "  init                Initialize DC chain and start replicate it"
  echo "  replica-add <brid>  Start replicate another chain"
fi
