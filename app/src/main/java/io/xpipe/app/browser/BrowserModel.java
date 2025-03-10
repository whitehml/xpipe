package io.xpipe.app.browser;

import io.xpipe.app.fxcomps.util.BindingsHelper;
import io.xpipe.app.storage.DataStorage;
import io.xpipe.app.util.BusyProperty;
import io.xpipe.app.util.ThreadHelper;
import io.xpipe.core.impl.FileStore;
import io.xpipe.core.store.ShellStore;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

@Getter
public class BrowserModel {

    public BrowserModel(Mode mode) {
        this.mode = mode;

        selected.addListener((observable, oldValue, newValue) -> {
            if (newValue == null) {
                return;
            }

            BindingsHelper.bindContent(selection, newValue.getFileList().getSelection());
        });
    }

    @Getter
    public enum Mode {
        BROWSER(false, true, true, true),
        SINGLE_FILE_CHOOSER(true, false, true, false),
        SINGLE_FILE_SAVE(true, false, true, false),
        MULTIPLE_FILE_CHOOSER(true, true, true, false),
        SINGLE_DIRECTORY_CHOOSER(true, false, false, true),
        MULTIPLE_DIRECTORY_CHOOSER(true, true, false, true);

        private final boolean chooser;
        private final boolean multiple;
        private final boolean acceptsFiles;
        private final boolean acceptsDirectories;

        Mode(boolean chooser, boolean multiple, boolean acceptsFiles, boolean acceptsDirectories) {
            this.chooser = chooser;
            this.multiple = multiple;
            this.acceptsFiles = acceptsFiles;
            this.acceptsDirectories = acceptsDirectories;
        }
    }

    public static final BrowserModel DEFAULT = new BrowserModel(Mode.BROWSER);

    private final Mode mode;

    @Setter
    private Consumer<List<FileStore>> onFinish;

    private final ObservableList<OpenFileSystemModel> openFileSystems = FXCollections.observableArrayList();
    private final Property<OpenFileSystemModel> selected = new SimpleObjectProperty<>();
    private final BrowserTransferModel localTransfersStage = new BrowserTransferModel(this);
    private final ObservableList<BrowserEntry> selection = FXCollections.observableArrayList();

    public void restoreState(BrowserSavedState state) {
        state.getLastSystems().forEach(e -> {
            restoreState(e, null);
        });
    }

    public void restoreState(BrowserSavedState.Entry e, BooleanProperty busy) {
        var storageEntry = DataStorage.get().getStoreEntry(e.getUuid());
        storageEntry.ifPresent(entry -> {
            openFileSystemAsync(
                    entry.getName(), entry.getStore().asNeeded(), e.getPath(), busy);
        });
    }

    public void reset() {
        var list = new ArrayList<BrowserSavedState.Entry>();
        openFileSystems.forEach(model -> {
            var storageEntry = DataStorage.get().getStoreEntryIfPresent(model.getStore());
            storageEntry.ifPresent(entry -> list.add(new BrowserSavedState.Entry(
                    entry.getUuid(), model.getCurrentPath().get())));
        });

        // Don't override state if it is empty
        if (list.size() == 0) {
            return;
        }

        var meaningful = list.size() > 1 || list.stream().allMatch(s -> s.getPath() != null);
        if (!meaningful) {
            return;
        }

        var state = BrowserSavedState.builder().lastSystems(list).build();
        state.save();
    }

    public void finishChooser() {
        if (!getMode().isChooser()) {
            throw new IllegalStateException();
        }

        var chosen = new ArrayList<>(selection);
        for (OpenFileSystemModel openFileSystem : openFileSystems) {
            closeFileSystemAsync(openFileSystem);
        }

        if (chosen.size() == 0) {
            return;
        }

        var stores = chosen.stream()
                .map(entry -> new FileStore(
                        entry.getRawFileEntry().getFileSystem().getStore(),
                        entry.getRawFileEntry().getPath()))
                .toList();
        onFinish.accept(stores);
    }

    public void closeFileSystemAsync(OpenFileSystemModel open) {
        ThreadHelper.runAsync(() -> {
            if (Objects.equals(selected.getValue(), open)) {
                selected.setValue(null);
            }
            open.closeSync();
            synchronized (BrowserModel.this) {
                openFileSystems.remove(open);
            }
        });
    }

    public void openExistingFileSystemIfPresent(String name, ShellStore store) {
        var found = openFileSystems.stream()
                .filter(model -> Objects.equals(model.getStore(), store))
                .findFirst();
        if (found.isPresent()) {
            selected.setValue(found.get());
        } else {
            openFileSystemAsync(name, store, null, null);
        }
    }

    public void openFileSystemAsync(String name, ShellStore store, String path, BooleanProperty externalBusy) {
        //        // Prevent multiple tabs in non browser modes
        //        if (!mode.equals(Mode.BROWSER)) {
        //            ThreadHelper.runFailableAsync(() -> {
        //                var open = openFileSystems.size() > 0 ? openFileSystems.get(0) : null;
        //                if (open != null) {
        //                    open.closeSync();
        //                    openFileSystems.remove(open);
        //                }
        //
        //                var model = new OpenFileSystemModel(this, store);
        //                openFileSystems.add(model);
        //                selected.setValue(model);
        //                model.switchSync(store);
        //            });
        //            return;
        //        }

        if (store == null) {
            return;
        }

        ThreadHelper.runFailableAsync(() -> {
            OpenFileSystemModel model;

            // Prevent multiple calls from interfering with each other
            synchronized (BrowserModel.this) {
                try (var b = new BusyProperty(externalBusy != null ? externalBusy : new SimpleBooleanProperty())) {
                    model = new OpenFileSystemModel(name, this, store);
                    model.initFileSystem();
                    model.initSavedState();
                }

                openFileSystems.add(model);
                selected.setValue(model);
            }
            if (path != null) {
                model.initWithGivenDirectory(path);
            } else {
                model.initWithDefaultDirectory();
            }
        });
    }
}
