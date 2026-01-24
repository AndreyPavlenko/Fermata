package me.aap.utils.voice;

import android.speech.SpeechRecognizer;

/**
 * @author Andrey Pavlenko
 */
public class SpeechToTextException extends Exception {
	private final int errorCode;

	public SpeechToTextException(int errorCode) {
		super(msg(errorCode));
		this.errorCode = errorCode;
	}

	public int getErrorCode() {
		return errorCode;
	}

	private static String msg(int errorCode) {
		String err = switch (errorCode) {
			case SpeechRecognizer.ERROR_NO_MATCH -> "ERROR_NO_MATCH";
			case SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "ERROR_SPEECH_TIMEOUT";
			case SpeechRecognizer.ERROR_AUDIO -> "ERROR_AUDIO";
			case SpeechRecognizer.ERROR_CLIENT -> "ERROR_CLIENT";
			case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "ERROR_INSUFFICIENT_PERMISSIONS";
			case SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "ERROR_LANGUAGE_NOT_SUPPORTED";
			case SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "ERROR_LANGUAGE_UNAVAILABLE";
			case SpeechRecognizer.ERROR_NETWORK -> "ERROR_NETWORK";
			case SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ERROR_NETWORK_TIMEOUT";
			case SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "ERROR_RECOGNIZER_BUSY";
			case SpeechRecognizer.ERROR_SERVER -> "ERROR_SERVER";
			case SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "ERROR_TOO_MANY_REQUESTS";
			case SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "ERROR_SERVER_DISCONNECTED";
			case SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT -> "ERROR_CANNOT_CHECK_SUPPORT";
			case SpeechRecognizer.ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS ->
					"ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS";
			default -> "code=" + errorCode;
		};
		return "Speech recognition failed: " + err;
	}
}
