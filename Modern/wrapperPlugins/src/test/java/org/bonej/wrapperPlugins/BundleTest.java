
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
 * BoneJ2 plugins do not depend on in compilation or testing This test checks that
 * those tools are present in the build environment.
 *
 * @author Richard Domander
 */
public class BundleTest {

	private static final Gateway IMAGE_J = new ImageJ();

	@AfterClass
	public static void oneTimeTearDown() {
		IMAGE_J.context().dispose();
	}

	@Test
	public void checkErode() {
		final Stream<CommandInfo> commands = IMAGE_J.command().getCommands()
			.stream();

		assertTrue(commands.anyMatch(i -> "net.imagej.plugins.commands.binary.ErodeBinaryImage".equals(i.getClassName())));
	}

	@Test
	public void checkDilate() {
		final Stream<CommandInfo> commands = IMAGE_J.command().getCommands()
			.stream();

		assertTrue(commands.anyMatch(i -> "net.imagej.plugins.commands.binary.DilateBinaryImage".equals(i.getClassName())));
	}

	@Test
    public void checkScancoISQFormat() {
        final Stream<PluginInfo<?>> infoStream = IMAGE_J.plugin().getPlugins().stream();

        assertTrue(infoStream.anyMatch(i -> "io.scif.formats.ScancoISQFormat".equals(i.getClassName())));
    }

    @Test
    public void checkKontronFormat() {
        final Stream<PluginInfo<?>> infoStream = IMAGE_J.plugin().getPlugins().stream();

        assertTrue(infoStream.anyMatch(i -> "io.scif.formats.KontronFormat".equals(i.getClassName())));
    }
}
