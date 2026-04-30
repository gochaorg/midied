Проблема «первый запрос проходит, остальные зависают» при запуске embedded Jetty на Android — это классическая ситуация, связанная с особенностями работы ART (Android Runtime), ограничениями потоков и реализацией NIO (Non-blocking I/O) в Android.

Jetty 12 (на базе EE10) значительно изменил архитектуру по сравнению с Jetty 9/10, что добавляет специфических требований.

Вот основные причины и пошаговое руководство по правильной настройке.

---

### Почему это происходит? (Диагностика)

1.  **Исчерпание пула потоков (Thread Pool Starvation):**
    *   По умолчанию Jetty создает пул потоков. Если первый запрос занимает поток, и этот поток не возвращается в пул (например, из-за исключения, которое проглочено, или блокировки на I/O), а размер пула мал (или равен 1), второй запрос будет ждать вечно.
    *   **Android специфика:** Android агрессивно ограничивает фоновые потоки.

2.  **Проблемы с NIO Selector (WakeUp):**
    *   Jetty использует `java.nio.channels.Selector`. В некоторых версиях Android реализация `selector.wakeup()` работала некорректно. Если сервер ждет данных на сокете (Keep-Alive), но селектор не «просыпается» для следующего события, запрос висит.

3.  **HTTP Keep-Alive и Content-Length:**
    *   Если клиент отправляет запрос с `Connection: keep-alive`, Jetty держит соединение открытым. Если на стороне Android не настроен `idleTimeout` или есть ошибка в чтении потока, сервер ждет продолжения данных, которые не придут.

4.  **Classloader и ресурсы:**
    *   Jetty 12 сильно зависит от контекстного ClassLoader. В Android он отличается от стандартного JVM. Если Jetty не может найти ресурсы (webapp, servlets), он может вести себя непредсказуемо.

5.  **Жизненный цикл процесса:**
    *   Если сервер запущен в `Activity`, при повороте экрана или сворачивании процесс может быть убит или приостановлен, что обрывает соединения.

---

### Как правильно готовить Embedded Jetty 12 для Android

#### 1. Зависимости (Gradle)
Jetty 12 требует Java 17+. Для Android нужно включить десугаринг.

```groovy
// build.gradle (Module)
android {
    compileOptions {
        coreLibraryDesugaringEnabled true
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
}

dependencies {
    coreLibraryDesugaring 'com.android.tools:desugar_jdk_libs:2.0.4'
    
    // Jetty 12 (EE10)
    implementation 'org.eclipse.jetty:jetty-server:12.0.x'
    implementation 'org.eclipse.jetty:jetty-servlet:12.0.x'
    implementation 'org.eclipse.jetty.ee10:jetty-ee10-servlet:12.0.x'
    // Если нужен HTTP/2 (лучше отключить для стабильности на Android)
    // implementation 'org.eclipse.jetty.http2:jetty-http2-server:12.0.x' 
}
```

#### 2. Настройка сервера (Критические моменты)

Вам нужно явно настроить пул потоков, таймауты и отключить лишние модули.

```java
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;

public class AndroidJettyService {
    private Server server;

    public void start(int port) throws Exception {
        // 1. Явно настраиваем пул потоков
        // Min 2, Max 10, Idle 60s. Не делайте max слишком большим.
        QueuedThreadPool threadPool = new QueuedThreadPool(10, 2, 60000);
        threadPool.setName("jetty-thread-pool");
        
        server = new Server(threadPool);

        // 2. Настройка коннектора
        ServerConnector connector = new ServerConnector(server);
        connector.setPort(port);
        
        // ВАЖНО: Таймаут простоя. Если клиент держит соединение, но молчит - закрывать.
        connector.setIdleTimeout(30000); 
        // Лимит приемочной очереди
        connector.setAcceptQueueSize(100); 
        
        server.addConnector(connector);

        // 3. Контекст
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        
        // Пример сервлета
        context.addServlet(new ServletHolder(new MyServlet()), "/*");
        
        server.setHandler(context);

        // 4. Запуск в отдельном потоке (НИКОГДА не в Main Thread)
        new Thread(() -> {
            try {
                server.start();
                server.join();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void stop() throws Exception {
        if (server != null) {
            server.stop();
        }
    }
}
```

#### 3. ProGuard / R8 Правила
Jetty использует отражение (Reflection). Без правил обфускации сервер упадет или будет работать некорректно.
Добавьте в `proguard-rules.pro`:

```proguard
-keep class org.eclipse.jetty.** { *; }
-keep class org.eclipse.jetty.ee10.** { *; }
-dontwarn org.eclipse.jetty.**
-keepclassmembers class * {
    @org.eclipse.jetty.util.annotation.ManagedAttribute *;
    @org.eclipse.jetty.util.annotation.ManagedObject *;
}
```

#### 4. Запуск в Foreground Service
Android убивает фоновые процессы. Сервер должен жить в `Service`, желательно `ForegroundService` (с уведомлением), чтобы система не убила его при нехватке памяти.

```java
// В AndroidManifest.xml
<service android:name=".JettyService" android:foregroundServiceType="dataSync" />
```

#### 5. Отладка сети (Network Security Config)
По умолчанию Android запрещает cleartext (HTTP) трафик. Разрешите localhost или 10.0.2.2 (для эмулятора).

```xml
<!-- res/xml/network_security_config.xml -->
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="true">127.0.0.1</domain>
        <domain includeSubdomains="true">localhost</domain>
    </domain-config>
</network-security-config>
```

---

### Специфические решения для проблемы "Зависание"

Если после настройки выше проблема осталась, попробуйте следующее:

1.  **Отключите HTTP/2:**
    Jetty 12 по умолчанию может пытаться negocировать HTTP/2. На Android это часто вызывает проблемы с ALPN. Убедитесь, что вы используете чистый `ServerConnector` (HTTP/1.1), а не `HTTP2ServerConnector`.

2.  **Принудительное закрытие соединений:**
    В заголовках ответа сервлета явно укажите:
    ```java
    response.setHeader("Connection", "close");
    ```
    Это заставит клиента не держать соединение открытым (Keep-Alive), что обойдет проблему с селекторами Android.

3.  **System.setProperty для NIO:**
    Иногда помогает принудительное указание реализации селектора (хотя в новых Android это менее актуально):
    ```java
    System.setProperty("org.eclipse.jetty.io.SelectorManager", "org.eclipse.jetty.io.SelectorManager");
    ```

4.  **Логирование:**
    Включите логи Jetty, чтобы увидеть, на каком этапе висит запрос.
    ```java
    import org.eclipse.jetty.util.log.Log;
    import org.eclipse.jetty.util.log.Logger;
    // В начале запуска
    Log.setLog(new Logger() { ... реализация вывода в Logcat ... });
    ```
    *Проще:* Добавьте зависимость `slf4j-android` и настройте уровень DEBUG для `org.eclipse.jetty`.

### Альтернатива (Рекомендация)

Запуск полноценного Jetty на Android — это «тяжелая артиллерия». Если вам нужен просто локальный сервер для общения между приложениями или с веб-вью:

1.  **NanoHTTPD:** Очень легкая, написана специально для embedded-систем, меньше проблем с потоками на Android.
2.  **Ktor Server (Android):** Если вы используете Kotlin, Ktor имеет лучшую поддержку корутин и может быть стабильнее на ART.
3.  **Android WebView + Interface:** Если цель — показать UI, лучше не поднимать HTTP сервер, а использовать `JavascriptInterface`.

### Чек-лист для исправления текущего бага:
1.  Увеличьте `maxThreads` в `QueuedThreadPool` (поставьте хотя бы 10).
2.  Установите `connector.setIdleTimeout(5000)`.
3.  В клиенте (который стучится на Android) убедитесь, что он закрывает поток ввода/вывода после ответа.
4.  Проверьте Logcat на наличие `Binder` ошибок или `StrictMode` предупреждений.
5.  Убедитесь, что вы не вызываете методы сервера из UI потока (Main Thread).

Чаще всего проблема решается комбинацией **явного `QueuedThreadPool`** и **уменьшения `idleTimeout`**, чтобы «мертвые» соединения не забивали селектор.