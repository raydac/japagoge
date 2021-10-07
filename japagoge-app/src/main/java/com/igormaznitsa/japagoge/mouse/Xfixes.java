package com.igormaznitsa.japagoge.mouse;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Structure;
import com.sun.jna.platform.unix.X11;
import com.sun.jna.ptr.NativeLongByReference;

public interface Xfixes extends Library {

  Xfixes INSTANCE = Native.load("Xfixes", Xfixes.class);

  XFixesCursorImage XFixesGetCursorImage(X11.Display dpy);

  @Structure.FieldOrder({"x", "y", "width", "height", "xhot", "yhot", "cursor_serial", "pixels", "atom", "name"})
  class XFixesCursorImage extends Structure {
    public short x;
    public short y;
    public short width;
    public short height;
    public short xhot;
    public short yhot;
    public NativeLong cursor_serial;

    public NativeLongByReference pixels;

    public NativeLong atom;
    public String name;

    public XFixesCursorImage() {
      super();
    }
  }
}
