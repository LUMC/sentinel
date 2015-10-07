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

/**
 * Base adapter that uses Scala's Futures.
 */
trait FutureAdapter {

  /** Default timeout. */
  implicit protected def timeout: Duration = 10.seconds

  /** Implicit value for making Future a Scalaz monad. */
  implicit def futureMonad(implicit context: ExecutionContext) = new Monad[Future] {
    override def point[T](t: => T): Future[T] = Future { t }
    override def bind[T, U](ft: Future[T])(f: T => Future[U]): Future[U] = ft.flatMap(f)
  }

  /**
   * For easier access to Scalaz's `Monoid[_]` conversion for subclasses.
   * See: https://groups.google.com/forum/#!topic/scalaz/9SJbGlpS7Kw
   */
  protected final implicit def lMonoid[A] = listMonoid[A]

  /**
   * Helper object for stacking the disjunction (`\/`) and `Future` monads.
   *
   * Without this object, we have to wrap methods returning Future[A], List[String] \/ A, or A manually in EitherT:
   *
   * {{{
   *   def f1(): Future[T] = ...
   *   def f2(): List[String] \/ T = ...
   *   def f3(): T = ...
   *
   *   val result = for {
   *       a <- EitherT(Future { f1() })
   *       b <- EitherT(Future.successful(f2()))
   *       c <- EitherT(Future { f3().right[List[String]] })
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
   * In both cases, the type of `result` is EitherT[Future, List[String], T], so its `run` method must still be invoked
   * to obtain the final value.
   *
   * Adapted from: http://www.47deg.com/blog/fp-for-the-average-joe-part-2-scalaz-monad-transformers
   */
  object ? {

    /** Type alias for our stacked monads. */
    type Result[T] = EitherT[Future, List[String], T]

    def <~[T](v: Future[List[String] \/ T]): Result[T] = EitherT(v)

    def <~[T](v: List[String] \/ T): Result[T] = EitherT(Future.successful(v))

    def <~[T](v: Future[T])(implicit context: ExecutionContext): Result[T] = EitherT(v.map(_.right[List[String]]))

    def <~[T](v: T)(implicit context: ExecutionContext): Result[T] = v.point[Result]
  }
}
