package de.tum.in.www1.artemis.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Wrapper class for a two-component statistic
 * depending on the due-date of an exercise.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DueDateStat(long inTime, long late) {
}
