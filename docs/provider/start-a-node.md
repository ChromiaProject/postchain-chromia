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
# Infrastructure to use. Can be  net.postchain.managed.Chromia0InfrastructureFactory if all blockchains should be in the same process
infrastructure=net.postchain.managed.Chromia0MasterInfrastructureFactory

# Storage
database.username=<postgres-user>
database.password=<postgres-pw>
database.schema=<postgres-schema>
# Url to database. the host must point in respect to the nodes network. 
# If node is run as a docker container, and the db is also on docker, then this would be the internal docker host.
database.url=jdbc:postgresql://localhost:5432/<db-name>

# Node information to connect to an existing network
genesis.pubkey=0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f56
genesis.host=231.123.12.2
genesis.port=9870

# Container
container.testmode=false
# Path to image used by subnode containers
container.docker-image=registry.gitlab.com/chromaway/postchain-chromia/chromaway/chromia-subnode:3.7.0
# Mount path to a directory on the host that can be used to store configurations. Note that we don't want to use /tmp since this folder will be cleaned when a container is stopped
container.host-mount-dir=/var/lib/subnode
# Hostname of the master host as seen by a subnode. If master is on docker, then the subnode will perceive the host as the internal docker host
# 172.17.0.1 on linux/Windows. Can be localhost if master node is a native java process
container.master-host=host.docker.internal
# Port used by the master node to establish connections with subnodes
container.master-port=9880
# Hostname of subnodes as seen by the master host. See above
container.subnode-host=host.docker.internal
# Subnodes will spawn and host its own database, use this if you want all subnodes to use another external database
container.subnode-database-url=jdbc:postgresql://localhost:5432/postchain
```

## Docker

When starting a node using docker you must expose a few ports and add some mount points. Folders containing node-configuration, blockchain configuration and the subnode mount path must be mounted and the docker socket must be a volume. The subnode mount path must have write access and the others can be readonly. Furthermore the messaging port, the api port and the subnode port must be exposed. 
Example:
```shell
docker run -it -d --name postchain \
    --volume /var/run/docker.sock:/var/run/docker.sock \
    --mount type=bind,source=/var/lib/subnode,target=/var/lib/subnode \
    --mount type=bind,source="$(pwd)/config",target=/config,readonly \
    --mount type=bind,source="$(pwd)/build",target=/build,readonly \
    -e POSTCHAIN_DEBUG=true \
    -e POSTCHAIN_CONFIG=/config/node-config.properties \
    -e POSTCHAIN_BLOCKCHAIN_CONFIG=/build/bc-config.xml \
    -p 9870:9870/tcp \
    -p 7740:7740/tcp -p 9880:9880/tcp \
    registry.gitlab.com/chromaway/postchain-chromia/chromaway/chromia-server:3.7.0 \
    run-node
```

## Native background process

The node can be started as a background process using for example `screen`

```shell
$ screen -S n0
# Ctrl+a, d  (means detach)
# screen -r n0  (means reattach)

$ postchain.sh run-node -nc config/node-config.properties --blockchain-config build/bc-config.xml --debug
```
