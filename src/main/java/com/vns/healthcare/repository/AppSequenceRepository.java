package com.vns.healthcare.repository;

import com.vns.healthcare.entity.AppSequence;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppSequenceRepository extends JpaRepository<AppSequence, String> {
}
