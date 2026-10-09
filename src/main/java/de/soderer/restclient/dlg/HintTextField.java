package de.soderer.restclient.dlg;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Toolkit;
import java.util.Map;

import javax.swing.JTextField;
import javax.swing.UIManager;

/**
 * JTextField showing a gray hint text while it is empty, the Swing counterpart
 * of SWT's Text.setMessage(). The hint is only painted, it is never part of the
 * field's text.
 *
 * @serial exclude
 */
public class HintTextField extends JTextField {
	private static final long serialVersionUID = 6627102934785196432L;

	private String hint;

	/**
	 * Creates a text field without hint text.
	 */
	public HintTextField() {
		super();
	}

	/**
	 * Creates a text field with a hint text.
	 *
	 * @param hint text shown while the field is empty, or null for none
	 */
	public HintTextField(final String hint) {
		super();
		this.hint = hint;
	}

	/**
	 * Returns the hint text.
	 *
	 * @return the hint text, or null if none is set
	 */
	public String getHint() {
		return hint;
	}

	/**
	 * Sets the hint text shown while the field is empty.
	 *
	 * @param hint the hint text, or null for none
	 */
	public void setHint(final String hint) {
		this.hint = hint;
		repaint();
	}

	@Override
	protected void paintComponent(final Graphics graphics) {
		super.paintComponent(graphics);

		if (hint != null && !hint.isEmpty() && getDocument().getLength() == 0) {
			final Graphics2D graphics2D = (Graphics2D) graphics.create();
			try {
				final Object desktopHints = Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints");
				if (desktopHints instanceof Map) {
					graphics2D.addRenderingHints((Map<?, ?>) desktopHints);
				}

				final Color hintColor = UIManager.getColor("TextField.inactiveForeground");
				graphics2D.setColor(hintColor != null ? hintColor : Color.GRAY);
				graphics2D.setFont(getFont());

				final Insets insets = getInsets();
				final FontMetrics fontMetrics = graphics2D.getFontMetrics();
				final int innerHeight = getHeight() - insets.top - insets.bottom;
				final int baseline = insets.top + (innerHeight - fontMetrics.getHeight()) / 2 + fontMetrics.getAscent();
				graphics2D.drawString(hint, insets.left, baseline);
			} finally {
				graphics2D.dispose();
			}
		}
	}
}
