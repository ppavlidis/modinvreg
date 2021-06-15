package ubc.pavlab.rdp.repositories;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.junit4.SpringRunner;
import ubc.pavlab.rdp.model.Taxon;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static ubc.pavlab.rdp.util.TestUtils.createTaxon;

/**
 * Created by mjacobson on 26/02/18.
 */
@RunWith(SpringRunner.class)
@DataJpaTest
public class TaxonRepositoryTest {

    @Autowired
    private TaxonRepository taxonRepository;

    @Test
    public void findByActiveTrue_thenReturnOnlyActive() {
        Taxon humanTaxon = createTaxon( 9606 );
        Collection<Taxon> taxon = taxonRepository.findByActiveTrueOrderByOrdering();
        assertThat( taxon ).containsExactly( humanTaxon );
    }


    @Test
    public void findAll_thenReturnAllInCorrectOrder() {
        List<Taxon> taxa = taxonRepository.findAll( Sort.by( Sort.Direction.ASC, "ordering" ) );
        assertThat( taxa ).containsExactly(
                createTaxon( 9606 ),
                createTaxon( 10090 ),
                createTaxon( 10116 ),
                createTaxon( 7955 ),
                createTaxon( 8364 ),
                createTaxon( 7227 ),
                createTaxon( 6239 ),
                createTaxon( 559292 ),
                createTaxon( 4896 ),
                createTaxon( 511145 ) );
    }
}
