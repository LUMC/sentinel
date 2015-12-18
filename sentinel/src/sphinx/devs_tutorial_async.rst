Asynchronous Processing
=======================

Having dealt with :doc:`handling expected errors </devs_tutorial_composition>`, we will now take a short tour on how
Sentinel does asynchronous processing. The basic rationale of using asynchronous processing in Sentinel is to ensure
that multiple HTTP requests can be handled at the same time. A single HTTP request may involve writing and/or reading
from the database several times, so most asynchronous functions in Sentinel are database-related operations.

.. note::

    This guide assumes readers are already familiar with the ``Future`` type and attempts only to explain how it is
    used in Sentinel. For more comprehensive ``Future`` guide, we find the
    `official overview <http://docs.scala-lang.org/overviews/core/futures.html>`_ a good starting point.

Sentinel uses the ``Future`` type defined in the standard library for its asynchronous functions. In some ways, this
allows us to leverage the type system to make our functions composable, similar to how we handled errors earlier using
the disjunction type ``\/``.


``Future`` with for comprehensions
----------------------------------

Like the ``Option``, ``Either``, and ``\/`` types we have seen previously, ``Future`` is composable. You can, for example,
use it inside a for comprehension:

.. code-block:: scala

    import scala.concurrent._

    // Remember that we need an execution context
    implicit val context = ExecutionContext.global

    def calc1(): Future[Int] = Future { ... }
    def calc2(): Future[Int] = Future { ... }
    def calc3(x: Int, y: Int) = Future[Int] = Future { ... }

    // Launch separate computation threads
    val value1 = calc1()
    val value2 = calc2()

    val finalResult = for {
        firstResult <- value1
        secondResult <- value2
        thirdResult <- calc3(firstResult, secondResult)
    } yield thirdResult

The code block above will start a computation for ``value1`` and ``value2`` in parallel threads and wait until they both
return their results, before using them as arguments for ``calc3``.

The code also illustrates how using ``Future`` with for comprehensions require a little more care. Notice that we
invoked ``calc1`` and ``calc2`` **outside** of the for comprehension block. This is intentional and the reason is
because function calls inside the block are sequential. Had we written ``finalResult`` like this:

.. code-block:: scala

    ...

    val finalResult = for {
        firstResult <- calc1()
        secondResult <- calc2()
        thirdResult <- calc3(firstResult, secondResult)
    } yield thirdResult

then it does not matter if we write our code inside a ``Future``. It will be invoked sequentially, defeating the purpose
of using ``Future`` in the first place.

In some cases the for comprehension does allow for a value declaration inside itself (i.e. using the `=` operator
instead of `<-`). This requires that the first statement inside the block is an `<-` statement using the abstract type
the block intends to return. Since we are using ``Future``, this means the first statement should be a `<-` statement
from a ``Future[_]`` type. Take the following example:

.. code-block:: scala

    ...

    def calc0(): Future[Unit] = Future { ... }
    def calc1(): Future[Int] = Future { ... }
    def calc2(): Future[Int] = Future { ... }
    def calc3(x: Int, y: Int) = Future[Int] = Future { ... }

    val finalResult = for {
        preResult <- calc0()
        // Here ``calc1`` and ``calc2`` gets executed asynchronously
        value1 = calc1()
        value2 = calc2()
        firstResult <- value1
        secondResult <- value2
        thirdResult <- calc3(firstResult, secondResult)
    } yield thirdResult

The code block above also computes ``value1`` and ``value2`` asynchronously, similar to our first example.


Combining ``Future`` and ``Perhaps``
------------------------------------

In Sentinel, ``Future`` is often combined with the ``Perhaps`` type we have defined earlier. Conceptually, this means
that there are cases where Sentinel invokes a function asynchronously that may or may not return its expected type.
A function with the following signature, for example:

.. code-block:: scala

    def storeUpload(contents: Array[Byte]): Future[Perhaps[DatabaseId]] = { ... }

is expected to be executed asynchronously. In this case, it is a function to store user uploads which will
return the database ID of the stored file. There could be different reasons of wrapping the database ID inside
``Perhaps``. One is that we may want to tell users when they are uploading files they have previously uploaded, so
we can save disk space and the user do not store the same data twice.

Naturally there are still cases where we do not need our results being wrapped inside ``Future``, or ``Perhaps``,
or even both.

Consider our earlier ``extractJson`` function. This is a function that we expect to execute very early
upon user upload. Does it make sense to wrap it inside a ``Future``? It depends on how you setup your processing of
course. But it is easy to imagine that we first want to ensure that the data that the user uploads can indeed be
processed into JSON first before doing anything else. In this case, we would only need to wrap the return value inside
a ``Perhaps`` and not a ``Future`` since no other processing would be done in parallel at the time we are doing
validation.

On the other hand, methods that interact with the database directly are often wrapped only inside a ``Future`` and not
``Perhaps``. An example would be a function storing sample data parsed from the JSON record:

.. code-block:: scala

    def storeSamples(samples: Seq[Samples]): Future[Unit] = { ... }

This is the case because in most cases we do not expect database connection failures to be something
the user can recover from, so there is little point in letting them know this. We should anticipate indeed that the
database connection from time to time may fail, but this is something that should only be displayed in the server logs
and not to the user, so we do not use ``Perhaps`` here.

.. tip::

    For asynchronous methods where the error is not something the user can work on, we should let the ``Future`` fail.
    There is a built-in check in Sentinel that captures these ``Future`` failures and then converts it to a general
    HTTP 500 Internal Server Error to the user.

The fact that not all methods return ``Future``, or ``Perhaps``, or even ``Future[Perhaps]`` is something we need
to take into account when composing these functions. We saw earlier that we can use for comprehensions for a series
of calls that all return ``Perhaps``, or a series of calls that all return ``Future`` in some form.

This is not the case when composing functions of these different abstract types, however. Let's say we have these
functions that we wish to compose into a single processing step:

.. code-block:: scala

    def extractMetrics(contents: Array[Byte]): Perhaps[Metrics] = { ... }
    def storeMetrics(metrics: Metrics): Future[DatabaseId] = { ... }
    def storeUpload(contents: Byte): Future[Perhaps[DatabaseId] = { ... }

Then this will not work because they all return different types:

.. code-block:: scala

    def processUpload(contents: Array[Byte]) = {
        metrics <- extractMetrics(contents)
        metricsId <- storeMetrics(metrics)
        fileId <- storeUpload(metrics)
    } yield (metricsId, fileId)

.. important::

    Not only will the code above not compile, but we are also launching the all ``Future`` sequentially.

A solution is to make these functions return the same type. It does not necessarily mean we have to change the
functions themselves. After all, we have seen that we can not force all functions to use ``Future`` or ``Perhaps``. Not
only is this conceptually wrong, it is also impractical to expect all functions we write to use a similar abstract type.
What we want is to somehow 'lift' the return values of the functions into a common return type, but only when we want
to compose them. This way, functions can remain as they are yet can still be composed with others when needed.

Lifting Into ``Future[Perhaps]]``
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

What should we use then as the common type? A good candidate is actually a function with a ``Future[Perhaps[T]]]`` type.
This type can be interpreted as types whose value are computed asynchronously with a possibility of returning an
``ApiPayload`` to be displayed to the user. Recall that in our case, ``Perhaps[T]`` is an alias for the disjunction
type ``ApiPayload \/ T``. ``Future[Perhaps[T]]`` is actually then an alias for ``Future[ApiPayload \/ T]``.

.. note::

    Why not ``Perhaps[Future[T]]`` instead? Since this type is an alias for ``ApiPayload \/ Future[T]``, we can
    interpret it as types whose value when failing is ``ApiPayload`` and when successful is an asynchronous computation
    returning ``T``. In other words, it encodes the types whose error value is not computed asynchronously. This
    distinction does not really make sense in practice. It sort of means that we only do the asynchronous computation
    when we know we will not get any failures, but we could not have known this prior to the computation itself.

How do we lift into ``Future[Perhaps]]``? There are two cases we need to consider, lifting from ``Perhaps`` types and
lifting from ``Future`` types. From ``Perhaps`` types, we can simply wrap it using ``Future.successful``. The function,
provided by the standard library, is precisely for lifting types into a ``Future`` without launching any parallel
computation thread which ``Future`` normally does. In other words, it creates a completed ``Future`` without the
unecessary work of launching the ``Future``.

``Future.successful`` can be used like so:

.. code-block:: scala

    // A simple call to our ``extractMetrics`` function
    val metrics: Perhaps[Metrics] = extractMetrics(...)

    // Lifting the call into a ``Future``
    val futureMetrics: Future[Perhaps[Metrics]] =
        Future.successful(extractMetrics(...))

For the second case of lifting ``Future`` into ``Future[Perhaps]``, we can simply use ``Future.map`` to lift the
results inside it. Essentially, we then only lift the inner type of ``Future`` into ``Perhaps``:

.. code-block:: scala

    // A call to launch our ``storeMetrics`` function
    val dbId: Future[DatabaseId] = storeMetrics(...)

    // Lifting the ``DatabaseId`` to ``Perhaps[DatabaseId]``
    val futureDbId: Future[Perhaps[DatabaseId]] =
        dbId.map(id => \/-(id))

Now, having covered both cases, let's make our earlier for comprehension work. We will also define ``UploadIds``, a
helper case class for storing our uploaded IDs.

.. code-block:: scala

    // Helper case class for storing uploaded IDs
    case class UploadId(metrics: DatabaseId, file: DatabaseId)

    def processUpload(contents: Array[Byte]): Future[Perhaps[UploadId]] = {
        // Here we lift from ``Perhaps``
        metrics <- Future.successful(extractMetrics(contents))
        // Here we lift from ``Future``. Remember
        // that we want to store asynchronously,
        // so we need to launch the computation as
        // value declarations
        asyncMetricsId = storeMetrics(metrics).map(id => \/-(id))
        asyncFileId = storeUpload(metrics)
        // This is where we actually wait for the
        // store methods in parallel
        metricsId <- asyncMetricsId
        res = UploadId(metricsId, fileId)
    } yield res

    val uploadResult: Future[Perhaps[UploadId]] = processUpload(...)

Does the code above work? Not quite. It has to do with the fact that we are now using two layers of abstract types,
``Future`` and ``Perhaps``. This means that in this line:

.. code-block:: scala

    ...
        // Here we lift from ``Perhaps``
        metrics <- Future.successful(extractMetrics(contents))

``metrics`` does not evaluate to a ``Metrics`` object, but instead to a ``Perhaps[Metrics]`` object. The for
comprehension unwraps only the ``Future`` and not ``Perhaps``. Consequently, we can not use ``metrics`` directly as an
argument for ``storeMetrics`` afterwards. We have to unwrap it first, for example:

.. code-block:: scala

    ...
        // Here we lift from ``Perhaps``
        metrics <- Future.successful(extractMetrics(contents))
        asyncMetricsId = metrics match {
            // when metrics is an error type, we still
            // need to wrap it inside a ``Future``
            case -\/(err) => Future.successful(err)
            case \/-(ok)  => storeMetrics(ok).map(id => \/-(id))
        }

Not only is this too verbose, but it also reads quite unintuitively. Consider also that this is only for one part of
the statement. We have to unwrap subsequent statements to make sure ``Perhaps`` is also unwrapped. Surely we can do
better than this? Indeed we can. The answer lies in another type defined in scalaz, called the ``EitherT`` type.

Scalaz's ``EitherT``
^^^^^^^^^^^^^^^^^^^^

``EitherT`` is a type meant to be used when the disjunction type ``\/`` is wrapped inside some other abstract types
(in our case, a ``Future``). Not all abstract types can be used here, and ``Future`` itself needs a little bit of
enhancement.

.. tip::

    The mathematical term for ``EitherT`` is a monad transformer, since ``\/`` is a monad, ``Future`` can be
    made into a monad, and ``EitherT`` transforms them both into another monad that is a combination of both. We are
    intentionally not using these terms here, but they are actually common abstractions that pop up here and there in
    various codebases. Plenty of tutorials and guides about monads can be found online. If you are interested in monad
    transformers in Scala in particular, we found
    `the guide here <http://underscore.io/blog/posts/2013/12/20/scalaz-monad-transformers.html>`_
    `and the guide here <http://www.47deg.com/blog/fp-for-the-average-joe-part-2-scalaz-monad-transformers>`_ as good starting
    points.

What can we do with ``EitherT``? Essentially it boils down to this: ``EitherT`` allows us to unwrap both ``Future`` and
``Perhaps`` in a single for comprehension statement by wrapping them into another type that combines both ``Future``
and ``Perhaps``. The new type, in our case, is called ``EitherT[Future, ApiPayload, T]``. It is not
``Future[Perhaps]]``, but it needs to be made from ``Future[Perhaps]``.

.. code-block:: scala

    // Alias for the new type, let's call it AsyncPerhaps
    val AsyncPerhaps[+T] = EitherT[Future, ApiPayload, T]

    // From a ``Future[Perhaps]``
    val val1: Future[Perhaps[Int]] = ...
    val lifted1: AsyncPerhaps[Int] = EitherT(v)

    // From a ``Future``
    val val2: Future[Int] =
    val lifted2: AsyncPerhaps[Int] = EitherT.right(val2)

    // From a ``Perhaps``
    val val3: Perhaps[Int] = ...
    val lifted3: AsyncPerhaps[T] = EitherT(Future.successful(val3))

    // Even from an arbitrary type
    val val4: Int = ...
    val lifted4: AsyncPerhaps[T] = val.point[AsyncPerhaps]

Notice that ``EitherT`` can be used directly on values with a ``Future[Perhaps]]`` type in our case. For ``Perhaps``
values, we need to wrap it inside a ``Future`` still. For ``Future`` methods, we use a helper method in ``EitherT`` that
essentially maps the inner type with ``Perhaps`` (essentially what we did earlier when we did ``Future.map`` manually).
In short, we still needed to lift our types into ``Future[Perhaps]`` first.

Using ``EitherT``, our previous iteration then becomes this:

.. code-block:: scala

    ...

    def processUpload(contents: Array[Byte]) = {
        metrics <- EitherT(Future.successful(extractMetrics(contents)))
        asyncMetricsId = storeMetrics(metrics)
        asyncFileId = storeUpload(metrics)
        metricsId <- EitherT.right(asyncMetricsId)
        res = UploadId(metricsId, fileId)
    } yield res

    val wrappedResult: EitherT[Future, ApiPayload, UploadResult] =
        processUpload(...)

There is only one thing left to do, which is to unwrap back ``wrappedResult``. Our ``EitherT`` type can be considered a
helper type that allows us to compose all our functions. The type that is actually useful for us outside of the for
comprehension, though, is the ``Future[Perhaps]`` type, since our for comprehensions combines both ``Future`` and
``Perhaps`` already. We can convert ``EitherT[Future, ApiPayload, UploadResult`` back to ``Future[Perhaps[UploadResult]``
by invoking the ``.run`` method:

.. code-block:: scala

    ...

    def processUpload(contents: Array[Byte]) = {
        metrics <- EitherT(Future.successful(extractMetrics(contents)))
        asyncMetricsId = storeMetrics(metrics)
        asyncFileId = storeUpload(metrics)
        metricsId <- EitherT.right(asyncMetricsId)
        res = UploadId(metricsId, fileId)
    } yield res

    val finalResult: Future[Perhaps[UploadResult]] =
        processUpload(...).run

And that's it. We now have combined ``Future`` and ``Perhaps`` into a single block of computation. ``EitherT`` has
definitely improved readablity since it spares us from the need to unwrap manually. We are not completely done yet,
however. There are some implicit values required by future (its ``ExecutionContext``) and some implicit methods required
by scalaz to make this work. The details can be ignored for our discussion. What's important to know is that these are
all already defined in the ``FutureMixin`` trait in the ``nl.lumc.sasc.sentinel.utils`` package.


Sentinel's ``FutureMixin``
--------------------------

There are two things that this trait does that helps you combine ``Future`` and ``Perhaps``:

    1. It defines all the necessary implicit methods to make ``Future`` suitable for ``EitherT``.

    2. It defines an object called ``?`` that you can use to make your for comprehension even more compact.

We will discuss the implicit methods here, but we would like to note the ``?`` object. Recall that our last iteration
of the ``processUpload`` function is like this:

.. code-block:: scala

    ...

    def processUpload(contents: Array[Byte]) = {
        metrics <- EitherT(Future.successful(extractMetrics(contents)))
        asyncMetricsId = storeMetrics(metrics)
        asyncFileId = storeUpload(metrics)
        metricsId <- EitherT.right(asyncMetricsId)
        res = UploadId(metricsId, fileId)
    } yield res


The ``?`` object defines all ``EitherT`` calls necessary for lifting our type into a function called ``<~``. The names
are not exactly pronounceable, but for good reason. Since You can omit the dot (``.``) and parentheses
(``(`` and ``)``) when calling an object's function, you can then do this with the ``?`` object:

.. code-block:: scala

    ...

    // This must be done in an object extending the
    // `FutureMixin` trait now.
    def processUpload(contents: Array[Byte]) = {
        metrics <- ? <~ extractMetrics(contents)
        asyncMetricsId = storeMetrics(metrics)
        asyncFileId = storeUpload(metrics)
        metricsId <- ? <~ asyncMetricsId
        res = UploadId(metricsId, fileId)
    } yield res

Notice there that we don't need to call ``EitherT`` manually again. For the first statement, for example, we are doing:

.. code-block:: scala

    ...

        metrics <- ? <~ extractMetrics(contents)

Which is essentially the same as:

.. code-block:: scala

    ...

        metrics <- ?.<~(extractMetrics(contents))

Some would still prefer to user ``EitherT`` here, and that is fine. The ``?`` object is simply there to give you the
option to shorten your ``EitherT`` instantiations by leveraging Scala's features.


Next Steps
----------

We have covered a lot now! Now you should be ready to implement all the things we have learned in a real Sentinel
``Processor``. Head over to the :doc:`next section </devs_tutorial_processors>` to do just that.
