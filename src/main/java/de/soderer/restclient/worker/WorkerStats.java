package de.soderer.restclient.worker;

import java.time.Duration;

/**
 * Statistics of one worker of the worker pool load test ({@link de.soderer.restclient.dlg.WorkerPoolDialog}).
 *
 * <p>
 * The latest duration and status always show the latest run, while the counters and the minimum, average
 * and maximum durations only include the runs counted in the statistics (i.e. not the runs started during
 * the ramp-up time). All methods are synchronized, since the statistics are written by the worker thread
 * and read by the Swing event dispatch thread.
 * </p>
 */
public class WorkerStats {
	private final int workerId;
	private int successCount;
	private int errorCount;
	private Duration minimumDuration;
	private Duration maximumDuration;
	private Duration latestDuration;
	private Boolean latestStatusWasSuccess = null;
	private Duration averageExecutionDuration;
	private long durationOverallMillis;
	private int numberOfExecutions;

	/**
	 * Creates empty statistics.
	 *
	 * @param workerId id of the worker, shown in the statistics table
	 */
	public WorkerStats(final int workerId) {
		this.workerId = workerId;
	}

	/**
	 * Adds a successful run.
	 *
	 * @param duration duration of the run
	 * @param countInStatistics false to only show the run as latest result without counting it (e.g. during ramp-up)
	 */
	public synchronized void addSuccess(final Duration duration, final boolean countInStatistics) {
		// Latest result is always shown immediately, even during ramp-up
		latestDuration = duration;
		latestStatusWasSuccess = true;

		if (countInStatistics) {
			successCount++;
			updateDauer(duration);
		}
	}

	/**
	 * Adds a failed run.
	 *
	 * @param duration duration of the run
	 * @param countInStatistics false to only show the run as latest result without counting it (e.g. during ramp-up)
	 */
	public synchronized void addError(final Duration duration, final boolean countInStatistics) {
		// Latest result is always shown immediately, even during ramp-up
		latestDuration = duration;
		latestStatusWasSuccess = false;

		if (countInStatistics) {
			errorCount++;
			updateDauer(duration);
		}
	}

	private void updateDauer(final Duration duration) {
		durationOverallMillis += duration.toMillis();
		numberOfExecutions++;

		if (minimumDuration == null) {
			minimumDuration = duration;
		} else {
			minimumDuration = (duration.compareTo(minimumDuration) >= 0) ? minimumDuration : duration;
		}

		averageExecutionDuration = Duration.ofMillis(durationOverallMillis / numberOfExecutions);

		if (maximumDuration == null) {
			maximumDuration = duration;
		} else {
			maximumDuration = (duration.compareTo(maximumDuration) >= 0) ? duration : maximumDuration;
		}
	}

	/**
	 * Returns the id of the worker.
	 *
	 * @return the worker id
	 */
	public int getWorkerId() {
		return workerId;
	}

	/**
	 * Returns the number of successful runs counted in the statistics.
	 *
	 * @return the success count
	 */
	public synchronized int getSuccessCount() {
		return successCount;
	}

	/**
	 * Returns the number of failed runs counted in the statistics.
	 *
	 * @return the error count
	 */
	public synchronized int getErrorCount() {
		return errorCount;
	}

	/**
	 * Returns the minimum duration of the runs counted in the statistics.
	 *
	 * @return the minimum duration, or null if no run was counted yet
	 */
	public synchronized Duration getMinimumDuration() {
		return minimumDuration;
	}

	/**
	 * Returns the average duration of the runs counted in the statistics.
	 *
	 * @return the average duration, or null if no run was counted yet
	 */
	public synchronized Duration getAverageDuration() {
		return averageExecutionDuration;
	}

	/**
	 * Returns the maximum duration of the runs counted in the statistics.
	 *
	 * @return the maximum duration, or null if no run was counted yet
	 */
	public synchronized Duration getMaximumDuration() {
		return maximumDuration;
	}

	/**
	 * Returns the duration of the latest run, also during ramp-up.
	 *
	 * @return the latest duration, or null if no run has finished yet
	 */
	public synchronized Duration getLatestDuration() {
		return latestDuration;
	}

	/**
	 * Returns whether the latest run was successful, also during ramp-up.
	 *
	 * @return true for success, false for error, or null if no run has finished yet
	 */
	public synchronized Boolean getLatestStatusWasSuccess() {
		return latestStatusWasSuccess;
	}
}
