package com.igormaznitsa.japagoge.grabbers;

import java.awt.*;
import java.awt.image.BufferedImage;

public interface ScreenAreaGrabber extends AutoCloseable {
  BufferedImage grabAsRgb(Rectangle area);
}
