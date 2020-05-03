package de.danielscholz.fileIndexer.common

import de.danielscholz.fileIndexer.Global
import org.slf4j.LoggerFactory
import java.lang.management.ManagementFactory
import java.lang.management.MemoryNotificationInfo
import java.lang.management.MemoryType
import javax.management.Notification
import javax.management.NotificationEmitter
import javax.management.NotificationListener
import kotlin.math.floor

private val logger = LoggerFactory.getLogger(LowMemoryListener::class.java)

class LowMemoryListener(private val max: Long, private val threshold: Long) : NotificationListener {

   private val logger = LoggerFactory.getLogger(this.javaClass)

   override fun handleNotification(notification: Notification, handback: Any?) {
      if (notification.type == MemoryNotificationInfo.MEMORY_THRESHOLD_EXCEEDED) {
         logger.error("Memory usage threshold reached: " + threshold.formatAsFileSize() + " von " + max.formatAsFileSize())
         logger.error("shutting down..")
         Global.cancel = true
      }
   }
}

private var registered = false

fun registerLowMemoryListener() {
   if (registered) return

   // heuristic to find the tenured pool (largest heap) as seen on http://www.javaspecialists.eu/archive/Issue092.html
   val tenuredGenPool = ManagementFactory.getMemoryPoolMXBeans().firstOrNull {
      it.type == MemoryType.HEAP && it.isUsageThresholdSupported
   }

   if (tenuredGenPool != null) {
      val max = tenuredGenPool.usage.max
      val threshold = floor(max * 0.9).toLong() // we do something when we reached 90% of memory usage
      tenuredGenPool.usageThreshold = threshold

      val notificationEmitter = ManagementFactory.getMemoryMXBean() as NotificationEmitter
      notificationEmitter.addNotificationListener(LowMemoryListener(max, threshold), null, null)
      registered = true

      logger.debug("LowMemoryListener successfully registered. Max available Memory: ${max.formatAsFileSize()}, Threshold: ${threshold.formatAsFileSize()}")
   }
}