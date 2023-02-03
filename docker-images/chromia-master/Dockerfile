# Copyright (c) 2023 ChromaWay Inc. See README for license information.
FROM eclipse-temurin:11-jammy@sha256:8c02cc578c299c24c3e93b84011c5647d5fdd8a940a5cfd0948e71abe93eb153

RUN apt update && apt install -y zfsutils-linux

ARG postchain_dir=/opt/chromaway/postchain
COPY target/lib $postchain_dir/lib
COPY src/main/resources/log4j2.yml $postchain_dir/log/log4j2.yml

ENV POSTCHAIN_DEBUG=false
ENV POSTCHAIN_RUNNING_IN_CONTAINER=true

WORKDIR $postchain_dir
ENTRYPOINT ["/usr/bin/java", "-XX:+CrashOnOutOfMemoryError", "-Dlog4j2.configurationFile=/opt/chromaway/postchain/log/log4j2.yml" \
, "-classpath", "/opt/chromaway/postchain/lib/*" \
, "net.postchain.AppKt" ]
