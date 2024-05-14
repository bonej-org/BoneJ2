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
package org.bonej.wrapperPlugins;

import java.util.List;

import net.imagej.ImgPlus;
import net.imagej.ops.OpService;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.ComplexType;

import org.bonej.wrapperPlugins.wrapperUtils.Common;
import org.bonej.wrapperPlugins.wrapperUtils.HyperstackUtils;
import org.bonej.wrapperPlugins.wrapperUtils.HyperstackUtils.Subspace;
import org.scijava.ItemIO;
import org.scijava.command.ContextCommand;
import org.scijava.plugin.Parameter;
import org.scijava.table.DefaultColumn;
import org.scijava.table.Table;

import static java.util.stream.Collectors.toList;

public abstract class BoneJCommand extends ContextCommand {
    protected List<Subspace<BitType>> subspaces;


    /**
     * The results of the command in a {@link Table}.
     * <p>
     * Null if there are no results.
     * </p>
     */
    @Parameter(type = ItemIO.OUTPUT, label = "BoneJ results")
    protected Table<DefaultColumn<Double>, Double> resultsTable;

    protected <C extends ComplexType<C>> List<Subspace<BitType>> find3DSubspaces(
            final ImgPlus<C> image) {
        final OpService opService = context().getService(OpService.class);
        final ImgPlus<BitType> bitImgPlus = Common.toBitTypeImgPlus(opService, image);
        return HyperstackUtils.split3DSubspaces(bitImgPlus).collect(toList());
    }
}
