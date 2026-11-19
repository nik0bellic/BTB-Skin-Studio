package de.behindtheblocks.skinstudio;

import de.behindtheblocks.skinstudio.anim.AnimationIO;
import de.behindtheblocks.skinstudio.anim.AnimationPresets;
import de.behindtheblocks.skinstudio.anim.CosmeticAnimation;
import de.behindtheblocks.skinstudio.geo.BakedModel;
import de.behindtheblocks.skinstudio.geo.GeoModel;
import de.behindtheblocks.skinstudio.geo.GeometryIO;
import de.behindtheblocks.skinstudio.molang.Molang;
import de.behindtheblocks.skinstudio.molang.MolangContext;
import de.behindtheblocks.skinstudio.util.FormatException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class CoreModelTest {
	static final String MODERN = """
			{ "format_version": "1.12.0", "minecraft:geometry": [ { "description": { "identifier": "geometry.test", "texture_width": 32, "texture_height": 16 },
			  "bones": [ { "name": "head", "pivot": [0, 24, 0] },
			             { "name": "hat", "parent": "head", "pivot": [0, 32, 0], "rotation": [0, 45, 0],
			               "cubes": [ { "origin": [-2, 32, -2], "size": [4, 3, 4], "uv": [0, 0] },
			                          { "origin": [-1, 35, -1], "size": [2, 2, 2], "uv": { "north": { "uv": [0, 8], "uv_size": [2, 2] } } } ] } ] } ] }
			""";

	@Test
	void readsModernGeometryAndBakes() {
		GeometryIO.Result r = GeometryIO.read(MODERN.getBytes(StandardCharsets.UTF_8));
		GeoModel m = r.first();
		assertEquals("geometry.test", m.identifier);
		assertEquals(2, m.bones.size());
		assertEquals(2, m.cubeCount());
		BakedModel b = BakedModel.bake(m);
		assertEquals(1, b.roots.size());
		BakedModel.Bone hat = b.bone("hat");
		assertSame(b.bone("head"), hat.parent);
		assertEquals(7, b.quadCount, "6 box faces + 1 per-face quad");
		assertEquals(-8f, hat.pivotY, 1e-5, "Java Y = 24 - 32");
		assertEquals(Math.toRadians(45), hat.rotY, 1e-5);
		// first quad (DOWN face of the box): vertices relative to the pivot in blocks
		float y = hat.quads[1];
		assertEquals(-3f / 16f, y, 1e-5, "top face of the 3 px hat lies 3 px above its pivot (Java Y down)");
		assertEquals(-1f, hat.quads[21], 1e-5, "top face normal points up (−Y in Java model space)");
		// UVs are normalized by texture size
		for (int q = 0; q < hat.quadCount(); q++) {
			for (int v = 0; v < 4; v++) {
				float u = hat.quads[q * BakedModel.QUAD_STRIDE + v * BakedModel.VERTEX_STRIDE + 3];
				assertTrue(u >= 0 && u <= 1, "u in range");
			}
		}
	}

	@Test
	void legacyGeometryWithInheritance() {
		String legacy = """
				{ "format_version": "1.8.0",
				  "geometry.base": { "texturewidth": 64, "textureheight": 64, "bones": [ { "name": "head", "pivot": [0,24,0], "cubes": [ { "origin": [-4,24,-4], "size": [8,8,8], "uv": [0,0] } ] } ] },
				  "geometry.child:geometry.base": { "bones": [ { "name": "extra", "parent": "head", "pivot": [0,32,0], "cubes": [ { "origin": [-1,32,-1], "size": [2,2,2], "uv": [32,0] } ] } ] } }
				""";
		GeometryIO.Result r = GeometryIO.read(legacy.getBytes(StandardCharsets.UTF_8));
		GeoModel child = r.find("geometry.child");
		assertNotNull(child);
		assertEquals(2, child.bones.size(), "inherits head from base");
	}

	@Test
	void geometryRoundTrip() {
		GeoModel m = GeometryIO.read(MODERN.getBytes(StandardCharsets.UTF_8)).first();
		GeoModel back = GeometryIO.read(GeometryIO.write(m)).first();
		assertEquals(m.cubeCount(), back.cubeCount());
		assertArrayEquals(m.bones.get(1).rotation, back.bones.get(1).rotation);
		assertNotNull(back.bones.get(1).cubes.get(1).faces);
	}

	@Test
	void rejectsGarbage() {
		assertThrows(FormatException.class, () -> GeometryIO.read("{ \"foo\": 1 }".getBytes()));
		assertThrows(FormatException.class, () -> GeometryIO.read("not json at all {".getBytes()));
	}

	@Test
	void molangBasics() {
		MolangContext ctx = new MolangContext();
		ctx.animTime = 0.25f;
		assertEquals(7, Molang.compile("1 + 2 * 3").eval(ctx), 1e-6);
		assertEquals(1, Molang.compile("math.sin(90)").eval(ctx), 1e-6, "Molang trig uses degrees");
		assertEquals(10, Molang.compile("q.anim_time * 40").eval(ctx), 1e-5);
		assertEquals(3, Molang.compile("query.anim_time > 0.1 ? 3 : 4").eval(ctx), 1e-6);
		assertEquals(2, Molang.compile("math.clamp(5, 0, 2)").eval(ctx), 1e-6);
		assertEquals(-4, Molang.compile("-(2 + 2)").eval(ctx), 1e-6);
		assertEquals(5, Molang.compile("v.x = 1; return 5;").eval(ctx), 1e-6);
		Molang.Expr unknown = Molang.compile("query.something_new * 2");
		assertEquals(0, unknown.eval(ctx));
		assertEquals(1, unknown.unknown().size());
		assertTrue(Molang.compile("3").isConstant());
		assertThrows(FormatException.class, () -> Molang.compile("1 + (2"));
	}

	@Test
	void keyframeAnimation() {
		String json = """
				{ "format_version": "1.8.0", "animations": { "animation.test": { "loop": true, "animation_length": 2,
				  "bones": { "flame": { "rotation": { "0.0": [0, 0, 0], "1.0": [0, 90, 0], "2.0": [0, 0, 0] },
				                         "position": [0, "math.sin(q.anim_time * 180) * 2", 0], "scale": 1.5 } } } } }
				""";
		AnimationIO.Result r = AnimationIO.read(json.getBytes(StandardCharsets.UTF_8));
		CosmeticAnimation a = r.animations().get("animation.test");
		assertEquals(CosmeticAnimation.Loop.LOOP, a.loop);
		MolangContext ctx = new MolangContext();
		float[] out = new float[3];
		ctx.animTime = a.localTime(2.5f);
		assertEquals(0.5f, ctx.animTime, 1e-5, "loops after 2 s");
		a.bones.get("flame").rotation.sample(ctx.animTime, ctx, out);
		assertEquals(45, out[1], 1e-4);
		a.bones.get("flame").position.sample(ctx.animTime, ctx, out);
		assertEquals(2, out[1], 1e-4);
		a.bones.get("flame").scale.sample(0, ctx, out);
		assertEquals(1.5f, out[2], 1e-6);
		// round trip through the writer keeps keyframes
		CosmeticAnimation back = AnimationIO.read(AnimationIO.write(java.util.Map.of(a.name, a))).animations().get(a.name);
		back.bones.get("flame").rotation.sample(0.5f, ctx, out);
		assertEquals(45, out[1], 1e-4);
	}

	@Test
	void presetsProduceWorkingMolang() {
		CosmeticAnimation bob = AnimationPresets.create(AnimationPresets.Preset.BOB, "root", null, 1, 2);
		MolangContext ctx = new MolangContext();
		ctx.animTime = 0.25f;
		float[] out = new float[3];
		bob.bones.get("root").position.sample(0, ctx, out);
		assertEquals(2, out[1], 1e-4);
		assertEquals(AnimationPresets.Preset.BOB, AnimationPresets.presetOf(bob.name));
		CosmeticAnimation flap = AnimationPresets.create(AnimationPresets.Preset.FLAP, "left_wing", "right_wing", 1, 20);
		assertEquals(2, flap.bones.size());
	}
}
