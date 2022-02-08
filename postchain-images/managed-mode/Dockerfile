# Copyright (c) 2019 ChromaWay Inc. See README for license information.
# Postchain Node Docker file
ARG postchain_image=chromaway/postchain-dapp
ARG postchain_version
FROM $postchain_image:$postchain_version AS postchain

ARG postchain_dir=/opt/chromaway/postchain
COPY src/main/resources/chain_zero $postchain_dir/chain_zero

ENV RELL_SRC=$postchain_dir/chain_zero
ENV RELL_CONF=$postchain_dir/chain_zero/manifest.xml
ENV RELL_OUT=$postchain_dir/chain0-generated

