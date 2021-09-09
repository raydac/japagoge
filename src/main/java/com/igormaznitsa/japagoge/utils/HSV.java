package com.igormaznitsa.japagoge.utils;

public final class HSV {
  public double hue;
  public double saturation;
  public double value;

  HSV(double h, double s, double v) {
    this.hue = h;
    this.saturation = s;
    this.value = v;
  }

  public double calcDistance(final HSV other) {
    final double dh = this.hue - other.hue;
    final double ds = this.saturation - other.saturation;
    final double dv = this.value - other.value;
    return Math.sqrt(dh * dh + ds * ds + dv * dv);
  }
}
