package com.igormaznitsa.japagoge.mouse;

import com.sun.jna.Platform;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class MouseInfoProviderFactory {
  private static final Logger LOGGER = Logger.getLogger(MouseInfoProviderFactory.class.getSimpleName());
  private static final MouseInfoProviderFactory INSTANCE = new MouseInfoProviderFactory();

  private MouseInfoProviderFactory() {
  }

  public static MouseInfoProviderFactory getInstance() {
    return INSTANCE;
  }

  private static MouseInfoProvider makeClass(final String className) throws Exception {
    final Class<?> klazz = Class.forName(className);
    return (MouseInfoProvider) klazz.getConstructor().newInstance();
  }

  public MouseInfoProvider makeProvider() {
    try {
      if (Platform.isX11()) {
        LOGGER.info("Detected X11 platform");
        return makeClass("com.igormaznitsa.japagoge.mouse.X11MouseInfoProvider");
      } else if (Platform.isWindows()) {
        LOGGER.info("Detected Windows platform");
        return makeClass("com.igormaznitsa.japagoge.mouse.WinMouseInfoProvider");
      } else {
        LOGGER.info("Default mouse info provider");
        return new DefaultMouseInfoProvider();
      }
    } catch (Exception ex) {
      LOGGER.log(Level.SEVERE, "Error during make mouse provider, going use default one", ex);
      return new DefaultMouseInfoProvider();
    }
  }
}
