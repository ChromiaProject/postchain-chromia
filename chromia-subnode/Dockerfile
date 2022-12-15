# Copyright (c) 2019 ChromaWay Inc. See README for license information.
# Postchain Node Docker file with postgres
ARG postchain_image=chromaway/chromia-server
ARG postchain_version
FROM $postchain_image:$postchain_version AS postchain

# --- INTERNAL POSTGRES DB ---
FROM postgres:11-alpine@sha256:8b4ef4f2a222f3fd9b77cd8db54547cdda0aee66dade441c5ac2dbfbb54e8312

RUN apk update && apk add openjdk17 bash curl libc6-compat gcompat su-exec

ENV POSTGRES_DB=postchain
ENV POSTGRES_USER=postchain
ENV POSTGRES_PASSWORD=postchain
# --- INTERNAL POSTGRES DB END ---

ARG postchain_dir=/opt/chromaway/postchain
COPY --from=postchain $postchain_dir $postchain_dir

# Scripts
COPY src/main/docker/scripts/* ${postchain_dir}/

ENV POSTCHAIN_DIR=$postchain_dir
ENV RELL_OUT=$POSTCHAIN_DIR/target
ENV POSTCHAIN_LOG4J2=$POSTCHAIN_DIR/log/log4j2.yml

WORKDIR $postchain_dir
ENTRYPOINT ["./entrypoint.sh"]

STOPSIGNAL SIGINT
