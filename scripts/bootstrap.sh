#!/usr/bin/env bash

# bootstrap.sh
#
# Script for bootstrapping local sentinel development.
#
# If you local MongoDB setup does not have any authentication enabled,
# this script can be run as is. Otherwise, you will need to uncomment the
# authentication settings below before running.
#
# TODO: short note on deployment?
# TODO: convert this to sbt task?

# Set useful defaults
__dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# Exit if any errors are encountered
set -o errexit
# Exit if any undeclared variables are used
set -o nounset
# Output exit code of last failed command in a piped execution
set -o pipefail

# MongoDB defaults (change to your local settings appropriately)
MONGO_EXE=mongo
MONGO_HOST=localhost
MONGO_PORT=27017

# Uncomment and fill values to authenticate against MongoDB instance.
MONGO_USER=admin
MONGO_PASSWD=admin
MONGO_AUTHDB=admin

# Sentinel-specific settings
DB_NAME=sentinel
DB_SCRIPT=${__dir}/dbSetup.js
REQ_MONGO_VERSION=3

# Function for checking that MongoDB with the correct version is installed
function __check_version {
    INS_MONGO_VERSION=$(${MONGO_EXE} --version | grep -oP "([0-9\.]+)$" | cut -d "." -f1)
    if [ "${INS_MONGO_VERSION}" -ne "${REQ_MONGO_VERSION}" ]
    then
        echo "ERROR: Sentinel requires MongoDB version ${REQ_MONGO_VERSION}. Exiting."
        exit 2
    fi
}

# Run dbSetup.js script
function __db_setup {
    __auth=''
    if [ -n "${MONGO_USER}" ]; then
        __auth+="-u ${MONGO_USER}"
    fi
    if [ -n "${MONGO_PASSWD}" ]; then
        __auth+=" -p ${MONGO_PASSWD}"
    fi
    if [ -n "${MONGO_AUTHDB}" ]; then
        __auth+=" --authenticationDatabase ${MONGO_AUTHDB}"
    fi
    ${MONGO_EXE} ${MONGO_HOST}:${MONGO_PORT}/${DB_NAME} ${DB_SCRIPT} ${__auth}
}

__check_version
__db_setup
