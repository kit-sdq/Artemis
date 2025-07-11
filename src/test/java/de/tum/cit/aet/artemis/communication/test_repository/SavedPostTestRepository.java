package de.tum.cit.aet.artemis.communication.test_repository;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.communication.repository.SavedPostRepository;

@Lazy
@Repository
@Primary
public interface SavedPostTestRepository extends SavedPostRepository {
}
