package org.motechproject.server.model.rct;

import org.motechproject.server.model.Facility;
import org.motechproject.ws.rct.ControlGroup;
import org.openmrs.User;

import java.util.Date;

public class RCTPatient {

    private Long id;
    private String studyId;
    private Stratum stratum;
    private ControlGroup controlGroup;
    private Date enrollmentDate;
    private User enrolledBy;
    private Character enrolled;

    public RCTPatient() {}

    public RCTPatient(String studyId, Stratum stratum, ControlGroup controlGroup, User enrolledBy) {
        this.studyId = studyId;
        this.stratum = stratum;
        this.controlGroup = controlGroup;
        this.enrolledBy = enrolledBy;
        this.enrollmentDate = new Date();
        this.enrolled = 'Y';
    }

    public Boolean isTreatment(){
        return controlGroup.isTreatment();
    }

    public Boolean isControl(){
        return !controlGroup.isTreatment();
    }

    public String getStudyId() {
        return studyId;
    }

    public Stratum getStratum() {
        return stratum;
    }
    
    public ControlGroup getControlGroup(){
        return controlGroup;
    }

    public Date getEnrollmentDate() {
        return enrollmentDate;
    }

    public User getEnrolledBy() {
        return enrolledBy;
    }

    public Character getEnrolled() {
        return enrolled;
    }

    public Facility getFacility(){
        return stratum.getRctFacility().getFacility();
    }
}
