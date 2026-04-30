package xyz.cofe.nipal.header;

import java.util.Optional;
import java.util.regex.Pattern;

public sealed interface ContentRange {
    static HeaderValue<Optional<Segment>> headerSegmentOptional() {
        HeaderValue<Optional<Segment>> hv = str -> {
            return Optional.of(parseSegment(str));
        };
        return hv.defaultValue(Optional.<Optional<Segment>>empty());
    }

    static HeaderValue<Segment> headerSegment() {
        return ContentRange::parseSegment;
    }

    interface Segment {
        long from();
        default long to() { return toInclusive() + 1; }
        long toInclusive();
        default long size(){
            return Math.abs(to() - from());
        }
    }

    record RangeWithTotal(long from, long toInclusive, long total) implements ContentRange, Segment {}
    record RangeOnly(long from, long toInclusive) implements ContentRange, Segment {}
    record NoRange(long total) implements ContentRange {}

    static final Pattern RangeWithTotalPattern = Pattern.compile("(?is)bytes +(?<from>\\d+) *- *(?<to>\\d+) */ *(?<total>\\d+)");
    static final Pattern RangePattern = Pattern.compile("(?is)bytes +(?<from>\\d+) *- *(?<to>\\d+) */ *\\*");
    static final Pattern NoRangePattern = Pattern.compile("(?is)bytes +\\* */ *(?<total>\\d+)");

    static Optional<ContentRange> parse(String string){
        if( string==null || string.isBlank() )return Optional.empty();
        var m0 = RangeWithTotalPattern.matcher(string);
        if( m0.matches() ){
            long from;
            long to;
            long total;
            try {
                from = Long.parseLong(m0.group("from"));
                to = Long.parseLong(m0.group("to"));
                total = Long.parseLong(m0.group("total"));
            } catch ( NumberFormatException e ) {
                return Optional.empty();
            }

            if( from>to )return Optional.empty();
            if( from<0 )return Optional.empty();

            return Optional.of(new RangeWithTotal(from, to, total));
        }

        var m1 = RangePattern.matcher(string);
        if( m1.matches() ){
            long from;
            long to;
            try {
                from = Long.parseLong(m1.group("from"));
                to = Long.parseLong(m1.group("to"));
            } catch ( NumberFormatException e ) {
                return Optional.empty();
            }

            if( from>to )return Optional.empty();
            if( from<0 )return Optional.empty();

            return Optional.of(new RangeOnly(from, to));
        }

        var m2 = NoRangePattern.matcher(string);
        if( m2.matches() ){
            long total;
            try {
                total = Long.parseLong(m0.group("total"));
            } catch ( NumberFormatException e ) {
                return Optional.empty();
            }

            return Optional.of(new NoRange(total));
        }

        return Optional.empty();
    }

    static Optional<Segment> parseSegment(String string){
        return parse(string).flatMap(cr -> cr instanceof Segment s ? Optional.of(s) : Optional.empty());
    }
}
