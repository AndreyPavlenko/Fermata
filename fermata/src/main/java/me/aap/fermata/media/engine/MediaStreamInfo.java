package me.aap.fermata.media.engine;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * @author Andrey Pavlenko
 */
public class MediaStreamInfo {
	private final long id;
	private final String language;
	private final String description;

	public MediaStreamInfo(long id, String language, String description) {
		this.id = id;
		this.language = (language == null) ? null :
				language.contains(" + ") ? language : new Locale(language).getDisplayLanguage();
		this.description = description;
	}

	public long getId() {
		return id;
	}

	public String getLanguage() {
		return language;
	}

	public String getDescription() {
		return description;
	}

	@Override
	public int hashCode() {
		return Long.hashCode(getId());
	}

	@Override
	public boolean equals(@Nullable Object obj) {
		return (obj == this) ||
				((getClass().isInstance(obj)) && ((MediaStreamInfo) obj).getId() == getId());
	}

	@NonNull
	@Override
	public String toString() {
		String lang = getLanguage();
		String desc = getDescription();
		boolean langEmpty = (lang == null) || (lang = lang.trim()).isEmpty();
		boolean descEmpty = (desc == null) || (desc = desc.trim()).isEmpty();

		if (langEmpty && descEmpty) {
			return "Track " + getId();
		} else if (langEmpty) {
			return desc;
		} else if (descEmpty) {
			return lang;
		} else {
			return desc.endsWith("]") ? desc : desc + " - [" + lang + ']';
		}
	}
}
