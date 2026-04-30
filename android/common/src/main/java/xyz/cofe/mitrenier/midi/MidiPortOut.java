package xyz.cofe.mitrenier.midi;

/**
 * Вывод сигнала в midi
 */
public interface MidiPortOut {
    public boolean isRunning();

    /**
     * Вызывается в начале воспроизведения
     */
    public void start();

    /**
     * Посылка сигнала в midi
     * @param event сигнал
     */
    public void send(MidiEvent<?> event);

    /**
     * Посылка сигнала в midi
     * @param event сигнал
     * @param nano Время отправки относительно {@link System#nanoTime()}
     */
    public void sendAt(MidiEvent<?> event, long nano);

    /**
     * Вызывается в Конце воспроизведения
     */
    public void stop();
}
