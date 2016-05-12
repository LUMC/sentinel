# Sentinel

[![Build Status](https://travis-ci.org/LUMC/sentinel.svg?branch=master)](https://travis-ci.org/LUMC/sentinel)  [![Coverage Status](https://coveralls.io/repos/LUMC/sentinel/badge.svg?branch=master&service=github)](https://coveralls.io/github/LUMC/sentinel?branch=master)

Sentinel is a JSON-based database for next-generation sequencing statistics. Queries and submissions are all done via a RESTful HTTP API which is specified based on [Swagger](http://swagger.io).

## Requirements

  * Java 8 (must be set as the default `java`)
  * Scala 2.11.6
  * MongoDB 3.2 (running on localhost port 27017 for live development server)
  * Python 2.7 and Sphinx (only when building the documentation)

If MongoDB 3.2 is not available in your official package repository, you can install it from the vendor's repository following the instructions [here](https://docs.mongodb.org/v3.2/administration/install-on-linux/).

## Support

Report issues to [the issue page](https://github.com/LUMC/sentinel/issues). Fixes and feature suggestions are also welcome.
