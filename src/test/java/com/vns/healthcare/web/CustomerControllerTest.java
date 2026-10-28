package com.vns.healthcare.web;

import com.vns.healthcare.domain.CustomerStatus;
import com.vns.healthcare.entity.Customer;
import com.vns.healthcare.entity.CustomerInvoice;
import com.vns.healthcare.repository.AppSequenceRepository;
import com.vns.healthcare.repository.BusinessBankAccountRepository;
import com.vns.healthcare.repository.CustomerInvoiceRepository;
import com.vns.healthcare.security.UserActivityService;
import com.vns.healthcare.service.CustomerChargeStatusService;
import com.vns.healthcare.service.CustomerDutyService;
import com.vns.healthcare.service.CustomerReportService;
import com.vns.healthcare.service.CustomerService;
import com.vns.healthcare.service.EmployeeService;
import com.vns.healthcare.service.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerControllerTest {

    @Mock
    private CustomerService customerService;
    @Mock
    private EmployeeService employeeService;
    @Mock
    private CustomerDutyService dutyService;
    @Mock
    private CustomerReportService reportService;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private BusinessBankAccountRepository businessBankAccountRepository;
    @Mock
    private CustomerInvoiceRepository customerInvoiceRepository;
    @Mock
    private AppSequenceRepository appSequenceRepository;
    @Mock
    private CustomerChargeStatusService chargeStatusService;
    @Mock
    private UserActivityService userActivityService;

    private CustomerController controller;

    @BeforeEach
    void setUp() {
        controller = new CustomerController(
                customerService,
                employeeService,
                dutyService,
                reportService,
                fileStorageService,
                businessBankAccountRepository,
                customerInvoiceRepository,
                appSequenceRepository,
                chargeStatusService,
                userActivityService);
    }

    @Test
    void editForm_restrictsStatusOptionsToActiveAndClosed() {
        Customer customer = new Customer();
        customer.setId(12L);
        customer.setCustCode("CUS-1001");
        customer.setPatientName("Anita");
        customer.setServiceType(com.vns.healthcare.domain.ServiceType.HOURS_24);
        customer.setStatus(CustomerStatus.ACTIVE);

        when(customerService.get(12L)).thenReturn(customer);
        when(employeeService.activeCareStaff()).thenReturn(Collections.emptyList());

        ExtendedModelMap model = new ExtendedModelMap();
        RedirectAttributesModelMap flash = new RedirectAttributesModelMap();

        String view = controller.editForm(12L, model, flash);

        assertEquals("customers/form", view);
        assertEquals("edit", model.getAttribute("mode"));
        assertArrayEquals(new CustomerStatus[]{CustomerStatus.ACTIVE, CustomerStatus.CLOSED},
                (CustomerStatus[]) model.getAttribute("statuses"));
    }

    @Test
    void update_validationFailure_usesRestrictedStatusOptions() {
        CustomerForm form = new CustomerForm();
        form.setPatientName("");
        BindingResult bindingResult = new BeanPropertyBindingResult(form, "form");
        bindingResult.rejectValue("patientName", "NotBlank", "Patient name is required");

        when(employeeService.activeStaff()).thenReturn(Collections.emptyList());

        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.update(12L, form, bindingResult, model, new RedirectAttributesModelMap());

        assertEquals("customers/form", view);
        assertArrayEquals(new CustomerStatus[]{CustomerStatus.ACTIVE, CustomerStatus.CLOSED},
                (CustomerStatus[]) model.getAttribute("statuses"));
    }

    @Test
    void saveInvoice_persistsAdvanceDeductionAndReturnsItInResponse() {
        Customer customer = new Customer();
        customer.setId(12L);
        customer.setCustCode("CUS-1001");
        customer.setPatientName("Anita");
        customer.setAdvancePayment(new BigDecimal("5000.00"));

        when(customerService.get(12L)).thenReturn(customer);
        when(customerService.applyAdvancePayment(any(Long.class), any(BigDecimal.class)))
                .thenReturn(customer);
        when(customerInvoiceRepository.save(any(CustomerInvoice.class))).thenAnswer(invocation -> {
            CustomerInvoice invoice = invocation.getArgument(0);
            invoice.setId(88L);
            invoice.setInvoiceNumber("vns/2026-2027/001");
            return invoice;
        });

        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("invoiceId", "");
        payload.put("invoiceNumber", "");
        payload.put("fromDate", "2026-10-01");
        payload.put("toDate", "2026-10-10");
        payload.put("totalAmount", new BigDecimal("2500.00"));
        payload.put("discountAmount", new BigDecimal("0.00"));
        payload.put("applyAdvance", true);
        payload.put("advanceAmount", new BigDecimal("1500.00"));
        payload.put("netAmount", new BigDecimal("1000.00"));
        payload.put("rows", Collections.emptyList());
        payload.put("accountId", "1");
        payload.put("accountName", "Sample");
        payload.put("bankName", "Bank");
        payload.put("accountNo", "123456");
        payload.put("ifscCode", "IFSC0001");
        payload.put("gpayPhonepe", "9999999999");

        ResponseEntity<Map<String, Object>> response = controller.saveInvoice(12L, payload);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(new BigDecimal("1500.0"), response.getBody().get("advanceAmount"));
    }

    @Test
    void getInvoice_returnsAdvanceAmountFromSavedRecord() {
        Customer customer = new Customer();
        customer.setId(12L);
        customer.setCustCode("CUS-1001");

        CustomerInvoice invoice = new CustomerInvoice();
        invoice.setId(88L);
        invoice.setCustomer(customer);
        invoice.setInvoiceNumber("vns/2026-2027/001");
        invoice.setFromDate(LocalDate.of(2026, 10, 1));
        invoice.setToDate(LocalDate.of(2026, 10, 10));
        invoice.setTotalAmount(new BigDecimal("2500.00"));
        invoice.setDiscountAmount(BigDecimal.ZERO);
        invoice.setAdvanceAmount(new BigDecimal("500.00"));
        invoice.setNetAmount(new BigDecimal("2000.00"));
        invoice.setBreakdown("[]");

        when(customerService.get(12L)).thenReturn(customer);
        when(customerInvoiceRepository.findById(88L)).thenReturn(Optional.of(invoice));

        ResponseEntity<Map<String, Object>> response = controller.getInvoice(12L, 88L);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(new BigDecimal("500.00"), response.getBody().get("advanceAmount"));
    }
}
