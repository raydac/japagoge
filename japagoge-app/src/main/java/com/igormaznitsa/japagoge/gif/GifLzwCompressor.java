package com.igormaznitsa.japagoge.gif;

import java.io.IOException;
import java.io.OutputStream;

// Adpapted from Jef Poskanzer's Java port
final class GifLzwCompressor {

  private static final int BITS = 12;
  private static final int HSIZE = 5003;
  private static final int[] MASKS = {
          0x0000, 0x0001, 0x0003, 0x0007, 0x000F, 0x001F, 0x003F,
          0x007F, 0x00FF, 0x01FF, 0x03FF, 0x07FF, 0x0FFF, 0x1FFF,
          0x3FFF, 0x7FFF, 0xFFFF
  };
  private static final int EOF = -1;
  private final int[] hTab = new int[HSIZE];
  private final int[] codeTab = new int[HSIZE];
  private final int hSize = HSIZE;
  private final byte[] accumulator = new byte[256];
  private final int maxBits = BITS;
  private final int maxMaxCode = 1 << BITS;
  private final int imageWidth, imageHeight;
  private final byte[] pixArray;
  private final int initialCodeSize;
  private final OutputStream outputStream;
  private int nBits;
  private int maxCode;
  private int freeEnt = 0;
  private boolean clearFlag = false;
  private int gInitBits;
  private int clearCode;
  private int eofCode;
  private int curAccum = 0;
  private int curBits = 0;
  private int aCount;
  private int remaining;
  private int curPixel;

  public GifLzwCompressor(final OutputStream outputStream, final int imageWidth, final int imageHeight, final byte[] pixelIndexes) {
    this.outputStream = outputStream;
    this.imageWidth = imageWidth;
    this.imageHeight = imageHeight;
    this.pixArray = pixelIndexes;
    this.initialCodeSize = 8;
  }

  private static int getMaxCode(final int numBits) {
    return (1 << numBits) - 1;
  }

  private void charOut(final byte c) throws IOException {
    this.accumulator[this.aCount++] = c;
    if (this.aCount >= 254) {
      this.flushChar();
    }
  }

  private void clearBlock() throws IOException {
    this.clearHash(hSize);
    this.freeEnt = this.clearCode + 2;
    this.clearFlag = true;
    this.output(this.clearCode);
  }

  private void clearHash(final int hSize) {
    for (int i = 0; i < hSize; ++i)
      this.hTab[i] = -1;
  }

  private void compress(final int initBits) throws IOException {
    this.gInitBits = initBits;
    this.clearFlag = false;
    this.nBits = this.gInitBits;
    this.maxCode = getMaxCode(this.nBits);

    this.clearCode = 1 << (initBits - 1);
    this.eofCode = this.clearCode + 1;
    this.freeEnt = this.clearCode + 2;

    this.aCount = 0;

    int ent = this.nextPixel();

    int hShift = 0;

    int fCode = this.hSize;
    for (; fCode < 65536; fCode <<= 1) ++hShift;
    hShift = 8 - hShift;

    int hSizeReg = this.hSize;
    this.clearHash(hSizeReg); // clear hash table

    this.output(this.clearCode);

    int c;
    outer_loop:
    while ((c = nextPixel()) != EOF) {
      fCode = (c << maxBits) + ent;
      // xor hashing
      int i = (c << hShift) ^ ent;

      if (hTab[i] == fCode) {
        ent = codeTab[i];
        continue;
      } else if (hTab[i] >= 0) {
        int disp = hSizeReg - i;
        if (i == 0)
          disp = 1;
        do {
          if ((i -= disp) < 0)
            i += hSizeReg;

          if (this.hTab[i] == fCode) {
            ent = this.codeTab[i];
            continue outer_loop;
          }
        } while (hTab[i] >= 0);
      }
      this.output(ent);
      ent = c;
      if (this.freeEnt < this.maxMaxCode) {
        this.codeTab[i] = this.freeEnt++;
        this.hTab[i] = fCode;
      } else
        this.clearBlock();
    }
    this.output(ent);
    this.output(eofCode);
  }

  public void encode() throws IOException {
    this.outputStream.write(this.initialCodeSize);
    this.remaining = this.imageWidth * this.imageHeight;
    this.curPixel = 0;
    this.compress(this.initialCodeSize + 1);
    this.outputStream.write(0);
  }

  private void flushChar() throws IOException {
    if (this.aCount > 0) {
      this.outputStream.write(this.aCount);
      this.outputStream.write(this.accumulator, 0, this.aCount);
      this.aCount = 0;
    }
  }

  private int nextPixel() {
    if (this.remaining == 0)
      return EOF;
    --this.remaining;
    return this.pixArray[this.curPixel++] & 0xFF;
  }

  private void output(final int code) throws IOException {
    this.curAccum &= MASKS[this.curBits];

    if (this.curBits > 0)
      this.curAccum |= (code << this.curBits);
    else
      this.curAccum = code;

    this.curBits += this.nBits;

    while (this.curBits >= 8) {
      this.charOut((byte) (this.curAccum & 0xff));
      this.curAccum >>= 8;
      this.curBits -= 8;
    }

    if (this.freeEnt > this.maxCode || this.clearFlag) {
      if (this.clearFlag) {
        this.maxCode = getMaxCode(this.nBits = this.gInitBits);
        this.clearFlag = false;
      } else {
        ++this.nBits;
        if (this.nBits == this.maxBits) {
          this.maxCode = this.maxMaxCode;
        } else {
          this.maxCode = getMaxCode(this.nBits);
        }
      }
    }

    if (code == this.eofCode) {
      while (this.curBits > 0) {
        this.charOut((byte) (this.curAccum & 0xff));
        this.curAccum >>= 8;
        this.curBits -= 8;
      }
      this.flushChar();
    }
  }
}
