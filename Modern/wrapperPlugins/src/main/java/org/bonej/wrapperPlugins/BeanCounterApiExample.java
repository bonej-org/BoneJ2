package org.bonej.wrapperPlugins;

import net.imagej.Dataset;

import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.command.CommandService;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import ch.beancounter.BeanCounter;
import ch.beancounter.BeanCounterPlugin;

/**
 * BeanCounter API example using a SciJava Command.
 * 
 * This plugin demonstrates how to use beans based on the total pixel count
 * of an input image. The important API calls are:
 * 
 * {@link ch.beancounter.BeanCounter#canRedeem(long)} checks that the user has the
 * ability to cover an anticipated amount of usage.
 * {@link ch.beancounter.BeanCounter#redeem(String, long)} uses the amount of beans
 * that the client code requests after a job is complete.
 * 
 */
@Plugin(type = Command.class, menuPath = "Plugins>BeanCounter API Example")
public class BeanCounterApiExample implements Command {

	@Parameter(type = ItemIO.INPUT, label = "Input Image")
	private Dataset dataset;

	@Parameter
	private LogService logService;

	@Parameter
	private CommandService commandService;

	@Override
	public void run() {
		// Estimate beans to be used by the job. Here we use 1 bean per 100k pixels opened
		// You are free to set whatever usage rate you want, based on whatever usage dimension
		// makes sense for your algorithm.
		long beans = dataset.size() / 100000;

		// Check whether the user has sufficient beans for the job,
		// and if not then give the opportunity to top up
		if (!BeanCounter.canRedeem(beans)) {
			logService.info("Not enough beans for this job: " + beans + " needed."
					+ " Opening 'Check Usage Voucher'...");
			commandService.run(BeanCounterPlugin.class, true);
			return;
		}
		
		// Process data here. Check whether it completed successfully and if so,
		// record the usage with BeanCounter
		boolean successfulCompletion = processDataset();

		if (successfulCompletion)
			BeanCounter.redeem(this.getClass().getName(), beans);
	}
	
	/**
	 * Do the image processing. Here we check if the image has zero or more pixels.
	 * 
	 * @return true on success, false on failure
	 */
	private boolean processDataset() {
		if (dataset.size() >= 0)
			return true;
		return false;
	}
}
