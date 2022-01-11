#!/bin/bash

set -e

REDIS_VERSION=6.2.6
REDIS_TARBALL="redis-${REDIS_VERSION}.tar.gz"
REDIS_URL="https://download.redis.io/releases/${REDIS_TARBALL}"

echo $ARCH

function copy_openssl_and_remove_dylibs() {
  # To make macOS builds more portable, we want to statically link OpenSSL,
  # which is not straightforward. To force static compilation, we copy
  # the openssl libraries and remove dylibs, forcing static linking
  OPENSSL_HOME="${1}"
  ARCH=$2
  OPENSSL_HOME_COPY="${3}/${ARCH}"
  if [ -d "${OPENSSL_HOME}" ]; then
    echo "*** Copying openssl libraries for static linking"
    cp -RL "${OPENSSL_HOME}" "${OPENSSL_HOME_COPY}"
    rm -f "${OPENSSL_HOME_COPY}"/lib/*.dylib
  else
    echo "*** WARNING: could not find openssl libraries at ${OPENSSL_HOME}; this build will almost certainly fail"
  fi
}

if [ "$(dirname ${0})" != "." ]; then
  echo "This script must be run from $(dirname ${0}). \`cd\` there and run again"
  exit 1
fi

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

  # temporary directory for openssl libraries for static linking.
  # assumes standard Homebrew openssl install:
  #   - arm64e at /opt/homebrew/opt/openssl
  #   - x86_64 at /usr/local/opt/openssl
  OPENSSL_TEMP=$(mktemp -d /tmp/embedded-redis-darwin-openssl.XXXXX)

  # build for arm64 on apple silicon
  if arch -arm64e true 2>/dev/null; then
    copy_openssl_and_remove_dylibs /opt/homebrew/opt/openssl arm64e "${OPENSSL_TEMP}"
    echo "*** Building redis version ${REDIS_VERSION} for darwin-arm64e (apple silicon)"
    make clean
    arch -arm64e make -j3 BUILD_TLS=yes OPENSSL_PREFIX="$OPENSSL_TEMP/arm64e"
    mv src/redis-server "../redis-server-${REDIS_VERSION}-darwin-arm64"
  else
    echo "*** WARNING: could not build for darwin-arm64e; you probably want to do this on an apple silicon device"
  fi

  # build for x86_64 if we're on apple silicon or a recent macos on x86_64
  if arch -x86_64 true 2>/dev/null; then
    copy_openssl_and_remove_dylibs /usr/local/opt/openssl x86_64 "${OPENSSL_TEMP}"
    echo "*** Building redis version ${REDIS_VERSION} for darwin-x86_64"
    make clean
    arch -x86_64 make -j3 BUILD_TLS=yes OPENSSL_PREFIX="$OPENSSL_TEMP/x86_64"
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
