package io.xpipe.app.update;

import io.xpipe.app.core.AppCache;
import io.xpipe.app.core.AppProperties;
import io.xpipe.app.core.mode.OperationMode;
import io.xpipe.app.issue.ErrorEvent;
import io.xpipe.app.issue.TrackEvent;
import io.xpipe.app.prefs.AppPrefs;
import io.xpipe.app.util.BusyProperty;
import io.xpipe.app.util.ThreadHelper;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.layout.Region;
import lombok.Builder;
import lombok.Getter;
import lombok.Value;
import lombok.With;
import lombok.extern.jackson.Jacksonized;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

@SuppressWarnings("InfiniteLoopStatement")
@Getter
public abstract class UpdateHandler {

    protected final Property<AvailableRelease> lastUpdateCheckResult = new SimpleObjectProperty<>();
    protected final Property<PreparedUpdate> preparedUpdate = new SimpleObjectProperty<>();
    protected final BooleanProperty busy = new SimpleBooleanProperty();
    protected final PerformedUpdate performedUpdate;
    protected final boolean updateSucceeded;

    protected UpdateHandler(boolean startBackgroundThread) {
        performedUpdate = AppCache.get("performedUpdate", PerformedUpdate.class, () -> null);
        var hasUpdated = performedUpdate != null;
        event("Was updated is " + hasUpdated);
        if (hasUpdated) {
            AppCache.clear("performedUpdate");
            updateSucceeded = AppProperties.get().getVersion().equals(performedUpdate.getNewVersion());
            AppCache.clear("preparedUpdate");
            event("Found information about recent update");
        } else {
            updateSucceeded = false;
        }

        preparedUpdate.setValue(AppCache.get("preparedUpdate", PreparedUpdate.class, () -> null));

        // Check if the original version this was downloaded from is still the same
        if (preparedUpdate.getValue() != null
                && (!preparedUpdate
                                .getValue()
                                .getSourceVersion()
                                .equals(AppProperties.get().getVersion())
                        || !XPipeDistributionType.get()
                                .getId()
                                .equals(preparedUpdate.getValue().getSourceDist()))) {
            preparedUpdate.setValue(null);
        }

        // Check if somehow the downloaded version is equal to the current one
        if (preparedUpdate.getValue() != null
                && preparedUpdate
                        .getValue()
                        .getVersion()
                        .equals(AppProperties.get().getVersion())) {
            preparedUpdate.setValue(null);
        }

        preparedUpdate.addListener((c, o, n) -> {
            AppCache.update("preparedUpdate", n);
        });
        lastUpdateCheckResult.addListener((c, o, n) -> {
            if (n != null
                    && preparedUpdate.getValue() != null
                    && n.isUpdate()
                    && n.getVersion().equals(preparedUpdate.getValue().getVersion())) {
                return;
            }

            preparedUpdate.setValue(null);
        });

        if (startBackgroundThread) {
            startBackgroundUpdater();
        }
    }

    private void startBackgroundUpdater() {
        ThreadHelper.createPlatformThread("updater", true, () -> {
                    ThreadHelper.sleep(Duration.ofMinutes(5).toMillis());
                    event("Starting background updater thread");
                    while (true) {
                        if (AppPrefs.get().automaticallyUpdate().get()) {
                            event("Performing background update");
                            refreshUpdateCheckSilent();
                            prepareUpdate();
                        }

                        ThreadHelper.sleep(Duration.ofHours(1).toMillis());
                    }
                })
                .start();
    }

    protected void event(String msg) {
        TrackEvent.builder().category("updater").type("info").message(msg).handle();
    }

    protected final boolean isUpdate(String releaseVersion) {
        if (AppPrefs.get() != null
                && AppPrefs.get().developerMode().getValue()
                && AppPrefs.get().developerDisableUpdateVersionCheck().get()) {
            event("Bypassing version check");
            return true;
        }

        if (!AppProperties.get().getVersion().equals(releaseVersion)) {
            event("Release has a different version");
            return true;
        }

        return false;
    }

    public final void prepareUpdateAsync() {
        ThreadHelper.runAsync(() -> prepareUpdate());
    }

    public final void refreshUpdateCheckAsync() {
        ThreadHelper.runAsync(() -> refreshUpdateCheckSilent());
    }

    public final AvailableRelease refreshUpdateCheckSilent() {
        try {
            return refreshUpdateCheck();
        } catch (Exception ex) {
            ErrorEvent.fromThrowable(ex).discard().handle();
            return null;
        }
    }

    public final void prepareUpdate() {
        if (busy.getValue()) {
            return;
        }

        if (lastUpdateCheckResult.getValue() == null) {
            return;
        }

        if (!lastUpdateCheckResult.getValue().isUpdate()) {
            return;
        }

        if (preparedUpdate.getValue() != null) {
            if (lastUpdateCheckResult
                    .getValue()
                    .getVersion()
                    .equals(preparedUpdate.getValue().getVersion())) {
                event("Update is already prepared ...");
                return;
            }
        }

        try (var ignored = new BusyProperty(busy)) {
            event("Performing update download ...");
            prepareUpdateImpl();
        }
    }

    public abstract Region createInterface();

    public void prepareUpdateImpl() {
        var changelogString =
                AppDownloads.downloadChangelog(lastUpdateCheckResult.getValue().getVersion(), false);
        var changelog = changelogString.orElse(null);

        var rel = new PreparedUpdate(
                AppProperties.get().getVersion(),
                XPipeDistributionType.get().getId(),
                lastUpdateCheckResult.getValue().getVersion(),
                lastUpdateCheckResult.getValue().getReleaseUrl(),
                null,
                changelog,
                lastUpdateCheckResult.getValue().getAssetType());
        preparedUpdate.setValue(rel);
    }

    public final void executeUpdateAndClose() {
        if (busy.getValue()) {
            return;
        }

        if (preparedUpdate.getValue() == null) {
            return;
        }

        var downloadFile = preparedUpdate.getValue().getFile();
        if (!Files.exists(downloadFile)) {
            return;
        }

        // Check if prepared update is still the latest.
        // We only do that here to minimize the sent requests by only executing when it's really necessary
        var available = XPipeDistributionType.get().getUpdateHandler().refreshUpdateCheckSilent();
        if (preparedUpdate.getValue() == null) {
            return;
        }

        if (available != null && !available.getVersion().equals(preparedUpdate.getValue().getVersion())) {
            preparedUpdate.setValue(null);
            return;
        }

        event("Executing update ...");
        OperationMode.executeAfterShutdown(() -> {
            try {
                executeUpdateAndCloseImpl();
            } catch (Throwable ex) {
                ex.printStackTrace();
            } finally {
                var performedUpdate = new PerformedUpdate(
                        preparedUpdate.getValue().getVersion(),
                        preparedUpdate.getValue().getBody(),
                        preparedUpdate.getValue().getVersion());
                AppCache.update("performedUpdate", performedUpdate);
            }
        });
    }

    public void executeUpdateAndCloseImpl() throws Exception {
        throw new UnsupportedOperationException();
    }

    public final AvailableRelease refreshUpdateCheck() throws Exception {
        if (busy.getValue()) {
            return lastUpdateCheckResult.getValue();
        }

        try (var ignored = new BusyProperty(busy)) {
            return refreshUpdateCheckImpl();
        }
    }

    public abstract AvailableRelease refreshUpdateCheckImpl() throws Exception;

    @Value
    @Builder
    @Jacksonized
    public static class PerformedUpdate {
        String name;
        String rawDescription;
        String newVersion;
    }

    @Value
    @Builder
    @Jacksonized
    @With
    public static class AvailableRelease {
        String sourceVersion;
        String sourceDist;
        String version;
        String releaseUrl;
        String downloadUrl;
        AppInstaller.InstallerAssetType assetType;
        Instant checkTime;
        boolean isUpdate;
    }

    @Value
    @Builder
    @Jacksonized
    public static class PreparedUpdate {
        String sourceVersion;
        String sourceDist;
        String version;
        String releaseUrl;
        Path file;
        String body;
        AppInstaller.InstallerAssetType assetType;
    }
}
