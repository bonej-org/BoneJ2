package org.bonej.ops.connectivity;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * A class to reverse engineer EulerCharacteristic to fix bugs
 *
 * @author Richard Domander
 */
public class ConnectivityProtos {
    @Test
    public void generateNeighborhoods() {
        Neighborhood[] neighborhoods = new Neighborhood[256];
        for (int n = 0; n < 256; n++) {
            neighborhoods[n] = new Neighborhood(n);
            System.out.println("N: " + n + " Index: " + neighborhoods[n].getIndex());
            assertEquals(n, neighborhoods[n].getIndex());
        }

        System.out.println();
    }

    public static class Neighborhood {
        public boolean [][][] neighbors = new boolean[2][2][2];
        public Neighborhood(int n) {
            neighbors[0][0][0] = (n & 1) > 0;
            neighbors[1][0][0] = (n & 2) > 0;
            neighbors[0][1][0] = (n & 4) > 0;
            neighbors[1][1][0] = (n & 8) > 0;
            neighbors[0][0][1] = (n & 16) > 0;
            neighbors[1][0][1] = (n & 32) > 0;
            neighbors[0][1][1] = (n & 64) > 0;
            neighbors[1][1][1] = (n & 128) > 0;
        }
        
        public int getIndex() {
            int index = 1;
            if (neighbors[1][1][1]) {
                if (neighbors[0][0][0]) { index |= 128; }
                if (neighbors[0][1][0]) { index |= 64; }
                if (neighbors[1][0][0]) { index |= 32; }
                if (neighbors[1][1][0]) { index |= 16; }
                if (neighbors[0][0][1]) { index |= 8; }
                if (neighbors[0][1][1]) { index |= 4; }
                if (neighbors[1][0][1]) { index |= 2; }
            } else if (neighbors[1][0][1]) {
                if (neighbors[0][1][0]) { index |= 128; }
                if (neighbors[1][1][0]) { index |= 64; }
                if (neighbors[0][0][0]) { index |= 32; }
                if (neighbors[1][0][0]) { index |= 16; }
                if (neighbors[0][1][1]) { index |= 8; }
                if (neighbors[0][0][1]) { index |= 2; }
            } else if (neighbors[0][1][1]) {
                if (neighbors[1][0][0]) { index |= 128; }
                if (neighbors[0][0][0]) { index |= 64; }
                if (neighbors[1][1][0]) { index |= 32; }
                if (neighbors[0][1][0]) { index |= 16; }
                if (neighbors[0][0][1]) { index |= 4; }
            } else if (neighbors[0][0][1]) {
                if (neighbors[1][1][0]) { index |= 128; }
                if (neighbors[1][0][0]) { index |= 64; }
                if (neighbors[0][1][0]) { index |= 32; }
                if (neighbors[0][0][0]) { index |= 16; }
            } else if (neighbors[1][1][0]) {
                if (neighbors[0][0][0]) { index |= 8; }
                if (neighbors[1][0][0]) { index |= 4; }
                if (neighbors[0][1][0]) { index |= 2; }
            } else if (neighbors[1][0][0]) {
                if (neighbors[0][1][0]) { index |= 8; }
                if (neighbors[0][0][0]) { index |= 4; }
            } else if (neighbors[0][1][0]) {
                if (neighbors[0][0][0]) { index |= 2; }
            }
            
            return index;
        }
    }
}
