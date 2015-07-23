Creating Processors
===================

Having created the schema, let's now move on to implementing the processors. We will write two processors, the
runs processor for processing the raw run summaries, and the stats processor for querying the metrics. Before that, we
will first write the internal models for our samples, libraries, and the statistics containers.

We will put all of them inside the ``nl.lumc.sasc.sentinel.processors.maple`` package, since everything will be specific
for the `Maple` pipeline support.

.. note::

   Since we will be using the internal models for this part, it is useful to browse the ScalaDoc along the way. Link
   to the most recent ScalaDoc is available in the sidebar.


Internal Models
---------------

To start off, we first consider the types of object we need to define:

* For the run itself, we'll define a ``MapleRunRecord`` that subclasses ``nl.lumc.sasc.sentinel.models.BaseRunRecord``.
* For the samples, we'll define ``MapleSampleRecord`` that subclasses ``nl.lumc.sasc.sentinel.models.BaseSampleRecord``.
* Likewise, for the library, we'll define ``MapleLibRecord`` subclassing ``nl.lumc.sasc.sentinel.models.BaseLibRecord``.
* And finally, for the statistics, we'll define ``MapleStats`` for the single data points and ``MapleStatsAggr`` for
  aggregated data points.

The definitions of these objects are outlined below. Note that while we are defining these objects once per file,
you have the freedom to create them in one large file. The important thing is they have the correct package name
(``nl.lumc.sasc.sentinel.maple`` in this case).

MapleRunRecord
^^^^^^^^^^^^^^

Let's start with the first one: ``MapleRunRecord``. Open a ``MapleRunRecord.scala`` file in the directory and add the
following contents:

.. code-block:: scala
   :linenos:

    package nl.lumc.sasc.sentinel.processors.maple

    import java.util.Date

    import com.novus.salat.annotations.Key
    import org.bson.types.ObjectId

    import nl.lumc.sasc.sentinel.models.BaseRunRecord
    import nl.lumc.sasc.sentinel.utils.getUtcTimeNow

    case class MapleRunRecord(
      @Key("_id") runId: ObjectId,
      uploaderId: String,
      pipeline: String,
      sampleIds: Seq[ObjectId],
      libIds: Seq[ObjectId],
      creationTimeUtc: Date = getUtcTimeNow,
      runName: Option[String] = None,
      deletionTimeUtc: Option[Date] = None) extends BaseRunRecord

From the definition above, you can already notice a few properties :

    1. Our run record stores most of its IDs as ``ObjectId``, which is the default ID type for MongoDB databases. The
       uploader ID is kept as a ``String`` for easy reference later on.

    2. We also store the date when the record is created in ``creationTimeUtc``. There is a default method that fetches
       the time (notice the implies it should be UTC+0) that we use from the ``utils`` subpackage.

    3. There is also a ``deletionTimeUtc`` attribute that stores when the record is deleted. The default is set to
       ``None``, since when an object is created it is not yet deleted.

One thing to note is the ``@Key`` annotation from the ``salat`` package. The ``MapleRunRecord`` is a Scala
representation of a MongoDB document. All MongoDB documents store its ID in the ``_id`` field by default. This is not
so descriptive for us, however, so when a MongoDB document of the run record is made into a Scala object, we put its
``_id`` attribute as the ``runId`` attribute. This is what the ``@Key`` annotation does: it marks the mapping between
a MongoDB document attribute and the actual Scala object attribute.

MapleSampleRecord
^^^^^^^^^^^^^^^^^

Now let's move on to the sample record definition. In the same directory, create the ``MapleSampleRecord.scala`` file
with the following contents:

.. code-block:: scala
   :linenos:

    package nl.lumc.sasc.sentinel.processors.maple

    import java.util.Date

    import com.novus.salat.annotations.Key
    import org.bson.types.ObjectId

    import nl.lumc.sasc.sentinel.models.BaseSampleRecord
    import nl.lumc.sasc.sentinel.utils.getUtcTimeNow

    case class MapleSampleRecord(
      uploaderId: String,
      runId: ObjectId,
      sampleName: Option[String] = None,
      runName: Option[String] = None,
      creationTimeUtc: Date = getUtcTimeNow,
      @Key("_id") id: ObjectId = new ObjectId) extends BaseSampleRecord

It looks almost similar to ``MapleRunRecord``, with the following differences:

    1. Notice there is no ``deletionTimeUtc`` attribute. This is because when sample records are removed from the
       database, Sentinel removes it completely and does not keep a record of which samples are removed. This is mainly
       because Sentinel never shows the sample document in the HTTP interface, so it is free to add and remove samples.
       The run record, on the other hand, are shown to users, and sometimes it is useful to keep track of ones that
       have been deleted.

    2. In addition to the ``runName``, we now also store ``sampleName``.

MapleLibRecord
^^^^^^^^^^^^^^

Next up, is the library, which is also similar to the previous two records:

.. code-block:: scala
   :linenos:

    package nl.lumc.sasc.sentinel.processors.maple

    import java.util.Date

    import com.novus.salat.annotations.Key
    import org.bson.types.ObjectId

    import nl.lumc.sasc.sentinel.models.BaseLibRecord
    import nl.lumc.sasc.sentinel.utils.getUtcTimeNow

    case class MapleLibRecord(
      stats: MapleStats,
      isPaired: Boolean = true,
      uploaderId: String,
      runId: ObjectId,
      libName: Option[String] = None,
      sampleName: Option[String] = None,
      runName: Option[String] = None,
      creationTimeUtc: Date = getUtcTimeNow,
      @Key("_id") id: ObjectId = new ObjectId) extends BaseLibRecord

Again, this is almost the same as the ``MapleSampleRecord`` definition, except:

    1. There is a yet-to-be defined type: ``MapleStats``. This is our actual metrics container and we will define it
       below. Don't worry if your IDE is showing that the definition is missing or that you can not compile at this
       point.

    2. There is an attribute called ``isPaired``, which as you can guess, denotes whether the library comes from
       paired-end sequencing or not. Since `Maple` handles paired-end files, we can set this definition by default to
       ``true``.

    2. And finally, we now see an additional name attribute: ``libName``, for storing the library name.

MapleStats & MapleStatsAggr
^^^^^^^^^^^^^^^^^^^^^^^^^^^

Finally, we come to the definition of our actual metrics container:

.. code-block:: scala
   :linenos:

    package nl.lumc.sasc.sentinel.processors.maple

    import nl.lumc.sasc.sentinel.models.{ DataPointAggr, DataPointLabels }

    case class MapleStats(
      labels: Option[DataPointLabels] = None,
      nReadPairsFastq: Long,
      nReadsBam: Long,
      nReadsAligned: Long)

    case class MapleStatsAggr(
      nReadPairsFastq: DataPointAggr,
      nReadsBam: DataPointAggr,
      nReadsAligned: DataPointAggr)

The two definitions above does not subclass anything since they practically can be anything. You'll notice that the
statistics in our JSON summary is present there as attributes of the two classes. They are used for different purposes,
however:

    1. ``MapleStats`` is meant for storing single data points. Our example summary earlier has two data points, one
       for each sample.

    2. ``MapleStatsAggr`` is meant for storing aggregate statistics, which include the average, min value, max value,
       median, and standard deviation. All of these are already defined in the ``DataPointsAggr`` case class and you
       can use them straight away.

That concludes our first part of the processors tutorial! Now we can move on the the actual implementation of the
processors.

The Runs Processor
------------------

First up is the runs processor. We'll start with the general outline first, and then define the two functions required
for processing the actual uploaded run summary file:

.. code-block:: scala
   :linenos:

    package nl.lumc.sasc.sentinel.processors.maple

    import scala.util.Try

    import org.bson.types.ObjectId
    import org.json4s._
    import org.scalatra.servlet.FileItem

    import nl.lumc.sasc.sentinel.Pipeline
    import nl.lumc.sasc.sentinel.db._
    import nl.lumc.sasc.sentinel.models.User
    import nl.lumc.sasc.sentinel.processors.RunsProcessor
    import nl.lumc.sasc.sentinel.utils.implicits._
    import nl.lumc.sasc.sentinel.utils.ValidationAdapter

    class MapleRunsProcessor(mongo: MongodbAccessObject) extends RunsProcessor(mongo)
        with UnitsAdapter[MapleSampleRecord, MapleLibRecord]
        with ValidationAdapter {

      // Pipeline name as string
      def pipelineName = "maple"

      // Our validator object
      val validator = createValidator("/schemas/maple.json")

      // Where the sample, library, and statistics are extracted from the raw JSON
      def extractUnits(runJson: JValue, uploaderId: String,
                       runId: ObjectId): (Seq[MapleSampleRecord], Seq[MapleLibRecord]) = ???

      // Where the uploaded JSON file is processed
      def processRun(fi: FileItem, user: User, pipeline: Pipeline.Value) = ???
    }

We can already see some new classes and objects being used there:

    1. The ``MapleRunsProcessor`` class, being the main class that subclasses ``RunsProcessor``. Notice the database
       access object ``MongodbAccessObject`` being passed into the constructor. This object encapsulates access to the
       database and enables the processor object to create, read, update, and delete database records.

    2. The ``UnitsAdapter`` trait, being used to extend the main processor with our earlier record definitions. This
       adapter endows our processor with the functions to process runs and libraries, as we will see later when we
       define the ``processRun`` function.

    3. The ``ValidationAdapter`` trait, being used to add validation capabilities to the processor. The
       ``createValidator`` function used a few lines afterwards is defined in the adapter. We also see that the function
       is supplied with the resource path to the schema that we also defined earlier.

    4. Notice also that there is a new type ``Pipeline.Value`` being used as an argument to the ``processRun`` function.
       This is a Scala enum type that we need to define for our pipeline. To add an enum for the `Maple` pipeline,
       open the root Sentinel ``package.scala`` file and find the ``object Pipeline extends Enumeration``. Inside the
       object, add a new entry for `Maple` as follows:

       .. code-block:: scala

          /** Supported pipeline summary schemas */
          object Pipeline extends Enumeration {
            type Pipeline = Value
            // ...
            val Maple = Value("maple")
          }

Now we're ready to take a stab at defining the ``extractUnits`` pipeline. Generally, there is at least one function to
extract the samples and libraries defined in a runs processor. This is completely up you (you can even define it inside
the ``processRun`` function if you wish). Here, we define it as a separate function so the structure is clearer.

Here's our definition of ``extractUnits``:

.. code-block:: scala
   :linenos:

      // Where the sample, library, and statistics are extracted from the raw JSON
      def extractUnits(runJson: JValue, uploaderId: String,
                       runId: ObjectId): (Seq[MapleSampleRecord], Seq[MapleLibRecord]) = {

        val runName = (runJson \ "run_name").extractOpt[String]

        def makeStats(libJson: JValue) =
          MapleStats(
            nReadPairsFastq = (libJson \ "nReadPairsFastq").extract[Long],
            nReadsBam = (libJson \ "nReadsBam").extract[Long],
            nReadsAligned = (libJson \ "nReadsAligned").extract[Long])

        def makeLib(sampleName: String, libName: String, libJson: JValue) =
          MapleLibRecord(
            stats = makeStats(libJson),
            uploaderId = uploaderId,
            runId = runId,
            libName = Option(libName),
            sampleName = Option(sampleName),
            runName = runName)

        def makeSample(sampleName: String, sampleJson: JValue) =
          MapleSampleRecord(uploaderId, runId, Option(sampleName), runName)

        val parsed = (runJson \ "samples").extract[Map[String, JValue]].view
          .map { case (sampleName, sampleJson) =>
            val sample = makeSample(sampleName, sampleJson)
            val libs = (sampleJson \ "libraries").extract[Map[String, JValue]]
              .map { case (libName, libJson) => makeLib(sampleName, libName, libJson) }
              .toSeq
            (sample, libs)
          }.toSeq
        (parsed.map(_._1), parsed.map(_._2).flatten)
      }

Our function takes as its input the object representing the entire summary JSON, the uploader name, and the database
ID of the stored raw summary file. It returns two ``Seq`` containers, one filled with our sample records and the other
filled with the library records.

Inside, you'll notice that we also have defined three helper functions: ``makeStats`` for creating the ``MapleStats``
object, ``makeLib`` for the library record, and ``makeSample`` for the sample record. All three functions are used
in the last part, where we work directly on the supplied run JSON object. There, you'll see that this allows us to
deconstruct the nested sample-library structure into two ``Seq``s: a ``Seq`` of samples and a ``Seq`` of libraries.

Again, although in theory you may not need the helper functions, we prefer to have them defined separately for
readability.

.. note::

    Sentinel uses the `JSON4S <http://json4s.org/>`_ library for processing JSON files. You don't need to install the
    library yourself as it comes bundled as a Sentinel dependency. If the JSON parsing methods seem unfamiliar to you,
    we recommend going over their quick tutorial first.

Finally, having defined the ``extractUnits`` function we are now ready for the final function: ``processRun``. Here
is the definition:

.. code-block:: scala
   :linenos:

    // Where the uploaded JSON file is processed
    def processRun(fi: FileItem, user: User, pipeline: Pipeline.Value) =
      for {
        // Read input stream and checks whether the uploaded file is unzipped or not
        (byteContents, unzipped) <- Try(fi.readInputStream())
        // Make sure it is JSON
        runJson <- Try(parseAndValidate(byteContents))
        // Store the raw file in our database
        fileId <- Try(storeFile(byteContents, user, pipeline, fi.getName, unzipped))
        // Extract run, samples, and libraries
        (samples, libs) <- Try(extractUnits(runJson, user.id, fileId))
        // Store samples
        _ <- Try(storeSamples(samples))
        // Store libraries
        _ <- Try(storeLibs(libs))
        // Create run record
        run = MapleRunRecord(fileId, user.id, pipelineName, samples.map(_.id), libs.map(_.id))
        // Store run record into database
        _ <- Try(storeRun(run))
      } yield run

This function is relatively small (it only contains one for-comprehension), but it packs a lot of punch. You'll notice
many new functions being used here. They are all functions defined either in the base processor class or one of the
adapters that we mix in. Most of the functions themselves should be self-explanatory. We'll note the seemingly unusual
ones and the general structure of the for-comprehension:

    1. The first thing you'll notice is the presence of all the ``Try`` wraps around our function calls. The main reason
       for this is that we want to fail fast when any one of our function call fails. In the event of failure, the
       ``Try`` object will encapsulate the final result of the failed function (an exception, most of the time) and then
       skips the remaining functions calls. It is similar to throwing an exception, with the added bonus denoting the
       possibility of failure in the type itself. Notice also that the code looks much cleaner, without any nested
       ``try-catch`` blocks.

    2. Some of the function calls' return values are simply an underscore. This means we are not using whatever
       the function is returning. Instead we are only interested in its side-effect. Indeed, all the functions whose
       result we discard are database storage functions.

And that's it! You now have fully-functioning runs processor.

The Stats Processor
-------------------

The final step is defining the stats processor. This step will be relatively simpler than the inputs processor, since
Sentinel now has a better idea of what to expect from the database records:

.. code-block:: scala
   :linenos:

    package nl.lumc.sasc.sentinel.processors.maple

    import nl.lumc.sasc.sentinel.db.MongodbAccessObject
    import nl.lumc.sasc.sentinel.processors.StatsProcessor

    class MapleStatsProcessor(mongo: MongodbAccessObject) extends StatsProcessor(mongo) {

      def pipelineName = "maple"

      // Attribute names of the statistics container
      val statsAttrs = Seq("nReadPairsFastq", "nReadsBam", "nReadsAligned")

      def getMapleStats = getLibStats[MapleStats]("stats") _

      def getMapleAggrStats = getLibAggrStats[MapleStatsAggr]("stats", statsAttrs) _
    }

You can see that the processor definition is already much shorter than its counterpart's. Still, it is useful to go
through what we have done here:

    1. The stats processor class is similarly instantiated with a database access object, like the runs processor.

    2. We also need to define at ``Seq[String]`` denoting the attribute names of our metrics container (the
       ``MapleStats`` we defined earlier). This may be changed in future versions when we can pull the attribute
       names directly from the case class definition.

    3. Finally, the two functions we defined afterwards relies on partially defining the ``getLibStats`` and
       ``getLibAggrStats`` functions. They are used, respectively, to query single data points and aggregated
       statistics.

With this, we have completely defined the required processors and internal data models. The next step is to expose these
processors via the HTTP controllers.
