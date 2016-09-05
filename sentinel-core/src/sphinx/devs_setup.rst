Local Development Setup
=======================


Dependencies
------------

The minimum requirements for a local development environment are:

* `git <https://git-scm.com/>`_ (version >= 1.9)
* `Java <https://www.java.com/en/>`_ (version >= 1.8)
* `MongoDB <https://www.mongodb.org/>`_ (version >= 3.2)

Note that for testing, Sentinel relies on an embedded MongoDB server which it downloads and runs automatically. If you
are only interested in running tests or are confident enough not to use any development servers, you can skip MongoDB
installation.

For building documentation, you will also need Python `Python <https://www.python.org/>`_ (version 2.7.x), since we use
the `Sphinx <http://sphinx-doc.org/>`_ documentation generator. A complete list of python libraries is listed in the
``requirements-dev.txt`` file in the root of the project.

While the following packages are not strictly required, they can make your development much easier:

* `IntelliJ <https://www.jetbrains.com/idea/>`_ IDE, with the
  `Scala plugin <https://plugins.jetbrains.com/plugin/?id=1347>`_.
* `httpie <https://github.com/jkbrzt/httpie>`_, a command-line HTTP client for issuing HTTP requests.

And finally, we should note that the current repository contains two packages: ``sentinel`` for all core methods and
``sentinel-lumc`` for code specific to our setup in the LUMC. In the future we will most likely separate
``sentinel-lumc`` out into its own repository.


Third party libraries
---------------------

There are several heavily-used libraries that Sentinel depend on. It is a good idea to get familiar with them if you
wish to extend Sentinel. These libraries are:

* `Json4s <http://json4s.org/>`_, for processing JSON uploads
* `Scalaz <https://github.com/scalaz/scalaz>`_ -- particularly the disjunction type (``\/``), to complement the standard
  library functions.
* `Casbah <https://mongodb.github.io/casbah/>_`, for working the MongoDB backend


Starting Up
-----------

Quick Links
^^^^^^^^^^^

* Source code: `https://github.com/lumc/sentinel <https://github.com/lumc/sentinel>`_

* Git: `https://github.com/lumc/sentinel.git <https://github.com/lumc/sentinel.git>`_

On the Command Line (without an IDE)
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

1. Head over to the command line, clone the repository, and go into the directory:

    .. code-block:: bash

        $ git clone https://github.com/lumc/sentinel.git
        $ cd sentinel

2. If you would like to be able to build documentation, install the python dependencies. We recommend that you use a
   `virtual environment <http://docs.python-guide.org/en/latest/dev/virtualenvs/>`_  to avoid polluting the root
   package namespace:

    .. code-block:: bash

       $ pip install -r requirements-dev.txt

3. If you would like to set up a local development server, make sure MongoDB is running locally on port 27017. You will
   also need to set up the Sentinel MongoDB users. Sentinel comes with a bash bootstrap script to help you do so. Thes
   script will set up two MongoDB users by default:

   * ``sentinel-owner`` (password: ``owner``), as the owner of the database.
   * ``sentinel-api`` (password: ``api``), which the Sentinel application uses to connect to the database.

   The script also sets up a Sentinel user with the following details:

   * User ID: ``dev``
   * Password: ``dev``
   * API key: ``dev``

   Remember that these are only meant for development purposes. It is strongly recommended to change these details when
   you deploy Sentinel.

   The bootstrap script can be run as follows:

    .. code-block:: bash

       $ ./scripts/bootstrap_dev.sh

4. Start the SBT interpreter via the bundled script. The first setup will take some time, but subsequent ones will be
   faster:

    .. code-block:: bash

       $ ./sbt

5. Run the full suite of tests to make sure you are set up correctly. Again, you will start downloading the dependencies
   if this is your first time:

    .. code-block:: none

        > all test it:test

6. If all the tests pass, you are good to go! Otherwise, please let us know so we can take a look. All tests from the
   default development branch should always pass.


With IntelliJ
^^^^^^^^^^^^^

Being a Scala-based project, you can use an IDE to develop Sentinel instead of just command line editors. There are
numerous IDEs to choose from, but one that we have found to work well is is IntelliJ. You can set up
sentinel in IntelliJ following these steps:

    1. Head over to the command line, go to a directory of your choice, and clone the repository

        .. code-block:: bash

            $ git clone https://github.com/lumc/sentinel.git

    2. Open IntelliJ, choose ``File`` -> ``New`` -> ``Project From Existing Source...``

    3. Select the location where the project was cloned.

    4. Select ``Import project from external model`` and choose ``SBT``. Make sure the Scala plugin is installed first
       so that the ``SBT`` option is present.

    5. In the dialog box, check the ``Use auto-import`` check box and select Java 8 for the project JDK. You may choose
       other checkboxes as well.

    6. Click ``OK`` and wait.


Using SBT
---------

Sentinel uses `SBT <http://www.scala-sbt.org/>`_ to manage its builds. You can use its console to run tasks, or directly
from the command line via the bundled `sbt` script.

It comes with many useful tasks, the most-used ones being:

* ``compile``: compiles all source files and formats the source code according to the preferences defined in the build
  file.
* ``container:start``: starts development server on port 8080.
* ``container:stop``: stops a running development server.
* ``browse``: opens a web browser window pointing to the development server.
* ``test``: runs all unit tests.
* ``it:test``: runs all integration tests.
* ``package-site``: creates the Sphinx and ScalaDoc documentation in the ``target/scala-2.11`` directory.
* ``assembly``: creates a JAR with embedded Jetty for deployment in the ``target/scala-2.11`` directory.
* ``assembly-fulltest``: runs all tests (unit and integration) and then creates the deployment JAR.

Note that by default these commands are run for both the ``sentinel`` and ``sentinel-lumc`` packages in parallel. If you
only want to run it for the ``sentinel`` package, then the commands must be prefixed with ``sentinel/``, for example
``test`` becomes ``sentinel/test``. Alternatively, you can also set the project scope first using the
``project sentinel`` command. Subsequent commands can then be run on ``sentinel`` without the prefix.

If you have set up development in IntelliJ, you can also run these commands from inside the IDE. Note however that you
may need to unmark the ``sentinel/src/test/scala/nl/lumc/sasc/sentinel/exts` directory as test since that may result in
some compilation problems. It is usually enough to mark the higher-level ``sentinel/src/test/scala`` as the test source.

You can check the `official SBT tutorial <http://www.scala-sbt.org/release/tutorial/>`_ to get more familiar with it.
