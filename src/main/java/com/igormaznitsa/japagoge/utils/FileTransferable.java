package com.igormaznitsa.japagoge.utils;

import java.awt.datatransfer.*;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

public class FileTransferable implements ClipboardOwner, Transferable {
  private static final DataFlavor DATA_FLAVOR_ARRAY_PNG;
  private static final DataFlavor DATA_FLAVOR_ARRAY_GIF;
  private static final DataFlavor DATA_FLAVOR_OCTET_STREAM;
  private static final long MAX_SIZE_FOR_FILE_PLACE_IN_CLIPBOARD = 8 * 1024 * 1024;

  static {
    try {
      DATA_FLAVOR_ARRAY_GIF = new DataFlavor("image/gif");
      DATA_FLAVOR_ARRAY_PNG = new DataFlavor("image/png");
      DATA_FLAVOR_OCTET_STREAM = new DataFlavor("application/octet-stream");
    } catch (Exception ex) {
      throw new Error(ex);
    }
  }

  private final File file;
  private final byte[] fileBody;
  private final DataFlavor[] flavors;

  public FileTransferable(final File file) {
    this.file = Objects.requireNonNull(file);

    final List<DataFlavor> flavorsList = new ArrayList<>();
    flavorsList.add(DataFlavor.stringFlavor);
    flavorsList.add(DataFlavor.javaFileListFlavor);

    if (this.file.isFile() && this.file.length() <= MAX_SIZE_FOR_FILE_PLACE_IN_CLIPBOARD) {
      var name = file.getName().toLowerCase(Locale.ENGLISH);
      final DataFlavor imageFlavor;
      if (name.endsWith(".png")) {
        imageFlavor = DATA_FLAVOR_ARRAY_PNG;
      } else if (name.endsWith(".gif")) {
        imageFlavor = DATA_FLAVOR_ARRAY_GIF;
      } else {
        imageFlavor = null;
      }
      if (imageFlavor != null) {
        byte[] readBody;
        try {
          readBody = Files.readAllBytes(this.file.toPath());
          flavorsList.add(imageFlavor);
        } catch (IOException ex) {
          readBody = null;
        }
        this.fileBody = readBody;
      } else {
        this.fileBody = null;
      }
    } else {
      this.fileBody = null;
    }

    this.flavors = flavorsList.toArray(new DataFlavor[0]);
  }

  public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
    if (DataFlavor.javaFileListFlavor.equals(flavor)) {
      return List.of(this.file);
    } else if (DataFlavor.stringFlavor.equals(flavor)) {
      return this.file.getAbsolutePath();
    } else if (this.fileBody != null &&
            (DATA_FLAVOR_OCTET_STREAM.equals(flavor) || DATA_FLAVOR_ARRAY_PNG.equals(flavor) || DATA_FLAVOR_ARRAY_GIF.equals(flavor))) {
      return new ByteArrayInputStream(this.fileBody);
    } else {
      throw new UnsupportedFlavorException(flavor);
    }
  }

  public DataFlavor[] getTransferDataFlavors() {
    return this.flavors;
  }

  public boolean isDataFlavorSupported(final DataFlavor flavor) {
    return Arrays.stream(this.flavors).anyMatch(x -> x.equals(flavor));
  }

  public void lostOwnership(final Clipboard clipboard, final Transferable contents) {
    //nothing
  }
}

