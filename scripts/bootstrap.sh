#!/usr/bin/env bash

# bootstrap.sh
#
# Script for bootstrapping Sentinel installation.
#
# TODO: convert this to sbt task?

# Set useful defaults
__dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
set -o errexit
set -o nounset
set -o pipefail

MONGO_EXE=mongo
MONGO_HOST=localhost
MONGO_PORT=27017
MONGO_VERSION=3
DB_NAME=sentinel
DB_SCRIPT=${__dir}/dbSetup.js

# check that mongodb with the correct version is installed
function __check_version {
    INSTALLED_VERSION=$(${MONGO_EXE} --version | grep -oP "([0-9\.]+)$" | cut -d "." -f1)
    if [ "${INSTALLED_VERSION}" -ne "${MONGO_VERSION}" ]
    then
        echo "ERROR: Sentinel requires MongoDB version ${MONGO_VERSION}. Exiting."
        exit 2
    fi
}

# run dbSetup.js script
# TODO: password prompt if auth is already set?
function __db_setup {
    ${MONGO_EXE} ${MONGO_HOST}:${MONGO_PORT}/${DB_NAME} ${DB_SCRIPT}
}

__check_version
__db_setup
