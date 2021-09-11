package com.igormaznitsa.japagoge;

import com.igormaznitsa.japagoge.gif.AGifWriter;
import com.igormaznitsa.japagoge.utils.PaletteUtils;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import java.util.zip.Inflater;

public class APngToGifConvertingWorker extends SwingWorker<File, Integer> {

  private static final Logger LOGGER = Logger.getLogger("APNGtoGIF");

  private final ScreenCapturer screenCapturer;
  private final File target;
  private final boolean accurateRgb;
  private final long sourceLength;
  private final AtomicLong readCounter = new AtomicLong();
  private final List<ProgressListener> listeners = new CopyOnWriteArrayList<>();
  private final ForkJoinPool forkJoinPool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());

  public APngToGifConvertingWorker(final ScreenCapturer screenCapturer, final boolean accurateRgb, final File target) {
    super();
    this.accurateRgb = accurateRgb;
    this.screenCapturer = screenCapturer;
    this.target = target;
    this.sourceLength = screenCapturer.getTargetFile().length();
  }

  private static void assertNext(final DataInputStream in, final AtomicLong counter, final int... values) throws IOException {
    for (final int i : values) {
      if (in.readUnsignedByte() != i) {
        throw new IllegalArgumentException("Can't find " + i + " at position " + counter.incrementAndGet());
      } else {
        counter.incrementAndGet();
      }
    }
  }

  private static int readInt(final DataInputStream in, final AtomicLong counter) throws IOException {
    final int result = in.readInt();
    counter.addAndGet(4);
    return result;
  }

  private static int readByte(final DataInputStream in, final AtomicLong counter) throws IOException {
    final int result = in.readUnsignedByte();
    counter.incrementAndGet();
    return result;
  }

  public static JPanel makePanelFor(final APngToGifConvertingWorker converter) {
    final JPanel panel = new JPanel(new BorderLayout());
    final JProgressBar progressBar = new JProgressBar(0, 100);
    final Dimension progressBarPreferredSize = progressBar.getPreferredSize();
    progressBarPreferredSize.width += progressBarPreferredSize.width;
    progressBar.setMinimumSize(progressBarPreferredSize);
    progressBar.setPreferredSize(progressBarPreferredSize);
    progressBar.setIndeterminate(true);

    converter.addProgressListener((g, p) -> {
      if (p >= 0) {
        progressBar.setIndeterminate(false);
      }
      if (p > 100 || p < 0) {
        var window = SwingUtilities.windowForComponent(panel);
        if (window != null) window.setVisible(false);
      } else {
        progressBar.setValue(p);
      }
    });

    panel.add(progressBar, BorderLayout.CENTER);

    return panel;
  }

  private static byte[] extractDataFromIndexed(final int width, final int height, final byte[] pngFrameData) {
    final byte[] result = new byte[width * height];
    Inflater inflater = new Inflater();
    inflater.setInput(pngFrameData);
    final byte[] unpacked = new byte[(width * height) + height];
    try {
      final int length = inflater.inflate(unpacked);
      if (length != unpacked.length) throw new IllegalStateException("Unexpected unpacked frame length");
    } catch (Exception ex) {
      throw new RuntimeException("Can't decompress frame", ex);
    }
    for (int y = 0; y < height; y++) {
      System.arraycopy(unpacked, y * (width + 1) + 1, result, y * width, width);
    }
    return result;
  }

  private byte[] generateIndexTable(final int[] rgb256Palette, final boolean accurateRgb) {
    final int paletteLength = rgb256Palette.length;

    final int[] splitRgbPalette = new int[rgb256Palette.length * 3];
    for (int i = 0; i < paletteLength; i++) {
      final int r = (rgb256Palette[i] >> 16) & 0xFF;
      final int g = (rgb256Palette[i] >> 8) & 0xFF;
      final int b = rgb256Palette[i] & 0xFF;
      int offset = i * 3;
      splitRgbPalette[offset++] = r;
      splitRgbPalette[offset++] = g;
      splitRgbPalette[offset] = b;
    }

    final byte[] result = new byte[256 * 256 * 256];

    try {
      this.forkJoinPool.submit(() -> IntStream.range(0, 256 * 256 * 256)
              .parallel()
              .mapToLong(rgb -> {
                final int r = (rgb >> 16) & 0xFF;
                final int g = (rgb >> 8) & 0xFF;
                final int b = rgb & 0xFF;

                final int y;
                final float h;
                if (accurateRgb) {
                  y = PaletteUtils.toY(r, g, b);
                  h = PaletteUtils.toHue(r, g, b);
                } else {
                  y = 0;
                  h = 0;
                }
                float distance = Float.MAX_VALUE;

                int foundPaletteIndex = 0;
                for (int i = 0; i < paletteLength; i++) {
                  int offset = i * 3;

                  final int tr = splitRgbPalette[offset++];
                  final int tg = splitRgbPalette[offset++];
                  final int tb = splitRgbPalette[offset];

                  final float distanceRgb;
                  if (accurateRgb) {
                    distanceRgb = PaletteUtils.calcAccurateRgbDistance(r, g, b, y, h, tr, tg, tb);
                  } else {
                    final int dr = r - tr;
                    final int dg = g - tg;
                    final int db = b - tb;
                    distanceRgb = dr * dr + dg * dg + db * db;
                  }

                  if (distanceRgb < distance) {
                    foundPaletteIndex = i;
                    distance = distanceRgb;
                  }
                }
                return ((long) rgb << 32) | foundPaletteIndex;
              })
              .peek(value -> {
                if (this.isCancelled()) {
                  forkJoinPool.shutdownNow();
                }
              })
              .forEach(rgbIndex -> {
                synchronized (result) {
                  result[(int) (rgbIndex >> 32)] = (byte) rgbIndex;
                }
              })).get();
    } catch (InterruptedException | CancellationException ex) {
      LOGGER.severe(" RGB index table stream has been interrupted");
      return null;
    } catch (Exception ex) {
      LOGGER.log(Level.SEVERE, "Exception during RGB table calculation", ex);
      throw new RuntimeException("Interrupted by exception", ex);
    }
    return result;
  }

  public void addProgressListener(final ProgressListener listener) {
    this.listeners.add(listener);
  }

  @SuppressWarnings("unused")
  public void removeActionListener(final ProgressListener listener) {
    this.listeners.remove(listener);
  }

  private byte[] convertRgbToIndexes(final int width, final int height, final byte[] pngFrameData, final byte[] rgb2IndexTable) {
    final byte[] result = new byte[width * height];
    Inflater inflater = new Inflater();
    inflater.setInput(pngFrameData);
    final byte[] unpacked = new byte[(width * 3 * height) + height];
    try {
      final int length = inflater.inflate(unpacked);
      if (length != unpacked.length) throw new IllegalStateException("Unexpected unpacked frame length");
    } catch (Exception ex) {
      throw new RuntimeException("Can't decompress frame", ex);
    }
    final int scanLineWidth = width * 3;
    for (int y = 0; y < height; y++) {
      final int lineOffset = y * (scanLineWidth + 1);
      int ox = y * width;
      for (int x = 1; x <= scanLineWidth; x += 3) {
        final int r = unpacked[lineOffset + x] & 0xFF;
        final int g = unpacked[lineOffset + x + 1] & 0xFF;
        final int b = unpacked[lineOffset + x + 2] & 0xFF;
        result[ox++] = rgb2IndexTable[(r << 16) | (g << 8) | b];
      }
    }
    return result;
  }

  private void notifyUpdateForSizeChange() {
    final long read = this.readCounter.get();
    this.publish((int) Math.round(((double) read / (double) this.sourceLength) * 100));
  }

  private void writeRgbFrame(final AGifWriter writer, final IhdrChunk ihdrChunk, final byte[] rgb2indexTable, final PngChunk dataChunk, final FctlChunk fctl) throws IOException {
    if (dataChunk.name.equals("IDAT")) {
      writer.addFrame(0, 0, ihdrChunk.width, ihdrChunk.height, fctl.getDuration(), convertRgbToIndexes(ihdrChunk.width, ihdrChunk.height, dataChunk.data, rgb2indexTable));
    } else {
      final byte[] rawData = new byte[dataChunk.data.length - 4];
      System.arraycopy(dataChunk.data, 4, rawData, 0, rawData.length);
      writer.addFrame(fctl.x, fctl.y, fctl.width, fctl.height, fctl.getDuration(), convertRgbToIndexes(fctl.width, fctl.height, rawData, rgb2indexTable));
    }
  }

  private void writeIndexedFrame(final AGifWriter writer, final IhdrChunk ihdrChunk, final PngChunk dataChunk, final FctlChunk fctl) throws IOException {
    if (dataChunk.name.equals("IDAT")) {
      writer.addFrame(0, 0, ihdrChunk.width, ihdrChunk.height, fctl.getDuration(), extractDataFromIndexed(ihdrChunk.width, ihdrChunk.height, dataChunk.data));
    } else {
      final byte[] rawData = new byte[dataChunk.data.length - 4];
      System.arraycopy(dataChunk.data, 4, rawData, 0, rawData.length);
      writer.addFrame(fctl.x, fctl.y, fctl.width, fctl.height, fctl.getDuration(), extractDataFromIndexed(fctl.width, fctl.height, rawData));
    }
  }

  @Override
  protected File doInBackground() {
    try (final DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(this.screenCapturer.getTargetFile())))) {
      assertNext(in, this.readCounter, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);

      int[] workRgbPalette = null;

      IhdrChunk ihdrChunk = null;
      FctlChunk fctlChunk = null;
      ActlChunk actlChunk = null;

      AGifWriter gifWriter = null;

      byte[] rgb2indexTable = null;

      try (final OutputStream output = new BufferedOutputStream(new FileOutputStream(this.target))) {
        mainLoop:
        do {
          final PngChunk nextChunk = new PngChunk(in, this.readCounter);
          switch (nextChunk.name) {
            case "IDAT":
            case "fdAT": {
              if (gifWriter == null) {
                final int[] rgbPalette;
                switch (Objects.requireNonNull(ihdrChunk, "Can't find IHDR before image").colorType) {
                  case 0: { // grayscale
                    rgbPalette = new int[256];
                    for (int i = 0; i < 256; i++) {
                      rgbPalette[i] = (i << 16) | (i << 8) | i;
                    }
                  }
                  break;
                  case 2: { // rgb
                    rgbPalette = this.screenCapturer.makeGlobalRgb256Palette();
                    LOGGER.info("Starting calculate RGB to index table, accurate RGB is " + this.accurateRgb);
                    final long startTime = System.currentTimeMillis();
                    rgb2indexTable = this.generateIndexTable(rgbPalette, this.accurateRgb);
                    if (rgb2indexTable == null) {
                      LOGGER.severe("Calculation of RGB index table has been interrupted so that interrupting conversion");
                      break mainLoop;
                    }
                    LOGGER.info("Calculate RGB to index table completed, took " + (System.currentTimeMillis() - startTime) + "ms");
                  }
                  break;
                  case 3: { // palette
                    rgbPalette = workRgbPalette == null ? this.screenCapturer.makeGlobalRgb256Palette() : workRgbPalette;
                  }
                  break;
                  default:
                    throw new IOException("Unexpected color type: " + ihdrChunk.colorType);
                }
                this.publish(0);
                workRgbPalette = rgbPalette;
                gifWriter = new AGifWriter(output, ihdrChunk.width, ihdrChunk.height, 0, workRgbPalette, actlChunk == null ? 0 : actlChunk.numPlays);
              }
              switch (ihdrChunk.colorType) {
                case 2: {
                  // rgb
                  this.writeRgbFrame(gifWriter, ihdrChunk, rgb2indexTable, nextChunk, fctlChunk);
                }
                break;
                case 0:
                case 3: {
                  // palette or grayscale
                  this.writeIndexedFrame(gifWriter, ihdrChunk, nextChunk, fctlChunk);
                }
                break;
                default:
                  throw new IllegalArgumentException("Unexpected color mode: " + ihdrChunk.colorType);
              }
              notifyUpdateForSizeChange();
            }
            break;
            case "PLTE": {
              final int paletteItems = nextChunk.data.length / 3;
              int index = 0;
              workRgbPalette = new int[paletteItems];
              for (int i = 0; i < nextChunk.data.length; ) {
                int r = nextChunk.data[i++] & 0xFF;
                int g = nextChunk.data[i++] & 0xFF;
                int b = nextChunk.data[i++] & 0xFF;
                workRgbPalette[index++] = (r << 16) | (g << 8) | b;
              }
            }
            break;
            case "acTL": {
              actlChunk = new ActlChunk(nextChunk);
            }
            break;
            case "fcTL": {
              fctlChunk = new FctlChunk(nextChunk);
            }
            break;
            case "IEND": {
              break mainLoop;
            }
            case "IHDR": {
              ihdrChunk = new IhdrChunk(nextChunk);
            }
            break;
          }
        } while (!Thread.currentThread().isInterrupted());
        output.flush();
      }
      this.publish(Integer.MAX_VALUE);
    } catch (Exception ex) {
      LOGGER.log(Level.SEVERE, "Error during GIF conversion", ex);
      this.publish(Integer.MIN_VALUE);
    }
    return this.target;
  }

  @Override
  protected void process(final List<Integer> chunks) {
    chunks.forEach(x -> this.listeners.forEach(a -> a.onProgress(this, x)));
  }

  public void dispose() {
    this.cancel(true);
    this.forkJoinPool.shutdownNow();
  }

  @FunctionalInterface
  public interface ProgressListener {
    void onProgress(APngToGifConvertingWorker converter, int progress);
  }

  private static class ActlChunk {
    final int numFrames;
    final int numPlays;

    ActlChunk(final PngChunk chunk) throws IOException {
      try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(chunk.data))) {
        this.numFrames = in.readInt();
        this.numPlays = in.readInt();
      }
    }

  }

  private static class IhdrChunk {
    final int width;
    final int height;
    final int bitDepth;
    final int colorType;
    final int compression;
    final int filter;
    final int interlace;

    IhdrChunk(final PngChunk chunk) throws IOException {
      try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(chunk.data))) {
        this.width = in.readInt();
        this.height = in.readInt();
        this.bitDepth = in.readUnsignedByte();
        this.colorType = in.readUnsignedByte();
        this.compression = in.readUnsignedByte();
        this.filter = in.readUnsignedByte();
        this.interlace = in.readUnsignedByte();
      }
    }

  }

  private static class FctlChunk {
    final int sequence;
    final int width;
    final int height;
    final int x;
    final int y;
    final int delayNum;
    final int delayDen;
    final int disposeOp;
    final int blendOp;

    FctlChunk(final PngChunk chunk) throws IOException {
      try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(chunk.data))) {
        this.sequence = in.readInt();
        this.width = in.readInt();
        this.height = in.readInt();
        this.x = in.readInt();
        this.y = in.readInt();
        this.delayNum = in.readUnsignedShort();
        this.delayDen = in.readUnsignedShort();
        this.disposeOp = in.readUnsignedByte();
        this.blendOp = in.readUnsignedByte();
      }
    }

    public Duration getDuration() {
      return Duration.ofMillis(Math.round(1000.0d * ((double) this.delayNum / (double) (this.delayDen == 0 ? 100 : this.delayDen))));
    }
  }

  private static class PngChunk {
    private final String name;
    private final byte[] data;

    @SuppressWarnings("unused")
    public PngChunk(final DataInputStream in, final AtomicLong counter) throws IOException {
      final int dataLength = in.readInt();
      final StringBuilder nameBuffer = new StringBuilder(4);
      for (int i = 0; i < 4; i++) {
        nameBuffer.append((char) readByte(in, counter));
      }
      this.name = nameBuffer.toString();
      this.data = new byte[dataLength];
      in.readFully(this.data);
      counter.addAndGet(dataLength);
      var crc = readInt(in, counter);
    }
  }
}
