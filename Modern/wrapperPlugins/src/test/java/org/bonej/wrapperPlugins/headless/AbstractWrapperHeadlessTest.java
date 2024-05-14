/*-
 * #%L
 * High-level BoneJ2 commands.
 * %%
 * Copyright (C) 2015 - 2024 Michael Doube, BoneJ developers
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

package org.bonej.wrapperPlugins.headless;

import net.imagej.ImageJ;
import org.bonej.utilities.SharedTable;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.scijava.command.CommandService;

/**
 * An abstract base test class that handles basic setup and tear down
 * for testing wrapper plugins in headless mode.
 * <p>
 * NB Remember to call the methods in this class if you override/hide the methods!
 * </p>
 * @author Richard Domander
 * @author Michael Doube
 */
public abstract class AbstractWrapperHeadlessTest {
    private static ImageJ imageJ;
    private static CommandService commandService;

    protected static CommandService command() {
        return commandService;
    }

    protected static ImageJ imageJ() {
        return imageJ;
    }

    @BeforeClass
    public static void basicOneTimeSetup() {
        imageJ = new ImageJ();
        commandService = imageJ.command();
    }

    @Before
    public void setup() {
        imageJ.ui().setHeadless(true);
    }

    @After
    public void tearDown() {
        SharedTable.reset();
    }

    @AfterClass
    public static void basicOneTimeTearDown() {
        imageJ.context().dispose();
        imageJ = null;
        commandService = null;
    }
}
