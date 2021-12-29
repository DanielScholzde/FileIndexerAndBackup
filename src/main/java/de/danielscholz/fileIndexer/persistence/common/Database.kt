package de.danielscholz.fileIndexer.persistence.common

import de.danielscholz.fileIndexer.Config
import de.danielscholz.fileIndexer.Global
import de.danielscholz.fileIndexer.common.inc
import de.danielscholz.fileIndexer.common.listOf
import de.danielscholz.fileIndexer.common.tryWith
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.sql.*
import java.time.Instant
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Class for accessing the database
 */
class Database(val dbFile: String) : Closeable {

   private val logger = LoggerFactory.getLogger(this.javaClass)

   private var connection: Connection
   private var changes: Int = 0
   private var lastTransactionCommitMillis = System.currentTimeMillis()

   var dbVersion = -1 // is set from PrepareDb

   init {
      // load the sqlite-JDBC driver using the current class loader
      Class.forName("org.sqlite.JDBC")
      val url = "jdbc:sqlite:$dbFile"
      logger.debug("Getting Connection to {}", url)
      // create a database connection
      connection = DriverManager.getConnection(url)
      connection.autoCommit = false
      //connection!!.transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
      //logger.debug("Transaction Isolation" + connection!!.transactionIsolation)
   }

   /**
    * für INSERT, UPDATE, CREATE, DROP
    */
   fun dbExec(sql: String, params: List<Any?> = listOf()): Long {
      try {
         return writeLock {
            traceQueryTime(sql, params) {
               connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).tryWith { prepStmt ->
                  setParams(prepStmt, params)
                  val count = prepStmt.executeUpdate()

                  if (changes++ > Config.INST.maxTransactionSize
                     || (System.currentTimeMillis() - lastTransactionCommitMillis) / 1000 > Config.INST.maxTransactionDurationSec
                  ) {
                     commit()
                  }

                  val rs = prepStmt.generatedKeys
                  if (sql.lowercase().trim().startsWith("insert") && rs.next()) {
                     return@tryWith logSql(rs.getLong(1), sql, params) // inserted ID
                  }
                  return@tryWith logSql(count.toLong(), sql, params)
               }
            }
         }
      } catch (e: SQLException) {
         logger.error("Error on execution of: $sql with parameter: $params")
         throw e
      }
   }

   /**
    * for pragma queries with no resultSet
    */
   fun dbExecNoResult(sql: String, params: List<Any?> = listOf()) {
      try {
         return writeLock {
            traceQueryTime(sql, params) {
               connection.prepareStatement(sql).tryWith { prepStmt ->
                  setParams(prepStmt, params)
                  prepStmt.execute()
               }
            }
         }
      } catch (e: SQLException) {
         logger.error("Error on execution of: $sql with parameter: $params")
         throw e
      }
   }

   // ---------------------------------------------

   fun dbQueryUniqueInt(sql: String, params: List<Any> = listOf()): Int {
      return dbQueryUnique(sql, params) { it.getInt(1) }
   }

   fun dbQueryUniqueLong(sql: String, params: List<Any> = listOf()): Long {
      return dbQueryUnique(sql, params) { it.getLong(1) }
   }

   fun dbQueryUniqueLongNullable(sql: String, params: List<Any> = listOf()): Long? {
      return dbQueryUniqueNullable(sql, params) { it.getLong(1) }
   }

   fun dbQueryUniqueStr(sql: String, params: List<Any> = listOf()): String {
      return dbQueryUnique(sql, params) { it.getString(1) }
   }

   // -----------------------------------------------

   fun <T> dbQueryUnique(sql: String, params: List<Any> = listOf(), resultExtractor: (ResultSet) -> T): T {
      return dbQueryUniqueNullable(sql, params, false, resultExtractor)!!
   }

   fun <T> dbQueryUniqueNullable(
      sql: String,
      params: List<Any> = listOf(),
      noResultAllowed: Boolean = true,
      resultExtractor: (ResultSet) -> T
   ): T? {
      try {
         return readLock {
            traceQueryTime(sql, params) {
               connection.prepareStatement(sql).tryWith { prepStmt ->
                  setParams(prepStmt, params)

                  val result = prepStmt.executeQuery()

                  if (result.next()) {
                     return@tryWith logSql(resultExtractor(result), sql, params)
                  }

                  if (!noResultAllowed) {
                     throw Exception("Database returned no result, but exact one result was expected!\nSQL: $sql")
                  }
                  return@tryWith logSql(null, sql, params)
               }
            }
         }
      } catch (e: SQLException) {
         logger.error("Error on execution of: $sql with parameter: $params")
         throw e
      }
   }

   /** readonly access */
   fun <T> dbQuery(sql: String, params: List<Any> = listOf(), resultExtractor: (ResultSet) -> T): List<T> {
      try {
         return readLock {
            traceQueryTime(sql, params) {
               connection.prepareStatement(sql).tryWith { prepStmt ->
                  setParams(prepStmt, params)

                  val resultSet = prepStmt.executeQuery()

                  val result = mutableListOf<T>()
                  while (resultSet.next()) {
                     result.add(resultExtractor(resultSet))
                  }
                  return@tryWith logSql(result, sql, params)
               }
            }
         }
      } catch (e: SQLException) {
         logger.error("Error on execution of: $sql with parameter: $params")
         throw e
      }
   }

   fun <K, V> dbQueryPair(
      sql: String,
      params: List<Any> = listOf(),
      resultExtractor: (ResultSet) -> Pair<K, V>
   ): MutableMap<K, V> {
      try {
         return readLock {
            traceQueryTime(sql, params) {
               connection.prepareStatement(sql).tryWith { prepStmt ->
                  setParams(prepStmt, params)

                  val resultSet = prepStmt.executeQuery()

                  val result = mutableMapOf<K, V>()
                  while (resultSet.next()) {
                     val r = resultExtractor(resultSet)
                     result[r.first] = r.second
                  }
                  return@tryWith logSql(result, sql, params)
               }
            }
         }
      } catch (e: SQLException) {
         logger.error("Error on execution of: $sql with parameter: $params")
         throw e
      }
   }

   private fun setParams(prepStmt: PreparedStatement, params: List<Any?>) {
      var i = 1
      for (param in params) {
         when (param) {
            is String  -> prepStmt.setString(i, param)
            is Long    -> prepStmt.setLong(i, param)
            is Int     -> prepStmt.setInt(i, param)
            is Instant -> prepStmt.setLong(i, param.toEpochMilli())
            is Boolean -> prepStmt.setLong(i, if (param) 1 else 0)
            null       -> prepStmt.setString(i, null) // todo is correct to set as a string?
            else       -> throw Exception("Unerwarteter Parameter-Typ: " + param.javaClass)
         }
         i++
      }
   }

   fun commit() {
      logger.debug("commit transaction")
      connection.commit()
      changes = 0
      lastTransactionCommitMillis = System.currentTimeMillis()
   }

   fun rollback() {
      logger.debug("rollback transaction")
      connection.rollback()
      changes = 0
   }

   override fun close() {
      logger.debug("closing connection")
      if (changes > 0) logger.warn("There are $changes not committed changes!")
      connection.close()
      //connection = null
   }

   private fun <T> logSql(result: T, sql: String, params: List<Any?>): T {
      if (result is List<*>) {
         if (result.size > 5) {
            logger.trace(
               "{}\nParameter: {}\nResult: {}...{} Entries",
               sql,
               params,
               result.subList(0, 5),
               result.size
            )
            return result
         }
      }
      logger.trace("{}\nParameter: {}\nResult: {}", sql, params, result)
      return result
   }

   private inline fun <T> traceQueryTime(sql: String, params: List<Any?>, code: () -> T): T {
      val start = System.nanoTime()
      try {
         return code()
      } finally {
         val duration = System.nanoTime() - start
         Global.stat.queryTime += duration
         Global.stat.queryCount++
         if (duration > Global.stat.maxQueryTime.get()) {
            Global.stat.maxQueryTime.set(duration)
            Global.stat.maxQuerySql = "$sql\n   " + params.joinToString(", ") {
               if (it is String) "\"$it\"" else it.toString()
            }
         }
      }
   }

   private val readWriteLock = ReentrantReadWriteLock(true)

   private inline fun <T> readLock(block: () -> T): T {
      return readWriteLock.read(block)
   }

   private inline fun <T> writeLock(block: () -> T): T {
      return readWriteLock.write(block)
   }

}

