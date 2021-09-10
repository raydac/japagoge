package com.igormaznitsa.japagoge;

import com.igormaznitsa.japagoge.mouse.MouseInfoProviderFactory;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Area;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.Objects.requireNonNull;

public class JapagogeFrame extends JFrame {

  private static final Logger LOGGER = Logger.getLogger("JapagogeFrame");

  private static final int BORDER_SIZE = 5;
  private static final int TITLE_HEIGHT = 26;

  private static final Dimension INITIAL_SIZE = new Dimension(256, 256);
  private static final Color COLOR_SELECT_POSITION = Color.GREEN;
  private static final Color COLOR_RECORDING = Color.CYAN;
  private static final Color COLOR_SAVING_RESULT = Color.YELLOW;
  private final ComponentResizer resizer;
  private final AtomicReference<ScreenCapturer> currentScreenCapturer = new AtomicReference<>();
  private final BufferedImage imageClose;
  private final BufferedImage imagePreferences;
  private final BufferedImage imageHourglassIcon;
  private final Rectangle buttonClose = new Rectangle();
  private final Rectangle buttonPreferences = new Rectangle();
  private Point lastMousePressedTitleScreenPoint = null;
  private final AtomicReference<State> state = new AtomicReference<>(State.SELECT_POSITION);

  public JapagogeFrame(final GraphicsConfiguration gc) throws AWTException {
    super("Japagoge", gc);

    this.getRootPane().putClientProperty("Window.shadow", Boolean.FALSE);

    try {
      this.setIconImage(ImageIO.read(requireNonNull(JapagogeFrame.class.getResourceAsStream("/icons/appico.png"))));
      this.imageHourglassIcon = ImageIO.read(requireNonNull(JapagogeFrame.class.getResourceAsStream("/icons/hourglass48x48.png")));
      this.imageClose = ImageIO.read(requireNonNull(JapagogeFrame.class.getResourceAsStream("/icons/btn_close.png")));
      this.imagePreferences = ImageIO.read(requireNonNull(JapagogeFrame.class.getResourceAsStream("/icons/btn_prefs.png")));
    } catch (Exception ex) {
      throw new Error(ex);
    }

    this.setUndecorated(true);
    this.setBackground(Color.ORANGE);
    this.setAlwaysOnTop(true);

    this.setFocusableWindowState(false);
    this.setFocusable(false);

    this.resizer = new ComponentResizer(new Insets(0, BORDER_SIZE, BORDER_SIZE, BORDER_SIZE));
    this.resizer.registerComponent(this);
    this.resizer.setMinimumSize(new Dimension(64, 64));
    this.resizer.setMaximumSize(new Dimension(2048, 2048));
    this.resizer.setSnapSize(new Dimension(1, 1));

    this.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(final ComponentEvent e) {
        updateLook();
      }
    });

    this.setPreferredSize(INITIAL_SIZE);
    this.setSize(INITIAL_SIZE);

    this.addMouseListener(new MouseAdapter() {

      @Override
      public void mouseClicked(final MouseEvent e) {
        if (!e.isConsumed()) {
          final State currentState = state.get();
          var componentPoint = e.getPoint();
          if (currentState == State.SELECT_POSITION && buttonClose.contains(componentPoint)) {
            onButtonClose();
          } else if (currentState == State.SELECT_POSITION && buttonPreferences.contains(componentPoint)) {
            onButtonPrefs();
          } else if (componentPoint.y < TITLE_HEIGHT && e.getClickCount() > 1) {
            lastMousePressedTitleScreenPoint = null;
            e.consume();
            onTitleDoubleClick();
          }
        }
      }

      @Override
      public void mousePressed(final MouseEvent e) {
        if (!e.isConsumed()) {
          var componentPoint = e.getPoint();
          var screenPoint = new Point(componentPoint);
          SwingUtilities.convertPointToScreen(screenPoint, JapagogeFrame.this);
          lastMousePressedTitleScreenPoint = screenPoint;
          if (componentPoint.y > TITLE_HEIGHT) {
            lastMousePressedTitleScreenPoint = null;
          }
        }
      }

      @Override
      public void mouseReleased(final MouseEvent e) {
        lastMousePressedTitleScreenPoint = null;
      }
    });

    this.addMouseMotionListener(new MouseMotionAdapter() {
      @Override
      public void mouseMoved(final MouseEvent e) {
        if (!e.isConsumed() && resizer.isEnabled()) {
          var newMouseScreenPoint = new Point(e.getPoint());
          if (newMouseScreenPoint.getY() < TITLE_HEIGHT) {
            if (buttonClose.contains(newMouseScreenPoint) || buttonPreferences.contains(newMouseScreenPoint)) {
              setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            } else {
              setCursor(Cursor.getDefaultCursor());
            }
          }
          e.consume();
        }
      }

      @Override
      public void mouseDragged(final MouseEvent e) {
        if (!e.isConsumed() && lastMousePressedTitleScreenPoint != null && resizer.isEnabled()) {
          var newMouseScreenPoint = new Point(e.getPoint());
          SwingUtilities.convertPointToScreen(newMouseScreenPoint, JapagogeFrame.this);

          var dx = newMouseScreenPoint.x - lastMousePressedTitleScreenPoint.x;
          var dy = newMouseScreenPoint.y - lastMousePressedTitleScreenPoint.y;

          var windowLocation = JapagogeFrame.this.getLocation();
          windowLocation.move(windowLocation.x + dx, windowLocation.y + dy);
          JapagogeFrame.this.setLocation(windowLocation);

          lastMousePressedTitleScreenPoint = newMouseScreenPoint;

          e.consume();
        }
      }
    });

    this.setState(State.SELECT_POSITION);
  }

  private void setState(final State newState) {
    if (this.state.get() != newState) {
      LOGGER.info("Change state to: " + newState);
      this.state.set(newState);
    }
    this.updateLook();
  }

  private void updateLook() {
    var bounds = this.getBounds();
    switch (this.state.get()) {
      case RECORD: {
        this.setOpacity(0.3f);
        this.setBackground(COLOR_RECORDING);
        this.setForeground(COLOR_RECORDING);
        this.getContentPane().setBackground(COLOR_RECORDING);
        this.setShape(this.makeArea(bounds.width, bounds.height, false));
      }
      break;
      case SELECT_POSITION: {
        this.setOpacity(1.0f);
        this.setBackground(COLOR_SELECT_POSITION);
        this.setForeground(COLOR_SELECT_POSITION);
        this.getContentPane().setBackground(COLOR_SELECT_POSITION);
        this.setShape(this.makeArea(bounds.width, bounds.height, true));
      }
      break;
      case SAVING_RESULT: {
        this.setOpacity(1.0f);
        this.setBackground(COLOR_SAVING_RESULT);
        this.setForeground(COLOR_SAVING_RESULT);
        this.getContentPane().setBackground(COLOR_SAVING_RESULT);
        this.setShape(this.makeArea(bounds.width, bounds.height, true));
      }
      break;
    }
    this.setBounds(bounds);
    this.revalidate();
    this.repaint();
  }

  private void onTitleDoubleClick() {
    switch ((this.state.get())) {
      case SELECT_POSITION: {
        this.setState(State.RECORD);
        final File tempFile;
        try {
          tempFile = makeTempRecordFile();
          LOGGER.info("Temp file: " + tempFile);
        } catch (IOException ex) {
          LOGGER.log(Level.SEVERE, "Can't make temp file", ex);
          JOptionPane.showMessageDialog(this, "Can't create temp file", "Error", JOptionPane.ERROR_MESSAGE);
          this.setState(State.SELECT_POSITION);
          return;
        }

        this.resizer.setEnable(false);

        try {
          final ScreenCapturer newScreenCapturer = new ScreenCapturer(
                  this.getGraphicsConfiguration().getDevice(),
                  this.findScreeCaptureArea(),
                  tempFile,
                  JapagogeConfig.getInstance().isPointer() ? MouseInfoProviderFactory.getInstance().makeProvider() : null,
                  JapagogeConfig.getInstance().getFilter(),
                  JapagogeConfig.getInstance().getGifPaletteForRgb(),
                  Duration.ofMillis(JapagogeConfig.getInstance().getFrameDelay())
          );
          if (this.currentScreenCapturer.compareAndSet(null, newScreenCapturer)) {
            SwingUtilities.invokeLater(newScreenCapturer::start);
          }
        } catch (AWTException ex) {
          JOptionPane.showMessageDialog(this, "Looks like it is impossible capture screen in your OS!", "Error", JOptionPane.ERROR_MESSAGE);
          System.exit(1);
        }
      }
      break;
      case RECORD: {
        try {
          var currentCapturer = this.currentScreenCapturer.getAndSet(null);
          if (currentCapturer != null) {
            currentCapturer.stop(JapagogeConfig.getInstance().getLoops());
            this.setState(State.SAVING_RESULT);

            SwingUtilities.invokeLater(() -> {
              var targetFile = this.makeTargetFile(JapagogeConfig.getInstance().getTargetFolder(), currentCapturer.getFilter().name(), "png");
              var tempFile = currentCapturer.getTargetFile();
              try {
                var fileChooser = new JFileChooser(JapagogeConfig.getInstance().getTargetFolder());
                fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                fileChooser.setSelectedFile(targetFile);
                fileChooser.setAcceptAllFileFilterUsed(false);

                var apngFilter = new FileFilter() {
                  @Override
                  public boolean accept(final File f) {
                    return f.isDirectory() || f.getName().toLowerCase(Locale.ENGLISH).endsWith(".png");
                  }

                  @Override
                  public String getDescription() {
                    return "PNG animation files (*.png)";
                  }
                };

                var gifFilter = new FileFilter() {
                  @Override
                  public boolean accept(final File f) {
                    return f.isDirectory() || f.getName().toLowerCase(Locale.ENGLISH).endsWith(".gif");
                  }

                  @Override
                  public String getDescription() {
                    return "GIF animation files (*.gif)";
                  }
                };

                fileChooser.addChoosableFileFilter(apngFilter);
                fileChooser.addChoosableFileFilter(gifFilter);
                fileChooser.setFileFilter(apngFilter);

                fileChooser.setDialogTitle("Save record");

                fileChooser.addPropertyChangeListener(JFileChooser.FILE_FILTER_CHANGED_PROPERTY, evt -> {
                  var selectedFilter = (FileFilter) evt.getNewValue();
                  final String extension;
                  if (selectedFilter == gifFilter) {
                    extension = "gif";
                  } else {
                    extension = "png";
                  }
                  fileChooser.setSelectedFile(this.makeTargetFile(fileChooser.getCurrentDirectory(), currentCapturer.getFilter().name(), extension));
                });
                if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                  var selectedFile = fileChooser.getSelectedFile();

                  var selectedFilter = fileChooser.getFileFilter();
                  JapagogeConfig.getInstance().setTargetFolder(selectedFile.getParentFile());

                  if (selectedFilter == gifFilter) {
                    LOGGER.info("Converting APNG file into GIF: " + tempFile);
                    var converter = new APngToGifConvertingWorker(currentCapturer, selectedFile);
                    converter.execute();
                    try {
                      JOptionPane.showOptionDialog(
                              this,
                              APngToGifConvertingWorker.makePanelFor(converter),
                              "Exporting as GIF (might take a while)",
                              JOptionPane.DEFAULT_OPTION,
                              JOptionPane.PLAIN_MESSAGE,
                              new ImageIcon(this.imageHourglassIcon),
                              new Object[]{"Cancel"},
                              null);
                    } finally {
                      converter.dispose();
                    }
                  } else {
                    LOGGER.info("Just moving APNG file: " + tempFile);
                    try {
                      Files.move(tempFile.toPath(), selectedFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                      LOGGER.info("Saved result file " + selectedFile.getName() + " size " + selectedFile.length() + " bytes");
                    } catch (Exception ex) {
                      LOGGER.log(Level.SEVERE, "Can't move result file", ex);
                      JOptionPane.showMessageDialog(this, "Can't move result file!", "Error", JOptionPane.ERROR_MESSAGE);
                    }
                  }
                } else {
                  LOGGER.info("Canceling file save");
                }
              } finally {
                if (tempFile.exists()) {
                  Exception detectedError;
                  int attempts = 3;
                  do {
                    detectedError = null;
                    try {
                      Files.deleteIfExists(tempFile.toPath());
                    } catch (Exception ex) {
                      try {
                        Thread.sleep(500);
                      } catch (InterruptedException ix) {
                        Thread.currentThread().interrupt();
                        break;
                      }
                      detectedError = ex;
                    }
                  } while (detectedError != null && --attempts > 0);
                  if (detectedError != null) {
                    LOGGER.log(Level.SEVERE, "Can't delete temp file, marking it delete on exit: " + tempFile, detectedError);
                    tempFile.deleteOnExit();
                  }
                }
                this.setState(State.SELECT_POSITION);
              }
            });
          } else {
            this.setState(State.SELECT_POSITION);
          }
        } finally {
          this.resizer.setEnable(true);
        }
      }
      break;
      case SAVING_RESULT: {
        // DO NOTHING BECAUSE ALL BUSINESS IN SAVE DIALOG
      }
      break;
    }
  }

  private File makeTargetFile(final File parentFolder, final String filter, final String extension) {
    var dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
    return new File(parentFolder, "record-" + filter.toLowerCase(Locale.ENGLISH) + '-' + dateFormat.format(new Date()) + '.' + extension);
  }

  private File makeTempRecordFile() throws IOException {
    return File.createTempFile(".japagoge-record", ".png");
  }

  private Area makeArea(final int width, final int height, final boolean cross) {
    var result = new Area(new Rectangle(0, 0, width, height));
    result.subtract(new Area(new Rectangle(BORDER_SIZE, BORDER_SIZE, width - (BORDER_SIZE * 2), height - (BORDER_SIZE * 2))));

    final int titleHeight = TITLE_HEIGHT;

    result.add(new Area(new Rectangle(0, 0, width, titleHeight)));

    if (cross) {
      result.add(new Area(new Polygon(new int[]{0, width, width - 1, 0}, new int[]{titleHeight, height - 1, height, titleHeight + 1}, 4)));
      result.add(new Area(new Polygon(new int[]{0, width - 1, width, 1}, new int[]{height - 1, titleHeight, titleHeight + 1, height}, 4)));
    }

    int buttonStartX = width - 52;
    this.buttonPreferences.setBounds(buttonStartX, (TITLE_HEIGHT - this.imagePreferences.getHeight()) / 2, this.imagePreferences.getWidth(), this.imagePreferences.getHeight());
    buttonStartX += this.buttonPreferences.width + 4;
    this.buttonClose.setBounds(buttonStartX, (TITLE_HEIGHT - this.imageClose.getHeight()) / 2, this.imageClose.getWidth(), this.imageClose.getHeight());

    return result;
  }

  private Rectangle findScreeCaptureArea() {
    final Rectangle bounds = this.getBounds();
    bounds.setBounds(bounds.x + BORDER_SIZE, bounds.y + TITLE_HEIGHT, bounds.width - BORDER_SIZE * 2, bounds.height - BORDER_SIZE - TITLE_HEIGHT);
    return bounds;
  }

  private void onButtonClose() {
    if (this.state.get() == State.SELECT_POSITION) {
      LOGGER.info("Closing by button");
      this.dispose();
      System.exit(0);
    }
  }

  private void onButtonPrefs() {
    var data = new JapagogeConfig.JapagogeConfigData();
    var panel = new PreferencesPanel(data);
    if (JOptionPane.showConfirmDialog(this, panel, "Options", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
      panel.fillData();
      data.save();
    }
  }

  @Override
  public void paint(final Graphics g) {
    final Graphics2D gfx = (Graphics2D) g;
    var bounds = this.getBounds();
    gfx.setColor(this.getBackground());
    gfx.fillRect(0, 0, bounds.width, bounds.height);
    if (this.state.get() == State.SELECT_POSITION) {
      gfx.drawImage(this.imagePreferences, this.buttonPreferences.x, this.buttonPreferences.y, null);
      gfx.drawImage(this.imageClose, this.buttonClose.x, this.buttonClose.y, null);
    } else {
      gfx.setColor(Color.BLACK);
      gfx.drawRect(0, 0, bounds.width - 1, bounds.height - 1);
    }
  }

  private enum State {
    SELECT_POSITION,
    RECORD,
    SAVING_RESULT
  }

}
