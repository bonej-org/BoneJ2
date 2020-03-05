/*
BSD 2-Clause License
Copyright (c) 2018, Michael Doube, Richard Domander, Alessandro Felder
All rights reserved.
Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
* Redistributions of source code must retain the above copyright notice, this
  list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright notice,
  this list of conditions and the following disclaimer in the documentation
  and/or other materials provided with the distribution.
THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package org.bonej.plugins;

import java.awt.BasicStroke;
import java.awt.Checkbox;
import java.awt.CheckboxGroup;
import java.awt.Choice;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Label;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Scrollbar;
import java.awt.Shape;
import java.awt.TextField;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.event.TextEvent;
import java.awt.event.TextListener;
import java.awt.event.WindowEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.GUI;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.gui.ShapeRoi;
import ij.gui.TextRoi;
import ij.plugin.frame.PlugInFrame;

/**
 * Indicator to show directions such as medial, proximal, cranial, north, left
 *
 * @author Michael Doube
 * @author Wayne Rasband
 */
// TODO Fix design: getInstance() probably shouldn't return reference to Orienteer.instance,
// but a clone of it. For example, what would happen if user called close() on that reference?
@SuppressWarnings("serial")
public final class Orienteer extends PlugInFrame implements AdjustmentListener,
	ItemListener, TextListener, MouseWheelListener
{

	private static final String LOC_KEY = "aa.loc";
	private static final String[][] axisLabels = { { "medial", "lateral", "M",
		"L" }, { "cranial", "caudal", "Cr", "Ca" }, { "rostral", "caudal", "Ro",
			"Ca" }, { "dorsal", "ventral", "D", "V" }, { "anterior", "posterior", "A",
				"P" }, { "superior", "inferior", "Sup", "Inf" }, { "proximal", "distal",
					"Pr", "Di" }, { "dorsal", "palmar", "Do", "Pa" }, { "dorsal",
						"plantar", "Do", "Pl" }, { "dorsal", "volar", "Do", "Vo" }, {
							"axial", "abaxial", "Ax", "Ab" }, { "north", "south", "N", "S" },
		{ "east", "west", "E", "W" }, { "up", "down", "Up", "D" }, { "right",
			"left", "R", "L" } };
	private static Orienteer instance;
	private final Map<Integer, Double> thetaHash = new HashMap<>();
	private final Map<Integer, Integer> lengthHash = new HashMap<>();
	private final Map<Integer, Point> centreHash = new HashMap<>();
	private final Map<Integer, GeneralPath> pathHash = new HashMap<>();
	private final Map<Integer, int[]> axisHash = new HashMap<>();
	private final Map<Integer, boolean[]> reflectHash = new HashMap<>();
	private final Map<Integer, boolean[]> unitHash = new HashMap<>();
	private final Overlay overlay = new Overlay();
	private final int fontSize = 12;
	private final Scrollbar slider = new Scrollbar(Scrollbar.HORIZONTAL, 0, 1, 0,
		360);
	private final Choice axis0Choice = new Choice();
	private final Choice axis1Choice = new Choice();
	private final Checkbox reflect0 = new Checkbox("Reflect");
	private final Checkbox reflect1 = new Checkbox("Reflect");
	private final TextField text = new TextField(IJ.d2s(0.0, 3), 7);
	private final Checkbox deg = new Checkbox("deg", true);
	private final Checkbox rad = new Checkbox("rad", false);
	/** Current principal direction choice */
	private int axis0 = 1;
	/** Current secondary direction choice */
	private int axis1;
	/** Direction labels */
	private String[] directions = { axisLabels[axis0][2], axisLabels[axis0][3],
		axisLabels[axis1][2], axisLabels[axis1][3] };
	/** Current orientation in radians */
	private double theta;
	/** Axis length */
	private int length;
	/** Compass centre coordinates */
	private Point p;
	private Integer activeImpID;
	private double scale = 1;
	private GeneralPath path;
	private BasicStroke stroke;
	private boolean isReflected0;
	private boolean isReflected1;

	public Orienteer() {
		super("Orientation");
		if (instance != null) {
			if (instance.getTitle().equals(getTitle())) {
				instance.toFront();
				return;
			}
			final Orienteer aa = instance;
			Prefs.saveLocation(LOC_KEY, aa.getLocation());
			aa.close();
		}
		instance = this;
		IJ.register(Orienteer.class);
		WindowManager.addWindow(this);

		final GridBagLayout gridbag = new GridBagLayout();
		final GridBagConstraints c = new GridBagConstraints();
		setLayout(gridbag);

		c.gridy = 0;
		c.insets = new Insets(2, 10, 0, 10);
		gridbag.setConstraints(slider, c);
		add(slider);
		slider.addAdjustmentListener(this);
		slider.setUnitIncrement(1);
		slider.setFocusable(false); // prevents blinking on Windows
		slider.setPreferredSize(new Dimension(360, 16));
		slider.addMouseWheelListener(this);

		final Panel degRadPanel = new Panel();
		final Label degRadLabel = new Label("Orientation");
		degRadPanel.add(degRadLabel);
		degRadPanel.add(text);
		text.addTextListener(this);

		final CheckboxGroup degRad = new CheckboxGroup();
		deg.setCheckboxGroup(degRad);
		rad.setCheckboxGroup(degRad);
		degRadPanel.add(deg);
		degRadPanel.add(rad);
		deg.addItemListener(this);
		rad.addItemListener(this);

		final Panel panel0 = new Panel();

		final Label label0 = new Label("Principal direction");
		panel0.add(label0);

		for (final String[] axisLabel : axisLabels) {
			axis0Choice.addItem(axisLabel[0] + " - " + axisLabel[1]);
		}
		axis0Choice.select(axis0);
		axis0Choice.addItemListener(this);
		panel0.add(axis0Choice);

		reflect0.setState(false);
		reflect0.addItemListener(this);
		panel0.add(reflect0);

		final Panel panel1 = new Panel();
		final Label label1 = new Label("Secondary direction");
		panel1.add(label1);

		for (final String[] axisLabel : axisLabels) {
			axis1Choice.addItem(axisLabel[0] + " - " + axisLabel[1]);
		}
		axis1Choice.select(0);
		axis1Choice.addItemListener(this);
		panel1.add(axis1Choice);

		reflect1.setState(false);
		reflect1.addItemListener(this);
		panel1.add(reflect1);

		c.gridx = 0;
		c.gridy = 1;
		c.gridwidth = 2;
		c.insets = new Insets(5, 5, 0, 5);
		c.anchor = GridBagConstraints.EAST;
		c.fill = GridBagConstraints.NONE;
		add(degRadPanel, c);

		c.gridx = 0;
		c.gridy = 2;
		c.gridwidth = 2;
		c.insets = new Insets(5, 5, 0, 5);
		c.anchor = GridBagConstraints.EAST;
		c.fill = GridBagConstraints.NONE;
		add(panel0, c);

		c.gridx = 0;
		c.gridy = 3;
		c.gridwidth = 2;
		c.insets = new Insets(5, 5, 0, 5);
		c.anchor = GridBagConstraints.EAST;
		c.fill = GridBagConstraints.NONE;
		add(panel1, c);

		pack();
		final Point loc = Prefs.getLocation(LOC_KEY);
		if (loc != null) setLocation(loc);
		else GUI.center(this);
		if (IJ.isMacOSX()) setResizable(false);
		setVisible(true);
		if (WindowManager.getImageCount() == 0) return;
		final ImagePlus imp = WindowManager.getCurrentImage();
		setup(imp);

		UsageReporter.reportEvent(this).send();
	}

	@Override
	public void adjustmentValueChanged(final AdjustmentEvent e) {
		if (e.getSource().equals(slider)) {
			rotateTo(slider.getValue() * Math.PI / 180);
			updateTextbox();
		}
	}

	@Override
	public void close() {
		super.close();
		if (WindowManager.getImageCount() == 0) return;
		// clear the orientation overlay from open images
		for (final Integer i : thetaHash.keySet()) {
			WindowManager.getImage(i).setOverlay(null);
		}
	}

	@Override
	public void itemStateChanged(final ItemEvent e) {
		boolean isImageOpen = true;
		if (WindowManager.getImageCount() == 0) isImageOpen = false;
		final Object source = e.getSource();
		if (source.equals(axis0Choice)) {
			final int i = axis0Choice.getSelectedIndex();
			if (i == axis1Choice.getSelectedIndex()) {
				axis0Choice.select(axis0);
				IJ.error("Both axes cannot indicate the same direction");
				return;
			}
			axis0 = i;
			final int[] axes = { axis0, axis1 };
			if (isImageOpen) axisHash.put(activeImpID, axes.clone());
			updateDirections();
		}
		else if (source.equals(axis1Choice)) {
			final int i = axis1Choice.getSelectedIndex();
			if (i == axis0Choice.getSelectedIndex()) {
				axis1Choice.select(axis1);
				IJ.error("Both axes cannot indicate the same direction");
				return;
			}
			axis1 = i;
			final int[] axes = { axis0, axis1 };
			if (isImageOpen) axisHash.put(activeImpID, axes.clone());
			updateDirections();
		}
		else if (source.equals(reflect0)) {
			isReflected0 = reflect0.getState();
			final boolean[] reflectors = { isReflected0, isReflected1 };
			if (isImageOpen) reflectHash.put(activeImpID, reflectors.clone());
			updateDirections();
		}
		else if (source.equals(reflect1)) {
			isReflected1 = reflect1.getState();
			final boolean[] reflectors = { isReflected0, isReflected1 };
			if (isImageOpen) reflectHash.put(activeImpID, reflectors.clone());
			updateDirections();
		}
		else if (source.equals(deg) || source.equals(rad)) {
			final boolean[] units = { deg.getState(), rad.getState() };
			if (isImageOpen) unitHash.put(activeImpID, units);
			updateTextbox();
		}
	}

	@Override
	public void mouseWheelMoved(final MouseWheelEvent e) {
		final int oldPos = slider.getValue();
		int newPos = oldPos + e.getWheelRotation();
		if (newPos < 0) newPos += 360;
		else if (newPos >= 360) newPos -= 360;
		rotateTo(newPos * Math.PI / 180);
		updateTextbox();
		slider.setValue(newPos);
	}

	@Override
	public void textValueChanged(final TextEvent e) {
		final TextField field = (TextField) e.getSource();
		double value = Double.parseDouble(field.getText());
		if (deg.getState()) value *= Math.PI / 180;
		value = value % (2 * Math.PI);
		if (value < 0) value += 2 * Math.PI;
		slider.setValue((int) Math.round((value * 180 / Math.PI)));
		rotateTo(value);
	}

	@Override
	public void windowActivated(final WindowEvent e) {
		super.windowActivated(e);
		update();
		WindowManager.setWindow(this);
	}

	@Override
	public void windowClosing(final WindowEvent e) {
		close();
		Prefs.saveLocation(LOC_KEY, getLocation());
	}

	private void addLabels() {
		if (WindowManager.getImageCount() == 0) return;
		final ImagePlus imp = WindowManager.getImage(activeImpID);
		scale = imp.getCanvas().getMagnification();
		final Font font = new Font("SansSerif", Font.PLAIN, (int) (fontSize /
			scale));
		final double lsinTheta = (length + (fontSize / scale)) * Math.sin(theta);
		final double lcosTheta = (length + (fontSize / scale)) * Math.cos(theta);
		addString(directions[0], (int) (p.x + lsinTheta), (int) (p.y - lcosTheta),
			Color.RED, font);
		addString(directions[1], (int) (p.x - lsinTheta), (int) (p.y + lcosTheta),
			Color.BLUE, font);
		addString(directions[2], (int) (p.x + lcosTheta), (int) (p.y + lsinTheta),
			Color.BLUE, font);
		addString(directions[3], (int) (p.x - lcosTheta), (int) (p.y - lsinTheta),
			Color.BLUE, font);
	}

	private void addPath(final Shape shape, final BasicStroke stroke) {
		final Roi roi = new ShapeRoi(shape);
		roi.setStrokeColor(Color.BLUE);
		roi.setStroke(stroke);
		roi.setStrokeWidth(roi.getStrokeWidth() / (float) scale);
		overlay.add(roi);
	}

	private void addString(final String text, final int x, final int y,
		final Color color, final Font font)
	{
		final TextRoi roi = new TextRoi(x, y, text, font);
		roi.setLocation(x - text.length() * (int) (fontSize / scale) / 4, y -
			(int) (fontSize / scale) / 2);
		roi.setStrokeColor(color);
		overlay.add(roi);
	}

	/**
	 * Keep hash lists up to date by removing keys of closed windows
	 */
	private void checkHash() {
		final Set<Integer> idset = thetaHash.keySet();
		for (final Integer i : idset) {
			if (WindowManager.getImage(i) == null) clearHashes(i);
		}
	}

	/**
	 * Check if an image is already handled by Orientation
	 *
	 * @param imp an image.
	 * @return true if this image is already handled by Orientation
	 */
	private boolean checkHash(final ImagePlus imp) {
		final Integer i = imp.getID();
		return thetaHash.containsKey(i);
	}

	/**
	 * Remove all image IDs from the hashes.
	 */
	private void clearHashes() {
		thetaHash.clear();
		reflectHash.clear();
		pathHash.clear();
		unitHash.clear();
		axisHash.clear();
		centreHash.clear();
		lengthHash.clear();
	}

	/**
	 * Remove a single image ID from the hashes.
	 */
	private void clearHashes(final Integer i) {
		thetaHash.remove(i);
		reflectHash.remove(i);
		pathHash.remove(i);
		unitHash.remove(i);
		axisHash.remove(i);
		centreHash.remove(i);
		lengthHash.remove(i);
	}

	/**
	 * Get the orientation of the principal direction associated with the image
	 *
	 * @param imp an image tracked by Orienteer
	 * @return orientation in radians clockwise from 12 o'clock
	 */
	private double getOrientation(final ImagePlus imp) {
		final Integer id = imp.getID();
		return thetaHash.get(id);
	}

	/**
	 * Rotate the direction indicator by a given angle
	 *
	 * @param deltaTheta number of radians to rotate by (+ve is clockwise, -ve is
	 *          anti-clockwise)
	 */
	private void rotate(final double deltaTheta) {
		if (WindowManager.getImageCount() == 0) return;
		final ImagePlus imp = WindowManager.getCurrentImage();
		activeImpID = imp.getID();
		final AffineTransform transform = new AffineTransform();
		transform.rotate(deltaTheta, p.x, p.y);
		theta += deltaTheta;
		path.transform(transform);
		overlay.clear();
		addPath(path, stroke);
		addLabels();
		imp.setOverlay(overlay);
		thetaHash.put(activeImpID, theta);
		pathHash.put(activeImpID, new GeneralPath(path));
	}

	/**
	 * Rotate the principal direction to a new angle
	 *
	 * @param newTheta desired orientation in radians clockwise from 12 o'clock of
	 *          the principal direction
	 */
	private void rotateTo(final double newTheta) {
		rotate(newTheta - theta);
		theta = newTheta;
		if (WindowManager.getImageCount() == 0) return;
		thetaHash.put(activeImpID, newTheta);
	}

	private void setup(final ImagePlus imp) {
		if (imp == null) return;
		if (checkHash(imp)) {
			IJ.log("Image has already been set up");
			return;
		}
		final Integer id = imp.getID();
		activeImpID = id;
		final int w = imp.getWidth();
		final int h = imp.getHeight();
		theta = 0;
		slider.setValue((int) (theta * 180 / Math.PI));
		isReflected0 = false;
		reflect0.setState(false);
		isReflected1 = false;
		reflect1.setState(false);
		length = Math.min(w, h) / 4;
		p = new Point(w / 2, h / 2);
		path = new GeneralPath();
		path.moveTo(p.x - length, p.y);
		path.lineTo(p.x + length, p.y);
		path.moveTo(p.x, p.y - length);
		path.lineTo(p.x, p.y + length);
		stroke = new BasicStroke(1, BasicStroke.CAP_ROUND, BasicStroke.JOIN_MITER);
		imp.setOverlay(path, Color.BLUE, stroke);
		rotateTo(theta);
		centreHash.put(id, new Point(p));
		thetaHash.put(id, theta);
		pathHash.put(id, new GeneralPath(path));
		final int[] axes = { axis0, 0 };
		axisHash.put(id, axes.clone());
		lengthHash.put(id, length);
		final boolean[] reflectors = { isReflected0, isReflected1 };
		reflectHash.put(id, reflectors.clone());
		deg.setState(true);
		rad.setState(false);
		final boolean[] units = { deg.getState(), rad.getState() };
		unitHash.put(id, units);
		updateTextbox();
	}

	private void update() {
		if (WindowManager.getImageCount() == 0) {
			instance.setTitle("Orientation - No Images Open");
			clearHashes();
			return;
		}
		final ImagePlus imp = WindowManager.getCurrentImage();
		activeImpID = imp.getID();
		instance.setTitle("Orientation - " + imp.getTitle());
		if (!checkHash(imp)) {
			setup(imp);
			return;
		}
		checkHash();
		p = centreHash.get(activeImpID);
		theta = thetaHash.get(activeImpID);
		final int[] axes = axisHash.get(activeImpID);
		axis0 = axes[0];
		axis1 = axes[1];
		path = pathHash.get(activeImpID);
		length = lengthHash.get(activeImpID);
		final boolean[] reflectors = reflectHash.get(activeImpID);
		isReflected0 = reflectors[0];
		isReflected1 = reflectors[1];
		axis0Choice.select(axis0);
		axis1Choice.select(axis1);
		slider.setValue((int) (theta * 180 / Math.PI));
		reflect0.setState(isReflected0);
		reflect1.setState(isReflected1);
		final boolean[] units = unitHash.get(activeImpID);
		deg.setState(units[0]);
		rad.setState(units[1]);
		updateTextbox();
		updateDirections();
	}

	private void updateDirections() {
		if (WindowManager.getImageCount() == 0) return;
		directions = getAxisLabels(WindowManager.getImage(activeImpID));
		rotate(0);
	}

	private void updateTextbox() {
		if (deg.getState()) text.setText(IJ.d2s(theta * 180 / Math.PI, 3));
		else text.setText(IJ.d2s(theta, 5));
	}

	static Orienteer getInstance() {
		return instance;
	}

	/**
	 * Gets the labels associated with an ImagePlus.
	 *
	 * @param imp an image.
	 * @return an array of axis labels, with the principal direction in the zeroth
	 *         position, the principal tail in the 1st position, the secondary
	 *         head in the 2nd position and the secondary tail in the 3rd
	 *         position.
	 */
	String[] getAxisLabels(final ImagePlus imp) {
		if (!checkHash(imp)) return null;
		final Integer id = imp.getID();
		final int[] axes = axisHash.get(id);
		final boolean[] ref = reflectHash.get(id);
		final String[] dirs = new String[4];

		if (ref[0]) {
			dirs[0] = axisLabels[axes[0]][3];
			dirs[1] = axisLabels[axes[0]][2];
		}
		else {
			dirs[0] = axisLabels[axes[0]][2];
			dirs[1] = axisLabels[axes[0]][3];
		}
		if (ref[1]) {
			dirs[2] = axisLabels[axes[1]][3];
			dirs[3] = axisLabels[axes[1]][2];
		}
		else {
			dirs[2] = axisLabels[axes[1]][2];
			dirs[3] = axisLabels[axes[1]][3];
		}
		return dirs;
	}

	/**
	 * Gets the principal orientation
	 *
	 * @return orientation of the principal direction in radians clockwise from 12
	 *         o'clock
	 */
	double getOrientation() {
		return theta;
	}

	/**
	 * Gets the principal orientation of the axis associated with the image
	 *
	 * @param imp an image tracked by orienteer
	 * @param axisLabel a label of the axis of the image
	 * @return orientation in radians clockwise from 12 o'clock
	 * @throws NullPointerException if imp is not tracked by orienteer.
	 */
	double getOrientation(final ImagePlus imp, final String axisLabel)
		throws NullPointerException
	{
		final String[] dir = getAxisLabels(imp);
		if (dir == null) {
			throw new NullPointerException("Image not tracked by Orienteer");
		}
		int quadrant = 0;
		for (int i = 0; i < 4; i++) {
			if (dir[i].equals(axisLabel)) {
				quadrant = i;
				break;
			}
		}
		double orientation = getOrientation(imp);

		switch (quadrant) {
			case 0:
				return orientation;
			case 1:
				orientation += Math.PI;
				break;
			case 2:
				orientation += Math.PI / 2;
				break;
			case 3:
				orientation += 3 * Math.PI / 2;
				break;
			default:
				throw new RuntimeException("Unexpected quadrant!");
		}
		if (orientation > 2 * Math.PI) {
			return orientation - 2 * Math.PI;
		}
		return orientation;
	}

	/**
	 * Given a set of (x,y) coordinates, find the caliper diameters across the
	 * axes
	 *
	 * @param points in double[n][2] format
	 * @return caliper diameters across the principal and secondary axes (zeroth
	 *         and first elements respectively)
	 */
	double[] getDiameters(final double[][] points) {
		double xMin = Double.POSITIVE_INFINITY;
		double xMax = Double.NEGATIVE_INFINITY;
		double yMin = Double.POSITIVE_INFINITY;
		double yMax = Double.NEGATIVE_INFINITY;
		for (final double[] point : points) {
			final double x = point[0];
			final double y = point[1];
			final double xr = x * Math.cos(-theta) - y * Math.sin(-theta);
			final double yr = x * Math.sin(-theta) + y * Math.cos(-theta);
			xMin = Math.min(xMin, xr);
			xMax = Math.max(xMax, xr);
			yMin = Math.min(yMin, yr);
			yMax = Math.max(yMax, yr);
		}
		return new double[] { yMax - yMin, xMax - xMin };
	}
}
