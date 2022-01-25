package com.igormaznitsa.japagoge.utils;

import java.util.stream.IntStream;

@SuppressWarnings("unused")
public final class PaletteUtils {

  private PaletteUtils() {
  }

  public static byte[] splitRgb(final int[] rgb) {
    final byte[] result = new byte[rgb.length * 3];
    for (int i = 0; i < rgb.length; i++) {
      final int rgbColor = rgb[i];
      int outIndex = i * 3;
      result[outIndex++] = (byte) (rgbColor >> 16);
      result[outIndex++] = (byte) (rgbColor >> 8);
      result[outIndex] = (byte) rgbColor;
    }
    return result;
  }

  public static int[] makeGrayscaleRgb256() {
    return IntStream.range(0, 256).map(y -> (y << 16) | (y << 8) | y).toArray();
  }

  public static int[] makeBlackWhiteRgb256() {
    return IntStream.range(0, 256).map(y -> y < 128 ? 0 : 0xFFFFFF).toArray();
  }

  public static int findClosestIndex(final int r, final int g, final int b, final byte[] rgbPalette) {
    final int paletteItems = rgbPalette.length / 3;
    float distance = Float.MAX_VALUE;
    int foundIndex = 0;
    for (int i = 0; i < paletteItems; i++) {
      int paletteOffset = i * 3;

      final int paletteR = rgbPalette[paletteOffset++] & 0xFF;
      final int paletteG = rgbPalette[paletteOffset++] & 0xFF;
      final int paletteB = rgbPalette[paletteOffset] & 0xFF;

      final int deltaR = r - paletteR;
      final int deltaG = g - paletteG;
      final int deltaB = b - paletteB;

      final float newDistance = deltaR * deltaR + deltaG * deltaG + deltaB * deltaB;
      if (newDistance <= distance) {
        distance = newDistance;
        foundIndex = i;
      }
    }
    return foundIndex;
  }

  public static int findClosestIndex(final int r, final int g, final int b, final int y, final float h, final byte[] rgbPalette) {
    float distance = Float.MAX_VALUE;
    int foundIndex = 0;
    for (int i = 0; i < rgbPalette.length; ) {
      final int currentIndex = i / 3;
      final int pr = rgbPalette[i++] & 0xFF;
      final int pg = rgbPalette[i++] & 0xFF;
      final int pb = rgbPalette[i++] & 0xFF;

      final int dr = r - pr;
      final int dg = g - pg;
      final int db = b - pb;

      final int dy = y - toY(pr, pg, pb);
      final float dh = h - toHue(pr, pg, pb);

      final float newDistance = dr * dr + dg * dg + db * db + dy * dy + dh * dh;

      if (newDistance < distance) {
        distance = newDistance;
        foundIndex = currentIndex;
      }
    }
    return foundIndex;
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
