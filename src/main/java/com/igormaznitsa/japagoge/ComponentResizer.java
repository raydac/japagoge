package com.igormaznitsa.japagoge;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.Map;

/**
 * The ComponentResizer allows you to resize a component by dragging a border
 * of the component.
 * reworked version of https://tips4java.wordpress.com/2009/09/13/resizing-components/
 */
@SuppressWarnings("unused")
public final class ComponentResizer extends MouseAdapter {
  private static final int NORTH = 1;
  private static final int WEST = 2;
  private static final int SOUTH = 4;
  private static final int EAST = 8;
  private final static Dimension MINIMUM_SIZE = new Dimension(10, 10);
  private final static Dimension MAXIMUM_SIZE =
          new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE);
  private static final Map<Integer, Cursor> cursors;

  static {
    cursors = new HashMap<>();
    cursors.put(1, Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR));
    cursors.put(2, Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR));
    cursors.put(4, Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR));
    cursors.put(8, Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
    cursors.put(3, Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR));
    cursors.put(9, Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR));
    cursors.put(6, Cursor.getPredefinedCursor(Cursor.SW_RESIZE_CURSOR));
    cursors.put(12, Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR));
  }

  private Insets dragInsets;
  private Dimension snapSize;
  private int direction;
  private Cursor sourceCursor;
  private boolean resizing;
  private Rectangle bounds;
  private Point pressed;
  private boolean autoScrolls;
  private Dimension minimumSize = MINIMUM_SIZE;
  private Dimension maximumSize = MAXIMUM_SIZE;
  private boolean enabled = true;

  public ComponentResizer(Insets dragInsets, Component... components) {
    this(dragInsets, new Dimension(1, 1), components);
  }

  public ComponentResizer(Insets dragInsets, Dimension snapSize, Component... components) {
    setDragInsets(dragInsets);
    setSnapSize(snapSize);
    registerComponent(components);
  }

  public boolean isEnabled() {
    return this.enabled;
  }

  public void setEnable(final boolean flag) {
    this.enabled = flag;
  }

  public Insets getDragInsets() {
    return dragInsets;
  }

  public void setDragInsets(Insets dragInsets) {
    validateMinimumAndInsets(minimumSize, dragInsets);

    this.dragInsets = dragInsets;
  }

  public Dimension getMaximumSize() {
    return maximumSize;
  }

  public void setMaximumSize(Dimension maximumSize) {
    this.maximumSize = maximumSize;
  }

  public Dimension getMinimumSize() {
    return minimumSize;
  }

  public void setMinimumSize(Dimension minimumSize) {
    validateMinimumAndInsets(minimumSize, dragInsets);

    this.minimumSize = minimumSize;
  }

  public void deregisterComponent(final Component... components) {
    for (final Component component : components) {
      component.removeMouseListener(this);
      component.removeMouseMotionListener(this);
    }
  }

  public void registerComponent(final Component... components) {
    for (final Component component : components) {
      component.addMouseListener(this);
      component.addMouseMotionListener(this);
    }
  }

  public Dimension getSnapSize() {
    return this.snapSize;
  }

  public void setSnapSize(Dimension snapSize) {
    this.snapSize = snapSize;
  }

  private void validateMinimumAndInsets(final Dimension minimum, final Insets drag) {
    int minimumWidth = drag.left + drag.right;
    int minimumHeight = drag.top + drag.bottom;

    if (minimum.width < minimumWidth
            || minimum.height < minimumHeight) {
      String message = "Minimum size cannot be less than drag insets";
      throw new IllegalArgumentException(message);
    }
  }

  @Override
  public void mouseMoved(final MouseEvent event) {
    if (this.enabled && !event.isConsumed()) {
      final Component source = event.getComponent();
      Point location = event.getPoint();
      direction = 0;

      if (location.x < dragInsets.left)
        direction += WEST;

      if (location.x > source.getWidth() - dragInsets.right - 1)
        direction += EAST;

      if (location.y < dragInsets.top)
        direction += NORTH;

      if (location.y > source.getHeight() - dragInsets.bottom - 1)
        direction += SOUTH;

      //  Mouse is no longer over a resizable border

      if (direction == 0) {
        source.setCursor(sourceCursor);
      } else  // use the appropriate resizable cursor
      {
        source.setCursor(cursors.get(direction));
      }
    }
  }

  @Override
  public void mouseEntered(final MouseEvent event) {
    if (this.enabled && !event.isConsumed() && !resizing) {
      Component source = event.getComponent();
      sourceCursor = source.getCursor();
    }
  }

  @Override
  public void mouseExited(final MouseEvent event) {
    if (this.enabled && !event.isConsumed() && !resizing) {
      Component source = event.getComponent();
      source.setCursor(sourceCursor);
    }
  }

  @Override
  public void mousePressed(final MouseEvent event) {
    if (this.enabled && !event.isConsumed()) {
      //	The mouseMoved event continually updates this variable

      if (direction == 0) return;

      //  Setup for resizing. All future dragging calculations are done based
      //  on the original bounds of the component and mouse pressed location.

      resizing = true;

      Component source = event.getComponent();
      pressed = event.getPoint();
      SwingUtilities.convertPointToScreen(pressed, source);
      bounds = source.getBounds();

      if (source instanceof JComponent) {
        JComponent jc = (JComponent) source;
        autoScrolls = jc.getAutoscrolls();
        jc.setAutoscrolls(false);
      }
    }
  }

  @Override
  public void mouseReleased(final MouseEvent event) {
    if (this.enabled && !event.isConsumed()) {
      this.resizing = false;

      Component source = event.getComponent();
      source.setCursor(sourceCursor);

      if (source instanceof JComponent) {
        ((JComponent) source).setAutoscrolls(autoScrolls);
      }
    }
  }

  @Override
  public void mouseDragged(final MouseEvent event) {
    if (this.enabled && !event.isConsumed() && this.resizing) {
      Component source = event.getComponent();
      Point dragged = event.getPoint();
      SwingUtilities.convertPointToScreen(dragged, source);

      this.changeBounds(source, direction, bounds, pressed, dragged);
    }
  }

  private void changeBounds(final Component source, final int direction, final Rectangle bounds, final Point pressed, final Point current) {
    int x = bounds.x;
    int y = bounds.y;
    int width = bounds.width;
    int height = bounds.height;

    //  Resizing the West or North border affects the size and location

    if (WEST == (direction & WEST)) {
      int drag = getDragDistance(pressed.x, current.x, snapSize.width);
      int maximum = Math.min(width + x, maximumSize.width);
      drag = getDragBounded(drag, snapSize.width, width, minimumSize.width, maximum);

      x -= drag;
      width += drag;
    }

    if (NORTH == (direction & NORTH)) {
      int drag = getDragDistance(pressed.y, current.y, snapSize.height);
      int maximum = Math.min(height + y, maximumSize.height);
      drag = getDragBounded(drag, snapSize.height, height, minimumSize.height, maximum);

      y -= drag;
      height += drag;
    }

    //  Resizing the East or South border only affects the size

    if (EAST == (direction & EAST)) {
      int drag = getDragDistance(current.x, pressed.x, snapSize.width);
      Dimension boundingSize = getBoundingSize(source);
      int maximum = Math.min(boundingSize.width - x, maximumSize.width);
      drag = getDragBounded(drag, snapSize.width, width, minimumSize.width, maximum);
      width += drag;
    }

    if (SOUTH == (direction & SOUTH)) {
      int drag = getDragDistance(current.y, pressed.y, snapSize.height);
      Dimension boundingSize = getBoundingSize(source);
      int maximum = Math.min(boundingSize.height - y, maximumSize.height);
      drag = getDragBounded(drag, snapSize.height, height, minimumSize.height, maximum);
      height += drag;
    }

    source.setBounds(x, y, width, height);
    source.validate();
  }

  private int getDragDistance(int larger, int smaller, int snapSize) {
    int halfway = snapSize / 2;
    int drag = larger - smaller;
    drag += (drag < 0) ? -halfway : halfway;
    drag = (drag / snapSize) * snapSize;

    return drag;
  }

  private int getDragBounded(int drag, int snapSize, int dimension, int minimum, int maximum) {
    while (dimension + drag < minimum)
      drag += snapSize;

    while (dimension + drag > maximum)
      drag -= snapSize;


    return drag;
  }

  private Dimension getBoundingSize(Component source) {
    if (source instanceof Window) {
      GraphicsEnvironment e = GraphicsEnvironment.getLocalGraphicsEnvironment();
      GraphicsDevice[] devices = e.getScreenDevices();
      Rectangle bounds = new Rectangle();

      for (GraphicsDevice device : devices) {
        GraphicsConfiguration[] configurations = device.getConfigurations();

        for (GraphicsConfiguration config : configurations) {
          Rectangle gcBounds = config.getBounds();
          bounds.add(gcBounds);
        }
      }
      return new Dimension(bounds.width, bounds.height);
    } else {
      return source.getParent().getSize();
    }
  }
}