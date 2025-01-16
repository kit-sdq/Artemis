package de.tum.cit.aet.artemis.core.domain;

import static de.tum.cit.aet.artemis.core.config.Constants.USERNAME_MAX_LENGTH;
import static de.tum.cit.aet.artemis.core.config.Constants.USERNAME_MIN_LENGTH;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import jakarta.annotation.Nullable;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyProgress;
import de.tum.cit.aet.artemis.atlas.domain.competency.LearningPath;
import de.tum.cit.aet.artemis.atlas.domain.profile.LearnerProfile;
import de.tum.cit.aet.artemis.communication.domain.SavedPost;
import de.tum.cit.aet.artemis.communication.domain.push_notification.PushNotificationDeviceConfiguration;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.exam.domain.ExamUser;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participant;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnitCompletion;
import de.tum.cit.aet.artemis.programming.service.vcs.VcsTokenRenewalService;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupRegistration;

/**
 * A user.
 */
@Entity
@Table(name = "jhi_user")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class User extends AbstractAuditingEntity implements Participant {

    @NotNull
    @Pattern(regexp = Constants.LOGIN_REGEX)
    @Size(min = USERNAME_MIN_LENGTH, max = USERNAME_MAX_LENGTH)
    @Column(length = USERNAME_MAX_LENGTH, unique = true, nullable = false)
    private String login;

    @JsonIgnore
    @Column(name = "password_hash")
    private String password;

    @Size(max = 50)
    @Column(name = "first_name", length = 50)
    private String firstName;

    @Size(max = 50)
    @Column(name = "last_name", length = 50)
    private String lastName;

    @Size(max = 20)
    @Column(name = "registration_number", length = 20)
    @JsonIgnore
    private String registrationNumber;

    // this value is typically null, except the registration number should be explicitly shown in the client
    // currently this is only the case for the course scores page and its csv export, and also for the individual student exam detail
    @Transient
    private String visibleRegistrationNumberTransient = null;

    @Email
    @Size(max = 100)
    @Column(length = 100)
    private String email;

    @NotNull
    @Column(nullable = false)
    private boolean activated = false;

    @NotNull
    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false; // default value

    @Size(min = 2, max = 6)
    @Column(name = "lang_key", length = 6)
    private String langKey;

    @Size(max = 256)
    @Column(name = "image_url", length = 256)
    private String imageUrl;

    @Size(max = 20)
    @Column(name = "activation_key", length = 20)
    @JsonIgnore
    private String activationKey;

    @Size(max = 20)
    @Column(name = "reset_key", length = 20)
    @JsonIgnore
    private String resetKey;

    @Column(name = "reset_date")
    private Instant resetDate = null;

    @Column(name = "last_notification_read")
    private ZonedDateTime lastNotificationRead = null;

    // hides all notifications with a notification date until (before) the given date in the notification sidebar.
    // if the value is null all notifications are displayed
    @Nullable
    @Column(name = "hide_notifications_until")
    private ZonedDateTime hideNotificationsUntil = null;

    @Column(name = "is_internal", nullable = false)
    private boolean isInternal = true;          // default value

    /**
     * The token the user can use to authenticate with the VCS.
     * This token is generated by Artemis when the user is created in the VCS.
     * It will e.g. be included in the repository clone URL.
     */
    @Nullable
    @JsonIgnore
    @Column(name = "vcs_access_token")
    private String vcsAccessToken = null;

    /**
     * The expiry date of the VCS access token.
     * This is used for checking if a access token needs to be renewed.
     *
     * @see VcsTokenRenewalService
     * @see UserRepository#getUsersWithAccessTokenExpirationDateBefore
     */
    @Nullable
    @JsonIgnore
    @Column(name = "vcs_access_token_expiry_date")
    private ZonedDateTime vcsAccessTokenExpiryDate = null;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "user_groups", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "user_groups")
    private Set<String> groups = new HashSet<>();

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<GuidedTourSetting> guidedTourSettings = new HashSet<>();

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY, cascade = CascadeType.REMOVE, orphanRemoval = true)
    private Set<SavedPost> savedPosts = new HashSet<>();

    @ManyToMany
    @JoinTable(name = "jhi_user_authority", joinColumns = { @JoinColumn(name = "user_id", referencedColumnName = "id") }, inverseJoinColumns = {
            @JoinColumn(name = "authority_name", referencedColumnName = "name") })
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @BatchSize(size = 20)
    private Set<Authority> authorities = new HashSet<>();

    @ManyToMany
    @JoinTable(name = "user_organization", joinColumns = { @JoinColumn(name = "user_id", referencedColumnName = "id") }, inverseJoinColumns = {
            @JoinColumn(name = "organization_id", referencedColumnName = "id") })
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonIgnoreProperties("user")
    private Set<Organization> organizations = new HashSet<>();

    @OneToMany(mappedBy = "student", fetch = FetchType.LAZY, cascade = CascadeType.REMOVE, orphanRemoval = true)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonIgnoreProperties(value = "student", allowSetters = true)
    public Set<TutorialGroupRegistration> tutorialGroupRegistrations = new HashSet<>();

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY, cascade = CascadeType.REMOVE, orphanRemoval = true)
    @JsonIgnore
    private Set<LectureUnitCompletion> completedLectureUnits = new HashSet<>();

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY, cascade = CascadeType.REMOVE, orphanRemoval = true)
    @JsonIgnore
    private Set<CompetencyProgress> competencyProgresses = new HashSet<>();

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY, cascade = CascadeType.REMOVE, orphanRemoval = true)
    @JsonIgnore
    private Set<LearningPath> learningPaths = new HashSet<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.LAZY)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonIgnore
    private Set<ExamUser> examUsers = new HashSet<>();

    @OneToMany(mappedBy = "owner", fetch = FetchType.LAZY, cascade = CascadeType.REMOVE, orphanRemoval = true)
    @JsonIgnore
    private Set<PushNotificationDeviceConfiguration> pushNotificationDeviceConfigurations = new HashSet<>();

    @Nullable
    @Column(name = "iris_accepted")
    private ZonedDateTime irisAccepted = null;

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties("user")
    @JoinColumn(name = "learner_profile_id")
    private LearnerProfile learnerProfile;

    public User() {
    }

    public User(Long id) {
        this.setId(id);
    }

    public User(Long id, String firstName, String lastName) {
        this(id);
        this.firstName = firstName;
        this.lastName = lastName;
    }

    public User(Long id, String login, String firstName, String lastName, String langKey, String email) {
        this(id);
        this.login = login;
        this.firstName = firstName;
        this.lastName = lastName;
        this.langKey = langKey;
        this.email = email;
    }

    public String getLogin() {
        return login;
    }

    // Lowercase the login before saving it in database
    public void setLogin(String login) {
        this.login = StringUtils.lowerCase(login, Locale.ENGLISH);
    }

    @Override
    public String getParticipantIdentifier() {
        return login;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    /**
     * @return name as a concatenation of first name and last name
     */
    @Override
    public String getName() {
        if (lastName != null && !lastName.isEmpty()) {
            return firstName + " " + lastName;
        }
        else {
            return firstName;
        }
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public boolean getActivated() {
        return activated;
    }

    public void setActivated(boolean activated) {
        this.activated = activated;
    }

    public String getActivationKey() {
        return activationKey;
    }

    public void setActivationKey(String activationKey) {
        this.activationKey = activationKey;
    }

    public String getResetKey() {
        return resetKey;
    }

    public void setResetKey(String resetKey) {
        this.resetKey = resetKey;
    }

    public Instant getResetDate() {
        return resetDate;
    }

    public void setResetDate(Instant resetDate) {
        this.resetDate = resetDate;
    }

    public ZonedDateTime getLastNotificationRead() {
        return lastNotificationRead;
    }

    public void setLastNotificationRead(ZonedDateTime lastNotificationRead) {
        this.lastNotificationRead = lastNotificationRead;
    }

    public ZonedDateTime getHideNotificationsUntil() {
        return hideNotificationsUntil;
    }

    public void setHideNotificationsUntil(ZonedDateTime hideNotificationsUntil) {
        this.hideNotificationsUntil = hideNotificationsUntil;
    }

    public String getLangKey() {
        return langKey;
    }

    public void setLangKey(String langKey) {
        this.langKey = langKey;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public void setRegistrationNumber(String registrationNumber) {
        this.registrationNumber = registrationNumber;
    }

    public String getVisibleRegistrationNumber() {
        return visibleRegistrationNumberTransient;
    }

    public void setVisibleRegistrationNumber(String visibleRegistrationNumber) {
        this.visibleRegistrationNumberTransient = visibleRegistrationNumber;
    }

    public void setVisibleRegistrationNumber() {
        this.visibleRegistrationNumberTransient = this.getRegistrationNumber();
    }

    public Set<String> getGroups() {
        return groups;
    }

    public void setGroups(Set<String> groups) {
        this.groups = groups;
    }

    public Set<Authority> getAuthorities() {
        return authorities;
    }

    public void setAuthorities(Set<Authority> authorities) {
        this.authorities = authorities;
    }

    public Set<Organization> getOrganizations() {
        return organizations;
    }

    public void setOrganizations(Set<Organization> organizations) {
        this.organizations = organizations;
    }

    public Set<LectureUnitCompletion> getCompletedLectureUnits() {
        return completedLectureUnits;
    }

    public void setCompletedLectureUnits(Set<LectureUnitCompletion> completedLectureUnits) {
        this.completedLectureUnits = completedLectureUnits;
    }

    public Set<CompetencyProgress> getCompetencyProgresses() {
        return competencyProgresses;
    }

    public void setCompetencyProgresses(Set<CompetencyProgress> competencyProgresses) {
        this.competencyProgresses = competencyProgresses;
    }

    public Set<LearningPath> getLearningPaths() {
        return learningPaths;
    }

    public void setLearningPaths(Set<LearningPath> learningPaths) {
        this.learningPaths = learningPaths;
    }

    public Set<ExamUser> getExamUsers() {
        return examUsers;
    }

    public void setExamUsers(Set<ExamUser> examUsers) {
        this.examUsers = examUsers;
    }

    public Set<GuidedTourSetting> getGuidedTourSettings() {
        return this.guidedTourSettings;
    }

    public void addGuidedTourSetting(GuidedTourSetting setting) {
        this.guidedTourSettings.add(setting);
        setting.setUser(this);
    }

    public void removeGuidedTourSetting(GuidedTourSetting setting) {
        this.guidedTourSettings.remove(setting);
        setting.setUser(null);
    }

    public void setGuidedTourSettings(Set<GuidedTourSetting> guidedTourSettings) {
        this.guidedTourSettings = guidedTourSettings;
    }

    @Override
    @JsonIgnore
    public Set<User> getParticipants() {
        return Set.of(this);
    }

    /**
     * @return an unmodifiable list of all granted authorities
     */
    @JsonIgnore
    public List<SimpleGrantedAuthority> getGrantedAuthorities() {
        return getAuthorities().stream().map(authority -> new SimpleGrantedAuthority(authority.getName())).toList();
    }

    @Override
    public String toString() {
        return "User{" + "login='" + login + '\'' + ", firstName='" + firstName + '\'' + ", lastName='" + lastName + '\'' + ", email='" + email + '\'' + ", imageUrl='" + imageUrl
                + '\'' + ", activated='" + activated + '\'' + ", langKey='" + langKey + '\'' + ", activationKey='" + activationKey + '\'' + "}";
    }

    @JsonIgnore
    public String toDatabaseString() {
        return "Student: login='" + login + '\'' + ", firstName='" + firstName + '\'' + ", lastName='" + lastName + '\'' + ", registrationNumber='" + registrationNumber + '\'';
    }

    public boolean isInternal() {
        return isInternal;
    }

    public void setInternal(boolean internal) {
        isInternal = internal;
    }

    public boolean isDeleted() {
        return isDeleted;
    }

    public void setDeleted(boolean deleted) {
        isDeleted = deleted;
    }

    @Nullable
    public String getVcsAccessToken() {
        return vcsAccessToken;
    }

    public void setVcsAccessToken(@Nullable String vcsAccessToken) {
        this.vcsAccessToken = vcsAccessToken;
    }

    @Nullable
    public ZonedDateTime getVcsAccessTokenExpiryDate() {
        return vcsAccessTokenExpiryDate;
    }

    public void setVcsAccessTokenExpiryDate(@Nullable ZonedDateTime vcsAccessTokenExpiryDate) {
        this.vcsAccessTokenExpiryDate = vcsAccessTokenExpiryDate;
    }

    public Set<TutorialGroupRegistration> getTutorialGroupRegistrations() {
        return tutorialGroupRegistrations;
    }

    public void setTutorialGroupRegistrations(Set<TutorialGroupRegistration> tutorialGroupRegistrations) {
        this.tutorialGroupRegistrations = tutorialGroupRegistrations;
    }

    public Set<PushNotificationDeviceConfiguration> getPushNotificationDeviceConfigurations() {
        return pushNotificationDeviceConfigurations;
    }

    public void setPushNotificationDeviceConfigurations(Set<PushNotificationDeviceConfiguration> pushNotificationDeviceConfigurations) {
        this.pushNotificationDeviceConfigurations = pushNotificationDeviceConfigurations;
    }

    @Nullable
    public ZonedDateTime getIrisAcceptedTimestamp() {
        return irisAccepted;
    }

    public void setIrisAcceptedTimestamp(@Nullable ZonedDateTime irisAccepted) {
        this.irisAccepted = irisAccepted;
    }

    public boolean hasAcceptedIris() {
        return irisAccepted != null;
    }

    /**
     * Checks if the user has accepted the Iris privacy policy.
     * If not, an {@link AccessForbiddenException} is thrown.
     */
    public void hasAcceptedIrisElseThrow() {
        if (irisAccepted == null) {
            throw new AccessForbiddenException("The user has not accepted the Iris privacy policy yet.");
        }
    }

    public LearnerProfile getLearnerProfile() {
        return learnerProfile;
    }

    public void setLearnerProfile(LearnerProfile learnerProfile) {
        this.learnerProfile = learnerProfile;
    }
}
