Composable Error Handling
=========================

Sentinel, being a framework that interacts with a database, faces common problems. It needs to validate
user-supplied data, write and read from the database, and so on. These are not new problems and various other frameworks
and/or languages have solved it in their own way.

Since Sentinel is based on Scala, we'll take a look at how we can actually achieve these things nicely in Scala. It may
look daunting at first, but the result is worth the effort: composable clean code.

We'll cover three topics in particular:

    1. Dealing expected failures such as user-input errors, in this guide.

    2. Launching task asynchronously using ``Future``, in the next guide: :doc:`devs_tutorial_async`.

    3. How Sentinel composes error handling asynchronously, also in the next guide: :doc:`devs_tutorial_async`.

.. note::

    This guide is geared towards use cases in Sentinel and is by no means comprehensive. Readers are expected to be
    familiar with Scala, particularly using `for comprehensions <http://docs.scala-lang.org/tutorials/FAQ/yield.html>`_
    and `pattern matching <http://docs.scala-lang.org/tutorials/tour/pattern-matching.html>`_. It is also a good idea
    to be familiar with the `scalaz library <https://github.com/scalaz/scalaz>`_ as Sentinel also makes considerable use
    of it.


Dealing with expected failures
------------------------------

Upon receiving a file upload, Sentinel parses the contents into a JSON object and extracts all the metrics contained
within. This means that Sentinel needs to ensure that:

    1. The file is valid JSON.
    2. The JSON file contains the expected metrics values.

If any of these requirements are not met, Sentinel should notify the uploader of the error since this is something that
we can expect the uploader to correct.

Let's assume that the uploaded data is stored as a ``Array[Byte]`` object and the data is parsed into a ``JValue``
object, as defined by the `Json4s <http://json4s.org>`_ library. We can use Json4s's ``parse`` function to extract our
JSON. It has the following (simplified) signature:

.. code-block:: scala

    // `JsonInput` can be many different types,
    // `java.io.ByteArrayInputStream` among them.
    // `Formats` is a Json4s-exported object that
    // determines how the parsing should be done.
    def parse(in: JsonInput)(implicit formats: Formats): JValue

We can then write a function to extract JSON out of our byte array using ``parse``.

.. code-block:: scala

    import java.io.ByteArrayInputStream
    import org.json4s.JValue
    import org.json4s.jackson.JsonMethods.parse

    // For now, we can use the default `Formats`
    // as the implicit value
    implicit val formats = org.json4s.DefaultFormats

    // first attempt
    def extractJson(contents: Array[Byte]): JValue =
        parse(new ByteArrayInputStream(contents))

In an ideal world, our function would always return a ``JValue`` given a byte array. In reality, our function will be
faced with user inputs which should be treated with extreme caution. That includes expecting corner cases, for example
if the byte array is not valid JSON or if the byte array is empty. Those cases will cause ``parse`` to throw an
exception that must be dealt with, otherwise our normal program flow is interrupted.


The ``Option`` type
-------------------

How should we handle the exception? Having a strong functional flavour, Scala offers a nice alternative using a type
called the ``Option[T]`` type. The short gist is that it allows us to encode return types that may or may not exist.
It has two concrete values: ``Some(_)`` or ``None``. For example, if a function has return type ``Option[Int]`` it can
either be of value ``Some(3)`` (which means it has the value 3) or ``None`` (which means no number was returned).

While exceptions may be more suitable for some cases, ``Option`` offers interesting benefits of its own. With
``Option``, we explicitly state the possibility that a function may not return its intended value in its very signature.
Consequently, this informs any caller of the function to deal with this possibility, making it less prone to errors.

It turns out also that the ``Option`` pattern occurs frequently in code. Functions that perform integer division, for
example, needs to acknowledge the fact that division by zero may occur. Another example is functions that return the
first item of an array. What should the function do when the array is empty? ``Option`` fits well into these and many
other cases. Over time, this has resulted in a set of common operations that can be applied to ``Option`` objects
that we can use to make our code more concise without sacrificing functionality. You can check out the
`official documentation <http://www.scala-lang.org/api/current/index.html#scala.Option>`_ for a glance of what these
operations are.

.. tip::

    For a more in depth treatment of ``Option``, we find the guide
    `here <http://danielwestheide.com/blog/2012/12/19/the-neophytes-guide-to-scala-part-5-the-option-type.html>`_
    informative.

Some operations worth highlighting are ``flatMap`` and ``withFilter``. They are used by Scala's for-comprehension,
which means you can chain code that returns ``Option`` like this:

.. code-block:: scala

    def alpha(): Option[Int] = { ... }
    def beta(n: Int): Option[String] = { ... }
    def gamma(s: String): Option[Double] = { ... }

    val finalResult: Option[Double] = for  {
        result1 <- alpha()
        result2 <- beta(result1)
        result3 <- gamma(result2)
        squared = result3 * result3
    } yield squared

In the code snippet above, ``alpha`` is called first, then its result is used for calling ``beta``, whose result is
used for calling ``gamma``. The beauty of the chain is that if any of the functions return ``None``, subsequent
functions will not be called and we get ``None`` as the value of ``finalResult``. There is no need to do manual checks
using ``if`` blocks. Furthermore, the for comprehension automatically unwraps ``result1`` and ``result2`` out of
``Option`` when used for calling ``beta`` and ``gamma``. Finally, we can slip an extra value declaration (``squared``)
which will only work if our chain produces an expected ``result3`` value.

.. tip::

    ``flatMap`` and ``withFilter`` are not the only methods that for comprehensions desugars into. Check out the
    `official FAQ <http://docs.scala-lang.org/tutorials/FAQ/yield.html>`_ on other possible methods.

Going back to our JSON extractor function, we need to update it so that it returns ``Option[JValue]``. Luckily, Json4s
also already has us covered here. In addition to ``parse``, it also provides a function called ``parseOpt`` which only
returns a ``JValue`` if the given input can be parsed into JSON. It has the following (simplified) signature:

.. code-block:: scala

    def parseOpt(in: JsonInput)(implicit formats: Formats): Option[JValue]

Our function then becomes:

.. code-block:: scala

    import java.io.ByteArrayInputStream
    import org.json4s.JValue
    import org.json4s.jackson.JsonMethods.parseOpt

    implicit val formats = org.json4s.DefaultFormats

    // second attempt
    def extractJson(contents: Array[Byte]): Option[JValue] =
        parseOpt(new ByteArrayInputStream(contents))


The ``Either`` type
-------------------

Our function now has a better return type for any of its caller. Notice however, that ``Option`` is black and white.
Either our function returns the expected ``JValue`` or not. In contrast, there are more than one way that the parsing
can fail. The JSON file could be malformed, maybe containing an extra comma or missing a bracket somewhere. There could
also be network errors that cause no bytes to be uploaded, resulting in an empty byte array. These are information that
is potentially useful for uploaders, so it would be desirable for Sentinel to be able to report what kind of error
causes the parsing to fail. In short, we would like to encode the possibility that our function may fail in multiple
ways.

Enter the ``Either`` type. ``Either`` allows us to encode two return types into one, unlike ``Option`` which only
allows one. Its two concrete values are either ``Right``, conventionally used to encode the type returned for successful
function calls, and ``Left`` for encoding errors.

This should be clearer with an example. We will use a function that returns the sum of the first and last item of a
``List[Int]`` to illustrate this. Here, the given list must contain at least two items. If that's not the case, we would
like to notify the caller. One way to write this with ``Either`` is like so:

.. code-block:: scala

    def sumFirstLast(list: List[Int]): Either[String, Int] =
        if (list.isEmpty) Left("List has no items.")
        else if (list.size == 1) Left("List only has one item.")
        else Right(list.head + list.last)

The type that encodes the error (the left type) can be anything. We use ``String`` here for convenience, but other
types such as ``List[String]`` or even a custom type can be used.

We can now further improve our ``extractJson`` function using ``Either``. Since Json4s does not provide a parsing
function that returns ``Either``, we need to modify our own function a bit:

.. code-block:: scala

    import java.io.ByteArrayInputStream
    import org.json4s.JValue
    import org.json4s.jackson.JsonMethods.parseOpt

    implicit val formats = org.json4s.DefaultFormats

    // third attempt
    def extractJson(contents: Array[Byte]): Either[String, JValue] =
        if (contents.isEmpty) Left("Nothing to parse.")
        else parseOpt(new ByteArrayInputStream(contents)) match {
            case None     => Left("Invalid syntax.")
            case Some(jv) => Right(jv)
        }


The disjunction type: ``\/``
----------------------------

Our iterations are looking better, but we are not there yet. ``Either``, as provided by the Scala standard library,
unfortunately does not play very well with for comprehensions like ``Option`` does. Scala does not enforce that
``Either``'s ``Left`` encodes the error return type (and consequently, that ``Right`` encodes the succes type). What
this means is that in for comprehensions, we have to tell whether we expect the ``Right`` or ``Left`` type for each
call. This is done by calling the ``Either.right`` or ``Either.left`` method.

.. code-block:: scala

    def uno(): Either[String, Int] = { ... }
    def dos(n: Int): Either[String, String] = { ... }
    def tres(s: String): Either[String, Double] = { ... }

    val finalResult: Either[String, Double] = for {
        result1 <- uno().right
        result2 <- dos(result1).right
        result3 <- tres(result2).right
    } yield result3

It seems like a minor inconvenience to add ``.right``, but there is something going on under the hood with ``.right``
and ``.left``. They do not actually create ``Right`` and ``Left``, but ``RightProjection`` and ``LeftProjection``, which
is a different type with different properties. The practical consequence is that the code below will not compile
anymore (unlike its ``Option`` counterpart):

.. code-block:: scala

    val finalResult: Either[String, Double] = for {
        result1 <- uno().right
        result2 <- dos(result1).right
        result3 <- tres(result2).right
        squared = result3 * result3
    } yield squared

To get it working, we need to manually wrap the ``squared`` declaration inside an ``Either``, invoke ``.right``, and
replace the value assignment operator:

.. code-block:: scala

    val finalResult: Either[String, Double] = for {
        result1 <- uno().right
        result2 <- dos(result1).right
        result3 <- tres(result2).right
        squared <- Right(result3 * result3).right
    } yield squared

This is getting unecessarily verbose. We have to invoke ``.right`` every time and we lose the ability to declare values
inside for comprehensions. To remedy this, we need to use the scalaz library.

Scalaz is a third party Scala library that provides many useful functional programming abstractions. One that we will
use now is called ``\/`` (often called the disjunction type, since it is inspired by the mathematical disjunction
operator ∨). It is very similar to ``Either``, except for the fact that it is right-biased. This means, it expects the
error type to be encoded as the left type and the expected type to be encoded as the right type.

Here is a quick comparison between ``Either`` and ``\/``:

.. code-block:: scala

    import scalaz._, Scalaz._

    // Type declaration.
    // We can use the `\/` type as an infix
    // operator as well, as shown in `value3`
    // declaration below
    def value1: Either[String, Int] // Scala
    def value2: \/[String, Int]     // scalaz
    def value3: String \/ Int       // scalaz

    // Right instance creation.
    // The scalaz constructor is the type name,
    // plus the side we use: `\/` appended with `-`
    val value4: Either[String, Int] = Right(3) // Scala
    val value5: String \/ Int       = \/-(3)   // scalaz

    // Left instance creation.
    // The scalaz constructor is analogous to its
    // right type counterpart: `\/` prepended with `-`
    val value6: Either[String, Int] = Left("err") // Scala
    val value7: String \/ Int       = -\/("err")  // scalaz

Our earlier example can now be made more concise using the disjunction type:

.. code-block:: scala

    def uno(): String \/ Int = { ... }
    def dos(n: Int): String \/ String = { ... }
    def tres(s: String): String \/ Double = { ... }

    val finalResult: String \/ Double = for {
        result1 <- uno()
        result2 <- dos(result1)
        result3 <- tres(result2)
        squared = result3 * result3
    } yield squared

One more thing: notice that we always encode the error type / left type as ``String`` and we need to redeclare it
every time. We can make this even shorter by creating a type alias to disjunction whose left type is always ``String``.
Let's call this alias ``Perhaps``:

.. code-block:: scala

    type Perhaps[+T] = String \/ T

    def uno(): Perhaps[Int] = { ... }
    def dos(n: Int): Perhaps[String] = { ... }
    def tres(s: String): Perhaps[Double] = { ... }

    val finalResult: Perhaps[Double] = for {
        result1 <- uno()
        result2 <- dos(result1)
        result3 <- tres(result2)
        squared = result3 * result3
    } yield squared

And finally, going back to our JSON extractor example, we need to update it like so:

.. code-block:: scala

    import java.io.ByteArrayInputStream
    import org.json4s.JValue
    import org.json4s.jackson.JsonMethods.parseOpt
    import scalaz._, Scalaz._

    implicit val formats = org.json4s.DefaultFormats

    type Perhaps[+T] = String \/ T

    // fourth attempt
    def extractJson(contents: Array[Byte]): Perhaps[JValue] =
        if (contents.isEmpty) -\/("Nothing to parse.")
        else parseOpt(new ByteArrayInputStream(contents)) match {
            case None     => -\/("Invalid syntax.")
            case Some(jv) => \/-(jv)
        }

Going even further, we can replace the pattern match with a call to scalaz's ``.toRightDisjunction``. This can be done
on the ``Option[JValue]`` value that ``parseOpt`` returns. The argument is the error value; the value that we would
like to return in case ``parseOpt`` evaluates to ``None``.

.. code-block:: scala

    ...

    // fourth attempt
    def extractJson(contents: Array[Byte]): Perhaps[JValue] =
        if (contents.isEmpty) -\/("Nothing to parse.")
        else parseOpt(new ByteArrayInputStream(contents))
            .toRightDisjunction("Invalid syntax.")

We can further shorter this using the ``\/>`` function, which is basically an alias to ``.toRighDisjunction``:

.. code-block:: scala

    ...

    // fourth attempt
    def extractJson(contents: Array[Byte]): Perhaps[JValue] =
        if (contents.isEmpty) -\/("Nothing to parse.")
        else parseOpt(contents) \/> "Invalid syntax."

This is functionally the same, and some would prefer the clarity of ``.toRightDisjunction`` instead of ``\/>``'s
brevity. We will stick to ``.toRighDisjunction`` for now.


Comprehensive value extraction
------------------------------

We did not use any for comprehensions in ``extractJson``, though, so why did we bother to use ``\/`` at all? Remember
that creating the JSON object is only the first part of our task. The next part is to extract the necessary metrics
from the created JSON object. At this point it is still possible to have a valid JSON object that does not contain
our expected metrics.

Let's assume that our expected JSON is simple:

.. code-block:: javascript

    {
        "nSnps": 100,
        "nReads": 10000
    }

There are only two values we expect, ``nSnps`` and ``nReads``. Using Json4s, extracting this value would be something
like this:

.. code-block:: scala

    // `json` is our parsed JSON
    val nSnps: Int = (json \ "nSnps").extract[Int]
    val nReads: Int = (json \ "nReads").extract[Int]

We can also use ``.extractOpt`` to extract the values into an ``Option`` type:

.. code-block:: scala

    // By doing `.extractOpt[Int]`, not only do we expect
    // `nSnps` to be present, but we also check that it is
    // parseable into an `Int`.
    val nSnps: Option[Int] = (json \ "nSnps").extractOpt[Int]
    val nReads: Option[Int] = (json \ "nReads").extractOpt[Int]

Now let's put them together in a single function. We'll also create a case class to contain the results in a single
object as well. Since we are doing two extractions, it's a good idea then to use the disjunction type instead of
``Option`` so that we can see if any error occurs.

.. code-block:: scala

    ...

    case class Metrics(nSnps: Int, nReads: Int)

    def extractMetrics(json: JValue): Perhaps[Metrics] = for {
        nSnps <- (json \ "nSnps")
            .extractOpt[Int]
            .toRightDisjunction("nSnps not found.")
        nReads <- (json \ "nReads")
            .extractOpt[Int]
            .toRightDisjunction("nReads not found.")
        metrics = Metrics(nSnps, nReads)
    } yield metrics

Both extraction steps now combine nicely in one for comprehension. The code is concise and we can still immediately see
that both ``nSnps`` and ``nReads`` must be present in the parsed JSON object. If any of them is not present, an error
message will be returned appropriately.

What's even nicer, is that ``extractMetrics`` compose well with our previous ``extractJson``. We can now write one
function that does both:

.. code-block:: scala

    ...

    def processUpload(contents: Array[Byte]): Perhaps[Metrics] = for {
        json <- extractJson(contents)
        metrics <- extractMetrics(json)
    } yield metrics

That's it. Our ``processUpload`` function extracts JSON from a byte array and then extracts the expected metrics from
the JSON object. If any error occurs within any of these steps, we will get the error message appropriately. If we ever
want to add additional steps afterwards (maybe checking if the uploaded metrics is already in a database or so), we
can simply add another line in the for comprehension so long as our function call returns a ``Perhaps`` type.


Sentinel's Error Type
---------------------

While ``String`` is a useful error type in some cases, in our cases it is not exactly the most suitable type for errors.
Consider a case where our uploaded JSON does not contain both ``nSnps`` and ``nReads``. In that case, the user would
first get an error message saying 'nSnps not found.'. Assuming he/she fixes the JSON by only adding ``nSnps``, he/she
would then get another error on the second attempt, saying 'nReads not found.'. This should have been displayed on the
first upload, since the error was already present then.

This approach of failing on the first error we see (often called failing fast) is then not exactly suitable for our
``extractMetrics`` function. Another approach where we accumulate the errors first (failing slow) before displaying
them seems more appropriate. To do so, we need to tweak our error type to be a ``List[String]`` instead of the
current ``String``. We can then add error messages to the list and return it to the user eventually.

It's only for ``extractMetrics``, though. We would still like to fail fast in ``extractJson`` as both errors we expect
to encounter there can not occur simultaneously. If the JSON file is empty, it must not contain any syntax errors and
vice versa.

Sentinel reconciles this by having a custom type for its error type, called the ``ApiPayload``. It is a case class
that contains both ``String`` and ``List[String]``.  The ``ApiPayload``
type is also associated with specific `HTTP status codes <https://en.wikipedia.org/wiki/List_of_HTTP_status_codes>`_.
This is because the error messages that Sentinel displays must be sent via HTTP and thus must be associated with a
specific code.

Its simplified signature is:

.. code-block:: scala

    // `httpFunc` defaults to a function
    // that returns HTTP 500
    sealed case class ApiPayload(
        message: String,
        hints: List[String],
        httpFunc: ApiPayload => ActionResult)

The idea here is that we always have a single error message that we want to display to users (the ``message`` attribute).
Accumulated errors can be grouped in ``hints``, if there are any. We also associate a specific error message with
a specific HTTP error code in one place.

.. note::

    Being based on the Scalatra framework, Sentinel uses Scalatra's
    `ActionResult <http://www.scalatra.org/2.4/guides/http/actions.html>`_ to denote HTTP actions. Scalatra already
    associates the canonical HTTP status message with the error code (for example ``InternalServerError`` has the 500
    code). Check out the Scalatra documentation if you need more background on ``ActionResult``.

Additionally, ``ApiPayload`` objects are transformed into plain JSON that are then sent back to the user. The
JSON representation displays only ``message`` and ``hints``, since ``httpFunc`` is only for internal Sentinel use.

An example of an ``ApiPayload`` would look something like this:

.. code-block:: scala

    // `BadRequest` is Scalatra's function
    // that evaluates to HTTP 400.
    val JsonInputError = ApiPayload(
        message = "JSON input can not be parsed.",
        hints = List("Input is empty."),
        httpFunc = (ap) => BadRequest(ap))

It can get a bit tedious, as you can see. Some HTTP error messages occur more frequently than others, fortunately, so
Sentinel already creates some predefined ``ApiPayload`` objects that you can use. They are all defined in
``nl.lumc.sasc.sentinel.models.Payloads``.

In our case, we can use ``JsonValidationError``. It is always associated with HTTP 400 and its ``message`` attribute
is hard coded to "JSON is invalid.". We only need to supply the hints inside a ``List[String]``. Moreover, our
disjunction type ``ApiPayload \/ T`` is also already defined by sentinel in ``nl.lumc.sasc.sentinel.models.Perhaps``,
so we can use that.

Let's now update our functions to use ``ApiPayload`` (along with some style updates). We will also outline how far we
have written our functions:

.. code-block:: scala

    // We import a mutable list for collecting our errors
    import collection.mutable.ListBuffer
    import java.io.ByteArrayInputStream
    import org.json4s.JValue
    import org.json4s.jackson.JsonMethods.parseOpt
    import scalaz._, Scalaz._
    import nl.lumc.sasc.sentinel.models.{ Payloads, Perhaps }, Payloads._

    implicit val formats = org.json4s.DefaultFormats

    case class Metrics(nSnps: Int, nReads: Int)

    // Our change here is mostly to replace
    // `String` with `ApiPayload`.
    def extractJson(contents: Array[Byte]): Perhaps[JValue] =
        if (contents.isEmpty) {
            val hints = JsonValidationError("Nothing to parse.")
            -\/(hints)
        } else {
            val stream = new ByteArrayInputStream(contents)
            val hints = JsonValidationError("Invalid syntax.")
            parseOpt(input).toRightDisjunction(hints)
        }

    // This is where most our changes happen
    def extractMetrics(json: JValue): Perhaps[Metrics] = {

        val maybe1 = (json \ "nSnps").extractOpt[Int]
        val maybe2 = (json \ "nReads").extractOpt[Int]

        (maybe1, maybe2) match {
            // When both values are defined, we can create
            // our desired return type. Remember we need
            // to wrap it inside `\/` still.
            case (Some(nSnps), Some(nReads)) =>
                \/-(Metrics(nSnps, nReads))
            // Otherwise we further check on what's missing
            case otherwise =>
                val errors: ListBuffer[String] = ListBuffer()
                if (!maybe1.isDefined) errors :+ "nSnps not found."
                if (!maybe2.isDefined) errors :+ "nReads not found."
                -\/(JsonValidationError(errors.toList))
        }
    }

    // This function remains the same.
    def processUpload(contents: Array[Byte]): Perhaps[Metrics] = for {
        json <- extractJson(contents)
        metrics <- extractMetrics(json)
    } yield metrics

And there we have it. Notice that even though we fiddled with the internals of ``extractJson`` and ``extractMetrics``,
our ``processUpload`` function stays the same. This is one of the biggest wins of keeping our API stable. Our functions
all follow the pattern of accepting concrete values and returning them wrapped in ``Perhaps``. This is all intentional,
so that we can keep ``processUpload`` clean and extendable.

Fitting the JSON Schema in
^^^^^^^^^^^^^^^^^^^^^^^^^^

Our ``extractMetrics`` function looks good now, but notice that it is already quite verbose even for a small JSON.
This is why we recommend that you define JSON schemas for your expected summary files. Sentinel can then validate
based on that schema, accumulating all the errors it sees.

The Sentinel validation function is called ``validateJson``, which has the following signature:

.. code-block:: scala

    def validateJson(json: JValue): Perhaps[JValue]

You can see that it expects as its input a parsed JSON object. This means that we need to create a JSON object first
before we validate it. To this end, Sentinel also provides an ``extractJson`` function. Its signature is the same as
the ``extractJson`` function you have been writing. We can then combine extraction and validation together in one
function like so:

.. code-block:: scala

    def extractAndValidateJson(contents: Array[Byte]): Perhaps[JValue] =
        for {
            json <- extractJson(contents)
            validatedJson <- validateJson(json)
        } yield validatedJson

Sentinel provides ``extractAndValidateJson`` as well. In fact, that is also how Sentinel composes JSON extraction and
JSON validation internally: using a single for comprehension.


Next Steps
----------

We hope we have convinced you that encoding errors as the return type instead of throwing exceptions can make our code
cleaner and more composable. In the next section, :doc:`devs_tutorial_async`, we will be combining our
``Perhaps`` type with Scala's ``Future`` so that we can process data asynchronously.
