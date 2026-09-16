package com.vns.healthcare.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityListeners;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@EntityListeners(AuditEntityListener.class)
@Table(name = "app_sequence")
public class AppSequence extends AuditableEntity {

    @Id
    @Column(name = "seq_name", length = 40)
    private String name;

    @Column(name = "next_value", nullable = false)
    private long nextValue;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getNextValue() {
        return nextValue;
    }

    public void setNextValue(long nextValue) {
        this.nextValue = nextValue;
    }
}
