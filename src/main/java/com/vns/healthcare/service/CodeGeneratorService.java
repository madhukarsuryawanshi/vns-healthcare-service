package com.vns.healthcare.service;

import com.vns.healthcare.entity.AppSequence;
import com.vns.healthcare.repository.AppSequenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CodeGeneratorService {

    private final AppSequenceRepository sequenceRepository;

    public CodeGeneratorService(AppSequenceRepository sequenceRepository) {
        this.sequenceRepository = sequenceRepository;
    }

    @Transactional
    public String nextEmployeeCode() {
        return next("EMP", "EMP-", 1001);
    }

    @Transactional
    public String nextCustomerCode() {
        return next("CUS", "CUS-", 1001);
    }

    private String next(String seqName, String prefix, long start) {
        AppSequence seq = sequenceRepository.findById(seqName).orElse(null);
        if (seq == null) {
            seq = new AppSequence();
            seq.setName(seqName);
            seq.setNextValue(start);
        }
        long value = seq.getNextValue();
        seq.setNextValue(value + 1);
        sequenceRepository.save(seq);
        return prefix + value;
    }
}
