package com.igormaznitsa.japagoge;

import com.igormaznitsa.japagoge.filters.ColorFilter;
import com.igormaznitsa.japagoge.mouse.MouseInfoProviderFactory;
import com.igormaznitsa.japagoge.utils.ClipboardUtils;
import com.igormaznitsa.japagoge.utils.Palette256;
import com.igormaznitsa.japagoge.utils.PaletteUtils;
import com.igormaznitsa.japagoge.utils.SystemUtils;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.igormaznitsa.japagoge.utils.SystemUtils.*;

@SuppressWarnings("unused")
public class JapagogeFrame extends JFrame {

  private static final String VERSION = "v 2.1.2";

  private static final Logger LOGGER = Logger.getLogger("JapagogeFrame");

  private static final int BORDER_SIZE = isHiDpi() ? 10 : 5;
  private static final int TITLE_HEIGHT = isHiDpi() ? 72 : 36;

  private static final Color COLOR_SELECT_POSITION = Color.GREEN;
  private static final Color COLOR_RECORDING = Color.CYAN;
  private static final Color COLOR_SAVING_RESULT = Color.YELLOW;
  private final ComponentResizer resizer;
  private final AtomicReference<ScreenCapturer> currentScreenCapturer = new AtomicReference<>();
  private final Image imageConvert = loadIcon("button-convert.png");
  private final Image imageClose = loadIcon("button-close.png");
  private final Image imageSettings = loadIcon("button-settings.png");
  private final Image imageRecord = loadIcon("button-record.png");
  private final Image imageStop = loadIcon("button-stop.png");
  private final Image imageHourglassIcon = loadIcon("hourglass48x48.png");
  private final Rectangle areaButtonConvert = new Rectangle();
  private final Rectangle areaButtonClose = new Rectangle();
  private final Rectangle areaButtonSettings = new Rectangle();
  private final Rectangle areaButtonRecordStop = new Rectangle();
  private final AtomicReference<State> state = new AtomicReference<>(State.SELECT_POSITION);
  private final JWindow statisticWindow;
  private final JLabel labelStatX;
  private final JLabel labelStatY;
  private final JLabel labelStatWidth;
  private final JLabel labelStatHeight;
  private Point lastMousePressedTitleScreenPoint = null;
  private boolean showCapturingAreaMetrics;
  private boolean drawStopButton = true;
  private final Timer halfSecondTimer = new Timer(500, e -> {
    drawStopButton = !drawStopButton;
    repaint();
  });

  public JapagogeFrame(final GraphicsConfiguration gc) {
    super("Japagoge", gc);
    this.setIconImage(loadIcon("appico.png"));

    SystemUtils.setApplicationTaskbarTitle(this.getIconImage(), this.getTitle(), null);
    Font uiManagerFont = UIManager.getFont("InternalFrame.titleFont");
    if (uiManagerFont == null) {
      uiManagerFont = new Font(Font.SANS_SERIF, Font.BOLD, 12);
    }

    this.setFont(isHiDpi() ? fontX2(uiManagerFont) : uiManagerFont);

    this.getRootPane().putClientProperty("Window.shadow", Boolean.FALSE);
    this.getRootPane().getRootPane().putClientProperty("apple.awt.draggableWindowBackground", Boolean.FALSE);

    final Dimension initialSize = isHiDpi() ? new Dimension(640, 512) : new Dimension(320, 256);

    this.setUndecorated(true);
    this.setBackground(Color.ORANGE);
    this.setAlwaysOnTop(true);

    this.setFocusableWindowState(false);
    this.setFocusable(false);

    this.resizer = new ComponentResizer(new Insets(0, BORDER_SIZE, BORDER_SIZE, BORDER_SIZE));
    this.resizer.registerComponent(this);
    this.resizer.setMinimumSize(new Dimension(imageRecord.getWidth(null), TITLE_HEIGHT * 2));
    this.resizer.setMaximumSize(new Dimension(4096, 4096));
    this.resizer.setSnapSize(new Dimension(1, 1));

    this.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(final ComponentEvent e) {
        updateLook();
      }
    });

    this.setPreferredSize(initialSize);
    this.setSize(initialSize);

    this.addMouseListener(new MouseAdapter() {

      @Override
      public void mouseClicked(final MouseEvent e) {
        if (!e.isConsumed()) {
          lastMousePressedTitleScreenPoint = null;
          final State currentState = state.get();
          var componentPoint = e.getPoint();
          switch (currentState) {
            case SELECT_POSITION: {
              if (areaButtonRecordStop.contains(componentPoint)) {
                e.consume();
                startRecording();
              } else if (areaButtonClose.contains(componentPoint)) {
                e.consume();
                onButtonClose();
              } else if (areaButtonConvert.contains(componentPoint)) {
                e.consume();
                onButtonConvert();
              } else if (areaButtonSettings.contains(componentPoint)) {
                e.consume();
                onButtonSettings();
              }
            }
            break;
            case RECORDING: {
              if (e.getClickCount() > 1 && areaButtonRecordStop.contains(componentPoint)) {
                e.consume();
                stopRecording();
              }
            }
            break;
            default: {
              // do noting
            }
            break;
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
            if (areaButtonClose.contains(newMouseScreenPoint)
                    || areaButtonSettings.contains(newMouseScreenPoint)
                    || areaButtonConvert.contains(newMouseScreenPoint)
                    || areaButtonRecordStop.contains(newMouseScreenPoint)) {
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

          validate();

          e.consume();
        }
      }
    });

    this.statisticWindow = new JWindow(this);
    this.statisticWindow.setFocusable(false);
    this.statisticWindow.getRootPane().putClientProperty("Window.shadow", Boolean.FALSE);
    this.statisticWindow.getRootPane().getRootPane().putClientProperty("apple.awt.draggableWindowBackground", Boolean.FALSE);

    this.statisticWindow.setFocusableWindowState(false);
    this.statisticWindow.getContentPane().setBackground(COLOR_SELECT_POSITION);
    this.statisticWindow.getContentPane().setForeground(COLOR_SELECT_POSITION);
    this.statisticWindow.getContentPane().setLayout(new BoxLayout(this.statisticWindow.getContentPane(), BoxLayout.Y_AXIS));

    final Font labelFont = new Font(Font.MONOSPACED, Font.BOLD, isHiDpi() ? 28 : 14);

    this.labelStatX = new JLabel(" X= ");
    this.labelStatX.setFont(labelFont);
    this.labelStatX.setBackground(COLOR_SELECT_POSITION);

    this.labelStatY = new JLabel(" Y= ");
    this.labelStatY.setFont(labelFont);
    this.labelStatY.setBackground(COLOR_SELECT_POSITION);

    this.labelStatWidth = new JLabel(" W= ");
    this.labelStatWidth.setFont(labelFont);
    this.labelStatWidth.setBackground(COLOR_SELECT_POSITION);

    this.labelStatHeight = new JLabel(" H= ");
    this.labelStatHeight.setFont(labelFont);
    this.labelStatHeight.setBackground(COLOR_SELECT_POSITION);

    this.statisticWindow.getContentPane().add(this.labelStatX);
    this.statisticWindow.getContentPane().add(this.labelStatY);
    this.statisticWindow.getContentPane().add(this.labelStatWidth);
    this.statisticWindow.getContentPane().add(this.labelStatHeight);

    this.setLocationRelativeTo(null);

    this.showCapturingAreaMetrics = JapagogeConfig.getInstance().isShowBoundsInfo();

    this.statisticWindow.setVisible(this.showCapturingAreaMetrics);

    this.setState(State.SELECT_POSITION);
  }

  private static Image loadIcon(final String resourceName) {
    try (final InputStream in = Objects.requireNonNull(JapagogeFrame.class.getResourceAsStream("/icons/" + resourceName))) {
      return isHiDpi() ? imageX2(ImageIO.read(in)) : ImageIO.read(in);
    } catch (Exception ex) {
      throw new Error("Can't load resource image: " + resourceName, ex);
    }
  }

  private void setState(final State newState) {
    if (this.state.get() != newState) {
      LOGGER.info("Change state to: " + newState);
      this.state.set(newState);
      if (newState == State.RECORDING) {
        this.halfSecondTimer.start();
      } else {
        this.halfSecondTimer.stop();
      }
    }
    this.updateLook();
  }

  @Override
  public void validate() {
    super.validate();

    final Rectangle mainWindowBounds = JapagogeFrame.this.getBounds();
    final Rectangle capturingArea = this.findScreeCaptureArea();
    this.labelStatX.setText(" X=" + capturingArea.x + ' ');
    this.labelStatY.setText(" Y=" + capturingArea.y + ' ');
    this.labelStatWidth.setText(" W=" + capturingArea.width + ' ');
    this.labelStatHeight.setText(" H=" + capturingArea.height + ' ');
    this.statisticWindow.pack();
    this.statisticWindow.setLocation(mainWindowBounds.x + (mainWindowBounds.width - this.statisticWindow.getWidth()) / 2, mainWindowBounds.y + TITLE_HEIGHT + (mainWindowBounds.height - TITLE_HEIGHT - this.statisticWindow.getHeight()) / 2);
  }

  private static String extractNameWithoutExtenstion(final File file) {
    String name = file.getName();
    int index = name.lastIndexOf('.');
    if (index > 0) {
      name = name.substring(0, index);
    }
    return name;
  }

  private void startRecording() {
    this.setState(State.RECORDING);
    final File tempFile;
    try {
      tempFile = makeTempRecordFile();
      LOGGER.info("Temp file: " + tempFile);
    } catch (IOException ex) {
      LOGGER.log(Level.SEVERE, "Can't make temp file", ex);
      JOptionPane.showMessageDialog(this, ex.getMessage() == null ? "Can't create temp file" : ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
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
              JapagogeConfig.getInstance().isForceWholeFrame(),
              JapagogeConfig.getInstance().getGifPaletteForRgb(),
              Duration.ofMillis(JapagogeConfig.getInstance().getCaptureDelay()),
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

  private void doVisualConversionPngToGif(
          final File pngFile,
          final File gifFile,
          final boolean accurateRgb,
          final boolean dithering,
          final int[] globalRgb256palette,
          final boolean placeLinkToClipboard,
          final ColorFilter forceColorFilter
  ) {
    LOGGER.info("Converting APNG file into GIF: " + pngFile + " -> " + gifFile);
    var converter = new APngToGifConvertingWorker(
            pngFile,
            gifFile,
            accurateRgb,
            dithering,
            globalRgb256palette,
            forceColorFilter
    );
    converter.execute();
    try {
      JOptionPane.showOptionDialog(
              this,
              APngToGifConvertingWorker.makePanelFor(converter),
              "Converting into GIF (might take a while)",
              JOptionPane.DEFAULT_OPTION,
              JOptionPane.PLAIN_MESSAGE,
              new ImageIcon(this.imageHourglassIcon),
              new Object[]{"Cancel"},
              null);
      if (placeLinkToClipboard)
        ClipboardUtils.placeFileLinks(gifFile);
    } finally {
      converter.dispose();
    }
  }

  private void stopRecording() {
    try {
      var currentCapturer = this.currentScreenCapturer.getAndSet(null);
      if (currentCapturer != null) {
        currentCapturer.stop(JapagogeConfig.getInstance().getLoops());
        this.setState(State.SAVING_RESULT);

        if (currentCapturer.hasStatistics()) {
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

                if (this.ensureOverwrite(selectedFile)) {

                  var selectedFilter = fileChooser.getFileFilter();
                  JapagogeConfig.getInstance().setTargetFolder(selectedFile.getParentFile());

                  if (selectedFilter == gifFilter) {
                    this.doVisualConversionPngToGif(
                            tempFile,
                            selectedFile,
                            JapagogeConfig.getInstance().isAccurateRgb(),
                            JapagogeConfig.getInstance().isDithering(),
                            currentCapturer.makeGlobalRgb256Palette(),
                            true,
                            null);
                  } else {
                    LOGGER.info("Just moving APNG file: " + tempFile);
                    try {
                      Files.move(tempFile.toPath(), selectedFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                      LOGGER.info("Saved result file " + selectedFile.getName() + " size " + selectedFile.length() + " bytes");
                      ClipboardUtils.placeFileLinks(selectedFile);
                    } catch (Exception ex) {
                      LOGGER.log(Level.SEVERE, "Can't move result file", ex);
                      JOptionPane.showMessageDialog(this, "Can't move result file!", "Error", JOptionPane.ERROR_MESSAGE);
                    }
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
          LOGGER.severe("No statistics, may be too quick stop");
          this.setState(State.SELECT_POSITION);
        }
      } else {
        this.setState(State.SELECT_POSITION);
      }
    } finally {
      this.resizer.setEnable(true);
    }
  }

  private File makeTargetFile(final File parentFolder, final String filter, final String extension) {
    var dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
    return new File(parentFolder, "record-" + filter.toLowerCase(Locale.ENGLISH) + '-' + dateFormat.format(new Date()) + '.' + extension);
  }

  private File makeTempRecordFile() throws IOException {
    final String tempFolderPath = JapagogeConfig.getInstance().getTempFolder();
    final File tempFolder = tempFolderPath.isBlank() ? null : new File(tempFolderPath);
    if (tempFolder != null) {
      if (!tempFolder.isDirectory()) {
        throw new IOException("Can't find temp folder: " + tempFolder);
      }
      if (!tempFolder.canRead()) {
        throw new IOException("Can't read temp folder: " + tempFolder);
      }
      if (!tempFolder.canWrite()) {
        throw new IOException("Can't write temp folder: " + tempFolder);
      }
    }
    return File.createTempFile(".japagoge-record", ".png", tempFolder);
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

    final int gapBetweenButtons = 4;

    int buttonStartX = width - this.imageSettings.getWidth(null) - this.imageClose.getWidth(null) - this.imageConvert.getWidth(null) - gapBetweenButtons * 3;
    this.areaButtonConvert.setBounds(buttonStartX, (TITLE_HEIGHT - this.imageConvert.getHeight(null)) / 2, this.imageConvert.getWidth(null), this.imageConvert.getHeight(null));
    buttonStartX += this.areaButtonConvert.width + gapBetweenButtons;
    this.areaButtonSettings.setBounds(buttonStartX, (TITLE_HEIGHT - this.imageSettings.getHeight(null)) / 2, this.imageSettings.getWidth(null), this.imageSettings.getHeight(null));
    buttonStartX += this.areaButtonSettings.width + gapBetweenButtons;
    this.areaButtonClose.setBounds(buttonStartX, (TITLE_HEIGHT - this.imageClose.getHeight(null)) / 2, this.imageClose.getWidth(null), this.imageClose.getHeight(null));
    this.areaButtonRecordStop.setBounds(BORDER_SIZE, (TITLE_HEIGHT - this.imageRecord.getHeight(null)) / 2, this.imageRecord.getWidth(null), this.imageRecord.getHeight(null));

    return result;
  }

  private Rectangle findScreeCaptureArea() {
    final Rectangle bounds = this.getGraphicsConfiguration().getBounds();

    //final Rectangle bounds = Toolkit.getDefaultToolkit().getS this.getBounds();
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

  private void updateLook() {
    var bounds = this.getBounds();
    switch (this.state.get()) {
      case RECORDING: {
        this.statisticWindow.setVisible(false);
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
        this.statisticWindow.setVisible(this.showCapturingAreaMetrics);
        this.validate();
      }
      break;
      case SAVING_RESULT: {
        this.statisticWindow.setVisible(false);
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

  private void onButtonConvert() {
    if (this.state.get() == State.SELECT_POSITION) {
      LOGGER.info("Conversion activated");
      final JFileChooser sourceFIleChooser = new JFileChooser(JapagogeConfig.getInstance().getTargetFolder());
      sourceFIleChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
      sourceFIleChooser.setMultiSelectionEnabled(false);
      sourceFIleChooser.setAcceptAllFileFilterUsed(false);
      sourceFIleChooser.addChoosableFileFilter(new FileFilter() {
        @Override
        public boolean accept(final File f) {
          if (f.isDirectory()) return true;
          final String name = f.getName().toLowerCase(Locale.ENGLISH);
          return name.endsWith(".png") || name.endsWith(".apng");
        }

        @Override
        public String getDescription() {
          return "PNG and APNG images (*.png, *.apng)";
        }
      });

      sourceFIleChooser.setDialogTitle("Select source PNG or APNG image");

      if (sourceFIleChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
        final File sourceFile = sourceFIleChooser.getSelectedFile();

        LOGGER.info("Selected source file: " + sourceFile);
        if (sourceFile.isFile()) {
          final JFileChooser targetFileChooser = new JFileChooser(JapagogeConfig.getInstance().getTargetFolder());
          targetFileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
          targetFileChooser.setMultiSelectionEnabled(false);
          targetFileChooser.setAcceptAllFileFilterUsed(false);
          targetFileChooser.setDialogTitle("Target GIF file");
          targetFileChooser.setSelectedFile(new File(extractNameWithoutExtenstion(sourceFile) + ".gif"));
          targetFileChooser.addChoosableFileFilter(new FileFilter() {
            @Override
            public boolean accept(final File f) {
              if (f.isDirectory()) return true;
              final String name = f.getName().toLowerCase(Locale.ENGLISH);
              return name.endsWith(".gif");
            }

            @Override
            public String getDescription() {
              return "GIF images (*.gif)";
            }
          });

          if (targetFileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            final File targetFile = ensureExtension(targetFileChooser.getSelectedFile(), ".gif");
            LOGGER.info("Selected target file: " + targetFile);
            if (this.ensureOverwrite(targetFile)) {
              Palette256 palette = JapagogeConfig.getInstance().getGifPaletteForRgb();
              if (palette == Palette256.AUTO) palette = Palette256.UNIVERSAL;

              LOGGER.info("Converting " + sourceFile + " to " + targetFile + ", palette " + palette.name());

              this.doVisualConversionPngToGif(sourceFile, targetFile,
                      JapagogeConfig.getInstance().isAccurateRgb(),
                      JapagogeConfig.getInstance().isDithering(),
                      palette.getPalette().orElseGet(PaletteUtils::makeGrayscaleRgb256),
                      false,
                      JapagogeConfig.getInstance().getFilter().get()
              );
            }
          }
        } else {
          JOptionPane.showMessageDialog(this, "Can't find file: " + sourceFile, "Error", JOptionPane.ERROR_MESSAGE);
        }
      }
    }


  }

  private File ensureExtension(final File file, final String extension) {
    String name = file.getName();
    if (!name.toLowerCase(Locale.ENGLISH).endsWith(extension.toLowerCase(Locale.ENGLISH))) {
      return new File(file.getParentFile(), name + extension);
    }
    return file;
  }

  private boolean ensureOverwrite(final File file) {
    boolean ok = true;
    if (file.isFile()) {
      ok = JOptionPane.showConfirmDialog(this, "File " + file.getName() + " exists, overwrite it?", "Overwrite file?", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION;
    }
    return ok;
  }

  private void onButtonSettings() {
    var data = new JapagogeConfig.JapagogeConfigData();
    var panel = new PreferencesPanel(data);
    if (JOptionPane.showConfirmDialog(this, panel, "Settings", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
      panel.fillData();
      data.save();
    }
    this.showCapturingAreaMetrics = data.isShowBoundsInfo();
    this.statisticWindow.setVisible(this.showCapturingAreaMetrics);
    repaint();
  }

  @SuppressWarnings("SameParameterValue")
  private void drawTitleText(final Graphics2D gfx, final String text) {
    final Rectangle freeArea = new Rectangle(this.areaButtonRecordStop.x + this.areaButtonRecordStop.width, 0, areaButtonConvert.x - this.areaButtonRecordStop.x - this.areaButtonRecordStop.width, TITLE_HEIGHT);

    final Font font = this.getFont();
    final FontMetrics fontMetrics = this.getFontMetrics(font);
    final Rectangle2D textBounds = fontMetrics.getStringBounds(text, gfx);

    final Shape oldClip = gfx.getClip();
    gfx.setFont(font);
    gfx.setClip(freeArea);
    gfx.setColor(Color.BLACK);
    gfx.drawString(text, freeArea.x + (freeArea.width - (int) textBounds.getWidth()) / 2, freeArea.y + (freeArea.height + (int) textBounds.getY()) / 2 - (int) textBounds.getY() - fontMetrics.getLeading());
    gfx.setClip(oldClip);
  }

  @Override
  public void paint(final Graphics g) {
    final Graphics2D gfx = (Graphics2D) g;
    var bounds = this.getBounds();
    gfx.setColor(this.getBackground());
    gfx.fillRect(0, 0, bounds.width, bounds.height);
    switch (this.state.get()) {
      case SELECT_POSITION: {
        this.drawTitleText(gfx, "Japagoge " + VERSION);
        gfx.drawImage(this.imageConvert, this.areaButtonConvert.x, this.areaButtonConvert.y, null);
        gfx.drawImage(this.imageSettings, this.areaButtonSettings.x, this.areaButtonSettings.y, null);
        gfx.drawImage(this.imageClose, this.areaButtonClose.x, this.areaButtonClose.y, null);
        gfx.drawImage(this.imageRecord, this.areaButtonRecordStop.x, this.areaButtonRecordStop.y, null);
      }
      break;
      case RECORDING: {
        gfx.setColor(Color.BLACK);
        gfx.drawRect(0, 0, bounds.width - 1, bounds.height - 1);
        if (this.drawStopButton)
          gfx.drawImage(this.imageStop, this.areaButtonRecordStop.x, this.areaButtonRecordStop.y, null);
      }
      break;
      case SAVING_RESULT: {
        // draw nothing
      }
      break;
      default:
        throw new Error("Unexpected state");
    }
  }

  private enum State {
    SELECT_POSITION,
    RECORDING,
    SAVING_RESULT
  }

}
