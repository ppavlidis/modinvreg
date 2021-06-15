package ubc.pavlab.rdp.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import ubc.pavlab.rdp.model.enums.PrivacyLevelType;

import javax.persistence.*;
import java.util.Optional;

/**
 * GO term tracked by a user.
 * <p>
 * TODO: add user to {@link EqualsAndHashCode} definition to distinguish
 * between terms from different users.
 * <p>
 * Created by mjacobson on 28/01/18.
 */
@Entity
@Table(name = "term",
        uniqueConstraints = { @UniqueConstraint(columnNames = { "user_id", "taxon_id", "go_id" }) },
        indexes = { @Index(columnList = "go_id") })
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@Getter
@Setter
@EqualsAndHashCode(of = { "user", "taxon" }, callSuper = true)
@ToString(of = { "user", "taxon" }, callSuper = true)
@NoArgsConstructor
public class UserTerm extends GeneOntologyTerm implements UserContent {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column
    @JsonIgnore
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    @JsonIgnore
    private User user;

    @ManyToOne
    @JoinColumn(name = "taxon_id")
    private Taxon taxon;

    @Column
    private Long frequency;

    @Column
    private Long size;

    public static UserTerm createUserTerm( User user, GeneOntologyTerm term, Taxon taxon ) {
        UserTerm userTerm = new UserTerm();
        userTerm.setGoId( term.getGoId() );
        userTerm.updateTerm( term );
        userTerm.user = user;
        userTerm.taxon = taxon;
        return userTerm;
    }

    public void updateTerm( GeneOntologyTerm term ) {
        this.setName( term.getName() );
        this.setDefinition( term.getDefinition() );
        this.setAspect( term.getAspect() );
    }

    @Override
    @JsonIgnore
    public Optional<User> getOwner() {
        return Optional.of( user );
    }

    @Override
    @JsonIgnore
    public PrivacyLevelType getEffectivePrivacyLevel() {
        return user.getEffectivePrivacyLevel();
    }
}
