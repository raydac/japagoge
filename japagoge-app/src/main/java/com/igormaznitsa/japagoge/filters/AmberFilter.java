package com.igormaznitsa.japagoge.filters;

import java.util.Optional;

public class AmberFilter extends GrayscaleFilter {

  private static final int LEVEL_BASE_R = 0x18;
  private static final float LEVEL_STEP_R = ((float) (0xFF - LEVEL_BASE_R)) / 256.0f;
  private static final int LEVEL_BASE_G = 0x18;
  private static final float LEVEL_STEP_G = ((float) (0xCC - LEVEL_BASE_G)) / 256.0f;
  private static final int LEVEL_BASE_B = 0x18;
  private static final float LEVEL_STEP_B = ((float) (0x38 - LEVEL_BASE_G)) / 256.0f;

  private final int[] palette;

  public AmberFilter() {
    this.palette = new int[256];
    for (int i = 0; i < this.palette.length; i++) {
      final int r = LEVEL_BASE_R + Math.round(LEVEL_STEP_R * i);
      final int g = LEVEL_BASE_G + Math.round(LEVEL_STEP_G * i);
      final int b = LEVEL_BASE_B + Math.round(LEVEL_STEP_B * i);
      this.palette[i] = (r << 16) | (g << 8) | b;
    }
  }

  @Override
  public Optional<int[]> getPalette() {
    return Optional.of(this.palette);
  }

}
