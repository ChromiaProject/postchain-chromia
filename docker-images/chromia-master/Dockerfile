# Copyright (c) 2023 ChromaWay Inc. See README for license information.
FROM eclipse-temurin:11-jammy@sha256:6a7b3762b5db37d5c7b1ee18123351becbd2d2ad6301e62f67251f10e53f183b

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
