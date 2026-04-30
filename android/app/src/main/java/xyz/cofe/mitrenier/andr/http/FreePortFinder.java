package xyz.cofe.mitrenier.andr.http;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Класс для поиска свободных портов.
 * Позволяет искать в заданном диапазоне или среди предпочтительных портов.
 */
public class FreePortFinder {
    // Настройки по умолчанию
    private String host = "0.0.0.0"; // Привязка ко всем интерфейсам

    public FreePortFinder host(String ip){
        if( ip==null ) throw new IllegalArgumentException("ip==null");
        host = ip;
        return this;
    }

    private Set<Integer> preferredPort = new LinkedHashSet<>(); // Множество предпочтительных портов (без дубликатов, с сохранением порядка)

    public FreePortFinder preferredPort(int... ports) {
        preferredPort.clear();
        for( var p : ports ) preferredPort.add(p);
        return this;
    }

    private int minPort = 2000; // Минимальный порт по умолчанию

    public FreePortFinder minPort(int port) {
        minPort = port;
        maxPort = Math.max(maxPort, port);
        return this;
    }

    private static final int MAX_PORT = 65535; // Максимальный номер порта
    private int maxPort = MAX_PORT; // Максимальный порт по умолчанию

    public FreePortFinder maxPort(int port) {
        maxPort = port;
        minPort = Math.min(minPort, port);
        return this;
    }

    public Scope scope(){
        return new Scope(host,new LinkedHashSet<>(preferredPort),minPort,maxPort);
    }

    public Optional<Integer> findFirstFree(){
        return scope().findFirstFreeParallel();
    }

    public Set<Integer> findAllFree(){
        return scope().findAllParallel();
    }

    /**
     * Внутренняя структура данных для хранения настроек поиска.
     * Использует Record для удобства и неизменяемости.
     */
    public record Scope(
        String host,          // IP-адрес, на котором проверяется порт
        Set<Integer> preferredPort, // Предпочтительные порты для проверки
        int minPort,          // Минимальный порт диапазона
        int maxPort           // Максимальный порт диапазона
    )
    {
        // Конструктор record автоматически генерирует проверки на null для полей,
        // но здесь мы добавляем свои валидации в компактной форме.
        public Scope {
            if( host == null ) throw new IllegalArgumentException("host==null");
            if( preferredPort == null ) throw new IllegalArgumentException("preferredPort==null");
            if( maxPort < minPort ) throw new IllegalArgumentException("maxPort<minPort");
            if( minPort < 1 ) throw new IllegalArgumentException("minPort<1");
            if( maxPort > MAX_PORT ) throw new IllegalArgumentException("maxPort>" + MAX_PORT);
        }

        /**
         * Возвращает множество всех портов, которые нужно проверить.
         * Включает сначала предпочтительные, потом все порты в диапазоне.
         */
        private Set<Integer> portSet() {
            LinkedHashSet<Integer> ports = new LinkedHashSet<>();
            ports.addAll(preferredPort);
            // Исправлено: раньше было p <= minPort, что давало только один порт
            for( var p = minPort; p <= maxPort; p++ ) {
                ports.add(p);
            }
            return ports;
        }

        /**
         * Последовательный поиск первого свободного порта.
         * Проверяет порты по одному в порядке, определенном portSet().
         * Блокирует текущий поток до завершения.
         *
         * @return Optional с номером порта или пустой Optional, если не найден.
         */
        public Optional<Integer> findFirstFreeSequentially() {
            for( var port : portSet() ) {
                if( isPortAvailable(host, port) ) {
                    return Optional.of(port); // Нашли первый свободный
                }
            }
            return Optional.empty(); // Ни один не подошел
        }

        /**
         * Параллельный поиск первого свободного порта.
         * Запускает проверку всех портов одновременно.
         * Возвращает первый найденный свободный порт.
         * Блокирует текущий поток до завершения.
         *
         * @return Optional с номером порта или пустой Optional, если не найден.
         */
        public Optional<Integer> findFirstFreeParallel() {
            // CompletableFuture, который завершится, когда будет найден первый свободный порт
            CompletableFuture<Optional<PortStatus>> firstFree = new CompletableFuture<>();

            List<CompletableFuture<?>> futures = new ArrayList<>();
            for( var port : portSet() ) {
                futures.add(
                    checkFree(port, free -> {
                        // Если порт свободен, завершаем основной future с результатом
                        firstFree.complete(Optional.of(free));
                    }));
            }

            // CompletableFuture, который завершится, когда завершатся ВСЕ проверки
            var waitAll = CompletableFuture.allOf(futures.toArray(new CompletableFuture[]{}));
            waitAll.thenRun(() -> {
                // Если все проверки завершены, и никто не вызвал firstFree.complete(),
                // значит, все порты были заняты. Завершаем основной future с пустым значением.
                // Если firstFree уже завершен (например, потому что порт был найден),
                // повторный вызов complete будет проигнорирован.
                firstFree.complete(Optional.empty());
            });

            // Блокируем текущий поток, дожидаясь результата
            return firstFree.join().map(p -> p.port);
        }

        /**
         * Асинхронная проверка конкретного порта.
         *
         * @param port      Номер порта для проверки.
         * @param foundFree Callback, который вызт оказался свободен.
         * @return CompletableFuture с результатом проверки (PortStatus).
         */
        private CompletableFuture<PortStatus> checkFree(int port, Consumer<PortStatus> foundFree) {
            return CompletableFuture.supplyAsync(() -> {
                boolean free = isPortAvailable(host, port);
                PortStatus status = new PortStatus(host, port, free);
                if( free && foundFree != null ) {
                    foundFree.accept(status); // Вызываем callback, если порт свободен
                }
                return status; // Возвращаем результат проверки
            });
            // ВАЖНО: Callback в потоке ForkJoinPool.commonPool(),
            // а не в том, откуда вызван checkFree.
        }

        /**
         * Параллельный поиск ВСЕХ свободных портов в наборе.
         * Запускает проверку всех портов одновременно.
         * Блокирует текущий поток до завершения всех проверок.
         *
         * @return Множество номеров свободных портов.
         */
        public Set<Integer> findAllParallel() {
            List<CompletableFuture<PortStatus>> futures = new ArrayList<>();
            for( var port : portSet() ) {
                futures.add(
                    checkFree(port, free -> {})); // Callback не нужен, просто запускаем проверку
            }

            // Ждем, пока завершатся все проверки
            var waitAll = CompletableFuture.allOf(futures.toArray(new CompletableFuture[]{}));
            waitAll.join();

            // Фильтруем результаты: оставляем только статусы со свободным портом (free=true)
            // и извлекаем номера портов
            return futures.stream()
                .map(CompletableFuture::join) // Получаем PortStatus из каждого future
                .flatMap(p -> p.free ? Stream.of(p.port) : Stream.empty()) // Если свободен - вернуть поток с его номером, иначе пустой
                .collect(Collectors.toSet()); // Собрать в Set
        }
    }

    /**
     * Структура для представления результата проверки порта.
     */
    public record PortStatus(String host, int port, boolean free) {}

    /**
     * Проверяет, доступен ли указанный порт для привязки.
     * ользует ServerSocket.bind() для проверки.
     *
     * @param host Хост (IP), на котором проверяется порт.
     * @param port Порт для проверки.
     * @return true, если порт свободен, иначе false.
     */
    private static boolean isPortAvailable(String host, int port) {
        // Проверяем, не выходит ли порт за границы возможных значений
        if( port < 1 || port > MAX_PORT ) {
            return false;
        }

        ServerSocket socket = null;
        try {
            // Создаем ServerSocket
            socket = new ServerSocket();
            // Позволяет повторно использовать адрес, если он был занят ранее
            socket.setReuseAddress(true);
            // Пробуем привязаться к указанному адресу и порту
            // backlog = 1 - размер очереди входящих соединений
            socket.bind(new InetSocketAddress(InetAddress.getByName(host), port), 1);
            // Если bind прошел успешно, порт свободен
            return true;
        } catch ( IOException e ) {
            // Порт занят или недоступен по другой причине
            return false;
        } finally {
            // Обязательно закрываем сокет, чтобы освободить ресурсы
            if( socket != null ) {
                try {
                    socket.close();
                } catch ( IOException e ) {
                    // Игнорируем ошибку закрытия
                }
            }
        }
    }
}