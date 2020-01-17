package de.codecentric.mule.batch;

import java.util.HashMap;
import java.util.Map;

/**
 * Helper to wait for a Mule Batch.
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
