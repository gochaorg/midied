package xyz.cofe.mitrenier.math;

import xyz.cofe.coll.im.ImList;
import xyz.cofe.mitrenier.str.StrAlign;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class PerfCounter {
    public PerfCounter(String name) {
        if( name == null ) throw new IllegalArgumentException("name==null");
        this.name = name;
    }

    public PerfCounter(String name, int cycleLength) {
        if( name == null ) throw new IllegalArgumentException("name==null");
        this.name = name;
        if( cycleLength > 0 ){
            durationsCycleBuff = new long[cycleLength];
        }
    }

    public PerfCounter(PerfCounter sample) {
        if( sample == null ) throw new IllegalArgumentException("sample==null");
        this.name = sample.name;
        this.duration = sample.duration;
        this.durationPtrCycleBuff = sample.durationPtrCycleBuff;
        if( sample.durationsCycleBuff != null ){
            this.durationsCycleBuff = Arrays.copyOf(sample.durationsCycleBuff, sample.durationsCycleBuff.length);
        }
        this.durationMin = sample.durationMin;
        this.durationMax = sample.durationMax;
        this.count = sample.count;
    }

    @SuppressWarnings("MethodDoesntCallSuperMethod")
    public PerfCounter clone() {
        synchronized(this) {
            return new PerfCounter(this);
        }
    }

    private long[] durationsCycleBuff;
    private int durationPtrCycleBuff = 0;

    //region name
    private final String name;

    public String name() {return name;}
    //endregion

    //region durationMin
    private volatile Duration durationMin = null;

    public Optional<Duration> durationMin() {
        return Optional.ofNullable(durationMin);
    }

    //endregion
    //region durationMax
    private volatile Duration durationMax = null;

    public Optional<Duration> durationMax() {
        return Optional.ofNullable(durationMax);
    }

    //endregion
    //region duration
    private volatile Duration duration = Duration.ZERO;

    public Duration duration() {return duration;}

    //endregion
    //region count
    private volatile long count = 0;

    public long count() {return count;}
    //endregion

    private List<Long> durationsList() {
        if( durationsCycleBuff == null ) return List.of();

        var lst = new ArrayList<Long>();
        if( durationPtrCycleBuff >= durationsCycleBuff.length ){
            for( var d : durationsCycleBuff ){
                lst.add(d);
            }
        } else{
            for( var i = 0; i < durationPtrCycleBuff; i++ ){
                lst.add(durationsCycleBuff[i]);
            }
        }

        return lst;
    }

    private Stat<Long> stat;

    public Stat<Long> stat() {
        if( stat != null ) return stat;
        stat = new Stat<>(durationsList(), Long::doubleValue);
        return stat;
    }

    public void collect(long nano) {
        collect(nano, 1);
    }

    @SuppressWarnings("NonAtomicOperationOnVolatileField")
    public void collect(long nano, long cnt) {
        duration = duration.plusNanos(nano);
        count = count + cnt;

        if( durationMin == null ){
            durationMin = Duration.ofNanos(nano);
        } else{
            durationMin = Duration.ofNanos(Math.min(nano, durationMin.toNanos()));
        }

        if( durationMax == null ){
            durationMax = Duration.ofNanos(nano);
        } else{
            durationMax = Duration.ofNanos(Math.max(nano, durationMax.toNanos()));
        }

        if( durationsCycleBuff != null ){
            for( var c = 0; c < cnt; c++ ){
                int idx = durationPtrCycleBuff % durationsCycleBuff.length;
                durationsCycleBuff[idx] = nano;
                durationPtrCycleBuff++;
            }
        }

        stat = null;
    }

    public String toString() {
        synchronized(this) {
            var sb = new StringBuilder();
            sb.append(name);
            if( count > 0 ){
                sb.append(" count=").append(count);
                sb.append(" dur=").append(duration);
                if( count > 1 ){
                    sb.append(" min=").append(durationMin);
                    sb.append(" avg=").append(Duration.ofNanos(duration.toNanos() / count));
                    sb.append(" max=").append(durationMax);

                    if( durationsCycleBuff != null ){
                        Stat<Long> stat = new Stat<>(durationsList(), Long::doubleValue);
                        stat.p25().ifPresent(p25 -> sb.append(" p25%=").append(Duration.ofNanos(p25.longValue())));
                        stat.p50().ifPresent(p50 -> sb.append(" p50%=").append(Duration.ofNanos(p50.longValue())));
                        stat.p75().ifPresent(p75 -> sb.append(" p75%=").append(Duration.ofNanos(p75.longValue())));
                        stat.p90().ifPresent(p90 -> sb.append(" p90%=").append(Duration.ofNanos(p90.longValue())));
                        stat.p95().ifPresent(p95 -> sb.append(" p95%=").append(Duration.ofNanos(p95.longValue())));
                        stat.stdev.get().ifPresent(stdev -> sb.append(" stdev=").append(Duration.ofNanos(stdev.longValue())));
                        stat.stdevp.get().ifPresent(stdevp -> sb.append(" stdevp=").append(Duration.ofNanos(stdevp.longValue())));
                    }
                }
            }
            return sb.toString();
        }
    }

    //region start/ stop / track
    public <R> R track(Supplier<R> code) {
        if( code == null ) throw new IllegalArgumentException("code==null");
        var t0 = System.nanoTime();
        var res = code.get();
        var t1 = System.nanoTime();

        synchronized(this) {
            collect(t1 - t0);
        }
        return res;
    }

    public void track(Runnable code) {
        if( code == null ) throw new IllegalArgumentException("code==null");

        var t0 = System.nanoTime();
        code.run();
        var t1 = System.nanoTime();

        synchronized(this) {
            collect(t1 - t0);
        }
    }

    private volatile long started = 0;

    public long start() {
        var t = System.nanoTime();
        started = t;
        return t;
    }

    public boolean stop(long started) {
        var stopped = System.nanoTime();
        var expectStarted = this.started;
        if( expectStarted == started ){
            var dur = stopped - expectStarted;
            synchronized(this) {
                collect(dur);
            }
            return true;
        }
        return false;
    }
    //endregion

    public static class Report {
        private final List<PerfCounter> counters;
        private final Map<String, Function<PerfCounter, String>> fetchValues;

        public Report(List<PerfCounter> counters) {
            if( counters == null ) throw new IllegalArgumentException("counters==null");

            //noinspection FuseStreamOperations
            this.counters = new ArrayList<>(counters.stream().map(PerfCounter::clone).collect(Collectors.toList()));
            this.counters.sort(Comparator.comparing(PerfCounter::name));

            fetchValues = new LinkedHashMap<>();
            fetchValues.put("name", PerfCounter::name);
            fetchValues.put("count", c -> c.count() + "");
            fetchValues.put("duration", c -> c.duration() + "");
            fetchValues.put("min", c -> c.durationMin().map(d -> d + "").orElse(""));
            fetchValues.put("avg", c -> c.count() > 1 ? (Duration.ofNanos(c.duration.toNanos() / c.count()) + "") : "");
            fetchValues.put("max", c -> c.durationMax().map(d -> d + "").orElse(""));
            fetchValues.put("stdev", c -> c.stat().stdev.get().map(n -> Duration.ofNanos(n.longValue())).map(Duration::toString).orElse(""));
            fetchValues.put("stdevp", c -> c.stat().stdevp.get().map(n -> Duration.ofNanos(n.longValue())).map(Duration::toString).orElse(""));
            fetchValues.put("25%", c -> c.stat().p25().map(n -> Duration.ofNanos(n.longValue())).map(Duration::toString).orElse(""));
            fetchValues.put("50%", c -> c.stat().p50().map(n -> Duration.ofNanos(n.longValue())).map(Duration::toString).orElse(""));
            fetchValues.put("75%", c -> c.stat().p75().map(n -> Duration.ofNanos(n.longValue())).map(Duration::toString).orElse(""));
            fetchValues.put("90%", c -> c.stat().p90().map(n -> Duration.ofNanos(n.longValue())).map(Duration::toString).orElse(""));
            fetchValues.put("95%", c -> c.stat().p95().map(n -> Duration.ofNanos(n.longValue())).map(Duration::toString).orElse(""));
        }

        @SuppressWarnings("SimplifyStreamApiCallChains")
        public String toString() {
            List<List<String>> cells = new ArrayList<>();

            List<String> header = new ArrayList<>();
            header.addAll(fetchValues.keySet());
            cells.add(header);

            for( var cntr : counters ){
                var line = new ArrayList<String>();
                for( var fetch : fetchValues.values() ){
                    line.add(fetch.apply(cntr));
                }
                cells.add(line);
            }

            List<Integer> maxWidth = new ArrayList<>();
            for( var ci = 0; ci < fetchValues.size(); ci++ ) maxWidth.add(0);

            for( var line : cells ){
                for( var ci = 0; ci < line.size(); ci++ ){
                    var cell = line.get(ci);
                    maxWidth.set(ci, Math.max(maxWidth.get(ci), cell.length()));
                }
            }

            for( var line : cells ){
                for( var ci = 0; ci < line.size(); ci++ ){
                    var cell = line.get(ci);

                    var first = ci == 0;
                    var last = ci < (line.size() - 1);

                    var maxWidthCell = maxWidth.get(ci) + (last ? 1 : 0);

                    var txt = StrAlign.strAlign(cell)
                        .len(maxWidthCell);

                    if( ci == 0 ){
                        txt.left();
                    } else{
                        txt.right();
                    }

                    line.set(ci,
                        "|" + txt + (last ? "" : "|")
                    );
                }
            }

            var header2 = ImList.from(header).enumerate().map(e -> {
                var s = "=".repeat(e.value().length());
                if( e.index() < header.size() - 1 ){
                    s = s.replaceAll("^(.)(.*)$", "|$2");
                } else{
                    s = s.replaceAll("^(.)(.*)(.)$", "|$2|");
                }
                return s;
            });

            cells.add(1, header2.toList());

            var sb = new StringBuilder();
            for( var line : cells ){
                for( var cell : line ){
                    sb.append(cell);
                }
                sb.append("\n");
            }
            return sb.toString();
        }
    }

    public static Report report(PerfCounter... counters) {
        return new Report(List.of(counters));
    }
}
