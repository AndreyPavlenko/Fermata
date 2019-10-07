package me.aap.fermata.pref;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import me.aap.fermata.R;

/**
 * @author Andrey Pavlenko
 */
public class PreferenceViewAdapter extends RecyclerView.Adapter<PreferenceViewAdapter.ViewHolder> {
	private PreferenceSet set;

	public PreferenceViewAdapter(PreferenceSet set) {
		this.set = set;
	}

	@NonNull
	@Override
	public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		PreferenceView v = (PreferenceView) LayoutInflater.from(parent.getContext())
				.inflate(R.layout.pref_view, parent, false);
		return new ViewHolder(v);
	}

	@Override
	public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
		holder.getPreferenceView().setPreference(this, set.preferences.get(position));
	}

	@Override
	public void onViewRecycled(@NonNull ViewHolder holder) {
		holder.getPreferenceView().setPreference(this, null);
	}

	@Override
	public int getItemCount() {
		return set.preferences.size();
	}

	public void setPreferenceSet(PreferenceSet set) {
		this.set = set;
		notifyDataSetChanged();
	}

	public PreferenceSet getPreferenceSet() {
		return set;
	}

	public static class ViewHolder extends RecyclerView.ViewHolder {

		public ViewHolder(@NonNull PreferenceView pref) {
			super(pref);
		}

		public PreferenceView getPreferenceView() {
			return (PreferenceView) itemView;
		}

	}
}
