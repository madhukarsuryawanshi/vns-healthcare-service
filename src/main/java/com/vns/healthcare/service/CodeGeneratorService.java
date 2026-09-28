package com.vns.healthcare.service;

import com.vns.healthcare.entity.AppSequence;
import com.vns.healthcare.repository.AppSequenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CodeGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(CodeGeneratorService.class);

    private final AppSequenceRepository sequenceRepository;

    public CodeGeneratorService(AppSequenceRepository sequenceRepository) {
        this.sequenceRepository = sequenceRepository;
    }

    @Transactional
    public String nextEmployeeCode() {
        String code = next("EMP", "EMP-", 1001);
        log.debug("Generated next employee code: {}", code);
        return code;
    }

    @Transactional
    public String nextCustomerCode() {
        String code = next("CUS", "CUS-", 1001);
        log.debug("Generated next customer code: {}", code);
        return code;
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
