package xyz.cofe.mitrenier.music;

import xyz.cofe.mitrenier.math.Fraction;

/**
 * "Удар" ноты
 * @param pitch Высота ноты
 * @param power Сила
 * @param length Продолжительность
 */
public record Beat(Pitch pitch, Fraction length, Power power) {

    public Beat {
        if( pitch==null ) throw new IllegalArgumentException("pitch==null");
        if( length==null ) throw new IllegalArgumentException("length==null");
        if( power==null ) throw new IllegalArgumentException("power==null");
    }

    public static Beat of(Pitch pitch, Fraction length, Power power) {
        return new Beat(pitch,length,power);
    }

    public static Builder0 strong(){ return new Builder0(new Power.Strong()); }
    public static Builder0 medium(){ return new Builder0(new Power.Medium()); }
    public static Builder0 weak(){ return new Builder0(new Power.Weak()); }

    public static class Builder0 {
        public final Power power;
        public Builder0(Power power) {
            if( power==null ) throw new IllegalArgumentException("power==null");
            this.power = power;
        }

        public Builder1 pitch(Pitch pitch){
            if( pitch==null ) throw new IllegalArgumentException("pitch==null");
            return new Builder1(this,pitch);
        }

        public Builder1 none(){
            return pitch(new Pitch.NonePitch());
        }

        public Builder1 any(){
            return pitch(new Pitch.AnyPitch());
        }

        public Builder1 octaveRelative(int note){
            return pitch(new Pitch.RelativePitch(note));
        }

        public Builder1 absolute(int note){
            return pitch(new Pitch.AbsolutePitch(note));
        }
    }
    public static class Builder1 {
        public final Builder0 builder0;
        public final Pitch pitch;

        public Builder1(Builder0 builder0, Pitch pitch) {
            this.builder0 = builder0;
            this.pitch = pitch;
        }

        public Beat length(Fraction len){
            if( len==null ) throw new IllegalArgumentException("len==null");
            return new Beat(pitch,len,builder0.power);
        }

        public Beat length(int dividend, int divisor){
            return new Beat(pitch,new Fraction(dividend,divisor),builder0.power);
        }
    }
}
