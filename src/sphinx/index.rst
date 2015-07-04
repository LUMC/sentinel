.. Sentinel documentation master file, created by
   sphinx-quickstart on Mon Jun 22 16:14:20 2015.
   You can adapt this file completely to your liking, but it should at least
   contain the root `toctree` directive.

Sentinel
========

Sentinel is a JSON-based database for various next-generation sequencing metrics. It is meant for storing various
metrics from various phases of an analysis pipeline run. For a given pipeline run, users upload a
`JSON <https://en.wikipedia.org/wiki/JSON>`_ file containing the metrics of that pipeline. The metrics can then be
queried using one of the predefined HTTP endpoints.

At the moment, Sentinel is meant for internal `LUMC <http://www.lumc.nl>`_ use only. URLs mentioned in this
documentation may not work outside LUMC.

Please use the navigation bar on the right to explore this site.

.. toctree::
   :hidden:
   :maxdepth: 2

   users_introduction
   users_terminologies
   users_upload
   users_query

.. toctree::
   :hidden:
   :maxdepth: 2

   devs_setup
   devs_design
   devs_codebase
   devs_tutorial_intro
   devs_tutorial_schema
   devs_tutorial_processors
   devs_tutorial_controllers

.. toctree::
   :hidden:
   :maxdepth: 2

   contribute
   changes
