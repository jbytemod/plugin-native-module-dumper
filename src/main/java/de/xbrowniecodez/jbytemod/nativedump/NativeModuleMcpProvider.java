package de.xbrowniecodez.jbytemod.nativedump;

import de.xbrowniecodez.jbytemod.mcp.McpPlugin;
import de.xbrowniecodez.jbytemod.mcp.api.McpToolDefinition;
import de.xbrowniecodez.jbytemod.mcp.api.McpToolProvider;
import de.xbrowniecodez.jbytemod.plugin.PluginContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class NativeModuleMcpProvider implements McpToolProvider {
    private static final List<McpToolDefinition> TOOLS = List.of(
            new McpToolDefinition("list_native_modules",
                    "List file-backed native libraries loaded by the attached JVM.",
                    schema(Map.of()), true, false, true),
            new McpToolDefinition("dump_native_module",
                    "Dump one file-backed native library from the attached JVM.",
                    schema(Map.of(
                            "modulePath", stringProperty(
                                    "Exact loaded-library path returned by list_native_modules."),
                            "outputPath", stringProperty(
                                    "Local output file or existing directory. Existing output files are overwritten.")),
                            "modulePath", "outputPath"), false, true, true),
            new McpToolDefinition("dump_all_native_modules",
                    "Dump every file-backed native library loaded by the attached JVM.",
                    schema(Map.of("outputDirectory", stringProperty(
                                    "Local directory for dumped libraries. Existing files are preserved with numbered names.")),
                            "outputDirectory"), false, true, true));

    private final NativeModuleService service;

    private NativeModuleMcpProvider(PluginContext context) {
        this.service = new NativeModuleService(context);
    }

    public static AutoCloseable register(PluginContext context) {
        return McpPlugin.registerToolProvider(new NativeModuleMcpProvider(context));
    }

    @Override
    public List<McpToolDefinition> tools() {
        return TOOLS;
    }

    @Override
    public Object call(String toolName, Map<String, Object> arguments) throws Exception {
        return switch (toolName) {
            case "list_native_modules" -> listNativeModules();
            case "dump_native_module" -> dumpNativeModule(arguments);
            case "dump_all_native_modules" -> dumpAllNativeModules(arguments);
            default -> throw new IllegalArgumentException("Unknown native module tool: " + toolName);
        };
    }

    private Map<String, Object> listNativeModules() throws Exception {
        List<NativeModule> modules = service.listModules();
        return Map.of("total", modules.size(), "modules", modules.stream()
                .map(NativeModuleMcpProvider::moduleResult).toList());
    }

    private Map<String, Object> dumpNativeModule(Map<String, Object> arguments) throws Exception {
        NativeModule module = findNativeModule(requiredString(arguments, "modulePath"));
        Path output = Path.of(requiredString(arguments, "outputPath")).toAbsolutePath().normalize();
        if (Files.isDirectory(output)) {
            output = output.resolve(safeFileName(module.name()));
        }
        createParentDirectories(output);
        byte[] bytes = service.dumpModule(module.path());
        Files.write(output, bytes);

        Map<String, Object> result = new LinkedHashMap<>(moduleResult(module));
        result.put("outputPath", output.toString());
        result.put("dumpedBytes", bytes.length);
        result.put("dumped", true);
        return result;
    }

    private Map<String, Object> dumpAllNativeModules(Map<String, Object> arguments) throws Exception {
        Path directory = Path.of(requiredString(arguments, "outputDirectory")).toAbsolutePath().normalize();
        Files.createDirectories(directory);
        List<NativeModule> modules = service.listModules();
        List<Map<String, Object>> dumpedModules = new ArrayList<>();
        Set<String> reservedNames = new HashSet<>();
        long dumpedBytes = 0;
        for (NativeModule module : modules) {
            Path output = uniqueOutputPath(directory, safeFileName(module.name()), reservedNames);
            byte[] bytes = service.dumpModule(module.path());
            Files.write(output, bytes);
            dumpedBytes += bytes.length;

            Map<String, Object> dumped = new LinkedHashMap<>(moduleResult(module));
            dumped.put("outputPath", output.toString());
            dumped.put("dumpedBytes", bytes.length);
            dumpedModules.add(dumped);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("outputDirectory", directory.toString());
        result.put("dumpedCount", modules.size());
        result.put("dumpedBytes", dumpedBytes);
        result.put("modules", dumpedModules);
        return result;
    }

    private NativeModule findNativeModule(String modulePath) throws Exception {
        Path requested = Path.of(modulePath).toAbsolutePath().normalize();
        return service.listModules().stream()
                .filter(module -> Path.of(module.path()).toAbsolutePath().normalize().equals(requested))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Native module is not loaded: " + requested));
    }

    private static Map<String, Object> moduleResult(NativeModule module) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", module.name());
        result.put("path", module.path());
        result.put("size", module.size());
        return result;
    }

    private static String requiredString(Map<String, Object> arguments, String name) {
        Object value = arguments.get(name);
        if (!(value instanceof String string) || string.isBlank()) {
            throw new IllegalArgumentException(name + " must be a non-empty string");
        }
        return string;
    }

    private static Map<String, Object> schema(Map<String, Object> properties, String... required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("additionalProperties", false);
        if (required.length > 0) {
            schema.put("required", List.of(required));
        }
        return schema;
    }

    private static Map<String, Object> stringProperty(String description) {
        return Map.of("type", "string", "description", description);
    }

    private static String safeFileName(String name) {
        String sanitized = name.replaceAll("[\\\\/:*?\"<>|]", "_");
        return sanitized.isBlank() ? "native-library.bin" : sanitized;
    }

    private static Path uniqueOutputPath(Path directory, String name, Set<String> reservedNames) {
        Path candidate = directory.resolve(name);
        if (!Files.exists(candidate) && reservedNames.add(name.toLowerCase(Locale.ROOT))) {
            return candidate;
        }
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String extension = dot > 0 ? name.substring(dot) : "";
        for (int index = 2; ; index++) {
            String numberedName = base + "-" + index + extension;
            candidate = directory.resolve(numberedName);
            if (!Files.exists(candidate) && reservedNames.add(numberedName.toLowerCase(Locale.ROOT))) {
                return candidate;
            }
        }
    }

    private static void createParentDirectories(Path output) throws Exception {
        Path parent = output.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }
}
