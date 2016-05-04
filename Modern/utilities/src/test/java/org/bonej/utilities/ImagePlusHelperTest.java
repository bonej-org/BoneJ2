package org.bonej.utilities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import net.imagej.Dataset;

import org.junit.Test;
import org.scijava.convert.ConvertService;

import ij.ImagePlus;

/**
 * Unit tests for the ImagePlusHelper class
 *
 * @author Richard Domander
 */
public class ImagePlusHelperTest {
	@Test(expected = NullPointerException.class)
	public void testToImagePlusThrowsNPEifConvertServiceNull() throws Exception {
		final Dataset dataset = mock(Dataset.class);

		ImagePlusHelper.toImagePlus(null, dataset);
	}

	@Test(expected = NullPointerException.class)
	public void testToImagePlusThrowsThrowsNPEifDatasetNull() throws Exception {
		final ConvertService convertService = mock(ConvertService.class);

		ImagePlusHelper.toImagePlus(convertService, null);
	}

	@Test
	public void testToImagePlusOptionalEmptyIfCannotConvert() {
		final Dataset dataset = mock(Dataset.class);
		final ConvertService convertService = mock(ConvertService.class);
		when(convertService.supports(dataset, ImagePlus.class)).thenReturn(false);

		final Optional<ImagePlus> result = ImagePlusHelper.toImagePlus(convertService, dataset);

		assertFalse("Optional should be empty", result.isPresent());
	}

	@Test
	public void testToImagePlusOptionalPresentIfCanConvert() {
		final Dataset dataset = mock(Dataset.class);
		final ConvertService convertService = mock(ConvertService.class);
		when(convertService.supports(dataset, ImagePlus.class)).thenReturn(true);
		when(convertService.convert(dataset, ImagePlus.class)).thenReturn(new ImagePlus());

		final Optional<ImagePlus> result = ImagePlusHelper.toImagePlus(convertService, dataset);

		assertTrue("Optional should be present", result.isPresent());
		assertEquals("Optional should contain an ImagePlus", result.get().getClass(), ImagePlus.class);
	}
}