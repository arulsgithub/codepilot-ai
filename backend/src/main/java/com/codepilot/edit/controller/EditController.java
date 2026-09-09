package com.codepilot.edit.controller;

import com.codepilot.edit.dto.EditPlanRequest;
import com.codepilot.edit.dto.EditPlanResponse;
import com.codepilot.edit.service.EditPlannerService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/edits")
public class EditController {

    private final EditPlannerService editPlannerService;

    public EditController(EditPlannerService editPlannerService) {
        this.editPlannerService = editPlannerService;
    }

    /** Plans edits and returns previews. Writes NOTHING to disk - Stage B adds apply. */
    @PostMapping("/plan")
    public ResponseEntity<EditPlanResponse> plan(@Valid @RequestBody EditPlanRequest request) {
        return ResponseEntity.ok(
                editPlannerService.planEdits(request.repositoryRoot(), request.instruction()));
    }
}