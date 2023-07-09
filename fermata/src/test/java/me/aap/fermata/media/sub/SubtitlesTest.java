package me.aap.fermata.media.sub;

import androidx.annotation.NonNull;

import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import me.aap.utils.concurrent.HandlerExecutor;
import me.aap.utils.function.BiConsumer;
import me.aap.utils.function.Cancellable;
import me.aap.utils.function.IntConsumer;

/**
 * @author Andrey Pavlenko
 */
public class SubtitlesTest extends Assert {

	@Test
	public void testGetNext() {
		int n = 1000;
		var rnd = new Random();
		Subtitles s = new Subtitles(b -> {});
		assertNull(s.getNext(1));

		s = new Subtitles(b -> {
			for (int i = 0; i < n; i++) {
				b.add("Text " + i, rnd.nextInt(100000), rnd.nextInt(100) + 1);
			}
		});

		for (int i = 0, c = s.size(); i < c; i++) {
			assertEquals(i, s.get(i).getIndex());
		}
		for (var t : s) {
			assertSame(t, s.getNext(t.getTime()));
		}
		s.get(s.size() / 2);
		for (int i = s.size() - 1; i >= 0; i--) {
			Subtitles.Text t = s.get(i);
			assertSame(t, s.getNext(t.getTime()));
		}
	}

	@Test
	public void testSrtSubtitles() throws IOException {
		// @formatter:off
		var text =
"""
WEBVTT
Kind: captions
Language: en

1
00:00:00.900 --> 00:00:01.100
{\\an7}TOP_LEFT

2
00:01:01,000 --> 00:01:03,000
{\\an5}MIDDLE_CENTER

3
09:59:59,999 --> 10:59:59.999
{\\an2}BOTTOM_CENTER

4
11:00:00.000 --> 11:00:00,001
BOTTOM_CENTER

5
12:00:00,000 --> 12:00:00,001
BOTTOM_CENTER
Multi line
""";
		// @formatter:on

		var sg = FileSubtitles.load(new ByteArrayInputStream(text.getBytes()));
		var s = sg.get(SubGrid.Position.TOP_LEFT).get(0);
		assertEquals(900, s.getTime());
		assertEquals(200, s.getDuration());
		assertEquals("TOP_LEFT", s.getText());
		s = sg.get(SubGrid.Position.MIDDLE_CENTER).get(0);
		assertEquals(61000, s.getTime());
		assertEquals(2000, s.getDuration());
		assertEquals("MIDDLE_CENTER", s.getText());
		s = sg.get(SubGrid.Position.BOTTOM_CENTER).get(0);
		assertEquals(9 * 60 * 60000 + 59 * 60000 + 59000 + 999, s.getTime());
		assertEquals(3600000, s.getDuration());
		assertEquals("BOTTOM_CENTER", s.getText());
		s = sg.get(SubGrid.Position.BOTTOM_CENTER).get(1);
		assertEquals(11 * 60 * 60000, s.getTime());
		assertEquals(1, s.getDuration());
		assertEquals("BOTTOM_CENTER", s.getText());
		s = sg.get(SubGrid.Position.BOTTOM_CENTER).get(2);
		assertEquals(12 * 60 * 60000, s.getTime());
		assertEquals(1, s.getDuration());
		assertEquals("BOTTOM_CENTER\nMulti line", s.getText());
	}

	@Test
	public void testScheduler() throws Exception {
		StringBuilder sb = new StringBuilder();
		IntConsumer itos = i -> {
			if (i < 10) {
				sb.append("00").append(i);
			} else if (i < 100) {
				sb.append("0").append(i);
			} else {
				sb.append(i);
			}
		};

		int n = 49;
		int dur = 10;
		List<String> expect = new ArrayList<>(2 * n);
		List<String> received = new ArrayList<>(2 * n);

		for (int i = 1, d = dur; i <= n; i++, d += dur) {
			var s = String.valueOf(i);
			expect.add(s);
			sb.append(s).append('\n');
			sb.append("00:00:00,");
			itos.accept(d);
			d += dur;
			sb.append(" --> 00:00:00,");
			itos.accept(d);
			sb.append('\n').append(s).append("\n\n");
		}

		var sg = FileSubtitles.load(new ByteArrayInputStream(sb.toString().getBytes()));
		var exec = new HandlerExecutor() {
			final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

			@Override
			public synchronized Cancellable schedule(@NonNull Runnable task, long delay) {
				var f = scheduler.schedule(task, delay, TimeUnit.MILLISECONDS);
				return new Cancellable() {
					@Override
					public boolean cancel() {
						return f.cancel(false);
					}
				};
			}
		};
		Semaphore sem = new Semaphore(0);
		BiConsumer<SubGrid.Position, Subtitles.Text> consumer = (p, t) -> {
			if (t == null) return;
			received.add(t.getText());
			if (expect.size() == received.size()) sem.release();
		};
		SubScheduler sched = new SubScheduler(exec, sg, consumer);
		sched.start(0, 0, 1);
		sem.acquire();
		assertArrayEquals(expect.toArray(), received.toArray());

		sched.stop(false);
		received.clear();
		sched.start(0, 10, 1);
		sem.acquire();
		assertArrayEquals(expect.toArray(), received.toArray());

		sched.stop(false);
		received.clear();
		sched.start(0, 10, 2);
		sem.acquire();
		assertArrayEquals(expect.toArray(), received.toArray());

		sched.stop(false);
		received.clear();
		sched.start(0, 10, 0.5f);
		sem.acquire();
		assertArrayEquals(expect.toArray(), received.toArray());
	}
}
