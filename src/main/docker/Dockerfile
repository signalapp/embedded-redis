ARG ALPINE_VERSION=3.19.1
FROM alpine:${ALPINE_VERSION}
RUN apk add --no-cache gcc musl-dev openssl-dev openssl-libs-static make pkgconfig linux-headers

WORKDIR /build

ARG REDIS_VERSION
ENV REDIS_VERSION=${REDIS_VERSION}
COPY redis-${REDIS_VERSION}.tar.gz /redis-${REDIS_VERSION}.tar.gz
RUN ls -l /

ARG ARCH
RUN tar zxf /redis-${REDIS_VERSION}.tar.gz && \
    cd redis-${REDIS_VERSION} && \
    make BUILD_TLS='yes' CC='gcc -static' LDFLAGS='-s' MALLOC='libc' && \
    mv src/redis-server /build/redis-server-${REDIS_VERSION}-linux-${ARCH}

CMD [ "/bin/sh" ]
