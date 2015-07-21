# Docker image for sentinel development.
#
# https://github.com/LUMC/sentinel

FROM lumc/base-java-python

MAINTAINER Wibowo Arindrarto <w.arindrarto@lumc.nl>

# MongoDB shell is installed so we can reset the database everytime the dev server starts
RUN apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv 7F0CEB10 \
    && echo "deb http://repo.mongodb.org/apt/debian wheezy/mongodb-org/3.0 main" | tee /etc/apt/sources.list.d/mongodb-org-3.0.list \
    && apt-get update \
    && apt-get install -y mongodb-org-shell

ADD . /sentinel

WORKDIR /sentinel

RUN pip install -r requirements-dev.txt \
    && ./sbt update
