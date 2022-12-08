# Start a node

Before you start a node, postgres must be installed. See official [postgres](https://www.postgresql.org/download/) documentation or start a postgres instance using docker:

```shell
docker run --name postgres -e POSTGRES_PASSWORD=<postgres-user> -e POSTGRES_USER=<postgres-pw> -p 5432:5432 -d postgres
```

Using PMC, a node running Chromia can be started as a docker container or as a native process. A node configuration file is needed. See this sample file:

```properties
# Node configuration
messaging.privkey=3132333435363738393031323334353637383930313233343536373839303131
messaging.pubkey=0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57
messaging.port=9870
api.port=7740

# Postchain configuration
configuration.provider.node=managed
infrastructure=net.postchain.managed.Chromia0InfrastructureFactory

# Storage
database.driverclass=org.postgresql.Driver
database.username=<postgres-user>
database.password=<postgres-pw>
database.schema=<postgres-schema>
database.url=jdbc:postgresql://localhost:5432/<db-name>

# Node information to connect to an existing network
genesis.pubkey=0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f56
genesis.host=231.123.12.2
genesis.port=9870
```

The node can then be started using `pmc.sh node start --node-config <file>`. Use the `--debug` flag to enable the debug api which gives you an overview of the chains that are running on the node.
To start a blockchain immediately, supply its blockchain configuration using `--blockchain-config <file>`.
If the node is not the first one in the network, you must also supply genesis node configuration properties pointing to a node in the network. These can also be specified via the `--genesis-peer` options.

For a full list of available flags, use `--help`

## PMC convenience script

PMC includes a few convenience functions for starting postchain as docker container or as a native process

### Docker

To start postchain using docker, set the database url to `jdbc:postgresql://host.docker.internal:5432/postchain` and set `--docker` flag.
Additionally, you can set the name, image (if other than default) and mount volumes.

If the genesis node is also run using the same docker daemon, you can use `genesis.host=host.docker.internal` since they will both be part of dockers internal network.

> **NOTE:** `host.docker.internal` is typically use for Mac, for linux and Windows machines, the host 172.17.0.1 can be used to access the docker host

### Native process

Using the `--native` flag, postchain will start as a native process. Supply the path to the postchain binary (postchain.sh) using `--postchain-path` or environment variable `POSTCHAIN_PATH`. 

## Native background process

Add the peer information of a node in the network

```shell
$ postchain.sh peerinfo-add -nc config/config.0.properties -h <host> -p <port> -pk <pubkey>
```

Add the blockchain configuration of the manager chain to the database

```shell
$ postchain.sh add-blockchain -bc manager.xml -cid 0 -nc config/config.0.properties
```

Now the node can be started as a background process using for example `screen`

```shell
$ screen -S n0
# Ctrl+a, d  (means detach)
# screen -r n0  (means reattach)

$ postchain.sh run-node -cid 0 -nc conf0/node-config.properties
```
