import com.igormaznitsa.japagoge.JapagogeFrame;

import javax.swing.*;
import java.awt.*;

public class Japagoge {

  public static void main(final String... args) {
    System.setProperty("sun.java2d.dpiaware", "true");

    SwingUtilities.invokeLater(() -> {
      try {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
      } catch (Exception ex) {
        // do nothing
      }

      var screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
      try {
        var window = new JapagogeFrame(screen);
        window.setVisible(true);
      } catch (Exception ex) {
        ex.printStackTrace();
        JOptionPane.showMessageDialog(null, "Can't create frame", "Error", JOptionPane.ERROR_MESSAGE);
      }
    });
  }

}
