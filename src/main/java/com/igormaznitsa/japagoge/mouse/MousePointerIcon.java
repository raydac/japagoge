package com.igormaznitsa.japagoge.mouse;

import java.awt.*;
import java.util.Objects;

public class MousePointerIcon {
  private final Image image;
  private final Point hotPoint;
  private final int width;
  private final int height;

  public MousePointerIcon(final Image image, final int hotPointX, final int hotPointY) {
    this.image = Objects.requireNonNull(image);
    this.hotPoint = new Point(hotPointX, hotPointY);
    this.width = this.image.getWidth(null);
    this.height = this.image.getHeight(null);
  }

  public int getWidth() {
    return this.width;
  }

  public int getHeight() {
    return this.height;
  }

  public Point toHot(final Point point) {
    point.x -= this.hotPoint.x;
    point.y -= this.hotPoint.y;
    return point;
  }

  public Image getImage() {
    return this.image;
  }

  public Point getHotPoint() {
    return this.hotPoint;
  }
}
