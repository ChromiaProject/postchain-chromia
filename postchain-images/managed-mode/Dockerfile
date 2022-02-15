# Copyright (c) 2019 ChromaWay Inc. See README for license information.
# Postchain Node Docker file
ARG postchain_image=chromaway/postchain-dapp
ARG postchain_version
FROM $postchain_image:$postchain_version AS postchain

ARG postchain_dir=/opt/chromaway/postchain
COPY target/chromia0/rell $postchain_dir/chain_zero
COPY src/main/resources/chain_zero/run-chromia0.xml $postchain_dir/chain_zero/manifest.xml
COPY src/main/docker/entrypoint.sh $postchain_dir/entrypoint.sh

ENV RELL_SRC=$postchain_dir/chain_zero
ENV RELL_CONF=$postchain_dir/chain_zero/manifest.xml
ENV RELL_OUT=$postchain_dir/chain0-generated

ENV NODE_HOST=127.0.0.1
ENV NODE_PORT=7740
ENV NODE_PUBKEY=00000000000000000000000000000000000000000000000

