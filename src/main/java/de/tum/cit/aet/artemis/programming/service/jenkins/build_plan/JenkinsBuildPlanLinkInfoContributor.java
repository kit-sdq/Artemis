package de.tum.cit.aet.artemis.programming.service.jenkins.build_plan;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_JENKINS;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.config.Constants;

@Component
@Lazy
@Profile(PROFILE_JENKINS)
public class JenkinsBuildPlanLinkInfoContributor implements InfoContributor {

    @Value("${artemis.continuous-integration.url}")
    private URI jenkinsServerUri;

    @Override
    public void contribute(Info.Builder builder) {
        final var buildPlanURLTemplate = jenkinsServerUri + "/job/{projectKey}/job/{buildPlanId}";
        builder.withDetail(Constants.INFO_BUILD_PLAN_URL_DETAIL, buildPlanURLTemplate);
    }
}
