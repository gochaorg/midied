package xyz.cofe.nipal;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ThreadPool;
import xyz.cofe.coll.im.ImList;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Билдер для создания и настройки сервиса на основе сервера Jetty.
 * Позволяет настраивать порт, IP-адрес, параметры соединения, обработчики
 * и потоки выполнения для сервера.
 */
public class ServiceBuilder {
    /**
     * Список конфигураторов для соединителя сервера.
     */
    private final List<Consumer<ServerConnector>> connectorConfig = new ArrayList<>();

    /**
     * Порт, на котором будет работать сервер.
     */
    private int port = 8080;
    /**
     * Устанавливает порт для сервера.
     *
     * @param port Номер порта
     * @return Текущий экземпляр билдера для цепочечного вызова
     */
    public ServiceBuilder port(int port){
        this.port = port;
        return this;
    }

    /**
     * IP-адрес, на котором будет работать сервер.
     */
    private String ip;
    /**
     * Устанавливает IP-адрес для сервера.
     *
     * @param ip IP-адрес
     * @return Текущий экземпляр билдера для цепочечного вызова
     */
    public ServiceBuilder ip(String ip){
        this.ip = ip;
        return this;
    }

    //region connector config
    /**
     * Добавляет конфигурацию соединителя через билдер.
     *
     * @param builder Функция для настройки соединителя
     * @return Текущий экземпляр билдера для цепочечного вызова
     */
    public ServiceBuilder connector(Consumer<CConector> builder){
        if( builder==null ) throw new IllegalArgumentException("builder==null");
        builder.accept(new CConector());
        return this;
    }

    /**
     * Базовый класс для конфигурации соединителя.
     *
     * @param <SELF> Тип самого себя для цепочечного вызова
     */
    @SuppressWarnings("UnusedReturnValue")
    public class ConnectorConfig<SELF> {
        /**
         * Ссылка на экземпляр для цепочечного вызова.
         */
        protected SELF self;

        public ConnectorConfig(SELF self) {
            this.self = self;
        }

        /**
         * Устанавливает таймаут бездействия для соединителя.
         *
         * @param duration Продолжительность таймаута
         * @return Экземпляр для цепочечного вызова
         */
        public SELF idleTimeout(Duration duration){
            if( duration==null ) throw new IllegalArgumentException("duration==null");
            connectorConfig.add( connector -> {
                connector.setIdleTimeout(duration.toMillis());
            });
            return self;
        }

        /**
         * Устанавливает размер очереди для принятия соединений.
         *
         * @param value Размер очереди
         * @return Экземпляр для цепочечного вызова
         */
        public SELF acceptQueueSize(int value){
            connectorConfig.add( connector -> {
                connector.setAcceptQueueSize(value);
            });
            return self;
        }

        /**
         * Позволяет настроить соединитель напрямую.
         *
         * @param conf Функция конфигурации соединителя
         * @return Экземпляр для цепочечного вызова
         */
        public SELF configure(Consumer<ServerConnector> conf){
            if( conf==null ) throw new IllegalArgumentException("conf==null");
            connectorConfig.add(conf);
            return self;
        }
    }

    /**
     * Конкретная реализация конфигурации соединителя.
     */
    public class CConector extends ConnectorConfig<CConector> {
        public CConector() {
            super(null);
            self = this;
        }
    }
    //endregion

    //region handlers
    /**
     * Список обработчиков запросов.
     */
    private final List<Handler> handlers = new ArrayList<>();

    /**
     * Добавляет обработчик запросов.
     *
     * @param h Обработчик
     * @return Текущий экземпляр билдера для цепочечного вызова
     */
    public ServiceBuilder addHandler( Handler h ){
        if( h==null ) throw new IllegalArgumentException("h==null");
        handlers.add(h);
        return this;
    }

    /**
     * Добавляет обработчик с префиксом пути.
     *
     * @param prefix Префикс пути
     * @param h      Поставщик обработчика
     * @return Текущий экземпляр билдера для цепочечного вызова
     */
    public ServiceBuilder addHandler(String prefix, Supplier<Handler> h ){
        if( h==null ) throw new IllegalArgumentException("h==null");
        if( prefix==null ) throw new IllegalArgumentException("prefix==null");
        handlers.add(RequestRouter.prefix(prefix, h));
        return this;
    }

    /**
     * Добавляет несколько обработчиков.
     *
     * @param handlers Коллекция обработчиков
     * @return Текущий экземпляр билдера для цепочечного вызова
     */
    public ServiceBuilder addHandlers( Iterable<Handler> handlers ){
        if( handlers==null ) throw new IllegalArgumentException("handlers==null");
        for( var h : handlers ) {
            this.handlers.add(h);
        }
        return this;
    }

    /**
     * Добавляет несколько обработчиков с префиксом пути.
     *
     * @param prefix   Префикс пути
     * @param handlers Поставщик итерируемой коллекции обработчиков
     * @return Текущий экземпляр билдера для цепочечного вызова
     */
    public ServiceBuilder addHandlers( String prefix, Supplier<Iterable<Handler>> handlers ){
        if( handlers==null ) throw new IllegalArgumentException("handlers==null");
        if( prefix==null ) throw new IllegalArgumentException("prefix==null");
        this.handlers.add(RequestRouter.prefixes(prefix,handlers));
        return this;
    }

    /**
     * Добавляет маршрутизатор запросов.
     *
     * @param route Функция для настройки маршрутизатора
     * @return Текущий экземпляр билдера для цепочечного вызова
     */
    public ServiceBuilder addRouter(Function<RequestRouter,Handler> route){
        if( route==null ) throw new IllegalArgumentException("route==null");

        var h = route.apply(new RequestRouter(ImList.of()));
        if( h!=null ){
            handlers.add(h);
        }

        return this;
    }

    /**
     * Создает объединенный обработчик из всех добавленных обработчиков.
     *
     * @return Объединенный обработчик
     */
    private Handler createHandler(){
        Handler.Sequence handlerSeq = new Handler.Sequence();
        handlerSeq.setHandlers(handlers);

        return handlerSeq;
    }
    //endregion

    /**
     * Запускает сервер и создает сервис.
     *
     * @return Новый экземпляр сервиса
     */
    public Service start(){
        var srvr = createServer();
        try {
            srvr.start();
        } catch ( Exception e ) {
            throw new RuntimeException(e);
        }

        return new Service(srvr);
    }

    /**
     * Поставщик пула потоков для сервера.
     */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private Optional<Supplier<ThreadPool>> serverThreadPoolBuilder = Optional.empty();

    /**
     * Абстрактный класс для конфигурации пула потоков.
     *
     * @param <SELF> Тип самого себя для цепочечного вызова
     */
    public abstract static class ThreadConfigure<SELF> implements Supplier<ThreadPool> {
        /**
         * Ссылка на экземпляр для цепочечного вызова.
         */
        protected SELF self;

        /**
         * Создает пул потоков с заданной конфигурацией.
         *
         * @return Новый пул потоков
         */
        public abstract ThreadPool threadPool();

        @Override
        public ThreadPool get() {
            return threadPool();
        }
    }

    /**
     * Конфигурация пула очередей для потоков.
     */
    @SuppressWarnings("UnusedReturnValue")
    public static class QueuedThreadPoolConfig extends ThreadConfigure<QueuedThreadPoolConfig> {
        /**
         * Список конфигураторов пула потоков.
         */
        private final List<Consumer<QueuedThreadPool>> config = new ArrayList<>();

        /**
         * Устанавливает режим демона для потоков.
         *
         * @param value true для потоков-демонов, false иначе
         * @return Текущий экземпляр конфигурации для цепочечного вызова
         */
        public  QueuedThreadPoolConfig daemon(boolean value){
            config.add(tp -> tp.setDaemon(value));
            return this;
        }

        /**
         * Устанавливает максимальное количество потоков.
         *
         * @param count Максимальное количество потоков
         * @return Текущий экземпляр конфигурации для цепочечного вызова
         */
        public  QueuedThreadPoolConfig maxThreads(int count){
            config.add(tp -> tp.setMaxThreads(count));
            return this;
        }

        /**
         * Устанавливает минимальное количество потоков.
         *
         * @param count Минимальное количество потоков
         * @return Текущий экземпляр конфигурации для цепочечного вызова
         */
        public  QueuedThreadPoolConfig minThreads(int count){
            config.add(tp -> tp.setMinThreads(count));
            return this;
        }

        /**
         * Устанавливает таймаут бездействия для потоков.
         *
         * @param idle Время бездействия
         * @return Текущий экземпляр конфигурации для цепочечного вызова
         */
        public  QueuedThreadPoolConfig idleTimeout(Duration idle){
            if( idle==null ) throw new IllegalArgumentException("idle==null");
            config.add(tp -> tp.setIdleTimeout( (int)Math.min(idle.toMillis(), Integer.MAX_VALUE) ));
            return this;
        }

        /**
         * Устанавливает количество зарезервированных потоков.
         *
         * @param count Количество зарезервированных потоков
         * @return Текущий экземпляр конфигурации для цепочечного вызова
         */
        public  QueuedThreadPoolConfig reservedThreads(int count){
            config.add(tp -> tp.setReservedThreads(count));
            return this;
        }

        @Override
        public ThreadPool threadPool() {
            QueuedThreadPool pool = new QueuedThreadPool();
            config.forEach( c -> c.accept(pool));
            return pool;
        }
    }

    /**
     * Настраивает пул очередей для потоков сервера.
     *
     * @param config Функция конфигурации пула очередей
     * @return Текущий экземпляр билдера для цепочечного вызова
     */
    public ServiceBuilder queuedThreadPool(Consumer<QueuedThreadPoolConfig> config){
        if( config==null ) throw new IllegalArgumentException("config==null");
        QueuedThreadPoolConfig cfg = new QueuedThreadPoolConfig();
        config.accept(cfg);
        serverThreadPoolBuilder = Optional.of(cfg);
        return this;
    }

    /**
     * Создает сервер с заданной конфигурацией.
     *
     * @return Новый экземпляр сервера
     */
    private Server createServer(){
        Server server =
            serverThreadPoolBuilder.isEmpty()
                ? new Server()
                : new Server(serverThreadPoolBuilder.get().get());

        var conn = new ServerConnector(server);
        if( ip!=null )conn.setHost(ip);
        conn.setPort(port);
        connectorConfig.forEach( conf -> conf.accept(conn));

        conn.setPort(port);
        server.addConnector(conn);

        server.setDefaultHandler(new DefaultHandler());
        server.setHandler(createHandler());

        return server;
    }

}