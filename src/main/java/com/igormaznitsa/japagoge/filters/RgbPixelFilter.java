package com.igormaznitsa.japagoge.filters;

public enum RgbPixelFilter {
  RGB(new NoneFilter()),
  GRAYSCALE(new GrayscaleFilter()),
  AMBER(new AmberFilter()),
  GREEN(new GreenFilter());
  private final ColorFilter filter;

  RgbPixelFilter(final ColorFilter filter) {
    this.filter = filter;
  }

  public ColorFilter get() {
    return this.filter;
  }
}
