package de.danielscholz.fileIndexer.persistence.common

import de.danielscholz.fileIndexer.common.isNotEmpty
import de.danielscholz.fileIndexer.persistence.*
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.full.findAnnotation

interface EntityBase {
   var id: Long // Jedes Entity muss eine ID haben
   var pl: PersistenceLayer
}

@Target(AnnotationTarget.PROPERTY)
annotation class NoDbProperty

@Target(AnnotationTarget.PROPERTY)
annotation class CustomCharset(val value: String)


private val sqlAttributesCache = mutableMapOf<String, String>()

fun <T : EntityBase> getSqlAttributes(clazz: KClass<T>, prefix: String = ""): String {
   if (sqlAttributesCache.containsKey(clazz.simpleName + prefix)) {
      return sqlAttributesCache[clazz.simpleName + prefix]!!
   }

   val result = ArrayList<String>()
   processFilteredProperties(clazz, false) { name, _ ->
      result.add(if (prefix.isNotEmpty()) "$prefix.$name AS ${prefix}_$name" else name)
   }

   val res = result.joinToString()
   sqlAttributesCache[clazz.simpleName + prefix] = res
   return res
}

fun <T : EntityBase> processFilteredProperties(
   clazz: KClass<T>,
   excludeId: Boolean = true,
   process: (String, KProperty<*>) -> Unit
) {
//    val map = clazz.declaredMemberProperties.associateBy { it.name }
//    for (attr in clazz.declaredMemberProperties) {
//        if (attr.name.endsWith("Id") && map.containsKey(attr.name.removeSuffix("Id"))) {
//            val value: Base? = map[attr.name.removeSuffix("Id")]!!.get(entity) as Base?
//            if (value != null && value.id == 0L) {
//                insert(value, value.)
//            }
//        }
//    }
   for (entityProperty in clazz.members) {
      if (entityProperty !is KProperty) continue
      if (entityProperty.name == "id" && excludeId) continue
      if (entityProperty.findAnnotation<NoDbProperty>() != null) continue // todo optimize reflection
      process(getSqlPropName(entityProperty), entityProperty)
   }
}

fun getSqlPropName(attr: KProperty<*>): String {
   var a = attr.name
   if (a.endsWith("Id")) {
      a = a.substring(0, a.length - 2) + "_id"
   }
   return a
}


fun getFileLocationSqlAttr(prefix: String = "") = getSqlAttributes(FileLocation::class, prefix)

fun getFileContentSqlAttr(prefix: String = "") = getSqlAttributes(FileContent::class, prefix)

fun getFileMetaSqlAttr(prefix: String = "") = getSqlAttributes(FileMeta::class, prefix)

fun getFilePathSqlAttr(prefix: String = "") = getSqlAttributes(FilePath::class, prefix)

fun getIndexRunSqlAttr(prefix: String = "") = getSqlAttributes(IndexRun::class, prefix)