package xyz.cofe.mitrenier.andr.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import androidx.appcompat.app.AppCompatActivity;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.cofe.mitrenier.R;
import xyz.cofe.mitrenier.andr.AppServices;

public class MainActivity extends AppCompatActivity {
    private static final Logger log = LoggerFactory.getLogger(MainActivity.class);
    private WebView webView;

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //////////////////
        webView = findViewById(R.id.webv);
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        webView.setClickable(true);

        onDestroy(()->{
            webView.loadUrl("about:blank");
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
            webView = null;
        });

        var webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true); // Важно для современных сайтов
        webSettings.setDomStorageEnabled(true); // Для localStorage
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setBuiltInZoomControls(true); // Включает зум
        webSettings.setDisplayZoomControls(false); // Скрывает нативные контролы зума
        webSettings.setSupportZoom(true);

        // Для Android 5.0+ разрешаем mixed content (HTTP в HTTPS)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            //webView.setMixedContentMode(WebView.MIXED_CONTENT_COMPATIBILITY_MODE);
        }

        Function<Integer,String> startPage = port ->
            "http://localhost:"+port+"/index.html";

        AppServices.getInstance().getHttpPort().ifPresentOrElse(port -> {
            var url = startPage.apply(port);
            log.info("navigate "+url);
            webView.loadUrl(url);
        }, ()->{
            log.info("listen for http start");
            //noinspection resource
            AppServices.getInstance().httpServerStarted.listen(serverInfo -> {
                serverInfo.getHttpPort().ifPresent( port -> {
                    var url = startPage.apply(port);
                    log.info("get event http start, navigate "+url);
                    webView.loadUrl(url);
                });
            });
        });

        setupFullscreenMode();

        //////////////////////////////
        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_SEND.equals(action) && type != null) {
            handleReceivedFile(intent);
        } else if (Intent.ACTION_VIEW.equals(action)) {
            Uri uri = intent.getData();
            if (uri != null) {
                handleFileFromUri(uri);
            }
        }
    }

    //region onDestroy
    private final List<Runnable> closeables = new CopyOnWriteArrayList<>();

    public void onDestroy(Runnable execOnDestroy) {
        if( execOnDestroy == null ) throw new IllegalArgumentException("execOnDestroy==null");
        closeables.add(execOnDestroy);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        for( var r : closeables ){
            r.run();
        }
        closeables.clear();
    }
    //endregion

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if( item.getItemId() == R.id.webServer ){
            //openActivity(WebServerActivity.class);
            return true;
        } else if( item.getItemId() == R.id.intervals_mi ){
            //openActivity(IntervalAnalyzeActivity.class);
            return true;
        } else if( item.getItemId() == R.id.anima_mi ){
            //openActivity(AnimaActivity.class);
            return true;
        } else if( item.getItemId() == android.R.id.home ){
            // Кнопка "назад"
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void openActivity(Class<? extends Activity> cls){
        var intent = new Intent(MainActivity.this, cls);
        startActivity(intent);
    }

    private void handleReceivedFile(Intent intent) {
        Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (uri != null) {
            handleFileFromUri(uri);
        }
    }

    private void handleFileFromUri(Uri uri) {
    }

    private void setupFullscreenMode() {
        if ( Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Для Android 11+ (API 30+)
            final WindowInsetsController insetsController = getWindow().getInsetsController();
            if (insetsController != null) {
                insetsController.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                //insetsController.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);

                // 👇 Корректное поведение в зависимости от версии Android
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // Android 12+: используем DEFAULT + явно управляем показом при необходимости
                    insetsController.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_DEFAULT);
                } else {
                    // Android 11: можно использовать временный показ по свайпу
                    insetsController.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                }
            }
        } else {
            // Для Android 10 (API 29) и ниже
            View decorView = getWindow().getDecorView();
            int uiOptions = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN;

            decorView.setSystemUiVisibility(uiOptions);

            // Критично для WebView: предотвращает "просветы" при скрытии системного UI
            getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            );

            // Дополнительная настройка для WebView
            if (webView != null) {
                WebSettings webSettings = webView.getSettings();
                webSettings.setUseWideViewPort(true);
                webSettings.setLoadWithOverviewMode(true);

                // Если ваш сайт поддерживает полноэкранный режим
                webSettings.setDisplayZoomControls(false);
            }

//            // Слушатель для обработки изменений системного UI
//            decorView.setOnSystemUiVisibilityChangeListener(visibility -> {
//                if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
//                    // Если системный UI стал видимым, снова скрываем его
//                    decorView.setSystemUiVisibility(uiOptions);
//                }
//            });
        }

        // Для WebView важно добавить этот обработчик
        if (webView != null) {
            webView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                setupFullscreenMode(); // Скрываем UI при прокрутке
            });

            // Обработчик для повторного показа клавиатуры при потере фокуса
            webView.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    v.requestFocus(View.FOCUS_DOWN);
                    // Принудительно показываем клавиатуру через JS-interop (опционально)
                    return false;
                }
                return false;
            });
        }
    }
}
