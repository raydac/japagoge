package com.igormaznitsa.japagoge.filters;

import com.igormaznitsa.japagoge.utils.PaletteUtils;

import java.util.Optional;

public class GrayscaleFilter implements ColorFilter {
  @Override
  public boolean isMonochrome() {
    return true;
  }

  @Override
  public Optional<int[]> getPalette() {
    return Optional.empty();
  }

  @Override
  public int doFiltering(final int rgb) {
    final int r = (rgb >> 16) & 0xFF;
    final int g = (rgb >> 8) & 0xFF;
    final int b = rgb & 0xFF;
    return PaletteUtils.toY(r, g, b);
  }
}
