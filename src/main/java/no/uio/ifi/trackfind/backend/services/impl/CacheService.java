package no.uio.ifi.trackfind.backend.services.impl;

import lombok.extern.slf4j.Slf4j;
import no.uio.ifi.trackfind.backend.events.DataReloadEvent;
import no.uio.ifi.trackfind.backend.pojo.Queries;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Service for resetting caches.
 */
// TODO: cover with tests
@Slf4j
@Service
@Transactional
public class CacheService {

    protected TrackFindService trackFindService;
    protected JdbcTemplate jdbcTemplate;

    /**
     * Refreshes materialized views in the database.
     */
    @TransactionalEventListener(classes = DataReloadEvent.class)
    public void resetCaches(DataReloadEvent dataReloadEvent) {
        log.info("Event {} received.", dataReloadEvent.getSource());
        jdbcTemplate.execute(Queries.REFRESH_MATERIALIZED_VIEWS);
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
