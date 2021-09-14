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
          final ICONINFOEXA iconinfoExa = new ICONINFOEXA();
          if (this.user32ext.GetIconInfoExA(hCursor, iconinfoExa).booleanValue()) {
            final BufferedImage cursorImage = this.loadIcon(hCursor);
            if (cursorImage == null) {
              return DefaultCursor.findForHandler(hCursor).orElse(DefaultCursor.ARROW).defaultIcon;
            } else {
              final MousePointerIcon result = new MousePointerIcon(cursorImage, iconinfoExa.xHotspot.intValue(), iconinfoExa.yHotspot.intValue());
              LOGGER.info("Loaded cursor icon: " + hCursor + ' ' + result);
              return result;
            }
          } else {
            LOGGER.severe("Can't get icon info for cursor: " + hCursor + ", " + Kernel32Util.getLastErrorMessage());
            return DefaultCursor.findForHandler(hCursor).orElse(DefaultCursor.ARROW).defaultIcon;
          }
        });
      } else {
        return DefaultCursor.ARROW.defaultIcon;
      }
    } catch (Exception ex) {
      return super.getMousePointerIcon();
    }
  }

  private BufferedImage loadIcon(final WinNT.HICON hIcon) {
    final WinGDI.ICONINFO iconInfo = new WinGDI.ICONINFO();

    try {
      final Dimension iconSize = WindowUtils.getIconSize(hIcon);
      final int width = iconSize.width;
      final int height = iconSize.height;

      if (!this.user32.GetIconInfo(hIcon, iconInfo)) {
        LOGGER.severe("Can't get icon info: " + hIcon + ", " + Kernel32Util.getLastErrorMessage());
        return null;
      }

      final WinDef.HWND hWnd = new WinDef.HWND();
      final WinDef.HDC dc = this.user32.GetDC(hWnd);

      if (dc == null) {
        LOGGER.severe("DC is null," + Kernel32Util.getLastErrorMessage());
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
        this.user32.ReleaseDC(hWnd, dc);
      }
    } catch (Exception ex) {
      LOGGER.log(Level.SEVERE, "Error during icon reading: " + hIcon, ex);
    } finally {
      this.gdi32.DeleteObject(iconInfo.hbmColor);
      this.gdi32.DeleteObject(iconInfo.hbmMask);
    }
    return null;
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

    WinDef.BOOL GetIconInfoExA(WinDef.HCURSOR hCursor, ICONINFOEXA iconInfoExa);

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
