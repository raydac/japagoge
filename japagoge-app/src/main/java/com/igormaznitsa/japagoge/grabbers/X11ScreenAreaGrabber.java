package com.igormaznitsa.japagoge.grabbers;

import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import java.awt.GraphicsDevice;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.concurrent.atomic.AtomicBoolean;

class X11ScreenAreaGrabber implements ScreenAreaGrabber {

  private static final int ZPixmap = 2;
  private final NativeLong allPlanes;
  private final X11GrabLib x11Grab;
  private final Pointer display;
  private final int window;
  private final int screen;
  private final int colorMap;
  private final X11GrabLib.XColor.ByReference colorPoint;

  private final AtomicBoolean closed = new AtomicBoolean();

  X11ScreenAreaGrabber(final GraphicsDevice device) {
    this.x11Grab = X11GrabLib.INSTANCE;
    this.allPlanes = this.x11Grab.XAllPlanes();
    this.display = x11Grab.XOpenDisplay(null);
    this.screen = x11Grab.XDefaultScreen(display);
    this.window = x11Grab.XRootWindow(this.display, this.screen);
    this.colorMap = x11Grab.XDefaultColormap(this.display, this.screen);
    this.colorPoint = new X11GrabLib.XColor.ByReference();
  }

  @Override
  public synchronized BufferedImage grabAsRgb(final Rectangle area) {
    if (this.closed.get()) {
      throw new IllegalStateException("Already closed");
    }
    final Pointer pImage = this.x11Grab.XGetImage(this.display, this.window, area.x, area.y, area.width, area.height, this.allPlanes, ZPixmap);
    final XImage image = new XImage(pImage);
    try {
      final BufferedImage result = new BufferedImage(area.width, area.height, BufferedImage.TYPE_INT_RGB);
      final int[] imageRgb = this.extractRgbData(pImage, image);
      final int[] rgbArray = ((DataBufferInt) result.getRaster().getDataBuffer()).getData();
      System.arraycopy(imageRgb, 0, rgbArray, 0, imageRgb.length);
      return result;
    } finally {
      this.x11Grab.XFree(pImage);
    }
  }

  private int[] extractRgbData(final Pointer pImage, final XImage xImage) {
    final int[] result;
    final boolean invertedByteOrder = xImage.byte_order == XImage.MSBFIRST;
    if (xImage.xoffset == 0 && xImage.bitmap_pad == 32 && xImage.depth >= 24 && xImage.bitmap_unit == 32) {
      result = xImage.data.getIntArray(0, (xImage.bytes_per_line / 4) * xImage.height);
      if (invertedByteOrder) {
        for (int i = 0; i < result.length; i++) {
          final int value = result[i];
          result[i] = (value << 24) | ((value << 8) & 0x00FF0000) | (value >>> 24) | ((value >> 8) & 0x0000FF00);
        }
      }
    } else {
      result = new int[xImage.width * xImage.height];
      final boolean useColorMap = xImage.bitmap_unit < 24;
      for (int y = 0; y < xImage.height; y++) {
        int offset = y * xImage.width;
        for (int x = 0; x < xImage.width; x++) {
          this.colorPoint.pixel = this.x11Grab.XGetPixel(pImage, x, y);
          final int rgb;
          if (useColorMap) {
            this.x11Grab.XQueryColor(this.display, this.colorMap, this.colorPoint);
            final int r = (this.colorPoint.red & 0xFFFF) >> 8;
            final int g = (this.colorPoint.green & 0xFFFF) >> 8;
            final int b = (this.colorPoint.blue & 0xFFFF) >> 8;
            rgb = (r << 16) | (g << 8) | b;
          } else {
            final long pixel = this.colorPoint.pixel.longValue();
            if (invertedByteOrder) {
              final int value = (int) (pixel >>> 32);
              rgb = (value << 24) | ((value << 8) & 0x00FF0000) | (value >>> 24) | ((value >> 8) & 0x0000FF00);
            } else {
              rgb = ((int) pixel & 0xFFFFFF);
            }
          }
          result[offset++] = 0xFF000000 | rgb;
        }
      }
    }
    return result;
  }

  @Override
  public synchronized void close() {
    if (this.closed.compareAndExchange(false, true)) {
      this.x11Grab.XCloseDisplay(this.display);
    }
  }

}
