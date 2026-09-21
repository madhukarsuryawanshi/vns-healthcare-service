package com.vns.healthcare.entity;

import com.vns.healthcare.domain.EmployeeStatus;
import com.vns.healthcare.domain.Gender;
import com.vns.healthcare.domain.TrainingStatus;

import org.springframework.format.annotation.DateTimeFormat;

import javax.persistence.CascadeType;
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.EntityListeners;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
import java.time.LocalDate;
import javax.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@EntityListeners(AuditEntityListener.class)
@Table(name = "employees", uniqueConstraints = {
        @UniqueConstraint(columnNames = "emp_code"),
        @UniqueConstraint(columnNames = "aadhar_number")
})
public class Employee extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "emp_code", nullable = false, length = 20)
    private String empCode;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(name = "mobile_no", nullable = false, length = 15)
    private String mobileNo;

    @Column(name = "joining_date", nullable = false)
//    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate joiningDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Gender gender;

    @Column(name = "date_of_birth", nullable = false)
//    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateOfBirth;

    @Column(name = "referred_by", length = 120)
    private String referredBy;

    @Column(name = "full_address", nullable = false, length = 500)
    private String fullAddress;

    @Column(name = "aadhar_number", nullable = false, length = 12)
    private String aadharNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "training_status", nullable = false, length = 20)
    private TrainingStatus trainingStatus = TrainingStatus.NOT_STARTED;

    @Column(name = "training_notes", length = 1000)
    private String trainingNotes;

    @Column(nullable = false)
    private boolean onboarded;

    @Column(name = "no_of_experience", precision = 4, scale = 1)
    private java.math.BigDecimal noOfExperience;

    @Column(precision = 12, scale = 2)
    private java.math.BigDecimal salary;

    @Column(name = "salary_start_date")
    private LocalDate salaryStartDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmployeeStatus status = EmployeeStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "designation", length = 40)
    private com.vns.healthcare.domain.Designation designation;

    @Column(name = "email", length = 160)
    private String email;

    @Column(name = "marital_status", length = 20)
    private String maritalStatus;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "employee_languages", joinColumns = @JoinColumn(name = "employee_id"))
    @Column(name = "language", nullable = false, length = 30)
    private Set<String> knownLanguages = new HashSet<String>();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "employee", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("uploadedAt DESC")
    private List<EmployeeDocument> documents = new ArrayList<EmployeeDocument>();

    @PrePersist
    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (trainingStatus == null) {
            trainingStatus = TrainingStatus.NOT_STARTED;
        }
        if (status == null) {
            status = EmployeeStatus.ACTIVE;
        }
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmpCode() {
        return empCode;
    }

    public void setEmpCode(String empCode) {
        this.empCode = empCode;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getMobileNo() {
        return mobileNo;
    }

    public void setMobileNo(String mobileNo) {
        this.mobileNo = mobileNo;
    }

    public LocalDate getJoiningDate() {
        return joiningDate;
    }

    public void setJoiningDate(LocalDate joiningDate) {
        this.joiningDate = joiningDate;
    }

    public Gender getGender() {
        return gender;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getReferredBy() {
        return referredBy;
    }

    public void setReferredBy(String referredBy) {
        this.referredBy = referredBy;
    }

    public String getFullAddress() {
        return fullAddress;
    }

    public void setFullAddress(String fullAddress) {
        this.fullAddress = fullAddress;
    }

    public String getAadharNumber() {
        return aadharNumber;
    }

    public void setAadharNumber(String aadharNumber) {
        this.aadharNumber = aadharNumber;
    }

    public TrainingStatus getTrainingStatus() {
        return trainingStatus;
    }

    public void setTrainingStatus(TrainingStatus trainingStatus) {
        this.trainingStatus = trainingStatus;
    }

    public String getTrainingNotes() {
        return trainingNotes;
    }

    public void setTrainingNotes(String trainingNotes) {
        this.trainingNotes = trainingNotes;
    }

    public boolean isOnboarded() {
        return onboarded;
    }

    public void setOnboarded(boolean onboarded) {
        this.onboarded = onboarded;
    }

    public java.math.BigDecimal getNoOfExperience() {
        return noOfExperience;
    }

    public void setNoOfExperience(java.math.BigDecimal noOfExperience) {
        this.noOfExperience = noOfExperience;
    }

    public java.math.BigDecimal getSalary() {
        return salary;
    }

    public void setSalary(java.math.BigDecimal salary) {
        this.salary = salary;
    }

    public LocalDate getSalaryStartDate() {
        return salaryStartDate;
    }

    public void setSalaryStartDate(LocalDate salaryStartDate) {
        this.salaryStartDate = salaryStartDate;
    }

    public EmployeeStatus getStatus() {
        return status;
    }

    public void setStatus(EmployeeStatus status) {
        this.status = status;
    }

    public com.vns.healthcare.domain.Designation getDesignation() {
        return designation;
    }

    public void setDesignation(com.vns.healthcare.domain.Designation designation) {
        this.designation = designation;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getMaritalStatus() {
        return maritalStatus;
    }

    public void setMaritalStatus(String maritalStatus) {
        this.maritalStatus = maritalStatus;
    }

    public Set<String> getKnownLanguages() {
        return knownLanguages;
    }

    public void setKnownLanguages(Set<String> knownLanguages) {
        this.knownLanguages = knownLanguages == null ? new HashSet<String>() : knownLanguages;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public List<EmployeeDocument> getDocuments() {
        return documents;
    }

    public void setDocuments(List<EmployeeDocument> documents) {
        this.documents = documents;
    }
}
