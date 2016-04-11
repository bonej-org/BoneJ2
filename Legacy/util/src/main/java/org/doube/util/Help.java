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

import java.awt.BorderLayout;
import java.io.IOException;

import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.WindowConstants;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;

import ij.plugin.BrowserLauncher;
import ij.plugin.PlugIn;

public class Help implements PlugIn {

	/**
	 * BoneJ version
	 *
	 * FIXME: it is fragile to have the version hard-coded here. Create a
	 * BoneJApp instead.
	 */
	public static final String bonejVersion = "1.5.0";

	@Override
	public void run(final String arg) {
		if (arg.equals("about")) {
			showAbout();
			// testing
			UsageReporter.reportEvent(this).send();
			return;
		}
	}

	private void showAbout() {
		final JEditorPane htmlPane = new JEditorPane("text/html",
				"<html>\n" + "  <body>\n" + "<p><b>BoneJ version " + bonejVersion + "</b>	</p>"
						+ "<p>BoneJ is an ImageJ plugin designed for (but not limited to) bone image analysis.</p>"
						+ "<p>User and developer documentation can be found at <a href=http://bonej.org/>bonej.org</a></p>"
						+ "\n" + "  </body>\n" + "</html>");
		htmlPane.setEditable(false);
		htmlPane.setOpaque(false);
		htmlPane.addHyperlinkListener(new HyperlinkListener() {
			@Override
			public void hyperlinkUpdate(final HyperlinkEvent e) {
				if (HyperlinkEvent.EventType.ACTIVATED.equals(e.getEventType()))
					try {
						BrowserLauncher.openURL(e.getURL().toString());
					} catch (final IOException exception) {
						// ignore
					}
			}
		});

		final JPanel panel = new JPanel(new BorderLayout());
		panel.add(htmlPane, BorderLayout.CENTER);

		final JFrame frame = new JFrame("About BoneJ...");
		frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		frame.getContentPane().add(panel, BorderLayout.CENTER);
		frame.pack();
		frame.setVisible(true);
	}

}
