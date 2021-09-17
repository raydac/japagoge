package com.igormaznitsa.japagoge.gif;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

public final class AGifWriter {

  private static final int DISPOSAL_NOT_SPECIFIED = 0;
  private static final int DISPOSAL_DO_NOT_DISPOSE = 1;
  private static final int DISPOSAL_OVERWRITE_BY_BACKGROUND_COLOR = 2;
  private static final int DISPOSAL_OVERWRITE_WITH_PREVIOUS_GRAPHICS = 4;

  private final OutputStream outputStream;
  private final byte[] globalRgbPalette;
  private final int logicalImageWidth;
  private final int logicalImageHeight;
  private final AtomicInteger frameCounter = new AtomicInteger();
  private final int repeat;
  private final int backgroundColorIndex;

  public AGifWriter(
          final OutputStream outputStream,
          final int logicalImageWidth,
          final int logicalImageHeight,
          final int backgroundColorIndex,
          final byte[] globalRgbPalette,
          final int repeat
  ) {
    this.outputStream = outputStream;
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

  private void writeLogicalScreenDescriptor(
          final int screenWidth,
          final int screenHeight,
          final int backgroundColorIndex,
          final int paletteSize) throws IOException {
    writeShort(screenWidth);
    writeShort(screenHeight);

    final int paletteN = findN(paletteSize);

    final int flags = 0x80 | (paletteN << 4) | paletteN;
    this.outputStream.write(flags);

    this.outputStream.write(backgroundColorIndex);
    this.outputStream.write(0);
  }

  private void writeShort(final int value) throws IOException {
    this.outputStream.write(value);
    this.outputStream.write(value >> 8);
  }

  private void writeString(final String str) throws IOException {
    for (int i = 0; i < str.length(); i++) {
      this.outputStream.write(str.charAt(i));
    }
  }

  private void writeGraphicCtrlExt(final Duration delay, final int disposalMethod) throws IOException {
    this.outputStream.write(0x21);
    this.outputStream.write(0xF9);
    this.outputStream.write(0x04);
    this.outputStream.write((disposalMethod & 7) << 2);
    writeShort(Math.round(delay.toMillis() / 10.0f));
    this.outputStream.write(0);
    this.outputStream.write(0);
  }

  private void writeImageDesc(final int x, final int y, final int width, final int height) throws IOException {
    this.outputStream.write(0x2C);
    writeShort(x);
    writeShort(y);
    writeShort(width);
    writeShort(height);
    this.outputStream.write(0);
  }

  private void writeNetscapeExt(final int repeats) throws IOException {
    this.outputStream.write(0x21);
    this.outputStream.write(0xFF);
    this.outputStream.write(11);
    writeString("NETSCAPE2.0");
    this.outputStream.write(3);
    this.outputStream.write(1);
    writeShort(repeats); // 0 - forever
    this.outputStream.write(0);
  }

  private void writePalette(final byte[] rgb) throws IOException {
    this.outputStream.write(rgb);
  }

  public byte[] getGlobalPalette() {
    return this.globalRgbPalette;
  }

  public void addFrame(
          final int x,
          final int y,
          final int width,
          final int height,
          final Duration delay,
          final byte[] pixelIndexes
  ) throws IOException {
    final int frame = this.frameCounter.getAndIncrement();
    if (frame == 0) {
      writeString("GIF89a");
      writeLogicalScreenDescriptor(this.logicalImageWidth, this.logicalImageHeight, this.backgroundColorIndex, this.globalRgbPalette.length);
      writePalette(this.globalRgbPalette);
      if (this.repeat >= 0) {
        writeNetscapeExt(this.repeat);
      }
      writeGraphicCtrlExt(delay, DISPOSAL_NOT_SPECIFIED);
    } else {
      writeGraphicCtrlExt(delay, DISPOSAL_DO_NOT_DISPOSE);
    }
    writeImageDesc(x, y, width, height);
    new GifLzwCompressor(this.outputStream, width, height, pixelIndexes).encode();
  }
}

