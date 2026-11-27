package ch.usi.studysystem.api.controllers;

import ch.usi.studysystem.api.dto.ApiResponse;
import ch.usi.studysystem.api.error.NotFoundException;
import ch.usi.studysystem.application.professor.ProfessorService;
import ch.usi.studysystem.domain.professor.Professor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/professors")
@Tag(name = "Professors", description = "Professors and the courses they teach (MongoDB)")
public class ProfessorController {

    private final ProfessorService professors;

    public ProfessorController(ProfessorService professors) {
        this.professors = professors;
    }

    @GetMapping
    @Operation(summary = "List professors", description = "Sorted by last name. `q` filters by first or last name.")
    public ApiResponse<List<Professor>> list(
            @Parameter(description = "Part of a first or last name, case-insensitive")
            @RequestParam(required = false) String q) {
        return ApiResponse.ok(professors.list(q));
    }

    @GetMapping("/{id}")
    @Operation(summary = "One professor", description = "e.g. prof:95ecad00cca3b94274998db811a1beda")
    public ApiResponse<Professor> get(@PathVariable String id) {
        return ApiResponse.ok(professors.get(id)
                .orElseThrow(() -> new NotFoundException("No professor " + id)));
    }
}
