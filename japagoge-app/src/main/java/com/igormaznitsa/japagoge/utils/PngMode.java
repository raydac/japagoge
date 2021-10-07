package com.igormaznitsa.japagoge.utils;

public enum PngMode {
  MODE_GRAYSCALE_1(1, 1, (samples, rgb, palette, rgbOffset) -> {
    final byte level = (samples & 1L) == 0L ? 0 : (byte) 0xFF;
    rgb[rgbOffset++] = level;
    rgb[rgbOffset++] = level;
    rgb[rgbOffset] = level;
  }),
  MODE_GRAYSCALE_2(1, 2, (samples, rgb, palette, rgbOffset) -> {
    final byte level = (byte) (Math.min(0xFF, (0x100 / 4) * (samples & 3L) * 85));
    rgb[rgbOffset++] = level;
    rgb[rgbOffset++] = level;
    rgb[rgbOffset] = level;
  }),
  MODE_GRAYSCALE_4(1, 4, (samples, rgb, palette, rgbOffset) -> {
    final byte level = (byte) (Math.min(0xFF, (0x100 / 16) * (samples & 0x0FL) * 85));
    rgb[rgbOffset++] = level;
    rgb[rgbOffset++] = level;
    rgb[rgbOffset] = level;
  }),
  MODE_GRAYSCALE_8(1, 8, (samples, rgb, palette, rgbOffset) -> {
    final byte level = (byte) (samples & 0xFFL);
    rgb[rgbOffset++] = level;
    rgb[rgbOffset++] = level;
    rgb[rgbOffset] = level;
  }),
  MODE_GRAYSCALE_16(1, 16, (samples, rgb, palette, rgbOffset) -> {
    final byte level = (byte) ((samples >> 8) & 0xFFL);
    rgb[rgbOffset++] = level;
    rgb[rgbOffset++] = level;
    rgb[rgbOffset] = level;
  }),

  MODE_RGB_8(3, 8, (samples, rgb, palette, rgbOffset) -> {
    rgb[rgbOffset++] = (byte) (samples >> 16);
    rgb[rgbOffset++] = (byte) (samples >> 8);
    rgb[rgbOffset] = (byte) samples;
  }),
  MODE_RGB_16(3, 16, (samples, rgb, palette, rgbOffset) -> {
    rgb[rgbOffset++] = (byte) (samples >> 40);
    rgb[rgbOffset++] = (byte) (samples >> 24);
    rgb[rgbOffset] = (byte) (samples >> 8);
  }),

  MODE_INDEX_1(1, 1, (samples, rgb, palette, rgbOffset) -> {
    int paletteIndex = ((int) samples & 1) * 3;
    rgb[rgbOffset++] = palette[paletteIndex++];
    rgb[rgbOffset++] = palette[paletteIndex++];
    rgb[rgbOffset] = palette[paletteIndex];
  }),
  MODE_INDEX_2(1, 2, (samples, rgb, palette, rgbOffset) -> {
    int paletteIndex = ((int) samples & 0x03) * 3;
    rgb[rgbOffset++] = palette[paletteIndex++];
    rgb[rgbOffset++] = palette[paletteIndex++];
    rgb[rgbOffset] = palette[paletteIndex];
  }),
  MODE_INDEX_4(1, 4, (samples, rgb, palette, rgbOffset) -> {
    int paletteIndex = ((int) samples & 0x0F) * 3;
    rgb[rgbOffset++] = palette[paletteIndex++];
    rgb[rgbOffset++] = palette[paletteIndex++];
    rgb[rgbOffset] = palette[paletteIndex];
  }),
  MODE_INDEX_8(1, 8, (samples, rgb, palette, rgbOffset) -> {
    int paletteIndex = ((int) samples & 0xFF) * 3;
    rgb[rgbOffset++] = palette[paletteIndex++];
    rgb[rgbOffset++] = palette[paletteIndex++];
    rgb[rgbOffset] = palette[paletteIndex];
  }),

  MODE_GRAYSCALE_ALPHA_8(2, 8, (samples, rgb, palette, rgbOffset) -> {
    int level = (int) (samples >> 8) & 0xFF;
    int alpha = (int) samples & 0xFF;
    final byte l = (byte) Math.round(level * (alpha / 255.0f));
    rgb[rgbOffset++] = l;
    rgb[rgbOffset++] = l;
    rgb[rgbOffset] = l;
  }),
  MODE_GRAYSCALE_ALPHA_16(2, 16, (samples, rgb, palette, rgbOffset) -> {
    int level = (int) (samples >> 24) & 0xFF;
    int alpha = (int) (samples >> 8) & 0xFF;
    final byte l = (byte) Math.round(level * (alpha / 255.0f));
    rgb[rgbOffset++] = l;
    rgb[rgbOffset++] = l;
    rgb[rgbOffset] = l;
  }),

  MODE_RGB_ALPHA_8(4, 8, (samples, rgb, palette, rgbOffset) -> {
    final int a = (int) samples & 0xFF;
    final int r = (int) (samples >> 24) & 0xFF;
    final int g = (int) (samples >> 16) & 0xFF;
    final int b = (int) (samples >> 8) & 0xFF;
    final float alpha = a / 255.0f;
    rgb[rgbOffset++] = (byte) Math.round(r * alpha);
    rgb[rgbOffset++] = (byte) Math.round(g * alpha);
    rgb[rgbOffset] = (byte) Math.round(b * alpha);
  }),
  MODE_RGB_ALPHA_16(4, 16, (samples, rgb, palette, rgbOffset) -> {
    final int a = (int) (samples >> 8) & 0xFF;
    final int r = (int) (samples >> 56) & 0xFF;
    final int g = (int) (samples >> 40) & 0xFF;
    final int b = (int) (samples >> 24) & 0xFF;
    final float alpha = a / 255.0f;
    rgb[rgbOffset++] = (byte) Math.round(r * alpha);
    rgb[rgbOffset++] = (byte) Math.round(g * alpha);
    rgb[rgbOffset] = (byte) Math.round(b * alpha);
  });

  private final int samples;
  private final int bitsPerSample;
  private final SamplesToRgb samplesToRgbProcessor;

  PngMode(final int samples, final int bitsPerPixel, final SamplesToRgb samplesToRgb) {
    this.samples = samples;
    this.bitsPerSample = bitsPerPixel;
    this.samplesToRgbProcessor = samplesToRgb;
  }

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

  public void samplesToRgb(final long samples, final byte[] rgb, final byte[] optionalRgbPalette, final int rgbOffset) {
    this.samplesToRgbProcessor.apply(samples, rgb, optionalRgbPalette, rgbOffset);
  }

  public int getSamples() {
    return this.samples;
  }

  public int getBitsPerSample() {
    return this.bitsPerSample;
  }

  public int calcBitsPerLine(final int width) {
    return 8 + this.samples * width * bitsPerSample;
  }

  public int calcBytesPerScanline(final int width) {
    final int lineBits = this.calcBitsPerLine(width);
    return (lineBits / 8) + (lineBits % 8 == 0 ? 0 : 1);
  }

  public int calcRasterDataSize(final int width, final int height) {
    return this.calcBytesPerScanline(width) * height;
  }

  public byte[] asRgb(final byte[] imageData) {
    return imageData;
  }

  @FunctionalInterface
  private interface SamplesToRgb {
    void apply(long samples, byte[] rgb, byte[] optionalRgbPalette, int rgbOffset);
  }
}
