package xyz.cofe.mitrenier.midi;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;
import java.time.Instant;

public class MidiEventJsonView implements JsonSerializer<MidiEvent<?>>,
                                          JsonDeserializer<MidiEvent<?>>
{
    @Override
    public MidiEvent<?> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws
        JsonParseException {
        var obj = json.getAsJsonObject();
        switch( obj.get("type").getAsString() ){
            case "noteOn" -> {
                return new MidiEvent.NoteOn(
                    context.deserialize(obj.get("time"), Instant.class),
                    obj.get("timestamp").getAsLong(),
                    obj.get("channel").getAsInt(),
                    obj.get("note").getAsInt(),
                    obj.get("velocity").getAsInt()
                );
            }
            case "noteOff" -> {
                return new MidiEvent.NoteOff(
                    context.deserialize(obj.get("time"), Instant.class),
                    obj.get("timestamp").getAsLong(),
                    obj.get("channel").getAsInt(),
                    obj.get("note").getAsInt(),
                    obj.get("velocity").getAsInt()
                );
            }
            case "channelPressure" -> {
                return new MidiEvent.ChannelPressure(
                    context.deserialize(obj.get("time"), Instant.class),
                    obj.get("timestamp").getAsLong(),
                    obj.get("channel").getAsInt(),
                    obj.get("value").getAsInt()
                );
            }
            case "controlChange" -> {
                return new MidiEvent.ControlChange(
                    context.deserialize(obj.get("time"), Instant.class),
                    obj.get("timestamp").getAsLong(),
                    obj.get("channel").getAsInt(),
                    obj.get("controller").getAsInt(),
                    obj.get("value").getAsInt()
                );
            }
            case "channelModeMessages" -> {
                return new MidiEvent.ChannelModeMessages(
                    context.deserialize(obj.get("time"), Instant.class),
                    obj.get("timestamp").getAsLong(),
                    obj.get("channel").getAsInt(),
                    obj.get("controller").getAsInt(),
                    obj.get("value").getAsInt()
                );
            }
            case "pitchWheelChange" -> {
                return new MidiEvent.PitchWheelChange(
                    context.deserialize(obj.get("time"), Instant.class),
                    obj.get("timestamp").getAsLong(),
                    obj.get("channel").getAsInt(),
                    obj.get("value").getAsInt()
                );
            }
            case "programChange" -> {
                return new MidiEvent.ProgramChange(
                    context.deserialize(obj.get("time"), Instant.class),
                    obj.get("timestamp").getAsLong(),
                    obj.get("channel").getAsInt(),
                    obj.get("programm").getAsInt()
                );
            }
            case "polyphonicKeyPressure" -> {
                return new MidiEvent.PolyphonicKeyPressure(
                    context.deserialize(obj.get("time"), Instant.class),
                    obj.get("timestamp").getAsLong(),
                    obj.get("channel").getAsInt(),
                    obj.get("note").getAsInt(),
                    obj.get("pressureValue").getAsInt()
                );
            }
        }
        return null;
    }

    @Override
    public JsonElement serialize(MidiEvent<?> src, Type typeOfSrc, JsonSerializationContext context) {
        if( src instanceof MidiEvent.NoteOn noteOn ) {
            var jsonObj = new JsonObject();
            jsonObj.addProperty("type", "noteOn");
            jsonObj.addProperty("note", noteOn.note());
            jsonObj.addProperty("channel", noteOn.channel());
            jsonObj.addProperty("velocity", noteOn.velocity());
            jsonObj.addProperty("timestamp", noteOn.timestampNano());
            jsonObj.add("time", context.serialize(noteOn.time()));
            return jsonObj;
        } else if(src instanceof MidiEvent.NoteOff noteOff) {
            var jsonObj = new JsonObject();
            jsonObj.addProperty("type", "noteOff");
            jsonObj.addProperty("note", noteOff.note());
            jsonObj.addProperty("channel", noteOff.channel());
            jsonObj.addProperty("velocity", noteOff.velocity());
            jsonObj.addProperty("timestamp", noteOff.timestampNano());
            jsonObj.add("time", context.serialize(noteOff.time()));
            return jsonObj;
        } else if(src instanceof MidiEvent.ChannelPressure channelPressure) {
            var jsonObj = new JsonObject();
            jsonObj.addProperty("type", "channelPressure");
            jsonObj.addProperty("timestamp", channelPressure.timestampNano());
            jsonObj.add("time", context.serialize(channelPressure.time()));
            jsonObj.addProperty("channel", channelPressure.channel());
            jsonObj.addProperty("value", channelPressure.value());
            return jsonObj;
        } else if(src instanceof MidiEvent.ControlChange controlChange) {
            var jsonObj = new JsonObject();
            jsonObj.addProperty("type", "controlChange");
            jsonObj.addProperty("timestamp", controlChange.timestampNano());
            jsonObj.add("time", context.serialize(controlChange.time()));
            jsonObj.addProperty("channel", controlChange.channel());
            jsonObj.addProperty("controller", controlChange.controller());
            jsonObj.addProperty("value", controlChange.value());
            return jsonObj;
        } else if(src instanceof MidiEvent.ChannelModeMessages channelModeMessages) {
            var jsonObj = new JsonObject();
            jsonObj.addProperty("type", "channelModeMessages");
            jsonObj.addProperty("timestamp", channelModeMessages.timestampNano());
            jsonObj.add("time", context.serialize(channelModeMessages.time()));
            jsonObj.addProperty("channel", channelModeMessages.channel());
            jsonObj.addProperty("controller", channelModeMessages.controller());
            jsonObj.addProperty("value", channelModeMessages.value());
            return jsonObj;
        } else if(src instanceof MidiEvent.PitchWheelChange pitchWheelChange) {
            var jsonObj = new JsonObject();
            jsonObj.addProperty("type", "pitchWheelChange");
            jsonObj.addProperty("timestamp", pitchWheelChange.timestampNano());
            jsonObj.add("time", context.serialize(pitchWheelChange.time()));
            jsonObj.addProperty("channel", pitchWheelChange.channel());
            jsonObj.addProperty("value", pitchWheelChange.value());
            return jsonObj;
        } else if(src instanceof MidiEvent.ProgramChange programChange) {
            var jsonObj = new JsonObject();
            jsonObj.addProperty("type", "programChange");
            jsonObj.addProperty("timestamp", programChange.timestampNano());
            jsonObj.add("time", context.serialize(programChange.time()));
            jsonObj.addProperty("channel", programChange.channel());
            jsonObj.addProperty("programm", programChange.programm());
            return jsonObj;
        } else if(src instanceof MidiEvent.PolyphonicKeyPressure polyphonicKeyPressure) {
            var jsonObj = new JsonObject();
            jsonObj.addProperty("type", "polyphonicKeyPressure");
            jsonObj.addProperty("timestamp", polyphonicKeyPressure.timestampNano());
            jsonObj.add("time", context.serialize(polyphonicKeyPressure.time()));
            jsonObj.addProperty("channel", polyphonicKeyPressure.channel());
            jsonObj.addProperty("note", polyphonicKeyPressure.note());
            jsonObj.addProperty("pressureValue", polyphonicKeyPressure.pressureValue());
            return jsonObj;
        }

        var jsonObj = new JsonObject();
        jsonObj.addProperty("type", "unsupported "+src.getClass());
        return jsonObj;
    }
}
