package de.danielscholz.fileIndexer.persistence

import de.danielscholz.fileIndexer.persistence.common.getFileContentSqlAttr
import de.danielscholz.fileIndexer.persistence.common.getFileLocationSqlAttr
import de.danielscholz.fileIndexer.persistence.common.getFileMetaSqlAttr
import de.danielscholz.fileIndexer.persistence.common.getIndexRunSqlAttr

@Suppress("MayBeConstant")
object Queries {

   val filePathRootId = 1L

   val fileContent1 = "SELECT * FROM fileContent WHERE hash = ? AND fileSize = ? "

   val indexRun1 = "SELECT * FROM IndexRun WHERE id = ? "
   val indexRun3 = "SELECT id FROM IndexRun WHERE id >= ? AND id <= ? "

   val indexRun2 = """SELECT distinct ${getIndexRunSqlAttr("r")}
                    | FROM   FileLocation l
                    |        join IndexRun r on (l.indexRun_id = r.id)
                    |        join FilePath p ON (l.filePath_id = p.id)
                    | WHERE  r.id = ? AND instr(p.path, ?) = 1
                    | LIMIT 1 """.trimMargin()

   val fileLocation1 = """SELECT ${getFileLocationSqlAttr("l")}, 
                        | ${getFileContentSqlAttr("c")}, 
                        | ${getFileMetaSqlAttr("m")}
                        | FROM FileLocation l left outer join FileContent c ON (c.id = l.fileContent_id) 
                        | left outer join FileMeta m ON (m.fileContent_id = c.id)
                        | WHERE l.id = ? """.trimMargin()

   val fileLocation2 = "SELECT count(*) FROM FileLocation l WHERE l.indexRun_id = ?"

   val fileLocation3 = "SELECT max(referenceInode) FROM FileLocation"

   val filePath1 = "SELECT * FROM FilePath WHERE pathPart = ? AND parentFilePath_id = ? "

   val filePath2 = "SELECT * FROM FilePath WHERE path = ? "

   val filePath3 = "SELECT * FROM FilePath WHERE id = ? "

   /////////////////////////

   val delFileLocation = "DELETE FROM FileLocation WHERE indexRun_id = ?"

   val delIndexRun = "DELETE FROM IndexRun WHERE id = ?"

   val delFileContent = """DELETE FROM FileContent
                         | WHERE id IN (
                         |       SELECT c.id
                         |       FROM FileContent c left outer join FileLocation l on (c.id = l.fileContent_id)
                         |       WHERE l.id IS NULL
                         |    ) """.trimMargin()

   val delFileMeta = """DELETE FROM FileMeta
                         | WHERE id IN (
                         |       SELECT m.id
                         |       FROM FileMeta m left outer join FileLocation l on (m.fileContent_id = l.fileContent_id)
                         |       WHERE l.id IS NULL
                         |    ) """.trimMargin()

   val delFilePath = """DELETE FROM FilePath
                      | WHERE id NOT IN (
                      |       SELECT DISTINCT l.filePath_id FROM FileLocation l
                      |    ) AND id NOT IN (
                      |       SELECT DISTINCT r.filePath_id FROM IndexRun r
                      |    ) AND id NOT IN (
                      |       SELECT DISTINCT p.parentFilePath_id FROM FilePath p WHERE p.parentFilePath_id IS NOT NULL
                      |    ) """.trimMargin()
}