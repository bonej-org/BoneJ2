/*
 * #%L
 * BoneJ utility classes.
 * %%
 * Copyright (C) 2007 - 2016 Michael Doube, BoneJ developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */
package org.doube.util;

import java.awt.Checkbox;
import java.awt.Choice;
import java.awt.Component;
import java.awt.Label;
import java.awt.Panel;
import java.awt.TextField;
import java.util.Vector;

import ij.IJ;
import ij.gui.GenericDialog;

public class DialogModifier {
	/**
	 * Replace the unit string to the right of all numeric textboxes in a
	 * GenericDialog
	 *
	 * @param gd
	 *            the dialog window
	 * @param oldUnits
	 *            original unit string
	 * @param newUnits
	 *            new unit string
	 */
	public static void replaceUnitString(final GenericDialog gd, final String oldUnits, final String newUnits) {
		for (int n = 0; n < gd.getComponentCount(); n++) {
			if (gd.getComponent(n) instanceof Panel) {
				final Panel panel = (Panel) gd.getComponent(n);
				if (panel.getComponent(1) instanceof Label) {
					final Label u = (Label) panel.getComponent(1);
					final String unitString = u.getText();
					u.setText(unitString.replace(oldUnits, newUnits));
				}
			}
		}
	}

	/**
	 * Go through all values in a GenericDialog's Components and call the
	 * appropriate get method. Recursively enter Panel Components. Will throw an
	 * ArrayIndexOutOfBounds exception if gd.getNext... is called elsewhere in
	 * dialogItemChanged().
	 *
	 * @param gd
	 * @param comps
	 */
	public static void registerMacroValues(final GenericDialog gd, final Component[] comps) {
		try {
			for (final Component c : comps) {
				if (c instanceof Checkbox)
					gd.getNextBoolean();
				else if (c instanceof Choice)
					gd.getNextChoice();
				else if (c instanceof TextField) {
					final String text = ((TextField) c).getText();
					try {
						Double.parseDouble(text);
						gd.getNextNumber();
					} catch (final NumberFormatException e) {
						gd.getNextString();
					}
				} else if (c instanceof Panel)
					registerMacroValues(gd, ((Panel) c).getComponents());
				else
					continue;
			}
		} catch (final Exception e) {
			IJ.log("This plugin causes an exception\n" + e.toString());
		}
		return;
	}

	/**
	 * Check all the numeric text fields in a dialog and return false if any of
	 * them cannot be parsed into a number. Accepts any decimal number,
	 * "Infinity" and "NaN". Rejects strings of 0 length or that contain any
	 * non-decimal characters.
	 *
	 *
	 * @param textFields
	 *            e.g. result of GenericDialog.getNumericFields();
	 * @return true if all numeric text fields contain a valid number
	 */
	public static boolean allNumbersValid(final Vector<?> textFields) {
		for (final Object text : textFields) {
			final String string = ((TextField) text).getText();
			if (string.length() == 0)
				return false;
			try {
				Double.parseDouble(string);
			} catch (final NumberFormatException e) {
				return false;
			}
		}
		return true;
	}
}
