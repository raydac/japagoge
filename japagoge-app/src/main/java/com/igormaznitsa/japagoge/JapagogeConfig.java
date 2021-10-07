package com.igormaznitsa.japagoge;

import com.igormaznitsa.japagoge.filters.RgbPixelFilter;
import com.igormaznitsa.japagoge.utils.Palette256;

import java.io.File;
import java.util.Locale;
import java.util.prefs.Preferences;

public class JapagogeConfig {

  private static final JapagogeConfig INSTANCE = new JapagogeConfig();
  private final Preferences preferences;

  private JapagogeConfig() {
    this.preferences = Preferences.userNodeForPackage(JapagogeConfig.class);
  }

  public static JapagogeConfig getInstance() {
    return INSTANCE;
  }

  public RgbPixelFilter getFilter() {
    try {
      return RgbPixelFilter.valueOf(this.preferences.get(Key.FILTER.name(), RgbPixelFilter.RGB.name()).trim().toUpperCase(Locale.ENGLISH));
    } catch (Exception ex) {
      return RgbPixelFilter.RGB;
    }
  }

  public void setFilter(final RgbPixelFilter filter) {
    if (filter == null) {
      this.preferences.remove(Key.FILTER.name());
    } else {
      this.preferences.put(Key.FILTER.name(), filter.name());
    }
  }

  public String getTempFolder() {
    return this.preferences.get(Key.TEMP_FOLDER.name(), "");
  }

  public void setTempFolder(final String value) {
    if (value == null) {
      this.preferences.remove(Key.TEMP_FOLDER.name());
    } else {
      this.preferences.put(Key.TEMP_FOLDER.name(), value);
    }
  }

  public Palette256 getGifPaletteForRgb() {
    try {
      return Palette256.valueOf(this.preferences.get(Key.GIF_PALETTE_FOR_RGB.name(), Palette256.AUTO.name()).trim().toUpperCase(Locale.ENGLISH));
    } catch (Exception ex) {
      return Palette256.AUTO;
    }
  }

  public void setGifPaletteForRgb(final Palette256 palette) {
    if (palette == null) {
      this.preferences.remove(Key.GIF_PALETTE_FOR_RGB.name());
    } else {
      this.preferences.put(Key.GIF_PALETTE_FOR_RGB.name(), palette.name());
    }
  }

  public boolean isPointer() {
    return this.preferences.getBoolean(Key.POINTER.name(), true);
  }

  public void setPointer(final boolean flag) {
    this.preferences.putBoolean(Key.POINTER.name(), flag);
  }

  public boolean isShowBoundsInfo() {
    return this.preferences.getBoolean(Key.SHOW_BOUNDS_INFO.name(), false);
  }

  public void setShowBoundsInfo(final boolean flag) {
    this.preferences.putBoolean(Key.SHOW_BOUNDS_INFO.name(), flag);
  }

  public boolean isForceWholeFrame() {
    return this.preferences.getBoolean(Key.FORCE_WHOLE_FRAME.name(), false);
  }

  public void setForceWholeFrame(final boolean flag) {
    this.preferences.putBoolean(Key.FORCE_WHOLE_FRAME.name(), flag);
  }

  public boolean isAccurateRgb() {
    return this.preferences.getBoolean(Key.ACCURATE_RGB.name(), false);
  }

  public void setAccurateRgb(final boolean flag) {
    this.preferences.putBoolean(Key.ACCURATE_RGB.name(), flag);
  }

  public boolean isDithering() {
    return this.preferences.getBoolean(Key.DITHERING.name(), false);
  }

  public void setDithering(final boolean flag) {
    this.preferences.putBoolean(Key.DITHERING.name(), flag);
  }

  public long getFrameDelay() {
    return this.preferences.getLong(Key.FRAME_DELAY.name(), 100);
  }

  public void setFrameDelay(final long delayMs) {
    this.preferences.putLong(Key.FRAME_DELAY.name(), Math.max(10, Math.min(delayMs, Short.MAX_VALUE)));
  }

  public long getCaptureDelay() {
    return this.preferences.getLong(Key.CAPTURE_DELAY.name(), this.getFrameDelay());
  }

  public void setCaptureDelay(final long delayMs) {
    this.preferences.putLong(Key.CAPTURE_DELAY.name(), Math.max(10, Math.min(delayMs, Short.MAX_VALUE)));
  }

  public int getLoops() {
    return Math.max(0, this.preferences.getInt(Key.LOOPS.name(), 0));
  }

  public void setLoops(final int loops) {
    this.preferences.putInt(Key.LOOPS.name(), Math.max(0, loops));
  }

  public File getTargetFolder() {
    var path = this.preferences.get(Key.FOLDER_PATH.name(), null);
    try {
      return new File(path);
    } catch (Exception ex) {
      return null;
    }
  }

  public void setTargetFolder(final File folder) {
    if (folder == null) {
      this.preferences.remove(Key.FOLDER_PATH.name());
    } else {
      var path = folder.isFile() ? folder.getParentFile() : folder;
      if (path == null) {
        this.preferences.remove(Key.FOLDER_PATH.name());
      } else {
        this.preferences.put(Key.FOLDER_PATH.name(), path.getAbsolutePath());
      }
    }
  }

  public void flush() {
    try {
      this.preferences.flush();
    } catch (final Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  private enum Key {
    TEMP_FOLDER,
    FORCE_WHOLE_FRAME,
    SHOW_BOUNDS_INFO,
    CAPTURE_DELAY,
    FRAME_DELAY,
    LOOPS,
    ACCURATE_RGB,
    DITHERING,
    POINTER,
    GIF_PALETTE_FOR_RGB,
    FOLDER_PATH,
    FILTER
  }

  public static class JapagogeConfigData {
    private String tenpFolder;
    private boolean pointer;
    private boolean forceWholeFrame;
    private boolean showBoundsInfo;
    private boolean dithering;
    private boolean accurateRgb;
    private RgbPixelFilter filter;
    private Palette256 gifPaletteForRgb;
    private long frameDelay;
    private long captureDelay;
    private File targetFolder;
    private int loops;

    public JapagogeConfigData() {
      this.tenpFolder = getInstance().getTempFolder();
      this.accurateRgb = getInstance().isAccurateRgb();
      this.dithering = getInstance().isDithering();
      this.gifPaletteForRgb = getInstance().getGifPaletteForRgb();
      this.pointer = getInstance().isPointer();
      this.showBoundsInfo = getInstance().isShowBoundsInfo();
      this.forceWholeFrame = getInstance().isForceWholeFrame();
      this.filter = getInstance().getFilter();
      this.frameDelay = getInstance().getFrameDelay();
      this.captureDelay = getInstance().getCaptureDelay();
      this.targetFolder = getInstance().getTargetFolder();
      this.loops = getInstance().getLoops();
    }

    public boolean isForceWholeFrame() {
      return forceWholeFrame;
    }

    public void setForceWholeFrame(final boolean value) {
      this.forceWholeFrame = value;
    }

    public boolean isShowBoundsInfo() {
      return this.showBoundsInfo;
    }

    public void setShowBoundsInfo(final boolean flag) {
      this.showBoundsInfo = flag;
    }

    public String getTenpFolder() {
      return this.tenpFolder;
    }

    public void setTenpFolder(final String path) {
      this.tenpFolder = path;
    }

    public RgbPixelFilter getFilter() {
      return this.filter;
    }

    public void setFilter(final RgbPixelFilter filter) {
      this.filter = filter == null ? RgbPixelFilter.RGB : filter;
    }

    public Palette256 getGifPaletteForRgb() {
      return this.gifPaletteForRgb;
    }

    public void setGifPaletteForRgb(final Palette256 palette) {
      this.gifPaletteForRgb = palette == null ? Palette256.AUTO : palette;
    }

    public boolean isPointer() {
      return this.pointer;
    }

    public void setPointer(final boolean pointer) {
      this.pointer = pointer;
    }

    public boolean isAccurateRgb() {
      return this.accurateRgb;
    }

    public void setAccurateRgb(final boolean flag) {
      this.accurateRgb = flag;
    }

    public boolean isDithering() {
      return this.dithering;
    }

    public void setDithering(final boolean flag) {
      this.dithering = flag;
    }

    public long getFrameDelay() {
      return frameDelay;
    }

    public void setFrameDelay(final long frameDelay) {
      this.frameDelay = frameDelay;
    }

    public long getCaptureDelay() {
      return captureDelay;
    }

    public void setCaptureDelay(final long delay) {
      this.captureDelay = delay;
    }

    public File getTargetFolder() {
      return this.targetFolder;
    }

    public void setTargetFolder(final File targetFolder) {
      this.targetFolder = targetFolder;
    }

    public int getLoops() {
      return this.loops;
    }

    public void setLoops(final int loops) {
      this.loops = loops;
    }

    public void save() {
      getInstance().setTempFolder(this.tenpFolder);
      getInstance().setGifPaletteForRgb(this.gifPaletteForRgb);
      getInstance().setPointer(this.pointer);
      getInstance().setForceWholeFrame(this.forceWholeFrame);
      getInstance().setShowBoundsInfo(this.showBoundsInfo);
      getInstance().setAccurateRgb(this.accurateRgb);
      getInstance().setDithering(this.dithering);
      getInstance().setFilter(this.filter);
      getInstance().setLoops(this.loops);
      getInstance().setTargetFolder(this.targetFolder);
      getInstance().setFrameDelay(this.frameDelay);
      getInstance().setCaptureDelay(this.captureDelay);
      getInstance().flush();
    }
  }
}
