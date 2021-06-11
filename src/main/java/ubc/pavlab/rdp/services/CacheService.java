package ubc.pavlab.rdp.services;

import lombok.extern.apachecommons.CommonsLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import ubc.pavlab.rdp.settings.ApplicationSettings;

import java.text.MessageFormat;
import java.time.LocalDateTime;

@CommonsLog
@Service
public class CacheService {

    private static final String UPDATE_CACHE_CRON = "0 0 0 1 * *";

    @Autowired
    private ApplicationSettings applicationSettings;

    @Autowired
    private GeneInfoService geneInfoService;

    @Autowired
    private UserGeneService geneService;

    @Autowired
    private GOService goService;

    @Autowired
    private UserService userService;

    @Autowired
    private OrganInfoService organInfoService;

    @Autowired
    private UserOrganService userOrganService;

    /**
     * Cached data are updated on startup and then monthly.
     * <p>
     * This task make sure that the updates are performed in a logical order (i.e. genes are updated prior to ortholog
     * relationships and user-gene associations).
     */
    @Scheduled(cron = UPDATE_CACHE_CRON)
    @EventListener(ApplicationReadyEvent.class)
    public void updateCache() {
        ApplicationSettings.CacheSettings cacheSettings = applicationSettings.getCache();
        if ( cacheSettings.isEnabled() ) {
            log.info( "Updating cached data..." );
            geneInfoService.updateGenes();
            geneInfoService.updateGeneOrthologs();
            geneService.updateUserGenes();
            goService.updateGoTerms();
            userService.updateUserTerms();
            organInfoService.updateOrganInfos();
            userOrganService.updateUserOrgans();
            CronExpression cronExpression = CronExpression.parse( UPDATE_CACHE_CRON );
            log.info( MessageFormat.format( "Done updating cached data. Next update is scheduled on {0}.",
                    cronExpression.next( LocalDateTime.now() ) ) );
        }
    }
}
