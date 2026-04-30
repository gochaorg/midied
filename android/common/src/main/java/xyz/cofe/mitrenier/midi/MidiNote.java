package xyz.cofe.mitrenier.midi;

public interface MidiNote {
    // @formatter:off
    /**
     * Имена октав
     */
    static  final String[] octaveNames = new String[]{
        "Субконтроктава",    // number -1 ; index 0
        "Контроктава",       // number  0 ; index 1
        "Большая октава",    // number  1 ; index 2
        "Малая октава",      // number  2 ; index 3
        "Первая октава",     // number  3 ; index 4
        "Вторая октава",     // number  4 ; index 5
        "Третья октава",     // number  5 ; index 6
        "Четвёртая октава",  // number  6 ; index 7
        "Пятая октава",      // number  7 ; index 8
        "Шестая октава",     // number  8 ; index 9
        "Седьмая октава"     // number  9 ; index 10
    };

    /**
     * Имена нот
     */
    static  final String[] noteNames = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
    // @formatter:on

    /**
     * Возвращает midi номер ноты
     * @return midi номер - 0..127
     */
    int note();

    /**
     * Индекс ноты в пределах октавы
     * @return 0..11, <br>
     * <ul>
     *     <li> 0 - До </li>
     *     <li> 1 - До-диез </li>
     *     <li> 2 - Ре </li>
     *     <li> 3 - Ре-диез </li>
     *     <li> 4 - Ми </li>
     *     <li> 5 - Фа </li>
     *     <li> 6 - Фа-диез </li>
     *     <li> 7 - Соль </li>
     *     <li> 8 - Соль-диез </li>
     *     <li> 9 - Ля </li>
     *     <li> 10 - Ля-диез </li>
     *     <li> 11 - Си </li>
     * </ul>
     *
     */
    default int noteIndex() {
        return note() % 12;
    }

    /**
     * Имя ноты согласно {@link #noteIndex()}
     * @return Имя ноты {@link #noteNames}
     */
    default String noteLetter() {
        return noteNames[noteIndex()];
    }

    /**
     * Имя ноты согласно {@link #noteIndex()} + {@link #octaveNumber()}
     * @return Имя ноты {@link #noteNames} + {@link #octaveNumber()}
     */
    default String noteName() {
        return noteNames[noteIndex()] + octaveNumber();
    }

    /**
     * Индекс октавы
     * @return 0..10
     */
    default int octaveIndex() {
        return note() / 12;
    }

    /**
     * Номер октавы
     * @return -1..9
     */
    default int octaveNumber() {
        return octaveIndex() - 1;
    }

    /**
     * Имя октавы, {@link #octaveNames} (Большая октава, Малая октава)
     * @return имя октавы
     */
    default String octaveName() {
        var i = octaveIndex();
        if( i < 0 ) return "?";
        if( i >= octaveNames.length ) return "?";
        return octaveNames[i];
    }

    /**
     * Создание ноты
     * @return Создание ноты
     */
    static OctaBuild octave(){ return new OctaBuild(); }

    /**
     * Создание ноты относительно номера
     * @param num номер октавы {@link #octaveNumber()}
     * @return Создание ноты
     */
    static NoteBuild octave(int num){ return new OctaBuild().number(num); }

    record OctaBuild() {
        public NoteBuild number(int num){
            if( num<-1 ) throw new IllegalArgumentException("num<-1");
            if( num>9 ) throw new IllegalArgumentException("num>9");

            return new NoteBuild(num+1);
        }
        public NoteBuild subContra(){ return new NoteBuild(0); }
        public NoteBuild contra(){ return new NoteBuild(1); }
        public NoteBuild great(){ return new NoteBuild(2); }
        public NoteBuild small(){ return new NoteBuild(3); }
        public NoteBuild first() { return new NoteBuild(4); }
        public NoteBuild second() { return new NoteBuild(5); }
        public NoteBuild third() { return new NoteBuild(6); }
        public NoteBuild fourth() { return new NoteBuild(7); }
        public NoteBuild fifth() { return new NoteBuild(8); }
        public NoteBuild sixth() { return new NoteBuild(9); }
        public NoteBuild seventh() { return new NoteBuild(10); }
    }

    record NoteBuild(int octaveIndex){
        public NoteBuild {
            if( octaveIndex<0 ) throw new IllegalArgumentException("octaveIndex<0");
            if( octaveIndex>10 ) throw new IllegalArgumentException("octaveIndex>10");
        }

        public int note(int num){
            int n = octaveIndex * 12 + num;
            if( n<0 ) throw new IllegalArgumentException("n<0");
            if( n>127 ) throw new IllegalArgumentException("n>127");
            return n;
        }

        // @formatter:off

        /** До-бемоль */
        public int Ces(){ return note(-1); }

        /** До */
        public int C(){ return note(0); }

        /** До-диез */
        public int Cis(){ return note(1); }

        /** Ре-бемоль */
        public int Des(){ return note(1); }

        /** Ре */
        public int D(){ return note(2); }

        /** Ре-диез */
        public int Dis(){ return note(3); }

        /** Ми-бемоль */
        public int Ees(){ return note(3); }

        /** Ми */
        public int E(){ return note(4); }

        /** Ми-диез */
        public int Eis(){ return note(5); }

        /** Фа-бемоль */
        public int Fes(){ return note(4); }

        /** Фа */
        public int F(){ return note(5); }

        /** Фа-диез */
        public int Fis(){ return note(6); }

        /** Соль-бемоль */
        public int Ges(){ return note(6); }

        /** Соль */
        public int G(){ return note(7); }

        /** Соль-диез */
        public int Gis(){ return note(8); }

        /** Ля-бемоль */
        public int Aes(){ return note(8); }

        /** Ля */
        public int A(){ return note(9); }

        /** Ля-диез */
        public int Ais(){ return note(10); }

        /** Си-бемоль */
        public int Bes(){ return note(10); }

        /** Си */
        public int B(){ return note(11); }

        /** Си-диез */
        public int Bis(){ return note(12); }

        // @formatter:on
    }
}
