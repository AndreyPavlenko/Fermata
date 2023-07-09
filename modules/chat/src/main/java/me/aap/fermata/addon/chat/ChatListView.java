package me.aap.fermata.addon.chat;

import static android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE;
import static java.util.Objects.requireNonNull;
import static me.aap.utils.ui.UiUtils.toIntPx;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.SpannableString;
import android.text.style.ImageSpan;
import android.text.style.URLSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.regex.Pattern;

import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.util.Utils;
import me.aap.utils.ui.UiUtils;
import me.aap.utils.ui.view.ScalableTextView;

/**
 * @author Andrey Pavlenko
 */
public class ChatListView extends RecyclerView {
	static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+");
	private final Drawable userIcon;
	private final Drawable assistantIcon;

	public ChatListView(@NonNull Context ctx) {
		super(ctx);
		var a = MainActivityDelegate.get(ctx);
		var scale = a.getPrefs().getTextIconSizePref(a);
		var size = getIconSize(ctx, scale);
		userIcon = requireNonNull(ContextCompat.getDrawable(ctx, R.drawable.person));
		assistantIcon = requireNonNull(ContextCompat.getDrawable(ctx, me.aap.fermata.R.drawable.chat));
		userIcon.setBounds(0, 0, size, size);
		assistantIcon.setBounds(0, 0, size, size);
		setLayoutManager(new LinearLayoutManager(ctx));
		setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
		setAdapter(new ChatListAdapter());
	}

	@Nullable
	@Override
	public ChatListAdapter getAdapter() {
		return (ChatListAdapter) super.getAdapter();
	}

	void scale(float scale) {
		var size = getIconSize(getContext(), scale);
		userIcon.setBounds(0, 0, size, size);
		assistantIcon.setBounds(0, 0, size, size);
		for (int n = getChildCount(), i = 0; i < n; i++) {
			ScalableTextView text = (ScalableTextView) getChildAt(i);
			text.scale(scale);
		}
	}

	private int getIconSize(Context ctx, float scale) {
		return (int) (toIntPx(ctx, 20) * scale);
	}

	class ChatListAdapter extends Adapter<Holder> {

		@NonNull
		@Override
		public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			var v = LayoutInflater.from(getContext())
					.inflate(me.aap.fermata.R.layout.list_item, parent, false);
			return new Holder(v);
		}

		@Override
		public void onBindViewHolder(@NonNull Holder holder, int position) {
			var msg = ChatGpt.getInstance().getMessages().get(position);
			var text = (ScalableTextView) holder.itemView;
			Drawable icon;

			if (msg.role == ChatGpt.Role.ASSISTANT) {
				icon = assistantIcon;
				text.setSelected(true);
			} else {
				icon = userIcon;
				text.setSelected(false);
			}

			icon.setTintList(text.getCompoundDrawableTintList());
			var s = new SpannableString(" " + msg.content);
			var is = new ImageSpan(icon, ImageSpan.ALIGN_CENTER);
			s.setSpan(is, 0, 1, SPAN_EXCLUSIVE_EXCLUSIVE);
			var urls = new ArrayList<String>();

			for (var m = URL_PATTERN.matcher(msg.content); m.find(); ) {
				var start = m.start();
				var end = m.end();
				var url = m.group();
				urls.add(url);
				s.setSpan(new URLSpan(url), start + 1, end + 1, SPAN_EXCLUSIVE_EXCLUSIVE);
			}

			if (urls.isEmpty()) {
				text.setOnClickListener(null);
			} else {
				text.setOnClickListener(v -> {
					if (urls.size() == 1) {
						Utils.openUrl(getContext(), urls.get(0));
					} else {
						MainActivityDelegate.get(getContext()).getContextMenu().show(b -> {
							for (int i = 0, n = urls.size(); i < n; i++) {
								var url = urls.get(i);
								var title = new SpannableString(url);
								title.setSpan(new URLSpan(url), 0, url.length(), SPAN_EXCLUSIVE_EXCLUSIVE);
								b.addItem(i, title);
							}
							b.setSelectionHandler(i -> Utils.openUrl(getContext(), urls.get(i.getItemId())));
						});
					}
				});
			}

			text.setOnLongClickListener(t-> {
				UiUtils.addToClipboard(getContext(),msg.role.toString(),msg.content);
				return true;
			});
			text.setText(s);
		}

		@Override
		public int getItemCount() {
			return ChatGpt.getInstance().getMessages().size();
		}

		void messageAdded(int index) {
			notifyItemInserted(index);
			scrollToPosition(index);
		}
	}

	private static final class Holder extends ViewHolder {
		public Holder(@NonNull View itemView) {
			super(itemView);
		}
	}
}
