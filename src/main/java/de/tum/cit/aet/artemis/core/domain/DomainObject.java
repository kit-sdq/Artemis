package de.tum.cit.aet.artemis.core.domain;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Base abstract class for entities which have an id that is generated automatically (basically all domain objects).
 */
@MappedSuperclass
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public abstract class DomainObject implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    /**
     * this method checks for database equality based on the id
     *
     * @param obj another object
     * @return whether this and the other object are equal based on the database id
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        DomainObject domainObject = (DomainObject) obj;
        if (domainObject.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), domainObject.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }
}
