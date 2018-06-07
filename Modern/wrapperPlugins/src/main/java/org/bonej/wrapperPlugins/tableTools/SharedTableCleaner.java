package org.bonej.wrapperPlugins.tableTools;

import java.util.List;

import org.bonej.utilities.SharedTable;
import org.scijava.command.Command;
import org.scijava.display.Display;
import org.scijava.display.DisplayService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

/**
 * A command that allows the user to clear the {@link org.bonej.utilities.SharedTable}
 *
 * @author Richard Domander
 */
@Plugin(type = Command.class, menuPath = "Plugins>BoneJ>Table>Clear BoneJ results")
public class SharedTableCleaner implements Command {

    @Parameter
    private DisplayService displayService;

    @Override
    public void run() {
        final List<Display<?>> displays = displayService.getDisplays(SharedTable.getTable());
        displays.forEach(Display::close);
        SharedTable.reset();
    }
}
