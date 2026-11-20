package com.minescore.score;

/**
 * An exact fraction, used for every position and length in a score (in whole notes), so tuplets add up
 * without rounding. Always kept in lowest terms with a positive denominator.
 */
public record Frac(long num, long den) implements Comparable<Frac> {
    public static final Frac ZERO = new Frac(0, 1);
    public static final Frac ONE = new Frac(1, 1);

    public Frac {
        if (den == 0) {
            throw new ArithmeticException("zero denominator");
        }
        if (den < 0) {
            num = -num;
            den = -den;
        }
        long g = gcd(Math.abs(num), den);
        if (g > 1) {
            num /= g;
            den /= g;
        }
    }

    public static Frac of(long num, long den) {
        return new Frac(num, den);
    }

    private static long gcd(long a, long b) {
        while (b != 0) {
            long t = a % b;
            a = b;
            b = t;
        }
        return a == 0 ? 1 : a;
    }

    public Frac plus(Frac o) {
        return new Frac(num * o.den + o.num * den, den * o.den);
    }

    public Frac minus(Frac o) {
        return new Frac(num * o.den - o.num * den, den * o.den);
    }

    public Frac times(Frac o) {
        return new Frac(num * o.num, den * o.den);
    }

    public Frac times(long n) {
        return new Frac(num * n, den);
    }

    public Frac divide(Frac o) {
        return new Frac(num * o.den, den * o.num);
    }

    public boolean isZero() {
        return num == 0;
    }

    public boolean isPositive() {
        return num > 0;
    }

    public double toDouble() {
        return (double) num / den;
    }

    public static Frac min(Frac a, Frac b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    public static Frac max(Frac a, Frac b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    @Override
    public int compareTo(Frac o) {
        return Long.compare(num * o.den, o.num * den);
    }

    /** "3/8", for saving. */
    @Override
    public String toString() {
        return num + "/" + den;
    }

    public static Frac parse(String s) {
        int slash = s.indexOf('/');
        return slash < 0 ? new Frac(Long.parseLong(s.trim()), 1)
                : new Frac(Long.parseLong(s.substring(0, slash).trim()), Long.parseLong(s.substring(slash + 1).trim()));
    }
}
