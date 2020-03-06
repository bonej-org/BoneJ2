package org.bonej;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;

/**
 * Launches legacy ImageJ for debugging (and a BoneJ plugin).
 *
 * @author Richard Domander
 * @author dscho
 */
class DebugLauncher {
    public static void main(String[] args) {
        // set the plugins.dir property to make the plugin appear in the Plugins menu
        /*
        Class<?> clazz = SliceGeometry.class;
        String url = clazz.getResource("/" + clazz.getName().replace('.', '/') + ".class").toString();
        String pluginsDir = url.substring(5, url.length() - clazz.getName().length() - 6);
        System.setProperty("plugins.dir", pluginsDir);
        */

        // start ImageJ
        new ImageJ();

        // open the Bat cochlea sample image
        final ImagePlus image = IJ.openImage("https://imagej.net/images/bat-cochlea-volume.zip");
        image.show();

        // run the plugin
        // IJ.runPlugIn(clazz.getName(), "");
    }
}
