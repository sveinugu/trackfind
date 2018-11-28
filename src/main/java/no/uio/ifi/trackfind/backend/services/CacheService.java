package no.uio.ifi.trackfind.backend.services;

import lombok.extern.slf4j.Slf4j;
import no.uio.ifi.trackfind.backend.dao.Queries;
import no.uio.ifi.trackfind.backend.data.providers.DataProvider;
import no.uio.ifi.trackfind.backend.events.DataReloadEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Service
public class CacheService {

    protected TrackFindService trackFindService;
    protected JdbcTemplate jdbcTemplate;

    /**
     * Refreshes materialized views in the database.
     */
    @TransactionalEventListener(classes = DataReloadEvent.class)
    public void resetCaches(DataReloadEvent dataReloadEvent) {
        log.info("Event {} received.", dataReloadEvent.getSource());
        jdbcTemplate.execute(Queries.REFRESH_DATASETS_VIEW);
        jdbcTemplate.execute(Queries.REFRESH_LATEST_DATASETS_VIEW);
        trackFindService.getDataProvider(dataReloadEvent.getDataProviderName());
        log.info("Materialized views refreshed.");
    }

    @Autowired
    public void setTrackFindService(TrackFindService trackFindService) {
        this.trackFindService = trackFindService;
    }

    @Autowired
    public void setJdbcTemplate(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

}
