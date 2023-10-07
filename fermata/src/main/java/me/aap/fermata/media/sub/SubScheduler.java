package me.aap.fermata.media.sub;

import static me.aap.utils.function.Cancellable.CANCELED;

import java.util.ArrayList;

import me.aap.fermata.media.sub.SubGrid.Position;
import me.aap.utils.concurrent.HandlerExecutor;
import me.aap.utils.function.BiConsumer;
import me.aap.utils.function.Cancellable;

/**
 * @author Andrey Pavlenko
 */
public class SubScheduler {
	private final HandlerExecutor executor;
	private final SubGrid subtitles;
	private final BiConsumer<Position, Subtitles.Text> consumer;
	private final ArrayList<Worker> workers;
	private long time;
	private long syncTime;
	private float speed;
	private boolean started;

	public SubScheduler(HandlerExecutor executor, SubGrid subtitles,
											BiConsumer<Position, Subtitles.Text> consumer) {
		this.executor = executor;
		this.subtitles = subtitles;
		this.consumer = consumer;
		workers = new ArrayList<>(9);
		for (var e : subtitles) {
			if (!e.getValue().isEmpty()) {
				workers.add(new Worker(e.getKey(), e.getValue()));
			}
		}
		workers.trimToSize();
	}

	public SubGrid getSubtitles() {
		return subtitles;
	}

	public void start(long time, int delay, float speed) {
		if (started) return;
		started = true;
		sync(time, delay, speed);
		assert started;
	}

	public void stop(boolean pause) {
		started = false;
		for (var w : workers) w.stop(pause);
		assert !started;
	}

	public boolean isStarted() {
		return started;
	}

	public void sync(long time, int delay, float speed) {
		if (!started) return;
		this.time = time + delay;
		this.speed = speed;
		syncTime = System.currentTimeMillis();
		for (var w : workers) {
			if (!w.isStarted()) w.start();
		}
	}

	private final class Worker implements Runnable {
		private final Position pos;
		private final Subtitles subtitles;
		private Cancellable sched = CANCELED;


		Worker(Position pos, Subtitles subtitles) {
			this.pos = pos;
			this.subtitles = subtitles;
		}

		@Override
		public void run() {
			assert started;
			assert !sched.cancel();
			long time = time();
			Subtitles.Text text = subtitles.getNext(time);

			if (text == null) {
				stop(false);
				return;
			}

			long delay = text.getTime() - time;

			if (delay > 500) {
				consumer.accept(pos, null);
				sched(delay);
			} else {
				consumer.accept(pos, text);
				sched(text.getDuration() + delay);
			}
		}

		void start() {
			assert !sched.cancel();
			sched = executor.submit(this);
		}

		void stop(boolean pause) {
			if (!pause) consumer.accept(pos, null);
			sched.cancel();
			sched = CANCELED;
		}

		boolean isStarted() {
			return sched != CANCELED;
		}

		private long time() {
			return time + (long) (speed * (System.currentTimeMillis() - syncTime));
		}


		private void sched(long delay) {
			if (!started) return;
			delay /= speed;
			if (delay > 0) sched = executor.schedule(this, delay);
			else sched = CANCELED;
		}
	}
}
