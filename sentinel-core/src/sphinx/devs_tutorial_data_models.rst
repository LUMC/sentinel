Defining the Data Models
========================

Having created the schema, let's now move on to implementing the processors. We will write two processors, the
runs processor for processing the raw run summaries, and the stats processor for querying the metrics. Before that, we
will first write the internal models for our samples, libraries, and the statistics containers.

We will put all of them inside the ``nl.lumc.sasc.sentinel.processors.maple`` package, since everything will be specific
for the `Maple` pipeline support.

.. note::

   Since we will be using the internal models for this part, it is useful to browse the ScalaDoc along the way. Link
   to the most recent ScalaDoc is available in the sidebar.

To start off, we first consider the types of object we need to define:

* For the run itself, we'll define a ``MapleRunRecord`` that subclasses ``nl.lumc.sasc.sentinel.models.BaseRunRecord``.
* For the samples, we'll define ``MapleSampleRecord`` that subclasses ``nl.lumc.sasc.sentinel.models.BaseSampleRecord``.
* Likewise, for the library, we'll define ``MapleReadGroupRecord`` subclassing
  ``nl.lumc.sasc.sentinel.models.BaseReadGroupRecord``.
* And finally, for the statistics, we'll define ``MapleStats`` for the single data points and ``MapleStatsAggr`` for
  aggregated data points.

The definitions of these objects are outlined below. Note that while we are defining these objects once per file,
you have the freedom to create them in one large file. The important thing is they have the correct package name
(``nl.lumc.sasc.sentinel.maple`` in this case).

MapleRunRecord
--------------

Let's start with the first one: ``MapleRunRecord``. Open a ``MapleRunRecord.scala`` file in the appropriate directory
and add the following contents (you can use your own package name, if you prefer):

.. code-block:: scala
   :linenos:

    package nl.lumc.sasc.sentinel.exts.maple

    import java.util.Date

    import org.bson.types.ObjectId

    import nl.lumc.sasc.sentinel.models._
    import nl.lumc.sasc.sentinel.utils.utcTimeNow

    /** Container for a Maple run. */
    case class MapleRunRecord(
      runId: ObjectId,
      uploaderId: String,
      pipeline: String,
      sampleIds: Seq[ObjectId],
      readGroupIds: Seq[ObjectId],
      runName: Option[String] = None,
      deletionTimeUtc: Option[Date] = None,
      creationTimeUtc: Date = utcTimeNow) extends BaseRunRecord

From the definition above, you can already notice a few properties :

    1. Our run record stores most of its IDs as ``ObjectId``, which is the default ID type for MongoDB databases. The
       uploader ID is kept as a ``String`` for later use.

    2. We also store the date when the record is created in ``creationTimeUtc``. We use the ``utctTimeNow`` function
       from the ``utils`` package to get the current UTC time.

    3. There is also a ``deletionTimeUtc`` attribute that stores when the record is deleted. The default is set to
       ``None``, since when an object is created it is not yet deleted.

MapleSampleRecord
-----------------

Now let's move on to the sample record definition. In the same file, add the following ``MapleSampleRecord`` definition:

.. code-block:: scala
   :linenos:

    /** Container for a single Maple sample. */
    case class MapleSampleRecord(
      stats: MapleSampleStats,
      uploaderId: String,
      runId: ObjectId,
      sampleName: Option[String] = None,
      runName: Option[String] = None) extends BaseSampleRecord

In contrast to ``MapleRunRecord``, our sample record can be quite short since it needs to store less information. The
actual metrics itself will be stored in a yet-defined ``MapleSampleStats`` object, under the ``stats`` attribute.
The name ``stats`` itself is free-form, you are free to choose the attribute name for your metrics object. You can even
define multiple attributes storing different statistics. This is useful for storing different types of metrics on the
same level, for example storing alignment metrics and variant calling metrics for a given sample.

Notice also that there is no ``deletionTimeUtc`` attribute. This is because when sample records are removed from the
database, Sentinel removes it completely and does not keep a record of which samples are removed. This is mainly
because Sentinel never shows the sample document in the HTTP interface, so it is free to add and remove samples. The
run record, on the other hand, are shown to users, and sometimes it is useful to keep track of ones that have been
deleted.

Finally, notice that now we store the sample name under ``sampleName`` in addition to the run name.

MapleReadGroupRecord
--------------------

Next up, is the read group record:

.. code-block:: scala
   :linenos:

    /** Container for a single Maple read group. */
    case class MapleReadGroupRecord(
      stats: MapleReadGroupStats,
      uploaderId: String,
      runId: ObjectId,
      isPaired: Boolean = true,
      readGroupName: Option[String] = None,
      sampleName: Option[String] = None,
      runName: Option[String] = None) extends BaseReadGroupRecord

This is almost similar to ``MapleSampleRecord``, except:

    1. There is an attribute called ``isPaired``, which as you can guess, denotes whether the library comes from
       paired-end sequencing or not. Since `Maple` handles paired-end files, we can set this definition by default to
       ``true``.

    2. There is an additional name attribute: ``readGroupName``, for storing the read group name.

Statistics container
--------------------

Finally, we come to the definition of our actual metrics container. Since we store the metrics on two levels, sample and
read group, we need to define the metrics container for each of these levels. This is what they look like:

.. code-block:: scala
   :linenos:

    /** Container for a single Maple sample statistics. */
    case class MapleSampleStats(
      nSnps: Long,
      labels: Option[DataPointLabels] = None) extends LabeledStats

    /** Container for a single Maple read group statistics. */
    case class MapleReadGroupStats(
      nReadsInput: Long,
      nReadsAligned: Long,
      labels: Option[DataPointLabels] = None) extend LabeledStats

For each level, we define a case class that extends ``LabeledStats``. This trait enforces the use of the ``labels``
attribute to tag a particular metrics data point with labels. For any given data point, it must at least be labeled
with the database ID of the run record (``runId``). Optionally, it may also be labeled with the run name, read group
name and/or sample name. All this is contained within the ``DataPointLabels`` instance stored in the ``labels``
attributed.

The objects defined above stores single data points of our metrics. They are instantiated for each sample or read group
that is present in the uploaded JSON summary file. We enforce the use of a case class here based on several reasons:

    1. To minimize potential runtime errors, since the case class ensures our stored metrics are all typed. The type
       information is also used to ensure user-defined metrics works well with the Sentinel core methods.

    2. Case classes play nicely with Swagger's automatic API spec generation. Supplying these as type parameters in our
       controllers later on results in Swagger generating the JSON object definitions.

In addition to the two case classes defined above, we may also want to define the following case classes for storing
aggregated data points instead of single data points:

.. code-block:: scala
   :linenos:

    /** Container for aggregated Maple sample statistics. */
    case class MapleSampleStatsAggr(nSnps: DataPointAggr)

    /** Container for aggregated Maple read group statistics. */
    case class MapleReadGroupStatsAggr(
      nReadsInput: DataPointAggr,
      nReadsAligned: DataPointAggr)

You'll notice that these are almost similar to the previous case classes, except:

    1. All the attribute types are ``DataPointAggr``.

    2. There are no labels anymore.

The ``DataPointAggr`` is another case class that contains aggregated statistics like `avg`, `max`, or `median`. It is
likely that we will use macros to generate these in future Sentinel versions, since they are very similar to the case
classes that define the single data points.

That concludes our first part of the processors tutorial! Now we can move on the the actual implementation of the
processors. Before you go on, however, we would like to note that the processors make use of Scalaz's disjunction type
(popularly known as ``\/``), its ``EitherT`` type, and the standard library ``Future`` type. If these do not sound
familiar, we strongly recommend that you go over our short guides on them first: :doc:`devs_tutorial_composition` and
:doc:`devs_tutorial_async`. Otherwise, feel free to go to the processors tutorial: :doc:`devs_tutorial_processors`
directly.
