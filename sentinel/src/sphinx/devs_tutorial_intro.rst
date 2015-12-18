Extending Sentinel
==================

Sentinel can be extended with support for capturing metrics of additional pipelines. Adding support for new pipeline
metrics can roughly be divided into three steps:

1. Defining the JSON run summary file, preferably with a schema document.
2. Adding the internal objects for the pipeline metrics, which include the runs processors, stats processors, sample and
   read group records, and other statistics container.
3. Updating the HTTP controllers with new endpoints.

.. note::

    Before adding new pipelines, it is a good idea to familiarize yourself with the project setup, internal data
    models, and Scalatra first. These are outlined in :doc:`devs_design` and :doc:`devs_codebase`. There is also an
    internal API documentation (link in the sidebar) for all the internal objects used by Sentinel.

This part of the documentation will walk you through implementing support for a simple pipeline. The pipeline takes one
paired-end sequencing file, aligns it to a genome, and calls SNPs on the aligned data. It is meant to be simple as
it is meant to highlight the minimum things you need to implement for supporting the pipeline. Later on, you should be
able to implement support for your own pipeline easily.

.. note::

    We will not be implementing the actual code for the pipeline itself, rather we will start from after the pipeline
    has finished running (hypothetically).

We'll start off with a name: since our pipeline is quite simple, let's call it the Minimum (variant calling) Analysis
PipeLinE (or `Maple` for short).
