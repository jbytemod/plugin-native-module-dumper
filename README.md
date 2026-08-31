# JByteMod Native Module Dumper

A plugin for [JByteMod Remastered](https://github.com/jbytemod/JByteMod-Remastered) that lists and dumps file-backed native libraries loaded by an attached JVM.

## Features

- Lists native libraries loaded through `System.load` and `System.loadLibrary`.
- Shows each library's name, size, and source path.
- Filters the module list by name or path.
- Dumps one, several, or all listed libraries.
- Reads libraries through JByteMod's authenticated attached-JVM agent connection.
- Depends only on the JByteMod API.

## Limitations

The plugin only lists libraries known to the JVM that still have a readable backing file. It cannot recover a DLL, SO, or dylib that was manually mapped directly from memory or whose backing file has already been removed. Reconstructing a manually mapped PE image requires a separate native memory scanner and does not reproduce the original file exactly after relocations and import resolution.

## Requirements

- JByteMod Remastered 2.11.0 or newer
- Java 21 or newer
- An active attachment to a JVM

## Installation

Build the plugin:

```shell
mvn package
```

Copy `target/plugin-native-module-dumper-1.0.0.jar` into JByteMod's plugin directory:

- Windows: `%APPDATA%\JByteMod-Remastered\plugins`
- macOS: `~/Library/Application Support/JByteMod-Remastered/plugins`
- Linux: `~/JByteMod-Remastered/plugins`

Restart or reload plugins in JByteMod, then open **Plugins > Native Module Dumper**.
