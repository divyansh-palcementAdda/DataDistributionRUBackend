package com.app.datadistribution.service.impl;

import com.app.datadistribution.common.PageRequestDTO;
import com.app.datadistribution.dto.lead.BoardPageResponse;
import com.app.datadistribution.dto.lead.BoardRequest;
import com.app.datadistribution.dto.lead.BoardResponse;
import com.app.datadistribution.entity.Board;
import com.app.datadistribution.exception.DuplicateResourceException;
import com.app.datadistribution.exception.ResourcesNotFoundException;
import com.app.datadistribution.mapper.LeadMapper;
import com.app.datadistribution.repository.BoardRepository;
import com.app.datadistribution.service.interfaces.IBoardService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BoardServiceImpl implements IBoardService {

    private final BoardRepository boardRepository;
    private final LeadMapper leadMapper;
    private final com.app.datadistribution.service.interfaces.IDashboardCardPermissionService dashboardCardPermissionService;

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "id", "name", "code", "description", "active", "displayOrder", "createdAt", "updatedAt"
    );

    @Override
    @Transactional
    public BoardResponse create(BoardRequest request) {
        if (boardRepository.existsByNameIgnoreCase(request.getName())) {
            throw new DuplicateResourceException("Board name already exists: " + request.getName());
        }

        String code = generateUniqueCode(request.getName(), request.getCode(), null);

        Board board = leadMapper.toEntity(request);
        board.setCode(code);
        if (request.getDisplayOrder() != null) {
            board.setDisplayOrder(request.getDisplayOrder());
        }

        Board saved = boardRepository.save(board);
        log.info("Created board: {} with code {}", saved.getName(), saved.getCode());

        try {
            dashboardCardPermissionService.ensureEntityCardAndPermission("BOARD", saved.getCode(), saved.getName(), "BOARD");
        } catch (Exception e) {
            log.warn("Failed to generate dashboard card/permission for new board {}", saved.getCode(), e);
        }

        return leadMapper.toDto(saved);
    }

    @Override
    @Transactional
    public BoardResponse update(UUID id, BoardRequest request) {
        Board board = boardRepository.findById(id)
                .filter(b -> !b.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Board not found with id: " + id));

        if (boardRepository.existsByNameIgnoreCaseAndIdNot(request.getName(), id)) {
            throw new DuplicateResourceException("Board name already exists: " + request.getName());
        }

        String code = generateUniqueCode(request.getName(), request.getCode(), id);

        board.setName(request.getName());
        board.setCode(code);
        board.setDescription(request.getDescription());
        board.setActive(request.isActive());
        if (request.getDisplayOrder() != null) {
            board.setDisplayOrder(request.getDisplayOrder());
        }

        Board updated = boardRepository.save(board);
        log.info("Updated board: {} ({})", updated.getName(), updated.getCode());
        return leadMapper.toDto(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public BoardResponse getById(UUID id) {
        Board board = boardRepository.findById(id)
                .filter(b -> !b.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Board not found with id: " + id));
        return leadMapper.toDto(board);
    }

    @Override
    @Transactional(readOnly = true)
    public BoardPageResponse getAll(PageRequestDTO pageRequest) {
        return getAll(pageRequest, null);
    }

    @Override
    @Transactional(readOnly = true)
    public BoardPageResponse getAll(PageRequestDTO pageRequest, String status) {
        String sortBy = pageRequest.getSortBy();
        if (sortBy == null || !ALLOWED_SORT_FIELDS.contains(sortBy)) {
            sortBy = "displayOrder";
        }
        Sort.Direction direction = Sort.Direction.fromString(
                pageRequest.getSortDirection() != null ? pageRequest.getSortDirection() : "ASC"
        );
        Pageable pageable = PageRequest.of(pageRequest.getPage(), pageRequest.getSize(), Sort.by(direction, sortBy));

        Specification<Board> spec = Specification.where(isNotDeleted());
        if (status != null && !status.isBlank()) {
            spec = spec.and(filterByStatus(status));
        }
        if (pageRequest.getSearch() != null && !pageRequest.getSearch().isBlank()) {
            spec = spec.and(searchBoards(pageRequest.getSearch()));
        }

        Page<Board> page = boardRepository.findAll(spec, pageable);
        List<BoardResponse> content = page.getContent().stream()
                .map(leadMapper::toDto)
                .collect(Collectors.toList());

        return BoardPageResponse.builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Board board = boardRepository.findById(id)
                .filter(b -> !b.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Board not found with id: " + id));
        board.setDeleted(true);
        boardRepository.save(board);
        log.info("Soft deleted board: {}", board.getName());
    }

    @Override
    @Transactional
    public BoardResponse toggleActive(UUID id) {
        Board board = boardRepository.findById(id)
                .filter(b -> !b.isDeleted())
                .orElseThrow(() -> new ResourcesNotFoundException("Board not found with id: " + id));
        board.setActive(!board.isActive());
        Board saved = boardRepository.save(board);
        log.info("Toggled board active status to {} for: {}", saved.isActive(), saved.getName());
        return leadMapper.toDto(saved);
    }

    private String generateUniqueCode(String name, String providedCode, UUID excludeId) {
        if (providedCode != null && !providedCode.isBlank()) {
            String trimmedCode = providedCode.trim().toUpperCase();
            boolean exists = (excludeId == null)
                    ? boardRepository.existsByCodeIgnoreCase(trimmedCode)
                    : boardRepository.existsByCodeIgnoreCaseAndIdNot(trimmedCode, excludeId);
            if (exists) {
                throw new DuplicateResourceException("Board code already exists: " + trimmedCode);
            }
            return trimmedCode;
        }

        String baseCode = name.replaceAll("[^a-zA-Z0-9]", "_").toUpperCase();
        if (baseCode.length() > 20) {
            baseCode = baseCode.substring(0, 20);
        }
        if (baseCode.isBlank()) {
            baseCode = "BOARD";
        }

        String candidate = baseCode;
        boolean exists = (excludeId == null)
                ? boardRepository.existsByCodeIgnoreCase(candidate)
                : boardRepository.existsByCodeIgnoreCaseAndIdNot(candidate, excludeId);

        if (!exists) {
            return candidate;
        }

        while (true) {
            String randomSuffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
            candidate = baseCode + "_" + randomSuffix;
            exists = (excludeId == null)
                    ? boardRepository.existsByCodeIgnoreCase(candidate)
                    : boardRepository.existsByCodeIgnoreCaseAndIdNot(candidate, excludeId);
            if (!exists) {
                return candidate;
            }
        }
    }

    private Specification<Board> isNotDeleted() {
        return (root, query, cb) -> cb.equal(root.get("isDeleted"), false);
    }

    private Specification<Board> filterByStatus(String status) {
        return (root, query, cb) -> {
            boolean active = "ACTIVE".equalsIgnoreCase(status);
            return cb.equal(root.get("active"), active);
        };
    }

    private Specification<Board> searchBoards(String keyword) {
        return (root, query, cb) -> {
            String searchPattern = "%" + keyword.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("name")), searchPattern),
                    cb.like(cb.lower(root.get("code")), searchPattern),
                    cb.like(cb.lower(root.get("description")), searchPattern)
            );
        };
    }
}
