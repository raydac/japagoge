package com.igormaznitsa.japagoge.utils;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.stream.Stream;

public final class SystemUtils {
  private SystemUtils() {

  }

  public static boolean isHiDpi(final GraphicsConfiguration gc) {
    final double displayWidth = gc.getDevice().getDisplayMode().getWidth();
    final double displayBoundsWidth = gc.getDevice().getDefaultConfiguration().getBounds().width;
    return displayBoundsWidth / displayWidth >= 1.5d || displayBoundsWidth > 3000;
  }

  public static Dimension findScreenSize() {
    final Rectangle rectangle = new Rectangle(0, 0, 0, 0);
    Stream.of(GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices())
            .forEach(x -> {
              rectangle.add(x.getDefaultConfiguration().getBounds());
            });
    return new Dimension(rectangle.width, rectangle.height);
  }

  public static BufferedImage ensureBufferedImage(final Image image, final int targetType) {
    if (image == null) return null;
    if (image instanceof BufferedImage && ((BufferedImage) image).getType() == targetType) {
      return (BufferedImage) image;
    }
    final BufferedImage result = new BufferedImage(image.getWidth(null), image.getHeight(null), targetType);
    final Graphics2D gfx = result.createGraphics();
    try {
      gfx.drawImage(image, 0, 0, null);
    } finally {
      gfx.dispose();
    }
    return result;
  }

  public static Image imageX2(final Image image) {
    if (image == null) return null;
    final BufferedImage result = new BufferedImage(image.getWidth(null) * 2, image.getHeight(null) * 2, BufferedImage.TYPE_INT_ARGB);
    final Graphics2D gfx = result.createGraphics();
    try {
      gfx.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
      gfx.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      gfx.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      gfx.drawImage(image, 0, 0, result.getWidth(), result.getHeight(), null);
    } finally {
      gfx.dispose();
    }
    return result;
  }

  public static Font fontX2(final Font font) {
    if (font == null) return null;
    return font.deriveFont((float) font.getSize() * 2);
  }

  public static void setApplicationTaskbarTitle(final Image taskBarIcon, final String title, final String taskBarBadgeTitle) {
    if (Taskbar.isTaskbarSupported()) {
      final Taskbar taskbar = Taskbar.getTaskbar();
      try {
        taskbar.setIconImage(taskBarIcon);
      } catch (Exception ex) {
        // do nothing
      }
      try {
        taskbar.setIconBadge(taskBarBadgeTitle);
      } catch (Exception ex) {
        // do nothing
      }
    }

    final Toolkit toolkit = Toolkit.getDefaultToolkit();
    try {
      final Field awtAppClassNameField =
              toolkit.getClass().getDeclaredField("awtAppClassName");
      awtAppClassNameField.setAccessible(true);
      awtAppClassNameField.set(toolkit, title);
    } catch (Exception ex) {
      // just ignoring
    }
  }

}
