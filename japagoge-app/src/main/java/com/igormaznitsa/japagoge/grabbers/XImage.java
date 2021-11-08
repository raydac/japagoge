package com.igormaznitsa.japagoge.grabbers;

import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;

@Structure.FieldOrder({
        "width",
        "height",
        "xoffset",
        "format",
        "data",
        "byte_order",
        "bitmap_unit",
        "bitmap_bit_order",
        "bitmap_pad",
        "depth",
        "bytes_per_line",
        "bits_per_pixel",
        "red_mask",
        "green_mask",
        "blue_mask"
})
public class XImage extends Structure {

  public static final int XYBITMAP = 0;
  public static final int XYPIXMAP = 1;
  public static final int ZPIXMAP = 2;

  public static final int LSBFIRST = 0;
  public static final int MSBFIRST = 1;

  /**
   * Image width.
   */
  public int width;
  /**
   * Image height.
   */
  public int height;

  /**
   * number of pixels offset in X direction
   */
  public int xoffset;

  /**
   * XYBitmap, XYPixmap, ZPixmap
   */
  public int format;

  /**
   * pointer to image data.
   */
  public Pointer data;

  /**
   * data byte order, LSBFirst, MSBFirst
   */
  public int byte_order;
  /**
   * quant. of scanline 8, 16, 32
   */
  public int bitmap_unit;
  /**
   * LSBFirst, MSBFirst
   */
  public int bitmap_bit_order;
  /**
   * 8, 16, 32 either XY or ZPixmap
   */
  public int bitmap_pad;
  /**
   * depth of image
   */
  public int depth;
  /**
   * accelarator to next line
   */
  public int bytes_per_line;
  /**
   * bits per pixel (ZPixmap)
   */
  public int bits_per_pixel;
  /**
   * bits in z arrangment
   */
  public NativeLong red_mask;
  public NativeLong green_mask;
  public NativeLong blue_mask;

  public XImage() {
    super();
  }

  public XImage(Pointer p) {
    super(p);
    this.read();
  }

  public static class ByReference extends XImage implements Structure.ByReference {
  }
}
