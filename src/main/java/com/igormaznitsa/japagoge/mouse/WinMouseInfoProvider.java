package com.igormaznitsa.japagoge.mouse;

import com.sun.jna.*;
import com.sun.jna.platform.WindowUtils;
import com.sun.jna.platform.win32.*;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.*;

// from https://stackoverflow.com/questions/47634213/get-mouse-type-in-java-using-jna
@SuppressWarnings("unused")
final class WinMouseInfoProvider extends DefaultMouseInfoProvider {

  private final Map<WinNT.HANDLE, Cursor> cursors;
  private final User32Ext user32ext;
  private final User32 user32;
  private final GDI32 gdi32;
  private final Map<Cursor, BufferedImage> cachedCursorImages = new EnumMap<>(Cursor.class);

  public WinMouseInfoProvider() {
    super();
    this.user32ext = User32Ext.INSTANCE;
    this.user32 = User32.INSTANCE;
    this.gdi32 = GDI32.INSTANCE;
    this.cursors = this.loadCursors();
  }

  @Override
  public MousePointerIcon getMousePointerIcon() {
    try {
      final Cursor cursor = this.getCurrentCursor();
      return cursor == null ? super.getMousePointerIcon() : cursor.getDefaultIcon();
    } catch (Exception ex) {
      return super.getMousePointerIcon();
    }
  }

  private Cursor getCurrentCursor() {
    final CURSORINFO cursorinfo = new CURSORINFO();
    final int success = this.user32ext.GetCursorInfo(cursorinfo);
    if (success == 1 && cursorinfo.hCursor != null) {
      return cursors.getOrDefault(cursorinfo.hCursor, Cursor.HAND);
    }
    return null;
  }

  public BufferedImage getImageByHICON(final WinDef.HICON hicon) {
    final WinGDI.ICONINFO iconInfo = new WinGDI.ICONINFO();

    try {
      final Dimension iconSize = WindowUtils.getIconSize(hicon);
      final int width = iconSize.width;
      final int height = iconSize.height;

      if (!this.user32.GetIconInfo(hicon, iconInfo)) {
        return null;
      }
      final WinDef.HWND hwdn = new WinDef.HWND();
      final WinDef.HDC dc = this.user32.GetDC(hwdn);

      if (dc == null) {

        return null;
      }
      try {
        final int nBits = width * height * 4;
        final Memory colorBitsMem = new Memory(nBits);
        final WinGDI.BITMAPINFO bitMapInfo = new WinGDI.BITMAPINFO();

        bitMapInfo.bmiHeader.biWidth = width;
        bitMapInfo.bmiHeader.biHeight = -height;
        bitMapInfo.bmiHeader.biPlanes = 1;
        bitMapInfo.bmiHeader.biBitCount = 32;
        bitMapInfo.bmiHeader.biCompression = WinGDI.BI_RGB;

        this.gdi32.GetDIBits(dc, iconInfo.hbmColor, 0, height, colorBitsMem, bitMapInfo, WinGDI.DIB_RGB_COLORS);
        final int[] colorBits = colorBitsMem.getIntArray(0, width * height);

        final BufferedImage resultImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        resultImage.setRGB(0, 0, width, height, colorBits, 0, height);
        return resultImage;
      } finally {
        this.user32.ReleaseDC(hwdn, dc);
      }
    } finally {
      this.user32.DestroyIcon(new WinDef.HICON(hicon.getPointer()));
      this.gdi32.DeleteObject(iconInfo.hbmColor);
      this.gdi32.DeleteObject(iconInfo.hbmMask);
    }
  }

  private Map<WinNT.HANDLE, Cursor> loadCursors() {
    final Map<WinNT.HANDLE, Cursor> cursors = new HashMap<>();
    for (final Cursor cursor : Cursor.values()) {

      final Memory memory = new Memory(Native.getNativeSize(Long.class, null));
      memory.setLong(0, cursor.getCode());
      final Pointer resource = memory.getPointer(0);
      final WinNT.HANDLE cursorHandle = this.user32ext.LoadImageA(
              null, resource, WinUser.IMAGE_CURSOR, 0, 0, WinUser.LR_SHARED
      );
      if (cursorHandle == null || Native.getLastError() != 0) {
        throw new RuntimeException("Cursor could not be loaded: " + Native.getLastError());
      }

      cursors.put(cursorHandle, cursor);
    }
    return Collections.unmodifiableMap(cursors);
  }

  public enum Cursor {
    APPSTARTING(32650, MOUSEICON_APPSTARTING),
    NORMAL(32512, MOUSEICON_NORMAL),
    CROSS(32515, MOUSEICON_CROSS),
    HAND(32649, MOUSEICON_HAND),
    HELP(32651, MOUSEICON_HELP),
    IBEAM(32513, MOUSEICON_IBEAM),
    NO(32648, MOUSEICON_NO),
    SIZEALL(32646, MOUSEICON_SIZE_ALL),
    SIZENESW(32643, MOUSEICON_SIZE_NESW),
    SIZENS(32645, MOUSEICON_SIZE_NS),
    SIZENWSE(32642, MOUSEICON_SIZE_NWSE),
    SIZEWE(32644, MOUSEICON_SIZE_WE),
    UP(32516, MOUSEICON_UP),
    WAIT(32514, MOUSEICON_WAIT),
    PEN(32631, MOUSEICON_PEN);

    private final int code;
    private final MousePointerIcon defaultIcon;

    Cursor(final int code, final MousePointerIcon defaultIcon) {
      this.code = code;
      this.defaultIcon = defaultIcon;
    }

    public MousePointerIcon getDefaultIcon() {
      return this.defaultIcon;
    }

    public int getCode() {
      return code;
    }
  }


  @SuppressWarnings("unused")
  private interface User32Ext extends Library {
    User32Ext INSTANCE = Native.loadLibrary("User32.dll", User32Ext.class);

    int GetCursorInfo(CURSORINFO cursorinfo);

    WinNT.HANDLE LoadImageA(
            WinDef.HINSTANCE hinst,
            Pointer lpszName,
            int uType,
            int cxDesired,
            int cyDesired,
            int fuLoad
    );

  }

  public static class CURSORINFO extends Structure {

    public int cbSize;
    public int flags;
    public WinDef.HCURSOR hCursor;
    public WinDef.POINT ptScreenPos;

    public CURSORINFO() {
      this.cbSize = Native.getNativeSize(CURSORINFO.class, null);
    }

    @Override
    protected List<String> getFieldOrder() {
      return Arrays.asList("cbSize", "flags", "hCursor", "ptScreenPos");
    }
  }
}
