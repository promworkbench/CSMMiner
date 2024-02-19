package org.processmining.csmminer.visualisation;

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JSplitPane;
import javax.swing.border.Border;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;

public class ImprovedSplitPaneDivider extends BasicSplitPaneDivider {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public ImprovedSplitPaneDivider(final BasicSplitPaneUI ui) {
		super(ui);
	}

	@Override
	public void paint(final Graphics g) {
		g.setColor(getBackground());
		g.fillRect(0, 0, getWidth(), getHeight());
		paintComponents(g);
	}

	@Override
	protected JButton createLeftOneTouchButton() {
		final JButton b = new JButton() {
			/**
			 * 
			 */
			private static final long serialVersionUID = 1L;

			// Don't want the button to participate in focus traversable.
			@Override
			@Deprecated
			public boolean isFocusTraversable() {
				return false;
			}

			@Override
			public void paint(final Graphics g) {
				if (splitPane != null) {
					final int[] xs = new int[3];
					final int[] ys = new int[3];
					int blockSize;

					// Fill the background first ...
					g.setColor(ImprovedSplitPaneDivider.this.getBackground());
					g.fillRect(0, 0, getWidth(), getHeight());

					// ... then draw the arrow.
					g.setColor(ImprovedSplitPaneDivider.this.getForeground());
					if (orientation == JSplitPane.VERTICAL_SPLIT) {
						blockSize = Math.min(getHeight(), BasicSplitPaneDivider.ONE_TOUCH_SIZE);
						xs[0] = blockSize;
						xs[1] = 0;
						xs[2] = blockSize << 1;
						ys[0] = 0;
						ys[1] = ys[2] = blockSize;
						g.drawPolygon(xs, ys, 3); // Little trick to make the
						// arrows of equal size
					} else {
						blockSize = Math.min(getWidth(), BasicSplitPaneDivider.ONE_TOUCH_SIZE);
						xs[0] = xs[2] = blockSize;
						xs[1] = 0;
						ys[0] = 0;
						ys[1] = blockSize;
						ys[2] = blockSize << 1;
					}
					g.fillPolygon(xs, ys, 3);
				}
			}

			@Override
			public void setBorder(final Border b) {
			}
		};
		b.setMinimumSize(new Dimension(BasicSplitPaneDivider.ONE_TOUCH_SIZE, BasicSplitPaneDivider.ONE_TOUCH_SIZE));
		b.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
		b.setFocusPainted(false);
		b.setBorderPainted(false);
		b.setRequestFocusEnabled(false);
		b.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				leftClicked();
			}
		});
		return b;
	}

	/**
	 * Creates and return an instance of JButton that can be used to
	 * collapse the right component in the split pane.
	 */
	@Override
	protected JButton createRightOneTouchButton() {
		final JButton b = new JButton() {
			/**
			 * 
			 */
			private static final long serialVersionUID = 1L;

			// Don't want the button to participate in focus traversable.
			@Override
			@Deprecated
			public boolean isFocusTraversable() {
				return false;
			}

			@Override
			public void paint(final Graphics g) {
				if (splitPane != null) {
					final int[] xs = new int[3];
					final int[] ys = new int[3];
					int blockSize;

					// Fill the background first ...
					g.setColor(ImprovedSplitPaneDivider.this.getBackground());
					g.fillRect(0, 0, getWidth(), getHeight());

					// ... then draw the arrow.
					if (orientation == JSplitPane.VERTICAL_SPLIT) {
						blockSize = Math.min(getHeight(), BasicSplitPaneDivider.ONE_TOUCH_SIZE);
						xs[0] = blockSize;
						xs[1] = blockSize << 1;
						xs[2] = 0;
						ys[0] = blockSize;
						ys[1] = ys[2] = 0;
					} else {
						blockSize = Math.min(getWidth(), BasicSplitPaneDivider.ONE_TOUCH_SIZE);
						xs[0] = xs[2] = 0;
						xs[1] = blockSize;
						ys[0] = 0;
						ys[1] = blockSize;
						ys[2] = blockSize << 1;
					}
					g.setColor(ImprovedSplitPaneDivider.this.getForeground());
					g.fillPolygon(xs, ys, 3);
				}
			}

			@Override
			public void setBorder(final Border border) {
			}
		};
		b.setMinimumSize(new Dimension(BasicSplitPaneDivider.ONE_TOUCH_SIZE, BasicSplitPaneDivider.ONE_TOUCH_SIZE));
		b.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
		b.setFocusPainted(false);
		b.setBorderPainted(false);
		b.setRequestFocusEnabled(false);
		b.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				rightClicked();
			}
		});
		return b;
	}

	protected void leftClicked() {

	}

	protected void rightClicked() {

	}
}
