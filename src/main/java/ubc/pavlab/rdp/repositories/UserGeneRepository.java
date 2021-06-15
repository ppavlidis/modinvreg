package ubc.pavlab.rdp.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ubc.pavlab.rdp.model.Taxon;
import ubc.pavlab.rdp.model.UserGene;
import ubc.pavlab.rdp.model.UserOrgan;
import ubc.pavlab.rdp.model.enums.PrivacyLevelType;
import ubc.pavlab.rdp.model.enums.TierType;

import javax.persistence.QueryHint;
import java.util.Collection;
import java.util.Set;

@Repository
public interface UserGeneRepository extends JpaRepository<UserGene, Integer> {

    Integer countByTierIn( Collection<TierType> tiers );

    /**
     * Count distinct genes within a given set of tiers.
     * <p>
     * Do not call this method with an empty set of tiers as it will result in an invalid SQL query.
     *
     * @param tiers a non-empty set of tiers from which user genes are counted
     * @return
     */
    @Query("select count(distinct geneId) FROM UserGene WHERE tier IN (:tiers)")
    Integer countDistinctGeneByTierIn( @Param("tiers") Collection<TierType> tiers );

    @Query("select count(distinct user) FROM UserGene WHERE taxon = :taxon")
    Integer countDistinctUserByTaxon( @Param("taxon") Taxon taxon );

    @Query("select count(distinct user) FROM UserGene")
    Integer countDistinctUser();

    Page<UserGene> findAllByPrivacyLevelAndUserProfilePrivacyLevel( PrivacyLevelType privacyLevel, PrivacyLevelType userProfilePrivacyLevel, Pageable pageable );

    @QueryHints(@QueryHint(name = "org.hibernate.cacheable", value = "true"))
    UserGene findById( int id );

    @QueryHints(@QueryHint(name = "org.hibernate.cacheable", value = "true"))
    Collection<UserGene> findByGeneId( int geneId );

    @QueryHints(@QueryHint(name = "org.hibernate.cacheable", value = "true"))
    Collection<UserGene> findByGeneIdAndTier( int geneId, TierType tier );

    @QueryHints(@QueryHint(name = "org.hibernate.cacheable", value = "true"))
    Collection<UserGene> findByGeneIdAndTierIn( int geneId, Set<TierType> tiers );

    @QueryHints(@QueryHint(name = "org.hibernate.cacheable", value = "true"))
    Collection<UserGene> findBySymbolContainingIgnoreCaseAndTaxon( String symbolContaining, Taxon taxon );

    @QueryHints(@QueryHint(name = "org.hibernate.cacheable", value = "true"))
    Collection<UserGene> findBySymbolContainingIgnoreCaseAndTaxonAndTier( String symbolContaining, Taxon taxon, TierType tier );

    @QueryHints(@QueryHint(name = "org.hibernate.cacheable", value = "true"))
    Collection<UserGene> findBySymbolContainingIgnoreCaseAndTaxonAndTierIn( String symbolContaining, Taxon taxon, Set<TierType> tiers );

    UserGene findBySymbolAndTaxon( String symbol, Taxon taxon );

    Collection<UserGene> findByGeneIdAndTierAndUserUserOrgansIn( int geneId, TierType tier, Collection<UserOrgan> organs );

    Collection<UserGene> findByGeneIdAndTierInAndUserUserOrgansIn( int geneId, Set<TierType> tiers, Collection<UserOrgan> organs );

    /**
     * Find all user genes ortholog to a given gene.
     */
    @Query("select user_gene from UserGene user_gene where user_gene.geneId in (select ortholog.geneId from GeneInfo gene_info join gene_info.orthologs as ortholog where gene_info.geneId = :geneId)")
    Collection<UserGene> findOrthologsByGeneId( @Param("geneId") Integer geneId );

    /**
     * Find user genes within a given taxon that are ortholog to a given gene.
     */
    @Query("select user_gene from UserGene user_gene where user_gene.geneId in (select ortholog.geneId from GeneInfo gene_info join gene_info.orthologs as ortholog where gene_info.geneId = :geneId and ortholog.taxon = :taxon)")
    Collection<UserGene> findOrthologsByGeneIdAndTaxon( @Param("geneId") Integer geneId, @Param("taxon") Taxon taxon );

    // Return all human genes.
    @QueryHints(@QueryHint(name = "org.hibernate.cacheable", value = "true"))
    @Query("select geneId FROM UserGene where taxon = '9606'")
    Collection<Integer> findAllHumanGenes();

    @Query("select gene from UserGene gene left join fetch gene.geneInfo")
    Collection<UserGene> findAllWithGeneInfo();
}
