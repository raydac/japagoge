package com.igormaznitsa.japagoge;

import com.igormaznitsa.japagoge.filters.RgbPixelFilter;
import com.igormaznitsa.japagoge.mouse.MouseInfoProvider;
import com.igormaznitsa.japagoge.utils.Palette256;
import com.igormaznitsa.japagoge.utils.PaletteUtils;

import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.time.Duration;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ScreenCapturer {

  private static final Logger LOGGER = Logger.getLogger("ScreenCapturer");

  private static final Timer internalTimer = new Timer("capture-timer", true);
  private APngWriter.Statistics pngStatistics;

  private final Robot robot;
  private final Rectangle screenArea;
  private final File targetFile;
  private final Duration delayBetweenFrames;
  private final Duration delayBetweenCaptures;
  private final RgbPixelFilter filter;
  private final AtomicReference<TimerTask> timerTask = new AtomicReference<>();
  private final AtomicReference<APngWriter> apngWriter = new AtomicReference<>();
  private final MouseInfoProvider mouseInfoProvider;
  private final Palette256 palette;
  private final boolean dithering;

  public ScreenCapturer(
          final GraphicsDevice device,
          final Rectangle screenArea,
          final File targetFile,
          final MouseInfoProvider mouseInfoProvider,
          final RgbPixelFilter filter,
          final boolean dithering,
          final Palette256 palette,
          final Duration delayBetweenCaptures,
          final Duration delayBetweenFrames
  ) throws AWTException {
    this.dithering = dithering;
    this.palette = palette;
    this.robot = new Robot(device);
    this.filter = filter;
    this.mouseInfoProvider = mouseInfoProvider;
    this.screenArea = Objects.requireNonNull(screenArea);
    this.targetFile = targetFile;
    this.delayBetweenCaptures = delayBetweenCaptures;
    this.delayBetweenFrames = Objects.requireNonNull(delayBetweenFrames);
  }

  public RgbPixelFilter getFilter() {
    return this.filter;
  }

  public File getTargetFile() {
    return this.targetFile;
  }

  public void start() {
    final TimerTask newTimerTask = new TimerTask() {
      @Override
      public void run() {
        doCapture();
      }
    };

    if (this.timerTask.compareAndSet(null, newTimerTask)) {
      final FileChannel fileChannel;
      try {
        fileChannel = new FileOutputStream(this.targetFile).getChannel();
      } catch (IOException ex) {
        LOGGER.log(Level.SEVERE, "Can't gen file channel", ex);
        throw new Error(ex);
      }
      var newWriter = new APngWriter(fileChannel, this.filter);

      if (!this.apngWriter.compareAndSet(null, newWriter)) {
        throw new Error("Unexpected state");
      }

      LOGGER.info("Staring capture task, delay " + this.delayBetweenCaptures.toMillis() + " ms");
      internalTimer.scheduleAtFixedRate(newTimerTask, 50L, this.delayBetweenCaptures.toMillis());
    }
  }

  private void doCapture() {
    try {
      var image = this.robot.createScreenCapture(this.screenArea);

      if (this.mouseInfoProvider != null) {
        var mouseLocation = this.mouseInfoProvider.getMousePointerLocation();
        var mousePointer = this.mouseInfoProvider.getMousePointerIcon();
        var pointerRectangle = new Rectangle(mousePointer.toHot(mouseLocation), new Dimension(mousePointer.getWidth(), mousePointer.getHeight()));
        if (this.screenArea.intersects(pointerRectangle)) {
          var gfx = image.createGraphics();
          try {
            gfx.drawImage(mousePointer.getImage(), mouseLocation.x - this.screenArea.x, mouseLocation.y - this.screenArea.y, null);
          } finally {
            gfx.dispose();
          }
        }
      }

      final APngWriter writer = this.apngWriter.get();
      if (writer != null) {
        if (writer.getState() == APngWriter.State.CREATED) {
          writer.start(image.getWidth(), image.getHeight());
        }
        writer.addFrame(image, this.dithering, this.delayBetweenFrames);
      }
    } catch (Exception ex) {
      LOGGER.log(Level.SEVERE, "Error during capturing", ex);
    }
  }

  public synchronized int[] makeGlobalRgb256Palette() {
    LOGGER.info("Make global RGB palette: " + this.palette);
    return this.filter.get().getPalette()
            .orElseGet(() -> {
                      if (this.pngStatistics == null) {
                        LOGGER.severe("PMG statistics in NULL, making grayscale palette");
                        return PaletteUtils.makeGrayscaleRgb256();
                      } else {
                        LOGGER.info("Using RGB palette: " + palette);
                        return palette.getPalette().orElseGet(() -> this.pngStatistics.colorStatistics.makeAutoPalette());
                      }
                    }
            );
  }

  public boolean isStarted() {
    return this.timerTask.get() != null;
  }

  public void stop(final int loops) {
    var startedTimerTask = this.timerTask.getAndSet(null);
    if (startedTimerTask != null) {
      startedTimerTask.cancel();
    }
    var apngWriter = this.apngWriter.getAndSet(null);
    if (apngWriter != null) {
      try {
        final APngWriter.Statistics statistics = apngWriter.close(loops);
        if (statistics == null) {
          LOGGER.severe("No write data");
          this.pngStatistics = null;
        } else {
          this.pngStatistics = statistics;
          LOGGER.info(String.format("Image(%s) %dx%d, buffer %d bytes, %d frames, length %d bytes",
                  this.filter.name(),
                  statistics.width,
                  statistics.height,
                  statistics.bufferSize,
                  statistics.frames,
                  statistics.size
          ));
        }
      } catch (Exception ex) {
        LOGGER.log(Level.SEVERE, "Error during close writer", ex);
      }
    }
  }

  public boolean hasStatistics() {
    return this.pngStatistics != null;
  }
}
