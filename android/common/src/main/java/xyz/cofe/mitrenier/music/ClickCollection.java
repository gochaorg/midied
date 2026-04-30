package xyz.cofe.mitrenier.music;

import xyz.cofe.coll.im.iter.ExtIterable;
import xyz.cofe.mitrenier.math.IntervalManager;
import xyz.cofe.mitrenier.math.IntervalTree;
import xyz.cofe.mitrenier.math.ObservableNavigableMap;
import xyz.cofe.mitrenier.math.ObservableNavigableSet;
import xyz.cofe.mitrenier.midi.MidiEvent;

import java.time.Instant;
import java.util.Comparator;

/**
 * Коллекция "кликов".
 */
public class ClickCollection {
    public ClickCollection() {
    }

    public ClickCollection(Iterable<MidiEvent<?>> events) {
        if( events == null ) throw new IllegalArgumentException("events==null");
        importEvents(events);
    }

    public ClickCollection(MidiSong song) {
        if( song == null ) throw new IllegalArgumentException("song==null");
        importEvents(song.getEvents());
    }

    private void importEvents(Iterable<MidiEvent<?>> events) {
        for( var ev : events ){
            if( ev instanceof MidiEvent.NoteOn noteOn ){
                add(noteOn);
            } else if( ev instanceof MidiEvent.NoteOff noteOff ){
                add(noteOff);
            }
        }
    }

    /**
     * Добавление события нажатия ноты
     *
     * @param noteOn событие ноты
     */
    public void add(MidiEvent.NoteOn noteOn) {
        if( noteOn == null ) throw new IllegalArgumentException("noteOn==null");
//        System.out.println("add "+noteOn);
        getIntervalManagerOfNote(noteOn).onInsertBegin(new ClickInterval(noteOn.time(), noteOn));
    }

    /**
     * Добавление события нажатия ноты
     *
     * @param noteOff событие ноты
     */
    public void add(MidiEvent.NoteOff noteOff) {
        if( noteOff == null ) throw new IllegalArgumentException("noteOff==null");
//        System.out.println("add "+noteOff);
        getIntervalManagerOfNote(noteOff).onInsertEnd(new ClickInterval(noteOff.time(), noteOff));
    }

    /**
     * Добавление события нажатия ноты
     *
     * @param noteOn событие ноты
     */
    public void delete(MidiEvent.NoteOn noteOn) {
        if( noteOn == null ) throw new IllegalArgumentException("noteOn==null");
        getIntervalManagerOfNote(noteOn).onDeleteBegin(new ClickInterval(noteOn.time(), noteOn));
    }

    /**
     * Добавление события нажатия ноты
     *
     * @param noteOff событие ноты
     */
    public void delete(MidiEvent.NoteOff noteOff) {
        if( noteOff == null ) throw new IllegalArgumentException("noteOff==null");
        getIntervalManagerOfNote(noteOff).onDeleteEnd(new ClickInterval(noteOff.time(), noteOff));
    }

    //region noteClickCMP : Comparator<NoteClick>
    public static final Comparator<NoteClick> noteClickCMP = Comparator.comparing(NoteClick::startAt)
        .thenComparing(NoteClick::note)
        .thenComparing(NoteClick::channel)
        .thenComparing( (a,b) -> {
            if( a.endAt().isEmpty() && b.endAt().isEmpty() )return 0;
            if( a.endAt().isEmpty() && b.endAt().isPresent() )return 1;
            if( a.endAt().isPresent() && b.endAt().isEmpty() )return -1;
            var a_e = a.endAt().get();
            var b_e = b.endAt().get();
            return a_e.compareTo(b_e);
        });
    //.thenComparing(nClick -> nClick.endAt().orElse(getCurrent()))
    //.thenComparing(NoteClick::startAt);
    //endregion

    private final ObservableNavigableMap<Integer, ObservableNavigableMap<Integer, IntervalManager<ClickInterval>>>
        noteIntervalManager = new ObservableNavigableMap<>();

    private IntervalManager<ClickInterval> getIntervalManagerOfNote(MidiEvent.NoteOnOrOff note) {
        return getIntervalManagerOfNote(note.channel(), note.note());
    }

    /**
     * Получение менеджера интевала для указанной ноты
     *
     * @param channel канал
     * @param note    нота
     * @return менеджер
     */
    private IntervalManager<ClickInterval> getIntervalManagerOfNote(int channel, int note) {
        return noteIntervalManager
            .computeIfAbsent(channel, x -> new ObservableNavigableMap<>())
            .computeIfAbsent(note, y -> {
                    var iman = new IntervalManager<ClickInterval>(Comparator.naturalOrder());

                    iman.intervalsBeginEnd.listener()
                        .onInsert((b, e) -> {
                            var fullClick = new NoteClick.FullClick((MidiEvent.NoteOn) b.note(), (MidiEvent.NoteOff) e.note());
                            fullClicksTree.insert(
                                fullClick.startAt(),
                                fullClick.endAtTime(),
                                fullClick);

                            var t = b.time();
                            fullClickStartAt.computeIfAbsent(t, x -> new ObservableNavigableSet<>(noteClickCMP)).add(fullClick);
                        })
                        .onDelete((b, e) -> {
                            var fullClick = new NoteClick.FullClick((MidiEvent.NoteOn) b.note(), (MidiEvent.NoteOff) e.note());
                            fullClicksTree.delete(
                                fullClick.startAt(),
                                fullClick.endAtTime(),
                                fullClick
                            );

                            var t = b.time();
                            var set = fullClickStartAt.get(t);
                            if( set != null ){
                                set.remove(fullClick);
                                if( set.isEmpty() ){
                                    fullClickStartAt.remove(t);
                                }
                            }
                        })
                        .add();

                    iman.addHalfOpenChangeListener((prevOpt, curOpt) -> {
                        prevOpt.ifPresent(prev -> {
                            var halfClick = new NoteClick.HalfClick((MidiEvent.NoteOn) prev.note());

                            var t = halfClick.startAt();
                            var set = halfClickStartAt.get(t);
                            if( set != null ){
                                set.remove(halfClick);
                                if( set.isEmpty() ){
                                    halfClickStartAt.remove(t);
                                }
                            }
                        });

                        curOpt.ifPresent(cur -> {
                            var halfClick = new NoteClick.HalfClick((MidiEvent.NoteOn) cur.note());
                            halfClickStartAt.computeIfAbsent(
                                halfClick.startAt(),
                                x -> new ObservableNavigableSet<>(noteClickCMP)).add(halfClick);
                        });
                    });
                    return iman;
                }
            );
    }

    private ExtIterable<ClickIntervalManager> noteIntervalManagers() {
        return ExtIterable.from(noteIntervalManager.keySet())
            .fmap(channel -> ExtIterable
                .from(noteIntervalManager.get(channel).keySet())
                .map(note -> new ClickIntervalManager(channel, note, noteIntervalManager.get(channel).get(note)))
                .iterator());
    }

    /**
     * Дерево "полных" кликов
     */
    public final IntervalTree<Instant, NoteClick.FullClick> fullClicksTree = IntervalTree.create();

    /**
     * События частичных кликов, только нажатия без отпускания
     */
    public final ObservableNavigableMap<Instant, ObservableNavigableSet<NoteClick.HalfClick>>
        halfClickStartAt = new ObservableNavigableMap<>();

    /**
     * События кликов, только нажатия с опусканиями
     */
    public final ObservableNavigableMap<Instant, ObservableNavigableSet<NoteClick.FullClick>>
        fullClickStartAt = new ObservableNavigableMap<>();

    /**
     * События кликов, только нажатия с опусканиями
     */
    public final ObservableNavigableSet<NoteClick.FullClick> fullClicks = new ObservableNavigableSet<>(noteClickCMP);

    {
        fullClickStartAt.listener().onInsert((t, set) -> {
            set.listener()
                .onInsert(fullClicks::add)
                .onDelete(fullClicks::remove)
                .bind();
        }).bind();
    }

    /**
     * События частичных кликов, только нажатия без отпускания
     */
    public final ObservableNavigableSet<NoteClick.HalfClick> halfClicks = new ObservableNavigableSet<>(noteClickCMP);

    {
        halfClickStartAt.listener()
            .onInsert((t, set) -> {
                set.listener()
                    .onInsert(halfClicks::add)
                    .onDelete(halfClicks::remove)
                    .bind();
            })
            .bind();
    }

    /**
     * Клики, включая полные и частичные
     */
    public final ObservableNavigableSet<NoteClick> clicks = new ObservableNavigableSet<>(noteClickCMP);

    {
        fullClicks.listener()
            .onInsert(c -> {
                clicks.add(c);
            })
            .onDelete( c -> {
                clicks.remove(c);
            })
            .bind();

        halfClicks.listener().onInsert(clicks::add).onDelete(clicks::remove).bind();
    }
}
