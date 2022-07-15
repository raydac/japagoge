package com.igormaznitsa.japagoge;

import com.igormaznitsa.japagoge.filters.RgbPixelFilter;
import com.igormaznitsa.japagoge.grabbers.ScreenAreaGrabber;
import com.igormaznitsa.japagoge.grabbers.ScreenAreaGrabberFactory;
import com.igormaznitsa.japagoge.mouse.MouseInfoProvider;
import com.igormaznitsa.japagoge.utils.Palette256;
import com.igormaznitsa.japagoge.utils.PaletteUtils;
import java.awt.AWTException;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
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
  private final ScreenAreaGrabber screenAreaGrabber;
  private final Rectangle screenArea;
  private final File targetFile;
  private final Duration delayBetweenFrames;
  private final Duration delayBetweenCaptures;
  private final RgbPixelFilter filter;
  private final AtomicReference<TimerTask> timerTask = new AtomicReference<>();
  private final AtomicReference<APngWriter> apngWriter = new AtomicReference<>();
  private final MouseInfoProvider mouseInfoProvider;
  private final Palette256 palette;
  private final boolean forceWholeFrame;
  private volatile boolean stopped;
  private APngWriter.Statistics pngStatistics;

  public ScreenCapturer(
      final GraphicsDevice device,
      final Rectangle screenArea,
      final File targetFile,
      final MouseInfoProvider mouseInfoProvider,
      final RgbPixelFilter filter,
      final boolean forceWholeFrame,
      final boolean forceJavaRobotGrabber,
      final Palette256 palette,
      final Duration delayBetweenCaptures,
      final Duration delayBetweenFrames
  ) throws AWTException {
    this.forceWholeFrame = forceWholeFrame;
    this.palette = palette;
    this.screenAreaGrabber = forceJavaRobotGrabber ? ScreenAreaGrabberFactory.getInstance()
        .makeJavaRobotGrabber(device) : ScreenAreaGrabberFactory.getInstance().makeGrabber(device);
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
        if (!stopped) {
          doCapture();
        }
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
      APngWriter newWriter = new APngWriter(fileChannel, this.filter);

      if (!this.apngWriter.compareAndSet(null, newWriter)) {
        throw new Error("Unexpected state");
      }

      LOGGER.info("Staring capture task, delay " + this.delayBetweenCaptures.toMillis() + " ms");
      internalTimer.scheduleAtFixedRate(newTimerTask, 50L, this.delayBetweenCaptures.toMillis());
    }
  }

  private void doCapture() {
    try {
      final BufferedImage image = this.screenAreaGrabber.grabAsRgb(this.screenArea);

      if (this.mouseInfoProvider != null) {
        Point mouseLocation = this.mouseInfoProvider.getMousePointerLocation();
        com.igormaznitsa.japagoge.mouse.MousePointerIcon mousePointer =
            this.mouseInfoProvider.getMousePointerIcon();
        Rectangle pointerRectangle = new Rectangle(mousePointer.toHot(mouseLocation),
            new Dimension(mousePointer.getWidth(), mousePointer.getHeight()));
        if (this.screenArea.intersects(pointerRectangle)) {
          Graphics2D gfx = image.createGraphics();
          try {
            gfx.drawImage(mousePointer.getImage(), mouseLocation.x - this.screenArea.x,
                mouseLocation.y - this.screenArea.y, null);
          } finally {
            gfx.dispose();
          }
        }
      }

      final APngWriter writer = this.apngWriter.get();
      if (writer != null) {
        if (writer.getState() == APngWriter.State.CREATED) {
          writer.start("JAPAGOGE", image.getWidth(null), image.getHeight(null));
        }
        writer.addFrame(image, this.forceWholeFrame, this.delayBetweenFrames);
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
    try {
      TimerTask startedTimerTask = this.timerTask.getAndSet(null);
      if (startedTimerTask != null) {
        stopped = true;
        startedTimerTask.cancel();
      }
      APngWriter apngWriter = this.apngWriter.getAndSet(null);
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
    } finally {
      try {
        this.screenAreaGrabber.close();
      } catch (Exception ex) {
        LOGGER.log(Level.SEVERE, "Error during screen area grabber cloe", ex);
      }
    }
  }

  public boolean hasStatistics() {
    return this.pngStatistics != null;
  }
}
