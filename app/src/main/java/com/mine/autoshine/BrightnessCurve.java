package com.mine.autoshine;

/**
 * Piecewise linear mapping from ambient light (lux) to screen brightness.
 * Works with any number of points; the lux values must be strictly increasing.
 */
public final class BrightnessCurve {

	private BrightnessCurve() {
	}

	public static int compute(int[] lux, int[] bright, int count, float value) {
		if (count <= 0)
			return 0;

		// below the first point / above the last point: clamp to the outer brightness
		if (count == 1 || value <= lux[0])
			return bright[0];
		if (value > lux[count - 1])
			return bright[count - 1];

		// find the segment (lux[i - 1], lux[i]] that contains the value
		int i = 1;
		while (i < count - 1 && value > lux[i])
			i++;

		float x1 = lux[i - 1];
		float x2 = lux[i];
		float y1 = bright[i - 1];
		float y2 = bright[i];

		if (x2 <= x1)
			return (int) y2;

		return (int) (y1 + (y2 - y1) * ((value - x1) / (x2 - x1)
                                   ));
	}
}
