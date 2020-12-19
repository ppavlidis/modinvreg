package ubc.pavlab.rdp.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ubc.pavlab.rdp.model.Taxon;
import ubc.pavlab.rdp.repositories.TaxonRepository;

import java.util.Collection;

/**
 * Created by mjacobson on 17/01/18.
 */
@Service("taxonService")
public class TaxonServiceImpl implements TaxonService {

    @Autowired
    private TaxonRepository taxonRepository;

    @Override
    public Taxon findById( Integer id ) {
        return taxonRepository.findById( id ).orElse( null );
    }

    @Override
    public Collection<Taxon> findByActiveTrue() {
        return taxonRepository.findByActiveTrueOrderByOrdering();
    }

    @Override
    public Collection<Taxon> loadAll() {
        return taxonRepository.findAll();
    }

}
