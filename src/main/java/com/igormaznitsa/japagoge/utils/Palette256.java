package com.igormaznitsa.japagoge.utils;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.Scanner;

public enum Palette256 {
  AUTO(null),
  DUEL("duel"),
  ATARI("atari"),
  AURORA("aurora"),
  RGBM("rgbm"),
  UZEBOX("uzebox"),
  WIN95("win95");

  private final int[] rgbPalette;

  Palette256(final String paletteName) {
    if (paletteName == null) {
      this.rgbPalette = null;
    } else {
      final int[] rgb = new int[256];
      try (final Scanner in = new Scanner(Objects.requireNonNull(Palette256.class.getResourceAsStream("/palettes/" + paletteName + ".hex")), StandardCharsets.UTF_8)) {
        int index = 0;
        while (in.hasNext()) {
          final String next = in.next().trim();
          if (!next.isEmpty()) {
            rgb[index++] = Integer.parseInt(next, 16);
          }
        }
        if (index != 256) throw new Error("No 256 colors in " + paletteName);
      }
      this.rgbPalette = rgb;
    }
  }

  public Optional<int[]> getPalette() {
    return Optional.ofNullable(this.rgbPalette == null ? null : this.rgbPalette.clone());
  }
}
