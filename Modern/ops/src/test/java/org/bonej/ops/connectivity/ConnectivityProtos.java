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
            neighbors[0][0][0] = (n & 0b00000001) > 0;
            neighbors[1][0][0] = (n & 0b00000010) > 0;
            neighbors[0][1][0] = (n & 0b00000100) > 0;
            neighbors[1][1][0] = (n & 0b00001000) > 0;
            neighbors[0][0][1] = (n & 0b00010000) > 0;
            neighbors[1][0][1] = (n & 0b00100000) > 0;
            neighbors[0][1][1] = (n & 0b01000000) > 0;
            neighbors[1][1][1] = (n & 0b10000000) > 0;
        }
        
        public int getIndex() {
            int index = 0;

            if (neighbors[0][0][0]) {
                index |= 0b00000001;
            }

            if (neighbors[1][0][0]) {
                index |= 0b00000010;
            }

            if (neighbors[0][1][0]) {
                index |= 0b00000100;
            }

            if (neighbors[1][1][0]) {
                index |= 0b00001000;
            }

            if (neighbors[0][0][1]) {
                index |= 0b00010000;
            }

            if (neighbors[1][0][1]) {
                index |= 0b00100000;
            }

            if (neighbors[0][1][1]) {
                index |= 0b01000000;
            }

            if (neighbors[1][1][1]) {
                index |= 0b10000000;
            }

            return index;
        }
    }
}
