package com.igormaznitsa.japagoge.utils;

import java.awt.*;
import java.lang.reflect.Field;

public final class SystemUtils {
  private SystemUtils() {

  }

  public static void setApplicationTaskbarTitle(final Image taskBarIcon, final String taskBarBadgeTitle) {
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
      awtAppClassNameField.set(toolkit, taskBarBadgeTitle);
    } catch (Exception ex) {
      // just ignoring
    }
  }

}
