package me.aap.utils.net;

import java.nio.ByteBuffer;

/**
 * @author Andrey Pavlenko
 */
public interface ByteBufferSupplier {

	ByteBuffer getByteBuffer();

	default ByteBufferSupplier retainByteBuffer(ByteBuffer bb) {
		return new ByteBufferSupplier() {
			ByteBuffer buf = bb;

			@Override
			public ByteBuffer getByteBuffer() {
				assert buf != null;
				return buf;
			}

			@Override
			public ByteBufferSupplier retainByteBuffer(ByteBuffer bb) {
				assert buf != null;
				assert bb == buf;
				assert bb.hasRemaining();
				return this;
			}

			@Override
			public void releaseByteBuffer(ByteBuffer bb) {
				assert buf != null;
				assert bb == buf;
				ByteBufferSupplier.this.releaseByteBuffer(bb);
			}

			@Override
			public void release() {
				assert buf != null;
				buf = null;
				ByteBufferSupplier.this.release();
			}
		};
	}

	default void releaseByteBuffer(ByteBuffer bb) {
	}

	default void release() {
	}

	default ByteBufferArraySupplier asArray() {
		return new ByteBufferArraySupplier() {
			ByteBufferSupplier supplier = ByteBufferSupplier.this;

			@Override
			public ByteBuffer[] getByteBufferArray() {
				return new ByteBuffer[]{supplier.getByteBuffer()};
			}

			@Override
			public ByteBufferArraySupplier retainByteBufferArray(ByteBuffer[] bb, int fromIndex) {
				assert supplier != null;
				assert fromIndex == 0;
				assert bb[fromIndex].hasRemaining();
				supplier = supplier.retainByteBuffer(bb[0]);
				return this;
			}

			@Override
			public void releaseByteBufferArray(ByteBuffer[] bb, int toIndex) {
				assert supplier != null;
				assert toIndex == 1;
				assert bb.length == 1;
				supplier.releaseByteBuffer(bb[0]);
			}

			@Override
			public void release() {
				supplier.release();
				supplier = null;
			}
		};
	}
}
