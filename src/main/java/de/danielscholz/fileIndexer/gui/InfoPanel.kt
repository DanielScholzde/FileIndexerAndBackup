package de.danielscholz.fileIndexer.gui

import de.danielscholz.fileIndexer.Global
import java.awt.GridLayout
import javax.swing.*

class InfoPanel {

   companion object {
      private const val EMPTY = "               "

      private var INSTANCE: InfoPanel? = null

      fun show() {
         if (INSTANCE == null) {
            INSTANCE = InfoPanel()
            INSTANCE!!.init()
            INSTANCE!!.frame.isVisible = true
         }
      }

      fun close() {
         if (INSTANCE != null) {
            INSTANCE!!.frame.isVisible = false
            INSTANCE!!.frame.dispose()
            INSTANCE = null
         }
      }

      fun setProgressTotal(text: String?) {
         if (INSTANCE != null) INSTANCE!!.progressTotal.text = text
      }

      fun setProgressDirectory(text: String?) {
         if (INSTANCE != null) INSTANCE!!.progressDirectory.text = text
      }

      fun setFastModeData(text: String?) {
         if (INSTANCE != null) INSTANCE!!.fastMode.text = text
      }

      fun setNewIndexedData(text: String?) {
         if (INSTANCE != null) INSTANCE!!.newIndexedData.text = text
      }

      fun setProcessedMbPerSec(text: String?) {
         if (INSTANCE != null) INSTANCE!!.processedMbPerSecond.text = text
      }

      fun setDbTime(text: String?) {
         if (INSTANCE != null) INSTANCE!!.dbTime.text = text
      }

      fun setCurrentParallelReads(text: String?) {
         if (INSTANCE != null) INSTANCE!!.currentParallelReads.text = text
      }

      fun setCurrentProcessedFilename(text: String?) {
         if (INSTANCE != null) INSTANCE!!.currentProcessedFilename.text = text
      }

      fun setDuration(text: String?) {
         if (INSTANCE != null) INSTANCE!!.duration.text = text
      }

      fun setRemainingDuration(text: String?) {
         if (INSTANCE != null) INSTANCE!!.remainingDuration.text = text
      }

      fun setOtherInfos(text: String?) {
         if (INSTANCE != null) INSTANCE!!.otherInfos.text = text
      }

      init {
         JFrame.setDefaultLookAndFeelDecorated(true)
      }
   }

   private val frame = JFrame()
   private val duration = JLabel(EMPTY)
   private val remainingDuration = JLabel(EMPTY)
   private val progressTotal = JLabel(EMPTY)
   private val progressDirectory = JLabel(EMPTY)
   private val newIndexedData = JLabel(EMPTY)
   private val fastMode = JLabel(EMPTY)
   private val processedMbPerSecond = JLabel(EMPTY)
   private val dbTime = JLabel(EMPTY)
   private val currentParallelReads = JLabel(EMPTY)
   private val currentProcessedFilename = JLabel(EMPTY)
   private val otherInfos = JTextArea()
   private val cancel = JButton("Cancel")

   private fun init() {
      frame.defaultCloseOperation = WindowConstants.HIDE_ON_CLOSE
      frame.title = "FileIndexerAndBackup - indexing progress"
      frame.isResizable = true
      frame.layout = GridLayout(0, 2)
      frame.setSize(600, 300)
      frame.add(JLabel("Time: "))
      frame.add(duration)
      frame.add(JLabel("Remaining time: "))
      frame.add(remainingDuration)
      frame.add(JLabel("Progress directory: "))
      frame.add(progressDirectory)
      frame.add(JLabel("Progress total: "))
      frame.add(progressTotal)
      frame.add(JLabel("FastMode statistic: "))
      frame.add(fastMode)
      frame.add(JLabel("New indexed: "))
      frame.add(newIndexedData)
      frame.add(JLabel("Processed data per sec.: "))
      frame.add(processedMbPerSecond)
      frame.add(JLabel("DB time: "))
      frame.add(dbTime)
      frame.add(JLabel("Parallel reads: "))
      frame.add(currentParallelReads)
      frame.add(JLabel("Processing file: "))
      frame.add(currentProcessedFilename)
      frame.add(JLabel("other infos: "))
      frame.add(otherInfos)
      otherInfos.isEditable = false
      frame.add(cancel)
      cancel.addActionListener { e -> Global.cancel = true }
      //frame.pack();
   }

}