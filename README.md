# JByteMod Native Module Dumper

A plugin for [JByteMod Remastered](https://github.com/jbytemod/JByteMod-Remastered) that lists and dumps file-backed native libraries loaded by an attached JVM.

## Features

- Lists native libraries loaded through `System.load` and `System.loadLibrary`.
- Includes loaded libraries with arbitrary file names, such as `.tmp`, `.bin`, or no extension.
- Shows each library's name, size, and source path.
- Filters the module list by name or path.
- Dumps one, several, or all listed libraries.
- Ships its own target-side agent extension and uses JByteMod's generic authenticated extension transport.
- Adds native-module tools to the MCP Server plugin when both plugins are enabled.
- Keeps both JByteMod API and optional MCP dependencies outside the plugin JAR.

## Limitations

The plugin only lists libraries known to the JVM that still have a readable backing file. It cannot recover a DLL, SO, or dylib that was manually mapped directly from memory or whose backing file has already been removed. Reconstructing a manually mapped PE image requires a separate native memory scanner and does not reproduce the original file exactly after relocations and import resolution.

## Requirements

- JByteMod Remastered 2.11.0 or newer
- Java 21 or newer
- An active attachment to a JVM
- MCP Server 1.0.0 or newer for optional MCP tools

## MCP integration

When the [MCP Server plugin](https://github.com/jbytemod/plugin-mcp) is installed and enabled, the dumper registers these additional tools:

- `list_native_modules`
- `dump_native_module`
- `dump_all_native_modules`

The Native Module Dumper continues to work as a regular GUI plugin when MCP Server is absent or disabled.

## Installation

Build the plugin:

```shell
mvn package
```

Copy `target/plugin-native-module-dumper-1.0.1.jar` into JByteMod's plugin directory:

- Windows: `%APPDATA%\JByteMod-Remastered\plugins`
- macOS: `~/Library/Application Support/JByteMod-Remastered/plugins`
- Linux: `~/JByteMod-Remastered/plugins`

Restart or reload plugins in JByteMod, then open **Plugins > Native Module Dumper**.
