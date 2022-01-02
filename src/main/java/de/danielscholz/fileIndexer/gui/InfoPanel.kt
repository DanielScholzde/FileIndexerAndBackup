package de.danielscholz.fileIndexer.gui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import de.danielscholz.fileIndexer.Global
import de.danielscholz.fileIndexer.actions.IndexFilesStats
import de.danielscholz.fileIndexer.common.formatAsFileSize
import kotlin.concurrent.thread


object InfoPanel {

   fun show(stats: IndexFilesStats) {
      thread {
         application(exitProcessOnExit = false) {
            Window(
               onCloseRequest = ::exitApplication,
               title = "Index Files",
               state = rememberWindowState(width = 600.dp, height = 500.dp)
            ) {
               val duration = remember { mutableStateOf("") }
               val remainingDuration = remember { mutableStateOf("") }
               val progressTotal = remember { mutableStateOf("") }
               val newIndexedFiles = remember { mutableStateOf("") }
               val newIndexedFilesSize = remember { mutableStateOf("") }
               val fastMode = remember { mutableStateOf("") }
               val processedMbPerSecond = remember { mutableStateOf("") }
               val dbTime = remember { mutableStateOf("") }
               val currentParallelReads = remember { mutableStateOf("") }
               val currentProcessedFilename = remember { mutableStateOf("") }
               val textArea = remember { mutableStateOf("") }

               MaterialTheme {
                  Column(Modifier.fillMaxSize(), Arrangement.spacedBy(5.dp)) {
                     Text("Time: ${duration.value}")
                     Text("Remaining time: ${remainingDuration.value}")
                     Text("Progress: ${progressTotal.value}")
                     Text("New indexed: ${newIndexedFiles.value} / ${newIndexedFilesSize.value}")
                     Text("FastMode statistic: ${fastMode.value}")
                     Text("Processed data per sec.: ${processedMbPerSecond.value}")
                     Text("DB time: ${dbTime.value}")
                     Text("Parallel reads: ${currentParallelReads.value}")
                     Text("Processing file: ${currentProcessedFilename.value}")
                     Text("Messages: ${textArea.value}")
                  }
               }

               LaunchedEffect(Unit) {
                  while (true) {
                     withFrameMillis {
                        progressTotal.value = stats.getProgressTotal()
                        duration.value = stats.getDuration()
                        remainingDuration.value = stats.getRemainingDuration()
                        fastMode.value = stats.getFastModeStats()
                        processedMbPerSecond.value = stats.getProcessedMbPerSec()
                        newIndexedFiles.value = Global.stat.newIndexedFilesCount.get().toString()
                        newIndexedFilesSize.value = Global.stat.newIndexedFilesSize.get().formatAsFileSize()
                        dbTime.value = stats.getDbTime()
                        currentParallelReads.value = stats.currentParallelReads()
                        currentProcessedFilename.value = stats.currentProcessedFile
                     }
                  }
               }
            }
         }
      }
   }

   fun close() {

   }
}
