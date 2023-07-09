package me.aap.fermata.media.sub;

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
		for (var w : workers) w.run();
	}

	public void stop(boolean pause) {
		started = false;
		for (var w : workers) w.stop(pause);
	}

	public boolean isStarted() {
		return started;
	}

	public void sync(long time, int delay, float speed) {
		this.time = time + delay;
		this.speed = speed;
		syncTime = System.currentTimeMillis();
	}

	private final class Worker implements Runnable {
		private final Position pos;
		private final Subtitles subtitles;
		private Cancellable sched = Cancellable.CANCELED;


		Worker(Position pos, Subtitles subtitles) {
			this.pos = pos;
			this.subtitles = subtitles;
		}

		@Override
		public void run() {
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
				sched = executor.schedule(this, delay(delay));
			} else {
				consumer.accept(pos, text);
				sched = executor.schedule(this, delay(text.getDuration() + delay));
			}
		}

		void stop(boolean pause) {
			if (!pause) consumer.accept(pos, null);
			sched.cancel();
			sched = Cancellable.CANCELED;
		}

		private long time() {
			return time + (long) (speed * (System.currentTimeMillis() - syncTime));
		}

		private long delay(long delay) {
			return (long) (delay / speed);
		}
	}
}
