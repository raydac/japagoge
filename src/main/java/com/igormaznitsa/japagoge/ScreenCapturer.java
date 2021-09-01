package com.igormaznitsa.japagoge;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
  private static final BufferedImage POINTER_IMAGE;

  static {
    try (InputStream in = ScreenCapturer.class.getResourceAsStream("/icons/pointer.png")) {
      POINTER_IMAGE = ImageIO.read(Objects.requireNonNull(in));
    } catch (Exception e) {
      throw new Error("Can't load pointer icon");
    }
  }

  private final Robot robot;
  private final Rectangle screenArea;
  private final File targetFile;
  private final Duration durationBetweenFrames;
  private final boolean capturePointer;
  private final AtomicReference<TimerTask> timerTask = new AtomicReference<>();
  private final AtomicReference<APngWriter> apngWriter = new AtomicReference<>();

  public ScreenCapturer(
          final GraphicsDevice device,
          final Rectangle screenArea,
          final File targetFile,
          final boolean capturePointer,
          final Duration delayBetweenFrames
  ) throws AWTException {
    this.robot = new Robot(device);
    this.capturePointer = capturePointer;
    this.screenArea = Objects.requireNonNull(screenArea);
    this.targetFile = targetFile;
    this.durationBetweenFrames = Objects.requireNonNull(delayBetweenFrames);
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
      var newWriter = new APngWriter(fileChannel);

      if (!this.apngWriter.compareAndSet(null, newWriter)) {
        throw new Error("Unexpected state");
      }
      internalTimer.scheduleAtFixedRate(newTimerTask, 50L, this.durationBetweenFrames.toMillis());
    }
  }

  private void doCapture() {
    try {
      var image = this.robot.createScreenCapture(this.screenArea);

      if (this.capturePointer) {
        var mouseLocation = MouseInfo.getPointerInfo().getLocation();
        var pointerRectangle = new Rectangle(mouseLocation, new Dimension(POINTER_IMAGE.getWidth(), POINTER_IMAGE.getHeight()));
        if (this.screenArea.intersects(pointerRectangle)) {
          var gfx = image.createGraphics();
          try {
            gfx.drawImage(POINTER_IMAGE, mouseLocation.x - this.screenArea.x, mouseLocation.y - this.screenArea.y, null);
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
        writer.addFrame(image, this.durationBetweenFrames);
      }
    } catch (Exception ex) {
      LOGGER.log(Level.SEVERE, "Error during capturing", ex);
    }
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
        LOGGER.info(String.format("Image %dx%d, buffer %d bytes, %d frames, length %d bytes",
                statistics.width,
                statistics.height,
                statistics.bufferSize,
                statistics.frames,
                statistics.size
        ));
      } catch (Exception ex) {
        LOGGER.log(Level.SEVERE, "Error during close writer", ex);
      }
    }
  }
}
