package com.crtghoul.locklauncher;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * High-performance, zero-dependency recursive descent math parser and evaluator.
 * Evaluates basic arithmetic (+, -, *, /, ^, %), parentheses, and percentages.
 */
public class MathEvaluator {

    public static class Result {
        public final boolean isValid;
        public final double value;
        public final String formatted;

        public Result(boolean isValid, double value, String formatted) {
            this.isValid = isValid;
            this.value = value;
            this.formatted = formatted;
        }
    }

    public static Result evaluate(String input) {
        if (input == null) return new Result(false, 0, "");
        String raw = input.trim();
        if (raw.isEmpty()) return new Result(false, 0, "");

        // Support "X% of Y" pattern
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.contains("% of ") || lower.contains(" percent of ")) {
            try {
                String[] parts = lower.split("% of | percent of ");
                if (parts.length == 2) {
                    Result p1 = evaluate(parts[0]);
                    Result p2 = evaluate(parts[1]);
                    if (p1.isValid && p2.isValid) {
                        double v = (p1.value / 100.0) * p2.value;
                        return formatResult(v);
                    }
                }
            } catch (Exception ignored) {}
        }

        // Clean operators (×, ÷, etc.)
        String expr = raw.replace("×", "*")
                .replace("÷", "/")
                .replace(" ", "");

        // Quick check: must contain at least one math operator to avoid evaluating single numbers
        boolean hasOperator = false;
        for (char c : expr.toCharArray()) {
            if (c == '+' || c == '-' || c == '*' || c == '/' || c == '^' || c == '%') {
                hasOperator = true;
                break;
            }
        }
        if (!hasOperator) {
            return new Result(false, 0, "");
        }

        // Must only contain digits, math operators, dots, parentheses
        for (char c : expr.toCharArray()) {
            if (!Character.isDigit(c) && c != '+' && c != '-' && c != '*' && c != '/' && c != '^' && c != '%' && c != '.' && c != '(' && c != ')') {
                return new Result(false, 0, "");
            }
        }

        try {
            Parser parser = new Parser(expr);
            double val = parser.parse();
            if (Double.isNaN(val) || Double.isInfinite(val)) {
                return new Result(false, 0, "");
            }
            return formatResult(val);
        } catch (Exception e) {
            return new Result(false, 0, "");
        }
    }

    private static Result formatResult(double val) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        DecimalFormat df = new DecimalFormat("#,##0.######", symbols);
        String formatted = df.format(val);
        return new Result(true, val, formatted);
    }

    private static class Parser {
        private final String str;
        private int pos = -1;
        private int ch;

        Parser(String str) {
            this.str = str;
        }

        void nextChar() {
            ch = (++pos < str.length()) ? str.charAt(pos) : -1;
        }

        boolean eat(int charToEat) {
            while (ch == ' ') nextChar();
            if (ch == charToEat) {
                nextChar();
                return true;
            }
            return false;
        }

        double parse() {
            nextChar();
            double x = parseExpression();
            if (pos < str.length()) throw new RuntimeException("Unexpected: " + (char) ch);
            return x;
        }

        double parseExpression() {
            double x = parseTerm();
            for (;;) {
                if (eat('+')) x += parseTerm();
                else if (eat('-')) x -= parseTerm();
                else return x;
            }
        }

        double parseTerm() {
            double x = parseFactor();
            for (;;) {
                if (eat('*')) x *= parseFactor();
                else if (eat('/')) {
                    double denom = parseFactor();
                    if (denom == 0) throw new ArithmeticException("Divide by zero");
                    x /= denom;
                } else if (eat('%')) {
                    x = x % parseFactor();
                } else return x;
            }
        }

        double parseFactor() {
            if (eat('+')) return parseFactor();
            if (eat('-')) return -parseFactor();

            double x;
            int startPos = this.pos;
            if (eat('(')) {
                x = parseExpression();
                if (!eat(')')) throw new RuntimeException("Missing ')'");
            } else if ((ch >= '0' && ch <= '9') || ch == '.') {
                while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
                x = Double.parseDouble(str.substring(startPos, this.pos));
            } else {
                throw new RuntimeException("Unexpected: " + (char) ch);
            }

            if (eat('^')) x = Math.pow(x, parseFactor());

            return x;
        }
    }
}
