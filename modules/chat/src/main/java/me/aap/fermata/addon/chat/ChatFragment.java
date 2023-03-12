package me.aap.fermata.addon.chat;

import static android.view.KeyEvent.KEYCODE_DPAD_CENTER;
import static android.view.KeyEvent.KEYCODE_ENTER;
import static android.view.KeyEvent.KEYCODE_NUMPAD_ENTER;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.LEFT;
import static androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.RIGHT;
import static java.util.Objects.requireNonNull;
import static me.aap.fermata.util.Utils.dynCtx;
import static me.aap.utils.text.TextUtils.isBlank;
import static me.aap.utils.ui.UiUtils.toIntPx;

import android.content.Context;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;

import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.activity.MainActivityPrefs;
import me.aap.fermata.ui.activity.VoiceCommand;
import me.aap.fermata.ui.fragment.MainActivityFragment;
import me.aap.utils.function.Cancellable;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.ui.UiUtils;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.view.FloatingButton;
import me.aap.utils.ui.view.ToolBarView;
import me.aap.utils.voice.TextToSpeech;

/**
 * @author Andrey Pavlenko
 */
public class ChatFragment extends MainActivityFragment
		implements ChatGpt.Listener, PreferenceStore.Listener {
	private final ChatGpt chat = new ChatGpt();
	TextToSpeech tts;

	@Override
	public int getFragmentId() {
		return me.aap.fermata.R.id.chat_addon;
	}

	@Nullable
	@org.jetbrains.annotations.Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
													 @Nullable Bundle savedInstanceState) {
		return new ChatListView(requireContext());
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		MainActivityDelegate.get(requireContext()).getPrefs().addBroadcastListener(this);
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		MainActivityDelegate.get(requireContext()).getPrefs().removeBroadcastListener(this);
	}

	@Override
	public void onResume() {
		super.onResume();
		ChatGpt gpt = ChatGpt.getInstance();
		List<ChatGpt.Message> messages = gpt.getMessages();
		gpt.addBroadcastListener(this);
		if (!messages.isEmpty()) getList().scrollToPosition(messages.size() - 1);
	}

	@Override
	public void onPause() {
		super.onPause();
		ChatGpt.getInstance().removeBroadcastListener(this);
		if (tts != null) tts.close();
		tts = null;
	}

	@Override
	public void messageAdded(ChatGpt.Message msg, int index) {
		ChatListView.ChatListAdapter a = getList().getAdapter();
		if (a != null) a.messageAdded(index);
	}

	@Override
	public ToolBarView.Mediator getToolBarMediator() {
		return ChatToolMediator.instance;
	}

	@Override
	public FloatingButton.Mediator getFloatingButtonMediator() {
		return FbMediator.instance;
	}

	@Override
	public void onPreferenceChanged(PreferenceStore store, List<PreferenceStore.Pref<?>> prefs) {
		MainActivityDelegate.getActivityDelegate(requireContext()).onSuccess(a -> {
			if (MainActivityPrefs.hasTextIconSizePref(a, prefs)) {
				getList().scale(a.getTextIconSize());
			}
		});
	}

	@Override
	public boolean startVoiceAssistant() {
		ChatAddon a = AddonManager.get().getAddon(ChatAddon.class);
		if (a == null) return false;
		if (tts != null) tts.stop();
		MainActivityDelegate.get(getContext()).startSpeechRecognizer(a.getGetChatLang(), false)
				.onSuccess(q -> {
					if (!q.isEmpty()) sendRequest(q.get(0), true);
				});
		return true;
	}

	@Override
	public boolean isVoiceCommandsSupported() {
		return true;
	}

	@Override
	public void voiceCommand(VoiceCommand cmd) {
		sendRequest(cmd.getQuery(), true);
	}

	private ChatListView getList() {
		return (ChatListView) requireView();
	}

	private void sendRequest(String text, boolean voice) {
		if ((text == null) || isBlank(text = text.trim())) return;
		ChatGpt gpt = ChatGpt.getInstance();
		gpt.addMessage(text, ChatGpt.Role.USER);
		chat.sendRequest(gpt.getLastMessages(6)).onCompletion((resp, err) -> {
			if (err != null) {
				UiUtils.showAlert(requireContext(), "Request failed: " + err);
			} else {
				ChatGpt.getInstance().addMessage(resp, ChatGpt.Role.ASSISTANT);
				if (!voice) return;
				if (tts == null) {
					ChatAddon addon = requireNonNull(AddonManager.get().getAddon(ChatAddon.class));
					Locale lang = Locale.forLanguageTag(addon.getGetChatLang());
					TextToSpeech.create(requireContext(), lang).onCompletion((tts, terr) -> {
						if (terr != null) {
							UiUtils.showAlert(requireContext(), "TTS failed: " + terr);
						} else {
							this.tts = tts;
							speak(resp);
						}
					});
				} else {
					speak(resp);
				}
			}
		});
	}

	private void speak(String text) {
		assert tts != null;
		Cancellable interrupt = getActivityDelegate().interruptPlayback();
		tts.speak(text).onCompletion((r, err) -> {
			if ((err != null) && !(err instanceof CancellationException))
				UiUtils.showAlert(requireContext(), "TTS failed: " + err);
			interrupt.cancel();
		});
	}

	private static final class ChatToolMediator implements ToolBarView.Mediator {
		static final ChatToolMediator instance = new ChatToolMediator();

		@Override
		public void enable(ToolBarView tb, ActivityFragment f) {
			Context ctx = tb.getContext();
			dynCtx(ctx);
			ChatFragment cf = (ChatFragment) f;
			EditText t = createTextField(tb, cf);
			ConstraintLayout.LayoutParams lp = (ConstraintLayout.LayoutParams) t.getLayoutParams();
			lp.setMargins(toIntPx(ctx, 2), 0, 0, 0);
			addView(tb, t, R.id.send_text, LEFT);
			addButton(tb, R.drawable.send, v -> send(cf, tb.findViewById(R.id.send_text)),
					R.id.send_button, RIGHT);
			ToolBarView.Mediator.super.enable(tb, f);
		}

		private EditText createTextField(ToolBarView tb, ChatFragment f) {
			EditText t = createEditText(tb);
			ConstraintLayout.LayoutParams lp = setLayoutParams(t, 0, WRAP_CONTENT);
			lp.horizontalWeight = 2;
			t.setBackgroundResource(me.aap.utils.R.color.tool_bar_edittext_bg);
			t.setOnKeyListener((v, keyCode, event) -> onKey(f, t, keyCode, event));
			t.setMaxLines(1);
			t.setSingleLine(true);
			return t;
		}

		private boolean onKey(ChatFragment f, EditText text, int keyCode, KeyEvent event) {
			if (event.getAction() != KeyEvent.ACTION_DOWN) return false;

			switch (keyCode) {
				case KEYCODE_DPAD_CENTER:
				case KEYCODE_ENTER:
				case KEYCODE_NUMPAD_ENTER:
					send(f, text);
					return true;
				default:
					return UiUtils.dpadFocusHelper(text, keyCode, event);
			}
		}

		private void send(ChatFragment f, EditText text) {
			f.sendRequest(text.getText().toString(), false);
			text.setText("");
		}
	}

	private static final class FbMediator implements FloatingButton.Mediator {
		static final FbMediator instance = new FbMediator();

		@Override
		public void enable(FloatingButton b, ActivityFragment f) {
			b.setImageResource(me.aap.fermata.R.drawable.record_voice);
			b.setOnClickListener(v -> ((ChatFragment) f).startVoiceAssistant());
			b.setOnLongClickListener(v -> ((ChatFragment) f).startVoiceAssistant());
		}
	}
}
