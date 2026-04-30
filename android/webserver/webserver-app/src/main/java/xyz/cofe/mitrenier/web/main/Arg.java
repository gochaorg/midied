package xyz.cofe.mitrenier.web.main;

import xyz.cofe.coll.im.Tuple2;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

public interface Arg<T> {
    Optional<Tuple2<T, Integer>> parse(List<String> args, int offset);

    public default <B> Join<T, B> plus(Arg<B> next) {
        return new Join<>(this, next);
    }

    public default Join<T, String> andThenExpect(boolean icase, String first, String... variants) {
        if( variants == null ) throw new IllegalArgumentException("variants==null");
        if( first == null ) throw new IllegalArgumentException("first==null");
        return plus(Arg.StringArg.flatMap(str -> {
            if( icase && first.equalsIgnoreCase(str) )return Optional.of(str);
            if( !icase && first.equals(str) )return Optional.of(str);

            for( var nextVariant : variants ){
                if( nextVariant==null )continue;
                if( icase && nextVariant.equalsIgnoreCase(str) )return Optional.of(str);
                if( !icase && nextVariant.equals(str) )return Optional.of(str);
            }

            return Optional.empty();
        }));
    }

    public default <B> Arg<B> flatMap(Function<T, Optional<B>> mapper) {
        var me = this;
        return (args, offset) -> {
            var res = me.parse(args, offset);
            if( res.isEmpty() ) return Optional.empty();

            var b = mapper.apply(res.get()._1());
            //noinspection OptionalIsPresent
            if( b.isEmpty() ) return Optional.empty();

            return Optional.of(Tuple2.of(b.get(), res.get()._2()));
        };
    }


    public static final Arg<String> StringArg = new Arg<String>() {
        @Override
        public Optional<Tuple2<String, Integer>> parse(List<String> args, int offset) {
            if( offset < 0 || offset >= args.size() ) return Optional.empty();
            String v = args.get(offset);
            return Optional.of(Tuple2.of(v, offset + 1));
        }
    };

    public static final Arg<Integer> IntArg = StringArg.flatMap(str -> {
        try {
            return Optional.of(Integer.parseInt(str));
        } catch ( NumberFormatException e ) {
            return Optional.empty();
        }
    });

    static final Pattern DURATION_PATTERN = Pattern.compile("(?is)(?<n>\\d+)(?<suf>ns|ms|s|m|h|d)");

    /**
     * [0..9]+ (ns|ms|s|m|h)
     */
    public static final Arg<Duration> DurationArg = StringArg.flatMap(str -> {
        try {
            var m = DURATION_PATTERN.matcher(str);
            if( !m.matches() )return Optional.empty();

            var n = Integer.parseInt(m.group("n"));
            var suf = m.group("suf");

            if( suf.equalsIgnoreCase("ns")){
                return Optional.of(Duration.ofNanos(n));
            }

            if( suf.equalsIgnoreCase("ms")){
                return Optional.of(Duration.ofMillis(n));
            }

            if( suf.equalsIgnoreCase("s")){
                return Optional.of(Duration.ofSeconds(n));
            }

            if( suf.equalsIgnoreCase("m")){
                return Optional.of(Duration.ofMinutes(n));
            }

            if( suf.equalsIgnoreCase("h")){
                return Optional.of(Duration.ofHours(n));
            }

            if( suf.equalsIgnoreCase("d")){
                return Optional.of(Duration.ofDays(n));
            }

            return Optional.empty();
        } catch ( NumberFormatException e ) {
            return Optional.empty();
        }
    });

    public static <E extends Enum<E>> Arg<E> enumArg(Class<E> cls, boolean ignoreCase) {
        return StringArg.flatMap(str -> {
            var consts = cls.getEnumConstants();
            for( var c : consts ) {
                if( ignoreCase ) {
                    if( c.name().equalsIgnoreCase(str) ) {
                        return Optional.of(c);
                    }
                } else {
                    if( c.name().equals(str) ) {
                        return Optional.of(c);
                    }
                }
            }
            return Optional.empty();
        });
    }

    public static class Join<A, B> implements Arg<Tuple2<A, B>> {
        private Arg<A> first;
        private Arg<B> second;

        public Join(Arg<A> first, Arg<B> second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public Optional<Tuple2<Tuple2<A, B>, Integer>> parse(List<String> args, int offset) {
            var ra = first.parse(args, offset);
            if( ra.isEmpty() ) return Optional.empty();

            var rb = second.parse(args, ra.get()._2());
            //noinspection OptionalIsPresent
            if( rb.isEmpty() ) return Optional.empty();

            return Optional.of(Tuple2.of(
                Tuple2.of(ra.get()._1(), rb.get()._1()),
                rb.get()._2()
            ));
        }

        public Arg<A> skipRight() {
            return (args, off) -> {
                return parse(args, off).map(tup -> Tuple2.of(tup._1()._1(), tup._2()));
            };
        }

        public Arg<B> skipLeft() {
            return (args, off) -> {
                return parse(args, off).map(tup -> Tuple2.of(tup._1()._2(), tup._2()));
            };
        }
    }
}
