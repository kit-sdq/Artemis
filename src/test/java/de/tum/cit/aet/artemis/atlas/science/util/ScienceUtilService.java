package de.tum.cit.aet.artemis.atlas.science.util;

import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Comparator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.domain.science.ScienceEvent;
import de.tum.cit.aet.artemis.atlas.domain.science.ScienceEventType;
import de.tum.cit.aet.artemis.atlas.test_repository.ScienceEventTestRepository;

@Lazy
@Service
@Profile(SPRING_PROFILE_TEST)
public class ScienceUtilService {

    @Autowired
    private ScienceEventTestRepository scienceEventRepository;

    /**
     * Creates a science event with the passed type and resource id.
     *
     * @param identity   The login of the user associated with the event.
     * @param type       The type of the event.
     * @param resourceId The id of the resource associated with the event.
     * @param timestamp  The timestamp of the event.
     */
    public ScienceEvent createScienceEvent(String identity, ScienceEventType type, Long resourceId, ZonedDateTime timestamp) {
        ScienceEvent event = new ScienceEvent();
        event.setIdentity(identity);
        event.setTimestamp(timestamp);
        event.setType(type);
        event.setResourceId(resourceId);
        return scienceEventRepository.save(event);
    }

    /**
     * Comparator for comparing two science events.
     * Allows for a 500ns difference between the timestamps due to the reimport from the csv export.
     */
    public static Comparator<ScienceEvent> scienceEventComparator = Comparator.comparing(ScienceEvent::getResourceId).thenComparing(ScienceEvent::getType)
            .thenComparing((ScienceEvent e1, ScienceEvent e2) -> {
                Duration duration = Duration.between(e1.getTimestamp(), e2.getTimestamp());
                return Math.abs(duration.toNanos()) < 1e5 ? 0 : duration.isNegative() ? -1 : 1;
            }).thenComparing(ScienceEvent::getIdentity);
}
