package de.tum.cit.aet.artemis.programming.service.ci.notification.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

import de.tum.cit.aet.artemis.programming.dto.BuildJobInterface;
import de.tum.cit.aet.artemis.programming.dto.TestCaseBase;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TestSuiteDTO(String name, double time, int errors, int skipped, int failures, int tests,
        @JsonProperty("testCases") @JsonSetter(nulls = Nulls.AS_EMPTY) List<TestCaseDTO> testCases) implements BuildJobInterface {

    @Override
    @JsonIgnore
    public List<? extends TestCaseBase> failedTests() {
        return testCases.stream().filter(testCase -> !testCase.isSuccessful()).toList();
    }

    @Override
    @JsonIgnore
    public List<? extends TestCaseBase> successfulTests() {
        return testCases.stream().filter(TestCaseDTO::isSuccessful).toList();
    }
}
