package de.behindtheblocks.skinstudio.anim;

import de.behindtheblocks.skinstudio.util.Tr;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import de.behindtheblocks.skinstudio.molang.Molang;
import de.behindtheblocks.skinstudio.util.FormatException;
import de.behindtheblocks.skinstudio.util.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Reads Bedrock animation files ({@code "animations": { "animation.x": { … } }}, format 1.8.0+). */
public final class AnimationIO {
	private AnimationIO() {
	}

	public record Result(Map<String, CosmeticAnimation> animations, List<String> warnings) {
	}

	public static Result read(byte[] bytes) {
		return read(Json.parseObject(bytes));
	}

	public static Result read(JsonObject root) {
		Map<String, CosmeticAnimation> out = new LinkedHashMap<>();
		List<String> warnings = new ArrayList<>();
		JsonElement anims = root.get("animations");
		if (anims == null || !anims.isJsonObject()) {
			throw new FormatException(Tr.t("btb.t.keine_animations_gefunden"));
		}
		for (Map.Entry<String, JsonElement> e : anims.getAsJsonObject().entrySet()) {
			if (!e.getValue().isJsonObject()) {
				continue;
			}
			try {
				out.put(e.getKey(), readAnimation(e.getKey(), e.getValue().getAsJsonObject(), warnings));
			} catch (FormatException ex) {
				warnings.add(Tr.t("btb.t.animation_2") + e.getKey() + Tr.t("btb.t.uebersprungen") + ex.getMessage());
			}
		}
		return new Result(out, warnings);
	}

	private static CosmeticAnimation readAnimation(String name, JsonObject o, List<String> warnings) {
		CosmeticAnimation a = new CosmeticAnimation(name);
		JsonElement loop = o.get("loop");
		if (loop != null && loop.isJsonPrimitive()) {
			String l = loop.getAsString().toLowerCase(Locale.ROOT);
			a.loop = l.equals("true") ? CosmeticAnimation.Loop.LOOP : l.equals("hold_on_last_frame") ? CosmeticAnimation.Loop.HOLD : CosmeticAnimation.Loop.ONCE;
		} else {
			a.loop = CosmeticAnimation.Loop.ONCE;
		}
		a.length = Json.number(o, "animation_length", 0);
		JsonElement bones = o.get("bones");
		if (bones != null && bones.isJsonObject()) {
			for (Map.Entry<String, JsonElement> b : bones.getAsJsonObject().entrySet()) {
				if (!b.getValue().isJsonObject()) {
					continue;
				}
				JsonObject bo = b.getValue().getAsJsonObject();
				CosmeticAnimation.BoneTrack t = new CosmeticAnimation.BoneTrack();
				t.rotation = channel(bo.get("rotation"), 0, warnings, name);
				t.position = channel(bo.get("position"), 0, warnings, name);
				t.scale = channel(bo.get("scale"), 1, warnings, name);
				a.bones.put(b.getKey(), t);
			}
		}
		if (o.has("particle_effects") || o.has("sound_effects")) {
			warnings.add(Tr.t("btb.t.animation_2") + name + Tr.t("btb.t.partikel_sound_keyframes_werden_ignorier"));
		}
		return a;
	}

	private static CosmeticAnimation.Channel channel(JsonElement e, float def, List<String> warnings, String anim) {
		if (e == null || e.isJsonNull()) {
			return null;
		}
		if (e.isJsonPrimitive() || e.isJsonArray()) {
			return CosmeticAnimation.Channel.constant(vector(e, def, warnings, anim));
		}
		JsonObject o = e.getAsJsonObject();
		List<CosmeticAnimation.Keyframe> keys = new ArrayList<>();
		for (Map.Entry<String, JsonElement> k : o.entrySet()) {
			float time;
			try {
				time = Float.parseFloat(k.getKey());
			} catch (NumberFormatException ex) {
				warnings.add(Tr.t("btb.t.animation_2") + anim + Tr.t("btb.t.ungueltige_keyframe_zeit") + k.getKey() + "'");
				continue;
			}
			JsonElement v = k.getValue();
			if (v.isJsonObject()) {
				JsonObject ko = v.getAsJsonObject();
				JsonElement post = ko.has("post") ? ko.get("post") : ko.get("pre");
				JsonElement pre = ko.has("pre") ? ko.get("pre") : post;
				if (pre == null) {
					continue;
				}
				String lerp = Json.string(ko, "lerp_mode", "linear");
				keys.add(new CosmeticAnimation.Keyframe(time, vector(pre, def, warnings, anim), vector(post, def, warnings, anim),
						lerp.equalsIgnoreCase("catmullrom") ? CosmeticAnimation.Lerp.CATMULLROM : lerp.equalsIgnoreCase("step") ? CosmeticAnimation.Lerp.STEP : CosmeticAnimation.Lerp.LINEAR));
			} else {
				Molang.Expr[] vec = vector(v, def, warnings, anim);
				keys.add(new CosmeticAnimation.Keyframe(time, vec, vec, CosmeticAnimation.Lerp.LINEAR));
			}
		}
		return CosmeticAnimation.Channel.keyframes(keys);
	}

	private static Molang.Expr[] vector(JsonElement e, float def, List<String> warnings, String anim) {
		Molang.Expr[] out = new Molang.Expr[3];
		if (e.isJsonArray()) {
			JsonArray a = e.getAsJsonArray();
			for (int i = 0; i < 3; i++) {
				out[i] = i < a.size() ? expr(a.get(i), warnings, anim) : Molang.constant(def);
			}
		} else {
			Molang.Expr x = expr(e, warnings, anim);
			out[0] = out[1] = out[2] = x;
		}
		return out;
	}

	private static Molang.Expr expr(JsonElement e, List<String> warnings, String anim) {
		if (e.isJsonPrimitive()) {
			JsonPrimitive p = e.getAsJsonPrimitive();
			if (p.isNumber()) {
				return Molang.constant(p.getAsFloat());
			}
			String s = p.getAsString().trim();
			if (s.isEmpty()) {
				return Molang.constant(0);
			}
			Molang.Expr x = Molang.compile(s);
			for (String u : x.unknown()) {
				String msg = Tr.t("btb.t.animation_2") + anim + Tr.t("btb.t.molang") + u + Tr.t("btb.t.wird_nicht_unterstuetzt_als_0_gewertet");
				if (!warnings.contains(msg)) {
					warnings.add(msg);
				}
			}
			return x;
		}
		return Molang.constant(0);
	}

	// ---------------------------------------------------------------- writing (simple presets only)

	/** Writes animations whose channels are constant Molang vectors or simple keyframes (what the editor produces). */
	public static JsonObject write(Map<String, CosmeticAnimation> animations) {
		JsonObject root = new JsonObject();
		root.addProperty("format_version", "1.8.0");
		JsonObject anims = new JsonObject();
		for (CosmeticAnimation a : animations.values()) {
			JsonObject ao = new JsonObject();
			ao.add("loop", a.loop == CosmeticAnimation.Loop.LOOP ? new JsonPrimitive(true) : a.loop == CosmeticAnimation.Loop.HOLD ? new JsonPrimitive("hold_on_last_frame") : new JsonPrimitive(false));
			if (a.length > 0) {
				ao.addProperty("animation_length", Json.round(a.length));
			}
			JsonObject bones = new JsonObject();
			for (Map.Entry<String, CosmeticAnimation.BoneTrack> b : a.bones.entrySet()) {
				JsonObject bo = new JsonObject();
				put(bo, "rotation", b.getValue().rotation);
				put(bo, "position", b.getValue().position);
				put(bo, "scale", b.getValue().scale);
				bones.add(b.getKey(), bo);
			}
			ao.add("bones", bones);
			anims.add(a.name, ao);
		}
		root.add("animations", anims);
		return root;
	}

	private static void put(JsonObject bo, String key, CosmeticAnimation.Channel c) {
		if (c == null) {
			return;
		}
		if (c.constant != null) {
			bo.add(key, vec(c.constant));
			return;
		}
		JsonObject keys = new JsonObject();
		for (CosmeticAnimation.Keyframe k : c.keys) {
			String t = String.valueOf(Json.round(k.time()));
			if (!t.contains(".")) {
				t = t + ".0";
			}
			if (k.lerp() == CosmeticAnimation.Lerp.LINEAR && k.pre() == k.post()) {
				keys.add(t, vec(k.post()));
			} else {
				JsonObject ko = new JsonObject();
				ko.add("pre", vec(k.pre()));
				ko.add("post", vec(k.post()));
				if (k.lerp() != CosmeticAnimation.Lerp.LINEAR) {
					ko.addProperty("lerp_mode", k.lerp().name().toLowerCase(Locale.ROOT));
				}
				keys.add(t, ko);
			}
		}
		bo.add(key, keys);
	}

	private static JsonArray vec(Molang.Expr[] v) {
		JsonArray a = new JsonArray();
		for (Molang.Expr e : v) {
			if (e.isConstant()) {
				a.add(Json.round(e.eval(null)));
			} else {
				a.add(e.source());
			}
		}
		return a;
	}
}
