package com.vns.healthcare.config;

import com.vns.healthcare.domain.EmployeeStatus;
import com.vns.healthcare.entity.Customer;
import com.vns.healthcare.entity.Employee;
import com.vns.healthcare.repository.CustomerRepository;
import com.vns.healthcare.repository.EmployeeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CacheRefreshService {

    private static final Logger log = LoggerFactory.getLogger(CacheRefreshService.class);

    private final CacheManager cacheManager;
    private final EmployeeRepository employeeRepository;
    private final CustomerRepository customerRepository;

    public CacheRefreshService(CacheManager cacheManager,
                              EmployeeRepository employeeRepository,
                              CustomerRepository customerRepository) {
        this.cacheManager = cacheManager;
        this.employeeRepository = employeeRepository;
        this.customerRepository = customerRepository;
    }

    public void clearEmployeeCaches() {
        clearCaches("employee-lists", "employee-pages", "employee-active", "employee-active-pages");
    }

    public void clearCustomerCaches() {
        clearCaches("customer-lists", "customer-pages");
    }

    @Transactional(readOnly = true)
    public void refreshEmployeeCaches() {
        clearEmployeeCaches();

        List<Employee> list = employeeRepository.findTop5ByOrderByCreatedAtDesc();
        putList("employee-lists", "all", list);

        Pageable pageRequest = PageRequest.of(0, 25, Sort.by(Sort.Direction.ASC, "empCode"));
        Page<Employee> page = employeeRepository.findFilteredPage(null, null, "", EmployeeRepository.buildPrefix(""), pageRequest);
        putPage("employee-pages", pageRequest.getPageNumber() + ":" + pageRequest.getPageSize() + ":" + pageRequest.getSort(), page);

        List<Employee> active = employeeRepository.findAllActive(EmployeeStatus.ACTIVE);
        putList("employee-active", "all", active);

        Pageable activePageRequest = PageRequest.of(0, 25);
        Page<Employee> activePage = employeeRepository.findActivePage(EmployeeStatus.ACTIVE, activePageRequest);
        putPage("employee-active-pages", activePageRequest.getPageNumber() + ":" + activePageRequest.getPageSize(), activePage);

        log.info("Refreshed employee caches: list={}, active={}, pageItems={}, activePageItems={}",
                list.size(), active.size(), page.getNumberOfElements(), activePage.getNumberOfElements());
    }

    @Transactional(readOnly = true)
    public void refreshCustomerCaches() {
        clearCustomerCaches();

        List<Customer> list = customerRepository.findPage(PageRequest.of(0, 20)).getContent();
        putList("customer-lists", "all", list);

        Pageable pageRequest = PageRequest.of(0, 25, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Customer> page = customerRepository.findPage(pageRequest);
        putPage("customer-pages", pageRequest.getPageNumber() + ":" + pageRequest.getPageSize() + ":" + pageRequest.getSort(), page);

        log.info("Refreshed customer caches: list={}, pageItems={}", list.size(), page.getNumberOfElements());
    }

    private void clearCaches(String... names) {
        for (String name : names) {
            Cache cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        }
    }

    private void putList(String cacheName, String key, List<?> values) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.put(key, values);
        }
    }

    private void putPage(String cacheName, String key, Page<?> page) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.put(key, page);
        }
    }
}
