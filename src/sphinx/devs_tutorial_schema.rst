Defining Run Summaries
======================

What Sentinel Expects
---------------------

In principle, Sentinel accepts any kind of JSON structure. Most important, however, is that a single JSON run summary
file contains a full run, with at least one sample containing at least one library. Usually this means storing the
samples as properties of a run object, and libraries as properties of a sample, although you are not limited to this
structure.

.. note::

    The exact definitions of samples, libraries, and runs that Sentinel uses are listed in
    :doc:`users_terminologies`.

What to Store
-------------

Having decided on the pipeline name, we need to define first what the pipeline will store. The following metrics should
be simple enough for our purposes:

    * Total number of `FASTQ <https://en.wikipedia.org/wiki/FASTQ_format>`_ reads.
    * Total number of reads in the aligned `BAM <http://genome.ucsc.edu/goldenpath/help/bam.html>`_ file.
    * Number of mapped reads in the BAM file.

We'll also store a name of the pipeline run (which will differ per pipeline run) so we can trace back our runs.

The JSON run summary file looks something like this:

.. code-block:: javascript

    {
      "run_name": "MySimpleRun",
      "samples": {
        "sample_A": {
          "libraries": {
            "lib_1": {
              "nReadPairsFastq": 10000,
              "nReadsBam": 10000,
              "nReadsAligned": 7500
            }
          }
        },
        "sample_B": {
          "libraries": {
            "lib_1": {
              "nReadPairsFastq": 20000,
              "nReadsBam": 20000,
              "nReadsAligned": 15000
            }
          }
        }
      }
    }

Note the nesting structure of the run summary above. We can see that within that single run, there are two samples
(``sample_A`` and ``sample_B``) and each sample contains a library called ``lib_1``. Within the library, we see the
actual metrics that we want to store: ``nReadPairsFastq``, ``nReadsBam``, and ``nReadsAligned``.

You can have a different structure (perhaps nesting everything under a ``run`` attribute or having the ``run_name``
attribute named something else). It is really up to you in the end (everybody has their own way of running these
pipelines after all).

Your Pipeline Schema
--------------------

Writing a schema to validate your run summaries is strongly recommended, though it is not required. Having a schema
makes it easier to check for run time errors and prevent incorrect data from being processed. Sentinel uses the
`JSON schema <http://json-schema.org/>`_ specifications to define run summary schemas. You can head over to their
site to see the full specification.

For our `Maple` pipeline, we'll use the schema already defined below. Save this as a file in the
``src/main/resources/schemas`` directory with the name ``map.json``.

.. code-block:: javascript

    {
      "$schema": "http://json-schema.org/draft-04/schema#",
      "title": "Maple pipeline schema",
      "description": "Schema for Maple pipeline runs",
      "type": "object",
      "required": [ "samples", "run_name" ],

      "properties": {

        "run_name": { "type": "string" },

        "samples": {
          "description": "All samples analyzed in this run",
          "type": "object",
          "minItems": 1,
          "additionalProperties": { "$ref": "#/definitions/sample" }
        }
      },

      "definitions": {

        "sample": {
          "description": "A single Maple sample",
          "type": "object",
          "required": [ "libraries" ],

          "properties": {

            "libraries": {
              "description": "All libraries belonging to the sample",
              "type": "object",
              "minItems": 1,
              "additionalProperties": { "$ref": "#/definitions/library" }
            }
          }
        },

        "library": {
          "description": "A single Maple library",
          "type": "object",
          "required": [ "nReadPairsFastq", "nReadsBam", "nReadsAligned" ],

          "properties": {
            "nReadPairsFastq": { "type": "integer" },
            "nReadsBam": { "type": "integer" },
            "nReadsAligned": { "type": "integer" }
          }
        }
      }
    }

If the above code looks daunting, don't worry. You can copy-paste the code as-is and try to understand the JSON schema
specifications later on. If you want to play around with the schema itself, there is an online validator available
`here <http://jsonschemalint.com/draft4/>`_. You can copy-paste both JSON documents above there and try tinkering with
them.
