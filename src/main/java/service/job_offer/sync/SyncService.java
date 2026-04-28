package service.job_offer.sync;

/**
 * Sync service interface for desktop ↔ web synchronization.
 *
 * Currently implemented as a no-op stub ({@link NoOpSyncService}).
 * Replace with {@code RestSyncService} when a web backend is available.
 */
public interface SyncService {

    /**
     * Push any locally queued changes to the web backend.
     * @return number of items successfully synced
     */
    int pushChanges();

    /**
     * Pull remote changes since last sync and apply them locally.
     * @return number of remote records processed
     */
    int pullChanges();

    /**
     * @return ISO-8601 timestamp of the last successful sync, or null if never synced
     */
    String getLastSyncTime();

    /**
     * @return true if the service is configured and able to reach the backend
     */
    boolean isConfigured();
}
