package me.aap.utils.net.http;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * @author Andrey Pavlenko
 */
public interface HttpRequest extends HttpMessage {

	@NonNull
	HttpMethod getMethod();

	@NonNull
	CharSequence getUri();

	@NonNull
	CharSequence getPath();

	@Nullable
	CharSequence getQuery();

	@Nullable
	Range getRange();
}
