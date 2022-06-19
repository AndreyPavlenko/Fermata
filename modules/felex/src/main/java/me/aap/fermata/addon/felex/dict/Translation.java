package me.aap.fermata.addon.felex.dict;

import static me.aap.utils.text.TextUtils.isNullOrBlank;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Andrey Pavlenko
 */
public class Translation {
	static final Translation DUMMY = new Translation("");
	private final String translation;
	private List<Example> examples = Collections.emptyList();

	public Translation(String translation) {
		this.translation = translation;
	}

	public Translation(String translation, @Nullable String example,
										 @Nullable String exampleTrans) {
		this(translation);
		if (!isNullOrBlank(example)) {
			addExample(new Example(example, exampleTrans));
		}
	}

	public String getTranslation() {
		return translation;
	}

	public List<Example> getExamples() {
		return examples;
	}

	public void setExamples(List<Example> examples) {
		this.examples = examples;
	}

	public boolean matches(String trans) {
		if (translation.equalsIgnoreCase(trans)) return true;
		for (String t : translation.split(",")) {
			if (t.trim().equalsIgnoreCase(trans)) return true;
		}
		return false;
	}

	@NonNull
	@Override
	public String toString() {
		return getTranslation();
	}

	void addExample(Example ex) {
		if (examples == Collections.EMPTY_LIST) examples = new ArrayList<>();
		examples.add(ex);
	}
}
