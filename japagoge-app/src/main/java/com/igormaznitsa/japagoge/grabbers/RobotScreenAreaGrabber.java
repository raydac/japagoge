package com.igormaznitsa.japagoge.grabbers;

import com.igormaznitsa.japagoge.utils.SystemUtils;

import java.awt.*;
import java.awt.image.BufferedImage;

class RobotScreenAreaGrabber implements ScreenAreaGrabber {

  private final Robot robot;

  RobotScreenAreaGrabber(final GraphicsDevice device) {
    try {
      this.robot = new Robot(device);
    } catch (Exception ex) {
      throw new RuntimeException("Can't create robot", ex);
    }
  }

  @Override
  public BufferedImage grabAsRgb(final Rectangle area) {
    var imageList = this.robot.createMultiResolutionScreenCapture(area);
    return SystemUtils.ensureBufferedImage(imageList.getResolutionVariant(area.width, area.height), BufferedImage.TYPE_INT_RGB);
  }

  @Override
  public void close() {
  }
}
