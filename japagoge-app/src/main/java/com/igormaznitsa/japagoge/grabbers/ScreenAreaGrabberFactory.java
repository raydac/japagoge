package com.igormaznitsa.japagoge.grabbers;

import java.awt.*;

public final class ScreenAreaGrabberFactory {
  private static final ScreenAreaGrabberFactory INSTANCE = new ScreenAreaGrabberFactory();

  private ScreenAreaGrabberFactory() {

  }

  public static ScreenAreaGrabberFactory getInstance() {
    return INSTANCE;
  }

  public ScreenAreaGrabber makeGrabber(final GraphicsDevice device) {
    return new RobotScreenAreaGrabber(device);
  }

}
