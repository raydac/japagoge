package com.igormaznitsa.japagoge;

import com.igormaznitsa.japagoge.filters.ColorFilter;
import com.igormaznitsa.japagoge.filters.NoneFilter;
import com.igormaznitsa.japagoge.gif.AGifWriter;
import com.igormaznitsa.japagoge.utils.Pair;
import com.igormaznitsa.japagoge.utils.PaletteUtils;
import com.igormaznitsa.japagoge.utils.PngMode;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

@SuppressWarnings("unused")
public class APngToGifConvertingWorker extends SwingWorker<File, Integer> {

  private static final Logger LOGGER = Logger.getLogger("APNGtoGIF");

  private final File source;
  private final File target;
  private final boolean accurateRgb;
  private final long sourceLength;
  private final AtomicLong readCounter = new AtomicLong();
  private final List<ProgressListener> listeners = new CopyOnWriteArrayList<>();
  private final ForkJoinPool forkJoinPool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());
  private final boolean dithering;
  private final int[] globalRgb256Palette;
  private final ColorFilter colorFilter;

  public APngToGifConvertingWorker(final File source, final File target, final boolean accurateRgb, final boolean dithering, final int[] globalRgbPalette, final ColorFilter forceColorFilter) {
    super();

    this.globalRgb256Palette = forceColorFilter == null ? Objects.requireNonNull(globalRgbPalette) : forceColorFilter.getPalette().orElse(globalRgbPalette);
    this.colorFilter = forceColorFilter == null ? new NoneFilter() : forceColorFilter;
    this.dithering = dithering;
    this.accurateRgb = accurateRgb;
    this.source = Objects.requireNonNull(source);
    this.target = Objects.requireNonNull(target);
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
        if (window != null) {
          if (p == Integer.MIN_VALUE) {
            JOptionPane.showMessageDialog(window, "Error during conversion, may be wrong image format!", "Error", JOptionPane.ERROR_MESSAGE);
          }
          window.setVisible(false);
        }
      } else {
        progressBar.setValue(p);
      }
    });

    panel.add(progressBar, BorderLayout.CENTER);

    return panel;
  }

  private static Pair<Integer, byte[]> ensureRgb(final ColorFilter colorFilter, final PngMode pngMode, final int width, final int height, final byte[] rgbPalette, final byte[] unpackedNormalizedRaster) {
    byte[] result = unpackedNormalizedRaster;
    int foundTransparentPaletteColor = -1;
    int lastAlpha = Integer.MAX_VALUE;
    final byte[] rgbArray = new byte[width * height * 3];
    final int bitsPerLine = pngMode.calcBitsPerLine(width) - 8;
    final int bytesPerLine = pngMode.calcBytesPerScanline(width) - 1;

    int rgbOffset = 0;
    final int bitsItemLength = pngMode.getBitsPerSample() * pngMode.getSamples();

    for (int y = 0; y < height; y++) {
      int srcOffset = y * bytesPerLine;

      long samplesAccumulator = 0L;
      int restBitsNumber = bitsItemLength;

      int bufferedBitsNumber = 0;
      int bufferedByte = 0;
      for (int x = 0; x < width; ) {
        if (bufferedBitsNumber == 0) {
          bufferedByte = unpackedNormalizedRaster[srcOffset++];
          bufferedBitsNumber = 8;
        }
        int bitsToRead = Math.min(bufferedBitsNumber, restBitsNumber);

        while (bitsToRead > 0) {
          samplesAccumulator = (samplesAccumulator << 1) | ((bufferedByte & 0x80) == 0 ? 0 : 1);
          bufferedByte <<= 1;
          restBitsNumber--;
          bitsToRead--;
          bufferedBitsNumber--;
        }

        if (restBitsNumber == 0) {
          final int alpha = pngMode.applySamplesAndGetAlpha(samplesAccumulator, rgbArray, rgbPalette, rgbOffset);

          int r = rgbArray[rgbOffset] & 0xFF;
          int g = rgbArray[rgbOffset + 1] & 0xFF;
          int b = rgbArray[rgbOffset + 2] & 0xFF;

          final int filteredRgb = colorFilter.filterRgb((r << 16) | (g << 8) | b);

          r = (filteredRgb >> 16) & 0xFF;
          g = (filteredRgb >> 8) & 0xFF;
          b = filteredRgb & 0xFF;

          rgbArray[rgbOffset] = (byte) r;
          rgbArray[rgbOffset + 1] = (byte) g;
          rgbArray[rgbOffset + 2] = (byte) b;

          if (alpha >= 0 && alpha < 255 && alpha < lastAlpha) {
            lastAlpha = alpha;
            foundTransparentPaletteColor = PaletteUtils.findClosestIndex(r, g, b, rgbPalette);
          }

          restBitsNumber = bitsItemLength;
          rgbOffset += 3;
          samplesAccumulator = 0L;
          x++;
        }
      }
      result = rgbArray;
    }
    return Pair.of(foundTransparentPaletteColor, result);
  }

  private static Inflater extractData(final Inflater inflater, final PngChunk dataChunk, final ByteArrayOutputStream outputStream) throws IOException {
    final byte[] workData;
    if (dataChunk.name.equals("IDAT")) {
      workData = dataChunk.data;
    } else if (dataChunk.name.equals("fdAT")) {
      workData = new byte[dataChunk.data.length - 4];
      System.arraycopy(dataChunk.data, 4, workData, 0, workData.length);
    } else throw new IllegalArgumentException("Unsupported data chunk: " + dataChunk.name);

    try {
      final Inflater workInflater = inflater == null ? new Inflater() : inflater;
      workInflater.setInput(workData);
      final byte[] unpackBuffer = new byte[16384];
      while (!workInflater.finished()) {
        final int unpackedLength = workInflater.inflate(unpackBuffer, 0, unpackBuffer.length);
        if (unpackedLength == 0)
          break;
        outputStream.write(unpackBuffer, 0, unpackedLength);
      }
      return workInflater.finished() ? null : workInflater;
    } catch (DataFormatException ex) {
      throw new IOException("Compressed data format error", ex);
    }
  }

  private static byte[] removeFiltration(final PngMode pngMode, final int width, final int height, final byte[] filteredPngRaster) {
    final int scanLineWidth = pngMode.calcBytesPerScanline(width);
    final int bpp = pngMode.getBytesPerPixel();

    final int bytesPerRasterLine = scanLineWidth - 1;

    final byte[] restoredRaster = new byte[bytesPerRasterLine * height];
    for (int y = 0; y < height; y++) {
      final int filteredLineOffset = y * scanLineWidth;
      final int restoredLineOffset = y * bytesPerRasterLine;
      final int scanLineFilter = filteredPngRaster[filteredLineOffset] & 0xFF;

      switch (scanLineFilter) {
        // https://www.w3.org/TR/2003/REC-PNG-20031110/#9Filters
        case 0: {
          System.arraycopy(filteredPngRaster, filteredLineOffset + 1, restoredRaster, restoredLineOffset, bytesPerRasterLine);
        }
        break;
        case 1: {
          // SUB
          System.arraycopy(filteredPngRaster, filteredLineOffset + 1, restoredRaster, restoredLineOffset, bytesPerRasterLine);
          for (int x = 0; x < bytesPerRasterLine; x++) {
            final int reconPos = restoredLineOffset + x;
            final int a = x < bpp ? 0 : restoredRaster[reconPos - bpp] & 0xFF;
            restoredRaster[reconPos] = (byte) (restoredRaster[reconPos] + a);
          }
        }
        break;
        case 2: {
          // UP
          if (y == 0) {
            System.arraycopy(filteredPngRaster, filteredLineOffset + 1, restoredRaster, restoredLineOffset, bytesPerRasterLine);
          } else {
            for (int x = 0; x < bytesPerRasterLine; x++) {
              final int reconPos = restoredLineOffset + x;
              final int b = restoredRaster[reconPos - bytesPerRasterLine] & 0xFF;
              restoredRaster[reconPos] = (byte) (filteredPngRaster[filteredLineOffset + x + 1] + b);
            }
          }
        }
        break;
        case 3: {
          // AVERAGE
          for (int x = 0; x < bytesPerRasterLine; x++) {
            final int reconOffset = restoredLineOffset + x;
            final int a = x < bpp ? 0 : restoredRaster[reconOffset - bpp] & 0xFF;
            final int b = y == 0 ? 0 : restoredRaster[reconOffset - bytesPerRasterLine] & 0xFF;
            restoredRaster[reconOffset] = (byte) ((filteredPngRaster[filteredLineOffset + 1 + x] & 0xFF) + ((a + b) >> 1));
          }
        }
        break;
        case 4: {
          // PAETH
          for (int x = 0; x < bytesPerRasterLine; x++) {
            final int reconPos = restoredLineOffset + x;
            final int a = x < bpp ? 0 : restoredRaster[reconPos - bpp] & 0xFF;
            final int b = y == 0 ? 0 : restoredRaster[reconPos - bytesPerRasterLine] & 0xFF;
            final int c = x < bpp || y == 0 ? 0 : restoredRaster[reconPos - bytesPerRasterLine - bpp] & 0xFF;
            restoredRaster[reconPos] = (byte) ((filteredPngRaster[filteredLineOffset + 1 + x] & 0xFF) + paethPredictor(a, b, c));
          }
        }
        break;
        default:
          throw new IllegalArgumentException("Unexpected filter: " + scanLineFilter);
      }
    }
    return restoredRaster;
  }

  private static int paethPredictor(int a, int b, int c) {
    final int p = a + b - c;
    final int pa = Math.abs(p - a);
    final int pb = Math.abs(p - b);
    final int pc = Math.abs(a + b - c - c);

    if (pa <= pb && pa <= pc) {
      return a;
    } else if (pb <= pc) {
      return b;
    } else return c;
  }

  private static void addArrayCell(final byte[] array, final int index, final int value) {
    array[index] = (byte) Math.max(0, Math.min(0xFF, (array[index] & 0xFF) + value));
  }

  public void addProgressListener(final ProgressListener listener) {
    this.listeners.add(listener);
  }

  @SuppressWarnings("unused")
  public void removeActionListener(final ProgressListener listener) {
    this.listeners.remove(listener);
  }

  private byte[] generateIndexTable(final byte[] rgb256Palette, final boolean accurateRgb) {

    final int[] intRgbPalette = new int[rgb256Palette.length];
    for (int i = 0; i < rgb256Palette.length; i++) {
      intRgbPalette[i] = rgb256Palette[i] & 0xFF;
    }
    final byte[] result = new byte[256 * 256 * 256];

    final int paletteItems = rgb256Palette.length / 3;
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
                for (int i = 0; i < paletteItems; i++) {
                  int offset = i * 3;

                  final int tr = intRgbPalette[offset++];
                  final int tg = intRgbPalette[offset++];
                  final int tb = intRgbPalette[offset];

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

  private byte[] replaceRgbByIndexes(final byte[] pngRaster, final byte[] rgb2IndexTable) {
    final byte[] result = new byte[pngRaster.length / 3];
    for (int i = 0; i < pngRaster.length; ) {
      final int outIndex = i / 3;
      final int r = pngRaster[i++] & 0xFF;
      final int g = pngRaster[i++] & 0xFF;
      final int b = pngRaster[i++] & 0xFF;
      result[outIndex] = rgb2IndexTable[(r << 16) | (g << 8) | b];
    }
    return result;
  }

  private byte[] convertRgbToIndexesDithering(final int width, final int height, final byte[] rgbPalette, final boolean accurateRgb, final byte[] rgbRaster) {
    final byte[] resultIndex = new byte[width * height];

    final int rgbLineLength = width * 3;

    for (int y = 0; y < height; y++) {
      final int indexLineStart = width * y;
      final int rgbLineStart = rgbLineLength * y;

      for (int x = 0; x < width; x++) {
        int pixelOffset = rgbLineStart + x * 3;
        final int rasterR = rgbRaster[pixelOffset++] & 0xFF;
        final int rasterG = rgbRaster[pixelOffset++] & 0xFF;
        final int rasterB = rgbRaster[pixelOffset] & 0xFF;

        final int paletteIndex;
        if (accurateRgb) {
          paletteIndex = PaletteUtils.findClosestIndex(rasterR, rasterG, rasterB, PaletteUtils.toY(rasterR, rasterG, rasterB), PaletteUtils.toHue(rasterR, rasterG, rasterB), rgbPalette);
        } else {
          paletteIndex = PaletteUtils.findClosestIndex(rasterR, rasterG, rasterB, rgbPalette);
        }

        resultIndex[indexLineStart + x] = (byte) paletteIndex;

        int paletteOffset = paletteIndex * 3;
        final int errorR = rasterR - (rgbPalette[paletteOffset++] & 0xFF);
        final int errorG = rasterG - (rgbPalette[paletteOffset++] & 0xFF);
        final int errorB = rasterB - (rgbPalette[paletteOffset] & 0xFF);

        if (x < width - 1) {
          // x+1, y
          pixelOffset = rgbLineStart + (x + 1) * 3;
          addArrayCell(rgbRaster, pixelOffset++, Math.round(errorR * 7.0f / 16.0f));
          addArrayCell(rgbRaster, pixelOffset++, Math.round(errorG * 7.0f / 16.0f));
          addArrayCell(rgbRaster, pixelOffset, Math.round(errorB * 7.0f / 16.0f));

          if (y < height - 1) {
            // x+1, y + 1
            pixelOffset = rgbLineStart + rgbLineLength + (x + 1) * 3;
            addArrayCell(rgbRaster, pixelOffset++, Math.round(errorR * 1.0f / 16.0f));
            addArrayCell(rgbRaster, pixelOffset++, Math.round(errorG * 1.0f / 16.0f));
            addArrayCell(rgbRaster, pixelOffset, Math.round(errorB * 1.0f / 16.0f));
          }
        }

        if (y < height - 1) {
          // x, y + 1
          pixelOffset = (rgbLineStart + rgbLineLength) + x * 3;
          addArrayCell(rgbRaster, pixelOffset++, Math.round(errorR * 5.0f / 16.0f));
          addArrayCell(rgbRaster, pixelOffset++, Math.round(errorG * 5.0f / 16.0f));
          addArrayCell(rgbRaster, pixelOffset, Math.round(errorB * 5.0f / 16.0f));
        }

        if (x > 0 && y < height - 1) {
          // x-1. y+1
          pixelOffset = (rgbLineStart + rgbLineLength) + (x - 1) * 3;
          addArrayCell(rgbRaster, pixelOffset++, Math.round(errorR * 3.0f / 16.0f));
          addArrayCell(rgbRaster, pixelOffset++, Math.round(errorG * 3.0f / 16.0f));
          addArrayCell(rgbRaster, pixelOffset, Math.round(errorB * 3.0f / 16.0f));
        }
      }
    }
    return resultIndex;
  }

  private void notifyUpdateForSizeChange() {
    final long read = this.readCounter.get();
    this.publish((int) Math.round(((double) read / (double) this.sourceLength) * 100));
  }

  private int writeRgbFrameIDAT(final AGifWriter writer, final AGifWriter.DisposalMode disposalMode, final IhdrChunk ihdrChunk, final byte[] rgb2indexTable, final byte[] rgbPalette, final byte[] unpackedData, final FctlChunk fctl) throws IOException {
    final Pair<Integer, byte[]> indexRgbArrayPair = ensureRgb(this.colorFilter, ihdrChunk.mode, ihdrChunk.width, ihdrChunk.height, rgbPalette, removeFiltration(ihdrChunk.mode, ihdrChunk.width, ihdrChunk.height, unpackedData));
    writer.addFrame(disposalMode, 0, 0, ihdrChunk.width, ihdrChunk.height, fctl.getDuration(), indexRgbArrayPair.getLeft(), replaceRgbByIndexes(indexRgbArrayPair.getRight(), rgb2indexTable));
    return indexRgbArrayPair.getLeft();
  }

  private int writeRgbFrameFDAT(final AGifWriter writer, final AGifWriter.DisposalMode disposalMode, final IhdrChunk ihdrChunk, final byte[] rgb2indexTable, final byte[] rgbPalette, final byte[] unpackedData, final FctlChunk fctl) throws IOException {
    final Pair<Integer, byte[]> indexRgbArrayPair = ensureRgb(this.colorFilter, ihdrChunk.mode, fctl.width, fctl.height, rgbPalette, removeFiltration(ihdrChunk.mode, fctl.width, fctl.height, unpackedData));
    writer.addFrame(disposalMode, fctl.x, fctl.y, fctl.width, fctl.height, fctl.getDuration(), indexRgbArrayPair.getLeft(), replaceRgbByIndexes(indexRgbArrayPair.getRight(), rgb2indexTable));
    return indexRgbArrayPair.getLeft();
  }

  private int writeRgbFrameDitheringIDAT(final AGifWriter writer, final AGifWriter.DisposalMode disposalMode,
                                         final IhdrChunk ihdrChunk, final byte[] rgbPalette, final boolean preciseRgb, final byte[] unpackedData,
                                         final FctlChunk fctl) throws IOException {
    final Pair<Integer, byte[]> indexRgbArrayPair = ensureRgb(this.colorFilter, ihdrChunk.mode, ihdrChunk.width, ihdrChunk.height, rgbPalette, removeFiltration(ihdrChunk.mode, ihdrChunk.width, ihdrChunk.height, unpackedData));
    writer.addFrame(disposalMode, 0, 0, ihdrChunk.width, ihdrChunk.height, fctl.getDuration(), indexRgbArrayPair.getLeft(), convertRgbToIndexesDithering(ihdrChunk.width, ihdrChunk.height, rgbPalette, preciseRgb, indexRgbArrayPair.getRight()));
    return indexRgbArrayPair.getLeft();
  }

  private int writeRgbFrameDitheringFDAT(final AGifWriter writer, final AGifWriter.DisposalMode disposalMode, final IhdrChunk ihdrChunk, final byte[] rgbPalette, final boolean preciseRgb, final byte[] unpackedData,
                                         final FctlChunk fctl) throws IOException {
    final Pair<Integer, byte[]> indexRgbArrayPair = ensureRgb(this.colorFilter, ihdrChunk.mode, fctl.width, fctl.height, rgbPalette, removeFiltration(ihdrChunk.mode, fctl.width, fctl.height, unpackedData));
    writer.addFrame(disposalMode, fctl.x, fctl.y, fctl.width, fctl.height, fctl.getDuration(), indexRgbArrayPair.getLeft(), convertRgbToIndexesDithering(fctl.width, fctl.height, rgbPalette, preciseRgb, indexRgbArrayPair.getRight()));
    return indexRgbArrayPair.getLeft();
  }

  private int writeIndexedFrameIDAT(final AGifWriter writer, final AGifWriter.DisposalMode disposalMode, final IhdrChunk ihdrChunk, final byte[] unpackedData, final FctlChunk fctl, final int transparentIndex) throws IOException {
    writer.addFrame(disposalMode, 0, 0, ihdrChunk.width, ihdrChunk.height, fctl.getDuration(), transparentIndex, removeFiltration(ihdrChunk.mode, ihdrChunk.width, ihdrChunk.height, unpackedData));
    return transparentIndex;
  }

  private int writeIndexedFrameFDAT(final AGifWriter writer, final AGifWriter.DisposalMode disposalMode, final IhdrChunk ihdrChunk, final byte[] unpackedData, final FctlChunk fctl, final int transparentIndex) throws IOException {
    writer.addFrame(disposalMode, fctl.x, fctl.y, fctl.width, fctl.height, fctl.getDuration(), transparentIndex, removeFiltration(ihdrChunk.mode, fctl.width, fctl.height, unpackedData));
    return transparentIndex;
  }

  private int calcExpectedRasterDataSize(final IhdrChunk ihdrChunk, final FctlChunk fctlChunk, final PngChunk dataChunk) {
    final int width;
    final int height;
    if (dataChunk.name.equals("IDAT")) {
      width = ihdrChunk.width;
      height = ihdrChunk.height;
    } else {
      width = fctlChunk.width;
      height = fctlChunk.height;
    }
    return ihdrChunk.mode.calcRasterDataSize(width, height);
  }

  @Override
  protected File doInBackground() {
    try (final DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(this.source)))) {
      assertNext(in, this.readCounter, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);

      byte[] workRgbPalette = null;

      IhdrChunk ihdrChunk = null;
      FctlChunk fctlChunk = null;
      ActlChunk actlChunk = null;

      AGifWriter gifWriter = null;

      byte[] rgb2indexTable = null;

      ByteArrayOutputStream frameDataBuffer = new ByteArrayOutputStream(16384);
      Inflater currentDataInflater = null;

      int transparentPaletteIndex = -1;

      boolean japagogeGeneratedSource = false;

      try (final OutputStream output = new BufferedOutputStream(new FileOutputStream(this.target))) {
        mainLoop:
        do {
          final PngChunk nextChunk = new PngChunk(in, this.readCounter);
          switch (nextChunk.name) {
            case "tRNS": {
              int foundIndex = -1;
              int minAlpha = Integer.MAX_VALUE;
              for (int i = 0; i < nextChunk.data.length; i++) {
                final int curAlpha = nextChunk.data[i] & 0xFF;
                if (curAlpha < minAlpha) {
                  foundIndex = i;
                  minAlpha = curAlpha;
                  if (curAlpha == 0) break;
                }
              }
              if (foundIndex >= 0) {
                transparentPaletteIndex = foundIndex;
              }
            }
            break;
            case "tEXt": {
              final String text = new String(nextChunk.data, StandardCharsets.US_ASCII).toLowerCase(Locale.ENGLISH);
              if (text.startsWith("software") && text.endsWith("japagoge")) {
                LOGGER.info("Detected JAPAGOGE producer record in PNG");
              }
            }
            break;
//            case "bKGD": {
//              if (nextChunk.data.length == 1) {
//                transparentPaletteIndex = nextChunk.data[0] & 0xFF;
//              } else if (nextChunk.data.length == 3 && workRgbPalette != null) {
//                transparentPaletteIndex = PaletteUtils.findClosestIndex(
//                        nextChunk.data[0] & 0xFF,
//                        nextChunk.data[1] & 0xFF,
//                        nextChunk.data[2] & 0xFF,
//                        workRgbPalette
//                );
//              }
//            }
//            break;
            case "IDAT":
            case "fdAT": {
              if (gifWriter == null) {
                final byte[] generatedPalette;
                switch (Objects.requireNonNull(ihdrChunk, "Can't find IHDR before image").mode) {
                  case MODE_GRAYSCALE_1:
                  case MODE_GRAYSCALE_2:
                  case MODE_GRAYSCALE_4:
                  case MODE_GRAYSCALE_8:
                  case MODE_GRAYSCALE_16:
                  case MODE_GRAYSCALE_ALPHA_8:
                  case MODE_GRAYSCALE_ALPHA_16: {
                    generatedPalette = new byte[256 * 3];
                    for (int i = 0; i < 256; i++) {
                      int index = i * 3;
                      generatedPalette[index++] = (byte) i;
                      generatedPalette[index++] = (byte) i;
                      generatedPalette[index] = (byte) i;
                    }
                  }
                  break;
                  case MODE_RGB_8:
                  case MODE_RGB_16:
                  case MODE_RGB_ALPHA_8:
                  case MODE_RGB_ALPHA_16: {
                    generatedPalette = PaletteUtils.splitRgb(this.globalRgb256Palette);
                    if (this.dithering) {
                      LOGGER.info("Don't build RGB index table because dithering mode");
                    } else {
                      LOGGER.info("Starting calculate RGB to index table, accurate RGB is " + this.accurateRgb);
                      final long startTime = System.currentTimeMillis();
                      rgb2indexTable = this.generateIndexTable(generatedPalette, this.accurateRgb);
                      if (rgb2indexTable == null) {
                        LOGGER.severe("Calculation of RGB index table has been interrupted so that interrupting conversion");
                        break mainLoop;
                      }
                      LOGGER.info("Calculate RGB to index table completed, took " + (System.currentTimeMillis() - startTime) + "ms");
                    }
                  }
                  break;
                  case MODE_INDEX_1:
                  case MODE_INDEX_2:
                  case MODE_INDEX_4:
                  case MODE_INDEX_8: {
                    generatedPalette = workRgbPalette == null ? PaletteUtils.splitRgb(this.globalRgb256Palette) : workRgbPalette;
                  }
                  break;
                  default:
                    throw new IOException("Unexpected color type: " + ihdrChunk.colorType);
                }
                this.publish(0);
                workRgbPalette = generatedPalette;
                gifWriter = new AGifWriter(output, ihdrChunk.width, ihdrChunk.height, 0, workRgbPalette, actlChunk == null ? 0 : actlChunk.numPlays);
              }

              final int expectedRasterDataSize = calcExpectedRasterDataSize(ihdrChunk, fctlChunk, nextChunk);

              currentDataInflater = extractData(currentDataInflater, nextChunk, frameDataBuffer);

              if (currentDataInflater == null) {
                final byte[] raster = frameDataBuffer.toByteArray();
                frameDataBuffer.reset();

                final AGifWriter.DisposalMode disposalMode;
                if (fctlChunk.width < ihdrChunk.width || fctlChunk.height < ihdrChunk.height) {
                  if (transparentPaletteIndex < 0) {
                    disposalMode = AGifWriter.DisposalMode.NOT_SPECIFIED;
                  } else {
                    disposalMode = AGifWriter.DisposalMode.OVERWRITE_BY_BACKGROUND_COLOR;
                  }
                } else {
                  disposalMode = AGifWriter.DisposalMode.NOT_SPECIFIED;
                }

                final int foundTransparentPaletteIndex;

                switch (ihdrChunk.mode) {
                  case MODE_GRAYSCALE_8:
                  case MODE_INDEX_8: {
                    // palette or grayscale
                    if (nextChunk.name.equals("IDAT")) {
                      foundTransparentPaletteIndex = this.writeIndexedFrameIDAT(gifWriter, disposalMode, ihdrChunk, raster, fctlChunk, transparentPaletteIndex);
                    } else {
                      foundTransparentPaletteIndex = this.writeIndexedFrameFDAT(gifWriter, disposalMode, ihdrChunk, raster, fctlChunk, transparentPaletteIndex);
                    }
                  }
                  break;
                  default: {
                    // rgb
                    if (nextChunk.name.equals("IDAT")) {
                      if (this.dithering) {
                        foundTransparentPaletteIndex = this.writeRgbFrameDitheringIDAT(gifWriter, disposalMode, ihdrChunk, workRgbPalette, this.accurateRgb, raster, fctlChunk);
                      } else {
                        foundTransparentPaletteIndex = this.writeRgbFrameIDAT(gifWriter, disposalMode, ihdrChunk, rgb2indexTable, workRgbPalette, raster, fctlChunk);
                      }
                    } else {
                      if (this.dithering) {
                        foundTransparentPaletteIndex = this.writeRgbFrameDitheringFDAT(gifWriter, disposalMode, ihdrChunk, workRgbPalette, this.accurateRgb, raster, fctlChunk);
                      } else {
                        foundTransparentPaletteIndex = this.writeRgbFrameFDAT(gifWriter, disposalMode, ihdrChunk, rgb2indexTable, workRgbPalette, raster, fctlChunk);
                      }
                    }
                  }
                  break;
                }
                if (transparentPaletteIndex < 0 && foundTransparentPaletteIndex >= 0) {
                  transparentPaletteIndex = foundTransparentPaletteIndex;
                }

              }

              notifyUpdateForSizeChange();
            }
            break;
            case "PLTE": {
              workRgbPalette = new byte[nextChunk.data.length];
              System.arraycopy(nextChunk.data, 0, workRgbPalette, 0, workRgbPalette.length);
              workRgbPalette = this.colorFilter.filterRgbPalette(workRgbPalette);
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
              fctlChunk = new FctlChunk(ihdrChunk.width, ihdrChunk.height);
              LOGGER.info("Detected IHDR: " + ihdrChunk);
            }
            break;
          }
        } while (!Thread.currentThread().isInterrupted());
        if (gifWriter != null) {
          gifWriter.end();
        }
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
    final PngMode mode;

    IhdrChunk(final PngChunk chunk) throws IOException {
      try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(chunk.data))) {
        this.width = in.readInt();
        this.height = in.readInt();
        this.bitDepth = in.readUnsignedByte();
        this.colorType = in.readUnsignedByte();
        this.compression = in.readUnsignedByte();
        this.filter = in.readUnsignedByte();
        this.interlace = in.readUnsignedByte();

        try {
          this.mode = PngMode.find(this.colorType, this.bitDepth);
        } catch (IllegalArgumentException ex) {
          throw new IOException("Unknown type of PNG file");
        }
      }
    }

    @Override
    public String toString() {
      return "IhdrChunk(width=" + this.width + ",height=" + this.height + ",bitDepth=" + bitDepth + ",colorType=" + this.colorType + ",compression=" + this.compression + ",filter=" + this.filter + ",interlace=" + this.interlace + ')';
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

    FctlChunk(final int imageWidth, final int imageHeight) {
      this.sequence = 0;
      this.width = imageWidth;
      this.height = imageHeight;
      this.x = 0;
      this.y = 0;
      this.delayNum = 1;
      this.delayDen = 100;
      this.disposeOp = -1;
      this.blendOp = 0;
    }

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

    @Override
    public String toString() {
      return "PngChunk(name=" + this.name + ",data=" + this.data.length + ')';
    }
  }
}
