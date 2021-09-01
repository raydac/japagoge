package com.igormaznitsa.japagoge;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.time.Duration;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

public final class APngWriter {

  private static final int SIG_IHDR = 0x49484452;
  private static final int SIG_acTL = 0x6163544c;
  private static final int SIG_IDAT = 0x49444154;
  private static final int SIG_fdAT = 0x66644154;
  private static final int SIG_fcTL = 0x6663544c;
  private static final int SIG_IEND = 0x49454e44;
  private static final int OFFSET_ACTL = 8 + 25;
  private final FileChannel fileChannel;
  private byte[] chunkBuffer;
  private int nextChunkBufferPosition = 0;
  private int frameCounter = 0;
  private int sequenceCounter = 0;
  private int width = -1;
  private int height = -1;
  private byte[] imageRgbBufferLast;
  private byte[] imageRgbBufferTemp;
  private Duration accumulatedFrameDuration = null;
  private volatile State state = State.CREATED;
  private ImagePortion lastFoundDifference;

  public APngWriter(final FileChannel file) {
    this.fileChannel = file;
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

  private static short[] getTimeFraction(Duration delay) {
    double x = delay.toMillis();
    x /= 1000;
    final double eps = 0.000001;
    int pFound = (int) Math.round(x);
    int qFound = 1;
    double errorFound = Math.abs(x - pFound);
    double error = 1;
    for (int q = 2; q < 100 && error > eps; ++q) {
      int p = (int) (x * q);
      for (int i = 0; i < 2; ++i) {
        error = Math.abs(x - ((double) p / q));
        if (error < errorFound) {
          pFound = p;
          qFound = q;
          errorFound = error;
        }
        ++p;
      }
    }
    return new short[]{(short) pFound, (short) qFound};
  }

  private static byte[] toRgd(final BufferedImage image, final byte[] buffer) {
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

    int imgPos = 0;
    for (final int argb : data) {
      if (imgPos % scanLineBytes == 0) {
        resultBuffer[imgPos++] = 0;
      }
      resultBuffer[imgPos++] = (byte) (argb >> 16);
      resultBuffer[imgPos++] = (byte) (argb >> 8);
      resultBuffer[imgPos++] = (byte) argb;
    }
    return resultBuffer;
  }

  public State getState() {
    return this.state;
  }

  private static ImagePortion extractChangedImagePortion(final byte[] oldPngData, final byte[] newPngData, final ImagePortion imageRect) {
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

    if (startByteX == 0 && startByteY == 0 && endByteX == scanLineWidth - 1 && endByteY == imageRect.heigh - 1) {
      imageRect.data = newPngData;
    } else {
      final int pixelX = (startByteX - 1) / 3;
      final int pixelWidth = ((endByteX - 1) - (startByteX - 1)) / 3 + 1;
      final int pixelHeight = (endByteY - startByteY) + 1;

      final int portionLineWidth = pixelWidth * 3 + 1;
      final byte[] portionArray = new byte[portionLineWidth * pixelHeight];

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
      imageRect.heigh = pixelHeight;
      imageRect.data = portionArray;
    }

    return imageRect;
  }

  private void saveSingleFrame(final ImagePortion portion, final Duration frameDelay) throws IOException {
    this.frameCounter++;

    final short[] delayFractions = getTimeFraction(frameDelay);
    // fcTL
    this.putInt(26);
    this.putInt(SIG_fcTL);
    this.putInt(this.sequenceCounter++);
    this.putInt(portion.width);
    this.putInt(portion.heigh);
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
      this.putInt(SIG_IDAT);
    } else {
      this.putInt(compressedData.length + 4);
      this.putInt(SIG_fdAT);
      this.putInt(this.sequenceCounter++);
    }

    this.put(compressedData);
    this.putInt(this.calcCrcForBufferedChunk());
    this.flushAndClearBuffer();
  }

  public synchronized void addFrame(final BufferedImage image, final Duration delay) throws IOException {
    if (this.state == State.STARTED) {
      if (image.getWidth() != this.width || image.getHeight() != this.height) {
        throw new IllegalArgumentException("Unexpected image size");
      }

      if (this.imageRgbBufferLast == null) {
        final int bufferSize = (this.width * this.height * 3) + this.height;
        this.imageRgbBufferLast = new byte[bufferSize];
        this.imageRgbBufferTemp = new byte[bufferSize];
      }

      this.imageRgbBufferTemp = toRgd(image, this.imageRgbBufferTemp);

      if (this.accumulatedFrameDuration == null) {
        System.arraycopy(this.imageRgbBufferTemp, 0, this.imageRgbBufferLast, 0, this.imageRgbBufferTemp.length);
        this.lastFoundDifference = new ImagePortion(0, 0, this.width, this.height, this.imageRgbBufferLast);
        this.accumulatedFrameDuration = delay;
      } else {
        final ImagePortion foundDifference = extractChangedImagePortion(this.imageRgbBufferLast, this.imageRgbBufferTemp, new ImagePortion(0, 0, this.width, this.height, null));

        if (foundDifference == null) {
          this.accumulatedFrameDuration = this.accumulatedFrameDuration.plus(delay);
        } else {
          this.saveSingleFrame(this.lastFoundDifference, this.accumulatedFrameDuration);

          System.arraycopy(this.imageRgbBufferTemp, 0, this.imageRgbBufferLast, 0, this.imageRgbBufferTemp.length);
          if (foundDifference.data == this.imageRgbBufferTemp) {
            foundDifference.data = this.imageRgbBufferLast;
          }

          this.lastFoundDifference = foundDifference;

          this.accumulatedFrameDuration = delay;
        }
      }
    }
  }

  public synchronized void close(final int loopCount) throws IOException {
    if (this.state != State.STARTED) throw new IllegalStateException("State: " + this.state);
    this.state = State.CLOSED;

    if (this.lastFoundDifference != null) {
      this.saveSingleFrame(this.lastFoundDifference, this.accumulatedFrameDuration);
    }

    try {
      this.putInt(0);
      this.putInt(SIG_IEND);
      this.putInt(this.calcCrcForBufferedChunk());
      this.flushAndClearBuffer();
      this.fileChannel.position(OFFSET_ACTL);
      this.writeAcTLChunk(this.frameCounter, loopCount);
    } finally {
      try {
        this.fileChannel.close();
      } finally {
        this.imageRgbBufferLast = null;
        this.imageRgbBufferTemp = null;
        this.chunkBuffer = null;
      }
    }
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

  private static final class ImagePortion {
    int x;
    int y;
    int width;
    int heigh;
    byte[] data;

    ImagePortion(final int x, final int y, final int width, final int height, final byte[] data) {
      this.x = x;
      this.y = y;
      this.width = width;
      this.heigh = height;
      this.data = data;
    }
  }

  public synchronized void start(final int width, final int height) throws IOException {
    if (this.state != State.CREATED) throw new IllegalStateException("State: " + this.state);
    this.state = State.STARTED;

    this.width = width;
    this.height = height;

    // signature
    this.put(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
    this.flushAndClearBuffer();

    // header
    this.putInt(13);
    this.putInt(SIG_IHDR);
    this.putInt(width);
    this.putInt(height);
    this.put(8); // bit depth
    this.put(2); // color type
    this.put(0); // compression method
    this.put(0); // filter method
    this.put(0); // interlace method
    this.putInt(this.calcCrcForBufferedChunk());
    this.flushAndClearBuffer();

    this.writeAcTLChunk(0, 0);
  }

  private void writeAcTLChunk(final int frameCount, final int loopCount) throws IOException {
    this.putInt(8);
    this.putInt(SIG_acTL);
    this.putInt(frameCount);
    this.putInt(loopCount); // 0 : infinite
    this.putInt(this.calcCrcForBufferedChunk());
    this.flushAndClearBuffer();
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

}
