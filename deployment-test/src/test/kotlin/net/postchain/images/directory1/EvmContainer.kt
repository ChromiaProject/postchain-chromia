package net.postchain.images.directory1

import org.testcontainers.containers.DockerComposeContainer
import java.io.File

class GethContainer : DockerComposeContainer<GethContainer>(File("src/test/resources/evm-container/geth-compose/docker-compose.yml"))

class BscContainer : DockerComposeContainer<BscContainer>(File("src/test/resources/evm-container/bsc-compose/docker-compose.yml"))
