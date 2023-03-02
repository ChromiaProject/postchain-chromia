# Copyright (c) 2019 ChromaWay Inc. See README for license information.
# Postchain Node Docker file with postgres
# --- INTERNAL POSTGRES DB ---
FROM postgres:11-alpine@sha256:8b4ef4f2a222f3fd9b77cd8db54547cdda0aee66dade441c5ac2dbfbb54e8312

RUN apk update && apk add openjdk17 bash curl libc6-compat gcompat su-exec

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
