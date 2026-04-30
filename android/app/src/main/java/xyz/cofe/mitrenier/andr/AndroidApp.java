package xyz.cofe.mitrenier.andr;

import android.app.Application;
import android.content.Intent;
import android.os.Build;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AndroidApp extends Application {
    private static final Logger log = LoggerFactory.getLogger(AndroidApp.class);

    @Override
    public void onCreate() {
        super.onCreate();

        Intent serviceIntent = new Intent(this, AndroidAppService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Для Android 8+ (Oreo и выше) нужно использовать startForegroundService
            startForegroundService(serviceIntent);
        } else {
            // На старых версиях просто startService
            startService(serviceIntent);
        }
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
    }
}
