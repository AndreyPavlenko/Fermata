package me.aap.fermata.addon.chat;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static me.aap.utils.async.Completed.completedVoid;
import static me.aap.utils.async.Completed.failed;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import me.aap.fermata.addon.AddonManager;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.event.EventBroadcaster;
import me.aap.utils.io.IoUtils;
import me.aap.utils.log.Log;
import me.aap.utils.net.http.HttpConnection;
import me.aap.utils.net.http.HttpHeader;
import me.aap.utils.net.http.HttpMethod;
import me.aap.utils.net.http.HttpStatusCode;

/**
 * @author Andrey Pavlenko
 */
class ChatGpt implements EventBroadcaster<ChatGpt.Listener> {
	private static final ChatGpt instance = new ChatGpt();
	private final Collection<ListenerRef<Listener>> listeners = new LinkedList<>();
	private final ChatAddon addon = requireNonNull(AddonManager.get().getAddon(ChatAddon.class));

	private final List<ChatGpt.Message> messages = new ArrayList<>();

	public static ChatGpt getInstance() {
		return instance;
	}

	@Override
	public Collection<ListenerRef<Listener>> getBroadcastEventListeners() {
		return listeners;
	}

	void addMessage(String text, ChatGpt.Role role) {
		Message msg = new Message(role, text);
		messages.add(msg);
		int idx = messages.size() - 1;
		fireBroadcastEvent(l -> l.messageAdded(msg, idx));
	}

	public List<Message> getMessages() {
		return messages;
	}

	List<Message> getLastMessages(int count) {
		var s = messages.size();
		if (s == 0) return Collections.emptyList();
		return messages.subList(s - Math.min(count, s), s);
	}

	public FutureSupplier<String> sendRequest(List<Message> messages) {
		var key = addon.getOpenaiKey();
		if ((key == null) || (key = key.trim()).isBlank()) {
			return failed(new IllegalStateException("OpenAI key is not specified"));
		}

		var k = key;
		var p = new Promise<String>();
		HttpConnection.connect(o -> {
			o.url("https://api.openai.com/v1/chat/completions");
			o.method = HttpMethod.POST;
			o.builder = b -> {
				b.addHeader(HttpHeader.ACCEPT);
				b.addHeader(HttpHeader.CONTENT_TYPE, "application/json");
				b.addHeader(HttpHeader.AUTHORIZATION, "Bearer " + k);

				try {
					JSONObject[] jsonMessages = new JSONObject[messages.size()];
					for (int i = 0; i < jsonMessages.length; i++) {
						Message msg = messages.get(i);
						jsonMessages[i] = new JSONObject();
						jsonMessages[i].put("role", msg.role.toString());
						jsonMessages[i].put("content", msg.content);
					}
					JSONObject json = new JSONObject();
					json.put("model", "gpt-3.5-turbo");
					json.put("temperature", 0.4f);
					json.put("messages", new JSONArray(jsonMessages));
					Log.d("Request: ", json.toString());
					return b.build(ByteBuffer.wrap(json.toString().getBytes(UTF_8)));
				} catch (JSONException ex) {
					p.completeExceptionally(ex);
					throw new RuntimeException(ex);
				}
			};
		}, (resp, err) -> {
			if (err != null) {
				p.completeExceptionally(err);
				return failed(err);
			} else if (resp.getStatusCode() != HttpStatusCode.OK) {
				err = new IOException("Request failed: " + resp.getReason());
				p.completeExceptionally(err);
				return failed(err);
			}

			return resp.getPayload((bb, perr) -> {
				try {
					if (perr != null) return failed(perr);
					bb = IoUtils.getFrom(bb);
					String jsonString = new String(bb.array(), bb.arrayOffset(), bb.remaining(), UTF_8);
					Log.d("Response: ", jsonString);
					JSONObject json = new JSONObject(jsonString);
					String response = json.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
							.getString("content").trim();
					p.complete(response);
				} catch (JSONException ex) {
					p.completeExceptionally(ex);
					return failed(ex);
				}
				return completedVoid();
			});
		});

		return p.main();
	}

	enum Role {
		USER, ASSISTANT;
		private final String name;

		Role() {
			this.name = name().toLowerCase();
		}

		@NonNull
		@Override
		public String toString() {
			return name;
		}
	}

	static final class Message {
		final Role role;
		final String content;

		Message(Role role, String content) {
			this.role = role;
			this.content = content;
		}
	}

	interface Listener {
		void messageAdded(Message msg, int index);
	}
}
