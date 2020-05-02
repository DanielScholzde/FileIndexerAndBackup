package de.danielscholz.fileIndexer.persistence

import de.danielscholz.fileIndexer.persistence.common.EntityBase
import de.danielscholz.fileIndexer.persistence.common.NoDbProperty
import java.time.Instant

/**
 * FileContent maps the content of any file, regardless where the file is located (drive, medium, ..)
 */
data class FileContent(
      override var id: Long,
      @NoDbProperty
      override var pl: PersistenceLayer,
      val fileSize: Long, // file size in Byte
      val hash: String, // sha1 hash of file content
      val hashBegin: String, // list of hashes of content chunks from the file begin
      val hashEnd: String, // list of hashes of content chunks from the file end

      @NoDbProperty
      var fileMeta: FileMeta? = null // FileMeta object. Will be set later.
) : EntityBase

/**
 * Metadata of image files
 */
data class FileMeta(
      override var id: Long,
      @NoDbProperty
      override var pl: PersistenceLayer,
      val fileContentId: Long,
      val imgWidth: Int?,
      val imgHeight: Int?,
      val imgExifOriginalDate: Instant?
) : EntityBase

/**
 * Saves path information
 */
data class FilePath(
      override var id: Long,
      @NoDbProperty
      override var pl: PersistenceLayer,
      val parentFilePathId: Long?, // foreign key to parent filePath.id OR null
      val path: String, // complete path
      val pathPart: String, // last part of the path
      val depth: Int // depth of directory
) : EntityBase

/**
 * FileLocation maps one file entry (metadata of file, but no file content/hash!)
 */
data class FileLocation(
      override var id: Long,
      @NoDbProperty
      override var pl: PersistenceLayer,
      var fileContentId: Long?, // only set if the file is not empty
      val filePathId: Long,
      val indexRunId: Long,
      val filename: String,
      val extension: String?,
      val created: Instant,
      val modified: Instant,
      val hidden: Boolean,
      val inArchive: Boolean,
      var referenceInode: Long?, // all files (mostly only within the same indexRun layer) which are hardlinks to each other get the same referenceInode number

      @NoDbProperty
      var indexRun: IndexRun? = null, // IndexRun object. Will be set later.
      @NoDbProperty
      var fileContent: FileContent? = null // FileContent object. Will be set later.
) : EntityBase

/**
 * IndexRun is the starting point of a single index run
 */
data class IndexRun(
      override var id: Long,
      @NoDbProperty
      override var pl: PersistenceLayer,
      val filePathId: Long, // ID of complete indexed root path
      val path: String, // complete indexed root path  todo path is redundant
      val pathPrefix: String, // drive letter (windows only), may be empty
      val includedPaths: String,
      val excludedPaths: String,
      val excludedFiles: String,
      val mediumDescription: String?, // description of the medium, e.g. "backup medium 2, 320 GB"
      val mediumSerial: String?, // volume serial number of medium / drive, can be self defined
      val mediumCaseSensitive: Boolean,
      val runDate: Instant, // date of index run
      val readonlyMedium: Boolean,
      val isBackup: Boolean, // true, if created by BackupFiles
      val onlyReadFirstMbOfContentForHash: Int?, // marker, if only the first MB of file content was read for calculation the hash
      val totalSpace: Long,
      var usableSpace: Long, // will be set later
      var failureOccurred: Boolean // will be set later
) : EntityBase
