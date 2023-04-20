# Copyright (c) 2019 ChromaWay Inc. See README for license information.
# Postchain Node Docker file with postgres
# --- INTERNAL POSTGRES DB ---
FROM postgres:14.7-alpine3.17@sha256:446abaf8831c54f57212c0ae52f5df84e69eeb2767e2376d07bed9c9742b1243

RUN apk update && apk add openjdk17 bash libc6-compat gcompat su-exec

ENV POSTGRES_DB=postchain
ENV POSTGRES_USER=postchain
ENV POSTGRES_PASSWORD=postchain
ENV POSTGRES_INITDB_ARGS="--lc-collate=C.UTF-8 --lc-ctype=C.UTF-8 --encoding=UTF-8"
# --- INTERNAL POSTGRES DB END ---

ARG postchain_dir=/opt/chromaway/postchain
COPY target/lib $postchain_dir/lib
COPY src/main/resources/log4j2.yml $postchain_dir/log/log4j2.yml

# Scripts
COPY src/main/docker/scripts/* ${postchain_dir}/

ENV POSTCHAIN_DIR=$postchain_dir
ENV LOG4J_CONFIGURATION_FILE=$POSTCHAIN_DIR/log/log4j2.yml

ENV POSTCHAIN_RUNNING_IN_CONTAINER=true

WORKDIR $postchain_dir
ENTRYPOINT ["./entrypoint.sh"]

STOPSIGNAL SIGINT
