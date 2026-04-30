package xyz.cofe.mitrenier.math;

import xyz.cofe.mitrenier.UnBind;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Менеджер интервалов - нахождение интервалов среди событий добавления/удаления начала/конца интервала.
 *
 * <h2>Задача</h2>
 * Есть поток событий
 * <ul>
 *     <li>Добавления начало интервала (нажатия клавиши)</li>
 *     <li>Добавления конца интервала (нажатия клавиши)</li>
 *     <li>Удаления начало интервала (нажатия клавиши)</li>
 *     <li>Удаления конца интервала (нажатия клавиши)</li>
 * </ul>
 * <h3>Необходимо</h3>
 * <ul>
 *     <li>Добавить интервал в {@link #intervalsBeginEnd}, {@link #intervalsEndBegin}</li>
 *     <li>Удалить интервал из {@link #intervalsBeginEnd}, {@link #intervalsEndBegin}</li>
 * </ul>
 *
 * <h2>Интервал</h2>
 *
 * Возможны несколько комбинаций интервалов исходя из событий:
 *
 * <pre>
 * [ ] - обозначение событий начала/конца {@link #begins}, {@link #ends}
 *
 *
 * Было 2 события
 * и интервал от 0 до 7             и интервал от 0 до 7
 *   [     ]                          [     ]
 * --+=====+------------            --+=====+------------
 *   0     7                          0     7
 *
 *
 * Добавили событие ] в 5          Добавили событие ] в 9
 *       *                                   *
 *   [   ] ]                         [     ] ]
 * --+===+---------------          --+=====+-------------
 *   0   5 7                         0     7
 *   будет:                          будет без изменений
 *     1) удален интервал 0..7
 *     2) добавлен интервал 0..5
 *
 * =======================================================
 *
 * Есть интервал 3..7              Есть интервал 3..7
 *    [     ]                         [     ]
 * ---+=====+-                     ---+=====+-
 *    3     7                         3     7
 *
 * Добавили [ в 1                  Добавили [ в 5
 *  *                                     *
 *  [  [     ]                         [  [  ]
 * -+========+-                    ----+=====+-
 *  1  3     7                         3  5  7
 * будет                           будет без изменений
 *   1) удален интервал 3..7
 *   2) добавлен интервал 1..7
 *
 * </pre>
 *
 * @param <I> Тип начала/конца интервала
 */
public class IntervalManager<I> {
    /**
     * События начала
     */
    public final ObservableNavigableSet<I> begins;

    /**
     * События конца
     */
    public final ObservableNavigableSet<I> ends;

    /**
     * Интервалы от начала к концу, должно совпадать по смыслу с {@link #intervalsEndBegin}
     */
    public final ObservableNavigableMap<I, I> intervalsBeginEnd;

    /**
     * Интервалы от конца к началу, должно совпадать по смыслу с {@link #intervalsBeginEnd}
     */
    public final ObservableNavigableMap<I, I> intervalsEndBegin;

    /**
     * Сравнения начала/конца интервала
     */
    public final Comparator<I> comparator;

    public IntervalManager(Comparator<I> cmp) {
        if( cmp == null ) throw new IllegalArgumentException("cmp==null");

        begins = new ObservableNavigableSet<>(cmp);
        ends = new ObservableNavigableSet<>(cmp);
        halfOpen = Optional.empty();

        intervalsBeginEnd = new ObservableNavigableMap<>(cmp);
        intervalsEndBegin = new ObservableNavigableMap<>(cmp);
        comparator = cmp;
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private Optional<I> halfOpen;

    /**
     * Возвращает полу интервал
     * @return полу интервал
     */
    public Optional<I> getHalfOpen(){ return halfOpen; }

    private final List<BiConsumer<Optional<I>, Optional<I>>> halfOpenListeners = new ArrayList<>();

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private void fireHalfOpenChanges(Optional<I> prev, Optional<I> cur ){
        for( var ls : halfOpenListeners ){
            ls.accept( prev, cur );
        }
    }

    public void addHalfOpenChangeListener( BiConsumer<Optional<I>, Optional<I>> listener ){
        if( listener==null ) throw new IllegalArgumentException("listener==null");
        halfOpenListeners.add(listener);
    }

    public void removeHalfOpenChangeListener( BiConsumer<Optional<I>, Optional<I>> listener ){
        halfOpenListeners.remove(listener);
    }

    /**
     * Проверка смены наличия полу интервала
     */
    private void checkOpenCloseHalf(){
        var prev = halfOpen;
        Optional<I> cur = null;

        if( begins.isEmpty() ){
            cur = Optional.empty();
        }else{
            if( ends.isEmpty() ){
                cur = Optional.of(begins.last());
            }else{
                var lastBegin = begins.last();
                var lastEnd = ends.last();
                cur = comparator.compare(lastBegin, lastEnd)>=0
                    ? Optional.of(lastBegin)
                    : Optional.empty();
            }
        }

        if( prev.isEmpty() && cur.isEmpty() ){
            return;
        }else if( prev.isEmpty() && cur.isPresent() ){
            halfOpen = cur;
            fireHalfOpenChanges( prev, cur );
        }else if( prev.isPresent() && cur.isEmpty() ){
            halfOpen = cur;
            fireHalfOpenChanges( prev, cur );
        }else {
            var p_val = prev.get();
            var c_val = cur.get();
            if( comparator.compare(p_val, c_val)!=0 ){
                halfOpen = cur;
                fireHalfOpenChanges(prev,cur);
            }
        }
    }

    //region on Insert/Delete Begin/End ...
    @SuppressWarnings("UnnecessaryLocalVariable")
    public void onInsertBegin(I begin) {
        begins.add(begin);

        var endOpt = ends.nextOf(begin);
        if( endOpt.isEmpty() ){
            // добавляем открытый полу интервал
            checkOpenCloseHalf();
            return;
        }

        // потенциальный интервал
        var end = endOpt.get();

        var existsEBInterval = intervalsEndBegin.get(end);
        if( existsEBInterval!=null ){
            // есть уже интервал
            var beginExists = existsEBInterval;
            if( comparator.compare(beginExists,begin)<=0 ){
                // вставка внутрь существующего
                // такое пропускаем
                return;
            }else {
                // надо расширить существующий
                intervalsBeginEnd.remove(beginExists);
                intervalsEndBegin.remove(end);

                intervalsBeginEnd.put(begin,end);
                intervalsEndBegin.put(end,begin);
            }
        }else{
            // добавляем новый интервал
            intervalsBeginEnd.put(begin,end);
            intervalsEndBegin.put(end,begin);
        }

        checkOpenCloseHalf();
    }

    public void onDeleteBegin(I begin) {
        begins.remove(begin);

        // Удаляем существующие интервалы
        var endExists = intervalsBeginEnd.get(begin);
        if( endExists!=null ){
            intervalsEndBegin.remove(endExists);
            intervalsBeginEnd.remove(begin);
        }

        checkOpenCloseHalf();
    }

    @SuppressWarnings("UnnecessaryLocalVariable")
    public void onInsertEnd(I end) {
        ends.add(end);

        var beginOpt = begins.previousOf(end);
        if( beginOpt.isEmpty() ){
            checkOpenCloseHalf();
            return;
        }

        // потенциальный интервал
        var begin = beginOpt.get();

        var existsBEInterval = intervalsBeginEnd.get(begin);
        if( existsBEInterval!=null ){
            // есть уже интервал
            var endExists = existsBEInterval;
            if( comparator.compare(end,endExists)>=0 ){
                // вставка за существующим
                // такое пропускаем
                return;
            }else{
                // Надо уменьшить существующий
                intervalsBeginEnd.remove(begin);
                intervalsEndBegin.remove(endExists);

                intervalsBeginEnd.put(begin,end);
                intervalsEndBegin.put(end,begin);
            }
        }else{
            // добавляем новый интервал
            intervalsBeginEnd.put(begin,end);
            intervalsEndBegin.put(end,begin);
        }

        checkOpenCloseHalf();
    }

    public void onDeleteEnd(I end) {
        ends.remove(end);

        // Удаляем существующие интервалы
        var beginExists = intervalsEndBegin.get(end);
        if( beginExists!=null ){
            intervalsBeginEnd.remove(beginExists);
            intervalsEndBegin.remove(end);
        }

        checkOpenCloseHalf();
    }
    //endregion

    public void init(Iterable<I> initialBegins, Iterable<I> initialEnds) {
        if( initialBegins==null ) throw new IllegalArgumentException("initialBegins==null");
        if( initialEnds==null ) throw new IllegalArgumentException("initialEnds==null");

        initialBegins.forEach(this::onInsertBegin);
        initialEnds.forEach(this::onInsertEnd);
        checkOpenCloseHalf();
    }

    /**
     * Синхронизация интервалов
     */
    public class Synchronizer<A> {
        public final Set<A> consumer;

        public Synchronizer(Set<A> consumer) {
            this.consumer = consumer;
        }

        private BiFunction<I,I,A> fullIntervalMapper;

        /**
         * Указывает функцию создания полного интервала
         * @param mapper создание интервала
         * @return SELF ссылка
         */
        public Synchronizer<A> fullInterval( BiFunction<I,I,A> mapper ){
            fullIntervalMapper = mapper;
            return this;
        }

        private Function<I,A> halfOpenIntervalMapper;

        /**
         * Указывает функцию создания полу интервала
         * @param mapper создание интервала
         * @return SELF ссылка
         */
        public Synchronizer<A> halfOpenInterval( Function<I,A> mapper ){
            halfOpenIntervalMapper = mapper;
            return this;
        }

        public UnBind bind(){
            var close = new ArrayList<Runnable>();

            if( fullIntervalMapper!=null ){
                var listener = intervalsBeginEnd.listener()
                    .onInsert((begin, end) -> {
                        consumer.add(fullIntervalMapper.apply(begin, end));
                    })
                    .onDelete((begin, end) -> {
                        consumer.remove(fullIntervalMapper.apply(begin, end));
                    })
                    .add();
                close.add(()->{
                    intervalsBeginEnd.removeListener(listener);
                });
            }

            if( halfOpenIntervalMapper!=null ){
                BiConsumer<Optional<I>,Optional<I>> listener2 = (prev,cur) -> {
                    prev.ifPresent(i -> consumer.remove(halfOpenIntervalMapper.apply(i)));
                    cur.ifPresent(i -> consumer.add(halfOpenIntervalMapper.apply(i)));
                };
                addHalfOpenChangeListener(listener2);
                close.add(() -> {
                    removeHalfOpenChangeListener(listener2);
                });
            }

            return new UnBind(close);
        }
    }

    public <A> Synchronizer<A> sync( Set<A> intervals ){
        return new Synchronizer<>(intervals);
    }

}
