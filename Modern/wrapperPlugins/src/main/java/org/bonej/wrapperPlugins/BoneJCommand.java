package org.bonej.wrapperPlugins;

import java.util.List;

import net.imagej.ImgPlus;
import net.imagej.ops.OpService;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.ComplexType;

import org.bonej.wrapperPlugins.wrapperUtils.Common;
import org.bonej.wrapperPlugins.wrapperUtils.HyperstackUtils;
import org.bonej.wrapperPlugins.wrapperUtils.HyperstackUtils.Subspace;
import org.bonej.wrapperPlugins.wrapperUtils.UsageReporter;
import org.scijava.ItemIO;
import org.scijava.command.CommandService;
import org.scijava.command.ContextCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.PluginService;
import org.scijava.prefs.PrefService;
import org.scijava.table.DefaultColumn;
import org.scijava.table.Table;

import static java.util.stream.Collectors.toList;

public abstract class BoneJCommand extends ContextCommand {
    private static UsageReporter reporter;
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

    protected void reportUsage() {
        if (reporter == null) {
            final PrefService prefService = context().getService(PrefService.class);
            final PluginService pluginService = context().getService(PluginService.class);
            final CommandService commandService = context().getService(CommandService.class);
            reporter = UsageReporter.getInstance(prefService, pluginService, commandService);
        }
        reporter.reportEvent(getClass().getName());
    }

    static void setReporter(final UsageReporter reporter) {
        BoneJCommand.reporter = reporter;
    }
}
