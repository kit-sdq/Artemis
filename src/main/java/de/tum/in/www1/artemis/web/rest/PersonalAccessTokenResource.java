package de.tum.in.www1.artemis.web.rest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.UserNotActivatedException;
import de.tum.in.www1.artemis.security.jwt.TokenProvider;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

/**
 * Controller to request personal access tokens.
 */
@RestController
@RequestMapping("/api")
@Profile("pat")
public class PersonalAccessTokenResource {

    private static final String ENTITY_NAME = "personalAccessTokenResource";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final TokenProvider tokenProvider;

    private final UserRepository userRepository;

    @Value("${artemis.personal-access-token.max-lifetime-in-days}")
    private long personalAccessTokenMaxLifetimeDays;

    public PersonalAccessTokenResource(TokenProvider tokenProvider, UserRepository userRepository) {
        this.tokenProvider = tokenProvider;
        this.userRepository = userRepository;
    }

    /**
     * Generates a personal access token (JWT token) for the current user.
     *
     * @param lifetimeDays the lifetime for the JWT token in days
     * @return a JWT Token
     */
    @PreAuthorize("hasRole('USER')")
    @PostMapping("/personal-access-token")
    public ResponseEntity<UserJWTController.JWTToken> getPersonalAccessToken(@RequestBody Long lifetimeDays) {
        User user = userRepository.getUser();

        if (!user.getActivated()) {
            throw new UserNotActivatedException("User was disabled!");
        }

        if (lifetimeDays > this.personalAccessTokenMaxLifetimeDays) {
            long difference = this.personalAccessTokenMaxLifetimeDays - lifetimeDays;
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "invalidPersonalAccessTokenLifetime",
                    "Requested token lifetime exceeds the maximum lifetime for personal access tokens by " + difference + " days")).build();
        }

        // Automatically returns 401 if not fully authorized
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        final long daysToMillis = 24 * 60 * 60 * 1000;
        final String jwt = tokenProvider.createTokenWithCustomDuration(authentication, daysToMillis * lifetimeDays);
        return new ResponseEntity<>(new UserJWTController.JWTToken(jwt), HttpStatus.OK);
    }

    /**
     * Returns the maximum lifetime for personal access tokens.
     * @return the maximum lifetime for personal access tokens in days
     */
    @GetMapping("/personal-access-token/maximum-lifetime")
    public ResponseEntity<Long> getPersonalAccessTokenMaxLifetime() {
        return new ResponseEntity<>(this.personalAccessTokenMaxLifetimeDays, HttpStatus.OK);
    }
}
