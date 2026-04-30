package xyz.cofe.mitrenier.file;

import xyz.cofe.coll.im.ImList;
import xyz.cofe.coll.im.Result;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static xyz.cofe.coll.im.Result.error;
import static xyz.cofe.coll.im.Result.ok;

public record UnixPath(
    ImList<String> names
)
{
    public UnixPath {
        if( names == null ) throw new IllegalArgumentException("names==null");
    }

    public static Result<UnixPath, String> parse(String path) {
        if( path == null ) return error("path is null");
        ImList<String> names = ImList.of();
        var state = "";
        var buff = new StringBuilder();
        for( var i = 0; i < path.length(); i++ ) {
            var c = path.charAt(i);
            switch( state ) {
                case "" -> {
                    switch( c ) {
                        case '\\' -> {
                            state = "esc";
                        }
                        case '/' -> {
                            names = names.prepend(buff.toString());
                            buff.setLength(0);
                        }
                        default -> {
                            buff.append(c);
                        }
                    }
                }
                case "esc" -> {
                    buff.append(c);
                    state = "";
                }
            }
        }
        names = names.prepend(buff.toString());
        return ok(new UnixPath(names.reverse()));
    }

    public String toString() {
        var sb = new StringBuilder();
        var idx = -1;
        for( var n : names ) {
            idx++;
            if( idx > 0 ) {
                sb.append("/");
            }
            sb.append(n.replace("/", "\\/"));
        }
        return sb.toString();
    }

    public boolean isRoot() {
        return names.size() == 2
            && names.get(0).map(String::isEmpty).orElse(false)
            && names.get(1).map(String::isEmpty).orElse(false);
    }

    public boolean isEmpty() {
        return
            names.isEmpty() ||
                (names.size() == 1 && names.get(0).map(String::isEmpty).orElse(false));
    }

    public boolean isAbsolute() {
        return names.size() > 1 && names.get(0).map(String::isEmpty).orElse(false);
    }

    public boolean isDirectory() {
        if( isEmpty() ) return false;
        return names.last().map(n -> n.isEmpty() || n.equals(".") || n.equals("..")).orElse(false);
    }

    public boolean equals(UnixPath path) {
        if( path == null ) throw new IllegalArgumentException("path==null");
        if( path.names.size() != names.size() ) return false;
        return names.zip(path.names).foldLeft(true, (r, t) -> r && t._1().equals(t._2()));
    }

    public boolean equals(Object o){
        if( o==null )return false;
        if( o instanceof UnixPath u ){
            return equals(u);
        }
        return false;
    }

    @Override
    public int hashCode() {
        Object[] objs = new Object[names.size()];
        for( var i=0; i<objs.length; i++ ){
            objs[i] = names.get(i).orElse(null);
        }
        return Objects.hash(objs);
    }

    /**
     * Нормализует путь:
     * - удаляет пустые сегменты от // (кроме ведущего/замыкающего)
     * - удаляет точки (.)
     * - разрешает двойные точки (..)
     * - сохраняет замыкающий пустой сегмент, если входной путь был "директорией"
     * - для относительных путей ".." добавляется как компонент, если стек пуст
     */
    public Result<UnixPath, String> normalize() {
        List<String> components = new ArrayList<>();
        for( String n : names ) components.add(n);

        boolean isAbs = isAbsolute();

        // Определяем, был ли входной путь "директорией":
        // - заканчивается на "" (явный /), ИЛИ
        // - заканчивается на "." или ".." И содержит "" на позиции > 0 (слеш перед спец-компонентом)
        boolean inputWasDirectory;
        if (components.isEmpty()) {
            inputWasDirectory = false;
        } else {
            String last = components.get(components.size() - 1);
            if (last.isEmpty()) {
                inputWasDirectory = true;  // Явный замыкающий /
            } else if (last.equals(".") || last.equals("..")) {
                // Проверяем наличие "" на позиции > 0 (перед спец-компонентом)
                inputWasDirectory = false;
                for (int i = 1; i < components.size() - 1; i++) {
                    if (components.get(i).isEmpty()) {
                        inputWasDirectory = true;
                        break;
                    }
                }
            } else {
                inputWasDirectory = false;  // Обычный файл
            }
        }

        // Диапазон обработки: исключаем замыкающий пустой, если он есть в конце
        int processEnd = components.size();
        if (!components.isEmpty() && components.get(components.size() - 1).isEmpty()) {
            processEnd = components.size() - 1;
        }

        var stack = new ArrayDeque<String>();

        for( int i = 0; i < processEnd; i++ ) {
            String name = components.get(i);

            if( name.isEmpty() ) {
                continue; // Пропускаем пустые (от //)
            }

            if( name.equals(".") ) {
                continue; // Текущая директория — игнорируем
            }

            if( name.equals("..") ) {
                if( isAbs ) {
                    // Абсолютный путь: не удаляем корень
                    if( !stack.isEmpty() ) {
                        stack.pollLast();
                    }
                } else {
                    // Относительный путь:
                    // - если стек не пуст и верхний элемент не "..", удаляем его
                    // - иначе добавляем ".." как литературный компонент
                    if( !stack.isEmpty() && !stack.peekLast().equals("..") ) {
                        stack.pollLast();
                    } else {
                        stack.addLast("..");
                    }
                }
            } else {
                stack.addLast(name);
            }
        }

        // Формируем результат
        List<String> result = new ArrayList<>();

        if( isAbs ) {
            result.add(""); // Ведущий пустой для абсолютного пути
            if( stack.isEmpty() ) {
                // Нормализация привела к корню: ["", ""]
                result.add("");
            } else {
                for( String s : stack ) result.add(s);
            }
        } else {
            // Относительный путь
            for( String s : stack ) result.add(s);
        }

        // Добавляем замыкающий слеш если входной путь был "директорией"
        if (inputWasDirectory) {
            boolean isRoot = isAbs && result.size() == 2 && result.get(1).isEmpty();
            if (!isRoot) {
                if (result.isEmpty()) {
                    // Пустой относительный результат + был директорией → [""]
                    if (!isAbs) {
                        result.add("");
                    }
                } else {
                    String last = result.get(result.size() - 1);
                    if (!last.equals(".") && !last.equals("..")) {
                        result.add("");
                    }
                }
            }
        }

        // Конвертируем обратно в ImList
        ImList<String> resultNames = ImList.of();
        for( int i = result.size() - 1; i >= 0; i-- ) {
            resultNames = resultNames.prepend(result.get(i));
        }

        return ok(new UnixPath(resultNames));
    }

    /**
     * Преобразует относительный путь в абсолютный относительно currentDir.
     * Если путь уже абсолютный, возвращается его нормализованная копия.
     */
    public Result<UnixPath, String> makeAbsolute(UnixPath currentDir) {
        if( currentDir == null ) return error("currentDir is null");
        if( isAbsolute() ) return normalize();
        if( !currentDir.isAbsolute() ) return error("currentDir must be absolute");

        // Комбинируем компоненты: currentDir + this (без дублирования корня)
        List<String> combined = new ArrayList<>();
        for( String n : currentDir.names ) combined.add(n);

        // Добавляем компоненты текущего пути, пропуская ведущий пустой (если вдруг есть)
        boolean first = true;
        for( String n : this.names ) {
            if( first && n.isEmpty() && !isAbsolute() ) {
                first = false;
                continue;
            }
            first = false;
            combined.add(n);
        }

        // Собираем в ImList и нормализуем (normalize() сохранит замыкающий / если нужно)
        ImList<String> combinedIm = ImList.of();
        for( int i = combined.size() - 1; i >= 0; i-- ) {
            combinedIm = combinedIm.prepend(combined.get(i));
        }

        return new UnixPath(combinedIm).normalize();
    }

    /**
     * Вычисляет относительный путь от абсолютного 'from' к текущему абсолютному пути.
     * Оба пути нормализуются перед вычислением.
     * Замыкающий '/' целевого пути сохраняется в результате, кроме случаев когда результат — "." или заканчивается на ".."
     */
    public Result<UnixPath, String> relativeTo(UnixPath from) {
        if (from == null) return error("from is null");

        var normFrom = from.normalize();
        var normTo = this.normalize();
        if (normFrom.isError()) return normFrom;
        if (normTo.isError()) return normTo;

        UnixPath f = normFrom.getOk().get();
        UnixPath t = normTo.getOk().get();

        if (!f.isAbsolute() || !t.isAbsolute()) {
            return error("Both paths must be absolute to compute relative path");
        }

        List<String> fList = new ArrayList<>();
        for (String n : f.names) fList.add(n);

        List<String> tList = new ArrayList<>();
        for (String n : t.names) tList.add(n);

        // Запоминаем, был ли замыкающий слеш у нормализованного целевого пути
        boolean toHasTrailing = tList.size() > 1 && tList.get(tList.size() - 1).isEmpty();

        // Для сравнения убираем замыкающие пустые
        List<String> fComp = new ArrayList<>(fList);
        List<String> tComp = new ArrayList<>(tList);
        if (fComp.size() > 1 && fComp.get(fComp.size() - 1).isEmpty()) fComp.remove(fComp.size() - 1);
        if (tComp.size() > 1 && tComp.get(tComp.size() - 1).isEmpty()) tComp.remove(tComp.size() - 1);

        // Ищем общий префикс
        int common = 0;
        int minLen = Math.min(fComp.size(), tComp.size());
        while (common < minLen && fComp.get(common).equals(tComp.get(common))) {
            common++;
        }

        if (common == 0) return error("Paths have no common root");

        // Строим относительный путь
        List<String> relNames = new ArrayList<>();

        for (int i = common; i < fComp.size(); i++) {
            relNames.add("..");
        }

        for (int i = common; i < tComp.size(); i++) {
            relNames.add(tComp.get(i));
        }

        // Если пути идентичны — возвращаем "." БЕЗ замыкающего /
        if (relNames.isEmpty()) {
            relNames.add(".");
            ImList<String> resIm = ImList.of();
            for (int i = relNames.size() - 1; i >= 0; i--) {
                resIm = resIm.prepend(relNames.get(i));
            }
            return ok(new UnixPath(resIm));
        }

        // Восстанавливаем замыкающий слеш, если он был у целевого пути
        // Но НЕ добавляем, если результат заканчивается на "." или ".."
        if (toHasTrailing && !relNames.isEmpty()) {
            String last = relNames.get(relNames.size() - 1);
            if (!last.equals(".") && !last.equals("..") && !last.isEmpty()) {
                relNames.add("");
            }
        }

        // Конвертируем в ImList
        ImList<String> resIm = ImList.of();
        for (int i = relNames.size() - 1; i >= 0; i--) {
            resIm = resIm.prepend(relNames.get(i));
        }

        return ok(new UnixPath(resIm));
    }

    /**
     * Возвращает родительский каталог текущего пути.
     * <p>
     * Семантика:
     * <ul>
     *   <li>Для корня {@code [ "", "" ]} возвращает {@code error} — у корня нет родителя</li>
     *   <li>Для абсолютного пути удаляет последний компонент, сохраняя корень {@code ""} на позиции 0</li>
     *   <li>Для относительного пути удаляет последний компонент</li>
     *   <li>Если исходный путь имел замыкающий {@code /} (последний элемент {@code ""}),
     *       результат также будет иметь замыкающий {@code /} (кроме случая нормализации до корня)</li>
     *   <li>Для относительного пути, если результат пустой, возвращается {@code [""]} как каноническое
     *       представление текущей директории (особенно если исходный путь имел замыкающий {@code /})</li>
     * </ul>
     *
     * @return {@code ok(UnixPath)} с родительским путём, или {@code error} если родителя нет
     */
    public Result<UnixPath, String> parent() {
        // Копируем компоненты для манипуляций
        List<String> components = new ArrayList<>();
        for (String n : names) components.add(n);

        // Проверка на пустой путь или корень
        if (isEmpty() || isRoot()) {
            return error(isEmpty() ? "Empty path has no parent" : "Root path has no parent");
        }

        boolean isAbs = isAbsolute();
        boolean hasTrailing = components.size() > 1 &&
            components.get(components.size() - 1).isEmpty();

        // Рабочая копия: временно убираем замыкающий пустой для обработки
        List<String> working = new ArrayList<>(components);
        if (hasTrailing) {
            working.remove(working.size() - 1);
        }

        // Удаляем последний значимый компонент (но не корень для абсолютных путей)
        if (working.size() > (isAbs ? 1 : 0)) {
            working.remove(working.size() - 1);
        }

        // Если для абсолютного пути остался только маркер корня — делаем его корнем-директорией ["", ""]
        if (isAbs && working.size() == 1 && working.get(0).isEmpty()) {
            working.add("");
        }
        // Сохраняем замыкающий / если он был в исходном пути
        // (для относительных путей, даже если результат пустой, это означает [""] для текущей директории)
        else if (hasTrailing) {
            // Для относительных путей: добавляем замыкающий даже если результат пустой
            // Для абсолютных путей: добавляем замыкающий только если есть компоненты кроме корня
            if (!isAbs || working.size() > 1) {
                working.add("");
            }
        }

        // Конвертируем обратно в ImList (prepend требует обратного порядка)
        ImList<String> resultNames = ImList.of();
        for (int i = working.size() - 1; i >= 0; i--) {
            resultNames = resultNames.prepend(working.get(i));
        }

        return ok(new UnixPath(resultNames));
    }

    /**
     * Разрешает дочерний путь относительно текущего каталога.
     * <p>
     * Семантика (стандарт Unix):
     * <ul>
     *   <li>Если {@code child} абсолютный — возвращается {@code child} как есть</li>
     *   <li>Если {@code child} относительный — его компоненты добавляются к текущему пути</li>
     *   <li>Если текущий путь имеет замыкающий {@code /} (последний элемент {@code ""}),
     *       он удаляется перед конкатенацией, чтобы избежать {@code //} в результате</li>
     *   <li>Замыкающий {@code /} дочернего пути сохраняется в результате</li>
     *   <li>Нормализация НЕ выполняется автоматически — вызывайте {@link #normalize()} отдельно при необходимости</li>
     * </ul>
     *
     * @param child дочерний путь для разрешения
     * @return {@code ok(UnixPath)} с разрешённым путём, или {@code error} при некорректных входных данных
     */
    public Result<UnixPath, String> resolve(UnixPath child) {
        if (child == null) return error("child is null");

        // Абсолютный дочерний путь переопределяет контекст — стандартное поведение Unix
        if (child.isAbsolute()) {
            return ok(child);
        }

        // Конкатенация компонентов с обработкой замыкающего "/" текущего пути
        List<String> result = new ArrayList<>();

        // Копируем компоненты текущего пути
        for (String n : this.names) result.add(n);

        // Если текущий путь имеет замыкающий "" (индикатор директории /),
        // удаляем его перед добавлением относительного дочернего пути, чтобы избежать "//"
        boolean currentHasTrailing = !result.isEmpty() && result.get(result.size() - 1).isEmpty();
        if (currentHasTrailing) {
            result.remove(result.size() - 1);
        }

        // Добавляем компоненты дочернего пути
        for (String n : child.names) result.add(n);

        // Конвертируем в ImList (prepend требует обратного порядка)
        ImList<String> resultNames = ImList.of();
        for (int i = result.size() - 1; i >= 0; i--) {
            resultNames = resultNames.prepend(result.get(i));
        }

        return ok(new UnixPath(resultNames));
    }

    /**
     * Удобная перегрузка: разрешает строковый путь относительно текущего каталога.
     * @param childPath строковое представление дочернего пути в Unix-формате
     * @return результат разрешения или ошибка парсинга
     */
    public Result<UnixPath, String> resolve(String childPath) {
        if (childPath == null) return error("childPath is null");
        var parsed = parse(childPath);
        if (parsed.isError()) return parsed;
        return resolve(parsed.getOk().get());
    }

    /**
     * Возвращает имя файла или каталога (последний значимый компонент пути).
     * <p>
     * Семантика:
     * <ul>
     *   <li>Для пустого пути или корня возвращает {@code error} — у них нет имени</li>
     *   <li>Если путь имеет замыкающий {@code /} (последний элемент {@code ""}),
     *       имя извлекается из предпоследнего компонента</li>
     *   <li>Для абсолютных путей пропускается ведущий маркер корня {@code ""}</li>
     *   <li>Возвращается "чистое" имя без модификации (без добавления/удаления слешей)</li>
     * </ul>
     *
     * @return {@code ok(String)} с именем, или {@code error} если имя не может быть извлечено
     */
    public Result<String, String> name() {
        // Копируем компоненты для анализа
        List<String> components = new ArrayList<>();
        for (String n : names) components.add(n);

        // Пустой путь или корень не имеют имени
        if (isEmpty() || isRoot()) {
            return error(isEmpty() ? "Empty path has no name" : "Root path has no name");
        }

        // Определяем диапазон значимых компонентов
        int start = isAbsolute() ? 1 : 0;  // Пропускаем маркер корня для абсолютных путей
        int end = components.size();

        // Если есть замыкающий слеш (пустой последний элемент), исключаем его из рассмотрения
        if (end > start && components.get(end - 1).isEmpty()) {
            end--;
        }

        // Если после фильтрации не осталось компонентов — ошибка
        if (end <= start) {
            return error("Path has no name component");
        }

        // Возвращаем последний значимый компонент
        return ok(components.get(end - 1));
    }

    /**
     * Возвращает имя файла без расширения.
     * <p>
     * Если имя содержит точку (не на первой позиции), возвращается часть до последней точки.
     * Точка в конце имени также удаляется (считается пустым расширением).
     * Если точка отсутствует или имя начинается с точки (скрытый файл) — возвращается имя как есть.
     *
     * @return {@code ok(String)} с именем без расширения, или {@code error} если имя не может быть извлечено
     */
    public Result<String, String> nameWithoutExtension() {
        return name().map(n -> {
            int lastDot = n.lastIndexOf('.');
            // Убираем расширение, если точка есть и она не первая (не скрытый файл)
            if (lastDot > 0) {
                return n.substring(0, lastDot);
            }
            return n;
        });
    }

    /**
     * Возвращает расширение файла (часть после последней точки), включая точку.
     * <p>
     * Если расширение отсутствует — возвращается пустая строка.
     *
     * @return {@code ok(String)} с расширением (например, {@code ".txt"}), или {@code error}
     */
    public Result<String, String> extension() {
        return name().map(n -> {
            int lastDot = n.lastIndexOf('.');
            if (lastDot > 0 && lastDot < n.length() - 1) {
                return n.substring(lastDot);
            }
            return "";
        });
    }

    public UnixPath asRelative(){
        ImList<String> names = ImList.of();
        for( String name : this.names ){
            if( name.isEmpty() )continue;
            names = names.prepend(name);
        }
        return new UnixPath(names.reverse());
    }
}
