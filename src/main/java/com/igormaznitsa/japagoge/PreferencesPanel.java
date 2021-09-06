package com.igormaznitsa.japagoge;

import com.igormaznitsa.japagoge.filters.RgbPixelFilter;

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

  private final JCheckBox checkBoxPointer;
  private final JComboBox<RgbPixelFilter> comboBoxFilter;
  private final JSpinner spinnerFrameDelay;
  private final JSpinner spinnerLoops;

  public PreferencesPanel(final JapagogeConfig.JapagogeConfigData data) {
    super(new GridBagLayout());
    this.data = Objects.requireNonNull(data);

    this.checkBoxPointer = new JCheckBox(null, null, data.isPointer());
    this.comboBoxFilter = new JComboBox<>(RgbPixelFilter.values());
    this.comboBoxFilter.setSelectedItem(data.getFilter());

    this.spinnerFrameDelay = new JSpinner(new SpinnerNumberModel(data.getFrameDelay(), 20, 10000, 1));
    this.spinnerLoops = new JSpinner(new SpinnerNumberModel(data.getLoops(), 0, 1000000, 1));

    final GridBagConstraints gblLeft = new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0f, 1.0f, GridBagConstraints.EAST, GridBagConstraints.NONE, new Insets(2, 0, 2, 0), 0, 0);
    final GridBagConstraints gblRight = new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 1.0f, 1.0f, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(2, 0, 2, 0), 0, 0);

    this.add(new JLabel("Frame delay (ms): "), gblLeft);
    this.add(this.spinnerFrameDelay, gblRight);

    this.add(new JLabel("Loops (0 infinity): "), gblLeft);
    this.add(this.spinnerLoops, gblRight);

    this.add(new JLabel("Show pointer: "), gblLeft);
    this.add(this.checkBoxPointer, gblRight);

    this.add(new JLabel("Color filter: "), gblLeft);
    this.add(this.comboBoxFilter, gblRight);

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
    this.data.setPointer(this.checkBoxPointer.isSelected());
    this.data.setFilter((RgbPixelFilter) this.comboBoxFilter.getSelectedItem());
    this.data.setLoops(((Number) this.spinnerLoops.getValue()).intValue());
    this.data.setFrameDelay(((Number) this.spinnerFrameDelay.getValue()).intValue());
  }

}
