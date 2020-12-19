package ubc.pavlab.rdp.controllers;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.apachecommons.CommonsLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ubc.pavlab.rdp.events.OnContactEmailUpdateEvent;
import ubc.pavlab.rdp.exception.TokenException;
import ubc.pavlab.rdp.model.*;
import ubc.pavlab.rdp.model.enums.PrivacyLevelType;
import ubc.pavlab.rdp.model.enums.TierType;
import ubc.pavlab.rdp.services.*;
import ubc.pavlab.rdp.settings.ApplicationSettings;

import javax.mail.MessagingException;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import java.text.MessageFormat;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.function.Function.identity;
import static ubc.pavlab.rdp.util.CollectionUtils.toNullableMap;

@Controller
@CommonsLog
@Secured({ "ROLE_USER", "ROLE_ADMIN" })
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private TaxonService taxonService;

    @Autowired
    private GeneInfoService geneService;

    @Autowired
    private GOService goService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private ApplicationSettings applicationSettings;

    @GetMapping(value = { "/user/home" })
    public ModelAndView userHome() {
        ModelAndView modelAndView = new ModelAndView( "user/home" );
        modelAndView.addObject( "user", userService.findCurrentUser() );
        return modelAndView;
    }

    @GetMapping(value = { "/user/model/{taxonId}" })
    public ModelAndView model( @PathVariable Integer taxonId ) {
        ModelAndView modelAndView = new ModelAndView( "user/model" );
        User user = userService.findCurrentUser();
        Taxon taxon = taxonService.findById( taxonId );
        modelAndView.addObject( "viewOnly", null );
        modelAndView.addObject( "user", user );
        modelAndView.addObject( "taxon", taxon );
        return modelAndView;
    }

    @GetMapping(value = "/user/taxon/{taxonId}/term/{goId}/gene/view")
    public ModelAndView getTermsGenesForTaxon( @PathVariable Integer taxonId, @PathVariable String goId ) {
        ModelAndView modelAndView = new ModelAndView( "fragments/gene-table::gene-table" );
        User user = userService.findCurrentUser();
        Taxon taxon = taxonService.findById( taxonId );

        if ( taxon == null ) {
            modelAndView.setViewName( "error/404" );
            modelAndView.setStatus( HttpStatus.NOT_FOUND );
            return modelAndView;
        }

        GeneOntologyTermInfo term = goService.getTerm( goId );

        if ( term != null ) {
            Collection<Integer> geneIds = goService.getGenesInTaxon( term, taxon );
            modelAndView.addObject( "genes",
                    user.getGenesByTaxonAndTier( taxon, TierType.MANUAL ).stream()
                            .filter( ug -> geneIds.contains( ug.getGeneId() ) )
                            .collect( Collectors.toSet() ) );
        } else {
            modelAndView.addObject( "genes", Collections.EMPTY_SET );
        }
        modelAndView.addObject( "viewOnly", Boolean.TRUE );
        return modelAndView;
    }

    @GetMapping(value = "/user/profile")
    public ModelAndView profile() {
        ModelAndView modelAndView = new ModelAndView( "user/profile" );
        User user = userService.findCurrentUser();
        modelAndView.addObject( "user", user );
        modelAndView.addObject( "viewOnly", null );
        return modelAndView;
    }

    @GetMapping(value = { "/user/documentation" })
    public ModelAndView documentation() {
        ModelAndView modelAndView = new ModelAndView( "user/documentation" );
        User user = userService.findCurrentUser();
        modelAndView.addObject( "user", user );
        return modelAndView;
    }

    @GetMapping(value = { "/user/faq" })
    public ModelAndView faq() {
        ModelAndView modelAndView = new ModelAndView( "user/faq" );
        if ( applicationSettings.getFaqFile() == null ) {
            modelAndView.setStatus( HttpStatus.NOT_FOUND );
            modelAndView.setViewName( "error/404" );
        } else {
            User user = userService.findCurrentUser();
            modelAndView.addObject( "user", user );
        }
        return modelAndView;
    }

    @GetMapping(value = { "/user/support" })
    public ModelAndView support() {
        ModelAndView modelAndView = new ModelAndView( "user/support" );
        User user = userService.findCurrentUser();
        modelAndView.addObject( "user", user );
        return modelAndView;
    }

    @PostMapping(value = { "/user/support" })
    public ModelAndView supportPost( HttpServletRequest request,
                                     @RequestParam String name,
                                     @RequestParam String message,
                                     @RequestParam(required = false) MultipartFile attachment ) {
        ModelAndView modelAndView = new ModelAndView( "user/support" );
        User user = userService.findCurrentUser();
        modelAndView.addObject( "user", user );

        log.info( MessageFormat.format( "{0} is attempting to contact support.", user ) );

        try {
            emailService.sendSupportMessage( message, name, user, request, attachment );
            modelAndView.addObject( "message", "Sent. We will get back to you shortly." );
            modelAndView.addObject( "success", Boolean.TRUE );
        } catch ( MessagingException e ) {
            log.error( MessageFormat.format( "Could not send support message to {0}.", user ), e );
            modelAndView
                    .addObject( "message", "There was a problem sending the support request. Please try again later." );
            modelAndView.addObject( "success", Boolean.FALSE );
        }

        return modelAndView;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class PasswordChange extends PasswordReset {

        @NotEmpty(message = "Current password cannot be empty.")
        private String oldPassword;
    }

    @GetMapping(value = "/user/password")
    public ModelAndView changePassword() {
        ModelAndView modelAndView = new ModelAndView( "user/password" );
        User user = userService.findCurrentUser();
        modelAndView.addObject( "user", user );

        modelAndView.addObject( "passwordChange", new PasswordChange() );
        return modelAndView;
    }

    @PostMapping(value = "/user/password")
    public ModelAndView changePassword( @Valid PasswordChange passwordChange, BindingResult bindingResult ) {
        ModelAndView modelAndView = new ModelAndView( "user/password" );
        User user = userService.findCurrentUser();
        modelAndView.addObject( "user", user );

        if ( !passwordChange.isValid() ) {
            bindingResult.rejectValue( "passwordConfirm", "error.passwordChange", "Password conformation does not match new password." );
        }

        if ( bindingResult.hasErrors() ) {
            // Short circuit before testing password.
            modelAndView.setStatus( HttpStatus.BAD_REQUEST );
            return modelAndView;
        }

        try {
            userService.changePassword( passwordChange.getOldPassword(), passwordChange.getNewPassword() );
        } catch ( BadCredentialsException e ) {
            bindingResult.rejectValue( "oldPassword", "error.passwordChange", "Current password does not match." );
        }

        if ( bindingResult.hasErrors() ) {
            modelAndView.setStatus( HttpStatus.BAD_REQUEST );
        } else {
            modelAndView.addObject( "passwordChange", new PasswordChange() );
            modelAndView.addObject( "message", "Password Updated" );
        }

        return modelAndView;
    }

    @Transactional
    @PostMapping("/user/resend-contact-email-verification")
    public Object resendContactEmailVerification( RedirectAttributes redirectAttributes ) {
        User user = userService.findCurrentUser();
        if ( user.getProfile().isContactEmailVerified() ) {
            return ResponseEntity.badRequest().body( "Contact email is already verified." );
        }
        VerificationToken token = userService.createContactEmailVerificationTokenForUser( user );
        eventPublisher.publishEvent( new OnContactEmailUpdateEvent( user, token ) );
        redirectAttributes.addFlashAttribute( "message", MessageFormat.format( "We will send an email to {0} with a link to verify your contact email.", user.getProfile().getContactEmail() ) );
        return "redirect:/user/profile";
    }

    @GetMapping("/user/verify-contact-email")
    public String verifyContactEmail( @RequestParam String token, RedirectAttributes redirectAttributes ) {
        try {
            userService.confirmVerificationToken( token );
            redirectAttributes.addFlashAttribute( "message", "Your contact email has been successfully verified." );
        } catch ( TokenException e ) {
            log.error( MessageFormat.format( "{0} attempt to confirm verification token failed.", userService.findCurrentUser() ), e );
            redirectAttributes.addFlashAttribute( "message", e.getMessage() );
            redirectAttributes.addFlashAttribute( "error", Boolean.TRUE );
        }
        return "redirect:/user/profile";
    }

    @Data
    static class ProfileWithOrganUberonIds {
        @Valid
        private Profile profile;
        private Set<String> organUberonIds;
    }

    @ResponseBody
    @PostMapping(value = "/user/profile", produces = MediaType.TEXT_PLAIN_VALUE)
    public String saveProfile( @RequestBody ProfileWithOrganUberonIds profileWithOrganUberonIds ) {
        User user = userService.findCurrentUser();
        userService.updateUserProfileAndPublicationsAndOrgans( user, profileWithOrganUberonIds.profile, profileWithOrganUberonIds.profile.getPublications(), profileWithOrganUberonIds.organUberonIds );
        return "Saved.";
    }

    @ResponseBody
    @GetMapping(value = "/user", produces = MediaType.APPLICATION_JSON_VALUE)
    public User getUser() {
        return userService.findCurrentUser();
    }

    @ResponseBody
    @GetMapping(value = "/user/taxon", produces = MediaType.APPLICATION_JSON_VALUE)
    public Set<Taxon> getTaxons() {
        return userService.findCurrentUser().getUserGenes().values().stream().map( UserGene::getTaxon ).collect( Collectors.toSet() );
    }

    @ResponseBody
    @GetMapping(value = "/user/gene", produces = MediaType.APPLICATION_JSON_VALUE)
    public Collection<UserGene> getGenes() {
        return userService.findCurrentUser().getUserGenes().values();
    }

    @ResponseBody
    @GetMapping(value = "/user/term", produces = MediaType.APPLICATION_JSON_VALUE)
    public Collection<UserTerm> getTerms() {
        return userService.findCurrentUser().getUserTerms();
    }

    @Data
    static class Model {
        private Map<Integer, TierType> geneTierMap;
        private Map<Integer, PrivacyLevelType> genePrivacyLevelMap;
        private List<String> goIds;
        private String description;
    }

    @ResponseBody
    @PostMapping(value = "/user/model/{taxonId}", produces = MediaType.TEXT_PLAIN_VALUE)
    public Object saveModelForTaxon( @PathVariable Integer taxonId, @RequestBody Model model ) {
        User user = userService.findCurrentUser();
        Taxon taxon = taxonService.findById( taxonId );

        if ( taxon == null ) {
            return ResponseEntity.notFound().build();
        }

        user.getTaxonDescriptions().put( taxon, model.getDescription() );

        Map<GeneInfo, TierType> genes = model.getGeneTierMap().keySet().stream()
                .filter( geneId -> model.getGeneTierMap().get( geneId ) != null ) // strip null values
                .map( geneId -> geneService.load( geneId ) )
                .filter( Objects::nonNull )
                .collect( Collectors.toMap( identity(), g -> model.getGeneTierMap().get( g.getGeneId() ) ) );

        Map<GeneInfo, PrivacyLevelType> privacyLevels = model.getGenePrivacyLevelMap().keySet().stream()
                .filter( geneId -> model.getGenePrivacyLevelMap().get( geneId ) != null ) // strip null values
                .map( geneId -> geneService.load( geneId ) )
                .filter( Objects::nonNull )
                .collect( Collectors.toMap( identity(), g -> model.getGenePrivacyLevelMap().get( g.getGeneId() ) ) );

        Set<GeneOntologyTermInfo> terms = model.getGoIds().stream()
                .map( goService::getTerm )
                .filter( Objects::nonNull )
                .collect( Collectors.toSet() );

        userService.updateTermsAndGenesInTaxon( user, taxon, genes, privacyLevels, terms );

        return "Saved.";
    }

    @ResponseBody
    @GetMapping(value = "/user/taxon/{taxonId}/gene", produces = MediaType.APPLICATION_JSON_VALUE)
    public Object getGenesForTaxon( @PathVariable Integer taxonId ) {
        Taxon taxon = taxonService.findById( taxonId );
        if ( taxon == null ) {
            return ResponseEntity.notFound().build();
        }
        return userService.findCurrentUser().getGenesByTaxon( taxon );
    }

    @ResponseBody
    @GetMapping(value = "/user/taxon/{taxonId}/term", produces = MediaType.APPLICATION_JSON_VALUE)
    public Object getTermsForTaxon( @PathVariable Integer taxonId ) {
        Taxon taxon = taxonService.findById( taxonId );
        if ( taxon == null ) {
            return ResponseEntity.notFound().build();
        }
        return userService.findCurrentUser().getTermsByTaxon( taxon );
    }

    @ResponseBody
    @GetMapping(value = "/user/taxon/{taxonId}/term/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public Object getTermsForTaxon( @PathVariable Integer taxonId,
                                    @RequestParam List<String> goIds ) {
        Taxon taxon = taxonService.findById( taxonId );
        if ( taxon == null ) {
            return ResponseEntity.notFound().build();
        }
        User user = userService.findCurrentUser();
        return goIds.stream().collect( toNullableMap( identity(), goId -> goService.getTerm( goId ) == null ? null : userService.convertTerm( user, taxon, goService.getTerm( goId ) ) ) );
    }

    @ResponseBody
    @GetMapping(value = "/user/taxon/{taxonId}/term/recommend", produces = MediaType.APPLICATION_JSON_VALUE)
    public Object getRecommendedTermsForTaxon( @PathVariable Integer taxonId ) {
        Taxon taxon = taxonService.findById( taxonId );
        if ( taxon == null ) {
            return ResponseEntity.notFound().build();
        }
        return userService.recommendTerms( userService.findCurrentUser(), taxon );
    }
}
