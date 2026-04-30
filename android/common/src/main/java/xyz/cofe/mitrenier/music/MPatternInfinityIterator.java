package xyz.cofe.mitrenier.music;

import xyz.cofe.coll.im.ImList;
import xyz.cofe.mitrenier.math.Fraction;

import java.util.Comparator;
import java.util.Iterator;

/**
 * Бесконечный итератор по нотам
 */
public class MPatternInfinityIterator implements Iterator<TimeBeats> {
    public MPatternInfinityIterator(MPattern pattern) {
        if( pattern == null ) throw new IllegalArgumentException("pattern==null");
        this.pattern = pattern;
        this.at = pattern.getFirstNoteStart();
        this.atPattern = at;
    }

    private final MPattern pattern;
    private Fraction at;
    private Fraction atPattern;

    public Fraction at() {
        return at;
    }

    public Fraction atPattern() {
        return atPattern;
    }

    @Override
    public boolean hasNext() {
        return false;
    }

    @Override
    public TimeBeats next() {
        var resAt = this.at;

        var atPtrn = this.atPattern;
        var beats = pattern.getBeatsAt(atPtrn);
        this.atPattern = pattern.getNextNoteStart(atPtrn).orElseGet(pattern::getFirstNoteStart);

        //noinspection UnnecessaryLocalVariable
        var prevAtPattern = atPtrn;
        var curAtPattern = this.atPattern;

        if( prevAtPattern.compareTo(curAtPattern) > 0 ){
            var dur = beats.stream().max(Comparator.comparing(Beat::length)).map(Beat::length).orElse(Fraction.of(0, 1));
            this.at = this.at.add(dur);
        } else{
            var dur = curAtPattern.subtract(prevAtPattern);
            this.at = this.at.add(dur);
        }

        return new TimeBeats(resAt, ImList.from(beats));
    }
}
