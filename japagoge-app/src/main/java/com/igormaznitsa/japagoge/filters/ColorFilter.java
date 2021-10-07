package com.igormaznitsa.japagoge.filters;

import java.util.Optional;

public interface ColorFilter {
  boolean isMonochrome();

  Optional<int[]> getPalette();

  int doFiltering(int rgb);
}
