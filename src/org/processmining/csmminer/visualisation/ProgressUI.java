package org.processmining.csmminer.visualisation;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;

import javax.swing.JComponent;
import javax.swing.JLayer;
import javax.swing.Timer;
import javax.swing.plaf.LayerUI;

public class ProgressUI extends LayerUI<JComponent> implements ActionListener {

	/**
	 * 
	 */
	private static final long serialVersionUID = 4538120804600504998L;

	private final Timer progressTimer;

	private int currentTick = 0;
	private int maxTick = 10;

	private int angle;

	public ProgressUI() {
		super();
		progressTimer = new Timer(1000 / 12, this);
		progressTimer.setInitialDelay(500);
	}

	@Override
	public void paint(Graphics g, JComponent c) {
		int width = c.getWidth();
		int height = c.getHeight();

		super.paint(g, c);

		if (progressTimer.isRunning()) {
			Graphics2D g2 = (Graphics2D) g.create();
			Composite composite = g2.getComposite();
			float fadeFactor = currentTick / (float) maxTick;
			g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, .25f * fadeFactor));
			g2.fillRect(0, 0, c.getWidth(), c.getHeight());
			g2.setComposite(composite);

			int s = Math.min(width, height) / 15;
			int cx = width / 2;
			int cy = height / 2;
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setStroke(new BasicStroke(s / 4, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_ROUND));
			g2.setPaint(Color.WHITE);
			g2.rotate(Math.PI * angle / 180, cx, cy);
			for (int i = 0; i < 12; i++) {
				g2.drawLine(cx + s, cy, cx + s * 2, cy);
				g2.rotate(-Math.PI / 6, cx, cy);
			}

			g2.dispose();
		}
	}

	public void showProgress() {
		currentTick = 0;
		progressTimer.start();
	}

	public void hideProgress() {
		progressTimer.stop();
		firePropertyChange("progress", true, false);
	}

	public void applyPropertyChange(PropertyChangeEvent evt, JLayer<? extends JComponent> l) {
		super.applyPropertyChange(evt, l);
		l.repaint();
	}

	public void actionPerformed(ActionEvent e) {
		firePropertyChange("progress", false, true);
		angle += 3;
		if (angle >= 360) {
			angle = 0;
		}
		currentTick = Math.min(maxTick, currentTick + 1);
	}

}
