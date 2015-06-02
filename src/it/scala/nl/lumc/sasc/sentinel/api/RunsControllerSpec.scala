package nl.lumc.sasc.sentinel.api

import java.io.{ File, RandomAccessFile }

import com.google.common.io.Files
import org.apache.commons.io.FileUtils.{ deleteDirectory, deleteQuietly }
import org.json4s._
import org.json4s.jackson.JsonMethods._

import nl.lumc.sasc.sentinel.HeaderApiKey
import nl.lumc.sasc.sentinel.SentinelServletSpec
import nl.lumc.sasc.sentinel.models.{ CommonErrors, RunDocument, User }
import nl.lumc.sasc.sentinel.settings._

class RunsControllerSpec extends SentinelServletSpec {

  sequential

  override def stop(): Unit = {
    deleteDirectory(tempDir)
    super.stop()
  }

  protected lazy val tempDir = Files.createTempDir()

  protected def createTempFile(name: String): File = new File(tempDir, name)

  protected def fillFile(file: File, size: Long): File = {
    val raf = new RandomAccessFile(file, "rw")
    raf.setLength(size)
    raf.close()
    file
  }

  class UnsupportedUploadContext extends SpecContext.PriorRunUploadClean {
    def pipelineParam = "unsupported"
    lazy val uploadPayload = makeUploadable("/schema_examples/unsupported.json")
    lazy val runId = (parse(priorResponse.body) \ "runId").extract[String]
  }

  class UnsupportedThenGentrapUploadContext extends UnsupportedUploadContext {

    def pipeline2 = "gentrap"
    def uploadParams2 = Seq(("userId", Users.avg2.id), ("pipeline", pipeline2))
    def uploadFile2 = Map("run" -> uploadPayload2)
    def uploadHeader2 = Map(HeaderApiKey -> Users.avg2.activeKey)
    lazy val uploadPayload2 = makeUploadable("/schema_examples/biopet/v0.4/gentrap_single_sample_single_lib.json")
    lazy val runId2 = (parse(priorResponses(1).body) \ "runId").extract[String]

    override def priorRequests = super.priorRequests ++ Seq(
      () => post(uploadEndpoint, uploadParams2, uploadFile2, uploadHeader2) { response }
    )

    s"and another user uploads the '$pipeline2' summary file" in {
      priorResponses.head.statusLine.code mustEqual 201
    }
  }

  implicit val swagger = new SentinelSwagger
  implicit val mongo = dao
  val servlet = new RunsController
  val baseEndpoint = "/runs"
  addServlet(servlet, s"$baseEndpoint/*")

  s"POST '$baseEndpoint'" >> {
    br

    val endpoint = baseEndpoint

    "when the pipeline is not specified" should {
      "return status 400 and the expected message" in {
        post(endpoint, Seq(("userId", Users.avg.id))) {
          status mustEqual 400
          contentType mustEqual "application/json"
          body must /("message" -> "Pipeline not specified.")
        }
      }
    }

    "when the request body is empty" should {
      "return status 400 and the expected message" in {
        post(endpoint, Seq(("userId", Users.avg.id), ("pipeline", "unsupported"))) {
          status mustEqual 400
          contentType mustEqual "application/json"
          body must /("message" -> "Run summary file not specified.")
        }
      }
    }

    "when an invalid pipeline is specified" should {
      "return status 400 and the expected message" in {
        val file = makeUploadable("/schema_examples/unsupported.json")
        post(endpoint, Seq(("userId", Users.avg.id), ("pipeline", "devtest")), Map("run" -> file)) {
          status mustEqual 400
          contentType mustEqual "application/json"
          body must /("message" -> "Pipeline parameter is invalid.")
          body must /("data" -> "Valid values are .+".r)
        }
      }
    }

    s"when the submitted run summary exceeds $MaxRunSummarySizeMb MB" should {
      "return status 413 and the expected message" in {
        val tooBigFile = createTempFile("tooBig.json")
        post(endpoint, Seq(("userId", Users.avg.id), ("pipeline", "unsupported")), Map("run" -> tooBigFile)) {
          status mustEqual 413
          contentType mustEqual "application/json"
          body must /("message" -> """Run summary exceeded maximum allowed size of \d+ MB.""".r)
        } before {
          fillFile(tooBigFile, MaxRunSummarySize + 100)
        } after {
          deleteQuietly(tooBigFile)
        }
      }
    }

    "using the 'unsupported' pipeline summary file" >> {
      br

      val pipeline = "unsupported"
      lazy val runUpload = makeUploadable("/schema_examples/unsupported.json")

      "when a run summary that passes all validation is uploaded" should {
        "return status 201 and the expected payload" in new ExampleContext.CleanDatabaseWithUser {
          post(endpoint, Seq(("userId", user.id), ("pipeline", pipeline)), Map("run" -> runUpload),
            Map(HeaderApiKey -> user.activeKey)) {
            status mustEqual 201
            contentType mustEqual "application/json"
            body must /("creationTimeUtc" -> ".+".r)
            body must /("nLibs" -> 0)
            body must /("nSamples" -> 0)
            body must /("pipeline" -> "unsupported")
            body must /("runId" -> """\S+""".r)
            body must /("uploaderId" -> user.id)
            body must not /("annotIds" -> ".+".r)
            body must not /("refId" -> ".+".r)
            body must not /("sampleIds" -> ".+".r)
          }
        }
      }

      "when the same run summary is uploaded more than once by different users" should {
        "return status 201 and the expected payload" in new ExampleContext.CleanDatabaseWithUser {
          def fileMap = Map("run" -> runUpload)
          override def before = {
            super.before
            post(endpoint, Seq(("userId", Users.avg2.id), ("pipeline", pipeline)), fileMap,
              Map(HeaderApiKey -> Users.avg2.activeKey)) {}
          }
          post(endpoint, Seq(("userId", user.id), ("pipeline", pipeline)), fileMap,
            Map(HeaderApiKey -> user.activeKey)) {
            status mustEqual 201
            body must /("creationTimeUtc" -> ".+".r)
            body must /("nLibs" -> 0)
            body must /("nSamples" -> 0)
            body must /("pipeline" -> "unsupported")
            body must /("runId" -> """\S+""".r)
            body must /("uploaderId" -> user.id)
            body must not /("annotIds" -> ".+".r)
            body must not /("refId" -> ".+".r)
            body must not /("sampleIds" -> ".+".r)
          }
        }
      }

      "when the user ID is not specified" should {
        "return status 400 and the expected message" in {
          post(endpoint, Seq(("pipeline", pipeline)), Map("run" -> runUpload)) {
            status mustEqual 400
            contentType mustEqual "application/json"
            body must /("message" -> "User ID not specified.")
          }
        }
      }

      "when a non-JSON file is uploaded" should {
        "return status 400 and the expected message" in new ExampleContext.CleanDatabaseWithUser {
          val fileMap = Map("run" -> makeUploadable("/schema_examples/not.json"))
          post(endpoint, Seq(("userId", user.id), ("pipeline", pipeline)), fileMap,
            Map(HeaderApiKey -> user.activeKey)) {
            status mustEqual 400
            contentType mustEqual "application/json"
            body must /("message" -> "File is not JSON-formatted.")
          }
        }
      }

      "when an invalid JSON run summary is uploaded" should {
        "return status 400 and the expected message" in new ExampleContext.CleanDatabaseWithUser {
          val fileMap = Map("run" -> makeUploadable("/schema_examples/invalid.json"))
          post(endpoint, Seq(("userId", user.id), ("pipeline", pipeline)), fileMap,
            Map(HeaderApiKey -> user.activeKey)) {
            status mustEqual 400
            contentType mustEqual "application/json"
            body must /("message" -> "JSON run summary is invalid.")
          }
        }
      }

      "when the same run summary is uploaded more than once by the same user" should {
        "return status 400 and the expected message" in new ExampleContext.CleanDatabaseWithUser {
          def params = Seq(("userId", user.id), ("pipeline", pipeline))
          def headers = Map(HeaderApiKey -> user.activeKey)
          def fileMap = Map("run" -> runUpload)
          override def before = {
            super.before
            post(endpoint, params, fileMap, headers) {}
          }
          post(endpoint, params, fileMap, headers) {
            status mustEqual 400
            contentType mustEqual "application/json"
            body must /("message" -> "Run summary already uploaded by the user.")
          }
        }
      }

      s"when the user does not provide the $HeaderApiKey header" should {
        "return status 401, the challenge response header, and the expected message" in new ExampleContext.CleanDatabaseWithUser {
          post(endpoint, Seq(("userId", user.id), ("pipeline", pipeline)), Map("run" -> runUpload)) {
            status mustEqual 401
            contentType mustEqual "application/json"
            header must havePair("WWW-Authenticate" -> "SimpleKey realm=\"Sentinel Ops\"")
            body must /("message" -> "Authentication required to access resource.")
          }
        }
      }

      s"when the provided $HeaderApiKey does not match the one owned by the user" should {
        "return status 401, the challenge response header, and the expected message" in new ExampleContext.CleanDatabaseWithUser {
          post(endpoint, Seq(("userId", user.id), ("pipeline", pipeline)), Map("run" -> runUpload),
            Map(HeaderApiKey -> (user.activeKey + "nono"))) {
              status mustEqual 401
              contentType mustEqual "application/json"
              header must havePair("WWW-Authenticate" -> "SimpleKey realm=\"Sentinel Ops\"")
              body must /("message" -> "Authentication required to access resource.")
            }
        }
      }

      "when a user without a verified email address uploads a run summary" should {
        "return status 403 and the expected message" in new ExampleContext.CleanDatabaseWithUser {
          post(endpoint, Seq(("userId", Users.unverified.id), ("pipeline", pipeline)), Map("run" -> runUpload),
            Map(HeaderApiKey -> Users.unverified.activeKey)) {
              status mustEqual 403
              contentType mustEqual "application/json"
              body must /("message" -> "Unauthorized to access resource.")
            }
        }
      }
    }

    "using the 'gentrap' pipeline summary run file" >> {
      br

      def params(implicit user: User) = Seq(("userId", user.id), ("pipeline", "gentrap"))
      def headers(implicit user: User) = Map(HeaderApiKey -> user.activeKey)
      lazy val v04SSampleSLib = makeUploadable("/schema_examples/biopet/v0.4/gentrap_single_sample_single_lib.json")

      "when the v0.4 run summary (single sample, single lib) is uploaded to an empty database" should {
        "return status 201 and the expected payload" in new ExampleContext.CleanDatabaseWithUser {
          post(endpoint, params, Map("run" -> v04SSampleSLib), headers) {
            status mustEqual 201
            contentType mustEqual "application/json"
            body must /("runId" -> """\S+""".r)
            body must /("uploaderId" -> user.id)
            body must /("pipeline" -> "gentrap")
            body must /("nSamples" -> 1)
            body must /("nLibs" -> 1)
            body must /("runId" -> """\S+""".r)
            body must not /("sampleIds" -> ".+".r)
            // TODO: use raw JSON matchers when we upgrade specs2
            jsonBody must beSome.like { case json => (json \ "annotIds") must haveSize(3) }
          }
        }
      }

    }
  }

  s"GET '$baseEndpoint'" >> {
    br

    val endpoint = baseEndpoint

    "when the database is empty" >> inline {

      new SpecContext.CleanDatabaseWithUser {

        "when the user authenticates correctly" should {

          val params = Seq(("userId", user.id))
          val headers = Map(HeaderApiKey -> user.activeKey)

          "return status 200" in {
            get(endpoint, params, headers) { status mustEqual 200 }
          }

          "return an empty JSON list" in {
            get(endpoint, params, headers) {
              contentType mustEqual "application/json"
              jsonBody must haveSize(0)
            }
          }
        }

        "when the user ID is not specified" should {

          val headers = Map(HeaderApiKey -> Users.unverified.activeKey)

          "return status 400" in {
            get(endpoint, Seq(), headers) { status mustEqual 400 }
          }

          "return a JSON object of the expected message" in {
            get(endpoint, Seq(), headers) {
              contentType mustEqual "application/json"
              body must /("message" -> CommonErrors.UnspecifiedUserId.message)
            }
          }
        }

        "when the user does not authenticate correctly" should {

          val params = Seq(("userId", user.id))
          val headers = Map(HeaderApiKey -> (user.activeKey + "diff"))

          "return status 401" in {
            get(endpoint, params, headers) { status mustEqual 401 }
          }

          "return the authentication challenge header" in {
            get(endpoint, params, headers) {
              header must havePair("WWW-Authenticate" -> "SimpleKey realm=\"Sentinel Ops\"")
            }
          }

          "return a JSON object of the expected message" in {
            get(endpoint, params, headers) {
              contentType mustEqual "application/json"
              body must /("message" -> CommonErrors.Unauthenticated.message)
            }
          }
        }

        "when the authenticated user is not verified" should {

          val params = Seq(("userId", Users.unverified.id))
          val headers = Map(HeaderApiKey -> Users.unverified.activeKey)

          "return status 403" in {
            get(endpoint, params, headers) { status mustEqual 403 }
          }

          "return a JSON object of the expected message" in {
            get(endpoint, params, headers) {
              contentType mustEqual "application/json"
              body must /("message" -> CommonErrors.Unauthorized.message)
            }
          }
        }
      }
    }

    "using the 'unsupported' and the 'gentrap' run summary files" >> inline {

      new UnsupportedThenGentrapUploadContext {

        "when the user ID is not specified" should {

          val headers = Map(HeaderApiKey -> Users.unverified.activeKey)

          "return status 400" in {
            get(endpoint, Seq(), headers) { status mustEqual 400 }
          }

          "return a JSON object of the expected message" in {
            get(endpoint, Seq(), headers) {
              contentType mustEqual "application/json"
              body must /("message" -> CommonErrors.UnspecifiedUserId.message)
            }
          }
        }

        "when the user does not authenticate correctly" should {

          val params = Seq(("userId", user.id))
          val headers = Map(HeaderApiKey -> (user.activeKey + "diff"))

          "return status 401" in {
            get(endpoint, params, headers) { status mustEqual 401 }
          }

          "return the authentication challenge header" in {
            get(endpoint, params, headers) {
              header must havePair("WWW-Authenticate" -> "SimpleKey realm=\"Sentinel Ops\"")
            }
          }

          "return a JSON object of the expected message" in {
            get(endpoint, params, headers) {
              contentType mustEqual "application/json"
              body must /("message" -> CommonErrors.Unauthenticated.message)
            }
          }
        }

        "when the authenticated user is not verified" should {

          val params = Seq(("userId", Users.unverified.id))
          val headers = Map(HeaderApiKey -> Users.unverified.activeKey)

          "return status 403" in {
            get(endpoint, params, headers) { status mustEqual 403 }
          }

          "return a JSON object of the expected message" in {
            get(endpoint, params, headers) {
              contentType mustEqual "application/json"
              body must /("message" -> CommonErrors.Unauthorized.message)
            }
          }
        }

        "when the user authenticates correctly" >> {
          br

          val params = Seq(("userId", user.id))
          val headers = Map(HeaderApiKey -> user.activeKey)

          "and queries with the default parameter" should {

            "return status 200" in {
              get(endpoint, params, headers) { status mustEqual 200 }
            }

            "return a JSON list containing a single run object with the expected payload" in {
              get(endpoint, params, headers) {
                contentType mustEqual "application/json"
                jsonBody must haveSize(1)
                body must /#(0) */("runId" -> """\S+""".r)
                body must /#(0) */("uploaderId" -> user.id)
                body must /#(0) */("pipeline" -> "unsupported")
                body must /#(0) */("nSamples" -> 0)
                body must /#(0) */("nLibs" -> 0)
                body must not /("sampleIds" -> ".+".r)
                body must not /# 0 */ "refId"
                body must not /# 0 */ "annotIds"
              }
            }
          }

          "and selects for a pipeline he/she has not uploaded" should {

            "return status 200" in {
              get(endpoint, params :+ ("pipelines", "gentrap"), headers) { status mustEqual 200 }
            }

            "return an empty JSON list" in {
              get(endpoint, params :+ ("pipelines", "gentrap"), headers) {
                contentType mustEqual "application/json"
                jsonBody must haveSize(0)
              }
            }
          }

          "and gives an incorrect pipeline parameter" should {

            "return status 400" in {
              get(endpoint, params :+ ("pipelines", "nonexistent"), headers) { status mustEqual 400 }
            }

            "return a JSON object of the expected message" in {
              get(endpoint, params :+ ("pipelines", "nonexistent"), headers) {
                contentType mustEqual "application/json"
                body must /("message" -> "One or more pipeline is invalid.")
                body must /("data") / "invalid pipelines" /# 0 / "nonexistent"
              }
            }
          }
        }
      }
    }
  }

  s"GET '$baseEndpoint/:runId'" >> {
    br

    def endpoint(runId: String) = s"$baseEndpoint/$runId"

    "using the 'unsupported' and the 'gentrap' run summary files" >> inline {

      new UnsupportedThenGentrapUploadContext {

        "when the user ID is not specified" should {

          val headers = Map(HeaderApiKey -> Users.unverified.activeKey)

          "return status 400" in {
            get(endpoint(runId), Seq(), headers) { status mustEqual 400 }
          }

          "return a JSON object of the expected message" in {
            get(endpoint(runId), Seq(), headers) {
              contentType mustEqual "application/json"
              body must /("message" -> CommonErrors.UnspecifiedUserId.message)
            }
          }
        }

        "when the user does not authenticate correctly" should {

          val params = Seq(("userId", user.id))
          val headers = Map(HeaderApiKey -> (user.activeKey + "diff"))

          "return status 401" in {
            get(endpoint(runId), params, headers) { status mustEqual 401 }
          }

          "return the authentication challenge header" in {
            get(endpoint(runId), params, headers) {
              header must havePair("WWW-Authenticate" -> "SimpleKey realm=\"Sentinel Ops\"")
            }
          }

          "return a JSON object of the expected message" in {
            get(endpoint(runId), params, headers) {
              contentType mustEqual "application/json"
              body must /("message" -> CommonErrors.Unauthenticated.message)
            }
          }
        }

        "when the authenticated user is not verified" should {

          val params = Seq(("userId", Users.unverified.id))
          val headers = Map(HeaderApiKey -> Users.unverified.activeKey)

          "return status 403" in {
            get(endpoint(runId), params, headers) { status mustEqual 403 }
          }

          "return a JSON object of the expected message" in {
            get(endpoint(runId), params, headers) {
              contentType mustEqual "application/json"
              body must /("message" -> CommonErrors.Unauthorized.message)
            }
          }
        }

        "when the user authenticates correctly" >> {
          br

          val params = Seq(("userId", user.id))
          val headers = Map(HeaderApiKey -> user.activeKey)

          "and queries a run he/she uploaded" >> {
            br

            def userRunId = runId

            "with the default parameter" should {

              "return status 200" in {
                get(endpoint(userRunId), params, headers) { status mustEqual 200 }
              }

              "return a JSON object of the run data" in {
                get(endpoint(userRunId), params, headers) {
                  contentType mustEqual "application/json"
                  body must /("runId" -> userRunId)
                  body must /("uploaderId" -> user.id)
                  body must /("nSamples" -> 0)
                  body must /("nLibs" -> 0)
                  body must /("pipeline" -> "unsupported")
                  body must not /("sampleIds" -> ".+".r)
                }
              }
            }

            "and sets the download parameter to some true values which" can {

              Seq("", "1", "yes", "true", "ok") foreach { dlParam =>
                s"be '$dlParam'" should {

                  val paramsWithDownload = params :+ ("download", dlParam)

                  "return status 200" in {
                    get(endpoint(userRunId), paramsWithDownload, headers) { status mustEqual 200 }
                  }

                  "return the expected Content-Disposition header" in {
                    get(endpoint(userRunId), paramsWithDownload, headers) {
                      header must havePair("Content-Disposition" -> ("attachment; filename=" + uploadPayload.fileName))
                    }
                  }

                  "return the uploaded summary file" in {
                    get(endpoint(userRunId), paramsWithDownload, headers) {
                      contentType mustEqual "application/octet-stream"
                      body mustEqual new String(uploadPayload.content)
                    }
                  }
                }
              }
            }

            "and sets the download parameter to some false values which" can {

              Seq("0", "no", "false", "none", "null", "nothing") foreach { dlParam =>
                s"be '$dlParam'" should {

                  val paramsWithDownload = params :+ ("download", dlParam)

                  "return status 200" in {
                    get(endpoint(userRunId), paramsWithDownload, headers) { status mustEqual 200 }
                  }

                  "return a JSON object of the run data" in {
                    get(endpoint(userRunId), paramsWithDownload, headers) {
                      contentType mustEqual "application/json"
                      body must /("runId" -> userRunId)
                      body must /("uploaderId" -> user.id)
                      body must not /("sampleIds" -> ".+".r)
                      body must /("nSamples" -> 0)
                      body must /("nLibs" -> 0)
                      body must /("pipeline" -> "unsupported")
                    }
                  }
                }
              }
            }


          }

          "and queries a run he/she did not upload" should {

            "return status 404" in {
              get(endpoint(runId2), params, headers) { status mustEqual 404 }
            }

            "return a JSON object of the expected message" in {
              get(endpoint(runId2), params, headers) {
                contentType mustEqual "application/json"
                body must /("message" -> CommonErrors.MissingRunId.message)
              }
            }
          }

          "and queries a run with an invalid ID" should {

            val invalidId = "nonexistendId"

            "return status 404" in {
              get(endpoint(invalidId), params, headers) { status mustEqual 404 }
            }

            "return a JSON object of the expected message" in  {
              get(endpoint(invalidId), params, headers) {
                contentType mustEqual "application/json"
                body must /("message" -> CommonErrors.MissingRunId.message)
              }
            }
          }
        }
      }
    }
  }

  s"DELETE '$baseEndpoint/:runId'" >> {
    br

    def endpoint(runId: String) = s"$baseEndpoint/$runId"

    "using the 'unsupported' run summary file" >> inline {

      new UnsupportedUploadContext {

        "when the user ID is not specified" should {

          val headers = Map(HeaderApiKey -> user.activeKey)

          "return status 400" in {
            delete(endpoint(runId), Seq(), headers) { status mustEqual 400 }
          }

          "return a JSON object of the expected message" in {
            delete(endpoint(runId), Seq(), headers) {
              contentType mustEqual "application/json"
              body must /("message" -> CommonErrors.UnspecifiedUserId.message)
            }
          }

          "not remove the run record" in {
            get(s"$baseEndpoint/$runId", Seq(("userId", user.id)), headers) {
              status mustEqual 200
              body must /("runId" -> """\S+""".r)
              body must not /("deletionTimeUtc" -> ".+".r)
            }
          }

          "not remove the uploaded run file" in {
            get(s"$baseEndpoint/$runId", Seq(("userId", user.id), ("download", "true")),
              Map(HeaderApiKey -> user.activeKey)) {
              status mustEqual 200
              header must havePair("Content-Disposition" -> ("attachment; filename=" + uploadPayload.fileName))
              contentType mustEqual "application/octet-stream"
              body mustEqual new String(uploadPayload.content)
            }
          }

          "not remove the run from collection listings" in {
            get(s"$baseEndpoint/", Seq(("userId", user.id)), headers) {
              status mustEqual 200
              jsonBody must haveSize(1)
              body must /#(0) /("runId" -> """\S+""".r)
              body must not /# 0 /("deletionTimeUtc" -> ".+".r)
            }
          }
        }

        "when the run ID is not specified" should {

          val params = Seq(("userId", user.id))
          val headers = Map(HeaderApiKey -> user.activeKey)

          "return status 400" in {
            delete(endpoint(""), params, headers) { status mustEqual 400 }
          }

          "return a JSON object of the expected message" in {
            delete(endpoint(""), params, headers) {
              contentType mustEqual "application/json"
              body must /("message" -> CommonErrors.UnspecifiedRunId.message)
            }
          }

          "not remove the run record" in {
            get(s"$baseEndpoint/$runId", Seq(("userId", user.id)), headers) {
              status mustEqual 200
              body must /("runId" -> """\S+""".r)
              body must not /("deletionTimeUtc" -> ".+".r)
            }
          }

          "not remove the uploaded run file" in {
            get(s"$baseEndpoint/$runId", Seq(("userId", user.id), ("download", "true")),
              Map(HeaderApiKey -> user.activeKey)) {
              status mustEqual 200
              header must havePair("Content-Disposition" -> ("attachment; filename=" + uploadPayload.fileName))
              contentType mustEqual "application/octet-stream"
              body mustEqual new String(uploadPayload.content)
            }
          }

          "not remove the run from collection listings" in {
            get(s"$baseEndpoint/", Seq(("userId", user.id)), headers) {
              status mustEqual 200
              jsonBody must haveSize(1)
              body must /#(0) /("runId" -> """\S+""".r)
              body must not /# 0 /("deletionTimeUtc" -> ".+".r)
            }
          }
        }

        "when the user does not authenticate correctly" should {

          val params = Seq(("userId", user.id))
          val headers = Map(HeaderApiKey -> (user.activeKey + "diff"))

          "return status 401" in {
            delete(endpoint(runId), params, headers) { status mustEqual 401 }
          }

          "return the authentication challenge header" in {
            delete(endpoint(runId), params, headers) {
              header must havePair("WWW-Authenticate" -> "SimpleKey realm=\"Sentinel Ops\"")
            }
          }

          "return a JSON object of the expected message" in {
            delete(endpoint(runId), params, headers) {
              contentType mustEqual "application/json"
              body must /("message" -> CommonErrors.Unauthenticated.message)
            }
          }

          "not remove the run record" in {
            get(s"$baseEndpoint/$runId", Seq(("userId", user.id)), Map(HeaderApiKey -> user.activeKey)) {
              status mustEqual 200
              body must /("runId" -> """\S+""".r)
              body must not /("deletionTimeUtc" -> ".+".r)
            }
          }

          "not remove the uploaded run file" in {
            get(s"$baseEndpoint/$runId", Seq(("userId", user.id), ("download", "true")),
              Map(HeaderApiKey -> user.activeKey)) {
              status mustEqual 200
              header must havePair("Content-Disposition" -> ("attachment; filename=" + uploadPayload.fileName))
              contentType mustEqual "application/octet-stream"
              body mustEqual new String(uploadPayload.content)
            }
          }

          "not remove the run from collection listings" in {
            get(s"$baseEndpoint/", Seq(("userId", user.id)), Map(HeaderApiKey -> user.activeKey)) {
              status mustEqual 200
              jsonBody must haveSize(1)
              body must /#(0) /("runId" -> """\S+""".r)
              body must not /# 0 /("deletionTimeUtc" -> ".+".r)
            }
          }
        }
      }
    }

    "when the user authenticates correctly" >> {
      br

      "with the default parameters for the 'unsupported' pipeline should" >> inline {

        new UnsupportedUploadContext {

          val params = Seq(("userId", user.id))
          val headers = Map(HeaderApiKey -> user.activeKey)
          def userRunId = runId

          "return status 202" in {
            delete(endpoint(userRunId), params, headers) {
              status mustEqual 202
            }
          }

          "return a JSON object of the run data with the deletionTimeUtc attribute" in {
            delete(endpoint(userRunId), params, headers) {
              contentType mustEqual "application/json"
              body must /("runId" -> userRunId)
              body must /("uploaderId" -> user.id)
              body must not /("sampleIds" -> ".+".r)
              body must /("nSamples" -> 0)
              body must /("nLibs" -> 0)
              body must /("pipeline" -> "unsupported")
              body must /("deletionTimeUtc" -> ".+".r)
            }
          }

          "remove the run record" in {
            get(s"$baseEndpoint/$runId", Seq(("userId", user.id)), Map(HeaderApiKey -> user.activeKey)) {
              status mustEqual 404
              body must not /("runId" -> ".+".r)
              body must /("message" -> CommonErrors.MissingRunId.message)
            }
          }

          "remove the uploaded run file" in {
            get(s"$baseEndpoint/$runId", Seq(("userId", user.id), ("download", "true")),
              Map(HeaderApiKey -> user.activeKey)) {
                status mustEqual 404
                contentType mustEqual "application/json"
                body must not /("runId" -> ".+".r)
                body must /("message" -> CommonErrors.MissingRunId.message)
              }
          }

          "remove the run from collection listings" in {
            get(s"$baseEndpoint/", Seq(("userId", user.id)), Map(HeaderApiKey -> user.activeKey)) {
              status mustEqual 200
              jsonBody must haveSize(0)
            }
          }

          "return status 202 again when repeated" in {
            delete(endpoint(userRunId), params, headers) {
              status mustEqual 202
            }
          }

          "return a JSON object of the run data with the deletionTimeUtc attribute again when repeated" in {
            delete(endpoint(userRunId), params, headers) {
              contentType mustEqual "application/json"
              body must /("runId" -> userRunId)
              body must /("uploaderId" -> user.id)
              body must not /("sampleIds" -> ".+".r)
              body must /("nSamples" -> 0)
              body must /("nLibs" -> 0)
              body must /("pipeline" -> "unsupported")
              body must /("deletionTimeUtc" -> ".+".r)
            }
          }
        }
      }

      "with the default parameters for the 'gentrap' pipeline (v0.4, single sample, single library) should" >> inline {

        new SpecContext.PriorRunUploadClean {
          def pipelineParam = "gentrap"
          lazy val uploadPayload = makeUploadable("/schema_examples/biopet/v0.4/gentrap_single_sample_single_lib.json")
          lazy val runId = parse(priorResponse.body).extract[RunDocument].runId.toString

          val params = Seq(("userId", user.id))
          val headers = Map(HeaderApiKey -> user.activeKey)
          def userRunId = runId

          "return status 202" in {
            delete(endpoint(userRunId), params, headers) {
              status mustEqual 202
            }
          }

          "return a JSON object of the run data with the deletionTimeUtc attribute" in {
            delete(endpoint(userRunId), params, headers) {
              contentType mustEqual "application/json"
              body must /("runId" -> userRunId)
              body must /("uploaderId" -> user.id)
              body must not /("sampleIds" -> ".+".r)
              body must /("annotIds" -> ".+".r)
              body must /("refId" -> """\S+""".r)
              body must /("nSamples" -> 1)
              body must /("nLibs" -> 1)
              body must /("pipeline" -> "gentrap")
              body must /("deletionTimeUtc" -> ".+".r)
            }
          }

          "remove the run record" in {
            get(s"$baseEndpoint/$runId", Seq(("userId", user.id)), Map(HeaderApiKey -> user.activeKey)) {
              status mustEqual 404
              body must not /("runId" -> ".+".r)
              body must /("message" -> CommonErrors.MissingRunId.message)
            }
          }

          "remove the uploaded run file" in {
            get(s"$baseEndpoint/$runId", Seq(("userId", user.id), ("download", "true")),
              Map(HeaderApiKey -> user.activeKey)) {
              status mustEqual 404
              contentType mustEqual "application/json"
              body must not /("runId" -> ".+".r)
              body must /("message" -> CommonErrors.MissingRunId.message)
            }
          }

          "remove the run from collection listings" in {
            get(s"$baseEndpoint/", Seq(("userId", user.id)), Map(HeaderApiKey -> user.activeKey)) {
              status mustEqual 200
              jsonBody must haveSize(0)
            }
          }

          "return status 202 again when repeated" in {
            delete(endpoint(userRunId), params, headers) {
              status mustEqual 202
            }
          }

          "return a JSON object of the run data with the deletionTimeUtc attribute again when repeated" in {
            delete(endpoint(userRunId), params, headers) {
              contentType mustEqual "application/json"
              body must /("runId" -> userRunId)
              body must /("uploaderId" -> user.id)
              body must not /("sampleIds" -> ".+".r)
              body must /("annotIds" -> ".+".r)
              body must /("refId" -> ".+".r)
              body must /("nSamples" -> 1)
              body must /("nLibs" -> 1)
              body must /("pipeline" -> "gentrap")
              body must /("deletionTimeUtc" -> ".+".r)
            }
          }
        }
      }
    }
  }
}
