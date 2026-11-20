package de.behindtheblocks.skinstudio.molang;

import de.behindtheblocks.skinstudio.util.Tr;

import de.behindtheblocks.skinstudio.util.FormatException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A compact Molang subset, enough for cosmetic animations from Blockbench and Bedrock packs:
 * numbers, + − * /, unary minus, parentheses, comparisons, {@code && || !}, {@code ?:},
 * {@code math.*} (sin/cos take degrees like Bedrock), {@code query.anim_time}, {@code query.life_time},
 * {@code query.modified_move_speed}, {@code query.ground_speed}, {@code query.is_sneaking},
 * {@code query.head_x_rotation}, {@code query.head_y_rotation} and {@code variable.*}/{@code temp.*} with assignment.
 * Unknown queries evaluate to 0 and are listed in {@link Expr#unknown()}.
 */
public final class Molang {
	private Molang() {
	}

	@FunctionalInterface
	public interface Node {
		float eval(MolangContext ctx);
	}

	public record Expr(String source, Node node, List<String> unknown) {
		public float eval(MolangContext ctx) {
			try {
				float v = node.eval(ctx);
				return Float.isFinite(v) ? v : 0;
			} catch (RuntimeException e) {
				return 0;
			}
		}

		public boolean isConstant() {
			return node instanceof Const;
		}
	}

	private record Const(float value) implements Node {
		@Override
		public float eval(MolangContext ctx) {
			return value;
		}
	}

	public static Expr constant(float v) {
		return new Expr(Float.toString(v), new Const(v), List.of());
	}

	public static Expr compile(String source) {
		Parser p = new Parser(source);
		Node n = p.parseStatements();
		return new Expr(source, n, p.unknown);
	}

	private static final class Parser {
		private final String s;
		private int pos;
		final List<String> unknown = new ArrayList<>();

		Parser(String s) {
			this.s = s.toLowerCase(Locale.ROOT);
		}

		Node parseStatements() {
			// "a; return b;" → value of the return (or the last statement)
			Node last = null;
			while (true) {
				skip();
				if (pos >= s.length()) {
					break;
				}
				if (s.startsWith("return", pos)) {
					pos += 6;
				}
				Node n = parseTernary();
				last = n;
				skip();
				if (pos < s.length() && s.charAt(pos) == ';') {
					pos++;
					continue;
				}
				if (pos < s.length()) {
					throw error(Tr.t("btb.t.unerwartetes_zeichen") + s.charAt(pos) + "'");
				}
			}
			if (last == null) {
				return new Const(0);
			}
			return fold(last);
		}

		private Node fold(Node n) {
			if (n instanceof Const) {
				return n;
			}
			return n;
		}

		Node parseTernary() {
			Node cond = parseOr();
			skip();
			if (peek('?')) {
				pos++;
				Node a = parseTernary();
				skip();
				expect(':');
				Node b = parseTernary();
				return ctx -> cond.eval(ctx) != 0 ? a.eval(ctx) : b.eval(ctx);
			}
			return cond;
		}

		Node parseOr() {
			Node l = parseAnd();
			while (true) {
				skip();
				if (s.startsWith("||", pos)) {
					pos += 2;
					Node a = l, b = parseAnd();
					l = ctx -> (a.eval(ctx) != 0 || b.eval(ctx) != 0) ? 1 : 0;
				} else {
					return l;
				}
			}
		}

		Node parseAnd() {
			Node l = parseCompare();
			while (true) {
				skip();
				if (s.startsWith("&&", pos)) {
					pos += 2;
					Node a = l, b = parseCompare();
					l = ctx -> (a.eval(ctx) != 0 && b.eval(ctx) != 0) ? 1 : 0;
				} else {
					return l;
				}
			}
		}

		Node parseCompare() {
			Node l = parseAdd();
			while (true) {
				skip();
				String op = null;
				for (String o : new String[]{"<=", ">=", "==", "!=", "<", ">"}) {
					if (s.startsWith(o, pos)) {
						op = o;
						break;
					}
				}
				if (op == null) {
					return l;
				}
				pos += op.length();
				Node a = l, b = parseAdd();
				l = switch (op) {
					case "<=" -> ctx -> a.eval(ctx) <= b.eval(ctx) ? 1 : 0;
					case ">=" -> ctx -> a.eval(ctx) >= b.eval(ctx) ? 1 : 0;
					case "==" -> ctx -> a.eval(ctx) == b.eval(ctx) ? 1 : 0;
					case "!=" -> ctx -> a.eval(ctx) != b.eval(ctx) ? 1 : 0;
					case "<" -> ctx -> a.eval(ctx) < b.eval(ctx) ? 1 : 0;
					default -> ctx -> a.eval(ctx) > b.eval(ctx) ? 1 : 0;
				};
			}
		}

		Node parseAdd() {
			Node l = parseMul();
			while (true) {
				skip();
				if (peek('+')) {
					pos++;
					Node a = l, b = parseMul();
					l = constFold(a, b, '+');
				} else if (peek('-')) {
					pos++;
					Node a = l, b = parseMul();
					l = constFold(a, b, '-');
				} else {
					return l;
				}
			}
		}

		Node parseMul() {
			Node l = parseUnary();
			while (true) {
				skip();
				if (peek('*')) {
					pos++;
					Node a = l, b = parseUnary();
					l = constFold(a, b, '*');
				} else if (peek('/')) {
					pos++;
					Node a = l, b = parseUnary();
					l = constFold(a, b, '/');
				} else {
					return l;
				}
			}
		}

		private static Node constFold(Node a, Node b, char op) {
			Node n = switch (op) {
				case '+' -> ctx -> a.eval(ctx) + b.eval(ctx);
				case '-' -> ctx -> a.eval(ctx) - b.eval(ctx);
				case '*' -> ctx -> a.eval(ctx) * b.eval(ctx);
				default -> ctx -> {
					float d = b.eval(ctx);
					return d == 0 ? 0 : a.eval(ctx) / d;
				};
			};
			if (a instanceof Const && b instanceof Const) {
				return new Const(n.eval(null));
			}
			return n;
		}

		Node parseUnary() {
			skip();
			if (peek('-')) {
				pos++;
				Node n = parseUnary();
				if (n instanceof Const c) {
					return new Const(-c.value());
				}
				return ctx -> -n.eval(ctx);
			}
			if (peek('!')) {
				pos++;
				Node n = parseUnary();
				return ctx -> n.eval(ctx) == 0 ? 1 : 0;
			}
			if (peek('+')) {
				pos++;
				return parseUnary();
			}
			return parsePrimary();
		}

		Node parsePrimary() {
			skip();
			if (pos >= s.length()) {
				throw error(Tr.t("btb.t.ausdruck_endet_unerwartet"));
			}
			char c = s.charAt(pos);
			if (c == '(') {
				pos++;
				Node n = parseTernary();
				skip();
				expect(')');
				return n;
			}
			if (Character.isDigit(c) || c == '.') {
				int start = pos;
				while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) {
					pos++;
				}
				if (pos < s.length() && s.charAt(pos) == 'f') {
					pos++;
					return new Const(Float.parseFloat(s.substring(start, pos - 1)));
				}
				return new Const(Float.parseFloat(s.substring(start, pos)));
			}
			if (Character.isLetter(c) || c == '_') {
				int start = pos;
				while (pos < s.length() && (Character.isLetterOrDigit(s.charAt(pos)) || s.charAt(pos) == '_' || s.charAt(pos) == '.')) {
					pos++;
				}
				String name = s.substring(start, pos);
				skip();
				List<Node> args = new ArrayList<>();
				if (peek('(')) {
					pos++;
					skip();
					if (!peek(')')) {
						while (true) {
							args.add(parseTernary());
							skip();
							if (peek(',')) {
								pos++;
								continue;
							}
							break;
						}
					}
					expect(')');
				}
				if (args.isEmpty() && peek('=') && !s.startsWith("==", pos) && isVariable(name)) {
					pos++;
					String var = variableName(name);
					Node value = parseTernary();
					return ctx -> {
						float v = value.eval(ctx);
						ctx.variables.put(var, v);
						return v;
					};
				}
				return identifier(name, args);
			}
			throw error(Tr.t("btb.t.unerwartetes_zeichen") + c + "'");
		}

		private static boolean isVariable(String name) {
			return name.startsWith("v.") || name.startsWith("variable.") || name.startsWith("t.") || name.startsWith("temp.");
		}

		private static String variableName(String name) {
			return name.substring(name.indexOf('.') + 1);
		}

		private Node identifier(String name, List<Node> a) {
			if (isVariable(name)) {
				String var = variableName(name);
				return ctx -> ctx.variables.getOrDefault(var, 0f);
			}
			String n = name;
			if (n.startsWith("q.")) {
				n = "query." + n.substring(2);
			} else if (n.startsWith("v.")) {
				n = "variable." + n.substring(2);
			} else if (n.startsWith("t.")) {
				n = "temp." + n.substring(2);
			} else if (n.startsWith("c.")) {
				n = "context." + n.substring(2);
			}
			if (n.startsWith("math.")) {
				return math(n.substring(5), a);
			}
			switch (n) {
				case "query.anim_time":
					return ctx -> ctx.animTime;
				case "query.life_time":
					return ctx -> ctx.lifeTime;
				case "query.modified_move_speed", "query.ground_speed":
					return ctx -> ctx.moveSpeed;
				case "query.modified_distance_moved", "query.walk_distance":
					return ctx -> ctx.walkDistance;
				case "query.is_sneaking":
					return ctx -> ctx.sneaking ? 1 : 0;
				case "query.is_on_ground":
					return ctx -> ctx.onGround ? 1 : 0;
				case "query.head_x_rotation":
					return ctx -> ctx.headPitch;
				case "query.head_y_rotation":
					return ctx -> ctx.headYaw;
				case "query.delta_time":
					return ctx -> 0.05f;
				case "true":
					return new Const(1);
				case "false":
					return new Const(0);
				default:
					if (!n.startsWith("variable.") && !n.startsWith("temp.") && !unknown.contains(n)) {
						unknown.add(n);
					}
					return new Const(0);
			}
		}

		private Node math(String fn, List<Node> a) {
			Node x = arg(a, 0), y = arg(a, 1), z = arg(a, 2);
			return switch (fn) {
				case "pi" -> new Const((float) Math.PI);
				case "sin" -> ctx -> (float) Math.sin(Math.toRadians(x.eval(ctx)));
				case "cos" -> ctx -> (float) Math.cos(Math.toRadians(x.eval(ctx)));
				case "abs" -> ctx -> Math.abs(x.eval(ctx));
				case "sqrt" -> ctx -> (float) Math.sqrt(Math.max(0, x.eval(ctx)));
				case "floor" -> ctx -> (float) Math.floor(x.eval(ctx));
				case "ceil" -> ctx -> (float) Math.ceil(x.eval(ctx));
				case "round" -> ctx -> Math.round(x.eval(ctx));
				case "trunc" -> ctx -> (float) (long) x.eval(ctx);
				case "min" -> ctx -> Math.min(x.eval(ctx), y.eval(ctx));
				case "max" -> ctx -> Math.max(x.eval(ctx), y.eval(ctx));
				case "clamp" -> ctx -> Math.max(y.eval(ctx), Math.min(z.eval(ctx), x.eval(ctx)));
				case "lerp" -> ctx -> x.eval(ctx) + (y.eval(ctx) - x.eval(ctx)) * z.eval(ctx);
				case "mod" -> ctx -> {
					float d = y.eval(ctx);
					return d == 0 ? 0 : x.eval(ctx) % d;
				};
				case "pow" -> ctx -> (float) Math.pow(x.eval(ctx), y.eval(ctx));
				case "exp" -> ctx -> (float) Math.exp(x.eval(ctx));
				case "ln" -> ctx -> (float) Math.log(x.eval(ctx));
				case "atan" -> ctx -> (float) Math.toDegrees(Math.atan(x.eval(ctx)));
				case "atan2" -> ctx -> (float) Math.toDegrees(Math.atan2(x.eval(ctx), y.eval(ctx)));
				case "asin" -> ctx -> (float) Math.toDegrees(Math.asin(x.eval(ctx)));
				case "acos" -> ctx -> (float) Math.toDegrees(Math.acos(x.eval(ctx)));
				case "sign" -> ctx -> Math.signum(x.eval(ctx));
				case "random" -> ctx -> x.eval(ctx) + (y.eval(ctx) - x.eval(ctx)) * ctx.random();
				case "die_roll", "random_integer" -> ctx -> Math.round(x.eval(ctx) + (y.eval(ctx) - x.eval(ctx)) * ctx.random());
				case "hermite_blend" -> ctx -> {
					float t = x.eval(ctx);
					return 3 * t * t - 2 * t * t * t;
				};
				default -> {
					if (!unknown.contains("math." + fn)) {
						unknown.add("math." + fn);
					}
					yield new Const(0);
				}
			};
		}

		private static Node arg(List<Node> a, int i) {
			return i < a.size() ? a.get(i) : new Const(0);
		}

		private boolean peek(char c) {
			return pos < s.length() && s.charAt(pos) == c;
		}

		private void expect(char c) {
			skip();
			if (!peek(c)) {
				throw error("'" + c + Tr.t("btb.t.erwartet"));
			}
			pos++;
		}

		private void skip() {
			while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
				pos++;
			}
		}

		private FormatException error(String msg) {
			return new FormatException(Tr.t("btb.t.molang_2") + s + "': " + msg + Tr.t("btb.t.position") + pos + ")");
		}
	}
}
