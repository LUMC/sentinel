Creating the Processors
=======================

In this section we will define the processor objects required for processing summary files from our Maple pipeline.
Recall that processors are the actual objects where pipeline support (that means summary file upload and statistics
querying) is implemented. There are two processors we need to define: a runs processor to process data uploaded by a
user and a stats processor to return statistics query.


The Runs Processor
------------------

First up is the runs processor. We mentioned earlier that it is meant for processing user uploads. This means extracting
all the values we need into various record objects we have defined :doc:`earlier </devs_tutorial_data_models>`.
Additionally, Sentinel also requires that the raw contents of the file be saved into the database. The purpose of this
is twofolds: to ensure duplicate files are not uploaded and to allow users to download their summary files again.
Sentinel also requires you to save the extracted record objects into the database. This is the actual metrics that will
be returned when a user queries for the metrics later on.

We will start with the general outline first and then define the functions required for processing the actual uploaded
run summary file. All of this will be in a file called ``MapleRunsProcessor.scala``

.. code-block:: scala

    package nl.lumc.sasc.sentinel.exts.maple

    import scala.concurrent._

    import org.bson.types.ObjectId
    import org.json4s.JValue

    import nl.lumc.sasc.sentinel.adapters._
    import nl.lumc.sasc.sentinel.models.User
    import nl.lumc.sasc.sentinel.processors.RunsProcessor
    import nl.lumc.sasc.sentinel.utils.{ ValidatedJsonExtractor, MongodbAccessObject }

    /**
     * Example of a simple pipeline runs processor.
     *
     * @param mongo MongoDB access object.
     */
    class MapleRunsProcessor(mongo: MongodbAccessObject)
            extends RunsProcessor(mongo)
            with ReadGroupsAdapter
            with ValidatedJsonExtractor {

    // Our code will be here
    }

Our runs processor extends the ``RunsProcessor`` abstract class that is instantiated by an object that provides access
to the database. It is further enriched by the ``ReadGroupsAdapter`` trait since we want to process data both on the
sample and read group level, and the ``ValidatedJsonExtractor`` since we want to parse and validate our incoming JSON.
It is important to note here that the ``RunsProcessor`` abstract class extends ``FutureMixin`` so we will need to define
an implicit ``ExecutionContext`` as well.

Let's now define some of the required abstract values and methods. We can start with two simple ones: the pipeline name
and the pipeline schema. The pipeline name is how the pipeline would be used in URL parameters
(that means it's safest if we only use alphanumeric characters) and the pipeline schema is the resource path to the
schema we defined earlier.

.. code-block:: scala

    ...

    class MapleRunsProcessor(mongo: MongodbAccessObject)
            extends RunsProcessor(mongo)
            with ReadGroupsAdapter
            with ValidatedJsonExtractor {

        /** Exposed pipeline name. */
        def pipelineName = "maple"

        /** JSON schema for incoming summaries. */
        def jsonSchemaUrls = Seq("/schema_examples/maple.json")
    }

Next is to define the ``Future`` execution contexts and the record objects. Aliasing the record objects in the runs
processor here is a requirement of both the ``RunsProcessor`` abstract class and the ``ReadGroupsAdapter`` trait.
It allows the generic methods in ``RunsProcessor`` to work with our custom record types.

.. code-block:: scala

    ...

    class MapleRunsProcessor(mongo: MongodbAccessObject)
            extends RunsProcessor(mongo)
            with ReadGroupsAdapter
            with ValidatedJsonExtractor {

        ...

        /** Implicit execution context. */
        implicit private def context: ExecutionContext =
            ExecutionContext.global

        /** Run records container. */
        type RunRecord = MapleRunRecord

        /** Sample-level metrics container. */
        type SampleRecord = MapleSampleRecord

        /** Read group-level metrics container. */
        type ReadGroupRecord = MapleReadGroupRecord
    }

Now we want to define how the record objects can be created from a parsed JSON. The exact implementation of this part
is completely up to you. You can do this with one function, two functions, or more. You can call the function anything
you want (so long as it does not interfere with the any parent trait methods). Basically, the details will differ
depending on the JSON file's structure.

In our case, one way to do this is using the ``extractUnits`` and a helper case class ``MapleUnits`` which will contain
``MapleSampleRecord`` and ``MapleReadGroupRecord`` objects. The implementation looks like this:

.. code-block:: scala

    ...

    class MapleRunsProcessor(mongo: MongodbAccessObject)
            extends RunsProcessor(mongo)
            with ReadGroupsAdapter
            with ValidatedJsonExtractor {

        ...

        /** Helper case class for storing records. */
        case class MapleUnits(
            samples: Seq[MapleSampleRecord],
            readGroups: Seq[MapleReadGroupRecord])

        /**
         * Extracts the raw summary JSON into samples and read groups containers.
         *
         * @param runJson Raw run summary JSON.
         * @param uploaderId Username of the uploader.
         * @param runId Database ID for the run record.
         * @return Two sequences: one for sample data and the other for read group data.
         */
        def extractUnits(runJson: JValue, uploaderId: String,
                         runId: ObjectId): = {

          /** Name of the current run. */
          val runName = (runJson \ "run_name").extractOpt[String]

          /** Given the sample name, read group name, and JSON section of the read group, create a read group container. */
          def makeReadGroup(sampleName: String, readGroupName: String, readGroupJson: JValue) =
            MapleReadGroupRecord(
              stats = MapleReadGroupStats(
                nReadsInput = (readGroupJson \ "nReadsInput").extract[Long],
                nReadsAligned = (readGroupJson \ "nReadsAligned").extract[Long]),
              uploaderId = uploaderId,
              runId = runId,
              readGroupName = Option(readGroupName),
              sampleName = Option(sampleName),
              runName = runName)

          /** Given the sample name and JSON section of the sample, create a sample container. */
          def makeSample(sampleName: String, sampleJson: JValue) =
            MapleSampleRecord(
              stats = MapleSampleStats(nSnps = (sampleJson \ "nSnps").extract[Long]),
              uploaderId, runId, Option(sampleName), runName)

          /** Raw sample and read group containers. */
          val parsed = (runJson \ "samples").extract[Map[String, JValue]].view
            .map {
              case (sampleName, sampleJson) =>
                val sample = makeSample(sampleName, sampleJson)
                val readGroups = (sampleJson \ "readGroups").extract[Map[String, JValue]]
                  .map { case (readGroupName, readGroupJson) => makeReadGroup(sampleName, readGroupName, readGroupJson) }
                  .toSeq
                (sample, readGroups)
            }.toSeq

          MapleUnits(parsed.map(_._1), parsed.flatMap(_._2))
        }
    }

To be fair, that is still quite verbose and there are possibly other ways of doing it. As we mentioned, though, this
depends largely on how your JSON file looks like. It is often the case as well that this is the most complex part of
the code that you need to define.

The final part that we need to define is the actual function for processing the upload. This is where we combine all
functions we have defined earlier (and some that are already defined by the traits we are extending) in one place. It
covers the part after user upload up until the part where we create a ``RunRecord`` object to be sent back to the user
as a JSON payload, notifying that the upload has been successful.

The function is called ``processRunUpload`` and is a requirement of the ``RunsProcessor`` abstract class. It has the
following signature:

.. code-block:: scala

    /**
     * Processes and stores the given uploaded file to the run records collection.
     *
     * @param contents Upload contents as a byte array.
     * @param uploadName File name of the upload.
     * @param uploader Uploader of the run summary file.
     * @return A run record of the uploaded run summary file.
     */
    def processRunUpload(
        contents: Array[Byte],
        uploadName: String,
        uploader: User): Future[Perhaps[RunRecord]]

It is invoked by Sentinel's ``RunsController`` after user authentication (hence the ``uploader`` argument). You do not
need to worry about ``uploadName`` nor ``uploader`` at this point. The important thing is to note the return type:
``Future[Perhaps[RunRecord]]``. We have covered this in our earlier guides. This is where we now actually implement
a working code for processing the user upload.

There are of course several different ways to implement ``processRunUpload``. Here is one that we have, to give you an
idea:

.. code-block:: scala

    class MapleRunsProcessor(mongo: MongodbAccessObject)
            extends RunsProcessor(mongo)
            with ReadGroupsAdapter
            with ValidatedJsonExtractor {

        ...

        def processRunUpload(contents: Array[Byte], uploadName: String, uploader: User) = {
            val stack = for {
                // Make sure it is JSON
                runJson <- ? <~ extractAndValidateJson(contents)
                // Store the raw file in our database
                fileId <- ? <~ storeFile(contents, uploader, uploadName)
                // Extract samples and read groups
                units <- ? <~ extractUnits(runJson, uploader.id, fileId)
                // Invoke store methods asynchronously
                storeSamplesResult = storeSamples(units.samples)
                storeReadGroupsResult = storeReadGroups(units.readGroups)
                // Check that all store methods are successful
                _ <- ? <~ storeReadGroupsResult
                _ <- ? <~ storeSamplesResult
                // Create run record
                sampleIds = units.samples.map(_.dbId)
                readGroupIds = units.readGroups.map(_.dbId)
                run = MapleRunRecord(fileId, uploader.id, pipelineName, sampleIds, readGroupIds)
                // Store run record into database
                _ <- ? <~ storeRun(run)
            } yield run

            stack.run
        }
    }

Our implementation above consists of a series of functions; beginning with parsing and validating the JSON, storing
the raw uploaded bytes, extracting the record objects, and then storing the record objects. Everything is wrapped inside
``EitherT[Future, ApiPayload, RunRecord]``, and stored as a value called ``stack``. This of course means we still need
to invoke the ``.run`` method in order to get the ``Future[Perhaps[RunRecord]]`` object which we will return.

We hope by now it is also clear to you that this single for comprehension block already has error handling with the
``ApiPayload`` type built in and that we always write to the database asynchronously whenever possible.

Here is our finished, complete ``MapleRunsProcessor`` for your reference:

.. code-block:: scala

    import scala.concurrent._

    import org.bson.types.ObjectId
    import org.json4s.JValue

    import nl.lumc.sasc.sentinel.adapters._
    import nl.lumc.sasc.sentinel.models.User
    import nl.lumc.sasc.sentinel.processors.RunsProcessor
    import nl.lumc.sasc.sentinel.utils.{ ValidatedJsonExtractor, MongodbAccessObject }

    /**
     * Example of a simple pipeline runs processor.
     *
     * @param mongo MongoDB access object.
     */
    class MapleRunsProcessor(mongo: MongodbAccessObject)
        extends RunsProcessor(mongo)
        with ReadGroupsAdapter
        with ValidatedJsonExtractor {

      /** Exposed pipeline name. */
      def pipelineName = "maple"

      /** JSON schema for incoming summaries. */
      def jsonSchemaUrls = Seq("/schema_examples/maple.json")

      /** Run records container. */
      type RunRecord = MapleRunRecord

      /** Sample-level metrics container. */
      type SampleRecord = MapleSampleRecord

      /** Read group-level metrics container. */
      type ReadGroupRecord = MapleReadGroupRecord

      /** Execution context. */
      implicit private def context: ExecutionContext = ExecutionContext.global

      /** Helper case class for storing records. */
      case class MapleUnits(
        samples: Seq[MapleSampleRecord],
        readGroups: Seq[MapleReadGroupRecord])

      /**
       * Extracts the raw summary JSON into samples and read groups containers.
       *
       * @param runJson Raw run summary JSON.
       * @param uploaderId Username of the uploader.
       * @param runId Database ID for the run record.
       * @return Two sequences: one for sample data and the other for read group data.
       */
      def extractUnits(runJson: JValue, uploaderId: String,
                       runId: ObjectId): MapleUnits = {

        /** Name of the current run. */
        val runName = (runJson \ "run_name").extractOpt[String]

        /** Given the sample name, read group name, and JSON section of the read group, create a read group container. */
        def makeReadGroup(sampleName: String, readGroupName: String, readGroupJson: JValue) =
          MapleReadGroupRecord(
            stats = MapleReadGroupStats(
              nReadsInput = (readGroupJson \ "nReadsInput").extract[Long],
              nReadsAligned = (readGroupJson \ "nReadsAligned").extract[Long]),
            uploaderId = uploaderId,
            runId = runId,
            readGroupName = Option(readGroupName),
            sampleName = Option(sampleName),
            runName = runName)

        /** Given the sample name and JSON section of the sample, create a sample container. */
        def makeSample(sampleName: String, sampleJson: JValue) =
          MapleSampleRecord(
            stats = MapleSampleStats(nSnps = (sampleJson \ "nSnps").extract[Long]),
            uploaderId, runId, Option(sampleName), runName)

        /** Raw sample and read group containers. */
        val parsed = (runJson \ "samples").extract[Map[String, JValue]].view
          .map {
            case (sampleName, sampleJson) =>
              val sample = makeSample(sampleName, sampleJson)
              val readGroups = (sampleJson \ "readGroups").extract[Map[String, JValue]]
                .map { case (readGroupName, readGroupJson) => makeReadGroup(sampleName, readGroupName, readGroupJson) }
                .toSeq
              (sample, readGroups)
          }.toSeq

        MapleUnits(parsed.map(_._1), parsed.flatMap(_._2))
      }

      /**
       * Validates and stores uploaded run summaries.
       *
       * @param contents Upload contents as a byte array.
       * @param uploadName File name of the upload.
       * @param uploader Uploader of the run summary file.
       * @return A run record of the uploaded run summary file or a list of error messages.
       */
      def processRunUpload(contents: Array[Byte], uploadName: String, uploader: User) = {
        val stack = for {
          // Make sure it is JSON
          runJson <- ? <~ extractAndValidateJson(contents)
          // Store the raw file in our database
          fileId <- ? <~ storeFile(contents, uploader, uploadName)
          // Extract samples and read groups
          units <- ? <~ extractUnits(runJson, uploader.id, fileId)
          // Invoke store methods asynchronously
          storeSamplesResult = storeSamples(units.samples)
          storeReadGroupsResult = storeReadGroups(units.readGroups)
          // Check that all store methods are successful
          _ <- ? <~ storeReadGroupsResult
          _ <- ? <~ storeSamplesResult
          // Create run record
          sampleIds = units.samples.map(_.dbId)
          readGroupIds = units.readGroups.map(_.dbId)
          run = MapleRunRecord(fileId, uploader.id, pipelineName, sampleIds, readGroupIds)
          // Store run record into database
          _ <- ? <~ storeRun(run)
        } yield run

        stack.run
      }
    }

And that's it! You now have fully-functioning runs processor.


The Stats Processor
-------------------

The final step is defining the stats processor. This step will be relatively simpler than the inputs processor, since
Sentinel now has a better idea of what to expect from the database records (courtesy of the record objects we defined
earlier).

.. code-block:: scala

    package nl.lumc.sasc.sentinel.exts.maple

    class MapleStatsProcessor(mongo: MongodbAccessObject)
        extends StatsProcessor(mongo) {

      def pipelineName = "maple"

      /* Function for retrieving Maple sample data points. */
      def getMapleSampleStats =
        getStats[MapleSampleStats]("stats")(AccLevel.Sample) _

      /* Function for aggregating over Maple read group data points. */
      def getMapleSampleAggrStats =
        getAggregateStats[MapleSampleStatsAggr]("stats")(AccLevel.Sample) _

      /** Function for retrieving Maple read group data points. */
      def getMapleReadGroupStats =
        getStats[MapleReadGroupStats]("stats")(AccLevel.ReadGroup) _

      /* Function for aggregating over Maple read group data points. */
      def getMapleReadGroupAggrStats =
        getAggregateStats[MapleReadGroupStatsAggr]("stats")(AccLevel.ReadGroup) _
    }

And that is all we need to have a fully functioning ``MapleStatsProcessor``. See also that what we are
doing here is defining functions partially (notice the use of `_` at the end of each function, which is how we do
partial function invocations in Scala).

In addition to ``pipelineName``, which has the same meaning and use as the ``pipelineName`` in ``MapleRunsProcessor``,
there are four functions we define here. These four functions calls two functions that are defined in the
``StatsProcessor`` abstract class: ``getStats`` and ``getAggregateStats``.

Let's take a look at ``getStats`` invoked in ``getMapleSampleStats`` first. Here we are calling it with one type
parameter, the ``MapleSampleStats`` type. This is a type we have defined earlier to contain our sample-level metrics.
In essence this is what ``getStats`` does. Upon user query, it creates sample-level metrics container objects from our
previously stored database records. It does that by reading the ``MapleSampleRecord`` object and extracting an attribute
from that object whose type is ``MapleSampleStats``. ``getStats`` is not smart enough to know which attribute that is,
however, so we need to supply the attribute name as an argument as well. In our case, this attribute is called
``stats`` and indeed we use ``stats`` here as the first value argument to ``getStats``. The final argument
``AccLevel.Sample`` simply tells ``getStats`` that we want this query to operate on the sample-level instead of read
group-level.

``getStats`` in ``getMapleReadGroupStats`` is called with the same logic. The difference is only the final argument,
where we use ``AccLevel.ReadGroup`` as we want this function to operate on the read group level.

``getAggregateStats`` is not exactly similar, but in this case are also called with the same logic. The main difference
is that the returned object is a single object containing various aggregated values.

With this, we have completely defined the required processors and internal data models. The next step is to expose these
processors via the HTTP controllers.
