package de.danielscholz.fileIndexer.gui;

import javax.swing.*;
import java.awt.*;


public class InfopanelSwing {

    private static final String EMPTY = "               ";

    private static InfopanelSwing INSTANCE;

    static {
        JFrame.setDefaultLookAndFeelDecorated(true);
    }

    private JFrame frame = new JFrame();
    private JLabel duration = new JLabel(EMPTY);
    private JLabel remainingDuration = new JLabel(EMPTY);
    private JLabel progressTotal = new JLabel(EMPTY);
    private JLabel newIndexedData = new JLabel(EMPTY);
    private JLabel fastMode = new JLabel(EMPTY);
    private JLabel processedMbPerSecond = new JLabel(EMPTY);
    private JLabel dbTime = new JLabel(EMPTY);
    private JLabel currentParallelReads = new JLabel(EMPTY);
    private JLabel currentProcessedFilename = new JLabel(EMPTY);
    private JTextArea textArea = new JTextArea();

    private void init() {
        frame.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        frame.setTitle("FileIndexerAndBackup - indexing progress");
        frame.setResizable(true);
        frame.setLayout(new GridLayout(0, 2));
        frame.setSize(600, 300);
        frame.add(new JLabel("Time: "));
        frame.add(duration);
        frame.add(new JLabel("Remaining time: "));
        frame.add(remainingDuration);
        frame.add(new JLabel("Progress total: "));
        frame.add(progressTotal);
        frame.add(new JLabel("FastMode statistic: "));
        frame.add(fastMode);
        frame.add(new JLabel("New indexed: "));
        frame.add(newIndexedData);
        frame.add(new JLabel("Processed data per sec.: "));
        frame.add(processedMbPerSecond);
        frame.add(new JLabel("DB time: "));
        frame.add(dbTime);
        frame.add(new JLabel("Parallel reads: "));
        frame.add(currentParallelReads);
        frame.add(new JLabel("Processing file: "));
        frame.add(currentProcessedFilename);
        //frame.add(new JLabel("other: "));
        //frame.add(textArea);
        //frame.pack();
        textArea.setEditable(false);
    }

    public static void show() {
        if (INSTANCE == null) {
            INSTANCE = new InfopanelSwing();
            INSTANCE.init();
            INSTANCE.frame.setVisible(true);
        }
    }

    public static void close() {
        if (INSTANCE != null) {
            INSTANCE.frame.setVisible(false);
            INSTANCE.frame.dispose();
            INSTANCE = null;
        }
    }

    public static void setProgressTotal(String text) {
        if (INSTANCE != null)
            INSTANCE.progressTotal.setText(text);
    }

    public static void setFastModeData(String text) {
        if (INSTANCE != null)
            INSTANCE.fastMode.setText(text);
    }

    public static void setNewIndexedData(String text) {
        if (INSTANCE != null)
            INSTANCE.newIndexedData.setText(text);
    }

    public static void setProcessedMbPerSec(String text) {
        if (INSTANCE != null)
            INSTANCE.processedMbPerSecond.setText(text);
    }

    public static void setDbTime(String text) {
        if (INSTANCE != null)
            INSTANCE.dbTime.setText(text);
    }

    public static void setCurrentParallelReads(String text) {
        if (INSTANCE != null)
            INSTANCE.currentParallelReads.setText(text);
    }

    public static void setCurrentProcessedFilename(String text) {
        if (INSTANCE != null)
            INSTANCE.currentProcessedFilename.setText(text);
    }

    public static void setDuration(String text) {
        if (INSTANCE != null)
            INSTANCE.duration.setText(text);
    }

    public static void setRemainingDuration(String text) {
        if (INSTANCE != null)
            INSTANCE.remainingDuration.setText(text);
    }

//    public static void showText(String text) {
//        if (INSTANCE != null)
//            INSTANCE.textArea.setText(text);
//    }

}
