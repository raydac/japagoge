package com.igormaznitsa.japagoge.filters;

import java.util.Optional;

public final class NoneFilter implements ColorFilter {
  @Override
  public boolean isMonochrome() {
    return false;
  }

  @Override
  public Optional<int[]> getPalette() {
    return Optional.empty();
  }

  @Override
  public int filterRgb(int rgb, int pass) {
    return rgb;
  }
}
