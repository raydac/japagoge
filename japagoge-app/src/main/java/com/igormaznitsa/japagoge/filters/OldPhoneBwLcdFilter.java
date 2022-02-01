package com.igormaznitsa.japagoge.filters;

import com.igormaznitsa.japagoge.utils.PaletteUtils;

import java.util.Optional;
import java.util.stream.IntStream;

public class OldPhoneBwLcdFilter implements ColorFilter {

  private static final int COLOR_DARK = 0x3A3F54;
  private static final int COLOR_LIGHT = 0x8F9F8F;
  private final int[] palette;
  int minY;
  int maxY;
  int avgY;

  public OldPhoneBwLcdFilter() {
    this.palette = IntStream.range(0, 256).map(y -> y < 0x80 ? COLOR_DARK : COLOR_LIGHT).toArray();
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
  public void reset() {
    this.minY = 0xFF;
    this.maxY = 0;
    this.avgY = 0;
  }

  @Override
  public int getPasses() {
    return 2;
  }

  @Override
  public boolean isPassImageUpdate(int pass) {
    return pass > 0;
  }

  @Override
  public int filterRgbPalette(final int index, final int rgb) {
    final int r = (rgb >> 16) & 0xFF;
    final int g = (rgb >> 8) & 0xFF;
    final int b = rgb & 0xFF;

    return PaletteUtils.toY(r, g, b) < 0x80 ? COLOR_DARK : COLOR_LIGHT;
  }

  @Override
  public int filterRgb(final int rgb, final int pass) {
    final int r = (rgb >> 16) & 0xFF;
    final int g = (rgb >> 8) & 0xFF;
    final int b = rgb & 0xFF;

    final int y = PaletteUtils.toY(r, g, b);

    if (pass == 0) {
      if (y < this.minY) {
        this.minY = y;
      }
      if (y > this.maxY) {
        this.maxY = y;
      }
      this.avgY = (this.maxY - this.minY) / 2;
      return rgb;
    } else {
      return y < this.avgY ? 0 : 0xFF;
    }
  }
}
