package com.mine.autoshine;

/**
 * Decides when a new light sensor value is allowed to change the brightness.
 *
 * A new value only counts if it differs from the last applied value by more than
 * a percentage, and it has to stay beyond that limit for a given time (hold time).
 * If the value falls back inside the limit, the timer starts over.
 */
public final class LuxFilter {

	private float ref = Float.NaN;   // lux value of the last applied brightness
	private long since = -1;         // time when the value first left the limit, -1 = not outside

	public void reset() {
		ref = Float.NaN;
		since = -1;
	}

	/**
	 * @return true if the brightness should be applied now for this value
	 */
	public boolean onValue(float lux, long nowMs, int percent, long holdMs) {
		// first value after start: apply at once
		if (Float.isNaN(ref)) {
			accept(lux);
			return true;
		}

		if (!isOutside(lux, percent)) {
			since = -1;
			return false;
		}

		if (since < 0)
			since = nowMs;

		if (nowMs - since >= holdMs) {
			accept(lux);
			return true;
		}

		return false;
	}

	/** true while a value is beyond the limit and waiting for the hold time to pass */
	public boolean isPending() {
		return since >= 0;
	}

	/** milliseconds until the hold time is over (at least 1 while pending) */
	public long remaining(long nowMs, long holdMs) {
		if (since < 0)
			return 0;
		return Math.max(1, holdMs - (nowMs - since));
	}

	private boolean isOutside(float lux, int percent) {
		float base = Math.max(ref, 1f);
		return Math.abs(lux - ref) > base * percent / 100f;
	}

	private void accept(float lux) {
		ref = lux;
		since = -1;
	}
}
