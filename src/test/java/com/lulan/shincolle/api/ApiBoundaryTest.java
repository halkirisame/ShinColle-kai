package com.lulan.shincolle.api;

import com.lulan.shincolle.api.attribute.ShipAttributeLayout;
import com.lulan.shincolle.apiboundaryfixture.InternalClassMarker;
import com.lulan.shincolle.apiboundaryfixture.InternalMarker;
import com.lulan.shincolle.apiboundaryfixture.InternalType;
import com.lulan.shincolle.apiboundaryfixture.InternalTypeUseMarker;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The public API package must not depend on, or hand out, anything inside the mod's internals.
 *
 * <p>Two checks cover different failures. The source check reads only the {@code import} declarations of the API
 * sources: an import of an internal package is forbidden even when it is unused or only used by a comment, and
 * the compiler leaves no trace of such an import. The class-file check reads the types that the compiled API
 * classes really name (supertypes, field and method types, signatures, calls, annotations), after the compiler
 * has resolved every spelling. It relies on {@code test} running after {@code compileJava}.</p>
 */
class ApiBoundaryTest {

    private static final Path API_SOURCES = Path.of("src/main/java/com/lulan/shincolle/api");
    private static final Path API_CLASSES = Path.of("build/classes/java/main/com/lulan/shincolle/api");
    private static final Pattern DESCRIPTOR_TYPE = Pattern.compile("L([^;<]+)[;<]");

    // ---- source check: parsed with the JDK's own parser ------------------------------------------------

    /**
     * Returns every qualified name in the source that starts with {@code com.lulan.shincolle} and whose next
     * element is not exactly {@code api}: in import declarations (also unused, static and wildcard ones) and in
     * qualified names written in code. The compiler's parser has already resolved Unicode escapes, comments,
     * literals and line breaks, so none of that is handled here.
     */
    static List<String> forbiddenReferences(String source) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "The test JVM has no system Java compiler (module jdk.compiler)");
        JavaFileObject file = new SimpleJavaFileObject(URI.create("string:///Source.java"), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return source;
            }
        };
        List<String> result = new ArrayList<>();
        try {
            JavacTask task = (JavacTask) compiler.getTask(null, null, diagnostic -> { },
                    List.of("-proc:none"), null, List.of(file));
            for (CompilationUnitTree unit : task.parse()) {
                for (ImportTree declaration : unit.getImports()) {
                    check(qualifiedName(declaration.getQualifiedIdentifier()), result);
                }
                new TreeScanner<Void, Void>() {
                    @Override
                    public Void visitMemberSelect(MemberSelectTree select, Void unused) {
                        List<String> name = qualifiedName(select);
                        if (name == null) {
                            return super.visitMemberSelect(select, unused);
                        }
                        check(name, result);
                        return null;
                    }
                }.scan(unit.getTypeDecls(), null);
            }
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        return result;
    }

    private static void check(List<String> name, List<String> result) {
        if (name != null && name.size() >= 4 && name.subList(0, 3).equals(List.of("com", "lulan", "shincolle"))
                && !name.get(3).equals("api")) {
            result.add(String.join(".", name));
        }
    }

    /** The identifiers of {@code a.b.c}, or null when the expression does not start with a plain identifier. */
    private static List<String> qualifiedName(Tree expression) {
        if (expression instanceof IdentifierTree identifier) {
            List<String> name = new ArrayList<>();
            name.add(withoutIgnorable(identifier.getName().toString()));
            return name;
        }
        if (expression instanceof MemberSelectTree select) {
            List<String> name = qualifiedName(select.getExpression());
            if (name != null) {
                name.add(withoutIgnorable(select.getIdentifier().toString()));
            }
            return name;
        }
        return null;
    }

    /** Java ignores some characters (for example U+200B) when it compares identifiers. */
    private static String withoutIgnorable(String identifier) {
        StringBuilder out = new StringBuilder(identifier.length());
        identifier.codePoints().filter(cp -> !Character.isIdentifierIgnorable(cp)).forEach(out::appendCodePoint);
        return out.toString();
    }

    // ---- class-file check: types the classes name ------------------------------------------------------

    static boolean isInternalType(String internalName) {
        return internalName.startsWith("com/lulan/shincolle/") && !internalName.startsWith("com/lulan/shincolle/api/");
    }

    /**
     * Returns every ShinColle type outside the API that a class file names as a type: class entries, field and
     * method descriptors, signatures, and annotation types and class values. String constants and annotation
     * strings are not types and are not looked at.
     */
    static List<String> internalReferences(byte[] classFile) throws IOException {
        TreeSet<String> internal = new TreeSet<>();
        for (String name : referencedTypes(classFile)) {
            if (isInternalType(name)) {
                internal.add(name);
            }
        }
        return new ArrayList<>(internal);
    }

    private static List<String> referencedTypes(byte[] classFile) throws IOException {
        List<String> types = new ArrayList<>();
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(classFile))) {
            assertEquals(0xCAFEBABE, in.readInt(), "not a class file");
            in.readUnsignedShort();
            in.readUnsignedShort();
            int count = in.readUnsignedShort();
            String[] utf8 = new String[count];
            int[] classNameOf = new int[count];
            List<Integer> descriptorIndexes = new ArrayList<>();
            for (int index = 1; index < count; index++) {
                int tag = in.readUnsignedByte();
                switch (tag) {
                    case 1 -> utf8[index] = in.readUTF();
                    case 3, 4, 9, 10, 11, 17, 18 -> in.skipNBytes(4);
                    case 5, 6 -> {
                        in.skipNBytes(8);
                        index++;
                    }
                    case 7 -> classNameOf[index] = in.readUnsignedShort();
                    case 8, 19, 20 -> in.skipNBytes(2);
                    case 12 -> {
                        in.skipNBytes(2);
                        descriptorIndexes.add(in.readUnsignedShort());
                    }
                    case 15 -> in.skipNBytes(3);
                    case 16 -> descriptorIndexes.add(in.readUnsignedShort());
                    default -> throw new IOException("Unknown constant pool tag " + tag);
                }
            }
            for (int index = 1; index < count; index++) {
                if (classNameOf[index] != 0) {
                    addClassName(types, utf8[classNameOf[index]]);
                }
            }
            for (int descriptor : descriptorIndexes) {
                addDescriptorTypes(types, utf8[descriptor]);
            }
            in.skipNBytes(6);
            in.skipNBytes(2L * in.readUnsignedShort());
            for (int member = 0; member < 2; member++) {
                int members = in.readUnsignedShort();
                for (int i = 0; i < members; i++) {
                    in.skipNBytes(4);
                    addDescriptorTypes(types, utf8[in.readUnsignedShort()]);
                    readAttributes(in, utf8, types);
                }
            }
            readAttributes(in, utf8, types);
        }
        return types;
    }

    private static void addClassName(List<String> types, String name) {
        if (name.startsWith("[")) {
            addDescriptorTypes(types, name);
        } else {
            types.add(name);
        }
    }

    private static void addDescriptorTypes(List<String> types, String descriptorOrSignature) {
        Matcher matcher = DESCRIPTOR_TYPE.matcher(descriptorOrSignature);
        while (matcher.find()) {
            types.add(matcher.group(1));
        }
    }

    private static void readAttributes(DataInputStream in, String[] utf8, List<String> types) throws IOException {
        int attributes = in.readUnsignedShort();
        for (int i = 0; i < attributes; i++) {
            String name = utf8[in.readUnsignedShort()];
            long length = Integer.toUnsignedLong(in.readInt());
            switch (name) {
                case "Signature" -> {
                    addDescriptorTypes(types, utf8[in.readUnsignedShort()]);
                    in.skipNBytes(length - 2);
                }
                case "RuntimeVisibleAnnotations", "RuntimeInvisibleAnnotations" -> {
                    int annotations = in.readUnsignedShort();
                    for (int a = 0; a < annotations; a++) {
                        readAnnotation(in, utf8, types);
                    }
                }
                case "RuntimeVisibleParameterAnnotations", "RuntimeInvisibleParameterAnnotations" -> {
                    int parameters = in.readUnsignedByte();
                    for (int p = 0; p < parameters; p++) {
                        int annotations = in.readUnsignedShort();
                        for (int a = 0; a < annotations; a++) {
                            readAnnotation(in, utf8, types);
                        }
                    }
                }
                case "RuntimeVisibleTypeAnnotations", "RuntimeInvisibleTypeAnnotations" -> {
                    int annotations = in.readUnsignedShort();
                    for (int a = 0; a < annotations; a++) {
                        skipTypeAnnotationTarget(in);
                        readAnnotation(in, utf8, types);
                    }
                }
                case "AnnotationDefault" -> readElementValue(in, utf8, types);
                default -> in.skipNBytes(length);
            }
        }
    }

    /** Skips target_type, target_info and type_path of a type annotation, up to its annotation. */
    private static void skipTypeAnnotationTarget(DataInputStream in) throws IOException {
        int targetType = in.readUnsignedByte();
        switch (targetType) {
            case 0x00, 0x01, 0x16 -> in.skipNBytes(1);
            case 0x10, 0x17, 0x42, 0x43, 0x44, 0x45, 0x46 -> in.skipNBytes(2);
            case 0x11, 0x12 -> in.skipNBytes(2);
            case 0x13, 0x14, 0x15 -> { }
            case 0x40, 0x41 -> in.skipNBytes(6L * in.readUnsignedShort());
            case 0x47, 0x48, 0x49, 0x4A, 0x4B -> in.skipNBytes(3);
            default -> throw new IOException("Unknown type annotation target " + targetType);
        }
        in.skipNBytes(2L * in.readUnsignedByte());
    }

    private static void readAnnotation(DataInputStream in, String[] utf8, List<String> types) throws IOException {
        addDescriptorTypes(types, utf8[in.readUnsignedShort()]);
        int pairs = in.readUnsignedShort();
        for (int i = 0; i < pairs; i++) {
            in.skipNBytes(2);
            readElementValue(in, utf8, types);
        }
    }

    private static void readElementValue(DataInputStream in, String[] utf8, List<String> types) throws IOException {
        int tag = in.readUnsignedByte();
        switch (tag) {
            case 'e' -> {
                addDescriptorTypes(types, utf8[in.readUnsignedShort()]);
                in.skipNBytes(2);
            }
            case 'c' -> addDescriptorTypes(types, utf8[in.readUnsignedShort()]);
            case '@' -> readAnnotation(in, utf8, types);
            case '[' -> {
                int values = in.readUnsignedShort();
                for (int i = 0; i < values; i++) {
                    readElementValue(in, utf8, types);
                }
            }
            default -> in.skipNBytes(2);
        }
    }

    private static byte[] bytesOf(Class<?> type) throws IOException {
        String file = type.getName().substring(type.getName().lastIndexOf('.') + 1) + ".class";
        try (InputStream in = type.getResourceAsStream(file)) {
            assertTrue(in != null, "class file of " + type + " not found");
            return in.readAllBytes();
        }
    }

    // ---- fixtures for the class-file check -------------------------------------------------------------

    static final class FieldUse {
        InternalType field;
    }

    static final class MethodUse {
        Object make() {
            return new InternalType();
        }
    }

    static final class GenericUse {
        List<InternalType> field;
    }

    @InternalMarker
    static final class RuntimeAnnotationUse {
    }

    @InternalClassMarker
    static final class ClassAnnotationUse {
    }

    static final class TypeAnnotationUse {
        @InternalTypeUseMarker String field;
    }

    @Deprecated(since = "com/lulan/shincolle/apiboundaryfixture/InternalType")
    static final class StringOnly {
        static final String NAME = "com/lulan/shincolle/apiboundaryfixture/InternalType";
    }

    static final class PartOfAnotherName {
        static final String NAME = "other/com/lulan/shincolle/equip/ShipEquipmentResolver";
        static final String DESCRIPTOR = "Lother/com/lulan/shincolle/equip/ShipEquipmentResolver;";
    }

    // ---- tests -----------------------------------------------------------------------------------------

    @Test
    void sourceCheckRejectsEveryWayOfWritingAnInternalReference() {
        String[] forbidden = {
            "import com.lulan.shincolle.reference.Reference;",
            "import static com.lulan.shincolle.utility.EnchantHelper.helper;",
            "import static com.lulan.shincolle.utility.*;",
            "import com.lulan.shincolle.utility.*;",
            "import com . lulan . shincolle . reference . Reference;",
            "import com\n    .lulan\n    .shincolle\n    .reference.Reference;",
            "import com/* split */.lulan.shincolle.reference.Reference;",
            "import com.// split\n lulan.shincolle.reference.Reference;",
            "package a; /* c */ import com.lulan.shincolle.reference.Reference;",
            "package a;/* c */import com.lulan.shincolle.reference.Reference;/* d */",
            "package a; import com.lulan.shincolle.reference.Reference;",
            "import static/* c */com.lulan.shincolle.reference.Reference.MOD_ID;",
            "import com.lulan.shincolle.api$internal.Hidden;",
            "import com.lulan.shincolle.apiInternal.Hidden;",
            "import com.lulan.shincolle.apié.Hidden;",
            "import com.lulan.shincolle.\\u0072eference.Reference;",
            "\\u0069mport com.lulan.shincolle.reference.Reference;",
            "package a;\rimport com.lulan.shincolle.reference.Reference;",
            "package a;\r\nimport com.lulan.shincolle.reference.Reference;",
            "import com.lulan.shincolle.ShinColle;",
            "import com.lulan.shincolle.reference.Reference;\n/** Mentions {@link Reference} only. */\nclass A {}",
            "class A { Object o = new com.lulan.shincolle.reference.ID(); }",
            "class A { com.lulan.shincolle.reference.ID field; }",
            "class A { java.util.List<com.lulan.shincolle.reference.ID> field; }",
            "class A { void m() { com.lulan.shincolle.utility.EnchantHelper.helper(); } }",
        };
        for (String source : forbidden) {
            assertEquals(1, forbiddenReferences(source).size(), source);
        }
    }

    @Test
    void sourceCheckAllowsApiReferencesAndTextThatIsNotCode() {
        String[] allowed = {
            "import com.lulan.shincolle.api.attribute.ShipAttributeType;",
            "import com . lulan . shincolle . api . attribute . ShipAttributeType;",
            "import com.lulan.shincolle./*c*/api.attribute.*;",
            "import static com.lulan.shincolle.api.ShinColleApi.MOD_ID;",
            "import com.lulan.shincolle.api​.attribute.ShipAttributeType;",
            "import net.minecraft.world.item.ItemStack;",
            "import other.com.lulan.shincolle.reference.Reference;",
            "package com.lulan.shincolle.api;",
            "// import com.lulan.shincolle.reference.Reference;\nclass A {}",
            "/*\nimport com.lulan.shincolle.reference.Reference;\n*/\nclass A {}",
            "/**\n * import com.lulan.shincolle.reference.Reference;\n */\nclass A {}",
            "class A { String s = \"import com.lulan.shincolle.reference.Reference;\"; }",
            "class A { String s = \"\"\"\nimport com.lulan.shincolle.reference.Reference;\n\"\"\"; }",
            "class A { String s = \"com.lulan.shincolle.reference.Reference\"; }",
            "/** See {@code com.lulan.shincolle.reference.Reference}. */\nclass A {}",
            "class A { com.lulan.shincolle.api.attribute.ShipAttributeType field; }",
        };
        for (String source : allowed) {
            assertEquals(List.of(), forbiddenReferences(source), source);
        }
    }

    @Test
    void apiSourcesReferenceNoInternalPackage() throws IOException {
        List<String> violations = new ArrayList<>();
        int scanned = 0;
        try (Stream<Path> files = Files.walk(API_SOURCES)) {
            for (Path file : (Iterable<Path>) files.filter(path -> path.toString().endsWith(".java"))::iterator) {
                scanned++;
                String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                for (String name : forbiddenReferences(source)) {
                    violations.add(file.getFileName() + " -> " + name);
                }
            }
        }
        assertTrue(scanned >= 20, "Too few API sources were scanned: " + scanned);
        assertTrue(violations.isEmpty(), "API sources reference internal packages: " + violations);
    }

    @Test
    void classCheckDetectsEveryWayAClassNamesAnInternalType() throws IOException {
        String internal = "com/lulan/shincolle/apiboundaryfixture/InternalType";
        assertEquals(List.of(internal), internalReferences(bytesOf(FieldUse.class)));
        assertEquals(List.of(internal), internalReferences(bytesOf(MethodUse.class)));
        assertEquals(List.of(internal), internalReferences(bytesOf(GenericUse.class)));
        assertTrue(internalReferences(bytesOf(RuntimeAnnotationUse.class))
                .contains("com/lulan/shincolle/apiboundaryfixture/InternalMarker"));
        assertTrue(internalReferences(bytesOf(ClassAnnotationUse.class))
                .contains("com/lulan/shincolle/apiboundaryfixture/InternalClassMarker"));
        assertTrue(internalReferences(bytesOf(TypeAnnotationUse.class))
                .contains("com/lulan/shincolle/apiboundaryfixture/InternalTypeUseMarker"));
        assertTrue(internalReferences(bytesOf(com.lulan.shincolle.equip.ShipEquipmentResolver.class))
                .contains("com/lulan/shincolle/equipdata/EquipDataRegistry"));
    }

    @Test
    void classCheckIgnoresStringsAndPartsOfOtherNames() throws IOException {
        assertEquals(List.of(), internalReferences(bytesOf(StringOnly.class)));
        assertEquals(List.of(), internalReferences(bytesOf(PartOfAnotherName.class)));
        assertEquals(List.of(), internalReferences(bytesOf(ShipAttributeLayout.class)));
        assertEquals(List.of(), internalReferences(bytesOf(ShinColleApi.class)));
        assertFalse(isInternalType("other/com/lulan/shincolle/equip/X"));
        assertFalse(isInternalType("com/lulan/shincolle/api/attribute/ShipAttributeType"));
        assertTrue(isInternalType("com/lulan/shincolle/api$internal/X"));
        assertTrue(isInternalType("com/lulan/shincolle/apiInternal/X"));
    }

    @Test
    void apiClassesNameNoInternalType() throws IOException {
        List<String> violations = new ArrayList<>();
        int scanned = 0;
        try (Stream<Path> files = Files.walk(API_CLASSES)) {
            for (Path file : (Iterable<Path>) files.filter(path -> path.toString().endsWith(".class"))::iterator) {
                scanned++;
                for (String reference : internalReferences(Files.readAllBytes(file))) {
                    violations.add(file.getFileName() + " -> " + reference);
                }
            }
        }
        assertTrue(scanned >= 20, "Too few API classes were scanned: " + scanned);
        assertTrue(violations.isEmpty(), "API classes name internal types: " + violations);
    }

    @Test
    void layoutHasNoPublicWayToCreateOrReplaceTheCanonicalLayout() {
        List<String> staticEntries = new ArrayList<>();
        for (Method method : ShipAttributeLayout.class.getMethods()) {
            if (Modifier.isStatic(method.getModifiers())) {
                staticEntries.add(method.getName());
            }
        }
        staticEntries.sort(String::compareTo);
        // current() only reads; detached() builds a throwaway layout that never becomes the canonical one.
        assertEquals(List.of("current", "detached"), staticEntries);
        assertFalse(staticEntries.contains("initialize"));
    }
}
