History
=======


Releases 0.2
------------

Version 0.2.0
^^^^^^^^^^^^^

`release date: TBD`


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
