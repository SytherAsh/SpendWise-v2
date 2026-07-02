package com.spendwise.transaction;

import com.spendwise.transaction.dto.CreateEmiRequest;
import com.spendwise.transaction.dto.EmiResponse;
import com.spendwise.transaction.dto.UpdateEmiRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** docs/api.md "/emis" — owned by the Transaction module. */
@RestController
@RequestMapping("/api/v1/emis")
public class EmiController {

    private final EmiService emiService;

    public EmiController(EmiService emiService) {
        this.emiService = emiService;
    }

    @GetMapping
    public List<EmiResponse> list(@AuthenticationPrincipal UUID userId) {
        return emiService.listActive(userId).stream().map(EmiResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<EmiResponse> create(@AuthenticationPrincipal UUID userId, @Valid @RequestBody CreateEmiRequest request) {
        Emi created = emiService.createManual(userId, request.label(), request.amount(), request.dueDay());
        return ResponseEntity.status(HttpStatus.CREATED).body(EmiResponse.from(created));
    }

    @PutMapping("/{id}")
    public void update(@AuthenticationPrincipal UUID userId, @PathVariable UUID id, @Valid @RequestBody UpdateEmiRequest request) {
        emiService.update(userId, id, request.label(), request.amount(), request.dueDay());
    }

    @PatchMapping("/{id}")
    public void deactivate(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        emiService.deactivate(userId, id);
    }
}
