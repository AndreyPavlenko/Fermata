package me.aap.utils.net;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import static me.aap.utils.io.IoUtils.emptyByteBufferArray;

/**
 * @author Andrey Pavlenko
 */
public interface ByteBufferArraySupplier {

	ByteBuffer[] getByteBufferArray();

	default ByteBufferArraySupplier retainByteBufferArray(ByteBuffer[] bb) {
		return retainByteBufferArray(bb, 0);
	}

	default ByteBufferArraySupplier retainByteBufferArray(ByteBuffer[] bb, int fromIndex) {
		assert bb.length > 0;
		assert (fromIndex >= 0) && (fromIndex < bb.length);
		assert bb[fromIndex].hasRemaining();

		return new ByteBufferArraySupplier() {
			ByteBuffer[] array = (fromIndex == 0) ? bb : Arrays.copyOfRange(bb, fromIndex, bb.length);

			@Override
			public ByteBuffer[] getByteBufferArray() {
				assert array != null;
				return array;
			}

			@Override
			public ByteBufferArraySupplier retainByteBufferArray(ByteBuffer[] bb, int fromIndex) {
				assert array != null;
				assert bb == array;
				array = (fromIndex == 0) ? bb : Arrays.copyOfRange(bb, fromIndex, bb.length);
				assert bb[fromIndex].hasRemaining();
				return this;
			}

			@Override
			public void releaseByteBufferArray(ByteBuffer[] bb, int toIndex) {
				assert array != null;
				assert bb == array;
				assert (toIndex > 0) && (toIndex <= bb.length);
				ByteBufferArraySupplier.this.releaseByteBufferArray(bb, toIndex);
			}

			@Override
			public void release() {
				array = null;
				ByteBufferArraySupplier.this.release();
			}
		};
	}

	default void releaseByteBufferArray(ByteBuffer[] bb) {
		releaseByteBufferArray(bb, bb.length);
	}

	default void releaseByteBufferArray(ByteBuffer[] bb, int toIndex) {
		assert (toIndex > 0) && (toIndex <= bb.length);

		for (int i = 0; i < toIndex; i++) {
			bb[i] = null;
		}
	}

	default void release() {
	}

	static ByteBufferArraySupplier wrap(ByteBufferSupplier... s) {
		return new ByteBufferArraySupplier() {
			private ByteBufferSupplier[] suppliers = s;

			@Override
			public ByteBuffer[] getByteBufferArray() {
				ByteBuffer[] a = new ByteBuffer[suppliers.length];

				for (int i = 0; i < suppliers.length; i++) {
					a[i] = suppliers[i].getByteBuffer();
				}

				assert a.length > 0;
				assert a[0].hasRemaining();
				return a;
			}

			@Override
			public ByteBufferArraySupplier retainByteBufferArray(ByteBuffer[] bb, int fromIndex) {
				assert bb.length > 0;
				assert bb[fromIndex].hasRemaining();

				for (int i = fromIndex; i < suppliers.length; i++) {
					suppliers[i] = suppliers[i].retainByteBuffer(bb[i]);
				}

				if (fromIndex != 0) {
					for (int i = 0; i < fromIndex; i++) {
						suppliers[i].release();
					}

					suppliers = Arrays.copyOfRange(suppliers, fromIndex, suppliers.length);
				}

				return this;
			}

			@Override
			public void releaseByteBufferArray(ByteBuffer[] bb, int toIndex) {
				assert (toIndex > 0) && (toIndex <= bb.length);

				for (int i = 0; i < toIndex; i++) {
					suppliers[i].releaseByteBuffer(bb[i]);
				}
			}

			@Override
			public void release() {
				ByteBufferSupplier[] suppliers = this.suppliers;

				if (suppliers != null) {
					this.suppliers = null;

					for (ByteBufferSupplier supplier : suppliers) {
						supplier.release();
					}
				}
			}
		};
	}

	static ByteBufferArraySupplier wrap(ByteBufferArraySupplier... s) {
		class SupplierWrapper {
			ByteBufferArraySupplier s;
			ByteBuffer[] a;

			public SupplierWrapper(ByteBufferArraySupplier s) {
				this.s = s;
			}
		}

		return new ByteBufferArraySupplier() {
			private SupplierWrapper[] wrappers = new SupplierWrapper[s.length];

			{
				for (int i = 0; i < s.length; i++) {
					wrappers[i] = new SupplierWrapper(s[i]);
				}
			}

			@Override
			public ByteBuffer[] getByteBufferArray() {
				if (wrappers.length == 1) {
					return wrappers[0].a = wrappers[0].s.getByteBufferArray();
				}

				ArrayList<ByteBuffer> l = new ArrayList<>(wrappers.length * 2);

				for (SupplierWrapper w : wrappers) {
					w.a = w.s.getByteBufferArray();
					l.ensureCapacity(w.a.length);
					Collections.addAll(l, w.a);
				}

				assert l.size() > 0;
				assert l.get(0).hasRemaining();
				return l.toArray(emptyByteBufferArray());
			}

			@Override
			public ByteBufferArraySupplier retainByteBufferArray(ByteBuffer[] bb, int fromIndex) {
				assert bb.length > 0;
				assert bb[fromIndex].hasRemaining();

				int released = 0;

				for (int i = 0, off = 0; i < wrappers.length; i++) {
					int l = wrappers[i].a.length;
					int from = fromIndex - off;

					if (from >= l) {
						released++;
						wrappers[i].s.release();
					} else {
						wrappers[i].s = wrappers[i].s.retainByteBufferArray(wrappers[i].a, Math.max(from, 0));
					}

					off += l;
				}

				if (released != 0) wrappers = Arrays.copyOfRange(wrappers, released, wrappers.length);
				return this;
			}

			@Override
			public void releaseByteBufferArray(ByteBuffer[] bb, int toIndex) {
				for (int i = 0, off = 0; i < wrappers.length; i++) {
					if (off < toIndex) {
						int l = wrappers[i].a.length;
						wrappers[i].s.releaseByteBufferArray(wrappers[i].a, Math.min(l, toIndex - off));
						off += l;
					} else {
						break;
					}
				}
			}

			@Override
			public void release() {
				SupplierWrapper[] wrappers = this.wrappers;

				if (wrappers != null) {
					this.wrappers = null;

					for (SupplierWrapper w : wrappers) {
						w.s.release();
					}
				}
			}
		};
	}
}
