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

import ij.Prefs;

/**
 * Multithreader utility class for convenient multithreading of ImageJ plugins
 *
 * @author Stephan Preibisch
 * @author Michael Doube
 *
 * @see
 * 		<p>
 *      <a href=
 *      "http://repo.or.cz/w/trakem2.git?a=blob;f=mpi/fruitfly/general/MultiThreading.java;hb=HEAD"
 *      >http://repo.or.cz/w/trakem2.git?a=blob;f=mpi/fruitfly/general/
 *      MultiThreading.java;hb=HEAD</a>
 */
public class Multithreader {
	public static void startTask(final Runnable run) {
		final Thread[] threads = newThreads();

		for (int ithread = 0; ithread < threads.length; ++ithread)
			threads[ithread] = new Thread(run);
		startAndJoin(threads);
	}

	public static void startTask(final Runnable run, final int numThreads) {
		final Thread[] threads = newThreads(numThreads);

		for (int ithread = 0; ithread < threads.length; ++ithread)
			threads[ithread] = new Thread(run);
		startAndJoin(threads);
	}

	public static Thread[] newThreads() {
		final int nthread = Prefs.getThreads();
		return new Thread[nthread];
	}

	public static Thread[] newThreads(final int numThreads) {
		return new Thread[numThreads];
	}

	public static void startAndJoin(final Thread[] threads) {
		for (int ithread = 0; ithread < threads.length; ++ithread) {
			threads[ithread].setPriority(Thread.NORM_PRIORITY);
			threads[ithread].start();
		}

		try {
			for (int ithread = 0; ithread < threads.length; ++ithread)
				threads[ithread].join();
		} catch (final InterruptedException ie) {
			throw new RuntimeException(ie);
		}
	}
}
