package ubc.pavlab.rdp.services;

import lombok.extern.apachecommons.CommonsLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;
import ubc.pavlab.rdp.model.PasswordResetToken;
import ubc.pavlab.rdp.model.User;
import ubc.pavlab.rdp.model.UserGene;
import ubc.pavlab.rdp.model.VerificationToken;
import ubc.pavlab.rdp.settings.SiteSettings;

import javax.mail.MessagingException;
import javax.servlet.http.HttpServletRequest;
import java.net.URI;
import java.text.MessageFormat;
import java.util.Locale;

/**
 * Email service implementation used for development that simply pastes the email content into the logs.
 */
@CommonsLog
@Profile("dev")
@Service("emailService")
public class LoggingEmailServiceImpl implements EmailService {

    @Autowired
    private SiteSettings siteSettings;

    @Override
    public void sendSupportMessage( String message, String name, User user, HttpServletRequest request, MultipartFile attachment ) {
        log.info( MessageFormat.format( "Support message for {0}:\n{1}", user, message ) );
    }

    @Override
    public void sendResetTokenMessage( User user, PasswordResetToken token, Locale locale ) {
        URI url = UriComponentsBuilder.fromUri( siteSettings.getHostUri() )
                .path( "updatePassword" )
                .queryParam( "id", "{id}" )
                .queryParam( "token", "{token}" )
                .build( user.getId(), token.getToken() );
        log.info( MessageFormat.format( "Reset URL for {0}: {1}", user, url ) );
    }

    @Override
    public void sendRegistrationMessage( User user, VerificationToken token ) {
        URI confirmationUrl = UriComponentsBuilder.fromUri( siteSettings.getHostUri() )
                .path( "registrationConfirm" )
                .queryParam( "token", "{token}" )
                .build( token.getToken() );
        log.info( MessageFormat.format( "Confirmation URL for {0}: {1}", user, confirmationUrl ) );
    }

    @Override
    public void sendContactEmailVerificationMessage( User user, VerificationToken token ) {
        URI confirmationUrl = UriComponentsBuilder.fromUri( siteSettings.getHostUri() )
                .path( "user/verify-contact-email" )
                .queryParam( "token", "{token}" )
                .build( token.getToken() );
        log.info( MessageFormat.format( "Contact email verification URL for {0}: {1}", user, confirmationUrl ) );
    }

    @Override
    public void sendUserRegisteredEmail( User user ) {
        log.info( MessageFormat.format( "{0} has been registered.", user ) );
    }

    @Override
    public void sendUserGeneAccessRequest( UserGene userGene, User by, String reason ) {
        log.info( MessageFormat.format( "{0} has been requested by {1} for: {2}.", userGene, by, reason ) );
    }
}
