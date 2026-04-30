package xyz.cofe.mitrenier.api.server.rest;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.cofe.mitrenier.json.JSON;
import xyz.cofe.mitrenier.midi.MidiEvent;
import xyz.cofe.mitrenier.midi.RawMidiEvent;
import xyz.cofe.nipal.JsonItfImpl;
import xyz.cofe.nipal.util.QueryString;
import xyz.cofe.nipal.RequestRouter;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * <h1>Rest Сервер midi input</h1>
 *
 * <h2>GET /</h2>
 * Список клиентов (listener)
 * <h3>Ответ, статус 200</h3>
 * тип: json String[] // список клиентов
 * <p>
 * <!-- --------------------------------------------------------------------------------------------------------------- -->
 * <h2>POST /new?...</h2>
 * Создать нового клиента <br/>
 * Query string
 * <ul>
 *     <li>
 *         <b>timestamp-shift</b> = "now" | nano_sec:number - опциональный параметр
 *         <ul>
 *             <li><b>now</b> - сдвигать timestamp на момент начала сессии нового клиента</li>
 *             <li>nano_sec - число в наносек для сдвига значения timstamp</li>
 *         </ul>
 *     </li>
 * </ul>
 *
 * <h3>Ответ, статус 200</h3>
 * тип: json String
 * <p>
 * тип: json
 * <pre>
 * {
 *     "id": String // идентификатор
 *     "millis": Long // {@link System#currentTimeMillis()}
 *     "nano": Long // {@link System#nanoTime()}
 *     "time": Instant // {@link Instant#now()}
 * }
 * </pre>
 *
 * <h3>Ответ, статус 503</h3>
 * тип: json String // описание ошибки
 * <p>
 * <!-- --------------------------------------------------------------------------------------------------------------- -->
 * <h2>GET /time</h2>
 * Получить информацию о времени сервера
 * <h3>Ответ, статус 200</h3>
 * тип: json
 * <pre>
 * {
 *     "millis": Long // {@link System#currentTimeMillis()}
 *     "nano": Long // {@link System#nanoTime()}
 *     "time": Instant // {@link Instant#now()}
 * }
 * </pre>
 * <p>
 * <!-- --------------------------------------------------------------------------------------------------------------- -->
 * <h2>DELETE /${id}</h2>
 * Удалить клиента
 * <h3>Ответ, статус 200</h3>
 * тип: json String
 * <h3>Ответ, статус 400</h3>
 * тип: json String // описание ошибки (клиент не найден)
 * <p>
 * <!-- --------------------------------------------------------------------------------------------------------------- -->
 * <h2>GET /${id}</h2>
 * Получить информацию о клиенте
 * <h3>Ответ, статус 200</h3>
 * тип: json
 * <pre>
 * {
 *   "events": number, // кол-во событий
 *   "eventsQueuesCount": number, // кол-во очередей ожидающих новых событий
 *   "rawEvents": number, // кол-во событий
 *   "rawEventsQueuesCount": number, // кол-во очередей ожидающих новых событий
 *   "timestampShift": number,
 *   "timeShiftMillis": number,
 *   "timeShiftNanos": number,
 * }
 * </pre>
 * <h3>Ответ, статус 400</h3>
 * тип: json String // описание ошибки (клиент не найден)
 * <p>
 * <!-- --------------------------------------------------------------------------------------------------------------- -->
 * <h2>GET /${id}/events</h2>
 * Получение событий (raw) <br>
 * queryString map
 * <ul>
 *     <li><b>drop-before-timestamp</b> = <i>number</i> - удалить первые события до указанной отметки</li>
 *     <li><b>drop-before-time</b> = <i>время</i> - удалить первые события до указанной отметки</li>
 *     <li><b>wait-if-empty-ms</b> = <i>number</i> - ждать указанное кол-во мс., если нет событий, до появления</li>
 *     <li><b>remove-after-read</b> = <i>number</i> - если 1, то удалить прочитанные события</li>
 * </ul>
 * <h3>Ответ, статус 200</h3>
 * тип: json {@link MidiEvent MidiEvent}[]
 *
 * <h3>Ответ, статус 400</h3>
 * тип: json String // описание ошибки (клиент не найден)
 * <p>
 * <!-- --------------------------------------------------------------------------------------------------------------- -->
 * <h2>GET /${id}/events/raw</h2>
 * Получение событий (raw) <br>
 * queryString map
 * <ul>
 *     <li><b>drop-before-timestamp</b> = <i>number</i> - удалить первые события до указанной отметки</li>
 *     <li><b>drop-before-time</b> = <i>время</i> - удалить первые события до указанной отметки</li>
 *     <li><b>wait-if-empty-ms</b> = <i>number</i> - ждать указанное кол-во мс., если нет событий, до появления</li>
 *     <li><b>remove-after-read</b> = <i>number</i> - если 1, то удалить прочитанные события</li>
 * </ul>
 * <h3>Ответ, статус 200</h3>
 * тип: json {@link RawMidiEvent RawMidiEvent}[]
 *
 * <h3>Ответ, статус 400</h3>
 * тип: json String // описание ошибки (клиент не найден)
 */
@SuppressWarnings("resource")
public class MidiClientApi extends ArrayList<Handler> {
    private final MidiInputClients midiInputClients;
    private final static Logger log = LoggerFactory.getLogger(MidiClientApi.class);

    public MidiClientApi(MidiInputClients midiInputClients) {
        if( midiInputClients == null ) throw new IllegalArgumentException("midiInputClients==null");
        this.midiInputClients = midiInputClients;
    }

    private MidiInputClients midiInputClients() {return midiInputClients;}

    {
        add(RequestRouter.Builder.with(JsonItfImpl.gson(JSON.gson)).GET().path("/").jsonResponse().call(() ->
            {
                log.info("GET /");
                return midiInputClients().getClients().values().stream().map(c -> c.id).collect(Collectors.toList());
            }
        ));

        add(RequestRouter.Builder.with(JsonItfImpl.gson(JSON.gson)).GET().path("/time").jsonResponse().call(() -> {
            log.info("GET /time");
            var response = new LinkedHashMap<String, Object>();
            response.put("millis", System.currentTimeMillis());
            response.put("nano", System.nanoTime());
            response.put("time", Instant.now());
            return response;
        }));

        /*
        🚀 curl -X POST http://localhost:8899/client/new
        {
          "id": "l1dla",
          "millis": 1772486734667,
          "nano": 58315447059156,
          "time": "2026-03-02T21:25:34.667370183Z"
        }
         */
        add(RequestRouter.Builder.with(JsonItfImpl.gson(JSON.gson)).POST().path("/new").jsonResponse().jettyRequest().call(req -> () -> {
                log.info("POST /new");
                return midiInputClients().startWithUniqID(MidiInputClient::new)
                    .map(client -> {
                        var qsMap = QueryString.parseQueryString(req);
                        Optional.ofNullable(qsMap.get("timestamp-shift")).ifPresent(ts -> {
                            if( ts.equals("now") ) {
                                client.setNanoTimeShift(-System.nanoTime());
                            } else if( ts.matches("[\\-\\+]?\\d+") ) {
                                client.setNanoTimeShift(Long.parseLong(ts));
                            }
                        });
                        return client;
                    })
                    .fold(
                        client -> {
                            var response = new LinkedHashMap<String, Object>();
                            response.put("id", client.id);
                            response.put("millis", System.currentTimeMillis());
                            response.put("nano", System.nanoTime());
                            response.put("time", Instant.now());
                            return response;
                        },
                        ServerError::new
                    );
            }
        ));

        add(RequestRouter.Builder.with(JsonItfImpl.gson(JSON.gson)).DELETE().pathTemplate("/${id}").jsonResponse().pathValue("id").call(id -> () -> {
            var client = midiInputClients().getClients().get(id);
            if( client == null ) {
                return new ClientError("client not found");
            }

            midiInputClients().stop(client);
            return "ok";
        }));

        /*
        🚀 curl http://localhost:8899/client/tliws
            {
              "events": 0,
              "rawEvents": 0
            }
         */
        add(RequestRouter.Builder.with(JsonItfImpl.gson(JSON.gson)).GET().pathTemplate("/${id}").jsonResponse().pathValue("id").call(id -> () -> {
            var client = midiInputClients().getClients().get(id);
            if( client == null ) {
                return new ClientError("client not found");
            }

            client.setLastActivity(Instant.now());

            var map = new LinkedHashMap<>();
            map.put("events", client.getMidiEvents().size());
            map.put("eventsQueuesCount", client.midiEventsQueue().size());

            map.put("rawEvents", client.getRawMidiEvents().size());
            map.put("rawEventsQueuesCount", client.midiRawEventsQueue().size());

            client.getNanoTimeShift().ifPresent(t -> map.put("timestampShift", t));
            client.getTimeShift().ifPresent(t -> {
                map.put("timeShiftMillis", t.toMillis());
                map.put("timeShiftNanos", t.toNanos());
            });

            return map;
        }));

        /*
        🚀 curl http://localhost:8899/client/tliws/events/raw
            [
              {
                "time": "2026-02-23T20:40:15.385585587Z",
                "timestamp": 10927246991397,
                "data": "901556"
              }
            ]
         */
        add(RequestRouter.Builder.with(JsonItfImpl.gson(JSON.gson)).GET().pathTemplate("/${id}/events/raw").jsonResponse().pathValue("id").jettyRequest().call(req -> id -> () -> {
            var client = midiInputClients().getClients().get(id);
            if( client == null ) {
                return new ClientError("client not found");
            }
            client.setLastActivity(Instant.now());
            return process(client.getRawMidiEvents(), client.midiRawEventsQueue(), rawMidiEventTimeOf, req);
        }));

        /*
        🚀 curl 'http://localhost:8899/client/4rsa3/events?remove-after-read=1&wait-if-empty-ms=10000'
         */
        add(RequestRouter.Builder.with(JsonItfImpl.gson(JSON.gson)).GET().pathTemplate("/${id}/events").jsonResponse().pathValue("id").jettyRequest().call(req -> id -> () -> {
            var client = midiInputClients().getClients().get(id);
            if( client == null ) {
                return new ClientError("client not found");
            }
            client.setLastActivity(Instant.now());
            return process(client.getMidiEvents(), client.midiEventsQueue(), midiEventTimeOf, req);
        }));
    }

    private interface TimeOf<T> {
        long nanoTimeOf(T t);

        Instant timeOf(T t);
    }

    private static TimeOf<RawMidiEvent> rawMidiEventTimeOf = new TimeOf<>() {
        @Override
        public long nanoTimeOf(RawMidiEvent event) {
            return event.timestamp();
        }

        @Override
        public Instant timeOf(RawMidiEvent event) {
            return event.time();
        }
    };

    private static TimeOf<MidiEvent<?>> midiEventTimeOf = new TimeOf<>() {
        @Override
        public long nanoTimeOf(MidiEvent<?> event) {
            return event.timestampNano();
        }

        @Override
        public Instant timeOf(MidiEvent<?> event) {
            return event.time();
        }
    };


    private <T> List<T> process(List<T> events, Set<BlockingQueue<T>> queues, TimeOf<T> timeOf, Request request) {
        var qsMap = QueryString.parseQueryString(request);
        if( qsMap.isEmpty() ) return events;

        var dropBeforeTimestampStr = qsMap.get("drop-before-timestamp");
        if( dropBeforeTimestampStr != null && dropBeforeTimestampStr.matches("\\d+") ) {
            var ts = Long.parseLong(dropBeforeTimestampStr);
            var idx = 0;
            while( true ) {
                if( idx >= events.size() ) break;
                var ev = events.get(idx);
                var ts_ev = timeOf.nanoTimeOf(ev);
                if( ts_ev < ts ) {
                    events.remove(idx);
                    continue;
                }
                idx++;
            }
        }

        var dropBeforeTimeStr = qsMap.get("drop-before-time");
        if( dropBeforeTimeStr != null && dropBeforeTimeStr.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.((\\d+)Z)?)?") ) {
            try {
                var t_before = Instant.parse(dropBeforeTimeStr);
                var idx = 0;
                while( true ) {
                    if( idx >= events.size() ) break;
                    var ev = events.get(idx);
                    var ts_ev = timeOf.timeOf(ev);
                    if( ts_ev.isBefore(t_before) ) {
                        events.remove(idx);
                        continue;
                    }
                    idx++;
                }
            } catch ( DateTimeParseException ex ) {
            }
        }

        var waitIsEmptyStr = qsMap.get("wait-if-empty-ms");
        if( events.isEmpty() && waitIsEmptyStr != null && waitIsEmptyStr.matches("\\d+") ) {
            var ms = Integer.parseInt(waitIsEmptyStr);
            BlockingQueue<T> queue = new LinkedBlockingQueue<>();
            try {
                queues.add(queue);
                try {
                    queue.poll(ms, TimeUnit.MILLISECONDS);
                } catch ( InterruptedException e ) {
                    //
                }
            } finally {
                queues.remove(queue);
            }
        }

        var removeAfterReadStr = qsMap.get("remove-after-read");
        if( removeAfterReadStr != null && removeAfterReadStr.equals("1") ) {
            var result = new ArrayList<>(events);
            events.clear();
            return result;
        }

        return events;
    }
}
