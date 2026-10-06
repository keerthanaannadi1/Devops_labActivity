package com.elms.department;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/departments")
@Tag(name = "Departments")
public class DepartmentController {

    private final DepartmentService service;

    public DepartmentController(DepartmentService service) {
        this.service = service;
    }

    public record DepartmentRequest(@NotBlank String name) {
    }

    public record DepartmentResponse(Long id, String name) {

        static DepartmentResponse from(Department department) {
            return new DepartmentResponse(department.getId(), department.getName());
        }
    }

    @GetMapping
    @Operation(summary = "List all departments")
    public List<DepartmentResponse> list() {
        return service.findAll().stream().map(DepartmentResponse::from).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one department")
    public DepartmentResponse get(@PathVariable Long id) {
        return DepartmentResponse.from(service.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('HR_ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a department")
    public DepartmentResponse create(@Valid @RequestBody DepartmentRequest request) {
        return DepartmentResponse.from(service.create(request.name()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('HR_ADMIN')")
    @Operation(summary = "Update a department")
    public DepartmentResponse update(@PathVariable Long id, @Valid @RequestBody DepartmentRequest request) {
        return DepartmentResponse.from(service.update(id, request.name()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('HR_ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a department")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
