package org.bonej.wrapperPlugins;

import net.imagej.ImageJ;
import org.junit.AfterClass;
import org.junit.Test;
import org.scijava.command.CommandInfo;

import java.util.stream.Stream;

import static org.junit.Assert.assertTrue;

/**
 * There is a number of tools which we want to offer BoneJ users,
 * but on which BoneJ2 plugins do not depend in compilation or testing
 * This test checks that those tools are present in vanilla ImageJ2.
 *
 * @author Richard Domander 
 */
public class BundleTest {
    private static final ImageJ IMAGE_J = new ImageJ();

    @AfterClass
    public static void oneTimeTearDown() {
        IMAGE_J.context().dispose();
    }

    @Test
    public void checkErode() {
        final Stream<CommandInfo> commands = IMAGE_J.command().getCommands().stream();

        assertTrue(commands.anyMatch(i -> i.getClassName()
                .equals("net.imagej.plugins.commands.binary.ErodeBinaryImage")));
    }

    @Test
    public void checkDilate() {
        final Stream<CommandInfo> commands = IMAGE_J.command().getCommands().stream();

        assertTrue(commands.anyMatch(i -> i.getClassName()
                .equals("net.imagej.plugins.commands.binary.DilateBinaryImage")));
    }
}
