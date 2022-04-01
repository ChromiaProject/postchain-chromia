# Copyright (c) 2022 ChromaWay Inc. See README for license information.
ARG postchain_image=chromaway/postchain-dapp
ARG postchain_version
FROM $postchain_image:$postchain_version

ARG postchain_dir=/opt/chromaway/postchain
ARG chain0_source
COPY $chain0_source $postchain_dir/chain_zero
COPY src/main/docker/entrypoint.sh $postchain_dir/entrypoint.sh

ENV RELL_SRC=$postchain_dir/chain_zero
ENV RELL_OUT=$postchain_dir/chain0-generated
ENV CHAIN_CONF=$postchain_dir/chain_zero/manifest.xml

ENV NODE_HOST=""
ENV NODE_PORT=""
ENV NODE_PUBKEY=""

ENV BOOTSTRAP_NODE_HOST=""
ENV BOOTSTRAP_NODE_PORT=""
ENV BOOTSTRAP_NODE_PUBKEY=""
