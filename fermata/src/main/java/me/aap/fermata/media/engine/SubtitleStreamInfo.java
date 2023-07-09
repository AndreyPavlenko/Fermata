package me.aap.fermata.media.engine;

import java.util.Collections;
import java.util.List;

import me.aap.utils.vfs.VirtualFile;

/**
 * @author Andrey Pavlenko
 */
public class SubtitleStreamInfo extends MediaStreamInfo {
	private final List<VirtualFile> files;

	public SubtitleStreamInfo(long id, String language, String description) {
		this(id, language, description, Collections.emptyList());
	}

	public SubtitleStreamInfo(long id, String language, String description,
														List<VirtualFile> files) {
		super(id, language, description);
		this.files = files;
	}

	public List<VirtualFile> getFiles() {
		return files;
	}
}
