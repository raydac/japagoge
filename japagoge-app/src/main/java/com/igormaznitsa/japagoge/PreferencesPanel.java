package com.igormaznitsa.japagoge;

import static java.util.Objects.requireNonNull;

import com.igormaznitsa.japagoge.filters.ColorFilter;
import com.igormaznitsa.japagoge.filters.RgbPixelFilter;
import com.igormaznitsa.japagoge.utils.Palette256;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

public class PreferencesPanel extends JPanel {

  private static final Logger LOGGER = Logger.getLogger("PreferencesPanel");

  private final JapagogeConfig.JapagogeConfigData data;

  private final JTextField textTempFolder;
  private final JCheckBox checkBoxPointer;
  private final JCheckBox checkBoxShowBoundsInfo;
  private final JCheckBox checkBoxForceWholeFrame;
  private final JCheckBox checkBoxForceJavaRonotGrabber;
  private final JCheckBox checkBoxAccurateRgb;
  private final JCheckBox checkBoxDithering;
  private final JComboBox<RgbPixelFilter> comboBoxFilter;
  private final JComboBox<Palette256> comboBoxPaletteForGifRgb;
  private final JSpinner spinnerFrameDelay;
  private final JSpinner spinnerCaptureDelay;
  private final JSpinner spinnerLoops;

  public PreferencesPanel(final JapagogeConfig.JapagogeConfigData data) {
    super(new GridBagLayout());
    this.data = requireNonNull(data);

    this.textTempFolder = new JTextField(data.getTenpFolder());
    this.textTempFolder.setToolTipText("Folder to keep intermediate results of recording, default if empty");

    this.checkBoxPointer = new JCheckBox(null, null, data.isPointer());
    this.checkBoxPointer.setToolTipText("Record mouse pointer");

    this.checkBoxAccurateRgb = new JCheckBox(null, null, data.isAccurateRgb());
    this.checkBoxAccurateRgb.setToolTipText("Find better colors in GIF palette for RGB, slow");

    this.checkBoxDithering = new JCheckBox(null, null, data.isDithering());
    this.checkBoxDithering.setToolTipText("Use dithering in result GIF, increasing result size");

    this.checkBoxShowBoundsInfo = new JCheckBox(null, null, data.isShowBoundsInfo());
    this.checkBoxShowBoundsInfo.setToolTipText("Show coordinates of capturing area");

    this.checkBoxForceWholeFrame = new JCheckBox(null, null, data.isForceWholeFrame());
    this.checkBoxForceWholeFrame.setToolTipText(
        "Disable size optimization, helps for quality of dithering GIF");

    this.checkBoxForceJavaRonotGrabber = new JCheckBox(null, null, data.isForceJavaRobotGrabber());
    this.checkBoxForceJavaRonotGrabber.setToolTipText("Force use of Java robot screen grabber");

    this.comboBoxFilter = new JComboBox<>(RgbPixelFilter.values());
    this.comboBoxFilter.setToolTipText("Select color filter for recording");
    this.comboBoxFilter.setSelectedItem(data.getFilter());
    this.comboBoxFilter.addActionListener(e ->
        this.onFilterChange(
            ((RgbPixelFilter) requireNonNull(this.comboBoxFilter.getSelectedItem())).get()));

    this.comboBoxPaletteForGifRgb = new JComboBox<>(Palette256.values());
    this.comboBoxPaletteForGifRgb.setToolTipText("Pre-defined palette for GIF conversion");
    this.comboBoxPaletteForGifRgb.setSelectedItem(data.getGifPaletteForRgb());

    this.spinnerCaptureDelay = new JSpinner(new SpinnerNumberModel(data.getCaptureDelay(), 20, Short.MAX_VALUE, 1));
    this.spinnerCaptureDelay.setToolTipText("Delay for capturing, milliseconds");

    this.spinnerFrameDelay = new JSpinner(new SpinnerNumberModel(data.getFrameDelay(), 20, Short.MAX_VALUE, 1));
    this.spinnerFrameDelay.setToolTipText("Delay for result frames, milliseconds");

    this.spinnerCaptureDelay.addChangeListener(e -> spinnerFrameDelay.setValue(spinnerCaptureDelay.getValue()));

    this.spinnerLoops = new JSpinner(new SpinnerNumberModel(data.getLoops(), 0, 1000000, 1));
    this.spinnerLoops.setToolTipText("Number of repeats, zero means infinity loops");

    final GridBagConstraints gblLeft = new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0f, 1.0f, GridBagConstraints.EAST, GridBagConstraints.NONE, new Insets(2, 0, 2, 0), 0, 0);
    final GridBagConstraints gblRight = new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 1.0f, 1.0f, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(2, 0, 2, 0), 0, 0);

    this.add(new JLabel("Temp folder: "), gblLeft);
    this.add(this.textTempFolder, gblRight);

    this.add(new JLabel("Capture delay (ms): "), gblLeft);
    this.add(this.spinnerCaptureDelay, gblRight);

    this.add(new JLabel("Frame delay (ms): "), gblLeft);
    this.add(this.spinnerFrameDelay, gblRight);

    this.add(new JLabel("Loops (0 infinity): "), gblLeft);
    this.add(this.spinnerLoops, gblRight);

    this.add(new JLabel("Show capturing area metrics: "), gblLeft);
    this.add(this.checkBoxShowBoundsInfo, gblRight);

    this.add(new JLabel("Grab mouse pointer: "), gblLeft);
    this.add(this.checkBoxPointer, gblRight);

    this.add(new JLabel("Color filter: "), gblLeft);
    this.add(this.comboBoxFilter, gblRight);

    this.add(new JLabel("Palette for RGB to GIF: "), gblLeft);
    this.add(this.comboBoxPaletteForGifRgb, gblRight);

    this.add(new JLabel("Better RGB colors in GIF: "), gblLeft);
    this.add(this.checkBoxAccurateRgb, gblRight);

    this.add(new JLabel("Dithering GIF: "), gblLeft);
    this.add(this.checkBoxDithering, gblRight);

    this.add(new JLabel("Force whole frame: "), gblLeft);
    this.add(this.checkBoxForceWholeFrame, gblRight);

    this.add(new JLabel("Force Java robot grabber: "), gblLeft);
    this.add(this.checkBoxForceJavaRonotGrabber, gblRight);

    gblLeft.gridwidth = 2;
    gblLeft.anchor = GridBagConstraints.CENTER;

    JLabel linkLabel =
        new JLabel("<html><a href=\"#\">https://github.com/raydac/japagoge</a></html>");
    linkLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    linkLabel.setToolTipText("The project home page where you can find some help and new versions");
    linkLabel.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(final MouseEvent e) {
        openWebpage(URI.create("https://github.com/raydac/japagoge"));
      }
    });

    this.add(linkLabel, gblLeft);
  }

  private static void openWebpage(final URI uri) {
    Desktop desktop = Desktop.isDesktopSupported() ? Desktop.getDesktop() : null;
    if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE)) {
      try {
        desktop.browse(uri);
      } catch (Exception e) {
        LOGGER.log(Level.SEVERE, "Error during link browsing activation", e);
      }
    }
  }

  private void onFilterChange(final ColorFilter colorFilter) {
    if (!this.checkBoxForceWholeFrame.isSelected()) {
      this.checkBoxForceWholeFrame.setSelected(colorFilter.isWholeFrameRequired());
    }
  }

  public void fillData() {
    this.data.setTenpFolder(this.textTempFolder.getText());
    this.data.setPointer(this.checkBoxPointer.isSelected());
    this.data.setShowBoundsInfo(this.checkBoxShowBoundsInfo.isSelected());
    this.data.setForceWholeFrame(this.checkBoxForceWholeFrame.isSelected());
    this.data.setForceJavaRobotGrabber(this.checkBoxForceJavaRonotGrabber.isSelected());
    this.data.setAccurateRgb(this.checkBoxAccurateRgb.isSelected());
    this.data.setDithering(this.checkBoxDithering.isSelected());
    this.data.setFilter((RgbPixelFilter) this.comboBoxFilter.getSelectedItem());
    this.data.setGifPaletteForRgb((Palette256) this.comboBoxPaletteForGifRgb.getSelectedItem());
    this.data.setLoops(((Number) this.spinnerLoops.getValue()).intValue());
    this.data.setFrameDelay(((Number) this.spinnerFrameDelay.getValue()).intValue());
    this.data.setCaptureDelay(((Number) this.spinnerCaptureDelay.getValue()).intValue());
  }

}
