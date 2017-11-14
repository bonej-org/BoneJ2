
package org.bonej.wrapperPlugins;

/**
 * A JUnit category marker for slow integration tests, that require mocked
 * interaction with the UI, or threaded {@link org.scijava.command.Command}
 * execution.
 *
 * @author Richard Domander
 */
public interface SlowWrapperTest {}
