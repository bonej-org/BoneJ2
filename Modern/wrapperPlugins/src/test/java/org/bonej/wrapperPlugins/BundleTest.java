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

package org.bonej.wrapperPlugins;

import static org.junit.Assert.assertTrue;

import java.util.stream.Stream;

import net.imagej.ImageJ;

import org.junit.AfterClass;
import org.junit.Test;
import org.scijava.Gateway;
import org.scijava.command.CommandInfo;
import org.scijava.plugin.PluginInfo;

/**
 * There is a number of tools which we want to offer BoneJ users, but on which
 * BoneJ2 plugins do not depend on in compilation or testing This test checks
 * that those tools are present in the build environment.
 *
 * @author Richard Domander
 */
public class BundleTest {

	private static final Gateway IMAGE_J = new ImageJ();

	@Test
	public void checkDilate() {
		final Stream<CommandInfo> commands = IMAGE_J.command().getCommands()
			.stream();

		assertTrue(commands.anyMatch(
			i -> "net.imagej.plugins.commands.binary.DilateBinaryImage".equals(i
				.getClassName())));
	}

	@Test
	public void checkErode() {
		final Stream<CommandInfo> commands = IMAGE_J.command().getCommands()
			.stream();

		assertTrue(commands.anyMatch(
			i -> "net.imagej.plugins.commands.binary.ErodeBinaryImage".equals(i
				.getClassName())));
	}

	@Test
	public void checkKontronFormat() {
		final Stream<PluginInfo<?>> infoStream = IMAGE_J.plugin().getPlugins()
			.stream();

		assertTrue(infoStream.anyMatch(i -> "io.scif.formats.KontronFormat".equals(i
			.getClassName())));
	}

	@Test
	public void checkScancoISQFormat() {
		final Stream<PluginInfo<?>> infoStream = IMAGE_J.plugin().getPlugins()
			.stream();

		assertTrue(infoStream.anyMatch(i -> "io.scif.formats.ScancoISQFormat"
			.equals(i.getClassName())));
	}

	@AfterClass
	public static void oneTimeTearDown() {
		IMAGE_J.context().dispose();
	}
}
