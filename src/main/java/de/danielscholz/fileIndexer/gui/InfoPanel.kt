package de.danielscholz.fileIndexer.gui

import de.danielscholz.fileIndexer.Global
import java.awt.Container
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
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
         UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
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
      frame.setSize(700, 400)

      addGuiElements(frame.contentPane)

      //otherInfos.background = Color(255, 255, 255, 255)
      //frame.pack()
   }

   private fun addGuiElements(pane: Container) {
      pane.layout = object : GridBagLayout() {
         override fun getLayoutAlignmentY(parent: Container?): Float {
            return 0f
         }
      }

      val c = GridBagConstraints()
      c.fill = GridBagConstraints.BOTH
//      c.ipadx = 3
//      c.ipady = 3
      c.insets = Insets(3, 4, 3, 4)
      c.anchor = GridBagConstraints.FIRST_LINE_START

      pane.add(JLabel("Time: ").left(), c.next())
      pane.add(duration, c.next())
      pane.add(JLabel("Remaining time: ").left(), c.next())
      pane.add(remainingDuration, c.next())
      pane.add(JLabel("Progress directory: ").left(), c.next())
      pane.add(progressDirectory, c.next())
      pane.add(JLabel("Progress total: ").left(), c.next())
      pane.add(progressTotal, c.next())
      pane.add(JLabel("FastMode statistic: ").left(), c.next())
      pane.add(fastMode, c.next())
      pane.add(JLabel("New indexed: ").left(), c.next())
      pane.add(newIndexedData, c.next())
      pane.add(JLabel("Processed data per sec.: ").left(), c.next())
      pane.add(processedMbPerSecond, c.next())
      pane.add(JLabel("DB time: ").left(), c.next())
      pane.add(dbTime, c.next())
      pane.add(JLabel("Parallel reads: ").left(), c.next())
      pane.add(currentParallelReads, c.next())
      pane.add(JLabel("Processing file: ").left(), c.next())
      pane.add(currentProcessedFilename, c.next())
      pane.add(JLabel("other infos: ").left(), c.next())
      pane.add(otherInfos, c.next())
      otherInfos.isEditable = false
      pane.add(cancel.left(), c.next())
      cancel.addActionListener { e -> Global.cancel = true }
   }

   var row = 0
   var col = 0

   private fun GridBagConstraints.next(): GridBagConstraints {
      this.gridx = col
      this.gridy = row
      if (col == 0) {
         this.weightx = 0.25
      } else {
         this.weightx = 1 - this.weightx
      }

      col++
      if (col > 1) {
         row++
         col = 0
      }
      return this
   }

   private fun JComponent.left(): JComponent {
//      this.minimumSize = Dimension(200, 20)
//      this.preferredSize = Dimension(200, 20)
//      this.maximumSize = Dimension(200, 20)
      return this
   }
}