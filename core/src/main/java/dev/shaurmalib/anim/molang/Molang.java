package dev.shaurmalib.anim.molang;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Підмножина Molang для значень ключових кадрів (як у Bedrock / GeckoLib / Blockbench).
 *
 * Підтримується: числа, {@code + - * /}, дужки, унарний мінус, порівняння, {@code && || !},
 * тернарний {@code a ? b : c}, {@code math.*} (sin/cos у ГРАДУСАХ, як у Molang), змінні
 * {@code query.* / q.*}, {@code variable.* / v.*}, {@code temp.* / t.*} — їх дає {@link MolangContext}.
 * Невідома функція/змінна = 0 (як у грі): кривий вираз не валить анімацію.
 *
 * Компілюється один раз у дерево {@link Expr}; обчислення без алокацій.
 */
public final class Molang {

    @FunctionalInterface
    public interface Expr {
        double eval(MolangContext ctx);
    }

    public static Expr constant(double v) {
        return new Const(v);
    }

    /** Константа відома без контексту — щоб ключі з числами не ганяли інтерпретатор. */
    public static final class Const implements Expr {
        public final double value;
        Const(double v) { value = v; }
        @Override public double eval(MolangContext ctx) { return value; }
    }

    public static Expr compile(String src) {
        if (src == null) return constant(0);
        String t = src.trim();
        if (t.isEmpty()) return constant(0);
        try {
            return constant(Double.parseDouble(t));
        } catch (NumberFormatException ignored) {
            // вираз
        }
        Molang p = new Molang(t.toLowerCase(Locale.ROOT));
        Expr e = p.parseStatements();
        return e;
    }

    // ── Парсер (рекурсивний спуск) ───────────────────────────────────────

    private final String s;
    private int i;

    private Molang(String s) { this.s = s; }

    /** {@code a; b; return c} — Bedrock дозволяє кілька тверджень; повертаємо останнє/return. */
    private Expr parseStatements() {
        List<Expr> parts = new ArrayList<>();
        while (true) {
            ws();
            if (i >= s.length()) break;
            if (s.startsWith("return", i) && (i + 6 >= s.length() || !Character.isLetterOrDigit(s.charAt(i + 6)))) {
                i += 6;
                parts.add(ternary());
                break;
            }
            parts.add(ternary());
            ws();
            if (i < s.length() && s.charAt(i) == ';') { i++; continue; }
            break;
        }
        if (parts.isEmpty()) return constant(0);
        return parts.get(parts.size() - 1);
    }

    private Expr ternary() {
        Expr c = or();
        ws();
        if (eat('?')) {
            Expr a = ternary();
            ws();
            Expr b = eat(':') ? ternary() : constant(0);
            return ctx -> c.eval(ctx) != 0 ? a.eval(ctx) : b.eval(ctx);
        }
        return c;
    }

    private Expr or() {
        Expr l = and();
        while (true) {
            ws();
            if (eat2("||")) { Expr a = l, b = and(); l = ctx -> (a.eval(ctx) != 0 || b.eval(ctx) != 0) ? 1 : 0; }
            else return l;
        }
    }

    private Expr and() {
        Expr l = cmp();
        while (true) {
            ws();
            if (eat2("&&")) { Expr a = l, b = cmp(); l = ctx -> (a.eval(ctx) != 0 && b.eval(ctx) != 0) ? 1 : 0; }
            else return l;
        }
    }

    private Expr cmp() {
        Expr l = add();
        while (true) {
            ws();
            Expr a = l;
            if (eat2("<=")) { Expr b = add(); l = ctx -> a.eval(ctx) <= b.eval(ctx) ? 1 : 0; }
            else if (eat2(">=")) { Expr b = add(); l = ctx -> a.eval(ctx) >= b.eval(ctx) ? 1 : 0; }
            else if (eat2("==")) { Expr b = add(); l = ctx -> a.eval(ctx) == b.eval(ctx) ? 1 : 0; }
            else if (eat2("!=")) { Expr b = add(); l = ctx -> a.eval(ctx) != b.eval(ctx) ? 1 : 0; }
            else if (eat('<')) { Expr b = add(); l = ctx -> a.eval(ctx) < b.eval(ctx) ? 1 : 0; }
            else if (eat('>')) { Expr b = add(); l = ctx -> a.eval(ctx) > b.eval(ctx) ? 1 : 0; }
            else return l;
        }
    }

    private Expr add() {
        Expr l = mul();
        while (true) {
            ws();
            Expr a = l;
            if (eat('+')) { Expr b = mul(); l = ctx -> a.eval(ctx) + b.eval(ctx); }
            else if (eat('-')) { Expr b = mul(); l = ctx -> a.eval(ctx) - b.eval(ctx); }
            else return fold(l);
        }
    }

    private Expr mul() {
        Expr l = unary();
        while (true) {
            ws();
            Expr a = l;
            if (eat('*')) { Expr b = unary(); l = ctx -> a.eval(ctx) * b.eval(ctx); }
            else if (eat('/')) { Expr b = unary(); l = ctx -> { double d = b.eval(ctx); return d == 0 ? 0 : a.eval(ctx) / d; }; }
            else return l;
        }
    }

    private Expr unary() {
        ws();
        if (eat('-')) { Expr e = unary(); return fold(ctx -> -e.eval(ctx)); }
        if (eat('+')) return unary();
        if (eat('!')) { Expr e = unary(); return ctx -> e.eval(ctx) == 0 ? 1 : 0; }
        return primary();
    }

    private Expr primary() {
        ws();
        if (i >= s.length()) return constant(0);
        char c = s.charAt(i);
        if (c == '(') {
            i++;
            Expr e = ternary();
            ws();
            eat(')');
            return e;
        }
        if (Character.isDigit(c) || c == '.') {
            int st = i;
            while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) i++;
            return constant(Double.parseDouble(s.substring(st, i)));
        }
        if (Character.isLetter(c) || c == '_') {
            int st = i;
            while (i < s.length() && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '_' || s.charAt(i) == '.')) i++;
            String name = s.substring(st, i);
            ws();
            if (eat('(')) {
                List<Expr> args = new ArrayList<>();
                ws();
                if (!eat(')')) {
                    do { args.add(ternary()); ws(); } while (eat(','));
                    eat(')');
                }
                return function(name, args);
            }
            if (name.equals("true")) return constant(1);
            if (name.equals("false")) return constant(0);
            if (name.equals("math.pi")) return constant(Math.PI);
            String key = normalize(name);
            if (i < s.length() && s.charAt(i) == '=' && (i + 1 >= s.length() || s.charAt(i + 1) != '=')) {
                i++; // присвоєння: v.x = вираз
                Expr value = ternary();
                return ctx -> {
                    double v = value.eval(ctx);
                    ctx.set(key, v);
                    return v;
                };
            }
            return ctx -> ctx.get(key);
        }
        i++; // невідомий символ — пропускаємо
        return constant(0);
    }

    private static String normalize(String name) {
        if (name.startsWith("q.")) return "query." + name.substring(2);
        if (name.startsWith("v.")) return "variable." + name.substring(2);
        if (name.startsWith("t.")) return "temp." + name.substring(2);
        if (name.startsWith("c.")) return "context." + name.substring(2);
        return name;
    }

    private static Expr fold(Expr e) {
        if (e instanceof Const) return e;
        return e;
    }

    private static Expr function(String name, List<Expr> a) {
        Expr x = a.size() > 0 ? a.get(0) : constant(0);
        Expr y = a.size() > 1 ? a.get(1) : constant(0);
        Expr z = a.size() > 2 ? a.get(2) : constant(0);
        switch (name) {
            case "math.sin": return ctx -> Math.sin(Math.toRadians(x.eval(ctx)));
            case "math.cos": return ctx -> Math.cos(Math.toRadians(x.eval(ctx)));
            case "math.abs": return ctx -> Math.abs(x.eval(ctx));
            case "math.sqrt": return ctx -> Math.sqrt(Math.max(0, x.eval(ctx)));
            case "math.floor": return ctx -> Math.floor(x.eval(ctx));
            case "math.ceil": return ctx -> Math.ceil(x.eval(ctx));
            case "math.round": return ctx -> Math.round(x.eval(ctx));
            case "math.trunc": return ctx -> (double) (long) x.eval(ctx);
            case "math.exp": return ctx -> Math.exp(x.eval(ctx));
            case "math.ln": return ctx -> Math.log(x.eval(ctx));
            case "math.pow": return ctx -> Math.pow(x.eval(ctx), y.eval(ctx));
            case "math.min": return ctx -> Math.min(x.eval(ctx), y.eval(ctx));
            case "math.max": return ctx -> Math.max(x.eval(ctx), y.eval(ctx));
            case "math.mod": return ctx -> { double d = y.eval(ctx); return d == 0 ? 0 : x.eval(ctx) % d; };
            case "math.clamp": return ctx -> Math.max(y.eval(ctx), Math.min(z.eval(ctx), x.eval(ctx)));
            case "math.lerp": return ctx -> { double s0 = x.eval(ctx); return s0 + (y.eval(ctx) - s0) * z.eval(ctx); };
            case "math.atan": return ctx -> Math.toDegrees(Math.atan(x.eval(ctx)));
            case "math.atan2": return ctx -> Math.toDegrees(Math.atan2(x.eval(ctx), y.eval(ctx)));
            case "math.asin": return ctx -> Math.toDegrees(Math.asin(x.eval(ctx)));
            case "math.acos": return ctx -> Math.toDegrees(Math.acos(x.eval(ctx)));
            case "math.random": return ctx -> { double lo = x.eval(ctx); return lo + Math.random() * (y.eval(ctx) - lo); };
            case "math.die_roll":
            case "math.random_integer": return ctx -> {
                double lo = a.size() > 1 ? x.eval(ctx) : 0, hi = a.size() > 1 ? y.eval(ctx) : x.eval(ctx);
                return Math.floor(lo + Math.random() * (hi - lo + 1));
            };
            default: {
                String key = normalize(name);
                // query-функції з аргументами (q.is_item_name_any(...)) — віддаємо контексту без аргументів
                return ctx -> ctx.get(key);
            }
        }
    }

    private void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }

    private boolean eat(char c) {
        if (i < s.length() && s.charAt(i) == c) { i++; return true; }
        return false;
    }

    private boolean eat2(String op) {
        if (s.startsWith(op, i)) { i += op.length(); return true; }
        return false;
    }
}
