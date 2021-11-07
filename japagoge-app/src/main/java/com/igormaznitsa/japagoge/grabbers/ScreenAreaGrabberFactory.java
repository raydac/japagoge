package com.igormaznitsa.japagoge.grabbers;

import java.awt.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ScreenAreaGrabberFactory {
  private static final ScreenAreaGrabberFactory INSTANCE = new ScreenAreaGrabberFactory();

  private static final Logger LOGGER = Logger.getLogger(ScreenAreaGrabberFactory.class.getName());

  private ScreenAreaGrabberFactory() {

  }

  public static ScreenAreaGrabberFactory getInstance() {
    return INSTANCE;
  }

  private static ScreenAreaGrabber tryMakeGrabber(final String className, final GraphicsDevice device) {
    try {
      return (ScreenAreaGrabber) Class.forName(className).getDeclaredConstructor(GraphicsDevice.class).newInstance(device);
    } catch (Exception ex) {
      LOGGER.log(Level.SEVERE, "Can't load class " + className + " for error", ex);
    }
    return null;
  }

  public ScreenAreaGrabber makeGrabber(final GraphicsDevice device) {
    ScreenAreaGrabber result = null;
//    if (SystemUtils.IS_OS_LINUX) {
//      result = tryMakeGrabber("com.igormaznitsa.japagoge.grabbers.X11ScreenAreaGrabber", device);
//    }
    return result == null ? new RobotScreenAreaGrabber(device) : result;
  }

}
