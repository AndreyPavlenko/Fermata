package me.aap.fermata.engine.exoplayer;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;

import androidx.annotation.NonNull;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.util.UnstableApi;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.addon.SubGenAddon;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.log.Log;

@UnstableApi
class PendingLoadAudioProcessor implements AudioProcessor {
	private final ExoPlayerEngine.Accessor player;
	private final AtomicInteger state = new AtomicInteger();
	private AudioFormat pendingConfiguration;
	private FutureSupplier<AudioTranscriptProcessor> delegate = completedNull();

	PendingLoadAudioProcessor(ExoPlayerEngine.Accessor player) {
		this.player = player;
	}

	@Override
	public long getDurationAfterProcessorApplied(long durationUs) {
		var d = delegate.peek();
		return (d != null) ? d.getDurationAfterProcessorApplied(durationUs) : durationUs;
	}

	@NonNull
	@Override
	public AudioFormat configure(@NonNull AudioFormat inputAudioFormat)
			throws UnhandledAudioFormatException {
		pendingConfiguration = null;
		var source = player.getSource();
		if (source == null) {
			releaseDelegate();
			return inputAudioFormat;
		}

		var ps = source.getPrefs();
		if (ps.getBooleanPref(SubGenAddon.ENABLED)) {
			var atp = delegate.peek();
			if (atp != null) {
				if (!atp.reconfigure(ps)) {
					atp.release();
					createDelegate();
				}
			} else if (delegate.isDone()) {
				createDelegate();
			}
		} else {
			releaseDelegate();
		}

		var d = delegate.peek();
		if (d == null) {
			return pendingConfiguration = inputAudioFormat;
		} else {
			pendingConfiguration = null;
			return d.configure(inputAudioFormat);
		}
	}

	@Override
	public boolean isActive() {
		var d = get();
		return (d != null) && d.isActive() || !delegate.isDone();
	}

	@Override
	public void queueInput(@NonNull ByteBuffer inputBuffer) {
		var d = get();
		if ((d == null) && !delegate.isDone()) {
			try {
				d = delegate.get(500, MILLISECONDS);
			} catch (TimeoutException ignore) {
			} catch (Exception err) {
				Log.e(err);
			}
		}
		if (d != null) d.queueInput(inputBuffer);
	}

	@Override
	public void queueEndOfStream() {
		var d = get();
		if (d != null) d.queueEndOfStream();
	}

	@NonNull
	@Override
	public ByteBuffer getOutput() {
		var d = get();
		return (d != null) ? d.getOutput() : EMPTY_BUFFER;
	}

	@Override
	public boolean isEnded() {
		var d = get();
		return (d != null) && d.isEnded();
	}

	@Override
	public void flush() {
		var d = get();
		if (d != null) d.flush();
	}

	@Override
	public void reset() {
		state.incrementAndGet();
		var d = delegate.peek();
		delegate = completedNull();
		if (d != null) {
			d.reset();
			d.release();
		}
	}

	private void createDelegate() {
		var st = state.incrementAndGet();
		delegate = FermataApplication.get().getAddonManager().getOrInstallAddon(SubGenAddon.class)
				.then(a -> {
					if (a == null || state.get() != st) return completedNull();
					var pi = player.getSource();
					if (pi == null || !pi.getPrefs().getBooleanPref(SubGenAddon.ENABLED))
						return completedNull();
					return a.getTranscriptor(pi.getPrefs());
				}).then(t -> {
					if (t == null) return completedNull();
					var pi = player.getSource();
					if (pi == null || state.get() != st ||
							!pi.getPrefs().getBooleanPref(SubGenAddon.ENABLED)) {
						t.release();
						return completedNull();
					}
					if (!t.reconfigure(pi.getPrefs())) {
						t.release();
						createDelegate();
						return completedNull();
					}
					var ps = pi.getPrefs();
					return delegate = completed(new AudioTranscriptProcessor(player, t,
							ps.getIntPref(SubGenAddon.BUF_LEN), ps.getIntPref(SubGenAddon.CHUNK_LEN)));
				});
	}

	private void releaseDelegate() {
		state.incrementAndGet();
		var atp = delegate.peek();
		if (atp != null) {
			atp.release();
			delegate = completedNull();
		}
	}

	private AudioTranscriptProcessor get() {
		var atp = delegate.peek();
		if (atp == null) return null;
		if (pendingConfiguration != null) {
			try {
				atp.configure(pendingConfiguration);
			} catch (UnhandledAudioFormatException err) {
				throw new RuntimeException(err);
			}
			pendingConfiguration = null;
		}
		return atp;
	}
}
