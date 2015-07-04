Uploading Summary Files
=======================

Getting a User Account
----------------------

To be able to upload summary files, you will need a verified user account. Please contact
`sasc@lumc.nl <mailto:sasc@lumc.nl?subject=Sentinel%20Account%20Request>`_ to request for an account. We are working on
an automated system so that you can register and verify yourself.

The account comes with an API key which allows you to authenticate against Sentinel. Authentication is done by using the
HTTP header key ``X-SENTINEL-KEY``, setting its value to your API key. You must keep this key private to yourself.


Your First Upload
-----------------

Since Sentinel relies on the HTTP protocol, any tool which supports the HTTP protocol can be used. For this
documentation, we will be using  `httpie <https://github.com/jakubroztocil/httpie>`_, a command-line HTTP client.
You are free to use other clients such as  `curl <http://curl.haxx.se/>`_ or `wget <http://www.gnu.org/software/wget/>`_
of course. If you prefer a graphical interface, you can use our :apidoc:`live API documentation < >`.

Now, with httpie ready and assuming these parameters:

* Your user ID is ``myID``
* Your API key is ``MyKey123``
* The pipeline name is ``gentrap``
* The summary file name is ``summary.json``

uploading is as simple as issuing a ``POST`` request to the ``/runs`` endpoint.

.. parsed-literal::

    $ http -f POST '|sentinel_url|/runs?userId=myID&pipeline=gentrap' run@summary.json X-SENTINEL-KEY:MyKey123

From the live documentation page, you can do the same by filling in the forms in the ``POST /runs``
:endpoint:`endpoint <runs/runsPost>` and then clicking the ``Try it out!`` button.

After issuing the command above, you should get a response which looks something like this

.. code-block:: javascript

    {
        "pipeline": "gentrap",
        "uploaderId": "myID",
        "creationTimeUtc": "2015-07-02T13:03:43Z",
        "runId": "559536af60b2adf7844567c4",
        "nLibs": 10,
        "nSamples": 15
    }

Some things are already apparent here:

    * We get some of our provided information back: ``pipeline: gentrap`` and ``uploaderId: myID`` denote the pipeline
      name and your user ID, respectively.
    * We get back the creation time of our run record. Notice that the time zone is UTC.
    * We get a randomly-assigned ``runID``. This value is the internal ID that Sentinel assigns to your uploaded run.
      It can be used, for example, to filter for specific runs when querying the metrics.
    * ``nLibs`` and ``nSamples`` both denote the number of sequencing libraries and sequencing samples present in your
      uploaded run (see :doc:`users_terminologies`).

Depending on the pipeline, you may also see additional attributes such as:

    * ``runName``, which is a human-readable name of your pipeline run.
    * ``refId``, which denote the reference sequence ID used by the uploaded pipeline run.
    * ``annotIds``, which denote the annotation file IDs used by the uploaded pipeline run.

Large Run Summaries
^^^^^^^^^^^^^^^^^^^

Some pipeline run may contain hundreds of samples, which in turn increases the run summary file size as well. Sentinel
has a default upload limit of 16MB. While this may seem small, there are several things you can do to minimize your
uploaded summary:

1. GZIP compression. Consider compressing your ``summary.json`` file using `gzip`. Sentinel can detect gzip compression
and will happily decompress it internally.

2. Whitespace removal. JSON files can be made smaller by removing whitespace between its entries (not inside the literal
strings, of course). These two JSON objects, for example, are considered semantically equal:

.. code-block:: javascript

    {
        "samples": {
            "sample 1": {
                "libSize": 1000
            }
        }
    }

.. code-block:: javascript

    {"samples":{"sample 1":{"libSize":1000}}}

Also, depending on the client you use for uploading, consider setting the time out limit since Sentinel may take a while
to process your uploaded run. In httpie, this is done by setting the ``--timeout`` flag.


Retrieving a Summary
--------------------

You can retrieve your uploaded run summary by issuing a GET HTTP command to the ``/runs/{runId}`` endpoint, specifying
the Sentinel-assigned run ID.

Assuming these parameters:

* Your user ID is ``myID``
* Your API key is ``MyKey123``
* The run summary's ID is ``559536af60b2adf7844567c4``

The using httpie, you can download your file as follows:

.. parsed-literal::

    $ http GET '|sentinel_url|/runs?runId=559536af60b2adf7844567c4&userId=myID&download=true' X-SENTINEL-KEY:MyKey123

Notice the ``download=true`` parameter specified in the end. If this is not specified, you will get instead a JSON
object representing the uploaded run, but not the actual run summary file itself. The JSON record is what you get
when you first upload the run summary file.

If you have trouble finding out the run ID, you can try listing all of the runs you have uploaded using GET on the
``/runs`` endpoint:

.. parsed-literal::

    $ http GET '|sentinel_url|/runs?userId=myID' X-SENTINEL-KEY:MyKey123

This will return a list of run records of all your uploaded JSON files.

As with uploading, you can try do the above methods by filling in the forms in the ``GET /runs/{runId}``
:endpoint:`endpoint <runs/runsGet>` and/or the ``GET /runs`` :endpoint:`endpoint <runs/runIdGet>` endpoints and then
clicking the ``Try it out!`` button.


Deleting a Run Summary
----------------------

If for some reason you decided to remove your run summary from Sentinel, you can do so using the
``DELETE /runs/{runId}`` endpoint.

Assuming these parameters:

* Your user ID is ``myID``
* Your API key is ``MyKey123``
* The run summary's ID is ``559536af60b2adf7844567c4``

The using httpie, you can perform the deletion as follows:

.. parsed-literal::

    $ http DELETE '|sentinel_url|/runs?runId=559536af60b2adf7844567c4&userId=myID' X-SENTINEL-KEY:MyKey123

After deletion, all data points from the run summary will be removed from Sentinel.
