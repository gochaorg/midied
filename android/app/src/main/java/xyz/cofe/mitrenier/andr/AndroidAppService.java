package xyz.cofe.mitrenier.andr;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.cofe.mitrenier.R;

public class AndroidAppService extends Service {
    private static final Logger log = LoggerFactory.getLogger(AndroidAppService.class);

    // ID уведомления и канала (любые уникальные числа/строки)
    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "MitrenierServiceChannel";

    @Override
    public void onCreate() {
        super.onCreate();
        AppServices.init(getApplicationContext());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 1. Создаем канал уведомлений (обязательно для Android 8.0+)
        if ( Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "App Service Channel",
                NotificationManager.IMPORTANCE_LOW // Низкий приоритет, чтобы не шуметь
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }

        // 2. Создаем уведомление
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Mitrenier Service")
            .setContentText("Работает в фоне")
            //.setSmallIcon(android.R.drawable.ic_dialog_info) // Замените на свой иконку
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Замените на свой иконку
            .build();

        // 3. КРИТИЧЕСКИ ВАЖНО: Вызываем startForeground
        startForeground(NOTIFICATION_ID, notification);

        // Здесь можно выполнить фоновую работу
        // START_NOT_STICKY: Не перезапускать сервис, если его убьет система
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        try {
            AppServices.getInstance().close();
        }catch ( IllegalStateException e ){
            log.error("can't close app services");
        }
        //
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        // Возвращаем null, так как сервис не поддерживает привязку (binding)
        return null;
    }
}
