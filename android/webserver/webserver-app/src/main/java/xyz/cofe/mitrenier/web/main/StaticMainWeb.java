package xyz.cofe.mitrenier.web.main;

import xyz.cofe.mitrenier.IO;
import xyz.cofe.mitrenier.api.server.rest.IndexFileApi;
import xyz.cofe.mitrenier.api.server.rest.MidiClientApi;
import xyz.cofe.mitrenier.api.server.rest.MidiInputClients;
import xyz.cofe.mitrenier.api.server.rest.MidiPortEmuApi;
import xyz.cofe.mitrenier.midi.MidiPortIn;
import xyz.cofe.mitrenier.player.desktop.JavaMidiPortIn;
import xyz.cofe.nipal.Service;
import xyz.cofe.nipal.ServiceBuilder;
import xyz.cofe.nipal.files.StaticFiles;

import javax.sound.midi.MidiDevice;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class StaticMainWeb {
    public static void main(String[] args0) {
        if( args0 != null && args0.length == 1 && args0[0].matches("(?i)[\\-/](\\?|help)") ) {
            var url = StaticMainWeb.class.getResource("/help/static-main-web.md");
            if( url != null ) {
                IO.tryReadAllBytes(url).getOk().ifPresent(bytes ->
                    System.out.println(new String(bytes, StandardCharsets.UTF_8)));
            }
            return;
        }

        var main = new StaticMainWeb();
        main.cmdLine.parse(args0);
        main.start();
    }

    public int port = 8080;
    public String ip;
    public Path baseDir;

    public record DirBinding(String dir, boolean writeable) {
        @Override
        public boolean equals(Object object) {
            if( object == null || getClass() != object.getClass() ) return false;
            DirBinding that = (DirBinding) object;
            return Objects.equals(dir, that.dir);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(dir);
        }
    }
    public Map<String, Set<DirBinding>> url2dir;

    public String clientApi = "/client";
    public String emuApi = "/emu";
    public LinkedHashMap<String, Supplier<String>> params = new LinkedHashMap<>();
    private IndexFileApi indexFileApi;

    private IndexFileApi indexFileApi() {
        if( indexFileApi == null ) indexFileApi = new IndexFileApi();
        return indexFileApi;
    }

    public StaticMainWeb() {
        baseDir = Paths.get(".").toAbsolutePath().normalize();
    }

    public final CmdLine cmdLine = new CmdLine();

    private enum Cmd {
        Port, Host, Bind, BaseDir, Midi, Connector, Threads, Index
    }
    private enum MidiCmd {
        List,
        Use
    }

    private static final Arg<Cmd> CmdArg = Arg.enumArg(Cmd.class, true);

    private static Arg<Cmd> cmdMatch(Cmd name) {
        return CmdArg.flatMap(cmd -> cmd == name ? Optional.of(cmd) : Optional.empty());
    }

    {
        // connector idleTimeout <duration>
        // connector idle <duration>
        cmdLine.add(
            cmdMatch(Cmd.Connector).andThenExpect(true, "idleTimeout", "idle").skipLeft().plus(Arg.DurationArg).skipLeft(),
            duration -> {
                connectorConf().add(cc -> {
                    System.out.println("connector idle timeout " + duration);
                    cc.idleTimeout(duration);
                });
            }
        );

        // connector queue <int>
        cmdLine.add(
            cmdMatch(Cmd.Connector).andThenExpect(true, "queue").skipLeft().plus(Arg.IntArg).skipLeft(),
            value -> {
                connectorConf().add(cc -> {
                    System.out.println("connector accept queue size " + value);
                    cc.acceptQueueSize(value);
                });
            }
        );

        // threads idleTimeout <duration>
        // threads idle <duration>
        cmdLine.add(
            cmdMatch(Cmd.Threads).andThenExpect(true, "idleTimeout", "idle").skipLeft().plus(Arg.DurationArg).skipLeft(),
            value -> {
                httpThreadConf().add(cc -> {
                    System.out.println("http threads idle " + value);
                    cc.idleTimeout(value);
                });
            }
        );

        // threads min <count:int>
        cmdLine.add(
            cmdMatch(Cmd.Threads).andThenExpect(true, "min").skipLeft().plus(Arg.IntArg).skipLeft(),
            value -> {
                httpThreadConf().add(cc -> {
                    System.out.println("http threads min count " + value);
                    cc.minThreads(value);
                });
            }
        );

        // threads max <count:int>
        cmdLine.add(
            cmdMatch(Cmd.Threads).andThenExpect(true, "max").skipLeft().plus(Arg.IntArg).skipLeft(),
            value -> {
                httpThreadConf().add(cc -> {
                    System.out.println("http threads max count " + value);
                    cc.maxThreads(value);
                });
            }
        );

        // threads minmax <count:int> <count:int>
        cmdLine.add(
            cmdMatch(Cmd.Threads).andThenExpect(true, "minmax").skipLeft().plus(Arg.IntArg).skipLeft().plus(Arg.IntArg),
            value -> {
                httpThreadConf().add(cc -> {
                    var minCount = value._1();
                    var maxCount = value._2();
                    System.out.println("http threads min count " + minCount);
                    cc.minThreads(maxCount);
                    System.out.println("http threads max count " + maxCount);
                    cc.maxThreads(maxCount);
                });
            }
        );

        // port <port num>
        cmdLine.add(
            cmdMatch(Cmd.Port).plus(Arg.IntArg).skipLeft(),
            port -> {
                this.port = port;
            });

        // bind <url> <dir> (ro | rw)
        cmdLine.add(
            cmdMatch(Cmd.Bind).plus(Arg.StringArg).skipLeft().plus(Arg.StringArg).andThenExpect(true,"ro", "rw"),
            urlDir -> {
                var url = urlDir._1()._1();
                url = url.equals("/") ? "" : url;

                var dir = urlDir._1()._2();

                var rw_ro = urlDir._2();

                if( url2dir == null ) url2dir = new LinkedHashMap<>();
                url2dir.computeIfAbsent(url, x -> new LinkedHashSet<>()).add(new DirBinding(dir, rw_ro.equalsIgnoreCase("rw")));
            }
        );

        // bind <url> <dir>
        cmdLine.add(
            cmdMatch(Cmd.Bind).plus(Arg.StringArg).skipLeft().plus(Arg.StringArg),
            urlDir -> {
                var url = urlDir._1();
                url = url.equals("/") ? "" : url;
                var dir = urlDir._2();

                if( url2dir == null ) url2dir = new LinkedHashMap<>();
                url2dir.computeIfAbsent(url, x -> new LinkedHashSet<>()).add(new DirBinding(dir, false));
            }
        );

        // basedir <dir>
        cmdLine.add(
            cmdMatch(Cmd.BaseDir).plus(Arg.StringArg).skipLeft(),
            str -> this.baseDir = Paths.get(str)
        );

        // host <id or dns_name>
        cmdLine.add(
            cmdMatch(Cmd.Host).plus(Arg.StringArg).skipLeft(),
            str -> this.ip = str
        );

        var midiCmdPrefix = cmdMatch(Cmd.Midi).plus(Arg.enumArg(MidiCmd.class, true)).skipLeft();
        var midiCmdList = midiCmdPrefix.flatMap(c -> c == MidiCmd.List ? Optional.of(c) : Optional.empty());
        var midiUse = midiCmdPrefix.flatMap(c -> c == MidiCmd.Use ? Optional.of(c) : Optional.empty());
        var midiUseAny = midiUse.plus(Arg.StringArg).skipLeft().flatMap(a -> a.equalsIgnoreCase("any") ? Optional.of(true) : Optional.empty());
        var midiUseByIdx = midiUse.plus(Arg.StringArg).skipLeft()
            .flatMap(a -> a.equalsIgnoreCase("idx") ? Optional.of(true) : Optional.empty())
            .plus(Arg.IntArg).skipLeft();

        // midi list
        cmdLine.add(
            midiCmdList,
            a -> listMidiDevices()
        );

        // midi use any
        cmdLine.add(midiUseAny, a -> midiDeviceSupplier = Optional.of(() ->
            MidiSelection.open(MidiSelection.Predicates.selectMidiAny())
        ));

        // midi use idx <num>
        cmdLine.add(midiUseByIdx, idx -> midiDeviceSupplier = Optional.of(() -> MidiSelection.open(MidiSelection.Predicates.selectMidiByIdx(idx))));

        // midi use name = <name>
        cmdLine.add(
            midiUse
                .andThenExpect(true, "name").skipLeft()
                .andThenExpect(true, "=").skipLeft()
                .plus(Arg.StringArg).skipLeft(),
            name ->
                midiDeviceSupplier = Optional.of(() -> MidiSelection.open(MidiSelection.Predicates.selectMidiByName(name)))
        );

        // midi use name ~ <name>
        cmdLine.add(
            midiUse
                .andThenExpect(true, "name").skipLeft()
                .andThenExpect(true, "~").skipLeft()
                .plus(Arg.StringArg).skipLeft(),
            name ->
                midiDeviceSupplier = Optional.of(() -> MidiSelection.open(MidiSelection.Predicates.selectMidiByNameWildcard(name)))
        );

        var midiApi = cmdMatch(Cmd.Midi).andThenExpect(true, "api").skipLeft();

        // midi api client _url_
        // midi api client off | null
        cmdLine.add(
            midiApi.andThenExpect(true, "client").skipLeft().plus(Arg.StringArg).skipLeft(),
            url -> {
                if( url.matches("(?is)null|off") ) {
                    clientApi = null;
                } else if( url.startsWith("/") ) {
                    clientApi = url;
                }
            }
        );


        // midi api emu _url_
        // midi api emu off | null
        cmdLine.add(
            midiApi.andThenExpect(true, "emu").skipLeft().plus(Arg.StringArg).skipLeft(),
            url -> {
                if( url.matches("(?is)null|off") ) {
                    emuApi = null;
                } else if( url.startsWith("/") ) {
                    emuApi = url;
                }
            }
        );

        // index url _url_
        cmdLine.add(
            cmdMatch(Cmd.Index)
                .andThenExpect(true, "url").skipLeft()
                .plus(Arg.StringArg).skipLeft(),
            url -> {indexFileApi().urlPath(url);}
        );

        // index off
        cmdLine.add(
            cmdMatch(Cmd.Index)
                .andThenExpect(true, "off").skipLeft(),
            url -> {indexFileApi = null;}
        );

        // index file _file_
        cmdLine.add(
            cmdMatch(Cmd.Index)
                .andThenExpect(true, "file").skipLeft()
                .plus(Arg.StringArg).skipLeft(),
            file -> {indexFileApi().source().file(file);}
        );

        // index set _key_ = _value_
        cmdLine.add(
            cmdMatch(Cmd.Index)
                .andThenExpect(true, "set").skipLeft()
                .plus(Arg.StringArg).skipLeft()
                .andThenExpect(true, "=").skipRight()
                .plus(Arg.StringArg),
            (keyValue) -> {indexFileApi().params().set(keyValue._1(), keyValue._2());}
        );
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private Optional<Supplier<MidiDevice>> midiDeviceSupplier = Optional.empty();

    private final List<Consumer<ServiceBuilder.CConector>> connectorConf = new ArrayList<>();

    private List<Consumer<ServiceBuilder.CConector>> connectorConf() {return connectorConf;}

    private final List<Consumer<ServiceBuilder.QueuedThreadPoolConfig>> httpThreadConf = new ArrayList<>();

    private List<Consumer<ServiceBuilder.QueuedThreadPoolConfig>> httpThreadConf() {return httpThreadConf;}

    public Service start() {
        var b = Service.builder();

        if( !connectorConf.isEmpty() ) {
            b.connector(cc -> {
                connectorConf.forEach(conf -> conf.accept(cc));
            });
        }

        if( !httpThreadConf.isEmpty() ) {
            b.queuedThreadPool(cc -> {
                httpThreadConf.forEach(conf -> conf.accept(cc));
            });
        }

        System.out.println("port " + port);
        b.port(port);

        if( ip != null ) {
            System.out.println("host " + ip);
            b.ip(ip);
        }

        if( indexFileApi != null ) {
            System.out.println("bind index on "+indexFileApi.urlPath());
            b.addHandler(indexFileApi.build());
        }

        int mappedStatic = 0;
        if( url2dir != null ) {
            for( var urlPrefE : url2dir.entrySet() ) {
                var urlPref = urlPrefE.getKey();
                for( var dir : urlPrefE.getValue() ) {
                    if( dir == null ) continue;

                    System.out.println("bind url $url to dir $dir, writeable=$rw"
                        .replace("$url", urlPref.isEmpty() ? "/" : urlPref)
                        .replace("$dir", dir.dir())
                        .replace("$rw", dir.writeable()+"")
                    );

                    var sf = StaticFiles.dir(Paths.get(dir.dir())).uriBase(urlPref);
                    if( dir.writeable() ){
                        sf = sf.allow( allow -> allow
                            .upload(true)
                            .delete(true)
                            .rename(true)
                            .mkdir(true)
                        );
                    }

                    b.addHandler(
                        sf.buildRouter()
                    );

                    mappedStatic++;
                }
            }
        }

        if( mappedStatic == 0 ) {
            System.out.println("bind url $url to dir $dir"
                .replace("$url", "/")
                .replace("$dir", baseDir.toString())
            );
            b.addHandler(
                StaticFiles.dir(baseDir).uriBase("").buildRouter()
            );
        }

        //noinspection rawtypes
        MidiPortIn midiPortIn;
        if( midiDeviceSupplier.isPresent() ) {
            System.out.println("find midi device");
            var midiDevice = midiDeviceSupplier.get().get();

            var di = midiDevice.getDeviceInfo();
            System.out.println("create midi port in for device: name=" + di.getName() + " vendor=" + di.getVendor() + " version=" + di.getVersion() + " description=" + di.getDescription());
            midiPortIn = new JavaMidiPortIn(midiDevice);
        } else {
            System.out.println("create dummy port in");
            var dummy = new MidiPortIn.Dummy();
            midiPortIn = dummy;

            if( emuApi != null ) {
                System.out.println("bind MidiPortEmuApi on " + emuApi);
                b.addHandlers(emuApi, () -> new MidiPortEmuApi(dummy));
            }
        }

        var midiClientScheduler = Executors.newScheduledThreadPool(1, r -> {
            var th = new Thread(r);
            th.setName("midi-clients-gc");
            th.setDaemon(true);
            return th;
        });
        var midiClients = new MidiInputClients(midiPortIn, midiClientScheduler);

        if( clientApi != null ) {
            System.out.println("bind MidiClientApi on " + clientApi);
            b.addHandlers(clientApi, () -> new MidiClientApi(midiClients));
        }

        return b.start();
    }

    private void listMidiDevices() {
        var devices = MidiSelection.list();
        System.out.println("devices (" + devices.size() + "):");
        for( var d : devices ) {
            System.out.println("  " + d);
        }
    }

}
