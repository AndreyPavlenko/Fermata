package me.aap.utils.function;

/**
 * @author Andrey Pavlenko
 */
public interface LongConsumer {
	void accept(long value);

	default java.util.function.LongConsumer andThen(LongConsumer after) {
		return (long t) -> {
			accept(t);
			after.accept(t);
		};
	}
}
