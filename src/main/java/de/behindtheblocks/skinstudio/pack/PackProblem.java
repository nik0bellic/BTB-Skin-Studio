package de.behindtheblocks.skinstudio.pack;

/** A problem found while reading a pack or cosmetic; shown in the import preview and the log. */
public record PackProblem(Severity severity, String where, String message) {
	public enum Severity {
		/** The pack or cosmetic cannot be used. */
		ERROR,
		/** Loaded, but something was skipped or approximated. */
		WARNING,
		/** Informational, e.g. "protected Marketplace content, cannot be imported". */
		INFO
	}

	public static PackProblem error(String where, String message) {
		return new PackProblem(Severity.ERROR, where, message);
	}

	public static PackProblem warning(String where, String message) {
		return new PackProblem(Severity.WARNING, where, message);
	}

	public static PackProblem info(String where, String message) {
		return new PackProblem(Severity.INFO, where, message);
	}

	@Override
	public String toString() {
		return (where == null || where.isEmpty() ? "" : where + ": ") + message;
	}
}
