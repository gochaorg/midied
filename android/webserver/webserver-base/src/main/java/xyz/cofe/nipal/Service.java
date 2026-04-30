package xyz.cofe.nipal;

import org.eclipse.jetty.server.Server;

/**
 * Класс, представляющий сервис, основанный на сервере Jetty.
 * Обеспечивает управление жизненным циклом сервера: запуск, остановка и проверка состояния.
 */
public class Service {
    /**
     * Сервер Jetty, которым управляет этот сервис.
     */
    public final Server server;

    /**
     * Создает новый экземпляр сервиса с указанным сервером.
     *
     * @param server Экземпляр сервера Jetty
     * @throws IllegalArgumentException если server равен null
     */
    public Service(Server server){
        if( server==null ) throw new IllegalArgumentException("server==null");
        this.server = server;
    }

    /**
     * Останавливает сервер.
     * Метод является потокобезопасным и выполняет синхронизированный доступ к серверу.
     *
     * @return Текущий экземпляр сервиса для цепочечного вызова
     */
    public Service stop(){
        synchronized(this){
            if( server!=null ){
                try {
                    server.stop();
                } catch ( Exception e ) {
                    throw new RuntimeException(e);
                }
            }
        }
        return this;
    }

    /**
     * Проверяет, запущен ли сервер.
     *
     * @return true, если сервер запущен, иначе false
     */
    public boolean isRunning(){
        var srv = server;
        return srv != null && srv.isRunning();
    }

    /**
     * Создает новый билдер для построения сервиса.
     *
     * @return Новый экземпляр ServiceBuilder
     */
    public static ServiceBuilder builder(){
        return new ServiceBuilder();
    }
}