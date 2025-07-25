package de.tum.cit.aet.artemis.core.service.connectors;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_SAML2;
import static de.tum.cit.aet.artemis.core.config.Constants.SYSTEM_ACCOUNT;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.service.notifications.MailService;
import de.tum.cit.aet.artemis.core.config.SAML2Properties;
import de.tum.cit.aet.artemis.core.domain.Authority;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.vm.ManagedUserVM;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.UserNotActivatedException;
import de.tum.cit.aet.artemis.core.security.jwt.AuthenticationMethod;
import de.tum.cit.aet.artemis.core.service.ArtemisSuccessfulLoginService;
import de.tum.cit.aet.artemis.core.service.user.UserCreationService;
import de.tum.cit.aet.artemis.core.service.user.UserService;
import de.tum.cit.aet.artemis.core.util.HttpRequestUtils;

/**
 * This class describes a service for SAML2 authentication.
 * <p>
 * The main method is {@link #handleAuthentication(Authentication,Saml2AuthenticatedPrincipal)}. The service extracts the user information
 * from the {@link Saml2AuthenticatedPrincipal} and creates the user, if it does not exist already.
 * <p>
 * When the user gets created, the SAML2 attributes can be used to fill in user information. The configuration happens
 * via patterns for every field in the SAML2 configuration.
 * <p>
 * The service creates a {@link UsernamePasswordAuthenticationToken} which can then be used by the client to authenticate.
 * This is needed, since the client "does not know" that he is already authenticated via SAML2.
 */
@Lazy
@Service
@Profile(PROFILE_SAML2)
public class SAML2Service {

    private final AuditEventRepository auditEventRepository;

    @Value("${info.saml2.enablePassword:#{null}}")
    private Optional<Boolean> saml2EnablePassword;

    @Value("${info.saml2.syncUserData:#{null}}")
    private Optional<Boolean> saml2syncUserData;

    private static final Logger log = LoggerFactory.getLogger(SAML2Service.class);

    private final UserCreationService userCreationService;

    private final UserRepository userRepository;

    private final UserService userService;

    private final SAML2Properties properties;

    private final MailService mailService;

    private final Map<String, Pattern> extractionPatterns;

    private final ArtemisSuccessfulLoginService artemisSuccessfulLoginService;

    /**
     * Constructs a new instance.
     *
     * @param auditEventRepository The audit event repository
     * @param userRepository       The user repository
     * @param properties           The properties
     * @param userCreationService  The user creation service
     */
    public SAML2Service(final AuditEventRepository auditEventRepository, final UserRepository userRepository, final SAML2Properties properties,
            final UserCreationService userCreationService, MailService mailService, UserService userService, ArtemisSuccessfulLoginService artemisSuccessfulLoginService) {
        this.auditEventRepository = auditEventRepository;
        this.userRepository = userRepository;
        this.properties = properties;
        this.userCreationService = userCreationService;
        this.mailService = mailService;
        this.userService = userService;
        this.artemisSuccessfulLoginService = artemisSuccessfulLoginService;

        this.extractionPatterns = generateExtractionPatterns(properties);
    }

    private Map<String, Pattern> generateExtractionPatterns(final SAML2Properties properties) {
        return properties.getValueExtractionPatterns().stream()
                .collect(Collectors.toMap(SAML2Properties.ExtractionPattern::getKey, pattern -> Pattern.compile(pattern.getValuePattern())));
    }

    /**
     * Handles an authentication via SAML2.
     * <p>
     * Registers new users and returns a new {@link UsernamePasswordAuthenticationToken} matching the SAML2 user.
     *
     * @param originalAuth the original authentication with details
     * @param principal    the principal, containing the user information
     * @param request      the HTTP request, used to extract the client environment
     * @return a new {@link UsernamePasswordAuthenticationToken} matching the SAML2 user
     */
    public Authentication handleAuthentication(final Authentication originalAuth, final Saml2AuthenticatedPrincipal principal, final HttpServletRequest request) {
        Map<String, Object> details = originalAuth.getDetails() == null ? Map.of() : Map.of("details", originalAuth.getDetails());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        log.debug("SAML2 User '{}' logged in, attributes {}", auth.getName(), principal.getAttributes());
        log.debug("SAML2 password-enabled: {}", saml2EnablePassword);

        final String username = substituteAttributes(properties.getUsernamePattern(), principal);
        Optional<User> user = userRepository.findOneWithGroupsAndAuthoritiesByLogin(username);
        if (user.isEmpty()) {
            // create User if not exists
            user = Optional.of(createUser(username, principal));
            Map<String, Object> accountCreationDetails = new HashMap<>(details);
            accountCreationDetails.put("user", user.get().getLogin());
            auditEventRepository.add(new AuditEvent(Instant.now(), SYSTEM_ACCOUNT, "SAML2_ACCOUNT_CREATE", accountCreationDetails));

            if (saml2EnablePassword.isPresent() && Boolean.TRUE.equals(saml2EnablePassword.get())) {
                log.debug("Sending SAML2 creation mail");
                if (userService.prepareUserForPasswordReset(user.get())) {
                    mailService.sendSAML2SetPasswordMail(user.get());
                }
                else {
                    log.error("User {} was created but could not be found in the database!", user.get());
                }
            }
        }
        else if (saml2syncUserData.isPresent() && Boolean.TRUE.equals(saml2syncUserData.get())) {
            syncUserDataFromSaml2(principal, user.get());
        }

        if (!user.get().getActivated()) {
            log.debug("Not activated SAML2 user {} attempted login.", user.get());
            throw new UserNotActivatedException("User was disabled.");
        }

        String login = user.get().getLogin();
        auth = new UsernamePasswordAuthenticationToken(login, user.get().getPassword(), toGrantedAuthorities(user.get().getAuthorities()));
        auditEventRepository.add(new AuditEvent(Instant.now(), login, "SAML2_AUTHENTICATION_SUCCESS", details));
        artemisSuccessfulLoginService.sendLoginEmail(login, AuthenticationMethod.SAML2, HttpRequestUtils.getClientEnvironment(request));
        return auth;
    }

    private void syncUserDataFromSaml2(Saml2AuthenticatedPrincipal principal, User user) {
        log.debug("SAML2 sync user data enabled and will be performed for user {}", user.getLogin());
        // We assume that only the name of the user might change
        String newFirstName = substituteAttributes(properties.getFirstNamePattern(), principal);
        String newLastName = substituteAttributes(properties.getLastNamePattern(), principal);
        String oldFirstName = user.getFirstName();
        String oldLastName = user.getLastName();

        boolean changed = false;
        if (!Objects.equals(oldFirstName, newFirstName)) {
            user.setFirstName(newFirstName);
            changed = true;
        }
        if (!Objects.equals(oldLastName, newLastName)) {
            user.setLastName(newLastName);
            changed = true;
        }

        if (changed) {
            log.info("SAML2 user's name changed ... before: {} {}, after: {} {}", oldFirstName, oldLastName, newFirstName, newLastName);
            userRepository.save(user);
        }
    }

    private User createUser(String username, final Saml2AuthenticatedPrincipal principal) {
        ManagedUserVM newUser = new ManagedUserVM();
        // Fill in User information using the patterns and the SAML2 attributes.
        newUser.setLogin(username);
        newUser.setFirstName(substituteAttributes(properties.getFirstNamePattern(), principal));
        newUser.setLastName(substituteAttributes(properties.getLastNamePattern(), principal));
        newUser.setEmail(substituteAttributes(properties.getEmailPattern(), principal));
        String registrationNumber = substituteAttributes(properties.getRegistrationNumberPattern(), principal);
        if (!registrationNumber.isBlank()) {
            newUser.setVisibleRegistrationNumber(registrationNumber);
        } // else set registration number to null to preserve uniqueness
        newUser.setLangKey(substituteAttributes(properties.getLangKeyPattern(), principal));
        newUser.setAuthorities(new HashSet<>(Set.of(Role.STUDENT.getAuthority())));
        newUser.setGroups(new HashSet<>());

        // userService.createUser(ManagedUserVM) does create an activated User
        // a random password is generated
        return userCreationService.createUser(newUser);
    }

    private static Collection<GrantedAuthority> toGrantedAuthorities(final Collection<Authority> authorities) {
        return authorities.stream().map(Authority::getName).map(SimpleGrantedAuthority::new).collect(Collectors.toSet());
    }

    private String substituteAttributes(final String input, final Saml2AuthenticatedPrincipal principal) {
        String output = input;
        for (String key : principal.getAttributes().keySet()) {
            final String escapedKey = Pattern.quote(key);
            output = output.replaceAll("\\{" + escapedKey + "\\}", getAttributeValue(principal, key));
            log.debug("SAML principal key: {}, raw value: {}, after replacements: {}", key, principal.getFirstAttribute(key), output);
        }
        return output.replaceAll("\\{[^\\}]*?\\}", "");
    }

    /**
     * Gets the value associated with the given key from the principal.
     *
     * @param principal containing the user information.
     * @param key       of the attribute that should be extracted.
     * @return the value associated with the given key.
     */
    private String getAttributeValue(final Saml2AuthenticatedPrincipal principal, final String key) {
        final String value = principal.getFirstAttribute(key);
        if (value == null) {
            return "";
        }

        final Pattern extractionPattern = extractionPatterns.get(key);
        if (extractionPattern == null) {
            return value;
        }

        final Matcher matcher = extractionPattern.matcher(value);
        if (matcher.matches()) {
            return matcher.group(SAML2Properties.ATTRIBUTE_VALUE_EXTRACTION_GROUP_NAME);
        }
        else {
            return value;
        }
    }
}
