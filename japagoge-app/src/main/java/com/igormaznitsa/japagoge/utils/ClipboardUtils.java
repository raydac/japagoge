package com.igormaznitsa.japagoge.utils;

import java.awt.Toolkit;
import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ClipboardUtils {
  private static final Logger LOGGER = Logger.getLogger(ClipboardUtils.class.getSimpleName());

  private ClipboardUtils() {

  }

  public static void placeFileLinks(final File file) {
    try {
      java.awt.datatransfer.Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
      FileTransferable transferable = new FileTransferable(file);
      clipboard.setContents(transferable, transferable);
      LOGGER.info("Placed link for " + file.getAbsolutePath());
    } catch (Exception ex) {
      LOGGER.log(Level.SEVERE, "Error during publicFileLink", ex);
    }
  }
}
