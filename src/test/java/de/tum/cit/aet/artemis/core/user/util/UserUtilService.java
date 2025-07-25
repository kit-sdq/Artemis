package de.tum.cit.aet.artemis.core.user.util;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.Authority;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.AuthorityRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.user.PasswordService;
import de.tum.cit.aet.artemis.core.test_repository.UserTestRepository;

/**
 * Service responsible for initializing the database with specific testdata related to Users for use in integration tests.
 */
@Lazy
@Service
@Profile(SPRING_PROFILE_TEST)
public class UserUtilService {

    private static final Logger log = LoggerFactory.getLogger(UserUtilService.class);

    private static final Authority userAuthority = new Authority(Role.STUDENT.getAuthority());

    private static final Authority tutorAuthority = new Authority(Role.TEACHING_ASSISTANT.getAuthority());

    private static final Authority editorAuthority = new Authority(Role.EDITOR.getAuthority());

    private static final Authority instructorAuthority = new Authority(Role.INSTRUCTOR.getAuthority());

    private static final Authority adminAuthority = new Authority(Role.ADMIN.getAuthority());

    private static final Set<Authority> studentAuthorities = Set.of(userAuthority);

    private static final Set<Authority> tutorAuthorities = Set.of(userAuthority, tutorAuthority);

    private static final Set<Authority> editorAuthorities = Set.of(userAuthority, tutorAuthority, editorAuthority);

    private static final Set<Authority> instructorAuthorities = Set.of(userAuthority, tutorAuthority, editorAuthority, instructorAuthority);

    private static final Set<Authority> adminAuthorities = Set.of(userAuthority, tutorAuthority, editorAuthority, instructorAuthority, adminAuthority);

    @Autowired
    private AuthorityRepository authorityRepository;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private UserTestRepository userTestRepository;

    /**
     * Changes the currently authorized User to the User with the given username.
     *
     * @param username The username of the User to change to
     */
    public void changeUser(String username) {
        User user = getUserByLogin(username);
        List<GrantedAuthority> grantedAuthorities = new ArrayList<>();
        for (Authority authority : user.getAuthorities()) {
            grantedAuthorities.add(new SimpleGrantedAuthority(authority.getName()));
        }
        org.springframework.security.core.userdetails.User securityContextUser = new org.springframework.security.core.userdetails.User(user.getLogin(), user.getPassword(),
                grantedAuthorities);
        Authentication authentication = new UsernamePasswordAuthenticationToken(securityContextUser, securityContextUser.getPassword(), grantedAuthorities);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        TestSecurityContextHolder.setContext(context);
    }

    /**
     * Creates and saves the given amount of Users with the given arguments.
     *
     * @param loginPrefix              The prefix that will be added in front of every User's username
     * @param groups                   The groups that the Users will be added to
     * @param authorities              The authorities that the Users will have
     * @param amount                   The amount of Users to generate
     * @param registrationNumberPrefix The prefix that will be added in front of every User's registration number
     * @return The List of generated Users
     */
    public List<User> generateActivatedUsersWithRegistrationNumber(String loginPrefix, String[] groups, Set<Authority> authorities, int amount, String registrationNumberPrefix) {
        List<User> generatedUsers = generateAndSaveActivatedUsers(loginPrefix, groups, authorities, amount);
        for (int i = 0; i < generatedUsers.size(); i++) {
            generatedUsers.get(i).setRegistrationNumber(registrationNumberPrefix + "R" + i);
        }
        return generatedUsers;
    }

    /**
     * Creates and saves the given amount of Users with the given arguments.
     *
     * @param loginPrefix A number will be appended to this prefix to create the login
     * @param groups      The groups that the Users will be added to
     * @param authorities The authorities that the Users will have
     * @param amount      The amount of Users to generate
     * @return The List of generated Users
     */
    public List<User> generateAndSaveActivatedUsers(String loginPrefix, String[] groups, Set<Authority> authorities, int amount) {
        return generateAndSaveActivatedUsers(loginPrefix, UserFactory.USER_PASSWORD, groups, authorities, amount);
    }

    /**
     * Creates and saves the given amount of Users with the given arguments.
     *
     * @param loginPrefix        A number will be appended to this prefix to create the login
     * @param commonPasswordHash The password hash that will be set for every User
     * @param groups             The groups that the Users will be added to
     * @param authorities        The authorities that the Users will have
     * @param amount             The amount of Users to generate
     * @return The List of generated Users
     */
    public List<User> generateAndSaveActivatedUsers(String loginPrefix, String commonPasswordHash, String[] groups, Set<Authority> authorities, int amount) {
        List<User> generatedUsers = new ArrayList<>();
        for (int i = 1; i <= amount; i++) {
            var login = loginPrefix + i;
            // the following line either creates the user or resets and existing user to its original state
            User user = createOrReuseExistingUser(login, commonPasswordHash);
            if (groups != null) {
                user.setGroups(Set.of(groups));
                user.setAuthorities(authorities);
            }
            user = userTestRepository.save(user);
            generatedUsers.add(user);
        }
        return generatedUsers;
    }

    /**
     * Updates and saves the Users' registration numbers. The username of the updated Users is a concatenation of the testPrefix + "student" + a number counting from 1 to the size
     * of
     * the registrationNumbers list. Throws an IllegalArgumentException if the Users do not exist.
     *
     * @param registrationNumbers The registration numbers to set
     * @param testPrefix          The prefix to use for the username
     * @return A List of the updated Users
     */
    public List<User> setRegistrationNumberOfStudents(List<String> registrationNumbers, String testPrefix) {
        List<User> students = new ArrayList<>();
        for (int i = 1; i <= registrationNumbers.size(); i++) {
            students.add(setRegistrationNumberOfUserAndSave(testPrefix + "student" + i, registrationNumbers.get(i - 1)));
        }
        return students;
    }

    /**
     * Updates and saves the User's registration number.
     *
     * @param login              The username of the User to update
     * @param registrationNumber The registration number to set
     * @return The updated User
     */
    public User setRegistrationNumberOfUserAndSave(String login, String registrationNumber) {
        User user = getUserByLogin(login);
        return setRegistrationNumberOfUserAndSave(user, registrationNumber);
    }

    /**
     * Updates and saves the User's registration number.
     *
     * @param user               The User to update
     * @param registrationNumber The registration number to set
     * @return The updated User
     */
    public User setRegistrationNumberOfUserAndSave(User user, String registrationNumber) {
        user.setRegistrationNumber(registrationNumber);
        return userTestRepository.save(user);
    }

    /**
     * Updates and saves the user's vcsAccessToken and its expiry date
     *
     * @param user           The User to update
     * @param vcsAccessToken The userVcsAccessToken to set
     * @param expiryDate     The tokens expiry date
     * @return The updated User
     */
    public User setUserVcsAccessTokenAndExpiryDateAndSave(User user, String vcsAccessToken, ZonedDateTime expiryDate) {
        user.setVcsAccessToken(vcsAccessToken);
        user.setVcsAccessTokenExpiryDate(expiryDate);
        return userTestRepository.save(user);
    }

    /**
     * Deletes the userVcsAccessToken from a user
     *
     * @param userWithUserToken The user whose token gets deleted
     */
    public void deleteUserVcsAccessToken(User userWithUserToken) {
        userWithUserToken.setVcsAccessTokenExpiryDate(null);
        userWithUserToken.setVcsAccessToken(null);
        userTestRepository.save(userWithUserToken);
    }

    /**
     * Creates and saves the given amount of Users with the given arguments.
     *
     * @param loginPrefix        The prefix that will be added in front of every User's username
     * @param commonPasswordHash The password hash that will be set for every User
     * @param groups             The groups that the Users will be added to
     * @param authorities        The authorities that the Users will have
     * @param amount             The amount of Users to generate
     * @return The List of generated Users
     */
    public List<User> generateActivatedUsers(String loginPrefix, String commonPasswordHash, String[] groups, Set<Authority> authorities, int amount) {
        return generateActivatedUsers(loginPrefix, commonPasswordHash, groups, authorities, 1, amount);
    }

    /**
     * Creates and saves Users with the given arguments. Creates [to - from + 1] Users.
     *
     * @param loginPrefix        The prefix that will be added in front of every User's username
     * @param commonPasswordHash The password hash that will be set for every User
     * @param groups             The groups that the Users will be added to
     * @param authorities        The authorities that the Users will have
     * @param from               The first number to append to the loginPrefix
     * @param to                 The last number to append to the loginPrefix
     * @return The List of generated Users
     */
    public List<User> generateActivatedUsers(String loginPrefix, String commonPasswordHash, String[] groups, Set<Authority> authorities, int from, int to) {
        List<User> generatedUsers = new ArrayList<>();
        for (int i = from; i <= to; i++) {
            var login = loginPrefix + i;
            // the following line either creates the user or resets and existing user to its original state
            User user = createOrReuseExistingUser(login, commonPasswordHash);
            if (groups != null) {
                user.setGroups(Set.of(groups));
                user.setAuthorities(authorities);
            }
            generatedUsers.add(user);
        }
        return generatedUsers;
    }

    /**
     * Creates and saves a User. If a User with the given username already exists, the existing User is updated and saved.
     *
     * @param login          The username of the User
     * @param hashedPassword The password hash of the User
     * @return The created User
     */
    public User createAndSaveUser(String login, String hashedPassword) {
        User user = UserFactory.generateActivatedUser(login, hashedPassword);
        if (userExistsWithLogin(login)) {
            // save the user with the newly created values (to override previous changes) with the same ID
            user.setId(getUserByLogin(login).getId());
        }
        return userTestRepository.save(user);
    }

    /**
     * Creates a User. If a User with the given username already exists, the newly created User's ID is set to the existing User's ID.
     *
     * @param login          The username of the User
     * @param hashedPassword The password hash of the User
     * @return The created User
     */
    public User createOrReuseExistingUser(String login, String hashedPassword) {
        User user = UserFactory.generateActivatedUser(login, hashedPassword);
        if (userExistsWithLogin(login)) {
            // save the user with the newly created values (to override previous changes) with the same ID
            user.setId(getUserByLogin(login).getId());
        }
        return user;
    }

    /**
     * Creates and saves a User. If a User with the given username already exists, the existing User is updated and saved.
     *
     * @param login The username of the User
     * @return The created User
     */
    public User createAndSaveUser(String login) {
        User user = UserFactory.generateActivatedUser(login);
        if (userExistsWithLogin(login)) {
            // save the user with the newly created values (to override previous changes) with the same ID
            user.setId(getUserByLogin(login).getId());
        }
        return userTestRepository.save(user);
    }

    /**
     * Creates and saves multiple Users given the amounts for each role.
     *
     * @param numberOfStudents    The number of students to create
     * @param numberOfTutors      The number of tutors to create
     * @param numberOfEditors     The number of editors to create
     * @param numberOfInstructors The number of instructors to create
     * @return The List of created Users
     */
    public List<User> addUsers(int numberOfStudents, int numberOfTutors, int numberOfEditors, int numberOfInstructors) {
        return addUsers("", numberOfStudents, numberOfTutors, numberOfEditors, numberOfInstructors);
    }

    /**
     * Creates and saves multiple students, tutors, editors, and instructors given the corresponding numbers. It also creates and saves an admin User if it does not exist.
     * The username of the Users is a concatenation of the prefix, the role (student|tutor|editor|instructor) and a number counting from 1 to the number of Users with the
     * corresponding role. The admin User's username is "admin". This method avoids the accumulation of many Users per Course by removing existing Users before adding new ones.
     *
     * @param prefix              The prefix for the User username
     * @param numberOfStudents    The number of students to create
     * @param numberOfTutors      The number of tutors to create
     * @param numberOfEditors     The number of editors to create
     * @param numberOfInstructors The number of instructors to create
     * @return The List of created Users
     */
    public List<User> addUsers(String prefix, int numberOfStudents, int numberOfTutors, int numberOfEditors, int numberOfInstructors) {
        if (authorityRepository.count() == 0) {
            authorityRepository.saveAll(adminAuthorities);
        }
        log.debug("Generate {} students...", numberOfStudents);
        var students = generateActivatedUsers(prefix + "student", passwordService.hashPassword(UserFactory.USER_PASSWORD),
                new String[] { "tumuser", "testgroup", prefix + "tumuser" }, studentAuthorities, numberOfStudents);
        log.debug("{} students generated. Generate {} tutors...", numberOfStudents, numberOfTutors);
        var tutors = generateActivatedUsers(prefix + "tutor", passwordService.hashPassword(UserFactory.USER_PASSWORD), new String[] { "tutor", "testgroup", prefix + "tutor" },
                tutorAuthorities, numberOfTutors);
        log.debug("{} tutors generated. Generate {} editors...", numberOfTutors, numberOfEditors);
        var editors = generateActivatedUsers(prefix + "editor", passwordService.hashPassword(UserFactory.USER_PASSWORD), new String[] { "editor", "testgroup", prefix + "editor" },
                editorAuthorities, numberOfEditors);
        log.debug("{} editors generated. Generate {} instructors...", numberOfEditors, numberOfInstructors);
        var instructors = generateActivatedUsers(prefix + "instructor", passwordService.hashPassword(UserFactory.USER_PASSWORD),
                new String[] { "instructor", "testgroup", prefix + "instructor" }, instructorAuthorities, numberOfInstructors);
        log.debug("{} instructors generated", numberOfInstructors);

        List<User> usersToAdd = new ArrayList<>();
        usersToAdd.addAll(students);
        usersToAdd.addAll(tutors);
        usersToAdd.addAll(editors);
        usersToAdd.addAll(instructors);

        if (!userExistsWithLogin("admin")) {
            log.debug("Generate admin");
            User admin = UserFactory.generateActivatedUser("admin", passwordService.hashPassword(UserFactory.USER_PASSWORD));
            admin.setGroups(Set.of("admin"));
            admin.setAuthorities(adminAuthorities);
            usersToAdd.add(admin);
            log.debug("Generate admin done");
        }

        // Before adding new users, existing users are removed from courses.
        // Otherwise, the amount users per course constantly increases while running the tests,
        // even though the old users are not needed anymore.
        if (!usersToAdd.isEmpty()) {
            Set<User> currentUsers = userTestRepository.findAllByGroupsNotEmpty();
            log.debug("Removing {} users from all courses...", currentUsers.size());
            currentUsers.forEach(user -> user.setGroups(Set.of()));
            userTestRepository.saveAll(currentUsers);
            log.debug("Removing {} users from all courses. Done", currentUsers.size());
            log.debug("Save {} users to database...", usersToAdd.size());
            usersToAdd = userTestRepository.saveAll(usersToAdd);
            log.debug("Save {} users to database. Done", usersToAdd.size());
        }

        return usersToAdd;
    }

    /**
     * Creates and saves Users with student authorities. Creates [to - from + 1] Users.
     *
     * @param prefix The prefix that will be added in front of every User's username
     * @param from   The first number to append to the loginPrefix
     * @param to     The last number to append to the loginPrefix
     */
    public void addStudents(String prefix, int from, int to) {
        var students = generateActivatedUsers(prefix + "student", passwordService.hashPassword(UserFactory.USER_PASSWORD),
                new String[] { "tumuser", "testgroup", prefix + "tumuser" }, studentAuthorities, from, to);
        userTestRepository.saveAll(students);
    }

    /**
     * Updates and saves the User's registration number setting it to null.
     *
     * @param user The User to update
     */
    public void cleanUpRegistrationNumberForUser(User user) {
        if (user.getRegistrationNumber() == null) {
            return;
        }

        var existingUserWithRegistrationNumber = userTestRepository.findOneWithGroupsAndAuthoritiesByRegistrationNumber(user.getRegistrationNumber());
        if (existingUserWithRegistrationNumber.isPresent()) {
            existingUserWithRegistrationNumber.get().setRegistrationNumber(null);
            userTestRepository.save(existingUserWithRegistrationNumber.get());
        }
    }

    /**
     * Creates and saves a User with instructor authorities, if no User with the given username exists.
     *
     * @param instructorGroup The group that the instructor will be added to
     * @param instructorName  The login of the instructor
     */
    public void addInstructor(final String instructorGroup, final String instructorName) {
        User instructor = createOrReuseExistingUser(instructorName, UserFactory.USER_PASSWORD);
        String[] groups = new String[] { instructorGroup, "testgroup" };
        instructor.setGroups(Set.of(groups));
        instructor.setAuthorities(instructorAuthorities);
        instructor = userTestRepository.save(instructor);
        assertThat(instructor.getId()).as("Instructor has been created").isNotNull();
    }

    /**
     * Creates and saves a User with editor authorities, if no User with the given username exists.
     *
     * @param editorGroup The group that the editor will be added to
     * @param editorName  The login of the editor
     */
    public void addEditor(final String editorGroup, final String editorName) {
        User editor = createOrReuseExistingUser(editorName, UserFactory.USER_PASSWORD);
        String[] groups = new String[] { editorGroup, "testgroup" };
        editor.setGroups(Set.of(groups));
        editor.setAuthorities(editorAuthorities);
        editor = userTestRepository.save(editor);
        assertThat(editor.getId()).as("Editor has been created").isNotNull();
    }

    /**
     * Creates and saves a User with tutor authorities, if no User with the given username exists.
     *
     * @param taGroup The group that the tutor will be added to
     * @param taName  The login of the tutor
     */
    public void addTeachingAssistant(final String taGroup, final String taName) {
        User ta = createOrReuseExistingUser(taName, UserFactory.USER_PASSWORD);
        String[] groups = new String[] { taGroup, "testgroup" };
        ta.setGroups(Set.of(groups));
        ta.setAuthorities(tutorAuthorities);
        ta = userTestRepository.save(ta);
        assertThat(ta.getId()).as("Teaching assistant has been created").isNotNull();
    }

    /**
     * Creates and saves a User with student authorities, if no User with the given username exists.
     *
     * @param studentGroup The group that the student will be added to
     * @param studentName  The login of the student
     */
    public void addStudent(final String studentGroup, final String studentName) {
        User student = createOrReuseExistingUser(studentName, UserFactory.USER_PASSWORD);
        String[] groups = new String[] { studentGroup, "testgroup" };
        student.setGroups(Set.of(groups));
        student.setAuthorities(studentAuthorities);
        student = userTestRepository.save(student);
        assertThat(student.getId()).as("Student has been created").isNotNull();
    }

    /**
     * Gets a user from the database using the provided login but without the authorities.
     * <p>
     * Note: Jackson sometimes fails to deserialize the authorities leading to flaky server tests. The specific
     * circumstances when this happens in still unknown.
     *
     * @param login login to find user with
     * @return user with the provided logih
     */
    public User getUserByLoginWithoutAuthorities(String login) {
        return userTestRepository.findOneByLogin(login).orElseThrow(() -> new IllegalArgumentException("Provided login " + login + " does not exist in database"));
    }

    /**
     * Gets the User with the given username from the database. Throws an IllegalArgumentException if the User does not exist.
     *
     * @param login The username of the User
     * @return The User with eagerly loaded groups and authorities
     */
    public User getUserByLogin(String login) {
        // we convert to lowercase for convenience, because logins have to be lower case
        return userTestRepository.findOneWithGroupsAndAuthoritiesByLogin(login.toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("Provided login " + login + " does not exist in database"));
    }

    /**
     * Checks if a User with the given username exists.
     *
     * @param login The username of the User
     * @return True, if a User with the given login exists, false otherwise
     */
    public boolean userExistsWithLogin(String login) {
        return userTestRepository.findOneByLogin(login).isPresent();
    }

    /**
     * Removes the User with the given username from all Courses and saves the updated User.
     *
     * @param login The login of the User
     */
    public void removeUserFromAllCourses(String login) {
        User user = getUserByLogin(login);
        user.setGroups(Set.of());
        userTestRepository.save(user);
    }

    /**
     * Updates and saves the User's groups.
     *
     * @param userPrefix          The prefix of the User's username
     * @param userSuffix          The suffix of the custom group
     * @param numberOfStudents    The number of students to update
     * @param numberOfTutors      The number of tutors to update
     * @param numberOfEditors     The number of editors to update
     * @param numberOfInstructors The number of instructors to update
     */
    public void adjustUserGroupsToCustomGroups(String userPrefix, String userSuffix, int numberOfStudents, int numberOfTutors, int numberOfEditors, int numberOfInstructors) {
        for (int i = 1; i <= numberOfStudents; i++) {
            var user = getUserByLogin(userPrefix + "student" + i);
            user.setGroups(Set.of(userPrefix + "student" + userSuffix));
            userTestRepository.save(user);
        }
        for (int i = 1; i <= numberOfTutors; i++) {
            var user = getUserByLogin(userPrefix + "tutor" + i);
            user.setGroups(Set.of(userPrefix + "tutor" + userSuffix));
            userTestRepository.save(user);
        }
        for (int i = 1; i <= numberOfEditors; i++) {
            var user = getUserByLogin(userPrefix + "editor" + i);
            user.setGroups(Set.of(userPrefix + "editor" + userSuffix));
            userTestRepository.save(user);
        }
        for (int i = 1; i <= numberOfInstructors; i++) {
            var user = getUserByLogin(userPrefix + "instructor" + i);
            user.setGroups(Set.of(userPrefix + "instructor" + userSuffix));
            userTestRepository.save(user);
        }
    }
}
