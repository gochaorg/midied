package xyz.cofe.mitrenier.andr.midi.http;

import android.content.Context;
import android.media.midi.MidiDeviceInfo;
import android.media.midi.MidiManager;
import org.eclipse.jetty.server.Handler;
import xyz.cofe.mitrenier.andr.midi.MidiDevID;
import xyz.cofe.mitrenier.json.JSON;
import xyz.cofe.nipal.JsonItfImpl;
import xyz.cofe.nipal.RequestRouter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public class MidiDeviceRestApi extends ArrayList<Handler> {
    private final Context context;

    public MidiDeviceRestApi(Context context){
        if( context==null ) throw new IllegalArgumentException("context==null");
        this.context = context;

        add(RequestRouter.Builder.with(JsonItfImpl.gson(JSON.gson)).GET().path("/").jsonResponse().call(this::devices));
        add(RequestRouter.Builder.with(JsonItfImpl.gson(JSON.gson)).GET().path("").jsonResponse().call(this::devices));
    }

    private Object devices(){
        Map<String,Object> map = new LinkedHashMap<>();

        var midiManager = (MidiManager) context.getSystemService(Context.MIDI_SERVICE);
        var devicesInfos = midiManager.getDevices();
        for( var i=0; i<devicesInfos.length; i++ ){
            var midiDev = devicesInfos[i];

            var dev = MidiDevID.from(midiDev);

            var jsn = new LinkedHashMap<String,Object>();
            map.put( ""+dev.id(), jsn );

            jsn.put("name", dev.name());
            jsn.put("manufacture", dev.manufacture());
            jsn.put("serialNumber", dev.serialNumber());
            jsn.put("version", dev.version());
            jsn.put("type", midiDev.getType());
            dev.getType().ifPresent( t -> jsn.put("typeDesc", t.name()));

            var ports = new LinkedHashMap<String,Object>();
            jsn.put("ports", ports);
            jsn.put("portsInputCount", midiDev.getInputPortCount());
            jsn.put("portsOutputCount", midiDev.getOutputPortCount());

            var midiPorts = midiDev.getPorts();
            for( var p=0; p<midiPorts.length; p++ ){
                var port = midiPorts[p];

                var pjsn = new LinkedHashMap<>();
                ports.put(""+port.getPortNumber(), pjsn);

                pjsn.put("name",port.getName());

                var isInput = port.getType()== MidiDeviceInfo.PortInfo.TYPE_INPUT;
                var isOutput = port.getType()== MidiDeviceInfo.PortInfo.TYPE_OUTPUT;

                pjsn.put("typeIsInput", isInput);
                pjsn.put("typeIsOutput", isOutput);
                pjsn.put("type", port.getType());

                if( isInput ) pjsn.put("typeDesc", "INPUT");
                if( isOutput ) pjsn.put("typeDesc", "OUTPUT");
            }
        }

        return map;
    }
}
