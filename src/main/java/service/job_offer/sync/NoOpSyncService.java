package service.job_offer.sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * No-op implementation of {@link SyncService}.
 *
 * Used when no web backend is configured. All operations succeed silently.
 * Replace with a real {@code RestSyncService} when the web backend is ready.
 *
 * To enable real sync, implement {@link SyncService} and inject it wherever
 * {@code NoOpSyncService} is instantiated.
 */
public class NoOpSyncService implements SyncService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NoOpSyncService.class);

    public NoOpSyncService() {
        LOGGER.info("ATS sync running in no-op mode. " +
                    "Configure a web backend in ats-config.properties to enable real sync.");
    }

    @Override
    public int pushChanges() {
        LOGGER.debug("NoOpSyncService.pushChanges() — web backend not configured, skipping");
        return 0;
    }

    @Override
    public int pullChanges() {
        LOGGER.debug("NoOpSyncService.pullChanges() — web backend not configured, skipping");
        return 0;
    }

    @Override
    public String getLastSyncTime() {
        return null; // Never synced
    }

    @Override
    public boolean isConfigured() {
        return false;
    }
}
