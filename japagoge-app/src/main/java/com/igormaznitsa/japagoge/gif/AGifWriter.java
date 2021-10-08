package com.igormaznitsa.japagoge.gif;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings("unused")
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

  private boolean ended = false;

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

    final int globalPaletteItems = (int) Math.pow(2, globalPaletteSize(globalRgbPalette.length / 3) + 1);

    this.globalRgbPalette = Arrays.copyOf(globalRgbPalette.clone(), globalPaletteItems * 3);
  }

  private static int globalPaletteSize(int paletteColors) {
    if (paletteColors > 256 || paletteColors <= 0) throw new IllegalArgumentException("Must be in interval 1..256");

    if (paletteColors == 256) return 7;
    if (paletteColors >= 128) return 6;
    if (paletteColors >= 64) return 5;
    if (paletteColors >= 32) return 4;
    if (paletteColors >= 16) return 3;
    if (paletteColors >= 8) return 2;
    if (paletteColors >= 4) return 1;

    return 0;
  }

  private void writeLogicalScreenDescriptor(
          final int screenWidth,
          final int screenHeight,
          final int backgroundColorIndex,
          final int paletteSize) throws IOException {
    writeShort(screenWidth);
    writeShort(screenHeight);

    final int gctSize = globalPaletteSize(paletteSize);

    this.outputStream.write(0x80 | (7 << 4) | gctSize);
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
    this.outputStream.write(0x0B);
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
    this.assertNotEnded();
    final int frame = this.frameCounter.getAndIncrement();
    if (frame == 0) {
      writeString("GIF89a");
      writeLogicalScreenDescriptor(this.logicalImageWidth, this.logicalImageHeight, this.backgroundColorIndex, this.globalRgbPalette.length / 3);
      writePalette(this.globalRgbPalette);
      if (this.repeat >= 0) {
        writeNetscapeExt(this.repeat);
      }
      writeGraphicCtrlExt(delay, DISPOSAL_NOT_SPECIFIED);
    } else {
      writeGraphicCtrlExt(delay, DISPOSAL_DO_NOT_DISPOSE);
    }
    writeImageDesc(x, y, width, height);
    new GifLzwCompressor(this.outputStream, width, height, pixelIndexes)
            .encode();
  }

  private void assertNotEnded() {
    if (this.ended) throw new IllegalStateException("Already ended");
  }

  public void end() throws IOException {
    this.assertNotEnded();
    this.ended = true;
    this.outputStream.write(0x3B);
  }
}

