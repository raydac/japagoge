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
  private byte[] buffer;
  private int bufferPosition = 0;
  private int frameCounter = 0;
  private int sequenceCounter = 0;
  private int width = -1;
  private int height = -1;
  private byte[] imageRgbBuffer;
  private volatile State state = State.CREATED;

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

  public State getState() {
    return this.state;
  }

  public synchronized void addFrame(final BufferedImage image, final Duration delay) throws IOException {
    if (this.state == State.STARTED) {
      if (image.getWidth() != this.width || image.getHeight() != this.height) {
        throw new IllegalArgumentException("Unexpected image size");
      }

      this.frameCounter++;

      final short[] delayFractions = getTimeFraction(delay);

      // fcTL
      this.putInt(26);
      this.putInt(SIG_fcTL);
      this.putInt(this.sequenceCounter++);
      this.putInt(this.width);
      this.putInt(this.height);
      this.putInt(0);               // x position
      this.putInt(0);               // y position
      this.putShort(delayFractions[0]);     // fps num
      this.putShort(delayFractions[1]);     // fps den
      this.put(1);           //dispose 1:clear, 0: do nothing, 2: revert
      this.put(0);               //blend   1:blend, 0: overwrite
      this.putInt(this.calcCrcForBufferedChunk());

      this.flushAndClearBuffer();

      final int[] data = ((DataBufferInt) image.getData().getDataBuffer()).getData();

      if (this.imageRgbBuffer == null) {
        this.imageRgbBuffer = new byte[(this.width * this.height * 3) + this.height];
      }

      final int scanLineBytes = (this.width * 3) + 1;

      int imgPos = 0;
      for (final int argb : data) {
        if (imgPos % scanLineBytes == 0) imgPos++; // offset for scanline filter byte
        this.imageRgbBuffer[imgPos++] = (byte) (argb >> 16);
        this.imageRgbBuffer[imgPos++] = (byte) (argb >> 8);
        this.imageRgbBuffer[imgPos++] = (byte) argb;
      }

      var compressedData = compress(this.imageRgbBuffer);

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
  }

  private void flushAndClearBuffer() throws IOException {
    this.fileChannel.write(ByteBuffer.wrap(this.buffer, 0, this.bufferPosition));
    this.resetPosition();
  }

  private void resetPosition() {
    this.bufferPosition = 0;
  }

  private void putInt(final int value) {
    this.buffer[this.bufferPosition++] = (byte) (value >>> 24);
    this.buffer[this.bufferPosition++] = (byte) (value >>> 16);
    this.buffer[this.bufferPosition++] = (byte) (value >>> 8);
    this.buffer[this.bufferPosition++] = (byte) value;
  }

  public synchronized void close(final int loopCount) throws IOException {
    if (this.state != State.STARTED) throw new IllegalStateException("State: " + this.state);
    this.state = State.CLOSED;
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
        this.imageRgbBuffer = null;
        this.buffer = null;
      }
    }
  }

  public synchronized void start(final int width, final int height) throws IOException {
    if (this.state != State.CREATED) throw new IllegalStateException("State: " + this.state);
    this.state = State.STARTED;

    this.width = width;
    this.height = height;

    this.buffer = new byte[Math.max(128 * 1024, this.width * this.height * 3)];

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
    this.buffer[this.bufferPosition++] = (byte) (value >> 8);
    this.buffer[this.bufferPosition++] = (byte) value;
  }

  private void put(final byte[] array) {
    for (final byte b : array) {
      this.buffer[this.bufferPosition++] = b;
    }
  }

  private void put(final int value) {
    this.buffer[this.bufferPosition++] = (byte) value;
  }

  private int calcCrcForBufferedChunk() {
    CRC32 crc = new CRC32();
    crc.update(this.buffer, 4, this.bufferPosition - 4);
    return (int) crc.getValue();
  }

  public enum State {
    CREATED,
    STARTED,
    CLOSED
  }

}
