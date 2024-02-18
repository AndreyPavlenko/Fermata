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
	private final String isoLanguage;
	private final String description;

	public MediaStreamInfo(long id, String language, String description) {
		this.id = id;
		if ((language == null) || (language = language.trim()).isEmpty()) {
			this.language = isoLanguage = null;
		} else {
			var lang = new Locale(language).getDisplayLanguage();
			if (lang.equalsIgnoreCase(language)) {
				this.language = isoLanguage = language;
			} else {
				this.language = lang;
				isoLanguage = language;
			}
		}
		this.description =
				(description == null) || (description = description.trim()).isEmpty() ? null : description;
	}

	public long getId() {
		return id;
	}

	public String getLanguage() {
		return language;
	}

	public String getIsoLanguage() {
		return isoLanguage;
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

		if ((lang == null) && (desc == null)) {
			return "Track " + getId();
		} else if (lang == null) {
			return desc;
		} else if (desc == null) {
			return lang;
		} else {
			return desc.endsWith("]") ? desc : desc + " - [" + lang + ']';
		}
	}
}
