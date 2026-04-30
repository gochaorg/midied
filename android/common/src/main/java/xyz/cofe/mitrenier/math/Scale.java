package xyz.cofe.mitrenier.math;

public class Scale {
    public static double inner2outer(double x, double innerLo, double innerHi, double outerLo, double outerHi) {
        double inSize = innerHi - innerLo;
        double outSize = outerHi - outerLo;
        double kof = outSize / inSize;

        double offInner = x - innerLo;
        double offOuter = offInner * kof;
        return outerLo + offOuter;
    }

    public record X(double x) {
        public From from(double lo, double hi){
            return new From(x, lo, hi);
        }
    }

    public record From(double x, double fromLo, double fromHi) {
        public double to( double toLo, double toHi ){
            return inner2outer(x, fromLo, fromHi, toLo, toHi);
        }
    }

    public static X x( double x ){
        return new X(x);
    }
}
