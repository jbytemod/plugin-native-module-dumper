package de.xbrowniecodez.jbytemod.nativedump.agent;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class NativeModuleAgentExtension {
    private static final int COMMAND_LIST = 1;
    private static final int COMMAND_DUMP = 2;

    private NativeModuleAgentExtension() {
    }

    public static byte[] invoke(byte[] request, Instrumentation instrumentation) throws Exception {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(request))) {
            int command = input.readUnsignedByte();
            return switch (command) {
                case COMMAND_LIST -> listModules(instrumentation);
                case COMMAND_DUMP -> dumpModule(readString(input), instrumentation);
                default -> throw new IllegalArgumentException("Unknown native module command: " + command);
            };
        }
    }

    private static byte[] listModules(Instrumentation instrumentation) throws Exception {
        List<Path> modules = loadedModules(instrumentation);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(modules.size());
            for (Path module : modules) {
                writeString(output, module.getFileName().toString());
                writeString(output, module.toString());
                output.writeLong(Files.size(module));
            }
        }
        return bytes.toByteArray();
    }

    private static byte[] dumpModule(String modulePath, Instrumentation instrumentation) throws Exception {
        Path requested = Path.of(modulePath).toAbsolutePath().normalize();
        Path module = loadedModules(instrumentation).stream()
                .filter(path -> path.equals(requested))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Native module is not loaded: " + requested));
        long size = Files.size(module);
        if (size > 256L * 1024 * 1024) {
            throw new IllegalArgumentException("Native module is too large to dump: " + size + " bytes");
        }
        return Files.readAllBytes(module);
    }

    private static List<Path> loadedModules(Instrumentation instrumentation) throws Exception {
        Module javaBase = Object.class.getModule();
        Module extensionModule = NativeModuleAgentExtension.class.getModule();
        instrumentation.redefineModule(javaBase, Set.of(),
                Map.of("jdk.internal.loader", Set.of(extensionModule)),
                Map.of("jdk.internal.loader", Set.of(extensionModule)),
                Set.of(), Map.of());

        Class<?> nativeLibraries = Class.forName("jdk.internal.loader.NativeLibraries");
        Field loadedNames = nativeLibraries.getDeclaredField("loadedLibraryNames");
        loadedNames.setAccessible(true);
        Set<?> names = (Set<?>) loadedNames.get(null);
        return new ArrayList<>(names).stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(Path::of)
                .map(path -> path.toAbsolutePath().normalize())
                .filter(Files::isRegularFile)
                .distinct()
                .sorted((left, right) -> left.toString().compareToIgnoreCase(right.toString()))
                .toList();
    }

    private static void writeString(DataOutputStream output, String value) throws Exception {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String readString(DataInputStream input) throws Exception {
        int length = input.readInt();
        if (length < 0 || length > 1024 * 1024) {
            throw new IllegalArgumentException("Invalid string size: " + length);
        }
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) throw new IllegalStateException("Incomplete native module request");
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
