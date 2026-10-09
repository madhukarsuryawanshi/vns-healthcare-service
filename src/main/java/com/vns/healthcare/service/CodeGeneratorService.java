package com.vns.healthcare.service;

import com.vns.healthcare.entity.AppSequence;
import com.vns.healthcare.repository.AppSequenceRepository;
import com.vns.healthcare.repository.CustomerRepository;
import com.vns.healthcare.repository.EmployeeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CodeGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(CodeGeneratorService.class);

    private final AppSequenceRepository sequenceRepository;
    private final EmployeeRepository employeeRepository;
    private final CustomerRepository customerRepository;

    public CodeGeneratorService(AppSequenceRepository sequenceRepository,
                                EmployeeRepository employeeRepository,
                                CustomerRepository customerRepository) {
        this.sequenceRepository = sequenceRepository;
        this.employeeRepository = employeeRepository;
        this.customerRepository = customerRepository;
    }

    @Transactional
    public String nextEmployeeCode() {
        String code = next("EMP", "EMP-", 1001, true);
        log.debug("Generated next employee code: {}", code);
        return code;
    }

    @Transactional
    public String nextCustomerCode() {
        String code = next("CUS", "CUS-", 1001, false);
        log.debug("Generated next customer code: {}", code);
        return code;
    }

    private String next(String seqName, String prefix, long start, boolean employeeCode) {
        AppSequence seq = sequenceRepository.findById(seqName).orElse(null);
        long value = (seq == null) ? start : seq.getNextValue();

        while (true) {
            String candidate = prefix + value;
            boolean exists = employeeCode
                    ? employeeRepository.findByEmpCode(candidate).isPresent()
                    : customerRepository.findByCustCode(candidate).isPresent();
            if (!exists) {
                if (seq == null) {
                    seq = new AppSequence();
                    seq.setName(seqName);
                }
                seq.setNextValue(value + 1);
                sequenceRepository.save(seq);
                return candidate;
            }
            value++;
        }
    }
}
