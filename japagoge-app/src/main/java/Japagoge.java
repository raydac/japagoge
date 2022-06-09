import com.igormaznitsa.japagoge.JapagogeFrame;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class Japagoge {

  public static void main(final String... args) {
    SwingUtilities.invokeLater(() -> {
      try {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
      } catch (Exception ex) {
        // do nothing
      }

      GraphicsConfiguration
          screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
          .getDefaultConfiguration();
      try {
        JapagogeFrame window = new JapagogeFrame(screen);
        window.setVisible(true);
      } catch (Exception ex) {
        ex.printStackTrace();
        JOptionPane.showMessageDialog(null, "Can't create frame", "Error",
            JOptionPane.ERROR_MESSAGE);
      }
    });
  }

}
