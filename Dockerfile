# Docker image for sentinel development.
#
# https://github.com/LUMC/sentinel

FROM phusion/baseimage

MAINTAINER Wibowo Arindrarto <w.arindrarto@lumc.nl>

ENV LANG C.UTF-8

RUN  add-apt-repository ppa:webupd8team/java && \
     add-apt-repository -y ppa:webupd8team/java && \
     echo debconf shared/accepted-oracle-license-v1-1 select true | sudo debconf-set-selections && \
     echo debconf shared/accepted-oracle-license-v1-1 seen true | sudo debconf-set-selections && \
     apt-get update && \
     apt-get install -y oracle-java8-installer python2.7-minimal python-pip

ADD . /sentinel

WORKDIR /sentinel

RUN pip install -r dev-requirements.txt

RUN ./sbt update
