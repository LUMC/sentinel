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
package nl.lumc.sasc.sentinel.adapters

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }

import scalaz._, Scalaz._

import nl.lumc.sasc.sentinel.models.ApiPayload

/**
 * Base adapter that uses Scala's Futures.
 */
trait FutureAdapter {

  /** Type alias for operations that returns a user-visible payloads when failing. */
  type Perhaps[+A] = nl.lumc.sasc.sentinel.Perhaps[A]

  /** Default timeout. */
  implicit protected def timeout: Duration = 10.seconds

  /** Implicit value for making Future a Scalaz monad. */
  implicit def futureMonad(implicit context: ExecutionContext) = new Monad[Future] {
    override def point[T](t: => T): Future[T] = Future { t }
    override def bind[T, U](ft: Future[T])(f: T => Future[U]): Future[U] = ft.flatMap(f)
  }

  /**
   * Implicit method for making [[nl.lumc.sasc.sentinel.models.ApiPayload]] a monoid instance.
   *
   * This is required so that for-comprehensions using `ApiMessage` as the left disjunction type
   * works. Scalaz requires that type to be a monoid instance so that when we do `filter`, a zero
   * value can be returned.
   *
   * More at: https://groups.google.com/forum/#!topic/scalaz/9SJbGlpS7Kw
   */
  protected final implicit def apiPayloadMonoid = new Monoid[ApiPayload] {
    def zero = ApiPayload("")
    def append(f1: ApiPayload, f2: => ApiPayload) = {
      val f2c = f2 // force computation
      (f1, f2c) match {
        case (m1, m2) if m1.message.isEmpty && m2.message.isEmpty => m1
        case (m1 @ _, m2) if m2.message.isEmpty => m1
        case (m1, m2 @ _) if m1.message.isEmpty => m2
        case otherwise => ApiPayload(s"${f1.message} | ${f2c.message}", f1.hints ++ f2.hints)
      }
    }
  }

  /**
   * Helper object for stacking the disjunction (`\/`) and `Future` monads.
   *
   * Without this object, we have to wrap methods returning Future[T], ApiPayload \/ T, or T manually in EitherT:
   *
   * {{{
   *   def f1(): Future[T] = ...
   *   def f2(): ApiPayload \/ T = ...
   *   def f3(): T = ...
   *
   *   val result = for {
   *       a <- EitherT(Future { f1() })
   *       b <- EitherT(Future.successful(f2()))
   *       c <- EitherT(Future { f3().right[ApiPayload] })
   *   } yield c
   * }}}
   *
   * With this object in scope, we can do the following:
   *
   * {{{
   *   val result = for {
   *       a <- ? <~ f1()
   *       b <- ? <~ f2()
   *       c <- ? <~ f3()
   *   } yield c
   * }}}
   *
   * In both cases, the type of `result` is EitherT[Future, ApiPayload, T], so its `run` method must still be invoked
   * to obtain the final value.
   *
   * Adapted from: http://www.47deg.com/blog/fp-for-the-average-joe-part-2-scalaz-monad-transformers
   */
  object ? {

    /** Type alias for our stacked monads. */
    type Stacked[T] = EitherT[Future, ApiPayload, T]

    def <~[T](v: Future[Perhaps[T]]): Stacked[T] = EitherT(v)

    def <~[T](v: Perhaps[T]): Stacked[T] = EitherT(Future.successful(v))

    def <~[T](v: Future[T])(implicit context: ExecutionContext): Stacked[T] = EitherT.right(v)

    def <~[T](v: T)(implicit context: ExecutionContext): Stacked[T] = v.point[Stacked]
  }
}
