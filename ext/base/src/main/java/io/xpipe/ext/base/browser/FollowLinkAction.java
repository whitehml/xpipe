package io.xpipe.ext.base.browser;

import io.xpipe.app.browser.BrowserEntry;
import io.xpipe.app.browser.OpenFileSystemModel;
import io.xpipe.app.browser.action.LeafAction;
import io.xpipe.core.impl.FileNames;
import io.xpipe.core.store.FileKind;
import javafx.scene.Node;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.List;

public class FollowLinkAction implements LeafAction {

    @Override
    public boolean automaticallyResolveLinks() {
        return false;
    }

    @Override
    public void execute(OpenFileSystemModel model, List<BrowserEntry> entries) {
        var target = FileNames.getParent(entries.get(0).getRawFileEntry().resolved().getPath());
        model.cdAsync(target);
    }

    @Override
    public Category getCategory() {
        return Category.OPEN;
    }

    @Override
    public Node getIcon(OpenFileSystemModel model, List<BrowserEntry> entries) {
        return new FontIcon("mdi2a-arrow-top-right-thick");
    }

    @Override
    public boolean isApplicable(OpenFileSystemModel model, List<BrowserEntry> entries) {
        return entries.size() == 1
                && entries.stream().allMatch(entry -> entry.getRawFileEntry().getKind() == FileKind.LINK && entry.getRawFileEntry().resolved().getKind() != FileKind.DIRECTORY);
    }

    @Override
    public String getName(OpenFileSystemModel model, List<BrowserEntry> entries) {
        return "Follow link";
    }
}
