package xyz.cofe.mitrenier.web.main;

public class DebugRun1 {
    public static void main(String[] args){
        StaticMainWeb.main(new String[]{
            "port",  "8080",
            "index", "file", "/home/user/code/midi/rust/wasm-app-ui/dist/index.html",
            "index", "set",      "MIDIED_MIDI_READ_HTTP","=","http://localhost:8080/client",
            "bind",  "/emu/ui",  "/home/user/code/midi/android/webserver/webserver-app/src/main/resources/static/",
            "bind",  "/",        "/home/user/code/midi/rust/wasm-app-ui/dist",
            "bind",  "/home",    "/home/user/code/midi/rust/wasm-app-ui/test-data","rw",
            "index", "set",      "MIDIED_FS_HOME_TYPE","=","http",
            "index", "set",      "MIDIED_FS_HOME_MOUNT","=","http://localhost:8080/home",
            "threads", "idle", "15s",
            "threads","min","2",
            "threads","max","10",
            "connector","idle","15s"
        });
    }
}
