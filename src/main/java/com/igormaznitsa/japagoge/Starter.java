package com.igormaznitsa.japagoge;

import javax.swing.*;
import java.awt.*;

public class Starter {
  public static void main(final String... args) {
    SwingUtilities.invokeLater(() -> {
      var screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
      try {
        var window = new JapagogeFrame(screen);
        window.setVisible(true);
      } catch (AWTException ex) {
        JOptionPane.showMessageDialog(null, "Can't create frame", "Error", JOptionPane.ERROR_MESSAGE);
      }
    });
  }
}
