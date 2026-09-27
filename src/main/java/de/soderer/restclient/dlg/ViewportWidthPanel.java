package de.soderer.restclient.dlg;

import java.awt.Container;
import java.awt.Dimension;
import java.awt.LayoutManager;
import java.awt.Rectangle;

import javax.swing.JPanel;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;

/**
 * Panel for use as the view of a JScrollPane, which always takes the full
 * width of the viewport (so it only ever scrolls vertically) and also fills its
 * full height while its content is smaller than the viewport. The Swing
 * counterpart of an SWT ScrolledComposite with expandHorizontal/expandVertical
 * and a minimum height.
 */
public class ViewportWidthPanel extends JPanel implements Scrollable {
	private static final long serialVersionUID = -2279164541062803147L;

	private static final int UNIT_INCREMENT = 16;

	public ViewportWidthPanel(final LayoutManager layout) {
		super(layout);
	}

	@Override
	public Dimension getPreferredScrollableViewportSize() {
		return getPreferredSize();
	}

	@Override
	public int getScrollableUnitIncrement(final Rectangle visibleRect, final int orientation, final int direction) {
		return UNIT_INCREMENT;
	}

	@Override
	public int getScrollableBlockIncrement(final Rectangle visibleRect, final int orientation, final int direction) {
		return orientation == SwingConstants.VERTICAL ? visibleRect.height : visibleRect.width;
	}

	@Override
	public boolean getScrollableTracksViewportWidth() {
		return true;
	}

	@Override
	public boolean getScrollableTracksViewportHeight() {
		final Container parent = getParent();
		return parent instanceof JViewport && parent.getHeight() > getPreferredSize().height;
	}
}
