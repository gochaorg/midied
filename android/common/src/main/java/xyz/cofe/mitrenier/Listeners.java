package xyz.cofe.mitrenier;

import java.util.List;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Утилитный класс для управления списком слушателей событий с поддержкой
 * сильных и слабых ссылок, а также автоматической отписки через {@link UnBind}.
 *
 * <p>Предназначен для встраивания в классы-владельцы событий. Позволяет
 * регистрировать обработчики событий, уведомлять их при наступлении события
 * и безопасно управлять их жизненным циклом.</p>
 *
 * @param <EVENT> тип события
 * @param <OWNER> тип владельца (обычно — класс, в который встроен этот объект)
 */
public class Listeners<EVENT, OWNER> {
    /**
     * Поставщик владельца, необходимый для возврата из цепочек вызовов (fluent API).
     */
    public final Supplier<OWNER> owner;

    /**
     * Создаёт экземпляр с заданным поставщиком владельца.
     * @param owner поставщик владельца; не может быть {@code null}
     * @throws IllegalArgumentException если {@code owner == null}
     */
    public Listeners(Supplier<OWNER> owner) {
        if( owner==null ) throw new IllegalArgumentException("owner==null");
        this.owner = owner;
    }

    /**
     * Слушатели со слабыми ссылками: автоматически удаляются при сборке мусора.
     */
    private final WeakHashMap<Consumer<EVENT>, Object> weakUpdateListeners = new WeakHashMap<>();

    /**
     * Слушатели с сильными ссылками: сохраняются до явного удаления.
     */
    private final List<Consumer<EVENT>> strongUpdateListeners = new CopyOnWriteArrayList<>();

    /**
     * Уведомляет всех зарегистрированных слушателей о наступлении события.
     * Сначала вызываются слушатели со слабыми ссылками, затем — со сильными.
     * @param event событие для передачи слушателям
     */
    public void fire(EVENT event){
        for( var ls : weakUpdateListeners.keySet() )ls.accept(event);
        for( var ls : strongUpdateListeners )ls.accept(event);
    }

    /**
     * Возвращает кол-во подписчиков
     * @return кол-во подписчиков
     */
    public int getListenersCount(){
        return weakUpdateListeners.size() + strongUpdateListeners.size();
    }

    /**
     * Создаёт построитель слушателя с явно указанным владельцем.
     * Защищённый метод для использования в подклассах или расширениях.
     * @param listener обработчик события
     * @param owner владелец (возвращаемое значение при завершении цепочки)
     * @return построитель слушателя
     */
    protected Append listener(Consumer<EVENT> listener, OWNER owner){
        return new Append(listener, owner);
    }

    /**
     * Начинает процесс регистрации слушателя события.
     * Владелец берётся через {@link #owner}.
     * @param listener обработчик события; не может быть {@code null}
     * @return построитель слушателя
     * @throws IllegalArgumentException если {@code listener == null}
     */
    public Append listener(Consumer<EVENT> listener) {
        if( listener==null ) throw new IllegalArgumentException("listener==null");
        return listener(listener, owner.get());
    }

    public UnBind listen(Consumer<EVENT> listener){
        if( listener==null ) throw new IllegalArgumentException("listener==null");

        UnBind uBind = new UnBind();
        listener(listener).unBind(uBind).bind();
        return uBind;
    }

    /**
     * Вспомогательный класс для настройки и регистрации слушателя через цепочку вызовов.
     */
    public class Append {
        private final Consumer<EVENT> listener;
        private final OWNER owner;

        /**
         * Создаёт построитель слушателя.
         * @param listener обработчик события
         * @param owner владелец (возвращается методом {@link #bind()})
         * @throws IllegalArgumentException если {@code listener == null}
         */
        public Append(Consumer<EVENT> listener, OWNER owner) {
            if( listener == null ) throw new IllegalArgumentException("listener==null");
            this.listener = listener;
            this.owner = owner;
        }

        //region weak
        private boolean weak = false;

        /**
         * Указывает, что слушатель должен храниться со слабой ссылкой.
         * @param v {@code true} — использовать слабую ссылку
         * @return текущий построитель
         */
        public Append weak(boolean v) {
            weak = v;
            return this;
        }

        /**
         * Указывает, что слушатель должен храниться со слабой ссылкой.
         * @return текущий построитель
         */
        public Append weak() {return weak(true);}
        //endregion

        //region unBind
        private UnBind unBind;

        /**
         * Привязывает отписку к объекту управления жизненным циклом.
         * При закрытии {@code unBind} слушатель будет автоматически удалён.
         * @param unBind объект управления отпиской
         * @return текущий построитель
         */
        public Append unBind(UnBind unBind) {
            this.unBind = unBind;
            return this;
        }
        //endregion

        /**
         * Регистрирует слушатель и завершает цепочку вызовов, возвращая владельца.
         * @return владелец (значение, полученное от {@link Listeners#owner})
         */
        public OWNER bind() {
            if( weak ){
                weakUpdateListeners.put(listener, true);
                if( unBind != null ) unBind.runnables().add(() -> {
                    weakUpdateListeners.remove(listener);
                });
                return owner;
            } else{
                strongUpdateListeners.add(listener);
                if( unBind != null ) unBind.runnables().add(() -> {
                    strongUpdateListeners.remove(listener);
                });
                return owner;
            }
        }
    }
}
