package com.igormaznitsa.japagoge.mouse;

import java.awt.*;

public interface MouseInfoProvider {
  Point getMousePointerLocation();

  MousePointerIcon getMousePointerIcon();
}
