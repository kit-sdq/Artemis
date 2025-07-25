package de.tum.cit.aet.artemis.core.service.user;

import static de.tum.cit.aet.artemis.core.config.Constants.PASSWORD_MAX_LENGTH;
import static de.tum.cit.aet.artemis.core.config.Constants.PASSWORD_MIN_LENGTH;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.USERNAME_MAX_LENGTH;
import static de.tum.cit.aet.artemis.core.config.Constants.USERNAME_MIN_LENGTH;
import static de.tum.cit.aet.artemis.core.config.Constants.USER_EMAIL_DOMAIN_AFTER_SOFT_DELETE;
import static de.tum.cit.aet.artemis.core.config.Constants.USER_FIRST_NAME_AFTER_SOFT_DELETE;
import static de.tum.cit.aet.artemis.core.config.Constants.USER_LAST_NAME_AFTER_SOFT_DELETE;
import static de.tum.cit.aet.artemis.core.domain.Authority.ADMIN_AUTHORITY;
import static de.tum.cit.aet.artemis.core.security.Role.ADMIN;
import static de.tum.cit.aet.artemis.core.security.Role.STUDENT;
import static org.apache.commons.lang3.StringUtils.lowerCase;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import jakarta.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import de.tum.cit.aet.artemis.atlas.api.LearnerProfileApi;
import de.tum.cit.aet.artemis.atlas.api.ScienceEventApi;
import de.tum.cit.aet.artemis.communication.domain.SavedPost;
import de.tum.cit.aet.artemis.communication.repository.SavedPostRepository;
import de.tum.cit.aet.artemis.communication.service.CourseNotificationSettingService;
import de.tum.cit.aet.artemis.communication.service.GlobalNotificationSettingService;
import de.tum.cit.aet.artemis.communication.service.UserCourseNotificationStatusService;
import de.tum.cit.aet.artemis.core.FilePathType;
import de.tum.cit.aet.artemis.core.config.FullStartupEvent;
import de.tum.cit.aet.artemis.core.domain.Authority;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.StudentDTO;
import de.tum.cit.aet.artemis.core.dto.UserDTO;
import de.tum.cit.aet.artemis.core.dto.vm.ManagedUserVM;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.AccountRegistrationBlockedException;
import de.tum.cit.aet.artemis.core.exception.EmailAlreadyUsedException;
import de.tum.cit.aet.artemis.core.exception.PasswordViolatesRequirementsException;
import de.tum.cit.aet.artemis.core.exception.UsernameAlreadyUsedException;
import de.tum.cit.aet.artemis.core.repository.AuthorityRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.core.service.connectors.ldap.LdapAuthenticationProvider;
import de.tum.cit.aet.artemis.core.service.ldap.LdapUserDto;
import de.tum.cit.aet.artemis.core.service.ldap.LdapUserService;
import de.tum.cit.aet.artemis.core.service.messaging.InstanceMessageSendService;
import de.tum.cit.aet.artemis.core.util.FilePathConverter;
import de.tum.cit.aet.artemis.programming.domain.ParticipationVCSAccessToken;
import de.tum.cit.aet.artemis.programming.service.ParticipationVcsAccessTokenService;
import de.tum.cit.aet.artemis.programming.service.ci.CIUserManagementService;
import de.tum.cit.aet.artemis.programming.service.sshuserkeys.UserSshPublicKeyService;
import tech.jhipster.security.RandomUtil;

/**
 * Service class for managing users.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    @Value("${artemis.user-management.internal-admin.username:#{null}}")
    private Optional<String> artemisInternalAdminUsername;

    @Value("${artemis.user-management.internal-admin.password:#{null}}")
    private Optional<String> artemisInternalAdminPassword;

    @Value("${artemis.user-management.internal-admin.email:#{null}}")
    private Optional<String> artemisInternalAdminEmail;

    private final UserCreationService userCreationService;

    private final UserRepository userRepository;

    private final PasswordService passwordService;

    private final AuthorityService authorityService;

    private final Optional<LdapUserService> ldapUserService;

    private final Optional<CIUserManagementService> optionalCIUserManagementService;

    private final CacheManager cacheManager;

    private final AuthorityRepository authorityRepository;

    private final InstanceMessageSendService instanceMessageSendService;

    private final FileService fileService;

    private final Optional<ScienceEventApi> scienceEventApi;

    private final ParticipationVcsAccessTokenService participationVCSAccessTokenService;

    private final Optional<LearnerProfileApi> learnerProfileApi;

    private final SavedPostRepository savedPostRepository;

    private final UserSshPublicKeyService userSshPublicKeyService;

    private final CourseNotificationSettingService courseNotificationSettingService;

    private final UserCourseNotificationStatusService userCourseNotificationStatusService;

    private final GlobalNotificationSettingService globalNotificationSettingService;

    public UserService(UserCreationService userCreationService, UserRepository userRepository, AuthorityService authorityService, AuthorityRepository authorityRepository,
            CacheManager cacheManager, Optional<LdapUserService> ldapUserService, PasswordService passwordService,
            Optional<CIUserManagementService> optionalCIUserManagementService, InstanceMessageSendService instanceMessageSendService, FileService fileService,
            Optional<ScienceEventApi> scienceEventApi, ParticipationVcsAccessTokenService participationVCSAccessTokenService, Optional<LearnerProfileApi> learnerProfileApi,
            SavedPostRepository savedPostRepository, UserSshPublicKeyService userSshPublicKeyService, CourseNotificationSettingService courseNotificationSettingService,
            UserCourseNotificationStatusService userCourseNotificationStatusService, GlobalNotificationSettingService globalNotificationSettingService) {
        this.userCreationService = userCreationService;
        this.userRepository = userRepository;
        this.authorityService = authorityService;
        this.authorityRepository = authorityRepository;
        this.cacheManager = cacheManager;
        this.ldapUserService = ldapUserService;
        this.passwordService = passwordService;
        this.optionalCIUserManagementService = optionalCIUserManagementService;
        this.instanceMessageSendService = instanceMessageSendService;
        this.fileService = fileService;
        this.scienceEventApi = scienceEventApi;
        this.participationVCSAccessTokenService = participationVCSAccessTokenService;
        this.learnerProfileApi = learnerProfileApi;
        this.savedPostRepository = savedPostRepository;
        this.userSshPublicKeyService = userSshPublicKeyService;
        this.courseNotificationSettingService = courseNotificationSettingService;
        this.userCourseNotificationStatusService = userCourseNotificationStatusService;
        this.globalNotificationSettingService = globalNotificationSettingService;
    }

    /**
     * Make sure that the internal artemis admin (in case it is defined in the yml configuration) is available in the database
     */
    @EventListener(FullStartupEvent.class)
    public void applicationReady() {

        try {
            if (artemisInternalAdminUsername.isPresent() && artemisInternalAdminPassword.isPresent()) {
                // authenticate so that db queries are possible
                SecurityUtils.setAuthorizationObject();
                Optional<User> existingInternalAdmin = userRepository.findOneWithGroupsAndAuthoritiesByLogin(artemisInternalAdminUsername.get());
                if (existingInternalAdmin.isPresent()) {
                    log.info("Update internal admin user {}", artemisInternalAdminUsername.get());
                    existingInternalAdmin.get().setPassword(passwordService.hashPassword(artemisInternalAdminPassword.get()));
                    // needs to be mutable --> new HashSet<>(Set.of(...))
                    existingInternalAdmin.get().setAuthorities(new HashSet<>(Set.of(ADMIN_AUTHORITY, new Authority(STUDENT.getAuthority()))));
                    saveUser(existingInternalAdmin.get());
                    updateUserInConnectorsAndAuthProvider(existingInternalAdmin.get(), existingInternalAdmin.get().getLogin(), existingInternalAdmin.get().getGroups(),
                            artemisInternalAdminPassword.get());
                }
                else {
                    log.info("Create internal admin user {}", artemisInternalAdminUsername.get());
                    final var managedUserVM = createManagedUserVm(artemisInternalAdminUsername.get(), artemisInternalAdminPassword.get());
                    userCreationService.createUser(managedUserVM);
                }
            }
        }
        catch (Exception ex) {
            log.error("An error occurred after application startup when creating or updating the admin user or in the LDAP search", ex);
        }
    }

    private ManagedUserVM createManagedUserVm(String login, String password) {
        ManagedUserVM userDto = new ManagedUserVM();
        userDto.setLogin(login);
        userDto.setPassword(password);
        userDto.setActivated(true);
        userDto.setFirstName("Administrator");
        userDto.setLastName("Administrator");
        userDto.setEmail(artemisInternalAdminEmail.orElse("admin@localhost"));
        userDto.setLangKey("en");
        userDto.setCreatedBy("system");
        userDto.setLastModifiedBy("system");
        // needs to be mutable --> new HashSet<>(Set.of(...))
        userDto.setAuthorities(new HashSet<>(Set.of(ADMIN.getAuthority(), STUDENT.getAuthority())));
        userDto.setGroups(new HashSet<>());
        return userDto;
    }

    /**
     * Activate user registration
     *
     * @param key activation key for user registration
     * @return user if user exists otherwise null
     */
    public Optional<User> activateRegistration(String key) {
        log.debug("Activating user for activation key {}", key);
        return userRepository.findOneWithGroupsByActivationKey(key).map(user -> {
            activateUser(user);
            return user;
        });
    }

    /**
     * Activates the user and cancels the automatic cleanup of the account.
     *
     * @param user the non-activated user
     */
    public void activateUser(User user) {
        // Cancel automatic removal of the user since it's activated.
        instanceMessageSendService.sendCancelRemoveNonActivatedUserSchedule(user.getId());
        // activate given user for the registration key.
        userCreationService.activateUser(user);
    }

    /**
     * Reset user password for given reset key
     *
     * @param newPassword new password string
     * @param key         reset key
     * @return user for whom the password was performed
     */
    public Optional<User> completePasswordReset(String newPassword, String key) {
        log.debug("Reset user password for reset key {}", key);
        return userRepository.findOneByResetKey(key).filter(user -> user.getResetDate().isAfter(Instant.now().minusSeconds(86400))).map(user -> {
            user.setPassword(passwordService.hashPassword(newPassword));
            user.setResetKey(null);
            user.setResetDate(null);
            saveUser(user);
            optionalCIUserManagementService.ifPresent(ciUserManagementService -> ciUserManagementService.updateUser(user, newPassword));
            return user;
        });
    }

    /**
     * saves the user and clears the cache
     *
     * @param user the user object that will be saved into the database
     * @return the saved and potentially updated user object
     */
    public User saveUser(User user) {
        clearUserCaches(user);
        log.debug("Save user {}", user);
        return userRepository.save(user);
    }

    /**
     * Set password reset data for a user if eligible
     *
     * @param user user requesting reset
     * @return true if the user is eligible
     */
    public boolean prepareUserForPasswordReset(User user) {
        if (user.getActivated() && user.isInternal()) {
            user.setResetKey(RandomUtil.generateResetKey());
            user.setResetDate(Instant.now());
            saveUser(user);
            return true;
        }
        return false;
    }

    /**
     * Register user and create it only in the internal Artemis database. This is a pure service method without any logic with respect to external systems.
     *
     * @param userDTO  user data transfer object
     * @param password string
     * @return newly registered user or throw registration exception
     */
    public User registerUser(UserDTO userDTO, String password) {
        // Prepare the new user object.
        final var newUser = new User();
        String passwordHash = passwordService.hashPassword(password);
        newUser.setLogin(userDTO.getLogin().toLowerCase());
        // new user gets initially a generated password
        newUser.setPassword(passwordHash);
        newUser.setFirstName(userDTO.getFirstName());
        newUser.setLastName(userDTO.getLastName());
        newUser.setEmail(userDTO.getEmail().toLowerCase());
        newUser.setImageUrl(userDTO.getImageUrl());
        newUser.setLangKey(userDTO.getLangKey());
        // new user is not active
        newUser.setActivated(false);
        // registered users are always internal
        newUser.setInternal(true);
        // new user gets registration key
        newUser.setActivationKey(RandomUtil.generateActivationKey());
        Set<Authority> authorities = new HashSet<>();
        authorityRepository.findById(STUDENT.getAuthority()).ifPresent(authorities::add);
        newUser.setAuthorities(authorities);

        // Find user that has the same login
        Optional<User> optionalExistingUser = userRepository.findOneWithGroupsByLogin(userDTO.getLogin().toLowerCase());
        if (optionalExistingUser.isPresent()) {
            User existingUser = optionalExistingUser.get();
            return handleRegisterUserWithSameLoginAsExistingUser(newUser, existingUser);
        }

        // Find user that has the same email
        optionalExistingUser = userRepository.findOneWithGroupsByEmailIgnoreCase(userDTO.getEmail());
        if (optionalExistingUser.isPresent()) {
            User existingUser = optionalExistingUser.get();

            // An account with the same login is already activated.
            if (existingUser.getActivated()) {
                throw new EmailAlreadyUsedException();
            }

            // The email is different which means that the user wants to re-register the same
            // account with a different email. Block this.
            throw new AccountRegistrationBlockedException(newUser.getEmail());
        }

        // we need to save first so that the user can be found in the database in the subsequent method
        User savedNonActivatedUser = saveUser(newUser);

        // Automatically remove the user if it wasn't activated after a certain amount of time.
        instanceMessageSendService.sendRemoveNonActivatedUserSchedule(savedNonActivatedUser.getId());

        log.debug("Created Information for User: {}", newUser);
        return newUser;
    }

    /**
     * Handles the case where a user registers a new account but a user with the same login already
     * exists in Artemis.
     *
     * @param newUser      the new user
     * @param existingUser the existing user
     * @return the existing non-activated user in Artemis.
     */
    private User handleRegisterUserWithSameLoginAsExistingUser(User newUser, User existingUser) {
        // An account with the same login is already activated.
        if (existingUser.getActivated()) {
            throw new UsernameAlreadyUsedException();
        }

        // The user has the same login and email, but the account is not activated.
        // Return the existing non-activated user so that Artemis can re-send the
        // activation link.
        if (existingUser.getEmail().equals(newUser.getEmail())) {
            // Update the existing user and VCS
            newUser.setId(existingUser.getId());
            User updatedExistingUser = userRepository.save(newUser);

            // Post-pone the cleaning up of the account
            instanceMessageSendService.sendRemoveNonActivatedUserSchedule(updatedExistingUser.getId());
            return updatedExistingUser;
        }

        // The email is different which means that the user wants to re-register the same
        // account with a different email. Block this.
        throw new AccountRegistrationBlockedException(existingUser.getEmail());
    }

    /**
     * Creates a new Artemis user from LDAP in case this is active and a user with the login can be found
     *
     * @param login the login of the user
     * @return a new user or null if the LDAP user was not found
     */
    public Optional<User> createUserFromLdapWithLogin(String login) {
        return findUserInLdap(login, () -> ldapUserService.orElseThrow().findByLogin(login));
    }

    /**
     * Creates a new Artemis user from LDAP in case this is active and a user with the email can be found
     *
     * @param email the email of the user
     * @return a new user or null if the LDAP user was not found
     */
    public Optional<User> createUserFromLdapWithEmail(String email) {
        return findUserInLdap(email, () -> ldapUserService.orElseThrow().findByAnyEmail(email));
    }

    /**
     * Creates a new Artemis user from LDAP in case this is active and a user with the registration number can be found
     *
     * @param registrationNumber the matriculation number of the user
     * @return a new user or null if the LDAP user was not found
     */
    public Optional<User> createUserFromLdapWithRegistrationNumber(String registrationNumber) {
        return findUserInLdap(registrationNumber, () -> ldapUserService.orElseThrow().findByRegistrationNumber(registrationNumber));
    }

    /**
     * Searches the (optional) LDAP service for a user with the given unique user identifier (e.g. login, email, registration number) and supplier function
     * and returns a new Artemis user.
     * Note: this method should only be used if the user does not yet exist in the database
     *
     * @param userIdentifier       the userIdentifier of the user (e.g. login, email, registration number)
     * @param userSupplierFunction the function that supplies the user, typically a call to ldapUserService, e.g. "() -> ldapUserService.orElseThrow().findByLogin(email)"
     * @return a new user or null if the LDAP user was not found
     */
    private Optional<User> findUserInLdap(String userIdentifier, Supplier<Optional<LdapUserDto>> userSupplierFunction) {
        if (!StringUtils.hasText(userIdentifier)) {
            return Optional.empty();
        }
        if (ldapUserService.isPresent()) {
            Optional<LdapUserDto> ldapUserOptional = userSupplierFunction.get();
            if (ldapUserOptional.isPresent()) {
                LdapUserDto ldapUser = ldapUserOptional.get();
                log.info("Ldap User {} has login: {}", ldapUser.getFirstName() + " " + ldapUser.getFirstName(), ldapUser.getLogin());

                // handle edge case, the user already exists in Artemis, but for some reason the values differ
                if (StringUtils.hasText(ldapUser.getLogin())) {
                    // load the user with groups and authorities because they might be needed later
                    var existingUser = userRepository.findOneWithGroupsAndAuthoritiesByLogin(ldapUser.getLogin());
                    if (existingUser.isPresent()) {
                        LdapUserService.syncUserDetails(existingUser.get(), ldapUser);
                        saveUser(existingUser.get());
                        return existingUser;
                    }
                }

                // Use empty password, so that we don't store the credentials of external users in the Artemis DB
                User user = userCreationService.createUser(ldapUser.getLogin(), "", null, ldapUser.getFirstName(), ldapUser.getLastName(), ldapUser.getEmail(),
                        ldapUser.getRegistrationNumber(), null, "en", false);
                // load the user with groups and authorities because they might be needed later
                return userRepository.findOneWithGroupsAndAuthoritiesById(user.getId());
            }
            else {
                log.warn("Ldap User with userIdentifier '{}' not found", userIdentifier);
            }
        }
        return Optional.empty();
    }

    /**
     * Updates the user (and synchronizes its password) and its groups in the connected continuous integration system (e.g. Jenkins if available).
     * Also updates the user groups in the used authentication provider (like {@link LdapAuthenticationProvider}).
     *
     * @param oldUserLogin The username of the user. If the username is updated in the user object, it must be the one before the update in order to find the user in the VCS
     * @param user         The updated user in Artemis (this method assumes that the user including its groups was already saved to the Artemis database)
     * @param oldGroups    The old groups of the user before the update
     * @param newPassword  If provided, the password gets updated
     */
    // TODO: The password can be null but Jenkins requires it to be non null => How do we get the password on update?
    // Or how do we get Jenkins to update the user without recreating it
    public void updateUserInConnectorsAndAuthProvider(User user, String oldUserLogin, Set<String> oldGroups, String newPassword) {
        final var updatedGroups = user.getGroups();
        final var removedGroups = oldGroups.stream().filter(group -> !updatedGroups.contains(group)).collect(Collectors.toSet());
        final var addedGroups = updatedGroups.stream().filter(group -> !oldGroups.contains(group)).collect(Collectors.toSet());
        optionalCIUserManagementService
                .ifPresent(ciUserManagementService -> ciUserManagementService.updateUserAndGroups(oldUserLogin, user, newPassword, addedGroups, removedGroups));
    }

    /**
     * Performs soft-delete on the user based on login string
     *
     * @param login user login string
     */
    public void softDeleteUser(String login) {
        userRepository.findOneWithGroupsByLogin(login).ifPresent(user -> {
            participationVCSAccessTokenService.deleteAllByUserId(user.getId());
            learnerProfileApi.ifPresent(api -> api.deleteProfile(user));
            userSshPublicKeyService.deleteAllByUserId(user.getId());
            globalNotificationSettingService.deleteAllByUserId(user.getId());
            user.setDeleted(true);
            user.setLearnerProfile(null);
            anonymizeUser(user);
            log.warn("Soft Deleted User: {}", user);
        });
    }

    /**
     * Sets the properties of the user to random or dummy values, making it impossible to identify the user.
     * Also updates the user in connectors and auth provider.
     *
     * @param user the user that should be anonymized
     */
    protected void anonymizeUser(User user) {
        final String originalLogin = user.getLogin();
        final Set<String> originalGroups = user.getGroups();
        final String randomPassword = RandomUtil.generatePassword();
        final String userImageString = user.getImageUrl();
        final String anonymizedLogin = lowerCase(RandomUtil.generateRandomAlphanumericString(), Locale.ENGLISH);

        user.setFirstName(USER_FIRST_NAME_AFTER_SOFT_DELETE);
        user.setLastName(USER_LAST_NAME_AFTER_SOFT_DELETE);
        user.setLogin(anonymizedLogin);
        user.setPassword(randomPassword);
        user.setEmail(RandomUtil.generateRandomAlphanumericString() + USER_EMAIL_DOMAIN_AFTER_SOFT_DELETE);
        user.setRegistrationNumber(null);
        user.setImageUrl(null);
        user.setActivated(false);
        user.setGroups(Collections.emptySet());

        List<SavedPost> savedPostsOfUser = savedPostRepository.findSavedPostsByUserId(user.getId());

        if (!savedPostsOfUser.isEmpty()) {
            savedPostRepository.deleteAll(savedPostsOfUser);
        }

        userCourseNotificationStatusService.deleteAllForUser(user.getId());
        courseNotificationSettingService.deleteAllForUser(user.getId());

        userRepository.save(user);
        clearUserCaches(user);
        userRepository.flush();

        scienceEventApi.ifPresent(api -> api.renameIdentity(originalLogin, anonymizedLogin));

        if (userImageString != null) {
            fileService.schedulePathForDeletion(FilePathConverter.fileSystemPathForExternalUri(URI.create(userImageString), FilePathType.PROFILE_PICTURE), 0);
        }

        updateUserInConnectorsAndAuthProvider(user, originalLogin, originalGroups, randomPassword);
    }

    /**
     * Trys to find a user by the internal admin username
     *
     * @return an Optional.emtpy() if no internal admin user is found, otherwise an optional with the internal admin user
     */
    public Optional<User> findInternalAdminUser() {
        if (artemisInternalAdminUsername.isEmpty()) {
            log.warn("The internal admin username is not configured and no internal admin user can be retrieved.");
            return Optional.empty();
        }
        return userRepository.findOneByLogin(artemisInternalAdminUsername.get());
    }

    /**
     * Change password of current user
     *
     * @param currentClearTextPassword cleartext password
     * @param newPassword              new password string
     */
    public void changePassword(String currentClearTextPassword, String newPassword) {
        SecurityUtils.getCurrentUserLogin().flatMap(userRepository::findOneByLogin).ifPresent(user -> {
            String currentPasswordHash = user.getPassword();
            if (!passwordService.checkPasswordMatch(currentClearTextPassword, currentPasswordHash)) {
                throw new PasswordViolatesRequirementsException();
            }
            String newPasswordHash = passwordService.hashPassword(newPassword);
            user.setPassword(newPasswordHash);
            saveUser(user);
            optionalCIUserManagementService.ifPresent(ciUserManagementService -> ciUserManagementService.updateUser(user, newPassword));

            log.debug("Changed password for User: {}", user);
        });
    }

    /**
     * Check the username and password for validity. Throws Exception if invalid.
     *
     * @param username The username to check
     * @param password The password to check
     */
    public void checkUsernameAndPasswordValidityElseThrow(String username, String password) {
        checkUsernameOrThrow(username);
        checkNullablePasswordOrThrow(password);
    }

    private void checkUsernameOrThrow(String username) {
        if (username == null || username.length() < USERNAME_MIN_LENGTH) {
            throw new AccessForbiddenException("The username has to be at least " + USERNAME_MIN_LENGTH + " characters long");
        }
        else if (username.length() > USERNAME_MAX_LENGTH) {
            throw new AccessForbiddenException("The username has to be less than " + USERNAME_MAX_LENGTH + " characters long");
        }
    }

    /**
     * <p>
     * The password can be null, then a random one will be generated ({@code Create}) or it won't be changed ({@code Update}).
     * <p>
     * If the password is not null, its length has to be at least {@code PASSWORD_MIN_LENGTH}.
     *
     * @param password The password to check
     */
    private void checkNullablePasswordOrThrow(String password) {
        if (password == null) {
            return;
        }
        if (password.length() < PASSWORD_MIN_LENGTH) {
            throw new AccessForbiddenException("The password has to be at least " + PASSWORD_MIN_LENGTH + " characters long");
        }
        if (password.length() > PASSWORD_MAX_LENGTH) {
            throw new AccessForbiddenException("The password has to be less than " + PASSWORD_MAX_LENGTH + " characters long");
        }
    }

    private void clearUserCaches(User user) {
        var userCache = cacheManager.getCache(User.class.getName());
        if (userCache != null) {
            userCache.evict(user.getLogin());
        }
    }

    /**
     * delete the group with the given name
     *
     * @param groupName the name of the group which should be deleted
     */
    public void deleteGroup(String groupName) {
        removeGroupFromUsers(groupName);
    }

    /**
     * removes the passed group from all users in the Artemis database, e.g. when the group was deleted
     *
     * @param groupName the group that should be removed from all existing users
     */
    public void removeGroupFromUsers(String groupName) {
        log.info("Remove group {} from users", groupName);
        Set<User> users = userRepository.findAllWithGroupsAndAuthoritiesByDeletedIsFalseAndGroupsContains(groupName);
        log.info("Found {} users with group {}", users.size(), groupName);
        for (User user : users) {
            user.getGroups().remove(groupName);
            saveUser(user);
        }
    }

    /**
     * Add the user to the specified group and update in CIS (like Jenkins) if used, and registers the user to necessary channels
     *
     * @param user  the user
     * @param group the group
     */
    public void addUserToGroup(User user, String group) {
        addUserToGroupInternal(user, group); // internal Artemis database
        // e.g. Jenkins
        optionalCIUserManagementService.ifPresent(ciUserManagementService -> ciUserManagementService.addUserToGroups(user.getLogin(), Set.of(group)));
    }

    /**
     * adds the user to the group only in the Artemis database
     *
     * @param user  the user
     * @param group the group
     */
    private void addUserToGroupInternal(User user, String group) {
        log.debug("Add user {} to group {}", user.getLogin(), group);
        if (!user.getGroups().contains(group)) {
            user.getGroups().add(group);
            user.setAuthorities(authorityService.buildAuthorities(user));
            saveUser(user);
        }
    }

    /**
     * remove the user from the specified group
     *
     * @param user  the user
     * @param group the group
     */
    public void removeUserFromGroup(User user, String group) {
        removeUserFromGroupInternal(user, group); // internal Artemis database
        // e.g. Jenkins
        optionalCIUserManagementService.ifPresent(ciUserManagementService -> ciUserManagementService.removeUserFromGroups(user.getLogin(), Set.of(group)));
    }

    /**
     * remove the user from the specified group
     *
     * @param user  the user
     * @param group the group
     */
    private void removeUserFromGroupInternal(User user, String group) {
        log.info("Remove user {} from group {}", user.getLogin(), group);
        if (user.getGroups().contains(group)) {
            user.getGroups().remove(group);
            user.setAuthorities(authorityService.buildAuthorities(user));
            saveUser(user);
        }
    }

    /**
     * This method first tries to find the student in the internal Artemis user database (because the user is most probably already using Artemis).
     * In case the user cannot be found, we additionally search the (TUM) LDAP in case it is configured properly.
     * <p>
     * Steps:
     * <p>
     * 1) we use the registration number and try to find the student in the Artemis user database
     * 2) if we cannot find the student, we use the registration number and try to find the student in the (TUM) LDAP, create it in the Artemis DB and in a potential external user
     * management system
     * 3) if we cannot find the user in the (TUM) LDAP or the registration number was not set properly, try again using the login
     * 4) if we still cannot find the user, we try again using the email
     *
     * @param registrationNumber the registration number of the user
     * @param login              the login of the user
     * @param email              the email of the user
     * @return the found student, otherwise returns an empty optional
     */
    public Optional<User> findUser(@Nullable String registrationNumber, @Nullable String login, @Nullable String email) {
        if (!StringUtils.hasText(login) && !StringUtils.hasText(email) && !StringUtils.hasText(registrationNumber)) {
            // if none of the three values is specified, the user cannot be found
            return Optional.empty();
        }
        try {
            var optionalUser = findUserInDatabase(registrationNumber, login, email);
            if (optionalUser.isEmpty()) {
                // In this case, the user was NOT found in the database! We can try to create it from the external user management, in case it is configured
                optionalUser = findUserInLdap(registrationNumber, login, email);
            }

            if (optionalUser.isPresent()) {
                return optionalUser;
            }

            log.warn("User with registration number '{}', login '{}' and email '{}' NOT found in Artemis user database NOR in connected LDAP", registrationNumber, login, email);
        }
        catch (Exception ex) {
            log.warn("Error while trying to find user with registration number {}, login {}, email {}", registrationNumber, login, email, ex);
        }
        return Optional.empty();
    }

    /**
     * This method first tries to find the user and then adds the user to the course
     *
     * @param registrationNumber the registration number of the user
     * @param login              the login of the user
     * @param email              the email of the user
     * @param courseGroupName    the courseGroup the user has to be added to
     * @return the found user, otherwise returns an empty optional
     */
    public Optional<User> findUserAndAddToCourse(@Nullable String registrationNumber, @Nullable String login, @Nullable String email, String courseGroupName) {
        var optionalUser = findUser(registrationNumber, login, email);

        if (optionalUser.isPresent()) {
            var user = optionalUser.get();
            // we only need to add the user to the course group, if the user is not yet part of it, otherwise the user cannot access the course
            if (!user.getGroups().contains(courseGroupName)) {
                this.addUserToGroup(user, courseGroupName);
            }
            return optionalUser;
        }

        return Optional.empty();
    }

    private Optional<User> findUserInDatabase(@Nullable String registrationNumber, @Nullable String login, @Nullable String email) {
        Optional<User> optionalUser = Optional.empty();
        if (StringUtils.hasText(login)) {
            optionalUser = userRepository.findUserWithGroupsAndAuthoritiesByLogin(login);
        }
        if (optionalUser.isEmpty() && StringUtils.hasText(email)) {
            optionalUser = userRepository.findUserWithGroupsAndAuthoritiesByEmail(email);
        }
        if (optionalUser.isEmpty() && StringUtils.hasText(registrationNumber)) {
            optionalUser = userRepository.findUserWithGroupsAndAuthoritiesByRegistrationNumber(registrationNumber);
        }
        return optionalUser;
    }

    private Optional<User> findUserInLdap(@Nullable String registrationNumber, @Nullable String login, @Nullable String email) {
        Optional<User> optionalUser = Optional.empty();
        if (StringUtils.hasText(login)) {
            optionalUser = createUserFromLdapWithLogin(login);
        }
        if (optionalUser.isEmpty() && StringUtils.hasText(email)) {
            optionalUser = createUserFromLdapWithEmail(email);
        }
        if (optionalUser.isEmpty() && StringUtils.hasText(registrationNumber)) {
            optionalUser = createUserFromLdapWithRegistrationNumber(registrationNumber);
        }
        return optionalUser;
    }

    public void updateUserLanguageKey(Long userId, String languageKey) {
        userRepository.updateUserLanguageKey(userId, languageKey);
    }

    /**
     * This method first tries to find and then to add each user of the given list to the course
     *
     * @param userDtos users to be added to the course
     * @return a list of not found users
     */
    public List<StudentDTO> importUsers(List<StudentDTO> userDtos) {
        List<StudentDTO> notFoundUsers = new ArrayList<>();
        for (var userDto : userDtos) {
            var optionalStudent = findUser(userDto.registrationNumber(), userDto.login(), userDto.email());
            if (optionalStudent.isEmpty()) {
                notFoundUsers.add(userDto);
            }
        }

        return notFoundUsers;
    }

    /**
     * Get the vcs access token associated with a user and a participation
     *
     * @param user            the user associated with the vcs access token
     * @param participationId the participation's participationId associated with the vcs access token
     *
     * @return the users participation vcs access token, or throws an exception if it does not exist
     */
    public ParticipationVCSAccessToken getParticipationVcsAccessTokenForUserAndParticipationIdOrElseThrow(User user, Long participationId) {
        return participationVCSAccessTokenService.findByUserAndParticipationIdOrElseThrow(user, participationId);
    }

    /**
     * Create a vcs access token associated with a user and a participation, and return it
     *
     * @param user            the user associated with the vcs access token
     * @param participationId the participation's participationId associated with the vcs access token
     *
     * @return the users newly created participation vcs access token, or throws an exception if it already existed
     */
    public ParticipationVCSAccessToken createParticipationVcsAccessTokenForUserAndParticipationIdOrElseThrow(User user, Long participationId) {
        return participationVCSAccessTokenService.createVcsAccessTokenForUserAndParticipationIdOrElseThrow(user, participationId);
    }
}
