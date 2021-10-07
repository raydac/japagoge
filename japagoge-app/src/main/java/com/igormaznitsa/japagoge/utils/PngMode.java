package com.igormaznitsa.japagoge.utils;

public enum PngMode {
  MODE_GRAYSCALE_1,
  MODE_GRAYSCALE_2,
  MODE_GRAYSCALE_4,
  MODE_GRAYSCALE_8,
  MODE_GRAYSCALE_16,

  MODE_RGB_8,
  MODE_RGB_16,

  MODE_INDEX_1,
  MODE_INDEX_2,
  MODE_INDEX_4,
  MODE_INDEX_8,

  MODE_GRAYSCALE_ALPHA_8,
  MODE_GRAYSCALE_ALPHA_16,

  MODE_RGB_ALPHA_8,
  MODE_RGB_ALPHA_16;

  public static PngMode find(final int colorType, final int bitDepth) {
    switch (colorType) {
      case 0: {
        switch (bitDepth) {
          case 1:
            return MODE_GRAYSCALE_1;
          case 2:
            return MODE_GRAYSCALE_2;
          case 4:
            return MODE_GRAYSCALE_4;
          case 8:
            return MODE_GRAYSCALE_8;
          case 16:
            return MODE_GRAYSCALE_16;
          default:
            throw new IllegalArgumentException();
        }
      }
      case 2: {
        switch (bitDepth) {
          case 8:
            return MODE_RGB_8;
          case 16:
            return MODE_RGB_16;
          default:
            throw new IllegalArgumentException();
        }
      }
      case 3: {
        switch (bitDepth) {
          case 1:
            return MODE_INDEX_1;
          case 2:
            return MODE_INDEX_2;
          case 4:
            return MODE_INDEX_4;
          case 8:
            return MODE_INDEX_8;
          default:
            throw new IllegalArgumentException();
        }
      }
      case 4: {
        switch (bitDepth) {
          case 8:
            return MODE_GRAYSCALE_ALPHA_8;
          case 16:
            return MODE_GRAYSCALE_ALPHA_16;
          default:
            throw new IllegalArgumentException();
        }
      }
      case 6: {
        switch (bitDepth) {
          case 8:
            return MODE_RGB_ALPHA_8;
          case 16:
            return MODE_RGB_ALPHA_16;
          default:
            throw new IllegalArgumentException();
        }
      }
      default:
        throw new IllegalArgumentException();
    }
  }

  public byte[] asRgb(final byte[] imageData) {
    return imageData;
  }
}
