package de.xbrowniecodez.jbytemod.nativedump;

import de.xbrowniecodez.jbytemod.plugin.PluginContext;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class NativeModuleService {
    private static final String EXTENSION_ID = "jbytemod.native-module-dumper";
    private static final String ENTRY_CLASS =
            "de.xbrowniecodez.jbytemod.nativedump.agent.NativeModuleAgentExtension";
    private static final Map<String, byte[]> EXTENSION_CLASSES = Map.of(ENTRY_CLASS, readExtensionClass());
    private static final int COMMAND_LIST = 1;
    private static final int COMMAND_DUMP = 2;

    private final PluginContext context;

    NativeModuleService(PluginContext context) {
        this.context = context;
    }

    List<NativeModule> listModules() throws Exception {
        byte[] response = invoke(new byte[]{COMMAND_LIST});
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(response))) {
            int count = input.readInt();
            if (count < 0 || count > 10_000) {
                throw new IOException("Invalid native module count: " + count);
            }
            List<NativeModule> modules = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                modules.add(new NativeModule(readString(input), readString(input), input.readLong()));
            }
            if (input.available() != 0) throw new IOException("Unexpected native module response data");
            return List.copyOf(modules);
        }
    }

    byte[] dumpModule(String path) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeByte(COMMAND_DUMP);
            writeString(output, path);
        }
        return invoke(bytes.toByteArray());
    }

    private byte[] invoke(byte[] request) throws Exception {
        return context.invokeAttachedJvmAgentExtension(
                EXTENSION_ID, ENTRY_CLASS, EXTENSION_CLASSES, request);
    }

    private static byte[] readExtensionClass() {
        String resource = "/" + ENTRY_CLASS.replace('.', '/') + ".class";
        try (InputStream input = NativeModuleService.class.getResourceAsStream(resource)) {
            if (input == null) throw new IllegalStateException("Native module agent extension is missing");
            return input.readAllBytes();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read native module agent extension", exception);
        }
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String readString(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > 1024 * 1024) throw new IOException("Invalid string size: " + length);
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) throw new IOException("Incomplete native module response");
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
