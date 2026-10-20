package javax.security.auth;

public interface Destroyable {
	default boolean isDestroyed() {
		return false;
	}

	default void destroy() throws DestroyFailedException {
		throw new DestroyFailedException("Not supported on web");
	}
}
