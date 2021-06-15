package ubc.pavlab.rdp.controllers;

import org.assertj.core.util.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import ubc.pavlab.rdp.WebSecurityConfig;
import ubc.pavlab.rdp.events.OnRequestAccessEvent;
import ubc.pavlab.rdp.exception.RemoteException;
import ubc.pavlab.rdp.listeners.UserListener;
import ubc.pavlab.rdp.model.*;
import ubc.pavlab.rdp.model.enums.PrivacyLevelType;
import ubc.pavlab.rdp.model.enums.ResearcherCategory;
import ubc.pavlab.rdp.model.enums.TierType;
import ubc.pavlab.rdp.services.*;
import ubc.pavlab.rdp.settings.ApplicationSettings;
import ubc.pavlab.rdp.settings.SiteSettings;

import java.net.URI;
import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static ubc.pavlab.rdp.util.TestUtils.*;

@RunWith(SpringRunner.class)
@WebMvcTest(SearchController.class)
@Import(WebSecurityConfig.class)
public class SearchControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean(name = "applicationSettings")
    private ApplicationSettings applicationSettings;

    @MockBean
    private ApplicationSettings.ProfileSettings profileSettings;

    @MockBean
    private ApplicationSettings.PrivacySettings privacySettings;

    @MockBean(name = "siteSettings")
    private SiteSettings siteSettings;

    @MockBean
    private ApplicationSettings.OrganSettings organSettings;

    @MockBean
    private ApplicationSettings.InternationalSearchSettings iSearchSettings;

    @MockBean(name = "userService")
    private UserService userService;

    @MockBean(name = "taxonService")
    private TaxonService taxonService;

    @MockBean(name = "geneService")
    private GeneInfoService geneService;

    @MockBean(name = "userGeneService")
    private UserGeneService userGeneService;

    @MockBean(name = "userOrganService")
    private UserOrganService userOrganService;

    @MockBean(name = "tierService")
    private TierService tierService;

    @MockBean
    private RemoteResourceService remoteResourceService;

    @MockBean
    private PrivacyService privacyService;

    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private BCryptPasswordEncoder bCryptPasswordEncoder;

    @MockBean
    private PermissionEvaluator permissionEvaluator;

    @MockBean(name = "organInfoService")
    private OrganInfoService organInfoService;

    @MockBean
    private UserListener userListener;

    @Before
    public void setUp() {
        when( applicationSettings.getEnabledTiers() ).thenReturn( Lists.newArrayList( "TIER1", "TIER2", "TIER3" ) );
        when( applicationSettings.getPrivacy() ).thenReturn( privacySettings );
        when( applicationSettings.getProfile() ).thenReturn( profileSettings );
        when( profileSettings.getEnabledResearcherCategories() ).thenReturn( Lists.newArrayList( "TIER1", "TIER2", "TIER3" ) );
        when( profileSettings.getEnabledResearcherPositions() ).thenReturn( Lists.newArrayList( "PRINCIPAL_INVESTIGATOR" ) );
        when( applicationSettings.getOrgans() ).thenReturn( organSettings );
        when( applicationSettings.getIsearch() ).thenReturn( iSearchSettings );
    }

    @Test
    public void getSearch_return200() throws Exception {
        when( permissionEvaluator.hasPermission( any(), isNull(), eq( "search" ) ) ).thenReturn( true );
        mvc.perform( get( "/search" ) )
                .andExpect( status().isOk() )
                .andExpect( view().name( "search" ) );
    }

    @Test
    public void getSearch_withoutPublicSearch_redirect3xx() throws Exception {
        when( permissionEvaluator.hasPermission( any(), isNull(), eq( "search" ) ) ).thenReturn( false );
        mvc.perform( get( "/search" ) )
                .andExpect( status().is3xxRedirection() )
                .andExpect( redirectedUrl( "http://localhost/login" ) );
    }

    @Test
    public void getSearch_ByNameLike_return200() throws Exception {
        when( permissionEvaluator.hasPermission( any(), isNull(), eq( "search" ) ) ).thenReturn( true );
        mvc.perform( get( "/search" )
                .param( "nameLike", "K" )
                .param( "prefix", "true" )
                .param( "iSearch", "false" ) )
                .andExpect( status().isOk() )
                .andExpect( view().name( "search" ) )
                .andExpect( model().attributeExists( "users" ) );
    }

    @Test
    public void getSearch_ByDescriptionLike_return200() throws Exception {
        when( permissionEvaluator.hasPermission( any(), isNull(), eq( "search" ) ) ).thenReturn( true );
        mvc.perform( get( "/search" )
                .param( "descriptionLike", "pancake" )
                .param( "iSearch", "false" ) )
                .andExpect( status().isOk() )
                .andExpect( view().name( "search" ) )
                .andExpect( model().attributeExists( "users" ) );
    }

    @Test
    public void getSearch_ByGeneSymbol_return200() throws Exception {
        when( permissionEvaluator.hasPermission( any(), isNull(), eq( "search" ) ) ).thenReturn( true );
        Taxon humanTaxon = createTaxon( 9606 );
        GeneInfo gene = createGene( 1, humanTaxon );
        when( taxonService.findById( 9606 ) ).thenReturn( humanTaxon );
        when( geneService.findBySymbolAndTaxon( "BRCA1", humanTaxon ) ).thenReturn( gene );
        mvc.perform( get( "/search" )
                .param( "symbol", "BRCA1" )
                .param( "taxonId", "9606" )
                .param( "iSearch", "false" ) )
                .andExpect( status().isOk() )
                .andExpect( view().name( "search" ) )
                .andExpect( model().attributeExists( "usergenes" ) );
        verify( userGeneService ).handleGeneSearch( gene, TierType.ANY, null, null, null, null );
    }

    @Test
    public void viewUser_thenReturnSuccess() throws Exception {
        User user = createUser( 1 );
        when( userService.findUserById( user.getId() ) ).thenReturn( user );
        mvc.perform( get( "/userView/{userId}", user.getId() ) )
                .andExpect( status().isOk() )
                .andExpect( view().name( "userView" ) );
        verify( userService ).findUserById( user.getId() );
    }

    @Test
    public void viewUser_whenUserIsNotFound_thenReturnSuccess() throws Exception {
        User user = createUser( 1 );
        when( userService.findUserById( user.getId() ) ).thenReturn( null );
        mvc.perform( get( "/userView/{userId}", user.getId() ) )
                .andExpect( status().isNotFound() );
        verify( userService ).findUserById( user.getId() );
    }

    @Test
    public void viewUser_whenUserIsRemote_thenReturnSuccess() throws Exception {
        User user = createUser( 1 );
        when( remoteResourceService.getRemoteUser( user.getId(), URI.create( "example.com" ) ) ).thenReturn( user );
        when( privacyService.checkCurrentUserCanSearch( true ) ).thenReturn( true );
        mvc.perform( get( "/userView/{userId}", user.getId() )
                .param( "remoteHost", "example.com" ) )
                .andExpect( status().isOk() )
                .andExpect( view().name( "userView" ) );
        verify( remoteResourceService ).getRemoteUser( user.getId(), URI.create( "example.com" ) );
    }

    @Test
    public void viewUser_whenRemoteUserIsNotFound_thenReturnNotFound() throws Exception {
        when( remoteResourceService.getRemoteUser( 1, URI.create( "example.com" ) ) ).thenReturn( null );
        when( privacyService.checkCurrentUserCanSearch( true ) ).thenReturn( true );
        mvc.perform( get( "/userView/{userId}", 1 )
                .param( "remoteHost", "example.com" ) )
                .andExpect( status().isNotFound() );
        verify( remoteResourceService ).getRemoteUser( 1, URI.create( "example.com" ) );
    }

    @Test
    public void viewUser_whenRemoteIsUnavailable_thenReturnNotFound() throws Exception {
        when( remoteResourceService.getRemoteUser( 1, URI.create( "example.com" ) ) ).thenThrow( RemoteException.class );
        when( privacyService.checkCurrentUserCanSearch( true ) ).thenReturn( true );
        mvc.perform( get( "/userView/{userId}", 1 )
                .param( "remoteHost", "example.com" ) )
                .andExpect( status().isServiceUnavailable() );
        verify( remoteResourceService ).getRemoteUser( 1, URI.create( "example.com" ) );
    }

    @Test
    public void searchItlUsersByNameView_thenReturnSuccess() throws Exception {
        User user = createUser( 1 );
        when( permissionEvaluator.hasPermission( any(), isNull(), eq( "international-search" ) ) ).thenReturn( true );
        when( remoteResourceService.findUsersByLikeName( "Mark", true, null, null, null ) )
                .thenReturn( Collections.singleton( user ) );
        mvc.perform( get( "/search/view/international" )
                .param( "nameLike", "Mark" )
                .param( "prefix", "true" ) )
                .andExpect( status().isOk() )
                .andExpect( view().name( "fragments/user-table::user-table" ) );
    }

    @Test
    public void viewUser_whenRemoteUserCannotBeRetrieved_thenReturnNotFound() throws Exception {
        User user = createUser( 1 );
        when( remoteResourceService.getRemoteUser( user.getId(), URI.create( "example.com" ) ) ).thenThrow( RemoteException.class );
        when( privacyService.checkCurrentUserCanSearch( true ) ).thenReturn( true );
        mvc.perform( get( "/userView/{userId}", user.getId() )
                .param( "remoteHost", "example.com" ) )
                .andExpect( status().isServiceUnavailable() );
        verify( remoteResourceService ).getRemoteUser( user.getId(), URI.create( "example.com" ) );
    }

    @Test
    public void previewUser_thenReturnSuccess() throws Exception {
        User user = createUser( 1 );
        user.getProfile().setDescription( "This is a description." );
        when( userService.findUserById( 1 ) ).thenReturn( user );
        mvc.perform( get( "/search/view/user-preview/{userId}", user.getId() ) )
                .andExpect( status().isOk() )
                .andExpect( view().name( "fragments/profile::user-preview" ) )
                .andExpect( model().attribute( "user", user ) );
    }

    @Test
    public void previewUser_whenUserProfileIsEmpty_thenReturnNoContent() throws Exception {
        User user = createUser( 1 );
        when( userService.findUserById( 1 ) ).thenReturn( user );
        mvc.perform( get( "/search/view/user-preview/{userId}", user.getId() ) )
                .andExpect( status().isNoContent() );
    }

    @Test
    public void previewUser_whenUserIsRemote_thenReturnSuccess() throws Exception {
        User user = createUser( 1 );
        user.getProfile().setDescription( "This is a description." );
        when( userService.findUserById( 1 ) ).thenReturn( user );
        when( remoteResourceService.getRemoteUser( 1, URI.create( "http://localhost/" ) ) ).thenReturn( user );
        mvc.perform( get( "/search/view/user-preview/{userId}", user.getId() )
                .param( "remoteHost", "http://localhost/" ) )
                .andExpect( status().isOk() );
        verify( remoteResourceService ).getRemoteUser( 1, URI.create( "http://localhost/" ) );
    }

    @Test
    public void previewAnonymousUser_thenReturnSuccess() throws Exception {
        User user = createUser( 1 );
        User anonymousUser = createAnonymousUser();
        anonymousUser.getProfile().getResearcherCategories().add( ResearcherCategory.IN_SILICO );
        when( userService.findUserByAnonymousIdNoAuth( anonymousUser.getAnonymousId() ) ).thenReturn( user );
        when( userService.anonymizeUser( user ) ).thenReturn( anonymousUser );
        mvc.perform( get( "/search/view/user-preview/by-anonymous-id/{anonymousId}", anonymousUser.getAnonymousId() ) )
                .andExpect( status().isOk() )
                .andExpect( view().name( "fragments/profile::user-preview" ) )
                .andExpect( model().attribute( "user", anonymousUser ) );
        verify( userService ).anonymizeUser( user );
    }

    @Test
    public void previewAnonymousUser_whenUserProfileIsEmpty_thenReturnNoContent() throws Exception {
        User user = createUser( 1 );
        User anonymousUser = createAnonymousUser();
        when( userService.findUserByAnonymousIdNoAuth( anonymousUser.getAnonymousId() ) ).thenReturn( user );
        when( userService.anonymizeUser( user ) ).thenReturn( anonymousUser );
        mvc.perform( get( "/search/view/user-preview/by-anonymous-id/{anonymousId}", anonymousUser.getAnonymousId() ) )
                .andExpect( status().isNoContent() )
                .andExpect( view().name( "fragments/profile::user-preview" ) )
                .andExpect( model().attribute( "user", anonymousUser ) );
        verify( userService ).anonymizeUser( user );
    }

    @Test
    public void previewAnonymousUser_whenUserIsRemote_thenReturnSuccess() throws Exception {
        User anonymousUser = createAnonymousUser();
        anonymousUser.getProfile().getResearcherCategories().add( ResearcherCategory.IN_SILICO );
        when( remoteResourceService.getApiVersion( URI.create( "http://localhost/" ) ) ).thenReturn( "1.4.0" );
        when( remoteResourceService.getAnonymizedUser( anonymousUser.getAnonymousId(), URI.create( "http://localhost/" ) ) ).thenReturn( anonymousUser );
        mvc.perform( get( "/search/view/user-preview/by-anonymous-id/{anonymousId}", anonymousUser.getAnonymousId() )
                .param( "remoteHost", "http://localhost/" ) )
                .andExpect( status().isOk() )
                .andExpect( view().name( "fragments/profile::user-preview" ) )
                .andExpect( model().attribute( "user", anonymousUser ) );
        verify( remoteResourceService ).getAnonymizedUser( anonymousUser.getAnonymousId(), URI.create( "http://localhost/" ) );
    }

    @Test
    public void previewAnonymousUser_whenUserIsRemoteAndApiVersionIsPre14_thenReturnNotFound() throws Exception {
        User anonymousUser = createAnonymousUser();
        anonymousUser.getProfile().getResearcherCategories().add( ResearcherCategory.IN_SILICO );
        when( remoteResourceService.getApiVersion( URI.create( "http://localhost/" ) ) ).thenReturn( "1.0.0" );
        mvc.perform( get( "/search/view/user-preview/by-anonymous-id/{anonymousId}", anonymousUser.getAnonymousId() )
                .param( "remoteHost", "http://localhost/" ) )
                .andExpect( status().isNotFound() )
                .andExpect( view().name( "fragments/error::message" ) );
    }

    @Test
    @WithMockUser
    public void requestAccess_thenReturnSuccess() throws Exception {
        User user = createUser( 1 );
        when( userService.findCurrentUser() ).thenReturn( user );
        Taxon taxon = createTaxon( 1 );
        GeneInfo gene = createGene( 1, taxon );
        UserGene userGene = createUserGene( 1, gene, createUser( 2 ), TierType.TIER1, PrivacyLevelType.PRIVATE );
        UserGene anonymizedUserGene = UserGene.builder()
                .anonymousId( UUID.randomUUID() )
                .user( User.builder().profile( new Profile() ).build() )
                .build();
        when( userService.anonymizeUserGene( userGene ) ).thenReturn( anonymizedUserGene );
        when( userService.findUserGeneByAnonymousIdNoAuth( anonymizedUserGene.getAnonymousId() ) ).thenReturn( userGene );

        mvc.perform( get( "/search/gene/by-anonymous-id/{anonymousId}/request-access", anonymizedUserGene.getAnonymousId() ) )
                .andExpect( status().is2xxSuccessful() )
                .andExpect( model().attribute( "userGene", anonymizedUserGene ) );
        verify( userService ).anonymizeUserGene( userGene );

        mvc.perform( post( "/search/gene/by-anonymous-id/{anonymousId}/request-access", anonymizedUserGene.getAnonymousId() )
                .param( "reason", "Because." ) )
                .andExpect( status().is3xxRedirection() )
                .andExpect( redirectedUrl( "/search" ) )
                .andExpect( flash().attributeExists( "message" ) );
        ArgumentCaptor<OnRequestAccessEvent> captor = ArgumentCaptor.forClass( OnRequestAccessEvent.class );
        verify( userListener ).onGeneRequestAccess( captor.capture() );
        assertThat( captor.getValue() )
                .hasFieldOrPropertyWithValue( "user", user )
                .hasFieldOrPropertyWithValue( "object", userGene )
                .hasFieldOrPropertyWithValue( "reason", "Because." );
    }

    @Test
    @WithMockUser
    public void requestAccess_whenUserHasPermission_thenRedirectToUserProfile() throws Exception {
        User user = createUser( 1 );
        when( userService.findCurrentUser() ).thenReturn( user );
        Taxon taxon = createTaxon( 1 );
        GeneInfo gene = createGene( 1, taxon );
        UserGene userGene = createUserGene( 1, gene, createUser( 2 ), TierType.TIER1, PrivacyLevelType.PRIVATE );
        UserGene anonymizedUserGene = UserGene.builder()
                .anonymousId( UUID.randomUUID() )
                .user( User.builder().profile( new Profile() ).build() )
                .build();
        when( userService.anonymizeUserGene( userGene ) ).thenReturn( anonymizedUserGene );
        when( userService.findUserGeneByAnonymousIdNoAuth( anonymizedUserGene.getAnonymousId() ) ).thenReturn( userGene );

        when( permissionEvaluator.hasPermission( any(), eq( userGene ), eq( "read" ) ) ).thenReturn( true );

        mvc.perform( get( "/search/gene/by-anonymous-id/{anonymousId}/request-access", anonymizedUserGene.getAnonymousId() ) )
                .andExpect( status().is3xxRedirection() )
                .andExpect( redirectedUrl( "http://localhost/userView/2" ) )
                .andExpect( flash().attributeExists( "message" ) );
    }


}
