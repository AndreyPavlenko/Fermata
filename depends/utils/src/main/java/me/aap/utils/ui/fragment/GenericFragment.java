package me.aap.utils.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.utils.R;
import me.aap.utils.function.Consumer;
import me.aap.utils.ui.view.FloatingButton;
import me.aap.utils.ui.view.NavBarView;
import me.aap.utils.ui.view.ToolBarView;

/**
 * @author Andrey Pavlenko
 */
public class GenericFragment extends ActivityFragment {
	private String title = "";
	private ToolBarView.Mediator toolBarMediator;
	private NavBarView.Mediator navBarMediator;
	private FloatingButton.Mediator floatingButtonMediator;
	private Consumer<ViewGroup> contentProvider;

	@Override
	public int getFragmentId() {
		return R.id.generic_fragment;
	}

	public CharSequence getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public ToolBarView.Mediator getToolBarMediator() {
		return (toolBarMediator != null) ? toolBarMediator : super.getToolBarMediator();
	}

	public void setToolBarMediator(ToolBarView.Mediator toolBarMediator) {
		this.toolBarMediator = toolBarMediator;
	}

	public NavBarView.Mediator getNavBarMediator() {
		return (navBarMediator != null) ? navBarMediator : super.getNavBarMediator();
	}

	public void setNavBarMediator(NavBarView.Mediator navBarMediator) {
		this.navBarMediator = navBarMediator;
	}

	public FloatingButton.Mediator getFloatingButtonMediator() {
		return (floatingButtonMediator != null) ? floatingButtonMediator : super.getFloatingButtonMediator();
	}

	public void setFloatingButtonMediator(FloatingButton.Mediator floatingButtonMediator) {
		this.floatingButtonMediator = floatingButtonMediator;
	}

	public void setContentProvider(Consumer<ViewGroup> contentProvider) {
		this.contentProvider = contentProvider;
		ViewGroup v = getView();
		if (v == null) return;
		v.removeAllViews();
		contentProvider.accept(v);
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
													 @Nullable Bundle savedInstanceState) {
		return new LinearLayout(getContext());
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		if (contentProvider != null) contentProvider.accept(getView());
	}

	@Nullable
	@Override
	public ViewGroup getView() {
		return (ViewGroup) super.getView();
	}

	@Override
	public void switchingFrom(@Nullable ActivityFragment currentFragment) {
		if ((navBarMediator != null) || (currentFragment == null)) return;
		navBarMediator = currentFragment.getNavBarMediator();
	}
}
