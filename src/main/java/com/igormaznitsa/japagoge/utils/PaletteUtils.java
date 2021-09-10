package com.igormaznitsa.japagoge.utils;

import java.util.stream.IntStream;

@SuppressWarnings("unused")
public final class PaletteUtils {

  private PaletteUtils() {
  }

  public static int[] makeGrayscaleRgb256() {
    return IntStream.range(0, 256).map(y -> (y << 16) | (y << 8) << 8).toArray();
  }

  public static float calcAccurateRgbDistance(final int r1, final int g1, final int b1, final int y1, final float h1, final int r2, final int g2, final int b2) {
    final int dr = r1 - r2;
    final int dg = g1 - g2;
    final int db = b1 - b2;
    final int dy = y1 - toY(r2, g2, b2);
    final float dh = h1 - toHue(r2, g2, b2);
    return dr * dr + dg * dg + db * db + dy * dy + dh * dh;
  }

  public static float toHue(final int r, final int g, final int b) {
    final float dr = r / 255.0f;
    final float dg = g / 255.0f;
    final float db = b / 255.0f;

    final float cMax = Math.max(dr, Math.max(dg, db));
    final float cMin = Math.min(dr, Math.min(dg, db));
    final float diff = cMax - cMin;
    float h = -1;

    if (cMax == cMin) {
      h = 0;
    } else if (cMax == dr) {
      h = (60 * ((dg - db) / diff) + 360) % 360;
    } else if (cMax == dg) {
      h = (60 * ((db - dr) / diff) + 120) % 360;
    } else if (cMax == db) {
      h = (60 * ((dr - dg) / diff) + 240) % 360;
    }
    return h;
  }

  public static int toY(final int r, final int g, final int b) {
    return Math.min(255, Math.round(r * 0.299f + g * 0.587f + b * 0.114f));
  }

  public static float toU(final int r, final int g, final int b) {
    return -0.148f * r - 0.291f * g + 0.439f * b + 128.0f;
  }

  public static float toV(final int r, final int g, final int b) {
    return 0.439f * r - 0.368f * g - 0.071f * b + 128.0f;
  }

  public static HSV toHSV(final int r, final int g, final int b) {
    double dr = r / 255.0d;
    double dg = g / 255.0d;
    double db = b / 255.0d;

    double cmax = Math.max(dr, Math.max(dg, db)); // maximum of r, g, b
    double cmin = Math.min(dr, Math.min(dg, db)); // minimum of r, g, b
    double diff = cmax - cmin; // diff of cmax and cmin.
    double h = -1, s;

    if (cmax == cmin) {
      h = 0;
    } else if (cmax == dr) {
      h = (60 * ((dg - db) / diff) + 360) % 360;
    } else if (cmax == dg) {
      h = (60 * ((db - dr) / diff) + 120) % 360;
    } else if (cmax == db) {
      h = (60 * ((dr - dg) / diff) + 240) % 360;
    }
    if (cmax == 0) {
      s = 0;
    } else {
      s = (diff / cmax) * 100;
    }
    double v = cmax * 100;

    return new HSV(h, s, v);
  }
}
