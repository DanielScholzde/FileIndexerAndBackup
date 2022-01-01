package de.danielscholz.fileIndexer.persistence

import de.danielscholz.fileIndexer.common.isNotEmpty
import de.danielscholz.fileIndexer.common.syncronizedMutableMapOf
import de.danielscholz.fileIndexer.common.toInstant
import de.danielscholz.fileIndexer.persistence.common.CustomCharset
import de.danielscholz.fileIndexer.persistence.common.Database
import de.danielscholz.fileIndexer.persistence.common.EntityBase
import de.danielscholz.fileIndexer.persistence.common.processFilteredProperties
import java.lang.ref.WeakReference
import java.nio.charset.Charset
import java.sql.ResultSet
import java.time.Instant
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation

open class PersistenceLayerBase(val db: Database) {

   private class Types(
      var boolean: Boolean,
      var booleanN: Boolean?,
      var instant: Instant,
      var instantN: Instant?,
      var long: Long,
      var longN: Long?,
      var string: String,
      var stringN: String?,
      var int: Int,
      var intN: Int?
   )

   private val nativeSqlTypes = listOf(
      Types::string.returnType,
      Types::stringN.returnType,
      Types::long.returnType,
      Types::longN.returnType,
      Types::int.returnType,
      Types::intN.returnType
   )

   private val entityCache = syncronizedMutableMapOf<Pair<KClass<out EntityBase>, Long>, WeakReference<out EntityBase>>()

   protected fun getEntityFromCacheOrNull(clazz: KClass<out EntityBase>, id: Long): EntityBase? {
      val entityRef = entityCache[Pair(clazz, id)]
      if (entityRef != null) {
         val entity = entityRef.get()
         if (entity != null) {
            return entity
         }
      }
      return null
   }

   /**
    * Reads the data for the entity from resultSet and creates an instance of the entity class.
    */
   protected fun <T : EntityBase> extractToEntity(clazz: KClass<T>, result: ResultSet, prefix: String = ""): T? {
      val p = if (prefix.isNotEmpty()) prefix + "_" else ""
      val id = result.getLong("${p}id") // id maybe 0 instead of null. Because of that, we have to check via result.wasNull()
      if (!result.wasNull()) {
         val entityRef = entityCache[Pair(clazz, id)]
         if (entityRef != null) {
            val entity = entityRef.get()
            if (entity != null) {
               @Suppress("UNCHECKED_CAST")
               return entity as T
            } else {
               entityCache.remove(Pair(clazz, id))
            }
         }

         val params = mutableMapOf<String, Any?>()
         params[EntityBase::pl.name] = this
         processFilteredProperties(clazz, false) { sqlPropName, entityProp ->
            if (entityProp.returnType == Types::string.returnType || entityProp.returnType == Types::stringN.returnType) {
               val customCharset = entityProp.findAnnotation<CustomCharset>()
               if (customCharset != null) {
                  val bytes = result.getBytes("$p$sqlPropName")
                  params[entityProp.name] = if (bytes != null) {
                     val str = String(bytes, Charset.forName(customCharset.value))
                     if (str.contains('�')) throw Exception("String contains unknown chars!")
                     str
                  } else null
               } else {
                  val str = result.getString("$p$sqlPropName")
                  if (str != null && str.contains('�')) throw Exception("String contains unknown chars!")
                  params[entityProp.name] = str
               }
            } else if (entityProp.returnType == Types::long.returnType || entityProp.returnType == Types::longN.returnType) {
               val value = result.getLong("$p$sqlPropName")
               params[entityProp.name] = if (result.wasNull()) null else value
            } else if (entityProp.returnType == Types::int.returnType || entityProp.returnType == Types::intN.returnType) {
               val value = result.getInt("$p$sqlPropName")
               params[entityProp.name] = if (result.wasNull()) null else value
            } else if (entityProp.returnType == Types::instant.returnType || entityProp.returnType == Types::instantN.returnType) {
               val value = result.getLong("$p$sqlPropName")
               params[entityProp.name] = if (result.wasNull()) null else value.toInstant()
            } else if (entityProp.returnType == Types::boolean.returnType || entityProp.returnType == Types::booleanN.returnType) {
               val value = result.getInt("$p$sqlPropName")
               params[entityProp.name] = if (result.wasNull()) null else value == 1
            } else {
               throw Exception("unknown datatype: " + entityProp.returnType)
            }
         }

         if (clazz.constructors.count() == 1) {
            for (constructor in clazz.constructors) {
               val entity = constructor.call(*constructor.parameters.map { params[it.name] }.toTypedArray())
               entityCache[Pair(entity::class, entity.id)] = WeakReference(entity)
               return entity
            }
         }
         throw IllegalStateException()
      }
      return null
   }

   /**
    * Inserts an entity into the database and sets its ID to the newly created ID.
    */
   protected fun <T : EntityBase> insert(entity: T, clazz: KClass<T>, validator: (T) -> Unit = { }): T {
      validator(entity)
      val (sqlPropNames, sqlPropValues) = getSqlPropertyNamesAndValues(entity, clazz)
      val sql = "INSERT INTO ${clazz.simpleName} (${sqlPropNames.joinToString()}) " +
            "VALUES (${",?".repeat(sqlPropNames.size).substring(1)})"
      entity.id = db.dbExec(sql, sqlPropValues)
      entityCache[Pair(entity::class, entity.id)] = WeakReference(entity)
      return entity
   }

   /**
    * Validates and updates the data of an entity in the database.
    */
   protected fun <T : EntityBase> update(entity: T, clazz: KClass<T>, validator: (T) -> Unit = { }): T {
      validator(entity)
      val (sqlPropNames, sqlPropValues) = getSqlPropertyNamesAndValues(entity, clazz)
      sqlPropValues.add(entity.id)
      db.dbExec(
         "UPDATE ${clazz.simpleName} " +
               "SET ${sqlPropNames.joinToString(" = ?, ", postfix = " = ?")} " +
               "WHERE id = ?",
         sqlPropValues
      )
      return entity
   }

   private fun <T : EntityBase> getSqlPropertyNamesAndValues(entity: T, clazz: KClass<T>): Pair<List<String>, MutableList<Any?>> {
      val sqlPropertyNames = mutableListOf<String>()
      val sqlPropValues = mutableListOf<Any?>()
      processFilteredProperties(clazz) { sqlPropName, entityProp ->
         sqlPropertyNames.add(sqlPropName)

         val value = entityProp.getter.call(entity)
         when (entityProp.returnType) {
            in nativeSqlTypes          -> {
               sqlPropValues.add(value)
            }
            Types::instant.returnType  -> {
               sqlPropValues.add((value as Instant).toEpochMilli())
            }
            Types::instantN.returnType -> {
               sqlPropValues.add((value as Instant?)?.toEpochMilli())
            }
            Types::boolean.returnType  -> {
               sqlPropValues.add(if (value as Boolean) 1 else 0)
            }
            Types::booleanN.returnType -> {
               val b = value as Boolean?
               sqlPropValues.add(if (b == true) 1 else if (b == false) 0 else null)
            }
            else                       -> {
               throw Exception("unknown datatype: " + entityProp.returnType)
            }
         }
      }
      return Pair(sqlPropertyNames, sqlPropValues)
   }

   fun cleanupEntityCache() {
      val iterator = entityCache.values.iterator()
      while (iterator.hasNext()) {
         if (iterator.next().get() == null) {
            iterator.remove()
         }
      }
   }
}