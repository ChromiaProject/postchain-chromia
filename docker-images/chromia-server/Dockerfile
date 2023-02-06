# Copyright (c) 2022 ChromaWay Inc. See README for license information.
FROM eclipse-temurin:17-jammy@sha256:36d8a3b875dad9a98366a18244649ba71a1007410b14511c861e49ac58ef76ff

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
