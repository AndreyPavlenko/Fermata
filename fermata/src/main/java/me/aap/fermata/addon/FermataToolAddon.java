package me.aap.fermata.addon;

import me.aap.fermata.ui.fragment.ToolBarMediator;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.view.ToolBarView;

/**
 * @author Andrey Pavlenko
 */
public interface FermataToolAddon extends FermataActivityAddon {
	void contributeTool(ToolBarMediator m, ToolBarView tb, ActivityFragment f);
}
