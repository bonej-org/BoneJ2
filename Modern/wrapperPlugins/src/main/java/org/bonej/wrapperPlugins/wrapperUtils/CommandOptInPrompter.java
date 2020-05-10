package org.bonej.wrapperPlugins.wrapperUtils;

import org.scijava.Context;
import org.scijava.command.CommandModule;
import org.scijava.command.CommandService;
import org.scijava.log.LogService;

import java.util.concurrent.ExecutionException;

public class CommandOptInPrompter implements OptInPrompter {

    private final CommandService commandService;
    // private final LogService logService;

    CommandOptInPrompter(final CommandService commandService) {
        if (commandService == null) {
            throw new NullPointerException("CommandService cannot be null");
        }
        this.commandService = commandService;
        // logService = context.getService(LogService.class);
    }

    @Override
    public void promptUser() {
        // logService.debug("User permission has not been sought, requesting it...\n");
        try {
            commandService.run(UsageReporterOptions.class, true).get();
        }
        catch (final InterruptedException | ExecutionException e) {
            // logService.trace(e);
        }
    }
}
