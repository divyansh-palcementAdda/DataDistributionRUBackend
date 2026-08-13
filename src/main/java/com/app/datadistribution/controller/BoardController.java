package com.app.datadistribution.controller;

import com.app.datadistribution.common.ApiResponse;
import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.dto.lead.BoardPageResponse;
import com.app.datadistribution.dto.lead.BoardRequest;
import com.app.datadistribution.dto.lead.BoardResponse;
import com.app.datadistribution.service.interfaces.IBoardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/boards")
@RequiredArgsConstructor
@Tag(name = "Board Management", description = "Endpoints for managing dynamic lead boards")
public class BoardController {

    private final IBoardService boardService;

    @PostMapping
    @PreAuthorize("hasAuthority('BOARD_CREATE')")
    @Operation(summary = "Create a new board")
    public ResponseEntity<ApiResponse<BoardResponse>> create(@Valid @RequestBody BoardRequest request) {
        BoardResponse response = boardService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Board created successfully", response, HttpStatus.CREATED.value()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('BOARD_UPDATE')")
    @Operation(summary = "Update an existing board")
    public ResponseEntity<ApiResponse<BoardResponse>> update(
            @PathVariable("id") UUID id,
            @Valid @RequestBody BoardRequest request) {
        BoardResponse response = boardService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success("Board updated successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('BOARD_VIEW')")
    @Operation(summary = "Get board details by ID")
    public ResponseEntity<ApiResponse<BoardResponse>> getById(@PathVariable("id") UUID id) {
        BoardResponse response = boardService.getById(id);
        return ResponseEntity.ok(ApiResponse.success("Board fetched successfully", response, HttpStatus.OK.value()));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('BOARD_VIEW')")
    @Operation(summary = "Get list of boards with pagination, sorting, search, and status filtering")
    public ResponseEntity<ApiResponse<BoardPageResponse>> getAll(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "sortBy", defaultValue = "displayOrder") String sortBy,
            @RequestParam(value = "sortDirection", defaultValue = "ASC") String sortDirection,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "status", required = false) String status) {

        PageRequestDTO pageRequest = PageRequestDTO.builder()
                .page(page)
                .size(size)
                .sortBy(sortBy)
                .sortDirection(sortDirection)
                .search(search)
                .build();

        BoardPageResponse response = boardService.getAll(pageRequest, status);
        return ResponseEntity.ok(ApiResponse.success("Boards retrieved successfully", response, HttpStatus.OK.value()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('BOARD_DELETE')")
    @Operation(summary = "Soft delete a board")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable("id") UUID id) {
        boardService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Board deleted successfully", null, HttpStatus.OK.value()));
    }

    @PutMapping("/{id}/toggle-active")
    @PreAuthorize("hasAuthority('BOARD_UPDATE')")
    @Operation(summary = "Toggle board active/inactive status")
    public ResponseEntity<ApiResponse<BoardResponse>> toggleActive(@PathVariable("id") UUID id) {
        BoardResponse response = boardService.toggleActive(id);
        return ResponseEntity.ok(ApiResponse.success("Board toggled successfully", response, HttpStatus.OK.value()));
    }
}
