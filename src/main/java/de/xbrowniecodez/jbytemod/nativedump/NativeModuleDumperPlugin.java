package de.xbrowniecodez.jbytemod.nativedump;

import de.xbrowniecodez.jbytemod.plugin.Plugin;
import de.xbrowniecodez.jbytemod.plugin.PluginContext;

import javax.swing.SwingUtilities;
import java.awt.Window;

public final class NativeModuleDumperPlugin extends Plugin {
    private NativeModuleDialog dialog;
    private AutoCloseable mcpRegistration;

    public NativeModuleDumperPlugin() {
        super("Native Module Dumper", getPluginVersion(), "brownie");
    }

    private static String getPluginVersion() {
        String version = NativeModuleDumperPlugin.class.getPackage().getImplementationVersion();
        return version == null ? "dev" : version;
    }

    @Override
    public void init() {
        try {
            Class<?> provider = Class.forName(
                    "de.xbrowniecodez.jbytemod.nativedump.NativeModuleMcpProvider",
                    true, getClass().getClassLoader());
            mcpRegistration = (AutoCloseable) provider
                    .getMethod("register", PluginContext.class)
                    .invoke(null, getContext());
        } catch (ClassNotFoundException | NoClassDefFoundError ignored) {
        } catch (ReflectiveOperationException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            getContext().logError("Could not register native module MCP tools", cause);
        }
    }

    @Override
    public boolean isClickable() {
        return true;
    }

    @Override
    public void menuClick() {
        if (dialog != null && dialog.isDisplayable()) {
            dialog.setVisible(true);
            dialog.toFront();
            return;
        }
        Window owner = SwingUtilities.getWindowAncestor(getMenu());
        dialog = new NativeModuleDialog(owner, getContext());
        dialog.setVisible(true);
    }

    @Override
    public void shutdown() {
        if (dialog != null) {
            dialog.dispose();
            dialog = null;
        }
        if (mcpRegistration != null) {
            try {
                mcpRegistration.close();
            } catch (Exception exception) {
                getContext().logError("Could not unregister native module MCP tools", exception);
            }
            mcpRegistration = null;
        }
    }
}
