## Import Chain Tooling – Manual mode example

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

### Importing managed chain

If the source chain is managed you can export it in the same way as in step 3 but configurations need to be fetched with
PMC instead. Run:

`pmc blockchain get-all-configurations --save` command with `--export-format` flag.

Supply the file that is outputted as `--configuration-file` in step 4.

> **Note:** Ensure that the chain you import is not relying on any functionality that is only supported in managed mode
> (e.g. ICMF and ICCF).
