# Sentinel

[![Build Status](https://travis-ci.org/LUMC/sentinel.svg?branch=master)](https://travis-ci.org/LUMC/sentinel)  [![Coverage Status](https://coveralls.io/repos/LUMC/sentinel/badge.svg?branch=master&service=github)](https://coveralls.io/github/LUMC/sentinel?branch=master)

Sentinel is a JSON-based database for next-generation sequencing statistics. Queries and submissions are all done via a RESTful HTTP API which is specified based on [Swagger](http://swagger.io).

## Requirements

- Java 8 (must be set as the default `java`)
- Scala 2.11.6
- MongoDB 3.0 (running on localhost port 27017 for live development server)
- Python 2.7 and Sphinx (only when building the documentation)

## Set Up

You can set up Sentinel in your local machine or using the provided Vagrantfile in the ``deployments`` directory. Which one should you do? That depends:

  * Choose local machine set up if you want to hack inside the actual Sentinel code. It requires some initial set up (a database server must be running with a specific user account), but it allows you to do faster iterations with your code.

  * Choose the Vagrant set up if you just want to see how a running Sentinel looks like, along with the user documentation and the Scaladoc documentation. The initial setup is much quicker, but you can only run Sentinel which has beenpushed to a remote git location (this is the master branch in the official repositories by default). We use Ansible to deploy Sentinel and you can change some of the variables in the Sentinel ansible role inside the ``deployments`` directory.

### Local Machine

Prerequisites:

- A running local MongoDB server.
- An active Python virtual environment using the ``requirements-dev.txt`` file.

```sh
$ git clone {this-repository}
$ cd sentinel

# for first-time runs, install Sphinx dependencies
$ pip install -r requirements-dev.txt

# create a test database for development
$ ./scripts/bootstrap_dev.sh

# go into the SBT shell
$ ./sbt

# select the sentinel-lumc project
> project sentinel-lumc

# start the development server
> container:start
```

Sentinel should now be running at [http://localhost:8080/](http://localhost:8080/) in your browser.

### Vagrant

Prerequisites:

- Vagrant version 1.8.1 or later.
- An active Python virtual environment using the ``requirements-deploy.txt`` file.

```sh
$ git clone {this-repository}
$ cd sentinel

# for first-time runs, install Sphinx dependencies
$ pip install -r requirements-deploy.txt

# go to the Vagrant directory
$ cd deployments/vagrant

# go into the SBT shell
$ vagrant up
```

The initial command may take a while to finish. Afterwards, the following sites should be accessible via your web browser:

  * Sentinel live documentation - [http://localhost:9080/](http://localhost:9080/)
  * Sentinel user guide - [http://localhost:9080/guide/index.html](http://localhost:9080/guide/index.html)
  * Sentinel Scaladoc - [http://localhost:9080/scaladoc/index.html](http://localhost:9080/scaladoc/index.html)

By default, the VM is running on [http://192.168.90.90/]. Ports 22 and 80 of the VM are available via ports 9022 and 9080 from your host machine, respectively.

## Support

Report issues to [the issue page](https://git.lumc.nl/sasc/sentinel/issues). Fixes and feature suggestions are also
welcome.
