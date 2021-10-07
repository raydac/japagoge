package com.igormaznitsa.japagoge.mouse;

import com.sun.jna.*;
import com.sun.jna.platform.WindowUtils;
import com.sun.jna.platform.win32.*;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

@SuppressWarnings("unused")
final class WinMouseInfoProvider extends DefaultMouseInfoProvider {
  private static final Logger LOGGER = Logger.getLogger(WinMouseInfoProvider.class.getSimpleName());

  private final User32Ext user32ext;
  private final User32 user32;
  private final GDI32 gdi32;
  private final Map<WinDef.HCURSOR, MousePointerIcon> cachedCursorImages = new HashMap<>();

  public WinMouseInfoProvider() {
    super();
    this.user32ext = User32Ext.INSTANCE;
    this.user32 = User32.INSTANCE;
    this.gdi32 = GDI32.INSTANCE;
    loadDefaultCursorHandlers();
  }

  private void loadDefaultCursorHandlers() {
    for (final DefaultCursor defaultCursor : DefaultCursor.values()) {
      final Memory memory = new Memory(Native.getNativeSize(Long.class, null));
      memory.setLong(0, defaultCursor.idc);
      final Pointer resource = memory.getPointer(0);
      final WinNT.HANDLE hCursor = this.user32ext.LoadImageA(
              null, resource, new WinDef.UINT(WinUser.IMAGE_CURSOR), 0, 0, new WinDef.UINT(WinUser.LR_SHARED)
      );
      if (hCursor == null || Native.getLastError() != 0) {
        LOGGER.log(Level.SEVERE, "Can't load cursor: " + defaultCursor + ", " + Kernel32Util.getLastErrorMessage());
      } else {
        LOGGER.info("Loaded cursor: " + defaultCursor + ", " + hCursor);
        defaultCursor.hCursor.set(hCursor);
      }
    }
  }

  @Override
  public synchronized MousePointerIcon getMousePointerIcon() {
    try {
      final CURSORINFO cursorinfo = new CURSORINFO();
      if (this.user32ext.GetCursorInfo(cursorinfo).booleanValue() && cursorinfo.hCursor != null) {
        return cachedCursorImages.computeIfAbsent(cursorinfo.hCursor, hCursor -> {
          LOGGER.info("Loading icon for cursor: " + hCursor);
          final MousePointerIcon mousePointerIcon = this.loadMouseIcon(hCursor);
          if (mousePointerIcon == null) {
            LOGGER.severe("Can't load cursor icon: " + hCursor);
            return DefaultCursor.findForHandler(hCursor).orElse(DefaultCursor.ARROW).defaultIcon;
          } else {
            LOGGER.info("Loaded cursor icon: " + hCursor + ' ' + mousePointerIcon);
            return mousePointerIcon;
          }
        });
      } else {
        return DefaultCursor.ARROW.defaultIcon;
      }
    } catch (Exception ex) {
      LOGGER.log(Level.SEVERE, "Unexpoected error in getMousePointerIcon", ex);
      return super.getMousePointerIcon();
    }
  }

  private MousePointerIcon loadMouseIcon(final WinDef.HICON hIcon) {
    final Dimension iconSize = WindowUtils.getIconSize(hIcon);
    if (iconSize.width == 0 || iconSize.height == 0)
      return null;

    final int width = iconSize.width;
    final int height = iconSize.height;
    final short depth = 24;

    final byte[] lpBitsColor = new byte[width * height * depth / 8];
    final Pointer lpBitsColorPtr = new Memory(lpBitsColor.length);
    final byte[] lpBitsMask = new byte[width * height * depth / 8];
    final Pointer lpBitsMaskPtr = new Memory(lpBitsMask.length);
    final WinGDI.BITMAPINFO bitmapInfo = new WinGDI.BITMAPINFO();
    final WinGDI.BITMAPINFOHEADER hdr = new WinGDI.BITMAPINFOHEADER();

    bitmapInfo.bmiHeader = hdr;
    hdr.biWidth = width;
    hdr.biHeight = height;
    hdr.biPlanes = 1;
    hdr.biBitCount = depth;
    hdr.biCompression = 0;
    hdr.write();
    bitmapInfo.write();

    final WinDef.HDC hDC = this.user32.GetDC(null);
    try {
      final WinGDI.ICONINFO iconInfo = new WinGDI.ICONINFO();
      this.user32.GetIconInfo(hIcon, iconInfo);
      iconInfo.read();
      this.gdi32.GetDIBits(hDC, iconInfo.hbmColor, 0, height, lpBitsColorPtr, bitmapInfo, 0);
      lpBitsColorPtr.read(0, lpBitsColor, 0, lpBitsColor.length);
      this.gdi32.GetDIBits(hDC, iconInfo.hbmMask, 0, height, lpBitsMaskPtr, bitmapInfo, 0);
      lpBitsMaskPtr.read(0, lpBitsMask, 0, lpBitsMask.length);
      final BufferedImage image = new BufferedImage(width, height,
              BufferedImage.TYPE_INT_ARGB);

      int opaquePixels = 0;
      int x = 0, y = height - 1;
      for (int i = 0; i < lpBitsColor.length; i += 3) {
        final int b = lpBitsColor[i] & 0xFF;
        final int g = lpBitsColor[i + 1] & 0xFF;
        final int r = lpBitsColor[i + 2] & 0xFF;
        final int a = 0xFF - lpBitsMask[i] & 0xFF;
        final int argb = (a << 24) | (r << 16) | (g << 8) | b;
        if (a != 0) opaquePixels++;
        image.setRGB(x, y, argb);
        x = (x + 1) % width;
        if (x == 0) y--;
      }
      return opaquePixels == 0 ? null : new MousePointerIcon(image, iconInfo.xHotspot, iconInfo.yHotspot);
    } finally {
      User32.INSTANCE.ReleaseDC(null, hDC);
    }
  }


  public enum DefaultCursor {
    APPSTARTING(WinUser.IDC_APPSTARTING, MOUSEICON_APPSTARTING),
    ARROW(WinUser.IDC_ARROW, MOUSEICON_NORMAL),
    CROSS(WinUser.IDC_CROSS, MOUSEICON_CROSS),
    HAND(WinUser.IDC_HAND, MOUSEICON_HAND),
    HELP(WinUser.IDC_HELP, MOUSEICON_HELP),
    IBEAM(WinUser.IDC_IBEAM, MOUSEICON_IBEAM),
    NO(WinUser.IDC_NO, MOUSEICON_NO),
    SIZEALL(WinUser.IDC_SIZEALL, MOUSEICON_SIZE_ALL),
    SIZENESW(WinUser.IDC_SIZENESW, MOUSEICON_SIZE_NESW),
    SIZENS(WinUser.IDC_SIZENS, MOUSEICON_SIZE_NS),
    SIZENWSE(WinUser.IDC_SIZENWSE, MOUSEICON_SIZE_NWSE),
    SIZEWE(WinUser.IDC_SIZEWE, MOUSEICON_SIZE_WE),
    UP(WinUser.IDC_UPARROW, MOUSEICON_UP),
    WAIT(WinUser.IDC_WAIT, MOUSEICON_WAIT);

    private final int idc;
    private final MousePointerIcon defaultIcon;
    private final AtomicReference<WinNT.HANDLE> hCursor = new AtomicReference<>();

    DefaultCursor(final int idc, final MousePointerIcon defaultIcon) {
      this.idc = idc;
      this.defaultIcon = defaultIcon;
    }

    public static Optional<DefaultCursor> findForHandler(final WinNT.HANDLE handler) {
      return Arrays.stream(DefaultCursor.values())
              .filter(x -> x.hCursor.get() != null && x.hCursor.get().equals(handler))
              .findFirst();
    }

    public MousePointerIcon getDefaultIcon() {
      return this.defaultIcon;
    }

  }


  @SuppressWarnings("unused")
  private interface User32Ext extends Library {
    User32Ext INSTANCE = Native.load("User32.dll", User32Ext.class);

    WinDef.BOOL GetIconInfoExA(WinDef.HICON hCursor, ICONINFOEXA iconInfoExa);

    WinDef.BOOL GetCursorInfo(CURSORINFO cursorinfo);

    WinNT.HANDLE LoadImageA(
            WinDef.HINSTANCE hinst,
            Pointer lpszName,
            WinNT.UINT uType,
            int cxDesired,
            int cyDesired,
            WinNT.UINT fuLoad
    );

  }

  @Structure.FieldOrder({"cbSize", "fIcon", "xHotspot", "yHotspot", "hbmMask", "hbmColor", "wResID", "szModName", "szResName"})
  public static class ICONINFOEXA extends Structure {
    public WinDef.DWORD cbSize;
    public WinDef.BOOL fIcon;
    public WinDef.DWORD xHotspot;
    public WinDef.DWORD yHotspot;
    public WinDef.HBITMAP hbmMask;
    public WinDef.HBITMAP hbmColor;
    public WinDef.WORD wResID;
    public byte[] szModName = new byte[256];
    public byte[] szResName = new byte[256];
  }

  @Structure.FieldOrder({"cbSize", "flags", "hCursor", "ptScreenPos"})
  public static class CURSORINFO extends Structure {

    public WinNT.DWORD cbSize;
    public WinNT.DWORD flags;
    public WinDef.HCURSOR hCursor;
    public WinDef.POINT ptScreenPos;

    public CURSORINFO() {
      this.cbSize = new WinDef.DWORD(Native.getNativeSize(CURSORINFO.class, null));
    }
  }

}
