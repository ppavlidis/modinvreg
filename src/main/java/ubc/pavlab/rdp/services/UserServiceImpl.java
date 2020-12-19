package ubc.pavlab.rdp.services;

import lombok.NonNull;
import lombok.extern.apachecommons.CommonsLog;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PostFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ubc.pavlab.rdp.events.OnContactEmailUpdateEvent;
import ubc.pavlab.rdp.exception.TokenException;
import ubc.pavlab.rdp.model.*;
import ubc.pavlab.rdp.model.enums.PrivacyLevelType;
import ubc.pavlab.rdp.model.enums.ResearcherCategory;
import ubc.pavlab.rdp.model.enums.ResearcherPosition;
import ubc.pavlab.rdp.model.enums.TierType;
import ubc.pavlab.rdp.repositories.*;
import ubc.pavlab.rdp.settings.ApplicationSettings;

import javax.validation.ValidationException;
import java.security.SecureRandom;
import java.text.MessageFormat;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.function.Function.identity;
import static org.springframework.util.CollectionUtils.containsAny;

/**
 * Created by mjacobson on 16/01/18.
 */
@Service("userService")
@CommonsLog
public class UserServiceImpl implements UserService {

    public static final String USERS_BY_ANONYMOUS_ID_CACHE_KEY = "ubc.pavlab.rdp.model.User.byAnonymousId";
    public static final String USER_GENES_BY_ANONYMOUS_ID_CACHE_KEY = "ubc.pavlab.rdp.model.UserGene.byAnonymousId";

    @Autowired
    private ApplicationSettings applicationSettings;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired
    private VerificationTokenRepository tokenRepository;
    @Autowired
    private BCryptPasswordEncoder bCryptPasswordEncoder;
    @Autowired
    private GOService goService;
    @Autowired
    private OrganInfoService organInfoService;
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private AccessTokenRepository accessTokenRepository;
    @Autowired
    private CacheManager cacheManager;
    @Autowired
    private MessageSource messageSource;
    @Autowired
    private GeneInfoService geneInfoService;
    @Autowired
    private PrivacyService privacyService;
    @Autowired
    private SecureRandom secureRandom;

    @Transactional
    @Override
    public User create( User user ) {
        user.setPassword( bCryptPasswordEncoder.encode( user.getPassword() ) );
        Role userRole = roleRepository.findByRole( "ROLE_USER" );
        user.getRoles().add( userRole );
        return userRepository.save( user );
    }

    @Secured("ROLE_ADMIN")
    @Transactional
    @Override
    public User createAdmin( User admin ) {
        admin.setPassword( bCryptPasswordEncoder.encode( admin.getPassword() ) );
        Role adminRole = roleRepository.findByRole( "ROLE_ADMIN" );
        admin.getRoles().add( adminRole );
        return userRepository.save( admin );
    }

    @Override
    @Secured("ROLE_ADMIN")
    @Transactional
    public User createServiceAccount( User user ) {
        user.setPassword( bCryptPasswordEncoder.encode( createSecureRandomToken() ) );
        Role serviceAccountRole = roleRepository.findByRole( "ROLE_SERVICE_ACCOUNT" );
        user.getRoles().add( serviceAccountRole );
        user = userRepository.save( user );
        createAccessTokenForUser( user );
        return user;
    }

    @Override
    @PreAuthorize("hasPermission(#user, 'update')")
    public User update( User user ) {
        if ( applicationSettings.getPrivacy() == null ) {
            // FIXME: this should not be possible...
            log.warn( MessageFormat.format( "{0} attempted to update, but applicationSettings.privacy is null.", user.getEmail() ) );
        } else {
            PrivacyLevelType defaultPrivacyLevel = PrivacyLevelType.values()[applicationSettings.getPrivacy().getDefaultLevel()];
            boolean defaultSharing = applicationSettings.getPrivacy().isDefaultSharing();
            boolean defaultGenelist = applicationSettings.getPrivacy().isAllowHideGenelist();

            if ( user.getProfile().getPrivacyLevel() == null ) {
                log.warn( "Received a null 'privacyLevel' value in profile." );
                user.getProfile().setPrivacyLevel( defaultPrivacyLevel );
            }
        }

        PrivacyLevelType userPrivacyLevel = user.getProfile().getPrivacyLevel();

        // We cap the user gene privacy level to its new profile setting
        for ( UserGene gene : user.getUserGenes().values() ) {
            PrivacyLevelType genePrivacyLevel = gene.getPrivacyLevel();
            // in case any of the user or gene privacy level is null, we already have a cascading value
            if ( userPrivacyLevel == null || genePrivacyLevel == null ) {
                continue;
            }
            if ( userPrivacyLevel.ordinal() < genePrivacyLevel.ordinal() ) {
                gene.setPrivacyLevel( userPrivacyLevel );
                log.info( MessageFormat.format( "Privacy level of {0} will be capped to {1} (was {2}",
                        gene, userPrivacyLevel, genePrivacyLevel ) );
            }
        }

        return userRepository.save( user );
    }

    @Secured("ROLE_ADMIN")
    @Transactional
    @Override
    public void delete( User user ) {

        VerificationToken verificationToken = tokenRepository.findByUser( user );

        if ( verificationToken != null ) {
            tokenRepository.delete( verificationToken );
        }

        PasswordResetToken passwordToken = passwordResetTokenRepository.findByUser( user );

        if ( passwordToken != null ) {
            passwordResetTokenRepository.delete( passwordToken );
        }

        userRepository.delete( user );
    }

    @Transactional
    @Override
    public User changePassword( String oldPassword, String newPassword )
            throws BadCredentialsException, ValidationException {
        User user = findCurrentUser();
        if ( bCryptPasswordEncoder.matches( oldPassword, user.getPassword() ) ) {
            if ( newPassword.length() >= 6 ) { //TODO: Tie in with hibernate constraint on User or not necessary?

                user.setPassword( bCryptPasswordEncoder.encode( newPassword ) );
                return update( user );
            } else {
                throw new ValidationException( "Password must be a minimum of 6 characters" );
            }
        } else {
            throw new BadCredentialsException( "Password incorrect" );
        }
    }

    @Override
    public String getCurrentUserName() {
        return getCurrentEmail();
    }

    @Override
    public String getCurrentEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getName();
    }

    @Override
    public User findCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if ( auth == null || auth.getPrincipal().equals( "anonymousUser" ) ) {
            return null;
        }

        return findUserByIdNoAuth( ( (UserPrinciple) auth.getPrincipal() ).getId() );
    }

    @Override
    @PostAuthorize("hasPermission(returnObject, 'read')")
    public User findUserById( int id ) {
        return userRepository.findById( id ).orElse( null );
    }


    /**
     * Retrieve a user using an anonymous identifier.
     * <p>
     * Identifiers are generated and stored when calling anonymizeUser on a {@link User} model.
     * <p>
     * There is no authorization check performed, so make sure that you reuse anonymizeUser before presenting the data
     * in the view.
     *
     * @param anonymousId
     * @return the user if found, otherwise null
     */
    @Override
    public User findUserByAnonymousIdNoAuth( UUID anonymousId ) {
        return cacheManager.getCache( USERS_BY_ANONYMOUS_ID_CACHE_KEY ).get( anonymousId, User.class );
    }

    /**
     * Retrieve a user's gene using an anonymous identifier.
     * <p>
     * Identifiers are generated and stored when calling anonymizeUserGene on a {@link UserGene} model.
     * <p>
     * There is no authorization check performed, so make sure that you reuse anonymizeUserGene before presenting the
     * data in the view.
     *
     * @param anonymousId
     * @return the user's gene if found, otherwise null
     */
    @Override
    public UserGene findUserGeneByAnonymousIdNoAuth( UUID anonymousId ) {
        return cacheManager.getCache( USER_GENES_BY_ANONYMOUS_ID_CACHE_KEY ).get( anonymousId, UserGene.class );
    }

    @Override
    public User findUserByIdNoAuth( int id ) {
        // Only use this in placed where no authentication of user is needed
        return userRepository.findOneWithRoles( id );
    }

    @Override
    public User findUserByEmailNoAuth( String email ) {
        return userRepository.findByEmailIgnoreCase( email );
    }

    @Override
    public User findUserByAccessTokenNoAuth( String accessToken ) throws TokenException {
        AccessToken token = accessTokenRepository.findByToken( accessToken );
        if ( token == null ) {
            return null;
        }
        if ( Instant.now().isAfter( token.getExpiryDate().toInstant() ) ) {
            // token is expired
            throw new TokenException( "Token is expired." );
        }
        return token.getUser();
    }

    @Override
    @PostAuthorize("hasPermission(returnObject, 'read')")
    public User anonymizeUser( User user ) {
        Profile profile = Profile.builder()
                .name( messageSource.getMessage( "rdp.site.anonymized-user-name", new String[]{}, Locale.getDefault() ) )
                .privacyLevel( applicationSettings.getPrivacy().isEnableAnonymizedSearchResults() ? PrivacyLevelType.PUBLIC : PrivacyLevelType.PRIVATE )
                .shared( true )
                .build();
        profile.getResearcherCategories().addAll( user.getProfile().getResearcherCategories() );
        User anonymizedUser = User.builder()
                .id( 0 )
                .anonymousId( UUID.randomUUID() )
                .profile( profile )
                .build();
        // TODO: check if this is leaking too much personal information
        anonymizedUser.getUserOrgans().putAll( user.getUserOrgans() );
        cacheManager.getCache( USERS_BY_ANONYMOUS_ID_CACHE_KEY ).put( anonymizedUser.getAnonymousId(), user );
        return anonymizedUser;
    }

    @Override
    @PostAuthorize("hasPermission(returnObject, 'read')")
    public UserGene anonymizeUserGene( UserGene userGene ) {
        UserGene anonymizedUserGene = UserGene.builder()
                .id( 0 )
                .anonymousId( UUID.randomUUID() )
                .user( anonymizeUser( userGene.getUser() ) )
                .geneInfo( userGene.getGeneInfo() )
                .privacyLevel( applicationSettings.getPrivacy().isEnableAnonymizedSearchResults() ? PrivacyLevelType.PUBLIC : PrivacyLevelType.PRIVATE )
                .tier( userGene.getTier() )
                .build();
        anonymizedUserGene.updateGene( userGene );
        cacheManager.getCache( USER_GENES_BY_ANONYMOUS_ID_CACHE_KEY ).put( anonymizedUserGene.getAnonymousId(), userGene );
        return anonymizedUserGene;
    }

    @Override
    public void revokeAccessToken( AccessToken accessToken ) {
        accessTokenRepository.delete( accessToken );
    }

    @Override
    public AccessToken createAccessTokenForUser( User user ) {
        AccessToken token = new AccessToken();
        token.updateToken( createSecureRandomToken() );
        token.setUser( user );
        return accessTokenRepository.save( token );
    }

    @Override
    public User getRemoteAdmin() {
        return userRepository.findOneWithRoles( applicationSettings.getIsearch().getUserId() );
    }

    @Override
    @PostFilter("hasPermission(filterObject, 'read')")
    public Collection<User> findAll() {
        return userRepository.findAll();
    }

    @Override
    public Page<User> findAllNoAuth( Pageable pageable ) {
        return userRepository.findAll( pageable );
    }

    @Override
    public Page<User> findAllByPrivacyLevel( PrivacyLevelType privacyLevel, Pageable pageable ) {
        return userRepository.findAllByProfilePrivacyLevel( privacyLevel, pageable );
    }

    @Override
    @PostFilter("hasPermission(filterObject, 'read')")
    public Collection<User> findByLikeName( String nameLike, Set<ResearcherPosition> researcherPositions, Set<ResearcherCategory> researcherTypes, Collection<UserOrgan> userOrgans ) {
        return userRepository.findByProfileNameContainingIgnoreCaseOrProfileLastNameContainingIgnoreCase( nameLike, nameLike ).stream()
                .filter( u -> researcherPositions == null || researcherPositions.contains( u.getProfile().getResearcherPosition() ) )
                .filter( u -> researcherTypes == null || containsAny( researcherTypes, u.getProfile().getResearcherCategories() ) )
                .filter( u -> userOrgans == null || containsAny( userOrgans, u.getUserOrgans().values() ) )
                .collect( Collectors.toSet() );
    }

    @Override
    @PostFilter("hasPermission(filterObject, 'read')")
    public Collection<User> findByStartsName( String startsName, Set<ResearcherPosition> researcherPositions, Set<ResearcherCategory> researcherTypes, Collection<UserOrgan> userOrgans ) {
        return userRepository.findByProfileLastNameStartsWithIgnoreCase( startsName ).stream()
                .filter( u -> researcherPositions == null || researcherPositions.contains( u.getProfile().getResearcherPosition() ) )
                .filter( u -> researcherTypes == null || containsAny( researcherTypes, u.getProfile().getResearcherCategories() ) )
                .filter( u -> userOrgans == null || containsAny( userOrgans, u.getUserOrgans().values() ) )
                .collect( Collectors.toSet() );
    }

    @Override
    @PostFilter("hasPermission(filterObject, 'read')")
    public Collection<User> findByDescription( String descriptionLike, Set<ResearcherPosition> researcherPositions, Collection<ResearcherCategory> researcherTypes, Collection<UserOrgan> userOrgans ) {
        return userRepository.findByProfileDescriptionContainingIgnoreCaseOrTaxonDescriptionsContainingIgnoreCase( descriptionLike, descriptionLike ).stream()
                .filter( u -> researcherPositions == null || researcherPositions.contains( u.getProfile().getResearcherPosition() ) )
                .filter( u -> researcherTypes == null || containsAny( researcherTypes, u.getProfile().getResearcherCategories() ) )
                .filter( u -> userOrgans == null || containsAny( userOrgans, u.getUserOrgans().values() ) )
                .collect( Collectors.toSet() );
    }

    @Override
    public long countResearchers() {
        return userRepository.count();
    }

    @Override
    public long countPublicResearchers() {
        return userRepository.countByProfilePrivacyLevel( PrivacyLevelType.PUBLIC );
    }

    @Override
    @PostAuthorize("hasPermission(returnObject, 'read')")
    public UserTerm convertTerm( User user, Taxon taxon, GeneOntologyTermInfo term ) {
        UserTerm ut = UserTerm.createUserTerm( user, term, taxon );
        ut.setFrequency( computeTermFrequencyInTaxon( user, term, taxon ) );
        ut.setSize( goService.getSizeInTaxon( term, taxon ) );
        return ut;
    }

    @Override
    @PostFilter("hasPermission(filterObject, 'read')")
    public Collection<UserTerm> convertTerms( User user, Taxon taxon, Collection<GeneOntologyTermInfo> terms ) {
        return terms.stream()
                .map( term -> convertTerm( user, taxon, term ) )
                .collect( Collectors.toSet() );
    }

    @Override
    @PostFilter("hasPermission(filterObject, 'read')")
    public Collection<UserTerm> recommendTerms( @NonNull User user, @NonNull Taxon taxon ) {
        return recommendTerms( user, taxon, 10, applicationSettings.getGoTermSizeLimit(), 2 );
    }

    @Override
    @PostFilter("hasPermission(filterObject, 'read')")
    public Collection<UserTerm> recommendTerms( @NonNull User user, @NonNull Taxon taxon, long minSize, long maxSize, long minFrequency ) {
        Set<UserGene> genes = new HashSet<>( user.getGenesByTaxonAndTier( taxon, TierType.MANUAL ) );

        // terms already associated to user within the taxon
        Set<String> userTermGoIds = user.getUserTerms().stream()
                .filter( ut -> ut.getTaxon().equals( taxon ) )
                .map( UserTerm::getGoId )
                .collect( Collectors.toSet() );

        // Then keep only those terms not already added and with the highest frequency
        Set<GeneOntologyTermInfo> topResults = goService.termFrequencyMap( genes ).entrySet().stream()
                .filter( e -> minFrequency < 0 || e.getValue() >= minFrequency )
                .filter( e -> minSize < 0 || goService.getSizeInTaxon( e.getKey(), taxon ) >= minSize )
                .filter( e -> maxSize < 0 || goService.getSizeInTaxon( e.getKey(), taxon ) <= maxSize )
                .filter( e -> !userTermGoIds.contains( e.getKey().getGoId() ) )
                .map( Map.Entry::getKey )
                .collect( Collectors.toSet() );

        // Keep only leafiest of remaining terms (keep if it has no descendants in results)
        return topResults.stream()
                .filter( term -> Collections.disjoint( topResults, goService.getDescendants( term ) ) )
                .filter( term -> goService.getSizeInTaxon( term, taxon ) <= applicationSettings.getGoTermSizeLimit() )
                .map( term -> convertTerm( user, taxon, term ) )
                .collect( Collectors.toSet() );
    }

    @Transactional
    @Override
    @PreAuthorize("hasPermission(#user, 'update')")
    public User updateTermsAndGenesInTaxon( User user,
                                            Taxon taxon,
                                            Map<GeneInfo, TierType> genesToTierMap,
                                            Map<GeneInfo, PrivacyLevelType> genesToPrivacyLevelMap,
                                            Collection<GeneOntologyTermInfo> goTerms ) {
        Map<Integer, UserGene> userGenes = genesToTierMap.keySet()
                .stream()
                .filter( g -> g.getTaxon().equals( taxon ) )
                .map( g -> {
                    UserGene userGene = user.getUserGenes().getOrDefault( g.getGeneId(), new UserGene() );
                    userGene.setUser( user );
                    userGene.setTier( genesToTierMap.get( g ) );
                    if ( applicationSettings.getPrivacy().isCustomizableGeneLevel() ) {
                        // if no privacy level is set, we inherit the profile value
                        PrivacyLevelType privacyLevel = genesToPrivacyLevelMap.getOrDefault( g, null );
                        if ( privacyLevel == null || privacyService.isGenePrivacyLevelEnabled( privacyLevel ) ) {
                            userGene.setPrivacyLevel( privacyLevel );
                        } else {
                            log.warn( MessageFormat.format( "{0} attempted to set {1} privacy level to a value that is not enabled: {2}. The new value was ignored.", findCurrentUser(), g, privacyLevel ) );
                        }
                    }
                    userGene.updateGene( g );
                    return userGene;
                } )
                .collect( Collectors.toMap( Gene::getGeneId, identity() ) );

        // add calculated genes from terms
        Map<Integer, UserGene> userGenesFromTerms = goTerms.stream()
                .flatMap( term -> goService.getGenesInTaxon( term, taxon ).stream() )
                .distinct() // terms might refer to the same gene
                .map( geneInfoService::load )
                .filter( Objects::nonNull )
                .map( g -> {
                    UserGene userGene = user.getUserGenes().getOrDefault( g.getGeneId(), new UserGene() );
                    userGene.setUser( user );
                    userGene.setTier( genesToTierMap.getOrDefault( g, TierType.TIER3 ) );
                    // we let the privacy level inherit the profile value
                    userGene.updateGene( g );
                    return userGene;
                } )
                .collect( Collectors.toMap( Gene::getGeneId, identity() ) );

        // remove all genes in the taxon
        user.getUserGenes().entrySet()
                .removeIf( e -> e.getValue().getTaxon().equals( taxon ) );

        user.getUserGenes().putAll( userGenes );
        user.getUserGenes().putAll( userGenesFromTerms );

        // update terms
        // go terms with the same identifier will be replaced
        Collection<UserTerm> userTerms = convertTerms( user, taxon, goTerms );
        user.getUserTerms().removeIf( e -> e.getTaxon().equals( taxon ) && !userTerms.contains( e ) );
        user.getUserTerms().addAll( userTerms );

        // update frequency and size as those have likely changed with new genes
        for ( UserTerm userTerm : user.getUserTerms() ) {
            GeneOntologyTermInfo cachedTerm = goService.getTerm( userTerm.getGoId() );
            userTerm.setFrequency( computeTermFrequencyInTaxon( user, cachedTerm, taxon ) );
            userTerm.setSize( goService.getSizeInTaxon( cachedTerm, taxon ) );
        }

        return update( user );
    }

    @Override
    public long computeTermOverlaps( UserTerm userTerm, Collection<GeneInfo> genes ) {
        return genes.stream()
                .flatMap( g -> goService.getTermsForGene( g, true ).stream() )
                .filter( term -> term.getGoId().equals( userTerm.getGoId() ) )
                .count();
    }

    /**
     * Compute the number of TIER1/TIER2 user genes that are associated to a given ontology term.
     *
     * @param user
     * @param term
     * @param taxon
     * @return
     */
    @Override
    public long computeTermFrequencyInTaxon( User user, GeneOntologyTerm term, Taxon taxon ) {
        Set<Integer> geneIds = goService.getGenes( goService.getTerm( term.getGoId() ) ).stream().collect( Collectors.toSet() );
        return user.getGenesByTaxonAndTier( taxon, TierType.MANUAL ).stream()
                .map( UserGene::getGeneId )
                .filter( geneIds::contains )
                .count();
    }

    @Transactional
    @Override
    @PreAuthorize("hasPermission(#user, 'update')")
    public User updateUserProfileAndPublicationsAndOrgans( User user, Profile profile, Set<Publication> publications, Set<String> organUberonIds ) {
        user.getProfile().setDepartment( profile.getDepartment() );
        user.getProfile().setDescription( profile.getDescription() );
        user.getProfile().setLastName( profile.getLastName() );
        user.getProfile().setName( profile.getName() );
        if ( profile.getResearcherPosition() == null || applicationSettings.getProfile().getEnabledResearcherPositions().contains( profile.getResearcherPosition().name() ) ) {
            user.getProfile().setResearcherPosition( profile.getResearcherPosition() );
        } else {
            log.warn( MessageFormat.format( "User {0} attempted to set user {1} researcher position to an unknown value {2}.",
                    findCurrentUser(), user, profile.getResearcherPosition() ) );
        }
        user.getProfile().setResearcherPosition( profile.getResearcherPosition() );
        user.getProfile().setOrganization( profile.getOrganization() );

        if ( profile.getResearcherCategories() != null ) {
            Set<String> researcherCategoryNames = profile.getResearcherCategories().stream()
                    .map( ResearcherCategory::name )
                    .collect( Collectors.toSet() );
            if ( applicationSettings.getProfile().getEnabledResearcherCategories().containsAll( researcherCategoryNames ) ) {
                user.getProfile().getResearcherCategories().retainAll( profile.getResearcherCategories() );
                user.getProfile().getResearcherCategories().addAll( profile.getResearcherCategories() );
            } else {
                log.warn( MessageFormat.format( "User {0} attempted to set user {1} researcher type to an unknown value {2}.",
                        findCurrentUser(), user, profile.getResearcherCategories() ) );
            }
        }

        if ( user.getProfile().getContactEmail() == null ||
                !user.getProfile().getContactEmail().equals( profile.getContactEmail() ) ) {
            user.getProfile().setContactEmail( profile.getContactEmail() );
            if ( user.getProfile().getContactEmail() != null && !user.getProfile().getContactEmail().isEmpty() ) {
                if ( user.getProfile().getContactEmail().equals( user.getEmail() ) ) {
                    // if the contact email is set to the user email, it's de facto verified
                    user.getProfile().setContactEmailVerified( true );
                } else {
                    user.getProfile().setContactEmailVerified( false );
                    VerificationToken token = createContactEmailVerificationTokenForUser( user );
                    eventPublisher.publishEvent( new OnContactEmailUpdateEvent( user, token ) );
                }
            } else {
                // contact email is unset, so we don't need to send a confirmation
                user.getProfile().setContactEmailVerified( false );
            }
        }

        user.getProfile().setPhone( profile.getPhone() );
        user.getProfile().setWebsite( profile.getWebsite() );

        // privacy settings
        if ( applicationSettings.getPrivacy().isCustomizableLevel() ) {
            if ( privacyService.isPrivacyLevelEnabled( profile.getPrivacyLevel() ) ) {
                // reset gene privacy levels if the profile value is changed
                if ( applicationSettings.getPrivacy().isCustomizableGeneLevel() &&
                        user.getProfile().getPrivacyLevel() != profile.getPrivacyLevel() ) {
                    // reset gene-level privacy when profile is updated
                    user.getUserGenes().values().forEach( ug -> ug.setPrivacyLevel( profile.getPrivacyLevel() ) );
                }
                user.getProfile().setPrivacyLevel( profile.getPrivacyLevel() );
            } else {
                log.warn( MessageFormat.format( "{0} attempted to set {1} its profile privacy level to a disabled value: {2}. The new value was ignored.",
                        findCurrentUser(), user, profile.getPrivacyLevel() ) );
            }
        }
        if ( applicationSettings.getPrivacy().isCustomizableSharing() ) {
            user.getProfile().setShared( profile.isShared() );
        }
        if ( applicationSettings.getPrivacy().isAllowHideGenelist() ) {
            user.getProfile().setHideGenelist( profile.isHideGenelist() );
        }

        if ( publications != null ) {
            user.getProfile().getPublications().retainAll( publications );
            user.getProfile().getPublications().addAll( publications );
        }

        if ( applicationSettings.getOrgans().getEnabled() ) {
            Map<String, UserOrgan> userOrgans = organInfoService.findByUberonIdIn( organUberonIds ).stream()
                    .map( organInfo -> user.getUserOrgans().getOrDefault( organInfo.getUberonId(), UserOrgan.createFromOrganInfo( user, organInfo ) ) )
                    .collect( Collectors.toMap( Organ::getUberonId, identity() ) );
            user.getUserOrgans().clear();
            user.getUserOrgans().putAll( userOrgans );
        }

        return update( user );
    }

    @Transactional
    @Override
    public PasswordResetToken createPasswordResetTokenForUser( User user ) {
        PasswordResetToken userToken = new PasswordResetToken();
        userToken.setUser( user );
        userToken.updateToken( createSecureRandomToken() );
        return passwordResetTokenRepository.save( userToken );
    }

    @Override
    public PasswordResetToken verifyPasswordResetToken( int userId, String token ) throws TokenException {
        PasswordResetToken passToken = passwordResetTokenRepository.findByToken( token );

        if ( passToken == null ) {
            throw new TokenException( "Password reset token is invalid." );
        }

        if ( !passToken.getUser().getId().equals( userId ) ) {
            throw new TokenException( "Password reset token is invalid." );
        }

        if ( Instant.now().isAfter( passToken.getExpiryDate().toInstant() ) ) {
            throw new TokenException( "Password reset token is expired." );
        }

        return passToken;
    }

    @Transactional(rollbackFor = { TokenException.class })
    @Override
    public User changePasswordByResetToken( int userId, String token, PasswordReset passwordReset ) throws TokenException, ValidationException {

        PasswordResetToken passToken = verifyPasswordResetToken( userId, token );

        // Preauthorize might cause trouble here if implemented, fix by setting manual authentication
        User user = findUserByIdNoAuth( userId );

        user.setPassword( bCryptPasswordEncoder.encode( passwordReset.getNewPassword() ) );

        passwordResetTokenRepository.delete( passToken );

        return userRepository.save( user );
    }

    @Transactional
    @Override
    public VerificationToken createVerificationTokenForUser( User user ) {
        VerificationToken userToken = new VerificationToken();
        userToken.setUser( user );
        userToken.setEmail( user.getEmail() );
        userToken.updateToken( createSecureRandomToken() );
        return tokenRepository.save( userToken );
    }

    @Transactional
    @Override
    public VerificationToken createContactEmailVerificationTokenForUser( User user ) {
        VerificationToken userToken = new VerificationToken();
        userToken.setUser( user );
        userToken.setEmail( user.getProfile().getContactEmail() );
        userToken.updateToken( createSecureRandomToken() );
        return tokenRepository.save( userToken );
    }

    @Override
    @Transactional(rollbackFor = { TokenException.class })
    public User confirmVerificationToken( String token ) throws TokenException {
        VerificationToken verificationToken = tokenRepository.findByToken( token );
        if ( verificationToken == null ) {
            throw new TokenException( "Verification token is invalid." );
        }

        if ( Instant.now().isAfter( verificationToken.getExpiryDate().toInstant() ) ) {
            throw new TokenException( "Verification token is expired." );
        }

        User user = verificationToken.getUser();

        boolean tokenUsed = false;

        if ( verificationToken.getEmail().equals( user.getEmail() ) ) {
            user.setEnabled( true );
            tokenUsed = true;
        }

        if ( user.getProfile().getContactEmail() != null && verificationToken.getEmail().equals( user.getProfile().getContactEmail() ) ) {
            user.getProfile().setContactEmailVerified( true );
            tokenUsed = true;
        }

        if ( tokenUsed ) {
            tokenRepository.delete( verificationToken );
            return userRepository.save( user );
        } else {
            throw new TokenException( "Verification token email does not match neither the user email nor contact email." );
        }
    }

    @PostFilter("hasPermission(filterObject, 'read')")
    private Collection<User> findAllWithNonEmptyProfileLastName() {
        return userRepository.findAllWithNonEmptyProfileLastName();
    }

    @Override
    public SortedSet<String> getLastNamesFirstChar() {
        return findAllWithNonEmptyProfileLastName()
                .stream()
                .map( u -> u.getProfile().getLastName().substring( 0, 1 ).toUpperCase() )
                .filter( StringUtils::isAlpha )
                .collect( Collectors.toCollection( TreeSet::new ) );
    }

    @Override
    @Transactional
    public void updateUserTerms() {
        log.info( "Updating user terms..." );
        for ( User user : userRepository.findAllWithUserTerms() ) {
            for ( UserTerm userTerm : user.getUserTerms() ) {
                GeneOntologyTermInfo cachedTerm = goService.getTerm( userTerm.getGoId() );
                if ( cachedTerm == null ) {
                    log.warn( MessageFormat.format( "User has a reference to a GO term missing from the cache: {0}.", userTerm ) );
                    continue;
                }
                userTerm.updateTerm( cachedTerm );
                userTerm.setFrequency( computeTermFrequencyInTaxon( user, cachedTerm, userTerm.getTaxon() ) );
                userTerm.setSize( goService.getSizeInTaxon( cachedTerm, userTerm.getTaxon() ) );
            }
            userRepository.save( user );
        }
        log.info( "Done updating user terms." );
    }

    private String createSecureRandomToken() {
        byte tokenBytes[] = new byte[24];
        secureRandom.nextBytes( tokenBytes );
        return Base64.getEncoder().encodeToString( tokenBytes );
    }
}
