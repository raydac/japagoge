package com.igormaznitsa.japagoge;

import com.igormaznitsa.japagoge.filters.ColorFilter;
import com.igormaznitsa.japagoge.filters.RgbPixelFilter;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

public final class APngWriter {

  private static final int OFFSET_ACTL_NO_PALETTE = 8 + 25;
  private final FileChannel fileChannel;
  private final ColorFilter filter;
  private final ColorComponentStatistics colorStatistics;
  private byte[] chunkBuffer;
  private int nextChunkBufferPosition = 0;
  private int frameCounter = 0;
  private int sequenceCounter = 0;
  private int width = -1;
  private int height = -1;
  private byte[] imageDataBufferLast;
  private byte[] imageDataBufferTemp;
  private Duration accumulatedFrameDuration = null;
  private volatile State state = State.CREATED;
  private ImagePortion lastFoundDifference;

  public APngWriter(final FileChannel file, final RgbPixelFilter filter) {
    this.colorStatistics = filter.get().getPalette().isPresent() ? null : new ColorComponentStatistics();
    this.fileChannel = file;
    this.filter = filter.get();
  }

  private static byte[] toRgb(final BufferedImage image, final byte[] buffer, final ColorComponentStatistics colorStatistics, final ColorFilter filter) {
    final int imageWidth = image.getWidth();
    final int imageHeight = image.getHeight();

    final int requiredBufferSize = (imageWidth + 3) * imageHeight + imageHeight;
    final int[] data = ((DataBufferInt) image.getData().getDataBuffer()).getData();

    final byte[] resultBuffer;
    if (buffer == null || buffer.length < requiredBufferSize) {
      resultBuffer = new byte[requiredBufferSize];
    } else {
      resultBuffer = buffer;
    }
    final int scanLineBytes = (imageWidth * 3) + 1;

    for (int pass = 0; pass < filter.getPasses(); pass++) {
      int imgPos = 0;
      for (int argb : data) {
        if (imgPos % scanLineBytes == 0) {
          resultBuffer[imgPos++] = 0;
        }

        argb = filter.filterRgb(argb, pass);

        if (filter.isPassImageUpdate(pass)) {
          final int r = (argb >> 16) & 0xFF;
          final int g = (argb >> 8) & 0xFF;
          final int b = argb & 0xFF;

          if (colorStatistics != null) {
            colorStatistics.update(r, g, b);
          }

          resultBuffer[imgPos++] = (byte) r;
          resultBuffer[imgPos++] = (byte) g;
          resultBuffer[imgPos++] = (byte) b;
        } else {
          imgPos += 3;
        }
      }
    }
    return resultBuffer;
  }

  private static byte[] compress(final byte[] data) throws IOException {
    final Deflater deflater = new Deflater(9);
    var outBuffer = new ByteArrayOutputStream(data.length / 2);
    var deflatesStream = new DeflaterOutputStream(outBuffer, deflater, 0x2000, false);
    deflatesStream.write(data);
    deflatesStream.finish();
    deflatesStream.flush();
    deflatesStream.close();
    return outBuffer.toByteArray();
  }

  private static short[] getTimeFraction(final Duration delay) {
    final long milliseconds = delay.toMillis();
    if (milliseconds == 0L) {
      return new short[]{0, 0};
    } else {
      return new short[]{(short) milliseconds, (short) 1000};
    }
  }

  private static byte[] toMonochrome(final BufferedImage image, final byte[] buffer, final ColorFilter filter) {
    final int imageWidth = image.getWidth();
    final int imageHeight = image.getHeight();

    final int requiredBufferSize = (imageWidth + 1) * imageHeight + imageHeight;
    final int[] data = ((DataBufferInt) image.getData().getDataBuffer()).getData();

    final byte[] resultBuffer;
    if (buffer == null || buffer.length < requiredBufferSize) {
      resultBuffer = new byte[requiredBufferSize];
    } else {
      resultBuffer = buffer;
    }
    final int scanLineBytes = imageWidth + 1;

    for (int pass = 0; pass < filter.getPasses(); pass++) {
      int imgPos = 0;
      for (final int argb : data) {
        if (imgPos % scanLineBytes == 0) {
          resultBuffer[imgPos++] = 0;
        }

        final byte filteredData = (byte) filter.filterRgb(argb, pass);
        if (filter.isPassImageUpdate(pass)) {
          resultBuffer[imgPos++] = filteredData;
        } else {
          imgPos++;
        }
      }
    }
    return resultBuffer;
  }

  private static ImagePortion extractChangedImagePortionRgb(final byte[] oldPngData, final byte[] newPngData, final ImagePortion imageRect) {
    int startByteX = Integer.MAX_VALUE;
    int startByteY = Integer.MAX_VALUE;
    int endByteX = Integer.MIN_VALUE;
    int endByteY = Integer.MIN_VALUE;

    final int scanLineWidth = imageRect.width * 3 + 1;

    final int dataLength = oldPngData.length;

    for (int i = 0; i < dataLength; i++) {
      if (oldPngData[i] != newPngData[i]) {
        final int px = i % scanLineWidth;
        final int py = i / scanLineWidth;
        startByteX = Math.min(startByteX, px);
        startByteY = Math.min(startByteY, py);
        endByteX = Math.max(endByteX, px);
        endByteY = Math.max(endByteY, py);
      }
    }

    if (startByteX == Integer.MAX_VALUE) return null;

    if (startByteX == 0 && startByteY == 0 && endByteX == scanLineWidth - 1 && endByteY == imageRect.height - 1) {
      imageRect.data = newPngData;
    } else {
      final int pixelX = (startByteX - 1) / 3;
      final int pixelWidth = ((endByteX - 1) - (startByteX - 1)) / 3 + 1;
      final int pixelHeight = (endByteY - startByteY) + 1;

      final int portionLineWidth = pixelWidth * 3 + 1;
      final int fragmentDataLength = portionLineWidth * pixelHeight;

      if (fragmentDataLength > (newPngData.length * 3) / 4) {
        imageRect.data = newPngData;
      } else {
        final byte[] portionArray = new byte[fragmentDataLength];

        if (startByteX == 0) {
          int srcOffset = startByteY * scanLineWidth;
          int dstOffset = 0;
          for (int i = 0; i < pixelHeight; i++) {
            System.arraycopy(newPngData, srcOffset, portionArray, dstOffset, portionLineWidth);
            srcOffset += scanLineWidth;
            dstOffset += portionLineWidth;
          }
        } else {
          int srcOffset = startByteY * scanLineWidth + (pixelX * 3) + 1;
          int dstOffset = 1;
          final int copyLineLength = pixelWidth * 3;
          for (int i = 0; i < pixelHeight; i++) {
            System.arraycopy(newPngData, srcOffset, portionArray, dstOffset, copyLineLength);
            srcOffset += scanLineWidth;
            dstOffset += portionLineWidth;
          }
        }

        imageRect.x = pixelX;
        imageRect.y = startByteY;
        imageRect.width = pixelWidth;
        imageRect.height = pixelHeight;
        imageRect.data = portionArray;
      }
    }

    return imageRect;
  }

  private static ImagePortion extractChangedImagePortionMonochrome(final byte[] oldPngData, final byte[] newPngData, final ImagePortion imageRect) {
    int startByteX = Integer.MAX_VALUE;
    int startByteY = Integer.MAX_VALUE;
    int endByteX = Integer.MIN_VALUE;
    int endByteY = Integer.MIN_VALUE;

    final int scanLineWidth = imageRect.width + 1;

    final int dataLength = oldPngData.length;

    for (int i = 0; i < dataLength; i++) {
      if (oldPngData[i] != newPngData[i]) {
        final int px = i % scanLineWidth;
        final int py = i / scanLineWidth;
        startByteX = Math.min(startByteX, px);
        startByteY = Math.min(startByteY, py);
        endByteX = Math.max(endByteX, px);
        endByteY = Math.max(endByteY, py);
      }
    }

    if (startByteX == Integer.MAX_VALUE) return null;

    if (startByteX == 0 && startByteY == 0 && endByteX == scanLineWidth - 1 && endByteY == imageRect.height - 1) {
      imageRect.data = newPngData;
    } else {
      final int pixelX = (startByteX - 1);
      final int pixelWidth = ((endByteX - 1) - (startByteX - 1)) + 1;
      final int pixelHeight = (endByteY - startByteY) + 1;

      final int portionLineWidth = pixelWidth + 1;
      final int fragmentDataLength = portionLineWidth * pixelHeight;

      if (fragmentDataLength > (newPngData.length * 3) / 4) {
        imageRect.data = newPngData;
      } else {
        final byte[] portionArray = new byte[fragmentDataLength];

        if (startByteX == 0) {
          int srcOffset = startByteY * scanLineWidth;
          int dstOffset = 0;
          for (int i = 0; i < pixelHeight; i++) {
            System.arraycopy(newPngData, srcOffset, portionArray, dstOffset, portionLineWidth);
            srcOffset += scanLineWidth;
            dstOffset += portionLineWidth;
          }
        } else {
          int srcOffset = startByteY * scanLineWidth + pixelX + 1;
          int dstOffset = 1;
          for (int i = 0; i < pixelHeight; i++) {
            System.arraycopy(newPngData, srcOffset, portionArray, dstOffset, pixelWidth);
            srcOffset += scanLineWidth;
            dstOffset += portionLineWidth;
          }
        }

        imageRect.x = pixelX;
        imageRect.y = startByteY;
        imageRect.width = pixelWidth;
        imageRect.height = pixelHeight;
        imageRect.data = portionArray;
      }
    }

    return imageRect;
  }

  public Optional<ColorComponentStatistics> getColorStatistics() {
    return Optional.ofNullable(this.colorStatistics);
  }

  public synchronized void addFrame(final BufferedImage image, final boolean forceWholeFrame, final Duration delay) throws IOException {
    if (this.state == State.STARTED) {
      if (image.getWidth() != this.width || image.getHeight() != this.height) {
        throw new IllegalArgumentException("Unexpected image size");
      }

      if (this.imageDataBufferLast == null) {
        final int bufferSize = (this.width * this.height * 3) + this.height;
        this.imageDataBufferLast = new byte[bufferSize];
        this.imageDataBufferTemp = new byte[bufferSize];
      }

      this.imageDataBufferTemp = this.filter.isMonochrome() ? toMonochrome(image, this.imageDataBufferTemp, this.filter)
              : toRgb(image, this.imageDataBufferTemp, this.colorStatistics, this.filter);

      if (this.accumulatedFrameDuration == null) {
        System.arraycopy(this.imageDataBufferTemp, 0, this.imageDataBufferLast, 0, this.imageDataBufferTemp.length);
        this.lastFoundDifference = new ImagePortion(0, 0, this.width, this.height, this.imageDataBufferLast);
        this.accumulatedFrameDuration = delay;
      } else {
        final ImagePortion foundDifference = this.filter.isMonochrome() ?
                extractChangedImagePortionMonochrome(this.imageDataBufferLast, this.imageDataBufferTemp, new ImagePortion(0, 0, this.width, this.height, null))
                : extractChangedImagePortionRgb(this.imageDataBufferLast, this.imageDataBufferTemp, new ImagePortion(0, 0, this.width, this.height, null));

        if (foundDifference == null) {
          this.accumulatedFrameDuration = this.accumulatedFrameDuration.plus(delay);
        } else {
          if (forceWholeFrame) {
            System.arraycopy(this.imageDataBufferTemp, 0, this.imageDataBufferLast, 0, this.imageDataBufferTemp.length);
            this.lastFoundDifference = new ImagePortion(0, 0, this.width, this.height, this.imageDataBufferLast);
          }
          this.saveSingleFrame(this.lastFoundDifference, this.accumulatedFrameDuration);

          System.arraycopy(this.imageDataBufferTemp, 0, this.imageDataBufferLast, 0, this.imageDataBufferTemp.length);
          if (foundDifference.data == this.imageDataBufferTemp) {
            foundDifference.data = this.imageDataBufferLast;
          }

          this.lastFoundDifference = foundDifference;
          this.accumulatedFrameDuration = delay;
        }
      }
    }
  }

  public State getState() {
    return this.state;
  }

  private void saveSingleFrame(final ImagePortion portion, final Duration frameDelay) throws IOException {
    this.frameCounter++;

    final short[] delayFractions = getTimeFraction(frameDelay);
    // fcTL
    this.putInt(26);
    this.putText("fcTL");
    this.putInt(this.sequenceCounter++);
    this.putInt(portion.width);
    this.putInt(portion.height);
    this.putInt(portion.x);               // x position
    this.putInt(portion.y);               // y position
    this.putShort(delayFractions[0]);     // fps num
    this.putShort(delayFractions[1]);     // fps den
    this.put(0);           //dispose 1:clear, 0: do nothing, 2: revert
    this.put(0);               //blend   1:blend, 0: overwrite
    this.putInt(this.calcCrcForBufferedChunk());
    this.flushAndClearBuffer();

    var compressedData = compress(portion.data);

    if (this.frameCounter == 1) {
      this.putInt(compressedData.length);
      this.putText("IDAT");
    } else {
      this.putInt(compressedData.length + 4);
      this.putText("fdAT");
      this.putInt(this.sequenceCounter++);
    }

    this.put(compressedData);
    this.putInt(this.calcCrcForBufferedChunk());
    this.flushAndClearBuffer();
  }

  public synchronized Statistics close(final int loopCount) throws IOException {
    if (this.state == State.CLOSED) throw new IllegalStateException("Already closed");
    final State oldState = this.state;
    this.state = State.CLOSED;
    if (oldState == State.CREATED) return null;

    if (this.lastFoundDifference != null) {
      this.saveSingleFrame(this.lastFoundDifference, this.accumulatedFrameDuration);
    }

    try {
      this.writeIEND();

      int actlStartOffset = OFFSET_ACTL_NO_PALETTE;

      final Optional<int[]> palette = this.filter.getPalette();
      if (palette.isPresent()) {
        actlStartOffset += palette.orElseThrow().length * 3 + 12;
      }

      this.fileChannel.position(actlStartOffset);
      this.writeAcTLChunk(this.frameCounter, loopCount);
      return new Statistics(this.colorStatistics, this.chunkBuffer.length, this.frameCounter, this.width, this.height, this.fileChannel.size());
    } finally {
      try {
        this.fileChannel.close();
      } finally {
        this.imageDataBufferLast = null;
        this.imageDataBufferTemp = null;
        this.chunkBuffer = null;
      }
    }
  }

  public synchronized void start(final String productName, final int width, final int height) throws IOException {
    if (this.state != State.CREATED) throw new IllegalStateException("State: " + this.state);
    this.state = State.STARTED;

    this.width = width;
    this.height = height;

    // signature
    this.put(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
    this.flushAndClearBuffer();

    final Optional<int[]> palette = this.filter.getPalette();

    // header
    this.putInt(13);
    this.putText("IHDR");
    this.putInt(width);
    this.putInt(height);

    this.put(8); // bit depth

    if (palette.isPresent()) {
      this.put(3);
    } else {
      this.put(this.filter.isMonochrome() ? 0 : 2); // color type
    }

    this.put(0); // compression method
    this.put(0); // filter method
    this.put(0); // interlace method
    this.putInt(this.calcCrcForBufferedChunk());
    this.flushAndClearBuffer();

    if (palette.isPresent()) {
      this.writePtleChunk(palette.orElseThrow());
    }
    this.writeAcTLChunk(0, 0);
    if (productName != null) {
      this.writeTextChunk("Software", productName);
    }
  }

  private void writeIEND() throws IOException {
    this.putInt(0);
    this.putText("IEND");
    this.putInt(this.calcCrcForBufferedChunk());
    this.flushAndClearBuffer();
  }

  private void flushAndClearBuffer() throws IOException {
    this.fileChannel.write(ByteBuffer.wrap(this.chunkBuffer, 0, this.nextChunkBufferPosition));
    this.resetPosition();
  }

  private void resetPosition() {
    this.nextChunkBufferPosition = 0;
  }

  private void putInt(final int value) {
    this.put(value >>> 24);
    this.put(value >>> 16);
    this.put(value >>> 8);
    this.put(value);
  }

  private void writePtleChunk(final int[] rgbPalette) throws IOException {
    this.putInt(rgbPalette.length * 3);
    this.putText("PLTE");
    for (int c : rgbPalette) {
      this.put(c >> 16);
      this.put(c >> 8);
      this.put(c);
    }
    this.putInt(this.calcCrcForBufferedChunk());
    this.flushAndClearBuffer();
  }

  private void writeTextChunk(final String key, final String value) throws IOException {
    final byte[] textBytes = (key + '\u0000' + value).getBytes(StandardCharsets.US_ASCII);
    this.putInt(textBytes.length);
    this.putText("tEXt");
    this.put(textBytes);
    this.putInt(this.calcCrcForBufferedChunk());
    this.flushAndClearBuffer();
  }

  private void writeAcTLChunk(final int frameCount, final int loopCount) throws IOException {
    this.putInt(8);
    this.putText("acTL");
    this.putInt(frameCount);
    this.putInt(loopCount); // 0 : infinite
    this.putInt(this.calcCrcForBufferedChunk());
    this.flushAndClearBuffer();
  }

  private void putText(final String name) {
    for (int i = 0; i < name.length(); i++) {
      this.put(name.charAt(i));
    }
  }

  private void putShort(final int value) {
    this.put(value >> 8);
    this.put(value);
  }

  private void put(final byte[] array) {
    for (final byte b : array) {
      this.put(b);
    }
  }

  private void put(final int value) {
    if (this.chunkBuffer == null) {
      this.chunkBuffer = new byte[64 * 1024];
    } else if (this.nextChunkBufferPosition == this.chunkBuffer.length) {
      final byte[] newBuffer = new byte[this.chunkBuffer.length * 2];
      System.arraycopy(this.chunkBuffer, 0, newBuffer, 0, this.chunkBuffer.length);
      this.chunkBuffer = newBuffer;
    }
    this.chunkBuffer[this.nextChunkBufferPosition++] = (byte) value;
  }

  private int calcCrcForBufferedChunk() {
    CRC32 crc = new CRC32();
    crc.update(this.chunkBuffer, 4, this.nextChunkBufferPosition - 4);
    return (int) crc.getValue();
  }

  public enum State {
    CREATED,
    STARTED,
    CLOSED
  }

  public final static class ColorComponentStatistics {
    private final int[] statisticsR = new int[256];
    private final int[] statisticsG = new int[256];
    private final int[] statisticsB = new int[256];

    private static int[] toRgb(final byte[] rgb) {
      final int[] result = new int[256];
      for (int i = 0; i < 256; i++) {
        final int offset = i * 3;
        final int r = rgb[offset] & 0xFF;
        final int g = rgb[offset + 1] & 0xFF;
        final int b = rgb[offset + 2] & 0xFF;
        result[i] = (r << 16) | (g << 8) | b;
      }
      return result;
    }

    public int[] getStatisticsR() {
      return this.statisticsR.clone();
    }

    public int[] getStatisticsG() {
      return this.statisticsG.clone();
    }

    public int[] getStatisticsB() {
      return this.statisticsB.clone();
    }

    private void update(final int r, final int g, final int b) {
      if (this.statisticsR[r] < Integer.MAX_VALUE) this.statisticsR[r]++;
      if (this.statisticsG[g] < Integer.MAX_VALUE) this.statisticsG[g]++;
      if (this.statisticsB[b] < Integer.MAX_VALUE) this.statisticsB[b]++;
    }

    public int[] makeAutoPalette() {
      final byte[] rgb = new byte[256 * 3];

      final List<Container> containers = new ArrayList<>();
      containers.add(new Container(this.statisticsR, 0));
      containers.add(new Container(this.statisticsG, 1));
      containers.add(new Container(this.statisticsB, 2));
      Collections.sort(containers);

      containers.forEach(x -> x.fillByColorComponent(rgb));

      return toRgb(rgb);
    }

    private static class Container implements Comparable<Container> {
      private final int colors;
      private final int position;
      private final int[] componentStatistics;

      private Container(final int[] componentStatistics, final int position) {
        this.componentStatistics = componentStatistics;
        this.position = position;
        this.colors = findNumberOrNonZero(componentStatistics);
      }

      private static int findNumberOrNonZero(final int[] component) {
        return (int) IntStream.of(component).filter(c -> c != 0).count();
      }

      void fillByColorComponent(final byte[] resultRgb) {
        final int[] statisticsClone = this.componentStatistics.clone();

        int outPosition;
        for (outPosition = 0; outPosition < 256; outPosition++) {
          int maxCount = 0;
          int foundPosition = -1;
          for (int i = 0; i < 256; i++) {
            if (statisticsClone[i] != 0 && maxCount < statisticsClone[i]) {
              maxCount = statisticsClone[i];
              foundPosition = i;
            }
          }
          if (foundPosition < 0) break;
          else {
            resultRgb[outPosition * 3 + this.position] = (byte) foundPosition;
            statisticsClone[foundPosition] = 0;
          }
        }

        final int limit = outPosition;
        while (outPosition < 256) {
          resultRgb[outPosition * 3 + this.position] = resultRgb[(outPosition % limit) * 3 + this.position];
          outPosition++;
        }
      }

      @Override
      public int compareTo(final Container cont) {
        return Integer.compare(cont.colors, this.colors);
      }
    }
  }

  private static final class ImagePortion {
    int x;
    int y;
    int width;
    int height;
    byte[] data;

    @SuppressWarnings("SameParameterValue")
    ImagePortion(final int x, final int y, final int width, final int height, final byte[] data) {
      this.x = x;
      this.y = y;
      this.width = width;
      this.height = height;
      this.data = data;
    }
  }

  public static final class Statistics {
    public final ColorComponentStatistics colorStatistics;
    public final int bufferSize;
    public final int frames;
    public final int width;
    public final int height;
    public final long size;

    private Statistics(final ColorComponentStatistics colorStatistics, final int bufferSize, final int frames, final int width, final int height, final long size) {
      this.colorStatistics = colorStatistics;
      this.bufferSize = bufferSize;
      this.frames = frames;
      this.width = width;
      this.height = height;
      this.size = size;
    }
  }

}
