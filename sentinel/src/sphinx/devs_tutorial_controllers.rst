Updating Controllers
====================

.. note::

    We are still working on making the controllers setup more modular. At the moment, adding support to new pipelines
    means changing the source code of the existing controllers directly.

For our `Maple` pipeline, there are two controllers that need to be updated: the ``RunsController`` where users upload
their run summaries and the ``StatsController`` where users query pipeline metrics. These controllers are actually
Scalatra controllers with additional Swagger endpoint descriptions. If you are not yet familiar with Scalatra
controllers, we recommend reading their `guide <http://www.scalatra.org/2.4/guides/http/routes.html>`_ first.

When you're set, let's start with the ``RunsController`` first.

RunsController
--------------

The updates that we need to do on the ``RunsController`` are quite minimum. First, we need to instantiate a copy of our
``MapleRunsProcessor`` in it, and then make sure the ``POST /runs`` endpoint recognizes ``Maple``.

To instantiate ``MapleRunsProcessor``, you must first import the processor in the top of the file:

.. code-block:: scala

    import nl.lumc.sasc.sentinel.maple.MapleRunsProcessor


After that, you can add the following line inside the controller, before any routes are defined:

.. code-block:: scala

    val maple = new MapleRunsProcessor(mongo)

Then head over to the ``POST /runs`` route matcher and add a case for the `Maple` enum we defined earlier:

.. code-block:: scala

    val processor = AllowedPipelineParams.get(pipeline).collect {
      // ...
      case Pipeline.Maple   => maple
    }

And that's it. You should now be able to upload maple run summaries when you run the development server.


StatsController
---------------

The last step is to update the stats controller. Here, we need to define two new endpoints for querying `Maple` metrics:
one for querying the single data points and another for querying aggregated metrics. Both endpoints will use the HTTP
``GET`` method. Let's call our endpoints as follow:

    * For the data points endpoint, ``/stats/maple/datapoints``
    * For the aggregate endpoint, ``/stats/maple/datapoints/aggregate``

The first part of the endpoint, `/stats` is already automatically set by Sentinel, so our route matchers only needs to
define the last part.

Before we begin, we need to import the Maple stats containers and their processor in the ``StatsController.scala`` file:

.. code-block:: scala

    import nl.lumc.sasc.sentinel.lumc.processors.{ MapleStats, MapleStatsAggr, MapleStatsProcessor }

And similar to the ``RunsController``, we'll need to instantiate the proper processor as well:

.. code-block:: scala

    val maple = new MapleStatsProcessor(mongo)

Now let's start with the data points endpoint first.

/stats/maple/datapoints
^^^^^^^^^^^^^^^^^^^^^^^

In the ``StatsController.scala`` file, add the following Swagger operation definition:

.. code-block:: scala
    :linenos:

    val statsMapleDatapointsGetOp =
        (apiOperation[Seq[MapleStats]]("statsMapleDatapointsGet")
            summary "Retrieves Maple data points"
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
              StringResponseMessage(401, CommonMessages.UnauthenticatedOptional.message)))

While the definitions is not required per-se, it is always useful to let users know the parameters your endpoint
accepts. In this case, our endpoint accepts three optional parameters: run ID for filtering and
user ID with the associated API key for optional authentication. We also define the HTTP error code we will return
in case any of the supplied arguments are invalid.

Here comes the route matcher for the data points query:

.. code-block:: scala
    :linenos:

    get("/maple/datapoints", operation(statsMapleDatapointsGetOp)) {
      val runIds = getRunObjectIds(params.getAs[String]("runIds"))
      val user = Try(simpleKeyAuth(params => params.get("userId"))).toOption
      if ((Option(request.getHeader(HeaderApiKey)).nonEmpty || params.get("userId").nonEmpty)
           && user.isEmpty)
        halt(401, CommonMessages.UnauthenticatedOptional)

      Ok(maple.getMapleStats(Option(LibType.Paired), user, runIds, Seq(), Seq(), false))
    }

Most of the things defined above are for making sure that the supplied request parameters are correct. The actual
query to the database itself is only one line. You'll notice that we are also passing some empty ``Seq`` and a boolean
``false``. This is mostly an artifact from the way we define our function. You can check the ScalaDoc for the complete
parameter information, if you wish. What we can tell is that we are also still working on making the function
definitions more compact, this function included.


/stats/maple/datapoints/aggregate
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

With that set, we can now define the endpoint for aggregated queries. Let's start with the API definition as before:

.. code-block:: scala
    :linenos:

    val statsMapleDatapointsAggregateGetOp =
        (apiOperation[MapleStatsAggr]("statsMapleDatapointsAggregateGet")
            summary "Retrieves Maple data points"
            parameters
            queryParam[Seq[String]]("runIds")
                .description("Run ID filter.")
                .multiValued
                .optional
            responseMessages StringResponseMessage(400, "Invalid Run IDs supplied"))

The API definition is similar to the single data points, with difference being the authentication is not present
anymore. This makes sense, since aggregated data points do not have any name labels associated with them.

.. code-block:: scala
    :linenos:

    get("/maple/datapoints/aggregate", operation(statsMapleDatapointsAggregateGetOp)) {
      val runIds = getRunObjectIds(params.getAs[String]("runIds"))
      maple.getMapleAggrStats(Option(LibType.Paired), runIds, Seq(), Seq()) match {
        case None      => NotFound(CommonMessages.MissingDataPoints)
        case Some(res) => Ok(transformMapReduceResult(res))
      }
    }

Having a shorter API description now means that we only need to implement fewer parameter parsing, as you can see in the
route matcher above. There, we only capture the ``runIds`` filter parameter. The rest of the code deals with the actual
querying and aggregating of the data.


Epilogue
--------

The ``MapleStatsController`` implementation marks the end of our tutorial. You have just added a new pipeline support to
Sentinel! Feel free to play around with uploading and querying the endpoints you just created. When you're more
familiar with the code base, you can experiment with adding support for more complex pipelines. If that's not enough,
head over to the :doc:`contribute` page and see how you can contribute to Sentinel development.
