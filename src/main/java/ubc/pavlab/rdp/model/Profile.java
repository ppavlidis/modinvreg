package ubc.pavlab.rdp.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ubc.pavlab.rdp.model.enums.PrivacyLevelType;
import ubc.pavlab.rdp.model.enums.ResearcherCategory;
import ubc.pavlab.rdp.model.enums.ResearcherPosition;

import javax.persistence.*;
import javax.validation.constraints.Email;
import javax.validation.constraints.NotEmpty;
import java.net.URL;
import java.text.MessageFormat;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by mjacobson on 18/01/18.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class Profile {
    @Column(name = "name")
    @NotEmpty(message = "Please provide your name.", groups = { User.ValidationUserAccount.class, User.ValidationServiceAccount.class })
    private String name;

    @Column(name = "last_name")
    @NotEmpty(message = "Please provide your last name.", groups = { User.ValidationUserAccount.class })
    private String lastName;

    @Transient
    public String getFullName() {
        if ( lastName == null || lastName.isEmpty() ) {
            return name == null ? "" : name;
        } else if ( name == null || name.isEmpty() ) {
            return "";
        } else {
            return MessageFormat.format( "{0}, {1}", lastName, name );
        }
    }

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "organization")
    private String organization;

    @Column(name = "department")
    private String department;

    @Column(name = "phone")
    private String phone;

    @Email(message = "Your email address is not valid.")
    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "contact_email_verified", nullable = false)
    private boolean contactEmailVerified;

    @Column(name = "website")
    private String website;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "privacy_level")
    private PrivacyLevelType privacyLevel;

    @Column(name = "shared", nullable = false)
    private boolean shared;

    @Column(name = "hide_genelist", nullable = false)
    private boolean hideGenelist;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "user_id")
    private final Set<Publication> publications = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "researcher_position")
    private ResearcherPosition researcherPosition;

    @Enumerated(EnumType.STRING)
    @Column(name = "researcher_category")
    @ElementCollection
    @CollectionTable(name = "user_researcher_category", joinColumns = { @JoinColumn(name = "user_id") })
    private final Set<ResearcherCategory> researcherCategories = new HashSet<>();
}
