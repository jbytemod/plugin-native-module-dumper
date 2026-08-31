package de.xbrowniecodez.jbytemod.nativedump;

import de.xbrowniecodez.jbytemod.plugin.Plugin;

import javax.swing.SwingUtilities;
import java.awt.Window;

public final class NativeModuleDumperPlugin extends Plugin {
    private NativeModuleDialog dialog;

    public NativeModuleDumperPlugin() {
        super("Native Module Dumper", getPluginVersion(), "brownie");
    }

    private static String getPluginVersion() {
        String version = NativeModuleDumperPlugin.class.getPackage().getImplementationVersion();
        return version == null ? "dev" : version;
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
    }
}
