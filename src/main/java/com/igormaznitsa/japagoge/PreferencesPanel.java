package com.igormaznitsa.japagoge;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

public class PreferencesPanel extends JPanel {
  private final JapagogeConfig.JapagogeConfigData data;

  private final JCheckBox checkBoxPointer;
  private final JSpinner spinnerFrameDelay;
  private final JSpinner spinnerLoops;

  public PreferencesPanel(final JapagogeConfig.JapagogeConfigData data) {
    super(new GridBagLayout());
    this.data = Objects.requireNonNull(data);

    this.checkBoxPointer = new JCheckBox(null, null, data.isPointer());
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
  }

  public void fillData() {
    this.data.setPointer(this.checkBoxPointer.isSelected());
    this.data.setLoops(((Number) this.spinnerLoops.getValue()).intValue());
    this.data.setFrameDelay(((Number) this.spinnerFrameDelay.getValue()).intValue());
  }

}
