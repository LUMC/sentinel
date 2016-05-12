Updating Controllers
====================

.. note::

    We are still working on making the controllers setup more modular. At the moment, adding support to new pipelines
    means changing the source code of the existing controllers directly.

For our `Maple` pipeline, there are two controllers that need to be made aware of the processors we wrote in the
previous section. The controllers, as mentioned in :doc:`an earlier section </devs_design>`, are responsible for
mapping a given URL to a specific action that Sentinel will execute. Two controllers that are tightly coupled
with the processors we have written are called ``RunsController``, which handles a URL for summary file uploads,
and ``StatsController``, which handles URLs for statistics query. Accordingly, the ``RunsController`` needs
to be made aware of our ``MapleRunsProcessor`` and the ``StatsController`` of our ``MapleStatsProcessor``.

What do we mean by making the controllers aware of our processors? Basically, it means they need to know
how to initialize the processor classes. They are handled quite differently for each controllers. This is
because Sentinel by default has one endpoint for uploading the summary files (by default this is a ``POST``
request on the ``/runs`` endpoint), but more than one endpoint for querying the statistics. For the current
version, we can simply edit the main ``ScalatraBootstrap`` class to add our ``MapleRunsProcessor`` class while
we need to manually edit the ``StatsController`` class to add our ``MapleStatsProcessor``.

The difference in instantiating the processor classes is mostly caused by the need to document the schema returned
by different statistics queries. A given pipeline may return a metrics object (the metrics case class we defined
earlier) which is completely different from another pipeline. This is not the case for the summary file uploads,
where all pipeline processors use the same ``processRunUpload`` method. This limitation may be removed in future
versions.


RunsController
--------------

The ``RunsController`` can be made aware of our ``MapleRunsProcessor`` by injecting the class name in the class
responsible for instantiating the controller itself. This class, the ``ScalatraBootstrap`` class, has an ``init`` method
where it instantiates all controllers. What we need to do, is to have an implicit value of the type
``Set[MongodbAccessObject => RunsProcessor]``. In other words, a set of functions that creates a ``RunsProcessor``
object given a ``MongodbAccessObject`` object.

The ``MongodbAccessObject`` object is an object representing access to our database. In production runs, this represents
access to a live database server, while in testing this is replaced by a testing server. Sentinel has a helper method
called ``makeDelayedProcessor`` in the ``nl.lumc.sasc.sentinel.utils.reflect`` package, which can create the function we
require from the processor class.

Invoking it is then quite simple:

.. code-block:: scala


    class ScalatraBootstrap extends LifeCycle {

        ...

        override def init(context: ServletContext) {

            val runsProcessors = Set(
                makeDelayedProcessor[MapleRunsProcessor])

            // continue to initialize and mount the controllers
        }
    }

And that's it. That's all we require so that the ``RunsController`` class can process uploaded Maple summary files.


StatsController
---------------

The last step is to update the stats controller. There are four endpoints that we can define, each using a method that
we have written in ``MapleStatsProcessor``:

    * For the sample-level data points endpoint, ``/stats/maple/samples``
    * For the sample-level aggregated endpoint, ``/stats/maple/samples/aggregate``
    * For the read group-level data points endpoint, ``/stats/maple/readgroups``
    * For the read group-level aggregated endpoint, ``/stats/maple/readgroups/aggregate``

We will define the sample-level endpoints together and leave the read group-level endpoints for you to define.

.. note::

    The first part of the endpoint, `/stats` is already automatically set by Sentinel, so our route matchers only needs
    to define the last part.

Before we begin, we need to import the Maple stats containers and their processor in the ``StatsController.scala`` file:

.. code-block:: scala

    import nl.lumc.sasc.sentinel.lumc.ext.maple._

Different from its runs processor counterpart, we will need to instantiate the ``MapleStatsProcessor`` directly inside
the ``StatsController`` body:

.. code-block:: scala

    val maple = new MapleStatsProcessor(mongo)

After this, we can start with implementing the actual endpoints.

``/stats/maple/samples``
^^^^^^^^^^^^^^^^^^^^^^^^

In the ``StatsController.scala`` file, add the following Swagger operation definition:

.. code-block:: scala

    val statsMapleSamplesGetOp =
        (apiOperation[Seq[MapleSampleStats]]("statsMapleSamplesGet")
            summary "Retrieves Maple sample-level data points"
            parameters (
              queryParam[Seq[String]]("runIds")
                .description("Run ID filter.")
                .multiValued
                .optional,
              queryParam[String]("userId").description("User ID.")
                .optional,
              headerParam[String](HeaderApiKey).description("User API key.")
                .optional)
            responseMessages (
              StringResponseMessage(400, "Invalid Run IDs supplied"),
              StringResponseMessage(401, Payloads.OptionalAuthenticationError.message)))

While the definitions is not required per-se, it is always useful to let users know the parameters your endpoint
accepts. In this case, our endpoint accepts three optional parameters: run ID for filtering and
user ID with the associated API key for optional authentication. We also define the HTTP error code we will return
in case any of the supplied arguments are invalid.

Here comes the route matcher for the data points query:

.. code-block:: scala

    get("/maple/datapoints", operation(statsMapleSamplesGetOp)) {

      val runIds = params.getAs[Seq[DbId]]("runIds").getOrElse(Seq.empty)
      val idSelector = ManyContainOne("runId", runIds)

      val user = Try(simpleKeyAuth(params => params.get("userId"))).toOption
      if ((Option(request.getHeader(HeaderApiKey)).nonEmpty || params.get("userId").nonEmpty) && user.isEmpty)
        halt(401, Payloads.OptionalAuthenticationError)

      new AsyncResult {
        val is = maple.getMapleSampleStats(idSelection, user)
          .map {
            case -\/(err) => err.toActionResult
            case \/-(res) => Ok(res)
          }
      }
    }

In the code block, you can see that the first two ``val`` declarations capture the parameters supplied by the user.
The ``runIds`` parameter is an optional parameter for selecting only particular run IDs. These are IDs that users get
when they upload the run summary file for the first time and are assigned randomly by the database. We then proceeded
to create a selector object (basically a ``MongoDBObject``) which will then be used for filtering the metrics. Here we
use the Sentinel-defined ``ManyContainOne`` helper case class, which has the effect of selecting any metrics whose
run ID is contained within the user-supplied run ID. If the user does not supply any run IDs, then no filtering will be
done.

The ``val user`` declaration allows for optional user authentication. A succesfully authenticated user will get
additional information for data points that he/she has uploaded, such as the sample name. He/she may still see data
points uploaded by other users, only without any identifying information.

Finally, we run the query on the database using the ``AsyncResult`` class provided by Scalatra. This allows our query to
be run asynchronously so that Sentinel may process other queries without waiting for this to finish.


``/stats/maple/samples/aggregate``
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

With that set, we can now define the endpoint for aggregated queries. Let's start with the API definition as before:

.. code-block:: scala

    val statsMapleSamplesAggregateGetOp =
        (apiOperation[MapleSampleStatsAggr]("statsMapleSamplesAggregateGet")
            summary "Retrieves Maple sample-level aggregated data points"
            parameters
            queryParam[Seq[String]]("runIds")
                .description("Run ID filter.")
                .multiValued
                .optional
            responseMessages StringResponseMessage(400, "Invalid Run IDs supplied"))

The API definition is similar to the single data points, with difference being the authentication is not present
anymore. This makes sense, since aggregated data points do not have any name labels associated with them.

.. code-block:: scala

    get("/maple/datapoints/aggregate", operation(statsMapleDatapointsAggregateGetOp)) {
      val runIds = getRunObjectIds(params.getAs[String]("runIds"))
      val idSelector = ManyContainOne("runId", runIds)

      new AsyncResult {
        val is =
            maple.getMapleSampleAggrStats(None)(idSelector)
              .map {
                case -\/(err) => err.toActionResult
                case \/-(res) => Ok(res)
              }
      }
    }

This is almost the same as our previous endpoint, except that there is an extra ``None`` argument supplied to the
function above. This is used only when our stats processor distinguishes between single-end and paired-end data. In our
case, we made no such distinction and thus we can simply use ``None`` there.


Epilogue
--------

The ``MapleStatsController`` implementation marks the end of our tutorial. You have just added a new pipeline support to
Sentinel! Feel free to play around with uploading and querying the endpoints you just created. When you're more
familiar with the code base, you can experiment with adding support for more complex pipelines. If that's not enough,
head over to the :doc:`contribute` page and see how you can contribute to Sentinel development.
