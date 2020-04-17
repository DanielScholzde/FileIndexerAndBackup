package de.danielscholz.fileIndexer.persistence

import de.danielscholz.fileIndexer.common.transaction
import de.danielscholz.fileIndexer.persistence.common.Database
import org.slf4j.LoggerFactory

object PrepareDb {

   private val logger = LoggerFactory.getLogger(this.javaClass)

   fun prepareDB(db: Database) {
      fun setVersion(version: Int): Int {
         db.dbExec("UPDATE schemeVersion SET version = $version ")
         return version
      }

      var version = 0
      transaction(logger, db) {

         db.dbExec("""
			CREATE TABLE IF NOT EXISTS SchemeVersion (
                version INTEGER NOT NULL
			)""")
         if (db.dbQueryUniqueLongNullable("SELECT version FROM SchemeVersion") == null) {
            db.dbExec("INSERT INTO schemeVersion (version) VALUES (1)") // create entry if table is empty
         }

         db.dbExec("""
			CREATE TABLE IF NOT EXISTS FileMeta (
                id INTEGER PRIMARY KEY,
                fileContent_id INTEGER NOT NULL UNIQUE REFERENCES FileContent(id),
                imgWidth INTEGER,
                imgHeight INTEGER,
                imgExifOriginalDate INTEGER 
			)""")

         db.dbExec("""
			CREATE TABLE IF NOT EXISTS FileContent (
                id INTEGER PRIMARY KEY,
                fileSize INTEGER NOT NULL,
                hash TEXT NOT NULL,
                hashBegin TEXT NOT NULL,
                hashEnd TEXT NOT NULL
			)""")
         db.dbExec("""CREATE UNIQUE INDEX IF NOT EXISTS indexFC_1 ON FileContent (fileSize, hash)""")

         db.dbExec("""
			CREATE TABLE IF NOT EXISTS IndexRun (
                id INTEGER PRIMARY KEY,
                filePath_id INTEGER NOT NULL REFERENCES FilePath(id),
                path TEXT NOT NULL,
                pathPrefix TEXT NOT NULL,
                mediumDescription TEXT,
                mediumSerial TEXT,
                runDate INTEGER NOT NULL,
                readonlyMedium INTEGER NOT NULL,
                isBackup INTEGER NOT NULL,
                onlyReadFirstMbOfContentForHash INTEGER,
                mediumCaseSensitive INTEGER NOT NULL,
                excludedPaths TEXT NOT NULL,
                excludedFiles TEXT NOT NULL,
                totalSpace INTEGER NOT NULL,
                usableSpace INTEGER NOT NULL,
                failureOccurred INTEGER NOT NULL
			)""")
         db.dbExec("CREATE UNIQUE INDEX IF NOT EXISTS indexIR_1 ON IndexRun (path, runDate, coalesce(mediumSerial, ''))") // pathPrefix ?

         db.dbExec("""
			CREATE TABLE IF NOT EXISTS FilePath (
                id INTEGER PRIMARY KEY,
                parentFilePath_id INTEGER REFERENCES FilePath(id),
                path TEXT NOT NULL,
                pathPart TEXT NOT NULL,
                depth INTEGER NOT NULL
			)""")
         db.dbExec("CREATE UNIQUE INDEX IF NOT EXISTS indexFP_1 ON FilePath (parentFilePath_id, pathPart)")
         db.dbExec("CREATE UNIQUE INDEX IF NOT EXISTS indexFP_2 ON FilePath (path)")
         if (db.dbQueryUniqueLong("SELECT count(*) FROM FilePath") == 0L) {
            db.dbExec("INSERT INTO FilePath (id, parentFilePath_id, path, pathPart, depth) VALUES (${Queries.filePathRootId}, null, '/', '/', 0)")
         }

         db.dbExec("""
			CREATE TABLE IF NOT EXISTS FileLocation (
                id INTEGER PRIMARY KEY,
                indexRun_id INTEGER NOT NULL REFERENCES IndexRun(id),
                fileContent_id INTEGER REFERENCES FileContent(id),
                filePath_id INTEGER NOT NULL REFERENCES FilePath(id),
                filename TEXT NOT NULL, /* test.txt */
                extension TEXT, /* txt */
                referenceInode INTEGER,
                created INTEGER NOT NULL,
                modified INTEGER NOT NULL,
                hidden INTEGER NOT NULL,
                inArchive INTEGER NOT NULL
			)""")
         db.dbExec("CREATE UNIQUE INDEX IF NOT EXISTS indexFL_3 ON FileLocation (indexRun_id, filePath_id, filename)")

         version = setVersion(1)
      }

      db.dbVersion = version
   }

}