package xyz.cofe.mitrenier.midi;

import xyz.cofe.mitrenier.Listeners;
import xyz.cofe.mitrenier.UnBind;

public interface MidiPortIn<SELF extends MidiPortIn<SELF>> {
    Listeners<MidiEvent<?>,SELF> midiInput();
    Listeners<RawMidiEvent,SELF> rawMidiInput();

    public static class Dummy implements MidiPortIn<Dummy> {
        public final Listeners<MidiEvent<?>, Dummy> midiDecoded = new Listeners<>(()->this);

        @Override
        public Listeners<MidiEvent<?>, Dummy> midiInput() {
            return midiDecoded;
        }

        public final Listeners<RawMidiEvent, Dummy> midiRaw = new Listeners<>(()->this);

        @Override
        public Listeners<RawMidiEvent, Dummy> rawMidiInput() {
            return midiRaw;
        }
    }

    public static class Proxy implements MidiPortIn<Proxy> {
        protected volatile MidiPortIn<?> target = new Dummy();
        protected volatile UnBind targetUnBind = new UnBind();

        public synchronized void setTarget(MidiPortIn<?> target){
            if( target==null ) throw new IllegalArgumentException("target==null");

            var ubind = targetUnBind;
            if( ubind!=null ){
                ubind.close();
            }

            targetUnBind = new UnBind();
            target.midiInput().listener(midiDecoded::fire).unBind(targetUnBind).bind();
            target.rawMidiInput().listener(midiRaw::fire).unBind(targetUnBind).bind();
        }

        public synchronized void setDummyTarget(){
            setTarget(new Dummy());
        }

        public final Listeners<MidiEvent<?>, Proxy> midiDecoded = new Listeners<>(()->this);

        @Override
        public Listeners<MidiEvent<?>, Proxy> midiInput() {
            return midiDecoded;
        }

        public final Listeners<RawMidiEvent, Proxy> midiRaw = new Listeners<>(()->this);

        @Override
        public Listeners<RawMidiEvent, Proxy> rawMidiInput() {
            return midiRaw;
        }
    }
}
