package xyz.cofe.mitrenier.andr;

import android.annotation.SuppressLint;
import android.content.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.cofe.mitrenier.Listeners;
import xyz.cofe.mitrenier.andr.http.FreePortFinder;
import xyz.cofe.mitrenier.andr.http.HttpServerInfo;
import xyz.cofe.mitrenier.andr.midi.MidiAdapter;
import xyz.cofe.mitrenier.andr.midi.http.MidiDeviceRestApi;
import xyz.cofe.mitrenier.andr.midi.http.MidiRestApi;
import xyz.cofe.mitrenier.api.server.rest.IndexFileApi;
import xyz.cofe.nipal.MimeType;
import xyz.cofe.nipal.RequestRouter;
import xyz.cofe.nipal.Service;
import xyz.cofe.nipal.files.StaticFiles;

import java.io.File;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Enumeration;
import java.util.Optional;

public class AppServices implements HttpServerInfo {
    private static final Logger log = LoggerFactory.getLogger(AppServices.class);

    @SuppressLint("StaticFieldLeak")
    private static volatile AppServices instance;

    public static AppServices getInstance() {
        if( instance != null ) return instance;
        synchronized(AppServices.class) {
            if( instance != null ) return instance;
            instance = new AppServices();
            return instance;
        }
    }

    @SuppressLint("StaticFieldLeak")
    private static volatile Context context;

    public static void init(Context context) {
        if( context == null ) throw new IllegalArgumentException("context==null");
        synchronized(AppServices.class) {
            AppServices.context = context;
            getInstance().start();
        }
    }

    private volatile Service httpService;
    private final MidiAdapter midiAdapter = new MidiAdapter();

    // todo move it
    private static String getLocalIpAddress() {
        try {
            for( Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
                 en.hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();
                for( Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses();
                     enumIpAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if( !inetAddress.isLoopbackAddress() &&
                        (inetAddress.getAddress().length == 4) ) { // IPv4
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch ( SocketException ex ) {
            ex.printStackTrace();
        }
        return "?";
    }

    private volatile Integer httpPort = null;

    @Override
    public Optional<Integer> getHttpPort() {
        return Optional.ofNullable(httpPort);
    }

    public final Listeners<HttpServerInfo, AppServices> httpServerStarted = new Listeners<>(() -> this);

    public boolean isHttpRunning() {
        var s = httpService;
        return s != null && s.isRunning();
    }

    private void start() {
        synchronized(this) {
            var freePort = new FreePortFinder().minPort(10000).maxPort(65535).preferredPort(8899, 12345, 23456).findFirstFree();
            freePort.ifPresentOrElse(
                port -> {
                    httpPort = port;
                    var ip = getLocalIpAddress();

                    try {
                        log.info(
                            "start http://${ip}:${port}"
                                .replace("${ip}", ip)
                                .replace("${port}", "" + port)
                        );
                        var serviceBuilder = Service.builder()
                            .port(port)
                            .connector(c -> c.idleTimeout(Duration.ofSeconds(30)).acceptQueueSize(100))
                            .queuedThreadPool(p -> p.idleTimeout(Duration.ofSeconds(40)).minThreads(2).maxThreads(10));

                        serviceBuilder.addHandlers(new MidiRestApi(midiAdapter));
                        log.info("http MidiRestApi mounted");

                        Path appDataDir;

                        if( context != null ) {
                            serviceBuilder.addHandlers("/device", () -> new MidiDeviceRestApi(context));
                            appDataDir = Optional.ofNullable(context.getFilesDir()).map(File::toPath).orElse(null);
                            log.info("http MidiDeviceRestApi mounted on /device");
                        } else {
                            appDataDir = null;
                            log.error("http /device not mounted, context = null");
                        }

                        log.info("mount /index.html");
                        serviceBuilder.addHandler(
                            new IndexFileApi().urlPath("/index.html")
                                .source(srcCfg -> srcCfg
                                    .resource(AppServices.class, "/wasm/index.html")
                                )
                                .params(params -> {
                                    params.set("MIDIED_MIDI_READ_HTTP",
                                        "http://${ip}:${port}/client"
                                            .replace("${ip}", ip)
                                            .replace("${port}", "" + port)
                                    );

                                    if( appDataDir!=null ){
                                        params.set("MIDIED_FS_HOME_TYPE", "http");
                                        params.set("MIDIED_FS_HOME_MOUNT",
                                            "http://localhost:${port}/app-data"
                                            .replace("${ip}", ip)
                                            .replace("${port}", "" + port)
                                        );
                                    }
                                })
                                .build());

                        if( appDataDir!=null ){
                            log.info("mount /app-data");
                            serviceBuilder.addHandler(
                                StaticFiles.dir(appDataDir).uriBase("/app-data")
                                    .allow(allow -> allow
                                        .mkdir(true)
                                        .rename(true)
                                        .delete(true)
                                        .upload(true)
                                        .abilities(true)
                                        .read(true)
                                    )
                                    .buildRouter()
                            );
                        }

                        serviceBuilder.addHandler(new StaticFiles().customFiles(AppServices.class, cf -> {
                            //cf.addResource("/index.html", "/wasm/index.html").contentType(MimeType.TEXT_HTML);

                            var jsUrl = "wasm-app-ui.js";
                            log.info("mount " + jsUrl);
                            cf.addResource(jsUrl, "/wasm/wasm-app-ui.js").contentType(MimeType.APP_JS)
                                .addPath("/" + jsUrl);

                            var wasmUrl = "wasm-app-ui_bg.wasm";
                            log.info("mount " + wasmUrl);
                            cf.addResource(wasmUrl, "/wasm/wasm-app-ui_bg.wasm").contentType(MimeType.APP_WASM)
                                .addPath("/"+wasmUrl);
                        }).buildRouter());

                        log.info("mount /ip");
                        serviceBuilder.addHandler(RequestRouter.Builder.GET().path("/ip").htmlResponse().call(() ->
                            """
                                midi http://${ip}:${port}/device
                                """
                                .replace("${ip}", ip)
                                .replace("${port}", port.toString()
                                )
                        ));

                        httpService = serviceBuilder.start();

                        httpServerStarted.fire(this);
                    } catch ( Throwable err ) {
                        log.error("can't start http", err);
                    }
                },
                () -> {}
            );

            midiAdapter.init(context);
        }
    }

    public void close() {
        synchronized(this) {
            midiAdapter.terminate();

            if( httpService != null && httpService.isRunning() ) {
                try {
                    httpService.stop();
                    httpService = null;
                } catch ( Throwable e ) {
                    log.error("can't stop http");
                }
            }
        }
    }
}
