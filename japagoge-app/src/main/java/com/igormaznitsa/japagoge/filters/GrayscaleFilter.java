package com.igormaznitsa.japagoge.filters;

import com.igormaznitsa.japagoge.utils.PaletteUtils;

import java.util.Optional;

public class GrayscaleFilter implements ColorFilter {

  private final int[] palette;

  public GrayscaleFilter() {
    this.palette = PaletteUtils.makeGrayscaleRgb256();
  }

  @Override
  public boolean isMonochrome() {
    return true;
  }

  @Override
  public Optional<int[]> getPalette() {
    return Optional.of(this.palette);
  }

  @Override
  public int filterRgb(final int rgb, final int pass) {
    final int r = (rgb >> 16) & 0xFF;
    final int g = (rgb >> 8) & 0xFF;
    final int b = rgb & 0xFF;

    final int y = PaletteUtils.toY(r, g, b);

    return (y << 16) | (y << 8) | y;
  }
}
