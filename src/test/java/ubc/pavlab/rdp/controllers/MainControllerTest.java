package ubc.pavlab.rdp.controllers;

import org.assertj.core.util.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import ubc.pavlab.rdp.WebSecurityConfig;
import ubc.pavlab.rdp.model.User;
import ubc.pavlab.rdp.services.TaxonService;
import ubc.pavlab.rdp.services.TierService;
import ubc.pavlab.rdp.services.UserService;
import ubc.pavlab.rdp.settings.ApplicationSettings;
import ubc.pavlab.rdp.settings.SiteSettings;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static ubc.pavlab.rdp.util.TestUtils.createUser;

@RunWith(SpringRunner.class)
@WebMvcTest(MainController.class)
@Import(WebSecurityConfig.class)
public class MainControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean(name = "applicationSettings")
    private ApplicationSettings applicationSettings;

    @MockBean
    private ApplicationSettings.ProfileSettings profileSettings;

    @MockBean
    private ApplicationSettings.PrivacySettings privacySettings;

    @MockBean
    private SiteSettings siteSettings;

    @MockBean
    private ApplicationSettings.OrganSettings organSettings;

    @MockBean
    private ApplicationSettings.InternationalSearchSettings iSearchSettings;

    @MockBean(name = "userService")
    private UserService userService;

    @MockBean(name = "taxonService")
    private TaxonService taxonService;

    @MockBean(name = "tierService")
    private TierService tierService;

    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private BCryptPasswordEncoder bCryptPasswordEncoder;

    @MockBean
    private PermissionEvaluator permissionEvaluator;

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
    public void getIndex_redirect3xx() throws Exception {
        when( permissionEvaluator.hasPermission( any(), isNull(), eq( "search" ) ) ).thenReturn( true );
        mvc.perform( get( "/" ) )
                .andExpect( status().is3xxRedirection() )
                .andExpect( redirectedUrl( "/search" ) );
    }

    @Test
    public void getIndex_withoutPublicSearch_redirect3xx() throws Exception {
        mvc.perform( get( "/" ) )
                .andExpect( status().is3xxRedirection() )
                .andExpect( redirectedUrl( "http://localhost/login" ) );
    }

    @Test
    @WithMockUser
    public void getIndex_withUser_redirect3xx() throws Exception {
        User user = createUser( 1 );
        when( userService.findCurrentUser() ).thenReturn( user );
        when( permissionEvaluator.hasPermission( any(), isNull(), eq( "search" ) ) ).thenReturn( true );
        mvc.perform( get( "/" ) )
                .andExpect( status().is3xxRedirection() )
                .andExpect( redirectedUrl( "/user/home" ) );
    }

    @Test
    public void getHtmlStats_redirect3xx() throws Exception {
        mvc.perform( get( "/stats.html" ) )
                .andExpect( status().is3xxRedirection() )
                .andExpect( redirectedUrl( "/stats" ) );
    }

    @Test
    @WithMockUser
    public void getTimeout_withUser_return200() throws Exception {
        User user = createUser( 1 );
        when( userService.findCurrentUser() ).thenReturn( user );
        mvc.perform( get( "/gettimeout" ) )
                .andExpect( status().isOk() )
                .andExpect( content().contentTypeCompatibleWith( MediaType.TEXT_PLAIN ) )
                .andExpect( content().string( "Session timeout refreshed." ) )
                .andExpect( cookie().exists( "serverTime" ) )
                .andExpect( cookie().exists( "sessionExpiry" ) );
    }

    @Test
    public void getTimeout_withoutUser_redirect3xx() throws Exception {
        mvc.perform( get( "/gettimeout" ) )
                .andExpect( status().is3xxRedirection() )
                .andExpect( redirectedUrl( "http://localhost/login" ) );
    }
}
