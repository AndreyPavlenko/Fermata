package me.aap.utils.net.http;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.utils.net.NetChannel;

/**
 * @author Andrey Pavlenko
 */
public interface HttpResponse extends HttpMessage {

	int getStatusCode();

	@NonNull
	CharSequence getReason();

	@Nullable
	CharSequence getLocation();

	@Nullable
	CharSequence getEtag();

	@NonNull
	HttpConnection getConnection();

	@NonNull
	@Override
	default NetChannel getChannel() {
		return getConnection().getChannel();
	}
}
