package ubc.pavlab.rdp.repositories;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import ubc.pavlab.rdp.model.Gene;
import ubc.pavlab.rdp.model.GeneOntologyTermInfo;

import java.util.*;

@Repository
public class GeneOntologyTermInfoRepository implements CrudRepository<GeneOntologyTermInfo, String> {

    private Map<String, GeneOntologyTermInfo> terms = new HashMap<>();

    private MultiValueMap<Integer, GeneOntologyTermInfo> geneIdsToTerms = new LinkedMultiValueMap<>();

    @Override
    public <S extends GeneOntologyTermInfo> S save( S term ) {
        return saveByAlias( term.getGoId(), term );
    }

    @Override
    public <S extends GeneOntologyTermInfo> Iterable<S> saveAll( Iterable<S> iterable ) {
        List<GeneOntologyTermInfo> savedTerms = new ArrayList<>();
        for ( GeneOntologyTermInfo term : iterable ) {
            savedTerms.add( save( term ) );
        }
        return (Iterable<S>) savedTerms;
    }

    @Override
    public Optional<GeneOntologyTermInfo> findById( String s ) {
        return Optional.ofNullable( terms.get( s ) );
    }

    @Override
    public boolean existsById( String s ) {
        return terms.containsKey( s );
    }

    public <S extends GeneOntologyTermInfo> S saveByAlias( String alias, S term ) {
        terms.put( alias, term );
        for ( Integer geneId : term.getDirectGeneIds() ) {
            geneIdsToTerms.add( geneId, term );
        }
        return term;
    }

    public <S extends GeneOntologyTermInfo> Iterable<S> saveAllByAlias( Map<String, GeneOntologyTermInfo> terms ) {
        List<GeneOntologyTermInfo> savedTerms = new ArrayList<>( terms.size() );
        for ( Map.Entry<String, GeneOntologyTermInfo> entry : terms.entrySet() ) {
            savedTerms.add( saveByAlias( entry.getKey(), entry.getValue() ) );
        }
        return (Iterable<S>) savedTerms;
    }

    @Override
    public Collection<GeneOntologyTermInfo> findAll() {
        return terms.values();
    }

    @Override
    public Collection<GeneOntologyTermInfo> findAllById( Iterable<String> iterable ) {
        Collection<GeneOntologyTermInfo> results = new HashSet<>();
        for ( String goTerm : iterable ) {
            if ( terms.containsKey( goTerm ) ) {
                results.add( terms.get( goTerm ) );
            }
        }
        return results;
    }

    public Collection<GeneOntologyTermInfo> findAllByGene( Gene gene ) {
        return geneIdsToTerms.getOrDefault( gene.getGeneId(), Collections.emptyList() );
    }

    @Override
    public long count() {
        return terms.values().stream().distinct().count();
    }

    @Override
    public void deleteById( String s ) {
        terms.remove( s );
    }

    @Override
    public void delete( GeneOntologyTermInfo term ) {
        terms.remove( term.getGoId() );
    }

    @Override
    public void deleteAllById( Iterable<? extends String> iterable ) {
        for ( String s : iterable ) {
            deleteById( s );
        }
    }

    @Override
    public void deleteAll( Iterable<? extends GeneOntologyTermInfo> iterable ) {
        for ( GeneOntologyTermInfo term : iterable ) {
            delete( term );
        }

    }

    @Override
    public void deleteAll() {
        terms.clear();
        geneIdsToTerms.clear();
    }
}
