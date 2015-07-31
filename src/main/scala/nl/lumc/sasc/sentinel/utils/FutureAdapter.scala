/*
 * Copyright (c) 2015 Leiden University Medical Center and contributors
 *                    (see AUTHORS.md file for details).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.lumc.sasc.sentinel.utils

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._

import scalaz._

/**
 * Base adapter that uses Scala's Futures.
 */
trait FutureAdapter {

  /** Default execution context. */
  implicit protected def context: ExecutionContext

  /** Default timeout. */
  implicit protected def timeout: Duration = 10.seconds

  /** Implicit value for making Future a Scalaz monad. */
  implicit def futureMonad(implicit context: ExecutionContext) = new Monad[Future] {
    override def point[T](t: => T): Future[T] = Future { t }
    override def bind[T, U](ft: Future[T])(f: T => Future[U]): Future[U] = ft.flatMap(f)
  }
}
