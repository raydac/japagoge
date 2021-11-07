package com.igormaznitsa.japagoge.utils;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;

public final class SystemUtils {
  private SystemUtils() {

  }

  public static boolean isHiDpi() {
    return Toolkit.getDefaultToolkit().getScreenResolution() >= 100;
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
