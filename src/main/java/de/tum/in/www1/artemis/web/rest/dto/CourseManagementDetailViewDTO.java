package de.tum.in.www1.artemis.web.rest.dto;

public class CourseManagementDetailViewDTO {

    private Integer numberOfStudentsInCourse;

    private Integer numberOfTeachingAssistantsInCourse;

    private Integer numberOfEditorsInCourse;

    private Integer numberOfInstructorsInCourse;

    // Total Assessment
    private Double currentPercentageAssessments;

    private Long currentAbsoluteAssessments;

    private Long currentMaxAssessments;

    // Total Complaints
    private Double currentPercentageComplaints;

    private Long currentAbsoluteComplaints;

    private Long currentMaxComplaints;

    // More Feedback Request
    private Double currentPercentageMoreFeedbacks;

    private Long currentAbsoluteMoreFeedbacks;

    private Long currentMaxMoreFeedbacks;

    // Average Student Score
    private Double currentPercentageAverageScore;

    private Double currentAbsoluteAverageScore;

    private Double currentMaxAverageScore;

    private Integer[] activeStudents;

    public Integer getNumberOfStudentsInCourse() {
        return numberOfStudentsInCourse;
    }

    public void setNumberOfStudentsInCourse(Integer numberOfStudentsInCourse) {
        this.numberOfStudentsInCourse = numberOfStudentsInCourse;
    }

    public Integer getNumberOfTeachingAssistantsInCourse() {
        return numberOfTeachingAssistantsInCourse;
    }

    public void setNumberOfTeachingAssistantsInCourse(Integer numberOfTeachingAssistantsInCourse) {
        this.numberOfTeachingAssistantsInCourse = numberOfTeachingAssistantsInCourse;
    }

    public Integer getNumberOfEditorsInCourse() {
        return numberOfEditorsInCourse;
    }

    public void setNumberOfEditorsInCourse(Integer numberOfEditorsInCourse) {
        this.numberOfEditorsInCourse = numberOfEditorsInCourse;
    }

    public Integer getNumberOfInstructorsInCourse() {
        return numberOfInstructorsInCourse;
    }

    public void setNumberOfInstructorsInCourse(Integer numberOfInstructorsInCourse) {
        this.numberOfInstructorsInCourse = numberOfInstructorsInCourse;
    }

    public Double getCurrentPercentageAssessments() {
        return currentPercentageAssessments;
    }

    public void setCurrentPercentageAssessments(Double currentPercentageAssessments) {
        this.currentPercentageAssessments = currentPercentageAssessments;
    }

    public Long getCurrentAbsoluteAssessments() {
        return currentAbsoluteAssessments;
    }

    public void setCurrentAbsoluteAssessments(Long currentAbsoluteAssessments) {
        this.currentAbsoluteAssessments = currentAbsoluteAssessments;
    }

    public Long getCurrentMaxAssessments() {
        return currentMaxAssessments;
    }

    public void setCurrentMaxAssessments(Long currentMaxAssessments) {
        this.currentMaxAssessments = currentMaxAssessments;
    }

    public Double getCurrentPercentageComplaints() {
        return currentPercentageComplaints;
    }

    public void setCurrentPercentageComplaints(Double currentPercentageComplaints) {
        this.currentPercentageComplaints = currentPercentageComplaints;
    }

    public Long getCurrentAbsoluteComplaints() {
        return currentAbsoluteComplaints;
    }

    public void setCurrentAbsoluteComplaints(Long currentAbsoluteComplaints) {
        this.currentAbsoluteComplaints = currentAbsoluteComplaints;
    }

    public Long getCurrentMaxComplaints() {
        return currentMaxComplaints;
    }

    public void setCurrentMaxComplaints(Long currentMaxComplaints) {
        this.currentMaxComplaints = currentMaxComplaints;
    }

    public Double getCurrentPercentageMoreFeedbacks() {
        return currentPercentageMoreFeedbacks;
    }

    public void setCurrentPercentageMoreFeedbacks(Double currentPercentageMoreFeedbacks) {
        this.currentPercentageMoreFeedbacks = currentPercentageMoreFeedbacks;
    }

    public Long getCurrentAbsoluteMoreFeedbacks() {
        return currentAbsoluteMoreFeedbacks;
    }

    public void setCurrentAbsoluteMoreFeedbacks(Long currentAbsoluteMoreFeedbacks) {
        this.currentAbsoluteMoreFeedbacks = currentAbsoluteMoreFeedbacks;
    }

    public Long getCurrentMaxMoreFeedbacks() {
        return currentMaxMoreFeedbacks;
    }

    public void setCurrentMaxMoreFeedbacks(Long currentMaxMoreFeedbacks) {
        this.currentMaxMoreFeedbacks = currentMaxMoreFeedbacks;
    }

    public Double getCurrentPercentageAverageScore() {
        return currentPercentageAverageScore;
    }

    public void setCurrentPercentageAverageScore(Double currentPercentageAverageScore) {
        this.currentPercentageAverageScore = currentPercentageAverageScore;
    }

    public Double getCurrentAbsoluteAverageScore() {
        return currentAbsoluteAverageScore;
    }

    public void setCurrentAbsoluteAverageScore(Double currentAbsoluteAverageScore) {
        this.currentAbsoluteAverageScore = currentAbsoluteAverageScore;
    }

    public Double getCurrentMaxAverageScore() {
        return currentMaxAverageScore;
    }

    public void setCurrentMaxAverageScore(Double currentMaxAverageScore) {
        this.currentMaxAverageScore = currentMaxAverageScore;
    }

    public Integer[] getActiveStudents() {
        return activeStudents;
    }

    public void setActiveStudents(Integer[] activeStudents) {
        this.activeStudents = activeStudents;
    }
}
