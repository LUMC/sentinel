# Docker image for sentinel development.
#
# https://github.com/LUMC/sentinel

FROM lumc/base-java-python

MAINTAINER Wibowo Arindrarto <w.arindrarto@lumc.nl>

ADD . /sentinel

WORKDIR /sentinel

RUN pip install -r dev-requirements.txt

RUN ./sbt update
