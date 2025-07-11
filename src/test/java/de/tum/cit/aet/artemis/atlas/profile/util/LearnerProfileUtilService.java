package de.tum.cit.aet.artemis.atlas.profile.util;

import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.domain.profile.CourseLearnerProfile;
import de.tum.cit.aet.artemis.atlas.domain.profile.LearnerProfile;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.test_repository.UserTestRepository;

@Lazy
@Service
@Profile(SPRING_PROFILE_TEST)
public class LearnerProfileUtilService {

    @Autowired
    private UserTestRepository userTestRepository;

    public void createLearnerProfilesForUsers(String userPrefix) {
        Set<User> users = userTestRepository.findAllByUserPrefix(userPrefix).stream().peek(user -> {
            LearnerProfile learnerProfile = new LearnerProfile();
            learnerProfile.setUser(user);
            user.setLearnerProfile(learnerProfile);
        }).collect(Collectors.toSet());
        userTestRepository.saveAll(users);
    }

    public void createCourseLearnerProfileForUsers(String userPrefix, Set<Course> courses) {
        Set<User> users = userTestRepository.findAllByUserPrefix(userPrefix).stream().peek(user -> {
            LearnerProfile learnerProfile = new LearnerProfile();
            learnerProfile.setUser(user);
            user.setLearnerProfile(learnerProfile);

            learnerProfile.setCourseLearnerProfiles(courses.stream().map(course -> {
                CourseLearnerProfile courseLearnerProfile = new CourseLearnerProfile();
                courseLearnerProfile.setLearnerProfile(learnerProfile);
                courseLearnerProfile.setCourse(course);
                courseLearnerProfile.setAimForGradeOrBonus(1);
                courseLearnerProfile.setRepetitionIntensity(1);
                courseLearnerProfile.setTimeInvestment(1);
                return courseLearnerProfile;
            }).collect(Collectors.toSet()));
        }).collect(Collectors.toSet());
        userTestRepository.saveAll(users);
    }
}
