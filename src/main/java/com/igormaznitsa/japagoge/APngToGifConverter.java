package com.igormaznitsa.japagoge;

import com.igormaznitsa.japagoge.gif.AGifWriter;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

public class APngToGifConverter extends SwingWorker<File, Integer> {

  private static final Logger LOGGER = Logger.getLogger("APNGtoGIF");

  private final File source;
  private final File target;
  private final long sourceLength;
  private final AtomicLong readCounter = new AtomicLong();

  private final List<ProgressListener> listeners = new CopyOnWriteArrayList<>();

  public APngToGifConverter(final File source, final File target) {
    super();
    this.source = source;
    this.target = target;
    this.sourceLength = source.length();
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

  private static void skipBytes(final int bytes, final DataInputStream in, final AtomicLong counter) throws IOException {
    counter.addAndGet(in.skipBytes(bytes));
  }

  public static JPanel makePanelFor(final APngToGifConverter converter) {
    final JPanel panel = new JPanel(new BorderLayout());
    final JProgressBar progressBar = new JProgressBar(0, 100);

    converter.addProgressListener((g, p) -> {
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

  public void addProgressListener(final ProgressListener listener) {
    this.listeners.add(listener);
  }

  public void removeActionListener(final ProgressListener listener) {
    this.listeners.remove(listener);
  }

  private void notifyUpdate() {
    final long read = this.readCounter.get();
    this.publish((int) Math.round(((double) read / (double) this.sourceLength) * 100));
  }

  private void writeGrayscaleFrame(final FileChannel out, final PngChunk chunk, final PngChunk fctlChuтk) throws IOException {

  }

  private void writeRgbFrame(final FileChannel out, final PngChunk chunk, final PngChunk fctlChuтk) throws IOException {

  }

  private void writePaletteFrame(final FileChannel out, final int[] rgbPalette, final PngChunk chunk, final PngChunk fctlChuтk) throws IOException {

  }

  @Override
  protected File doInBackground() {
    try (final DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(this.source)))) {
      assertNext(in, this.readCounter, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);

      final Map<String, PngChunk> specialChunks = new HashMap<>();

      boolean headerMeet = false;
      int imageWidth = -1;
      int imageHeight = -1;
      int colorMode = -1;
      int[] rgbPalette = null;

      PngChunk lastFctl = null;

      AGifWriter gifWriter = null;

      try (final FileChannel output = new FileOutputStream(this.source).getChannel()) {
        mainLoop:
        do {
          final PngChunk nextChunk = new PngChunk(in, this.readCounter);
          switch (nextChunk.name) {
            case "IDAT":
            case "fdAT": {
              switch (colorMode) {
                case 0: {
                  // grayscale
                  this.writeGrayscaleFrame(output, nextChunk, lastFctl);
                }
                break;
                case 2: {
                  // rgb
                  this.writeRgbFrame(output, nextChunk, lastFctl);
                }
                break;
                case 3: {
                  // palette
                  this.writePaletteFrame(output, rgbPalette, nextChunk, lastFctl);
                }
                break;
                default:
                  throw new IllegalArgumentException("Unexpected color mode: " + colorMode);
              }
            }
            break;
            case "PLTE": {
              final int paletteItems = nextChunk.data.length / 3;
              int index = 0;
              rgbPalette = new int[paletteItems];
              for (int i = 0; i < nextChunk.data.length; ) {
                int r = nextChunk.data[i++] & 0xFF;
                int g = nextChunk.data[i++] & 0xFF;
                int b = nextChunk.data[i++] & 0xFF;
                rgbPalette[index++] = (r << 16) | (g << 8) | b;
              }
            }
            break;
            case "fcTL": {
              lastFctl = nextChunk;
            }
            break;
            case "IEND": {
              break mainLoop;
            }
            case "IHDR": {
              headerMeet = true;
              try (final DataInputStream lin = new DataInputStream(new ByteArrayInputStream(nextChunk.data))) {
                imageWidth = lin.readInt();
                imageHeight = lin.readInt();
                if (lin.readUnsignedByte() != 8) {
                  LOGGER.severe("Unsupported pixel bit depth");
                  throw new IllegalArgumentException("Unsupported pixel bit depth");
                }
                colorMode = lin.readUnsignedByte();
              }
            }
            break;
            default: {
              specialChunks.put(nextChunk.name, nextChunk);
            }
            break;
          }
          notifyUpdate();
        } while (Thread.currentThread().isInterrupted());
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
    chunks.forEach(x -> {
      this.listeners.forEach(a -> a.onProgress(this, x));
    });
  }

  @FunctionalInterface
  public interface ProgressListener {
    void onProgress(APngToGifConverter converter, int progress);
  }

  private static class PngChunk {
    private final String name;
    private final byte[] data;

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
