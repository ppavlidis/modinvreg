package ubc.pavlab.rdp.model;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import ubc.pavlab.rdp.model.enums.RelationshipType;

/**
 * Created by mjacobson on 28/01/18.
 */
@Getter
@AllArgsConstructor
@EqualsAndHashCode(of = { "term" })
@ToString
public class Relationship {

    private final String term;
    private final RelationshipType type;

    @Deprecated
    public Relationship( GeneOntologyTermInfo term, RelationshipType type ) {
        this( term.getGoId(), type );
    }

}