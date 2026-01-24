package me.aap.utils.voice;

/**
 * @author Andrey Pavlenko
 */
public class TextToSpeechException extends Exception {
	public static final int TTS_ERR_INIT = 1;
	public static final int TTS_ERR_LANG_MISSING_DATA = 2;
	public static final int TTS_ERR_LANG_NOT_SUPPORTED = 3;
	public static final int TTS_ERR_SPEAK_FAILED = 4;
	public static final int TTS_ERR_CLOSED = 5;
	private final int errorCode;

	public TextToSpeechException(String msg, int errorCode) {
		super(msg);
		this.errorCode = errorCode;
	}

	public int getErrorCode() {
		return errorCode;
	}
}
