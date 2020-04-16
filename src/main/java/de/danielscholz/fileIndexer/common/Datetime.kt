package de.danielscholz.fileIndexer.common

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoField

fun Instant.convertToLocalZone(): ZonedDateTime {
   return this.atZone(zoneIdLocal)
}

fun Instant.convertToUtcZone(): ZonedDateTime {
   return this.atZone(zoneIdUTC)
}

fun Instant.ignoreMillis(): Instant {
   return this.with(ChronoField.NANO_OF_SECOND, 0)
}

fun Long.toInstant(): Instant {
   return Instant.ofEpochMilli(this)
}

// Formatter are thread-safe
private val dateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
private val dateTimeFormatterSys = DateTimeFormatter.ofPattern("yyyyMMdd-HH:mm:ss")!!
private val dateTimeFormatterSys_nano = DateTimeFormatter.ofPattern("yyyyMMdd-HH:mm:ss.n")!!
private val zoneIdLocal = ZoneId.systemDefault()
private val zoneIdUTC = ZoneId.of("UTC")

fun ZonedDateTime.toStr(): String {
   return dateTimeFormatter.format(this)
}

fun ZonedDateTime.toStrSys(): String {
   if (this.nano > 0) {
      return dateTimeFormatterSys_nano.format(this).removeSuffix("000000")
   }
   return dateTimeFormatterSys.format(this)
}

fun ZonedDateTime.toStrFilename(): String {
   return toStrSys()
             .replace(Regex("[:]"), "")
             .replace('-', '_')
}