package de.tum.cit.aet.artemis.atlas.repository;

import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.profile.LearnerProfile;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

@Conditional(AtlasEnabled.class)
@Lazy
@Repository
public interface LearnerProfileRepository extends ArtemisJpaRepository<LearnerProfile, Long> {

    Optional<LearnerProfile> findByUser(User user);

    default LearnerProfile findByUserElseThrow(User user) {
        return getValueElseThrow(findByUser(user));
    }

    Set<LearnerProfile> findAllByUserIn(Set<User> users);

    @Transactional // ok because of delete
    @Modifying
    void deleteByUser(User user);
}
