package de.tum.cit.aet.artemis.communication.service.notifications.push_notifications;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import de.tum.cit.aet.artemis.communication.domain.push_notification.PushNotificationDeviceType;
import de.tum.cit.aet.artemis.communication.repository.PushNotificationDeviceConfigurationRepository;
import de.tum.cit.aet.artemis.communication.service.CourseNotificationPushProxyService;

/**
 * Handles the sending of iOS Notifications to the Relay Service
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
@EnableAsync(proxyTargetClass = true)
public class ApplePushNotificationService extends PushNotificationService {

    private static final Logger log = LoggerFactory.getLogger(ApplePushNotificationService.class);

    private final PushNotificationDeviceConfigurationRepository repository;

    @Value("${artemis.push-notification-relay:https://hermes-sandbox.artemis.cit.tum.de}")
    private String relayServerBaseUrl;

    public ApplePushNotificationService(CourseNotificationPushProxyService pushProxyService, PushNotificationDeviceConfigurationRepository repository, RestTemplate restTemplate) {
        super(restTemplate, pushProxyService);
        this.repository = repository;
    }

    @Override
    public PushNotificationDeviceConfigurationRepository getRepository() {
        return repository;
    }

    @Override
    public PushNotificationDeviceType getDeviceType() {
        return PushNotificationDeviceType.APNS;
    }

    @Override
    String getRelayBaseUrl() {
        return relayServerBaseUrl;
    }

    @Override
    String getRelayPath() {
        return "/api/push_notification/send_apns";
    }

    @Override
    void sendSpecificNotificationRequestsToEndpoint(List<RelayNotificationRequest> requests, String relayServerBaseUrl) {
        requests.forEach(request -> {
            try {
                String body = mapper.writeValueAsString(request);
                sendRelayRequest(body, relayServerBaseUrl);
            }
            catch (Exception e) {
                log.error("Failed to send push notification to relay server", e);
            }
        });
    }
}
