package xyz.cofe.mitrenier.andr.midi;

import android.media.midi.MidiDeviceInfo;

import java.util.Optional;

public record MidiDevID(int id, String name, String manufacture, String serialNumber, String version, int type) {
    public enum Type {
        USB,
        VIRTUAL,
        BLUETOOTH
    }

    public static MidiDevID from(MidiDeviceInfo deviceInfo) {
        if( deviceInfo == null ) throw new IllegalArgumentException("deviceInfo==null");
        return new MidiDevID(
            deviceInfo.getId(),
            deviceInfo.getProperties().getString(MidiDeviceInfo.PROPERTY_NAME),
            deviceInfo.getProperties().getString(MidiDeviceInfo.PROPERTY_MANUFACTURER),
            deviceInfo.getProperties().getString(MidiDeviceInfo.PROPERTY_SERIAL_NUMBER),
            deviceInfo.getProperties().getString(MidiDeviceInfo.PROPERTY_VERSION),
            deviceInfo.getType()
        );
    }

    public boolean isUsb() {return type == MidiDeviceInfo.TYPE_USB;}
    public boolean isVirtual() {return type == MidiDeviceInfo.TYPE_VIRTUAL;}
    public boolean isBluetooth() {return type == MidiDeviceInfo.TYPE_BLUETOOTH;}

    public Optional<Type> getType(){
        return isUsb()
            ? Optional.of(Type.USB)
            : isBluetooth()
            ? Optional.of(Type.BLUETOOTH)
            : isVirtual()
            ? Optional.of(Type.VIRTUAL)
            : Optional.empty();
    }

    /** @noinspection NullableProblems*/
    @Override
    public String toString() {
        return "MidiDevID{" +
            "id=" + id +
            ", name='" + name + '\'' +
            ", manufacture='" + manufacture + '\'' +
            ", serialNumber='" + serialNumber + '\'' +
            ", version='" + version + '\'' +
            ", type=" + getType() +
            '}';
    }
}
