package de.danielscholz.fileIndexer.persistence

import de.danielscholz.fileIndexer.Config
import de.danielscholz.fileIndexer.common.convertToLocalZone
import de.danielscholz.fileIndexer.common.testIfCancel
import de.danielscholz.fileIndexer.common.toStr
import de.danielscholz.fileIndexer.persistence.common.getFileContentSqlAttr
import org.slf4j.LoggerFactory

/**
 * Liest die FileLocations und stellt sie einem processor zur Verfügung. Die Bedingungen für die
 * FileLocations werden über die furtherConditions gesteuert.
 * Es werden auch die Objekte indexRun und fileContent gefüllt!
 */
class LoadFileContents(private val pl: PersistenceLayer) {

   private val logger = LoggerFactory.getLogger(this.javaClass)

   fun load(toProcess: IndexRunFilePathResult, inclFilesInArchives: Boolean = true): Collection<FileContent> {
      return load(toProcess.indexRun, toProcess.filePath, inclFilesInArchives)
   }

   fun load(indexRun: IndexRun, inclFilesInArchives: Boolean = true): Collection<FileContent> {
      return load(indexRun, null, inclFilesInArchives)
   }

   private fun load(indexRun: IndexRun,
                    filePath: FilePath?,
                    inclFilesInArchives: Boolean): Collection<FileContent> {

      logger.debug("Load indexed file content from ${pl.getFullPath(indexRun, filePath?.id)} " +
                   "and date ${indexRun.runDate.convertToLocalZone().toStr()} " +
                   "and excluded paths ${indexRun.excludedPaths.split('|').filter { !Config.INST.defaultExcludedPaths.contains(it) }.joinToString { "\"$it\"" }} " +
                   "and files ${indexRun.excludedFiles.split('|').filter { !Config.INST.defaultExcludedFiles.contains(it) }.joinToString { "\"$it\"" }}")

      val sql = """
			SELECT DISTINCT
			  ${getFileContentSqlAttr("c")}
			FROM FileLocation l 
			JOIN FileContent c ON (l.fileContent_id = c.id)
         JOIN FilePath p ON (p.id = l.filePath_id)
			WHERE
			  l.indexRun_id = ?
           ${if (!inclFilesInArchives) "AND l.inArchive = 0" else ""}
			  ${if (filePath != null) "AND instr(p.path, ?) = 1" else ""} """

      val params = mutableListOf<Any>()

      params.add(indexRun.id)
      if (filePath != null) params.add(filePath.path)

      val result = pl.db.dbQuery(sql, params) {
         val fileContent = pl.extractFileContent(it, "c")!!
         testIfCancel(pl.db)
         fileContent
      }

      logger.debug("${result.size} indexed file contents loaded")
      return result
   }

}
