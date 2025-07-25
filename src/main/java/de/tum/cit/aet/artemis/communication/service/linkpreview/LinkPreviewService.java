package de.tum.cit.aet.artemis.communication.service.linkpreview;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.dto.LinkPreviewDTO;

/**
 * Service for retrieving meta information from a given url.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class LinkPreviewService {

    private static final Logger log = LoggerFactory.getLogger(LinkPreviewService.class);

    /**
     * Retrieves meta information from a given url.
     *
     * @param url the url to parse
     * @return a String containing the meta information
     */
    @Cacheable(value = "linkPreview", key = "#url", unless = "#result == null")
    public LinkPreviewDTO getLinkPreview(String url) {
        log.debug("Parsing html meta elements from url: {}", url);

        // Create a new OgParser instance and parse the html meta elements, then get the OpenGraph object
        OgParser ogParser = new OgParser(new OgMetaElementHtmlParser());
        OpenGraph openGraph = ogParser.getOpenGraphOf(url);

        Content titleContent = openGraph.getContentOf("title");
        Content descriptionContent = openGraph.getContentOf("description");
        Content imageContent = openGraph.getContentOf("image");
        Content urlContent = openGraph.getContentOf("url");

        // Return a LinkPreviewDTO object containing the meta information if all of the required meta elements are present
        if (titleContent != null && descriptionContent != null && imageContent != null && urlContent != null) {
            return new LinkPreviewDTO(titleContent.value(), descriptionContent.value(), imageContent.value(), urlContent.value());
        }
        return new LinkPreviewDTO(null, null, null, null);
    }
}
