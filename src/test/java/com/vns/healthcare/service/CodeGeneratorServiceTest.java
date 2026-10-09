package com.vns.healthcare.service;

import com.vns.healthcare.entity.AppSequence;
import com.vns.healthcare.entity.Employee;
import com.vns.healthcare.repository.AppSequenceRepository;
import com.vns.healthcare.repository.CustomerRepository;
import com.vns.healthcare.repository.EmployeeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CodeGeneratorServiceTest {

    @Mock
    private AppSequenceRepository sequenceRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private CodeGeneratorService codeGeneratorService;

    @Test
    void nextEmployeeCode_skipsExistingCodesAndPersistsNextValue() {
        AppSequence sequence = new AppSequence();
        sequence.setName("EMP");
        sequence.setNextValue(1001L);

        when(sequenceRepository.findById("EMP")).thenReturn(Optional.of(sequence));
        when(employeeRepository.findByEmpCode("EMP-1001")).thenReturn(Optional.of(new Employee()));
        when(employeeRepository.findByEmpCode("EMP-1002")).thenReturn(Optional.empty());

        String code = codeGeneratorService.nextEmployeeCode();

        assertEquals("EMP-1002", code);
        assertEquals(1003L, sequence.getNextValue());
        verify(sequenceRepository).save(sequence);
    }

    @Test
    void nextCustomerCode_usesNewSequenceIfRowIsMissing() {
        when(sequenceRepository.findById("CUS")).thenReturn(Optional.empty());
        when(customerRepository.findByCustCode("CUS-1001")).thenReturn(Optional.empty());

        String code = codeGeneratorService.nextCustomerCode();

        assertEquals("CUS-1001", code);
        verify(sequenceRepository).save(any(AppSequence.class));
    }
}
