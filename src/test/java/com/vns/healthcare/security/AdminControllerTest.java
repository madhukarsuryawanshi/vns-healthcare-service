package com.vns.healthcare.security;

import com.vns.healthcare.entity.Employee;
import com.vns.healthcare.repository.BusinessBankAccountRepository;
import com.vns.healthcare.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.ui.ExtendedModelMap;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock
    private RoleRepository roleRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private BusinessBankAccountRepository businessBankAccountRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private com.vns.healthcare.service.EmployeeService employeeService;
    @Mock
    private UserActivityService userActivityService;
    @Mock
    private CacheManager cacheManager;

    private AdminController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminController(
                roleRepository,
                userRepository,
                employeeRepository,
                businessBankAccountRepository,
                passwordEncoder,
                employeeService,
                userActivityService,
                cacheManager);
    }

    @Test
    void users_populatesEmployeeNameFromMatchingEmployeeCode() {
        AppUser user = new AppUser();
        user.setId(1L);
        user.setUsername("EMP01");
        user.setEnabled(true);

        Employee employee = new Employee();
        employee.setEmpCode("EMP01");
        employee.setFullName("Alice Johnson");

        when(userRepository.findAll()).thenReturn(List.of(user));
        when(employeeRepository.findByEmpCode("EMP01")).thenReturn(Optional.of(employee));

        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.users(null, "username", "asc", model);

        assertEquals("admin/users", view);
        @SuppressWarnings("unchecked")
        Map<Long, String> employeeNames = (Map<Long, String>) model.getAttribute("employeeNames");
        assertEquals("Alice Johnson", employeeNames.get(1L));
    }
}
