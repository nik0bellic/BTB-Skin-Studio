package de.behindtheblocks.skinstudio.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Last line of defence for code that runs inside the game's render and tick loops: an unexpected error in
 * BTB Skin Studio is logged (once per place) and that one element is skipped – the game keeps running.
 */
public final class Guard {
	private static final Logger LOG = LoggerFactory.getLogger("BTB Skin Studio");
	private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

	private Guard() {
	}

	public static void report(String where, Throwable t) {
		if (REPORTED.add(where + ":" + t.getClass().getName())) {
			LOG.error("Fehler in {} – wird übersprungen, das Spiel läuft weiter. Bitte melden.", where, t);
		}
	}

	public static void run(String where, Runnable r) {
		try {
			r.run();
		} catch (RuntimeException | LinkageError | StackOverflowError e) {
			report(where, e);
		}
	}
}
