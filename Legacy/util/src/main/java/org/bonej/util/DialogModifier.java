/*-
 * #%L
 * Utility classes for BoneJ1 plugins
 * %%
 * Copyright (C) 2015 - 2020 Michael Doube, BoneJ developers
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
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

package org.bonej.util;

import java.awt.Checkbox;
import java.awt.Choice;
import java.awt.Component;
import java.awt.Container;
import java.awt.Label;
import java.awt.Panel;
import java.awt.TextComponent;
import java.awt.TextField;

import ij.IJ;
import ij.gui.GenericDialog;

public final class DialogModifier {

	private DialogModifier() {}

	/**
	 * Check all the numeric text fields in a dialog and return false if any of
	 * them cannot be parsed into a number. Accepts any decimal number, "Infinity"
	 * and "NaN". Rejects strings of 0 length or that contain any non-decimal
	 * characters.
	 *
	 * @param textFields e.g. result of GenericDialog.getNumericFields();
	 * @return true if all numeric text fields contain a valid number
	 */
	public static boolean hasInvalidNumber(final Iterable<?> textFields) {
		for (final Object text : textFields) {
			final String string = ((TextComponent) text).getText();
			try {
				Double.parseDouble(string);
			}
			catch (final NumberFormatException e) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Go through all values in a GenericDialog's Components and call the
	 * appropriate get method. Recursively enter Panel Components. Will throw an
	 * ArrayIndexOutOfBounds exception if gd.getNext... is called elsewhere in
	 * dialogItemChanged().
	 *
	 * @param gd the dialog window
	 * @param comps a list of components in the dialog.
	 */
	public static void registerMacroValues(final GenericDialog gd,
		final Component[] comps)
	{
		try {
			for (final Component c : comps) {
				if (c instanceof Checkbox) {
					gd.getNextBoolean();
				}
				else if (c instanceof Choice) {
					gd.getNextChoice();
				}
				else if (c instanceof TextField) {
					final String text = ((TextComponent) c).getText();
					try {
						Double.parseDouble(text);
						gd.getNextNumber();
					}
					catch (final NumberFormatException e) {
						gd.getNextString();
					}
				}
				else if (c instanceof Panel) {
					// TODO Loop continues, even if recursive call(s) throw an exception
					registerMacroValues(gd, ((Container) c).getComponents());
				}
			}
		}
		catch (final ArrayIndexOutOfBoundsException e) {
			IJ.log("Dialog has no more components\n" + e);
		}
	}

	/**
	 * Replace the unit string to the right of all numeric textboxes in a
	 * GenericDialog
	 *
	 * @param gd the dialog window
	 * @param oldUnits original unit string
	 * @param newUnits new unit string
	 */
	public static void replaceUnitString(final GenericDialog gd,
		final CharSequence oldUnits, final CharSequence newUnits)
	{
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
}
