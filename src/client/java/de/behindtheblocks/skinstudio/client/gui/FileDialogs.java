package de.behindtheblocks.skinstudio.client.gui;

import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.SDLDialog;
import org.lwjgl.sdl.SDL_DialogFileCallback;
import org.lwjgl.sdl.SDL_DialogFileFilter;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Native "open file" dialog via SDL3 (bundled with Minecraft 26.x). The result is delivered on the
 * client thread. If the platform has no dialog, the callback receives an empty list and the UI
 * falls back to drag &amp; drop and the import folder.
 */
public final class FileDialogs {
	private static final Logger LOG = LoggerFactory.getLogger("BTB Skin Studio/Dialog");
	private static SDL_DialogFileCallback pending;
	private static SDL_DialogFileFilter.Buffer filters;
	private static java.nio.ByteBuffer filterName_;
	private static java.nio.ByteBuffer filterPattern;

	private static void freeFilters() {
		if (filters != null) {
			filters.free();
			MemoryUtil.memFree(filterName_);
			MemoryUtil.memFree(filterPattern);
			filters = null;
		}
	}

	private FileDialogs() {
	}

	public static void openFiles(String filterName, String patterns, boolean folder, Consumer<List<Path>> result) {
		Minecraft mc = Minecraft.getInstance();
		if (pending != null) {
			pending.free();
		}
		pending = SDL_DialogFileCallback.create((userdata, filelist, filter) -> {
			List<Path> out = new ArrayList<>();
			if (filelist != 0) {
				for (int i = 0; ; i++) {
					long p = MemoryUtil.memGetAddress(filelist + (long) i * org.lwjgl.system.Pointer.POINTER_SIZE);
					if (p == 0) {
						break;
					}
					String s = MemoryUtil.memUTF8(p);
					out.add(Path.of(s));
				}
			} else {
				LOG.info("Dateidialog nicht verfügbar: {}", org.lwjgl.sdl.SDLError.SDL_GetError());
			}
			mc.execute(() -> result.accept(out));
		});
		long window = mc.getWindow().handle();
		try {
			if (folder) {
				SDLDialog.SDL_ShowOpenFolderDialog(pending, 0L, window, (CharSequence) null, false);
			} else {
				// SDL keeps reading the filter memory until the callback fires, so it must not live on the stack
				freeFilters();
				filterName_ = MemoryUtil.memUTF8(filterName);
				filterPattern = MemoryUtil.memUTF8(patterns);
				filters = SDL_DialogFileFilter.malloc(1);
				filters.get(0).name(filterName_).pattern(filterPattern);
				SDLDialog.SDL_ShowOpenFileDialog(pending, 0L, window, filters, (CharSequence) null, true);
			}
		} catch (Throwable t) {
			LOG.warn("Dateidialog konnte nicht geöffnet werden: {}", t.toString());
			result.accept(List.of());
		}
	}
}
