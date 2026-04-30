package xyz.cofe.mitrenier.file;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import xyz.cofe.coll.im.ImList;
import xyz.cofe.coll.im.Result;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static xyz.cofe.coll.im.Result.error;
import static xyz.cofe.coll.im.Result.ok;

@DisplayName("UnixPath")
class UnixPathTest {

    // ===== Вспомогательные методы =====

    private UnixPath path(String... names) {
        ImList<String> list = ImList.of();
        for (int i = names.length - 1; i >= 0; i--) {
            list = list.prepend(names[i]);
        }
        return new UnixPath(list);
    }

    private <V, E> V assertOk(Result<V, E> result) {
        assertTrue(result.isOk(), () -> "Expected ok but got error: " + result.getError().orElse(null));
        return result.getOk().orElseThrow();
    }

    private <V, E> E assertError(Result<V, E> result) {
        assertTrue(result.isError(), () -> "Expected error but got ok: " + result.getOk().orElse(null));
        return result.getError().orElseThrow();
    }

    // ===== parse() =====

    @Nested
    @DisplayName("parse()")
    class ParseTests {

        @Test
        @DisplayName("null path returns error")
        void parseNull() {
            var result = UnixPath.parse(null);
            assertTrue(result.isError());
            assertEquals("path is null", assertError(result));
        }

        @ParameterizedTest
        @MethodSource("parseSuccessCases")
        @DisplayName("parses valid paths correctly")
        void parseSuccess(String input, String[] expectedNames) {
            var result = UnixPath.parse(input);
            var path = assertOk(result);
            var expected = path(expectedNames);
            assertTrue(path.equals(expected),
                () -> "Expected " + expected + " but got " + path);
        }

        static Stream<Arguments> parseSuccessCases() {
            return Stream.of(
                Arguments.of("", new String[]{""}),
                Arguments.of("/", new String[]{"", ""}),
                Arguments.of("a", new String[]{"a"}),
                Arguments.of("/a", new String[]{"", "a"}),
                Arguments.of("a/", new String[]{"a", ""}),
                Arguments.of("/a/", new String[]{"", "a", ""}),
                Arguments.of("a/b/c", new String[]{"a", "b", "c"}),
                Arguments.of("/a/b/c/", new String[]{"", "a", "b", "c", ""}),
                Arguments.of("a//b", new String[]{"a", "", "b"}), // пустые сегменты сохраняются при парсинге
                Arguments.of("\\/a", new String[]{"/a"}), // экранированный слеш
                Arguments.of("a\\/", new String[]{"a/"}),
                Arguments.of(".", new String[]{"."}),
                Arguments.of("..", new String[]{".."})
            );
        }
    }

    // ===== toString() =====

    @Nested
    @DisplayName("toString()")
    class ToStringTests {

        @ParameterizedTest
        @MethodSource("toStringCases")
        @DisplayName("converts path to string correctly")
        void toStringTest(String[] names, String expected) {
            var path = path(names);
            assertEquals(expected, path.toString());
        }

        static Stream<Arguments> toStringCases() {
            return Stream.of(
                Arguments.of(new String[]{""}, ""),
                Arguments.of(new String[]{"", ""}, "/"),
                Arguments.of(new String[]{"a"}, "a"),
                Arguments.of(new String[]{"", "a"}, "/a"),
                Arguments.of(new String[]{"a", ""}, "a/"),
                Arguments.of(new String[]{"", "a", ""}, "/a/"),
                Arguments.of(new String[]{"a", "b", "c"}, "a/b/c"),
                Arguments.of(new String[]{"", "a", "b", "c", ""}, "/a/b/c/"),
                Arguments.of(new String[]{"a/b"}, "a\\/b"), // экранирование слеша в имени
                Arguments.of(new String[]{".", ".."}, "./..")
            );
        }
    }

    // ===== Predicates =====

    @Nested
    @DisplayName("Predicates")
    class PredicateTests {

        @Test
        void isRoot() {
            assertTrue(path("", "").isRoot());
            assertFalse(path("", "a").isRoot());
            assertFalse(path("a").isRoot());
            assertFalse(path("").isRoot());
        }

        @Test
        void isEmpty() {
            assertTrue(path().isEmpty()); // ImList.of()
            assertTrue(path("").isEmpty());
            assertFalse(path("a").isEmpty());
            assertFalse(path("", "").isEmpty());
            assertFalse(path("", "a").isEmpty());
        }

        @Test
        void isAbsolute() {
            assertTrue(path("", "a").isAbsolute());
            assertTrue(path("", "").isAbsolute());
            assertFalse(path("a").isAbsolute());
            assertFalse(path().isAbsolute());
            assertFalse(path("").isAbsolute());
        }

        @Test
        void isDirectory() {
            // Замыкающий "" = директория
            assertTrue(path("a", "").isDirectory());
            assertTrue(path("", "a", "").isDirectory());
            // . и .. также считаются директориями
            assertTrue(path("a", ".").isDirectory());
            assertTrue(path("a", "..").isDirectory());
            // Обычные файлы
            assertFalse(path("a").isDirectory());
            assertFalse(path("", "a").isDirectory());
            // Пустой путь
            assertFalse(path().isDirectory());
            assertFalse(path("").isDirectory());
        }
    }

    // ===== equals() =====

    @Nested
    @DisplayName("equals()")
    class EqualsTests {

        @Test
        void equalsNull() {
            assertThrows(IllegalArgumentException.class, () -> path("a").equals(null));
        }

        @Test
        void equalsSame() {
            var p = path("", "a", "b");
            assertTrue(p.equals(p));
        }

        @Test
        void equalsDifferent() {
            assertFalse(path("a", "b").equals(path("a", "c")));
            assertFalse(path("", "a").equals(path("a")));
            assertFalse(path("a").equals(path("a", "")));
        }

        @Test
        void equalsWithTrailing() {
            assertTrue(path("a", "").equals(path("a", "")));
            assertFalse(path("a").equals(path("a", "")));
        }
    }

    // ===== normalize() =====

    @Nested
    @DisplayName("normalize()")
    class NormalizeTests {

        @ParameterizedTest
        @MethodSource("normalizeSuccessCases")
        @DisplayName("normalizes paths correctly")
        void normalizeSuccess(String[] input, String[] expected) {
            var result = path(input).normalize();
            var normalized = assertOk(result);
            var exp = path(expected);
            assertTrue(normalized.equals(exp),
                () -> "Expected " + exp + " but got " + normalized);
        }

        static Stream<Arguments> normalizeSuccessCases() {
            return Stream.of(
                // Удаление пустых сегментов (//)
                Arguments.of(new String[]{"", "", "a"}, new String[]{"", "a"}),
                Arguments.of(new String[]{"", "a", "", "b"}, new String[]{"", "a", "b"}),

                // Удаление .
                Arguments.of(new String[]{"", ".", "a"}, new String[]{"", "a"}),
                Arguments.of(new String[]{"a", ".", "b", "."}, new String[]{"a", "b"}),

                // Обработка .. в абсолютных путях
                Arguments.of(new String[]{"", "a", ".."}, new String[]{"", ""}), // корень
                Arguments.of(new String[]{"", "a", "b", ".."}, new String[]{"", "a"}),
                Arguments.of(new String[]{"", "a", "..", "b"}, new String[]{"", "b"}),
                Arguments.of(new String[]{"", "..", "a"}, new String[]{"", "a"}), // .. на корне игнорируется

                // Обработка .. в относительных путях
                Arguments.of(new String[]{"a", ".."}, new String[]{}), // пустой относительный
                Arguments.of(new String[]{"a", "b", ".."}, new String[]{"a"}),
                Arguments.of(new String[]{"..", "a"}, new String[]{"..", "a"}), // .. остаётся

                // Сохранение замыкающего /
                Arguments.of(new String[]{"", "a", "b", "", ".."}, new String[]{"", "a", ""}),
                Arguments.of(new String[]{"a", "b", "", ".."}, new String[]{"a", ""}),

                // Корень с замыкающим
                Arguments.of(new String[]{"", "a", "..", ""}, new String[]{"", ""}),

                // Сложные случаи
                Arguments.of(new String[]{"", "a", ".", "b", "..", "..", "c"}, new String[]{"", "c"}),
                Arguments.of(new String[]{"a", ".", "..", "..", "b"}, new String[]{"..", "b"})
            );
        }

    }

    // ===== makeAbsolute() =====

    @Nested
    @DisplayName("makeAbsolute()")
    class MakeAbsoluteTests {

        @Test
        void makeAbsoluteNull() {
            var result = path("a").makeAbsolute(null);
            assertTrue(result.isError());
            assertEquals("currentDir is null", assertError(result));
        }

        @Test
        void makeAbsoluteCurrentNotAbsolute() {
            var result = path("a").makeAbsolute(path("current"));
            assertTrue(result.isError());
            assertEquals("currentDir must be absolute", assertError(result));
        }

        @Test
        void makeAbsoluteAlreadyAbsolute() {
            var abs = path("", "a", "b");
            var result = abs.makeAbsolute(path("", "home"));
            var normalized = assertOk(result);
            assertTrue(normalized.equals(path("", "a", "b")));
        }

        @ParameterizedTest
        @MethodSource("makeAbsoluteSuccessCases")
        @DisplayName("makes relative paths absolute")
        void makeAbsoluteSuccess(String[] rel, String[] current, String[] expected) {
            var result = path(rel).makeAbsolute(path(current));
            var absolute = assertOk(result);
            var exp = path(expected);
            assertTrue(absolute.equals(exp),
                () -> "Expected " + exp + " but got " + absolute);
        }

        static Stream<Arguments> makeAbsoluteSuccessCases() {
            return Stream.of(
                Arguments.of(new String[]{"a"}, new String[]{"", "home", "user"}, new String[]{"", "home", "user", "a"}),
                Arguments.of(new String[]{"a", "b"}, new String[]{"", "home"}, new String[]{"", "home", "a", "b"}),
                Arguments.of(new String[]{"a", ""}, new String[]{"", "home", ""}, new String[]{"", "home", "a", ""}),
                Arguments.of(new String[]{"..", "docs"}, new String[]{"", "home", "user"}, new String[]{"", "home", "docs"}),
                Arguments.of(new String[]{"."}, new String[]{"", "var", "log"}, new String[]{"", "var", "log"})
            );
        }
    }

    // ===== relativeTo() =====

    @Nested
    @DisplayName("relativeTo()")
    class RelativeToTests {

        @Test
        void relativeToNull() {
            var result = path("", "a").relativeTo(null);
            assertTrue(result.isError());
            assertEquals("from is null", assertError(result));
        }

        @Test
        void relativeToNotAbsolute() {
            var result = path("", "a").relativeTo(path("relative"));
            assertTrue(result.isError());
            assertEquals("Both paths must be absolute to compute relative path", assertError(result));
        }

        @ParameterizedTest
        @MethodSource("relativeToSuccessCases")
        @DisplayName("computes relative paths correctly")
        void relativeToSuccess(String[] to, String[] from, String[] expected) {
            var result = path(to).relativeTo(path(from));
            var relative = assertOk(result);
            var exp = path(expected);
            assertTrue(relative.equals(exp),
                () -> "Expected " + exp + " but got " + relative);
        }

        static Stream<Arguments> relativeToSuccessCases() {
            return Stream.of(
                // Одинаковые пути
                Arguments.of(new String[]{"", "a", "b"}, new String[]{"", "a", "b"}, new String[]{"."}),
                Arguments.of(new String[]{"", "a", "b", ""}, new String[]{"", "a", "b"}, new String[]{"."}),

                // Прямое подчинение
                Arguments.of(new String[]{"", "a", "b", "c"}, new String[]{"", "a", "b"}, new String[]{"c"}),
                Arguments.of(new String[]{"", "a", "b", "c", ""}, new String[]{"", "a", "b"}, new String[]{"c", ""}),

                // Выход на уровень выше
                Arguments.of(new String[]{"", "a", "b"}, new String[]{"", "a", "b", "c"}, new String[]{".."}),

                // Сложные случаи
                Arguments.of(new String[]{"", "home", "user", "docs"}, new String[]{"", "home", "admin", "scripts"},
                    new String[]{"..", "..", "user", "docs"}),
                Arguments.of(new String[]{"", "opt", "app"}, new String[]{"", "usr", "local"},
                    new String[]{"..", "..", "opt", "app"}),

                // Сохранение замыкающего /
                Arguments.of(new String[]{"", "a", "b", ""}, new String[]{"", "a"}, new String[]{"b", ""}),

                // Корень
                Arguments.of(new String[]{"", ""}, new String[]{"", "a", "b"}, new String[]{"..", ".."}),
                Arguments.of(new String[]{"", "a"}, new String[]{"", ""}, new String[]{"a"})
            );
        }

        @Test
        void relativeToNoCommonRoot() {
            // Теоретически в Unix все абсолютные пути имеют общий корень "",
            // но проверяем защиту на всякий случай
            var result = path("", "a").relativeTo(path("", "b"));
            // Это должно успешно выполниться: ["..", "a"]
            var relative = assertOk(result);
            assertTrue(relative.equals(path("..", "a")));
        }
    }

    // ===== parent() =====

    @Nested
    @DisplayName("parent()")
    class ParentTests {

        @ParameterizedTest
        @MethodSource("parentErrorCases")
        @DisplayName("returns error for paths without parent")
        void parentError(String[] input, String expectedError) {
            var result = path(input).parent();
            assertTrue(result.isError());
            assertEquals(expectedError, assertError(result));
        }

        static Stream<Arguments> parentErrorCases() {
            return Stream.of(
                Arguments.of(new String[]{}, "Empty path has no parent"),
                Arguments.of(new String[]{""}, "Empty path has no parent"),
                Arguments.of(new String[]{"", ""}, "Root path has no parent")
            );
        }

        @ParameterizedTest
        @MethodSource("parentSuccessCases")
        @DisplayName("returns correct parent")
        void parentSuccess(String[] input, String[] expected) {
            var result = path(input).parent();
            var parent = assertOk(result);
            var exp = path(expected);
            assertTrue(parent.equals(exp),
                () -> "Expected " + exp + " but got " + parent);
        }

        static Stream<Arguments> parentSuccessCases() {
            return Stream.of(
                // Абсолютные пути
                Arguments.of(new String[]{"", "a"}, new String[]{"", ""}),
                Arguments.of(new String[]{"", "a", ""}, new String[]{"", ""}),
                Arguments.of(new String[]{"", "a", "b"}, new String[]{"", "a"}),
                Arguments.of(new String[]{"", "a", "b", ""}, new String[]{"", "a", ""}),
                Arguments.of(new String[]{"", "a", "b", "c"}, new String[]{"", "a", "b"}),

                // Относительные пути
                Arguments.of(new String[]{"a"}, new String[]{}),
                Arguments.of(new String[]{"a", ""}, new String[]{""}),
                Arguments.of(new String[]{"a", "b"}, new String[]{"a"}),
                Arguments.of(new String[]{"a", "b", ""}, new String[]{"a", ""}),

                // Сохранение замыкающего /
                Arguments.of(new String[]{"", "x", "y", "z", ""}, new String[]{"", "x", "y", ""}),
                Arguments.of(new String[]{"p", "q", "r", ""}, new String[]{"p", "q", ""})
            );
        }
    }

    // ===== resolve() =====

    @Nested
    @DisplayName("resolve()")
    class ResolveTests {

        @Test
        void resolveNullPath() {
            var result = path("a").resolve((UnixPath) null);
            assertTrue(result.isError());
            assertEquals("child is null", assertError(result));
        }

        @Test
        void resolveNullString() {
            var result = path("a").resolve((String) null);
            assertTrue(result.isError());
            assertEquals("childPath is null", assertError(result));
        }

        @Test
        @DisplayName("resolve(String) with absolute path string")
        void resolveStringAbsolute() {
            var current = path("", "home", "user");
            var result = current.resolve("/etc/config");
            var resolved = assertOk(result);
            // Абсолютный путь из строки должен переопределить контекст
            var expected = UnixPath.parse("/etc/config").unwrap();
            assertTrue(resolved.equals(expected),
                () -> "Expected " + expected + " but got " + resolved);
        }

        @ParameterizedTest
        @MethodSource("resolveSuccessCases")
        @DisplayName("resolves relative child paths")
        void resolveRelative(String[] current, String[] child, String[] expected) {
            var result = path(current).resolve(path(child));
            var resolved = assertOk(result);
            var exp = path(expected);
            assertTrue(resolved.equals(exp),
                () -> "Expected " + exp + " but got " + resolved);
        }

        static Stream<Arguments> resolveSuccessCases() {
            return Stream.of(
                Arguments.of(new String[]{"", "home"}, new String[]{"user", "docs"}, new String[]{"", "home", "user", "docs"}),
                Arguments.of(new String[]{"", "var", "log", ""}, new String[]{"app", ""}, new String[]{"", "var", "log", "app", ""}),
                Arguments.of(new String[]{"project"}, new String[]{"src", "main"}, new String[]{"project", "src", "main"}),
                // ИСПРАВЛЕНО: a/ + b = a/b, а не a//b
                Arguments.of(new String[]{"a", ""}, new String[]{"b"}, new String[]{"a", "b"}),
                Arguments.of(new String[]{}, new String[]{"x"}, new String[]{"x"}),
                // Абсолютный child переопределяет: ["", "home"] + ["", "etc"] = ["", "etc"]
                Arguments.of(new String[]{"", "home"}, new String[]{"", "etc"}, new String[]{"", "etc"})
            );
        }

        @Test
        void resolveStringParseError() {
            // parse() не должен вернуть ошибку для валидного Unix-пути,
            // но проверяем обработку ошибки парсинга если бы она была
            var result = path("a").resolve("valid/path");
            assertTrue(result.isOk());
        }
    }

    // ===== name(), nameWithoutExtension(), extension() =====

    @Nested
    @DisplayName("name() and extensions")
    class NameTests {

        @ParameterizedTest
        @MethodSource("nameErrorCases")
        @DisplayName("name() returns error for paths without name")
        void nameError(String[] input, String expectedError) {
            var result = path(input).name();
            assertTrue(result.isError());
            assertEquals(expectedError, assertError(result));
        }

        static Stream<Arguments> nameErrorCases() {
            return Stream.of(
                Arguments.of(new String[]{}, "Empty path has no name"),
                Arguments.of(new String[]{""}, "Empty path has no name"),
                Arguments.of(new String[]{"", ""}, "Root path has no name"),
                Arguments.of(new String[]{"", ""}, "Root path has no name") // корень с замыкающим
            );
        }

        @ParameterizedTest
        @MethodSource("nameSuccessCases")
        @DisplayName("name() returns correct name")
        void nameSuccess(String[] input, String expectedName) {
            var result = path(input).name();
            var name = assertOk(result);
            assertEquals(expectedName, name);
        }

        static Stream<Arguments> nameSuccessCases() {
            return Stream.of(
                Arguments.of(new String[]{"", "a"}, "a"),
                Arguments.of(new String[]{"", "a", ""}, "a"),
                Arguments.of(new String[]{"", "home", "user", "file.txt"}, "file.txt"),
                Arguments.of(new String[]{"", "var", "log", "app.log", ""}, "app.log"),
                Arguments.of(new String[]{"project", "src", "Main.java"}, "Main.java"),
                Arguments.of(new String[]{"docs", ""}, "docs"),
                Arguments.of(new String[]{"."}, "."),
                Arguments.of(new String[]{".."}, "..")
            );
        }

        @ParameterizedTest
        @MethodSource("nameWithoutExtensionCases")
        @DisplayName("nameWithoutExtension() works correctly")
        void nameWithoutExtension(String[] input, String expected) {
            var result = path(input).nameWithoutExtension();
            var name = assertOk(result);
            assertEquals(expected, name);
        }

        static Stream<Arguments> nameWithoutExtensionCases() {
            return Stream.of(
                Arguments.of(new String[]{"", "file.txt"}, "file"),
                Arguments.of(new String[]{"", "archive.tar.gz"}, "archive.tar"),
                Arguments.of(new String[]{"", "noext"}, "noext"),
                Arguments.of(new String[]{"", ".hidden"}, ".hidden"), // точка в начале - не расширение
                Arguments.of(new String[]{"", "file."}, "file"), // точка в конце
                Arguments.of(new String[]{"", "a.b.c.d"}, "a.b.c")
            );
        }

        @ParameterizedTest
        @MethodSource("extensionCases")
        @DisplayName("extension() works correctly")
        void extension(String[] input, String expected) {
            var result = path(input).extension();
            var ext = assertOk(result);
            assertEquals(expected, ext);
        }

        static Stream<Arguments> extensionCases() {
            return Stream.of(
                Arguments.of(new String[]{"", "file.txt"}, ".txt"),
                Arguments.of(new String[]{"", "archive.tar.gz"}, ".gz"),
                Arguments.of(new String[]{"", "noext"}, ""),
                Arguments.of(new String[]{"", ".hidden"}, ""), // точка в начале - не расширение
                Arguments.of(new String[]{"", "file."}, ""), // точка в конце - пустое расширение
                Arguments.of(new String[]{"", "a.b.c.d"}, ".d")
            );
        }
    }

    // ===== Интеграционные тесты =====

    @Nested
    @DisplayName("Integration")
    class IntegrationTests {

        @Test
        @DisplayName("parse -> normalize -> toString roundtrip")
        void parseNormalizeToString() {
            String[] inputs = {
                "/home/user/../docs/./file.txt",
                "a//b/./c/../d",
                "/var/log//app.log",
                "relative/path/.."
            };
            for (String input : inputs) {
                var parsed = UnixPath.parse(input);
                assertTrue(parsed.isOk(), "Failed to parse: " + input);
                var normalized = assertOk(parsed).normalize();
                assertTrue(normalized.isOk(), "Failed to normalize: " + input);
                // toString не должен бросать исключений
                assertDoesNotThrow(() -> assertOk(normalized).toString());
            }
        }

        @Test
        @DisplayName("makeAbsolute -> relativeTo roundtrip")
        void absoluteRelativeRoundtrip() {
            var current = UnixPath.parse("/home/user").unwrap();
            var relative = UnixPath.parse("docs/file.txt").unwrap();

            var absolute = relative.makeAbsolute(current);
            assertTrue(absolute.isOk());
            var absPath = assertOk(absolute);

            var backToRelative = absPath.relativeTo(current);
            assertTrue(backToRelative.isOk());

            // После нормализации должны получить эквивалентный путь
            var relNormalized = assertOk(relative.normalize());
            var backNormalized = assertOk(backToRelative.unwrap().normalize());
            assertTrue(relNormalized.equals(backNormalized) ||
                relNormalized.toString().equals(backNormalized.toString()));
        }

        @Test
        @DisplayName("parent -> resolve roundtrip")
        void parentResolveRoundtrip() {
            var path = UnixPath.parse("/home/user/docs/file.txt").unwrap();
            var parent = path.parent();
            assertTrue(parent.isOk());
            var parentPath = assertOk(parent);

            var name = path.name();
            assertTrue(name.isOk());
            var fileName = assertOk(name);

            var resolved = parentPath.resolve(fileName);
            assertTrue(resolved.isOk());
            // После нормализации должны получить исходный путь
            var originalNorm = assertOk(path.normalize());
            var resolvedNorm = assertOk(resolved.unwrap().normalize());
            assertTrue(originalNorm.equals(resolvedNorm));
        }
    }

    @Test
    public void asRelative(){
        assertEquals("a", UnixPath.parse("/a").unwrap().asRelative().toString());
        assertEquals("a/b", UnixPath.parse("/a/b").unwrap().asRelative().toString());
        assertEquals("a/b", UnixPath.parse("/a/b/").unwrap().asRelative().toString());
        assertEquals("a/..", UnixPath.parse("/a/../").unwrap().asRelative().toString());
        assertEquals("a/..", UnixPath.parse("a/../").unwrap().asRelative().toString());
    }
}