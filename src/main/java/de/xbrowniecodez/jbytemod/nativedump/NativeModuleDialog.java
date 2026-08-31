package de.xbrowniecodez.jbytemod.nativedump;

import de.xbrowniecodez.jbytemod.plugin.PluginContext;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

final class NativeModuleDialog extends JDialog {
    private final PluginContext context;
    private final NativeModuleService service;
    private final NativeModuleTableModel model = new NativeModuleTableModel();
    private final JTable table = new JTable(model);
    private final TableRowSorter<NativeModuleTableModel> sorter = new TableRowSorter<>(model);
    private final JTextField filterField = new JTextField(24);
    private final JLabel status = new JLabel("Attach to a JVM, then refresh the module list.");
    private final JProgressBar progress = new JProgressBar();
    private final JButton refreshButton = new JButton("Refresh");
    private final JButton dumpSelectedButton = new JButton("Dump Selected");
    private final JButton dumpAllButton = new JButton("Dump All");

    NativeModuleDialog(Window owner, PluginContext context) {
        super(owner, "Native Module Dumper", ModalityType.MODELESS);
        this.context = context;
        this.service = new NativeModuleService(context);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        add(createHeader(), BorderLayout.NORTH);
        configureTable();
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(createFooter(), BorderLayout.SOUTH);

        setSize(900, 520);
        setMinimumSize(new Dimension(680, 360));
        setLocationRelativeTo(owner);
        refreshModules();
    }

    private JPanel createHeader() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        JLabel description = new JLabel(
                "File-backed native libraries loaded through the JVM. Memory-only/manual-mapped modules are not listed.");
        panel.add(description, BorderLayout.NORTH);
        JPanel filter = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        filter.add(new JLabel("Filter:"));
        filter.add(filterField);
        panel.add(filter, BorderLayout.SOUTH);
        filterField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                updateFilter();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                updateFilter();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                updateFilter();
            }
        });
        return panel;
    }

    private void configureTable() {
        table.setRowSorter(sorter);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(false);
        table.getColumnModel().getColumn(0).setPreferredWidth(220);
        table.getColumnModel().getColumn(1).setPreferredWidth(90);
        table.getColumnModel().getColumn(2).setPreferredWidth(520);
        table.getColumnModel().getColumn(1).setCellRenderer(new SizeRenderer());
        table.getSelectionModel().addListSelectionListener(event -> updateActions());
    }

    private JPanel createFooter() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        JPanel state = new JPanel(new BorderLayout(8, 0));
        progress.setIndeterminate(true);
        progress.setVisible(false);
        progress.setPreferredSize(new Dimension(120, 16));
        state.add(status, BorderLayout.CENTER);
        state.add(progress, BorderLayout.EAST);
        panel.add(state, BorderLayout.NORTH);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        refreshButton.addActionListener(event -> refreshModules());
        dumpSelectedButton.addActionListener(event -> dumpSelected());
        dumpAllButton.addActionListener(event -> dumpAll());
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(event -> dispose());
        actions.add(refreshButton);
        actions.add(dumpSelectedButton);
        actions.add(dumpAllButton);
        actions.add(closeButton);
        panel.add(actions, BorderLayout.SOUTH);
        updateActions();
        return panel;
    }

    private void refreshModules() {
        setBusy(true, "Reading loaded native libraries...");
        new SwingWorker<List<NativeModule>, Void>() {
            @Override
            protected List<NativeModule> doInBackground() throws Exception {
                return service.listModules();
            }

            @Override
            protected void done() {
                try {
                    List<NativeModule> libraries = get();
                    model.setLibraries(libraries);
                    setBusy(false, libraries.size() + " native libraries found");
                } catch (Exception exception) {
                    model.setLibraries(List.of());
                    setBusy(false, "Unable to read native libraries");
                    showError(exception);
                }
            }
        }.execute();
    }

    private void dumpSelected() {
        List<NativeModule> selected = selectedLibraries();
        if (selected.isEmpty()) return;
        if (selected.size() == 1) {
            JFileChooser chooser = new JFileChooser();
            chooser.setSelectedFile(new File(selected.getFirst().name()));
            if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
            Path output = chooser.getSelectedFile().toPath();
            if (!confirmOverwrite(output)) return;
            dumpLibraries(selected, output.getParent(), output.getFileName().toString());
            return;
        }
        Path directory = chooseDirectory();
        if (directory != null) dumpLibraries(selected, directory, null);
    }

    private void dumpAll() {
        if (model.getRowCount() == 0) return;
        Path directory = chooseDirectory();
        if (directory != null) dumpLibraries(model.libraries(), directory, null);
    }

    private Path chooseDirectory() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        return chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION
                ? chooser.getSelectedFile().toPath() : null;
    }

    private void dumpLibraries(List<NativeModule> libraries, Path directory, String singleFileName) {
        if (directory == null) directory = Path.of(".").toAbsolutePath();
        Path targetDirectory = directory;
        setBusy(true, "Dumping " + libraries.size() + " native libraries...");
        new SwingWorker<Integer, Void>() {
            @Override
            protected Integer doInBackground() throws Exception {
                Files.createDirectories(targetDirectory);
                Set<String> usedNames = new HashSet<>();
                int dumped = 0;
                for (NativeModule library : libraries) {
                    String name = singleFileName == null
                            ? uniqueFileName(targetDirectory, safeFileName(library.name()), usedNames)
                            : singleFileName;
                    byte[] bytes = service.dumpModule(library.path());
                    Files.write(targetDirectory.resolve(name), bytes);
                    dumped++;
                }
                return dumped;
            }

            @Override
            protected void done() {
                try {
                    int dumped = get();
                    setBusy(false, "Dumped " + dumped + " native libraries to " + targetDirectory);
                } catch (Exception exception) {
                    setBusy(false, "Native library dump failed");
                    showError(exception);
                }
            }
        }.execute();
    }

    private List<NativeModule> selectedLibraries() {
        List<NativeModule> selected = new ArrayList<>();
        for (int viewRow : table.getSelectedRows()) {
            selected.add(model.libraryAt(table.convertRowIndexToModel(viewRow)));
        }
        return selected;
    }

    private boolean confirmOverwrite(Path output) {
        return !Files.exists(output) || JOptionPane.showConfirmDialog(this,
                "Overwrite " + output.getFileName() + "?", "Overwrite file",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
    }

    private void updateFilter() {
        String text = filterField.getText().trim();
        sorter.setRowFilter(text.isEmpty() ? null : RowFilter.regexFilter("(?i)" + Pattern.quote(text)));
    }

    private void setBusy(boolean busy, String message) {
        progress.setVisible(busy);
        refreshButton.setEnabled(!busy);
        table.setEnabled(!busy);
        status.setText(message);
        updateActions();
    }

    private void updateActions() {
        boolean ready = table.isEnabled() && model.getRowCount() > 0;
        dumpSelectedButton.setEnabled(ready && table.getSelectedRowCount() > 0);
        dumpAllButton.setEnabled(ready);
    }

    private void showError(Exception exception) {
        Throwable cause = exception.getCause() == null ? exception : exception.getCause();
        context.logError("Native module dumper failed", cause);
        JOptionPane.showMessageDialog(this, cause.getMessage(), "Native Module Dumper",
                JOptionPane.ERROR_MESSAGE);
    }

    private static String safeFileName(String name) {
        String sanitized = name.replaceAll("[\\\\/:*?\"<>|]", "_");
        return sanitized.isBlank() ? "native-library.bin" : sanitized;
    }

    private static String uniqueFileName(Path directory, String name, Set<String> usedNames) {
        if (reserveFileName(directory, name, usedNames)) return name;
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String extension = dot > 0 ? name.substring(dot) : "";
        for (int index = 2; ; index++) {
            String candidate = base + "-" + index + extension;
            if (reserveFileName(directory, candidate, usedNames)) return candidate;
        }
    }

    private static boolean reserveFileName(Path directory, String name, Set<String> usedNames) {
        return !Files.exists(directory.resolve(name)) && usedNames.add(name.toLowerCase(Locale.ROOT));
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double value = bytes;
        String[] units = {"KiB", "MiB", "GiB"};
        int unit = -1;
        do {
            value /= 1024;
            unit++;
        } while (value >= 1024 && unit < units.length - 1);
        return String.format(Locale.ROOT, "%.1f %s", value, units[unit]);
    }

    private static final class NativeModuleTableModel extends AbstractTableModel {
        private final String[] columns = {"Name", "Size", "Path"};
        private List<NativeModule> libraries = List.of();

        void setLibraries(List<NativeModule> libraries) {
            this.libraries = List.copyOf(libraries);
            fireTableDataChanged();
        }

        List<NativeModule> libraries() {
            return libraries;
        }

        NativeModule libraryAt(int row) {
            return libraries.get(row);
        }

        @Override
        public int getRowCount() {
            return libraries.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return column == 1 ? Long.class : String.class;
        }

        @Override
        public Object getValueAt(int row, int column) {
            NativeModule library = libraries.get(row);
            return switch (column) {
                case 0 -> library.name();
                case 1 -> library.size();
                case 2 -> library.path();
                default -> throw new IndexOutOfBoundsException(column);
            };
        }
    }

    private static final class SizeRenderer extends DefaultTableCellRenderer {
        SizeRenderer() {
            setHorizontalAlignment(RIGHT);
        }

        @Override
        protected void setValue(Object value) {
            setText(value instanceof Long bytes ? formatSize(bytes) : "");
        }
    }
}
