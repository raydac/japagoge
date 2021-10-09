package com.igormaznitsa.japagoge.filters;

import java.util.Optional;

public interface ColorFilter {
  boolean isMonochrome();

  Optional<int[]> getPalette();

  int filterRgb(int rgb);

  default byte[] filterRgbPalette(final byte[] rgbPalette) {
    final int items = rgbPalette.length / 3;
    for (int i = 0; i < items; i++) {
      final int offset = i * 3;

      final int r = rgbPalette[offset] & 0xFF;
      final int g = rgbPalette[offset + 1] & 0xFF;
      final int b = rgbPalette[offset + 2] & 0xFF;

      final int filteredRgb = this.filterRgb((r << 16) | (g << 8) | b);

      rgbPalette[offset] = (byte) (filteredRgb >> 16);
      rgbPalette[offset + 1] = (byte) (filteredRgb >> 8);
      rgbPalette[offset + 2] = (byte) filteredRgb;
    }
    return rgbPalette;
  }
}
