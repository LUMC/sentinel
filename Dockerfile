# Docker image for sentinel development.
#
# https://github.com/LUMC/sentinel
#
# Python-related commands modified from the official python:2.7 image.

FROM java:openjdk-8-jdk

MAINTAINER Wibowo Arindrarto <w.arindrarto@lumc.nl>

ENV LANG C.UTF-8

ENV PYTHON_VERSION 2.7.10

ENV PYTHON_PIP_VERSION 7.1.0

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates \
        libsqlite3-0 \
        libssl1.0.0 \
    && rm -rf /var/lib/apt/lists/*

RUN gpg --keyserver hkp://ha.pool.sks-keyservers.net:80 --recv-keys C01E1CAD5EA2C4F0B8E3571504C367C218ADD4FF

RUN set -x \
    && buildDeps=' \
        curl \
        gcc \
        libbz2-dev \
        libc6-dev \
        libncurses-dev \
        libreadline-dev \
        libsqlite3-dev \
        libssl-dev \
        make \
        xz-utils \
        zlib1g-dev \
    ' \
    && apt-get update && apt-get install -y $buildDeps --no-install-recommends && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /usr/src/python \
    && curl -SL "https://www.python.org/ftp/python/$PYTHON_VERSION/Python-$PYTHON_VERSION.tar.xz" -o python.tar.xz \
    && curl -SL "https://www.python.org/ftp/python/$PYTHON_VERSION/Python-$PYTHON_VERSION.tar.xz.asc" -o python.tar.xz.asc \
    && gpg --verify python.tar.xz.asc \
    && tar -xJC /usr/src/python --strip-components=1 -f python.tar.xz \
    && rm python.tar.xz* \
    && cd /usr/src/python \
    && ./configure --enable-shared --enable-unicode=ucs4 \
    && make -j$(nproc) \
    && make install \
    && ldconfig \
    && curl -SL 'https://bootstrap.pypa.io/get-pip.py' | python2 \
    && pip install --no-cache-dir --upgrade pip==$PYTHON_PIP_VERSION \
    && find /usr/local \
        \( -type d -a -name test -o -name tests \) \
        -o \( -type f -a -name '*.pyc' -o -name '*.pyo' \) \
        -exec rm -rf '{}' + \
    && apt-get purge -y --auto-remove $buildDeps \
    && rm -rf /usr/src/python

ADD . /sentinel

WORKDIR /sentinel

RUN pip install -r dev-requirements.txt

RUN ./sbt update
