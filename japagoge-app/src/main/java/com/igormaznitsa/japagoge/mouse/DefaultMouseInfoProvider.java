package com.igormaznitsa.japagoge.mouse;

import javax.imageio.ImageIO;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

@SuppressWarnings("unused")
class DefaultMouseInfoProvider implements MouseInfoProvider {

  public static final MousePointerIcon MOUSEICON_APPSTARTING = makeMousePointerIcon("mouse_appstarting", 10, 10);
  public static final MousePointerIcon MOUSEICON_CROSS = makeMousePointerIcon("mouse_cross", 10, 10);
  public static final MousePointerIcon MOUSEICON_HAND = makeMousePointerIcon("mouse_hand", 10, 9);
  public static final MousePointerIcon MOUSEICON_HELP = makeMousePointerIcon("mouse_help", 10, 10);
  public static final MousePointerIcon MOUSEICON_IBEAM = makeMousePointerIcon("mouse_ibeam", 10, 10);
  public static final MousePointerIcon MOUSEICON_NO = makeMousePointerIcon("mouse_no", 10, 10);
  public static final MousePointerIcon MOUSEICON_PEN = makeMousePointerIcon("mouse_pen", 5, 5);
  public static final MousePointerIcon MOUSEICON_SIZE_ALL = makeMousePointerIcon("mouse_sizeall", 10, 11);
  public static final MousePointerIcon MOUSEICON_SIZE_NESW = makeMousePointerIcon("mouse_sizenesw", 10, 10);
  public static final MousePointerIcon MOUSEICON_SIZE_NS = makeMousePointerIcon("mouse_sizens", 10, 10);
  public static final MousePointerIcon MOUSEICON_SIZE_NWSE = makeMousePointerIcon("mouse_sizenwse", 10, 10);
  public static final MousePointerIcon MOUSEICON_SIZE_WE = makeMousePointerIcon("mouse_sizewe", 10, 10);
  public static final MousePointerIcon MOUSEICON_UP = makeMousePointerIcon("mouse_up", 10, 10);
  public static final MousePointerIcon MOUSEICON_WAIT = makeMousePointerIcon("mouse_wait", 10, 10);
  public static final MousePointerIcon MOUSEICON_NORMAL = makeMousePointerIcon("mouse_normal", 10, 10);
  public static final MousePointerIcon MOUSEICON_DRAG = makeMousePointerIcon("mouse_drag", 10, 10);

  private static MousePointerIcon makeMousePointerIcon(final String name, final int px, final int py) {
    try (InputStream in = DefaultMouseInfoProvider.class.getResourceAsStream(String.format("/icons/mouse/%s.png", name))) {
      return new MousePointerIcon(ImageIO.read(Objects.requireNonNull(in)), px, py);
    } catch (IOException e) {
      throw new RuntimeException("Can't load default pointer icon", e);
    }
  }

  @Override
  public Point getMousePointerLocation() {
    return MouseInfo.getPointerInfo().getLocation();
  }

  @Override
  public MousePointerIcon getMousePointerIcon() {
    return MOUSEICON_NORMAL;
  }
}
