package com.dsh.powermeter.qa;

import java.util.ArrayList;
import java.util.List;

/**
 * 极简断言框架：纯 main + 断言，不依赖 JUnit、不依赖 Android 运行时。
 * 每条断言计 1 个测试用例；失败时打印「期望 / 实际」并计入失败数。
 */
public final class TestKit {

    private static int passed;
    private static int failed;
    private static String currentSection = "";
    private static final List<String> failures = new ArrayList<>();

    private TestKit() {
    }

    public static void section(String title) {
        currentSection = title;
        System.out.println();
        System.out.println("## " + title);
    }

    public static void pass(String name) {
        passed++;
        System.out.println("PASS " + name);
    }

    public static void fail(String name, String detail) {
        failed++;
        failures.add((currentSection.isEmpty() ? "" : currentSection + " | ") + name + " → " + detail);
        System.out.println("FAIL " + name + " → " + detail);
    }

    public static void eq(String name, long expected, long actual) {
        if (expected == actual) {
            pass(name);
        } else {
            fail(name, "期望 " + expected + "，实际 " + actual);
        }
    }

    public static void eqDouble(String name, double expected, double actual, double delta) {
        if (Double.isFinite(actual) && Math.abs(expected - actual) <= delta) {
            pass(name);
        } else {
            fail(name, "期望 " + fmt(expected) + " ± " + fmt(delta) + "，实际 " + fmt(actual));
        }
    }

    public static void eqStr(String name, String expected, String actual) {
        if (expected == null ? actual == null : expected.equals(actual)) {
            pass(name);
        } else {
            fail(name, "期望「" + expected + "」，实际「" + actual + "」");
        }
    }

    public static void contains(String name, String expectedSubstring, String actual) {
        if (actual != null && actual.contains(expectedSubstring)) {
            pass(name);
        } else {
            fail(name, "期望包含「" + expectedSubstring + "」，实际「" + actual + "」");
        }
    }

    public static void isTrue(String name, boolean cond) {
        if (cond) {
            pass(name);
        } else {
            fail(name, "期望 true，实际 false");
        }
    }

    public static void isFalse(String name, boolean cond) {
        if (!cond) {
            pass(name);
        } else {
            fail(name, "期望 false，实际 true");
        }
    }

    public static void notNull(String name, Object value) {
        if (value != null) {
            pass(name);
        } else {
            fail(name, "期望非 null，实际 null");
        }
    }

    public static void isNaN(String name, double value) {
        if (Double.isNaN(value)) {
            pass(name);
        } else {
            fail(name, "期望 NaN，实际 " + fmt(value));
        }
    }

    /** actual > bound。 */
    public static void gt(String name, double actual, double bound) {
        if (Double.isFinite(actual) && actual > bound) {
            pass(name);
        } else {
            fail(name, "期望 > " + fmt(bound) + "，实际 " + fmt(actual));
        }
    }

    /** actual < bound。 */
    public static void lt(String name, double actual, double bound) {
        if (Double.isFinite(actual) && actual < bound) {
            pass(name);
        } else {
            fail(name, "期望 < " + fmt(bound) + "，实际 " + fmt(actual));
        }
    }

    /** lo <= actual <= hi（闭区间）。 */
    public static void range(String name, double lo, double hi, double actual) {
        if (Double.isFinite(actual) && actual >= lo && actual <= hi) {
            pass(name);
        } else {
            fail(name, "期望落在 [" + fmt(lo) + ", " + fmt(hi) + "]，实际 " + fmt(actual));
        }
    }

    public static int passed() {
        return passed;
    }

    public static int failedCount() {
        return failed;
    }

    public static List<String> failures() {
        return failures;
    }

    private static String fmt(double v) {
        if (Double.isNaN(v)) return "NaN";
        if (Double.isInfinite(v)) return v > 0 ? "Infinity" : "-Infinity";
        return String.format(java.util.Locale.ROOT, "%.4f", v);
    }
}
