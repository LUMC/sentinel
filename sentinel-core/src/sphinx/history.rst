History
=======


Version 0.2
------------

Release 0.2.0
^^^^^^^^^^^^^

`release date: TBD`


Release 0.2.0-beta1
^^^^^^^^^^^^^^^^^^^

`release date: January 25 2016`

First beta release of the 0.2 version.

The majority of the change in this version compared to the previous version
is internal. Some of the more important changes are:

    * The ``sentinel`` package has now been split into ``sentinel``,
      containing generic functionalities and ``sentinel-lumc`` containing
      LUMC-specific pipeline support. This separation is not yet complete,
      since a part of the user configuration still refers to LUMC-specific
      functionalities. Instead, it is meant to pave way for a complete
      separation which is expected to happen in future versions.

    * A new type for expected errors called ``ApiPayload`` was created to
      replace the previous ``ApiMessage`` case class which contains only
      error messages. In addition to error messages, ``ApiPayload`` may also
      contain a function that returns a specific HTTP error code. This allows
      for a given error message to always be tied to a specific HTTP error
      code.

    * The main pipeline upload processing function in ``RunsProcessorr``
      has been renamed to ``processRunUpload`` and is now expected to return
      ``Future[ApiPayload \/ RunRecord]`` instead of ``Try[BaseRunRecord]``.
      Related to this change, this version also makes heavier use of the
      scalaz library, most notably its disjunction type. This allows for a
      nicer composition with ``Future``, which has resulted in changes across
      database-related functions to use ``Future`` as well.

    * The base functions in the ``StatsProcessor`` abstract class underwent
      a major refactor. In this version, the same functionality was achieved
      using only two generic classes ``getStats`` and ``getAggregateStats``.
      Future versions are expected to bring additional changes most notably
      to the MapReduce step, since newer versions of MongoDB supports most
      (if not all) of the metrics using its aggregation framework only.

There are also some indirect changes related to the code:

    * Sentinel now comes with minimum deployment setup using Ansible. This can
      be optionally run in a Vagrant VM with the provided Vagrantfile.

    * The required MongoDB version is now 3.2 instead of 3.0.

The complete list of changes are available in the commit logs.

The user-visible changes are quite minimum. Aside from some LUMC
pipeline-specific changes, the most visible changes are:

    * The `library` nomenclature has been replaced with `read group`. The
      previous use of `library` was incorrect and the current one is more
      in-line with popular next-generation sequencing tools.

    * There is now a URL-parameter called ``displayNull`` which allows for
      clients to view missing JSON attributes as ``null`` when this value is
      set to ``true``. When set to ``false``, which is the default value,
      missing attributes will be omitted completely from the returned JSON
      payload.


Version 0.1
------------

Release 0.1.3
^^^^^^^^^^^^^

`release date: October 13 2015`

Bug fix (upstream) release:

    * Update CollectInsertSizeMetrics summary object. In some cases, it is
      possible to have a single end alignment still output a
      CollectInsertSizeMetrics object in the summary file wit null values,
      as opposed to not having the object at all.


Release 0.1.2
^^^^^^^^^^^^^

`release date: July 14 2015`

Bug fix (upstream) release:

    * Improve summary file parsing for Picard CollectAlignmentSummaryMetrics
      numbers. In some cases, the number of total reads and aligned reads
      may be 0. In that case, we use the BiopetFlagstat value instead.


Release 0.1.1
^^^^^^^^^^^^^

`release date: July 11 2015`

Bug fix release:

    * Fix bug caused by the Gentrap summary format having a different
      executable entries for JAR and non-JAR files.

    * Documentation improvements (typos, etc.).

Release 0.1.0
^^^^^^^^^^^^^

`release date: July 6 2015`

New version:

    * First release of Sentinel, with support of the Gentrap pipeline.
