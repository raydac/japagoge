package com.igormaznitsa.japagoge.gif;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

public final class AGifWriter {

  private final int[] globalRgbPalette;
  private final int logicalImageWidth;
  private final int logicalImageHeight;

  private final AtomicInteger frameCounter = new AtomicInteger();
  private final int repeat;
  private final int backgroundColorIndex;

  public AGifWriter(
          final int logicalImageWidth,
          final int logicalImageHeight,
          final int backgroundColorIndex,
          final int[] globalRgbPalette,
          final int repeat
  ) {
    this.repeat = repeat;
    this.backgroundColorIndex = backgroundColorIndex;
    this.logicalImageWidth = logicalImageWidth;
    this.logicalImageHeight = logicalImageHeight;
    this.globalRgbPalette = globalRgbPalette.clone();
  }

  private static int findN(final int number) {
    int x = number - 1;
    if (x >= 255) return 7;
    if (x >= 127) return 6;
    if (x >= 63) return 5;
    if (x >= 31) return 4;
    if (x >= 15) return 3;
    if (x >= 7) return 2;
    if (x >= 3) return 1;
    if (x >= 1) return 0;
    throw new IllegalArgumentException("Unexpected number: " + number);
  }

  private static void writeLogicalScreenDescriptor(
          final int screenWidth,
          final int screenHeight,
          final int backgroundColorIndex,
          final int paletteSize,
          final OutputStream out) throws IOException {
    writeShort(screenWidth, out);
    writeShort(screenHeight, out);

    final int paletteN = findN(paletteSize);

    final int flags = ((0x80 | paletteN) << 4) | paletteN;
    out.write(flags);

    out.write(backgroundColorIndex);
    out.write(0);
  }

  private static void writeShort(final int value, final OutputStream out) throws IOException {
    out.write(value);
    out.write(value >> 8);
  }

  private static void writeString(final String str, final OutputStream out) throws IOException {
    for (int i = 0; i < str.length(); i++) {
      out.write(str.charAt(i));
    }
  }

  private static void writeGraphicCtrlExt(final Duration delay, final OutputStream out) throws IOException {
    out.write(0x21);
    out.write(0xF9);
    out.write(0x04);
    out.write(0x04);
    writeShort(Math.round(delay.toMillis() / 10.0f), out);
    out.write(0);
    out.write(0);
  }

  private static void writeImageDesc(final int x, final int y, final int width, final int height, final OutputStream out) throws IOException {
    out.write(0x2C);
    writeShort(x, out);
    writeShort(y, out);
    writeShort(width, out);
    writeShort(height, out);
    out.write(0);
  }

  private static void writeNetscapeExt(final int repeats, final OutputStream out) throws IOException {
    out.write(0x21);
    out.write(0xFF);
    out.write(11);
    writeString("NETSCAPE2.0", out);
    out.write(3);
    out.write(1);
    writeShort(repeats, out); // 0 - forever
    out.write(0);
  }

  private static void writePalette(final int[] rgb, final OutputStream out) throws IOException {
    for (int c : rgb) {
      out.write(c >> 16);
      out.write(c >> 8);
      out.write(c);
    }
  }

  public int[] getGlobalPalette() {
    return this.globalRgbPalette;
  }

  public void addFrame(
          final OutputStream out,
          final int x,
          final int y,
          final int width,
          final int height,
          final Duration delay,
          final byte[] pixelIndexes
  ) throws IOException {
    final int frame = this.frameCounter.getAndIncrement();
    if (frame == 0) {
      writeString("GIF89a", out);
      writeLogicalScreenDescriptor(this.logicalImageWidth, this.logicalImageHeight, this.backgroundColorIndex, this.globalRgbPalette.length, out);
      writePalette(this.globalRgbPalette, out);
      if (this.repeat >= 0) {
        writeNetscapeExt(this.repeat, out);
      }
    }
    writeGraphicCtrlExt(delay, out);
    writeImageDesc(x, y, width, height, out);
    new GifLzwCompressor(out, width, height, pixelIndexes).encode();
  }
}

