package de.tum.cit.aet.artemis.exercise.service.team;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.StudentsAppearMultipleTimesException;
import de.tum.cit.aet.artemis.core.exception.StudentsNotFoundException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.dto.TeamImportStrategyType;
import de.tum.cit.aet.artemis.exercise.dto.TeamSearchUserDTO;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.exercise.service.ParticipationDeletionService;
import de.tum.cit.aet.artemis.exercise.web.TeamResource;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class TeamService {

    private final TeamRepository teamRepository;

    private final UserRepository userRepository;

    private final ParticipationDeletionService participationDeletionService;

    public TeamService(TeamRepository teamRepository, UserRepository userRepository, ParticipationDeletionService participationDeletionService) {
        this.teamRepository = teamRepository;
        this.userRepository = userRepository;
        this.participationDeletionService = participationDeletionService;
    }

    /**
     * Search for users by login or name in course
     *
     * @param course      Course in which to search students
     * @param exercise    Exercise in which the student might be added to a team
     * @param loginOrName Login or name by which to search students
     * @return users whose login matched
     */
    public List<TeamSearchUserDTO> searchByLoginOrNameInCourseForExerciseTeam(Course course, Exercise exercise, String loginOrName) {
        List<User> users = userRepository.searchByLoginOrNameInGroup(course.getStudentGroupName(), loginOrName);
        List<Long> userIds = users.stream().map(User::getId).toList();

        // Get list of all students (with id of assigned team) that are already assigned to a team for the exercise
        List<long[]> userIdAndTeamIdPairs = teamRepository.findAssignedUserIdsWithTeamIdsByExerciseIdAndUserIds(exercise.getId(), userIds);

        // convert Set<[userId, teamId]> into Map<userId -> teamId>
        Map<Long, Long> userIdToTeamIdMap = userIdAndTeamIdPairs.stream().collect(Collectors.toMap(userIdAndTeamIdPair -> userIdAndTeamIdPair[0], // userId
                userIdAndTeamIdPair -> userIdAndTeamIdPair[1] // teamId
        ));

        // Annotate to which team the user is already assigned to for the given exercise (null if not assigned)
        return users.stream().map(user -> TeamSearchUserDTO.of(user, userIdToTeamIdMap.get(user.getId()))).toList();
    }

    /**
     * Imports the given teams into exercise using the given strategy
     *
     * @param exercise           Exercise in which to import the given teams
     * @param teams              Teams that will be added to exercise
     * @param importStrategyType Type of strategy used to import teams (relevant for conflicts)
     * @return list of all teams that are now in the exercise
     */
    public List<Team> importTeamsFromTeamListIntoExerciseUsingStrategy(Exercise exercise, List<Team> teams, TeamImportStrategyType importStrategyType) {
        TeamImportStrategy teamImportStrategy = getTeamImportStrategy(importStrategyType);
        teamImportStrategy.importTeams(exercise, teams);
        return teamRepository.findAllByExerciseId(exercise.getId());
    }

    /**
     * Imports the teams from the source exercise into destination exercise using the given strategy
     *
     * @param sourceExercise      Exercise from which to copy the existing teams
     * @param destinationExercise Exercise in which to copy the teams from source exercise
     * @param importStrategyType  Type of strategy used to import teams (relevant for conflicts)
     * @return list of all teams that are now in the destination exercise
     */
    public List<Team> importTeamsFromSourceExerciseIntoDestinationExerciseUsingStrategy(Exercise sourceExercise, Exercise destinationExercise,
            TeamImportStrategyType importStrategyType) {
        TeamImportStrategy teamImportStrategy = getTeamImportStrategy(importStrategyType);
        teamImportStrategy.importTeams(sourceExercise, destinationExercise);
        return teamRepository.findAllByExerciseId(destinationExercise.getId());
    }

    /**
     * Converts teams' students with only login or registration number to students on database
     * This is used for replacing students that has incomplete information with their counterpart in the database using
     * their login or registration number as identifier.
     * Login is used as primary identifier and registration number is used as fallback
     *
     * @param course Course in which the users will be searched
     * @param teams  Teams that students are described only by login or visible registration number
     * @return list of all teams that now have registered users
     * @throws BadRequestAlertException             if there is any student without login and registration number
     * @throws StudentsNotFoundException            if there is any student does not exist in course's students
     * @throws StudentsAppearMultipleTimesException if a student appears in multiple teams
     */
    public List<Team> convertTeamsStudentsToUsersInDatabase(Course course, List<Team> teams) {
        // Put all students from given team list into a list of users
        List<User> students = teams.stream().flatMap(team -> team.getStudents().stream()).toList();
        // Put logins of students which have login information into a list
        List<String> logins = students.stream().map(User::getLogin).filter(Objects::nonNull).toList();
        // Put registration numbers of students which have no login information but have registration number into a list
        // Visible registration number is used because that is what available for the consumers of the API
        List<String> registrationNumbers = students.stream().filter(student -> student.getLogin() == null && student.getVisibleRegistrationNumber() != null)
                .map(User::getVisibleRegistrationNumber).toList();
        // If number of students is not same with number of logins and registration numbers combined throw BadRequestAlertException
        // In other words, if there is at least one student without any identifier throw an exception
        if (students.size() != logins.size() + registrationNumbers.size()) {
            throw new BadRequestAlertException("Students do not have an identifier", TeamResource.ENTITY_NAME, "studentIdentifierNotFound", true);
        }

        // Get list of users which has the logins of the students and list of logins with which no user could be found
        // Search users with student group name of the course to only find students of given course
        Pair<List<User>, List<String>> existingStudentsAndNotFoundLoginsPair = getUsersFromLogins(logins, course.getStudentGroupName());
        List<User> existingStudentsWithLogin = existingStudentsAndNotFoundLoginsPair.getFirst();
        List<String> notFoundLogins = existingStudentsAndNotFoundLoginsPair.getSecond();

        // Get list of users which has the registration numbers of the students and list of registration numbers with which no user could be found
        // Give logins as argument to not find the same user again
        // Search users with student group name of the course to only find students of given course
        Pair<List<User>, List<String>> existingStudentsAndNotFoundRegistrationNumbersPair = getUsersFromRegistrationNumbers(registrationNumbers, logins,
                course.getStudentGroupName());
        List<User> existingStudentsWithRegistrationNumber = existingStudentsAndNotFoundRegistrationNumbersPair.getFirst();
        List<String> notFoundRegistrationNumbers = existingStudentsAndNotFoundRegistrationNumbersPair.getSecond();

        // If there is an identifier with which no user could be found,
        // throw StudentsNotFoundException with list of corresponding logins and registration numbers
        if (!notFoundLogins.isEmpty() || !notFoundRegistrationNumbers.isEmpty()) {
            throw new StudentsNotFoundException(notFoundRegistrationNumbers, notFoundLogins);
        }

        // Create a map of logins and corresponding users to efficiently pick up users when replacing the students
        Map<String, User> studentsWithLogin = existingStudentsWithLogin.stream().collect(Collectors.toMap(User::getLogin, Function.identity()));
        // Create a map of registration numbers and corresponding users to efficiently pick up users when replacing the students
        Map<String, User> studentsWithRegistrationNumber = existingStudentsWithRegistrationNumber.stream()
                .collect(Collectors.toMap(User::getRegistrationNumber, Function.identity()));

        // Convert students in given team list with existing users in database that we have put in maps
        return convertTeamsStudentsToUsersInMaps(teams, studentsWithLogin, studentsWithRegistrationNumber);
    }

    /**
     * Returns both students in database that has given logins and groupName, and logins with which no user could be found
     * <p>
     * This is used to find the complete information of users of which we only know logins
     * It also returns the logins with which no user could be found so that the caller of the function can be informed that
     * the given user does not exist or login is wrong
     *
     * @param logins    Logins to find users with
     * @param groupName Group in which users will be searched
     * @return list of users with given logins
     */
    private Pair<List<User>, List<String>> getUsersFromLogins(List<String> logins, String groupName) {
        // Create initial empty list for found students
        List<User> existingStudentsWithLogin = new ArrayList<>();
        // Create initial empty list for logins with which no user could be found
        List<String> notFoundLogins = new ArrayList<>();
        // Check group name is not null, a list of logins is given and it is not empty
        if (groupName != null && logins != null && !logins.isEmpty()) {
            // Find all users whose login is in the given login list and who have the given group name
            existingStudentsWithLogin = userRepository.findAllWithGroupsByDeletedIsFalseAndGroupsContainsAndLoginIn(groupName, new HashSet<>(logins));
            // Get the list of logins of found users
            Set<String> existingLogins = existingStudentsWithLogin.stream().map(User::getLogin).collect(Collectors.toCollection(HashSet::new));
            // Add logins that are in given login list but not in found users to notFoundLogins
            notFoundLogins = logins.stream().filter(login -> !existingLogins.contains(login)).toList();
        }
        // Return both found users and not found logins
        return Pair.of(existingStudentsWithLogin, notFoundLogins);
    }

    /**
     * Returns both students in database that has given registration numbers and group name, and registration numbers with which no user could be found
     * <p>
     * This is used to find the complete information of users of which we only know registration numbers
     * It gets login list as argument as well since registration number is used as a fallback identifier and the same user could be found by login and registration number
     * It throws exception if such a user is found
     * Additionally, it returns registration numbers with which no users could be found so that the caller can be informed that
     * the given user does not exist or registration number is wrong
     *
     * @param registrationNumbers Registration numbers to find users with
     * @param logins              Logins to find if there is any users with given login found, throws error if there is any
     * @param groupName           Group in which users will be searched
     * @return list of users with given registration numbers
     * @throws StudentsAppearMultipleTimesException if any user has one of the given logins
     */
    private Pair<List<User>, List<String>> getUsersFromRegistrationNumbers(List<String> registrationNumbers, List<String> logins, String groupName) {
        // Create initial empty list for found students
        List<User> existingStudentsWithRegistrationNumber = new ArrayList<>();
        // Create initial empty list for registration numbers with which no user could be found
        List<String> notFoundRegistrationNumbers = new ArrayList<>();
        // Check group name is not null, list of logins is given, list of registration numbers is given and it is not empty
        if (groupName != null && logins != null && registrationNumbers != null && !registrationNumbers.isEmpty()) {
            // Find all users whose login is in the given registration number list and who have the given group name
            existingStudentsWithRegistrationNumber = userRepository.findAllWithGroupsByDeletedIsFalseAndGroupsContainsAndRegistrationNumberIn(groupName,
                    new HashSet<>(registrationNumbers));
            // Find users whose login is in given logins
            Set<String> loginsSet = new HashSet<>(logins);
            List<User> usersWhoAppearsMoreThanOnce = existingStudentsWithRegistrationNumber.stream().filter(student -> loginsSet.contains(student.getLogin())).toList();
            // If there is a user whose login is in given logins, throw StudentsAppearMultipleTimesException
            if (!usersWhoAppearsMoreThanOnce.isEmpty()) {
                throw new StudentsAppearMultipleTimesException(usersWhoAppearsMoreThanOnce);
            }
            // Get the list of registration numbers of found users
            Set<String> existingRegistrationNumbers = existingStudentsWithRegistrationNumber.stream().map(User::getRegistrationNumber)
                    .collect(Collectors.toCollection(HashSet::new));
            // Add registration numbers that are in given registration number list but not in found users to notFoundRegistrationNumbers
            notFoundRegistrationNumbers = registrationNumbers.stream().filter(registrationNumber -> !existingRegistrationNumbers.contains(registrationNumber)).toList();
        }
        // Return both found users and not found registration numbers
        return Pair.of(existingStudentsWithRegistrationNumber, notFoundRegistrationNumbers);
    }

    /**
     * Converts teams' students with only login or registration number to students on given maps
     *
     * @param teams                          Course in which the users will be searched
     * @param studentsWithLogin              A map that contains logins as keys and users as values
     * @param studentsWithRegistrationNumber A map that contains registration numbers as keys and users as values
     * @return list of teams that now contains students in given maps
     */
    private List<Team> convertTeamsStudentsToUsersInMaps(List<Team> teams, Map<String, User> studentsWithLogin, Map<String, User> studentsWithRegistrationNumber) {
        List<Team> convertedTeams = new ArrayList<>();

        // For every student in the given team get students list
        // Replace each student with their counterparts in studentsWithLogin or studentsWithRegistrationNumber
        teams.forEach(team -> {
            Set<User> newStudents = new HashSet<>();
            team.getStudents().forEach(student -> {
                // If the student has login get the user from studentsWithLogin map
                if (student.getLogin() != null) {
                    User foundStudent = studentsWithLogin.get(student.getLogin());
                    if (foundStudent != null) {
                        newStudents.add(foundStudent);
                    }
                }
                // If the student does not have login but has a registration number get the user from studentsWithRegistrationNumber
                // Here visible registration number is used because that is what available for the consumers of the API
                else if (student.getVisibleRegistrationNumber() != null) {
                    User foundStudent = studentsWithRegistrationNumber.get(student.getVisibleRegistrationNumber());
                    if (foundStudent != null) {
                        newStudents.add(foundStudent);
                    }
                }
            });
            team.students(newStudents);
            convertedTeams.add(team);
        });
        return convertedTeams;
    }

    /**
     * Returns an instance of TeamImportStrategy based on the given import strategy type (enum)
     *
     * @param importStrategyType Type for which to instantiate a strategy
     * @return an instance of TeamImportStrategy based on the given import strategy type
     */
    private TeamImportStrategy getTeamImportStrategy(TeamImportStrategyType importStrategyType) {
        return switch (importStrategyType) {
            case PURGE_EXISTING -> new PurgeExistingStrategy(teamRepository, participationDeletionService);
            case CREATE_ONLY -> new CreateOnlyStrategy(teamRepository);
        };
    }
}
