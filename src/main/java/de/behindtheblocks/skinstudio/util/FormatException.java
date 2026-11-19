package de.behindtheblocks.skinstudio.util;

/** A user-facing error in a cosmetic, model, animation or pack file. The message is shown in the UI. */
public class FormatException extends RuntimeException {
	public FormatException(String message) {
		super(message);
	}

	public FormatException(String message, Throwable cause) {
		super(message, cause);
	}
}
