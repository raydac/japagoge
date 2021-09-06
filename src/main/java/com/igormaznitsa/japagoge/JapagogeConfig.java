package com.igormaznitsa.japagoge;

import com.igormaznitsa.japagoge.filters.RgbPixelFilter;

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
    this.preferences.put(Key.FILTER.name(), filter.name());
  }

  public boolean isPointer() {
    return this.preferences.getBoolean(Key.POINTER.name(), true);
  }

  public void setPointer(final boolean flag) {
    this.preferences.putBoolean(Key.POINTER.name(), flag);
  }

  public long getFrameDelay() {
    return this.preferences.getLong(Key.FRAME_DELAY.name(), 80);
  }

  public void setFrameDelay(final long delayMs) {
    this.preferences.putLong(Key.FRAME_DELAY.name(), Math.max(20, delayMs));
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
    FRAME_DELAY,
    LOOPS,
    POINTER,
    FOLDER_PATH,
    FILTER
  }

  public static class JapagogeConfigData {
    private boolean pointer;
    private RgbPixelFilter filter;
    private long frameDelay;
    private File targetFolder;
    private int loops;

    public JapagogeConfigData() {
      this.pointer = getInstance().isPointer();
      this.filter = getInstance().getFilter();
      this.frameDelay = getInstance().getFrameDelay();
      this.targetFolder = getInstance().getTargetFolder();
      this.loops = getInstance().getLoops();
    }

    public RgbPixelFilter getFilter() {
      return this.filter;
    }

    public void setFilter(final RgbPixelFilter filter) {
      this.filter = filter == null ? RgbPixelFilter.RGB : filter;
    }

    public boolean isPointer() {
      return this.pointer;
    }

    public void setPointer(final boolean pointer) {
      this.pointer = pointer;
    }

    public long getFrameDelay() {
      return frameDelay;
    }

    public void setFrameDelay(final long frameDelay) {
      this.frameDelay = frameDelay;
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
      getInstance().setPointer(this.pointer);
      getInstance().setFilter(this.filter);
      getInstance().setLoops(this.loops);
      getInstance().setTargetFolder(this.targetFolder);
      getInstance().setFrameDelay(this.frameDelay);
      getInstance().flush();
    }
  }
}
