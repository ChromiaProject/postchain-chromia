# Copyright (c) 2019 ChromaWay Inc. See README for license information.
# Postchain Node Docker file
FROM alpine:3.15.1@sha256:d6d0a0eb4d40ef96f2310ead734848b9c819bb97c9d846385c4aca1767186cd4

RUN apk update \
    && apk upgrade \
    && apk add ca-certificates \
    && update-ca-certificates \
    && apk add --update coreutils && rm -rf /var/cache/apk/*   \
    && apk add --update openjdk17 tzdata curl unzip bash \
    && apk add libc6-compat \
    && apk add gcompat \
    && apk add --no-cache nss \
    && apk add libxml2 libxml2-utils \
    && rm -rf /var/cache/apk/*

ARG postchain_dir=/opt/chromaway/postchain

COPY target/lib $postchain_dir/lib
COPY src/main/scripts $postchain_dir
COPY src/main/resources/log4j2.yml $postchain_dir/log/log4j2.yml

RUN chmod +x $postchain_dir/postchain.sh \
    && chmod +x $postchain_dir/multigen.sh \
    && chmod +x $postchain_dir/docker-entrypoint.sh \
    && chmod +x $postchain_dir/entrypoint.sh

ENV POSTCHAIN_DIR=$postchain_dir
ENV POSTCHAIN_LOG4J2=$POSTCHAIN_DIR/log/log4j2.yml
ENV RELL_DIR=/opt/chromaway/rell
ENV RELL_SRC=$RELL_DIR/src
ENV RELL_OUT=$RELL_DIR/out

ENV CHAIN_CONF=$RELL_DIR/config/run.xml
ENV NODE_CONF=$RELL_DIR/config/node-config.properties

ENV WIPE_DB=false
ENV GENERATE_BLOCKCHAIN=true

EXPOSE 7740
EXPOSE 9870
STOPSIGNAL SIGINT

WORKDIR $postchain_dir
ENTRYPOINT ["./docker-entrypoint.sh"]
CMD ["run-node-auto"]
