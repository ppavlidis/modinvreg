/*
 * The rdp project
 *
 * Copyright (c) 2014 University of British Columbia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package ubc.pavlab.rdp.services;

import lombok.extern.apachecommons.CommonsLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.access.prepost.PostFilter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ubc.pavlab.rdp.model.*;
import ubc.pavlab.rdp.model.enums.PrivacyLevelType;
import ubc.pavlab.rdp.model.enums.ResearcherCategory;
import ubc.pavlab.rdp.model.enums.ResearcherPosition;
import ubc.pavlab.rdp.model.enums.TierType;
import ubc.pavlab.rdp.repositories.TaxonRepository;
import ubc.pavlab.rdp.repositories.UserGeneRepository;
import ubc.pavlab.rdp.settings.ApplicationSettings;

import java.text.MessageFormat;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Comparator.*;
import static org.springframework.util.CollectionUtils.containsAny;

/**
 * Created by mjacobson on 17/01/18.
 */
@CommonsLog
@Service("userGeneService")
public class UserGeneServiceImpl implements UserGeneService {

    @Autowired
    private TaxonRepository taxonRepository;

    @Autowired
    private UserGeneRepository userGeneRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private TierService tierService;

    @Autowired
    private PermissionEvaluator permissionEvaluator;

    @Autowired
    private ApplicationSettings applicationSettings;

    @Override
    public Page<UserGene> findAllNoAuth( Pageable pageable ) {
        return userGeneRepository.findAll( pageable );
    }

    @Override
    public Page<UserGene> findAllByPrivacyLevel( PrivacyLevelType privacyLevelType, Pageable pageable ) {
        return userGeneRepository.findAllByPrivacyLevelAndUserProfilePrivacyLevel( privacyLevelType, privacyLevelType, pageable );
    }

    @Cacheable(cacheNames = "stats", key = "#root.methodName")
    @Override
    public Integer countUniqueAssociations() {
        return userGeneRepository.countDistinctGeneByTierIn( TierType.MANUAL );
    }

    @Cacheable(cacheNames = "stats", key = "#root.methodName")
    @Override
    public Integer countAssociations() {
        return userGeneRepository.countByTierIn( TierType.MANUAL );
    }

    @Cacheable(cacheNames = "stats", key = "#root.methodName")
    @Override
    public Map<String, Integer> researcherCountByTaxon() {
        return taxonRepository.findByActiveTrueOrderByOrdering().stream()
                .collect( Collectors.toMap( Taxon::getCommonName, userGeneRepository::countDistinctUserByTaxon ) );
    }

    @Cacheable(cacheNames = "stats", key = "#root.methodName")
    @Override
    public Integer countUsersWithGenes() {
        return userGeneRepository.countDistinctUser();
    }

    @Cacheable(cacheNames = "stats", key = "#root.methodName")
    @Override
    public Integer countUniqueAssociationsAllTiers() {
        if ( tierService.getEnabledTiers().isEmpty() )
            return 0;
        return userGeneRepository.countDistinctGeneByTierIn( tierService.getEnabledTiers() );
    }

    @Cacheable(cacheNames = "stats", key = "#root.methodName")
    @Override
    public Integer countUniqueAssociationsToHumanAllTiers() {
        /* This is also called the "human gene coverage" */
        Collection<Integer> humanGenes = new HashSet<>( userGeneRepository.findAllHumanGenes() );

        // Add orthologs mapped to humans
        // TODO
        // humanGenes.addAll( userGeneRepository.findHumanGenesForTarget(
        //         userGeneRepository.findDistinctGeneByTierIn( TierType.ANY )
        // ));

        // Add directly entered human genes

        return humanGenes.size();
    }

    @Override
    @PostFilter("hasPermission(filterObject, 'read')")
    public Collection<UserGene> handleGeneSearch( Gene gene, Set<TierType> tiers, Taxon orthologTaxon, Set<ResearcherPosition> researcherPositions, Collection<ResearcherCategory> researcherCategories, Collection<UserOrgan> organs ) {
        if ( applicationSettings.getPrivacy().isEnableAnonymizedSearchResults() ) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            return handleGeneSearchInternal( gene, tiers, orthologTaxon, researcherPositions, researcherCategories, organs ).stream()
                    .map( ug -> permissionEvaluator.hasPermission( auth, ug, "read" ) ? ug : userService.anonymizeUserGene( ug ) )
                    .sorted( comparing( UserGene::getAnonymousId, nullsFirst( naturalOrder() ) ) )
                    .collect( Collectors.toList() ); // we need to preserve the search order
        } else {
            return handleGeneSearchInternal( gene, tiers, orthologTaxon, researcherPositions, researcherCategories, organs );
        }
    }

    private Collection<UserGene> handleGeneSearchInternal( Gene gene, Set<TierType> tiers, Taxon orthologTaxon, Set<ResearcherPosition> researcherPositions, Collection<ResearcherCategory> researcherCategories, Collection<UserOrgan> organs ) {
        Collection<UserGene> uGenes = new LinkedHashSet<>();

        // ortholog relationship is not reflexive (i.e. a gene is not its own ortholog), but we still want to display
        // that gene first when ortholog search is performed in the same MO
        if ( orthologTaxon == null || gene.getTaxon().equals( orthologTaxon ) ) {
            uGenes.addAll( userGeneRepository.findByGeneIdAndTierIn( gene.getGeneId(), tiers ).stream()
                    .filter( ug -> researcherPositions == null || researcherPositions.contains( ug.getUser().getProfile().getResearcherPosition() ) )
                    .filter( ug -> researcherCategories == null || containsAny( researcherCategories, ug.getUser().getProfile().getResearcherCategories() ) )
                    .filter( ortholog -> organs == null || containsAny( organs, ortholog.getUser().getUserOrgans().values() ) )
                    .collect( Collectors.toSet() ) );
        }

        uGenes.addAll( handleOrthologSearchInternal( gene, tiers, orthologTaxon, researcherPositions, researcherCategories, organs ) );

        return uGenes;
    }

    private Collection<UserGene> handleOrthologSearchInternal( Gene gene, Set<TierType> tiers, Taxon orthologTaxon, Set<ResearcherPosition> researcherPositions, Collection<ResearcherCategory> researcherCategories, Collection<UserOrgan> userOrgans ) {
        return ( orthologTaxon == null ? userGeneRepository.findOrthologsByGeneId( gene.getGeneId() ) : userGeneRepository.findOrthologsByGeneIdAndTaxon( gene.getGeneId(), orthologTaxon ) ).stream()
                .filter( ortholog -> tiers.contains( ortholog.getTier() ) )
                .filter( ug -> researcherPositions == null || researcherPositions.contains( ug.getUser().getProfile().getResearcherPosition() ) )
                .filter( ug -> researcherCategories == null || containsAny( researcherCategories, ug.getUser().getProfile().getResearcherCategories() ) )
                .filter( ortholog -> userOrgans == null || containsAny( userOrgans, ortholog.getUser().getUserOrgans().values() ) )
                .collect( Collectors.toSet() );
    }

    @Override
    @Transactional
    public void updateUserGenes() {
        log.info( "Updating user genes..." );
        for ( UserGene userGene : userGeneRepository.findAllWithGeneInfo() ) {
            GeneInfo cachedGene = userGene.getGeneInfo();
            if ( cachedGene == null ) {
                log.warn( MessageFormat.format( "User has a reference to a gene missing from the cache: {0}.", userGene ) );
                continue;
            }
            userGene.updateGene( cachedGene );
            userGeneRepository.save( userGene );
        }
        log.info( "Done updating user genes." );
    }
}
