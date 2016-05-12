The Codebase
============

Before diving deeper into the code, it is useful to see how the source code is organized.

Starting from the root, we see three directories:

    * ``project``, where the build definition files are located.

    * ``scripts``, where helper scripts are located.

    * ``sentinel`` and ``sentinel-lumc``, where the actual source code files are located. ``sentinel`` is meant to
      contain the sentinel core code, while ``sentinel-lumc`` contains code specific to LUMC pipelines. Each of these
      directories contain a directory called ``src`` that points to the actual source code.

Inside each ``src``, we see four more directories. This may look unusual if you come from a Java background, less-so if
you are already used to Scala. They are:

    * ``main``, where the main source files are located.

    * ``test``, where unit tests are defined.

    * ``it``, where integration tests are defined.

    * ``sphinx``, where the raw documentation source files are located.

From here on, you should already get a good grip on the contents of the deeper level directories. Some are worth noting,
for reasons of clarity:

    * ``test/resources`` contains all test files and example run summaries used for testing. It is symlinked to
      ``it/resources`` to avoid having duplicate testing resources.

    * ``main/resources`` contains run-time resource files that are loaded into the deployment JAR. In most cases, these
      are pipeline schema files.

    * ``main/webapp/api-docs`` contains a distribution copy of the `swagger-ui <https://github.com/swagger-api/swagger-ui>`_
      package. The package is also bundled into the deployment JAR, to help users explore the Sentinel APIs
      interactively.
