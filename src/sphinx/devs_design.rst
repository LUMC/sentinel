Internal Design Notes
=====================

General Aims
------------

The goal of Sentinel is to enable storing and retrieval of next-generation sequencing metrics as general as possible.
It should not be constrained to a specific data analysis pipeline, a specific reference sequence, nor a specific
sequencing technology. The challenge here is to have a framework that can be adapted to the need of a lab / institution
processing large quantities of such data, when the data analysis pipelines can be so diverse with so many moving parts.

This is why we decided to implement Sentinel as a service which communicates via the HTTP protocol using JSON files.
JSON files are essentially free-form, yet it still enforces a useful structure and useful data types which can store the
sequencing metrics. Communicating via HTTP also means that we are not constrained to a specific language. A huge number
of tools and programming languages that can communicate via HTTP exist today.

The current implementation still has a noticeable drawback, however. At the moment, if one wishes to add support to
his / her own data analysis pipeline, he/she must clone the entire source code and implement the JSON file parsing
functions and also the pipeline's HTTP endpoints there. More ideal is to have a single core module (e.g.
a ``sentinel-core`` package) and other modules which implements specific pipeline support separately. This is not yet
implemented since in order to do so, we need to be able to combine not only parsing logic, but also the HTTP endpoints
that exposes the pipeline's metrics. While this seems possible, we have not found a way to do so cleanly yet.

Framework
---------

Sentinel is written in `Scala <http://www.scala-lang.org/>`_ using the `Scalatra <http://www.scalatra.org/>`_ web
framework. Scalatra was chosen since it is has a minimal core allowing us to add / remove parts as we see fit. Other
frameworks, such as the Play Framework, may come with features that we probably will never use (e.g. a full-blown
templating engine).

The API specification is written based on the `Swagger specification <http://swagger.io>`_. It is not the only API
specification available out there nor is it an official specification endorsed by the W3C. It seems, however,
to enjoy noticeable support from the programming community in general, with various third-party tools and
libraries available (at the time of writing). The spec itself is also accompanied by useful tools such as the
`automatic interactive documentation generator <https://github.com/swagger-api/swagger-ui>`_. Finally, Scalatra can
generate the specification directly from the code, allowing the spec to live side-by-side with the code.


Persistence Layer
-----------------

For the underlying database, Sentinel uses `MongoDB <https://www.mongodb.org/>`_. This is in line with what Sentinel is
trying to achieve: to be as general as possible. MongoDB helps by not imposing any schema on its own. However, we would
like to stress that this does not mean there is no underlying schema of any sort. While MongoDB allows JSON
document of any structure, Sentinel does expect a certain structure from all incoming JSON summary files. They must
represent a single pipeline run, which contain at least one sample, which contain at least one library. Internally,
Sentinel also breaks down an uploaded run summary file into single samples. It is these single samples that are stored
and queried in the database. One can consider that MongoDB allows us to define the 'schema' on our own, in our own code.

Considering this, we strongly recommend that JSON summary files be validated against a schema. Sentinel uses 
`JSON schema <http://json-schema.org/>`_, which itself is JSON, for the pipeline schemas.


Data Modeling
-------------

The following list denotes some commonly-used objects inside Sentinel. Other objects exist, so this is not an
exhaustive list.

Controllers
^^^^^^^^^^^

HTTP endpoints are represented as ``Controller`` objects which subclass from the ``SentinelServlet`` class. The
exception to this rule is the ``RootController``, since it implements only few endpoints and is the only controller
that returns HTML for browser display. API specifications are defined inside the controllers and is tied to a specific
route matcher of an HTTP method.

Processors
^^^^^^^^^^

Pipeline support is achieved using ``Processor`` objects, implemented now in the ``nl.lumc.sasc.sentinel.processors``
package. For a given pipeline, two processors must be implemented: a runs processor, responsible for processing
incoming run summary files, and a stats processor, responsible for querying and aggregating metrics of the pipeline.

Adapters
^^^^^^^^

Adapters are traits that are mixed into processors to add processing capabilities. For example, the
``ReferencesAdapter`` can be mixed in to a processor so it also processes reference sequence information. Most of the
adapters involve connection to the MongoDB database, although not all do so.

Records
^^^^^^^

These objects are more loosely-defined, but most of the time they are case classes that represents a MongoDB object
stored in the database. While it is possible to interact with raw MongoDB objects, we prefer to have these objects
contained within case classes to minimize run time errors.
