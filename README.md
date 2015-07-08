# Sentinel

[![Build Status](https://travis-ci.org/LUMC/sentinel.svg?branch=master)](https://travis-ci.org/LUMC/sentinel)

Sentinel is a JSON-based database for next-generation sequencing statistics. Queries and submissions are all done via a
RESTful HTTP API which is specified based on [Swagger](http://swagger.io).

## Requirements

- Java 8 (must be set as the default `java`)
- Scala 2.11.6
- MongoDB 3.0 (running on localhost port 27017 for live development server)
- Python 2.7 and Sphinx (only when building the documentation)

## Quick Start

```sh
$ git clone {this-repository}
$ cd sentinel
$ ./scripts/bootstrap_dev.sh
$ ./sbt
> container:start
> browse
```

If `browse` doesn't launch your browser, manually open [http://localhost:8080/](http://localhost:8080/) in your browser.

## Support

Report issues to [the issue page](https://git.lumc.nl/sasc/sentinel/issues). Fixes and feature suggestions are also
welcome.

## More

Please see the documentation for a complete guide on the project.
