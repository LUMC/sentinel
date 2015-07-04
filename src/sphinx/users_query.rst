Querying Metrics
================

Having uploaded a run summary file, we can now start querying Sentinel for the metrics. In general, metrics can be
queried in the data points level (where each data point may represent a library or sample, depending on the pipeline
and provided parameters).

At the moment, only `Gentrap <https://git.lumc.nl/biopet/biopet>`_, our general purpose RNA-seq analysis pipeline can
be queried for metrics. Further releases will support other in-house pipelines.

Gentrap
-------

Gentrap is a general RNA-seq pipeline meant for producing various abundance measurements (read counts, FPKM counts,
etc.). It can run multiple samples in one go, each consisting of at least one library. Sentinel supports Gentrap by
providing the following metrics:

1. Sequence metrics (for :endpoint:`singular data points <stats/statsGentrapSequencesGet>` and
   :endpoint:`aggregates <stats/statsGentrapSequencesAggregationsGet>`)

2. Alignment metrics (also for :endpoint:`singular data points <stats/statsGentrapAlignmentsGet>` and
   :endpoint:`aggregates <stats/statsGentrapAlignmentsAggregatesGet>`)

You can consult the respective API documentation for a more in-depth explanations of all the parameters and return
values.

