package com.igormaznitsa.japagoge.filters;

public enum RgbPixelFilter {
  RGB(new NoneFilter()),
  GRAYSCALE(new GrayscaleFilter()),
  AMBER(new AmberFilter()),
  GREEN(new GreenFilter()),
  BLACK_WHITE(new BlackWhiteFilter()),
  OLD_PHONE_BW_LCD(new OldPhoneBwLcdFilter());

  private final ColorFilter filter;

  RgbPixelFilter(final ColorFilter filter) {
    this.filter = filter;
  }

  public ColorFilter get() {
    return this.filter;
  }
}
