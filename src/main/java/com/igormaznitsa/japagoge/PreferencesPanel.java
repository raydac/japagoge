package com.igormaznitsa.japagoge;

import com.igormaznitsa.japagoge.filters.RgbPixelFilter;
import com.igormaznitsa.japagoge.utils.Palette256;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PreferencesPanel extends JPanel {

  private static final Logger LOGGER = Logger.getLogger("PreferencesPanel");

  private final JapagogeConfig.JapagogeConfigData data;

  private final JTextField textTempFolder;
  private final JCheckBox checkBoxPointer;
  private final JCheckBox checkBoxAccurateRgb;
  private final JComboBox<RgbPixelFilter> comboBoxFilter;
  private final JComboBox<Palette256> comboBoxPaletteForGifRgb;
  private final JSpinner spinnerFrameDelay;
  private final JSpinner spinnerCaptureDelay;
  private final JSpinner spinnerLoops;

  public PreferencesPanel(final JapagogeConfig.JapagogeConfigData data) {
    super(new GridBagLayout());
    this.data = Objects.requireNonNull(data);

    this.textTempFolder = new JTextField(data.getTenpFolder());
    this.textTempFolder.setToolTipText("Folder to keep intermediate results of recording, default if empty");

    this.checkBoxPointer = new JCheckBox(null, null, data.isPointer());
    this.checkBoxPointer.setToolTipText("If ON then pointer will be presented in result");

    this.checkBoxAccurateRgb = new JCheckBox(null, null, data.isAccurateRgb());
    this.checkBoxAccurateRgb.setToolTipText("If ON then precise palette color in use during GIF conversion, but slower");

    this.comboBoxFilter = new JComboBox<>(RgbPixelFilter.values());
    this.comboBoxFilter.setToolTipText("Select color filter for recording");
    this.comboBoxFilter.setSelectedItem(data.getFilter());

    this.comboBoxPaletteForGifRgb = new JComboBox<>(Palette256.values());
    this.comboBoxPaletteForGifRgb.setToolTipText("256 color palette for GIF conversion");
    this.comboBoxPaletteForGifRgb.setSelectedItem(data.getGifPaletteForRgb());

    this.spinnerCaptureDelay = new JSpinner(new SpinnerNumberModel(data.getCaptureDelay(), 20, Short.MAX_VALUE, 1));
    this.spinnerCaptureDelay.setToolTipText("Delay in milliseconds between screen captures");

    this.spinnerFrameDelay = new JSpinner(new SpinnerNumberModel(data.getFrameDelay(), 20, Short.MAX_VALUE, 1));
    this.spinnerFrameDelay.setToolTipText("Delay in milliseconds between result frames");

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

    this.add(new JLabel("Show pointer: "), gblLeft);
    this.add(this.checkBoxPointer, gblRight);

    this.add(new JLabel("Color filter: "), gblLeft);
    this.add(this.comboBoxFilter, gblRight);

    this.add(new JLabel("Palette for RGB to GIF: "), gblLeft);
    this.add(this.comboBoxPaletteForGifRgb, gblRight);

    this.add(new JLabel("Accurate RGB (slower GIF conversion): "), gblLeft);
    this.add(this.checkBoxAccurateRgb, gblRight);

    gblLeft.gridwidth = 2;
    gblLeft.anchor = GridBagConstraints.CENTER;

    var linkLabel = new JLabel("<html><a href=\"#\">https://github.com/raydac/japagoge</a></html>");
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
    var desktop = Desktop.isDesktopSupported() ? Desktop.getDesktop() : null;
    if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE)) {
      try {
        desktop.browse(uri);
      } catch (Exception e) {
        LOGGER.log(Level.SEVERE, "Error during link browsing activation", e);
      }
    }
  }

  public void fillData() {
    this.data.setTenpFolder(this.textTempFolder.getText());
    this.data.setPointer(this.checkBoxPointer.isSelected());
    this.data.setAccurateRgb(this.checkBoxAccurateRgb.isSelected());
    this.data.setFilter((RgbPixelFilter) this.comboBoxFilter.getSelectedItem());
    this.data.setGifPaletteForRgb((Palette256) this.comboBoxPaletteForGifRgb.getSelectedItem());
    this.data.setLoops(((Number) this.spinnerLoops.getValue()).intValue());
    this.data.setFrameDelay(((Number) this.spinnerFrameDelay.getValue()).intValue());
    this.data.setCaptureDelay(((Number) this.spinnerCaptureDelay.getValue()).intValue());
  }

}
