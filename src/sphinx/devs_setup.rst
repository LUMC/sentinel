Local Development Setup
=======================


Dependencies
------------

The minimum requirements for a local development environment are:

* `git <https://git-scm.com/>`_ (version >= 1.9)
* `Java <https://www.java.com/en/>`_ (version >= 1.8)
* `MongoDB <https://www.mongodb.org/>`_ (version >= 3.0)

Note that for testing, Sentinel relies on an embedded MongoDB server which it downloads and runs automatically. If you
are only interested in running tests or are confident enough not to use any development servers, you can skip MongoDB
installation.

For building documentation, you will also need Python `Python <https://www.python.org/>`_ (version 2.7.x), since we use
the `Sphinx <http://sphinx-doc.org/>`_ documentation generator. A complete list of python libraries is listed in the
``requirements-dev.txt`` file in the root of the project.

And finally, while the following packages are not required per se, they can make your development much easier:

* `IntelliJ <https://www.jetbrains.com/idea/>`_ IDE, with the
  `Scala plugin <https://plugins.jetbrains.com/plugin/?id=1347>`_.
* `httpie <https://github.com/jkbrzt/httpie>`_, a command-line HTTP client for issuing HTTP requests.


Starting Up
-----------

Quick Links
^^^^^^^^^^^

* Source code: `https://git.lumc.nl/sasc/sentinel <https://git.lumc.nl/sasc/sentinel>`_

* Git: `git@git.lumc.nl:sasc/sentinel.git <git@git.lumc.nl:sasc/sentinel.git>`_

On the Command Line (without an IDE)
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

1. Head over to the command line, clone the repository, and go into the directory:

    .. code-block:: bash

        $ git clone git@git.lumc.nl:sasc/sentinel.git
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

   Remember that these are only meant for development purposes only. It is strongly recommended that these details be
   changed in when you deploy Sentinel.

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

While we think that IntelliJ can boost your productivity considerably once it is set up, the latest version (14.1)
unfortunately has a `known bug <https://youtrack.jetbrains.com/issue/SCL-8675>`_ that makes importing SBT projects
difficult. Despite this, we still believe that using IntelliJ is worth the initial hassle, so we are showing the set up
steps below.

In case you are using an earlier version, you may try the following steps to see if the bug also exists there:

    1. Head over to the command line and clone the repository

        .. code-block:: bash

            $ git clone git@git.lumc.nl:sasc/sentinel.git

    2. Open IntelliJ, choose ``File`` -> ``New`` -> ``Project From Existing Source...``

    3. Select the location where the project was cloned.

    4. Select ``Import project from external model`` and choose ``SBT``. Make sure the Scala plugin is installed first
       so that the ``SBT`` option is present.

    5. In the dialog box, check the ``Use auto-import`` check box and select Java 8 for the project JDK. You may choose
       other checkboxes as well.

    6. Click ``OK`` and wait.

If nothing shows up, it is likely that your version has the bug. In that case, you can try the workaround to have your
project set up. What the workaround does is simply creating a new SBT project manually, then overwriting the project
with all Sentinel files. The steps are as follows:

    1. Clone the project into a location (the same as step 1 above).

    2. Open IntelliJ, choose ``File`` -> ``New`` -> ``Project...``

    3. In the new dialog window, choose ``Scala`` then ``SBT``

    4. In the the ``New Project`` dialog box, fill out the project details. Make sure that the project SDK is Java 8,
       SBT version is 0.13.8, and Scala version is at least 2.11.6. Check the ``Use auto-import`` check box as well.
       Other check boxes may or may not be selected, depending on your preference.

    5. Click ``Finish``.

    6. Remember the location where the project is created, then close the newly created IntelliJ window. You can do this
       immediately, without waiting for all background tasks to finish.

    7. Move all files from the cloned repository earlier to the newly-created IntelliJ project directory. Make sure all
       files, including the ones in ``project`` and the hidden git files (``.gitignore`` and the ``.git``) are all
       moved.

    8. Start IntelliJ again. You should have the project set up correctly this time. If prompted for a VCS being
       unregistered, you can choose ``Add root`` to have the project set up with git.


Using SBT
---------

Sentinel uses `SBT <http://www.scala-sbt.org/>`_ to manage its builds. You can use its console to run tasks, or directly
from the command line via the bundled `sbt` script. All the build definitions are listed in the

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

You can check the `official SBT tutorial <http://www.scala-sbt.org/release/tutorial/>`_ to get more familiar with it.
