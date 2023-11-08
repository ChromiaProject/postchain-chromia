# Start a node

Before you start a node, postgres must be installed. See official [postgres](https://www.postgresql.org/download/) 
documentation or start a postgres instance using Docker:

```shell
docker run --name postgres -e POSTGRES_INITDB_ARGS="--lc-collate=C.UTF-8 --lc-ctype=C.UTF-8 --encoding=UTF-8" -e POSTGRES_PASSWORD=<postgres-user> -e POSTGRES_USER=<postgres-pw> -p 5432:5432 -d postgres:14.9
```

A node running Chromia can be started as a Docker container or as a native process. A node configuration file is 
needed. See this sample file:

```properties
# Node configuration
messaging.privkey=3132333435363738393031323334353637383930313233343536373839303131
messaging.pubkey=0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57
messaging.port=9870
api.port=7740

# Postchain configuration
configuration.provider.node=managed
# Infrastructure to use. Can be  net.postchain.d1.D1InfrastructureFactory if all blockchains should be in the same process
infrastructure=net.postchain.d1.D1MasterInfrastructureFactory

# Storage
database.username=<postgres-user>
database.password=<postgres-pw>
database.schema=<postgres-schema>
# Url to database. the host must point in respect to the nodes network. 
# If node is run as a Docker container, and the db is also on Docker, then this would be the internal Docker host.
database.url=jdbc:postgresql://localhost:5432/<db-name>

# Node information to connect to an existing network
genesis.pubkey=0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f56
genesis.host=231.123.12.2
genesis.port=9870

# Container
# Path to image used by subnode containers
container.docker-image=registry.gitlab.com/chromaway/postchain-chromia/chromaway/chromia-subnode:3.7.0
# Mount path to a directory on the host that can be used to store configurations. Note that we don't want to use /tmp since this folder will be cleaned when a container is stopped
container.host-mount-dir=/var/lib/chromaway/postchain/subnode
# The host device that the `container.host-mount-dir` is located on. This is used to enforce disk I/O limits.
# Note that this should only be the name of the device, not the partition.
# For Mac you may use `/dev/vda` to enforce limits
container.host-mount-device=/dev/sda
# Hostname of the master host as seen by a subnode. If master is on Docker, then the subnode will perceive the host as the internal Docker host
# 172.17.0.1 on linux/Windows. Can be localhost if master node is a native java process
container.master-host=host.docker.internal
# Port used by the master node to establish connections with subnodes
container.master-port=9880
# Hostname of subnodes as seen by the master host. See above
container.subnode-host=host.docker.internal
# Subnodes will spawn and host its own database, use this if you want all subnodes to use another external database
container.subnode-database-url=jdbc:postgresql://localhost:5432/postchain
# You can set custom Docker labels on the subnode containers 
container.label.XXX1=YYY1
container.label.XXX2=YYY2
```

## Docker

When starting a node using Docker you must expose a few ports and add some mount points. Folders containing 
node-configuration, blockchain configuration and the subnode mount path must be mounted and the Docker socket must be a 
volume. The subnode mount path must have write access and the others can be readonly. Furthermore, the messaging port, 
the api port and the subnode port must be exposed. The container will run as the current user/group, and subnode containers 
will be run as the same user/group. It needs the `docker` group to be able to talk to the Docker daemon.

You should also ensure that your machine does not run out of memory. Consider how much dedicated memory you have 
left after subtracting the memory that is dedicated to dapp-containers. Also consider the memory consumption of 
postgres (and any other applications you may have running on your machine). You can limit memory usage by setting 
JVM flags via `JAVA_TOOL_OPTIONS` environment variables.

Example:
```shell
docker run -d --name postchain \
    --restart unless-stopped \
    --user $(id -u):$(id -g) \
    --group-add $(cut -d: -f3 < <(getent group docker)) \
    --volume /var/run/docker.sock:/var/run/docker.sock \
    --mount type=bind,source="/etc/passwd",target=/etc/passwd,readonly \
    --mount type=bind,source=/var/lib/chromaway/postchain/subnode,target=/var/lib/chromaway/postchain/subnode \
    --mount type=bind,source="$(pwd)/config",target=/config,readonly \
    --mount type=bind,source="$(pwd)/build",target=/build,readonly \
    -e JAVA_TOOL_OPTIONS="-Xmx2g" \
    -e POSTCHAIN_DEBUG=true \
    -e POSTCHAIN_CONFIG=/config/node-config.properties \
    -e POSTCHAIN_BLOCKCHAIN_CONFIG=/build/bc-config.xml \
    -p 9870:9870/tcp \
    -p 7740:7740/tcp \
    -p 9880:9880/tcp \
    registry.gitlab.com/chromaway/postchain-chromia/chromaway/chromia-server:3.7.0 \
    run-node
```

## Native background process

The node can be started as a background process using for example `screen`. You can add JVM flags by setting 
environment variable `JAVA_TOOL_OPTIONS`. Ensure that the process is restarted on crash.

```shell
$ screen -S n0
# Ctrl+a, d  (means detach)
# screen -r n0  (means reattach)

$ export JAVA_TOOL_OPTIONS="-Xmx2g"
$ postchain.sh run-node -nc config/node-config.properties --blockchain-config build/bc-config.xml --debug
```

## Subnode disk quotas

Subnode disk quotas can be enforced with either ext4 or ZFS.

### ext4

Ext4 disk quotas can be used with a native master node, or a master node running in a Docker container with 
`chromaway/chromia-server` image started with `--privileged` and run as root. To enable this:

Create an ext4 file system with project quotas enabled and mount it with project quota enabled:

```shell
mkfs.ext4 -v -L postchain -O quota -E quotatype=prjquota /dev/...
mount -o prjquota /dev/... /mnt/chromaway/postchain
```

Add the following properties to the node configuration file:

```properties
container.filesystem=ext4
container.host-mount-dir=/mnt/chromaway/postchain
```

Start master node container:

```shell
docker run -d --name postchain \
    --privileged \
    --restart unless-stopped \
    --volume /var/run/docker.sock:/var/run/docker.sock \
    --mount type=bind,source=/mnt/chromaway/postchain,target=/mnt/chromaway/postchain \
    --mount type=bind,source="$(pwd)/config",target=/config,readonly \
    --mount type=bind,source="$(pwd)/build",target=/build,readonly \
    -e JAVA_TOOL_OPTIONS="-Xmx2g" \
    -e POSTCHAIN_DEBUG=true \
    -e POSTCHAIN_CONFIG=/config/node-config.properties \
    -e POSTCHAIN_BLOCKCHAIN_CONFIG=/build/bc-config.xml \
    -e POSTCHAIN_SUBNODE_USER=$(id -u):$(id -g) \    
    -p 9870:9870/tcp \
    -p 7740:7740/tcp \
    -p 9880:9880/tcp \
    registry.gitlab.com/chromaway/postchain-chromia/chromaway/chromia-server:3.7.2 \
    run-node
```

If running master node natively, it needs to be run as root and the quota tool `setquota` needs to be installed. 
It can be found in the package `quota` in Debian and Ubuntu.

Subnode containers need to run as a non-root user, configured with node configuration property `container.subnode-user` 
or environment variable `POSTCHAIN_SUBNODE_USER`. The value should be `<user-id>:<group-id>`, numerical user and group 
ids need to be used.

### ZFS

ZFS disk quotas requires a native master node. To enable this:

Create ZFS pool named `psvol`:

```shell
zpool create psvol /dev/...
```

Add the following properties to the node configuration file:

```properties
container.filesystem=zfs
container.zfs.pool-name=psvol
```

In this case `container.host-mount-dir` (and `container.master-mount-dir` if present) will be ignored and all 
container's files will be located in `/${zfs_pool_name}/${container_name}`.

## Logging

Logging is done using [Apache Log4j 2](https://logging.apache.org/log4j/2.x/).      

### Native process

The distribution package contains a default log configuration file `chromia-node/config/log4j2.yml` suitable for a native process. 
It logs both to standard output and to `logs/postchain.log` file. The log file is rotated, compressed and old files are 
deleted when the total size exceeds 100 MiB.

See [configuration with YAML](https://logging.apache.org/log4j/2.x/manual/configuration.html#configuration-with-yaml) on 
how to configure Log4j, and specifically [RollingFileAppender](https://logging.apache.org/log4j/2.x/manual/appenders.html#RollingFileAppender) 
on how to configure log file rotation. A different log configuration file can be used by setting the environment variable 
`LOG4J_CONFIGURATION_FILE`.

### Docker container

The `chromia-server` Docker image contain default log configuration file `/opt/chromaway/postchain/log/log4j2.yml` 
suitable for Docker container. It logs to standard output only, and we recommend letting Docker handle log file 
management or forwarding to a log aggregator. Logging can be customized by mounting a different log configuration file 
into the container over `/opt/chromaway/postchain/log/log4j2.yml`. You can also set the environment variable 
`LOG4J_CONFIGURATION_FILE` to a different location (in the container).

Docker supports different kinds of log drivers that can be used when collecting logs from a running container. The
different log variants are controlled through which log driver is configured to be used. For more information about
available log drivers see the official [logging drivers](https://docs.docker.com/config/containers/logging/configure/)
documentation.

To start a node with a specific log driver add `log-driver` and optionally `log-opt` to the Docker run command when
starting the node.

Example:

```shell
docker run -d --name postchain \
    ...
    --log-driver=fluentd
    --log-opt fluentd-address=fluentdhost:24224
    --log-opt fluentd-async=true
    ...
```

### Subnodes

Since subnodes are started automatically, the log driver and log options has to be added to the node configuration file.

Example:

```properties
container.docker-log-driver=fluentd
container.docker-log-opts=fluentd-address=fluentdhost:24224;fluentd-async=true
```
