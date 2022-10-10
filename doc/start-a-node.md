# Start a node

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
```

The node can then be started using `pmc.sh node start --node-config <file>`. Use the `--debug` flag to enable the debug api which gives you an overview of the chains that are running on the node.
To start a blockchain immediately, supply its blockchain configuration using `--blockchain-config <file>`.
If the node is not the first one in the network, you must also supply parameters `--genesis-pubkey` and `--generis-peer` pointing to a node in the network.

For a full list of available flags, use `--help`

## Docker

To start postchain using docker, set the database url to `jdbc:postgresql://host.docker.internal:5432/postchain` and set `--docker` flag.
Additionally, you can set the name, image (if other than default) and mount volumes.
If the genesis node is also run using the same docker daemon, use `--genesis-peer host.docker.internal:<port>` since they will both be part of dockers internal network.

## Native process

Using the `--native` flag, postchain will start as a native process. Supply the path to the postchain binary (postchain.sh) using `--postchain-path` or environment variable `POSTCHAIN_PATH`. 
