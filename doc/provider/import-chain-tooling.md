# Import Chain Tooling

## Manual mode example

1. Suppose we have a node `postchain_manual0` running a chain in a manual mode:
```shell
docker run --name postchain_manual0 \
	--mount type=bind,source="$(pwd)"/config,target=/opt/chromaway/postchain/config,readonly \
	--mount type=bind,source="$(pwd)"/dapp/build,target=/opt/chromaway/postchain/dapp/build,readonly \
	-e POSTCHAIN_DB_SCHEMA=postchain_manual0 \
	registry.gitlab.com/chromaway/postchain-chromia/chromaway/chromia-server \
	run-node \
	-nc ./config/node-config.properties \
	-bc ./dapp/build/0.xml \
	-cid 0
```

2. Add a few blockchain configs:
```shell
docker run --rm \
	--mount type=bind,source="$(pwd)"/config,target=/opt/chromaway/postchain/config,readonly \
	--mount type=bind,source="$(pwd)"/dapp/build,target=/opt/chromaway/postchain/dapp/build,readonly \
	registry.gitlab.com/chromaway/postchain-chromia/chromaway/chromia-server \
	node blockchain add-configuration \
	-nc ./config/node-config.properties \
	-bc ./dapp/build/1.xml \
	-fh 10 \
	-cid 0
```

3. Export blockchain to 'export' dir:
```shell
docker run --rm \
	--mount type=bind,source="$(pwd)"/config,target=/opt/chromaway/postchain/config,readonly \
	--mount type=bind,source="$(pwd)"/export,target=/opt/chromaway/postchain/export \
	registry.gitlab.com/chromaway/postchain-chromia/chromaway/chromia-server \
	node blockchain export \
	-nc ./config/node-config.properties \
	--configurations-file ./export/dapp.configs \
	--blocks-file ./export/dapp.blocks \
	--up-to-height 1000 \
	-cid 0 \
	--overwrite
```

4. Import blockchain from 'export' dir to the new node `postchain_manual1` with chain-id 10:
```shell
docker run --rm \
	--mount type=bind,source="$(pwd)"/config,target=/opt/chromaway/postchain/config,readonly \
	--mount type=bind,source="$(pwd)"/export,target=/opt/chromaway/postchain/export,readonly \
	-e POSTCHAIN_DB_SCHEMA=postchain_manual1 \
	registry.gitlab.com/chromaway/postchain-chromia/chromaway/chromia-server \
	node blockchain import \
	-nc ./config/node-config.properties \
	--configurations-file ./export/dapp.configs \
	--blocks-file ./export/dapp.blocks \
	-cid 10
```

5. Start a new node `postchain_manual1` with chain-id 10:
```shell
docker run --name postchain_manual1 \
	--mount type=bind,source="$(pwd)"/config,target=/opt/chromaway/postchain/config,readonly \
	--mount type=bind,source="$(pwd)"/dapp/build,target=/opt/chromaway/postchain/dapp/build,readonly \
	-e POSTCHAIN_DB_SCHEMA=postchain_manual1 \
	registry.gitlab.com/chromaway/postchain-chromia/chromaway/chromia-server \
	run-node \
	-nc ./config/node-config.properties \
	-cid 10
```

## Managed mode example

6. Suppose we have Directory1 testnet and we want to import blockchain to the container C1 of this network. Importing consists of three phases: 
   1. Importing of blockchain configurations. At this phase, a new blockchain in `PAUSED` state will be added to the container, and all its configurations will be loaded to the Directory1. Note that every configuration should be voted on separately. To simplify this step, one can (i) import blockchain to a cluster with a single provider voter set or (ii) update the `threshold` parameter of the cluster voter set to the value 1 (1 vote). Once the import is done, cluster governance can be strengthened by (i) adding more providers or (ii) setting the initial value of `threshold`.
   2. Importing of blocks to any node of the cluster.
   3. Finalizing import. At this phase, the blockchain's state will be changed from `PAUSED` to `RUNNING`.

(i) Import blockchain to the container C1:
```shell
pmc blockchain import --configurations-file ./export/dapp.configs -c C1 -n dapp1
```

(ii) Import blocks to the node:

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
	-cid 100 \
	--incremental
```

(iii). Finalize blockchain import:
```shell
pmc blockchain finish-import --configurations-file ./export/dapp.configs -brid 3EA1FB24DB9C77765D5B4F06B2163D99994AA894DBB907984152F6C65A8C2A5C
```

