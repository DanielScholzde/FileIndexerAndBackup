package de.danielscholz.fileIndexer.persistence

import de.danielscholz.fileIndexer.persistence.common.EntityBase

private class ValidateException(entity: EntityBase) : Exception("Validation failed for $entity")

fun validate(filePath: FilePath) {
   if (filePath.id == Queries.filePathRootId) return
   if (filePath.parentFilePathId == null) throw ValidateException(filePath)
   if (filePath.parentFilePathId < Queries.filePathRootId) throw ValidateException(filePath)
   if (filePath.depth <= 0) throw ValidateException(filePath)
   if (filePath.path.isEmpty()) throw ValidateException(filePath)
   if (filePath.path.contains('\\')) throw ValidateException(filePath)
   if (filePath.path.contains("//")) throw ValidateException(filePath)
   if (!filePath.path.startsWith("/")) throw ValidateException(filePath)
   if (!filePath.path.endsWith("/")) throw ValidateException(filePath)
   if (filePath.pathPart.isEmpty()) throw ValidateException(filePath)
   if (filePath.pathPart.contains('\\')) throw ValidateException(filePath)
   if (filePath.pathPart.contains("/")) throw ValidateException(filePath)
}

fun validate(fileContent: FileContent) {
   if (fileContent.fileSize <= 0) throw ValidateException(fileContent)
   if (fileContent.hash.isEmpty()) throw ValidateException(fileContent)
   if (fileContent.hashBegin.isEmpty()) throw ValidateException(fileContent)
   if (fileContent.hashEnd.isEmpty()) throw ValidateException(fileContent)
}

@Suppress("UNUSED_PARAMETER")
fun validate(fileMeta: FileMeta) {
}

fun validate(fileLocation: FileLocation) {
   if (fileLocation.filePathId < Queries.filePathRootId) throw ValidateException(fileLocation)
   if (fileLocation.filename.isEmpty()) throw ValidateException(fileLocation)
}

fun validate(indexRun: IndexRun) {
   if (indexRun.filePathId < Queries.filePathRootId) throw ValidateException(indexRun)
   if (indexRun.path.isEmpty()) throw ValidateException(indexRun)
   if (indexRun.path.contains("\\")) throw ValidateException(indexRun)
   if (indexRun.path.contains("//")) throw ValidateException(indexRun)
   if (!indexRun.path.endsWith("/")) throw ValidateException(indexRun)
   if (!indexRun.path.startsWith("/")) throw ValidateException(indexRun)
   //if (indexRun.pathPrefix.isEmpty()) throw ValidateException(indexRun)
   if (indexRun.pathPrefix.contains("/")) throw ValidateException(indexRun)
   if (indexRun.pathPrefix.contains("\\")) throw ValidateException(indexRun)
   if (indexRun.excludedPaths.contains("\\")) throw ValidateException(indexRun)
   if (indexRun.excludedFiles.contains("\\")) throw ValidateException(indexRun)
}