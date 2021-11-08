package com.igormaznitsa.japagoge.grabbers;

import com.sun.jna.*;

@SuppressWarnings("unused")
interface X11GrabLib extends Library {

  X11GrabLib INSTANCE = Native.load("X11", X11GrabLib.class);

  Pointer XOpenDisplay(String display_name);

  void XCloseDisplay(Pointer display);

  int XDefaultScreen(Pointer display);

  int XRootWindow(Pointer display, int screen_number);

  NativeLong XAllPlanes();

  int XDefaultColormap(Pointer display, int screen_number);

  Pointer XGetImage(Pointer display, int drawable, int x, int y, int width, int height, NativeLong plane_mask, int format);

  NativeLong XGetPixel(Pointer xImage, int x, int y);

  void XQueryColor(Pointer display, int colormap, Structure color);

  void XFree(Pointer data);

  @Structure.FieldOrder({"pixel", "red", "green", "blue", "flags", "pad"})
  class XColor extends Structure {
    public NativeLong pixel;
    public short red, green, blue;
    public byte flags;
    public byte pad;

    static class ByReference extends XColor implements Structure.ByReference {
    }
  }

}
