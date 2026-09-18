package com.routinemachine.core.api;

import com.routinemachine.core.domain.RoutineItem;
import com.routinemachine.core.repository.RoutineItemRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/routine-items")
public class RoutineItemController {

    private final RoutineItemRepository routineItemRepository;

    public RoutineItemController(RoutineItemRepository routineItemRepository) {
        this.routineItemRepository = routineItemRepository;
    }

    @GetMapping
    public List<RoutineItemResponse> list() {
        return routineItemRepository.findAll().stream().map(RoutineItemResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoutineItemResponse create(@Valid @RequestBody RoutineItemRequest request) {
        RoutineItem saved = routineItemRepository.save(new RoutineItem(request.name(), request.description()));
        return RoutineItemResponse.from(saved);
    }
}
