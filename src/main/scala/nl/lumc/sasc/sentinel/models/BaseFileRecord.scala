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
package nl.lumc.sasc.sentinel.models

import com.novus.salat.annotations.Salat

/** Base class for file entries. */
@Salat abstract class BaseFileRecord {

  /** File system path of the file. */
  def path: String

  /** MD5 checksum of the file. */
  def md5: String
}

/** Minimal implementation of a file entry. */
case class FileRecord(path: String, md5: String) extends BaseFileRecord

/**
 * Sequencing input files, which can be single-end or paired-end.
 *
 * @param read1 The first read (if paired-end) or the only read (if single end).
 * @param read2 The second read. Only defined for paired-end inputs.
 */
case class SeqFiles(read1: FileRecord, read2: Option[FileRecord] = None)
