package me.aap.fermata.media.engine;

import java.util.ArrayList;
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

	public SubtitleStreamInfo(long id, String language, String description, VirtualFile file) {
		this(id, language, description, Collections.singletonList(file));
	}

	private SubtitleStreamInfo(long id, String language, String description,
														 List<VirtualFile> files) {
		super(id, language, description);
		this.files = files;
	}

	public List<VirtualFile> getFiles() {
		return files;
	}

	public SubtitleStreamInfo join(long id, SubtitleStreamInfo other) {
		var files1 = getFiles();
		var files2 = other.getFiles();
		var files = new ArrayList<VirtualFile>(files1.size() + files2.size());
		files.addAll(files1);
		files.addAll(files2);
		return new SubtitleStreamInfo(id, null, null, files) {
			@Override
			public String getLanguage() {
				return join(SubtitleStreamInfo.this.getLanguage(), other.getLanguage());
			}

			@Override
			public String getIsoLanguage() {
				return join(SubtitleStreamInfo.this.getIsoLanguage(), other.getIsoLanguage());
			}

			@Override
			public String getDescription() {
				return join(SubtitleStreamInfo.this.getDescription(), other.getDescription());
			}

			private String join(String s1, String s2) {
				return (s1 == null) ? s2 : (s2 == null) ? s1 : s1 + " + " + s2;
			}
		};
	}

	public static class Generated extends SubtitleStreamInfo {
		public Generated(String language) {
			super(Long.MIN_VALUE, language, "Auto generated", Collections.emptyList());
		}
	}
}
