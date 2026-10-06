package com.elms.department;

import com.elms.common.ConflictException;
import com.elms.common.NotFoundException;
import com.elms.employee.EmployeeRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DepartmentService {

    private final DepartmentRepository repository;
    private final EmployeeRepository employeeRepository;

    public DepartmentService(DepartmentRepository repository, EmployeeRepository employeeRepository) {
        this.repository = repository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional(readOnly = true)
    public List<Department> findAll() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public Department findById(Long id) {
        return repository.findById(id).orElseThrow(() -> NotFoundException.of("Department", id));
    }

    @Transactional
    public Department create(String name) {
        return repository.save(new Department(name));
    }

    @Transactional
    public Department update(Long id, String name) {
        Department department = findById(id);
        department.setName(name);
        return repository.save(department);
    }

    @Transactional
    public void delete(Long id) {
        Department department = findById(id);
        if (!employeeRepository.findByDepartmentId(id).isEmpty()) {
            throw new ConflictException("Cannot delete department '" + department.getName()
                    + "' while employees are still assigned to it");
        }
        repository.delete(department);
    }
}
