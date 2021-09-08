package com.igormaznitsa.japagoge.mouse;

import com.sun.jna.NativeLong;
import com.sun.jna.platform.unix.X11;

import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unused")
public class X11MouseInfoProvider extends DefaultMouseInfoProvider {

  private final Map<String, MousePointerIcon> images = new HashMap<>();

  public X11MouseInfoProvider() {
    super();
  }

  private MousePointerIcon getCursorImage() {
    MousePointerIcon resultImage;

    final X11 x11 = X11.INSTANCE;
    final Xfixes xfixes = Xfixes.INSTANCE;

    final X11.Display display = x11.XOpenDisplay(null);
    try {
      final Xfixes.XFixesCursorImage cursorImage = xfixes.XFixesGetCursorImage(display);
      resultImage = this.images.get(cursorImage.name);
      if (resultImage == null) {
        final ByteBuffer pixelBuffer = cursorImage.pixels.getPointer().getByteBuffer(0,
                (long) cursorImage.width * (long) cursorImage.height * NativeLong.SIZE);
        pixelBuffer.order(ByteOrder.LITTLE_ENDIAN);
        final BufferedImage resultIcon = new BufferedImage(cursorImage.width, cursorImage.height, BufferedImage.TYPE_INT_ARGB);
        final WritableRaster raster = resultIcon.getRaster();
        for (int y = 0; y < cursorImage.height; y++) {
          for (int x = 0; x < cursorImage.width; x++) {
            final long z = NativeLong.SIZE == 8 ? pixelBuffer.getLong() : pixelBuffer.getInt();
            final int b = (int) ((z >> 24) & 0xFF);
            final int a = (int) ((z >> 16) & 0xFF);
            final int g = (int) ((z >> 8) & 0xFF);
            final int r = (int) (z & 0xFF);
            raster.setPixel(x, y, new int[]{a, r, g, b});
          }
        }
        resultImage = new MousePointerIcon(resultIcon, cursorImage.xhot, cursorImage.yhot);
        this.images.put(cursorImage.name, resultImage);
      }
    } catch (Exception ex) {
      resultImage = null;
    } finally {
      x11.XCloseDisplay(display);
    }
    return resultImage;
  }

  @Override
  public MousePointerIcon getMousePointerIcon() {
    try {
      final MousePointerIcon result = this.getCursorImage();
      return result == null ? super.getMousePointerIcon() : result;
    } catch (Exception ex) {
      return super.getMousePointerIcon();
    }
  }
}
