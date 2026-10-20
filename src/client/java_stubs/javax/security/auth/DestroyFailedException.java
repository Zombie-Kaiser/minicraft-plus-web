package javax.security.auth;

public class DestroyFailedException extends Exception {
	private static final long serialVersionUID = 1L;

	public DestroyFailedException() {
		super();
	}

	public DestroyFailedException(String message) {
		super(message);
	}
}
