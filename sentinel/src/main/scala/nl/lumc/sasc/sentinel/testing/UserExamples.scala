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
package nl.lumc.sasc.sentinel.testing

import nl.lumc.sasc.sentinel.models.User

/** Various types of User objects for testing. */
object UserExamples {

  import User.hashPassword

  /** Expected normal user: verified but not an admin. */
  val avg = User("avg", "avg@test.id", hashPassword("0PwdAvg"), "key1", verified = true, isAdmin = false)

  /** Also an expected normal user: verified but not an admin. */
  val avg2 = User("avg2", "avg2@test.id", hashPassword("0PwdAvg2"), "key2", verified = true, isAdmin = false)

  /** Admin user. */
  val admin = User("admin", "admin@test.id", hashPassword("0PwdAdmin"), "key3", verified = true, isAdmin = true)

  /** Unverified user. */
  val unverified = User("unv", "unv@test.id", hashPassword("0PwdUnverified"), "key4", verified = false, isAdmin = false)

  /** Set of all testing users. */
  lazy val all = Set(avg, avg2, admin, unverified)
}
