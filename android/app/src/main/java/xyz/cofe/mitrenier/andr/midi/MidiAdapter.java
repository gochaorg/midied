package xyz.cofe.mitrenier.andr.midi;

import android.content.Context;
import android.media.midi.MidiDevice;
import android.media.midi.MidiManager;
import android.media.midi.MidiOutputPort;
import android.os.Handler;
import android.os.HandlerThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.cofe.mitrenier.midi.MidiPortIn;
import xyz.cofe.mitrenier.player.andr.AndrMidiPortIn;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class MidiAdapter implements MidiPortInputSupplier {
    private static final Logger log = LoggerFactory.getLogger(MidiAdapter.class);

    private final MidiPortIn.Proxy midiPortIn = new MidiPortIn.Proxy();

    public MidiPortIn<?> getMidiPortInput(){
        return midiPortIn;
    }

    private volatile MidiDevice midiDevice;
    public Optional<MidiDevice> getMidiDevice(){
        return Optional.ofNullable(midiDevice);
    }

    private volatile MidiOutputPort midiAndroidOutputPort;
    public Optional<MidiOutputPort> getMidiInputPortAndroid(){
        return Optional.ofNullable(midiAndroidOutputPort);
    }

    public synchronized void init(Context context){
        if( context==null ) throw new IllegalArgumentException("context==null");
        log.info("find midi");

        findPreferredMidiDevice(context,midiDeviceOpt -> {
            midiDeviceOpt.ifPresent(dev -> {
                midiDevice = dev;
                findPreferredMidiPort(midiDevice).ifPresent(p -> {
                    midiAndroidOutputPort = p.androidPort();
                    midiPortIn.setTarget(p.adapter());
                });
            });
        });
    }

    private void findPreferredMidiDevice(Context context, Consumer<Optional<MidiDevice>> midiDevConsumer){
        var ctx = context;
        if( ctx==null ){
            log.error("android context is null");
            return;
        }

        var midiManager = (MidiManager) ctx.getSystemService(Context.MIDI_SERVICE);

        var devicesInfos = midiManager.getDevices();
        log.info("found {} midi devices", devicesInfos.length);
        for( var i=0; i<devicesInfos.length; i++ ){
            var di = devicesInfos[i];
            log.info("device [{}] {}", di, MidiDevID.from(di));
        }

        if( devicesInfos.length!=1 ){
            log.warn("expect one midi");
            return;
        }

        HandlerThread midiThread = new HandlerThread("MidiThread");

        midiThread.setDaemon(true);
        midiThread.start();

        Handler midiHandler = new Handler(midiThread.getLooper());

        // Флаг, чтобы убедиться, что consumer вызван только 1 раз
        AtomicBoolean isFinished = new AtomicBoolean(false);

        var selectedDevice = devicesInfos[0];
        log.info("connecting to {}", MidiDevID.from(selectedDevice));

        // Задача для таймаута
        Runnable timeoutTask = () -> {
            if (isFinished.compareAndSet(false, true)) {
                log.warn("expect midi connect timeout");
                // Время вышло, устройства нет
                midiDevConsumer.accept(Optional.empty());
                // Закрываем поток
                midiThread.quitSafely();
            }
        };

        // Планируем таймаут на 30 секунд
        midiHandler.postDelayed(timeoutTask, 30000);

        midiManager.openDevice(selectedDevice, device -> {
            // Успех! Удаляем задачу таймаута, чтобы она не сработала
            midiHandler.removeCallbacks(timeoutTask);

            if (isFinished.compareAndSet(false, true)) {
                log.info("midi connected");
                // Передаем устройство
                midiDevConsumer.accept(Optional.of(device));
                // Закрываем поток
                midiThread.quitSafely();
            }
        }, midiHandler);
    }
    public record PortAndAdapter(MidiPortIn<?> adapter, MidiOutputPort androidPort) {}
    private Optional<PortAndAdapter> findPreferredMidiPort(MidiDevice device){
        var port = device.openOutputPort(0);
        if( port==null ){
            log.warn("can't open port 0 of {}", MidiDevID.from(device.getInfo()));
        }

        return Optional.ofNullable(port).map( p -> new PortAndAdapter(new AndrMidiPortIn(p), p));
    }

    public synchronized void terminate(){
        midiPortIn.setDummyTarget();

        midiAndroidOutputPort = null;

        var dev = midiDevice;
        midiDevice = null;
        if( dev != null ) {
            try {
                log.info("close device {}", MidiDevID.from(dev.getInfo()));
                dev.close();
            } catch ( IOException e ) {
                log.error("can't close midi", e);
            }
        }
    }
}
