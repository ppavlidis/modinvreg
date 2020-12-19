package ubc.pavlab.rdp.model;

import lombok.*;
import ubc.pavlab.rdp.model.enums.PrivacyLevelType;

import javax.persistence.*;
import javax.validation.constraints.Email;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAmount;
import java.util.Optional;

/**
 * Created by mjacobson on 22/01/18.
 */
@Entity
@Table(name = "verification_token",
        uniqueConstraints = { @UniqueConstraint(columnNames = { "token" }) })
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = { "user" }, callSuper = true)
@ToString(of = { "user" }, callSuper = true)
public class VerificationToken extends Token implements UserContent {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer id;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(nullable = false, name = "user_id")
    private User user;

    @Email
    @Column(name = "email")
    private String email;

    @Override
    protected TemporalAmount getDuration() {
        return Duration.of( 24, ChronoUnit.HOURS );
    }

    @Override
    public Optional<User> getOwner() {
        return Optional.of( getUser() );
    }

    @Override
    public PrivacyLevelType getEffectivePrivacyLevel() {
        return PrivacyLevelType.PRIVATE;
    }
}
