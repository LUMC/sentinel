Terminologies
=============

In the context of next-generation sequencing, the same words are often used to refer to multiple things. Here we list
terms that are used repeatedly in the Sentinel documentation.

Read Group
----------

A read group denotes a single execution / run of an NGS machine. It may consist of a single sequence file (in the case
of single-end sequencing) or two sequence files (paired-end sequencing). Read groups are often used when
a single sample needs to be sequenced more than once (e.g. because its sequencing depth is less than desired) or when
one sample is sequenced in different lanes.

Sample
------

A sample denotes a single experimental unit being investigated. It may be RNA isolated from a single treatment, DNA
isolated from a single individual, or something else, depending on the experiment. One sample may be sequenced multiple
times (for example when the sequencing depth is inadequate). In this case, the sample would be composed of multiple
read groups. It follows that a sample has at least one read group.

Run
---

A run is a single data analysis pipeline execution. It may contain one or multiple samples, each possibly containing
one or more libraries, depending on the data analysis pipeline.

Run Summary File
----------------

A run summary file is a JSON file that contains metrics of a run. This is the file uploaded to Sentinel. It is up to
you/your pipeline to create this file. Our other project, the
`Biopet pipeline framework <https://github.com/biopet/biopet>`_, is an example of pipelines that generate such JSON
files.
