package ubc.pavlab.rdp.services;

import lombok.extern.apachecommons.CommonsLog;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ubc.pavlab.rdp.model.GeneInfo;
import ubc.pavlab.rdp.model.Taxon;
import ubc.pavlab.rdp.model.enums.GeneMatchType;
import ubc.pavlab.rdp.repositories.GeneInfoRepository;
import ubc.pavlab.rdp.settings.ApplicationSettings;
import ubc.pavlab.rdp.util.*;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.MessageFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.groupingBy;

/**
 * Created by mjacobson on 17/01/18.
 */
@Service("geneService")
@CommonsLog
public class GeneInfoServiceImpl implements GeneInfoService {

    @Autowired
    private GeneInfoRepository geneInfoRepository;

    @Autowired
    private TaxonService taxonService;

    @Autowired
    private ApplicationSettings applicationSettings;

    @Autowired
    private GeneInfoParser geneInfoParser;

    @Autowired
    private GeneOrthologsParser geneOrthologsParser;

    @Override
    public GeneInfo load( Integer id ) {
        return geneInfoRepository.findByGeneId( id );
    }

    @Override
    public Collection<GeneInfo> load( Collection<Integer> ids ) {
        return geneInfoRepository.findAllByGeneIdIn( ids );
    }

    @Override
    public GeneInfo findBySymbolAndTaxon( String symbol, Taxon taxon ) {
        return geneInfoRepository.findBySymbolAndTaxon( symbol, taxon );
    }

    @Override
    public Collection<GeneInfo> findBySymbolInAndTaxon( Collection<String> symbols, Taxon taxon ) {
        return geneInfoRepository.findBySymbolInAndTaxon( symbols, taxon );
    }

    @Override
    public Collection<SearchResult<GeneInfo>> autocomplete( String query, Taxon taxon, int maxResults ) {
        Collection<SearchResult<GeneInfo>> results = new LinkedHashSet<>();

        if ( addAll( results, geneInfoRepository.findAllBySymbolAndTaxon( query, taxon ), GeneMatchType.EXACT_SYMBOL, maxResults ) ) {
            return results;
        }

        if ( addAll( results, geneInfoRepository.findAllBySymbolStartingWithIgnoreCaseAndTaxon( query, taxon ),
                GeneMatchType.SIMILAR_SYMBOL, maxResults ) ) {
            return results;
        }

        if ( addAll( results, geneInfoRepository.findAllByNameStartingWithIgnoreCaseAndTaxon( query, taxon ),
                GeneMatchType.SIMILAR_NAME, maxResults ) ) {
            return results;
        }

        addAll( results, geneInfoRepository.findAllByAliasesContainingIgnoreCaseAndTaxon( query, taxon ),
                GeneMatchType.SIMILAR_ALIAS, maxResults );

        return results;
    }

    @Override
    @Transactional
    public void updateGenes() {
        ApplicationSettings.CacheSettings cacheSettings = applicationSettings.getCache();
        log.info( "Updating genes..." );
        for ( Taxon taxon : taxonService.findByActiveTrue() ) {
            if ( taxon.getGeneUrl() == null ) {
                log.warn( MessageFormat.format( "Gene info URL for {0} is not defined, skipping this taxon.", taxon ) );
            }
            try {
                Resource resource;
                if ( cacheSettings.isLoadFromDisk() ) {
                    resource = cacheSettings.getGeneFilesLocation().createRelative( FilenameUtils.getName( taxon.getGeneUrl().getPath() ) );
                } else {
                    resource = new UrlResource( taxon.getGeneUrl() );
                }
                log.info( MessageFormat.format( "Loading genes for {0} from {1}.", taxon, resource ) );
                List<GeneInfoParser.Record> data = geneInfoParser.parse( new GZIPInputStream( resource.getInputStream() ), taxon.getId() );
                log.info( MessageFormat.format( "Done parsing genes for {0}.", taxon ) );
                // retrieve all relevant genes in a single database query
                // note that because of this, we have to make this whole process @Transactional
                Map<Integer, GeneInfo> foundGenesByGeneId = geneInfoRepository
                        .findAllByGeneIdIn( data.stream().map( GeneInfoParser.Record::getGeneId ).collect( Collectors.toList() ) )
                        .stream()
                        .collect( Collectors.toMap( GeneInfo::getGeneId, identity() ) );
                log.info( MessageFormat.format( "Done retrieving existing genes for {0}, will now proceed to update.", taxon ) );
                long numberOfGenesInTaxon = geneInfoRepository.countByTaxon( taxon );
                if ( foundGenesByGeneId.size() < numberOfGenesInTaxon ) {
                    log.warn( MessageFormat.format( "No information were found for {0} genes in {1}.", numberOfGenesInTaxon - foundGenesByGeneId.size(), taxon ) );
                }
                Set<GeneInfo> geneData = data.stream()
                        .map( record -> {
                            GeneInfo gene = foundGenesByGeneId.getOrDefault( record.getGeneId(), new GeneInfo() );
                            gene.setTaxon( taxon );
                            gene.setGeneId( record.getGeneId() );
                            gene.setSymbol( record.getSymbol() );
                            gene.setAliases( record.getSynonyms() );
                            gene.setName( record.getDescription() );
                            gene.setModificationDate( record.getModificationDate() );
                            return gene;
                        } ).collect( Collectors.toSet() );
                geneInfoRepository.saveAll( geneData );
                log.info( MessageFormat.format( "Done updating genes for {0}.", taxon ) );
            } catch ( ParseException | IOException e ) {
                log.error( MessageFormat.format( "Issue loading genes for {0}.", taxon ), e );
            }
        }
        log.info( MessageFormat.format( "Finished updating {0} genes.", geneInfoRepository.count() ) );
    }

    @Override
    @Transactional
    public void updateGeneOrthologs() {
        Resource resource = applicationSettings.getCache().getOrthologFile();

        if ( resource == null ) {
            log.warn( "No orthologs file found, skipping update of gene orthologs." );
            return;
        }

        log.info( MessageFormat.format( "Updating gene orthologs from {0}...", resource ) );

        DecimalFormat geneIdFormat = new DecimalFormat();
        geneIdFormat.setGroupingUsed( false );

        // only orthologs update active taxon
        Set<Integer> activeTaxonIds = taxonService.findByActiveTrue()
                .stream()
                .map( Taxon::getId )
                .collect( Collectors.toSet() );

        List<GeneOrthologsParser.Record> records;
        try {
            records = geneOrthologsParser.parse( new GZIPInputStream( resource.getInputStream() ) );
        } catch ( IOException | ParseException e ) {
            log.error( MessageFormat.format( "Failed to parse gene orthologs from {0}.", resource ), e );
            return;
        }

        Map<Integer, List<GeneOrthologsParser.Record>> recordByGeneId = records.stream()
                .filter( record -> record.getRelationship().equals( "Ortholog" ) )
                .filter( record -> activeTaxonIds.contains( record.getTaxonId() ) && activeTaxonIds.contains( record.getOrthologTaxonId() ) )
                .collect( groupingBy( GeneOrthologsParser.Record::getGeneId ) );

        log.info( MessageFormat.format( "Loading all genes referred by {0}", resource ) );
        Map<Integer, GeneInfo> geneInfoWithOrthologsByGeneId = geneInfoRepository.findAllByGeneIdWithOrthologs( recordByGeneId.keySet() )
                .stream()
                .distinct() // for some bizarre reason, this can result in duplicate entries...
                .collect( Collectors.toMap( GeneInfo::getGeneId, identity() ) );

        log.info( MessageFormat.format( "Loading all orthologs referred by {0}", resource ) );
        Set<Integer> orthologGeneIds = records.stream()
                .map( GeneOrthologsParser.Record::getOrthologId )
                .collect( Collectors.toSet() );
        Map<Integer, GeneInfo> orthologByGeneId = geneInfoRepository.findAllByGeneIdIn( orthologGeneIds ).stream()
                .collect( Collectors.toMap( GeneInfo::getGeneId, identity() ) );

        log.info( "Now updating orthologs..." );
        for ( Map.Entry<Integer, List<GeneOrthologsParser.Record>> entry : recordByGeneId.entrySet() ) {
            Integer geneId = entry.getKey();
            List<GeneOrthologsParser.Record> geneRecords = entry.getValue();
            GeneInfo gene = geneInfoWithOrthologsByGeneId.get( geneId );
            if ( gene == null ) {
                log.info( MessageFormat.format( "Ignoring orthologs for {0} since it is missing from the database.",
                        geneIdFormat.format( geneId ) ) );
                continue;
            }
            Set<GeneInfo> orthologs = geneRecords.stream()
                    .map( GeneOrthologsParser.Record::getOrthologId )
                    .map( orthologByGeneId::get )
                    .filter( Objects::nonNull )
                    .collect( Collectors.toSet() );
            gene.getOrthologs().removeIf( o -> !orthologs.contains( o ) );
            gene.getOrthologs().addAll( orthologs );
            geneInfoRepository.save( gene );
        }
        log.info( "Done updating gene orthologs." );
    }

    private <T> boolean addAll( Collection<SearchResult<T>> container, Collection<T> newValues, GeneMatchType match, int maxSize ) {

        for ( T newValue : newValues ) {
            if ( maxSize == -1 || container.size() < maxSize ) {
                container.add( new SearchResult<>( match, newValue ) );
            } else {
                return true;
            }
        }

        return false;
    }
}
