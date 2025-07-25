package de.tum.cit.aet.artemis.tutorialgroup.test_repository;

import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSchedule;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupScheduleRepository;

@Lazy
@Repository
@Primary
public interface TutorialGroupScheduleTestRepository extends TutorialGroupScheduleRepository {

    Optional<TutorialGroupSchedule> findByTutorialGroupId(Long tutorialGroupId);
}
