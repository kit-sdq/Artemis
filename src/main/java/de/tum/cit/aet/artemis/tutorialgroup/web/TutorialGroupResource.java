package de.tum.cit.aet.artemis.tutorialgroup.web;

import static de.tum.cit.aet.artemis.core.util.DateUtil.isIso8601DateString;
import static de.tum.cit.aet.artemis.core.util.DateUtil.isIso8601TimeString;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.BadRequestException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.course_notifications.TutorialGroupAssignedNotification;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.TutorialGroupDeletedNotification;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.TutorialGroupUnassignedNotification;
import de.tum.cit.aet.artemis.communication.service.CourseNotificationService;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.StudentDTO;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastInstructorInCourse;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.tutorialgroup.api.TutorialGroupRegistrationApi;
import de.tum.cit.aet.artemis.tutorialgroup.config.TutorialGroupEnabled;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupRegistration;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupRegistrationType;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSchedule;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupsConfiguration;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupRepository;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupsConfigurationRepository;
import de.tum.cit.aet.artemis.tutorialgroup.service.TutorialGroupChannelManagementService;
import de.tum.cit.aet.artemis.tutorialgroup.service.TutorialGroupScheduleService;
import de.tum.cit.aet.artemis.tutorialgroup.service.TutorialGroupService;

@Conditional(TutorialGroupEnabled.class)
@Lazy
@RestController
@RequestMapping("api/tutorialgroup/")
public class TutorialGroupResource {

    private static final String TITLE_REGEX = "^[a-zA-Z0-9]{1}[a-zA-Z0-9- ]{0,19}$";

    public static final String ENTITY_NAME = "tutorialGroup";

    private static final Logger log = LoggerFactory.getLogger(TutorialGroupResource.class);

    private final TutorialGroupService tutorialGroupService;

    private final TutorialGroupRepository tutorialGroupRepository;

    private final CourseRepository courseRepository;

    private final UserRepository userRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final TutorialGroupsConfigurationRepository tutorialGroupsConfigurationRepository;

    private final TutorialGroupScheduleService tutorialGroupScheduleService;

    private final TutorialGroupChannelManagementService tutorialGroupChannelManagementService;

    private final CourseNotificationService courseNotificationService;

    private final TutorialGroupRegistrationApi tutorialGroupRegistrationApi;

    public TutorialGroupResource(AuthorizationCheckService authorizationCheckService, UserRepository userRepository, CourseRepository courseRepository,
            TutorialGroupService tutorialGroupService, TutorialGroupRepository tutorialGroupRepository, TutorialGroupsConfigurationRepository tutorialGroupsConfigurationRepository,
            TutorialGroupScheduleService tutorialGroupScheduleService, TutorialGroupChannelManagementService tutorialGroupChannelManagementService,
            CourseNotificationService courseNotificationService, TutorialGroupRegistrationApi tutorialGroupRegistrationApi) {
        this.tutorialGroupService = tutorialGroupService;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.tutorialGroupRepository = tutorialGroupRepository;
        this.tutorialGroupsConfigurationRepository = tutorialGroupsConfigurationRepository;
        this.tutorialGroupScheduleService = tutorialGroupScheduleService;
        this.tutorialGroupChannelManagementService = tutorialGroupChannelManagementService;
        this.courseNotificationService = courseNotificationService;
        this.tutorialGroupRegistrationApi = tutorialGroupRegistrationApi;
    }

    /**
     * GET /tutorial-groups/:tutorialGroupId/title : Returns the title of the tutorial-group with the given id
     * <p>
     * NOTE: Used by entity-title service in the client to resolve the title of a tutorial group for breadcrumbs
     *
     * @param tutorialGroupId the id of the tutorial group
     * @return ResponseEntity with status 200 (OK) and with body containing the title of the tutorial group
     */
    @GetMapping("tutorial-groups/{tutorialGroupId}/title")
    @EnforceAtLeastStudent
    public ResponseEntity<String> getTitle(@PathVariable Long tutorialGroupId) {
        log.debug("REST request to get title of TutorialGroup : {}", tutorialGroupId);
        return tutorialGroupRepository.getTutorialGroupTitle(tutorialGroupId).map(ResponseEntity::ok)
                .orElseThrow(() -> new EntityNotFoundException("TutorialGroup", tutorialGroupId));
    }

    /**
     * GET /courses/:courseId/tutorial-groups/campus-values : gets the campus values used for the tutorial groups of all tutorials where user is instructor
     * Note: Used for autocomplete in the client tutorial form
     *
     * @param courseId the id of the course to which the tutorial groups belong to
     * @return ResponseEntity with status 200 (OK) and with body containing the unique campus values of all tutorials where user is instructor
     */
    @GetMapping("courses/{courseId}/tutorial-groups/campus-values")
    @EnforceAtLeastInstructor
    public ResponseEntity<Set<String>> getUniqueCampusValues(@PathVariable Long courseId) {
        log.debug("REST request to get unique campus values used for tutorial groups in course : {}", courseId);
        var course = courseRepository.findByIdElseThrow(courseId);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, user);
        return ResponseEntity.ok(tutorialGroupRepository.findAllUniqueCampusValuesInRegisteredCourse(user.getGroups()));
    }

    /**
     * GET /courses/:courseId/tutorial-groups/language-values : gets the language values used for the tutorial groups of all tutorials where user is instructor
     * Note: Used for autocomplete in the client tutorial form
     *
     * @param courseId the id of the course to which the tutorial groups belong to
     * @return ResponseEntity with status 200 (OK) and with body containing the unique language values of all tutorials where user is instructor
     */
    @GetMapping("courses/{courseId}/tutorial-groups/language-values")
    @EnforceAtLeastInstructor
    public ResponseEntity<Set<String>> getUniqueLanguageValues(@PathVariable Long courseId) {
        log.debug("REST request to get unique language values used for tutorial groups in course : {}", courseId);
        var course = courseRepository.findByIdElseThrow(courseId);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, user);
        return ResponseEntity.ok(tutorialGroupRepository.findAllUniqueLanguageValuesInRegisteredCourse(user.getGroups()));
    }

    /**
     * GET /courses/:courseId/tutorial-groups: gets the tutorial groups of the specified course.
     *
     * @param courseId the id of the course to which the tutorial groups belong to
     * @return the ResponseEntity with status 200 (OK) and with body containing the tutorial groups of the course
     */
    @GetMapping("courses/{courseId}/tutorial-groups")
    @EnforceAtLeastStudent
    public ResponseEntity<List<TutorialGroup>> getAllForCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all tutorial groups of course with id: {}", courseId);
        var course = courseRepository.findByIdElseThrow(courseId);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);
        // ToDo: Optimization Idea: Do not send all registered student information but just the number in a DTO
        var tutorialGroups = tutorialGroupService.findAllForCourse(course, user);
        return ResponseEntity.ok(new ArrayList<>(tutorialGroups));
    }

    /**
     * GET /courses/{courseId}/tutorial-groups/:tutorialGroupId : gets the tutorial group with the specified id.
     *
     * @param courseId        the id of the course to which the tutorial group belongs to
     * @param tutorialGroupId the id of the tutorial group to retrieve
     * @return ResponseEntity with status 200 (OK) and with body the tutorial group
     */
    @GetMapping("courses/{courseId}/tutorial-groups/{tutorialGroupId}")
    @EnforceAtLeastStudent
    public ResponseEntity<TutorialGroup> getOneOfCourse(@PathVariable Long courseId, @PathVariable Long tutorialGroupId) {
        log.debug("REST request to get tutorial group: {} of course: {}", tutorialGroupId, courseId);
        var course = courseRepository.findByIdElseThrow(courseId);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);
        var tutorialGroup = tutorialGroupService.getOneOfCourse(course, user, tutorialGroupId);
        return ResponseEntity.ok().body(tutorialGroup);
    }

    /**
     * POST /courses/:courseId/tutorial-groups : creates a new tutorial group.
     *
     * @param courseId      the id of the course to which the tutorial group should be added
     * @param tutorialGroup the tutorial group that should be created
     * @return ResponseEntity with status 201 (Created) and in the body the new tutorial group
     */
    @PostMapping("courses/{courseId}/tutorial-groups")
    @EnforceAtLeastInstructor
    public ResponseEntity<TutorialGroup> create(@PathVariable Long courseId, @RequestBody @Valid TutorialGroup tutorialGroup) throws URISyntaxException {
        log.debug("REST request to create TutorialGroup: {} in course: {}", tutorialGroup, courseId);
        if (tutorialGroup.getId() != null) {
            throw new BadRequestException("A new tutorial group cannot already have an ID");
        }
        var course = courseRepository.findByIdElseThrow(courseId);
        var responsibleUser = userRepository.getUserWithGroupsAndAuthorities();
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, responsibleUser);

        Optional<TutorialGroupsConfiguration> configurationOptional = tutorialGroupsConfigurationRepository.findByCourseIdWithEagerTutorialGroupFreePeriods(courseId);
        if (configurationOptional.isEmpty()) {
            throw new BadRequestException("The course has no tutorial groups configuration");
        }
        var configuration = configurationOptional.get();
        if (configuration.getCourse().getTimeZone() == null) {
            throw new BadRequestException("The course has no time zone");
        }

        tutorialGroup.setCourse(course);
        trimStringFields(tutorialGroup);
        checkTitleIsValid(tutorialGroup);

        // persist first without schedule
        TutorialGroupSchedule tutorialGroupSchedule = tutorialGroup.getTutorialGroupSchedule();
        if (tutorialGroupSchedule != null) {
            checkScheduleDateAndTimeFormatAreValid(tutorialGroupSchedule);
        }

        tutorialGroup.setTutorialGroupSchedule(null);
        TutorialGroup persistedTutorialGroup = tutorialGroupRepository.save(tutorialGroup);

        // persist the schedule and generate the sessions
        if (tutorialGroupSchedule != null) {
            tutorialGroupScheduleService.saveScheduleAndGenerateScheduledSessions(configuration, persistedTutorialGroup, tutorialGroupSchedule);
            persistedTutorialGroup.setTutorialGroupSchedule(tutorialGroupSchedule);
        }

        if (tutorialGroup.getTeachingAssistant() != null) {
            // Note: We have to load the teaching assistants from database otherwise languageKey is not defined and email sending fails
            var taFromDatabase = userRepository.findOneByLogin(tutorialGroup.getTeachingAssistant().getLogin());
            taFromDatabase.ifPresent(user -> {
                if (!Objects.equals(user.getId(), responsibleUser.getId())) {
                    var tutorialGroupAssignedNotification = new TutorialGroupAssignedNotification(course.getId(), course.getTitle(), course.getCourseIcon(),
                            tutorialGroup.getTitle(), tutorialGroup.getId(), responsibleUser.getName());

                    courseNotificationService.sendCourseNotification(tutorialGroupAssignedNotification, List.of(user));
                }
            });
        }

        if (configuration.getUseTutorialGroupChannels()) {
            tutorialGroupChannelManagementService.createChannelForTutorialGroup(persistedTutorialGroup);
        }

        return ResponseEntity.created(new URI("/api/tutorialgroup/courses/" + courseId + "/tutorial-groups/" + persistedTutorialGroup.getId()))
                .body(TutorialGroup.preventCircularJsonConversion(persistedTutorialGroup));
    }

    /**
     * DELETE /courses/:courseId/tutorial-groups/:tutorialGroupId : delete a tutorial group.
     *
     * @param courseId        the id of the course to which the tutorial group belongs to
     * @param tutorialGroupId the id of the tutorial group to delete
     * @return the ResponseEntity with status 204 (NO_CONTENT)
     */
    @DeleteMapping("courses/{courseId}/tutorial-groups/{tutorialGroupId}")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> delete(@PathVariable Long courseId, @PathVariable Long tutorialGroupId) {
        log.info("REST request to delete a TutorialGroup: {} of course: {}", tutorialGroupId, courseId);
        var tutorialGroupFromDatabase = this.tutorialGroupRepository.findByIdWithTeachingAssistantAndRegistrationsElseThrow(tutorialGroupId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, tutorialGroupFromDatabase.getCourse(), null);
        checkEntityIdMatchesPathIds(tutorialGroupFromDatabase, Optional.of(courseId), Optional.of(tutorialGroupId));
        tutorialGroupChannelManagementService.deleteTutorialGroupChannel(tutorialGroupFromDatabase);
        tutorialGroupRepository.deleteById(tutorialGroupFromDatabase.getId());

        // Notify users
        var course = tutorialGroupFromDatabase.getCourse();
        var currentUser = userRepository.getUser();
        var tutorialGroupDeletedNotification = new TutorialGroupDeletedNotification(course.getId(), course.getTitle(), course.getCourseIcon(), tutorialGroupFromDatabase.getTitle(),
                tutorialGroupFromDatabase.getId(), currentUser.getName());
        courseNotificationService.sendCourseNotification(tutorialGroupDeletedNotification,
                findUsersToNotify(tutorialGroupFromDatabase).stream().filter((user -> !Objects.equals(currentUser.getId(), user.getId()))).toList());

        return ResponseEntity.noContent().build();
    }

    /**
     * A DTO representing an updated tutorial group with an optional notification text about the update
     *
     * @param tutorialGroup                  the updated tutorial group
     * @param notificationText               the optional notification text
     * @param updateTutorialGroupChannelName whether the tutorial group channel name should be updated with the new tutorial group title or not
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record TutorialGroupUpdateDTO(@Valid @NotNull TutorialGroup tutorialGroup, @Size(min = 1, max = 1000) @Nullable String notificationText,
            @Nullable Boolean updateTutorialGroupChannelName) {
    }

    /**
     * PUT /courses/:courseId/tutorial-groups/:tutorialGroupId : Updates an existing tutorial group
     *
     * @param courseId               the id of the course to which the tutorial group belongs to
     * @param tutorialGroupId        the id of the tutorial group to update
     * @param tutorialGroupUpdateDTO dto containing the tutorial group to update and the optional notification text
     * @return the ResponseEntity with status 200 (OK) and with body the updated tutorial group
     */
    @PutMapping("courses/{courseId}/tutorial-groups/{tutorialGroupId}")
    @EnforceAtLeastInstructor
    public ResponseEntity<TutorialGroup> update(@PathVariable long courseId, @PathVariable long tutorialGroupId,
            @RequestBody @Valid TutorialGroupUpdateDTO tutorialGroupUpdateDTO) {
        TutorialGroup updatedTutorialGroup = tutorialGroupUpdateDTO.tutorialGroup();
        log.debug("REST request to update TutorialGroup : {}", updatedTutorialGroup);
        if (updatedTutorialGroup.getId() == null) {
            throw new BadRequestException("A tutorial group cannot be updated without an id");
        }
        var oldTutorialGroup = this.tutorialGroupRepository.findByIdWithTeachingAssistantAndRegistrationsElseThrow(tutorialGroupId);
        updatedTutorialGroup.setCourse(oldTutorialGroup.getCourse());
        checkEntityIdMatchesPathIds(oldTutorialGroup, Optional.of(courseId), Optional.of(tutorialGroupId));
        var responsibleUser = userRepository.getUserWithGroupsAndAuthorities();
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, oldTutorialGroup.getCourse(), responsibleUser);

        trimStringFields(updatedTutorialGroup);

        if (updatedTutorialGroup.getTutorialGroupSchedule() != null) {
            checkScheduleDateAndTimeFormatAreValid(updatedTutorialGroup.getTutorialGroupSchedule());
        }

        Optional<TutorialGroupsConfiguration> configurationOptional = tutorialGroupsConfigurationRepository.findByCourseIdWithEagerTutorialGroupFreePeriods(courseId);
        if (configurationOptional.isEmpty()) {
            throw new BadRequestException("The course has no tutorial groups configuration");
        }
        var configuration = configurationOptional.get();
        if (configuration.getCourse().getTimeZone() == null) {
            throw new BadRequestException("The course has no time zone");
        }
        if (!oldTutorialGroup.getTitle().equals(updatedTutorialGroup.getTitle())) {
            checkTitleIsValid(updatedTutorialGroup);
            if (configuration.getUseTutorialGroupChannels() && tutorialGroupUpdateDTO.updateTutorialGroupChannelName()) {
                tutorialGroupChannelManagementService.updateNameOfTutorialGroupChannel(updatedTutorialGroup);
            }
        }

        // Note: We have to load the teaching assistants from database otherwise languageKey is not defined and email sending fails

        var oldTA = oldTutorialGroup.getTeachingAssistant();
        var newTA = updatedTutorialGroup.getTeachingAssistant();

        if (newTA != null && (oldTA == null || !oldTA.equals(newTA))) {
            var newTAFromDatabase = userRepository.findOneByLogin(newTA.getLogin());
            newTAFromDatabase.ifPresent(user -> {
                if (!Objects.equals(user.getId(), responsibleUser.getId())) {
                    var course = updatedTutorialGroup.getCourse();
                    var tutorialGroupAssignedNotification = new TutorialGroupAssignedNotification(course.getId(), course.getTitle(), course.getCourseIcon(),
                            updatedTutorialGroup.getTitle(), updatedTutorialGroup.getId(), responsibleUser.getName());

                    courseNotificationService.sendCourseNotification(tutorialGroupAssignedNotification, List.of(user));
                }
                if (configuration.getUseTutorialGroupChannels()) {
                    tutorialGroupChannelManagementService.addUsersToTutorialGroupChannel(updatedTutorialGroup, Set.of(user));
                    tutorialGroupChannelManagementService.grantUsersModeratorRoleToTutorialGroupChannel(updatedTutorialGroup, Set.of(user));
                }
            });
        }
        if (oldTA != null && (newTA == null || !newTA.equals(oldTA))) {
            var oldTAFromDatabase = userRepository.findOneByLogin(oldTA.getLogin());
            oldTAFromDatabase.ifPresent(user -> {
                if (!Objects.equals(user.getId(), responsibleUser.getId())) {
                    var course = oldTutorialGroup.getCourse();
                    var tutorialGroupUnassignedNotification = new TutorialGroupUnassignedNotification(course.getId(), course.getTitle(), course.getCourseIcon(),
                            oldTutorialGroup.getTitle(), oldTutorialGroup.getId(), responsibleUser.getName());

                    courseNotificationService.sendCourseNotification(tutorialGroupUnassignedNotification, List.of(user));
                }
                if (configuration.getUseTutorialGroupChannels()) {
                    tutorialGroupChannelManagementService.removeUsersFromTutorialGroupChannel(oldTutorialGroup, Set.of(user));
                }
            });
        }

        overrideValues(updatedTutorialGroup, oldTutorialGroup);
        if (oldTutorialGroup.getTutorialGroupSchedule() != null) {
            oldTutorialGroup.getTutorialGroupSchedule().setTutorialGroup(oldTutorialGroup);
        }
        var persistedTutorialGroup = tutorialGroupRepository.save(oldTutorialGroup);
        tutorialGroupScheduleService.updateScheduleIfChanged(configuration, persistedTutorialGroup, Optional.ofNullable(persistedTutorialGroup.getTutorialGroupSchedule()),
                Optional.ofNullable(updatedTutorialGroup.getTutorialGroupSchedule()));
        persistedTutorialGroup = tutorialGroupRepository.findByIdElseThrow(persistedTutorialGroup.getId());

        return ResponseEntity.ok(TutorialGroup.preventCircularJsonConversion(persistedTutorialGroup));
    }

    /**
     * DELETE /courses/:courseId/tutorial-groups/:tutorialGroupId/deregister/:studentLogin : deregister a student from a tutorial group.
     *
     * @param courseId        the id of the course to which the tutorial group belongs to
     * @param tutorialGroupId the id of the tutorial group
     * @param studentLogin    the login of the student to deregister
     * @return the ResponseEntity with status 204 (NO_CONTENT)
     */
    @DeleteMapping("courses/{courseId}/tutorial-groups/{tutorialGroupId}/deregister/{studentLogin:" + Constants.LOGIN_REGEX + "}")
    @EnforceAtLeastTutor
    public ResponseEntity<Void> deregisterStudent(@PathVariable Long courseId, @PathVariable Long tutorialGroupId, @PathVariable String studentLogin) {
        log.debug("REST request to deregister {} student from tutorial group : {}", studentLogin, tutorialGroupId);
        var tutorialGroupFromDatabase = this.tutorialGroupRepository.findByIdElseThrow(tutorialGroupId);
        var responsibleUser = userRepository.getUserWithGroupsAndAuthorities();
        tutorialGroupService.isAllowedToChangeRegistrationsOfTutorialGroup(tutorialGroupFromDatabase, responsibleUser);
        checkEntityIdMatchesPathIds(tutorialGroupFromDatabase, Optional.of(courseId), Optional.of(tutorialGroupId));
        User studentToDeregister = userRepository.getUserWithGroupsAndAuthorities(studentLogin);
        tutorialGroupService.deregisterStudent(studentToDeregister, tutorialGroupFromDatabase, TutorialGroupRegistrationType.INSTRUCTOR_REGISTRATION, responsibleUser);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /courses/:courseId/tutorial-groups/:tutorialGroupId/register/:studentLogin : register a student to a tutorial group.
     *
     * @param courseId        the id of the course to which the tutorial group belongs to
     * @param tutorialGroupId the id of the tutorial group
     * @param studentLogin    the login of the student to register
     * @return the ResponseEntity with status 204 (NO_CONTENT)
     */
    @PostMapping("courses/{courseId}/tutorial-groups/{tutorialGroupId}/register/{studentLogin:" + Constants.LOGIN_REGEX + "}")
    @EnforceAtLeastTutor
    public ResponseEntity<Void> registerStudent(@PathVariable Long courseId, @PathVariable Long tutorialGroupId, @PathVariable String studentLogin) {
        log.debug("REST request to register {} student to tutorial group : {}", studentLogin, tutorialGroupId);
        var tutorialGroupFromDatabase = this.tutorialGroupRepository.findByIdElseThrow(tutorialGroupId);
        var responsibleUser = userRepository.getUserWithGroupsAndAuthorities();
        tutorialGroupService.isAllowedToChangeRegistrationsOfTutorialGroup(tutorialGroupFromDatabase, responsibleUser);
        checkEntityIdMatchesPathIds(tutorialGroupFromDatabase, Optional.of(courseId), Optional.of(tutorialGroupId));
        User userToRegister = userRepository.getUserWithGroupsAndAuthorities(studentLogin);
        if (!userToRegister.getGroups().contains(tutorialGroupFromDatabase.getCourse().getStudentGroupName())) {
            throw new BadRequestAlertException("The user is not a student of the course", ENTITY_NAME, "userNotPartOfCourse");
        }
        // ToDo: Discuss if we change the registration type if registration is done by the tutor itself
        tutorialGroupService.registerStudent(userToRegister, tutorialGroupFromDatabase, TutorialGroupRegistrationType.INSTRUCTOR_REGISTRATION, responsibleUser);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /courses/:courseId/tutorial-groups/:tutorialGroupId/register-multiple" : Register multiple users to the tutorial group
     *
     * @param courseId        the id of the course to which the tutorial group belongs to
     * @param tutorialGroupId the id of the tutorial group to which the users should be registered to
     * @param studentDtos     the list of students who should be registered to the tutorial group
     * @return the list of students who could not be registered for the tutorial group, because they could NOT be found in the Artemis database as students of the tutorial group
     *         course
     */
    @PostMapping("courses/{courseId}/tutorial-groups/{tutorialGroupId}/register-multiple")
    @EnforceAtLeastInstructor
    public ResponseEntity<Set<StudentDTO>> registerMultipleStudentsToTutorialGroup(@PathVariable long courseId, @PathVariable long tutorialGroupId,
            @RequestBody Set<StudentDTO> studentDtos) {
        log.debug("REST request to register {} to tutorial group {}", studentDtos, tutorialGroupId);
        var tutorialGroupFromDatabase = this.tutorialGroupRepository.findByIdElseThrow(tutorialGroupId);
        var responsibleUser = userRepository.getUserWithGroupsAndAuthorities();
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, tutorialGroupFromDatabase.getCourse(), responsibleUser);
        checkEntityIdMatchesPathIds(tutorialGroupFromDatabase, Optional.of(courseId), Optional.of(tutorialGroupId));
        Set<StudentDTO> notFoundStudentDtos = tutorialGroupService.registerMultipleStudents(tutorialGroupFromDatabase, studentDtos,
                TutorialGroupRegistrationType.INSTRUCTOR_REGISTRATION, responsibleUser);
        return ResponseEntity.ok().body(notFoundStudentDtos);
    }

    /**
     * POST /courses/:courseId/tutorial-groups/import: Import tutorial groups and student registrations
     *
     * @param courseId   the id of the course to which the tutorial groups belong
     * @param importDTOs the list registration import DTOsd
     * @return the list of registrations with information about the success of the import sorted by tutorial group title
     */
    @PostMapping("courses/{courseId}/tutorial-groups/import")
    @EnforceAtLeastInstructor
    public ResponseEntity<List<TutorialGroupRegistrationImportDTO>> importRegistrations(@PathVariable Long courseId,
            @RequestBody @Valid Set<TutorialGroupRegistrationImportDTO> importDTOs) {
        log.debug("REST request to import registrations {} to course {}", importDTOs, courseId);
        var courseFromDatabase = this.courseRepository.findByIdElseThrow(courseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, courseFromDatabase, null);
        var registrations = tutorialGroupService.importRegistrations(courseFromDatabase, importDTOs);
        var sortedRegistrations = registrations.stream().sorted(Comparator.comparing(TutorialGroupRegistrationImportDTO::title)).toList();
        return ResponseEntity.ok().body(sortedRegistrations);
    }

    private void trimStringFields(TutorialGroup tutorialGroup) {
        if (tutorialGroup.getTitle() != null) {
            tutorialGroup.setTitle(tutorialGroup.getTitle().trim());
        }
        if (tutorialGroup.getAdditionalInformation() != null) {
            tutorialGroup.setAdditionalInformation(tutorialGroup.getAdditionalInformation().trim());
        }
        if (tutorialGroup.getCampus() != null) {
            tutorialGroup.setCampus(tutorialGroup.getCampus().trim());
        }
    }

    private void checkScheduleDateAndTimeFormatAreValid(TutorialGroupSchedule schedule) {
        if (!isIso8601DateString(schedule.getValidToInclusive()) || !isIso8601DateString(schedule.getValidFromInclusive())) {
            throw new BadRequestException("Schedule valid to and from must be valid ISO 8601 date strings");
        }
        if (!isIso8601TimeString(schedule.getStartTime()) || !isIso8601TimeString(schedule.getEndTime())) {
            throw new BadRequestException("Schedule start and end time must be valid ISO 8601 time strings");
        }
    }

    private void checkTitleIsValid(TutorialGroup tutorialGroup) {
        if (tutorialGroupRepository.existsByTitleAndCourse(tutorialGroup.getTitle(), tutorialGroup.getCourse())) {
            throw new BadRequestException("A tutorial group with this title already exists in the course.");
        }
        if (!tutorialGroup.getTitle().matches(TITLE_REGEX)) {
            throw new BadRequestException("Title can only contain letters, numbers, spaces and dashes.");
        }
    }

    private static void overrideValues(TutorialGroup sourceTutorialGroup, TutorialGroup originalTutorialGroup) {
        originalTutorialGroup.setTitle(sourceTutorialGroup.getTitle());
        originalTutorialGroup.setTeachingAssistant(sourceTutorialGroup.getTeachingAssistant());
        originalTutorialGroup.setAdditionalInformation(sourceTutorialGroup.getAdditionalInformation());
        originalTutorialGroup.setCapacity(sourceTutorialGroup.getCapacity());
        originalTutorialGroup.setIsOnline(sourceTutorialGroup.getIsOnline());
        originalTutorialGroup.setLanguage(sourceTutorialGroup.getLanguage());
        originalTutorialGroup.setCampus(sourceTutorialGroup.getCampus());
    }

    private void checkEntityIdMatchesPathIds(TutorialGroup tutorialGroup, Optional<Long> courseId, Optional<Long> tutorialGroupId) {
        courseId.ifPresent(courseIdValue -> {
            if (!tutorialGroup.getCourse().getId().equals(courseIdValue)) {
                throw new BadRequestAlertException("The courseId in the path does not match the courseId in the tutorial group", ENTITY_NAME, "courseIdMismatch");
            }
        });
        tutorialGroupId.ifPresent(tutorialGroupIdValue -> {
            if (!tutorialGroup.getId().equals(tutorialGroupIdValue)) {
                throw new BadRequestAlertException("The tutorialGroupId in the path does not match the id in the tutorial group", ENTITY_NAME, "tutorialGroupIdMismatch");
            }
        });
    }

    /**
     * GET /courses/:courseId/tutorial-groups/export : Export tutorial groups for a specific course to a CSV file.
     *
     * @param courseId the id of the course for which the tutorial groups should be exported
     * @param fields   the list of fields to include in the CSV export
     * @return the ResponseEntity with status 200 (OK) and the CSV file containing the tutorial groups
     */
    @GetMapping(value = "courses/{courseId}/tutorial-groups/export/csv", produces = "text/csv")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<byte[]> exportTutorialGroupsToCSV(@PathVariable Long courseId, @RequestParam List<String> fields) {
        log.debug("REST request to export TutorialGroups to CSV for course: {}", courseId);
        var course = courseRepository.findByIdElseThrow(courseId);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        String csvContent;
        try {
            csvContent = tutorialGroupService.exportTutorialGroupsToCSV(course, user, fields);
        }
        catch (IOException e) {
            throw new BadRequestException("Error occurred while exporting tutorial groups", e);
        }
        byte[] bytes = csvContent.getBytes(StandardCharsets.UTF_8);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv"));
        headers.setContentDispositionFormData("attachment", "tutorial-groups.csv");
        headers.setContentLength(bytes.length);

        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    /**
     * GET /courses/:courseId/tutorial-groups/export/json : Export tutorial groups to JSON.
     *
     * @param courseId the id of the course to which the tutorial groups belong to
     * @param fields   the fields to be included in the export
     * @return ResponseEntity with the JSON data of the tutorial groups
     */
    @GetMapping(value = "courses/{courseId}/tutorial-groups/export/json", produces = MediaType.APPLICATION_JSON_VALUE)
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<List<TutorialGroupService.TutorialGroupExportDTO>> exportTutorialGroupsToJSON(@PathVariable Long courseId, @RequestParam List<String> fields) {
        log.debug("REST request to export TutorialGroups to JSON for course: {}", courseId);
        var exportInformation = tutorialGroupService.exportTutorialGroupInformation(courseId, fields);
        return ResponseEntity.ok().body(exportInformation);
    }

    /**
     * Describes the Errors that can lead to a failed import of a tutorial group registration
     */
    public enum TutorialGroupImportErrors {
        NO_TITLE, NO_USER_FOUND, MULTIPLE_REGISTRATIONS
    }

    /**
     * Identifies users who should be notified about changes in a tutorial group.
     * This method collects a set of users who need to be notified, including:
     * - All instructors registered to the tutorial group
     * - The teaching assistant of the group (if one exists)
     * Only users with a valid email address are included in the final set.
     *
     * @param tutorialGroup the tutorial group for which to find users to notify
     * @return a set of users who should receive notifications, filtered to include only those with valid email addresses
     */
    private Set<User> findUsersToNotify(TutorialGroup tutorialGroup) {
        var potentiallyInterestedUsers = tutorialGroupRegistrationApi.findAllByTutorialGroupAndType(tutorialGroup, TutorialGroupRegistrationType.INSTRUCTOR_REGISTRATION).stream()
                .map(TutorialGroupRegistration::getStudent);
        if (tutorialGroup.getTeachingAssistant() != null) {
            potentiallyInterestedUsers = Stream.concat(potentiallyInterestedUsers, Stream.of(tutorialGroup.getTeachingAssistant()));
        }
        return potentiallyInterestedUsers.filter(user -> StringUtils.hasText(user.getEmail())).collect(Collectors.toSet());
    }

    /**
     * DTO used for client-server communication in the import of tutorial groups and student registrations from csv files
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record TutorialGroupRegistrationImportDTO(@Nullable String title, @Nullable StudentDTO student, @Nullable Boolean importSuccessful,
            @Nullable TutorialGroupImportErrors error, @Nullable String campus, @Nullable Integer capacity, @Nullable String language, @Nullable String additionalInformation,
            @Nullable Boolean isOnline) {

        public TutorialGroupRegistrationImportDTO withImportResult(boolean importSuccessful, TutorialGroupImportErrors error) {
            return new TutorialGroupRegistrationImportDTO(title(), student(), importSuccessful, error, campus(), capacity(), language(), additionalInformation(), isOnline());
        }

        public TutorialGroupRegistrationImportDTO(@Nullable String title, @Nullable StudentDTO student, @Nullable String campus, @Nullable Integer capacity,
                @Nullable String language, @Nullable String additionalInformation, @Nullable Boolean isOnline) {
            this(title, student, null, null, campus, capacity, language, additionalInformation, isOnline);
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (object == null || getClass() != object.getClass()) {
                return false;
            }

            TutorialGroupRegistrationImportDTO that = (TutorialGroupRegistrationImportDTO) object;

            if (!Objects.equals(title, that.title)) {
                return false;
            }
            return Objects.equals(student, that.student);
        }

        @Override
        public int hashCode() {
            int result = title != null ? title.hashCode() : 0;
            result = 31 * result + (student != null ? student.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return "TutorialGroupRegistrationImportDTO{" + "title='" + title + '\'' + ", student=" + student + ", importSuccessful=" + importSuccessful + ", error=" + error
                    + ", campus=" + campus + ", capacity=" + capacity + ", language=" + language + ", additionalInformation=" + additionalInformation + ", isOnline=" + isOnline
                    + '}';
        }
    }
}
