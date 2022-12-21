# Postchain image for developers

A simple image for daily use by developers

## Database

This image will assume that you have a postgres database running, see [these instructions](https://gitlab.com/chromaway/postchain/-/blob/dev/postchain-devtools/README_postgres_setup.md) for how to set it up.
Specify the path to this using either `database.url=` in the node config or `POSTCHAIN_DB_URL` environment variable.
If you are using docker compose, the environment variable to set on the postchain service is
`POSTCHAIN_DB_URL: jdbc:postgresql://postgres/postchain`

## Running a node

The `rell` directory of your dapp, containing `src`, `run.xml` & `config` directory should be mapped to `/opt/chromaway/rell` in the container.

```
docker run -it -v $(pwd):/opt/chromaway/rell -e POSTCHAIN_DB_URL=jdbc:postgresql://host.docker.internal:5432/postchain -p 7740:7740 --rm chromaway/postchain-test-dapp
```

## Running tests

Tests can be run using the same image.

```
docker run -it -v $(pwd):/opt/chromaway/rell --rm chromaway/postchain-test-dapp test
```

## Environment variables

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

## Persisting blockchain data

If you wish to persist the blockchain data, then make sure to map `/var/lib/postgresql/data` as well.

```
volumes:
  - ./rell:/opt/chromaway/rell
  - ./data:/var/lib/postgresql/data
```

## Docker compose

The simplest setup is to run the image with docker-compose, so that you don't need an external db. See the sample compose file

### Start the container
````shell
docker-compose -f <path-to-compose.yml> up --detach
````

### Stop the container
````shell
docker-compose -f <path-to-compose.yml> down
````

### Update blockchain configuration

To update the existing blockchain configuration to a running container, do the following:
- To update chain configuration, make sure the affected chain is ordered *first* in the run.xml file and make any changes you want here.
- Update the rell code as you want.
- Run the following docker compose command:
```shell
docker-compose -f <path-to-compose.yml> run postchain update
```
where *postchain* is the name of the postchain service.
The new configuration will be active after 5 blocks.

### Run rell tests

Set command to `test` on the postchain service, and optionaly set `GENERATE_CONFIGURATION` environment variable to false.

````shell
docker-compose -f <path-to-compose.yml> up --abort-on-container-exit --exit-code-from postchain
````
where *postchain* is the name of the postchain service.

### Run only one rell test

Set command to `single-test test_foo` on the postchain service (to run ONLY the test "test_foo()"), and optionaly set `GENERATE_CONFIGURATION` environment variable to false.

(The rest is the same for full testing).