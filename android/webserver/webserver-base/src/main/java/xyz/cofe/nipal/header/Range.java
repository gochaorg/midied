package xyz.cofe.nipal.header;

import xyz.cofe.coll.im.ImList;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

public record Range(ImList<RangeSegment> segments) {
    public Range {
        if( segments==null ) throw new IllegalArgumentException("segments==null");
    }

    @Override
    public boolean equals(Object obj) {
        if( obj==null )return false;
        if( obj instanceof Range rng ){
            if( segments.size()!=rng.segments.size() )return false;
            return segments.zip(rng.segments).map( t -> {
                var a = t._1();
                var b = t._2();
                return a.equals(b);
            }).foldLeft(true, (r,a) -> r && a);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(segments.stream().toArray());
    }

    public sealed interface RangeSegment {
        Optional<FromTo> intersectWithFileSize(long fileSize);
    }
    public record FromTo(long from, long to) implements RangeSegment {
        public FromTo {
            if( from>to ) throw new IllegalArgumentException("from>to");
            if( from<0 ) throw new IllegalArgumentException("from<0");
        }

        public long size(){ return to - from; }

        @Override
        public Optional<FromTo> intersectWithFileSize(long fileSize) {
            if( fileSize<=0 )return Optional.empty();
            if( from>fileSize )return Optional.empty();
            if( to>fileSize ){
                return Optional.of(new FromTo(from, fileSize));
            }
            return Optional.of( this );
        }
    }
    public record From(long from) implements RangeSegment {
        public From {
            if( from<0 ) throw new IllegalArgumentException("from<0");
        }

        @Override
        public Optional<FromTo> intersectWithFileSize(long fileSize) {
            if( fileSize<=0 )return Optional.empty();
            if( from>=fileSize )return Optional.empty();
            return Optional.of( new FromTo(from, fileSize) );
        }
    }
    public record To(long to) implements RangeSegment {
        public To {
            if( to<0 ) throw new IllegalArgumentException("to<0");
        }

        public long size(){ return to; }

        @Override
        public Optional<FromTo> intersectWithFileSize(long fileSize) {
            if( fileSize<=0 )return Optional.empty();
            if( to > fileSize ){
                return Optional.of(new FromTo(0, fileSize));
            }
            return Optional.of(new FromTo(0, to));
        }
    }

    private static final Pattern pattern = Pattern.compile("(?is)bytes\\s*=\\s*(?<segments>.+)");
    private static final Pattern fromToPtrn = Pattern.compile("(?<from>\\d+)\\s*-\\s*(?<to>\\d+)");
    private static final Pattern fromPtrn = Pattern.compile("(?<from>\\d+)\\s*-\\s*");
    private static final Pattern toPtrn = Pattern.compile("\\s*-\\s*(?<to>\\d+)");

    public static Optional<Range> parse(String rangeHeader){
        if(rangeHeader==null)return Optional.empty();
        if(rangeHeader.isBlank())return Optional.empty();
        var trimmed = rangeHeader.trim();
        var matchSeg = pattern.matcher(trimmed);
        if( !matchSeg.matches() )return Optional.empty();

        var lst = ImList.<RangeSegment>of();
        var segmentsStr = matchSeg.group("segments");
        for( var segmentStr : segmentsStr.split("\\s*,\\s*") ){
            var mFT = fromToPtrn.matcher(segmentStr);
            var mF = fromPtrn.matcher(segmentStr);
            var mT = toPtrn.matcher(segmentStr);
            if( mFT.matches() ){
                lst = lst.prepend(new FromTo(Long.parseLong(mFT.group("from")), Long.parseLong(mFT.group("to"))+1));
            }else if( mF.matches() ){
                lst = lst.prepend(new From(Long.parseLong(mF.group("from"))));
            }else if( mT.matches() ){
                lst = lst.prepend(new To(Long.parseLong(mT.group("to"))+1));
            }
        }

        return Optional.of(new Range(lst.reverse()));
    }
}
