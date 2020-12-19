package ubc.pavlab.rdp.controllers;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.support.FormattingConversionService;
import org.springframework.http.MediaType;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import ubc.pavlab.rdp.WebSecurityConfig;
import ubc.pavlab.rdp.model.AccessToken;
import ubc.pavlab.rdp.model.Role;
import ubc.pavlab.rdp.model.User;
import ubc.pavlab.rdp.repositories.AccessTokenRepository;
import ubc.pavlab.rdp.repositories.RoleRepository;
import ubc.pavlab.rdp.services.TaxonService;
import ubc.pavlab.rdp.services.UserService;
import ubc.pavlab.rdp.settings.ApplicationSettings;
import ubc.pavlab.rdp.settings.SiteSettings;
import ubc.pavlab.rdp.util.TestUtils;

import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.when;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static ubc.pavlab.rdp.util.TestUtils.createRole;
import static ubc.pavlab.rdp.util.TestUtils.createUser;

/**
 * Created by mjacobson on 13/02/18.
 */
@RunWith(SpringRunner.class)
@WebMvcTest(AdminController.class)
@Import(WebSecurityConfig.class)
public class AdminControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private BCryptPasswordEncoder bCryptPasswordEncoder;

    @MockBean(name = "roleRepository")
    private RoleRepository roleRepository;

    @MockBean
    private AccessTokenRepository accessTokenRepository;

    @MockBean(name = "taxonService")
    private TaxonService taxonService;

    @MockBean(name = "userService")
    private UserService userService;

    @MockBean(name = "applicationSettings")
    private ApplicationSettings applicationSettings;

    @MockBean
    ApplicationSettings.PrivacySettings privacySettings;

    @MockBean(name = "siteSettings")
    private SiteSettings siteSettings;

    @MockBean
    private PermissionEvaluator permissionEvaluator;

    @Autowired
    private FormattingConversionService conversionService;

    private class UserIdToUserConverter implements Converter<String, User> {

        @Override
        public User convert( String s ) {
            return userService.findUserById( Integer.parseInt( s ) );
        }
    }

    private class AccessTokenIdToAccessTokenConverter implements Converter<String, AccessToken> {

        @Override
        public AccessToken convert( String s ) {
            return accessTokenRepository.findById( Integer.parseInt( s ) ).orElse( null );
        }
    }

    private class RoleIdToRoleConverter implements Converter<String, Role> {

        @Override
        public Role convert( String s ) {
            return roleRepository.findById( Integer.parseInt( s ) ).orElse( null );
        }
    }

    @Before
    public void setUp() {
        when( applicationSettings.getPrivacy() ).thenReturn( privacySettings );
        conversionService.addConverter( new UserIdToUserConverter() );
        conversionService.addConverter( new AccessTokenIdToAccessTokenConverter() );
        conversionService.addConverter( new RoleIdToRoleConverter() );
    }

    @Test
    @WithMockUser(roles = { "ADMIN" })
    public void givenLoggedIn_whenCreateServiceAccount_thenRedirect3xx() throws Exception {
        when( siteSettings.getHostUri() ).thenReturn( URI.create( "http://localhost/" ) );
        when( roleRepository.findById( 1 ) ).thenReturn( Optional.of( createRole( 1, "ROLE_USER" ) ) );
        when( roleRepository.findById( 2 ) ).thenReturn( Optional.of( createRole( 2, "ROLE_ADMIN" ) ) );
        when( userService.createServiceAccount( any() ) ).thenAnswer( answer -> {
            User createdUser = answer.getArgument( 0, User.class );
            createdUser.setId( 1 );
            return createdUser;
        } );
        mvc.perform( post( "/admin/create-service-account" )
                .param( "profile.name", "Service Account" )
                .param( "email", "service-account" ) )
                .andExpect( status().is3xxRedirection() )
                .andExpect( redirectedUrl( "/admin/users/1" ) );
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass( User.class );
        verify( userService ).createServiceAccount( captor.capture() );
        assertThat( captor.getValue() )
                .hasFieldOrPropertyWithValue( "profile.name", "Service Account" )
                .hasFieldOrPropertyWithValue( "email", "service-account@localhost" );
    }

    @Test
    @WithMockUser(roles = { "ADMIN" })
    public void givenLoggedIn_whenCreateAccessToken_thenRedirect3xx() throws Exception {
        User user = createUser( 1 );
        AccessToken accessToken = TestUtils.createAccessToken( 1, user, "1234" );
        when( userService.findUserById( 1 ) ).thenReturn( user );
        when( userService.createAccessTokenForUser( user ) ).thenReturn( accessToken );
        when( roleRepository.findByRole( "ROLE_USER" ) ).thenReturn( createRole( 1, "ROLE_USER" ) );
        mvc.perform( post( "/admin/users/{user}/create-access-token", user.getId() ) )
                .andExpect( status().is3xxRedirection() )
                .andExpect( redirectedUrl( "/admin/users/1" ) );
        verify( userService ).createAccessTokenForUser( user );
    }

    @Test
    @WithMockUser(roles = { "ADMIN" })
    public void givenLoggedIn_whenRevokeAccessToken_thenRedirect3xx() throws Exception {
        User user = createUser( 1 );
        AccessToken accessToken = TestUtils.createAccessToken( 1, user, "1234" );
        when( userService.findUserById( 1 ) ).thenReturn( user );
        when( accessTokenRepository.findById( 1 ) ).thenReturn( Optional.of( accessToken ) );
        mvc.perform( post( "/admin/users/{user}/revoke-access-token/{accessToken}", user.getId(), accessToken.getId() ) )
                .andExpect( status().is3xxRedirection() )
                .andExpect( redirectedUrl( "/admin/users/1" ) );
        verify( userService ).revokeAccessToken( accessToken );
    }

    @Test
    public void givenNotLoggedIn_whenDeleteUser_thenReturn3xx()
            throws Exception {

        User user = createUser( 1 );
        when( userService.findUserById( eq( 1 ) ) ).thenReturn( user );

        mvc.perform( get( "/admin/users/{userId}", user.getId() )
                .contentType( MediaType.APPLICATION_JSON ) )
                .andExpect( status().is3xxRedirection() );
    }

    @Test
    @WithMockUser
    public void givenLoggedInAsUser_whenDeleteUser_thenReturn403()
            throws Exception {

        User me = createUser( 1 );

        when( userService.findUserById( eq( 1 ) ) ).thenReturn( me );

        mvc.perform( get( "/admin/users/{userId}", me.getId() )
                .contentType( MediaType.APPLICATION_JSON ) )
                .andExpect( status().isForbidden() );
    }

    @Test
    @WithMockUser(roles = { "ADMIN" })
    public void givenLoggedInAsAdmin_whenDeleteUser_thenSucceed()
            throws Exception {

        User me = createUser( 1 );

        when( userService.findUserById( eq( 1 ) ) ).thenReturn( me );

        mvc.perform( delete( "/admin/users/{userId}", me.getId() )
                .param( "email", me.getEmail() ) )
                .andExpect( status().is3xxRedirection() )
                .andExpect( redirectedUrl( "/admin/users" ) );

        verify( userService ).delete( me );
    }

    @Test
    @WithMockUser(roles = { "ADMIN" })
    public void givenLoggedInAsAdmin_whenDeleteUserWithWrongConfirmationEmail_thenReturnBadRequest() throws Exception {
        User me = createUser( 1 );

        when( taxonService.findByActiveTrue() ).thenReturn( Collections.emptySet() );
        when( userService.findUserById( eq( 1 ) ) ).thenReturn( me );

        mvc.perform( delete( "/admin/users/{userId}", me.getId() )
                .param( "email", "123@example.com" ) )
                .andExpect( status().isBadRequest() )
                .andExpect( view().name( "admin/user" ) );

        verify( userService, never() ).delete( me );
    }
}
