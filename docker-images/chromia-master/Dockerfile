# Copyright (c) 2023 ChromaWay Inc. See README for license information.
FROM eclipse-temurin:11-alpine@sha256:8368526729a4fa4275807761f51770c505a16c8dcad969609d7644de9dab9367

RUN apk update && apk add zfs

ARG postchain_dir=/opt/chromaway/postchain
COPY target/lib $postchain_dir/lib
COPY src/main/resources/log4j2.yml $postchain_dir/log/log4j2.yml

ENV POSTCHAIN_DEBUG=false
ENV POSTCHAIN_RUNNING_IN_CONTAINER=true

WORKDIR $postchain_dir
ENTRYPOINT ["/opt/java/openjdk/bin/java", "-XX:+CrashOnOutOfMemoryError", "-Dlog4j2.configurationFile=/opt/chromaway/postchain/log/log4j2.yml" \
, "-classpath", "/opt/chromaway/postchain/lib/*" \
, "net.postchain.AppKt" ]
