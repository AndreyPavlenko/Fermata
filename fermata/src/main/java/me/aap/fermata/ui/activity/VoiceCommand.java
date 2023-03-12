package me.aap.fermata.ui.activity;

/**
 * @author Andrey Pavlenko
 */
public class VoiceCommand {
	public static final int ACTION_PLAY = 1;
	public static final int ACTION_FIND = 2;
	public static final int ACTION_OPEN = 4;
	public static final int ACTION_CHAT = 5;

	private final String query;
	private final int action;

	public VoiceCommand(String query, int action) {
		this.query = query;
		this.action = action;
	}

	public String getQuery() {
		return query;
	}

	public int getAction() {
		return action;
	}

	public boolean isPlay() {
		return (getAction() & ACTION_PLAY) != 0;
	}

	public boolean isFind() {
		return (getAction() & ACTION_FIND) != 0;
	}

	public boolean isOpen() {
		return (getAction() & ACTION_OPEN) != 0;
	}
}
