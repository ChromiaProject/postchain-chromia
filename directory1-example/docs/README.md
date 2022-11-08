# Directory1 Example

In this guid you can learn how to set up your on blockchain network by studying a concrete example. The example project is called "The city tracker".

The management chain (BC0) is set up on the first node and also added by the management client (postchain-mc) so that bc0 will become aware of itself. Then we can in a convenient way update configuration, add a second blockchain to be managed by bc0: ``city``. More nodes can optionally be added and be made signers. This sample provides configurations for up to 4 nodes.

## Content

This sample contains:
```shell
├── app
│   ├── config
│   │   └── deploy.xml
│   └── src
│       └── main.rell
├── chromia-deployment-tool-cli-3.7.0-SNAPSHOT-dist.tar.gz
├── config
│   ├── c0-deploy.xml
│   ├── common.properties
│   ├── config.0.properties
│   ├── config.1.properties
│   ├── config.2.properties
│   ├── config.3.properties
│   ├── docker.db.properties
│   └── localhost.db.properties
├── directory1-3.7.0-SNAPSHOT-sources.tar.gz
├── docker-compose.yml
├── logs
│   └── postchain.log
├── pmc-directory-3.7.0-SNAPSHOT-dist.tar.gz
├── postchain-client-3.7.0-SNAPSHOT-dist.tar.gz
├── provider
│   ├── alpha
│   ├── beta
│   ├── delta
│   └── gamma
└── scripts
    ├── find-ip
    ├── postchain-debug.sh
    ├── postchain.sh
    ├── run.sh
    └── setup.sh
```

`app` is the city tracker which can be deployed to the network.
`config` contains node configurations for four nodes. By default, they are pointing to `docker.db.properties` which uses `host.docker.internal:5432`. Update this path to the real database that you are using.
`provider` contains configurations for four providers. THey are all pointing towards node 0-3 respectively. Change this using `pmc config` command.
`scripts` contains some utility scripts. `find-ip` lets you find your external ip address. Note that in a production environment, the IP of the server running the node must be used. 
`setup.sh` will unpack all tarballs and generate blockchain configurations.

> **Note**: We recommend installing [direnv](https://direnv.net) to easily access all scripts in the scripts folder and the management console directly from path.

## Setup

Run the script `scripts/setup.sh` to initiate this project. 
The script will unpack the management console as well as the deployment tool and the BC0 sources. It will also generate the blockchain configurations to the `out` and `app-out` folders.

In the configuration for BC0 we have:
```xml
<arg key="initial_provider">
    <bytea>03A692CDB2FD63037D883804A2028C5FBFD5E8A7E20E08BC387071220F8AD06710</bytea>
</arg>
```

which is configured to the provider called `alpha` (found in providers/alpha directory). This will be the initial provider of the network.

### Postgres

You must have a postgres server running to be able to run nodes. You can start a postgres server using docker

```shell
$ docker run --name postgres -e POSTGRES_PASSWORD=postchain -e POSTGRES_USER=postchain -p 5432:5432 -d postgres
```

You then need to configure `config/docker.db.properties` to point the postgres instance. 

> **Note**: The host must be the docker host. On mac, this is `host.docker.internal`, whereas on linux/windows, this is typically `172.17.0.1`

