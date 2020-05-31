package de.danielscholz.fileIndexer.common

import java.io.IOException

class FileSizeChangedException : IOException("File size changed!")

class CancelPipelineException : Exception()

class CancelException : Exception("Cancel")