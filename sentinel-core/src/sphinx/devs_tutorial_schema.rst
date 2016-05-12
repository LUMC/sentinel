Describing Run Summaries
========================

What Sentinel Expects
---------------------

In principle, Sentinel accepts any kind of JSON structure. Most important, however, is that a single JSON run summary
file contains a full run, with at least one sample containing at least one read group. Usually this means storing the
samples as properties of a run object, and read groups as properties of a sample, although you are not limited to this
structure.

.. note::

    The exact definitions of samples, read groups, and runs that Sentinel uses are listed in
    :doc:`users_terminologies`.

What to Store
-------------

Having decided on the pipeline name, we need to outline first what the pipeline will store. The following metrics should
be simple enough for our purposes:

    * Total number of `FASTQ <https://en.wikipedia.org/wiki/FASTQ_format>`_ reads.
    * Total number of reads in the `BAM <http://genome.ucsc.edu/goldenpath/help/bam.html>`_ file (mapped and unmapped).
    * Number of mapped reads in the BAM file.

We'll also store a name of the pipeline run (which will differ per pipeline run) so we can trace back our runs.

Our hypothetical pipeline can analyze multiple samples and multiple read groups at the same time. It generates a JSON
run summary file like this:

.. code-block:: javascript

    {
      "runName": "MyRun",
      "samples": {
        "sampleA": {
          "readGroups": {
            "rg1": {
              "nReadsInput": 10000,
              "nReadsAligned": 7500
            }
          },
          "nSnps": 200
        },
        "sampleB": {
          "readGroups": {
            "rg1": {
              "nReadsInput": 20000,
              "nReadsAligned": 15000
            }
          },
          "nSnps": 250
        }
      }
    }

Note the nesting structure of the run summary above. We can see that within that single run, there are two samples
(``sampleA`` and ``sampleB``) and each sample contains a read group called ``rg1``. Within the read group, we see the
actual metrics that we want to store: ``nReadsInput`` and ``nReadsAligned``. On the sample-level, we store the number
of SNPs called for that sample in `nSnps`.

You are free to decide on your own structure. Perhaps you don't really care about read-group level statistics, so your
pipeline omits them. Or perhaps your pipeline only runs a single sample, so you can put all metrics in the top level.
You could also store the samples and/or read groups in an array instead of a JSON object, if you prefer. It is really
up to you in the end (everybody has their own way of running these pipelines after all). The important thing is that
your soon-to-be-written JSON reader understands the structure.


Your Pipeline Schema
--------------------

Writing a schema to validate your run summaries is strongly recommended, though it is not required. Having a schema
makes it easier to check for run time errors and prevent incorrect data from being processed. Sentinel uses the
`JSON schema <http://json-schema.org/>`_ specifications to define run summary schemas. You can head over to their
site to see the full specification.

For our `Maple` pipeline, we'll use the schema already defined below. Save this as a file in the
``src/main/resources/schemas`` directory with the name ``maple.json``.

.. code-block:: javascript

    {
      "$schema": "http://json-schema.org/draft-04/schema#",
      "title": "Maple pipeline schema",
      "description": "Schema for Maple pipeline runs",
      "type": "object",
      "required": [ "samples", "runName" ],

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
          "required": [ "readGroups", "nSnps" ],

          "properties": {

            "readGroups": {
              "description": "All read groups belonging to the sample",
              "type": "object",
              "minItems": 1,
              "additionalProperties": { "$ref": "#/definitions/readGroup" }
            },

            "nSnps": {
              "description": "Number of SNPs called",
              "type": "integer"
            }
          }
        },

        "readGroup": {
          "description": "A single Maple readGroup",
          "type": "object",
          "required": [ "nReadsInput", "nReadsAligned" ],

          "properties": {
            "nReadsInput": { "type": "integer" },
            "nReadsAligned": { "type": "integer" }
          }
        }
      }
    }

If the above code looks daunting, don't worry. You can copy-paste the code as-is and try to understand the JSON schema
specifications later on. If you want to play around with the schema itself, there is an online validator available
`here <http://jsonschemalint.com/draft4/>`_. You can copy-paste both the JSON summary and JSON schema examples above
there and try tinkering with them.