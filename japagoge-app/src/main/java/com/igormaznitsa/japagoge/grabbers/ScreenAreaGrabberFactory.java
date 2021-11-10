package com.igormaznitsa.japagoge.grabbers;

import org.apache.commons.lang3.SystemUtils;

import java.awt.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ScreenAreaGrabberFactory {
  private static final ScreenAreaGrabberFactory INSTANCE = new ScreenAreaGrabberFactory();

  private static final Logger LOGGER = Logger.getLogger(ScreenAreaGrabberFactory.class.getName());

  private static final String PROPERTY_FORCE_ROBOT = "japagoge.force.robot";

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
    if (Boolean.getBoolean(PROPERTY_FORCE_ROBOT)) {
      LOGGER.info("Detected " + PROPERTY_FORCE_ROBOT + " as TRUE");
    } else {
      if (SystemUtils.IS_OS_LINUX || SystemUtils.IS_OS_SOLARIS) {
        LOGGER.info("Detected potential X11 system");
        result = tryMakeGrabber("com.igormaznitsa.japagoge.grabbers.X11ScreenAreaGrabber", device);
      }
    }
    return result == null ? new RobotScreenAreaGrabber(device) : result;
  }

}
