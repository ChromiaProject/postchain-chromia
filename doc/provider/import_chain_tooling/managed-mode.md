## Import Chain Tooling – Managed mode example

1. Suppose we have Directory1 testnet and we want to import blockchain to the container C1 of this network. Importing consists of three phases: 
   1. Importing of blockchain configurations. At this phase, a new blockchain in `IMPORTING` state will be added to the container, and all its configurations will be loaded to the Directory1. Note that every configuration should be voted on separately. To simplify this step, one can (i) import blockchain to a container with a deployer voter set consisting of a single provider or (ii) update the `threshold` parameter of the container deployer voter set to the value 1 (1 vote). Once the import is done, cluster governance can be strengthened by (i) adding more providers or (ii) setting the initial value of `threshold`.
   2. Importing of blocks to any node of the cluster.
   3. Finalizing import. At this phase, the blockchain's state will be changed from `IMPORTING` to `RUNNING`.

(i) Import blockchain to the container C1:
```shell
pmc blockchain import --configurations-file ./export/dapp.configs -c C1 -n dapp1
pmc blockchains -i
export DAPP1=3EA1FB24DB9C77765D5B4F06B2163D99994AA894DBB907984152F6C65A8C2A5C
```

(ii) Import blocks to the node (see also point 2):

To do this find an internal IP address of the docker container (that contains `C1` as a second component, e.g. `0350fe40-C1-1`):
```shell
docker inspect 0350fe40-C1-1 | jq '.[0].NetworkSettings.Networks.bridge.IPAddress'
```

and use this IP address in `POSTCHAIN_DB_URL` environment variable. Also set `POSTCHAIN_DB_SCHEMA` to `devnet_psi0_C1`, where `devnet_psi0` is a master's DB schema. Run a blocks import:

```shell
docker run --rm \
    --mount type=bind,source="$(pwd)"/config,target=/opt/chromaway/postchain/config,readonly \
    --mount type=bind,source="$(pwd)"/export,target=/opt/chromaway/postchain/export,readonly \
    -e POSTCHAIN_DB_SCHEMA=devnet_psi0_c1 \
    -e POSTCHAIN_DB_URL=jdbc:postgresql://172.17.0.3:5432/postchain \
    registry.gitlab.com/chromaway/postchain-chromia/chromaway/chromia-server \
    node blockchain import \
    -nc ./config/config.master.0.properties \
    --configurations-file ./export/dapp.configs \
    --blocks-file ./export/dapp.blocks \
    -brid $DAPP1 \
    --incremental
```

(iii). Finalize blockchain import:
```shell
pmc blockchain finish-import --configurations-file ./export/dapp.configs -brid $DAPP1 --finish-at-height $H
```

where `$H` is the height of the last imported block. 

2. _Monitoring of syncing and anchoring._ Syncing and anchoring might take a long time for big blockchains. To skip the sync step, one can import blocks on _every_ node in parallel on step 1.ii. Anchoring can be sped up by adjusting `anchoring.max_blocks_per_chain` blockchain configuration parameter of the cluster anchoring chain, which equals 100 by default.  

```shell
# E.g., syncing on every cluster node
curl -X GET https://node0.devnet2.chromia.dev:7740/blockchain/$DAPP2/height
curl -X GET https://node1.devnet2.chromia.dev:7740/blockchain/$DAPP2/height
curl -X GET https://node2.devnet2.chromia.dev:7740/blockchain/$DAPP2/height

# anchoring, where CAC is a blockchain RID of the cluster anchoring chain
export CAC=33D4511A8CDD4AF0C0D30A36F0AF8FB40FDC50CF25F507865AE23E32DDFC8E10
docker run --rm \
    registry.gitlab.com/chromaway/core-tools/chromia-cli/chr:0.9.1 \
    query --api-url https://node0.devnet2.chromia.dev:7740/ \
    --blockchain-rid "$CAC" \
    get_last_anchored_block -- blockchain_rid="$DAPP1" \
    | grep -o 'block_height=[0-9]*'
```

