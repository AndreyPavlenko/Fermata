package me.aap.utils.resource;

import androidx.annotation.NonNull;

import me.aap.utils.text.SharedTextBuilder;
import me.aap.utils.text.TextUtils;

/**
 * @author Andrey Pavlenko
 */
class GenericRid implements Rid {
	final String str;
	final int port;
	final int sEnd;
	final int uStart;
	final int uEnd;
	final int hStart;
	final int hEnd;
	final int pStart;
	String scheme;
	String authority;
	String userInfo;
	String host;
	String path;

	private GenericRid(String str, int port, int sEnd, int uStart, int uEnd, int hStart, int hEnd, int pStart) {
		this.str = str;
		this.sEnd = sEnd;
		this.uStart = uStart;
		this.uEnd = uEnd;
		this.hStart = hStart;
		this.hEnd = hEnd;
		this.pStart = pStart;
		this.port = port;
	}

	static GenericRid create(CharSequence scheme, CharSequence userInfo, CharSequence host, int port,
													 CharSequence path, CharSequence query, CharSequence fragment) {
		String str;
		int sEnd = -1;
		int uStart = -1;
		int uEnd = -1;
		int hStart = -1;
		int hEnd = -1;
		int pStart = -1;
		int pEnd = -1;
		int qStart = -1;
		int qEnd = -1;
		int fStart = -1;

		try (SharedTextBuilder tb = SharedTextBuilder.get()) {
			if (scheme != null) {
				tb.append(scheme);
				sEnd = tb.length();
				tb.append("://");
			}
			if (userInfo != null) {
				uStart = tb.length();
				tb.append(userInfo);
				uEnd = tb.length();
				tb.append('@');
			}
			if (host != null) {
				if (TextUtils.indexOf(host, ':') == -1) {
					hStart = tb.length();
					tb.append(host);
					hEnd = tb.length();
				} else {
					tb.append('[');
					hStart = tb.length();
					tb.append(host);
					hEnd = tb.length();
					tb.append(']');
				}
			}
			if (port != -1) tb.append(':').append(port);
			if (path != null) {
				pStart = tb.length();
				tb.append(path);
				pEnd = tb.length();
			}
			if (query != null) {
				tb.append('?');
				qStart = tb.length();
				tb.append(query);
				qEnd = tb.length();
			}
			if (fragment != null) {
				tb.append('#');
				fStart = tb.length();
				tb.append(fragment);
			}

			str = tb.toString();
		}

		if (fragment != null) {
			return new FragmentRid(str, port, sEnd, uStart, uEnd, hStart, hEnd, pStart, pEnd, qStart, qEnd, fStart);
		} else if (query != null) {
			return new QueryRid(str, port, sEnd, uStart, uEnd, hStart, hEnd, pStart, pEnd, qStart);
		} else {
			return new GenericRid(str, port, sEnd, uStart, uEnd, hStart, hEnd, pStart);
		}
	}

	@Override
	public String getScheme() {
		if ((scheme == null) && (sEnd != -1)) {
			scheme = str.substring(0, sEnd);
		}
		return scheme;
	}

	@Override
	public String getUserInfo() {
		if ((userInfo == null) && (uStart != -1)) {
			userInfo = str.substring(uStart, uEnd);
		}
		return userInfo;
	}

	@Override
	public String getHost() {
		if ((host == null) && (hStart != -1)) {
			host = str.substring(hStart, hEnd);
		}
		return host;
	}

	@Override
	public int getPort() {
		return port;
	}

	@Override
	public String getPath() {
		if ((path == null) && (pStart != -1)) {
			path = str.substring(pStart);
		}
		return path;
	}

	@Override
	public String getAuthority() {
		if (authority == null) {
			if (uStart != -1) {
				authority = (pStart != -1) ? str.substring(uStart, pStart) : str.substring(uStart);
			} else if (hStart != -1) {
				authority = (pStart != -1) ? str.substring(hStart, pStart) : str.substring(hStart);
			}
		}
		return authority;
	}

	@Override
	public String getQuery() {
		return null;
	}

	@Override
	public String getFragment() {
		return null;
	}

	@NonNull
	@Override
	public String toString() {
		return str;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof Rid)) return false;
		return str.equals(o.toString());
	}

	@Override
	public int hashCode() {
		return str.hashCode();
	}

	private static class QueryRid extends GenericRid {
		private final int pEnd;
		final int qStart;
		private String query;

		QueryRid(String str, int port, int sEnd, int uStart, int uEnd, int hStart, int hEnd,
						 int pStart, int pEnd, int qStart) {
			super(str, port, sEnd, uStart, uEnd, hStart, hEnd, pStart);
			this.pEnd = pEnd;
			this.qStart = qStart;
		}

		@Override
		public String getPath() {
			if ((path == null) && (pStart != -1)) {
				path = str.substring(pStart, pEnd);
			}
			return path;
		}

		@Override
		public String getAuthority() {
			if (authority == null) {
				if (uStart != -1) {
					authority = (pStart != -1) ? str.substring(uStart, pStart) : str.substring(uStart, qStart - 1);
				} else if (hStart != -1) {
					authority = (pStart != -1) ? str.substring(hStart, pStart) : str.substring(hStart, qStart - 1);
				}
			}
			return authority;
		}

		@Override
		public String getQuery() {
			if (query == null) {
				query = toString().substring(qStart);
			}
			return query;
		}
	}

	private static final class FragmentRid extends QueryRid {
		private final int qEnd;
		private final int fStart;
		private String query;
		private String fragment;

		public FragmentRid(String str, int port, int sEnd, int uStart, int uEnd, int hStart,
											 int hEnd, int pStart, int pEnd, int qStart, int qEnd, int fStart) {
			super(str, port, sEnd, uStart, uEnd, hStart, hEnd, pStart, pEnd, qStart);
			this.qEnd = qEnd;
			this.fStart = fStart;
		}

		@Override
		public String getAuthority() {
			if (authority == null) {
				if (uStart != -1) {
					authority = (pStart != -1) ? str.substring(uStart, pStart)
							: (qStart != -1) ? str.substring(uStart, qStart - 1)
							: str.substring(uStart, fStart - 1);
				} else if (hStart != -1) {
					authority = (pStart != -1) ? str.substring(hStart, pStart)
							: (qStart != -1) ? str.substring(hStart, qStart - 1)
							: str.substring(hStart, fStart - 1);
				}
			}
			return authority;
		}

		@Override
		public String getQuery() {
			if ((query == null) && (qStart != -1)) {
				query = toString().substring(qStart, qEnd);
			}
			return query;
		}

		@Override
		public String getFragment() {
			if (fragment == null) {
				fragment = toString().substring(fStart);
			}
			return fragment;
		}
	}
}