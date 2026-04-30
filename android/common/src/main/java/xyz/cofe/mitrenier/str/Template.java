package xyz.cofe.mitrenier.str;

//import org.mozilla.javascript.NativeArray;
//import org.mozilla.javascript.NativeObject;
//import org.mozilla.javascript.Scriptable;
//import org.mozilla.javascript.Undefined;
import xyz.cofe.coll.im.ImList;

import java.util.function.BiFunction;
import java.util.function.Function;

public record Template(ImList<Text> text) {
    public sealed interface Text {
        public record Plain(String text) implements Text {}
        public record Expression(String code) implements Text {}
    }

    public static Template parse(String source) {
        if( source == null ) throw new IllegalArgumentException("source==null");
        var buff = new StringBuilder();
        var txt = ImList.<Text>of();
        var s = "";
        var l = 0;
        for( var ci = 0; ci < source.length(); ci++ ){
            var ch = source.charAt(ci);
            switch( s ){
                case "" -> {
                    switch( ch ){
                        case '\\' -> s = "\\";
                        case '$' -> {
                            s = "$";
                        }
                        default -> buff.append(ch);
                    }
                }
                case "\\" -> {
                    buff.append(ch);
                    s = "";
                }
                case "$" -> {
                    switch( ch ){
                        case '{' -> {
                            l = 0;
                            s = "${";
                            txt = txt.prepend(new Text.Plain(buff.toString()));
                            buff.setLength(0);
                        }
                        default -> {
                            buff.append("$").append(ch);
                            s = "";
                        }
                    }
                }
                case "${" -> {
                    switch( ch ){
                        case '{' -> {
                            l++;
                            buff.append(ch);
                        }
                        case '}' -> {
                            l--;
                            if( l <= 0 ){
                                txt = txt.prepend(new Text.Expression(buff.toString()));
                                buff.setLength(0);
                                s = "";
                            } else{
                                buff.append(ch);
                            }
                        }
                        default -> {
                            buff.append(ch);
                        }
                    }
                }
            }
        }

        //noinspection SizeReplaceableByIsEmpty
        if( buff.length() > 0 ){
            switch( s ){
                case "" -> {
                    txt = txt.prepend(new Text.Plain(buff.toString()));
                }
                case "${" -> {
                    txt = txt.prepend(new Text.Expression(buff.toString()));
                }
                default -> {}
            }
        }

        return new Template(txt.reverse());
    }

    public String toString(Function<String, String> codeEvaluate) {
        if( codeEvaluate == null ) throw new IllegalArgumentException("codeEvaluate==null");
        var sb = new StringBuilder();
        for( var txt : text ){
            if( txt instanceof Text.Plain t ) sb.append(t.text);
            else if( txt instanceof Text.Expression t ) sb.append(codeEvaluate.apply(t.code));
        }
        return sb.toString();
    }

    public class Eval<R> {
        private final R init;
        private final BiFunction<R,R,R> combine;

        public Eval(R init, BiFunction<R, R, R> combine) {
            if( init==null ) throw new IllegalArgumentException("init==null");
            if( combine==null ) throw new IllegalArgumentException("combine==null");
            this.init = init;
            this.combine = combine;
        }

        private Function<String,R> plain;
        public Eval<R> plain(Function<String,R> plain) {
            this.plain = plain;
            return this;
        }

        private Function<String,R> expression;
        public Eval<R> expression(Function<String,R> expression) {
            this.expression = expression;
            return this;
        }

        public R go(){
            R res = init;
            for( var txt : text ){
                if( txt instanceof Text.Plain t && t.text!=null ) res = combine.apply(res, plain.apply(t.text));
                else if( txt instanceof Text.Expression t ) res = combine.apply(res, expression.apply(t.code));
            }
            return res;
        }
    }

    public <R> Eval<R> eval(R init, BiFunction<R,R,R> combine) {
        return new Eval<>(init, combine);
    }
}
