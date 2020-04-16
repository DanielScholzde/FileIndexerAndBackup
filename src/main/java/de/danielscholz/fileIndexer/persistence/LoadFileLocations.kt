package de.danielscholz.fileIndexer.persistence

import de.danielscholz.fileIndexer.Config
import de.danielscholz.fileIndexer.common.convertToLocalZone
import de.danielscholz.fileIndexer.common.listOf
import de.danielscholz.fileIndexer.common.testIfCancel
import de.danielscholz.fileIndexer.common.toStr
import de.danielscholz.fileIndexer.persistence.common.getFileContentSqlAttr
import de.danielscholz.fileIndexer.persistence.common.getFileLocationSqlAttr
import de.danielscholz.fileIndexer.persistence.common.getFileMetaSqlAttr
import de.danielscholz.fileIndexer.persistence.common.getSqlPropName
import org.slf4j.LoggerFactory
import kotlin.reflect.KProperty1

/**
 * Liest die FileLocations und stellt sie einem processor zur Verfügung. Die Bedingungen für die
 * FileLocations werden über die furtherConditions gesteuert.
 * Es werden auch die Objekte indexRun und fileContent gefüllt!
 */
class LoadFileLocations(private val toProcess: IndexRunFilePathResult, private val pl: PersistenceLayer) {

   private val logger = LoggerFactory.getLogger(this.javaClass)

   enum class Operator(val sqlCode: String) {
      EQ("="),
      LT("<"),
      LTE("<="),
      GT(">"),
      GTE(">="),
      NEQ("<>"),
      IN("IN")
   }

   data class Condition<T>(val attribute: KProperty1<T, *>, val operator: Operator, val value: Any)

   fun load(inclFilesInArchives: Boolean = true,
            furtherConditions: List<Condition<FileLocation>> = listOf()): Collection<FileLocation> {
      return load(toProcess.indexRun, toProcess.filePath, inclFilesInArchives, furtherConditions)
   }

   private fun load(indexRun: IndexRun,
                    filePath: FilePath,
                    inclFilesInArchives: Boolean,
                    furtherConditions: List<Condition<FileLocation>>): Collection<FileLocation> {

      fun getFurtherConditions(): String {
         val inArchive = if (!inclFilesInArchives) " l.inArchive = ? AND\n" else ""
         if (furtherConditions.isEmpty()) return inArchive
         return furtherConditions.joinToString(" AND ", postfix = " AND ") { condition ->
            if (condition.operator == Operator.IN && condition.value is Collection<*>) {
               val result = StringBuilder()
               for (it in condition.value) {
                  result.append(",?")
               }
               "l.${getSqlPropName(condition.attribute)} ${condition.operator.sqlCode} (${result.substring(1)})"
            } else {
               "l.${getSqlPropName(condition.attribute)} ${condition.operator.sqlCode} ?"
            }
         } + inArchive
      }

      logger.debug("Load indexed Files from ${pl.getFullPath(indexRun, filePath.id)} " +
                   "and Date ${indexRun.runDate.convertToLocalZone().toStr()} " +
                   "and excluded Paths ${indexRun.excludedPaths.split('|').filter { !Config.defaultExcludedPaths.contains(it) }.joinToString { "\"$it\"" }} " +
                   "and Files ${indexRun.excludedFiles.split('|').filter { !Config.defaultExcludedFiles.contains(it) }.joinToString { "\"$it\"" }}")

      val sql = """
			SELECT
			  ${getFileContentSqlAttr("c")},
			  ${getFileMetaSqlAttr("m")},
			  ${getFileLocationSqlAttr("l")}
			FROM FileLocation l 
			LEFT JOIN FileContent c ON (l.fileContent_id = c.id)
			LEFT JOIN FileMeta m ON (c.id = m.fileContent_id)
         JOIN FilePath p ON (p.id = l.filePath_id)
			WHERE
			  ${getFurtherConditions()}
			  l.indexRun_id = ? AND
			  instr(p.path, ?) = 1 """

      val params = mutableListOf<Any>()

      if (!inclFilesInArchives) params.add(0)

      for (cond in furtherConditions.map { it.value }) {
         if (cond is Collection<*>) {
            cond.forEach { params.add(it!!) }
         } else
            params.add(cond)
      }
      params.add(indexRun.id)
      params.add(filePath.path)

      val result = pl.db.dbQuery(sql, params) {
         val fileLocation = pl.extractFileLocation(it, "l")!!
         val fileContent = pl.extractFileContent(it, "c")
         val fileMeta = pl.extractFileMeta(it, "m")
         fileContent?.fileMeta = fileMeta
         fileLocation.fileContent = fileContent
         fileLocation.indexRun = indexRun

         testIfCancel(pl.db)

         fileLocation
      }

      logger.debug("${result.size} indexed files loaded")
      return result
   }

}
