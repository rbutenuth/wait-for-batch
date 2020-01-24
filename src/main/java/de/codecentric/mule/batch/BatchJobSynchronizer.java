package de.codecentric.mule.batch;

import java.util.HashMap;
import java.util.Map;

/**
 * Helper class to wait for a batch in a Mule application. This class needs book keeping of batch jobs instances
 * and uses it's own ids for that purpose. The "real" batch job instance may have a different id, but it's a
 * good idea to use the same id. <br/>
 * Usage: <ol>
 * <li>Call {@link #createJob(String)} to create a new book keeping instance.</li>
 * <li>Call {@link #finishJob(String, Object)} when the job is finished, either from the completion phase
 * or at the end of a batch aggregator. The first parameter is the generated id, the secondary arbitrary, it will be passed through.</li>
 * <li>Call {@link #waitUntilFinished(String)} to wait for the end of the batch job instance. The parameter is the generated id,
 * returned will be the passed through value from the <code>finishJob</code> call.</li>
 * </ol>
 */
public class BatchJobSynchronizer {
	private static Map<String, StateHolder> jobs = new HashMap<>();
	private static long jobCounter = System.currentTimeMillis();

	/**
	 * Create a new "batch job" (only the administration data to wait for it).
	 * 
	 * @param prefix
	 *            Prefix of the job id.
	 * @return Unique job it, starting with <code>prefix</code>
	 */
	public static String createJob(String prefix) {
		synchronized (jobs) {
			String id = prefix + jobCounter++;
			jobs.put(id, new StateHolder());
			return id;
		}
	}

	/**
	 * Mark a job as finished. This should be called from within the completion
	 * phase of a Mule batch.
	 * 
	 * @param id
	 *            The <code>id</code> returned by {@link #createJob(String)}.
	 */
	public static void finishJob(String id, Object result) {
		StateHolder stateHolder;
		synchronized (jobs) {
			stateHolder = jobs.get(id);
		}
		if (stateHolder != null) {
			stateHolder.finish(result);
		}
	}

	/**
	 * Wait until a job finishes.
	 * 
	 * @param id
	 *            The <code>id</code> returned by {@link #createJob(String)}.
	 * @return <code>result</code> provided by {@link #finishJob(String, Object)} or
	 *         <code>null</code when job is not found.
	 */
	public static Object waitUntilFinished(String id) {
		StateHolder stateHolder;
		Object result = null;
		synchronized (jobs) {
			stateHolder = jobs.get(id);
		}
		if (stateHolder != null) {
			result = stateHolder.waitUntilFinished();
			synchronized (jobs) {
				jobs.remove(id);
			}
		}
		return result;
	}

	private static class StateHolder {
		private State state;
		private Object result;

		public StateHolder() {
			state = State.RUNNING;
		}

		public synchronized void finish(Object result) {
			state = State.FINISHED;
			this.result = result;
			notifyAll();
		}

		public synchronized Object waitUntilFinished() {
			while (state == State.RUNNING) {
				try {
					wait();
				} catch (InterruptedException e) {
					// nothing to do
				}
			}
			return result;
		}
	}

	private enum State {
		RUNNING, FINISHED
	}
}
