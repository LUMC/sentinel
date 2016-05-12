Introduction
============

Why Sentinel
------------

The modern sequencing analysis ecosystem is growing rapidly, both in volume and complexity. An enormous amount of
data from various organisms is being generated daily by a plethora of machines. Depending on the research question,
the data then must be passed through a specific data analysis pipeline, composed of various tools and (often) ad-hoc
scripts. These pipelines usually depend on a number of different external data sources as well, such as genome
assemblies and/or annotation files.

In order to properly answer the biological question at hand, a researcher must take into account all of these moving
parts. However, grappling with such huge amount of data and variation is not a trivial task. Questions such as 'Is my
sequencing run good enough?' or 'How does my sequencing run compare with others?' are often answered only using
anecdotal evidence.

To address this issue, we developed Sentinel. Sentinel is a database designed to store various metrics of various
sequencing analysis pipeline runs. It provides a systematic way of storing and querying these metrics, with various
filter and selection capabilities. We believe that gathering sufficient data points is the first step to make
informed decisions about a sequencing experiment.


At a Glance
-----------

Sentinel is implemented as a `MongoDB-based <https://www.mongodb.org/>`_ database which is exposed through an HTTP
`RESTful <https://en.wikipedia.org/wiki/Representational_state_transfer>`_ interface. Users upload their sequencing
metrics in a single `JSON <https://en.wikipedia.org/wiki/JSON>`_ file (which we also call the summary file, since it
contains summary of a pipeline run). The uploaded JSON is then parsed and stored in the database.

The structure of the JSON file is very loosely defined. In principle it can be of any form, though Sentinel does require
that it conforms to a certain structure (see :doc:`devs_tutorial_schema` for the full requirements). Most
important is that Sentinel knows how to parse and store the particular JSON file. This entails extending the core
Sentinel methods with user-defined parsing code. Sentinel enforces the parsed objects through various interfaces, which
in turn makes a wide range of data format compatible for querying.

All uploaded JSON files are only accessible to the uploader and site administrators. The data points contained in the
JSON file however, are available to anybody with access to the HTTP endpoints. These data points are anonymized by
default. Only after (optional) authentication, can a user see the names of the data points.

