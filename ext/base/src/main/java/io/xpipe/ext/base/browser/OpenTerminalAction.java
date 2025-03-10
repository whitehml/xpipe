package io.xpipe.ext.base.browser;

import io.xpipe.app.browser.BrowserEntry;
import io.xpipe.app.browser.OpenFileSystemModel;
import io.xpipe.app.browser.action.LeafAction;
import io.xpipe.app.prefs.AppPrefs;
import io.xpipe.app.util.TerminalHelper;
import io.xpipe.core.store.FileKind;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.List;

public class OpenTerminalAction implements LeafAction {

    public String getId() {
        return "openTerminal";
    }

    @Override
    public void execute(OpenFileSystemModel model, List<BrowserEntry> entries) throws Exception {
        if (model.getInOverview().get()) {
            TerminalHelper.open(
                    model.getName(),
                    model.getFileSystem().getShell().orElseThrow().prepareTerminalOpen(model.getName()));
            return;
        }

        if (entries.size() == 0) {
            model.openTerminalAsync(model.getCurrentDirectory().getPath());
            return;
        }

        for (var entry : entries) {
            model.openTerminalAsync(entry.getRawFileEntry().getPath());
        }
    }

    @Override
    public Category getCategory() {
        return Category.OPEN;
    }

    @Override
    public Node getIcon(OpenFileSystemModel model, List<BrowserEntry> entries) {
        return new FontIcon("mdi2c-console");
    }

    @Override
    public boolean isApplicable(OpenFileSystemModel model, List<BrowserEntry> entries) {
        return entries.stream().allMatch(entry -> entry.getRawFileEntry().getKind() == FileKind.DIRECTORY);
    }

    @Override
    public boolean isActive(OpenFileSystemModel model, List<BrowserEntry> entries) {
        var t = AppPrefs.get().terminalType().getValue();
        return t != null;
    }

    @Override
    public KeyCombination getShortcut() {
        return new KeyCodeCombination(KeyCode.T, KeyCombination.SHORTCUT_DOWN);
    }

    @Override
    public String getName(OpenFileSystemModel model, List<BrowserEntry> entries) {
        return "Open in terminal";
    }
}
