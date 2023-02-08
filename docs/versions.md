# Versions

## Version compatibility 

| Directory1                                    | PMC     |
|-----------------------------------------------|---------|
| Delta                                         | 3.7.1   |
| Sigma                                         | 3.8.0 * |

*) Command `pmc blockchain update` is backward compatible, so `Delta` can be upgraded to `Sigma` by means of pmc 3.8.0   


## Multiple versions environment

For multiple network support it is convenient to use dockerized PMC:

#### Delta, PMC 3.7.1

```shell
$ export PMC_IMAGE_371=registry.gitlab.com/chromaway/postchain-chromia/chromaway/postchain-mc:3.7.1
$ docker pull $PMC_IMAGE_371

# pmc version
$ docker run --rm \
    --mount type=bind,source="$(pwd)"/provider/alpha/.pmc/config-docker,target=/opt/chromaway/postchain/.pmc/config,readonly \
    $PMC_IMAGE_371 \
    --version

# pmc network version
$ docker run --rm \
    --mount type=bind,source="$(pwd)"/provider/alpha/.pmc/config-docker,target=/opt/chromaway/postchain/.pmc/config,readonly $PMC_IMAGE_371 \
    network version -cfg .pmc/config

# pmc network summary
$ docker run --rm -it --mount type=bind,source="$(pwd)"/provider/alpha/.pmc/config-docker,target=/opt/chromaway/postchain/.pmc/config,readonly \
    $PMC_IMAGE_371 
    \network summary -cfg .pmc/config
```

#### Sigma, PMC 3.8.0

```shell
$ export PMC_IMAGE_380=registry.gitlab.com/chromaway/postchain-chromia/chromaway/postchain-mc:3.8.0-SNAPSHOT
$ docker pull $PMC_IMAGE_380

# pmc version
$ docker run --rm \
    --mount type=bind,source="$(pwd)"/provider/alpha/.pmc/config-docker,target=/opt/chromaway/postchain/.pmc/config,readonly \
    $PMC_IMAGE_380 \
    --version

# pmc network version
$ docker run --rm \
    --mount type=bind,source="$(pwd)"/provider/alpha/.pmc/config-docker,target=/opt/chromaway/postchain/.pmc/config,readonly \
    $PMC_IMAGE_380 \
    network version -cfg .pmc/config

# pmc network summary
$ docker run --rm \
    --mount type=bind,source="$(pwd)"/provider/alpha/.pmc/config-docker,target=/opt/chromaway/postchain/.pmc/config,readonly \
    $PMC_IMAGE_380 \
    network summary -cfg .pmc/config
```

