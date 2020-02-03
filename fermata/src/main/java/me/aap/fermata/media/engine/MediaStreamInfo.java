package me.aap.fermata.media.engine;

import androidx.annotation.Nullable;

/**
 * @author Andrey Pavlenko
 */
public class MediaStreamInfo {
	private final int id;
	private final String language;
	private final String description;

	public MediaStreamInfo(int id, String language, String description) {
		this.id = id;
		this.language = language;
		this.description = description;
	}

	public int getId() {
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
		return getId();
	}

	@Override
	public boolean equals(@Nullable Object obj) {
		return (obj == this) || ((getClass().isInstance(obj))
				&& ((MediaStreamInfo) obj).getId() == getId());
	}

	public String toString() {
		String lang = getLanguage();
		String desc = getDescription();
		boolean langEmpty = (lang == null) || (lang = lang.trim()).isEmpty();
		boolean descEmpty = (desc == null) || (desc = desc.trim()).isEmpty();

		if (langEmpty && descEmpty) {
			return String.valueOf(getId());
		} else if (langEmpty) {
			return desc;
		} else {
			return lang + ": " + desc;
		}
	}
}
