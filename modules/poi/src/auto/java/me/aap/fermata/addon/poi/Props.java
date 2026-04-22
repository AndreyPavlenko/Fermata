package me.aap.fermata.addon.poi;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;

import me.aap.utils.app.App;
import me.aap.utils.function.Function;
import me.aap.utils.function.Supplier;
import me.aap.utils.log.Log;

public class Props {
	private static final Props[] NO_ANCESTORS = new Props[0];
	private final Props[] ancestors;
	private final Map<String, Object> props;

	public Props(Map<String, Object> props, Props... ancestors) {
		this.props = props;
		this.ancestors = ancestors;
	}

	@Nullable
	public String getStrProp(String name) {
		return getStrProp(name, null);
	}

	public String getStrProp(String name, String def) {
		var v = getProp(name);
		return v != null ? substitute(v.toString()) : def;
	}

	@Nullable
	public Object getProp(String name) {
		if (name.startsWith("R.")) {
			// Get string resource by name, e.g. R.string.some_string
			var res = App.get().getResources();
			var id = res.getIdentifier(name.substring(2), null, App.get().getPackageName());
			if (id != 0) return res.getString(id);
			Log.e("Resource not found: ", name);
			return null;

		}
		var v = get(name);
		if (v instanceof Supplier<?> sp) return sp.get();
		if (v instanceof Function) //noinspection unchecked
			return ((Function<Object, Props>) v).apply(this);
		return v;
	}

	@SuppressWarnings("unchecked")
	@NonNull
	public <T> T getProp(String name, @NonNull T def) {
		var v = getProp(name);
		if (v == null) return def;
		if (!def.getClass().isInstance(v)) {
			Log.e("Invalid prop value for ", name, ": ", v, ", expected ", def.getClass());
			return def;
		}
		return (T) v;
	}

	@Nullable
	protected Object get(String name) {
		var v = props.get(name);
		if (v == null) {
			for (int i = ancestors.length - 1; i >= 0; i--) {
				v = ancestors[i].get(name);
				if (v != null) return v;
			}
		}
		return v;
	}

	public void setProp(String name, String value) {
		props.put(name, value);
	}

	public void setProp(String name, int value) {
		props.put(name, value);
	}

	public <T> void setProp(String name, Supplier<T> supplier) {
		props.put(name, supplier);
	}

	public <T> void setProp(String name, Function<Props, T> fn) {
		props.put(name, fn);
	}

	public String substitute(String s) {
		int start = s.indexOf("${");
		if (start < 0) return s;
		int end = s.indexOf('}', start + 2);
		if (end < 0) return s;

		StringBuilder sb = new StringBuilder(s.length());
		var value = getStrProp(s.substring(start + 2, end));
		sb.append(s, 0, start);
		if (value != null) sb.append(value);
		else sb.append(s, 0, end + 1);


		for (int i = end + 1, n = s.length(); i < n; ) {
			start = s.indexOf("${", i);
			if (start < 0) {
				sb.append(s, i, s.length());
				break;
			}
			end = s.indexOf('}', start + 2);
			if (end < 0) {
				sb.append(s, i, s.length());
				break;
			}
			value = getStrProp(s.substring(start + 2, end));
			if (value != null) sb.append(s, i, start).append(value);
			else sb.append(s, i, end + 1);
			i = end + 1;
		}
		return sb.toString();
	}
}
