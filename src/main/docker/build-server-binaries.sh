#!/bin/bash

REDIS_VERSION=6.0.5

docker build --build-arg REDIS_VERSION=${REDIS_VERSION} -t redis-server-builder-amd64 -f ./build-server-amd64.docker .
docker build --build-arg REDIS_VERSION=${REDIS_VERSION} -t redis-server-builder-x86   -f ./build-server-x86.docker .

docker run -it --rm \
		-v "$(pwd)/":/redis-server-binaries \
		redis-server-builder-amd64 \
		sh -c "cd redis-${REDIS_VERSION}; make CC='gcc -static' LDFLAGS='-s' MALLOC='libc'; cp src/redis-server /redis-server-binaries/redis-server-${REDIS_VERSION}"

docker run -it --rm \
		-v "$(pwd)/":/redis-server-binaries \
		redis-server-builder-x86 \
		sh -c "cd redis-${REDIS_VERSION}; make CC='gcc -static' CFLAGS='-m32' LDFLAGS='-m32 -s' MALLOC='libc'; cp src/redis-server /redis-server-binaries/redis-server-${REDIS_VERSION}-32"
