package de.tum.cit.aet.artemis.core.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.AcceptExternalLLMUsageDTO;
import de.tum.cit.aet.artemis.core.dto.UserDTO;
import de.tum.cit.aet.artemis.core.dto.UserInitializationDTO;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.service.user.UserCreationService;
import de.tum.cit.aet.artemis.lti.api.LtiApi;
import tech.jhipster.web.util.PaginationUtil;

/**
 * REST controller for managing users.
 * <p>
 * This class accesses the {@link User} entity, and needs to fetch its collection of authorities.
 * <p>
 * For a normal use-case, it would be better to have an eager relationship between User and Authority, and send everything to the client side: there would be no View Model and DTO,
 * a lot less code, and an outer-join which would be good for performance.
 * <p>
 * We use a View Model and a DTO for 3 reasons:
 * <ul>
 * <li>We want to keep a lazy association between the user and the authorities, because people will quite often do relationships with the user, and we don't want them to get the
 * authorities all the time for nothing (for performance reasons). This is the #1 goal: we should not impact our users' application because of this use-case.</li>
 * <li>Not having an outer join causes n+1 requests to the database. This is not a real issue as we have by default a second-level cache. This means on the first HTTP call we do
 * the n+1 requests, but then all authorities come from the cache, so in fact it's much better than doing an outer join (which will get lots of data from the database, for each
 * HTTP call).</li>
 * <li>As this manages users, for security reasons, we'd rather have a DTO layer.</li>
 * </ul>
 * <p>
 * Another option would be to have a specific JPA entity graph to handle this case.
 */
@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/core/")
public class UserResource {

    private static final Logger log = LoggerFactory.getLogger(UserResource.class);

    private final UserCreationService userCreationService;

    private final Optional<LtiApi> ltiApi;

    private final UserRepository userRepository;

    public UserResource(UserRepository userRepository, UserCreationService userCreationService, Optional<LtiApi> ltiApi) {
        this.userRepository = userRepository;
        this.ltiApi = ltiApi;
        this.userCreationService = userCreationService;
    }

    /**
     * GET users/search : search all users by login or name (result size is limited though on purpose, see below)
     *
     * @param loginOrName the login or name by which to search users
     * @return the ResponseEntity with status 200 (OK) and with body all users
     */
    @GetMapping("users/search")
    @EnforceAtLeastInstructor
    public ResponseEntity<List<UserDTO>> searchAllUsers(@RequestParam("loginOrName") String loginOrName) {
        log.debug("REST request to search all Users for {}", loginOrName);
        // restrict result size by only allowing reasonable searches
        if (loginOrName.length() < 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Query param 'loginOrName' must be three characters or longer.");
        }
        // limit search results to 25 users (larger result sizes would impact performance and are not useful for specific user searches)
        final Page<UserDTO> page = userRepository.searchAllUsersByLoginOrName(PageRequest.of(0, 25), loginOrName);
        page.forEach(user -> {
            // remove some values which are not needed in the client
            user.setLangKey(null);
            user.setLastModifiedBy(null);
            user.setLastModifiedDate(null);
            user.setCreatedBy(null);
            user.setCreatedDate(null);
            user.setExternalLLMUsageAccepted(null);
        });
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    /**
     * Initialises users that are flagged as such and are LTI users by setting a new password that gets returned
     *
     * @return The ResponseEntity with a status 200 (Ok) and either an empty password or the newly created password
     */
    @PutMapping("users/initialize")
    @EnforceAtLeastStudent
    public ResponseEntity<UserInitializationDTO> initializeUser() {
        User user = userRepository.getUserWithGroupsAndAuthorities();
        if (user.getActivated()) {
            return ResponseEntity.ok().body(new UserInitializationDTO(null));
        }
        if ((ltiApi.isPresent() && !ltiApi.get().isLtiCreatedUser(user)) || !user.isInternal()) {
            user.setActivated(true);
            userRepository.save(user);
            return ResponseEntity.ok().body(new UserInitializationDTO(null));
        }

        String result = userCreationService.setRandomPasswordAndReturn(user);
        return ResponseEntity.ok().body(new UserInitializationDTO(result));
    }

    /**
     * PUT users/accept-external-llm-usage : sets the externalLLMUsageAccepted flag for the user to ZonedDateTime.now() or null,
     * depending on whether the user accepted or declined the usage of external LLMs.
     *
     * @param acceptExternalLLMUsageDTO the DTO containing the user's choice regarding external LLM usage
     * @return the ResponseEntity with status 200 (OK), with status 404 (Not Found),
     *         or with status 400 (Bad Request) if external LLM usage was already accepted
     */
    @PutMapping("users/accept-external-llm-usage")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> setExternalLLMUsageAcceptedToTimestamp(@RequestBody AcceptExternalLLMUsageDTO acceptExternalLLMUsageDTO) {
        User user = userRepository.getUser();

        ZonedDateTime hasAcceptedTimestamp = acceptExternalLLMUsageDTO.accepted() ? ZonedDateTime.now() : null;
        userRepository.updateExternalLLMUsageAcceptedToDate(user.getId(), hasAcceptedTimestamp);
        return ResponseEntity.ok().build();
    }
}
