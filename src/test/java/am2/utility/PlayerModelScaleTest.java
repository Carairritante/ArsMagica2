package am2.utility;

import org.junit.Test;
import static org.junit.Assert.*;

public class PlayerModelScaleTest {
    @Test public void preservesFeetTranslationAndScalesRotatedBasisOnce() {
        float[] matrix = {0,0,-2,0, 0,3,0,0, 4,0,0,0, 25,80,-14,1};
        PlayerModelScale.apply(matrix, 1);
        assertArrayEquals(new float[]{0,0,-.5f,0, 0,.75f,0,0, 1,0,0,0, 25,80,-14,1}, matrix, 0);
    }

    @Test public void transitionMatchesVanillaShrinkAndNormalIsUnchanged() {
        for (float pct : new float[]{0, .1f, .5f, 1, 2}) {
            float[] matrix = {1,0,0,0, 0,1,0,0, 0,0,1,0, 1,2,3,1};
            PlayerModelScale.apply(matrix, pct);
            float scale = 1f - .75f * Math.min(1, pct);
            assertEquals(scale, matrix[0], 0); assertEquals(scale, matrix[5], 0);
            assertEquals(scale, matrix[10], 0); assertEquals(2, matrix[13], 0);
        }
    }

    @Test public void invalidInputDoesNotCorruptTransform() {
        PlayerModelScale.apply(null, 1); PlayerModelScale.apply(new float[15], 1);
        for (float pct : new float[]{-1, Float.NaN}) {
            float[] matrix = new float[16]; java.util.Arrays.fill(matrix, 2);
            float[] before = matrix.clone(); PlayerModelScale.apply(matrix, pct);
            assertArrayEquals(before, matrix, 0);
        }
    }
}
