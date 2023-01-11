## Setup

The default configuration consists of the following folder structure

```
config/node-config.properties
config/run.xml
src/
```

where `src` contains the rell source and run.xml configures the dapp.
For a minimum working example of this setup, see [example section](#example-node-configuration).

See [description of `node-config.properties` file](https://gitlab.com/chromaway/postchain/-/wikis/Postchain-Node-Configuration-Properties).

See [description of `run.xml` file](https://rell.chromia.com/en/master/runxml.html).

Deviations from the default setup can be configured by using environment variables.

### Environment

| Variable            | Default                                 | Description                                                                    |
| :------------------ | :-------------------------------------- | :----------------------------------------------------------------------------- |
| POSTCHAIN_DIR       | /opt/chromaway/postchain                | Base folder for all postchain-related scripts/jars used by this container      |
| POSTCHAIN_LOG4J2    | $POSTCHAIN_DIR/log/log4j2.xml           | File for configuring log4j                                                     |
| RELL_DIR            | /opt/chromaway/rell                     | Base path for rell-related files                                               |
| RELL_SRC            | $RELL_DIR/src                           | Path to rell source base directory                                             |
| RELL_OUT            | $RELL_DIR/out                           | Path to generated blockchain configurations                                    |
| CHAIN_CONF          | $RELL_DIR/config/run.xml                | File for blockchain definitions                                                |
| NODE_CONF           | $RELL_DIR/config/node-config.properties | File for node configuration                                                    |
| WIPE_DB             | false                                   | If the database should be wiped prior to startup                               |
| GENERATE_BLOCKCHAIN | true                                    | If the container should generate new blockchain configuration prior to startup |

### Database

This image will assume that you have a postgres database running, see [these instructions](https://gitlab.com/chromaway/postchain/-/blob/dev/postchain-devtools/README_postgres_setup.md) for how to set it up.
Specify the path to this using either `database.url=` in the node config or `POSTCHAIN_DB_URL` environment variable.
If you are using docker compose, the environment variable to set on the postchain service is
`POSTCHAIN_DB_URL: jdbc:postgresql://postgres/postchain`
An example of a docker-compose file can be found in the [example section](#example-docker-compose)

## Usage

A typical usage is to mount the rell source as a volume to `$RELL_SRC` and the configurations to `$RELL_DIR/config`.
If you follow the convention as depicted above, it is enough to mount the base folder to `$RELL_DIR`.

### Master node setup

If you want to run your master/sub infrastructure where individual chains are running on subnodes in containers there
are some additional considerations.

Master node needs a docker agent that can launch the subnode docker image and there are two main alternatives:

1. Mount the docker socket of the host machine. Typically `/var/run/docker.sock`.

2. Run a dind container and set `DOCKER_HOST` environment variable to point to your dind container. See: https://hub.docker.com/_/docker

NOTE: Master container needs to be able to mount configs to subnode container when it launches it.
It is important to understand that the mounting will be done from the file system of the docker agent host, i.e. not the file system of the master container.

In the first alternative, just mount an appropriate directory (must be the same directory path on host and master container)
on host machine and make sure that `RELL_OUT` points to it.

In the second alternative you need to mount a directory on both master and dind container
(mount paths must match between containers)

Regardless of approach you need to ensure that the following configs are also appropriately set:

- `container.host-mount-dir` # A dir in host filesystem where container volume will be created [1]
- `container.master-mount-dir` # A path to dir where container volume is placed in the master (container) filesystem. Master node uses it to create container node config file, blockchains dir, etc. [1]
- `container.mount-dir` # Directory containing config (correctly mounted, see above)
- `container.master-host` # Host address of the master node
- `container.master-port` # Port that subnode should connect to (must be exposed on master container)
- `container.subnode-host` # Host address of subnode container

[1]: If master node is launched natively or by means of fabric8 maven plugin or Testcontainers Lib or CI/CD, `master-mount-dir` has to be equal to `host-mount-dir` (`master-mount-dir` can be omitted in config). If master node is launched inside a container, i.e. in case of DinD, `master-mount-dir` might not be equal to `host-mount-dir` (see subnode Dockerfile for details).

### Example

#### Example node-configuration {#example-node-configuration}

Node configuration for a single running node for testing rell code locally

##### config/private.properties

```shell
messaging.privkey=3132333435363738393031323334353637383930313233343536373839303131
messaging.pubkey=0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57
```

##### config/node-config.properties

```shell
include=private.properties

configuration.provider.node=legacy
infrastructure=base/ebft

database.driverclass=org.postgresql.Driver
database.url=jdbc:postgresql://host.docker.internal:5432/postchain

database.username=postchain
database.password=postchain
database.schema=postchain_single_dapp_

api.port=7740

node.0.id=node1
node.0.host=127.0.0.1
node.0.port=9871
node.0.pubkey=0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57
```

##### config/run.xml

```xml
<run wipe-db="false">
    <nodes>
        <!-- node config relative to this file -->
        <config src="node-config.properties" add-signers="true" />
        <test-config src="node-config.properties" />
    </nodes>
    <chains>
        <chain name="example" iid="1">
            <config height="0">
                <app module=""></app>
            </config>
        </chain>
    </chains>
</run>
```

##### src/main.rell

```shell
query hello_world() {
  return "Hello World!";
}
```

#### Example docker-compose {#example-docker-compose}

##### docker-compose.yml

```yaml
version: "3.1"
services:
  postgres:
    image: postgres:14.1-alpine
    restart: always
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data
    environment:
      POSTGRES_DB: postchain
      POSTGRES_PASSWORD: postchain
      POSTGRES_USER: postchain

  postchain:
    image: registry.gitlab.com/chromaway/postchain-chromia/chromaway/postchain-test-dapp:3.8.0-SNAPSHOT
    ports:
      - "7740:7740"
    depends_on:
      - postgres
    volumes:
      - .:/opt/chromaway/rell
    environment:
      POSTCHAIN_DB_URL: jdbc:postgresql://postgres/postchain

volumes:
  pgdata:
```

Make sure that for postchain `volumes` field that points to your rell code is correctly mounted. So if you have a folder structure like

```shell
.
└── project-name/
    ├── rell/
    │   ├── config
    │   └── src/
    │       └── module-name/
    │           └── module.rell
    └── docker-compose.yml
```

Then the correct mapping of volumes would be `./rell:/opt/chromaway/rell`

### Running a node

With the example configuration, you can run:

```shell
docker run -it -v $(pwd):/opt/chromaway/rell -p 7740:7740 --rm registry.gitlab.com/chromaway/postchain-chromia/chromaway/postchain-test-dapp:3.8.0-SNAPSHOT
```

or using docker-compose:

```shell
docker-compose up --detach
```
