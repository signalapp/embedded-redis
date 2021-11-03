#!/bin/bash

set -e

REDIS_VERSION=6.2.6
REDIS_TARBALL="redis-${REDIS_VERSION}.tar.gz"
REDIS_URL="https://download.redis.io/releases/${REDIS_TARBALL}"

if ! [ -f "${REDIS_TARBALL}" ]; then
  curl -o "${REDIS_TARBALL}" "${REDIS_URL}"
fi

all_linux=0
if command -pv docker 2>/dev/null; then
  for arch in x86_64 arm64 i386; do
    
    echo "*** Building redis version ${REDIS_VERSION} for linux-${arch}"

    set +e
    docker build \
      "--platform=linux/${arch}" \
      --build-arg "REDIS_VERSION=${REDIS_VERSION}" \
      --build-arg "ARCH=${arch}" \
      -t "redis-server-builder-${arch}" \
      .

    if [[ $? -ne 0 ]]; then
      echo "*** ERROR: could not build for linux-${arch}"
      continue
    fi
    set -e

    docker run -it --rm \
      "--platform=linux/${arch}" \
      -v "$(pwd)/":/mnt \
      "redis-server-builder-${arch}" \
      sh -c "cp /build/redis-server-${REDIS_VERSION}-linux-${arch} /mnt"

    ((all_linux+=1))

  done
else
  echo "*** WARNING: No docker command found. Cannot build for linux."
fi

if [[ "${all_linux}" -lt 3 ]]; then
  echo "*** WARNING: was not able to build for all linux arches; see above for errors"
fi

if [[ "$(uname -s)" == "Darwin" ]]; then

  tar zxf "${REDIS_TARBALL}"
  cd "redis-${REDIS_VERSION}"

  # build for arm64 on apple silicon
  if arch -arm64e true 2>/dev/null; then
    echo "*** Building redis version ${REDIS_VERSION} for darwin-arm64e (apple silicon)"
    make clean
    arch -arm64e make -j3 BUILD_TLS=yes
    mv src/redis-server "../redis-server-${REDIS_VERSION}-darwin-arm64"
  else
    echo "*** WARNING: could not build for darwin-arm64e; you probably want to do this on an apple silicon device"
  fi

  # build for x86_64 if we're on apple silicon or a recent macos on x86_64
  if arch -x86_64 true 2>/dev/null; then
    echo "*** Building redis version ${REDIS_VERSION} for darwin-x86_64"
    make clean
    arch -x86_64 make -j3 BUILD_TLS=yes
    mv src/redis-server "../redis-server-${REDIS_VERSION}-darwin-x86_64"
  else
    echo "*** WARNING: you are on a version of macos that lacks /usr/bin/arch, you probably do not want this"
    exit 1
  fi
  cd ..

else
  echo "*** WARNING: Cannot build for macos/darwin on a $(uname -s) host"
fi

ls -l redis-server-*

echo "*** Moving built binaries to ../resources; you need to handle the rest yourself"
mv redis-server-* ../resources/
