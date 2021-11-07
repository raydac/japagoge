package com.igormaznitsa.japagoge.grabbers;

import java.awt.*;
import java.awt.image.BufferedImage;

class X11ScreenAreaGrabber implements ScreenAreaGrabber {

  X11ScreenAreaGrabber(final GraphicsDevice device) {
    throw new UnsupportedOperationException("Unsupported yet");
  }

  @Override
  public BufferedImage grabAsRgb(Rectangle area) {
    return null;
  }

  @Override
  public void close() {

  }

}
